/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore

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
import org.signal.core.models.AccountEntropyPool
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.TemporaryScreenshotSecurity
import org.thoughtcrime.securesms.fonts.MonoTypeface
import org.thoughtcrime.securesms.registration.ui.phonenumber.PhoneNumberNoticeAction
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationAction
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationBottomActions
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationNoticeDialog
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationProgress
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationTextField

/**
 * Shared screen infrastructure for entering an [AccountEntropyPool].
 */
@Composable
fun EnterBackupKeyScreen(
  isDisplayedDuringManualRestore: Boolean,
  enteredText: String,
  inProgress: Boolean,
  isBackupKeyValid: Boolean,
  chunkLength: Int,
  aepValidationError: AccountEntropyPoolVerification.AEPValidationError?,
  onBackupKeyChanged: (String) -> Unit = {},
  onNextClicked: () -> Unit = {},
  onLearnMore: () -> Unit = {},
  onSkip: () -> Unit = {},
  dialogContent: @Composable () -> Unit
) {
  TemporaryScreenshotSecurity.bind()

  var showNoBackupKey by remember { mutableStateOf(false) }

  RegistrationScreen(
    title = stringResource(R.string.EnterBackupKey_title),
    subtitle = stringResource(R.string.EnterBackupKey_subtitle),
    bottomContent = {
      Box(modifier = Modifier.fillMaxWidth()) {
        RegistrationBottomActions(
          left = RegistrationAction(
            label = stringResource(id = R.string.EnterBackupKey_no_backup_key),
            enabled = !inProgress,
            onClick = { showNoBackupKey = true }
          ),
          right = if (inProgress) {
            null
          } else {
            RegistrationAction(
              label = stringResource(id = R.string.RegistrationActivity_next),
              enabled = isBackupKeyValid && aepValidationError == null,
              onClick = onNextClicked
            )
          }
        )
        if (inProgress) {
          RegistrationProgress(
            modifier = Modifier
              .align(Alignment.CenterEnd)
              .padding(end = 2.5f.gridUnitsAsDp())
          )
        }
      }
    }
  ) {
    val focusRequester = remember { FocusRequester() }
    var requestFocus: Boolean by remember { mutableStateOf(true) }
    val visualTransform = remember(chunkLength) { BackupKeyVisualTransformation(chunkSize = chunkLength) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val autoFillHelper = backupKeyAutoFillHelper { onBackupKeyChanged(it) }

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
      onValueChange = {
        onBackupKeyChanged(it)
        autoFillHelper.onValueChanged(it)
      },
      label = stringResource(id = R.string.EnterBackupKey_backup_key),
      textStyle = LightThemeTokens.typography.copy.copy(
        fontFamily = MonoTypeface.fontFamily(),
        lineHeight = 36.sp
      ),
      keyboardOptions = KeyboardOptions(
        keyboardType = KeyboardType.Password,
        capitalization = KeyboardCapitalization.None,
        imeAction = ImeAction.Next,
        autoCorrectEnabled = false
      ),
      keyboardActions = KeyboardActions(
        onNext = {
          if (isBackupKeyValid) {
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

    if (showNoBackupKey) {
      NoBackupKeyNotice(
        showSecondParagraph = isDisplayedDuringManualRestore,
        onLearnMore = onLearnMore,
        onSkip = onSkip,
        onDismiss = { showNoBackupKey = false }
      )
    }

    dialogContent()
  }
}

@DayNightPreviews
@Composable
private fun EnterBackupKeyScreenPreview() {
  Previews.Preview {
    EnterBackupKeyScreen(
      enteredText = "UY38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t".uppercase(),
      isBackupKeyValid = true,
      inProgress = false,
      chunkLength = 4,
      aepValidationError = null,
      isDisplayedDuringManualRestore = true
    ) {}
  }
}

@DayNightPreviews
@Composable
private fun EnterBackupKeyScreenErrorPreview() {
  Previews.Preview {
    EnterBackupKeyScreen(
      enteredText = "UY38jh2778hjjhj8lk19ga61s672jsj089r023s6a57809bap92j2yh5t326vv7t".uppercase(),
      isBackupKeyValid = true,
      inProgress = false,
      chunkLength = 4,
      aepValidationError = AccountEntropyPoolVerification.AEPValidationError.Invalid,
      isDisplayedDuringManualRestore = true
    ) {}
  }
}

/**
 * "No recovery key?", as a full-screen Light notice in place of the Material bottom sheet.
 *
 * The sheet's key artwork in a green disc has no Light counterpart and is dropped; its heading is
 * the top bar title, its paragraphs the notice copy, and its two buttons the notice's actions in the
 * same order. Each action takes the notice down before it runs, as a Light panel does -- the sheet
 * only hid itself for "Learn more", but "Skip" goes on to a confirmation or leaves the screen, and
 * neither should find the notice still up behind it. BACK closes it, as dismissing the sheet did.
 */
@Composable
fun NoBackupKeyNotice(
  showSecondParagraph: Boolean,
  onLearnMore: () -> Unit,
  onSkip: () -> Unit,
  onDismiss: () -> Unit
) {
  val message = if (showSecondParagraph) {
    stringResource(R.string.EnterBackupKey_no_key_paragraph_1) + "\n\n" + stringResource(R.string.EnterBackupKey_no_key_paragraph_2)
  } else {
    stringResource(R.string.EnterBackupKey_no_key_paragraph_1)
  }

  RegistrationNoticeDialog(
    title = stringResource(R.string.EnterBackupKey_no_backup_key),
    message = message,
    actions = listOf(
      PhoneNumberNoticeAction(stringResource(R.string.EnterBackupKey_learn_more)) {
        onDismiss()
        onLearnMore()
      },
      // "Skip and don't restore" does not fit on one line in capitals; the step's own short label
      // says the same thing.
      PhoneNumberNoticeAction(stringResource(R.string.EnterBackupKey_skip_restore)) {
        onDismiss()
        onSkip()
      }
    ),
    onBack = onDismiss,
    backDescription = stringResource(R.string.EnterBackupKey__back_to_recovery_key)
  )
}
