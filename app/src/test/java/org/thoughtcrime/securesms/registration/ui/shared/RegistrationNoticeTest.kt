/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.shared

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isCloseTo
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.designVerticalPxToSp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.signal.core.ui.CoreUiDependenciesRule
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.registration.ui.phonenumber.PhoneNumberNoticeAction

/**
 * The windowed Light notice and busy screen that the restore and recovery-key screens raise in place
 * of Material dialogs. Each exit has to run its own callback and nothing else; the busy screen has
 * none at all.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class RegistrationNoticeTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  private val calls = mutableListOf<String>()

  @Test
  fun `each action runs only its own callback`() {
    showNotice()

    composeTestRule.onNodeWithText("TRY AGAIN").performClick()
    composeTestRule.onNodeWithText("HELP").performClick()

    assertThat(calls).containsExactly("try again", "help")
  }

  @Test
  fun `BACK runs the cancel callback only`() {
    showNotice()

    composeTestRule.onNodeWithContentDescription("Back to recovery key").performClick()

    assertThat(calls).containsExactly("back")
  }

  @Test
  fun `the title is in the top bar and the copy is the Copy token`() {
    var copySp = 0f
    composeTestRule.setContent {
      MollyLightTheme {
        copySp = LightThemeTokens.typography.copy.fontSize.value.designVerticalPxToSp().value
      }
      notice()
    }

    composeTestRule.onAllNodesWithText("Backup not found").assertCountEquals(1)
    assertThat(composeTestRule.onNodeWithText("No backup was found.").fontSizeSp()).isCloseTo(copySp, 0.01f)
  }

  @Test
  fun `busy shows its status line as a Heading and offers nothing to tap`() {
    var headingSp = 0f
    composeTestRule.setContent {
      MollyLightTheme {
        headingSp = LightThemeTokens.typography.heading.fontSize.value.designVerticalPxToSp().value
      }
      RegistrationBusyDialog(message = "Restoring…")
    }

    composeTestRule.onNodeWithTag(REGISTRATION_BUSY_TAG).assertExists()
    composeTestRule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate)).assertExists()
    assertThat(composeTestRule.onNodeWithText("Restoring…").fontSizeSp()).isCloseTo(headingSp, 0.01f)
    composeTestRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).assertCountEquals(0)
  }

  @Test
  fun `busy without a message shows the ellipsis`() {
    composeTestRule.setContent {
      RegistrationBusyDialog()
    }

    composeTestRule.onNodeWithText("…").assertExists()
  }

  private fun showNotice() {
    composeTestRule.setContent { notice() }
  }

  @Composable
  private fun notice() {
    RegistrationNoticeDialog(
      title = "Backup not found",
      message = "No backup was found.",
      actions = listOf(
        PhoneNumberNoticeAction("Help") { calls += "help" },
        PhoneNumberNoticeAction("Try again") { calls += "try again" }
      ),
      onBack = { calls += "back" },
      backDescription = "Back to recovery key"
    )
  }

  private fun SemanticsNodeInteraction.fontSizeSp(): Float {
    var sp = Float.NaN
    performSemanticsAction(SemanticsActions.GetTextLayoutResult) { fetch ->
      val results = mutableListOf<TextLayoutResult>()
      fetch(results)
      sp = results.first().layoutInput.style.fontSize.value
      true
    }
    return sp
  }
}
