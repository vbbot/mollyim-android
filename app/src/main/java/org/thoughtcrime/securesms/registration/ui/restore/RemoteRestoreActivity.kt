/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightProgressBar
import com.thelightphone.sdk.ui.LightScrollBarPosition
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.util.ThreadUtil
import org.signal.core.util.bytes
import org.thoughtcrime.securesms.BaseActivity
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.v2.MessageBackupTier
import org.thoughtcrime.securesms.backup.v2.RestoreV2Event
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsTypeFeature
import org.thoughtcrime.securesms.components.contactsupport.ContactSupportCallbacks
import org.thoughtcrime.securesms.components.contactsupport.ContactSupportDialog
import org.thoughtcrime.securesms.components.contactsupport.ContactSupportViewModel
import org.thoughtcrime.securesms.components.contactsupport.SendSupportEmailEffect
import org.thoughtcrime.securesms.conversation.v2.registerForLifecycle
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.light.LightConfirmAction
import org.thoughtcrime.securesms.light.LightConfirmDialog
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.light.rememberMollyLightColors
import org.thoughtcrime.securesms.registration.util.RegistrationUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.PlayStoreUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.viewModel
import kotlin.time.Duration

/**
 * Restore backup from remote source.
 */
@SuppressLint("BaseActivitySubclass")
class RemoteRestoreActivity : BaseActivity() {
  companion object {

    private const val KEY_ONLY_OPTION = "ONLY_OPTION"

    fun getIntent(context: Context, isOnlyOption: Boolean = false): Intent {
      return Intent(context, RemoteRestoreActivity::class.java).apply {
        putExtra(KEY_ONLY_OPTION, isOnlyOption)
      }
    }
  }

  private val viewModel: RemoteRestoreViewModel by viewModel {
    RemoteRestoreViewModel(intent.getBooleanExtra(KEY_ONLY_OPTION, false))
  }

  private val contactSupportViewModel: ContactSupportViewModel<ContactSupportReason> by viewModels()

  private lateinit var wakeLock: RemoteRestoreWakeLock

  private val eventBusSubscriber = EventBusSubscriber()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    wakeLock = RemoteRestoreWakeLock(this)

