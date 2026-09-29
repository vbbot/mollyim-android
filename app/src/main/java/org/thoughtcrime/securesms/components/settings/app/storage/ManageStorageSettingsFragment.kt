/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.storage

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.integerArrayResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.navArgument
import org.signal.core.ui.BottomSheetUtil
import org.signal.core.ui.compose.Animations
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.bytes
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.billing.upgrade.UpgradeToEnableOptimizedStorageSheet
import org.thoughtcrime.securesms.billing.upgrade.UpgradeToPaidTierBottomSheet
import org.thoughtcrime.securesms.database.MediaTable
import org.thoughtcrime.securesms.keyvalue.KeepMessagesDuration
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.light.LightConfirmationDialog
import org.thoughtcrime.securesms.light.LightSettingsDivider
import org.thoughtcrime.securesms.light.LightSettingsRadioRow
import org.thoughtcrime.securesms.light.LightSettingsRow
import org.thoughtcrime.securesms.light.LightSettingsScreen
import org.thoughtcrime.securesms.light.LightSettingsSection
import org.thoughtcrime.securesms.light.LightSettingsToggleRow
import org.thoughtcrime.securesms.light.LightSettingsValueRow
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.mediaoverview.MediaOverviewActivity
import org.thoughtcrime.securesms.preferences.widgets.StorageGraphView
import org.thoughtcrime.securesms.util.viewModel
import java.text.NumberFormat

/**
 * Manage settings related to on-device storage including viewing usage and auto-delete settings.
 */
class ManageStorageSettingsFragment : ComposeFragment() {

