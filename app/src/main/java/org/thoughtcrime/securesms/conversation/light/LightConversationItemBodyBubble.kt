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
import androidx.appcompat.content.res.AppCompatResources
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
 * without its container; see
 * `org.thoughtcrime.securesms.conversation.v2.items.light.LightMessageShape.isBubbleless`. Being a
 * mode is also what makes [setBackgroundResource] load-bearing -- see there.
 *
 * Note this only governs the bubble *drawable*. An outgoing message's bubble is additionally painted
 * by the list itself, through the projections each item reports to `RecyclerViewColorizer`; refusing
 * that is `LightConversationItem.getColorizerProjections`, not this.
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

  /** Which mode the drawable currently hanging on this view was installed under. */
  private var installedBubbleless: Boolean? = null

  override fun setBackground(background: Drawable?) {
    installedBubbleless = bubbleless
    super.setBackground(if (bubbleless) ColorDrawable(Color.TRANSPARENT) else background)
  }

  /**
   * Makes sure a mode change is never skipped.
   *
   * `View.setBackgroundResource` returns immediately when handed the same resource it was handed
   * last time, without calling `setBackground` -- and the eight bubble shapes are picked from a
   * message's position in its cluster, so a recycled row is handed the same one as the message before
   * it more often than not. Silently skipping the install is how a photo ends up wearing the document
   * row's bubble, or a document row ends up with no bubble at all, depending on which way the row was
   * recycled.
   *
   * When the mode has not changed the short-circuit is harmless and is left in place; when it has,
   * the drawable is resolved here so that the install goes through [setBackground] regardless.
   */
  override fun setBackgroundResource(resid: Int) {
    if (resid != 0 && installedBubbleless != bubbleless) {
      setBackground(AppCompatResources.getDrawable(context, resid))
      return
    }

    super.setBackgroundResource(resid)
  }
}
