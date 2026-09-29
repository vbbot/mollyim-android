/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */
package org.thoughtcrime.securesms.components.settings.app.backups.local

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.isIdle
import org.thoughtcrime.securesms.components.compose.rememberBiometricsAuthentication
import org.thoughtcrime.securesms.keyvalue.protos.LocalBackupCreationProgress
import org.thoughtcrime.securesms.light.LightConfirmationDialog
import org.thoughtcrime.securesms.light.LightSettingsDivider
import org.thoughtcrime.securesms.light.LightSettingsProgressRow
import org.thoughtcrime.securesms.light.LightSettingsRow
import org.thoughtcrime.securesms.light.LightSettingsScreen
import org.thoughtcrime.securesms.light.LightSettingsSection
import org.thoughtcrime.securesms.light.LightSettingsValueRow
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.util.BackupUtil
import org.thoughtcrime.securesms.util.formatHours
import java.time.LocalTime
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun LocalBackupsSettingsScreen(
  state: LocalBackupsSettingsState,
  callback: LocalBackupsSettingsCallback,
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
  val context = LocalContext.current
  var showChooseLocationDialog by rememberSaveable { mutableStateOf(false) }
  var showTurnOffAndDeleteDialog by rememberSaveable { mutableStateOf(false) }
  var showOptimizeStorageWarningDialog by rememberSaveable { mutableStateOf(false) }
  var showTimeDialog by rememberSaveable { mutableStateOf(false) }

  val biometrics = rememberBiometricsAuthentication(
    promptTitle = stringResource(R.string.RemoteBackupsSettingsFragment__unlock_to_view_backup_key),
    educationSheetMessage = stringResource(R.string.RemoteBackupsSettingsFragment__to_view_your_key)
  )

  val proceedWithTurnOn = {
    if (BackupUtil.isUserSelectionRequired(context)) {
      showChooseLocationDialog = true
    } else {
      callback.onTurnOnClick()
    }
  }

  MollyLightTheme {
    LightSettingsScreen(
      title = stringResource(R.string.RemoteBackupsSettingsFragment__on_device_backups),
      onBack = callback::onNavigationClick
    ) {
      snackbarHostState.currentSnackbarData?.let { snackbarData ->
        LightSettingsRow(title = snackbarData.visuals.message, onClick = null)
        LaunchedEffect(snackbarData) {
          delay(4.seconds)
          snackbarData.dismiss()
        }
      }

      if (!state.backupsEnabled) {
        LightSettingsRow(
          title = stringResource(R.string.BackupsPreferenceFragment__backups_are_encrypted_with_a_passphrase),
          onClick = null
        )
        LightSettingsRow(
          title = stringResource(R.string.BackupsPreferenceFragment__turn_on),
          enabled = state.canTurnOn,
          onClick = {
            if (state.optimizeStorageEnabled) {
              showOptimizeStorageWarningDialog = true
            } else {
              proceedWithTurnOn()
            }
          }
        )
      } else {
        if (!state.progress.isIdle) {
          LightSettingsProgressRow(
            title = stringResource(R.string.BackupsPreferenceFragment__create_backup),
            summary = localBackupProgressMessage(state.progress)
          )
        } else {
          LightSettingsRow(
            title = stringResource(R.string.BackupsPreferenceFragment__create_backup),
            summary = state.lastBackupLabel?.let {
              stringResource(R.string.BackupsSettingsFragment_last_backup_s, it)
            },
            onClick = callback::onCreateBackupClick
          )
        }

        LightSettingsValueRow(
          title = stringResource(R.string.BackupsPreferenceFragment__backup_time),
          value = state.scheduleTimeLabel.orEmpty(),
          valueContentDescription = state.scheduleTimeLabel.orEmpty(),
          onClick = { showTimeDialog = true }
        )

        if (!state.folderDisplayName.isNullOrBlank()) {
          LightSettingsRow(
            title = stringResource(R.string.BackupsPreferenceFragment__backup_folder),
            summary = state.folderDisplayName,
            onClick = null
          )
        }

        LightSettingsRow(
          title = stringResource(R.string.UnifiedOnDeviceBackupsSettingsScreen__view_recovery_key),
          onClick = { biometrics.withBiometricsAuthentication { callback.onViewBackupKeyClick() } }
        )

        LightSettingsRow(
          title = stringResource(R.string.RemoteBackupsSettingsFragment__turn_off_and_delete),
          onClick = { showTurnOffAndDeleteDialog = true }
        )
      }

      LightSettingsDivider()
      val learnMore = stringResource(R.string.BackupsPreferenceFragment__learn_more)
      LightSettingsRow(
        title = stringResource(R.string.OnDeviceBackupsScreen__to_restore_a_backup, learnMore).trim(),
        onClick = callback::onLearnMoreClick
      )
    }
  }

  if (showChooseLocationDialog) {
    LightConfirmationDialog(
      title = stringResource(R.string.BackupDialog_enable_local_backups),
      body = stringResource(R.string.BackupDialog_to_enable_backups_choose_a_folder),
      confirmLabel = stringResource(R.string.BackupDialog_choose_folder),
      dismissLabel = stringResource(android.R.string.cancel),
      onConfirm = callback::onLaunchBackupLocationPickerClick,
      onDismiss = { showChooseLocationDialog = false }
    )
  }

  if (showOptimizeStorageWarningDialog) {
    LightThreeActionDialog(
      title = stringResource(R.string.RemoteBackupsSettingsFragment__on_device_backups),
      body = stringResource(R.string.OnDeviceBackupsScreen__you_have_optimize_signal_storage_on),
      positiveLabel = stringResource(R.string.OnDeviceBackupsScreen__view_setting),
      onPositive = {
        showOptimizeStorageWarningDialog = false
        callback.onViewOptimizeStorageSettingClick()
      },
      neutralLabel = stringResource(R.string.OnDeviceBackupsScreen__continue_without_turning_off),
      onNeutral = {
        showOptimizeStorageWarningDialog = false
        proceedWithTurnOn()
      },
      negativeLabel = stringResource(android.R.string.cancel),
      onNegative = { showOptimizeStorageWarningDialog = false }
    )
  }

  if (showTurnOffAndDeleteDialog) {
    LightConfirmationDialog(
      title = stringResource(R.string.BackupDialog_delete_backups),
      body = stringResource(R.string.BackupDialog_disable_and_delete_all_local_backups),
      confirmLabel = stringResource(R.string.BackupDialog_delete_backups_statement),
      dismissLabel = stringResource(android.R.string.cancel),
      onConfirm = callback::onTurnOffAndDeleteConfirmed,
      onDismiss = { showTurnOffAndDeleteDialog = false },
      destructive = true
    )
  }

  if (showTimeDialog) {
    LightBackupTimeDialog(
      initialHour = state.scheduleHour,
      initialMinute = state.scheduleMinute,
      onConfirm = { hour, minute ->
        callback.onBackupTimeSelected(hour, minute)
        showTimeDialog = false
      },
      onDismiss = { showTimeDialog = false }
    )
  }

  if (state.isDeleting) {
    val progress = if (state.deleteTotal > 0) {
      "${state.deleteCompleted} / ${state.deleteTotal}"
    } else {
      null
    }
    LightBlockingProgressDialog(
      title = stringResource(R.string.BackupDialog_deleting_local_backup),
      summary = progress
    )
  }
}

