/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.contactsupport.ContactSupportDialog
import org.thoughtcrime.securesms.components.contactsupport.ContactSupportViewModel
import org.thoughtcrime.securesms.components.contactsupport.SendSupportEmailEffect
import org.thoughtcrime.securesms.registration.ui.RegistrationCheckpoint
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import org.thoughtcrime.securesms.registration.ui.phonenumber.EnterPhoneNumberMode
import org.thoughtcrime.securesms.registration.ui.phonenumber.PhoneNumberNoticeAction
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationNoticeDialog
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Enter backup key screen for manual Signal Backups restore flow.
 */
class EnterBackupKeyFragment : ComposeFragment() {

  companion object {
    private const val LEARN_MORE_URL = "https://support.signal.org/hc/articles/360007059752"
  }

  private val sharedViewModel by activityViewModels<RegistrationViewModel>()
  private val viewModel by viewModels<EnterBackupKeyViewModel>()
  private val contactSupportViewModel: ContactSupportViewModel<Unit> by viewModels()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        sharedViewModel
          .state
          .map { it.registerAccountError }
          .filterNotNull()
          .collect {
            sharedViewModel.registerAccountErrorShown()
            viewModel.handleRegistrationFailure(it)
          }
      }
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        sharedViewModel
          .state
          .filter { it.registrationCheckpoint == RegistrationCheckpoint.BACKUP_TIMESTAMP_NOT_RESTORED }
          .collect {
            viewModel.handleBackupTimestampNotRestored()
          }
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sharedState by sharedViewModel.state.collectAsStateWithLifecycle()
    val contactSupportState: ContactSupportViewModel.ContactSupportState<Unit> by contactSupportViewModel.state.collectAsStateWithLifecycle()
    var showSkipRestoreWarning by remember { mutableStateOf(false) }

    SendSupportEmailEffect(
      contactSupportState = contactSupportState,
      subjectRes = { R.string.EnterBackupKey_network_failure_support_email },
      filterRes = { R.string.EnterBackupKey_network_failure_support_email_filter }
    ) {
      contactSupportViewModel.hideContactSupport()
    }

    EnterBackupKeyScreen(
      isDisplayedDuringManualRestore = true,
      enteredText = viewModel.enteredText,
      inProgress = sharedState.inProgress,
      isBackupKeyValid = state.backupKeyValid,
      chunkLength = state.chunkLength,
      aepValidationError = state.aepValidationError,
      onBackupKeyChanged = viewModel::updateBackupKey,
      onNextClicked = {
        viewModel.registering()
        sharedViewModel.registerWithBackupKey(
          context = requireContext(),
          backupKey = viewModel.backupKey,
          e164 = null,
          pin = null,
          aciIdentityKeyPair = null,
          pniIdentityKeyPair = null
        )
      },
      onLearnMore = { CommunicationActions.openBrowserLink(requireContext(), LEARN_MORE_URL) },
      onSkip = {
        showSkipRestoreWarning = true
      },
      dialogContent = {
        if (showSkipRestoreWarning) {
          SkipRestoreWarning(
            onSkip = {
              sharedViewModel.skipRestore()
              findNavController().safeNavigate(EnterBackupKeyFragmentDirections.goToEnterPhoneNumber(EnterPhoneNumberMode.RESTART_AFTER_COLLECTION))
            },
            onDismiss = { showSkipRestoreWarning = false },
            backDescription = stringResource(R.string.EnterBackupKey__back_to_recovery_key)
          )
        }
        if (contactSupportState.show) {
          ContactSupportDialog(
            showInProgress = contactSupportState.showAsProgress,
            callbacks = contactSupportViewModel
          )
        } else {
          ErrorContent(
            state = state,
            onBackupTierRetry = {
              viewModel.incrementBackupTierRetry()
              sharedViewModel.checkForBackupFile()
            },
            onAbandonRemoteRestoreAfterRegistration = {
              viewLifecycleOwner.lifecycleScope.launch {
                sharedViewModel.resetRestoreDecision()
                sharedViewModel.resumeNormalRegistration()
              }
            },
            onBackupTierNotRestoredDismiss = viewModel::hideRestoreBackupKeyFailed,
            onContactSupport = { contactSupportViewModel.showContactSupport() },
            onRegistrationErrorDismiss = viewModel::clearRegistrationError,
            onBackupKeyHelp = { CommunicationActions.openBrowserLink(requireContext(), LEARN_MORE_URL) }
          )
        }
      }
    )
  }
}

