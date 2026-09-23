/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items.light

import android.content.Context
import android.graphics.Color
import androidx.annotation.ColorInt
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.conversation.v2.items.V2ConversationContext
import org.thoughtcrime.securesms.conversation.v2.items.V2ConversationItemTheme

/**
 * The Light Phone's palette for conversation items: one foreground, no fills.
 *
 * Every colour Signal picks for a message assumes a filled chat-colour bubble underneath -- outgoing
 * body text is near-white so it reads on blue. Drawn bubble-less on the LP3's white background that
 * is invisible text, which is the single most likely way this screen breaks. Overriding the delegate
 * rather than re-colouring the views after `bind` matters: the item's presenters also run on partial
 * re-binds (a search query changing re-runs `presentBody` on its own), and anything patched up
 * afterwards would be undone by those.
 */
class LightConversationItemTheme(
  private val context: Context,
  conversationContext: V2ConversationContext
) : V2ConversationItemTheme(context, conversationContext) {

  @ColorInt
  override fun getBodyTextColor(conversationMessage: ConversationMessage): Int = LightItemStyle.contentColor(context)

  @ColorInt
  override fun getFooterForegroundColor(conversationMessage: ConversationMessage): Int = LightItemStyle.contentColor(context)

  /** Bubble-less: incoming and outgoing are told apart by which edge they hang off, nothing else. */
  @ColorInt
  override fun getBodyBubbleColor(conversationMessage: ConversationMessage): Int = Color.TRANSPARENT

  @ColorInt
  override fun getFooterBubbleColor(conversationMessage: ConversationMessage): Int = Color.TRANSPARENT

  @ColorInt
  override fun getReplyIconBackgroundColor(): Int = Color.TRANSPARENT
}
