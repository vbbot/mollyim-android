package org.thoughtcrime.securesms.pin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.ui.logging.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.lock.v2.LightSvrSplashView;
import org.thoughtcrime.securesms.util.CommunicationActions;

/**
 * "You have run out of PIN guesses" -- the end of the PIN restore flow.
 *
 * <p>Two statements and two ways on, and nothing else. Presentation is {@link LightSvrSplashView}'s,
 * the same view the PIN migration introduction uses, because it is the same shape: a title, a
 * paragraph, and a primary and secondary action.
 *
 * <p>There is no back affordance and no overflow. The screen before it is the one that ran out of
 * attempts, so backing out of this one lands the user where there is nothing left to do, and the
 * overflow it used to inherit from the activity's options menu carried the same two rows this view
 * now draws.
 */
public class PinRestoreLockedFragment extends LoggingFragment {

  private LightSvrSplashView splashView;

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.pin_restore_locked_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    splashView = view.findViewById(R.id.light_svr_splash_view);

    splashView.updateHeader(
        getString(R.string.PinRestoreLockedFragment_create_your_pin),
        getString(R.string.PinRestoreLockedFragment_youve_run_out_of_pin_guesses),
        getString(R.string.PinRestoreLockedFragment_create_new_pin),
        getString(R.string.AccountLockedFragment__learn_more)
    );

    splashView.setOnPrimary(this::onCreatePin);
    splashView.setOnSecondary(this::onLearnMore);
  }

  private void onCreatePin() {
    SvrRepository.onPinRestoreForgottenOrSkipped();
    ((PinRestoreActivity) requireActivity()).navigateToPinCreation();
  }

  private void onLearnMore() {
    CommunicationActions.openBrowserLink(requireContext(), getString(R.string.PinRestoreLockedFragment_learn_more_url));
  }
}