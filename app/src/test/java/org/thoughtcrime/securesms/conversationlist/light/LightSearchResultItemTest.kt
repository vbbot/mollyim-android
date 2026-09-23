/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversationlist.light

import android.app.Application
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.contacts.paged.ContactSearchData
import org.thoughtcrime.securesms.contacts.paged.light.LightContactItem
import org.thoughtcrime.securesms.conversationlist.ConversationListSearchModels
import org.thoughtcrime.securesms.database.model.ThreadWithRecipient
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.search.MessageResult

/**
 * What the search results' projection does with the three row kinds the contact picker never
 * produces: a conversation, a message hit, and the chat-filter row.
 *
 * The picker and the search share one row class and one projection on purpose (see
 * [LightSearchResultsView]), so the risk worth testing is the seam between them -- a row kind that
 * only one of the two hosts emits is exactly the kind that gets mapped wrong, or not at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LightSearchResultItemTest {

  private val context = RuntimeEnvironment.getApplication()

  /**
   * The decision this milestone had to make: a message hit carries a snippet, because the
   * conversation name alone cannot tell two hits in the same chat apart.
   */
  @Test
  fun `a message hit carries the conversation, the date and a snippet`() {
    val row = map(message(conversation = "Ada Lovelace", snippet = "about the analytical engine")).single()

    assertThat(row.kind).isEqualTo(LightContactItem.Kind.MESSAGE_HIT)
    assertThat(row.name).isEqualTo("Ada Lovelace")
    assertThat(row.snippet).isEqualTo("about the analytical engine")
    assertThat(row.detail).isNotEmpty()
    assertThat(row.clickable).isTrue()
  }

  /**
   * The row is named for the conversation the message is *in*, not its sender -- tapping it opens
   * that conversation, so naming it after the sender would promise something else. It matters in
   * groups, where the two differ.
   */
  @Test
  fun `a message hit in a group is named for the group, not the sender`() {
    val row = map(
      message(conversation = "Book Club", sender = "Ada Lovelace", snippet = "see you Thursday")
    ).single()

    assertThat(row.name).isEqualTo("Book Club")
  }

  /** Two hits in one conversation must not collide on a lazy list key. */
  @Test
  fun `two hits in the same conversation get two keys`() {
    val rows = map(
      message(conversation = "Ada Lovelace", messageId = 1L),
      message(conversation = "Ada Lovelace", messageId = 2L)
    )

    assertThat(rows.map { it.key }.toSet()).hasSize(2)
  }

  /**
   * A conversation hit is shaped like the Light chat list's own row -- name plus relative timestamp
   * -- rather than like the picker's, so that a chat found by searching looks like the chat you
   * scrolled past. It is emphatically not a member count, which is what the picker puts here.
   */
  @Test
  fun `a conversation hit shows a timestamp, not a member count`() {
    val row = map(thread(name = "Book Club", date = 1_600_000_000_000L)).single()

    assertThat(row.kind).isEqualTo(LightContactItem.Kind.CONTACT)
    assertThat(row.name).isEqualTo("Book Club")
    assertThat(row.detail).isNotEmpty()
    assertThat(row.detail.contains("MEMBER")).isFalse()
    assertThat(row.snippet).isEmpty()
  }

  /**
   * The crash this projection would otherwise have shipped.
   *
   * The conversation-list search puts its chat-filter rows through the same `Arbitrary` channel the
   * picker uses for "new group" and friends, but `ArbitraryRow.fromCode` is an `entries.first { }` --
   * it throws `NoSuchElementException` on a code it does not know. Searching with the unread filter
   * on would have taken the whole list down.
   */
  @Test
  fun `the chat filter row becomes a clear-filter action instead of throwing`() {
    val row = map(
      ContactSearchData.Arbitrary(ConversationListSearchModels.ChatFilterOptions.WITH_TIP.code)
    ).single()

    assertThat(row.kind).isEqualTo(LightContactItem.Kind.ACTION)
    assertThat(row.action).isEqualTo(LightContactItem.Action.CLEAR_CHAT_FILTER)
    assertThat(row.clickable).isTrue()
  }

  @Test
  fun `an unrecognised arbitrary row is blank space rather than a crash`() {
    val row = map(ContactSearchData.Arbitrary("something-invented-upstream")).single()

    assertThat(row.kind).isEqualTo(LightContactItem.Kind.PLACEHOLDER)
  }

  /**
   * Only the rendered fields take part in equality, so a message whose read state or delivery
   * receipt changed underneath does not recompose a row that looks identical.
   */
  @Test
  fun `equality covers the snippet`() {
    val a = map(message(snippet = "one")).single()
    val b = map(message(snippet = "two")).single()

    assertThat(a).isEqualTo(a)
    assertThat(a == b).isFalse()
  }

  /** The scrollbar needs to know, once, whether the list has any two-line rows in it. */
  @Test
  fun `a list containing a hit reports that it has snippets`() {
    val withHit = LightContactItem.map(context, listOf(message()), emptySet())
    val withoutHit = LightContactItem.map(context, listOf(thread()), emptySet())

    assertThat(withHit.hasSnippets).isTrue()
    assertThat(withoutHit.hasSnippets).isFalse()
  }

  /** Section labels are what keep three concatenated lists from reading as one. */
  @Test
  fun `the messages section is labelled`() {
    val row = map(
      ContactSearchData.Header(
        org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration.SectionKey.MESSAGES,
        null
      )
    ).single()

    assertThat(row.kind).isEqualTo(LightContactItem.Kind.HEADER)
    assertThat(row.name).isNotEmpty()
    // Upper-cased into the Light register, like every other label in the port.
    assertThat(row.name).isEqualTo(row.name.uppercase())
  }

  @Test
  fun `no results is a message row that cannot be tapped`() {
    val row = map(ContactSearchData.Empty("zzz")).single()

    assertThat(row.kind).isEqualTo(LightContactItem.Kind.MESSAGE)
    assertThat(row.clickable).isFalse()
    assertThat(row.name).contains("zzz")
  }

  private fun map(vararg data: ContactSearchData?): List<LightContactItem> =
    LightContactItem.map(context, data.toList(), emptySet()).rows

  private fun message(
    conversation: String = "Ada Lovelace",
    sender: String = conversation,
    snippet: String = "about the analytical engine",
    messageId: Long = 7L
  ) = ContactSearchData.Message(
    query = "engine",
    messageResult = MessageResult(
      conversationRecipient = recipient(conversation, 1L),
      messageRecipient = recipient(sender, 2L),
      body = snippet,
      bodySnippet = snippet,
      threadId = 3L,
      messageId = messageId,
      receivedTimestampMs = 1_600_000_000_000L,
      isMms = false
    )
  )

  private fun thread(name: String = "Book Club", date: Long = 1_600_000_000_000L) = ContactSearchData.Thread(
    query = "book",
    threadWithRecipient = ThreadWithRecipient.Builder(3L)
      .setRecipient(recipient(name, 4L))
      .setDate(date)
      .build()
  )

  private fun recipient(name: String, id: Long): Recipient = mockk {
    every { getDisplayName(any()) } returns name
    every { isGroup } returns false
    every { isSelf } returns false
    every { this@mockk.id } returns RecipientId.from(id)
  }
}
