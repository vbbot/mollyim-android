/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore.devicetransfer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.ui.logging.LoggingFragment
import org.signal.devicetransfer.DeviceToDeviceTransferService
import org.signal.devicetransfer.TransferStatus
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.FragmentDeviceTransferBinding
import org.thoughtcrime.securesms.util.visible

/**
 * Drives the UI for the actual device transfer progress. Shown after setup is complete
 * and the two devices are transferring.
 * <p>
 * Handles show progress and error state.
 */
abstract class DeviceTransferFragment : LoggingFragment(R.layout.fragment_device_transfer) {

  companion object {
    private const val TRANSFER_FINISHED_KEY = "transfer_finished"
  }

  private val onBackPressed = OnBackPressed()
  private val transferModeListener = TransferModeListener()
  protected val binding: FragmentDeviceTransferBinding by ViewBinderDelegate(FragmentDeviceTransferBinding::bind)
  protected val status: TextView by lazy { binding.deviceTransferFragmentStatus }

  protected var transferFinished: Boolean = false

  /** Set when this side draws [DeviceTransferLightView] instead of the layout; see [usesLightUi]. */
  private var lightView: DeviceTransferLightView? = null

  /**
   * Whether this side draws the Light transfer screen. The new device does; the old device -- the
   * sending side, under Settings -- keeps the layout, and with it [binding] and [status].
   */
  protected open val usesLightUi: Boolean
    get() = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState != null) {
      transferFinished = savedInstanceState.getBoolean(TRANSFER_FINISHED_KEY)
    }
  }

  override fun onStart() {
    super.onStart()
    if (transferFinished) {
      navigateToTransferComplete()
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBoolean(TRANSFER_FINISHED_KEY, transferFinished)
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
    return if (usesLightUi) {
      DeviceTransferLightView(requireContext()).also { lightView = it }
    } else {
      super.onCreateView(inflater, container, savedInstanceState)
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    val lightView = lightView
    if (lightView != null) {
      lightView.onStopRequested = Runnable { cancelActiveTransfer() }
    } else {
      binding.deviceTransferFragmentCancel.setOnClickListener {
        cancelActiveTransfer()
      }
      binding.deviceTransferFragmentTryAgain.setOnClickListener {
        restartTransfer()
      }
    }

    EventBus.getDefault().register(transferModeListener)

    requireActivity().onBackPressedDispatcher.addCallback(
      viewLifecycleOwner,
      onBackPressed
    )
  }

  override fun onDestroyView() {
    requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    EventBus.getDefault().unregister(transferModeListener)
    lightView = null
    super.onDestroyView()
  }

  /**
   * The progress line under the transfer. Subclasses set it through here rather than through
   * [status], which only exists while the layout is drawn.
   */
  protected fun setStatusText(text: CharSequence) {
    val lightView = lightView
    if (lightView != null) {
      lightView.setStatus(text)
    } else {
      status.text = text
    }
  }

  private fun restartTransfer() {
    EventBus.getDefault().unregister(transferModeListener)
    EventBus.getDefault().removeStickyEvent(TransferStatus::class.java)
    navigateToRestartTransfer()
  }

  private fun cancelActiveTransfer() {
    val stopTransfer = Runnable {
      EventBus.getDefault().unregister(transferModeListener)
      DeviceToDeviceTransferService.stop(requireContext())
      EventBus.getDefault().removeStickyEvent(TransferStatus::class.java)
      navigateAwayFromTransfer()
    }

    val lightView = lightView
    if (lightView != null) {
      lightView.confirmStop(stopTransfer)
      return
    }

    MaterialAlertDialogBuilder(requireContext()).apply {
      setTitle(R.string.DeviceTransfer__stop_transfer)
      setMessage(R.string.DeviceTransfer__all_transfer_progress_will_be_lost)
      setPositiveButton(R.string.DeviceTransfer__stop_transfer) { _, _ -> stopTransfer.run() }
      setNegativeButton(android.R.string.cancel, null)
    }
      .show()
  }

  protected fun ignoreTransferStatusEvents() {
    EventBus.getDefault().unregister(transferModeListener)
  }

  protected abstract fun navigateToRestartTransfer()

  protected abstract fun navigateAwayFromTransfer()

  protected abstract fun navigateToTransferComplete()

  protected fun abort() {
    abort(R.string.DeviceTransfer__transfer_failed)
  }

  protected fun abort(@StringRes errorMessage: Int) {
    EventBus.getDefault().unregister(transferModeListener)
    DeviceToDeviceTransferService.stop(requireContext())

    val lightView = lightView
    if (lightView != null) {
      lightView.showFailure(
        message = getString(errorMessage),
        onTryAgain = Runnable { restartTransfer() },
        onCancel = Runnable { navigateAwayFromTransfer() }
      )
    } else {
      binding.deviceTransferFragmentProgress.visible = false
      binding.deviceTransferFragmentAlert.visible = true
      binding.deviceTransferFragmentTryAgain.visible = true

      binding.deviceTransferFragmentTitle.setText(R.string.DeviceTransfer__unable_to_transfer)
      binding.deviceTransferFragmentStatus.setText(errorMessage)
      binding.deviceTransferFragmentCancel.setText(R.string.DeviceTransfer__cancel)
      binding.deviceTransferFragmentCancel.setOnClickListener { navigateAwayFromTransfer() }
    }

    onBackPressed.isActiveTransfer = false
  }

  private inner class TransferModeListener {
    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: TransferStatus) {
      if (event.transferMode != TransferStatus.TransferMode.SERVICE_CONNECTED) {
        abort()
      }
    }
  }

  protected inner class OnBackPressed : OnBackPressedCallback(true) {
    internal var isActiveTransfer = true

    override fun handleOnBackPressed() {
      if (isActiveTransfer) {
        cancelActiveTransfer()
      } else {
        navigateAwayFromTransfer()
      }
    }
  }
}
