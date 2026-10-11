/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.passphrase

import android.app.Application
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
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
import org.thoughtcrime.securesms.ChangePassphraseDialogFragment
import org.thoughtcrime.securesms.light.MollyLightTheme

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class ChangePassphraseScreenTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  private val state = ChangePassphraseFormState()
  private var backClicks = 0
  private var submitClicks = 0
  private lateinit var hostView: View
  private var paragraphSp = 0f

  private val passwordFields = SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)

  @Test
  fun `change shows old, new and repeat fields, all obscured`() {
    setScreen(ChangePassphraseDialogFragment.MODE_CHANGE)

    composeTestRule.onNodeWithText("Change passphrase").assertExists()
    composeTestRule.onNodeWithText("Old passphrase").assertExists()
    composeTestRule.onNodeWithText("New passphrase").assertExists()
    composeTestRule.onNodeWithText("Repeat new passphrase").assertExists()
    composeTestRule.onAllNodes(passwordFields).assertCountEquals(3)
  }

  @Test
  fun `enable shows the explanation and only the new passphrase fields`() {
    setScreen(ChangePassphraseDialogFragment.MODE_ENABLE)

    composeTestRule.onNodeWithText("Create passphrase").assertExists()
    composeTestRule.onNodeWithText("Old passphrase").assertDoesNotExist()
    composeTestRule.onNodeWithText("Please choose a passphrase", substring = true).assertExists()
    composeTestRule.onAllNodes(passwordFields).assertCountEquals(2)
  }

  @Test
  fun `disable shows one passphrase field and a DISABLE action`() {
    setScreen(ChangePassphraseDialogFragment.MODE_DISABLE)

    composeTestRule.onNodeWithText("Disable passphrase?").assertExists()
    composeTestRule.onNodeWithText("Passphrase").assertExists()
    composeTestRule.onAllNodes(passwordFields).assertCountEquals(1)

    composeTestRule.onNodeWithText("DISABLE").performClick()
    assertThat(submitClicks).isEqualTo(1)
  }

  @Test
  fun `OK submits and BACK cancels`() {
    setScreen(ChangePassphraseDialogFragment.MODE_CHANGE)

    composeTestRule.onNodeWithText("OK").performClick()
    composeTestRule.onNodeWithContentDescription("Close without changing the passphrase").performClick()

    assertThat(submitClicks).isEqualTo(1)
    assertThat(backClicks).isEqualTo(1)
  }

  @Test
  fun `an error shows under its field and clears when that field is edited`() {
    setScreen(ChangePassphraseDialogFragment.MODE_ENABLE)

    composeTestRule.runOnIdle { state.repeatPassphraseError = "Passphrases don't match!" }
    composeTestRule.onNodeWithText("Passphrases don't match!").assertExists()

    composeTestRule.onAllNodes(hasSetTextAction())[1].performTextInput("x")

    composeTestRule.onNodeWithText("Passphrases don't match!").assertDoesNotExist()
    composeTestRule.runOnIdle { assertThat(state.repeatPassphraseError).isNull() }
  }

  @Test
  fun `editing one field leaves another field's error`() {
    setScreen(ChangePassphraseDialogFragment.MODE_ENABLE)

    composeTestRule.runOnIdle { state.newPassphraseError = "Enter new passphrase!" }
    composeTestRule.onAllNodes(hasSetTextAction())[1].performTextInput("x")

    composeTestRule.onNodeWithText("Enter new passphrase!").assertExists()
  }

  @Test
  fun `typed text reaches the form state`() {
    setScreen(ChangePassphraseDialogFragment.MODE_DISABLE)

    composeTestRule.onAllNodes(hasSetTextAction())[0].performTextInput("s3cret")

    composeTestRule.runOnIdle {
      assertThat(ChangePassphraseFormState.copyOf(state.currentPassphrase).contentEquals("s3cret".toCharArray())).isTrue()
    }
  }

  @Test
  fun `SHOW toggles to HIDE and stays a password field`() {
    setScreen(ChangePassphraseDialogFragment.MODE_DISABLE)

    composeTestRule.onNodeWithContentDescription("Show passphrase").performClick()

    composeTestRule.onNodeWithContentDescription("Hide passphrase").assertExists()
    composeTestRule.onAllNodes(passwordFields).assertCountEquals(1)
  }

  @Test
  fun `key generation replaces the fields and makes the actions inert`() {
    setScreen(ChangePassphraseDialogFragment.MODE_ENABLE)

    composeTestRule.runOnIdle { state.inProgress = true }

    composeTestRule.onNodeWithText("Please wait…").assertExists()
    composeTestRule.onNodeWithText("Generating keys", substring = true).assertExists()
    composeTestRule.onAllNodes(passwordFields).assertCountEquals(0)
    composeTestRule.onNodeWithText("…").assertHasNoClickAction()
    composeTestRule.onAllNodesWithContentDescription("Close without changing the passphrase").assertCountEquals(0)
  }

  @Test
  fun `disable keeps its field during progress, disabled`() {
    setScreen(ChangePassphraseDialogFragment.MODE_DISABLE)
    composeTestRule.onAllNodes(passwordFields).onFirst().assertIsEnabled()

    composeTestRule.runOnIdle { state.inProgress = true }

    composeTestRule.onAllNodes(passwordFields).onFirst().assertIsNotEnabled()
    composeTestRule.onNodeWithText("…").assertHasNoClickAction()
  }

  @Test
  fun `the keyboard is asked for a password field with no learning and no autocorrect`() {
    setScreen(ChangePassphraseDialogFragment.MODE_DISABLE)

    composeTestRule.onAllNodes(hasSetTextAction())[0].performClick()
    val editorInfo = composeTestRule.runOnIdle { EditorInfo().also { hostView.onCreateInputConnection(it) } }

    assertThat(editorInfo.inputType and InputType.TYPE_MASK_VARIATION).isEqualTo(InputType.TYPE_TEXT_VARIATION_PASSWORD)
    assertThat(editorInfo.inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT).isEqualTo(0)
    assertThat(editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING).isEqualTo(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
  }

  @Test
  fun `the host view keeps the form out of autofill`() {
    val view = ChangePassphraseContent.create(
      RuntimeEnvironment.getApplication(),
      ChangePassphraseForm.forMode(ChangePassphraseDialogFragment.MODE_CHANGE),
      state,
      {},
      {},
    )

    assertThat(view.importantForAutofill).isEqualTo(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS)
  }

  /** The explanation is prose, so the SDK's `Paragraph` token, scaled for the LP3 as `LightText` scales it. */
  @Test
  fun `the explanation is a Paragraph`() {
    setScreen(ChangePassphraseDialogFragment.MODE_ENABLE)

    assertThat(composeTestRule.onNodeWithText("Please choose a passphrase", substring = true).fontSizeSp()).isCloseTo(paragraphSp, 0.01f)
  }

  private fun setScreen(mode: Int) {
    composeTestRule.setContent {
      hostView = LocalView.current
      MollyLightTheme {
        paragraphSp = LightThemeTokens.typography.paragraph.fontSize.value.designVerticalPxToSp().value
      }
      ChangePassphraseScreen(
        form = ChangePassphraseForm.forMode(mode),
        state = state,
        onBack = { backClicks++ },
        onSubmit = { submitClicks++ },
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
