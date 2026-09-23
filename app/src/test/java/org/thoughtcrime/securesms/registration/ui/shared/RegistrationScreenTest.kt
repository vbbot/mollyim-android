/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.shared

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule

@RunWith(RobolectricTestRunner::class)
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
}
