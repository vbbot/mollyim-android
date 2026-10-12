package org.thoughtcrime.securesms.lock.v2;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.ui.logging.LoggingFragment;
import org.thoughtcrime.securesms.PassphraseRequiredActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.pin.PinOptOutDialog;
import org.thoughtcrime.securesms.registration.util.RegistrationUtil;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.text.AfterTextChanged;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The shared base for the two Signal PIN screens -- create and confirm.
 *
 * <p>Presentation is {@link LightSvrPinView}'s: this class no longer finds views by id, and no
 * screen chrome is Material any more. The one exception is stated rather than hidden -- the
 * credential field is still a {@link org.thoughtcrime.securesms.components.NoAutofillEditText}, a
 * {@code TextInputEditText}, because it has to be a real focusable field for the device's system
 * IME. What this class still owns is everything that must not change -- the entry-length gate on
 * the confirm action, the keyboard-type switch, the opt-out menu's gating, and the navigation out of
 * the graph.
 *
 * <p>The field is typed into in {@link LightSvrPinView}'s full-screen editor, and the keyboard's
 * Done closes that editor rather than submitting: the step is submitted only by the form's confirm
 * action, which is never under the keyboard. The editor opens by itself on arrival, as upstream
 * focused the field with the keyboard up.
 *
 * <p><strong>Nothing here may weaken a PIN check.</strong> The Signal PIN is not an app lock; it is
 * the credential that makes a backup restorable after losing the phone. The length gate, the
 * navigation arguments, the `SignalStore` conditions on the opt-out, and the order in which the view
 * model's observers are attached are all load-bearing and are reproduced exactly.
 */
public abstract class BaseSvrPinFragment<ViewModel extends BaseSvrPinViewModel> extends LoggingFragment {

  /**
   * The screen, as Light components.
   *
   * <p>Protected because both subclasses drive it: they set the header, the status line and the
   * field's own state, exactly as they used to assign fields looked up by id.
   */
  protected LightSvrPinView pinView;

  private ViewModel viewModel;

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater,
                                     @Nullable ViewGroup container,
                                     @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.base_kbs_pin_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    pinView = view.findViewById(R.id.light_svr_pin_view);
    pinView.updateConfirm(getString(R.string.BaseKbsPinFragment__next), false, false);

    // Runs before the view model exists, exactly as `initializeViews` used to: the subclass sets
    // the header and the status line here, and the observers below are what ultimately settle the
    // confirm action's liveness.
    initializeViewStates();

    viewModel = initializeViewModel();

    viewModel.getUserEntry().observe(getViewLifecycleOwner(), svrPin -> {
      // Same gate as before, and the same ordering consequence: this observer is attached after
      // the subclass's own setup, so on first delivery it is what decides the confirm action's
      // state, not whatever `initializeViewStates` set.
      setConfirmEnabled(isEntryLongEnough(svrPin));
    });

    viewModel.getKeyboard().observe(getViewLifecycleOwner(), this::applyKeyboardType);

    pinView.setOnLearnMore(() ->
        CommunicationActions.openBrowserLink(requireContext(), getString(R.string.BaseKbsPinFragment__learn_more_url)));
    pinView.setOnBack(this::navigateBack);
    pinView.updateBackContentDescription(getBackContentDescription());

    updateOverflowItems();

    initializeListeners();

