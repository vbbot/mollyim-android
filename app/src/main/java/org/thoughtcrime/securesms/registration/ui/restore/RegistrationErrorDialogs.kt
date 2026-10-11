/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.thoughtcrime.securesms.registration.ui.phonenumber.PhoneNumberNoticeAction
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationNoticeDialog

/**
 * Shared error notices for registration failures during backup key entry.
 * Used by both remote and local backup restore flows.
 *
 * Full-screen Light notices in place of the Material dialogs these were. Neither dialog was
 * cancelable; a notice cannot refuse BACK, so BACK runs [onRegistrationErrorDismiss] -- the dismissal
 * both dialogs' buttons ran first -- and nothing else. The message-only error has no title upstream,
 * so the top bar names the step instead of standing empty.
 */
@Composable
fun RegistrationErrorDialogs(
  showRegistrationError: Boolean,
  registerAccountResult: RegisterAccountResult?,
  onRegistrationErrorDismiss: () -> Unit,
  onBackupKeyHelp: () -> Unit
) {
  if (!showRegistrationError) return

  val backDescription = stringResource(R.string.EnterBackupKey__back_to_recovery_key)

  if (registerAccountResult is RegisterAccountResult.IncorrectRecoveryPassword) {
    // SimpleAlertDialog's order: its confirm ran onDismiss() then onConfirm() (a no-op here), its
    // dismiss ran onDismiss() then onDeny(). Listed as the buttons were, dismiss before confirm.
    RegistrationNoticeDialog(
      title = stringResource(R.string.EnterBackupKey_incorrect_backup_key_title),
      message = stringResource(R.string.EnterBackupKey_incorrect_backup_key_message),
      actions = listOf(
        PhoneNumberNoticeAction(stringResource(R.string.EnterBackupKey_backup_key_help)) {
          onRegistrationErrorDismiss()
          onBackupKeyHelp()
        },
        PhoneNumberNoticeAction(stringResource(R.string.EnterBackupKey_try_again)) {
          onRegistrationErrorDismiss()
        }
      ),
      onBack = onRegistrationErrorDismiss,
      backDescription = backDescription
    )
  } else {
    val message = when (registerAccountResult) {
      is RegisterAccountResult.RateLimited -> stringResource(R.string.RegistrationActivity_you_have_made_too_many_attempts_please_try_again_later)
      else -> stringResource(R.string.RegistrationActivity_error_connecting_to_service)
    }

    RegistrationNoticeDialog(
      title = stringResource(R.string.EnterBackupKey_title),
      message = message,
      actions = listOf(
        PhoneNumberNoticeAction(stringResource(android.R.string.ok), onRegistrationErrorDismiss)
      ),
      onBack = onRegistrationErrorDismiss,
      backDescription = backDescription
    )
  }
}
