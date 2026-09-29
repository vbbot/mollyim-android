/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore.local

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationAction
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationBottomActions
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen

/** Shows the selected local backup while the callback owner retains all restore operations. */
@Composable
fun SelectLocalBackupScreen(
  selectedBackup: SelectableBackup,
  isSelectedBackupLatest: Boolean,
  onRestoreBackupClick: () -> Unit,
  onCancelClick: () -> Unit,
  onChooseADifferentBackupClick: () -> Unit
) {
  RegistrationScreen(
    title = stringResource(R.string.SelectLocalBackupScreen__restore_on_device_backup),
    subtitle = stringResource(R.string.SelectLocalBackupScreen__restore_your_messages_from_the_backup_folder),
    bottomContent = {
      RegistrationBottomActions(
        left = RegistrationAction(stringResource(android.R.string.cancel), onCancelClick),
        right = RegistrationAction(
          stringResource(R.string.SelectLocalBackupScreen__restore_backup),
          onRestoreBackupClick
        )
      )
    }
  ) {
    BackupSummary(
      selectedBackup = selectedBackup,
      isSelectedBackupLatest = isSelectedBackupLatest
    )

    BackupChoiceRow(
      label = if (isSelectedBackupLatest) {
        stringResource(R.string.SelectLocalBackupScreen__choose_an_earlier_backup)
      } else {
        stringResource(R.string.SelectLocalBackupScreen__choose_a_different_backup)
      },
      onClick = onChooseADifferentBackupClick
    )
  }
}

@Composable
private fun BackupSummary(
  selectedBackup: SelectableBackup,
  isSelectedBackupLatest: Boolean
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 0.75f.gridUnitsAsDp())
  ) {
    LightText(
      text = if (isSelectedBackupLatest) {
        stringResource(R.string.SelectLocalBackupScreen__your_latest_backup)
      } else {
        stringResource(R.string.SelectLocalBackupScreen__your_backup)
      },
      variant = LightTextVariant.Heading
    )
    Row(
      modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = ImageVector.vectorResource(R.drawable.symbol_recent_24),
        contentDescription = null,
        tint = LightThemeTokens.colors.content,
        modifier = Modifier.size(1.5f.gridUnitsAsDp())
      )
      LightText(
        text = selectedBackup.backupTime,
        variant = LightTextVariant.Copy,
        modifier = Modifier.padding(start = 0.75f.gridUnitsAsDp())
      )
    }
  }
}

@Composable
private fun BackupChoiceRow(label: String, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .lightClickable(onClick = onClick)
      .padding(vertical = 0.75f.gridUnitsAsDp()),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = ImageVector.vectorResource(R.drawable.symbol_backup_24),
      contentDescription = null,
      tint = LightThemeTokens.colors.content,
      modifier = Modifier.size(1.5f.gridUnitsAsDp())
    )
    LightText(
      text = label,
      variant = LightTextVariant.Heading,
      modifier = Modifier.padding(start = 0.75f.gridUnitsAsDp())
    )
  }
}

@DayNightPreviews
@Composable
fun SelectLocalBackupScreenPreview() {
  Previews.Preview {
    SelectLocalBackupScreen(
      selectedBackup = SelectableBackup(timestamp = 0L, backupTime = "Today · 12:34 PM"),
      isSelectedBackupLatest = true,
      onRestoreBackupClick = {},
      onCancelClick = {},
      onChooseADifferentBackupClick = {}
    )
  }
}
