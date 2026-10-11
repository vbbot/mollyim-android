/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.entercode

import android.app.Application
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.signal.core.ui.CoreUiDependenciesRule
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class EnterCodeScreenTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  private val field = VerificationCodeFieldState()
  private val submitted = mutableListOf<String>()
  private var backTapped = false
  private var resendTapped = 0
  private var callTapped = 0
  private var wrongNumberTapped = 0
  private var havingTroubleTapped = 0

  @Test
  fun `field keeps only digits and stops at six`() {
    setScreen()

    composeTestRule.onNodeWithTag(ENTER_CODE_FIELD_TAG).performTextInput("12a3-45678")

    composeTestRule.onNodeWithTag(ENTER_CODE_FIELD_TAG).assertTextEquals("123456")
  }

  @Test
  fun `the sixth digit submits the code once`() {
    setScreen()

    composeTestRule.onNodeWithTag(ENTER_CODE_FIELD_TAG).performTextInput("12345")
    assertThat(submitted).isEmpty()

    composeTestRule.onNodeWithTag(ENTER_CODE_FIELD_TAG).performTextInput("6")
    composeTestRule.onNodeWithTag(ENTER_CODE_FIELD_TAG).performTextInput("7")

    assertThat(submitted).containsExactly("123456")
  }

  @Test
  fun `a reset after an error empties the field and keeps it focused`() {
    setScreen()
    composeTestRule.onNodeWithTag(ENTER_CODE_FIELD_TAG).performTextInput("123456")

    // What the fragment does on resetRequiredAfterFailure.
    composeTestRule.runOnIdle {
      field.clear()
      field.requestFocus()
    }

    composeTestRule.onNodeWithTag(ENTER_CODE_FIELD_TAG).assertTextEquals("")
    composeTestRule.onNodeWithTag(ENTER_CODE_FIELD_TAG).assertIsFocused()

    composeTestRule.onNodeWithTag(ENTER_CODE_FIELD_TAG).performTextInput("654321")
    assertThat(submitted).containsExactly("123456", "654321")
  }

  @Test
  fun `the overflow lists the four actions with the resend countdown inert`() {
    // 42 seconds to the next SMS, the call already available.
    setScreen(now = 100.seconds, nextSms = 142.seconds, nextCall = 50.seconds, showHavingTrouble = true)

    composeTestRule.onNodeWithContentDescription("More options").performClick()

    composeTestRule.onNodeWithTag(ENTER_CODE_PANEL_TAG).assertExists()
    composeTestRule.onNodeWithText("RESEND CODE (00:42)").assertIsNotEnabled()
    composeTestRule.onNodeWithText("CALL").assertExists()
    composeTestRule.onNodeWithText("WRONG NUMBER?").assertExists()
    composeTestRule.onNodeWithText("HAVING TROUBLE REGISTERING?").assertExists()

    composeTestRule.onNodeWithText("RESEND CODE (00:42)").performClick()
    assertThat(resendTapped).isEqualTo(0)
    composeTestRule.onNodeWithTag(ENTER_CODE_PANEL_TAG).assertExists()

    composeTestRule.onNodeWithText("CALL").performClick()
    assertThat(callTapped).isEqualTo(1)
    composeTestRule.onNodeWithTag(ENTER_CODE_PANEL_TAG).assertDoesNotExist()
  }

  @Test
  fun `support is held back until three wrong codes, and a session without a time keeps resend inert`() {
    setScreen(now = 100.seconds, nextSms = 0.seconds, nextCall = 0.seconds, showHavingTrouble = false)

    composeTestRule.onNodeWithContentDescription("More options").performClick()

    composeTestRule.onNodeWithText("HAVING TROUBLE REGISTERING?").assertDoesNotExist()
    composeTestRule.onNodeWithText("RESEND CODE").assertIsNotEnabled()
    composeTestRule.onNodeWithText("CALL").assertIsNotEnabled()

    composeTestRule.onNodeWithText("WRONG NUMBER?").performClick()
    assertThat(wrongNumberTapped).isEqualTo(1)
  }

  @Test
  fun `BACK runs the back callback`() {
    setScreen()

    composeTestRule.onNodeWithContentDescription("Back to phone number").performClick()

    assertThat(backTapped).isTrue()
  }

  private fun setScreen(
    now: Duration = 0.seconds,
    nextSms: Duration = 0.seconds,
    nextCall: Duration = 0.seconds,
    showHavingTrouble: Boolean = false,
  ) {
    composeTestRule.setContent {
      EnterCodeScreen(
        sentTo = "Enter the code we sent to +44 7700 900123",
        field = field,
        inProgress = false,
        incorrectCode = false,
        nextSmsTimestamp = nextSms,
        nextCallTimestamp = nextCall,
        showHavingTrouble = showHavingTrouble,
        covered = false,
        now = { now },
        onBack = { backTapped = true },
        onCodeComplete = { submitted += it },
        onResendSms = { resendTapped++ },
        onCall = { callTapped++ },
        onWrongNumber = { wrongNumberTapped++ },
        onHavingTrouble = { havingTroubleTapped++ },
      )
    }
  }
}