    lifecycleScope.launch {
      lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        val restored = viewModel
          .state
          .map { it.importState }
          .filterIsInstance<RemoteRestoreViewModel.ImportState.Restored>()
          .firstOrNull()

        if (restored != null) {
          RegistrationUtil.maybeMarkRegistrationComplete()
          startActivity(MainActivity.clearTop(this@RemoteRestoreActivity))
          finishAffinity()
        }
      }
    }

    lifecycleScope.launch {
      lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        viewModel
          .state
          .map { it.importState }
          .collect {
            when (it) {
              RemoteRestoreViewModel.ImportState.InProgress -> {
                wakeLock.acquire()
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
              }

              else -> {
                wakeLock.release()
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
              }
            }
          }
      }
    }

    lifecycleScope.launch {
      lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        while (isActive) {
          if (TextSecurePreferences.isUnauthorizedReceived(this@RemoteRestoreActivity)) {
            ThreadUtil.runOnMain { showUnregisteredDialog() }
            break
          }
          delay(1000)
        }
      }
    }

    setContent {
      val state: RemoteRestoreViewModel.ScreenState by viewModel.state.collectAsStateWithLifecycle()
      val contactSupportState: ContactSupportViewModel.ContactSupportState<ContactSupportReason> by contactSupportViewModel.state.collectAsStateWithLifecycle()
      var showSkipRestoreWarning by remember { mutableStateOf(false) }

      SendSupportEmailEffect(
        contactSupportState = contactSupportState,
        subjectRes = { reason ->
          when (reason) {
            ContactSupportReason.SvrBFailure -> R.string.EnterBackupKey_permanent_failure_support_email
            else -> R.string.EnterBackupKey_network_failure_support_email
          }
        },
        filterRes = { reason ->
          when (reason) {
            ContactSupportReason.SvrBFailure -> R.string.EnterBackupKey_permanent_failure_support_email_filter
            else -> R.string.EnterBackupKey_network_failure_support_email_filter
          }
        }
      ) {
        contactSupportViewModel.hideContactSupport()
      }

      MollyLightTheme {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(LightThemeTokens.colors.background)
        ) {
          RestoreFromBackupContent(
            state = state,
            contactSupportState = contactSupportState,
            onRestoreBackupClick = { viewModel.restore() },
            onRetryRestoreTier = { viewModel.reload() },
            onContactSupport = { contactSupportViewModel.showContactSupport() },
            onCancelClick = {
              if (state.isRemoteRestoreOnlyOption) {
                showSkipRestoreWarning = true
              } else {
                finish()
              }
            },
            onImportErrorDialogDismiss = { viewModel.clearError() },
            onUpdateSignal = {
              PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(this@RemoteRestoreActivity)
            },
            contactSupportCallbacks = contactSupportViewModel
          )

          if (showSkipRestoreWarning) {
            LightRemoteConfirmation(
              title = stringResource(R.string.SelectRestoreMethodFragment__skip_restore_title),
              message = stringResource(R.string.SelectRestoreMethodFragment__skip_restore_warning),
              confirmLabel = stringResource(R.string.SelectRestoreMethodFragment__skip_restore),
              dismissLabel = stringResource(android.R.string.cancel),
              onConfirm = {
                lifecycleScope.launch {
                  viewModel.skipRestore()
                  viewModel.performStorageServiceAccountRestoreIfNeeded()
                  startActivity(MainActivity.clearTop(this@RemoteRestoreActivity))
                  finish()
                }
              },
              onDismiss = { showSkipRestoreWarning = false },
              dismissOnBack = false
            )
          }
        }
      }
    }

    EventBus.getDefault().registerForLifecycle(subscriber = eventBusSubscriber, lifecycleOwner = this)
  }

  private inner class EventBusSubscriber {
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEvent(restoreEvent: RestoreV2Event) {
      viewModel.updateRestoreProgress(restoreEvent)
    }
  }

  /**
   * Window-hosted, like the Material dialog it replaces, so it covers whichever restore state is on
   * screen. Not cancelable: OK is the only way out, and it dismisses before it leaves, as the
   * Material button did.
   */
  private fun showUnregisteredDialog() {
    val dialog = LightConfirmDialog.create(
      context = this,
      title = getString(R.string.RestoreActivity__no_longer_registered_title),
      message = getString(R.string.RestoreActivity__no_longer_registered_message),
      cancelable = false
    )
    dialog.setActions(
      listOf(
        LightConfirmAction(getString(android.R.string.ok)) {
          dialog.dismiss()
          viewModel.skipRestore()
          startActivity(MainActivity.clearTop(this))
          supportFinishAfterTransition()
        }
      )
    )
    dialog.show()
  }

  enum class ContactSupportReason {
    NetworkError, SvrBFailure
  }
}

@Composable
private fun RestoreFromBackupContent(
  state: RemoteRestoreViewModel.ScreenState,
  contactSupportState: ContactSupportViewModel.ContactSupportState<RemoteRestoreActivity.ContactSupportReason> = ContactSupportViewModel.ContactSupportState(),
  onRestoreBackupClick: () -> Unit = {},
  onRetryRestoreTier: () -> Unit = {},
  onContactSupport: () -> Unit = {},
  onCancelClick: () -> Unit = {},
  onImportErrorDialogDismiss: () -> Unit = {},
  onUpdateSignal: () -> Unit = {},
  contactSupportCallbacks: ContactSupportCallbacks = ContactSupportCallbacks.Empty
) {
  when (state.loadState) {
    RemoteRestoreViewModel.ScreenState.LoadState.LOADING -> {
      LightRestoreStatus(
        title = stringResource(R.string.RemoteRestoreActivity__restore_from_backup),
        message = stringResource(R.string.RemoteRestoreActivity__fetching_backup_details)
      )
    }

    RemoteRestoreViewModel.ScreenState.LoadState.LOADED -> {
      BackupAvailableContent(
        state = state,
        onRestoreBackupClick = onRestoreBackupClick,
        onCancelClick = onCancelClick,
        onImportErrorDialogDismiss = onImportErrorDialogDismiss,
        onUpdateSignal = onUpdateSignal,
        onContactSupport = onContactSupport
      )
    }

    RemoteRestoreViewModel.ScreenState.LoadState.NOT_FOUND -> {
      BackupNotFoundDialog(onDismiss = onCancelClick)
    }

    RemoteRestoreViewModel.ScreenState.LoadState.FAILURE -> {
      if (contactSupportState.show) {
        ContactSupportDialog(
          showInProgress = contactSupportState.showAsProgress,
          callbacks = contactSupportCallbacks
        )
      } else {
        TierRestoreFailedDialog(
          loadAttempts = state.loadAttempts,
          onRetryRestore = onRetryRestoreTier,
          onContactSupport = onContactSupport,
          onCancel = onCancelClick
        )
      }
    }

    RemoteRestoreViewModel.ScreenState.LoadState.STORAGE_SERVICE_RESTORE -> {
      LightRestoreStatus(
        title = stringResource(R.string.RemoteRestoreActivity__restore_from_backup),
        message = stringResource(R.string.RemoteRestoreActivity__restoring)
      )
    }
  }
}

