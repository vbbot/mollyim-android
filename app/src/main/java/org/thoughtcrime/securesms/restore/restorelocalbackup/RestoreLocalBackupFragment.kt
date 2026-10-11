/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore.restorelocalbackup

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.ui.logging.LoggingFragment
import org.signal.core.util.bytes
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.backup.BackupEvent
import org.thoughtcrime.securesms.backup.BackupPassphrase
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.restore.RestoreActivity
import org.thoughtcrime.securesms.restore.RestoreRepository
import org.thoughtcrime.securesms.restore.RestoreViewModel
import org.thoughtcrime.securesms.service.LocalBackupListener
import org.thoughtcrime.securesms.util.BackupUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.ViewModelFactory
import java.util.Locale

/**
 * This fragment is used to monitor and manage an in-progress backup restore.
 *
 * Drawn in Light by [RestoreLocalBackupV1Screen]; the fragment keeps every decision and hands the
 * screen a [RestoreLocalBackupV1UiState]. The passphrase prompt and the backup-folder question,
 * which were `MaterialAlertDialog`s, are now states of that screen.
 */
class RestoreLocalBackupFragment : LoggingFragment() {
  private val sharedViewModel: RestoreViewModel by activityViewModels()
  private val restoreLocalBackupViewModel: RestoreLocalBackupViewModel by viewModels(
    factoryProducer = ViewModelFactory.factoryProducer {
      val fileBackupUri = sharedViewModel.getBackupFileUri()!!
      RestoreLocalBackupViewModel(fileBackupUri)
    }
  )

