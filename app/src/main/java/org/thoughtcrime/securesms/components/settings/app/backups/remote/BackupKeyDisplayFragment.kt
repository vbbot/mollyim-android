/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.backups.remote

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.LightConfirmationDialog
import org.thoughtcrime.securesms.light.LightSettingsProgressRow
import org.thoughtcrime.securesms.light.LightSettingsScreen
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsKeyRecordMode
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsKeyRecordScreen
import org.thoughtcrime.securesms.backup.v2.ui.subscription.MessageBackupsKeyVerifyScreen
import org.thoughtcrime.securesms.compose.Nav
import org.thoughtcrime.securesms.util.viewModel

/**
 * Fragment which only displays the backup key to the user.
 */
class BackupKeyDisplayFragment : ComposeFragment() {

  companion object {
    const val AEP_ROTATION_KEY = "AEP_ROTATION_KEY"
  }

  private val viewModel: BackupKeyDisplayViewModel by viewModel { BackupKeyDisplayViewModel() }
  private val args: BackupKeyDisplayFragmentArgs by navArgs()

  @Composable
  override fun FragmentContent() {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    LaunchedEffect(Unit) {
      navController.setLifecycleOwner(this@BackupKeyDisplayFragment)
      navController.enableOnBackPressed(true)
    }

    LaunchedEffect(args.startWithKeyRotation, state.rotationState) {
      if (args.startWithKeyRotation && state.rotationState == BackupKeyRotationState.NOT_STARTED) {
        viewModel.rotateBackupKey()
      }
    }

    LaunchedEffect(state.rotationState) {
      if (state.rotationState == BackupKeyRotationState.FINISHED) {
        setFragmentResult(AEP_ROTATION_KEY, bundleOf(AEP_ROTATION_KEY to true))
        findNavController().popBackStack()
      }
    }

    val onBackPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    var displayWarningDialog by remember { mutableStateOf(false) }
    BackHandler(enabled = state.rotationState == BackupKeyRotationState.USER_VERIFICATION) {
      displayWarningDialog = true
    }

    val mode = remember(state.rotationState, state.canRotateKey) {
      if (state.rotationState == BackupKeyRotationState.NOT_STARTED) {
        MessageBackupsKeyRecordMode.CreateNewKey(
          onCreateNewKeyClick = {
            viewModel.rotateBackupKey()
          },
          onTurnOffAndDownloadClick = {
            viewModel.turnOffOptimizedStorageAndDownloadMedia()
            findNavController().popBackStack()
          },
          isOptimizedStorageEnabled = state.isOptimizedStorageEnabled,
          canRotateKey = state.canRotateKey
        )
      } else {
        MessageBackupsKeyRecordMode.Next(
          onNextClick = {
            navController.navigate(Screen.Verify.route)
          }
        )
      }
    }

    if (state.rotationState == BackupKeyRotationState.GENERATING_KEY || state.rotationState == BackupKeyRotationState.COMMITTING_KEY) {
      BackupKeyProgressDialog()
    }

    if (displayWarningDialog) {
      BackupKeyNotCommitedWarningDialog(
        onConfirm = {
          findNavController().popBackStack()
        },
        onCancel = {
          displayWarningDialog = false
          navController.navigate(Screen.Verify.route)
        }
      )
    }

    Nav.Host(
      navController = navController,
      startDestination = Screen.Record.route
    ) {
      composable(Screen.Record.route) {
        MessageBackupsKeyRecordScreen(
          backupKey = state.accountEntropyPool.displayValue,
          keySaveState = state.keySaveState,
          backupKeyCredentialManagerHandler = viewModel,
          mode = mode
        )
      }

      composable(Screen.Verify.route) {
        MessageBackupsKeyVerifyScreen(
          backupKey = state.accountEntropyPool.displayValue,
          onNavigationClick = { onBackPressedDispatcher?.onBackPressed() },
          onNextClick = { viewModel.commitBackupKey() }
        )
      }
    }
  }
}

@Composable
private fun BackupKeyNotCommitedWarningDialog(
  onConfirm: () -> Unit,
  onCancel: () -> Unit
) {
  var abandoningKey by remember { mutableStateOf(false) }
  LightConfirmationDialog(
    title = stringResource(R.string.BackupKeyDisplayFragment__cancel_key_creation_question),
    body = stringResource(R.string.BackupKeyDisplayFragment__your_new_backup_key),
    confirmLabel = stringResource(R.string.BackupKeyDisplayFragment__cancel_key_creation),
    dismissLabel = stringResource(R.string.BackupKeyDisplayFragment__confirm_key),
    onConfirm = {
      abandoningKey = true
      onConfirm()
    },
    onDismiss = {
      if (!abandoningKey) onCancel()
    },
    destructive = true
  )
}

@Composable
private fun BackupKeyProgressDialog() {
  val title = stringResource(R.string.BackupCreationProgressRow__processing_backup)
  Dialog(
    onDismissRequest = {},
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    MollyLightTheme {
      LightSettingsScreen(title = title, onBack = {}) {
        LightSettingsProgressRow(title = title)
      }
    }
  }
}

private enum class Screen(val route: String) {
  Record("record-screen"),
  Verify("verify-screen")
}
