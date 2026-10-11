/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore.restorelocalbackup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.fonts.MonoTypeface
import org.thoughtcrime.securesms.registration.ui.restore.BackupKeyVisualTransformation
import org.thoughtcrime.securesms.registration.ui.restore.local.LightRestoreNotice
import org.thoughtcrime.securesms.registration.ui.restore.local.LightRestoreNoticeAction
import org.thoughtcrime.securesms.registration.ui.restore.local.consumeAllPointerEvents
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationAction
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationBack
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationBottomActions
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationProgress
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationTextField
import org.thoughtcrime.securesms.util.BackupUtil

/** Spacing the passphrase in groups of five, as `PassphraseAsYouTypeFormatter` did. */
private const val PASSPHRASE_GROUP_SIZE = 5

/** What the legacy (V1) restore screen shows; owned by [RestoreLocalBackupFragment]. */
data class RestoreLocalBackupV1UiState(
  val backupCreated: String? = null,
  val backupCreatedDescription: String? = null,
  val backupSize: String? = null,
  val restoreInProgress: Boolean = false,
  val progressText: String? = null,
  val enteringPassphrase: Boolean = false,
  val confirmingBackupFolder: Boolean = false
)

/**
 * The legacy single-file restore, in the registration grammar. Was a 28sp headline, a circular
 * progress pill button, a secondary pill, a `MaterialAlertDialog` holding the passphrase field and a
 * second one asking for a backup folder.
 *
 * - RESTORE BACKUP opens the passphrase entry ([onRestoreClick]); while a restore runs it is the
 *   ellipsis progress mark and CANCEL is gone, as the layout's spinner and invisible cancel were.
 * - CANCEL and BACK run [onCancelClick] (the layout's cancel: navigate up). While restoring there is
 *   no BACK glyph; system back is unchanged.
 * - The passphrase entry is a full screen over this one: RESTORE is live once something is typed and
 *   runs [onPassphraseSubmitted]; CANCEL, BACK and system back close it, as the dialog's cancel did.
 * - The folder question is a notice with no way out but its two answers, as the non-cancelable
 *   dialog was: NOT NOW -> [onNotNow], CHOOSE FOLDER -> [onChooseFolder].
 */
@Composable
fun RestoreLocalBackupV1Screen(
  state: RestoreLocalBackupV1UiState,
  onRestoreClick: () -> Unit,
  onCancelClick: () -> Unit,
  onPassphraseSubmitted: (String) -> Unit,
  onPassphraseDismissed: () -> Unit,
  onNotNow: () -> Unit,
  onChooseFolder: () -> Unit
) {
  Box(modifier = Modifier.fillMaxSize()) {
    RegistrationScreen(
      title = stringResource(R.string.RegistrationActivity_restore_from_backup),
      subtitle = stringResource(R.string.RegistrationActivity_restore_your_messages_and_media_from_a_local_backup),
      bottomContent = {
        Box(modifier = Modifier.fillMaxWidth()) {
          RegistrationBottomActions(
            left = if (state.restoreInProgress) null else RegistrationAction(stringResource(R.string.RegistrationActivity_cancel), onCancelClick),
            right = if (state.restoreInProgress) null else RegistrationAction(stringResource(R.string.registration_activity__restore_backup), onRestoreClick)
          )
          if (state.restoreInProgress) {
            RegistrationProgress(
              modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 2.5f.gridUnitsAsDp())
            )
          }
        }
      },
      back = if (state.restoreInProgress) {
        null
      } else {
        RegistrationBack(
          contentDescription = stringResource(R.string.RegistrationScreen__back_to_previous_screen),
          onBack = onCancelClick
        )
      }
    ) {
      if (state.backupCreated != null) {
        LightText(
          text = state.backupCreated,
          variant = LightTextVariant.Copy,
          modifier = Modifier
            .padding(bottom = 0.5f.gridUnitsAsDp())
            .semantics { contentDescription = state.backupCreatedDescription ?: state.backupCreated }
        )
      }

      if (state.backupSize != null) {
        LightText(
          text = state.backupSize,
          variant = LightTextVariant.Copy,
          modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp())
        )
      }

      if (state.progressText != null) {
        LightText(
          text = state.progressText,
          variant = LightTextVariant.Detail,
          modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp())
        )
      }
    }

    if (state.enteringPassphrase) {
      // Opaque to touches, so nothing on the restore screen underneath can take a tap.
      Box(modifier = Modifier.fillMaxSize().consumeAllPointerEvents()) {
        EnterBackupPassphraseScreen(
          onSubmit = onPassphraseSubmitted,
          onDismiss = onPassphraseDismissed
        )
      }
    }

    if (state.confirmingBackupFolder) {
      LightRestoreNotice(
        title = stringResource(R.string.RestoreBackupFragment__restore_complete),
        message = stringResource(R.string.RestoreBackupFragment__to_continue_using_backups_please_choose_a_folder),
        actions = listOf(
          LightRestoreNoticeAction(stringResource(R.string.RestoreBackupFragment__not_now), onNotNow),
          LightRestoreNoticeAction(stringResource(R.string.RestoreBackupFragment__choose_folder), onChooseFolder)
        ),
        onBack = null
      )
    }
  }
}

