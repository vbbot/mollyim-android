package org.thoughtcrime.securesms.components.settings.app.notifications.profiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.EmojiStrings
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.models.NotificationProfileRow
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileId
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import java.util.UUID

/**
 * Primary entry point for Notification Profiles. When user has no profiles, shows empty state, otherwise shows
 * all current profiles.
 */
class NotificationProfilesFragment : ComposeFragment() {

  private val viewModel: NotificationProfilesViewModel by viewModels(
    factoryProducer = { NotificationProfilesViewModel.Factory() }
  )

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle(initialValue = NotificationProfilesState(profiles = emptyList()))
    val callback = remember { DefaultNotificationProfilesScreenCallback() }

    NotificationProfilesScreen(
      state = state,
      callbacks = callback
    )
  }

  inner class DefaultNotificationProfilesScreenCallback : NotificationProfilesScreenCallback {
    override fun onNavigationClick() {
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onCreateNewProfile() {
      findNavController().safeNavigate(R.id.action_notificationProfilesFragment_to_editNotificationProfileFragment)
    }

    override fun onProfileClick(profileId: Long) {
      findNavController().safeNavigate(NotificationProfilesFragmentDirections.actionNotificationProfilesFragmentToNotificationProfileDetailsFragment(profileId))
    }
  }
}

@Composable
fun NotificationProfilesScreen(
  state: NotificationProfilesState,
  callbacks: NotificationProfilesScreenCallback
) {
  val title = if (state.profiles.isEmpty()) {
    ""
  } else {
    stringResource(R.string.NotificationsSettingsFragment__notification_profiles)
  }

  org.thoughtcrime.securesms.light.MollyLightTheme {
    org.thoughtcrime.securesms.light.LightSettingsScreen(
      title = title,
      onBack = callbacks::onNavigationClick
    ) {
      if (state.profiles.isEmpty()) {
        NoNotificationProfilesEmpty(onCreateProfileClick = callbacks::onCreateNewProfile)
      } else {
        org.thoughtcrime.securesms.light.LightSettingsSection(
          stringResource(R.string.NotificationProfilesFragment__profiles)
        )
        org.thoughtcrime.securesms.light.LightSettingsRow(
          title = stringResource(R.string.NotificationProfilesFragment__new_profile),
          onClick = callbacks::onCreateNewProfile
        )

        state.profiles.sortedDescending().forEach { profile ->
          NotificationProfileRow(
            profile = profile,
            isActiveProfile = profile == state.activeProfile,
            onClick = callbacks::onProfileClick
          )
        }
      }
    }
  }
}

@Composable
private fun NoNotificationProfilesEmpty(
  onCreateProfileClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  org.thoughtcrime.securesms.light.LightSettingsSection(
    title = stringResource(R.string.NotificationProfilesFragment__notification_profiles),
    modifier = modifier
  )
  org.thoughtcrime.securesms.light.LightSettingsRow(
    title = stringResource(R.string.NotificationProfilesFragment__create_a_profile_to_receive_notifications_and_calls_only_from_the_people_and_groups_you_want_to_hear_from),
    onClick = null
  )
  org.thoughtcrime.securesms.light.LightSettingsRow(
    title = stringResource(R.string.NotificationProfilesFragment__create_profile),
    onClick = onCreateProfileClick
  )
}

@DayNightPreviews
@Composable
fun NotificationProfilesScreenPreview() {
  Previews.Preview {
    val profile = remember {
      NotificationProfile(
        id = 1L,
        name = "Test Profile",
        emoji = EmojiStrings.AUDIO,
        createdAt = System.currentTimeMillis(),
        schedule = NotificationProfileSchedule(
          id = 1
        ),
        notificationProfileId = NotificationProfileId(UUID.randomUUID())
      )
    }

    NotificationProfilesScreen(
      state = NotificationProfilesState(
        profiles = listOf(
          profile
        ),
        activeProfile = null
      ),
      callbacks = NotificationProfilesScreenCallback.Empty
    )
  }
}

@DayNightPreviews
@Composable
private fun NoNotificationProfilesEmptyPreview() {
  Previews.Preview {
    NoNotificationProfilesEmpty(
      onCreateProfileClick = {}
    )
  }
}

interface NotificationProfilesScreenCallback {
  fun onNavigationClick()
  fun onCreateNewProfile()
  fun onProfileClick(profileId: Long)

  object Empty : NotificationProfilesScreenCallback {
    override fun onNavigationClick() = Unit
    override fun onCreateNewProfile() = Unit
    override fun onProfileClick(profileId: Long) = Unit
  }
}
