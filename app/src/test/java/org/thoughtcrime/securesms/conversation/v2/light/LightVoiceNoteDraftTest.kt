/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.light

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
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
class LightVoiceNoteDraftTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `draft progress is bounded and safe before metadata arrives`() {
    assertThat(LightVoiceNoteDraftState(durationMillis = 0).progress).isEqualTo(0f)
    assertThat(LightVoiceNoteDraftState(positionMillis = -10, durationMillis = 100).progress).isEqualTo(0f)
    assertThat(LightVoiceNoteDraftState(positionMillis = 150, durationMillis = 100).progress).isEqualTo(1f)
  }

  @Test
  fun `seek conversion clamps values from the presentation`() {
    val state = LightVoiceNoteDraftState(durationMillis = 10_000)

    assertThat(state.progressToSeek(-1f)).isEqualTo(0.0)
    assertThat(state.progressToSeek(0.25f)).isEqualTo(0.25)
    assertThat(state.progressToSeek(2f)).isEqualTo(1.0)
  }

  @Test
  fun `draft actions route through immutable callbacks`() {
    val calls = mutableListOf<String>()
    setDraft(
      callbacks = LightVoiceNoteDraftCallbacks(
        onDelete = { calls += "delete" },
        onTogglePlayback = { calls += "play" },
        onSeek = {},
        onSend = { calls += "send" }
      )
    )

    composeTestRule.onNodeWithContentDescription("Delete voice note").performClick()
    composeTestRule.onNodeWithContentDescription("Play voice note").performClick()
    composeTestRule.onNodeWithContentDescription("Send voice note").performClick()

    assertThat(calls).isEqualTo(listOf("delete", "play", "send"))
  }

  @Test
  fun `playing state presents pause instead of play`() {
    setDraft(state = LightVoiceNoteDraftState(1_000, 5_000, isPlaying = true))

    composeTestRule.onNodeWithContentDescription("Pause voice note").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Play voice note").assertDoesNotExist()
  }

  @Test
  fun `seek bar sends a normalized position`() {
    var seek: Double? = null
    setDraft(
      callbacks = LightVoiceNoteDraftCallbacks({}, {}, { seek = it }, {})
    )

    val seekBar = composeTestRule.onNodeWithContentDescription("Seek voice note")
    val bounds = seekBar.getUnclippedBoundsInRoot()
    seekBar.performTouchInput {
      click(Offset(visibleSize.width * 0.75f, visibleSize.height / 2f))
    }

    assertThat(bounds.width.value).isBetween(100f, 260f)
    assertThat(seek ?: -1.0).isCloseTo(0.75, 0.03)
  }

  @Test
  fun `recorded draft host is eight grid units tall at LP3 geometry`() {
    setDraft()

    val bounds = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    val expectedHeight = 360.dp.value / 27f * 8f

    assertThat((bounds.bottom - bounds.top).value).isCloseTo(expectedHeight, 0.5f)
    assertThat(bounds.left.value).isEqualTo(0f)
    assertThat(bounds.right.value).isEqualTo(360f)
  }

  private fun setDraft(
    state: LightVoiceNoteDraftState = LightVoiceNoteDraftState(2_000, 10_000),
    callbacks: LightVoiceNoteDraftCallbacks = LightVoiceNoteDraftCallbacks({}, {}, {}, {})
  ) {
    composeTestRule.setContent {
      MollyLightTheme {
        LightVoiceNoteDraft(state = state, callbacks = callbacks)
      }
    }
  }
}
