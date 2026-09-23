/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.signal.core.ui.compose.NightPreview
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.TriggerAlignedPopupState.Companion.popupTrigger
import org.signal.core.ui.compose.TriggerAlignedPopupState.Companion.rememberTriggerAlignedPopupState
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.webrtc.CallParticipantsState
import org.thoughtcrime.securesms.components.webrtc.ToggleButtonOutputState
import org.thoughtcrime.securesms.components.webrtc.WebRtcAudioOutput
import org.thoughtcrime.securesms.components.webrtc.WebRtcControls
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.light.rememberLightTintedPainter
import org.thoughtcrime.securesms.util.RemoteConfig

/**
 * Renders the button strip / start call button in the call screen
 * bottom sheet.
 */
@Composable
fun CallControls(
  displayVideoTooltip: Boolean,
  callControlsState: CallControlsState,
  callScreenControlsListener: CallScreenControlsListener,
  callScreenSheetDisplayListener: CallScreenSheetDisplayListener,
  additionalActionsState: AdditionalActionsState,
  audioOutputPickerController: AudioOutputPickerController,
  modifier: Modifier = Modifier
) {
  val density = LocalDensity.current
  val padBottom = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
  var bottom by remember { mutableStateOf(padBottom) }
  if (padBottom != 0.dp) bottom = padBottom

  val context = LocalContext.current
  val hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
  val hasRecordAudioPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
  val startCallText = if (callControlsState.displayStartCallButton) stringResource(callControlsState.startCallButtonText) else null
  val presentation = remember(callControlsState, hasCameraPermission, hasRecordAudioPermission, startCallText) {
    LightCallControlsPresentation.from(
      state = callControlsState,
      hasCameraPermission = hasCameraPermission,
      hasRecordAudioPermission = hasRecordAudioPermission,
      startCallText = startCallText
    )
  }
  val callbacks = remember(callScreenControlsListener, callScreenSheetDisplayListener, audioOutputPickerController) {
    LightCallControlCallbacks(
      onAudioOutput = audioOutputPickerController::show,
      onVideoChanged = callScreenControlsListener::onVideoChanged,
      onMicrophoneChanged = callScreenControlsListener::onMicChanged,
      onGroupRingChanged = callScreenControlsListener::onRingGroupChanged,
      onMore = callScreenControlsListener::onOverflowClicked,
      onEndCall = callScreenControlsListener::onEndCallPressed,
      onStartCall = callScreenControlsListener::onStartCall,
      onVideoTooltipDismissed = callScreenSheetDisplayListener::onVideoTooltipDismissed
    )
  }

  val currentOutput = audioOutputPickerController.outputState.currentDevice
  val audioIconRes = remember(currentOutput, audioOutputPickerController.willDisplayPicker) {
    if (!audioOutputPickerController.willDisplayPicker && currentOutput == WebRtcAudioOutput.HANDSET) {
      WebRtcAudioOutput.SPEAKER.iconRes
    } else {
      currentOutput.iconRes
    }
  }

  MollyLightTheme {
    LightCallControls(
      presentation = presentation,
      callbacks = callbacks,
      audioOutputIcon = rememberLightTintedPainter(audioIconRes),
      displayVideoTooltip = displayVideoTooltip,
      moreButtonModifier = Modifier.popupTrigger(additionalActionsState.triggerAlignedPopupState),
      modifier = modifier.padding(bottom = bottom)
    )
  }

  audioOutputPickerController.Sheet()
  LaunchedEffect(audioOutputPickerController.displaySheet) {
    callScreenSheetDisplayListener.onAudioDeviceSheetDisplayChanged(audioOutputPickerController.displaySheet)
  }
}

@NightPreview
@Composable
fun CallControlsPreview() {
  Previews.Preview {
    CallControls(
      callControlsState = CallControlsState(
        displayAudioOutputToggle = true,
        audioOutput = WebRtcAudioOutput.WIRED_HEADSET,
        displayMicToggle = true,
        isMicEnabled = true,
        displayVideoToggle = true,
        isVideoEnabled = true,
        displayGroupRingingToggle = true,
        isGroupRingingEnabled = true,
        displayAdditionalActions = true,
        displayStartCallButton = true,
        startCallButtonText = R.string.WebRtcCallView__start_call,
        displayEndCallButton = true
      ),
      displayVideoTooltip = false,
      callScreenControlsListener = CallScreenControlsListener.Empty,
      callScreenSheetDisplayListener = CallScreenSheetDisplayListener.Empty,
      additionalActionsState = AdditionalActionsState(
        triggerAlignedPopupState = rememberTriggerAlignedPopupState()
      ),
      audioOutputPickerController = AudioOutputPickerController(
        outputState = ToggleButtonOutputState(),
        onSelectedDeviceChanged = {}
      )
    )
  }
}

