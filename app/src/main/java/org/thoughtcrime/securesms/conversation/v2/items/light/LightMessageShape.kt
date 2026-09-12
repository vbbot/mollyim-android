/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items.light

import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.util.hasDocument
import org.thoughtcrime.securesms.util.hasGiftBadge
import org.thoughtcrime.securesms.util.hasPoll
import org.thoughtcrime.securesms.util.hasSharedContact
import org.thoughtcrime.securesms.util.isViewOnceMessage

/**
 * Which message subtypes the Light thread draws with no container behind them.
 *
 * Everything that is not plain text goes through `ConversationItem`, a view that measures itself
 * against the bubble it draws, so the bubble comes off subtype by subtype rather than all at once.
 * This is the list; `org.thoughtcrime.securesms.conversation.light.LightConversationItem` is what
 * acts on it.
 *
 * It lives here, beside [LightMessageColumn] and [LightQuoteLine], rather than as a method on the
 * item, because it is a design decision rather than a piece of view plumbing -- and because a
 * decision stated on its own can be tested on its own. `LightConversationItem` cannot be loaded in a
 * unit test at all: it is a `ConversationItem`, whose layouts carry `EmojiTextView`s.
 */
object LightMessageShape {

  /**
   * Whether [record] reads correctly with nothing drawn behind it.
   *
   * True for the subtypes the Light design has a grammar for: thumbnail media (photo, video, GIF,
   * album) with or without a caption, voice notes, the already-container-less stickers and
   * borderless images, and link previews -- an image with a title and a domain under it, which is
   * the same image-plus-caption shape a photo message already takes here.
   *
   * False for the composite cards. A document row, a contact card, a view-once placeholder, a poll,
   * a payment, a gift badge: each expresses its own internal structure *through* the container --
   * several unrelated fragments that read as one object only because a box says so -- and none has a
   * Light vocabulary to fall back on. Strip the box off those and you get a row of loose pieces. A
   * remote-deleted message is the extreme case: it is drawn as an outlined empty bubble, so the
   * outline is the entire message.
   */
  @JvmStatic
  fun isBubbleless(record: MessageRecord): Boolean {
    return when {
      record.isViewOnceMessage() -> false
      record.hasSharedContact() -> false
      record.hasDocument() -> false
      record.hasPoll() -> false
      record.hasGiftBadge() -> false
      record.isPaymentNotification || record.isPaymentTombstone -> false
      record.isRemoteDelete -> false
      else -> true
    }
  }
}
