/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore.local

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.thoughtcrime.securesms.R

enum class RestoreLocalBackupDialog {
  FAILED_TO_LOAD_ARCHIVE,
  SKIP_RESTORE_WARNING,
  CONFIRM_DIFFERENT_ACCOUNT
}

/**
 * The local-backup flow's dialogs, as full-screen Light notices drawn over the current destination.
 *
 * Each exit maps onto the callback the `Dialogs.*` version ran:
 *
 * - FAILED_TO_LOAD_ARCHIVE: OK and system back both ran [onDismiss] (it was a message dialog whose
 *   dismiss request was the same as its button), and still do. BACK is offered for it. It had no
 *   title, so the top bar carries the flow's.
 * - SKIP_RESTORE_WARNING: SKIP RESTORE runs [onDialogConfirmed]. CANCEL used to run only the
 *   dialog's default no-op dismiss, which left the warning latched on screen with no way off it; it
 *   now runs [onDismiss], which is what the host passes for clearing the dialog, and so does back.
 * - CONFIRM_DIFFERENT_ACCOUNT: RESTORE runs [onDialogConfirmed] and CANCEL runs [onDialogDenied]. Its
 *   dismiss request was a no-op, so system back is swallowed and there is no BACK glyph: both
 *   choices navigate, and neither is a "never mind".
 */
@Composable
fun RestoreLocalBackupDialogDisplay(
  dialog: RestoreLocalBackupDialog?,
  onDialogConfirmed: (RestoreLocalBackupDialog) -> Unit,
  onDialogDenied: (RestoreLocalBackupDialog) -> Unit,
  onDismiss: () -> Unit
) {
  val backDescription = stringResource(R.string.RegistrationScreen__back_to_previous_screen)

  when (dialog) {
    RestoreLocalBackupDialog.FAILED_TO_LOAD_ARCHIVE -> {
      LightRestoreNotice(
        // The message dialog had no title; the top bar carries the flow's own.
        title = stringResource(R.string.SelectLocalBackupTypeScreen__restore_on_device_backup),
        message = stringResource(R.string.RestoreLocalBackupDialog__failed_to_load_archive),
        actions = listOf(
          LightRestoreNoticeAction(stringResource(R.string.RestoreLocalBackupDialog__ok), onDismiss)
        ),
        onBack = onDismiss,
        backDescription = backDescription
      )
    }

    RestoreLocalBackupDialog.SKIP_RESTORE_WARNING -> {
      LightRestoreNotice(
        title = stringResource(R.string.RestoreLocalBackupDialog__skip_restore),
        message = stringResource(R.string.RestoreLocalBackupDialog__skip_restore_body),
        actions = listOf(
          LightRestoreNoticeAction(stringResource(android.R.string.cancel), onDismiss),
          LightRestoreNoticeAction(stringResource(R.string.RestoreLocalBackupDialog__skip_restore_confirm)) {
            onDialogConfirmed(RestoreLocalBackupDialog.SKIP_RESTORE_WARNING)
          }
        ),
        onBack = onDismiss,
        backDescription = backDescription
      )
    }

    RestoreLocalBackupDialog.CONFIRM_DIFFERENT_ACCOUNT -> {
      LightRestoreNotice(
        title = stringResource(R.string.RestoreLocalBackupDialog__restore_to_new_account),
        message = stringResource(R.string.RestoreLocalBackupDialog__restore_to_new_account_body),
        actions = listOf(
          LightRestoreNoticeAction(stringResource(android.R.string.cancel)) {
            onDialogDenied(RestoreLocalBackupDialog.CONFIRM_DIFFERENT_ACCOUNT)
          },
          LightRestoreNoticeAction(stringResource(R.string.RestoreLocalBackupDialog__restore)) {
            onDialogConfirmed(RestoreLocalBackupDialog.CONFIRM_DIFFERENT_ACCOUNT)
          }
        ),
        onBack = null
      )
    }

    null -> return
  }
}
