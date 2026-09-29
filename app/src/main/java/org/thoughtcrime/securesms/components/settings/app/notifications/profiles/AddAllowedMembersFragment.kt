package org.thoughtcrime.securesms.components.settings.app.notifications.profiles

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.kotlin.subscribeBy
import kotlinx.coroutines.rx3.asFlow
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.emoji.EmojiStrings
import org.thoughtcrime.securesms.components.settings.app.notifications.profiles.AddAllowedMembersViewModel.NotificationProfileAndRecipients
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfile
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileId
import org.thoughtcrime.securesms.notifications.profiles.NotificationProfileSchedule
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import java.util.UUID

/**
 * Show and allow addition of recipients to a profile during the create flow.
 */
class AddAllowedMembersFragment : ComposeFragment() {

  private val viewModel: AddAllowedMembersViewModel by viewModels(factoryProducer = { AddAllowedMembersViewModel.Factory(profileId) })
  private val lifecycleDisposable = LifecycleDisposable()
  private val profileId: Long by lazy { AddAllowedMembersFragmentArgs.fromBundle(requireArguments()).profileId }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    lifecycleDisposable.bindTo(viewLifecycleOwner.lifecycle)
  }

  @Composable
  override fun FragmentContent() {
    val state by remember { viewModel.getProfile().map { GetProfileResult.Ready(it) }.asFlow() }
      .collectAsStateWithLifecycle(GetProfileResult.Loading)
    val callbacks = remember { Callbacks() }

    if (state is GetProfileResult.Ready) {
      AddAllowedMembersContent(
        state = (state as GetProfileResult.Ready).notificationProfileAndRecipients,
        callbacks = callbacks
      )
    }
  }

  private fun undoRemove(id: RecipientId) {
    lifecycleDisposable += viewModel.addMember(id)
      .subscribe()
  }

  companion object {
    private val TAG = Log.tag(AddAllowedMembersFragment::class.java)
  }

  private inner class Callbacks : AddAllowedMembersCallbacks {
    override fun onNavigationClick() {
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onAllowAllCallsChanged(enabled: Boolean) {
      lifecycleDisposable += viewModel.toggleAllowAllCalls()
        .subscribeBy(
          onError = { Log.w(TAG, "Error updating profile", it) }
        )
    }

    override fun onNotifyForAllMentionsChanged(enabled: Boolean) {
      lifecycleDisposable += viewModel.toggleAllowAllMentions()
        .subscribeBy(
          onError = { Log.w(TAG, "Error updating profile", it) }
        )
    }

    override fun onAddMembersClick(id: Long, allowedMembers: Set<RecipientId>) {
      findNavController().safeNavigate(
        AddAllowedMembersFragmentDirections.actionAddAllowedMembersFragmentToSelectRecipientsFragment(id)
          .setCurrentSelection(allowedMembers.toTypedArray())
      )
    }

    override fun onRemoveMemberClick(id: RecipientId) {
      lifecycleDisposable += viewModel.removeMember(id)
        .subscribeBy(
          onSuccess = { removed ->
            view?.let { view ->
              Snackbar.make(view, getString(R.string.NotificationProfileDetails__s_removed, removed.getDisplayName(requireContext())), Snackbar.LENGTH_LONG)
                .setAction(R.string.NotificationProfileDetails__undo) { undoRemove(id) }
                .show()
            }
          }
        )
    }

    override fun onNextClick() {
      findNavController().safeNavigate(AddAllowedMembersFragmentDirections.actionAddAllowedMembersFragmentToEditNotificationProfileScheduleFragment(profileId, true))
    }
  }
}

private sealed interface GetProfileResult {
  data object Loading : GetProfileResult
  data class Ready(val notificationProfileAndRecipients: NotificationProfileAndRecipients) : GetProfileResult
}

private interface AddAllowedMembersCallbacks {
  fun onNavigationClick() = Unit
  fun onAllowAllCallsChanged(enabled: Boolean) = Unit
  fun onNotifyForAllMentionsChanged(enabled: Boolean) = Unit
  fun onAddMembersClick(id: Long, allowedMembers: Set<RecipientId>) = Unit
  fun onRemoveMemberClick(id: RecipientId) = Unit
  fun onNextClick() = Unit

  object Empty : AddAllowedMembersCallbacks
}

@Composable
private fun AddAllowedMembersContent(
  state: NotificationProfileAndRecipients,
  callbacks: AddAllowedMembersCallbacks
) {
  org.thoughtcrime.securesms.light.MollyLightTheme {
    org.thoughtcrime.securesms.light.LightSettingsScreen(
      title = "",
      onBack = callbacks::onNavigationClick
    ) {
      org.thoughtcrime.securesms.light.LightSettingsSection(
        stringResource(R.string.AddAllowedMembers__allowed_notifications)
      )
      org.thoughtcrime.securesms.light.LightSettingsRow(
        title = stringResource(R.string.AddAllowedMembers__add_people_and_groups_you_want_notifications_and_calls_from_when_this_profile_is_on),
        onClick = null
      )

      val addMembers = remember(state.profile.id, state.profile.allowedMembers) {
        {
          callbacks.onAddMembersClick(state.profile.id, state.profile.allowedMembers)
        }
      }
      NotificationProfileAddMembers(onClick = addMembers)

      for (member in state.recipients) {
        NotificationProfileRecipient(
          recipient = member,
          onRemoveClick = callbacks::onRemoveMemberClick
        )
      }

      org.thoughtcrime.securesms.light.LightSettingsSection(
        stringResource(R.string.AddAllowedMembers__exceptions)
      )
      org.thoughtcrime.securesms.light.LightSettingsToggleRow(
        checked = state.profile.allowAllCalls,
        title = stringResource(R.string.AddAllowedMembers__allow_all_calls),
        onCheckedChange = callbacks::onAllowAllCallsChanged
      )
      org.thoughtcrime.securesms.light.LightSettingsToggleRow(
        checked = state.profile.allowAllMentions,
        title = stringResource(R.string.AddAllowedMembers__notify_for_all_mentions),
        onCheckedChange = callbacks::onNotifyForAllMentionsChanged
      )

      org.thoughtcrime.securesms.light.LightSettingsDivider()
      org.thoughtcrime.securesms.light.LightSettingsRow(
        title = stringResource(R.string.EditNotificationProfileFragment__next),
        onClick = callbacks::onNextClick
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun AddAllowedMembersContentPreview() {
  Previews.Preview {
    AddAllowedMembersContent(
      state = NotificationProfileAndRecipients(
        profile = NotificationProfile(
          id = 0L,
          name = "Test Profile",
          emoji = EmojiStrings.PHOTO,
          createdAt = System.currentTimeMillis(),
          schedule = NotificationProfileSchedule(
            id = 0L
          ),
          notificationProfileId = NotificationProfileId(UUID.randomUUID())
        ),
        recipients = (1..3).map {
          Recipient(
            id = RecipientId.from(it.toLong()),
            isResolving = false,
            registeredValue = RecipientTable.RegisteredState.REGISTERED,
            systemContactName = "Test User $it"
          )
        }
      ),
      callbacks = AddAllowedMembersCallbacks.Empty
    )
  }
}
