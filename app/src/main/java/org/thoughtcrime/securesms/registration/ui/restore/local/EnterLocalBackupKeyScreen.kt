/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore.local

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.TemporaryScreenshotSecurity
import org.thoughtcrime.securesms.fonts.MonoTypeface
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.thoughtcrime.securesms.registration.ui.restore.AccountEntropyPoolVerification
import org.thoughtcrime.securesms.registration.ui.restore.BackupKeyVisualTransformation
import org.thoughtcrime.securesms.registration.ui.restore.RegistrationErrorDialogs
import org.thoughtcrime.securesms.registration.ui.restore.attachBackupKeyAutoFillHelper
import org.thoughtcrime.securesms.registration.ui.restore.backupKeyAutoFillHelper
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationAction
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationBottomActions
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationProgress
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationTextField

/** Light recovery-key entry; restore state, validation and account operations remain callback-owned. */
@Composable
fun EnterLocalBackupKeyScreen(
  enteredText: String,
  isRegistrationInProgress: Boolean,
  isBackupKeyValid: Boolean,
  aepValidationError: AccountEntropyPoolVerification.AEPValidationError?,
  onBackupKeyChanged: (String) -> Unit,
  onNextClicked: () -> Unit,
  onNoBackupKeyClick: () -> Unit,
  showRegistrationError: Boolean = false,
  registerAccountResult: RegisterAccountResult? = null,
  onRegistrationErrorDismiss: () -> Unit = {},
  onBackupKeyHelp: () -> Unit = {}
) {
  TemporaryScreenshotSecurity.bind()

  val visualTransform = remember { BackupKeyVisualTransformation(chunkSize = 4) }
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusRequester = remember { FocusRequester() }
  var requestFocus by remember { mutableStateOf(true) }
  val autoFillHelper = backupKeyAutoFillHelper(onBackupKeyChanged)

  RegistrationScreen(
    title = stringResource(R.string.EnterLocalBackupKeyScreen__enter_your_recovery_key),
    subtitle = stringResource(R.string.EnterLocalBackupKeyScreen__your_recovery_key_is_a_64_character_code),
    bottomContent = {
      Box(modifier = Modifier.fillMaxWidth()) {
        RegistrationBottomActions(
          left = RegistrationAction(
            label = stringResource(R.string.EnterLocalBackupKeyScreen__no_recovery_key),
            onClick = onNoBackupKeyClick
          ),
          right = if (isRegistrationInProgress) {
            null
          } else {
            RegistrationAction(
              label = stringResource(R.string.EnterLocalBackupKeyScreen__next),
              enabled = isBackupKeyValid && aepValidationError == null,
              onClick = onNextClicked
            )
          }
        )
        if (isRegistrationInProgress) {
          RegistrationProgress(
            modifier = Modifier
              .align(Alignment.CenterEnd)
              .padding(end = 2.5f.gridUnitsAsDp())
          )
        }
      }
    }
  ) {
    val validationErrorText = when (aepValidationError) {
      is AccountEntropyPoolVerification.AEPValidationError.TooLong -> stringResource(
        R.string.EnterBackupKey_too_long_error,
        aepValidationError.count,
        aepValidationError.max
      )
      AccountEntropyPoolVerification.AEPValidationError.Invalid -> stringResource(R.string.EnterBackupKey_invalid_backup_key_error)
      AccountEntropyPoolVerification.AEPValidationError.Incorrect -> stringResource(R.string.EnterBackupKey_incorrect_backup_key_error)
      null -> null
    }

    RegistrationTextField(
      value = enteredText,
      onValueChange = { value ->
        onBackupKeyChanged(value)
        autoFillHelper.onValueChanged(value)
      },
      label = stringResource(id = R.string.EnterBackupKey_backup_key),
      textStyle = LightThemeTokens.typography.copy.copy(
        fontFamily = MonoTypeface.fontFamily(),
        lineHeight = 36.sp
      ),
      keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Password,
        capitalization = KeyboardCapitalization.None,
        imeAction = ImeAction.Done,
        autoCorrectEnabled = false
      ),
      keyboardActions = KeyboardActions(
        onDone = {
          if (isBackupKeyValid && aepValidationError == null) {
            keyboardController?.hide()
            onNextClicked()
          }
        }
      ),
      supportingText = validationErrorText,
      isError = aepValidationError != null,
      singleLine = false,
      minLines = 4,
      visualTransformation = visualTransform,
      inputModifier = Modifier
        .focusRequester(focusRequester)
        .attachBackupKeyAutoFillHelper(autoFillHelper)
        .onGloballyPositioned {
          if (requestFocus) {
            focusRequester.requestFocus()
            requestFocus = false
          }
        }
    )

    RegistrationErrorDialogs(
      showRegistrationError = showRegistrationError,
      registerAccountResult = registerAccountResult,
      onRegistrationErrorDismiss = onRegistrationErrorDismiss,
      onBackupKeyHelp = onBackupKeyHelp
    )
  }
}

@DayNightPreviews
@Composable
private fun EnterLocalBackupKeyScreenPreview() {
  Previews.Preview {
    EnterLocalBackupKeyScreen(
      enteredText = "",
      isRegistrationInProgress = false,
      isBackupKeyValid = false,
      aepValidationError = null,
      onBackupKeyChanged = {},
      onNextClicked = {},
      onNoBackupKeyClick = {}
    )
  }
}
