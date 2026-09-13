/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.paged.light

import android.content.Context
import androidx.compose.runtime.Immutable
import org.thoughtcrime.securesms.ContactSelectionListModels
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.paged.ChatType
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration
import org.thoughtcrime.securesms.contacts.paged.ContactSearchData
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.conversationlist.ConversationListSearchModels
import org.thoughtcrime.securesms.conversationlist.light.LightRelativeTimestamp
import org.thoughtcrime.securesms.recipients.Recipient
import java.util.Locale

/**
 * One rendered row of the Light contact picker.
 *
 * Everything the row draws is resolved once, up front, off the composition -- [LightContactRow] only
 * ever reads plain [String]s and [Boolean]s, which is also what [com.thelightphone.sdk.ui.LightText]
 * accepts (it has no `AnnotatedString` overload). That is also why the search query's highlight span
 * is dropped: the list narrows as you type, which is the feedback that matters, and the SDK has no
 * styled-text component to carry a highlight.
 *
 * [equals]/[hashCode] cover only the *rendered* fields plus [sourceIndex]. The [data]
 * back-reference is carried so that a tap can be handed straight back to `ContactSelectionListFragment`
 * (which needs the full [ContactSearchData] for the selection, limit and navigation logic that is
 * deliberately left alone), but including it in equality would recompose rows for changes that are
 * invisible in the Light design -- a new avatar, a badge, an "about" line, a profile refresh.
 * [ContactSearchData.Header], [ContactSearchData.Expand] and [ContactSearchData.Arbitrary] are plain
 * classes with identity equality, so including them would in fact recompose *every* such row on
 * every emission.
 */
