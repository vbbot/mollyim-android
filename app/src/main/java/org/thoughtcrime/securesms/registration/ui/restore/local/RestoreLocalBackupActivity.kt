/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore.local

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.thelightphone.sdk.ui.LightThemeTokens
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.BaseActivity
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.contactsupport.ContactSupportCallbacks
import org.thoughtcrime.securesms.components.contactsupport.ContactSupportDialog
import org.thoughtcrime.securesms.components.contactsupport.ContactSupportViewModel
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.registration.ui.restore.RemoteRestoreWakeLock
import org.thoughtcrime.securesms.restore.RestoreActivity

/**
 * Handles the synchronous restoration of the proto files from a V2 backup. Media is
 * handled by background tasks.
 */
@SuppressLint("BaseActivitySubclass")
class RestoreLocalBackupActivity : BaseActivity() {
  companion object {
    private const val KEY_FINISH = "finish"

    fun getIntent(context: Context, finish: Boolean = true): Intent {
      return Intent(context, RestoreLocalBackupActivity::class.java).apply {
        putExtra(KEY_FINISH, finish)
      }
    }
  }

  private val viewModel: RestoreLocalBackupActivityViewModel by viewModels()
  private val contactSupportViewModel: ContactSupportViewModel<Unit> by viewModels()

  private val finishActivity by lazy {
    intent.getBooleanExtra(KEY_FINISH, false)
  }

  private lateinit var wakeLock: RemoteRestoreWakeLock

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    wakeLock = RemoteRestoreWakeLock(this, "localRestore")

