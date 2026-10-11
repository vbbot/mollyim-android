/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.devicetransfer

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.LightTextAction
import org.thoughtcrime.securesms.registration.ui.restore.local.LightRestoreNotice
import org.thoughtcrime.securesms.registration.ui.restore.local.LightRestoreNoticeAction
import org.thoughtcrime.securesms.registration.ui.restore.local.LightRestoreProgress
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationAction
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationBack
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationBottomActions
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen

const val DEVICE_TRANSFER_SAS_CODE_TAG = "device-transfer-sas-code"

/**
 * What [DeviceTransferSetupFragment] draws, separated from what it decides.
 *
 * The fragment's state machine -- every [SetupStep], the permission, location, Wi-Fi and Wi-Fi Direct
 * checks, the taking-too-long timers and the service calls -- stays in the fragment. It only tells
 * one of these what to show, and hands it the exact `Runnable` each button used to run. There are two
 * implementations: the original XML layout, which the old device (the sending side, under Settings)
 * still uses unchanged, and [DeviceTransferSetupLightView], which the new device uses.
 */
interface DeviceTransferSetupUi {
  /** Called first for every step, as the layout's group visibilities were. */
  fun showStep(step: SetupStep)

  /** The progress steps' status line. Survives across steps, as the layout's text view did. */
  fun setStatus(status: CharSequence)

  fun showError(message: CharSequence, actionLabel: CharSequence, onAction: Runnable)

  fun showVerify(code: String, onContinue: Runnable, onNumbersDoNotMatch: Runnable)

  fun showTroubleshooting(
    title: CharSequence,
    onLocationPermission: Runnable,
    onLocationServices: Runnable,
    onWifi: Runnable,
    onWifiDirect: Runnable,
    onSupport: Runnable,
    onTryAgain: Runnable
  )

  /** The "numbers do not match" confirmation. Cancel (and back) only closes it. */
  fun confirmNumbersDoNotMatch(onStopTransfer: Runnable)

  /** The non-cancelable "error connecting" alert over the ERROR step. Every action closes it first. */
  fun showErrorConnecting(message: CharSequence, onRetry: Runnable, onCancel: Runnable, onSubmitDebugLogs: Runnable)
}

/** The setup screen the new device shows, as a Light Compose view the Java fragment can host. */
class DeviceTransferSetupLightView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs), DeviceTransferSetupUi {

  /** What BACK in the top bar does. The fragment points it at the activity's back dispatcher. */
  var onBackRequested: Runnable? = null

  private var screen: SetupScreen by mutableStateOf(SetupScreen.Progress)
  private var status: String by mutableStateOf("")
  private var notice: SetupNotice? by mutableStateOf(null)

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    setBackgroundColor(Color.BLACK)
  }

  override fun showStep(step: SetupStep) {
    when {
      step.isProgress -> screen = SetupScreen.Progress
      step == SetupStep.WAITING_FOR_OTHER_TO_VERIFY -> screen = SetupScreen.WaitingForOther
      // Error, verify and troubleshooting screens arrive with their content through their own calls.
      else -> Unit
    }
  }

  override fun setStatus(status: CharSequence) {
    this.status = status.toString()
  }

  override fun showError(message: CharSequence, actionLabel: CharSequence, onAction: Runnable) {
    screen = SetupScreen.Error(message.toString(), actionLabel.toString(), onAction)
  }

  override fun showVerify(code: String, onContinue: Runnable, onNumbersDoNotMatch: Runnable) {
    screen = SetupScreen.Verify(code, onContinue, onNumbersDoNotMatch)
  }

  override fun showTroubleshooting(
    title: CharSequence,
    onLocationPermission: Runnable,
    onLocationServices: Runnable,
    onWifi: Runnable,
    onWifiDirect: Runnable,
    onSupport: Runnable,
    onTryAgain: Runnable
  ) {
    screen = SetupScreen.Troubleshooting(title.toString(), onLocationPermission, onLocationServices, onWifi, onWifiDirect, onSupport, onTryAgain)
  }

  override fun confirmNumbersDoNotMatch(onStopTransfer: Runnable) {
    notice = SetupNotice.NumbersDoNotMatch(onStopTransfer)
  }

  override fun showErrorConnecting(message: CharSequence, onRetry: Runnable, onCancel: Runnable, onSubmitDebugLogs: Runnable) {
    notice = SetupNotice.ErrorConnecting(message.toString(), onRetry, onCancel, onSubmitDebugLogs)
  }

  @Composable
  override fun Content() {
    DeviceTransferSetupContent(
      screen = screen,
      status = status,
      notice = notice,
      onBack = { onBackRequested?.run() },
      onCloseNotice = { notice = null }
    )
  }
}

