/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items.light

import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.Guideline
import androidx.core.view.updateLayoutParams
import com.thelightphone.sdk.ui.LightTextVariant
import org.signal.core.util.dp
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.conversation.v2.items.V2ConversationContext
import org.thoughtcrime.securesms.conversation.v2.items.V2ConversationItemLayout
import org.thoughtcrime.securesms.conversation.v2.items.V2ConversationItemTextOnlyBindingBridge
import org.thoughtcrime.securesms.conversation.v2.items.V2ConversationItemTextOnlyViewHolder
import org.thoughtcrime.securesms.databinding.LightConversationItemTextOnlyIncomingBinding
import org.thoughtcrime.securesms.databinding.LightConversationItemTextOnlyOutgoingBinding
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel

/**
 * A text-only conversation item in The Light Phone's design language.
 *
 * This deliberately subclasses [V2ConversationItemTextOnlyViewHolder] rather than replacing it. The
 * part of a message that is hardest to render -- the body -- is a `CharSequence` carrying Signal's
 * emoji image spans, mention annotations, spoilers, formatting and link spans, and the presenter that
 * assembles it also does link-ification, search-result highlighting, "read more" overflow and
 * condensed mode. Reimplementing that is how you lose formatting silently. So the body keeps its
 * `EmojiTextView` and its existing presenter, and with it the thread keeps reactions, swipe-to-reply,
 * multiselect, the long-press snapshot, chat-colour invalidation and the pulse/search highlight.
 *
 * What changes is the *layout and the type*: `light_conversation_item_text_only_*.xml` hang the
 * message off one edge with no bubble at all, put the footer on its own line above the body, and this
 * class restyles everything from the Light SDK's own tokens (see [LightItemStyle]).
 */
