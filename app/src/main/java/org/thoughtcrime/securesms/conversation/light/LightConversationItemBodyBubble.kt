/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.light

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import org.thoughtcrime.securesms.conversation.ConversationItemBodyBubble

/**
 * A message bubble that can be told not to be one.
 *
 * `ConversationItem` installs the bubble by *resource*: `setMessageShape` picks one of eight
 * `message_bubble_background_*` drawables from the message's position in its cluster, and
 * `setBubbleState` then reaches back through `getBackground()` to stamp the chat colour onto it.
 * Nulling the background after the fact would strand that second call on a null drawable, and
 * merely re-colouring it to transparent would still leave the shape's corners clipping the content.
 *
 * So the fill is refused at the point of installation instead: in [bubbleless] mode every background
 * handed to this view is swapped for a fully transparent [ColorDrawable]. `getBackground()` keeps
 * returning something, so `setBubbleState`'s colour filters land harmlessly -- a `SRC_IN` or
 * `MULTIPLY` filter over zero alpha is still zero alpha -- and nothing is drawn.
 *
 * [bubbleless] is a mode rather than a constant because not every media subtype stays readable
 * without its container; see `LightConversationItem.isBubbleless`.
 */
class LightConversationItemBodyBubble @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : ConversationItemBodyBubble(context, attrs) {

  /**
   * Whether this row draws without a bubble.
   *
   * Set this *before* the bind that follows: `ConversationItem.bind` re-installs the background on
   * its way through `setMessageShape`, which is what makes the new mode take effect. Nothing is
   * re-drawn here, precisely so that a recycled view cannot end up showing the previous message's
   * bubble state between the two calls.
   */
  var bubbleless: Boolean = false

  override fun setBackground(background: Drawable?) {
    super.setBackground(if (bubbleless) ColorDrawable(Color.TRANSPARENT) else background)
  }
}
