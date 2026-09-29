/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.chats

import android.net.Uri
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.signal.core.ui.compose.Launchers
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.LightSettingsProgressRow
import org.thoughtcrime.securesms.light.LightSettingsRow
import org.thoughtcrime.securesms.light.LightSettingsScreen
import org.thoughtcrime.securesms.light.MollyLightTheme

/**
 * Dialogs displayed while processing a user's decrypted chat export.
 *
 * Displayed *after* the user has confirmed via phone auth.
 */
@Composable
fun ChatExportDialogs(state: ChatExportState, callbacks: ChatExportCallbacks) {
  val folderPicker = Launchers.rememberOpenDocumentTreeLauncher {
    if (it != null) {
      callbacks.onFolderSelected(it)
    } else {
      callbacks.onCancelStartExport()
    }
  }

  when (state) {
    ChatExportState.None -> Unit
    ChatExportState.ConfirmExport -> ConfirmExportDialog(
      onConfirmExport = callbacks::onConfirmExport,
      onCancel = callbacks::onCancelStartExport
    )

    ChatExportState.ChooseAFolder -> ChooseAFolderDialog(
      onChooseAFolder = { folderPicker.launch(null) },
      onCancel = callbacks::onCancelStartExport
    )

    ChatExportState.Canceling -> LightExportDialog(
      title = stringResource(R.string.ChatExportDialogs__export_chat_history_title),
      onDismiss = {},
      dismissible = false
    ) {
      LightSettingsProgressRow(
        title = stringResource(R.string.ChatExportDialogs__canceling_export)
      )
    }

    ChatExportState.Success -> CompleteDialog(
      onOK = callbacks::onCompletionConfirmed
    )
  }
}

@Composable
private fun ConfirmExportDialog(
  onConfirmExport: (withMedia: Boolean) -> Unit,
  onCancel: () -> Unit
) {
  LightExportDialog(
    title = stringResource(R.string.ChatExportDialogs__export_chat_history_title),
    onDismiss = onCancel
  ) {
    LightSettingsRow(
      title = stringResource(R.string.ChatExportDialogs__be_careful_warning),
      summary = stringResource(R.string.ChatExportDialogs__export_confirm_body),
      onClick = null
    )
    LightSettingsRow(
      title = stringResource(R.string.ChatExportDialogs__export_with_media),
      onClick = { onConfirmExport(true) }
    )
    LightSettingsRow(
      title = stringResource(R.string.ChatExportDialogs__export_without_media),
      onClick = { onConfirmExport(false) }
    )
  }
}

@Composable
private fun ChooseAFolderDialog(
  onChooseAFolder: () -> Unit,
  onCancel: () -> Unit
) {
  LightExportDialog(
    title = stringResource(R.string.ChatExportDialogs__choose_a_folder_title),
    onDismiss = onCancel
  ) {
    LightSettingsRow(
      title = stringResource(R.string.ChatExportDialogs__choose_a_folder_body),
      onClick = null
    )
    LightSettingsRow(
      title = stringResource(R.string.ChatExportDialogs__choose_folder_button),
      onClick = onChooseAFolder
    )
  }
}

@Composable
private fun CompleteDialog(
  onOK: () -> Unit
) {
  LightExportDialog(
    title = stringResource(R.string.ChatExportDialogs__complete_title),
    onDismiss = onOK
  ) {
    LightSettingsRow(
      title = stringResource(R.string.ChatExportDialogs__be_careful),
      summary = stringResource(R.string.ChatExportDialogs__complete_body),
      onClick = null
    )
    LightSettingsRow(
      title = stringResource(android.R.string.ok),
      onClick = onOK
    )
  }
}

@Composable
private fun LightExportDialog(
  title: String,
  onDismiss: () -> Unit,
  dismissible: Boolean = true,
  content: @Composable ColumnScope.() -> Unit
) {
  Dialog(
    onDismissRequest = { if (dismissible) onDismiss() },
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    MollyLightTheme {
      LightSettingsScreen(
        title = title,
        onBack = { if (dismissible) onDismiss() },
        content = content
      )
    }
  }
}

enum class ChatExportState {
  None,
  ConfirmExport,
  ChooseAFolder,
  Canceling,
  Success
}

interface ChatExportCallbacks {
  fun onConfirmExport(withMedia: Boolean)
  fun onFolderSelected(uri: Uri)
  fun onCancelStartExport()
  fun onCompletionConfirmed()

  object Empty : ChatExportCallbacks {
    override fun onConfirmExport(withMedia: Boolean) = Unit
    override fun onFolderSelected(uri: Uri) = Unit
    override fun onCancelStartExport() = Unit
    override fun onCompletionConfirmed() = Unit
  }
}
