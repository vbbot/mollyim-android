/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.signal.core.ui.CoreUiDependenciesRule
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import java.util.Locale

/**
 * The Light notices that replace the restore choice's and recovery-key screen's Material dialogs and
 * bottom sheet. What can go wrong is which callback a gesture reaches, and in which order: every
 * Material button cleared its dialog *before* acting, and the notices have to keep that order, or a
 * callback that navigates finds the notice still up.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class RestoreNoticesTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  private val calls = mutableListOf<String>()

  @Test
  fun `skip restore warning - CANCEL only dismisses`() {
    showSkipWarning()

    composeTestRule.onNodeWithText(action(android.R.string.cancel)).performClick()

    assertThat(calls).containsExactly("dismiss")
  }

  @Test
  fun `skip restore warning - SKIP RESTORE dismisses, then skips`() {
    showSkipWarning()

    composeTestRule.onNodeWithText(action(R.string.SelectRestoreMethodFragment__skip_restore)).performClick()

    assertThat(calls).containsExactly("dismiss", "skip")
  }

  @Test
  fun `skip restore warning - BACK cancels`() {
    showSkipWarning()

    composeTestRule.onNodeWithContentDescription(string(R.string.SelectRestoreMethodFragment__back_to_restore_options)).performClick()

    assertThat(calls).containsExactly("dismiss")
  }

  @Test
  fun `no recovery key - LEARN MORE dismisses, then opens the article`() {
    showNoBackupKey(showSecondParagraph = true)

    composeTestRule.onNodeWithText(action(R.string.EnterBackupKey_learn_more)).performScrollTo().performClick()

    assertThat(calls).containsExactly("dismiss", "learn more")
  }

  @Test
  fun `no recovery key - SKIP RESTORE dismisses, then skips`() {
    showNoBackupKey(showSecondParagraph = true)

    composeTestRule.onNodeWithText(action(R.string.EnterBackupKey_skip_restore)).performScrollTo().performClick()

    assertThat(calls).containsExactly("dismiss", "skip")
  }

  @Test
  fun `no recovery key - BACK only closes`() {
    showNoBackupKey(showSecondParagraph = true)

    composeTestRule.onNodeWithContentDescription(string(R.string.EnterBackupKey__back_to_recovery_key)).performClick()

    assertThat(calls).containsExactly("dismiss")
  }

  @Test
  fun `no recovery key - the second paragraph is only for the manual restore`() {
    showNoBackupKey(showSecondParagraph = false)

    composeTestRule.onNodeWithText(string(R.string.EnterBackupKey_no_key_paragraph_1)).assertExists()
    composeTestRule.onNodeWithText(string(R.string.EnterBackupKey_no_key_paragraph_2), substring = true).assertDoesNotExist()
  }

  @Test
  fun `no recovery key - the manual restore shows both paragraphs`() {
    showNoBackupKey(showSecondParagraph = true)

    composeTestRule.onNodeWithText(string(R.string.EnterBackupKey_no_key_paragraph_1), substring = true).assertExists()
    composeTestRule.onNodeWithText(string(R.string.EnterBackupKey_no_key_paragraph_2), substring = true).assertExists()
  }

  @Test
  fun `recovery key screen - NO RECOVERY KEY opens the notice in place of the sheet`() {
    composeTestRule.setContent {
      EnterBackupKeyScreen(
        isDisplayedDuringManualRestore = false,
        enteredText = "",
        inProgress = false,
        isBackupKeyValid = false,
        chunkLength = 4,
        aepValidationError = null
      ) {}
    }

    composeTestRule.onNodeWithText(string(R.string.EnterBackupKey_no_key_paragraph_1)).assertDoesNotExist()

    composeTestRule.onNodeWithText(action(R.string.EnterBackupKey_no_backup_key)).performClick()

    composeTestRule.onNodeWithText(string(R.string.EnterBackupKey_no_key_paragraph_1)).assertExists()
  }

  @Test
  fun `incorrect key - RECOVERY KEY HELP dismisses, then opens help`() {
    showRegistrationError(RegisterAccountResult.IncorrectRecoveryPassword(RuntimeException()))

    composeTestRule.onNodeWithText(action(R.string.EnterBackupKey_backup_key_help)).performClick()

    assertThat(calls).containsExactly("dismiss", "help")
  }

  @Test
  fun `incorrect key - TRY AGAIN only dismisses`() {
    showRegistrationError(RegisterAccountResult.IncorrectRecoveryPassword(RuntimeException()))

    composeTestRule.onNodeWithText(action(R.string.EnterBackupKey_try_again)).performClick()

    assertThat(calls).containsExactly("dismiss")
  }

  @Test
  fun `incorrect key - BACK only dismisses`() {
    showRegistrationError(RegisterAccountResult.IncorrectRecoveryPassword(RuntimeException()))

    composeTestRule.onNodeWithContentDescription(string(R.string.EnterBackupKey__back_to_recovery_key)).performClick()

    assertThat(calls).containsExactly("dismiss")
  }

  @Test
  fun `other registration error - OK dismisses, under the step title`() {
    showRegistrationError(RegisterAccountResult.UnknownError(RuntimeException()))

    composeTestRule.onNodeWithText(string(R.string.EnterBackupKey_title)).assertExists()
    composeTestRule.onNodeWithText(string(R.string.RegistrationActivity_error_connecting_to_service)).assertExists()
    composeTestRule.onNodeWithText(action(android.R.string.ok)).performClick()

    assertThat(calls).containsExactly("dismiss")
  }

  @Test
  fun `no registration error shows nothing`() {
    composeTestRule.setContent {
      RegistrationErrorDialogs(
        showRegistrationError = false,
        registerAccountResult = null,
        onRegistrationErrorDismiss = { calls += "dismiss" },
        onBackupKeyHelp = { calls += "help" }
      )
    }

    composeTestRule.onNodeWithText(action(android.R.string.ok)).assertDoesNotExist()
    assertThat(calls).isEmpty()
  }

  private fun showSkipWarning() {
    composeTestRule.setContent {
      SkipRestoreWarning(
        onSkip = { calls += "skip" },
        onDismiss = { calls += "dismiss" }
      )
    }
  }

  private fun showNoBackupKey(showSecondParagraph: Boolean) {
    composeTestRule.setContent {
      NoBackupKeyNotice(
        showSecondParagraph = showSecondParagraph,
        onLearnMore = { calls += "learn more" },
        onSkip = { calls += "skip" },
        onDismiss = { calls += "dismiss" }
      )
    }
  }

  private fun showRegistrationError(result: RegisterAccountResult) {
    composeTestRule.setContent {
      RegistrationErrorDialogs(
        showRegistrationError = true,
        registerAccountResult = result,
        onRegistrationErrorDismiss = { calls += "dismiss" },
        onBackupKeyHelp = { calls += "help" }
      )
    }
  }

  private fun string(@StringRes id: Int): String = RuntimeEnvironment.getApplication().getString(id)

  /** Notice and bottom-bar actions are shown in capitals. */
  private fun action(@StringRes id: Int): String = string(id).uppercase(Locale.getDefault())
}