@Composable
private fun BackupAvailableContent(
  state: RemoteRestoreViewModel.ScreenState,
  onRestoreBackupClick: () -> Unit,
  onCancelClick: () -> Unit,
  onImportErrorDialogDismiss: () -> Unit,
  onUpdateSignal: () -> Unit,
  onContactSupport: () -> Unit
) {
  val locale = LocalLocale.current.platformLocale
  val subtitle = if (state.backupSize.bytes > 0) {
    stringResource(
      id = R.string.RemoteRestoreActivity__backup_created_at_with_size,
      DateUtils.formatDateWithoutDayOfWeek(locale, state.backupTime),
      DateUtils.getOnlyTimeString(LocalContext.current, state.backupTime),
      state.backupSize.toUnitString()
    )
  } else {
    stringResource(
      id = R.string.RemoteRestoreActivity__backup_created_at,
      DateUtils.formatDateWithoutDayOfWeek(locale, state.backupTime),
      DateUtils.getOnlyTimeString(LocalContext.current, state.backupTime)
    )
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(LightThemeTokens.colors.background)
  ) {
    // The title lives in the top bar, as it does on every Light screen. No back glyph: leaving is
    // the bottom bar's cancel/skip, which has its own rules, and system back is unchanged.
    LightTopBar(
      center = LightTopBarCenter.Text(stringResource(R.string.RemoteRestoreActivity__restore_from_backup)),
      modifier = Modifier.statusBarsPadding()
    )

    LightScrollView(
      scrollBarPosition = LightScrollBarPosition.Inside,
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(horizontal = 2f.gridUnitsAsDp())
    ) {
      LightText(
        text = subtitle,
        variant = LightTextVariant.Detail,
        lighten = true,
        modifier = Modifier.padding(top = 1f.gridUnitsAsDp())
      )

      if (state.backupTier != null) {
        // A caption over the list, not an in-body heading: the screen's title is in the top bar.
        LightText(
          text = stringResource(R.string.RemoteRestoreActivity__your_backup_includes),
          variant = LightTextVariant.Detail,
          lighten = true,
          modifier = Modifier.padding(top = 1.5f.gridUnitsAsDp(), bottom = 0.35f.gridUnitsAsDp())
        )
        getFeatures(state.backupTier, state.backupMediaTTL).forEach { feature ->
          LightText(
            text = feature.label,
            variant = LightTextVariant.Copy,
            modifier = Modifier.padding(vertical = 0.4f.gridUnitsAsDp())
          )
        }
      }

      LightText(
        text = stringResource(R.string.RemoteRestoreActivity__your_media_will_restore_in_the_background),
        variant = LightTextVariant.Paragraph,
        modifier = Modifier.padding(top = 1.25f.gridUnitsAsDp(), bottom = 1f.gridUnitsAsDp())
      )
    }

    LightBottomBar(
      items = listOf(
        LightBarButton.Text(
          text = stringResource(
            if (state.isRemoteRestoreOnlyOption) {
              R.string.RemoteRestoreActivity__skip_restore
            } else {
              android.R.string.cancel
            }
          ).uppercase(),
          onClick = onCancelClick
        ),
        LightBarButton.Text(
          text = stringResource(R.string.RemoteRestoreActivity__restore_backup).uppercase(),
          onClick = if (state.isLoaded()) onRestoreBackupClick else null
        )
      )
    )
  }

  when (state.importState) {
    RemoteRestoreViewModel.ImportState.None -> Unit
    RemoteRestoreViewModel.ImportState.InProgress -> RestoreProgressDialog(state.restoreProgress)
    RemoteRestoreViewModel.ImportState.Restored -> Unit
    RemoteRestoreViewModel.ImportState.NetworkFailure -> RestoreNetworkFailedDialog(onDismiss = onImportErrorDialogDismiss)
    RemoteRestoreViewModel.ImportState.Failed -> {
      if (SignalStore.backup.hasInvalidBackupVersion) {
        InvalidBackupVersionDialog(onUpdateSignal = onUpdateSignal, onDismiss = onImportErrorDialogDismiss)
      } else {
        RestoreFailedDialog(onDismiss = onImportErrorDialogDismiss)
      }
    }

    RemoteRestoreViewModel.ImportState.FailureWithLogPrompt -> {
      RestoreFailedWithLogPromptDialog(
        onDismiss = onImportErrorDialogDismiss,
        onContactSupport = onContactSupport
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun RestoreFromBackupContentPreview() {
  Previews.Preview {
    RestoreFromBackupContent(
      state = RemoteRestoreViewModel.ScreenState(
        backupTier = MessageBackupTier.PAID,
        backupTime = System.currentTimeMillis(),
        backupSize = 1234567.bytes,
        importState = RemoteRestoreViewModel.ImportState.None,
        restoreProgress = null
      )
    )
  }
}

@DayNightPreviews
@Composable
private fun RestoreFromBackupUnknownTierPreview() {
  Previews.Preview {
    RestoreFromBackupContent(
      state = RemoteRestoreViewModel.ScreenState(
        loadState = RemoteRestoreViewModel.ScreenState.LoadState.LOADED,
        backupTier = null,
        backupTime = System.currentTimeMillis(),
        backupSize = 0.bytes,
        importState = RemoteRestoreViewModel.ImportState.Restored,
        restoreProgress = null
      )
    )
  }
}

@DayNightPreviews
@Composable
private fun RestoreFromBackupContentLoadingPreview() {
  Previews.Preview {
    RestoreFromBackupContent(
      state = RemoteRestoreViewModel.ScreenState(
        importState = RemoteRestoreViewModel.ImportState.None,
        restoreProgress = null
      )
    )
  }
}

@Composable
private fun getFeatures(tier: MessageBackupTier?, mediaTTL: Duration): ImmutableList<MessageBackupsTypeFeature> {
  return when (tier) {
    null -> persistentListOf()
    MessageBackupTier.PAID -> {
      persistentListOf(
        MessageBackupsTypeFeature(
          iconResourceId = R.drawable.symbol_thread_compact_bold_16,
          label = stringResource(id = R.string.RemoteRestoreActivity__all_of_your_media)
        ),
        MessageBackupsTypeFeature(
          iconResourceId = R.drawable.symbol_recent_compact_bold_16,
          label = stringResource(id = R.string.RemoteRestoreActivity__all_of_your_messages)
        )
      )
    }

    MessageBackupTier.FREE -> {
      persistentListOf(
        MessageBackupsTypeFeature(
          iconResourceId = R.drawable.symbol_thread_compact_bold_16,
          label = stringResource(id = R.string.RemoteRestoreActivity__your_last_d_days_of_media, mediaTTL.inWholeDays)
        ),
        MessageBackupsTypeFeature(
          iconResourceId = R.drawable.symbol_recent_compact_bold_16,
          label = stringResource(id = R.string.RemoteRestoreActivity__all_of_your_messages)
        )
      )
    }
  }
}

@Composable
private fun RestoreProgressDialog(restoreProgress: RestoreV2Event?) {
  BackHandler(onBack = {})

  val progressText = when (restoreProgress?.type) {
    RestoreV2Event.Type.PROGRESS_DOWNLOAD -> stringResource(R.string.RemoteRestoreActivity__downloading_backup)
    RestoreV2Event.Type.PROGRESS_RESTORE -> stringResource(R.string.RemoteRestoreActivity__restoring_messages)
    RestoreV2Event.Type.PROGRESS_FINALIZING -> stringResource(R.string.RemoteRestoreActivity__finishing_restore)
    else -> stringResource(R.string.RemoteRestoreActivity__restoring)
  }
  val progressDetail = if (restoreProgress != null && restoreProgress.type != RestoreV2Event.Type.PROGRESS_FINALIZING) {
    stringResource(
      R.string.RemoteRestoreActivity__s_of_s_s,
      restoreProgress.count.toUnitString(),
      restoreProgress.estimatedTotalCount.toUnitString(),
      "%.2f%%".format(restoreProgress.getProgress() * 100)
    )
  } else {
    null
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(LightThemeTokens.colors.background)
      .padding(horizontal = 2f.gridUnitsAsDp()),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    LightText(
      text = progressText,
      variant = LightTextVariant.Heading,
      align = TextAlign.Center
    )
    // `progressDetail` is null exactly when there is no measurable progress to show: no event yet,
    // or the finalizing pass, which the original spinner treated the same way.
    if (progressDetail != null) {
      LightProgressBar(
        colors = rememberMollyLightColors(),
        progress = restoreProgress?.getProgress() ?: 0f
      )
      LightText(
        text = progressDetail,
        variant = LightTextVariant.Detail,
        align = TextAlign.Center,
        lighten = true,
        modifier = Modifier.padding(top = 0.75f.gridUnitsAsDp())
      )
    }
  }
}

@Composable
private fun LightRestoreStatus(title: String, message: String) {
  BackHandler(onBack = {})

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(LightThemeTokens.colors.background)
      .padding(horizontal = 2f.gridUnitsAsDp()),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    LightText(
      text = title,
      variant = LightTextVariant.Heading,
      align = TextAlign.Center
    )
    LightText(
      text = message,
      variant = LightTextVariant.Paragraph,
      align = TextAlign.Center,
      modifier = Modifier.padding(top = 0.75f.gridUnitsAsDp())
    )
  }
}

@Composable
private fun LightRemoteConfirmation(
  title: String,
  message: String,
  confirmLabel: String,
  dismissLabel: String? = null,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit = {},
  dismissOnBack: Boolean = true
) {
  LightRemoteActionScreen(
    title = title,
    message = message,
    onBack = if (dismissOnBack) onDismiss else null,
    actions = buildList {
      if (dismissLabel != null) {
        // Matches Dialogs.SimpleAlertDialog's dismiss button: clear the dialog state first, then
        // run the deny action (which these call sites never supplied, so it stays a no-op).
        add(LightBarButton.Text(text = dismissLabel.uppercase(), onClick = onDismiss))
      }
      // Matches Dialogs.SimpleAlertDialog's confirm button, which ran `onDismiss()` and *then*
      // `onConfirm()`. Several call sites rely on that ordering: RestoreFailedWithLogPromptDialog
      // clears the import-error state via onDismiss while onContactSupport opens support, and
      // InvalidBackupVersionDialog clears it while onUpdateSignal leaves for the Play Store.
      // Calling only onConfirm left the failure dialog latched open behind support/Play Store.
      add(LightBarButton.Text(text = confirmLabel.uppercase(), onClick = { onDismiss(); onConfirm() }))
    }
  )
}

@Composable
private fun LightRemoteActionScreen(
  title: String,
  message: String,
  actions: List<LightBarButton>,
  onBack: (() -> Unit)? = null
) {
  BackHandler(onBack = onBack ?: {})

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(LightThemeTokens.colors.background)
  ) {
    Column(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(horizontal = 2f.gridUnitsAsDp()),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      LightText(
        text = title,
        variant = LightTextVariant.Heading,
        align = TextAlign.Center
      )
      LightText(
        text = message,
        variant = LightTextVariant.Paragraph,
        align = TextAlign.Center,
        modifier = Modifier.padding(top = 0.75f.gridUnitsAsDp())
      )
    }
    LightBottomBar(items = actions)
  }
}

@DayNightPreviews
@Composable
private fun ProgressDialogPreview() {
  Previews.Preview {
    RestoreProgressDialog(
      RestoreV2Event(
        type = RestoreV2Event.Type.PROGRESS_RESTORE,
        count = 1234.bytes,
        estimatedTotalCount = 10240.bytes
      )
    )
  }
}

@Composable
fun BackupNotFoundDialog(
  onDismiss: () -> Unit = {}
) {
  LightRemoteConfirmation(
    title = stringResource(R.string.EnterBackupKey_backup_not_found),
    message = stringResource(R.string.EnterBackupKey_backup_key_you_entered_is_correct_but_no_backup),
    confirmLabel = stringResource(android.R.string.ok),
    onConfirm = onDismiss,
    onDismiss = onDismiss
  )
}

@Composable
fun RestoreFailedDialog(
  onDismiss: () -> Unit = {}
) {
  LightRemoteConfirmation(
    title = stringResource(R.string.RemoteRestoreActivity__couldnt_transfer),
    message = stringResource(R.string.RemoteRestoreActivity__error_occurred),
    confirmLabel = stringResource(android.R.string.ok),
    onConfirm = onDismiss,
    onDismiss = onDismiss
  )
}

@Composable
fun RestoreFailedWithLogPromptDialog(
  onDismiss: () -> Unit = {},
  onContactSupport: () -> Unit = {}
) {
  LightRemoteConfirmation(
    title = stringResource(R.string.RemoteRestoreActivity__failure_with_log_prompt_title),
    message = stringResource(R.string.RemoteRestoreActivity__failure_with_log_prompt_body),
    confirmLabel = stringResource(R.string.RemoteRestoreActivity__failure_with_log_prompt_contact_button),
    dismissLabel = stringResource(android.R.string.ok),
    onConfirm = onContactSupport,
    onDismiss = onDismiss
  )
}

@Composable
fun RestoreNetworkFailedDialog(
  onDismiss: () -> Unit = {}
) {
  LightRemoteConfirmation(
    title = stringResource(R.string.RemoteRestoreActivity__couldnt_transfer),
    message = stringResource(R.string.RegistrationActivity_error_connecting_to_service),
    confirmLabel = stringResource(android.R.string.ok),
    onConfirm = onDismiss,
    onDismiss = onDismiss
  )
}

@Composable
fun TierRestoreFailedDialog(
  loadAttempts: Int = 0,
  onRetryRestore: () -> Unit = {},
  onContactSupport: () -> Unit = {},
  onCancel: () -> Unit = {}
) {
  LightRemoteActionScreen(
    title = stringResource(R.string.EnterBackupKey_cant_restore_backup),
    message = stringResource(R.string.EnterBackupKey_your_backup_cant_be_restored_right_now),
    actions = buildList {
      add(
        LightBarButton.Text(
          text = stringResource(android.R.string.cancel).uppercase(),
          onClick = onCancel
        )
      )
      if (loadAttempts > 2) {
        add(
          LightBarButton.Text(
            text = stringResource(R.string.EnterBackupKey_contact_support).uppercase(),
            onClick = onContactSupport
          )
        )
      }
      add(
        LightBarButton.Text(
          text = stringResource(R.string.EnterBackupKey_try_again).uppercase(),
          onClick = onRetryRestore
        )
      )
    }
  )
}

@DayNightPreviews
@Composable
private fun RestoreFailedDialogPreview() {
  Previews.Preview {
    RestoreFailedDialog()
  }
}

@DayNightPreviews
@Composable
private fun RestoreFailedWithLogPromptDialogPreview() {
  Previews.Preview {
    RestoreFailedWithLogPromptDialog()
  }
}

@Composable
fun InvalidBackupVersionDialog(
  onUpdateSignal: () -> Unit = {},
  onDismiss: () -> Unit = {}
) {
  LightRemoteConfirmation(
    title = stringResource(R.string.RemoteRestoreActivity__couldnt_restore),
    message = stringResource(R.string.RemoteRestoreActivity__update_latest),
    confirmLabel = stringResource(R.string.RemoteRestoreActivity__update_signal),
    dismissLabel = stringResource(R.string.RemoteRestoreActivity__not_now),
    onConfirm = onUpdateSignal,
    onDismiss = onDismiss
  )
}

@DayNightPreviews
@Composable
private fun InvalidBackupVersionDialogPreview() {
  Previews.Preview {
    InvalidBackupVersionDialog()
  }
}
