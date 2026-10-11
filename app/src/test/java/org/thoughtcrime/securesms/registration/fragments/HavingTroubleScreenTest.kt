/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.fragments

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.MollyLightTheme
import java.util.Locale

/**
 * The verification-code screen's "having trouble" help page, which was a Material bottom sheet with
 * two link spans. The links are now two text actions; each has to reach only its own destination,
 * and BACK only closes the page.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class HavingTroubleScreenTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  private val calls = mutableListOf<String>()
  private var paragraphSp = 0f

  @Test
  fun `TROUBLESHOOT opens the troubleshooting steps only`() {
    setScreen()

    composeTestRule.onNodeWithText(action(R.string.ContactSupportBottomSheetFragment__troubleshoot)).performClick()

    assertThat(calls).containsExactly("troubleshoot")
  }

  @Test
  fun `CONTACT SUPPORT emails support only`() {
    setScreen()

    composeTestRule.onNodeWithText(action(R.string.RegistrationActivity_support_bottom_sheet_cta_contact_support_substring)).performClick()

    assertThat(calls).containsExactly("contact support")
  }

  @Test
  fun `BACK only closes`() {
    setScreen()

    composeTestRule.onNodeWithContentDescription(string(R.string.EnterCodeFragment__back_to_verification_code)).performClick()

    assertThat(calls).containsExactly("back")
  }

  @Test
  fun `the title is in the top bar and the suggestions are a Paragraph`() {
    setScreen()

    composeTestRule.onNodeWithText(string(R.string.ContactSupportBottomSheetFragment__having_trouble)).assertExists()
    assertThat(
      composeTestRule.onNodeWithText(string(R.string.RegistrationActivity_support_bottom_sheet_body_suggestions)).fontSizeSp()
    ).isCloseTo(paragraphSp, 0.01f)
  }

  private fun setScreen() {
    composeTestRule.setContent {
      MollyLightTheme {
        paragraphSp = LightThemeTokens.typography.paragraph.fontSize.value.designVerticalPxToSp().value
      }
      HavingTroubleScreen(
        onBack = { calls += "back" },
        onTroubleshoot = { calls += "troubleshoot" },
        onContactSupport = { calls += "contact support" }
      )
    }
  }

  private fun string(@StringRes id: Int): String = RuntimeEnvironment.getApplication().getString(id)

  private fun action(@StringRes id: Int): String = string(id).uppercase(Locale.getDefault())

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
