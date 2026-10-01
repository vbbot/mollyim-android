/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.thelightphone.sdk.ui.gridUnitsAsDp
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.signal.core.ui.CoreUiDependenciesRule
import org.thoughtcrime.securesms.light.MollyLightTheme

/**
 * The notice that floats over a call.
 *
 * **What is not tested here, and why.** This component's whole reason to exist is that it is
 * visible: a call screen is video or black, and this notice is a black box on both, so if its
 * white hairline ever went, it would stop existing on the resting surface. That is the invisible-UI
 * failure this project has shipped before, and the right test for it reads rendered pixels --
 * a `border` in the modifier tree and a border in the image are different claims.
 *
 * That test is not written here because this harness cannot produce a rendered image. Two
 * independent routes were tried and both fail for the same reason: `captureToImage` blocks forever
 * in `forceRedraw`, because Robolectric never delivers the frame-commit callback it waits on, and a
 * `GraphicsLayer` recorded from `drawWithContent` comes back zero-sized, because `drawWithContent`
 * never runs. The harness composes and lays out; it does not draw. So the colours this component
 * renders are, right now, checked by nothing, and they stay unverified until the notice is seen on
 * LP3 hardware. Do not read a green run of this class as that having been settled.
 */
@RunWith(RobolectricTestRunner::class)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LightCallNoticeTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  /**
   * Whether the notice is waiting on something.
   *
   * `createComposeRule` allows one `setContent` per test, so the spinner test drives the notice by
   * flipping this and letting Compose recompose, rather than by rebuilding the tree.
   */
  private var showSpinner by mutableStateOf(false)

  @Before
  fun setUp() {
    composeTestRule.setContent {
      MollyLightTheme {
        Box(modifier = Modifier.padding(GUTTER_UNITS.gridUnitsAsDp())) {
          LightCallNotice(text = NOTICE_TEXT, showSpinner = showSpinner)
        }
      }
    }
  }

  @Test
  fun `the notice carries the line it is there to show`() {
    composeTestRule.onNodeWithText(NOTICE_TEXT).assertIsDisplayed()
  }

  /**
   * The spinner says the notice is waiting on something. Showing it on a settled notice would claim
   * work is in flight that is not, and on a panel with one foreground colour the spinner is the
   * only cue saying so.
   */
  @Test
  fun `the spinner appears only while the notice is waiting`() {
    showSpinner = true
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(LIGHT_CALL_NOTICE_SPINNER_TAG).assertIsDisplayed()

    showSpinner = false
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag(LIGHT_CALL_NOTICE_SPINNER_TAG).assertDoesNotExist()
  }

  private companion object {
    const val NOTICE_TEXT = "WAITING TO BE LET IN"

    /** Space around the notice, so the hairline is not flush with the edge of its own bounds. */
    const val GUTTER_UNITS = 4f
  }
}
