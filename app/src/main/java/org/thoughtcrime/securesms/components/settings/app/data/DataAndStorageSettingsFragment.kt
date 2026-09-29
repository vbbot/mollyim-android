package org.thoughtcrime.securesms.components.settings.app.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.util.bytes
import org.signal.mediasend.SentMediaQuality
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.LightMultiChoiceDialog
import org.thoughtcrime.securesms.light.LightSettingsDivider
import org.thoughtcrime.securesms.light.LightSettingsRow
import org.thoughtcrime.securesms.light.LightSettingsScreen
import org.thoughtcrime.securesms.light.LightSettingsSection
import org.thoughtcrime.securesms.light.LightSettingsValueRow
import org.thoughtcrime.securesms.light.LightSingleChoiceDialog
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.util.AttachmentUtil
import org.thoughtcrime.securesms.util.SecurePreferenceManager
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.webrtc.CallDataMode
import kotlin.math.abs

class DataAndStorageSettingsFragment : ComposeFragment() {

  private val viewModel: DataAndStorageSettingsViewModel by viewModels(
    factoryProducer = {
      val preferences = SecurePreferenceManager.getSecurePreferences(requireContext())
      val repository = DataAndStorageSettingsRepository()
      DataAndStorageSettingsViewModel.Factory(preferences, repository)
    }
  )

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val callbacks = remember { Callbacks() }

    DataAndStorageSettingsScreen(
      state = state,
      callbacks = callbacks
    )
  }

  private inner class Callbacks : DataAndStorageSettingsCallbacks {
    override fun onNavigationClick() {
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onManageStorageClick() {
      findNavController().safeNavigate(R.id.action_dataAndStorageSettingsFragment_to_storagePreferenceFragment)
    }

    override fun onSentMediaQualitySelected(code: String) {
      viewModel.setSentMediaQuality(SentMediaQuality.fromCode(code.toInt()))
    }

    override fun onCallDataModeSelected(code: String) {
      viewModel.setCallDataMode(CallDataMode.fromCode(abs(code.toInt() - 2)))
    }

    override fun onMobileDataAutoDownloadSelectionChanged(selection: Array<String>) {
      viewModel.setMobileAutoDownloadValues(selection.toSet())
    }

    override fun onWifiDataAutoDownloadSelectionChanged(selection: Array<String>) {
      viewModel.setWifiAutoDownloadValues(selection.toSet())
    }

    override fun onRoamingDataAutoDownloadSelectionChanged(selection: Array<String>) {
      viewModel.setRoamingAutoDownloadValues(selection.toSet())
    }
  }
}

private interface DataAndStorageSettingsCallbacks {
  fun onNavigationClick() = Unit
  fun onManageStorageClick() = Unit
  fun onSentMediaQualitySelected(code: String) = Unit
  fun onCallDataModeSelected(code: String) = Unit
  fun onMobileDataAutoDownloadSelectionChanged(selection: Array<String>) = Unit
  fun onWifiDataAutoDownloadSelectionChanged(selection: Array<String>) = Unit
  fun onRoamingDataAutoDownloadSelectionChanged(selection: Array<String>) = Unit
  fun onForceWebsocketModeChanged(enabled: Boolean) = Unit
  fun onConfirmStayConnectedInBackground() = Unit
  fun onDismissStayConnectedInBackgroundDialog() = Unit

  object Empty : DataAndStorageSettingsCallbacks
}

private enum class DataChoice {
  MOBILE,
  WIFI,
  ROAMING,
  MEDIA_QUALITY,
  CALL_DATA
}

