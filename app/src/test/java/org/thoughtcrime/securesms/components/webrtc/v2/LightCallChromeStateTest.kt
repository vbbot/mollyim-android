/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.Test

/** Pure call-chrome policy and callback tests. No Activity, service, renderer, or RingRTC is created. */
class LightCallChromeStateTest {

  @Test
  fun `the densest group call retains every existing control`() {
    val presentation = LightCallControlsPresentation.from(
      state = CallControlsState(
        displayAudioOutputToggle = true,
        displayVideoToggle = true,
        isVideoEnabled = true,
        displayMicToggle = true,
        isMicEnabled = true,
        displayGroupRingingToggle = true,
        isGroupRingingEnabled = true,
        isGroupRingingAllowed = true,
        displayAdditionalActions = true,
        displayEndCallButton = true
      ),
      hasCameraPermission = true,
      hasRecordAudioPermission = true,
      startCallText = null
    )

    assertThat(presentation.controls.map { it.action }).containsExactly(
      LightCallControlAction.AUDIO_OUTPUT,
      LightCallControlAction.VIDEO,
      LightCallControlAction.MICROPHONE,
      LightCallControlAction.GROUP_RING,
      LightCallControlAction.MORE,
      LightCallControlAction.END_CALL
    )
  }

  @Test
  fun `screen sharing suppresses only the video toggle`() {
    val presentation = LightCallControlsPresentation.from(
      state = CallControlsState(
        displayAudioOutputToggle = true,
        displayVideoToggle = true,
        displayMicToggle = true,
        displayEndCallButton = true,
        isLocalScreenSharing = true
      ),
      hasCameraPermission = true,
      hasRecordAudioPermission = true,
      startCallText = null
    )

    assertThat(presentation.controls.map { it.action }).containsExactly(
      LightCallControlAction.AUDIO_OUTPUT,
      LightCallControlAction.MICROPHONE,
      LightCallControlAction.END_CALL
    )
  }

  @Test
  fun `permission state affects presentation without changing the source control state`() {
    val presentation = LightCallControlsPresentation.from(
      state = CallControlsState(
        displayVideoToggle = true,
        isVideoEnabled = true,
        displayMicToggle = true,
        isMicEnabled = true
      ),
      hasCameraPermission = false,
      hasRecordAudioPermission = false,
      startCallText = null
    )

    assertThat(presentation.controls.single { it.action == LightCallControlAction.VIDEO }.selected).isFalse()
    assertThat(presentation.controls.single { it.action == LightCallControlAction.MICROPHONE }.selected).isFalse()
  }

  @Test
  fun `start call keeps its existing text and video mode`() {
    val presentation = LightCallControlsPresentation.from(
      state = CallControlsState(displayStartCallButton = true, isVideoEnabled = true),
      hasCameraPermission = true,
      hasRecordAudioPermission = true,
      startCallText = "JOIN CALL"
    )

    assertThat(presentation.startCallText).isEqualTo("JOIN CALL")
    assertThat(presentation.startWithVideo).isTrue()

    val hidden = LightCallControlsPresentation.from(
      state = CallControlsState(displayStartCallButton = false),
      hasCameraPermission = true,
      hasRecordAudioPermission = true,
      startCallText = "JOIN CALL"
    )
    assertThat(hidden.startCallText).isNull()
  }

  @Test
  fun `every active control routes its exact callback and desired value`() {
    val events = mutableListOf<String>()
    val callbacks = LightCallControlCallbacks(
      onAudioOutput = { events += "audio" },
      onVideoChanged = { events += "video:$it" },
      onMicrophoneChanged = { events += "mic:$it" },
      onGroupRingChanged = { enabled, allowed -> events += "ring:$enabled:$allowed" },
      onMore = { events += "more" },
      onEndCall = { events += "end" }
    )

    callbacks.invoke(LightCallControlsPresentation.Control(LightCallControlAction.AUDIO_OUTPUT))
    callbacks.invoke(LightCallControlsPresentation.Control(LightCallControlAction.VIDEO, selected = true))
    callbacks.invoke(LightCallControlsPresentation.Control(LightCallControlAction.MICROPHONE, selected = false))
    callbacks.invoke(LightCallControlsPresentation.Control(LightCallControlAction.GROUP_RING, selected = false, ringingAllowed = true))
    callbacks.invoke(LightCallControlsPresentation.Control(LightCallControlAction.MORE))
    callbacks.invoke(LightCallControlsPresentation.Control(LightCallControlAction.END_CALL))
    callbacks.invoke(LightCallControlsPresentation.Control(LightCallControlAction.AUDIO_OUTPUT, enabled = false))

    assertThat(events).containsExactly(
      "audio",
      "video:false",
      "mic:true",
      "ring:true:true",
      "more",
      "end"
    )
  }

  @Test
  fun `incoming video and audio expose exactly their supported answer modes`() {
    assertThat(incoming(isVideo = true).actions).containsExactly(
      LightIncomingCallAction.DECLINE,
      LightIncomingCallAction.ANSWER_WITHOUT_VIDEO,
      LightIncomingCallAction.ANSWER
    )
    assertThat(incoming(isVideo = false).actions).containsExactly(
      LightIncomingCallAction.DECLINE,
      LightIncomingCallAction.ANSWER
    )
  }

  @Test
  fun `incoming actions retain distinct callback paths`() {
    val events = mutableListOf<String>()
    val callbacks = LightIncomingCallCallbacks(
      onDecline = { events += "decline" },
      onAnswerWithoutVideo = { events += "voice" },
      onAnswer = { events += "answer" }
    )

    callbacks.invoke(LightIncomingCallAction.DECLINE)
    callbacks.invoke(LightIncomingCallAction.ANSWER_WITHOUT_VIDEO)
    callbacks.invoke(LightIncomingCallAction.ANSWER)

    assertThat(events).containsExactly("decline", "voice", "answer")
  }

  private fun incoming(isVideo: Boolean): LightIncomingCallPresentation {
    return LightIncomingCallPresentation(
      callerName = "Caller",
      callType = "Signal call",
      isVideoCall = isVideo
    )
  }
}
