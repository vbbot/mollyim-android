/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items.light

import android.app.Application
import android.net.Uri
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.attachments.UriAttachment
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.database.FakeMessageRecords
import org.thoughtcrime.securesms.database.AttachmentTable
import org.thoughtcrime.securesms.database.MessageTypes
import org.thoughtcrime.securesms.database.model.databaseprotos.GiftBadge
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.mms.DocumentSlide
import org.thoughtcrime.securesms.mms.SlideDeck
import org.thoughtcrime.securesms.polls.PollRecord
import org.thoughtcrime.securesms.recipients.RecipientId
import java.util.Optional

/**
 * Which message subtypes the Light thread draws without a container, stated as a list so that
 * changing one is a deliberate act rather than a side effect.
 *
 * The rows themselves cannot be inflated in a unit test -- every conversation layout carries an
 * `EmojiTextView`, whose constructor calls `EmojiSource.getLatest` and blocks forever without the
 * app's dependency graph -- so [LightMessageShape] holds the decision on its own, away from the view,
 * and this asserts on it directly.
 *
 * The `false` cases matter as much as the `true` ones: each is a composite card that falls apart
 * without its box, and each has been deliberately left with one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class LightMessageShapeTest {

  @Test
  fun `a plain message is drawn without a bubble`() {
    assertThat(LightMessageShape.isBubbleless(FakeMessageRecords.buildMediaMmsMessageRecord())).isTrue()
  }

  /**
   * The change milestone 6 makes. A link preview is a picture with a title and a domain under it,
   * which is the shape a captioned photo already takes in this thread -- the card around it was
   * Material convention, not structure.
   */
  @Test
  fun `a link preview is drawn without a bubble`() {
    val record = FakeMessageRecords.buildMediaMmsMessageRecord(
      linkPreviews = listOf(LinkPreview("https://example.com/a", "A title", "A description", 0L, Optional.empty()))
    )

    assertThat(LightMessageShape.isBubbleless(record)).isTrue()
  }

  /**
   * Built from an [UriAttachment] rather than through `DocumentSlide(context, uri, ...)`: that
   * constructor sniffs the mime type through `AppDependencies.blobs`, which is not up in a unit test.
   */
  @Test
  fun `a document keeps its bubble`() {
    val attachment = UriAttachment(
      uri = Uri.parse("content://molly/doc"),
      contentType = "application/pdf",
      transferState = AttachmentTable.TRANSFER_PROGRESS_DONE,
      size = 1024L,
      fileName = "minutes.pdf",
      voiceNote = false,
      borderless = false,
      videoGif = false,
      quote = false,
      quoteTargetContentType = null,
      caption = null,
      stickerLocator = null,
      blurHash = null,
      audioHash = null,
      transformProperties = null
    )

    val slideDeck = SlideDeck().apply { addSlide(DocumentSlide(attachment)) }

    assertThat(LightMessageShape.isBubbleless(FakeMessageRecords.buildMediaMmsMessageRecord(slideDeck = slideDeck))).isFalse()
  }

  @Test
  fun `a contact share keeps its bubble`() {
    val contact = Contact(Contact.Name("Steinar", "Steinar", null, null, null, null), null, emptyList(), emptyList(), emptyList(), null)

    assertThat(LightMessageShape.isBubbleless(FakeMessageRecords.buildMediaMmsMessageRecord(contacts = listOf(contact)))).isFalse()
  }

  @Test
  fun `a view-once message keeps its bubble`() {
    assertThat(LightMessageShape.isBubbleless(FakeMessageRecords.buildMediaMmsMessageRecord(viewOnce = true))).isFalse()
  }

  @Test
  fun `a poll keeps its bubble`() {
    val poll = PollRecord(1L, "Lunch?", emptyList(), false, false, 1L, 1L)

    assertThat(LightMessageShape.isBubbleless(FakeMessageRecords.buildMediaMmsMessageRecord(poll = poll))).isFalse()
  }

  @Test
  fun `a gift badge keeps its bubble`() {
    assertThat(LightMessageShape.isBubbleless(FakeMessageRecords.buildMediaMmsMessageRecord(giftBadge = GiftBadge()))).isFalse()
  }

  @Test
  fun `a payment notification keeps its bubble`() {
    val record = FakeMessageRecords.buildMediaMmsMessageRecord(
      mailbox = MessageTypes.BASE_INBOX_TYPE or MessageTypes.SPECIAL_TYPE_PAYMENTS_NOTIFICATION
    )

    assertThat(LightMessageShape.isBubbleless(record)).isFalse()
  }

  /** The outline is the whole message: a remote-deleted bubble is empty apart from it. */
  @Test
  fun `a remote-deleted message keeps its bubble`() {
    val record = FakeMessageRecords.buildMediaMmsMessageRecord(body = "", deletedBy = RecipientId.from(1L))

    assertThat(LightMessageShape.isBubbleless(record)).isFalse()
  }
}
