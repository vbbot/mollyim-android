/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.passphrase

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class PassphraseCreateScreenTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  private val clicks = mutableListOf<String>()
  private var paragraphSp = 0f
  private var copySp = 0f

  @Test
  fun `title is in the top bar and both paragraphs are shown`() {
    setScreen()

    composeTestRule.onNodeWithText("Database encryption").assertExists()
    composeTestRule.onNodeWithText("Now you can set a passphrase", substring = true).assertExists()
    composeTestRule.onNodeWithText("Require passphrase to unlock Molly?").assertExists()
  }

  @Test
  fun `the first-run root has no back`() {
    setScreen()

    composeTestRule.onAllNodes(hasContentDescription("Back", substring = true, ignoreCase = true)).assertCountEquals(0)
  }

  @Test
  fun `ENCRYPT and NOT NOW report their own choice`() {
    setScreen()

    composeTestRule.onNodeWithText("ENCRYPT").performClick()
    composeTestRule.onNodeWithText("NOT NOW").performClick()

    assertThat(clicks).containsExactly("encrypt", "not now")
  }

  /**
   * The explanation is prose, so `Paragraph`; the one-line question under it is a short statement,
   * so `Copy`. Sizes are the SDK tokens as the composition sees them, scaled for the LP3.
   */
  @Test
  fun `the explanation is a Paragraph and the question is Copy`() {
    setScreen()

    assertThat(composeTestRule.onNodeWithText("Now you can set a passphrase", substring = true).fontSizeSp()).isCloseTo(paragraphSp, 0.01f)
    assertThat(composeTestRule.onNodeWithText("Require passphrase to unlock Molly?").fontSizeSp()).isCloseTo(copySp, 0.01f)
  }

  private fun setScreen() {
    composeTestRule.setContent {
      MollyLightTheme {
        paragraphSp = LightThemeTokens.typography.paragraph.fontSize.value.designVerticalPxToSp().value
        copySp = LightThemeTokens.typography.copy.fontSize.value.designVerticalPxToSp().value
      }
      PassphraseCreateScreen(
        onNotNowClicked = { clicks += "not now" },
        onEncryptClicked = { clicks += "encrypt" },
      )
    }
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