/**
 * The tier-restore and registration errors, as full-screen Light notices.
 *
 * Every action runs what its Material button ran, in the same order: the Simple dialogs' buttons
 * cleared the error before acting, and the three-option dialog's buttons were each given that
 * clearing explicitly. The three-option dialog was cancelable and its cancel was "Cancel"; the two
 * Simple dialogs were not, and a notice cannot refuse BACK, so BACK takes their dismiss-side exit --
 * "Cancel" or "Skip restore", which both leave remote restore -- making BACK mean the same thing on
 * all three.
 */
@Composable
private fun ErrorContent(
  state: EnterBackupKeyViewModel.EnterBackupKeyState,
  onBackupTierRetry: () -> Unit = {},
  onAbandonRemoteRestoreAfterRegistration: () -> Unit = {},
  onBackupTierNotRestoredDismiss: () -> Unit = {},
  onContactSupport: () -> Unit = {},
  onRegistrationErrorDismiss: () -> Unit = {},
  onBackupKeyHelp: () -> Unit = {}
) {
  val backDescription = stringResource(R.string.EnterBackupKey__back_to_recovery_key)

  if (state.showBackupTierNotRestoreError == EnterBackupKeyViewModel.TierRestoreError.NETWORK_ERROR) {
    val cancel = {
      onBackupTierNotRestoredDismiss()
      onAbandonRemoteRestoreAfterRegistration()
    }

    if (state.tierRetryAttempts > 1) {
      RegistrationNoticeDialog(
        title = stringResource(R.string.EnterBackupKey_cant_restore_backup),
        message = stringResource(R.string.EnterBackupKey_your_backup_cant_be_restored_right_now),
        actions = listOf(
          PhoneNumberNoticeAction(stringResource(R.string.EnterBackupKey_try_again)) {
            onBackupTierNotRestoredDismiss()
            onBackupTierRetry()
          },
          PhoneNumberNoticeAction(stringResource(R.string.EnterBackupKey_contact_support)) {
            onBackupTierNotRestoredDismiss()
            onContactSupport()
          },
          PhoneNumberNoticeAction(stringResource(android.R.string.cancel), cancel)
        ),
        onBack = cancel,
        backDescription = backDescription
      )
    } else {
      RegistrationNoticeDialog(
        title = stringResource(R.string.EnterBackupKey_cant_restore_backup),
        message = stringResource(R.string.EnterBackupKey_your_backup_cant_be_restored_right_now),
        actions = listOf(
          PhoneNumberNoticeAction(stringResource(android.R.string.cancel), cancel),
          PhoneNumberNoticeAction(stringResource(R.string.EnterBackupKey_try_again)) {
            onBackupTierNotRestoredDismiss()
            onBackupTierRetry()
          }
        ),
        onBack = cancel,
        backDescription = backDescription
      )
    }
  } else if (state.showBackupTierNotRestoreError == EnterBackupKeyViewModel.TierRestoreError.NOT_FOUND) {
    val skipRestore = {
      onBackupTierNotRestoredDismiss()
      onAbandonRemoteRestoreAfterRegistration()
    }

    RegistrationNoticeDialog(
      title = stringResource(R.string.EnterBackupKey_backup_not_found),
      message = stringResource(R.string.EnterBackupKey_backup_key_you_entered_is_correct_but_no_backup),
      actions = listOf(
        PhoneNumberNoticeAction(stringResource(R.string.EnterBackupKey_skip_restore), skipRestore),
        PhoneNumberNoticeAction(stringResource(R.string.EnterBackupKey_try_again)) {
          onBackupTierNotRestoredDismiss()
          onBackupTierRetry()
        }
      ),
      onBack = skipRestore,
      backDescription = backDescription
    )
  } else {
    RegistrationErrorDialogs(
      showRegistrationError = state.showRegistrationError,
      registerAccountResult = state.registerAccountResult,
      onRegistrationErrorDismiss = onRegistrationErrorDismiss,
      onBackupKeyHelp = onBackupKeyHelp
    )
  }
}