@Composable
private fun localBackupProgressMessage(progress: LocalBackupCreationProgress): String {
  val exporting = progress.exporting
  val transferring = progress.transferring
  return when {
    exporting != null -> when (exporting.phase) {
      LocalBackupCreationProgress.ExportPhase.MESSAGE -> if (exporting.frameTotalCount > 0) {
        stringResource(
          R.string.BackupCreationProgressRow__processing_messages_s_of_s_d,
          "%,d".format(exporting.frameExportCount),
          "%,d".format(exporting.frameTotalCount),
          (exporting.frameExportCount * 100 / exporting.frameTotalCount).toInt()
        )
      } else {
        stringResource(R.string.BackupCreationProgressRow__processing_messages)
      }
      LocalBackupCreationProgress.ExportPhase.NONE -> stringResource(R.string.BackupCreationProgressRow__processing_backup)
      LocalBackupCreationProgress.ExportPhase.FINALIZING -> stringResource(R.string.BackupCreationProgressRow__finalizing)
      else -> stringResource(R.string.BackupCreationProgressRow__preparing_backup)
    }
    transferring != null -> {
      val percent = if (transferring.total == 0L) 0 else (transferring.completed * 100 / transferring.total).toInt()
      stringResource(R.string.BackupCreationProgressRow__exporting_media_d, percent)
    }
    else -> stringResource(R.string.BackupCreationProgressRow__processing_backup)
  }
}

