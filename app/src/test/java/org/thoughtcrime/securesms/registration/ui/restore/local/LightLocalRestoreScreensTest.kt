/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore.local

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.addCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.util.bytes
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.contactsupport.ContactSupportCallbacks
import org.thoughtcrime.securesms.components.contactsupport.ContactSupportViewModel

/**
 * The local-backup flow's Light surfaces: the full screens that replaced its Material bottom sheets,
 * the notices that replaced its `Dialogs.*` alerts, and `RestoreLocalBackupActivity`'s progress and
 * completion states. What is pinned is which callback each exit reaches -- the hosts' callbacks are
 * one-liners onto the view models, unchanged by the conversion.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class LightLocalRestoreScreensTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  private val context = RuntimeEnvironment.getApplication()
  private val calls = mutableListOf<String>()
  private lateinit var backDispatcher: OnBackPressedDispatcher

  private fun string(id: Int): String = context.getString(id)
  private fun upper(id: Int): String = string(id).uppercase()

  private fun setContent(content: @Composable () -> Unit) {
    composeTestRule.setContent {
      backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
      content()
    }
  }

  // region RestoreLocalBackupDialogDisplay

  private fun showDialog(dialog: RestoreLocalBackupDialog) {
    setContent {
      RestoreLocalBackupDialogDisplay(
        dialog = dialog,
        onDialogConfirmed = { calls += "confirmed:$it" },
        onDialogDenied = { calls += "denied:$it" },
        onDismiss = { calls += "dismiss" }
      )
    }
  }

  @Test
  fun `failed to load archive OK dismisses`() {
    showDialog(RestoreLocalBackupDialog.FAILED_TO_LOAD_ARCHIVE)

    composeTestRule.onNodeWithText(string(R.string.RestoreLocalBackupDialog__failed_to_load_archive)).assertIsDisplayed()
    composeTestRule.onNodeWithText(upper(R.string.RestoreLocalBackupDialog__ok)).performClick()

    assertThat(calls).containsExactly("dismiss")
  }

  @Test
  fun `failed to load archive system back dismisses`() {
    showDialog(RestoreLocalBackupDialog.FAILED_TO_LOAD_ARCHIVE)

    composeTestRule.runOnIdle { backDispatcher.onBackPressed() }

    assertThat(calls).containsExactly("dismiss")
  }

  @Test
  fun `skip restore warning confirms or cancels`() {
    showDialog(RestoreLocalBackupDialog.SKIP_RESTORE_WARNING)

    composeTestRule.onNodeWithText(upper(R.string.RestoreLocalBackupDialog__skip_restore_confirm)).performClick()
    composeTestRule.onNodeWithText(upper(android.R.string.cancel)).performClick()
    composeTestRule.onNodeWithContentDescription(string(R.string.RegistrationScreen__back_to_previous_screen)).performClick()

    assertThat(calls).containsExactly("confirmed:SKIP_RESTORE_WARNING", "dismiss", "dismiss")
  }

  @Test
  fun `different account notice confirms, denies, and swallows back`() {
    showDialog(RestoreLocalBackupDialog.CONFIRM_DIFFERENT_ACCOUNT)

    composeTestRule.onAllNodesWithContentDescription(string(R.string.RegistrationScreen__back_to_previous_screen)).assertCountEquals(0)
    composeTestRule.runOnIdle { backDispatcher.onBackPressed() }
    composeTestRule.onNodeWithText(upper(R.string.RestoreLocalBackupDialog__restore)).performClick()
    composeTestRule.onNodeWithText(upper(android.R.string.cancel)).performClick()

    assertThat(calls).containsExactly("confirmed:CONFIRM_DIFFERENT_ACCOUNT", "denied:CONFIRM_DIFFERENT_ACCOUNT")
  }

  @Test
  fun `a notice consumes taps in its empty space`() {
    var underneath = 0
    setContent {
      Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().clickable { underneath++ })
        RestoreLocalBackupDialogDisplay(RestoreLocalBackupDialog.SKIP_RESTORE_WARNING, {}, {}, {})
      }
    }

    composeTestRule.onNodeWithTag(LIGHT_RESTORE_NOTICE_TAG).performTouchInput {
      click(Offset(x = 4f, y = height - 4f))
    }

    assertThat(underneath).isEqualTo(0)
  }

  // endregion

  // region Former sheets

  @Test
  fun `folder instructions continue and back`() {
    setContent {
      SelectYourBackupFolderScreen(onContinueClick = { calls += "continue" }, onBackClick = { calls += "back" })
    }

    composeTestRule.onNodeWithText(string(R.string.SelectInstructionsSheet__select_your_backup_folder)).assertIsDisplayed()
    composeTestRule.onNodeWithText(upper(R.string.SelectInstructionsSheet__continue_button)).performClick()
    composeTestRule.onNodeWithContentDescription(string(R.string.RegistrationScreen__back_to_previous_screen)).performClick()

    assertThat(calls).containsExactly("continue", "back")
  }

  @Test
  fun `file instructions continue`() {
    setContent {
      SelectYourBackupFileScreen(onContinueClick = { calls += "continue" }, onBackClick = { calls += "back" })
    }

    composeTestRule.onNodeWithText(string(R.string.SelectInstructionsSheet__select_your_backup_file)).assertIsDisplayed()
    composeTestRule.onNodeWithText(upper(R.string.SelectInstructionsSheet__continue_button)).performClick()

    assertThat(calls).containsExactly("continue")
  }

  @Test
  fun `backup list continues only after picking a different backup`() {
    val latest = SelectableBackup(timestamp = 2L, backupTime = "Today 3:38am")
    val older = SelectableBackup(timestamp = 1L, backupTime = "August 13 3:21am")
    val selected = mutableListOf<SelectableBackup>()
    setContent {
      SelectLocalBackupListScreen(
        selectedBackup = latest,
        selectableBackups = persistentListOf(latest, older),
        onBackupSelected = { selected += it },
        onBackClick = { calls += "back" }
      )
    }

    val continueLabel = upper(R.string.SelectLocalBackupSheet__continue_button)
    composeTestRule.onNodeWithText(continueLabel).assertIsNotEnabled()

    composeTestRule.onNodeWithText("August 13 3:21am").performClick()
    composeTestRule.onNodeWithText(continueLabel).assertIsEnabled().performClick()

    assertThat(selected).containsExactly(older)
  }

  @Test
  fun `no recovery key skip, learn more and back`() {
    setContent {
      NoRecoveryKeyScreen(
        onSkipAndDontRestoreClick = { calls += "skip" },
        onBackClick = { calls += "back" },
        onLearnMoreClick = { calls += "learn" }
      )
    }

    composeTestRule.onNodeWithText(upper(R.string.LearnMoreTextView_learn_more)).performClick()
    composeTestRule.onNodeWithText(upper(R.string.EnterBackupKey_skip_and_dont_restore)).performClick()
    composeTestRule.onNodeWithContentDescription(string(R.string.LightRestore__back_to_recovery_key)).performClick()
    composeTestRule.runOnIdle { backDispatcher.onBackPressed() }

    assertThat(calls).containsExactly("learn", "skip", "back", "back")
  }

  // endregion

  // region RestoreLocalBackupActivity

  private fun showActivityScreen(state: RestoreLocalBackupScreenState, contactSupport: Boolean = false) {
    setContent {
      RestoreLocalBackupScreen(
        state = state,
        onFailureDialogConfirm = { calls += "failureOk" },
        onConfirmBackupLocation = { calls += "useCurrent" },
        onChangeBackupLocation = { calls += "chooseNew" },
        onDisableBackups = { calls += "disable" },
        onLocalBackupsDisabledDialogConfirm = { calls += "turnOn" },
        onLocalBackupsDisabledDialogDeny = { calls += "notNow" },
        onContactSupportClick = { calls += "support" },
        contactSupportState = ContactSupportViewModel.ContactSupportState(show = contactSupport),
        contactSupportCallbacks = ContactSupportCallbacks.Empty
      )
    }
  }

  @Test
  fun `restoring with byte counts shows status, a determinate bar and the detail line`() {
    showActivityScreen(
      RestoreLocalBackupScreenState(
        restorePhase = RestorePhase.RESTORING,
        bytesRead = 40L.bytes,
        totalBytes = 100L.bytes,
        progress = 0.4f
      )
    )

    composeTestRule.onNodeWithText(string(R.string.RestoreLocalBackupActivity__restoring_backup)).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.RestoreLocalBackupActivity__depending_on_the_size)).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.RestoreLocalBackupActivity__restoring_messages)).assertIsDisplayed()
    composeTestRule.onNodeWithTag(LIGHT_RESTORE_STATUS_TAG)
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0.4f, 0f..1f)))
    composeTestRule.onAllNodesWithText("(40%)", substring = true).assertCountEquals(1)
    composeTestRule.onAllNodesWithTag(LIGHT_RESTORE_NOTICE_TAG).assertCountEquals(0)
  }

  @Test
  fun `restoring before any progress is indeterminate and has no detail`() {
    showActivityScreen(RestoreLocalBackupScreenState(restorePhase = RestorePhase.RESTORING))

    composeTestRule.onNodeWithTag(LIGHT_RESTORE_STATUS_TAG)
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate))
    composeTestRule.onAllNodesWithText("%)", substring = true).assertCountEquals(0)
  }

  @Test
  fun `finalizing shows its status line`() {
    showActivityScreen(RestoreLocalBackupScreenState(restorePhase = RestorePhase.FINALIZING))

    composeTestRule.onNodeWithText(string(R.string.RestoreLocalBackupActivity__finalizing)).assertIsDisplayed()
  }

  @Test
  fun `progress leaves system back alone`() {
    var activityBack = 0
    composeTestRule.setContent {
      backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
      // Registered before the first composition's effects, so any back handler the screen installed
      // would take precedence over it -- which is exactly what must not happen during progress.
      remember { backDispatcher.addCallback { activityBack++ } }
      RestoreLocalBackupScreen(
        state = RestoreLocalBackupScreenState(restorePhase = RestorePhase.RESTORING),
        onFailureDialogConfirm = {},
        onConfirmBackupLocation = {},
        onChangeBackupLocation = {},
        onDisableBackups = {},
        onLocalBackupsDisabledDialogConfirm = {},
        onLocalBackupsDisabledDialogDeny = {},
        onContactSupportClick = {},
        contactSupportState = ContactSupportViewModel.ContactSupportState(),
        contactSupportCallbacks = ContactSupportCallbacks.Empty
      )
    }

    composeTestRule.runOnIdle { backDispatcher.onBackPressed() }

    assertThat(activityBack).isEqualTo(1)
  }

  @Test
  fun `failure notice offers contact support and OK and swallows back`() {
    showActivityScreen(RestoreLocalBackupScreenState(restorePhase = RestorePhase.FAILED))

    composeTestRule.onNodeWithText(string(R.string.RestoreLocalBackupActivity__error_occurred_while_restoring)).assertIsDisplayed()
    composeTestRule.runOnIdle { backDispatcher.onBackPressed() }
    composeTestRule.onNodeWithText(upper(R.string.RestoreLocalBackupActivity__contact_support)).performClick()
    composeTestRule.onNodeWithText(upper(android.R.string.ok)).performClick()

    assertThat(calls).containsExactly("support", "failureOk")
  }

  @Test
  fun `choose a folder maps each choice, and back disables backups`() {
    showActivityScreen(
      RestoreLocalBackupScreenState(
        restorePhase = RestorePhase.COMPLETE,
        dialog = RestoreLocalBackupActivityDialog.CONFIRM_BACKUP_LOCATION
      )
    )

    composeTestRule.onAllNodesWithContentDescription(string(R.string.RegistrationScreen__back_to_previous_screen)).assertCountEquals(0)
    composeTestRule.onNodeWithText(upper(R.string.RestoreLocalBackupActivity__use_current_folder)).performClick()
    composeTestRule.onNodeWithText(upper(R.string.RestoreLocalBackupActivity__choose_new_folder)).performClick()
    composeTestRule.onNodeWithText(upper(R.string.RestoreLocalBackupActivity__disable_backups)).performClick()
    composeTestRule.runOnIdle { backDispatcher.onBackPressed() }

    assertThat(calls).containsExactly("useCurrent", "chooseNew", "disable", "disable")
  }

  @Test
  fun `backups disabled maps turn on and not now, and swallows back`() {
    showActivityScreen(
      RestoreLocalBackupScreenState(
        restorePhase = RestorePhase.COMPLETE,
        dialog = RestoreLocalBackupActivityDialog.LOCAL_BACKUPS_DISABLED
      )
    )

    composeTestRule.runOnIdle { backDispatcher.onBackPressed() }
    composeTestRule.onNodeWithText(upper(R.string.RestoreLocalBackupActivity__not_now)).performClick()
    composeTestRule.onNodeWithText(upper(R.string.RestoreLocalBackupActivity__turn_on_now)).performClick()

    assertThat(calls).containsExactly("notNow", "turnOn")
  }

  @Test
  fun `no notice while restoring`() {
    showActivityScreen(RestoreLocalBackupScreenState(restorePhase = RestorePhase.RESTORING))

    composeTestRule.onAllNodesWithTag(LIGHT_RESTORE_NOTICE_TAG).assertCountEquals(0)
    assertThat(calls).isEmpty()
  }

  // endregion
}
