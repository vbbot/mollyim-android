/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.Launchers
import org.signal.core.ui.contracts.OpenDocumentContract
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.registration.ui.restore.EnterBackupKeyViewModel

/**
 * Handles the restoration flow for V2 backups. Can also launch into V1 backup flow if needed.
 *
 * Every destination is a full Light screen. The instruction, backup-choice and no-recovery-key
 * destinations used to be Material bottom sheets over the previous screen; they are now ordinary
 * entries on the same back stack, so system back (and each screen's BACK glyph) pops them exactly
 * as swiping a sheet away did, and every action that used to dismiss its sheet pops it instead.
 */
@Composable
fun RestoreLocalBackupNavDisplay(
  state: RestoreLocalBackupState,
  callback: RestoreLocalBackupCallback,
  isRegistrationInProgress: Boolean,
  enterBackupKeyState: EnterBackupKeyViewModel.EnterBackupKeyState,
  enteredText: String
) {
  val backstack = rememberNavBackStack(RestoreLocalBackupNavKey.SelectLocalBackupTypeScreen)
  val backPressedDispatcher = LocalOnBackPressedDispatcherOwner.current
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val folderLauncher = Launchers.rememberOpenDocumentTreeLauncher {
    if (it != null) {
      val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
      context.contentResolver.takePersistableUriPermission(it, takeFlags)

      scope.launch {
        if (callback.setSelectedBackupDirectory(context, it)) {
          backstack.add(RestoreLocalBackupNavKey.SelectLocalBackupScreen)
        }
      }
    }
  }

  val fileLauncher = Launchers.rememberOpenDocumentLauncher {
    if (it != null) {
      callback.routeToLegacyBackupRestoration(it)
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    NavDisplay(
      backStack = backstack,
      entryProvider = entryProvider {
        entry<RestoreLocalBackupNavKey.SelectLocalBackupTypeScreen> {
          SelectLocalBackupTypeScreen(
            onSelectBackupFolderClick = {
              backstack.add(RestoreLocalBackupNavKey.FolderInstructionSheet)
            },
            onSelectBackupFileClick = {
              backstack.add(RestoreLocalBackupNavKey.FileInstructionSheet)
            },
            onCancelClick = {
              backPressedDispatcher?.onBackPressedDispatcher?.onBackPressed()
            }
          )
        }

        entry<RestoreLocalBackupNavKey.FileInstructionSheet> {
          SelectYourBackupFileScreen(
            onContinueClick = {
              fileLauncher.launch(OpenDocumentContract.Input())
            },
            onBackClick = { backstack.removeLastOrNull() }
          )
        }

        entry<RestoreLocalBackupNavKey.FolderInstructionSheet> {
          SelectYourBackupFolderScreen(
            onContinueClick = {
              folderLauncher.launch(null)
            },
            onBackClick = { backstack.removeLastOrNull() }
          )
        }

        entry<RestoreLocalBackupNavKey.SelectLocalBackupScreen> {
          SelectLocalBackupScreen(
            selectedBackup = requireNotNull(state.selectedBackup) { "No chosen backup." },
            isSelectedBackupLatest = state.selectedBackup == state.selectableBackups.firstOrNull(),
            onRestoreBackupClick = {
              backstack.add(RestoreLocalBackupNavKey.EnterLocalBackupKeyScreen)
            },
            onCancelClick = {
              backPressedDispatcher?.onBackPressedDispatcher?.onBackPressed()
            },
            onChooseADifferentBackupClick = {
              backstack.add(RestoreLocalBackupNavKey.SelectLocalBackupSheet)
            }
          )
        }

        entry<RestoreLocalBackupNavKey.SelectLocalBackupSheet> {
          SelectLocalBackupListScreen(
            selectedBackup = requireNotNull(state.selectedBackup) { "No chosen backup." },
            selectableBackups = state.selectableBackups,
            onBackupSelected = {
              callback.setSelectedBackup(it)
              backstack.removeLastOrNull()
            },
            onBackClick = { backstack.removeLastOrNull() }
          )
        }

        entry<RestoreLocalBackupNavKey.EnterLocalBackupKeyScreen> {
          EnterLocalBackupKeyScreen(
            enteredText = enteredText,
            isRegistrationInProgress = isRegistrationInProgress,
            isBackupKeyValid = enterBackupKeyState.backupKeyValid,
            aepValidationError = enterBackupKeyState.aepValidationError,
            onBackupKeyChanged = callback::onBackupKeyChanged,
            onNextClicked = callback::submitBackupKey,
            onNoBackupKeyClick = {
              backstack.add(RestoreLocalBackupNavKey.NoRecoveryKeySheet)
            },
            showRegistrationError = enterBackupKeyState.showRegistrationError,
            registerAccountResult = enterBackupKeyState.registerAccountResult,
            onRegistrationErrorDismiss = callback::clearRegistrationError,
            onBackupKeyHelp = callback::onBackupKeyHelp
          )
        }

        entry<RestoreLocalBackupNavKey.NoRecoveryKeySheet> {
          NoRecoveryKeyScreen(
            onSkipAndDontRestoreClick = {
              backstack.removeLastOrNull()
              callback.displaySkipRestoreWarning()
            },
            onBackClick = { backstack.removeLastOrNull() },
            // Upstream left this as a TODO, which shipped a button that did nothing. It now opens the
            // same backups support article the recovery-key help link does.
            onLearnMoreClick = callback::onBackupKeyHelp
          )
        }
      }
    )

    RestoreLocalBackupDialogDisplay(
      dialog = state.dialog,
      onDialogConfirmed = {
        when (it) {
          RestoreLocalBackupDialog.SKIP_RESTORE_WARNING -> callback.skipRestore()
          RestoreLocalBackupDialog.CONFIRM_DIFFERENT_ACCOUNT -> callback.confirmRestoreWithDifferentAccount()
          else -> Unit
        }
      },
      onDialogDenied = {
        when (it) {
          RestoreLocalBackupDialog.CONFIRM_DIFFERENT_ACCOUNT -> callback.denyRestoreWithDifferentAccount()
          else -> Unit
        }
      },
      onDismiss = callback::clearDialog
    )

    if (state.isLoadingBackupDirectory) {
      // Was an M3 spinner floated over the current screen. The Light grammar has no spinner, so the
      // wait is a status screen; it swallows taps so the screen underneath cannot be used mid-load.
      LightRestoreProgress(
        title = stringResource(R.string.SelectLocalBackupTypeScreen__restore_on_device_backup),
        status = stringResource(R.string.LinkDeviceFragment__loading),
        modifier = Modifier.consumeAllPointerEvents()
      )
    }
  }
}

data class RestoreLocalBackupState(
  val dialog: RestoreLocalBackupDialog? = null,
  val selectedBackup: SelectableBackup? = null,
  val selectableBackups: PersistentList<SelectableBackup> = persistentListOf(),
  val isLoadingBackupDirectory: Boolean = false
)

interface RestoreLocalBackupCallback {
  fun setSelectedBackup(backup: SelectableBackup)
  suspend fun setSelectedBackupDirectory(context: Context, uri: Uri): Boolean
  fun displaySkipRestoreWarning()
  fun clearDialog()
  fun skipRestore()
  fun confirmRestoreWithDifferentAccount()
  fun denyRestoreWithDifferentAccount()
  fun submitBackupKey()
  fun routeToLegacyBackupRestoration(uri: Uri)
  fun onBackupKeyChanged(key: String)
  fun clearRegistrationError()
  fun onBackupKeyHelp()

  object Empty : RestoreLocalBackupCallback {
    override fun setSelectedBackup(backup: SelectableBackup) = Unit
    override suspend fun setSelectedBackupDirectory(context: Context, uri: Uri) = false
    override fun displaySkipRestoreWarning() = Unit
    override fun clearDialog() = Unit
    override fun skipRestore() = Unit
    override fun confirmRestoreWithDifferentAccount() = Unit
    override fun denyRestoreWithDifferentAccount() = Unit
    override fun submitBackupKey() = Unit
    override fun routeToLegacyBackupRestoration(uri: Uri) = Unit
    override fun onBackupKeyChanged(key: String) = Unit
    override fun clearRegistrationError() = Unit
    override fun onBackupKeyHelp() = Unit
  }
}