    // First arrival only -- not on a recreation, where the form is shown as it was left -- and a
    // no-op if anything already holds the screen. Opening the editor is what focuses the field and
    // raises the Light Phone's system IME, as the focus request in `onResume` used to.
    if (savedInstanceState == null) {
      pinView.openEditor();
    }
  }

  @Override
  public void onResume() {
    super.onResume();

    // Repeated because the old code re-evaluated this in `onPrepareOptionsMenu`, which runs on
    // every invalidate, and `SignalStore` may have been written to while this screen was stopped.
    updateOverflowItems();
  }

  /**
   * The overflow panel, rebuilt whenever the screen resumes.
   *
   * <p>This is the former {@code R.menu.pin_skip} options menu, and the conditions that decided
   * whether it had any items have not moved: both entries disappear once a PIN exists, the user has
   * opted out, or registration lock is enabled. The evaluation is repeated on resume because the
   * old code ran it in {@code onPrepareOptionsMenu}, which is called more than once.
   */
  private void updateOverflowItems() {
    if (SignalStore.svr().isRegistrationLockEnabled() ||
        SignalStore.svr().hasPin() ||
        SignalStore.svr().hasOptedOut())
    {
      pinView.updateOverflowItems(Collections.emptyList());
      return;
    }

    List<LightSvrPinMenuItem> items = new ArrayList<>(2);
    items.add(new LightSvrPinMenuItem(
        getString(R.string.KbsSplashFragment__learn_more_about_pins),
        () -> CommunicationActions.openBrowserLink(requireContext(), getString(R.string.KbsSplashFragment__learn_more_link))
    ));
    items.add(new LightSvrPinMenuItem(
        getString(R.string.KbsSplashFragment__disable_pin),
        this::onPinSkipped
    ));

    pinView.updateOverflowItems(items);
  }

  protected abstract ViewModel initializeViewModel();

  protected abstract void initializeViewStates();

  /**
   * What this screen's back affordance is <em>for</em>, in words.
   *
   * A back icon whose content description is just "back" tells a screen-reader user nothing they can
   * act on, so it is required rather than defaulted, and each screen names its own destination:
   * this one steps back to the PIN introduction it was pushed from, the confirm screen steps back to
   * the screen the PIN was typed on.
   */
  protected @NonNull String getBackContentDescription() {
    return getString(R.string.BaseKbsPinFragment__back_to_pin_introduction);
  }

  /** The credential field. */
  protected EditText getInput() {
    return pinView.getInput();
  }

  protected LightSvrPinView getPinView() {
    return pinView;
  }

  protected void closeNavGraphBranch() {
    Intent activityIntent = requireActivity().getIntent();
    if (activityIntent != null && activityIntent.hasExtra(PassphraseRequiredActivity.NEXT_INTENT_EXTRA)) {
      final Intent nextIntent = activityIntent.getParcelableExtra(PassphraseRequiredActivity.NEXT_INTENT_EXTRA);
      if (nextIntent != null) {
        startActivity(nextIntent);
      }
    }

    requireActivity().finish();
  }

  private void initializeListeners() {
    getInput().addTextChangedListener(new AfterTextChanged(s -> viewModel.setUserEntry(s.toString())));

    // The field and the entry start out agreeing. The field is a new, empty `EditText` whenever this
    // view is created -- including on the way back from the confirm screen -- while the view model
    // outlives it, and the watcher above only reports changes. Without this the confirm action could
    // be live, and the view model holding a PIN, over a field the form shows as empty.
    viewModel.setUserEntry(getInput().getText().toString());

    // The IME action is `LightSvrPinView`'s: Done closes the editor and keeps the entry. It no longer
    // submits the step, which is the form's confirm action's to do.
    pinView.setOnKeyboardToggle(() -> viewModel.toggleAlphaNumeric());
    pinView.setOnConfirm(() -> viewModel.confirm());
  }

  /**
   * Applies a keyboard type to the credential field and updates the toggle's label.
   *
   * <p>{@code PinKeyboardType.applyTo} is deliberately not called: its second argument is a
   * {@code MaterialButton} it sets an icon on, and there is no Material button here. The label was
   * always set separately, immediately after, so nothing observable is lost but the icon -- which
   * the Light design language replaces with a text action anyway.
   *
   * <p>Note this clears the field when the input <em>class</em> changes (numeric to text or back).
   * That is {@code PinKeyboardType}'s behaviour and it is preserved: switching keyboards must not
   * carry a half-numeric PIN across.
   */
  private void applyKeyboardType(@NonNull PinKeyboardType keyboardType) {
    keyboardType.applyInputTypeTo(getInput());
    pinView.updateKeyboardToggle(keyboardType.toggleLabel(requireContext()));
  }

  /**
   * Whether {@code userEntry} is long enough to submit.
   *
   * <p>The Signal PIN is the credential that makes a backup restorable after losing the phone, so
   * this is the one line on these screens that must not drift. It counts <em>characters</em>, not
   * digits: a PIN may be alphanumeric ({@link PinKeyboardType#ALPHA_NUMERIC}), and a four-character
   * alphanumeric PIN is four characters long. {@link SvrPin#from} trims first, so a stray space is
   * not a character the user gets to count, and a null entry is {@link SvrPin#EMPTY} rather than a
   * pass.
   *
   * <p>Package-private so it can be pinned by a test; it is called from exactly one place, the
   * {@code userEntry} observer above, which is attached <em>after</em> {@link
   * #initializeViewStates()}. That ordering is deliberate: it is what makes the flag a function of
   * what is in the field rather than something a screen's own setup can leave stuck on.
   */
  static boolean isEntryLongEnough(@NonNull SvrPin userEntry) {
    return userEntry.length() >= SvrConstants.MINIMUM_PIN_LENGTH;
  }

  /** Sets the confirm action's liveness without disturbing whether it is in flight. */
  protected void setConfirmEnabled(boolean enabled) {
    pinView.updateConfirm(pinView.getConfirmLabel(), enabled, pinView.getBusy());
  }

  /** Sets the confirm action's in-flight state without disturbing whether it is live. */
  protected void setConfirmBusy(boolean busy) {
    pinView.updateConfirm(pinView.getConfirmLabel(), pinView.getConfirmEnabled(), busy);
  }

  /**
   * Back means back: pops this screen off the graph, or leaves the activity when there is nothing
   * behind it. Routed through the activity's dispatcher so it lands on exactly the behaviour the
   * system back gesture had.
   */
  private void navigateBack() {
    requireActivity().getOnBackPressedDispatcher().onBackPressed();
  }

  private void onPinSkipped() {
    PinOptOutDialog.show(requireContext(), getViewLifecycleOwner(), false, () -> {
      RegistrationUtil.maybeMarkRegistrationComplete();
      closeNavGraphBranch();
    });
  }
}
