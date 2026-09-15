/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversationlist.light

import android.content.Context
import androidx.compose.runtime.Immutable
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversationlist.model.Conversation
import org.thoughtcrime.securesms.conversationlist.model.ConversationSet
import java.util.Locale

/**
 * One rendered row of the Light conversation list.
 *
 * The Light design language row is name + timestamp + unread marker, nothing else: no avatar, no
 * snippet, no delivery status. Everything the row draws is resolved once, up front, off the
 * composition -- [LightConversationRow] only ever reads plain [String]s and [Boolean]s, which is
 * also what [com.thelightphone.sdk.ui.LightText] accepts (it has no `AnnotatedString` overload).
 *
 * [equals]/[hashCode] deliberately cover only the *rendered* fields plus [sourceIndex]. The
 * [conversation] back-reference is carried so that a click can be handed straight back to
 * `ConversationListFragment` (which needs the full [Conversation] for navigation, selection and the
 * context menu), but including it in equality would recompose rows for changes that are invisible
 * in the Light design -- a new snippet, a delivery receipt, a typing indicator.
 */
@Immutable
class LightConversationListItem(
  /** Stable LazyColumn key. Thread id for real rows, `"ph:<index>"` for not-yet-loaded pages. */
  val key: Any,
  /** Index of this row in the *paged data source*, which may include rows we do not render. */
  val sourceIndex: Int,
  val kind: Kind,
  val name: String,
  val timestamp: String,
  val timestampDescription: String,
  val unread: Boolean,
  val selected: Boolean,
  val conversation: Conversation?
) {

  enum class Kind {
    /** A real conversation. */
    THREAD,

    /** The "Archived chats (n)" row that opens the archive. */
    ARCHIVE,

    /** A page the paging controller has not filled in yet. Renders as blank space of row height. */
    PLACEHOLDER
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is LightConversationListItem) return false
    return key == other.key &&
      sourceIndex == other.sourceIndex &&
      kind == other.kind &&
      name == other.name &&
      timestamp == other.timestamp &&
      timestampDescription == other.timestampDescription &&
      unread == other.unread &&
      selected == other.selected
  }

  override fun hashCode(): Int {
    var result = key.hashCode()
    result = 31 * result + sourceIndex
    result = 31 * result + kind.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + timestamp.hashCode()
    result = 31 * result + timestampDescription.hashCode()
    result = 31 * result + unread.hashCode()
    result = 31 * result + selected.hashCode()
    return result
  }

  companion object {

    /**
     * Projects the paging data source's `List<Conversation>` onto the rows the Light list draws.
     *
     * The source list is *positional*: it is a `CompressedList` sized to the full result set, with
     * `null` at every index the paging controller has not loaded yet, and it carries synthetic
     * header/footer/empty rows alongside real threads. We therefore keep [sourceIndex] on every
     * row we emit, so that the paging controller can still be driven with data-source indices even
     * though the rendered list is shorter (see [LightConversationListState.pagingIndexFor]).
     *
     * Dropped entirely, because the Light design has no equivalent:
     * - `PINNED_HEADER` / `UNPINNED_HEADER` -- no section headers. Pinned chats still sort first,
     *   and are distinguished by having no timestamp (the reference client's convention).
     * - `CONVERSATION_FILTER_FOOTER` -- the pull-to-filter tip.
     * - the `*_EMPTY` rows -- surfaced as [LightConversationListState.emptyText] instead of a row.
     */
    @JvmStatic
    fun map(
      context: Context,
      conversations: List<Conversation?>,
      selection: ConversationSet,
      locale: Locale = Locale.getDefault(),
      nowMs: Long = System.currentTimeMillis()
    ): LightConversationListState {
      val rows = ArrayList<LightConversationListItem>(conversations.size)
      var emptyText: String? = null

      conversations.forEachIndexed { index, conversation ->
        if (conversation == null) {
          rows += LightConversationListItem(
            key = "ph:$index",
            sourceIndex = index,
            kind = Kind.PLACEHOLDER,
            name = "",
            timestamp = "",
            timestampDescription = "",
            unread = false,
            selected = false,
            conversation = null
          )
          return@forEachIndexed
        }

        val thread = conversation.threadRecord

        when (conversation.type) {
          Conversation.Type.THREAD -> {
            val recipient = thread.recipient
            rows += LightConversationListItem(
              key = thread.threadId,
              sourceIndex = index,
              kind = Kind.THREAD,
              name = if (recipient.isSelf) context.getString(R.string.note_to_self) else recipient.getDisplayName(context),
              // Pinned rows drop the time, matching the Light reference client: with no section
              // headers it is the only thing that sets a pinned chat apart from an unpinned one.
              timestamp = if (thread.isPinned) "" else LightRelativeTimestamp.format(context, thread.date, locale, nowMs = nowMs),
              timestampDescription = if (thread.isPinned) "" else LightRelativeTimestamp.describe(context, thread.date, locale),
              unread = !thread.isRead,
              selected = selection.containsThreadId(thread.threadId),
              conversation = conversation
            )
          }

          Conversation.Type.ARCHIVED_FOOTER -> {
            rows += LightConversationListItem(
              key = thread.threadId,
              sourceIndex = index,
              kind = Kind.ARCHIVE,
              name = context.getString(R.string.ConversationListItemAction_archived_conversations_d, thread.unreadCount),
              timestamp = "",
              timestampDescription = "",
              unread = false,
              selected = false,
              conversation = conversation
            )
          }

          Conversation.Type.EMPTY -> emptyText = context.getString(R.string.conversation_list_fragment__no_chats_yet_get_started_by_messaging_a_friend)
          Conversation.Type.ARCHIVED_EMPTY -> emptyText = context.getString(R.string.conversation_list_fragment__archived_chats_will_appear_here)
          Conversation.Type.CONVERSATION_FILTER_EMPTY -> emptyText = context.getString(R.string.ConversationListFragment__no_unread_chats)
          Conversation.Type.CHAT_FOLDER_EMPTY -> emptyText = context.getString(R.string.conversation_list_fragment__no_chats_to_display)

          Conversation.Type.PINNED_HEADER,
          Conversation.Type.UNPINNED_HEADER,
          Conversation.Type.CONVERSATION_FILTER_FOOTER -> Unit
        }
      }

      return LightConversationListState(rows = rows, emptyText = emptyText)
    }
  }
}

/**
 * Everything the Light conversation list screen renders, derived in one pass from the view model's
 * `Flowable<List<Conversation>>`.
 */
@Immutable
data class LightConversationListState(
  val rows: List<LightConversationListItem> = emptyList(),
  val emptyText: String? = null
) {

  /**
   * The data-source index the paging controller should be asked about when [renderedIndex] is the
   * last visible row. Returns -1 when there is nothing to ask for.
   */
  fun pagingIndexFor(renderedIndex: Int): Int = rows.getOrNull(renderedIndex)?.sourceIndex ?: -1
}
