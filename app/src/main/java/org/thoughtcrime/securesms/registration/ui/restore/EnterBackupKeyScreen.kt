/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.launch
import org.signal.core.models.AccountEntropyPool
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.horizontalGutters
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.ui.BackupsIconColors
import org.thoughtcrime.securesms.components.TemporaryScreenshotSecurity
import org.thoughtcrime.securesms.fonts.MonoTypeface
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationAction
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationBottomActions
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationProgress
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationTextField

/**
 * Shared screen infrastructure for entering an [AccountEntropyPool].
 */
@OptIn(ExperimentalMaterial3Api::class)
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

  val coroutineScope = rememberCoroutineScope()
  val sheetState = rememberModalBottomSheetState(
    skipPartiallyExpanded = true
  )

  RegistrationScreen(
    title = stringResource(R.string.EnterBackupKey_title),
    subtitle = stringResource(R.string.EnterBackupKey_subtitle),
    bottomContent = {
      Box(modifier = Modifier.fillMaxWidth()) {
        RegistrationBottomActions(
          left = RegistrationAction(
            label = stringResource(id = R.string.EnterBackupKey_no_backup_key),
            enabled = !inProgress,
            onClick = {
              coroutineScope.launch {
                sheetState.show()
              }
            }
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

    if (sheetState.isVisible) {
      ModalBottomSheet(
        dragHandle = null,
        onDismissRequest = {
          coroutineScope.launch {
            sheetState.hide()
          }
        }
      ) {
        NoBackupKeyBottomSheet(
          onLearnMore = {
            coroutineScope.launch {
              sheetState.hide()
            }
            onLearnMore()
          },
          onSkip = onSkip,
          showSecondParagraph = isDisplayedDuringManualRestore
        )
      }
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

@Composable
private fun NoBackupKeyBottomSheet(
  showSecondParagraph: Boolean,
  onLearnMore: () -> Unit = {},
  onSkip: () -> Unit = {}
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .fillMaxWidth()
      .horizontalGutters()
  ) {
    BottomSheets.Handle()

    Icon(
      painter = painterResource(id = R.drawable.symbol_key_24),
      tint = BackupsIconColors.Success.foreground,
      contentDescription = null,
      modifier = Modifier
        .padding(top = 18.dp, bottom = 16.dp)
        .size(88.dp)
        .background(
          color = BackupsIconColors.Success.background,
          shape = CircleShape
        )
        .padding(20.dp)
    )

    Text(
      text = stringResource(R.string.EnterBackupKey_no_backup_key),
      style = MaterialTheme.typography.titleLarge
    )

    Spacer(modifier = Modifier.height(12.dp))

    Text(
      text = stringResource(R.string.EnterBackupKey_no_key_paragraph_1),
      style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    if (showSecondParagraph) {
      Spacer(modifier = Modifier.height(24.dp))

      Text(
        text = stringResource(R.string.EnterBackupKey_no_key_paragraph_2),
        style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    Spacer(modifier = Modifier.height(36.dp))

    Row(
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 24.dp)
    ) {
      TextButton(
        onClick = onLearnMore
      ) {
        Text(
          text = stringResource(id = R.string.EnterBackupKey_learn_more)
        )
      }

      TextButton(
        onClick = onSkip
      ) {
        Text(
          text = stringResource(id = R.string.EnterBackupKey_skip_and_dont_restore)
        )
      }
    }
  }
}

@DayNightPreviews
@Composable
private fun NoBackupKeyBottomSheetPreview() {
  Previews.BottomSheetContentPreview {
    NoBackupKeyBottomSheet(
      showSecondParagraph = true
    )
  }
}

@DayNightPreviews
@Composable
private fun NoBackupKeyBottomSheetNoSecondParagraphPreview() {
  Previews.BottomSheetContentPreview {
    NoBackupKeyBottomSheet(
      showSecondParagraph = false
    )
  }
}