@Composable
private fun LightBackupTimeDialog(
  initialHour: Int,
  initialMinute: Int,
  onConfirm: (Int, Int) -> Unit,
  onDismiss: () -> Unit
) {
  var hour by rememberSaveable(initialHour) { mutableIntStateOf(initialHour) }
  var minute by rememberSaveable(initialMinute) { mutableIntStateOf(initialMinute) }
  val context = LocalContext.current
  val formattedTime = remember(hour, minute) { LocalTime.of(hour, minute).formatHours(context) }
  val hourUnit = stringResource(R.string.TimeDurationPickerDialog_single_letter_hour_abbreviation)
  val minuteUnit = stringResource(R.string.TimeDurationPickerDialog_single_letter_minute_abbreviation)

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    MollyLightTheme {
      LightSettingsScreen(
        title = stringResource(R.string.BackupsPreferenceFragment__set_backup_time),
        onBack = onDismiss
      ) {
        LightSettingsSection(formattedTime)
        LightSettingsRow(title = "−1 $hourUnit", onClick = { hour = (hour + 23) % 24 })
        LightSettingsRow(title = "+1 $hourUnit", onClick = { hour = (hour + 1) % 24 })
        LightSettingsRow(title = "−1 $minuteUnit", onClick = {
          val total = (hour * 60 + minute + 1439) % 1440
          hour = total / 60
          minute = total % 60
        })
        LightSettingsRow(title = "+1 $minuteUnit", onClick = {
          val total = (hour * 60 + minute + 1) % 1440
          hour = total / 60
          minute = total % 60
        })
        LightSettingsDivider()
        LightSettingsRow(title = stringResource(android.R.string.ok), onClick = { onConfirm(hour, minute) })
        LightSettingsRow(title = stringResource(android.R.string.cancel), onClick = onDismiss)
      }
    }
  }
}

@Composable
private fun LightThreeActionDialog(
  title: String,
  body: String,
  positiveLabel: String,
  onPositive: () -> Unit,
  neutralLabel: String,
  onNeutral: () -> Unit,
  negativeLabel: String,
  onNegative: () -> Unit
) {
  Dialog(
    onDismissRequest = onNegative,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    MollyLightTheme {
      LightSettingsScreen(title = title, onBack = onNegative) {
        LightSettingsRow(title = body, onClick = null)
        LightSettingsDivider()
        LightSettingsRow(title = positiveLabel, onClick = onPositive)
        LightSettingsRow(title = neutralLabel, onClick = onNeutral)
        LightSettingsRow(title = negativeLabel, onClick = onNegative)
      }
    }
  }
}

@Composable
internal fun LightBlockingProgressDialog(title: String, summary: String?) {
  Dialog(
    onDismissRequest = {},
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    MollyLightTheme {
      LightSettingsScreen(title = title, onBack = {}) {
        LightSettingsProgressRow(title = title, summary = summary)
      }
    }
  }
}

@DayNightPreviews
@Composable
private fun LocalBackupsSettingsPreview() {
  Previews.Preview {
    LocalBackupsSettingsScreen(
      state = LocalBackupsSettingsState(
        backupsEnabled = true,
        lastBackupLabel = "1 hour ago",
        folderDisplayName = "Signal Backups",
        scheduleTimeLabel = "1:00 AM"
      ),
      callback = LocalBackupsSettingsCallback.Empty
    )
  }
}
