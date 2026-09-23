/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.thelightphone.sdk.ui.gridUnitsAsDp
import org.signal.core.ui.compose.NightPreview
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.events.CallParticipant
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.ringrtc.CameraState

/**
 * Incoming-call renderer plus Light chrome.
 *
 * The renderer remains in Signal's call presentation tree so its sink, mirroring and camera behavior
 * are unchanged. [MollyLightTheme] is scoped to [LightIncomingCallChrome] rather than wrapped around
 * the renderer. The chrome itself installs pointer handling only on its top and bottom actions.
 */
@Composable
fun IncomingCallScreen(
  callRecipient: Recipient,
  callStatus: String?,
  isVideoCall: Boolean,
  callScreenControlsListener: CallScreenControlsListener,
  localParticipant: CallParticipant = CallParticipant.EMPTY
) {
  val showLocalVideo = localParticipant.isVideoEnabled

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black)
  ) {
    if (showLocalVideo) {
      RemoteParticipantContent(
        participant = localParticipant,
        renderInPip = false,
        raiseHandAllowed = false,
        mirrorVideo = localParticipant.cameraDirection == CameraState.Direction.FRONT,
        showAudioIndicator = false,
        onInfoMoreInfoClick = null,
        modifier = Modifier.fillMaxSize()
      )

      // Visual contrast only. With no pointer modifier this does not steal preview gestures.
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.28f))
      )
    }

    val context = LocalContext.current
    val presentation = LightIncomingCallPresentation(
      callerName = callRecipient.getDisplayName(context),
      callType = if (isVideoCall) {
        context.getString(R.string.WebRtcCallView__signal_video_call)
      } else {
        context.getString(R.string.WebRtcCallView__signal_call)
      },
      callStatus = callStatus,
      isVideoCall = isVideoCall
    )
    val callbacks = LightIncomingCallCallbacks(
      onNavigateUp = callScreenControlsListener::onNavigateUpClicked,
      onCallInfo = callScreenControlsListener::onCallInfoClicked,
      onDecline = callScreenControlsListener::onDenyCallPressed,
      onAnswerWithoutVideo = callScreenControlsListener::onAcceptCallWithVoiceOnlyPressed,
      onAnswer = callScreenControlsListener::onAcceptCallPressed
    )

    MollyLightTheme {
      LightIncomingCallChrome(
        presentation = presentation,
        callbacks = callbacks,
        avatar = {
          AvatarImage(
            recipient = callRecipient,
            modifier = Modifier.size(6f.gridUnitsAsDp())
          )
        }
      )
    }
  }
}

@NightPreview
@Preview(device = "spec:parent=pixel_5,orientation=landscape", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun IncomingVideoCallScreenPreview() {
  Previews.Preview {
    IncomingCallScreen(
      callRecipient = Recipient(systemContactName = "Test User"),
      callScreenControlsListener = CallScreenControlsListener.Empty,
      isVideoCall = true,
      callStatus = "Spiderman is calling the group"
    )
  }
}

@NightPreview
@Preview(device = "spec:parent=pixel_5,orientation=landscape", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun IncomingAudioCallScreenPreview() {
  Previews.Preview {
    IncomingCallScreen(
      callRecipient = Recipient(systemContactName = "Test User"),
      callScreenControlsListener = CallScreenControlsListener.Empty,
      isVideoCall = false,
      callStatus = "Spiderman is calling the group"
    )
  }
}