/** The step-specific screen under any notice. */
sealed interface SetupScreen {
  data object Progress : SetupScreen
  data object WaitingForOther : SetupScreen
  data class Error(val message: String, val actionLabel: String, val onAction: Runnable) : SetupScreen
  data class Verify(val code: String, val onContinue: Runnable, val onNumbersDoNotMatch: Runnable) : SetupScreen
  data class Troubleshooting(
    val title: String,
    val onLocationPermission: Runnable,
    val onLocationServices: Runnable,
    val onWifi: Runnable,
    val onWifiDirect: Runnable,
    val onSupport: Runnable,
    val onTryAgain: Runnable
  ) : SetupScreen
}

/** The two alerts the setup raised as Material dialogs, independent of the step, as windows were. */
sealed interface SetupNotice {
  data class NumbersDoNotMatch(val onStopTransfer: Runnable) : SetupNotice
  data class ErrorConnecting(val message: String, val onRetry: Runnable, val onCancel: Runnable, val onSubmitDebugLogs: Runnable) : SetupNotice
}

/**
 * The new device's setup screens.
 *
 * System back belongs to the fragment's own callback (stop the service and pop) on every screen, so
 * the screens here never install a back handler; their BACK glyph runs [onBack], which the fragment
 * routes through that same callback. The one exception is a notice, which takes back while it is up
 * exactly as its dialog window did.
 */
@Composable
fun DeviceTransferSetupContent(
  screen: SetupScreen,
  status: String,
  notice: SetupNotice?,
  onBack: () -> Unit,
  onCloseNotice: () -> Unit
) {
  val title = stringResource(R.string.OldDeviceTransferInstructions__transfer_account)
  val backDescription = stringResource(R.string.RegistrationScreen__back_to_previous_screen)
  val back = LightBarButton.LightIcon(
    icon = LightIcons.BACK,
    onClick = onBack,
    contentDescription = backDescription
  )

  Box(modifier = Modifier.fillMaxSize()) {
    when (screen) {
      SetupScreen.Progress -> {
        // The layout showed a bare spinner over an empty status at first; the ellipsis is that.
        LightRestoreProgress(
          title = title,
          status = status.ifEmpty { "…" },
          back = back
        )
      }

      SetupScreen.WaitingForOther -> {
        LightRestoreProgress(
          title = stringResource(R.string.DeviceTransferSetup__waiting_for_other_device),
          prose = stringResource(R.string.DeviceTransferSetup__tap_continue_on_your_other_device_to_start_the_transfer),
          status = stringResource(R.string.DeviceTransferSetup__tap_continue_on_your_other_device),
          back = back
        )
      }

      is SetupScreen.Error -> {
        LightRestoreNotice(
          title = title,
          message = screen.message,
          actions = listOf(LightRestoreNoticeAction(screen.actionLabel) { screen.onAction.run() }),
          onBack = onBack,
          backDescription = backDescription,
          ownsSystemBack = false
        )
      }

      is SetupScreen.Verify -> VerifyCodeScreen(screen, onBack, backDescription)

      is SetupScreen.Troubleshooting -> TroubleshootingScreen(screen, title, onBack, backDescription)
    }

    when (notice) {
      is SetupNotice.NumbersDoNotMatch -> {
        LightRestoreNotice(
          title = stringResource(R.string.DeviceTransferSetup__the_numbers_do_not_match),
          message = stringResource(R.string.DeviceTransferSetup__if_the_numbers_on_your_devices_do_not_match_its_possible_you_connected_to_the_wrong_device),
          actions = listOf(
            LightRestoreNoticeAction(stringResource(android.R.string.cancel), onCloseNotice),
            LightRestoreNoticeAction(stringResource(R.string.DeviceTransferSetup__stop_transfer)) {
              onCloseNotice()
              notice.onStopTransfer.run()
            }
          ),
          onBack = onCloseNotice,
          backDescription = stringResource(R.string.LightRestore__back_to_verify_code)
        )
      }

      is SetupNotice.ErrorConnecting -> {
        // Non-cancelable upstream: back is swallowed and only the three choices leave it. They are
        // listed in the Material dialog's left-to-right order: neutral, negative, positive.
        LightRestoreNotice(
          title = stringResource(R.string.DeviceTransferSetup__error_connecting),
          message = notice.message,
          actions = listOf(
            LightRestoreNoticeAction(stringResource(R.string.DeviceTransferSetup__submit_debug_logs)) {
              onCloseNotice()
              notice.onSubmitDebugLogs.run()
            },
            LightRestoreNoticeAction(stringResource(android.R.string.cancel)) {
              onCloseNotice()
              notice.onCancel.run()
            },
            LightRestoreNoticeAction(stringResource(R.string.DeviceTransferSetup__retry)) {
              onCloseNotice()
              notice.onRetry.run()
            }
          ),
          onBack = null
        )
      }

      null -> Unit
    }
  }
}

