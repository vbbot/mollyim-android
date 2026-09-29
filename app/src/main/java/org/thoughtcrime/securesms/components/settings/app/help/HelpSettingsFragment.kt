/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.help

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import im.molly.app.base.ApkInfo
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.util.Util
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.LightConfirmationDialog
import org.thoughtcrime.securesms.light.LightSettingsDivider
import org.thoughtcrime.securesms.light.LightSettingsRow
import org.thoughtcrime.securesms.light.LightSettingsScreen
import org.thoughtcrime.securesms.light.LightSettingsToggleRow
import org.thoughtcrime.securesms.light.LightSettingsValueRow
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.navigation.safeNavigate

class HelpSettingsFragment : ComposeFragment() {

  private val viewModel: HelpSettingsViewModel by viewModels()

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navController: NavController = remember { findNavController() }
    val context = LocalContext.current
    var confirmDisableLog by remember { mutableStateOf(false) }

    MollyLightTheme {
      LightSettingsScreen(
        title = stringResource(R.string.preferences__help),
        onBack = { navController.popBackStack() }
      ) {
        LightSettingsRow(
          title = stringResource(R.string.HelpSettingsFragment__molly_im_website),
          onClick = {
            CommunicationActions.openBrowserLink(context, getString(R.string.website_url))
          }
        )
        LightSettingsRow(
          title = stringResource(R.string.HelpSettingsFragment__support_center),
          onClick = {
            CommunicationActions.openBrowserLink(context, getString(R.string.support_center_url))
          }
        )
        LightSettingsRow(
          title = stringResource(R.string.HelpSettingsFragment__contact_us),
          onClick = {
            navController.safeNavigate(R.id.action_helpSettingsFragment_to_helpFragment)
          }
        )

        LightSettingsDivider()

        LightSettingsValueRow(
          title = stringResource(R.string.HelpSettingsFragment__version),
          value = ApkInfo.versionName,
          valueContentDescription = ApkInfo.versionName,
          onClick = {
            navController.safeNavigate(R.id.action_helpSettingsFragment_to_appUpdatesFragment)
          },
          onLongClick = {
            Util.copyToClipboard(context, ApkInfo.versionName)
            Toast.makeText(context, R.string.HelpSettingsFragment__copied_to_clipboard, Toast.LENGTH_SHORT).show()
            true
          }
        )
        LightSettingsToggleRow(
          checked = state.logEnabled,
          title = stringResource(R.string.preferences__enable_debug_log),
          onCheckedChange = { checked ->
            if (checked) {
              viewModel.setLogEnabled(true)
            } else {
              confirmDisableLog = true
            }
          }
        )
        LightSettingsRow(
          enabled = state.logEnabled,
          title = stringResource(R.string.HelpSettingsFragment__debug_log),
          onClick = {
            navController.safeNavigate(R.id.action_helpSettingsFragment_to_submitDebugLogActivity)
          }
        )
        LightSettingsRow(
          title = stringResource(R.string.HelpSettingsFragment__licenses),
          onClick = {
            navController.safeNavigate(R.id.action_helpSettingsFragment_to_licenseFragment)
          }
        )
        LightSettingsRow(
          title = stringResource(R.string.HelpSettingsFragment__terms_amp_privacy_policy),
          onClick = {
            CommunicationActions.openBrowserLink(context, getString(R.string.terms_and_privacy_policy_url))
          }
        )
        LightSettingsRow(
          title = listOf(
            stringResource(R.string.HelpFragment__copyright_signal_messenger),
            stringResource(R.string.HelpFragment__licenced_under_the_agplv3),
            stringResource(R.string.HelpSettingsFragment__signal_is_a_501c3)
          ).joinToString("\n"),
          onClick = null
        )
      }

      if (confirmDisableLog) {
        LightConfirmationDialog(
          title = stringResource(R.string.preferences__enable_debug_log),
          body = stringResource(R.string.HelpSettingsFragment_disable_and_delete_debug_log),
          confirmLabel = stringResource(android.R.string.ok),
          dismissLabel = stringResource(android.R.string.cancel),
          onConfirm = { viewModel.setLogEnabled(false) },
          onDismiss = { confirmDisableLog = false },
          destructive = true
        )
      }
    }
  }
}
