/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups.remote

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import kotlinx.coroutines.delay
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.util.bytes
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.ArchiveUploadProgress
import org.thoughtcrime.securesms.backup.DeletionState
import org.thoughtcrime.securesms.backup.RestoreState
import org.thoughtcrime.securesms.backup.v2.ArchiveRestoreProgressState
import org.thoughtcrime.securesms.backup.v2.ArchiveRestoreProgressState.RestoreStatus
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.ui.BackupAlert
import org.thoughtcrime.securesms.backup.v2.ui.BackupAlertBottomSheet
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsType
import org.thoughtcrime.securesms.billing.launchManageBackupsSubscription
import org.thoughtcrime.securesms.components.compose.BiometricsAuthentication
import org.thoughtcrime.securesms.components.compose.rememberBiometricsAuthentication
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.backups.BackupState
import org.thoughtcrime.securesms.components.settings.app.subscription.MessageBackupsCheckoutLauncher.createBackupsCheckoutLauncher
import org.thoughtcrime.securesms.help.HelpFragment
import org.thoughtcrime.securesms.keyvalue.BackupValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.protos.ArchiveUploadProgressState
import org.thoughtcrime.securesms.light.LightActionDialog
import org.thoughtcrime.securesms.light.LightConfirmationDialog
import org.thoughtcrime.securesms.light.LightDialogAction
import org.thoughtcrime.securesms.light.LightNoticeDialog
import org.thoughtcrime.securesms.light.LightProgressDialog
import org.thoughtcrime.securesms.light.LightSettingsDivider
import org.thoughtcrime.securesms.light.LightSettingsProgressRow
import org.thoughtcrime.securesms.light.LightSettingsRow
import org.thoughtcrime.securesms.light.LightSettingsScreen
import org.thoughtcrime.securesms.light.LightSettingsSection
import org.thoughtcrime.securesms.light.LightSettingsToggleRow
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionActivity
import org.thoughtcrime.securesms.payments.FiatMoneyUtil
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.viewModel
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Remote backups settings fragment. */
class RemoteBackupsSettingsFragment : ComposeFragment() {

  private val viewModel by viewModel { RemoteBackupsSettingsViewModel() }
  private val args: RemoteBackupsSettingsFragmentArgs by navArgs()
  private lateinit var checkoutLauncher: ActivityResultLauncher<MessageBackupTier?>

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsState()
    val backupProgress by ArchiveUploadProgress.progress.collectAsStateWithLifecycle(initialValue = null)
    val restoreState by viewModel.restoreState.collectAsState()
    val deleteState by SignalStore.backup.deletionStateFlow.collectAsStateWithLifecycle(initialValue = SignalStore.backup.deletionState)
    val callbacks = remember { Callbacks() }

