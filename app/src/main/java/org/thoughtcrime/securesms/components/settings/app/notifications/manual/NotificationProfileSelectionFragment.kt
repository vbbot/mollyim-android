package org.thoughtcrime.securesms.components.settings.app.notifications.manual

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.lightClickable
import org.signal.core.ui.BottomSheetUtil
import org.signal.core.util.DimensionUnit
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.DSLConfiguration
import org.thoughtcrime.securesms.components.settings.DSLSettingsAdapter
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.LightDSLSettingsBottomSheetFragment
import org.thoughtcrime.securesms.components.settings.registerLightPreference
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.settings.app.notifications.manual.models.NotificationProfileSelection
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.NotificationProfilesRepository
import org.thoughtcrime.securesms.components.settings.configure
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfiles
import org.thoughtcrime.securesms.light.LightSettingsRow
import org.thoughtcrime.securesms.util.formatHours
import java.time.LocalTime

/**
 * BottomSheetDialogFragment that allows a user to select a notification profile to manually enable/disable.
 */
class NotificationProfileSelectionFragment : LightDSLSettingsBottomSheetFragment() {

  private val viewModel: NotificationProfileSelectionViewModel by viewModels(
    factoryProducer = {
      NotificationProfileSelectionViewModel.Factory(NotificationProfilesRepository())
    }
  )

  override fun bindAdapter(adapter: DSLSettingsAdapter) {
    registerLightPreference(adapter, NotificationProfileSelection.Entry::class.java) { model ->
      val context = LocalContext.current
      val profile = model.notificationProfile
      Column {
        LightSettingsRow(
          title = "${profile.emoji.ifBlank { "☾" }} ${profile.name}",
          summary = model.summary.resolve(context),
          onClick = { model.onRowClick(profile) },
          trailing = {
            LightText(
              text = if (model.isExpanded) "⌃" else "⌄",
              variant = LightTextVariant.Copy,
              modifier = Modifier.lightClickable { model.onToggleClick(profile) }
            )
          }
        )
        if (model.isExpanded) {
          LightSettingsRow(
            title = context.getString(R.string.NotificationProfileSelection__for_1_hour),
            onClick = { model.onTimeSlotAClick(profile) }
          )
          LightSettingsRow(
            title = context.getString(
              R.string.NotificationProfileSelection__until_s,
              LocalTime.from(model.timeSlotB).formatHours(context)
            ),
            onClick = { model.onTimeSlotBClick(profile, model.timeSlotB) }
          )
          LightSettingsRow(
            title = context.getString(R.string.NotificationProfileSelection__view_settings),
            onClick = { model.onViewSettingsClick(profile) }
          )
        }
      }
    }
    registerLightPreference(adapter, NotificationProfileSelection.New::class.java) { model ->
      LightSettingsRow(
        title = LocalContext.current.getString(R.string.NotificationProfileSelection__new_profile),
        onClick = model.onClick
      )
    }

    recyclerView.itemAnimator = null

    viewModel.state.observe(viewLifecycleOwner) {
      adapter.submitList(getConfiguration(it).toMappingModelList())
    }
  }

  private fun getConfiguration(state: NotificationProfileSelectionState): DSLConfiguration {
    val activeProfile: NotificationProfile? = NotificationProfiles.getActiveProfile(state.notificationProfiles)

    return configure {
      state.notificationProfiles.sortedDescending().forEach { profile ->
        customPref(
          NotificationProfileSelection.Entry(
            isOn = profile == activeProfile,
            summary = if (profile == activeProfile) DSLSettingsText.from(NotificationProfiles.getActiveProfileDescription(requireContext(), profile)) else DSLSettingsText.from(R.string.NotificationProfileDetails__off),
            notificationProfile = profile,
            isExpanded = profile.id == state.expandedId,
            timeSlotB = state.timeSlotB,
            onRowClick = viewModel::toggleEnabled,
            onTimeSlotAClick = viewModel::enableForOneHour,
            onTimeSlotBClick = viewModel::enableUntil,
            onToggleClick = viewModel::setExpanded,
            onViewSettingsClick = { navigateToSettings(it) }
          )
        )
        space(DimensionUnit.DP.toPixels(16f).toInt())
      }

      customPref(
        NotificationProfileSelection.New(
          onClick = {
            startActivity(AppSettingsActivity.createNotificationProfile(requireContext()))
            dismissAllowingStateLoss()
          }
        )
      )

      space(DimensionUnit.DP.toPixels(20f).toInt())
    }
  }

  private fun navigateToSettings(notificationProfile: NotificationProfile) {
    startActivity(AppSettingsActivity.notificationProfileDetails(requireContext(), notificationProfile.id))
    dismissAllowingStateLoss()
  }

  companion object {
    @JvmStatic
    fun show(fragmentManager: FragmentManager) {
      NotificationProfileSelectionFragment().show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }
}