/**
 * The verification-code comparison. The code is the screen's content, so it is the one large line:
 * Heading in monospace, centred, never wrapped. CONTINUE confirms the match; THE NUMBERS DO NOT
 * MATCH raises its confirmation and is listed last, as the consequential choice.
 */
@Composable
private fun VerifyCodeScreen(
  screen: SetupScreen.Verify,
  onBack: () -> Unit,
  backDescription: String
) {
  RegistrationScreen(
    title = stringResource(R.string.DeviceTransferSetup__verify_code),
    subtitle = stringResource(R.string.DeviceTransferSetup__verify_that_the_code_below_matches_on_both_of_your_devices),
    bottomContent = {
      Column(modifier = Modifier.padding(horizontal = 2f.gridUnitsAsDp())) {
        LightTextAction(
          label = stringResource(R.string.DeviceTransferSetup__continue),
          onClick = screen.onContinue,
          align = Alignment.Center
        )
        LightTextAction(
          label = stringResource(R.string.DeviceTransferSetup__the_numbers_do_not_match),
          onClick = screen.onNumbersDoNotMatch,
          align = Alignment.Center
        )
      }
    },
    back = RegistrationBack(contentDescription = backDescription, onBack = onBack)
  ) {
    LightText(
      text = screen.code,
      variant = LightTextVariant.Heading,
      monospace = true,
      maxLines = 1,
      align = TextAlign.Center,
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 1f.gridUnitsAsDp())
        .testTag(DEVICE_TRANSFER_SAS_CODE_TAG)
    )
  }
}

/**
 * The troubleshooting checklist. Each of the layout's links is a row naming where it goes, and the
 * Wi-Fi Direct step's inline link becomes its own row under the step, since `LightText` carries no
 * spans. TRY AGAIN is the one bottom action; the support page is a row, because its label is too long
 * to share the bar.
 */
@Composable
private fun TroubleshootingScreen(
  screen: SetupScreen.Troubleshooting,
  title: String,
  onBack: () -> Unit,
  backDescription: String
) {
  RegistrationScreen(
    title = title,
    subtitle = screen.title,
    bottomContent = {
      RegistrationBottomActions(
        center = RegistrationAction(stringResource(R.string.DeviceTransferSetup__try_again), { screen.onTryAgain.run() })
      )
    },
    back = RegistrationBack(contentDescription = backDescription, onBack = onBack)
  ) {
    TroubleshootingStep(stringResource(R.string.DeviceTransferSetup__make_sure_the_following_permissions_are_enabled))
    TroubleshootingLink(stringResource(R.string.DeviceTransferSetup__location_permission), screen.onLocationPermission)
    TroubleshootingLink(stringResource(R.string.DeviceTransferSetup__location_services), screen.onLocationServices)
    TroubleshootingLink(stringResource(R.string.DeviceTransferSetup__wifi), screen.onWifi)

    TroubleshootingStep(stringResource(R.string.DeviceTransferSetup__on_the_wifi_direct_screen_remove_all_remembered_groups_and_unlink_any_invited_or_connected_devices))
    TroubleshootingLink(stringResource(R.string.DeviceTransferSetup__wifi_direct_screen), screen.onWifiDirect)

    TroubleshootingStep(stringResource(R.string.DeviceTransferSetup__try_turning_wifi_off_and_on_on_both_devices))
    TroubleshootingStep(stringResource(R.string.DeviceTransferSetup__make_sure_both_devices_are_in_transfer_mode))

    TroubleshootingLink(stringResource(R.string.DeviceTransferSetup__go_to_support_page), screen.onSupport)
  }
}

@Composable
private fun TroubleshootingStep(text: String) {
  LightText(
    text = text,
    variant = LightTextVariant.Copy,
    modifier = Modifier.padding(top = 0.75f.gridUnitsAsDp(), bottom = 0.25f.gridUnitsAsDp())
  )
}

@Composable
private fun TroubleshootingLink(label: String, onClick: Runnable) {
  LightText(
    text = label,
    variant = LightTextVariant.Heading,
    modifier = Modifier
      .fillMaxWidth()
      .semantics { role = Role.Button }
      .lightClickable { onClick.run() }
      .padding(vertical = 0.5f.gridUnitsAsDp())
  )
}
