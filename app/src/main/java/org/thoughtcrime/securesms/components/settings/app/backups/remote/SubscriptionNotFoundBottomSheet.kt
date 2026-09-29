/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups.remote

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.LightSettingsDivider
import org.thoughtcrime.securesms.light.LightSettingsRow
import org.thoughtcrime.securesms.light.LightSettingsScreen
import org.thoughtcrime.securesms.light.LightSettingsSection
import org.thoughtcrime.securesms.light.MollyLightTheme

/**
 * Displayed after the service subscription cannot be matched to an on-device billing subscription.
 */
@Composable
fun SubscriptionNotFoundBottomSheet(
  onDismiss: () -> Unit,
  onContactSupport: () -> Unit
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    SubscriptionNotFoundContent(
      onDismiss = onDismiss,
      onContactSupport = onContactSupport
    )
  }
}

@Composable
private fun SubscriptionNotFoundContent(
  onDismiss: () -> Unit = {},
  onContactSupport: () -> Unit = {}
) {
  MollyLightTheme {
    LightSettingsScreen(
      title = stringResource(R.string.SubscriptionNotFoundBottomSheet__subscription_not_found),
      onBack = onDismiss
    ) {
      LightSettingsRow(
        title = stringResource(R.string.SubscriptionNotFoundBottomSheet__your_subscription_couldnt_be_restored),
        onClick = null
      )
      LightSettingsSection(stringResource(R.string.SubscriptionNotFoundBottomSheet__this_could_happen_if))
      SubscriptionNotFoundReason(stringResource(R.string.SubscriptionNotFoundBottomSheet__youre_signed_into_the_play_store_with_a_different_google_account))
      SubscriptionNotFoundReason(stringResource(R.string.SubscriptionNotFoundBottomSheet__you_transferred_from_an_iphone))
      SubscriptionNotFoundReason(stringResource(R.string.SubscriptionNotFoundBottomSheet__your_subscription_recently_expired))
      LightSettingsRow(
        title = stringResource(R.string.SubscriptionNotFoundBottomSheet__if_you_have_an_active_subscription_on),
        onClick = null
      )
      LightSettingsDivider()
      LightSettingsRow(
        title = stringResource(R.string.SubscriptionNotFoundBottomSheet__got_it),
        onClick = onDismiss
      )
      LightSettingsRow(
        title = stringResource(R.string.SubscriptionNotFoundBottomSheet__contact_support),
        onClick = onContactSupport
      )
    }
  }
}

@Composable
private fun SubscriptionNotFoundReason(text: String) {
  LightSettingsRow(title = text, onClick = null)
}

@DayNightPreviews
@Composable
private fun SubscriptionNotFoundContentPreview() {
  Previews.Preview {
    SubscriptionNotFoundContent()
  }
}
