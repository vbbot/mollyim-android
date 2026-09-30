package org.thoughtcrime.securesms.lock.v2;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.pin.PinOptOutDialog;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The PIN migration introduction.
 *
 * <p>The migration activity's root screen, and it stays one: the system back gesture is still
 * swallowed, so the user cannot leave the migration half-finished. That is also why it carries no
 * back affordance -- there is nothing behind it to go back to, and a chevron would promise a
 * destination that does not exist.
 *
 * <p>Presentation is {@link LightSvrSplashView}'s. The two decisions this screen actually makes --
 * registration lock or no registration lock, and therefore which pair of labels to show -- and the
 * conditions on its opt-out entry are unchanged.
 */
public final class SvrSplashFragment extends Fragment {

  private LightSvrSplashView splashView;

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater,
                                     @Nullable ViewGroup container,
                                     @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.kbs_splash_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    splashView = view.findViewById(R.id.light_svr_splash_view);

    splashView.setOnPrimary(this::onCreatePin);
    splashView.setOnSecondary(this::onLearnMore);

    if (SignalStore.svr().isRegistrationLockEnabled()) {
      setUpRegLockEnabled();
    } else {
      setUpRegLockDisabled();
    }

    updateOverflowItems();

    requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
      @Override
      public void handleOnBackPressed() { }
    });
  }

  @Override
  public void onResume() {
    super.onResume();

    // Repeated because the old code re-evaluated this in `onPrepareOptionsMenu`, which runs on
    // every invalidate, and `SignalStore` may have been written to while this screen was stopped.
    updateOverflowItems();
  }

  /**
   * The overflow panel, which is the former {@code R.menu.pin_skip} options menu.
   *
   * <p>Its one gating condition has not moved: the opt-out is not offered while registration lock is
   * enabled, because a registration lock <em>is</em> the PIN and there is nothing left to opt out of.
   */
  private void updateOverflowItems() {
    if (SignalStore.svr().isRegistrationLockEnabled()) {
      splashView.updateOverflowItems(Collections.emptyList());
      return;
    }

    List<LightSvrPinMenuItem> items = new ArrayList<>(2);
    items.add(new LightSvrPinMenuItem(
        getString(R.string.KbsSplashFragment__learn_more_about_pins),
        this::onLearnMore
    ));
    items.add(new LightSvrPinMenuItem(
        getString(R.string.KbsSplashFragment__disable_pin),
        this::onPinSkipped
    ));

    splashView.updateOverflowItems(items);
  }

  private void setUpRegLockEnabled() {
    splashView.updateHeader(
        getString(R.string.KbsSplashFragment__registration_lock_equals_pin),
        getString(R.string.KbsSplashFragment__your_registration_lock_is_now_called_a_pin),
        getString(R.string.KbsSplashFragment__update_pin),
        getString(R.string.KbsSplashFragment__learn_more)
    );
  }

  private void setUpRegLockDisabled() {
    splashView.updateHeader(
        getString(R.string.KbsSplashFragment__introducing_pins),
        getString(R.string.KbsSplashFragment__pins_keep_information_stored_with_signal_encrypted),
        getString(R.string.KbsSplashFragment__create_your_pin),
        getString(R.string.KbsSplashFragment__learn_more)
    );
  }

  private void onCreatePin() {
    SvrSplashFragmentDirections.ActionCreateKbsPin action = SvrSplashFragmentDirections.actionCreateKbsPin();

    action.setIsPinChange(SignalStore.svr().hasPin());

    SafeNavigation.safeNavigate(Navigation.findNavController(requireView()), action);
  }

  private void onLearnMore() {
    CommunicationActions.openBrowserLink(requireContext(), getString(R.string.KbsSplashFragment__learn_more_link));
  }

  private void onPinSkipped() {
    PinOptOutDialog.show(requireContext(), getViewLifecycleOwner(), false, () -> requireActivity().finish());
  }
}
