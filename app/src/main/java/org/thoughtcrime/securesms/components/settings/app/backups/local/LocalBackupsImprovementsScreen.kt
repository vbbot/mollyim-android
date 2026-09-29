/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups.local

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.LightSettingsDivider
import org.thoughtcrime.securesms.light.LightSettingsRow
import org.thoughtcrime.securesms.light.LightSettingsScreen
import org.thoughtcrime.securesms.light.LightSettingsSection
import org.thoughtcrime.securesms.light.MollyLightTheme

/** Screen explaining the improvements made to the on-device backups experience. */
@Composable
fun LocalBackupsImprovementsScreen(
  onNavigationClick: () -> Unit = {},
  onContinueClick: () -> Unit = {}
) {
  MollyLightTheme {
    LightSettingsScreen(
      title = stringResource(R.string.OnDeviceBackupsImprovementsScreen__improvements_to_on_device_backups),
      onBack = onNavigationClick
    ) {
      LightSettingsRow(
        title = stringResource(R.string.OnDeviceBackupsImprovementsScreen__your_on_device_backup_will_be_upgraded),
        onClick = null
      )

      LightSettingsDivider()
      LightSettingsSection(stringResource(R.string.OnDeviceBackupsImprovementsScreen__improvements_to_on_device_backups))
      LightSettingsRow(
        title = stringResource(R.string.OnDeviceBackupsImprovementsScreen__backups_now_save_faster),
        onClick = null
      )
      LightSettingsRow(
        title = stringResource(R.string.OnDeviceBackupsImprovementsScreen__your_backup_will_be_saved_as_a_folder),
        onClick = null
      )
      LightSettingsRow(
        title = stringResource(R.string.OnDeviceBackupsImprovementsScreen__all_backups_remain_end_to_end_encrypted),
        onClick = null
      )

      LightSettingsDivider()
      LightSettingsRow(
        title = stringResource(R.string.OnDeviceBackupsImprovementsScreen__continue),
        onClick = onContinueClick
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun LocalBackupsImprovementsScreenPreview() {
  Previews.Preview {
    LocalBackupsImprovementsScreen()
  }
}