class LightTextOnlyViewHolder<Model : MappingModel<Model>>(
  private val lightBinding: V2ConversationItemTextOnlyBindingBridge,
  private val lightContext: V2ConversationContext
) : V2ConversationItemTextOnlyViewHolder<Model>(
  lightBinding,
  lightContext,
  NoFooterTuck,
  LightConversationItemTheme(lightBinding.root.context, lightContext)
) {

  companion object {
    /** Group-start rows breathe; grouped continuation rows stay tight. Matches the reference client. */
    private val GROUP_START_SPACING = 8.dp
    private val GROUPED_SPACING = 3.dp

    /**
     * Signal's [org.thoughtcrime.securesms.conversation.v2.items.V2FooterPositionDelegate] re-pads the
     * body across two measure passes so the timestamp can tuck into the end of the last line. The Light
     * layout gives the footer its own line, so there is nothing to tuck and nothing to re-measure.
     */
    private val NoFooterTuck = object : V2ConversationItemLayout.OnMeasureListener {
      override fun onPreMeasure() = Unit
      override fun onPostMeasure(): Boolean = false
    }

    fun incoming(binding: LightConversationItemTextOnlyIncomingBinding): V2ConversationItemTextOnlyBindingBridge {
      return V2ConversationItemTextOnlyBindingBridge(
        root = binding.root,
        senderNameWithLabel = binding.groupSenderNameWithLabel,
        // The Light design carries no avatars or badges in the thread.
        senderPhoto = null,
        senderBadge = null,
        body = binding.conversationItemBody,
        bodyWrapper = binding.conversationItemBodyWrapper,
        reply = binding.conversationItemReply,
        reactions = binding.conversationItemReactions,
        deliveryStatus = null,
        footerDate = binding.conversationItemFooterDate,
        footerExpiry = binding.conversationItemExpirationTimer,
        footerBackground = binding.conversationItemFooterBackground,
        footerSpace = null,
        alert = null,
        isIncoming = true,
        footerPinned = binding.conversationItemFooterPinned,
        footerStarred = binding.conversationItemFooterStarred,
        starredSource = null,
        starredSourceWrapper = null,
        starredSourceAvatar = null
      )
    }

    fun outgoing(binding: LightConversationItemTextOnlyOutgoingBinding): V2ConversationItemTextOnlyBindingBridge {
      return V2ConversationItemTextOnlyBindingBridge(
        root = binding.root,
        senderNameWithLabel = null,
        senderPhoto = null,
        senderBadge = null,
        body = binding.conversationItemBody,
        bodyWrapper = binding.conversationItemBodyWrapper,
        reply = binding.conversationItemReply,
        reactions = binding.conversationItemReactions,
        deliveryStatus = binding.conversationItemDeliveryStatus,
        footerDate = binding.conversationItemFooterDate,
        footerExpiry = binding.conversationItemExpirationTimer,
        footerBackground = binding.conversationItemFooterBackground,
        footerSpace = null,
        alert = binding.conversationItemAlert,
        isIncoming = false,
        footerPinned = binding.conversationItemFooterPinned,
        footerStarred = binding.conversationItemFooterStarred,
        starredSource = null,
        starredSourceWrapper = null,
        starredSourceAvatar = null
      )
    }
  }

  /** The quoted message of a reply. Hidden on every row that is not one. See [LightQuoteLine]. */
  private val quoteLine: TextView = lightBinding.root.findViewById(R.id.light_quote_line)

  init {
    // Bubble-less. The superclass installs a ChatColorsDrawable here in its own init; detaching it is
    // what removes the fill, the corners and the chat colour in one go. The drawable itself stays
    // alive and keeps absorbing the colour/projection updates the item decoration pushes at it -- it
    // simply is not attached to anything that draws.
    lightBinding.bodyWrapper.background = null

    // Undoes the superclass's `SignalStore.settings.messageFontSize`. Light typography is fixed.
    LightItemStyle.apply(lightBinding.body, LightTextVariant.Paragraph)
    LightItemStyle.apply(lightBinding.footerDate, LightTextVariant.Superfine)
    // Signal tints group sender names with a per-member chat colour; the Light palette is monochrome,
    // and this pins it on the view so that a name-colour payload cannot put the tint back.
    lightBinding.senderNameWithLabel?.setTextStyle(LightItemStyle.composeStyle(context, LightTextVariant.Detail))
    lightBinding.senderNameWithLabel?.pinColor(LightItemStyle.contentColor(context))
    LightQuoteLine.style(quoteLine)

    val isIncoming = lightBinding.isIncoming
    lightBinding.root.findViewById<Guideline>(R.id.light_column_start)
      .setGuidelinePercent(LightMessageColumn.startFraction(isIncoming))
    lightBinding.root.findViewById<Guideline>(R.id.light_column_end)
      .setGuidelinePercent(LightMessageColumn.endFraction(isIncoming))
  }

  /**
   * Signal hangs the footer off the *last* message of a cluster, where it tucks into the last line.
   * The Light layout puts it above the body, so it belongs to the *first* message -- one timestamp per
   * cluster, at the top, exactly as the reference client renders it.
   */
  override fun shouldShowFooter(): Boolean {
    return isForcedFooter() || shape.isStartingShape
  }

  /**
   * Colours come from [LightConversationItemTheme], not from here, so that the presenters which run on
   * partial re-binds stay correct. All that is left is the spacing, which Signal derives from the
   * bubble cluster shape and the Light design states directly.
   */
  override fun onBound() {
    presentQuoteLine()

    val spacing = if (shape.isStartingShape) GROUP_START_SPACING else GROUPED_SPACING
    itemView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
      topMargin = spacing
      bottomMargin = spacing
    }
  }

  /**
   * A reply's quoted message, as one line above the body.
   *
   * Signal routes anything carrying a quote to the media view holder because a quote has nowhere to
   * live outside a bubble; `ConversationDataSource` sends text replies here instead, so the quote is
   * presented here. Tapping it still jumps to the original -- the same listener `QuoteView` gets on
   * the media path, including its deference to an in-progress multi-select.
   */
  private fun presentQuoteLine() {
    val record = conversationMessage.messageRecord as? MmsMessageRecord
    if (record == null || !LightQuoteLine.present(quoteLine, record)) {
      quoteLine.setOnClickListener(null)
      quoteLine.isClickable = false
      return
    }

    quoteLine.setOnClickListener {
      if (lightContext.selectedItems.isEmpty()) {
        lightContext.clickListener.onQuoteClicked(record)
      } else {
        lightContext.clickListener.onItemClick(getMultiselectPartForLatestTouch())
      }
    }
  }
}
