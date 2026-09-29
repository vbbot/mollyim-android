/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import kotlinx.coroutines.delay
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.DeletionState
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsType
import org.thoughtcrime.securesms.components.settings.app.subscription.MessageBackupsCheckoutLauncher.createBackupsCheckoutLauncher
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.light.LightSettingsDivider
import org.thoughtcrime.securesms.light.LightSettingsProgressRow
import org.thoughtcrime.securesms.light.LightSettingsRadioRow
import org.thoughtcrime.securesms.light.LightSettingsRow
import org.thoughtcrime.securesms.light.LightSettingsScreen
import org.thoughtcrime.securesms.light.LightSettingsSection
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.Environment
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import java.math.BigDecimal
import java.util.Currency
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Top-level backups settings screen. */
class BackupsSettingsFragment : ComposeFragment() {

  private lateinit var checkoutLauncher: ActivityResultLauncher<MessageBackupTier?>

  private val viewModel: BackupsSettingsViewModel by viewModels()
  private val args: BackupsSettingsFragmentArgs by navArgs()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    checkoutLauncher = createBackupsCheckoutLauncher {
      findNavController().safeNavigate(R.id.action_backupsSettingsFragment_to_remoteBackupsSettingsFragment)
    }

    if (savedInstanceState == null && args.launchCheckoutFlow) {
      checkoutLauncher.launch(null)
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    BackupsSettingsContent(
      backupsSettingsState = state,
      onNavigationClick = { requireActivity().onNavigateUp() },
      onBackupsRowClick = {
        when (state.backupState) {
          is BackupState.Error -> Unit
          is BackupState.None -> checkoutLauncher.launch(null)
          else -> findNavController().safeNavigate(R.id.action_backupsSettingsFragment_to_remoteBackupsSettingsFragment)
        }
      },
      onOnDeviceBackupsRowClick = {
        if (SignalStore.backup.newLocalBackupsEnabled || (Environment.Backups.isNewFormatSupportedForLocalBackup() && !SignalStore.settings.isBackupEnabled)) {
          findNavController().safeNavigate(R.id.action_backupsSettingsFragment_to_localBackupsFragment)
        } else {
          findNavController().safeNavigate(R.id.action_backupsSettingsFragment_to_backupsPreferenceFragment)
        }
      },
      onBackupTierInternalOverrideChanged = viewModel::onBackupTierInternalOverrideChanged
    )
  }
}

@Composable
private fun BackupsSettingsContent(
  backupsSettingsState: BackupsSettingsState,
  onNavigationClick: () -> Unit = {},
  onBackupsRowClick: () -> Unit = {},
  onOnDeviceBackupsRowClick: () -> Unit = {},
  onBackupTierInternalOverrideChanged: (MessageBackupTier?) -> Unit = {}
) {
  MollyLightTheme {
    LightSettingsScreen(
      title = stringResource(R.string.preferences_chats__backups),
      onBack = onNavigationClick
    ) {
      if (backupsSettingsState.showBackupTierInternalOverride) {
        LightSettingsSection("ALPHA ONLY")
        LightSettingsRow(
          title = "Use this to override the subscription state to one of your choosing.",
          onClick = null
        )
        InternalBackupOverrideRows(backupsSettingsState, onBackupTierInternalOverrideChanged)
        LightSettingsDivider()
      }

      LightSettingsRow(
        title = stringResource(R.string.RemoteBackupsSettingsFragment__back_up_your_message_history),
        onClick = null
      )

      val showOtherWays = when (val backupState = backupsSettingsState.backupState) {
        is BackupState.LocalStore -> {
          val plan = when (backupState.tier) {
            MessageBackupTier.FREE -> stringResource(R.string.RemoteBackupsSettingsFragment__your_backup_plan_is_free)
            MessageBackupTier.PAID -> stringResource(R.string.MessageBackupsTypeSelectionScreen__text_plus_all_your_media)
          }
          BackupPlanRows(
            summary = "$plan\n${lastBackupSummary(backupsSettingsState.lastBackupAt)}",
            action = stringResource(R.string.BackupsSettingsFragment_view_settings),
            onClick = onBackupsRowClick
          )
          true
        }

        is BackupState.Inactive -> {
          BackupPlanRows(
            summary = stringResource(R.string.RemoteBackupsSettingsFragment__off),
            action = stringResource(R.string.BackupsSettingsFragment_view_settings),
            onClick = onBackupsRowClick
          )
          true
        }

        is BackupState.ActiveFree, is BackupState.ActivePaid, is BackupState.Canceled -> {
          ActiveBackupPlanRows(
            backupState = backupState as BackupState.WithTypeAndRenewalTime,
            lastBackupAt = backupsSettingsState.lastBackupAt,
            onClick = onBackupsRowClick
          )
          true
        }

        is BackupState.None -> {
          if (backupState.featureSupported) {
            BackupPlanRows(
              summary = stringResource(R.string.BackupsSettingsFragment_automatic_backups_with_signals),
              action = stringResource(R.string.BackupsSettingsFragment_set_up),
              onClick = onBackupsRowClick
            )
            true
          } else {
            false
          }
        }

        is BackupState.Error -> {
          LightSettingsProgressRow(
            title = stringResource(R.string.RemoteBackupsSettingsFragment__waiting_for_network)
          )
          true
        }

        BackupState.NotFound -> {
          BackupPlanRows(
            summary = stringResource(R.string.BackupsSettingsFragment_subscription_not_found_on_this_device),
            action = stringResource(R.string.BackupsSettingsFragment_view_settings),
            onClick = onBackupsRowClick
          )
          true
        }

        is BackupState.Pending -> {
          LightSettingsProgressRow(
            title = stringResource(R.string.RemoteBackupsSettingsFragment__signal_backups),
            summary = stringResource(R.string.RemoteBackupsSettingsFragment__payment_pending)
          )
          LightSettingsRow(
            title = stringResource(R.string.BackupsSettingsFragment_view_settings),
            onClick = onBackupsRowClick
          )
          true
        }

        is BackupState.SubscriptionMismatchMissingGooglePlay -> {
          ActiveBackupPlanRows(
            backupState = backupState,
            lastBackupAt = backupsSettingsState.lastBackupAt,
            onClick = onBackupsRowClick
          )
          true
        }
      }

      if (showOtherWays) {
        LightSettingsDivider()
        LightSettingsSection(stringResource(R.string.RemoteBackupsSettingsFragment__other_ways_to_backup))
      }

      LightSettingsRow(
        title = stringResource(R.string.RemoteBackupsSettingsFragment__on_device_backups),
        summary = stringResource(R.string.RemoteBackupsSettingsFragment__save_your_backups_to),
        onClick = onOnDeviceBackupsRowClick
      )
    }
  }
}