@Immutable
class LightContactItem(
  /**
   * Stable LazyColumn key, and always a [String].
   *
   * Derived from the row's [ContactSearchKey] rather than being the key itself: `RecipientSearchKey`
   * wraps a `RecipientId`, which is not one of the types Compose will put in a saved-instance-state
   * bundle, and a lazy list key that cannot be saved takes the list's scroll position down with it
   * on a configuration change.
   */
  val key: String,
  /** Index of this row in the *paged data source*, which may include rows we do not render. */
  val sourceIndex: Int,
  val kind: Kind,
  val name: String,
  /** Right-aligned secondary text. See [detailFor] for what goes here and why. May be empty. */
  val detail: String,
  /**
   * A second line, drawn under [name] in `Superfine`. Empty on every row kind but a message hit --
   * see [Kind.MESSAGE_HIT] for why that one row is allowed to be two lines tall.
   */
  val snippet: String = "",
  val selected: Boolean,
  /**
   * Whether this row can be turned on and off. False for the fixed contacts a screen was opened
   * with -- existing group members, say -- which Signal shows permanently checked and inert.
   */
  val enabled: Boolean,
  /** Non-null for the rows that are not contacts: the extra actions and the expand affordance. */
  val action: Action?,
  val data: ContactSearchData?
) {

  enum class Kind {
    /** A recipient, a chat type, or a typed-in phone number: the ordinary, selectable row. */
    CONTACT,

    /**
     * A single matching *message*, from the search results' MESSAGES section. Two lines: the
     * conversation's name and the hit's date on the first, a snippet of the message on the second.
     *
     * It is the one row in the Light port that is allowed a second line, and it earns it. Every
     * other list here answers "which of these do you want?", where a name is the whole answer. A
     * message search answers "where did we talk about this?", and a name alone cannot: search
     * "pizza" and three hits in the same chat produce three rows that differ only by a date. The
     * snippet is the only thing on the row that says which message was found.
     *
     * The shape is not invented -- it is the Light reference client's own two-line row
     * (`chats/.../ContactsScreen.kt:ContactRow`, `Heading` over `Superfine`, both clipped to one
     * line), which that client uses for exactly the same reason: a row whose primary text does not
     * identify it on its own. It is *not* Signal's row: no avatar, no sender name, no delivery
     * state, and the highlight span on the matched term is dropped, because `LightText` takes a
     * `String` and the SDK has no styled-text component to carry one.
     */
    MESSAGE_HIT,

    /** A section label. Not clickable. */
    HEADER,

    /** "New group", "Invite to Signal", "View more" -- a row that does something but selects nothing. */
    ACTION,

    /** A line of centred copy inside the list, which is how Signal renders "no results". */
    MESSAGE,

    /** A page the paging controller has not filled in yet. Renders as blank space of row height. */
    PLACEHOLDER
  }

  /**
   * The rows that are not recipients. Carried as an enum rather than a lambda so that the item stays
   * [Immutable] and comparable, and so that the [View][LightContactSearchView] layer -- which is the
   * only thing that knows Molly's callbacks -- does the dispatching.
   */
  enum class Action {
    NEW_GROUP,
    INVITE_TO_SIGNAL,
    FIND_CONTACTS,
    DISMISS_FIND_CONTACTS_BANNER,
    REFRESH_CONTACTS,
    FIND_BY_USERNAME,
    FIND_BY_PHONE_NUMBER,

    /** "View more" at the foot of a truncated section. Needs [data] for the section key. */
    EXPAND,

    /**
     * "Clear filter", from the chat-filter row the conversation-list search shows when you search
     * while the unread filter is on. Only the search host produces it; the picker never does.
     */
    CLEAR_CHAT_FILTER
  }

  /** Whether tapping this row does anything at all. */
  val clickable: Boolean
    get() = (kind == Kind.CONTACT && enabled) || kind == Kind.ACTION || kind == Kind.MESSAGE_HIT

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is LightContactItem) return false
    return key == other.key &&
      sourceIndex == other.sourceIndex &&
      kind == other.kind &&
      name == other.name &&
      detail == other.detail &&
      snippet == other.snippet &&
      selected == other.selected &&
      enabled == other.enabled &&
      action == other.action
  }

  override fun hashCode(): Int {
    var result = key.hashCode()
    result = 31 * result + sourceIndex
    result = 31 * result + kind.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + detail.hashCode()
    result = 31 * result + snippet.hashCode()
    result = 31 * result + selected.hashCode()
    result = 31 * result + enabled.hashCode()
    result = 31 * result + (action?.hashCode() ?: 0)
    return result
  }

  companion object {

    /**
     * Projects the view model's `List<ContactSearchData>` onto the rows the Light picker draws.
     *
     * The source list is *positional*: it is sized to the full result set with `null` at every index
     * the paging controller has not loaded yet (`ContactSearchModels.toMappingModelList` drops those
     * with `filterNotNull`, which is why the Material list can do without them). Those holes are kept
     * here as [Kind.PLACEHOLDER] rows so that [sourceIndex] stays the index the paging controller
     * understands and the scrollbar's geometry stays honest while a page loads.
     *
     * @param fixedContacts the keys the screen was opened with, which are shown checked and inert.
     * @param displayCheckBox whether this screen selects at all. Straight from
     *   `ContactSearchAdapter.DisplayOptions.displayCheckBox`, which `ContactSelectionListFragment`
     *   sets from `isMulti`.
     */
    @JvmStatic
    fun map(
      context: Context,
      data: List<ContactSearchData?>,
      selection: Set<ContactSearchKey>,
      fixedContacts: Set<ContactSearchKey> = emptySet(),
      displayCheckBox: Boolean = false
    ): LightContactState {
      val rows = ArrayList<LightContactItem>(data.size)

      data.forEachIndexed { index, row ->
        if (row == null) {
          rows += placeholder(index)
          return@forEachIndexed
        }

        when (row) {
          is ContactSearchData.KnownRecipient -> rows += recipientRow(
            index = index,
            data = row,
            recipient = row.recipient,
            name = displayName(context, row.recipient, row.showSelfAsYou),
            detail = detailFor(context, row.recipient),
            selection = selection,
            fixedContacts = fixedContacts,
            displayCheckBox = displayCheckBox
          )

          // Stories are out of scope for the picker and `ContactSelectionListFragment` never
          // configures a stories section, but the row is mapped rather than dropped so that a host
          // which does configure one gets a name instead of a silently missing row.
          is ContactSearchData.Story -> rows += recipientRow(
            index = index,
            data = row,
            recipient = row.recipient,
            name = row.recipient.getDisplayName(context),
            detail = "",
            selection = selection,
            fixedContacts = fixedContacts,
            displayCheckBox = displayCheckBox
          )

          // A conversation, from the search results' CHATS section. Deliberately shaped like the
          // Light chat list's own row -- name plus relative timestamp -- rather than like the
          // picker's, so that a chat you found by searching looks like the same chat you scrolled
          // past. That costs a group its member count here, but the chat list does not show one
          // either, so the two stay consistent.
          is ContactSearchData.Thread -> rows += recipientRow(
            index = index,
            data = row,
            recipient = row.threadWithRecipient.recipient,
            name = row.threadWithRecipient.recipient.getDisplayName(context),
            detail = LightRelativeTimestamp.format(context, row.threadWithRecipient.date),
            selection = selection,
            fixedContacts = fixedContacts,
            displayCheckBox = displayCheckBox
          )

          is ContactSearchData.GroupWithMembers -> rows += LightContactItem(
            key = "group-with-members:${row.groupRecord.id}",
            sourceIndex = index,
            kind = Kind.CONTACT,
            name = row.groupRecord.title.orEmpty(),
            detail = memberCount(context, row.groupRecord.members.size),
            selected = false,
            enabled = true,
            action = null,
            data = row
          )

          is ContactSearchData.UnknownRecipient -> rows += unknownRecipientRow(context, index, row, selection)

          is ContactSearchData.ChatTypeRow -> rows += LightContactItem(
            key = "chat-type:${row.chatType}",
            sourceIndex = index,
            kind = Kind.CONTACT,
            name = chatTypeName(context, row.chatType),
            detail = "",
            selected = selection.contains(row.contactSearchKey),
            enabled = true,
            action = null,
            data = row
          )

          is ContactSearchData.Header -> rows += LightContactItem(
            key = "header:${row.sectionKey}",
            sourceIndex = index,
            kind = Kind.HEADER,
            name = headerName(context, row.sectionKey),
            detail = "",
            selected = false,
            enabled = false,
            action = null,
            data = row
          )

          is ContactSearchData.Expand -> rows += LightContactItem(
            key = "expand:${row.sectionKey}",
            sourceIndex = index,
            kind = Kind.ACTION,
            name = context.getString(R.string.ExpandModel__view_more).uppercase(Locale.getDefault()),
            detail = "",
            selected = false,
            enabled = true,
            action = Action.EXPAND,
            data = row
          )

          is ContactSearchData.Arbitrary -> rows += arbitraryRows(context, index, row)

          // Signal renders this as an in-list line of copy rather than a whole-screen state,
          // because the "More" rows below it -- refresh, invite -- are part of the same empty state
          // and have to stay reachable. Kept as a row for exactly that reason.
          is ContactSearchData.Empty -> rows += LightContactItem(
            key = "empty",
            sourceIndex = index,
            kind = Kind.MESSAGE,
            name = if (row.query.isNullOrEmpty()) {
              context.getString(R.string.SearchFragment_no_results_empty)
            } else {
              context.getString(R.string.SearchFragment_no_results, row.query)
            },
            detail = "",
            selected = false,
            enabled = false,
            action = null,
            data = row
          )

          is ContactSearchData.Message -> rows += messageRow(context, index, row)

          is ContactSearchData.TestRow -> rows += placeholder(index)
        }
      }

      return LightContactState(
        rows = rows,
        selectionMode = displayCheckBox,
        hasSnippets = rows.any { it.snippet.isNotEmpty() }
      )
    }

    /**
     * The row's right-hand column: **the group's member count, and nothing else.**
     *
     * Only one fact fits here -- the Light row is a single line of `Heading` with a `Fine` token on
     * its right, as the chat list and the call log established -- so the question is which one earns
     * it. It is the member count, for three reasons.
     *
     * **It says "this row is a group".** That is the fact the Light picker most needs and has least
     * of: Signal told groups from people with the avatar, and the Light design has no avatars. A
     * name cannot do it -- "Book Club" and "Bookkeeper" read alike -- and without it you cannot tell
     * what tapping a row is about to start.
     *
     * **The phone number is deliberately not used**, even though it is the obvious disambiguator and
     * what the Light reference client's own contacts list shows. Two reasons. Signal itself does not
     * show it on this screen: with `DisplaySecondaryInformation.ALWAYS` the number branch of
     * `ContactSearchModels.KnownRecipientViewHolder.bindNumberField` sets the field *invisible*, so
     * dropping it is not a regression. And it does not fit: an E164 is about fourteen characters,
     * which at the `Fine` scale costs roughly ten of the twenty-two grid units a row has left after
     * the marker slot and the scrollbar gutter, and would ellipsize ordinary names to about eleven
     * characters on every row in the list to serve the rare pair of duplicates. Search, which this
     * screen puts front and centre, is the better answer to two people with one name.
     *
     * Upper-cased at the display edge, in the same clipped register as the call log's direction
     * token, the bottom bar's text items and the action panel's rows. Locale-sensitive: an invariant
     * upper case mangles Turkish.
     */
    @JvmStatic
    fun detailFor(context: Context, recipient: Recipient): String {
      if (!recipient.isGroup) {
        return ""
      }

      return memberCount(context, recipient.participantIds.size)
    }

    private fun memberCount(context: Context, count: Int): String {
      return context.resources
        .getQuantityString(R.plurals.ContactSearchItems__group_d_members, count, count)
        .uppercase(Locale.getDefault())
    }

    /**
     * A single matching message: the conversation it is in, when it was sent, and a snippet of it.
     *
     * `conversationRecipient` rather than `messageRecipient` is the name: the question a message
     * search asks is *where* a thing was said, and tapping the row opens the conversation, not the
     * sender. In a group that means the row reads as the group's name with the matched text under
     * it, which is what the thread it opens will show.
     *
     * `bodySnippet` is already the trimmed, centred-on-the-match extract `SearchRepository` built --
     * we take its plain text and drop the highlight span, because `LightText` has no styled-text
     * overload to carry one.
     */
    private fun messageRow(context: Context, index: Int, row: ContactSearchData.Message): LightContactItem {
      val result = row.messageResult

      return LightContactItem(
        key = "message:${result.messageId}",
        sourceIndex = index,
        kind = Kind.MESSAGE_HIT,
        name = result.conversationRecipient.getDisplayName(context),
        detail = LightRelativeTimestamp.format(context, result.receivedTimestampMs),
        snippet = result.bodySnippet.toString(),
        selected = false,
        enabled = true,
        action = null,
        data = row
      )
    }

    private fun placeholder(index: Int) = LightContactItem(
      key = "ph:$index",
      sourceIndex = index,
      kind = Kind.PLACEHOLDER,
      name = "",
      detail = "",
      selected = false,
      enabled = false,
      action = null,
      data = null
    )

    private fun recipientRow(
      index: Int,
      data: ContactSearchData,
      recipient: Recipient,
      name: String,
      detail: String,
      selection: Set<ContactSearchKey>,
      fixedContacts: Set<ContactSearchKey>,
      displayCheckBox: Boolean
    ): LightContactItem {
      val key = data.contactSearchKey
      val fixed = fixedContacts.contains(key)

      return LightContactItem(
        // The section is part of the key because the same recipient legitimately appears twice --
        // once under "Recent chats" and again under "Contacts" -- and two rows sharing a lazy list
        // key is a crash, not a glitch.
        key = "recipient:${sectionOf(data)}:${recipient.id.toLong()}",
        sourceIndex = index,
        kind = Kind.CONTACT,
        name = name,
        detail = detail,
        // Signal shows a fixed contact permanently checked, whether or not it is in the live
        // selection set, because it is already a member of whatever is being built.
        selected = fixed || selection.contains(key),
        enabled = !fixed,
        action = null,
        data = data
      )
    }

    private fun unknownRecipientRow(
      context: Context,
      index: Int,
      row: ContactSearchData.UnknownRecipient,
      selection: Set<ContactSearchKey>
    ): LightContactItem {
      // Signal's own shape: every mode but NEW_CONVERSATION puts a fixed label on the name line and
      // the typed query underneath, and NEW_CONVERSATION shows the query itself as the name.
      val label = when (row.mode) {
        ContactSearchConfiguration.NewRowMode.NEW_CALL -> R.string.contact_selection_list__new_call
        ContactSearchConfiguration.NewRowMode.BLOCK -> R.string.contact_selection_list__unknown_contact_block
        ContactSearchConfiguration.NewRowMode.ADD_TO_GROUP -> R.string.contact_selection_list__unknown_contact_add_to_group
        ContactSearchConfiguration.NewRowMode.NEW_CONVERSATION -> null
      }

      return LightContactItem(
        key = "unknown:${row.sectionKey}:${row.mode}",
        sourceIndex = index,
        kind = Kind.CONTACT,
        name = label?.let { context.getString(it) } ?: row.query,
        // The one place a phone number does appear on a Light row, because here it *is* the row:
        // there is no contact behind it yet, only what was typed.
        detail = if (label != null) row.query else "",
        selected = selection.contains(row.contactSearchKey),
        enabled = true,
        action = null,
        data = row
      )
    }

    /**
     * The extra rows Molly hangs off the picker: new group, invite, find-by, refresh.
     *
     * Returns a list because the contacts-permission banner is two actions -- "Allow access" and "No
     * thanks" -- and a Light row carries one. Signal drew it as a card with two buttons; here it
     * becomes the two rows it always was, so that the banner can still be dismissed and the screen
     * does not grow a second kind of row to hold buttons.
     */
    private fun arbitraryRows(context: Context, index: Int, row: ContactSearchData.Arbitrary): List<LightContactItem> {
      fun action(suffix: String, labelRes: Int, action: Action) = LightContactItem(
        key = "arbitrary:${row.type}:$suffix",
        sourceIndex = index,
        kind = Kind.ACTION,
        name = context.getString(labelRes).uppercase(Locale.getDefault()),
        detail = "",
        selected = false,
        enabled = true,
        action = action,
        data = row
      )

      // The conversation-list search puts its own codes through this same Arbitrary channel, and
      // `ArbitraryRow.fromCode` is an `entries.first { }` -- it throws NoSuchElementException on
      // anything it does not know, which would take the whole list down the moment you searched with
      // the unread filter on. Matched before the picker's own rows for that reason, and anything
      // still unrecognised becomes blank space rather than a crash.
      val chatFilter = ConversationListSearchModels.ChatFilterOptions.entries.firstOrNull { it.code == row.type }
      if (chatFilter != null) {
        return listOf(
          action("", R.string.ConversationListFragment__clear_filter, Action.CLEAR_CHAT_FILTER)
        )
      }

      if (ContactSelectionListModels.ArbitraryRow.entries.none { it.code == row.type }) {
        return listOf(placeholder(index))
      }

      return when (ContactSelectionListModels.ArbitraryRow.fromCode(row.type)) {
        ContactSelectionListModels.ArbitraryRow.NEW_GROUP ->
          listOf(action("", R.string.contact_selection_activity__new_group, Action.NEW_GROUP))

        ContactSelectionListModels.ArbitraryRow.INVITE_TO_SIGNAL ->
          listOf(action("", R.string.contact_selection_activity__invite_to_signal, Action.INVITE_TO_SIGNAL))

        ContactSelectionListModels.ArbitraryRow.FIND_CONTACTS ->
          listOf(action("", R.string.contact_selection_activity__allow_access_to_contacts, Action.FIND_CONTACTS))

        ContactSelectionListModels.ArbitraryRow.REFRESH_CONTACTS ->
          listOf(action("", R.string.contact_selection_activity__refresh_contacts, Action.REFRESH_CONTACTS))

        ContactSelectionListModels.ArbitraryRow.FIND_BY_USERNAME ->
          listOf(action("", R.string.ContactSelectionListFragment__find_by_username, Action.FIND_BY_USERNAME))

        ContactSelectionListModels.ArbitraryRow.FIND_BY_PHONE_NUMBER ->
          listOf(action("", R.string.ContactSelectionListFragment__find_by_phone_number, Action.FIND_BY_PHONE_NUMBER))

        ContactSelectionListModels.ArbitraryRow.FIND_CONTACTS_BANNER -> listOf(
          action("allow", R.string.ContactSelectionListFragment__allow_access, Action.FIND_CONTACTS),
          action("dismiss", R.string.ContactSelectionListFragment__no_thanks, Action.DISMISS_FIND_CONTACTS_BANNER)
        )

        ContactSelectionListModels.ArbitraryRow.MORE_HEADING -> listOf(
          LightContactItem(
            key = "arbitrary:${row.type}",
            sourceIndex = index,
            kind = Kind.HEADER,
            name = context.getString(R.string.contact_selection_activity__more).uppercase(Locale.getDefault()),
            detail = "",
            selected = false,
            enabled = false,
            action = null,
            data = row
          )
        )
      }
    }

    private fun displayName(context: Context, recipient: Recipient, showSelfAsYou: Boolean): String {
      return if (showSelfAsYou && recipient.isSelf) {
        context.getString(R.string.Recipient_you)
      } else {
        recipient.getDisplayName(context)
      }
    }

    private fun sectionOf(data: ContactSearchData): String = when (data) {
      is ContactSearchData.KnownRecipient -> data.sectionKey.name
      is ContactSearchData.Story -> ContactSearchConfiguration.SectionKey.STORIES.name
      is ContactSearchData.Thread -> ContactSearchConfiguration.SectionKey.CHATS.name
      else -> "OTHER"
    }

    private fun chatTypeName(context: Context, chatType: ChatType): String = when (chatType) {
      ChatType.INDIVIDUAL -> context.getString(R.string.ChatFoldersFragment__one_on_one_chats)
      ChatType.GROUPS -> context.getString(R.string.ChatFoldersFragment__groups)
    }

    /**
     * Section labels, upper-cased into the Light register. Signal's own strings, so they arrive
     * translated.
     */
    private fun headerName(context: Context, sectionKey: ContactSearchConfiguration.SectionKey): String {
      val label = when (sectionKey) {
        ContactSearchConfiguration.SectionKey.STORIES -> R.string.ContactsCursorLoader_my_stories
        ContactSearchConfiguration.SectionKey.RECENTS -> R.string.ContactsCursorLoader_recent_chats
        ContactSearchConfiguration.SectionKey.INDIVIDUALS -> R.string.ContactsCursorLoader_contacts
        ContactSearchConfiguration.SectionKey.GROUPS -> R.string.ContactsCursorLoader_groups
        ContactSearchConfiguration.SectionKey.GROUP_MEMBERS -> R.string.ContactsCursorLoader_group_members
        ContactSearchConfiguration.SectionKey.CHATS -> R.string.ContactsCursorLoader__chats
        ContactSearchConfiguration.SectionKey.MESSAGES -> R.string.ContactsCursorLoader__messages
        ContactSearchConfiguration.SectionKey.GROUPS_WITH_MEMBERS -> R.string.ContactsCursorLoader_group_members
        ContactSearchConfiguration.SectionKey.CONTACTS_WITHOUT_THREADS -> R.string.ContactsCursorLoader_contacts
        ContactSearchConfiguration.SectionKey.CHAT_TYPES -> R.string.ContactsCursorLoader__chat_types
        ContactSearchConfiguration.SectionKey.PHONE_NUMBER -> R.string.FindByActivity__find_by_phone_number
        ContactSearchConfiguration.SectionKey.USERNAME -> R.string.FindByActivity__find_by_username
        ContactSearchConfiguration.SectionKey.EMPTY,
        ContactSearchConfiguration.SectionKey.ARBITRARY -> return ""
      }

      return context.getString(label).uppercase(Locale.getDefault())
    }
  }
}

/**
 * Everything the Light contact picker renders, derived in one pass from the view model's
 * `StateFlow<List<ContactSearchData>>` and its selection set.
 */
@Immutable
data class LightContactState(
  val rows: List<LightContactItem> = emptyList(),
  val selectionMode: Boolean = false,
  /**
   * Whether any row is two lines tall, which is the only thing the list's scrollbar needs to know
   * about message hits. Computed once here rather than scanned in the composition.
   */
  val hasSnippets: Boolean = false
) {

  /**
   * The data-source index the paging controller should be asked about when [renderedIndex] is
   * visible. Returns -1 when there is nothing to ask for.
   */
  fun pagingIndexFor(renderedIndex: Int): Int = rows.getOrNull(renderedIndex)?.sourceIndex ?: -1
}
