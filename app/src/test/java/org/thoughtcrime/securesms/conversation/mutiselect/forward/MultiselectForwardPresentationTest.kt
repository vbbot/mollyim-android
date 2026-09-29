/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.mutiselect.forward

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import io.mockk.mockk
import org.junit.Test
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

class MultiselectForwardPresentationTest {

  @Test
  fun `chat and story destinations for one recipient have distinct presentation keys`() {
    val recipientId = RecipientId.from(42)
    val recipient = mockk<Recipient>()
    val chat = MultiselectForwardBottomBarState.SelectedContact.KnownRecipient(
      ContactSearchKey.RecipientSearchKey(recipientId, false),
      recipient
    )
    val story = MultiselectForwardBottomBarState.SelectedContact.KnownRecipient(
      ContactSearchKey.RecipientSearchKey(recipientId, true),
      recipient
    )

    assertThat(chat.key).isNotEqualTo(story.key)
  }

  @Test
  fun `unknown destination key remains part of presentation identity`() {
    val first = MultiselectForwardBottomBarState.SelectedContact.UnknownRecipient(
      ContactSearchKey.UnknownRecipientKey(ContactSearchConfiguration.SectionKey.PHONE_NUMBER, "+15551234567"),
      "+15551234567"
    )
    val second = MultiselectForwardBottomBarState.SelectedContact.UnknownRecipient(
      ContactSearchKey.UnknownRecipientKey(ContactSearchConfiguration.SectionKey.USERNAME, "+15551234567"),
      "+15551234567"
    )

    assertThat(first.key).isNotEqualTo(second.key)
  }

  @Test
  fun `clearing a sibling host tears it down exactly once`() {
    val lifecycle = SiblingHostLifecycle()
    var teardownCount = 0

    lifecycle.attach { teardownCount++ }
    lifecycle.clear()
    lifecycle.clear()

    assertThat(teardownCount).isEqualTo(1)
  }

  @Test
  fun `replacing a sibling host tears down the old then owns the new`() {
    val lifecycle = SiblingHostLifecycle()
    val teardownOrder = mutableListOf<String>()

    lifecycle.attach { teardownOrder += "first" }
    lifecycle.attach { teardownOrder += "second" }
    assertThat(teardownOrder).isEqualTo(listOf("first"))

    lifecycle.clear()
    assertThat(teardownOrder).isEqualTo(listOf("first", "second"))
  }
}