    lifecycleScope.launch {
      lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        viewModel.state
          .map { it.restorePhase }
          .collect { phase ->
            when (phase) {
              RestorePhase.RESTORING, RestorePhase.FINALIZING -> {
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

    setContent {
      val state by viewModel.state.collectAsStateWithLifecycle()

      LaunchedEffect(state.restorePhase) {
        when (state.restorePhase) {
          RestorePhase.COMPLETE -> {
            if (state.dialog == null) {
              startActivity(MainActivity.clearTop(this@RestoreLocalBackupActivity))
              if (finishActivity) {
                finishAffinity()
              }
            }
          }

          RestorePhase.FAILED -> {
            Toast.makeText(this@RestoreLocalBackupActivity, getString(R.string.RestoreLocalBackupActivity__backup_restore_failed), Toast.LENGTH_LONG).show()
          }

          else -> Unit
        }
      }

      val contactSupportState by contactSupportViewModel.state.collectAsStateWithLifecycle()
      val context = LocalContext.current

      MollyLightTheme {
        RestoreLocalBackupScreen(
          state = state,
          onContactSupportClick = contactSupportViewModel::showContactSupport,
          onConfirmBackupLocation = {
            viewModel.enableLocalBackupsAndDismissDialog()
            startActivity(MainActivity.clearTop(this@RestoreLocalBackupActivity))
            if (finishActivity) {
              finishAffinity()
            }
          },
          onDisableBackups = {
            viewModel.changeBackupLocation()
            startActivity(MainActivity.clearTop(this@RestoreLocalBackupActivity))
            if (finishActivity) {
              finishAffinity()
            }
          },
          onChangeBackupLocation = {
            viewModel.changeBackupLocation()
            startActivities(
              arrayOf(
                MainActivity.clearTop(this@RestoreLocalBackupActivity),
                AppSettingsActivity.backups(this@RestoreLocalBackupActivity)
              )
            )
            if (finishActivity) {
              finishAffinity()
            }
          },
          onLocalBackupsDisabledDialogConfirm = {
            viewModel.dismissDialog()
            startActivities(
              arrayOf(
                MainActivity.clearTop(this@RestoreLocalBackupActivity),
                AppSettingsActivity.backups(this@RestoreLocalBackupActivity)
              )
            )
            if (finishActivity) {
              finishAffinity()
            }
          },
          onLocalBackupsDisabledDialogDeny = {
            viewModel.dismissDialog()
            startActivity(MainActivity.clearTop(this@RestoreLocalBackupActivity))
            if (finishActivity) {
              finishAffinity()
            }
          },
          onFailureDialogConfirm = {
            if (finishActivity) {
              viewModel.resetRestoreState()
              startActivity(RestoreActivity.getRestoreIntent(context))
            }

            // User invocation here should always finish, it just shouldn't route back to RestoreActivity.
            supportFinishAfterTransition()
          },
          contactSupportState = contactSupportState,
          contactSupportCallbacks = contactSupportViewModel
        )
      }
    }
  }
}

/**
 * The restore's progress, completion and failure, in Light. Was upstream Material end to end -- a
 * 28sp headline, a 60dp M3 spinner and `Dialogs.*` alerts -- and now follows `RemoteRestoreActivity`:
 * the title in the top bar, the explanatory line as Paragraph, a centred Heading status line with a
 * [com.thelightphone.sdk.ui.LightProgressBar] and a Detail line once there is measurable progress.
 *
 * The three alerts are full-screen [LightRestoreNotice]s over it, and every exit runs the callback
 * the dialog's button ran:
 *
 * - Failure: CONTACT SUPPORT -> [onContactSupportClick]; OK -> [onFailureDialogConfirm]. Its dismiss
 *   request did nothing, so back is still swallowed. Closing contact support still confirms.
 * - Choose a folder: USE CURRENT FOLDER -> [onConfirmBackupLocation]; CHOOSE NEW FOLDER ->
 *   [onChangeBackupLocation]; DISABLE BACKUPS -> [onDisableBackups]. The Material dialog's dismiss
 *   request was its negative button, so system back still disables backups; there is deliberately
 *   no BACK glyph, since a glyph that reads "back" should not quietly decide something.
 * - Backups disabled: NOT NOW -> [onLocalBackupsDisabledDialogDeny]; TURN ON NOW ->
 *   [onLocalBackupsDisabledDialogConfirm]. Its dismiss request did nothing, so back is swallowed.
 *
 * During progress there is no back handler at all, exactly as before.
 */
@VisibleForTesting
@Composable
internal fun RestoreLocalBackupScreen(
  state: RestoreLocalBackupScreenState,
  onFailureDialogConfirm: () -> Unit,
  onConfirmBackupLocation: () -> Unit,
  onChangeBackupLocation: () -> Unit,
  onDisableBackups: () -> Unit,
  onLocalBackupsDisabledDialogConfirm: () -> Unit,
  onLocalBackupsDisabledDialogDeny: () -> Unit,
  onContactSupportClick: () -> Unit,
  contactSupportState: ContactSupportViewModel.ContactSupportState<Unit>,
  contactSupportCallbacks: ContactSupportCallbacks
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(LightThemeTokens.colors.background)
  ) {
    val statusText = when (state.restorePhase) {
      RestorePhase.RESTORING -> stringResource(R.string.RestoreLocalBackupActivity__restoring_messages)
      RestorePhase.FINALIZING -> stringResource(R.string.RestoreLocalBackupActivity__finalizing)
      RestorePhase.COMPLETE -> stringResource(R.string.RestoreLocalBackupActivity__restore_complete)
      RestorePhase.FAILED -> stringResource(R.string.RestoreLocalBackupActivity__restore_failed)
    }

    val progressDetail = if (state.restorePhase == RestorePhase.RESTORING && state.totalBytes.inWholeBytes > 0) {
      val progressPercent = (state.progress * 100).toInt()
      stringResource(R.string.RestoreLocalBackupActivity__s_of_s_d_percent, state.bytesRead.toUnitString(), state.totalBytes.toUnitString(), progressPercent)
    } else {
      null
    }

    LightRestoreProgress(
      title = stringResource(R.string.RestoreLocalBackupActivity__restoring_backup),
      prose = stringResource(R.string.RestoreLocalBackupActivity__depending_on_the_size),
      status = statusText,
      // The determinate spinner appeared exactly when progress was above zero; the bar does the same.
      progress = state.progress.takeIf { it > 0f },
      detail = progressDetail
    )

    if (state.restorePhase == RestorePhase.FAILED) {
      var wasContactSupportShown by remember { mutableStateOf(false) }
      LaunchedEffect(contactSupportState.show) {
        if (wasContactSupportShown && !contactSupportState.show) {
          onFailureDialogConfirm()
        }

        wasContactSupportShown = contactSupportState.show
      }

      if (!contactSupportState.show) {
        LightRestoreNotice(
          title = stringResource(R.string.RestoreLocalBackupActivity__cant_restore_backup),
          message = stringResource(R.string.RestoreLocalBackupActivity__error_occurred_while_restoring),
          actions = listOf(
            LightRestoreNoticeAction(stringResource(R.string.RestoreLocalBackupActivity__contact_support), onContactSupportClick),
            LightRestoreNoticeAction(stringResource(android.R.string.ok), onFailureDialogConfirm)
          ),
          onBack = null
        )
      } else {
        ContactSupportDialog(
          showInProgress = contactSupportState.showAsProgress,
          callbacks = contactSupportCallbacks
        )
      }
    }

    when (state.dialog) {
      RestoreLocalBackupActivityDialog.CONFIRM_BACKUP_LOCATION -> {
        LightRestoreNotice(
          title = stringResource(R.string.RestoreLocalBackupActivity__restore_complete),
          message = stringResource(R.string.RestoreLocalBackupActivity__choose_a_folder_for_backups),
          actions = listOf(
            LightRestoreNoticeAction(stringResource(R.string.RestoreLocalBackupActivity__use_current_folder), onConfirmBackupLocation),
            LightRestoreNoticeAction(stringResource(R.string.RestoreLocalBackupActivity__choose_new_folder), onChangeBackupLocation),
            LightRestoreNoticeAction(stringResource(R.string.RestoreLocalBackupActivity__disable_backups), onDisableBackups)
          ),
          onBack = onDisableBackups
        )
      }

      RestoreLocalBackupActivityDialog.LOCAL_BACKUPS_DISABLED -> {
        LightRestoreNotice(
          title = stringResource(R.string.RestoreLocalBackupActivity__turn_on_on_device_backups),
          message = stringResource(R.string.RestoreLocalBackupActivity__to_continue_using_on_device_backups),
          actions = listOf(
            LightRestoreNoticeAction(stringResource(R.string.RestoreLocalBackupActivity__not_now), onLocalBackupsDisabledDialogDeny),
            LightRestoreNoticeAction(stringResource(R.string.RestoreLocalBackupActivity__turn_on_now), onLocalBackupsDisabledDialogConfirm)
          ),
          onBack = null
        )
      }

      null -> Unit
    }
  }
}

@DayNightPreviews
@Composable
private fun RestoreLocalBackupScreenPreview() {
  Previews.Preview {
    RestoreLocalBackupScreen(
      state = RestoreLocalBackupScreenState(),
      onFailureDialogConfirm = {},
      onConfirmBackupLocation = {},
      onChangeBackupLocation = {},
      onDisableBackups = {},
      onLocalBackupsDisabledDialogConfirm = {},
      onLocalBackupsDisabledDialogDeny = {},
      onContactSupportClick = {},
      contactSupportState = ContactSupportViewModel.ContactSupportState(),
      contactSupportCallbacks = ContactSupportCallbacks.Empty
    )
  }
}
