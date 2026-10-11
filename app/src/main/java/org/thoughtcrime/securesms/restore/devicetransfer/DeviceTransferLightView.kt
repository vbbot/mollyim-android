/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore.devicetransfer

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import com.thelightphone.sdk.ui.LightBarButton
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.registration.ui.restore.local.LightRestoreNotice
import org.thoughtcrime.securesms.registration.ui.restore.local.LightRestoreNoticeAction
import org.thoughtcrime.securesms.registration.ui.restore.local.LightRestoreProgress
import java.util.Locale

/**
 * The transfer-in-progress screen the new device shows, as a Light view [DeviceTransferFragment] can
 * host in place of `fragment_device_transfer.xml`.
 *
 * It decides nothing. The fragment tells it the status line, when the transfer has failed, and when
 * to ask about stopping, and hands it the callbacks the layout's buttons ran. System back stays with
 * the fragment's own callback throughout, except while the stop confirmation is up, which takes back
 * to close itself as its dialog window did.
 */
class DeviceTransferLightView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs) {

  private var status: String by mutableStateOf("")
  private var failure: Failure? by mutableStateOf(null)
  private var stopConfirmation: Runnable? by mutableStateOf(null)

  /** What STOP TRANSFER in the bottom bar does while the transfer is live. */
  var onStopRequested: Runnable? = null

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setBackgroundColor(Color.BLACK)
  }

  fun setStatus(status: CharSequence) {
    this.status = status.toString()
  }

  /** The layout's abort state: the title became "Unable to transfer" with TRY AGAIN and CANCEL. */
  fun showFailure(message: CharSequence, onTryAgain: Runnable, onCancel: Runnable) {
    failure = Failure(message.toString(), onTryAgain, onCancel)
  }

  /** The "stop transfer?" confirmation. [onStop] runs only if the user confirms. */
  fun confirmStop(onStop: Runnable) {
    stopConfirmation = onStop
  }

  @Composable
  override fun Content() {
    DeviceTransferContent(
      status = status,
      failure = failure,
      stopConfirmation = stopConfirmation,
      onStopRequested = { onStopRequested?.run() },
      onCloseStopConfirmation = { stopConfirmation = null }
    )
  }

  data class Failure(val message: String, val onTryAgain: Runnable, val onCancel: Runnable)
}

@Composable
fun DeviceTransferContent(
  status: String,
  failure: DeviceTransferLightView.Failure?,
  stopConfirmation: Runnable?,
  onStopRequested: () -> Unit,
  onCloseStopConfirmation: () -> Unit
) {
  Box(modifier = Modifier.fillMaxSize()) {
    if (failure == null) {
      LightRestoreProgress(
        title = stringResource(R.string.DeviceTransfer__transferring_data),
        prose = stringResource(R.string.DeviceTransfer__keep_both_devices_near_each_other),
        // Empty until the first count arrives, under what was a bare spinner.
        status = status.ifEmpty { "…" },
        actions = listOf(
          LightBarButton.Text(
            text = stringResource(R.string.DeviceTransfer__stop_transfer).uppercase(Locale.getDefault()),
            onClick = onStopRequested
          )
        )
      )
    } else {
      // Back stays with the fragment, which by now navigates away -- the same as CANCEL.
      LightRestoreNotice(
        title = stringResource(R.string.DeviceTransfer__unable_to_transfer),
        message = failure.message,
        actions = listOf(
          LightRestoreNoticeAction(stringResource(R.string.DeviceTransfer__cancel)) { failure.onCancel.run() },
          LightRestoreNoticeAction(stringResource(R.string.DeviceTransfer__try_again)) { failure.onTryAgain.run() }
        ),
        ownsSystemBack = false
      )
    }

    if (stopConfirmation != null) {
      LightRestoreNotice(
        title = stringResource(R.string.DeviceTransfer__stop_transfer),
        message = stringResource(R.string.DeviceTransfer__all_transfer_progress_will_be_lost),
        actions = listOf(
          LightRestoreNoticeAction(stringResource(android.R.string.cancel), onCloseStopConfirmation),
          LightRestoreNoticeAction(stringResource(R.string.DeviceTransfer__stop_transfer)) {
            onCloseStopConfirmation()
            stopConfirmation.run()
          }
        ),
        onBack = onCloseStopConfirmation,
        backDescription = stringResource(R.string.LightRestore__back_to_transfer)
      )
    }
  }
}
