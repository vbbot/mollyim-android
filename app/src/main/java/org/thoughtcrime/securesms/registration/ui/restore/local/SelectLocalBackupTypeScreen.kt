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

/** Selects a folder-backed or single-file local backup without owning picker side effects. */
@Composable
fun SelectLocalBackupTypeScreen(
  onSelectBackupFolderClick: () -> Unit,
  onSelectBackupFileClick: () -> Unit,
  onCancelClick: () -> Unit
) {
  RegistrationScreen(
    title = stringResource(R.string.SelectLocalBackupTypeScreen__restore_on_device_backup),
    subtitle = stringResource(R.string.SelectLocalBackupTypeScreen__restore_your_messages_from_the_backup),
    bottomContent = {
      RegistrationBottomActions(
        center = RegistrationAction(stringResource(android.R.string.cancel), onCancelClick)
      )
    }
  ) {
    BackupTypeRow(
      title = stringResource(R.string.SelectLocalBackupTypeScreen__choose_backup_folder),
      subtitle = stringResource(R.string.SelectLocalBackupTypeScreen__select_the_folder_on_your_device),
      onClick = onSelectBackupFolderClick
    )

    BackupTypeRow(
      title = stringResource(R.string.SelectLocalBackupTypeScreen__i_saved_my_backup_as_a_single_file),
      subtitle = null,
      onClick = onSelectBackupFileClick
    )
  }
}

@Composable
private fun BackupTypeRow(
  title: String,
  subtitle: String?,
  onClick: () -> Unit
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .lightClickable(onClick = onClick)
      .padding(vertical = 0.75f.gridUnitsAsDp())
  ) {
    Icon(
      imageVector = ImageVector.vectorResource(R.drawable.symbol_folder_24),
      tint = LightThemeTokens.colors.content,
      contentDescription = null,
      modifier = Modifier.size(2f.gridUnitsAsDp())
    )

    Column(modifier = Modifier.padding(start = 1f.gridUnitsAsDp())) {
      LightText(text = title, variant = LightTextVariant.Heading)
      if (subtitle != null) {
        LightText(
          text = subtitle,
          variant = LightTextVariant.Detail,
          lighten = true,
          modifier = Modifier.padding(top = 0.25f.gridUnitsAsDp())
        )
      }
    }
  }
}

@DayNightPreviews
@Composable
private fun SelectLocalBackupTypeScreenPreview() {
  Previews.Preview {
    SelectLocalBackupTypeScreen({}, {}, {})
  }
}
