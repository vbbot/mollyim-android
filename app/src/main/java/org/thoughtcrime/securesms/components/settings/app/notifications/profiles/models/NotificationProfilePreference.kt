package org.thoughtcrime.securesms.components.settings.app.notifications.profiles.models

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.components.settings.DSLSettingsIcon
import org.thoughtcrime.securesms.components.settings.DSLSettingsText
import org.thoughtcrime.securesms.components.settings.PreferenceModel
import org.thoughtcrime.securesms.conversation.colors.AvatarColor
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileId
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfiles
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import java.util.UUID

/**
 * DSL custom preference for showing Notification Profile rows.
 */
object NotificationProfilePreference {

  fun register(adapter: MappingAdapter) {
    org.thoughtcrime.securesms.components.settings.registerLightPreference(adapter, Model::class.java) { model ->
      val context = LocalContext.current
      if (model.showSwitch) {
        org.thoughtcrime.securesms.light.LightSettingsToggleRow(
          title = model.title.resolve(context),
          summary = model.summary?.resolve(context),
          checked = model.isOn,
          enabled = model.isEnabled,
          onCheckedChange = { model.onClick() }
        )
      } else {
        org.thoughtcrime.securesms.light.LightSettingsRow(
          title = model.title.resolve(context),
          summary = model.summary?.resolve(context),
          enabled = model.isEnabled,
          onClick = model.onClick
        )
      }
    }
  }

  class Model(
    override val title: DSLSettingsText,
    override val summary: DSLSettingsText?,
    override val icon: DSLSettingsIcon?,
    val color: AvatarColor,
    val isOn: Boolean = false,
    val showSwitch: Boolean = false,
    val onClick: () -> Unit
  ) : PreferenceModel<Model>()
}

@Composable
fun NotificationProfileRow(
  profile: NotificationProfile,
  isActiveProfile: Boolean = false,
  showSwitch: Boolean = false,
  enabled: Boolean = true,
  onClick: (Long) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val summary = remember(isActiveProfile, profile) {
    if (isActiveProfile) {
      NotificationProfiles.getActiveProfileDescription(context, profile)
    } else {
      null
    }
  }

  if (showSwitch) {
    org.thoughtcrime.securesms.light.LightSettingsToggleRow(
      title = profile.name,
      summary = summary,
      checked = isActiveProfile,
      enabled = enabled,
      onCheckedChange = { onClick(profile.id) },
      modifier = modifier
    )
  } else {
    org.thoughtcrime.securesms.light.LightSettingsRow(
      title = profile.name,
      summary = summary,
      enabled = enabled,
      onClick = { onClick(profile.id) },
      modifier = modifier
    )
  }
}

@DayNightPreviews
@Composable
private fun NotificationProfileRowPreview() {
  Previews.Preview {
    Column {
      NotificationProfileRow(
        profile = NotificationProfile(
          id = 1L,
          name = "Work",
          createdAt = 0L,
          schedule = NotificationProfileSchedule(
            id = 1L
          ),
          emoji = "",
          notificationProfileId = NotificationProfileId(UUID.randomUUID())
        ),
        onClick = {}
      )

      NotificationProfileRow(
        profile = NotificationProfile(
          id = 1L,
          name = "Sleep",
          createdAt = 0L,
          schedule = NotificationProfileSchedule(
            id = 1L
          ),
          emoji = "",
          notificationProfileId = NotificationProfileId(UUID.randomUUID())
        ),
        onClick = {}
      )
    }
  }
}