  private val selectBackupDirectory = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    when (val resultCode = result.resultCode) {
      Activity.RESULT_OK -> {
        val backupDirectoryUri: Uri? = result.data?.data

        if (backupDirectoryUri != null) {
          Log.i(TAG, "Re-enabling backups with new directory")
          val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

          SignalStore.settings.setSignalBackupDirectory(backupDirectoryUri)
          requireContext().contentResolver.takePersistableUriPermission(backupDirectoryUri, takeFlags)

          enableLocalBackups(requireContext())
          resumeRegistrationAfterLocalBackupRestore()
        } else {
          Log.w(TAG, "Backup directory URI is null, reshowing dialog to re-enable")
          onLocalBackupRestoreCompletedSuccessfully()
        }
      }
      Activity.RESULT_CANCELED -> {
        Log.w(TAG, "Backup directory selection canceled, reshowing dialog to re-enable")
        onLocalBackupRestoreCompletedSuccessfully()
      }
      else -> Log.w(TAG, "Backup directory selection activity ended with unknown result code: $resultCode")
    }
  }

  private var uiState by mutableStateOf(RestoreLocalBackupV1UiState())

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        RestoreLocalBackupV1Screen(
          state = uiState,
          onRestoreClick = { presentBackupPassPhrasePromptDialog() },
          onCancelClick = {
            Log.i(TAG, "Cancel clicked.")
            findNavController().navigateUp()
          },
          onPassphraseSubmitted = { passphrase ->
            uiState = uiState.copy(enteringPassphrase = false)
            restoreLocalBackupViewModel.confirmPassphraseAndBeginRestore(requireContext(), passphrase)
          },
          onPassphraseDismissed = { uiState = uiState.copy(enteringPassphrase = false) },
          onNotNow = {
            uiState = uiState.copy(confirmingBackupFolder = false)
            BackupPassphrase.set(requireContext(), null)
            resumeRegistrationAfterLocalBackupRestore()
          },
          onChooseFolder = {
            uiState = uiState.copy(confirmingBackupFolder = false)
            chooseBackupFolder(requireContext())
          }
        )
      }
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    Log.i(TAG, "Backup restore.")

    if (sharedViewModel.getBackupFileUri() == null) {
      Log.i(TAG, "No backup URI found, must navigate back to choose one.")
      findNavController().navigate(RestoreLocalBackupFragmentDirections.goDirectlyToChooseLocalBackup())
      return
    }

    if (SignalStore.settings.isBackupEnabled) {
      Log.i(TAG, "Backups enabled, so a backup must have been previously restored.")
      onLocalBackupRestoreCompletedSuccessfully()
      return
    }

    restoreLocalBackupViewModel.backupReadError.observe(viewLifecycleOwner) { fileState ->
      fileState?.let {
        restoreLocalBackupViewModel.clearBackupFileStateError()
        handleBackupFileStateError(it)
      }
    }

    restoreLocalBackupViewModel.uiState.observe(viewLifecycleOwner) { fragmentState ->
      fragmentState.backupInfo?.let {
        presentBackupFileInfo(backupSize = it.size, backupTimestamp = it.timestamp)
      }

      if (fragmentState.restoreInProgress) {
        presentRestoreProgress(fragmentState.backupProgressCount)
      } else {
        presentProgressEnded()
      }
    }

    restoreLocalBackupViewModel.importResult.observe(viewLifecycleOwner) { importResult ->
      when (importResult) {
        null -> Unit
        RestoreRepository.BackupImportResult.SUCCESS -> onLocalBackupRestoreCompletedSuccessfully()
        else -> {
          handleBackupImportError(importResult)
          restoreLocalBackupViewModel.backupImportErrorShown()
        }
      }
    }

    restoreLocalBackupViewModel.prepareRestore(requireContext())
  }

  private fun onLocalBackupRestoreCompletedSuccessfully() {
    Log.d(TAG, "onBackupCompletedSuccessfully()")
    if (BackupUtil.isUserSelectionRequired(requireContext()) && !BackupUtil.canUserAccessBackupDirectory(requireContext())) {
      displayConfirmationDialog(requireContext())
    } else {
      enableLocalBackups(requireContext())
      resumeRegistrationAfterLocalBackupRestore()
    }
  }

  private fun resumeRegistrationAfterLocalBackupRestore() {
    val activity = requireActivity() as RestoreActivity
    activity.onBackupCompletedSuccessfully()
  }

  @RequiresApi(29)
  private fun displayConfirmationDialog(context: Context) {
    Log.d(TAG, "Showing continue using local backups dialog")
    uiState = uiState.copy(confirmingBackupFolder = true)
  }

  private fun chooseBackupFolder(context: Context) {
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
    intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
      Log.d(TAG, "Showing external file picker for new backup directory")
      selectBackupDirectory.launch(intent)
    } catch (_: ActivityNotFoundException) {
      Log.w(TAG, "No external activity found")
      Toast.makeText(context, R.string.BackupDialog_no_file_picker_available, Toast.LENGTH_LONG).show()
      BackupPassphrase.set(context, null)
      resumeRegistrationAfterLocalBackupRestore()
    }
  }

  private fun enableLocalBackups(context: Context) {
    if (BackupUtil.canUserAccessBackupDirectory(context)) {
      LocalBackupListener.setNextBackupTimeToIntervalFromNow(context)
      SignalStore.settings.isBackupEnabled = true
      LocalBackupListener.schedule(context)
    }
  }

  override fun onStart() {
    super.onStart()
    EventBus.getDefault().register(this)
  }

  override fun onStop() {
    super.onStop()
    EventBus.getDefault().unregister(this)
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onEvent(event: BackupEvent) {
    restoreLocalBackupViewModel.onBackupProgressUpdate(event)
  }

  private fun handleBackupFileStateError(fileState: BackupUtil.BackupFileState) {
    @StringRes
    val errorResId: Int = when (fileState) {
      BackupUtil.BackupFileState.READABLE -> throw AssertionError("Unexpected error state.")
      BackupUtil.BackupFileState.NOT_FOUND -> R.string.RestoreBackupFragment__backup_not_found
      BackupUtil.BackupFileState.NOT_READABLE -> R.string.RestoreBackupFragment__backup_has_a_bad_extension
      BackupUtil.BackupFileState.UNSUPPORTED_FILE_EXTENSION -> R.string.RestoreBackupFragment__backup_could_not_be_read
    }

    Toast.makeText(requireContext(), errorResId, Toast.LENGTH_LONG).show()
  }

  private fun handleBackupImportError(importResult: RestoreRepository.BackupImportResult) {
    when (importResult) {
      RestoreRepository.BackupImportResult.FAILURE_VERSION_DOWNGRADE -> {
        Log.i(TAG, "Notifying user of restore failure due to version downgrade.")
        Toast.makeText(requireContext(), R.string.RegistrationActivity_backup_failure_downgrade, Toast.LENGTH_LONG).show()
      }

      RestoreRepository.BackupImportResult.FAILURE_FOREIGN_KEY -> {
        Log.i(TAG, "Notifying user of restore failure due to foreign key.")
        Toast.makeText(requireContext(), R.string.RegistrationActivity_backup_failure_foreign_key, Toast.LENGTH_LONG).show()
      }

      RestoreRepository.BackupImportResult.FAILURE_UNKNOWN -> {
        Log.i(TAG, "Notifying user of restore failure due to incorrect passphrase.")
        Toast.makeText(requireContext(), R.string.RegistrationActivity_incorrect_backup_passphrase, Toast.LENGTH_LONG).show()
      }

      RestoreRepository.BackupImportResult.SUCCESS -> {
        Log.w(TAG, "Successful backup import should not be handled in this function.", IllegalStateException())
      }
    }
  }

  private fun presentProgressEnded() {
    uiState = uiState.copy(restoreInProgress = false, progressText = null)
  }

  private fun presentRestoreProgress(backupProgressCount: Long) {
    val progressText = if (backupProgressCount > 0L) {
      getString(R.string.RegistrationActivity_d_messages_so_far, backupProgressCount)
    } else {
      getString(R.string.RegistrationActivity_checking)
    }
    uiState = uiState.copy(restoreInProgress = true, progressText = progressText)
  }

  private fun presentBackupPassPhrasePromptDialog() {
    uiState = uiState.copy(enteringPassphrase = true)
    Log.i(TAG, "Prompt for backup passphrase shown to user.")
  }

  private fun presentBackupFileInfo(backupSize: Long, backupTimestamp: Long) {
    if (backupSize > 0) {
      uiState = uiState.copy(backupSize = getString(R.string.RegistrationActivity_backup_size_s, backupSize.bytes.toUnitString()))
    }

    if (backupTimestamp > 0) {
      val (date, dateContentDesc) = DateUtils.getExtendedRelativeTimeSpanString(requireContext(), Locale.getDefault(), backupTimestamp)
      uiState = uiState.copy(
        backupCreated = getString(R.string.RegistrationActivity_backup_timestamp_s, date),
        backupCreatedDescription = getString(R.string.RegistrationActivity_backup_timestamp_s, dateContentDesc)
      )
    }
  }

  companion object {
    private val TAG = Log.tag(RestoreLocalBackupFragment::class.java)
  }
}