  private val viewModel by viewModel<ManageStorageSettingsViewModel> { ManageStorageSettingsViewModel() }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    UpgradeToPaidTierBottomSheet.addResultListener(this) {
      viewModel.setOptimizeStorage(true)
    }
  }

  @ExperimentalMaterial3Api
  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val navController = rememberNavController()

    SignalTheme {
      NavHost(
        navController = navController,
        startDestination = "manage",
        enterTransition = { Animations.navHostSlideInTransition { it } },
        exitTransition = { Animations.navHostSlideOutTransition { -it } },
        popEnterTransition = { Animations.navHostSlideInTransition { -it } },
        popExitTransition = { Animations.navHostSlideOutTransition { it } }
      ) {
        composable("manage") {
          ManageStorageSettingsScreen(
            state = state,
            onNavigationClick = { findNavController().popBackStack() },
            onReviewStorage = { startActivity(MediaOverviewActivity.forAll(requireContext())) },
            onSetKeepMessages = { navController.navigate("set-keep-messages") },
            onSetChatLengthLimit = { navController.navigate("set-chat-length-limit") },
            onSyncTrimThreadDeletes = { viewModel.setSyncTrimDeletes(it) },
            onDeleteChatHistory = { navController.navigate("confirm-delete-chat-history") },
            onToggleOnDeviceStorageOptimization = { enabled ->
              if (state.isPaidTierPending) {
                navController.navigate("paid-tier-pending")
              } else if (state.onDeviceStorageOptimizationState == ManageStorageSettingsViewModel.OnDeviceStorageOptimizationState.REQUIRES_PAID_TIER) {
                UpgradeToEnableOptimizedStorageSheet().show(parentFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
              } else if (enabled && state.localBackupsEnabled) {
                navController.navigate("confirm-optimize-with-local-backup")
              } else {
                viewModel.setOptimizeStorage(enabled)
              }
            }
          )
        }

        composable("set-keep-messages") {
          SetKeepMessagesScreen(
            selection = state.keepMessagesDuration,
            onNavigationClick = { navController.popBackStack() },
            onSelectionChanged = { newDuration ->
              if (viewModel.showConfirmKeepDurationChange(newDuration)) {
                navController.navigate("confirm-set-keep-messages/${newDuration.id}")
              } else {
                viewModel.setKeepMessagesDuration(newDuration)
              }
            }
          )
        }

        composable("set-chat-length-limit") {
          SetChatLengthLimitScreen(
            currentLimit = state.lengthLimit,
            onNavigationClick = { navController.popBackStack() },
            onOptionSelected = { newLengthLimit ->
              if (viewModel.showConfirmSetChatLengthLimit(newLengthLimit)) {
                navController.navigate("confirm-set-length-limit/$newLengthLimit")
              } else {
                viewModel.setChatLengthLimit(newLengthLimit)
              }
            },
            onCustomSelected = { navController.navigate("custom-set-length-limit") }
          )
        }

        dialog("confirm-delete-chat-history") {
          var advancing by remember { mutableStateOf(false) }
          LightConfirmationDialog(
            title = stringResource(id = R.string.preferences_storage__delete_message_history),
            body = if (SignalStore.account.isMultiDevice) {
              stringResource(id = R.string.preferences_storage__this_will_delete_all_message_history_and_media_from_your_device_linked_device)
            } else {
              stringResource(id = R.string.preferences_storage__this_will_delete_all_message_history_and_media_from_your_device)
            },
            confirmLabel = stringResource(id = R.string.delete),
            dismissLabel = stringResource(id = android.R.string.cancel),
            onConfirm = { advancing = true },
            onDismiss = {
              if (advancing) {
                advancing = false
                navController.navigate("double-confirm-delete-chat-history")
              } else {
                navController.popBackStack()
              }
            },
            destructive = true
          )
        }

        dialog("double-confirm-delete-chat-history", dialogProperties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)) {
          var confirmed by remember { mutableStateOf(false) }
          LightConfirmationDialog(
            title = stringResource(id = R.string.preferences_storage__are_you_sure_you_want_to_delete_all_message_history),
            body = if (SignalStore.account.isMultiDevice) {
              stringResource(id = R.string.preferences_storage__all_message_history_will_be_permanently_removed_this_action_cannot_be_undone_linked_device)
            } else {
              stringResource(id = R.string.preferences_storage__all_message_history_will_be_permanently_removed_this_action_cannot_be_undone)
            },
            confirmLabel = stringResource(id = R.string.preferences_storage__delete_all_now),
            dismissLabel = stringResource(id = android.R.string.cancel),
            onConfirm = {
              confirmed = true
              viewModel.deleteChatHistory()
            },
            onDismiss = {
              if (confirmed) {
                navController.popBackStack("manage", inclusive = false)
              } else {
                navController.popBackStack()
              }
            },
            destructive = true
          )
        }

        dialog(
          route = "confirm-set-keep-messages/{keepMessagesId}",
          arguments = listOf(navArgument("keepMessagesId") { type = NavType.IntType })
        ) {
          val newDuration = KeepMessagesDuration.fromId(it.arguments!!.getInt("keepMessagesId"))

          LightConfirmationDialog(
            title = stringResource(id = R.string.preferences_storage__delete_older_messages),
            body = stringResource(id = R.string.preferences_storage__this_will_permanently_delete_all_message_history_and_media, stringResource(id = newDuration.stringResource)),
            confirmLabel = stringResource(id = R.string.delete),
            dismissLabel = stringResource(id = android.R.string.cancel),
            onConfirm = { viewModel.setKeepMessagesDuration(newDuration) },
            onDismiss = { navController.popBackStack() },
            destructive = true
          )
        }

        dialog(
          route = "confirm-set-length-limit/{lengthLimit}",
          arguments = listOf(navArgument("lengthLimit") { type = NavType.IntType })
        ) {
          val newLengthLimit = it.arguments!!.getInt("lengthLimit")

          LightConfirmationDialog(
            title = stringResource(id = R.string.preferences_storage__delete_older_messages),
            body = pluralStringResource(
              id = R.plurals.preferences_storage__this_will_permanently_trim_all_conversations_to_the_d_most_recent_messages,
              count = newLengthLimit,
              NumberFormat.getInstance().format(newLengthLimit)
            ),
            confirmLabel = stringResource(id = R.string.delete),
            dismissLabel = stringResource(id = android.R.string.cancel),
            onConfirm = { viewModel.setChatLengthLimit(newLengthLimit) },
            onDismiss = { navController.popBackStack() },
            destructive = true
          )
        }

        dialog(
          route = "custom-set-length-limit"
        ) {
          SetCustomLengthLimitDialog(
            currentLimit = if (state.lengthLimit != ManageStorageSettingsViewModel.ManageStorageState.NO_LIMIT) state.lengthLimit else null,
            onCustomLimitSet = { newLengthLimit ->
              if (viewModel.showConfirmSetChatLengthLimit(newLengthLimit)) {
                navController.navigate("confirm-set-length-limit/$newLengthLimit")
              } else {
                viewModel.setChatLengthLimit(newLengthLimit)
              }
            },
            onDismiss = { navController.popBackStack() }
          )
        }

        dialog("confirm-optimize-with-local-backup") {
          LightConfirmationDialog(
            title = stringResource(id = R.string.ManageStorageSettingsFragment__media_will_be_removed_from_your_on_device_backup),
            body = stringResource(id = R.string.ManageStorageSettingsFragment__turning_on_optimize_signal_storage_will_offload),
            confirmLabel = stringResource(id = R.string.ManageStorageSettingsFragment__turn_on),
            dismissLabel = stringResource(id = android.R.string.cancel),
            onConfirm = { viewModel.setOptimizeStorage(true) },
            onDismiss = { navController.popBackStack() }
          )
        }

        dialog(
          route = "paid-tier-pending",
          dialogProperties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
          MollyLightTheme {
            LightSettingsScreen(
              title = stringResource(R.string.preferences__storage),
              onBack = { navController.popBackStack() }
            ) {
              LightSettingsRow(
                title = stringResource(R.string.ManageStorageSettingsFragment__storage_optimization_can_only_be_used),
                onClick = null
              )
              LightSettingsRow(
                title = stringResource(android.R.string.ok),
                onClick = { navController.popBackStack() }
              )
            }
          }
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
  }
}

@Composable
private fun ManageStorageSettingsScreen(
  state: ManageStorageSettingsViewModel.ManageStorageState,
  onNavigationClick: () -> Unit = {},
  onReviewStorage: () -> Unit = {},
  onSetKeepMessages: () -> Unit = {},
  onSetChatLengthLimit: () -> Unit = {},
  onSyncTrimThreadDeletes: (Boolean) -> Unit = {},
  onDeleteChatHistory: () -> Unit = {},
  onToggleOnDeviceStorageOptimization: (Boolean) -> Unit = {}
) {
  MollyLightTheme {
    LightSettingsScreen(
      title = stringResource(id = R.string.preferences__storage),
      onBack = onNavigationClick
    ) {
      LightSettingsSection(stringResource(id = R.string.preferences_storage__storage_usage))

      StorageOverview(state.breakdown, onReviewStorage)

      if (state.onDeviceStorageOptimizationState > ManageStorageSettingsViewModel.OnDeviceStorageOptimizationState.FEATURE_NOT_AVAILABLE) {
        LightSettingsDivider()
        LightSettingsSection(stringResource(id = R.string.ManageStorageSettingsFragment__on_device_storage))
        LightSettingsToggleRow(
          checked = state.onDeviceStorageOptimizationState == ManageStorageSettingsViewModel.OnDeviceStorageOptimizationState.ENABLED,
          title = stringResource(id = R.string.ManageStorageSettingsFragment__optimize_on_device_storage),
          summary = stringResource(id = R.string.ManageStorageSettingsFragment__unused_media_will_be_offloaded),
          onCheckedChange = onToggleOnDeviceStorageOptimization
        )
      }

      LightSettingsDivider()
      LightSettingsSection(stringResource(id = R.string.ManageStorageSettingsFragment_chat_limit))

      val keepMessagesLabel = stringResource(id = state.keepMessagesDuration.stringResource)
      LightSettingsValueRow(
        title = stringResource(id = R.string.preferences__keep_messages),
        value = keepMessagesLabel,
        valueContentDescription = keepMessagesLabel,
        onClick = onSetKeepMessages
      )

      val lengthLimitLabel = if (state.lengthLimit != ManageStorageSettingsViewModel.ManageStorageState.NO_LIMIT) {
        pluralStringResource(
          id = R.plurals.preferences_storage__s_messages_plural,
          count = state.lengthLimit,
          NumberFormat.getInstance().format(state.lengthLimit)
        )
      } else {
        stringResource(id = R.string.preferences_storage__none)
      }
      LightSettingsValueRow(
        title = stringResource(id = R.string.preferences__conversation_length_limit),
        value = lengthLimitLabel,
        valueContentDescription = lengthLimitLabel,
        onClick = onSetChatLengthLimit
      )

      LightSettingsToggleRow(
        title = stringResource(id = R.string.ManageStorageSettingsFragment_apply_limits_title),
        summary = stringResource(id = R.string.ManageStorageSettingsFragment_apply_limits_description),
        checked = state.syncTrimDeletes,
        onCheckedChange = onSyncTrimThreadDeletes
      )

      LightSettingsDivider()
      LightSettingsRow(
        title = stringResource(id = R.string.ManageStorageSettingsFragment_delete_message_history),
        onClick = onDeleteChatHistory
      )
    }
  }
}

@Composable
private fun StorageOverview(
  breakdown: MediaTable.StorageBreakdown?,
  onReviewStorage: () -> Unit
) {
  AndroidView(
    factory = {
      LayoutInflater.from(it).inflate(R.layout.preference_storage_category, null)
    },
    modifier = Modifier.fillMaxWidth()
  ) {
    if (breakdown != null) {
      val breakdownEntries = StorageGraphView.StorageBreakdown(
        listOf(
          StorageGraphView.Entry(ContextCompat.getColor(it.context, R.color.storage_color_photos), breakdown.photoSize),
          StorageGraphView.Entry(ContextCompat.getColor(it.context, R.color.storage_color_videos), breakdown.videoSize),
          StorageGraphView.Entry(ContextCompat.getColor(it.context, R.color.storage_color_files), breakdown.documentSize),
          StorageGraphView.Entry(ContextCompat.getColor(it.context, R.color.storage_color_audio), breakdown.audioSize)
        )
      )

      it.findViewById<StorageGraphView>(R.id.storageGraphView).setStorageBreakdown(breakdownEntries)
      it.findViewById<TextView>(R.id.total_size).text = breakdownEntries.totalSize.bytes.toUnitString()
    }

    it.findViewById<View>(R.id.free_up_space).setOnClickListener {
      onReviewStorage()
    }
  }
}

@Composable
private fun SetKeepMessagesScreen(
  selection: KeepMessagesDuration,
  onNavigationClick: () -> Unit = {},
  onSelectionChanged: (KeepMessagesDuration) -> Unit = {}
) {
  MollyLightTheme {
    LightSettingsScreen(
      title = stringResource(id = R.string.preferences__keep_messages),
      onBack = onNavigationClick
    ) {
      KeepMessagesDuration.entries.forEach { duration ->
        LightSettingsRadioRow(
          title = stringResource(id = duration.stringResource),
          selected = duration == selection,
          onClick = { onSelectionChanged(duration) }
        )
      }

      LightSettingsRow(
        title = stringResource(id = R.string.ManageStorageSettingsFragment_keep_messages_duration_warning),
        onClick = null
      )
    }
  }
}

@Composable
private fun SetChatLengthLimitScreen(
  currentLimit: Int,
  onNavigationClick: () -> Unit = {},
  onOptionSelected: (Int) -> Unit = {},
  onCustomSelected: (Int) -> Unit = {}
) {
  val options = integerArrayResource(id = R.array.conversation_length_limit)
  val customSelected = currentLimit !in options

  MollyLightTheme {
    LightSettingsScreen(
      title = stringResource(id = R.string.preferences__conversation_length_limit),
      onBack = onNavigationClick
    ) {
      options.forEach { option ->
        LightSettingsRadioRow(
          selected = option == currentLimit,
          title = if (option == 0) {
            stringResource(id = R.string.preferences_storage__none)
          } else {
            pluralStringResource(
              id = R.plurals.preferences_storage__s_messages_plural,
              count = option,
              NumberFormat.getInstance().format(option.toLong())
            )
          },
          onClick = { onOptionSelected(option) }
        )
      }

      LightSettingsRadioRow(
        title = stringResource(id = R.string.preferences_storage__custom),
        summary = if (customSelected) {
          pluralStringResource(
            id = R.plurals.preferences_storage__s_messages_plural,
            count = currentLimit,
            NumberFormat.getInstance().format(currentLimit)
          )
        } else {
          null
        },
        selected = customSelected,
        onClick = { onCustomSelected(currentLimit) }
      )

      LightSettingsRow(
        title = stringResource(id = R.string.ManageStorageSettingsFragment_chat_length_limit_warning),
        onClick = null
      )
    }
  }
}

@Composable
private fun SetCustomLengthLimitDialog(
  currentLimit: Int? = 1000,
  onCustomLimitSet: (Int) -> Unit = {},
  onDismiss: () -> Unit = {}
) {
  var lengthLimit by remember {
    mutableStateOf(
      TextFieldValue(
        text = currentLimit?.toString() ?: "",
        selection = TextRange(currentLimit.toString().length)
      )
    )
  }

  val focusRequester = remember { FocusRequester() }

  androidx.compose.material3.AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(id = R.string.preferences__conversation_length_limit)) },
    text = {
      TextField(
        value = lengthLimit,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        onValueChange = { value ->
          val cleaned = value.text.replace("\\D".toRegex(), "")
          lengthLimit = if (cleaned == value.text) {
            value
          } else {
            value.copy(text = cleaned)
          }
        },
        modifier = Modifier.focusRequester(focusRequester)
      )

      LaunchedEffect(Unit) {
        focusRequester.requestFocus()
      }
    },
    confirmButton = {
      TextButton(
        enabled = lengthLimit.text.toIntOrNull() != null,
        onClick = {
          onDismiss()
          onCustomLimitSet(lengthLimit.text.toInt())
        }
      ) {
        Text(text = stringResource(id = android.R.string.ok))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(text = stringResource(id = android.R.string.cancel))
      }
    }
  )
}

@DayNightPreviews
@Composable
private fun ManageStorageSettingsScreenPreview() {
  Previews.Preview {
    ManageStorageSettingsScreen(
      state = ManageStorageSettingsViewModel.ManageStorageState(
        keepMessagesDuration = KeepMessagesDuration.FOREVER,
        lengthLimit = ManageStorageSettingsViewModel.ManageStorageState.NO_LIMIT,
        syncTrimDeletes = true,
        onDeviceStorageOptimizationState = ManageStorageSettingsViewModel.OnDeviceStorageOptimizationState.DISABLED
      )
    )
  }
}

@DayNightPreviews
@Composable
private fun SetKeepMessagesScreenPreview() {
  Previews.Preview {
    SetKeepMessagesScreen(selection = KeepMessagesDuration.FOREVER)
  }
}

@DayNightPreviews
@Composable
private fun SetChatLengthLimitScreenPreview() {
  Previews.Preview {
    SetChatLengthLimitScreen(
      currentLimit = 1000
    )
  }
}

@DayNightPreviews
@Composable
private fun SetCustomLengthLimitDialogPreview() {
  Previews.Preview {
    SetCustomLengthLimitDialog(currentLimit = 123)
  }
}
