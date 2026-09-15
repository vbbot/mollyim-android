/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.log.light

import android.content.Context
import androidx.compose.runtime.Immutable
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.calls.log.CallLogRow
import org.thoughtcrime.securesms.calls.log.CallLogSelectionState
import org.thoughtcrime.securesms.conversationlist.light.LightRelativeTimestamp
import org.thoughtcrime.securesms.database.CallTable
import org.thoughtcrime.securesms.recipients.RecipientId
import java.util.Locale

/**
 * One rendered row of the Light call log.
 *
 * Everything the row draws is resolved once, up front, off the composition -- [LightCallLogRow] only
 * ever reads plain [String]s and [Boolean]s, which is also what [com.thelightphone.sdk.ui.LightText]
 * accepts (it has no `AnnotatedString` overload). That is also why the search query's highlight span
 * is dropped: the Light list narrows as you type, which is the feedback that matters, and there is no
 * styled-text component in the SDK to carry the highlight itself.
 *
 * [equals]/[hashCode] cover only the *rendered* fields plus [sourceIndex]. The [row] back-reference
 * is carried so that a tap or a long press can be handed straight back to `CallLogFragment` (which
 * needs the full [CallLogRow] for navigation, selection and the action panel), but including it in
 * equality would recompose rows for changes that are invisible in the Light design -- a new peek
 * info, a changed avatar, a badge.
 */