@Composable
private fun BackupPlanRows(
  summary: String,
  action: String,
  onClick: () -> Unit
) {
  LightSettingsRow(
    title = stringResource(R.string.RemoteBackupsSettingsFragment__signal_backups),
    summary = summary,
    onClick = onClick
  )
  LightSettingsRow(title = action, onClick = onClick)
}

@Composable
private fun ActiveBackupPlanRows(
  backupState: BackupState.WithTypeAndRenewalTime,
  lastBackupAt: Duration,
  onClick: () -> Unit
) {
  val locale = LocalLocale.current.platformLocale
  val planSummary = when (val type = backupState.messageBackupsType) {
    is MessageBackupsType.Free -> stringResource(R.string.RemoteBackupsSettingsFragment__your_backup_plan_is_free)
    is MessageBackupsType.Paid -> when {
      backupState is BackupState.Canceled -> stringResource(R.string.BackupsSettingsFragment__subscription_canceled)
      type.pricePerMonth.amount == BigDecimal.ZERO -> stringResource(
        R.string.BackupsSettingsFragment_renews_s,
        DateUtils.formatDateWithYear(locale, backupState.renewalTime.inWholeMilliseconds)
      )
      else -> stringResource(
        R.string.BackupsSettingsFragment_s_month_renews_s,
        FiatMoneyUtil.format(LocalContext.current.resources, type.pricePerMonth),
        DateUtils.formatDateWithYear(locale, backupState.renewalTime.inWholeMilliseconds)
      )
    }
  }

  BackupPlanRows(
    summary = "$planSummary\n${lastBackupSummary(lastBackupAt)}",
    action = stringResource(R.string.BackupsSettingsFragment_view_settings),
    onClick = onClick
  )
}

@Composable
private fun lastBackupSummary(lastBackupAt: Duration): String {
  val context = LocalContext.current
  var label by remember(lastBackupAt) { mutableStateOf(calculateLastBackupTimeString(context, lastBackupAt)) }

  LaunchedEffect(lastBackupAt) {
    while (true) {
      delay(1.minutes)
      label = calculateLastBackupTimeString(context, lastBackupAt)
    }
  }

  return stringResource(R.string.BackupsSettingsFragment_last_backup_s, label)
}

private fun calculateLastBackupTimeString(context: Context, lastBackupAt: Duration): String {
  return if (lastBackupAt.inWholeMilliseconds > 0) {
    val relativeTime = DateUtils.getDatelessRelativeTimeSpanFormattedDate(
      context,
      Locale.getDefault(),
      lastBackupAt.inWholeMilliseconds
    )

    if (relativeTime.isRelative) {
      relativeTime.value
    } else {
      val day = DateUtils.getDayPrecisionTimeString(context, Locale.getDefault(), lastBackupAt.inWholeMilliseconds)
      context.getString(R.string.RemoteBackupsSettingsFragment__s_at_s, day, relativeTime.value)
    }
  } else {
    context.getString(R.string.RemoteBackupsSettingsFragment__never)
  }
}

@Composable
private fun InternalBackupOverrideRows(
  backupsSettingsState: BackupsSettingsState,
  onBackupTierInternalOverrideChanged: (MessageBackupTier?) -> Unit = {}
) {
  val options = remember {
    listOf(
      "Unset" to null,
      "Free" to MessageBackupTier.FREE,
      "Paid" to MessageBackupTier.PAID
    )
  }
  val deletionState by SignalStore.backup.deletionStateFlow.collectAsStateWithLifecycle(SignalStore.backup.deletionState)
  val enabled = deletionState == DeletionState.NONE || deletionState == DeletionState.COMPLETE

  options.forEach { (label, tier) ->
    LightSettingsRadioRow(
      title = label,
      selected = tier == backupsSettingsState.backupTierInternalOverride,
      enabled = enabled,
      onClick = { onBackupTierInternalOverrideChanged(tier) }
    )
  }
}

@DayNightPreviews
@Composable
private fun BackupsSettingsContentPreview() {
  Previews.Preview {
    BackupsSettingsContent(
      backupsSettingsState = BackupsSettingsState(
        backupState = BackupState.ActivePaid(
          messageBackupsType = MessageBackupsType.Paid(
            pricePerMonth = FiatMoney(BigDecimal.valueOf(4), Currency.getInstance("CAD")),
            storageAllowanceBytes = 1_000_000,
            mediaTtl = 30.days
          ),
          renewalTime = 0.seconds,
          price = FiatMoney(BigDecimal.valueOf(4), Currency.getInstance("CAD"))
        ),
        lastBackupAt = 0.seconds
      )
    )
  }
}
