/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isBetween
import com.thelightphone.sdk.ui.gridUnitsAsDp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.MollyLightTheme

/** LP3 chrome geometry and interaction tests using primitive fixtures; no RingRTC initialization. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class LightCallChromeGeometryTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `six active controls remain inside one bounded four-unit strip`() {
    var gridUnit = 0.dp

    composeTestRule.setContent {
      gridUnit = 1f.gridUnitsAsDp()
      MollyLightTheme {
        Box(modifier = Modifier.fillMaxSize()) {
          LightCallControls(
            presentation = denseControls(),
            callbacks = LightCallControlCallbacks(),
            audioOutputIcon = ColorPainter(Color.White),
            displayVideoTooltip = false,
            modifier = Modifier.align(Alignment.BottomCenter)
          )
        }
      }
    }

    val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    val bar = composeTestRule.onNodeWithTag(LIGHT_CALL_CONTROLS_TAG).getUnclippedBoundsInRoot()

    assertThat(bar.height.value).isBetween((gridUnit * 4f).value - 1f, (gridUnit * 4f).value + 1f)
    assertThat(bar.bottom.value).isBetween(root.bottom.value - 1f, root.bottom.value + 1f)

    denseControls().controls.forEach { control ->
      val bounds = composeTestRule.onNodeWithTag(lightCallControlTag(control.action)).getUnclippedBoundsInRoot()
      assertThat(bounds.left.value).isBetween(root.left.value, root.right.value)
      assertThat(bounds.right.value).isBetween(root.left.value, root.right.value)
      assertThat(bounds.top.value).isBetween(bar.top.value, bar.bottom.value)
      assertThat(bounds.bottom.value).isBetween(bar.top.value, bar.bottom.value)
    }
  }

  @Test
  fun `active control targets dispatch without requiring a controller`() {
    val events = mutableListOf<String>()

    composeTestRule.setContent {
      MollyLightTheme {
        LightCallControls(
          presentation = denseControls(),
          callbacks = LightCallControlCallbacks(
            onAudioOutput = { events += "audio" },
            onVideoChanged = { events += "video:$it" },
            onMicrophoneChanged = { events += "mic:$it" },
            onGroupRingChanged = { enabled, allowed -> events += "ring:$enabled:$allowed" },
            onMore = { events += "more" },
            onEndCall = { events += "end" }
          ),
          audioOutputIcon = ColorPainter(Color.White),
          displayVideoTooltip = false
        )
      }
    }

    denseControls().controls.forEach { control ->
      composeTestRule.onNodeWithTag(lightCallControlTag(control.action)).performClick()
    }

    assertThat(events).containsExactly("audio", "video:false", "mic:false", "ring:false:true", "more", "end")
  }

  @Test
  fun `incoming video chrome fits caller copy between bounded LP3 bars`() {
    val context = RuntimeEnvironment.getApplication()

    composeTestRule.setContent {
      MollyLightTheme {
        LightIncomingCallChrome(
          presentation = LightIncomingCallPresentation(
            callerName = "Miles Morales",
            callType = "Signal video call",
            callStatus = "Calling the group",
            isVideoCall = true
          ),
          callbacks = LightIncomingCallCallbacks(),
          avatar = { Box(modifier = Modifier.size(6f.gridUnitsAsDp())) }
        )
      }
    }

    val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    val top = composeTestRule.onNodeWithTag(LIGHT_INCOMING_TOP_BAR_TAG).getUnclippedBoundsInRoot()
    val bottom = composeTestRule.onNodeWithTag(LIGHT_INCOMING_ACTION_BAR_TAG).getUnclippedBoundsInRoot()
    val caller = composeTestRule.onNodeWithText("Miles Morales").getUnclippedBoundsInRoot()

    assertThat(top.top.value).isBetween(root.top.value, root.bottom.value)
    assertThat(bottom.bottom.value).isBetween(root.bottom.value - 1f, root.bottom.value + 1f)
    assertThat(caller.top.value).isBetween(top.bottom.value, bottom.top.value)
    assertThat(caller.bottom.value).isBetween(top.bottom.value, bottom.top.value)

    composeTestRule.onNodeWithContentDescription(context.getString(R.string.WebRtcCallScreen__decline)).performClick()
    composeTestRule.onNodeWithContentDescription(context.getString(R.string.WebRtcCallScreen__answer_without_video)).performClick()
    composeTestRule.onNodeWithContentDescription(context.getString(R.string.WebRtcCallScreen__answer)).performClick()
  }

  @Test
  fun `incoming answer buttons preserve all three callback owners`() {
    val events = mutableListOf<String>()
    val context = RuntimeEnvironment.getApplication()

    composeTestRule.setContent {
      MollyLightTheme {
        LightIncomingCallChrome(
          presentation = LightIncomingCallPresentation(
            callerName = "Caller",
            callType = "Signal video call",
            isVideoCall = true
          ),
          callbacks = LightIncomingCallCallbacks(
            onDecline = { events += "decline" },
            onAnswerWithoutVideo = { events += "voice" },
            onAnswer = { events += "answer" }
          ),
          avatar = {}
        )
      }
    }

    composeTestRule.onNodeWithContentDescription(context.getString(R.string.WebRtcCallScreen__decline)).performClick()
    composeTestRule.onNodeWithContentDescription(context.getString(R.string.WebRtcCallScreen__answer_without_video)).performClick()
    composeTestRule.onNodeWithContentDescription(context.getString(R.string.WebRtcCallScreen__answer)).performClick()

    assertThat(events).containsExactly("decline", "voice", "answer")
  }

  private fun denseControls(): LightCallControlsPresentation {
    return LightCallControlsPresentation(
      controls = listOf(
        LightCallControlsPresentation.Control(LightCallControlAction.AUDIO_OUTPUT),
        LightCallControlsPresentation.Control(LightCallControlAction.VIDEO, selected = true),
        LightCallControlsPresentation.Control(LightCallControlAction.MICROPHONE, selected = true),
        LightCallControlsPresentation.Control(LightCallControlAction.GROUP_RING, selected = true, ringingAllowed = true),
        LightCallControlsPresentation.Control(LightCallControlAction.MORE),
        LightCallControlsPresentation.Control(LightCallControlAction.END_CALL)
      )
    )
  }
}
