/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore.local

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationAction
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationBack
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationBottomActions
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen

/** Marks for the selected and unselected backup rows, as the Light settings radio rows draw them. */
private const val SELECTED_MARK = "●"
private const val UNSELECTED_MARK = "○"

/**
 * Picks which of the two most recent snapshots to restore. Was a Material bottom sheet of radio rows
 * on grey rounded cards; now a full Light screen with the same choice and the same rule: CONTINUE
 * is live only once a backup other than the current one is picked, and reports it through
 * [onBackupSelected]. BACK and system back leave without changing anything, as dismissing the sheet
 * did.
 */
@Composable
fun SelectLocalBackupListScreen(
  selectedBackup: SelectableBackup,
  selectableBackups: PersistentList<SelectableBackup>,
  onBackupSelected: (SelectableBackup) -> Unit,
  onBackClick: () -> Unit
) {
  var selection by remember { mutableStateOf(selectedBackup) }

  val backups = remember(selectableBackups) {
    selectableBackups.take(2)
  }

  RegistrationScreen(
    title = stringResource(R.string.SelectLocalBackupSheet__choose_a_backup_to_restore),
    subtitle = stringResource(R.string.SelectLocalBackupSheet__choosing_an_older_backup),
    bottomContent = {
      RegistrationBottomActions(
        center = RegistrationAction(
          label = stringResource(R.string.SelectLocalBackupSheet__continue_button),
          enabled = selectedBackup != selection,
          onClick = { onBackupSelected(selection) }
        )
      )
    },
    back = RegistrationBack(
      contentDescription = stringResource(R.string.RegistrationScreen__back_to_previous_screen),
      onBack = onBackClick
    )
  ) {
    backups.forEach { backup ->
      BackupRadioRow(
        label = backup.backupTime,
        selected = selection == backup,
        onClick = { selection = backup }
      )
    }
  }
}

@Composable
private fun BackupRadioRow(
  label: String,
  selected: Boolean,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .semantics {
        role = Role.RadioButton
        this.selected = selected
      }
      .lightClickable(onClick = onClick)
      .padding(vertical = 0.75f.gridUnitsAsDp()),
    verticalAlignment = Alignment.CenterVertically
  ) {
    LightText(
      text = label,
      variant = LightTextVariant.Heading,
      modifier = Modifier.weight(1f)
    )
    LightText(
      text = if (selected) SELECTED_MARK else UNSELECTED_MARK,
      variant = LightTextVariant.Copy,
      modifier = Modifier.padding(start = 1f.gridUnitsAsDp())
    )
  }
}

@DayNightPreviews
@Composable
private fun SelectLocalBackupListScreenPreview() {
  Previews.Preview {
    SelectLocalBackupListScreen(
      selectedBackup = SelectableBackup(
        timestamp = 0L,
        backupTime = "Today • 3:38am"
      ),
      selectableBackups = persistentListOf(
        SelectableBackup(
          timestamp = 0L,
          backupTime = "Today • 3:38am"
        ),
        SelectableBackup(
          timestamp = 1L,
          backupTime = "August 13, 2024 • 3:21am"
        )
      ),
      onBackupSelected = {},
      onBackClick = {}
    )
  }
}
