/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.paged.light

import android.app.Application
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.hasSize
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.ContactSelectionListModels.ArbitraryRow
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration
import org.thoughtcrime.securesms.contacts.paged.ContactSearchData
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * The Light contact picker's row vocabulary, and what the projection does with the rows that are not
 * contacts.
 *
 * The vocabulary is the thing worth pinning. The Material row carried an avatar, a checkbox, a
 * two-line layout, an "about" line and a pair of call buttons; the Light row is a name, a marker slot
 * and one short token, so every one of those had to be decided somewhere, and this is the record of
 * where each landed. See [LightContactItem.detailFor] for why the token is a member count and not a
 * phone number.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LightContactItemTest {

  private val context = RuntimeEnvironment.getApplication()

  @Test
  fun `a group says how many people are in it`() {
    val rows = map(known(group(participants = 3)))

    assertThat(rows.single().detail).isEqualTo("3 MEMBERS")
  }

  /**
   * The one-person case exists because the plural string has a `one` form in English, and a row
   * reading "1 MEMBERS" is the kind of thing that only ever gets noticed on the device.
   */
  @Test
  fun `a one-person group uses the singular`() {
    val rows = map(known(group(participants = 1)))

    assertThat(rows.single().detail).isEqualTo("1 MEMBER")
  }

  /**
   * Deliberate, and the decision most likely to be second-guessed: an individual's row says nothing
   * on the right. Signal's own adapter hides the number here too.
   */
  @Test
  fun `an individual says nothing, because the number is not shown here`() {
    val rows = map(known(individual()))

    assertThat(rows.single().detail).isEmpty()
  }

  @Test
  fun `a contact is a contact row and nothing else`() {
    val row = map(known(individual(name = "Ada Lovelace"))).single()

    assertThat(row.kind).isEqualTo(LightContactItem.Kind.CONTACT)
    assertThat(row.name).isEqualTo("Ada Lovelace")
    assertThat(row.action).isNull()
    assertThat(row.clickable).isTrue()
  }

  /** Holes in the paged source have to survive as rows, or the paging indices stop lining up. */
  @Test
  fun `an unloaded page keeps its place and its source index`() {
    val rows = map(null, null, known(individual()))

    assertThat(rows.map { it.kind }).containsExactly(
      LightContactItem.Kind.PLACEHOLDER,
      LightContactItem.Kind.PLACEHOLDER,
      LightContactItem.Kind.CONTACT
    )
    assertThat(rows.map { it.sourceIndex }).containsExactly(0, 1, 2)
    assertThat(rows[0].clickable).isFalse()
  }

  @Test
  fun `the source index survives a row that expands into two`() {
    val rows = map(
      ContactSearchData.Arbitrary(ArbitraryRow.FIND_CONTACTS_BANNER.code),
      known(individual())
    )

    // Three rendered rows over two source rows: the contact is still source index 1.
    assertThat(rows).hasSize(3)
    assertThat(rows.map { it.sourceIndex }).containsExactly(0, 0, 1)

    val state = LightContactItem.map(context, listOf<ContactSearchData?>(null, null), emptySet())
    assertThat(state.pagingIndexFor(1)).isEqualTo(1)
    assertThat(state.pagingIndexFor(99)).isEqualTo(-1)
  }

  /**
   * The permission banner was a card with two buttons. A Light row carries one action, so it becomes
   * the two rows it always was -- and the important half is that the *dismiss* survives, because
   * without it the banner could never be got rid of.
   */
  @Test
  fun `the contacts permission banner becomes an allow row and a dismiss row`() {
    val rows = map(ContactSearchData.Arbitrary(ArbitraryRow.FIND_CONTACTS_BANNER.code))

    assertThat(rows.map { it.action }).containsExactly(
      LightContactItem.Action.FIND_CONTACTS,
      LightContactItem.Action.DISMISS_FIND_CONTACTS_BANNER
    )
    assertThat(rows.map { it.key }.toSet()).hasSize(2)
  }

  @Test
  fun `every extra row keeps its action`() {
    fun actionOf(row: ArbitraryRow) = map(ContactSearchData.Arbitrary(row.code)).single().action

    assertThat(actionOf(ArbitraryRow.NEW_GROUP)).isEqualTo(LightContactItem.Action.NEW_GROUP)
    assertThat(actionOf(ArbitraryRow.INVITE_TO_SIGNAL)).isEqualTo(LightContactItem.Action.INVITE_TO_SIGNAL)
    assertThat(actionOf(ArbitraryRow.FIND_CONTACTS)).isEqualTo(LightContactItem.Action.FIND_CONTACTS)
    assertThat(actionOf(ArbitraryRow.REFRESH_CONTACTS)).isEqualTo(LightContactItem.Action.REFRESH_CONTACTS)
    assertThat(actionOf(ArbitraryRow.FIND_BY_USERNAME)).isEqualTo(LightContactItem.Action.FIND_BY_USERNAME)
    assertThat(actionOf(ArbitraryRow.FIND_BY_PHONE_NUMBER)).isEqualTo(LightContactItem.Action.FIND_BY_PHONE_NUMBER)
  }

  /** "More" is a heading, not something you can tap, even though it arrives as an arbitrary row. */
  @Test
  fun `the More row is a heading`() {
    val row = map(ContactSearchData.Arbitrary(ArbitraryRow.MORE_HEADING.code)).single()

    assertThat(row.kind).isEqualTo(LightContactItem.Kind.HEADER)
    assertThat(row.clickable).isFalse()
    assertThat(row.action).isNull()
  }

  @Test
  fun `section labels are upper case and not clickable`() {
    val row = map(ContactSearchData.Header(ContactSearchConfiguration.SectionKey.RECENTS, null)).single()

    assertThat(row.kind).isEqualTo(LightContactItem.Kind.HEADER)
    assertThat(row.name).isEqualTo(row.name.uppercase())
    assertThat(row.clickable).isFalse()
  }

  @Test
  fun `view more is an action that carries its section back`() {
    val row = map(ContactSearchData.Expand(ContactSearchConfiguration.SectionKey.GROUPS)).single()

    assertThat(row.action).isEqualTo(LightContactItem.Action.EXPAND)
    assertThat((row.data as ContactSearchData.Expand).sectionKey)
      .isEqualTo(ContactSearchConfiguration.SectionKey.GROUPS)
  }

  /**
   * "No results" stays a row rather than becoming a whole-screen state, because Signal puts the
   * refresh and invite actions *below* it in the same empty state and they have to stay reachable.
   */
  @Test
  fun `no results is a message row, not a screen`() {
    val row = map(ContactSearchData.Empty("zzz")).single()

    assertThat(row.kind).isEqualTo(LightContactItem.Kind.MESSAGE)
    assertThat(row.clickable).isFalse()
  }

  @Test
  fun `a typed phone number offers itself, with what was typed underneath`() {
    val row = map(
      ContactSearchData.UnknownRecipient(
        ContactSearchConfiguration.SectionKey.PHONE_NUMBER,
        ContactSearchConfiguration.NewRowMode.NEW_CALL,
        "+15551234567"
      )
    ).single()

    assertThat(row.kind).isEqualTo(LightContactItem.Kind.CONTACT)
    assertThat(row.detail).isEqualTo("+15551234567")
    assertThat(row.name).isNotEqualTo("+15551234567")
  }

  /** In the new-conversation mode there is no label, so the number itself is the name. */
  @Test
  fun `starting a conversation with a typed number shows the number as the name`() {
    val row = map(
      ContactSearchData.UnknownRecipient(
        ContactSearchConfiguration.SectionKey.PHONE_NUMBER,
        ContactSearchConfiguration.NewRowMode.NEW_CONVERSATION,
        "+15551234567"
      )
    ).single()

    assertThat(row.name).isEqualTo("+15551234567")
    assertThat(row.detail).isEmpty()
  }

  @Test
  fun `a selected contact is selected`() {
    val data = known(individual())
    val state = LightContactItem.map(context, listOf(data), setOf(data.contactSearchKey), displayCheckBox = true)

    assertThat(state.rows.single().selected).isTrue()
    assertThat(state.selectionMode).isTrue()
  }

  /**
   * A fixed contact -- an existing group member, say -- is shown checked and cannot be un-checked.
   * The Material row did this with a disabled checkbox; the Light row has to carry it as state,
   * because dimming and ignoring the tap are all that is left to say it with.
   */
  @Test
  fun `a fixed contact is checked and inert`() {
    val data = known(individual())
    val state = LightContactItem.map(
      context = context,
      data = listOf(data),
      selection = emptySet(),
      fixedContacts = setOf(data.contactSearchKey),
      displayCheckBox = true
    )

    val row = state.rows.single()
    assertThat(row.selected).isTrue()
    assertThat(row.enabled).isFalse()
    assertThat(row.clickable).isFalse()
  }

  /**
   * The same recipient legitimately appears twice -- once under "Recent chats" and again under
   * "Contacts". Two lazy list rows sharing a key is a crash, not a glitch.
   */
  @Test
  fun `the same recipient in two sections gets two keys`() {
    val recipient = individual()
    val rows = map(
      known(recipient, ContactSearchConfiguration.SectionKey.RECENTS),
      known(recipient, ContactSearchConfiguration.SectionKey.INDIVIDUALS)
    )

    assertThat(rows.map { it.key }.toSet()).hasSize(2)
  }

  /**
   * Equality covers what is drawn and nothing else, so that a profile refresh or a new avatar -- and
   * in particular a re-emitted `Header`, which is a plain class with identity equality -- does not
   * recompose a row whose appearance has not changed.
   */
  @Test
  fun `equality ignores the data back-reference`() {
    val a = LightContactItem.map(
      context,
      listOf(ContactSearchData.Header(ContactSearchConfiguration.SectionKey.GROUPS, null)),
      emptySet()
    ).rows.single()

    val b = LightContactItem.map(
      context,
      listOf(ContactSearchData.Header(ContactSearchConfiguration.SectionKey.GROUPS, null)),
      emptySet()
    ).rows.single()

    assertThat(a.data === b.data).isFalse()
    assertThat(a).isEqualTo(b)
    assertThat(a.hashCode()).isEqualTo(b.hashCode())
  }

  private fun map(vararg data: ContactSearchData?): List<LightContactItem> =
    LightContactItem.map(context, data.toList(), emptySet()).rows

  private fun known(
    recipient: Recipient,
    sectionKey: ContactSearchConfiguration.SectionKey = ContactSearchConfiguration.SectionKey.INDIVIDUALS
  ) = ContactSearchData.KnownRecipient(sectionKey, recipient)

  private fun individual(name: String = "Ada Lovelace", id: Long = 1L): Recipient = mockk {
    every { getDisplayName(any()) } returns name
    every { isGroup } returns false
    every { isSelf } returns false
    every { this@mockk.id } returns RecipientId.from(id)
  }

  private fun group(participants: Int, name: String = "Book Club", id: Long = 2L): Recipient = mockk {
    every { getDisplayName(any()) } returns name
    every { isGroup } returns true
    every { isSelf } returns false
    every { participantIds } returns List(participants) { RecipientId.from(100L + it) }
    every { this@mockk.id } returns RecipientId.from(id)
  }
}