@Immutable
class LightCallLogItem(
  /**
   * Stable LazyColumn key, and always a [String].
   *
   * [CallLogRow.Id] itself cannot be used: `Id.CallLink` wraps a `CallLinkRoomId`, which is not one
   * of the types Compose will put in a saved-instance-state bundle, and a lazy list key that cannot
   * be saved takes the list's scroll position down with it on a configuration change.
   */
  val key: String,
  /** Index of this row in the *paged data source*, which may include rows we do not render. */
  val sourceIndex: Int,
  val kind: Kind,
  val name: String,
  /** Short upper-case token telling direction and medium apart. See [detailFor]. May be empty. */
  val detail: String,
  val timestamp: String,
  val timestampDescription: String,
  /** Whether this call is one you missed, which is the row's one always-on marker. */
  val missed: Boolean,
  val selected: Boolean,
  val row: CallLogRow?
) {

  enum class Kind {
    /** A call event: the ordinary row. */
    CALL,

    /** A call link with no call events of its own. */
    CALL_LINK,

    /** The "Create a Call Link" row that sits at the head of an unfiltered log. */
    CREATE_CALL_LINK,

    /** A page the paging controller has not filled in yet. Renders as blank space of row height. */
    PLACEHOLDER
  }

  /** Whether a long press on this row has per-call actions to offer. */
  val hasActions: Boolean
    get() = kind == Kind.CALL || kind == Kind.CALL_LINK

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is LightCallLogItem) return false
    return key == other.key &&
      sourceIndex == other.sourceIndex &&
      kind == other.kind &&
      name == other.name &&
      detail == other.detail &&
      timestamp == other.timestamp &&
      timestampDescription == other.timestampDescription &&
      missed == other.missed &&
      selected == other.selected
  }

  override fun hashCode(): Int {
    var result = key.hashCode()
    result = 31 * result + sourceIndex
    result = 31 * result + kind.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + detail.hashCode()
    result = 31 * result + timestamp.hashCode()
    result = 31 * result + timestampDescription.hashCode()
    result = 31 * result + missed.hashCode()
    result = 31 * result + selected.hashCode()
    return result
  }

  companion object {

    /**
     * Projects the paging data source's `List<CallLogRow>` onto the rows the Light log draws.
     *
     * The source list is *positional*: it is sized to the full result set with `null` at every index
     * the paging controller has not loaded yet. Those holes are kept as [Kind.PLACEHOLDER] rows so
     * that [sourceIndex] stays the index the paging controller understands and the scrollbar's
     * geometry stays honest while a page loads (see [LightCallLogState.pagingIndexFor]).
     *
     * Dropped, because the Light design has no equivalent:
     * - [CallLogRow.ClearFilter] -- the "Clear filter" footer. The filter is already cleared from the
     *   pull view at the top of the list and from the overflow menu; a third way to do it is not
     *   worth a row that is not a call.
     * - [CallLogRow.ClearFilterEmpty] -- surfaced as [LightCallLogState.emptyTitle] instead of a row.
     */
    @JvmStatic
    fun map(
      context: Context,
      rows: List<CallLogRow?>,
      selection: CallLogSelectionState,
      localDeviceCallRecipientId: RecipientId?,
      locale: Locale = Locale.getDefault(),
      nowMs: Long = System.currentTimeMillis()
    ): LightCallLogState {
      val items = ArrayList<LightCallLogItem>(rows.size)
      var emptyTitle: String? = null
      var emptyBody: String? = null

      rows.forEachIndexed { index, row ->
        if (row == null) {
          items += LightCallLogItem(
            key = "ph:$index",
            sourceIndex = index,
            kind = Kind.PLACEHOLDER,
            name = "",
            detail = "",
            timestamp = "",
            timestampDescription = "",
            missed = false,
            selected = false,
            row = null
          )
          return@forEachIndexed
        }

        when (row) {
          is CallLogRow.Call -> {
            items += LightCallLogItem(
              key = "call:${row.record.callId}",
              sourceIndex = index,
              kind = Kind.CALL,
              name = row.peer.getDisplayName(context),
              detail = detailForCall(context, row, localDeviceCallRecipientId),
              timestamp = LightRelativeTimestamp.format(context, row.date, locale, nowMs = nowMs),
              timestampDescription = LightRelativeTimestamp.describe(context, row.date, locale),
              missed = row.record.isDisplayedAsMissedCallInUi,
              selected = selection.contains(row.id),
              row = row
            )
          }

          is CallLogRow.CallLink -> {
            items += LightCallLogItem(
              key = "link:${row.record.roomId.serialize()}",
              sourceIndex = index,
              kind = Kind.CALL_LINK,
              name = row.record.state.name.takeIf { it.isNotEmpty() } ?: context.getString(R.string.WebRtcCallView__signal_call),
              detail = callLinkDetail(context, row, localDeviceCallRecipientId),
              // A call link that has never been called has no date to show, and the Light row has no
              // second line to put "Call link" on -- the detail token says it instead.
              timestamp = "",
              timestampDescription = "",
              missed = false,
              selected = selection.contains(row.id),
              row = row
            )
          }

          is CallLogRow.CreateCallLink -> {
            items += LightCallLogItem(
              key = "create",
              sourceIndex = index,
              kind = Kind.CREATE_CALL_LINK,
              name = context.getString(R.string.CreateCallLink__create_a_call_link),
              detail = "",
              timestamp = "",
              timestampDescription = "",
              missed = false,
              selected = false,
              row = row
            )
          }

          is CallLogRow.ClearFilterEmpty -> emptyTitle = context.getString(R.string.CallLogAdapter__no_missed_calls)

          is CallLogRow.ClearFilter -> Unit
        }
      }

      if (emptyTitle == null && items.isEmpty()) {
        emptyTitle = context.getString(R.string.CallLogFragment__no_calls)
        emptyBody = context.getString(R.string.CallLogFragment__get_started_by_calling_a_friend)
      }

      return LightCallLogState(
        rows = items,
        emptyTitle = emptyTitle,
        emptyBody = emptyBody,
        selectionMode = selection.isNotEmpty(items.size)
      )
    }

    /**
     * The row's second column: one short upper-case token, or nothing at all.
     *
     * **Why a word and not an icon.** The Light Phone's own recents list marks a missed call with a
     * glyph in the row's leading slot and says nothing else -- no arrows, no camera badges. That is
     * the grammar this row keeps, and the missed glyph only *reads* as an exception because the other
     * rows leave the slot empty, exactly as the chat list's unread asterisk does. A direction glyph
     * on every row would spend that contrast. So direction and medium go where the reference puts
     * secondary detail instead: a `Fine`-variant token on the name's line, in the same clipped
     * upper-case register as the bottom bar's text items and the action panel's rows. A word also
     * beats a 13dp pictogram for legibility, which is the whole point of the exercise.
     *
     * The vocabulary, and nothing else:
     *
     * | row | token |
     * |---|---|
     * | missed voice call | *(empty -- the glyph already says it)* |
     * | missed video call | `VIDEO` |
     * | answered incoming / outgoing voice | `IN` / `OUT` |
     * | answered incoming / outgoing video | `VIDEO IN` / `VIDEO OUT` |
     * | group call | `GROUP` |
     * | call link | `LINK` |
     * | a call that is live right now | `JOIN`, or `RETURN` if you are already in it |
     *
     * `JOIN`/`RETURN` wins over everything else, and is what stands in for the Material row's inline
     * join button: the Light row has no buttons in it, but "there is a call happening in here right
     * now" is the one piece of state worth interrupting the vocabulary for. Both labels are Signal's
     * own, so they arrive translated.
     *
     * Several calls collapsed into one row keep Signal's own bracketed count, via Signal's own format
     * string, so the count is not lost: `(3) VIDEO IN`, or just `(3)` where the token is empty.
     */
    @JvmStatic
    fun detailFor(
      context: Context,
      type: CallTable.Type,
      direction: CallTable.Direction,
      missed: Boolean
    ): String {
      val token = when (type) {
        CallTable.Type.AD_HOC_CALL -> R.string.LightCallLog__link
        CallTable.Type.GROUP_CALL -> R.string.LightCallLog__group
        CallTable.Type.VIDEO_CALL -> when {
          missed -> R.string.LightCallLog__video
          direction == CallTable.Direction.INCOMING -> R.string.LightCallLog__video_in
          else -> R.string.LightCallLog__video_out
        }

        CallTable.Type.AUDIO_CALL -> when {
          missed -> return ""
          direction == CallTable.Direction.INCOMING -> R.string.LightCallLog__in
          else -> R.string.LightCallLog__out
        }
      }

      return context.getString(token)
    }

    private fun detailForCall(context: Context, call: CallLogRow.Call, localDeviceCallRecipientId: RecipientId?): String {
      liveCallToken(context, call, localDeviceCallRecipientId)?.let { return it }

      val detail = detailFor(
        context = context,
        type = call.record.type,
        direction = call.record.direction,
        missed = call.record.isDisplayedAsMissedCallInUi
      )

      if (call.children.size <= 1) {
        return detail
      }

      // "(3) VIDEO IN", Signal's own shape and its own format string. Trimmed because a missed voice
      // call contributes no token of its own and would otherwise leave a trailing space.
      return context.getString(R.string.CallLogAdapter__d_s, call.children.size, detail).trim()
    }

    private fun callLinkDetail(context: Context, callLink: CallLogRow.CallLink, localDeviceCallRecipientId: RecipientId?): String {
      if (callLink.callLinkPeekInfo?.isActive == true) {
        return joinToken(
          context = context,
          joined = callLink.callLinkPeekInfo.isJoined,
          isLocalDeviceInCall = callLink.recipient.id == localDeviceCallRecipientId
        )
      }

      return context.getString(R.string.LightCallLog__link)
    }

    /** `JOIN` / `RETURN` while a group call or a call link is live, matching the Material row's button. */
    private fun liveCallToken(context: Context, call: CallLogRow.Call, localDeviceCallRecipientId: RecipientId?): String? {
      val isLocalDeviceInCall = call.peer.id == localDeviceCallRecipientId

      return when (call.record.type) {
        CallTable.Type.AD_HOC_CALL -> {
          val peek = call.callLinkPeekInfo
          if (peek?.isActive == true) joinToken(context, peek.isJoined, isLocalDeviceInCall) else null
        }

        CallTable.Type.GROUP_CALL -> when (call.groupCallState) {
          CallLogRow.GroupCallState.ACTIVE -> joinToken(context, joined = false, isLocalDeviceInCall = isLocalDeviceInCall)
          CallLogRow.GroupCallState.LOCAL_USER_JOINED -> joinToken(context, joined = true, isLocalDeviceInCall = isLocalDeviceInCall)
          CallLogRow.GroupCallState.NONE, CallLogRow.GroupCallState.FULL -> null
        }

        CallTable.Type.AUDIO_CALL, CallTable.Type.VIDEO_CALL -> null
      }
    }

    private fun joinToken(context: Context, joined: Boolean, isLocalDeviceInCall: Boolean): String {
      val label = if (joined && isLocalDeviceInCall) R.string.CallLogAdapter__return else R.string.CallLogAdapter__join
      return context.getString(label).uppercase(Locale.getDefault())
    }
  }
}

/**
 * Everything the Light call log screen renders, derived in one pass from the view model's
 * `Flowable<List<CallLogRow>>`.
 */
@Immutable
data class LightCallLogState(
  val rows: List<LightCallLogItem> = emptyList(),
  val emptyTitle: String? = null,
  val emptyBody: String? = null,
  val selectionMode: Boolean = false
) {

  /**
   * The data-source index the paging controller should be asked about when [renderedIndex] is the
   * last visible row. Returns -1 when there is nothing to ask for.
   */
  fun pagingIndexFor(renderedIndex: Int): Int = rows.getOrNull(renderedIndex)?.sourceIndex ?: -1
}
