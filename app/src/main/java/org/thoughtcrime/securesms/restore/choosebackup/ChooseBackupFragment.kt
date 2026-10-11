/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.restore.choosebackup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.NavHostFragment
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.logging.LoggingFragment
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationAction
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationBack
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationBottomActions
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen
import org.thoughtcrime.securesms.restore.RestoreViewModel
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * This fragment presents a button to the user to browse their local file system for a legacy backup file.
 *
 * Drawn in Light by [ChooseBackupScreen]: the eight-tap debug log hook the headline carried is on the
 * top bar title, CHOOSE BACKUP opens the picker as the button did, and the inline "Learn more" link is
 * its own row opening the same support page.
 */
class ChooseBackupFragment : LoggingFragment() {
  private val sharedViewModel by activityViewModels<RestoreViewModel>()

  private val pickMedia = registerForActivityResult(BackupFileContract()) {
    if (it != null) {
      onUserChoseBackupFile(it)
    } else {
      Log.i(TAG, "Null URI returned for backup file selection.")
    }
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        ChooseBackupScreen(
          onChooseBackupClick = { onChooseBackupSelected() },
          onLearnMoreClick = { CommunicationActions.openBrowserLink(requireContext(), getString(R.string.backup_support_url)) },
          onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() }
        )
      }
    }
  }

  private fun onChooseBackupSelected() {
    pickMedia.launch("application/octet-stream")
  }

  private fun onUserChoseBackupFile(backupFileUri: Uri) {
    sharedViewModel.setBackupFileUri(backupFileUri)
    NavHostFragment.findNavController(this).safeNavigate(ChooseBackupFragmentDirections.actionChooseLocalBackupFragmentToRestoreLocalBackupFragment())
  }

  private class BackupFileContract : ActivityResultContracts.GetContent() {
    override fun createIntent(context: Context, input: String): Intent {
      return super.createIntent(context, input).apply {
        putExtra(Intent.EXTRA_LOCAL_ONLY, true)
        if (Build.VERSION.SDK_INT >= 26) {
          putExtra(DocumentsContract.EXTRA_INITIAL_URI, SignalStore.settings.latestSignalBackupDirectory)
        }
      }
    }
  }

  companion object {
    private val TAG = Log.tag(ChooseBackupFragment::class.java)
  }
}

@Composable
fun ChooseBackupScreen(
  onChooseBackupClick: () -> Unit,
  onLearnMoreClick: () -> Unit,
  onBack: () -> Unit
) {
  RegistrationScreen(
    title = stringResource(R.string.ChooseBackupFragment__restore_from_backup),
    subtitle = stringResource(R.string.ChooseBackupFragment__restore_your_messages_and_media),
    bottomContent = {
      RegistrationBottomActions(
        center = RegistrationAction(stringResource(R.string.ChooseBackupFragment__choose_backup), onChooseBackupClick)
      )
    },
    back = RegistrationBack(
      contentDescription = stringResource(R.string.RegistrationScreen__back_to_previous_screen),
      onBack = onBack
    )
  ) {
    LightText(
      text = stringResource(R.string.ChooseBackupFragment__learn_more),
      variant = LightTextVariant.Heading,
      modifier = Modifier
        .fillMaxWidth()
        .semantics { role = Role.Button }
        .lightClickable(onClick = onLearnMoreClick)
        .padding(vertical = 0.5f.gridUnitsAsDp())
    )
  }
}

@DayNightPreviews
@Composable
private fun ChooseBackupScreenPreview() {
  Previews.Preview {
    ChooseBackupScreen(onChooseBackupClick = {}, onLearnMoreClick = {}, onBack = {})
  }
}