@Composable
private fun DataAndStorageSettingsScreen(
  state: DataAndStorageSettingsState,
  callbacks: DataAndStorageSettingsCallbacks
) {
  val context = LocalContext.current
  val downloadLabels = stringArrayResource(R.array.pref_media_download_entries).toList()
  val downloadValues = stringArrayResource(R.array.pref_media_download_values).toList()
  val mediaQualityLabels = remember { SentMediaQuality.getLabels(context).toList() }
  val mediaQualityValues = SentMediaQuality.entries.map { it.code.toString() }
  val callDataLabels = stringArrayResource(R.array.pref_data_and_storage_call_data_mode_values).toList()
  val callDataValues = CallDataMode.entries.map { it.code.toString() }
  var choice by remember { mutableStateOf<DataChoice?>(null) }

  fun selectionSummary(selection: Set<String>): String {
    if (selection.isEmpty()) return context.getString(R.string.preferences__none)
    return downloadValues.mapIndexedNotNull { index, value ->
      downloadLabels.getOrNull(index)?.takeIf { value in selection }
    }.joinToString()
  }

  fun labelFor(values: List<String>, labels: List<String>, selected: String): String {
    return labels.getOrElse(values.indexOf(selected)) { selected }
  }

  MollyLightTheme {
    LightSettingsScreen(
      title = stringResource(R.string.preferences__data_and_storage),
      onBack = callbacks::onNavigationClick
    ) {
      val storageUse = state.totalStorageUse.bytes.toUnitString()
      LightSettingsValueRow(
        title = stringResource(R.string.preferences_data_and_storage__manage_storage),
        value = storageUse,
        valueContentDescription = storageUse,
        onClick = callbacks::onManageStorageClick
      )

      LightSettingsDivider()
      LightSettingsSection(stringResource(R.string.preferences_chats__media_auto_download))

      LightSettingsValueRow(
        title = stringResource(R.string.preferences_chats__when_using_mobile_data),
        value = selectionSummary(state.mobileAutoDownloadValues),
        valueContentDescription = selectionSummary(state.mobileAutoDownloadValues),
        onClick = { choice = DataChoice.MOBILE }
      )
      LightSettingsValueRow(
        title = stringResource(R.string.preferences_chats__when_using_wifi),
        value = selectionSummary(state.wifiAutoDownloadValues),
        valueContentDescription = selectionSummary(state.wifiAutoDownloadValues),
        onClick = { choice = DataChoice.WIFI }
      )
      LightSettingsValueRow(
        title = stringResource(R.string.preferences_chats__when_roaming),
        value = selectionSummary(state.roamingAutoDownloadValues),
        valueContentDescription = selectionSummary(state.roamingAutoDownloadValues),
        onClick = { choice = DataChoice.ROAMING }
      )
      LightSettingsRow(
        title = stringResource(
          R.string.DataAndStorageSettingsFragment__voice_messages_and_stickers_under_size_are_always_auto_downloaded,
          AttachmentUtil.SMALL_ATTACHMENT_SIZE.toUnitString()
        ),
        onClick = null
      )

      LightSettingsDivider()
      LightSettingsSection(stringResource(R.string.DataAndStorageSettingsFragment__media_quality))

      val selectedMediaQuality = state.sentMediaQuality.code.toString()
      LightSettingsValueRow(
        title = stringResource(R.string.DataAndStorageSettingsFragment__sent_media_quality),
        value = labelFor(mediaQualityValues, mediaQualityLabels, selectedMediaQuality),
        valueContentDescription = labelFor(mediaQualityValues, mediaQualityLabels, selectedMediaQuality),
        onClick = { choice = DataChoice.MEDIA_QUALITY }
      )
      LightSettingsRow(
        title = stringResource(R.string.DataAndStorageSettingsFragment__sending_high_quality_media_will_use_more_data),
        onClick = null
      )

      LightSettingsDivider()
      LightSettingsSection(stringResource(R.string.DataAndStorageSettingsFragment__calls))

      val selectedCallDataMode = abs(state.callDataMode.code - 2).toString()
      LightSettingsValueRow(
        title = stringResource(R.string.preferences_data_and_storage__use_less_data_for_calls),
        value = labelFor(callDataValues, callDataLabels, selectedCallDataMode),
        valueContentDescription = labelFor(callDataValues, callDataLabels, selectedCallDataMode),
        onClick = { choice = DataChoice.CALL_DATA }
      )
      LightSettingsRow(
        title = stringResource(R.string.preference_data_and_storage__using_less_data_may_improve_calls_on_bad_networks),
        onClick = null
      )
    }

    when (choice) {
      DataChoice.MOBILE,
      DataChoice.WIFI,
      DataChoice.ROAMING -> {
        val selection = when (choice) {
          DataChoice.MOBILE -> state.mobileAutoDownloadValues
          DataChoice.WIFI -> state.wifiAutoDownloadValues
          else -> state.roamingAutoDownloadValues
        }
        val title = when (choice) {
          DataChoice.MOBILE -> stringResource(R.string.preferences_chats__when_using_mobile_data)
          DataChoice.WIFI -> stringResource(R.string.preferences_chats__when_using_wifi)
          else -> stringResource(R.string.preferences_chats__when_roaming)
        }
        LightMultiChoiceDialog(
          title = title,
          items = downloadLabels,
          selected = downloadValues.map { it in selection }.toBooleanArray(),
          onSelected = { selected ->
            val values = downloadValues.filterIndexed { index, _ -> selected[index] }.toTypedArray()
            when (choice) {
              DataChoice.MOBILE -> callbacks.onMobileDataAutoDownloadSelectionChanged(values)
              DataChoice.WIFI -> callbacks.onWifiDataAutoDownloadSelectionChanged(values)
              else -> callbacks.onRoamingDataAutoDownloadSelectionChanged(values)
            }
          },
          onDismiss = { choice = null }
        )
      }

      DataChoice.MEDIA_QUALITY -> LightSingleChoiceDialog(
        title = stringResource(R.string.DataAndStorageSettingsFragment__sent_media_quality),
        items = mediaQualityLabels,
        selected = mediaQualityValues.indexOf(state.sentMediaQuality.code.toString()),
        confirmAction = false,
        onSelected = { callbacks.onSentMediaQualitySelected(mediaQualityValues[it]) },
        onDismiss = { choice = null }
      )

      DataChoice.CALL_DATA -> LightSingleChoiceDialog(
        title = stringResource(R.string.preferences_data_and_storage__use_less_data_for_calls),
        items = callDataLabels,
        selected = callDataValues.indexOf(abs(state.callDataMode.code - 2).toString()),
        confirmAction = false,
        onSelected = { callbacks.onCallDataModeSelected(callDataValues[it]) },
        onDismiss = { choice = null }
      )

      null -> Unit
    }
  }
}

@DayNightPreviews
@Composable
private fun DataAndStorageSettingsScreenPreview() {
  Previews.Preview {
    DataAndStorageSettingsScreen(
      state = DataAndStorageSettingsState(
        totalStorageUse = 100_000,
        mobileAutoDownloadValues = setOf(),
        wifiAutoDownloadValues = setOf(),
        roamingAutoDownloadValues = setOf(),
        callDataMode = CallDataMode.HIGH_ALWAYS,
        sentMediaQuality = SentMediaQuality.STANDARD,
      ),
      callbacks = DataAndStorageSettingsCallbacks.Empty
    )
  }
}