    RemoteBackupsSettingsContent(
      state = state,
      backupRestoreState = restoreState,
      backupDeleteState = deleteState,
      contentCallbacks = callbacks,
      backupProgress = backupProgress
    )
  }

  @Stable
  private inner class Callbacks : ContentCallbacks {
    override fun onNavigationClick() {
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onBackupTypeActionClick(tier: MessageBackupTier) {
      when (tier) {
        MessageBackupTier.FREE -> checkoutLauncher.launch(MessageBackupTier.PAID)
        MessageBackupTier.PAID -> launchManageBackupsSubscription()
      }
    }

    override fun onLaunchBackupsCheckoutFlow() {
      checkoutLauncher.launch(null)
    }

    override fun onBackUpUsingCellularClick(canUseCellular: Boolean) {
      viewModel.setCanBackUpUsingCellular(canUseCellular)
    }

    override fun onBackupNowClick() {
      viewModel.onBackupNowClick(args.forQuickRestore)
    }

    override fun onTurnOffAndDeleteBackupsClick() {
      viewModel.requestDialog(RemoteBackupsSettingsState.Dialog.TURN_OFF_AND_DELETE_BACKUPS)
    }

    override fun onBackupFrequencyClick() {
      viewModel.requestDialog(RemoteBackupsSettingsState.Dialog.BACKUP_FREQUENCY)
    }

    override fun onDialogDismissed() {
      viewModel.requestDialog(RemoteBackupsSettingsState.Dialog.NONE)
    }

    override fun onSnackbarDismissed() {
      viewModel.requestSnackbar(RemoteBackupsSettingsState.Snackbar.NONE)
    }

    override fun onTurnOffAndDeleteBackupsConfirm() {
      viewModel.turnOffAndDeleteBackups()
    }

    override fun onViewBackupKeyClick() {
      findNavController().safeNavigate(R.id.action_remoteBackupsSettingsFragment_to_backupKeyDisplayFragment)
    }

    override fun onStartMediaRestore() {
      viewModel.beginMediaRestore()
    }

    override fun onCancelMediaRestore() {
      viewModel.cancelMediaRestore()
    }

    override fun onDisplaySkipMediaRestoreProtectionDialog() {
      viewModel.requestDialog(RemoteBackupsSettingsState.Dialog.SKIP_MEDIA_RESTORE_PROTECTION)
    }

    override fun onSkipMediaRestore() {
      viewModel.skipMediaRestore()
    }

    override fun onLearnMoreAboutLostSubscription() {
      viewModel.requestDialog(RemoteBackupsSettingsState.Dialog.SUBSCRIPTION_NOT_FOUND)
    }

    override fun onRenewLostSubscription() {
      checkoutLauncher.launch(MessageBackupTier.PAID)
    }

    override fun onCancelUploadClick() {
      viewModel.cancelUpload()
    }

    override fun onContactSupport() {
      requireActivity().finish()
      requireActivity().startActivity(AppSettingsActivity.help(requireContext(), HelpFragment.REMOTE_BACKUPS_INDEX))
    }

    override fun onLearnMoreAboutBackupFailure() {
      BackupAlertBottomSheet.create(BackupAlert.BackupFailed).show(parentFragmentManager, null)
    }

    override fun onRestoreUsingCellularConfirm() {
      viewModel.requestDialog(RemoteBackupsSettingsState.Dialog.RESTORE_OVER_CELLULAR_PROTECTION)
    }

    override fun onRestoreUsingCellularClick() {
      viewModel.setCanRestoreUsingCellular()
    }

    override fun onRedemptionErrorDetailsClick() {
      BackupAlertBottomSheet.create(BackupAlert.CouldNotRedeemBackup).show(parentFragmentManager, null)
    }

    override fun onDisplayProgressDialog() {
      viewModel.requestDialog(RemoteBackupsSettingsState.Dialog.PROGRESS_SPINNER)
    }

    override fun onDisplayDownloadingBackupDialog() {
      viewModel.requestDialog(RemoteBackupsSettingsState.Dialog.DOWNLOADING_YOUR_BACKUP)
    }

    override fun onManageStorageClick() {
      requireActivity().finish()
      requireActivity().startActivity(AppSettingsActivity.manageStorage(requireActivity()))
    }

    override fun onIncludeDebuglogClick(newState: Boolean) {
      viewModel.setIncludeDebuglog(newState)
    }

    override fun onMediaBackupSizeClick() {
      viewModel.requestDialog(RemoteBackupsSettingsState.Dialog.FREE_TIER_MEDIA_EXPLAINER)
    }

    override fun onFreeTierBackupSizeLearnMore() {
      CommunicationActions.openBrowserLink(requireContext(), "https://support.signal.org/hc/articles/9708267671322")
    }

    override fun onTransferScanQrCodeClick() {
      startActivity(MediaSelectionActivity.camera(requireContext()))
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    checkoutLauncher = createBackupsCheckoutLauncher { backUpLater ->
      if (backUpLater) {
        viewModel.requestSnackbar(RemoteBackupsSettingsState.Snackbar.BACKUP_WILL_BE_CREATED_OVERNIGHT)
      }
    }

    setFragmentResultListener(BackupKeyDisplayFragment.AEP_ROTATION_KEY) { _, bundle ->
      if (bundle.getBoolean(BackupKeyDisplayFragment.AEP_ROTATION_KEY, false)) {
        viewModel.getKeyRotationLimit()
        viewModel.requestSnackbar(RemoteBackupsSettingsState.Snackbar.AEP_KEY_ROTATED)
      }
    }

    if (savedInstanceState == null && args.backupLaterSelected) {
      viewModel.requestSnackbar(RemoteBackupsSettingsState.Snackbar.BACKUP_WILL_BE_CREATED_OVERNIGHT)
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
  }
}

private interface ContentCallbacks {
  fun onNavigationClick() = Unit
  fun onLaunchBackupsCheckoutFlow() = Unit
  fun onBackupTypeActionClick(tier: MessageBackupTier) = Unit
  fun onBackUpUsingCellularClick(canUseCellular: Boolean) = Unit
  fun onBackupNowClick() = Unit
  fun onCancelUploadClick() = Unit
  fun onTurnOffAndDeleteBackupsClick() = Unit
  fun onBackupFrequencyClick() = Unit
  fun onDialogDismissed() = Unit
  fun onSnackbarDismissed() = Unit
  fun onTurnOffAndDeleteBackupsConfirm() = Unit
  fun onViewBackupKeyClick() = Unit
  fun onStartMediaRestore() = Unit
  fun onDisplaySkipMediaRestoreProtectionDialog() = Unit
  fun onSkipMediaRestore() = Unit
  fun onCancelMediaRestore() = Unit
  fun onRenewLostSubscription() = Unit
  fun onLearnMoreAboutLostSubscription() = Unit
  fun onContactSupport() = Unit
  fun onLearnMoreAboutBackupFailure() = Unit
  fun onRestoreUsingCellularConfirm() = Unit
  fun onRestoreUsingCellularClick() = Unit
  fun onRedemptionErrorDetailsClick() = Unit
  fun onDisplayProgressDialog() = Unit
  fun onDisplayDownloadingBackupDialog() = Unit
  fun onManageStorageClick() = Unit
  fun onIncludeDebuglogClick(newState: Boolean) = Unit
  fun onMediaBackupSizeClick() = Unit
  fun onFreeTierBackupSizeLearnMore() = Unit
  fun onTransferScanQrCodeClick() = Unit

  object Empty : ContentCallbacks
}

@Composable
private fun RemoteBackupsSettingsContent(
  state: RemoteBackupsSettingsState,
  backupRestoreState: BackupRestoreState,
  backupDeleteState: DeletionState,
  contentCallbacks: ContentCallbacks,
  backupProgress: ArchiveUploadProgressState?
) {
  val context = LocalContext.current
  val biometrics = rememberBiometricsAuthentication(
    promptTitle = stringResource(R.string.RemoteBackupsSettingsFragment__unlock_to_view_backup_key),
    educationSheetMessage = stringResource(R.string.RemoteBackupsSettingsFragment__to_view_your_key),
    onAuthenticationFailed = {
      Toast.makeText(context, R.string.RemoteBackupsSettingsFragment__authentication_required, Toast.LENGTH_SHORT).show()
    }
  )

  LaunchedEffect(backupDeleteState) {
    if (backupDeleteState != DeletionState.NONE && backupDeleteState != DeletionState.CLEAR_LOCAL_STATE) {
      contentCallbacks.onDialogDismissed()
    }
    if (backupDeleteState == DeletionState.AWAITING_MEDIA_DOWNLOAD) {
      contentCallbacks.onDisplayDownloadingBackupDialog()
    }
  }

  val snackbarText = remoteBackupSnackbarText(state.snackbar)
  MollyLightTheme {
    LightSettingsScreen(
      title = stringResource(R.string.RemoteBackupsSettingsFragment__secure_backups),
      onBack = contentCallbacks::onNavigationClick
    ) {
      if (snackbarText != null) {
        LightSettingsRow(title = snackbarText, onClick = null)
      }

      if (state.isOutOfStorageSpace) {
        LightSettingsRow(
          title = stringResource(R.string.RemoteBackupsSettingsFragment__youve_reached_the_s_storage_limit, state.totalAllowedStorageSpace),
          onClick = null
        )
        LightSettingsRow(
          title = stringResource(R.string.RemoteBackupsSettingsFragment__manage_storage),
          onClick = contentCallbacks::onManageStorageClick
        )
        LightSettingsDivider()
      }

      if (state.hasRedemptionError) {
        LightSettingsRow(
          title = stringResource(R.string.AppSettingsFragment__couldnt_redeem_your_backups_subscription),
          onClick = null
        )
        LightSettingsRow(
          title = stringResource(R.string.RemoteBackupsSettingsFragment__details),
          onClick = contentCallbacks::onRedemptionErrorDetailsClick
        )
      }

      BackupPlanContent(
        state = state,
        backupDeleteState = backupDeleteState,
        callbacks = contentCallbacks
      )

      if (backupDeleteState != DeletionState.NONE && backupDeleteState != DeletionState.CLEAR_LOCAL_STATE) {
        BackupDeletionContent(
          backupDeleteState = backupDeleteState,
          backupRestoreState = backupRestoreState,
          canRestoreUsingCellular = state.canRestoreUsingCellular,
          callbacks = contentCallbacks
        )
      } else if (state.backupsEnabled) {
        BackupDetailsContent(
          state = state,
          backupRestoreState = backupRestoreState,
          backupProgress = backupProgress,
          callbacks = contentCallbacks,
          biometrics = biometrics
        )
      } else {
        state.backupCreationError?.let {
          BackupCreationErrorContent(
            error = it,
            lastMessageCutoffTime = state.lastMessageCutoffTime,
            onLearnMoreClick = contentCallbacks::onLearnMoreAboutBackupFailure
          )
        }
        if (backupRestoreState is BackupRestoreState.Restoring) {
          RestoreStatusContent(
            state = backupRestoreState.state,
            isDownload = false,
            isCancelable = true,
            canRestoreUsingCellular = state.canRestoreUsingCellular,
            protectSkip = false,
            callbacks = contentCallbacks
          )
        }
        LightSettingsRow(
          title = stringResource(R.string.RemoteBackupsSettingsFragment__backups_have_been_turned_off),
          onClick = null
        )
        ReenableBackupsRow(contentCallbacks)
      }
    }
  }

  if (state.snackbar != RemoteBackupsSettingsState.Snackbar.NONE) {
    LaunchedEffect(state.snackbar) {
      delay(4.seconds)
      contentCallbacks.onSnackbarDismissed()
    }
  }

  RemoteBackupDialog(
    state = state,
    backupDeleteState = backupDeleteState,
    callbacks = contentCallbacks
  )
}

@Composable
private fun BackupPlanContent(
  state: RemoteBackupsSettingsState,
  backupDeleteState: DeletionState,
  callbacks: ContentCallbacks
) {
  when (val backupState = state.backupState) {
    is BackupState.LocalStore -> LightSettingsProgressRow(
      title = stringResource(R.string.RemoteBackupsSettingsFragment__secure_backups)
    )

    is BackupState.Error -> LightSettingsProgressRow(
      title = stringResource(R.string.RemoteBackupsSettingsFragment__waiting_for_network)
    )

    is BackupState.Pending -> LightSettingsProgressRow(
      title = stringResource(R.string.MessageBackupsTypeSelectionScreen__text_plus_all_your_media),
      summary = "${stringResource(R.string.RemoteBackupsSettingsFragment__s_per_month, FiatMoneyUtil.format(LocalContext.current.resources, backupState.price))}\n${stringResource(R.string.RemoteBackupsSettingsFragment__payment_pending)}"
    )

    is BackupState.SubscriptionMismatchMissingGooglePlay -> {
      val days = (backupState.renewalTime - System.currentTimeMillis().milliseconds).inWholeDays
      MissingSubscriptionRows(
        title = pluralStringResource(
          R.plurals.RemoteBackupsSettingsFragment__your_subscription_on_this_device_is_valid,
          days.toInt(),
          days
        ),
        renewEnabled = backupDeleteState.isIdle(),
        callbacks = callbacks
      )
    }

    BackupState.NotFound -> MissingSubscriptionRows(
      title = stringResource(R.string.RemoteBackupsSettingsFragment__your_subscription_was_not_found),
      renewEnabled = backupDeleteState.isIdle(),
      callbacks = callbacks
    )

    is BackupState.None -> Unit

    is BackupState.WithTypeAndRenewalTime -> ActivePlanRows(
      backupState = backupState,
      isPaidTierPricingAvailable = state.isPaidTierPricingAvailable,
      isGooglePlayServicesAvailable = state.isGooglePlayServicesAvailable,
      actionsEnabled = backupDeleteState.isIdle(),
      onAction = callbacks::onBackupTypeActionClick
    )
  }
}

@Composable
private fun MissingSubscriptionRows(
  title: String,
  renewEnabled: Boolean,
  callbacks: ContentCallbacks
) {
  LightSettingsRow(title = title, onClick = null)
  LightSettingsRow(
    title = stringResource(R.string.RemoteBackupsSettingsFragment__renew),
    onClick = callbacks::onRenewLostSubscription
  )
  LightSettingsRow(
    title = stringResource(R.string.RemoteBackupsSettingsFragment__learn_more),
    enabled = renewEnabled,
    onClick = callbacks::onLearnMoreAboutLostSubscription
  )
}

@Composable
private fun ActivePlanRows(
  backupState: BackupState.WithTypeAndRenewalTime,
  isPaidTierPricingAvailable: Boolean,
  isGooglePlayServicesAvailable: Boolean,
  actionsEnabled: Boolean,
  onAction: (MessageBackupTier) -> Unit
) {
  val type = backupState.messageBackupsType
  val title = when (type) {
    is MessageBackupsType.Paid -> stringResource(R.string.MessageBackupsTypeSelectionScreen__text_plus_all_your_media)
    is MessageBackupsType.Free -> pluralStringResource(
      R.plurals.MessageBackupsTypeSelectionScreen__text_plus_d_days_of_media,
      type.mediaRetentionDays,
      type.mediaRetentionDays
    )
  }
  val status = when (backupState) {
    is BackupState.ActivePaid -> stringResource(
      R.string.RemoteBackupsSettingsFragment__s_per_month,
      FiatMoneyUtil.format(LocalContext.current.resources, backupState.price)
    )
    is BackupState.ActiveFree -> stringResource(R.string.RemoteBackupsSettingsFragment__your_backup_plan_is_free)
    is BackupState.Inactive -> when (type) {
      is MessageBackupsType.Paid -> stringResource(R.string.RemoteBackupsSettingsFragment__subscription_inactive)
      is MessageBackupsType.Free -> stringResource(R.string.RemoteBackupsSettingsFragment__you_turned_off_backups)
    }
    is BackupState.Canceled -> stringResource(R.string.RemoteBackupsSettingsFragment__subscription_cancelled)
    else -> null
  }
  val renewal = if (type is MessageBackupsType.Paid && backupState.renewalTime > 0.seconds) {
    val date = DateUtils.formatDateWithYear(LocalLocale.current.platformLocale, backupState.renewalTime.inWholeMilliseconds)
    when (backupState) {
      is BackupState.ActivePaid -> stringResource(R.string.RemoteBackupsSettingsFragment__renews_s, date)
      is BackupState.Inactive -> stringResource(R.string.RemoteBackupsSettingsFragment__expired_on_s, date)
      is BackupState.Canceled -> stringResource(R.string.RemoteBackupsSettingsFragment__expires_on_s, date)
      else -> null
    }
  } else {
    null
  }
  val details = listOfNotNull(status, renewal).joinToString("\n")

  LightSettingsRow(title = title, summary = details, onClick = null)

  if (backupState.isActive() && isPaidTierPricingAvailable && isGooglePlayServicesAvailable) {
    LightSettingsRow(
      title = when (type) {
        is MessageBackupsType.Paid -> stringResource(R.string.RemoteBackupsSettingsFragment__manage_or_cancel)
        is MessageBackupsType.Free -> stringResource(R.string.RemoteBackupsSettingsFragment__upgrade)
      },
      enabled = actionsEnabled,
      onClick = { onAction(type.tier) }
    )
  } else if (backupState is BackupState.Canceled) {
    LightSettingsRow(
      title = stringResource(R.string.RemoteBackupsSettingsFragment__resubscribe),
      enabled = actionsEnabled,
      onClick = { onAction(MessageBackupTier.FREE) }
    )
  }
}

@Composable
private fun BackupDetailsContent(
  state: RemoteBackupsSettingsState,
  backupRestoreState: BackupRestoreState,
  backupProgress: ArchiveUploadProgressState?,
  callbacks: ContentCallbacks,
  biometrics: BiometricsAuthentication
) {
  LightSettingsDivider()
  LightSettingsSection(stringResource(R.string.RemoteBackupsSettingsFragment__backup_details))

  state.backupCreationError?.let {
    BackupCreationErrorContent(
      error = it,
      lastMessageCutoffTime = state.lastMessageCutoffTime,
      onLearnMoreClick = callbacks::onLearnMoreAboutBackupFailure
    )
  }

  when (backupRestoreState) {
    BackupRestoreState.None -> Unit
    is BackupRestoreState.Ready -> {
      val body = if (state.backupState is BackupState.Canceled) {
        stringResource(R.string.RemoteBackupsSettingsFragment__you_have_s_of_backup_data, backupRestoreState.bytes)
      } else {
        stringResource(R.string.RemoteBackupsSettingsFragment__you_have_s_of_backup_data_not_on_device, backupRestoreState.bytes)
      }
      LightSettingsRow(title = body, onClick = null)
      LightSettingsRow(
        title = stringResource(R.string.RemoteBackupsSettingsFragment__download),
        onClick = callbacks::onStartMediaRestore
      )
    }
    is BackupRestoreState.Restoring -> RestoreStatusContent(
      state = backupRestoreState.state,
      isDownload = false,
      isCancelable = true,
      canRestoreUsingCellular = state.canRestoreUsingCellular,
      protectSkip = true,
      callbacks = callbacks
    )
  }

  val isRestoringDatabase = backupRestoreState is BackupRestoreState.Restoring && backupRestoreState.state.restoreState == RestoreState.RESTORING_DB
  if (!isRestoringDatabase && (backupProgress == null || backupProgress.state == ArchiveUploadProgressState.State.None || backupProgress.state == ArchiveUploadProgressState.State.UserCanceled)) {
    val lastBackup = if (state.lastBackupTimestamp > 0) {
      val context = LocalContext.current
      val day = remember(state.lastBackupTimestamp) {
        DateUtils.getDayPrecisionTimeString(context, Locale.getDefault(), state.lastBackupTimestamp)
      }
      val time = remember(state.lastBackupTimestamp) {
        DateUtils.getOnlyTimeString(context, state.lastBackupTimestamp)
      }
      stringResource(R.string.RemoteBackupsSettingsFragment__s_at_s, day, time)
    } else {
      stringResource(R.string.RemoteBackupsSettingsFragment__never)
    }
    LightSettingsRow(
      title = stringResource(R.string.RemoteBackupsSettingsFragment__last_backup),
      summary = lastBackup,
      onClick = callbacks::onBackupFrequencyClick
    )
    LightSettingsRow(
      title = stringResource(R.string.RemoteBackupsSettingsFragment__back_up_now),
      enabled = !state.isOutOfStorageSpace,
      onClick = callbacks::onBackupNowClick
    )
  } else if (backupProgress != null) {
    LightSettingsProgressRow(
      title = stringResource(R.string.RemoteBackupsSettingsFragment__last_backup),
      summary = uploadProgressMessage(
        backupProgress,
        isPaidTier = state.tier == MessageBackupTier.PAID,
        canBackupMessagesRun = state.canBackupMessagesJobRun,
        canBackupUsingCellular = state.canBackUpUsingCellular
      )
    )
    if (backupProgress.state != ArchiveUploadProgressState.State.None && backupProgress.state != ArchiveUploadProgressState.State.UserCanceled) {
      LightSettingsRow(
        title = stringResource(R.string.RemoteBackupsSettingsFragment__cancel_upload),
        onClick = callbacks::onCancelUploadClick
      )
    }
  }

  val sizeText = if (state.backupMediaSize < 0L) {
    stringResource(R.string.RemoteBackupsSettingsFragment__calculating)
  } else {
    state.backupMediaSize.bytes.toUnitString()
  }
  LightSettingsRow(
    title = stringResource(R.string.RemoteBackupsSettingsFragment__backup_size),
    summary = sizeText,
    onClick = if (state.backupMediaSize >= 0L && state.tier == MessageBackupTier.FREE) callbacks::onMediaBackupSizeClick else null
  )
  LightSettingsToggleRow(
    title = stringResource(R.string.RemoteBackupsSettingsFragment__back_up_using_cellular),
    checked = state.canBackUpUsingCellular,
    onCheckedChange = callbacks::onBackUpUsingCellularClick
  )
  LightSettingsRow(
    title = stringResource(R.string.RemoteBackupsSettingsFragment__view_backup_key),
    enabled = state.canViewBackupKey,
    onClick = { biometrics.withBiometricsAuthentication { callbacks.onViewBackupKeyClick() } }
  )

  if (state.internalUser) {
    LightSettingsSection("INTERNAL ONLY")
    state.includeDebuglog?.let {
      LightSettingsToggleRow(
        title = "Include debuglog?",
        summary = "If enabled, we will capture a debuglog and include it in the backup file.",
        checked = it,
        onCheckedChange = callbacks::onIncludeDebuglogClick
      )
    }
    state.backupMediaDetails?.let {
      LightSettingsRow(
        title = "Backup Media Details",
        summary = "Awaiting Restore: ${it.awaitingRestore.toUnitString()}\nOffloaded: ${it.offloaded.toUnitString()}\nLast Proto Size: ${it.protoFileSize.toUnitString()}",
        onClick = null
      )
    }
  }

  LightSettingsDivider()
  LightSettingsRow(
    title = stringResource(R.string.RemoteBackupsSettingsFragment__turn_off_and_delete_backup),
    onClick = callbacks::onTurnOffAndDeleteBackupsClick
  )
}

@Composable
private fun BackupCreationErrorContent(
  error: BackupValues.BackupCreationError,
  lastMessageCutoffTime: Long,
  onLearnMoreClick: () -> Unit
) {
  val context = LocalContext.current
  val message = when (error) {
    BackupValues.BackupCreationError.TRANSIENT -> stringResource(R.string.BackupStatusRow__your_last_backup)
    BackupValues.BackupCreationError.VALIDATION -> stringResource(R.string.BackupStatusRow__your_last_backup_latest_version)
    BackupValues.BackupCreationError.BACKUP_FILE_TOO_LARGE -> if (lastMessageCutoffTime > 0) {
      stringResource(
        R.string.BackupStatusRow__not_backing_up_old_messages,
        DateUtils.getDayPrecisionTimeString(context, Locale.getDefault(), lastMessageCutoffTime)
      )
    } else {
      stringResource(R.string.BackupStatusRow__backup_file_too_large)
    }
    BackupValues.BackupCreationError.NOT_ENOUGH_DISK_SPACE -> stringResource(
      R.string.BackupStatusRow__not_enough_disk_space,
      DateUtils.getDayPrecisionTimeString(context, Locale.getDefault(), lastMessageCutoffTime)
    )
  }
  LightSettingsRow(title = message, onClick = null)
  if (error == BackupValues.BackupCreationError.VALIDATION) {
    LightSettingsRow(
      title = stringResource(R.string.BackupStatusRow__learn_more),
      onClick = onLearnMoreClick
    )
  }
}

@Composable
private fun BackupDeletionContent(
  backupDeleteState: DeletionState,
  backupRestoreState: BackupRestoreState,
  canRestoreUsingCellular: Boolean,
  callbacks: ContentCallbacks
) {
  when (backupDeleteState) {
    DeletionState.NONE, DeletionState.CLEAR_LOCAL_STATE -> Unit
    DeletionState.FAILED -> {
      LightSettingsRow(
        title = stringResource(R.string.RemoteBackupsSettingsFragment__backups_have_been_turned_off_but_there_was_an_error),
        onClick = null
      )
      LightSettingsRow(
        title = stringResource(R.string.RemoteBackupsSettingsFragment__failed_to_delete_backup),
        summary = stringResource(R.string.RemoteBackupsSettingsFragment__an_error_occurred_please_contact_support),
        onClick = null
      )
      ReenableBackupsRow(callbacks)
    }
    DeletionState.AWAITING_MEDIA_DOWNLOAD -> {
      LightSettingsRow(
        title = stringResource(R.string.RemoteBackupsSettingsFragment__backups_have_been_turned_off_your_data_will_be),
        onClick = null
      )
      if (backupRestoreState is BackupRestoreState.Restoring) {
        RestoreStatusContent(
          state = backupRestoreState.state,
          isDownload = true,
          isCancelable = false,
          canRestoreUsingCellular = canRestoreUsingCellular,
          protectSkip = true,
          callbacks = callbacks
        )
      } else {
        LightSettingsProgressRow(title = stringResource(R.string.RemoteBackupsSettingsFragment__downloading_your_backup))
      }
    }
    DeletionState.MEDIA_DOWNLOAD_FINISHED, DeletionState.DELETE_BACKUPS -> {
      LightSettingsRow(
        title = stringResource(R.string.RemoteBackupsSettingsFragment__backups_have_been_turned_off_and_your_data),
        onClick = null
      )
      LightSettingsProgressRow(title = stringResource(R.string.RemoteBackupsSettingsFragment__deleting_backup))
    }
    DeletionState.COMPLETE -> {
      LightSettingsRow(
        title = stringResource(R.string.RemoteBackupsSettingsFragment__backups_have_been_turned_off_and_your_data_has_been_deleted),
        onClick = null
      )
      ReenableBackupsRow(callbacks)
    }
  }
}

@Composable
private fun RestoreStatusContent(
  state: ArchiveRestoreProgressState,
  isDownload: Boolean,
  isCancelable: Boolean,
  canRestoreUsingCellular: Boolean,
  protectSkip: Boolean,
  callbacks: ContentCallbacks
) {
  if (state.restoreStatus == RestoreStatus.NOT_ENOUGH_DISK_SPACE) {
    LightSettingsRow(
      title = stringResource(R.string.BackupStatusRow__not_enough_space, state.remainingRestoreSize.toUnitString()),
      onClick = null
    )
    LightSettingsRow(
      title = stringResource(R.string.BackupStatusRow__skip_download),
      onClick = if (protectSkip) callbacks::onDisplaySkipMediaRestoreProtectionDialog else callbacks::onSkipMediaRestore
    )
  } else {
    LightSettingsProgressRow(
      title = if (isDownload) {
        stringResource(R.string.RemoteBackupsSettingsFragment__downloading_your_backup)
      } else {
        stringResource(R.string.BackupCreationProgressRow__processing_backup)
      },
      summary = restoreProgressMessage(state, isDownload)
    )
  }

  if (isCancelable) {
    LightSettingsRow(
      title = stringResource(R.string.BackupStatusRow__cancel_download),
      onClick = callbacks::onCancelMediaRestore
    )
  }

  if (state.restoreState == RestoreState.RESTORING_MEDIA && state.restoreStatus == RestoreStatus.WAITING_FOR_WIFI && !canRestoreUsingCellular) {
    LightSettingsRow(
      title = stringResource(R.string.RemoteBackupsSettingsFragment__resume_download),
      onClick = callbacks::onRestoreUsingCellularConfirm
    )
  }
}

@Composable
private fun restoreProgressMessage(state: ArchiveRestoreProgressState, isDownload: Boolean): String {
  val percent = ((state.progress ?: 0f) * 100).roundToInt()
  return when (state.restoreState) {
    RestoreState.CALCULATING_MEDIA, RestoreState.RESTORING_MEDIA -> when (state.restoreStatus) {
      RestoreStatus.RESTORING -> if (isDownload) {
        stringResource(
          R.string.BackupStatusRow__downloading_s_of_s_s,
          state.completedRestoredSize.toUnitString(2),
          state.totalRestoreSize.toUnitString(2),
          percent.toString()
        )
      } else {
        stringResource(
          R.string.BackupStatusRow__restoring_s_of_s_s,
          state.completedRestoredSize.toUnitString(2),
          state.totalRestoreSize.toUnitString(2),
          percent.toString()
        )
      }
      RestoreStatus.WAITING_FOR_INTERNET -> stringResource(if (isDownload) R.string.BackupStatusRow__download_no_internet else R.string.BackupStatusRow__restore_no_internet)
      RestoreStatus.WAITING_FOR_WIFI -> stringResource(if (isDownload) R.string.BackupStatusRow__download_waiting_for_wifi else R.string.BackupStatusRow__restore_waiting_for_wifi)
      RestoreStatus.LOW_BATTERY -> stringResource(if (isDownload) R.string.BackupStatusRow__download_device_has_low_battery else R.string.BackupStatusRow__restore_device_has_low_battery)
      RestoreStatus.FINISHED -> stringResource(R.string.BackupStatus__restore_complete)
      RestoreStatus.NOT_ENOUGH_DISK_SPACE -> stringResource(R.string.BackupStatusRow__not_enough_space, state.remainingRestoreSize.toUnitString())
      RestoreStatus.NONE, RestoreStatus.LOCAL_RESTORE_DIRECTORY_UNAVAILABLE -> stringResource(R.string.BackupCreationProgressRow__processing_backup)
    }
    RestoreState.CANCELING_MEDIA -> stringResource(R.string.BackupStatus__cancel_restore_media)
    RestoreState.NONE -> if (state.restoreStatus == RestoreStatus.FINISHED) {
      stringResource(R.string.BackupStatus__restore_complete)
    } else {
      stringResource(R.string.BackupCreationProgressRow__processing_backup)
    }
    RestoreState.PENDING, RestoreState.RESTORING_DB -> stringResource(R.string.BackupCreationProgressRow__processing_backup)
  }
}

@Composable
private fun uploadProgressMessage(
  state: ArchiveUploadProgressState,
  isPaidTier: Boolean,
  canBackupMessagesRun: Boolean,
  canBackupUsingCellular: Boolean
): String {
  return when (state.state) {
    ArchiveUploadProgressState.State.None, ArchiveUploadProgressState.State.UserCanceled -> stringResource(R.string.RemoteBackupsSettingsFragment__processing_backup)
    ArchiveUploadProgressState.State.Export -> when (state.backupPhase) {
      ArchiveUploadProgressState.BackupPhase.BackupPhaseNone -> when {
        canBackupMessagesRun -> stringResource(R.string.RemoteBackupsSettingsFragment__processing_backup)
        canBackupUsingCellular -> stringResource(R.string.RemoteBackupsSettingsFragment__Waiting_for_internet_connection)
        else -> stringResource(R.string.RemoteBackupsSettingsFragment__Waiting_for_Wifi)
      }
      ArchiveUploadProgressState.BackupPhase.Message -> pluralStringResource(
        R.plurals.RemoteBackupsSettingsFragment__processing_messages_progress_text,
        state.frameTotalCount.toInt(),
        "%,d".format(state.frameExportCount),
        "%,d".format(state.frameTotalCount),
        (state.frameExportProgress() * 100).toInt()
      )
      else -> stringResource(R.string.RemoteBackupsSettingsFragment__preparing_backup)
    }
    ArchiveUploadProgressState.State.UploadBackupFile, ArchiveUploadProgressState.State.UploadMedia -> {
      val percent = (state.uploadProgress() * 100).toInt()
      if (isPaidTier) {
        stringResource(
          R.string.RemoteBackupsSettingsFragment__uploading_s_of_s_d,
          state.uploadBytesUploaded.bytes.toUnitString(),
          state.uploadBytesTotal.bytes.toUnitString(),
          percent
        )
      } else {
        stringResource(R.string.RemoteBackupsSettingsFragment__uploading_d, percent)
      }
    }
  }
}

@Composable
private fun ReenableBackupsRow(callbacks: ContentCallbacks) {
  LightSettingsRow(
    title = stringResource(R.string.RemoteBackupsSettingsFragment__reenable_backups),
    onClick = { callbacks.onBackupTypeActionClick(MessageBackupTier.FREE) }
  )
}

@Composable
private fun remoteBackupSnackbarText(snackbar: RemoteBackupsSettingsState.Snackbar): String? {
  val id = when (snackbar) {
    RemoteBackupsSettingsState.Snackbar.NONE -> null
    RemoteBackupsSettingsState.Snackbar.BACKUP_DELETED_AND_TURNED_OFF -> R.string.RemoteBackupsSettingsFragment__backup_deleted_and_turned_off
    RemoteBackupsSettingsState.Snackbar.BACKUP_TYPE_CHANGED_AND_SUBSCRIPTION_CANCELLED -> R.string.RemoteBackupsSettingsFragment__backup_type_changed_and_subcription_deleted
    RemoteBackupsSettingsState.Snackbar.SUBSCRIPTION_CANCELLED -> R.string.RemoteBackupsSettingsFragment__subscription_cancelled
    RemoteBackupsSettingsState.Snackbar.DOWNLOAD_COMPLETE -> R.string.RemoteBackupsSettingsFragment__download_complete
    RemoteBackupsSettingsState.Snackbar.BACKUP_WILL_BE_CREATED_OVERNIGHT -> R.string.RemoteBackupsSettingsFragment__backup_will_be_created_overnight
    RemoteBackupsSettingsState.Snackbar.AEP_KEY_ROTATED -> R.string.RemoteBackupsSettingsFragment__new_backup_key_created
  }
  return if (id == null) null else stringResource(id)
}

@Composable
private fun RemoteBackupDialog(
  state: RemoteBackupsSettingsState,
  backupDeleteState: DeletionState,
  callbacks: ContentCallbacks
) {
  when (state.dialog) {
    RemoteBackupsSettingsState.Dialog.NONE -> Unit
    RemoteBackupsSettingsState.Dialog.TURN_OFF_FAILED -> LightNoticeDialog(
      title = stringResource(R.string.RemoteBackupsSettingsFragment__couldnt_turn_off_and_delete_backups),
      body = stringResource(R.string.RemoteBackupsSettingsFragment__a_network_error_occurred),
      onDismiss = callbacks::onDialogDismissed
    )
    RemoteBackupsSettingsState.Dialog.TURN_OFF_AND_DELETE_BACKUPS -> {
      val body = when (state.tier!!) {
        MessageBackupTier.FREE -> stringResource(R.string.RemoteBackupsSettingsFragment__your_backup_will_be_deleted_and_no_new_backups_will_be_created)
        MessageBackupTier.PAID -> stringResource(R.string.RemoteBackupsSettingsFragment__your_subscription_will_be_canceled)
      }
      LightConfirmationDialog(
        title = stringResource(R.string.RemoteBackupsSettingsFragment__turn_off_and_delete_backups),
        body = body,
        confirmLabel = stringResource(R.string.RemoteBackupsSettingsFragment__turn_off_and_delete),
        dismissLabel = stringResource(android.R.string.cancel),
        onConfirm = callbacks::onTurnOffAndDeleteBackupsConfirm,
        onDismiss = callbacks::onDialogDismissed,
        destructive = true
      )
    }
    RemoteBackupsSettingsState.Dialog.BACKUP_FREQUENCY -> LightNoticeDialog(
      title = stringResource(R.string.RemoteBackupsSettingsFragment__backup_frequency),
      body = stringResource(R.string.RemoteBackupsSettingsFragment__backup_frequency_dialog_body),
      onDismiss = callbacks::onDialogDismissed
    )
    RemoteBackupsSettingsState.Dialog.PROGRESS_SPINNER -> LightProgressDialog(
      title = stringResource(R.string.BackupCreationProgressRow__processing_backup),
      onDismiss = callbacks::onDialogDismissed
    )
    RemoteBackupsSettingsState.Dialog.DOWNLOADING_YOUR_BACKUP -> LightNoticeDialog(
      title = stringResource(R.string.RemoteBackupsSettingsFragment__downloading_your_backup),
      body = stringResource(R.string.RemoteBackupsSettingsFragment__depending_on_the_size),
      onDismiss = callbacks::onDialogDismissed
    )
    RemoteBackupsSettingsState.Dialog.SUBSCRIPTION_NOT_FOUND -> SubscriptionNotFoundBottomSheet(
      onDismiss = callbacks::onDialogDismissed,
      onContactSupport = callbacks::onContactSupport
    )
    RemoteBackupsSettingsState.Dialog.SKIP_MEDIA_RESTORE_PROTECTION -> {
      if (backupDeleteState.hasUx()) {
        LightConfirmationDialog(
          title = stringResource(R.string.RemoteBackupsSettingsFragment__skip_download_question),
          body = stringResource(R.string.RemoteBackupsSettingsFragment__if_you_skip_downloading_the_remaining),
          confirmLabel = stringResource(R.string.RemoteBackupsSettingsFragment__skip_and_delete_permanently),
          dismissLabel = stringResource(android.R.string.cancel),
          onConfirm = callbacks::onSkipMediaRestore,
          onDismiss = callbacks::onDialogDismissed,
          destructive = true
        )
      } else {
        val renewalTime = (state.backupState as? BackupState.WithTypeAndRenewalTime)?.renewalTime
          ?: error("Unexpected dialog display without renewal time.")
        val days = (renewalTime - System.currentTimeMillis().milliseconds).inWholeDays.toInt()
        LightConfirmationDialog(
          title = stringResource(R.string.RemoteBackupsSettingsFragment__skip_download_question),
          body = pluralStringResource(R.plurals.RemoteBackupsSettingsFragment__if_you_skip_downloading, days, days),
          confirmLabel = stringResource(R.string.RemoteBackupsSettingsFragment__skip),
          dismissLabel = stringResource(android.R.string.cancel),
          onConfirm = callbacks::onSkipMediaRestore,
          onDismiss = callbacks::onDialogDismissed,
          destructive = true
        )
      }
    }
    RemoteBackupsSettingsState.Dialog.CANCEL_MEDIA_RESTORE_PROTECTION -> LightConfirmationDialog(
      title = stringResource(R.string.RemoteBackupsSettingsFragment__skip_restore_question),
      body = stringResource(R.string.RemoteBackupsSettingsFragment__skip_restore_message),
      confirmLabel = stringResource(R.string.RemoteBackupsSettingsFragment__skip),
      dismissLabel = stringResource(android.R.string.cancel),
      onConfirm = callbacks::onSkipMediaRestore,
      onDismiss = callbacks::onDialogDismissed,
      destructive = true
    )
    RemoteBackupsSettingsState.Dialog.RESTORE_OVER_CELLULAR_PROTECTION -> LightConfirmationDialog(
      title = stringResource(R.string.ResumeRestoreCellular_resume_using_cellular_title),
      body = stringResource(R.string.ResumeRestoreCellular_resume_using_cellular_message),
      confirmLabel = stringResource(R.string.BackupStatus__resume),
      dismissLabel = stringResource(android.R.string.cancel),
      onConfirm = callbacks::onRestoreUsingCellularClick,
      onDismiss = callbacks::onDialogDismissed
    )
    RemoteBackupsSettingsState.Dialog.FREE_TIER_MEDIA_EXPLAINER -> LightActionDialog(
      title = stringResource(R.string.RemoteBackupsSettingsFragment__free_tier_storage_title),
      body = pluralStringResource(
        R.plurals.RemoteBackupsSettingsFragment__backup_frequency_dialog_body,
        state.freeTierMediaRetentionDays,
        state.freeTierMediaRetentionDays
      ),
      actions = listOf(
        LightDialogAction(stringResource(android.R.string.ok).uppercase(Locale.getDefault())) {
          callbacks.onDialogDismissed()
        },
        LightDialogAction(stringResource(R.string.RemoteBackupsSettingsFragment__learn_more).uppercase(Locale.getDefault())) {
          callbacks.onDialogDismissed()
          callbacks.onFreeTierBackupSizeLearnMore()
        }
      ),
      onDismiss = callbacks::onDialogDismissed
    )
    RemoteBackupsSettingsState.Dialog.KEY_ROTATION_LIMIT_REACHED -> LightNoticeDialog(
      title = stringResource(R.string.MessageBackupsKeyRecordScreen__limit_reached_title),
      body = stringResource(R.string.MessageBackupsKeyRecordScreen__limit_reached_body),
      onDismiss = callbacks::onDialogDismissed
    )
    RemoteBackupsSettingsState.Dialog.READY_TO_TRANSFER -> ReadyToTransferDialog(
      onScanQrCodeClick = callbacks::onTransferScanQrCodeClick,
      onDismiss = callbacks::onDialogDismissed
    )
  }
}

@Composable
private fun ReadyToTransferDialog(
  onScanQrCodeClick: () -> Unit,
  onDismiss: () -> Unit
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    MollyLightTheme {
      LightSettingsScreen(
        title = stringResource(R.string.RemoteBackupsSettingsFragment__ready_to_transfer),
        onBack = onDismiss
      ) {
        LightSettingsRow(
          title = stringResource(R.string.RemoteBackupsSettingsFragment__use_this_device_to_scan_qr),
          onClick = null
        )
        LightSettingsDivider()
        LightSettingsRow(
          title = stringResource(R.string.RemoteBackupsSettingsFragment__scan_qr_code),
          onClick = onScanQrCodeClick
        )
        LightSettingsRow(
          title = stringResource(android.R.string.cancel),
          onClick = onDismiss
        )
      }
    }
  }
}

private fun DeletionState.hasUx() = this !in setOf(DeletionState.NONE, DeletionState.CLEAR_LOCAL_STATE)

private fun ArchiveUploadProgressState.frameExportProgress(): Float {
  return if (frameTotalCount == 0L) 0f else frameExportCount / frameTotalCount.toFloat()
}

private fun ArchiveUploadProgressState.uploadProgress(): Float {
  val current = backupFileUploadedBytes + mediaUploadedBytes
  val total = backupFileTotalBytes + mediaTotalBytes
  return if (total == 0L) 0f else current / total.toFloat()
}

private val ArchiveUploadProgressState.uploadBytesTotal: Long
  get() = backupFileTotalBytes + mediaTotalBytes

private val ArchiveUploadProgressState.uploadBytesUploaded: Long
  get() = backupFileUploadedBytes + mediaUploadedBytes
