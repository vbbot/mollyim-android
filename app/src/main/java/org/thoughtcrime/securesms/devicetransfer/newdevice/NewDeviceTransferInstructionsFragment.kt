package org.thoughtcrime.securesms.devicetransfer.newdevice

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import org.greenrobot.eventbus.EventBus
import org.signal.core.ui.logging.LoggingFragment
import org.signal.devicetransfer.TransferStatus
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Shows instructions for new device to being transfer.
 *
 * Drawn in Light by [NewDeviceTransferInstructionsScreen]. CONTINUE navigates to setup exactly as the
 * layout's button did; BACK hands off to system back, so it does whatever back already did here.
 */
class NewDeviceTransferInstructionsFragment : LoggingFragment() {
  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return NewDeviceTransferViews.instructions(
      context = requireContext(),
      onContinue = { findNavController().safeNavigate(R.id.action_device_transfer_setup) },
      onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() }
    )
  }

  override fun onResume() {
    super.onResume()
    EventBus.getDefault().removeStickyEvent(TransferStatus::class.java)
  }
}
