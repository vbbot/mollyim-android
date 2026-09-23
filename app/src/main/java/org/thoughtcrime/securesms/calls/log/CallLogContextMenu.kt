package org.thoughtcrime.securesms.calls.log

import androidx.fragment.app.Fragment
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.util.concurrent.LifecycleDisposable
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.calls.YouAreAlreadyInACallSnackbar
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsActivity
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import org.thoughtcrime.securesms.util.CommunicationActions
import org.signal.core.ui.R as CoreUiR

/**
 * What a long press on a call log row offers, and which of those actions apply to which row.
 *
 * This is only the *contents* of the menu. It used to present them too, as a `SignalContextMenu`
 * dropped below the pressed row; the Light call log shows the same list as rows in a
 * `LightActionPanel` instead (see `CallLogFragment.showCallMenu`). The gating stayed here so that
 * there is still exactly one place that decides a group you have left offers no video call, a call
 * link offers no chat, and a call that is still running cannot be deleted.
 */
class CallLogContextMenu(
  private val fragment: Fragment,
  private val callbacks: Callbacks
) {

  private val lifecycleDisposable by lazy { LifecycleDisposable().bindTo(fragment.viewLifecycleOwner) }

  fun getActions(call: CallLogRow.Call): List<ActionItem> {
    return listOfNotNull(
      getVideoCallActionItem(call.peer),
      getAudioCallActionItem(call),
      getGoToChatActionItem(call),
      getInfoActionItem(call.peer, (call.id as CallLogRow.Id.Call).children.toLongArray()),
      getSelectActionItem(call),
      getDeleteActionItem(call)
    )
  }

  fun getActions(callLink: CallLogRow.CallLink): List<ActionItem> {
    return listOfNotNull(
      getVideoCallActionItem(callLink.recipient),
      getInfoActionItem(callLink.recipient, longArrayOf()),
      getSelectActionItem(callLink),
      getDeleteActionItem(callLink)
    )
  }

  private fun getVideoCallActionItem(peer: Recipient): ActionItem? {
    if (peer.isGroup && !peer.isActiveGroup) {
      return null
    }

    // TODO [alex] -- Need group calling disposition to make this correct
    return ActionItem(
      iconRes = R.drawable.symbol_video_24,
      title = fragment.getString(R.string.CallContextMenu__video_call)
    ) {
      CommunicationActions.startVideoCall(fragment, peer) {
        YouAreAlreadyInACallSnackbar.show(fragment.requireView())
      }
    }
  }

  private fun getAudioCallActionItem(call: CallLogRow.Call): ActionItem? {
    if (call.peer.isCallLink || call.peer.isGroup) {
      return null
    }

    return ActionItem(
      iconRes = CoreUiR.drawable.symbol_phone_24,
      title = fragment.getString(R.string.CallContextMenu__audio_call)
    ) {
      CommunicationActions.startVoiceCall(fragment, call.peer) {
        YouAreAlreadyInACallSnackbar.show(fragment.requireView())
      }
    }
  }

  private fun getGoToChatActionItem(call: CallLogRow.Call): ActionItem? {
    return when {
      call.peer.isCallLink -> null
      else -> ActionItem(
        iconRes = R.drawable.symbol_open_24,
        title = fragment.getString(R.string.CallContextMenu__go_to_chat)
      ) {
        lifecycleDisposable += ConversationIntents.createBuilder(fragment.requireContext(), call.peer.id, -1L)
          .subscribeBy {
            fragment.startActivity(it.build())
          }
      }
    }
  }

  private fun getInfoActionItem(peer: Recipient, messageIds: LongArray): ActionItem {
    return ActionItem(
      iconRes = CoreUiR.drawable.symbol_info_24,
      title = fragment.getString(R.string.CallContextMenu__info)
    ) {
      when {
        peer.isCallLink -> callbacks.goToCallLinkDetails(peer.requireCallLinkRoomId())
        else -> fragment.startActivity(ConversationSettingsActivity.forCall(fragment.requireContext(), peer, messageIds))
      }
    }
  }

  private fun getSelectActionItem(call: CallLogRow): ActionItem {
    return ActionItem(
      iconRes = CoreUiR.drawable.symbol_check_circle_24,
      title = fragment.getString(R.string.CallContextMenu__select)
    ) {
      callbacks.startSelection(call)
    }
  }

  private fun getDeleteActionItem(call: CallLogRow): ActionItem? {
    if (call is CallLogRow.Call && call.record.event == CallTable.Event.ONGOING) {
      return null
    }

    return ActionItem(
      iconRes = CoreUiR.drawable.symbol_trash_24,
      title = fragment.getString(R.string.CallContextMenu__delete)
    ) {
      callbacks.deleteCall(call)
    }
  }

  interface Callbacks {
    fun startSelection(call: CallLogRow)
    fun goToCallLinkDetails(roomId: CallLinkRoomId)
    fun deleteCall(call: CallLogRow)
  }
}
