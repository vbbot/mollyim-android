package org.thoughtcrime.securesms.devicetransfer.newdevice;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.signal.core.ui.logging.LoggingFragment;
import org.thoughtcrime.securesms.restore.RestoreActivity;

/**
 * Shown after the new device successfully completes receiving a backup from the old device.
 */
public final class NewDeviceTransferCompleteFragment extends LoggingFragment {
  public NewDeviceTransferCompleteFragment() {
    super();
  }

  /** Drawn in Light by {@link NewDeviceTransferScreensKt#NewDeviceTransferCompleteScreen}. */
  @Override
  public @NonNull View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return NewDeviceTransferViews.complete(requireContext(), () -> ((RestoreActivity) requireActivity()).onBackupCompletedSuccessfully());
  }

  @Override
  public void onActivityCreated(@Nullable Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
      @Override
      public void handleOnBackPressed() { }
    });
  }
}