/**
 * Callbacks for call controls actions.
 */
interface CallScreenSheetDisplayListener {
  fun onAudioDeviceSheetDisplayChanged(displayed: Boolean)
  fun onOverflowDisplayChanged(displayed: Boolean)
  fun onVideoTooltipDismissed()

  object Empty : CallScreenSheetDisplayListener {
    override fun onAudioDeviceSheetDisplayChanged(displayed: Boolean) = Unit
    override fun onOverflowDisplayChanged(displayed: Boolean) = Unit
    override fun onVideoTooltipDismissed() = Unit
  }
}

/**
 * State object representing how the controls should appear. Since these values are
 * gleaned from multiple data sources, this object represents the amalgamation of those
 * sources so we don't need to listen to multiple here.
 */
data class CallControlsState(
  val isEarpieceAvailable: Boolean = false,
  val isBluetoothHeadsetAvailable: Boolean = false,
  val isWiredHeadsetAvailable: Boolean = false,
  val skipHiddenState: Boolean = true,
  val displayAudioOutputToggle: Boolean = false,
  val audioOutput: WebRtcAudioOutput = WebRtcAudioOutput.HANDSET,
  val isAudioOutputChangePending: Boolean = false,
  val displayVideoToggle: Boolean = false,
  val isVideoEnabled: Boolean = false,
  val displayMicToggle: Boolean = false,
  val isMicEnabled: Boolean = false,
  val displayGroupRingingToggle: Boolean = false,
  val isGroupRingingEnabled: Boolean = false,
  val isGroupRingingAllowed: Boolean = false,
  val isGroupCall: Boolean = false,
  val displayAdditionalActions: Boolean = false,
  val displayStartCallButton: Boolean = false,
  val startCallButtonText: Int = R.string.WebRtcCallView__start_call,
  val displayEndCallButton: Boolean = false,
  val isLocalScreenSharing: Boolean = false
) {

  val hasAnyControls: Boolean
    get() = displayAudioOutputToggle ||
      displayVideoToggle ||
      displayMicToggle ||
      displayGroupRingingToggle ||
      displayAdditionalActions ||
      displayStartCallButton ||
      displayEndCallButton

  companion object {
    /**
     * Presentation-level method to build out the controls state from legacy objects.
     */
    @JvmStatic
    fun fromViewModelData(
      callParticipantsState: CallParticipantsState,
      webRtcControls: WebRtcControls,
      groupMemberCount: Int,
      isAudioDeviceChangePending: Boolean = false,
      isLocalScreenSharing: Boolean = false
    ): CallControlsState {
      return CallControlsState(
        isEarpieceAvailable = webRtcControls.isEarpieceAvailableForAudioToggle,
        isBluetoothHeadsetAvailable = webRtcControls.isBluetoothHeadsetAvailableForAudioToggle,
        isWiredHeadsetAvailable = webRtcControls.isWiredHeadsetAvailableForAudioToggle,
        skipHiddenState = !(webRtcControls.isFadeOutEnabled || webRtcControls == WebRtcControls.PIP || webRtcControls.displayErrorControls()),
        displayAudioOutputToggle = webRtcControls.displayAudioToggle(),
        audioOutput = webRtcControls.audioOutput,
        isAudioOutputChangePending = isAudioDeviceChangePending,
        displayVideoToggle = webRtcControls.displayVideoToggle(),
        isVideoEnabled = callParticipantsState.localParticipant.isVideoEnabled,
        displayMicToggle = webRtcControls.displayMuteAudio(),
        isMicEnabled = callParticipantsState.localParticipant.isMicrophoneEnabled,
        displayGroupRingingToggle = webRtcControls.displayRingToggle(),
        isGroupCall = webRtcControls.isGroupCall,
        isGroupRingingEnabled = callParticipantsState.ringGroup,
        isGroupRingingAllowed = groupMemberCount <= RemoteConfig.maxGroupCallRingSize,
        displayAdditionalActions = webRtcControls.displayOverflow(),
        displayStartCallButton = webRtcControls.displayStartCallControls(),
        startCallButtonText = webRtcControls.startCallButtonText,
        displayEndCallButton = webRtcControls.displayEndCall(),
        isLocalScreenSharing = isLocalScreenSharing
      )
    }
  }
}
