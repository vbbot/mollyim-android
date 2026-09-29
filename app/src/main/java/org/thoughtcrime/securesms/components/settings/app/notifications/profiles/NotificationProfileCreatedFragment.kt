package org.thoughtcrime.securesms.components.settings.app.notifications.profiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import kotlinx.coroutines.rx3.asFlow
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Shown at the end of the profile create flow.
 */
class NotificationProfileCreatedFragment : ComposeFragment() {

  private val profileId: Long by lazy { NotificationProfileCreatedFragmentArgs.fromBundle(requireArguments()).profileId }

  @Composable
  override fun FragmentContent() {
    val repository = remember { NotificationProfilesRepository() }
    val profile by remember(profileId, repository) {
      repository.getProfile(profileId)
        .observeOn(AndroidSchedulers.mainThread())
        .asFlow()
    }.collectAsStateWithLifecycle(initialValue = null)

    NotificationProfileCreatedContent(
      profile = profile,
      onBack = { findNavController().navigateUp() },
      onDone = {
        findNavController().safeNavigate(
          NotificationProfileCreatedFragmentDirections.actionNotificationProfileCreatedFragmentToNotificationProfileDetailsFragment(profileId)
        )
      }
    )
  }
}

@Composable
private fun NotificationProfileCreatedContent(
  profile: NotificationProfile?,
  onBack: () -> Unit,
  onDone: () -> Unit
) {
  org.thoughtcrime.securesms.light.MollyLightTheme {
    org.thoughtcrime.securesms.light.LightSettingsScreen(
      title = "",
      onBack = onBack
    ) {
      if (profile == null) {
        org.thoughtcrime.securesms.light.LightSettingsProgressRow(
          title = androidx.compose.ui.res.stringResource(R.string.NotificationProfileCreated__profile_created)
        )
      } else {
        org.thoughtcrime.securesms.light.LightSettingsSection(
          androidx.compose.ui.res.stringResource(R.string.NotificationProfileCreated__profile_created)
        )
        if (profile.schedule.enabled) {
          org.thoughtcrime.securesms.light.LightSettingsRow(
            title = androidx.compose.ui.res.stringResource(R.string.NotificationProfileCreated__your_profile_will_turn_on_and_off_automatically_according_to_your_schedule),
            onClick = null
          )
          org.thoughtcrime.securesms.light.LightSettingsRow(
            title = androidx.compose.ui.res.stringResource(R.string.NotificationProfileCreated__you_can_turn_your_profile_on_or_off_manually_via_the_menu_on_the_chat_list),
            onClick = null
          )
        } else {
          org.thoughtcrime.securesms.light.LightSettingsRow(
            title = androidx.compose.ui.res.stringResource(R.string.NotificationProfileCreated__you_can_turn_your_profile_on_or_off_manually_via_the_menu_on_the_chat_list),
            onClick = null
          )
          org.thoughtcrime.securesms.light.LightSettingsRow(
            title = androidx.compose.ui.res.stringResource(R.string.NotificationProfileCreated__add_a_schedule_in_settings_to_automate_your_profile),
            onClick = null
          )
        }
        org.thoughtcrime.securesms.light.LightSettingsDivider()
        org.thoughtcrime.securesms.light.LightSettingsRow(
          title = androidx.compose.ui.res.stringResource(R.string.NotificationProfileCreated__done),
          onClick = onDone
        )
      }
    }
  }
}
