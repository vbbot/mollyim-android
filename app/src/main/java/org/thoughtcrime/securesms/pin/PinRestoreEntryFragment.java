package org.thoughtcrime.securesms.pin;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import org.signal.core.util.logging.Log;
import org.signal.core.ui.logging.LoggingFragment;
import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.jobs.ProfileUploadJob;
import org.thoughtcrime.securesms.keyvalue.RestoreDecisionStateUtil;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.light.LightConfirmAction;
import org.thoughtcrime.securesms.lock.v2.LightSvrPinMenuItem;
import org.thoughtcrime.securesms.lock.v2.LightSvrPinView;
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType;
import org.thoughtcrime.securesms.lock.v2.SvrConstants;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.profiles.edit.CreateProfileActivity;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.registration.util.RegistrationUtil;
import org.thoughtcrime.securesms.restore.RestoreActivity;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.SupportEmailUtil;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;

import java.util.Collections;
import java.util.List;

/**
 * "Enter your Signal PIN", on the way into a restored account.
 *
 * <p>Presentation is {@link LightSvrPinView}'s -- the same view the create and confirm screens use,
 * because this is the same screen: a credential field, a status line, a keyboard switch, a confirm
 * action, and notices that stop the user getting anywhere until they have read them. What this class
 * still owns is everything that must not change: the length gate, the order the observers are
 * attached in, what each failure event does to the field, and where a rejected PIN leads.
 *
 * <p>The PIN is typed in the view's full-screen editor, which opens by itself on arrival, as upstream
 * focused the field with the keyboard up. The keyboard's Done closes the editor; the PIN is submitted
 * from the form's confirm action, and while it is checked the screen is a full-screen progress
 * state. A rejection returns to the form, with the field emptied and the reason on the status line.
 *
 * <p><strong>The Signal PIN is not an app lock.</strong> It is the credential that makes a backup
 * restorable after losing the phone, so every check below is reproduced exactly rather than
 * tidied. The one capability that did not survive the move to a Compose-hosted screen is the
 * debug-log multi-tap on the title, which needed a title {@code TextView} by id; the create and
 * confirm screens lost the same affordance when they moved, so this screen is now consistent with
 * them rather than uniquely missing it.
 */
public class PinRestoreEntryFragment extends LoggingFragment {
  private static final String TAG = Log.tag(PinRestoreActivity.class);

  private static final int MINIMUM_PIN_LENGTH = 4;

  private LightSvrPinView       pinView;
  private PinRestoreViewModel   viewModel;

  /**
   * Back closes a notice rather than the screen.
   *
   * <p>The notices this screen raises were all cancelable upstream -- none of them called
   * {@code setCancelable(false)} -- so back dismissed them and left the user on the PIN screen with
   * their entry intact. The notice is drawn in place rather than in a window, which would have let
   * the back stack through instead, so this is what keeps that behaviour true.
   */
  private final OnBackPressedCallback dismissNoticeOnBack = new OnBackPressedCallback(false) {
    @Override
    public void handleOnBackPressed() {
      clearNotice();
    }
  };

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.pin_restore_entry_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    pinView = view.findViewById(R.id.light_svr_pin_view);

    pinView.updateHeader(
        getString(R.string.RegistrationLockFragment__enter_your_pin),
        getString(R.string.RegistrationLockFragment__enter_the_pin_you_created),
        false
    );
    // `R` is non-transitive in this build, and Java has no import alias, so the one string that
    // lives in the registration module is named in full. It is the confirm label the button always
    // had; there is no app-local equivalent and borrowing another screen's string would be worse.
    pinView.updateConfirm(getString(org.signal.registration.R.string.RegistrationActivity_continue), true, false);
    pinView.updateLabel("", false);
    pinView.updateFieldPlaceholder(getString(R.string.RegistrationLockFragment__enter_your_pin));
    pinView.updateBusyHeading(getString(R.string.LightSvrPinView__checking_pin));
    pinView.updateSupport(null, null);
    pinView.updateOverflowItems(Collections.singletonList(
        new LightSvrPinMenuItem(getString(R.string.PinRestoreEntryFragment_skip), this::onSkipClicked)
    ));

    requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), dismissNoticeOnBack);

    // The IME action is the view's: Done closes the editor and keeps the entry. Submitting is the
    // form's confirm action's, below, and no longer the keyboard's.
    enablePinEntry();

    pinView.setOnConfirm(() -> {
      ViewUtil.hideKeyboard(requireContext(), pinView);
      onPinSubmitted();
    });

    pinView.setOnKeyboardToggle(() -> viewModel.toggleKeyboardType());

    initViewModel();

    // First arrival only, as upstream focused the field then; a recreation shows the form as it was
    // left. A notice the observers above raise once they are started closes the editor again.
    if (savedInstanceState == null) {
      pinView.openEditor();
    }
  }

  private void initViewModel() {
    viewModel = new ViewModelProvider(this).get(PinRestoreViewModel.class);

    viewModel.getKeyboardType().observe(getViewLifecycleOwner(), this::applyKeyboardType);

    viewModel.triesRemaining.observe(getViewLifecycleOwner(), this::presentTriesRemaining);
    viewModel.getEvent().observe(getViewLifecycleOwner(), this::presentEvent);
  }

  /**
   * Applies the keyboard type to the credential field and renames the toggle.
   *
   * <p>{@code PinKeyboardType.applyTo} is deliberately not called: its second argument is a
   * {@code MaterialButton} it sets an icon on, and there is no Material button here. Nothing
   * observable is lost but the icon, which the Light grammar replaces with a text action anyway.
   */
  private void applyKeyboardType(@NonNull PinKeyboardType keyboardType) {
    keyboardType.applyInputTypeTo(pinView.getInput());
    pinView.updateKeyboardToggle(keyboardType.toggleLabel(requireContext()));
  }

  private void presentTriesRemaining(PinRestoreViewModel.TriesRemaining triesRemaining) {
    if (triesRemaining.hasIncorrectGuess()) {
      if (triesRemaining.getCount() == 1) {
        showNotice(
            getString(R.string.PinRestoreEntryFragment_incorrect_pin),
            attemptsRemaining(triesRemaining.getCount())
        );
      }

      pinView.updateLabel(getString(R.string.PinRestoreEntryFragment_incorrect_pin), true);
    } else if (triesRemaining.getCount() == 1) {
      showNotice("", attemptsRemaining(triesRemaining.getCount()));
    }

    // Shown once a guess has been spent, or once only one is left. The old code set the button
    // visible in both branches and never hid it again; deriving it each time gives the same result,
    // because the count only ever falls.
    pinView.updateSupport(
        triesRemaining.hasIncorrectGuess() || triesRemaining.getCount() == 1
            ? getString(R.string.PinRestoreEntryFragment_need_help)
            : null,
        this::onNeedHelpClicked
    );

    if (triesRemaining.getCount() == 0) {
      Log.w(TAG, "Account locked. User out of attempts on KBS.");
      onAccountLocked();
    }
  }

  private void presentEvent(@NonNull PinRestoreViewModel.Event event) {
    switch (event) {
      case SUCCESS:
        handleSuccess();
        break;
      case EMPTY_PIN:
        // A toast upstream, and the only feedback this branch gave: the field is cleared and
        // refocused, so nothing stays on screen to explain what just happened.
        Toast.makeText(requireContext(), R.string.RegistrationActivity_you_must_enter_your_registration_lock_PIN, Toast.LENGTH_LONG).show();
        setBusy(false);
        clearInputAndReenable();
        break;
      case PIN_TOO_SHORT:
        Toast.makeText(requireContext(), getString(R.string.RegistrationActivity_your_pin_has_at_least_d_digits_or_characters, MINIMUM_PIN_LENGTH), Toast.LENGTH_LONG).show();
        setBusy(false);
        clearInputAndReenable();
        break;
      case PIN_INCORRECT:
        // Deliberately silent: `triesRemaining` fires alongside this and puts the failure on the
        // status line, so a toast here would say it twice.
        setBusy(false);
        clearInputAndReenable();
        break;
      case PIN_LOCKED:
        onAccountLocked();
        break;
      case NETWORK_ERROR:
        Toast.makeText(requireContext(), R.string.RegistrationActivity_error_connecting_to_service, Toast.LENGTH_LONG).show();
        setBusy(false);
        enablePinEntry();
        break;
    }
  }

  private void onPinSubmitted() {
    pinView.getInput().setEnabled(false);
    viewModel.onPinSubmitted(pinView.getInput().getText().toString());
    setBusy(true);
  }

  private void onNeedHelpClicked() {
    String subject = getString(R.string.PinRestoreEntryFragment_signal_registration_need_help_with_pin);

    showNotice(
        getString(R.string.PinRestoreEntryFragment_need_help),
        getString(R.string.PinRestoreEntryFragment_your_pin_is_a_d_digit_code, SvrConstants.MINIMUM_PIN_LENGTH),
        // Destructive last and separated: creating a new PIN is what unsticks this screen, and
        // contacting support is the ordinary way to ask about it first. Cancelling is the row that
        // does nothing, so it goes first and the row that gives up the old PIN is last.
        List.of(
            new LightConfirmAction(getString(R.string.PinRestoreEntryFragment_cancel), this::clearNotice),
            new LightConfirmAction(getString(R.string.PinRestoreEntryFragment_contact_support), () -> {
              clearNotice();
              CommunicationActions.openEmail(
                  requireContext(),
                  SupportEmailUtil.getSupportEmailAddress(requireContext()),
                  subject,
                  SupportEmailUtil.generateSupportEmailBody(requireContext(),
                                                              R.string.PinRestoreEntryFragment_signal_registration_need_help_with_pin,
                                                              null,
                                                              null)
              );
            }),
            new LightConfirmAction(getString(R.string.PinRestoreEntryFragment_create_new_pin), this::navigateToPinCreation)
        )
    );
  }

  private void onSkipClicked() {
    showNotice(
        getString(R.string.PinRestoreEntryFragment_skip_pin_entry),
        getString(R.string.PinRestoreEntryFragment_if_you_cant_remember_your_pin),
        List.of(
            new LightConfirmAction(getString(R.string.PinRestoreEntryFragment_cancel), this::clearNotice),
            new LightConfirmAction(getString(R.string.PinRestoreEntryFragment_create_new_pin), this::navigateToPinCreation)
        )
    );
  }

  /** Raises the informational notice: a title, a body and one acknowledgement. */
  private void showNotice(@NonNull String title, @NonNull String message) {
    showNotice(title, message, Collections.singletonList(
        new LightConfirmAction(getString(android.R.string.ok), this::clearNotice)
    ));
  }

  private void showNotice(@NonNull String title, @NonNull String message, @NonNull List<LightConfirmAction> actions) {
    pinView.updateNotice(title, message, actions);
    dismissNoticeOnBack.setEnabled(true);
  }

  private void clearNotice() {
    pinView.updateNotice(null, "", Collections.emptyList());
    dismissNoticeOnBack.setEnabled(false);
  }

  private String attemptsRemaining(int count) {
    return getResources().getQuantityString(R.plurals.PinRestoreEntryFragment_you_have_d_attempt_remaining, count, count);
  }

  private void setBusy(boolean busy) {
    pinView.updateConfirm(pinView.getConfirmLabel(), pinView.getConfirmEnabled(), busy);
  }

  private void onAccountLocked() {
    SvrRepository.onPinRestoreForgottenOrSkipped();
    SafeNavigation.safeNavigate(Navigation.findNavController(requireView()), PinRestoreEntryFragmentDirections.actionAccountLocked());
  }

  private void navigateToPinCreation() {
    clearNotice();
    SvrRepository.onPinRestoreForgottenOrSkipped();
    ((PinRestoreActivity) requireActivity()).navigateToPinCreation();
  }

  private void handleSuccess() {
    setBusy(false);
    SignalStore.onboarding().clearAll();

    Activity activity = requireActivity();

    if (RestoreDecisionStateUtil.isDecisionPending(SignalStore.registration().getRestoreDecisionState())) {
      final Intent transferOrRestore = RestoreActivity.getRestoreIntent(activity);
      transferOrRestore.putExtra(PassphraseRequiredActivity.NEXT_INTENT_EXTRA, MainActivity.clearTop(requireContext()));
      startActivity(transferOrRestore);
    } else if (Recipient.self().getProfileName().isEmpty() || !AvatarHelper.hasAvatar(activity, Recipient.self().getId())) {
      final Intent main    = MainActivity.clearTop(activity);
      final Intent profile = CreateProfileActivity.getIntentForUserProfile(activity);

      profile.putExtra("next_intent", main);
      startActivity(profile);
    } else {
      RegistrationUtil.maybeMarkRegistrationComplete();
      AppDependencies.getJobManager().add(new ProfileUploadJob());
      startActivity(MainActivity.clearTop(activity));
    }

    activity.finish();
  }

  /**
   * The rejection paths: drop what was typed and make the field usable again.
   *
   * <p>The form is left showing, with the PIN row emptied and the reason beside it, rather than the
   * editor reopened over it: the reason is what the user needs to read next, and the row is one tap
   * from the keyboard.
   */
  private void clearInputAndReenable() {
    pinView.clearInput();
    enablePinEntry();
  }

  private void enablePinEntry() {
    EditText input = pinView.getInput();
    input.setEnabled(true);
    input.setFocusable(true);
  }
}
