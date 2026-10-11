/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.shared

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class RegistrationScreenTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `each bottom action dispatches only its own callback`() {
    var skipped = 0
    var continued = 0

    composeTestRule.setContent {
      org.thoughtcrime.securesms.light.MollyLightTheme {
        RegistrationBottomActions(
          left = RegistrationAction("Not now", { skipped++ }),
          right = RegistrationAction("Next", { continued++ })
        )
      }
    }

    composeTestRule.onNodeWithText("NOT NOW").performClick()
    assertThat(skipped).isEqualTo(1)
    assertThat(continued).isEqualTo(0)

    composeTestRule.onNodeWithText("NEXT").performClick()
    assertThat(skipped).isEqualTo(1)
    assertThat(continued).isEqualTo(1)
  }

  @Test
  fun `disabled action remains visible but cannot dispatch`() {
    var submissions = 0

    composeTestRule.setContent {
      org.thoughtcrime.securesms.light.MollyLightTheme {
        RegistrationBottomActions(
          right = RegistrationAction("Next", { submissions++ }, enabled = false)
        )
      }
    }

    composeTestRule.onNodeWithText("NEXT").assertIsNotEnabled()

    assertThat(submissions).isEqualTo(0)
  }

  @Test
  fun `editable field forwards raw input to its state owner`() {
    var entered = ""

    composeTestRule.setContent {
      org.thoughtcrime.securesms.light.MollyLightTheme {
        RegistrationTextField(
          value = entered,
          onValueChange = { entered = it },
          label = "Verification code"
        )
      }
    }

    composeTestRule.onNode(hasSetTextAction()).performTextInput("123456")

    assertThat(entered).isEqualTo("123456")
  }

  /**
   * The subtitle is every registration screen's block of prose, so it is `Paragraph` in the full
   * content colour -- not `Copy`, and not lightened -- and it still carries the caller's link spans,
   * which is why it is not a `LightText`. Both values come from the SDK tokens as the composition
   * sees them, scaled for the LP3's height as `LightText` scales them.
   */
  @Test
  fun `subtitle is a content-coloured Paragraph that keeps its links`() {
    var paragraphSp = 0f
    var copySp = 0f
    var content = Color.Unspecified
    val subtitle = buildAnnotatedString {
      append("Read the ")
      withLink(LinkAnnotation.Clickable("terms") { }) { append("terms") }
    }

    composeTestRule.setContent {
      org.thoughtcrime.securesms.light.MollyLightTheme {
        paragraphSp = LightThemeTokens.typography.paragraph.fontSize.value.designVerticalPxToSp().value
        copySp = LightThemeTokens.typography.copy.fontSize.value.designVerticalPxToSp().value
        content = LightThemeTokens.colors.content
        RegistrationScreenSubtitle(subtitle)
      }
    }

    val layout = composeTestRule.onNodeWithText("Read the terms").textLayout()

    assertThat(layout.layoutInput.style.fontSize.value).isCloseTo(paragraphSp, 0.01f)
    assertThat(paragraphSp).isLessThan(copySp)
    assertThat(layout.layoutInput.style.color).isEqualTo(content)
    assertThat(layout.layoutInput.text.getLinkAnnotations(0, subtitle.length)).hasSize(1)
  }

  private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
    lateinit var layout: TextLayoutResult
    performSemanticsAction(SemanticsActions.GetTextLayoutResult) { fetch ->
      val results = mutableListOf<TextLayoutResult>()
      fetch(results)
      layout = results.first()
      true
    }
    return layout
  }
}
