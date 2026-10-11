/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.permissions

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import assertk.assertThat
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
import org.signal.registration.R as RegistrationR
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.MollyLightTheme

/**
 * The permissions screen's typography: the introduction is prose, so the SDK's `Paragraph`; each
 * permission is a row, so a `Heading` label over a `Detail` line. Sizes are the SDK tokens as the
 * composition sees them, scaled for the LP3 as `LightText` scales them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class GrantPermissionsScreenTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  private var paragraphSp = 0f
  private var headingSp = 0f
  private var detailSp = 0f

  @Test
  fun `the introduction is a Paragraph and rows are Heading over Detail`() {
    composeTestRule.setContent {
      MollyLightTheme {
        val typography = LightThemeTokens.typography
        paragraphSp = typography.paragraph.fontSize.value.designVerticalPxToSp().value
        headingSp = typography.heading.fontSize.value.designVerticalPxToSp().value
        detailSp = typography.detail.fontSize.value.designVerticalPxToSp().value
      }
      GrantPermissionsScreen(deviceBuildVersion = 34, isBackupSelectionRequired = false)
    }

    val context = RuntimeEnvironment.getApplication()

    assertThat(composeTestRule.onNodeWithText(context.getString(RegistrationR.string.GrantPermissionsFragment__to_help_you_message_people_you_know)).fontSizeSp())
      .isCloseTo(paragraphSp, 0.01f)
    assertThat(composeTestRule.onNodeWithText(context.getString(RegistrationR.string.GrantPermissionsFragment__contacts)).fontSizeSp())
      .isCloseTo(headingSp, 0.01f)
    assertThat(composeTestRule.onNodeWithText(context.getString(RegistrationR.string.GrantPermissionsFragment__find_people_you_know)).fontSizeSp())
      .isCloseTo(detailSp, 0.01f)
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
