/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.light

import android.app.Application
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.thoughtcrime.securesms.light.MollyLightTheme

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class LightVoiceRecordingTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `send remains disabled through exactly one second`() {
    assertThat(recordingState(999).canSend).isFalse()
    assertThat(recordingState(1_000).canSend).isFalse()
    assertThat(recordingState(1_001).canSend).isTrue()
  }

  @Test
  fun `only locked recording can send`() {
    assertThat(
      LightVoiceRecordingState(LightVoiceRecordingMode.HELD, 5_000).canSend
    ).isFalse()
  }

  @Test
  fun `one hour is the presentation limit boundary`() {
    assertThat(recordingState(MAXIMUM_VOICE_MESSAGE_DURATION_MILLIS - 1).hasReachedLimit).isFalse()
    assertThat(recordingState(MAXIMUM_VOICE_MESSAGE_DURATION_MILLIS).hasReachedLimit).isTrue()
  }

  @Test
  fun `formats the recorder clock without locale-dependent punctuation`() {
    assertThat(formatVoiceTime(0)).isEqualTo("0:00")
    assertThat(formatVoiceTime(65_999)).isEqualTo("1:05")
    assertThat(formatVoiceTime(3_600_000)).isEqualTo("60:00")
  }

  @Test
  fun `locked controls route to independent intents`() {
    val calls = mutableListOf<String>()
    setRecording(
      state = recordingState(2_000),
      callbacks = LightVoiceRecordingCallbacks(
        onDiscard = { calls += "discard" },
        onReview = { calls += "review" },
        onSend = { calls += "send" }
      )
    )

    composeTestRule.onNodeWithContentDescription("Discard recording").performClick()
    composeTestRule.onNodeWithContentDescription("Stop and review recording").performClick()
    composeTestRule.onNodeWithContentDescription("Send recording").performClick()

    assertThat(calls).isEqualTo(listOf("discard", "review", "send"))
  }

  @Test
  fun `short recording shows send but exposes no click action`() {
    setRecording(recordingState(1_000))

    composeTestRule
      .onNodeWithContentDescription("Send recording")
      .assertIsDisplayed()
      .assertHasNoClickAction()
  }

  @Test
  fun `held state preserves the slide gesture instruction without locked actions`() {
    setRecording(LightVoiceRecordingState(LightVoiceRecordingMode.HELD, 500))

    composeTestRule.onNodeWithText("SLIDE TO CANCEL").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Send recording").assertDoesNotExist()
  }

  @Test
  fun `recording host is eight grid units tall at LP3 geometry`() {
    setRecording(recordingState(2_000))

    val bounds = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    val expectedHeight = 360.dp.value / 27f * 8f

    assertThat((bounds.bottom - bounds.top).value).isCloseTo(expectedHeight, 0.5f)
    assertThat(bounds.left.value).isEqualTo(0f)
    assertThat(bounds.right.value).isEqualTo(360f)
  }

  private fun recordingState(elapsedMillis: Long) =
    LightVoiceRecordingState(LightVoiceRecordingMode.LOCKED, elapsedMillis)

  private fun setRecording(
    state: LightVoiceRecordingState,
    callbacks: LightVoiceRecordingCallbacks = LightVoiceRecordingCallbacks({}, {}, {})
  ) {
    composeTestRule.setContent {
      MollyLightTheme {
        LightVoiceRecording(state = state, callbacks = callbacks)
      }
    }
  }
}