/**
 * The passphrase prompt as a full Light screen with the LP3's system keyboard. The value is digits
 * only in practice (the dialog asked for a number keyboard) and is capped at the passphrase length,
 * as the formatter capped it; the grouping is visual only, so the passphrase handed on is exactly
 * what was typed.
 */
@Composable
private fun EnterBackupPassphraseScreen(
  onSubmit: (String) -> Unit,
  onDismiss: () -> Unit
) {
  var passphrase by rememberSaveable { mutableStateOf("") }
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusRequester = remember { FocusRequester() }
  val visualTransformation = remember { BackupKeyVisualTransformation(chunkSize = PASSPHRASE_GROUP_SIZE) }

  val submit = {
    if (passphrase.isNotBlank()) {
      keyboardController?.hide()
      onSubmit(passphrase)
    }
  }

  BackHandler(onBack = onDismiss)

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }

  RegistrationScreen(
    title = stringResource(R.string.RegistrationActivity_enter_backup_passphrase),
    subtitle = null as AnnotatedString?,
    bottomContent = {
      RegistrationBottomActions(
        left = RegistrationAction(stringResource(android.R.string.cancel), onDismiss),
        right = RegistrationAction(
          label = stringResource(R.string.RegistrationActivity_restore),
          enabled = passphrase.isNotBlank(),
          onClick = submit
        )
      )
    },
    back = RegistrationBack(
      contentDescription = stringResource(R.string.LightRestore__back_to_restore_from_backup),
      onBack = onDismiss
    )
  ) {
    RegistrationTextField(
      value = passphrase,
      onValueChange = { passphrase = it.take(BackupUtil.PASSPHRASE_LENGTH) },
      label = stringResource(R.string.enter_backup_passphrase_dialog__backup_passphrase),
      textStyle = LightThemeTokens.typography.copy.copy(fontFamily = MonoTypeface.fontFamily()),
      keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Number,
        imeAction = ImeAction.Done,
        autoCorrectEnabled = false
      ),
      keyboardActions = KeyboardActions(onDone = { submit() }),
      singleLine = false,
      visualTransformation = visualTransformation,
      inputModifier = Modifier.focusRequester(focusRequester)
    )
  }
}

@DayNightPreviews
@Composable
private fun RestoreLocalBackupV1ScreenPreview() {
  Previews.Preview {
    RestoreLocalBackupV1Screen(
      state = RestoreLocalBackupV1UiState(
        backupCreated = "Backup created: 2 days ago",
        backupSize = "Backup size: 1.2 GB"
      ),
      onRestoreClick = {},
      onCancelClick = {},
      onPassphraseSubmitted = {},
      onPassphraseDismissed = {},
      onNotNow = {},
      onChooseFolder = {}
    )
  }
}
