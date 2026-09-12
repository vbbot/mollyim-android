/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.light

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import com.bumptech.glide.RequestManager
import com.thelightphone.sdk.ui.LightTextVariant
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.AudioView
import org.thoughtcrime.securesms.components.ConversationItemFooter
import org.thoughtcrime.securesms.components.ConversationItemThumbnail
import org.thoughtcrime.securesms.components.QuoteView
import org.thoughtcrime.securesms.components.emoji.EmojiTextView
import org.thoughtcrime.securesms.conversation.ConversationItem
import org.thoughtcrime.securesms.conversation.ConversationItemDisplayMode
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.BindableConversationItem.EventListener
import org.thoughtcrime.securesms.conversation.colors.Colorizer
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectPart
import org.thoughtcrime.securesms.conversation.v2.items.SenderNameWithLabelView
import org.thoughtcrime.securesms.conversation.v2.items.light.LightItemStyle
import org.thoughtcrime.securesms.conversation.v2.items.light.LightMessageColumn
import org.thoughtcrime.securesms.conversation.v2.items.light.LightQuoteLine
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.hasDocument
import org.thoughtcrime.securesms.util.hasGiftBadge
import org.thoughtcrime.securesms.util.hasLinkPreview
import org.thoughtcrime.securesms.util.hasPoll
import org.thoughtcrime.securesms.util.hasSharedContact
import org.thoughtcrime.securesms.util.isViewOnceMessage
import java.util.Locale
import java.util.Optional

/**
 * A media-backed conversation item in The Light Phone's design language.
 *
 * Everything that is not plain text -- photos, videos, voice notes, documents, stickers, link
 * previews, contact shares -- goes through `ConversationItem`, a three-thousand-line view that
 * measures itself against the bubble it draws. Rebuilding it is out of the question, and the V2
 * media view holder that would have been an easier target is an unfinished prototype: its thumbnail
 * has no click handling, no download or play controls, and it hides albums outright, so routing real
 * media through it would trade a styling problem for broken photos.
 *
 * So, as with the text rows, this subclasses rather than replaces. `ConversationItem` keeps doing
 * the work -- attachment transfer state, Giphy playback, view-once, albums, swipe-to-reply,
 * multiselect, the long-press snapshot -- and this layer takes away the bubble, puts the Light type
 * scale and palette on the caption and the timestamp, and swaps Signal's boxed quote card for the
 * one-line [LightQuoteLine].
 *
 * ### Why not every subtype loses its bubble
 *
 * Signal picks its foreground colours to sit on a filled chat-colour bubble, so an outgoing caption
 * is near-white; drawn bubble-less on the LP3's white background that is invisible text. Everything
 * [isBubbleless] returns true for is re-coloured here to match. The subtypes it returns *false* for
 * are composite cards -- a document row, a contact card, a link preview, a view-once placeholder --
 * whose own internal structure is expressed through the bubble and which have no Light grammar to
 * fall back on; stripping the container off those leaves a row of disconnected fragments, so they
 * keep it. See the milestone report for the subtype-by-subtype account.
 */
class LightConversationItem @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : ConversationItem(context, attrs) {

  private lateinit var lightBodyBubble: LightConversationItemBodyBubble
  private lateinit var lightBodyText: EmojiTextView
  private lateinit var lightFooter: ConversationItemFooter
  private lateinit var lightQuoteLine: TextView
  private var lightStickerFooter: ConversationItemFooter? = null
  private var lightQuoteView: QuoteView? = null
  private var lightContactPhotoHolder: View? = null
  private var lightSenderName: SenderNameWithLabelView? = null

  /** The bubble's own horizontal inset, as the source layout states it. Restored on rows that keep one. */
  private var bubbleInset: Int = 0

  /** Signal's quote card's own inset, likewise. */
  private var quoteCardInset: Int = 0

  /** Recomputed per bind; drives every post-bind fix-up below. */
  private var bubbleless: Boolean = false

  /**
   * `ConversationItem` keeps its own listener private, and the quote line needs it to open the
   * message being replied to, so the setter is shadowed to keep a second reference.
   */
  private var lightEventListener: EventListener? = null

  override fun setEventListener(listener: EventListener?) {
    super.setEventListener(listener)
    lightEventListener = listener
  }

  override fun onFinishInflate() {
    super.onFinishInflate()

    lightBodyBubble = findViewById(R.id.body_bubble)
    lightBodyText = findViewById(R.id.conversation_item_body)
    lightFooter = findViewById(R.id.conversation_item_footer)
    lightQuoteLine = findViewById(R.id.light_quote_line)
    lightStickerFooter = findViewById(R.id.conversation_item_sticker_footer)
    lightQuoteView = findViewById(R.id.quote_view)
    lightContactPhotoHolder = findViewById(R.id.contact_photo_container)
    lightSenderName = findViewById(R.id.group_sender_name_with_label)
    bubbleInset = resources.getDimensionPixelOffset(R.dimen.message_bubble_horizontal_padding)
    quoteCardInset = lightQuoteView?.let { ViewUtil.getLeftMargin(it) } ?: 0

    // Resolving the Akkurat typeface walks the system font list, so it happens once per view rather
    // than once per bind. The sizes do have to be re-applied on every bind -- see applyLightType.
    LightQuoteLine.style(lightQuoteLine)

    // Signal tints a group sender's name with a per-member chat colour, and re-tints it on its own
    // whenever the name-colour payload fires. Pinning it on the view is what stops that; the text
    // rows do the same.
    // getMaxBubbleWidth() subtracts the avatar's declared width from the column on group rows. The
    // avatar is hidden in a Light thread, so it must stop claiming the space as well as the pixels.
    findViewById<View>(R.id.contact_photo)?.layoutParams?.width = 0

    lightSenderName?.setTextStyle(LightItemStyle.composeStyle(context, LightTextVariant.Detail))
    lightSenderName?.pinColor(LightItemStyle.contentColor(context))
  }

  override fun bind(
    lifecycleOwner: LifecycleOwner,
    conversationMessage: ConversationMessage,
    previousMessageRecord: Optional<MessageRecord>,
    nextMessageRecord: Optional<MessageRecord>,
    requestManager: RequestManager,
    locale: Locale,
    batchSelected: MutableSet<MultiselectPart>,
    conversationRecipient: Recipient,
    searchQuery: String?,
    pulse: Boolean,
    hasWallpaper: Boolean,
    isMessageRequestAccepted: Boolean,
    allowedToPlayInline: Boolean,
    colorizer: Colorizer,
    displayMode: ConversationItemDisplayMode
  ) {
    val record = conversationMessage.messageRecord

    // Before super.bind: the bubble drawable is installed on the way through setMessageShape, and
    // this is what decides whether that install is honoured.
    bubbleless = isBubbleless(record)
    lightBodyBubble.bubbleless = bubbleless

    super.bind(
      lifecycleOwner,
      conversationMessage,
      previousMessageRecord,
      nextMessageRecord,
      requestManager,
      locale,
      batchSelected,
      conversationRecipient,
      searchQuery,
      pulse,
      hasWallpaper,
      isMessageRequestAccepted,
      allowedToPlayInline,
      colorizer,
      displayMode
    )

    applyLightType()
    applyLightPalette()
    applyLightGutters(record)
    applyLightInsets()
    applyLightThumbnailStyle()
    presentQuoteLine(record)

    // No avatars in a Light thread -- the text rows carry none either, and the sender's name already
    // heads each cluster in a group.
    lightContactPhotoHolder?.visibility = View.GONE
  }

  /**
   * `setBubbleState` runs again whenever the thread recipient changes (a chat-colour edit, a
   * wallpaper change), re-deriving every foreground colour from the bubble that is no longer there.
   */
  override fun onRecipientChanged(modified: Recipient) {
    super.onRecipientChanged(modified)
    applyLightPalette()
  }

  /**
   * Which media subtypes read correctly with no container behind them.
   *
   * True for the ones the Light design has a grammar for: thumbnail media (photo, video, GIF,
   * album) with or without a caption, voice notes, and the already-container-less stickers and
   * borderless images. False for the composite cards -- see the class note.
   */
  private fun isBubbleless(record: MessageRecord): Boolean {
    return when {
      record.isViewOnceMessage() -> false
      record.hasSharedContact() -> false
      record.hasLinkPreview() -> false
      record.hasDocument() -> false
      record.hasPoll() -> false
      record.hasGiftBadge() -> false
      record.isPaymentNotification || record.isPaymentTombstone -> false
      // A remote-deleted message is drawn as an outlined, empty bubble; that outline is the only
      // thing marking it, so it keeps its container.
      record.isRemoteDelete -> false
      else -> true
    }
  }

  /**
   * Light typography on the two pieces of text this row owns.
   *
   * Re-applied per bind because `setBodyText` resets the body to
   * `SignalStore.settings.messageFontSize` every time.
   *
   * Unconditional, unlike the palette: a caption should be Akkurat at the thread's own size whether
   * or not its row kept a container, and a row is recycled across every media subtype, so leaving a
   * bubbled row on Signal's face would depend on which message the view held last.
   */
  private fun applyLightType() {
    LightItemStyle.apply(lightBodyText, LightTextVariant.Paragraph)
    LightItemStyle.apply(lightFooter.dateView as TextView, LightTextVariant.Superfine)
    lightStickerFooter?.let { LightItemStyle.apply(it.dateView as TextView, LightTextVariant.Superfine) }
  }

  /**
   * The single most likely way this screen breaks.
   *
   * `setBubbleState` picks the caption and footer colours to read on a filled chat-colour bubble, so
   * an outgoing message's are near-white. With the bubble gone that is white on white. Every
   * foreground on a bubble-less row is pulled back to the Light theme's own content token.
   */
  private fun applyLightPalette() {
    if (!bubbleless) {
      return
    }

    val content = LightItemStyle.contentColor(context)

    lightBodyText.setTextColor(content)
    lightBodyText.setLinkTextColor(content)

    for (footer in listOfNotNull<ConversationItemFooter>(lightFooter, lightStickerFooter)) {
      footer.setTextColor(content)
      footer.setIconColor(content)
      footer.setRevealDotColor(content)
    }

    // A voice note's waveform, scrubber, duration and play glyph are tinted for the bubble too:
    // white on an outgoing one, which is the same invisible-ink problem in a different view.
    //
    // The filled disc behind the play glyph goes to the surface colour rather than to a tint, which
    // erases it: the reference client's own voice-note row is a bare play glyph beside the duration,
    // with no disc and no container. Signal's waveform scrubber stays -- dropping it to match the
    // reference exactly would take scrubbing with it.
    val audioView: AudioView? = findViewById(R.id.audio_view)
    if (audioView != null && audioView.visibility == View.VISIBLE) {
      audioView.setTint(content)
      audioView.setProgressAndPlayBackgroundTint(LightItemStyle.backgroundColor(context))
    }
  }

  /**
   * Hangs the row off the same column edges the text rows use.
   *
   * `setGutterSizes` pads the row out for an avatar that is hidden here, and the bubble's own edge
   * margin then caps how wide it may get -- two bubble-shaped numbers. Both are replaced by the
   * column [LightMessageColumn] describes, which is the reference client's own row arithmetic and is
   * pinned against it by `LightMessageRowGeometryTest`.
   *
   * Expressed as the row's padding rather than the bubble's margins so that one sandwich does both
   * jobs: the near edge places the content and the far edge caps it at 0.875 of the content band,
   * exactly as the text rows' two guidelines do. The bubble is `wrap_content` between them and
   * hangs off whichever edge its direction calls for.
   */
  private fun applyLightGutters(record: MessageRecord) {
    if (!bubbleless) {
      return
    }

    val rowWidth = if (width > 0) width else resources.displayMetrics.widthPixels
    val isIncoming = !record.isOutgoing

    ViewUtil.setPaddingStart(this, (LightMessageColumn.startFraction(isIncoming) * rowWidth).toInt())
    ViewUtil.setPaddingEnd(this, ((1f - LightMessageColumn.endFraction(isIncoming)) * rowWidth).toInt())
  }

  /**
   * Squares the thumbnail's corners, and flattens it, on a row that has lost its bubble.
   *
   * `setThumbnailCorners` rounds a photo to *match the bubble around it*: square where the bubble
   * carries on past it -- under a caption, above a quote, at the head of a group cluster -- and
   * rounded where the bubble ends. With nothing carrying on past it, that leaves a photo with two
   * rounded corners and two square ones hanging in white space.
   *
   * Squared rather than uniformly rounded because that is what the reference client does: its image
   * rows carry no `clip` and no `RoundedCornerShape` at all. Rounding is bubble vocabulary.
   *
   * `setCorners` covers the album view as well as the single thumbnail, so multi-image messages get
   * the same treatment.
   */
  private fun applyLightThumbnailStyle() {
    if (!bubbleless) {
      return
    }

    val thumbnail = findViewById<ConversationItemThumbnail>(R.id.image_view) ?: return

    thumbnail.setCorners(0, 0, 0, 0)
    // The stub declares eight dips of elevation, which lifted media off the bubble it sat on. With
    // no bubble it is a drop shadow on bare paper, and Light's surfaces do not cast one.
    thumbnail.elevation = 0f
  }

  /**
   * Stands Signal's own quote card down, without taking it out of the tree.
   *
   * `INVISIBLE` at zero height rather than `GONE`, and this is load-bearing: `ConversationItem`'s
   * measure pass still asks whether the *record* has a quote, and on every pass it compares the
   * card's measured width against the width available to it and re-measures the whole row when they
   * differ. A `GONE` child is never measured, so its width would never converge and every layout
   * would burn through the three-measure cap. Measured at zero height it settles in one extra pass,
   * exactly as a real card does, and an invisible view is not drawn.
   */
  private fun collapseSignalQuoteCard() {
    val card = lightQuoteView ?: return

    card.visibility = View.INVISIBLE
    card.layoutParams.height = 0
    ViewUtil.setTopMargin(card, 0, false)
    ViewUtil.setBottomMargin(card, 0, false)
    ViewUtil.setLeftMargin(card, 0)
    ViewUtil.setRightMargin(card, 0)
  }

  /**
   * Puts the card's own geometry back on a row that keeps its container.
   *
   * `ConversationItem.setQuote` re-derives the card's visibility, width and bottom margin on every
   * bind but never its height or its side insets, so without this a view recycled out of a
   * bubble-less row would carry the collapse into the next message and show nothing where its quote
   * should be.
   */
  private fun restoreSignalQuoteCard() {
    val card = lightQuoteView ?: return

    card.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
    ViewUtil.setLeftMargin(card, quoteCardInset)
    ViewUtil.setRightMargin(card, quoteCardInset)
  }

  /**
   * Drops the bubble's internal padding from the row's content.
   *
   * Every horizontal margin inside `body_bubble` is an inset from the bubble's edge -- twelve dips
   * of it on the caption, the timestamp and a voice note, another twelve of padding on the group
   * sender name. With no bubble to inset from they are just an indent that pushes media rows out of
   * line with the text rows beside them, so they go to zero, and come back the moment a subtype
   * keeps its container.
   */
  private fun applyLightInsets() {
    val inset = if (bubbleless) 0 else bubbleInset

    for (view in listOfNotNull<View>(lightBodyText, lightFooter, lightStickerFooter, findViewById(R.id.audio_view))) {
      ViewUtil.setLeftMargin(view, inset)
      ViewUtil.setRightMargin(view, inset)
    }

    lightSenderName?.let { it.setPaddingRelative(inset, it.paddingTop, inset, it.paddingBottom) }
  }

  /**
   * A reply's quoted message, as one dimmed line above the body rather than Signal's boxed card.
   *
   * Only on rows that have lost their bubble: where the card is still the container's own grammar,
   * replacing it with a bare line would leave the quote floating off the top of a box it belongs to.
   */
  private fun presentQuoteLine(record: MessageRecord) {
    val mediaRecord = record as? MmsMessageRecord
    val isReply = bubbleless && mediaRecord != null && LightQuoteLine.present(lightQuoteLine, mediaRecord)

    if (!isReply) {
      lightQuoteLine.visibility = View.GONE
      lightQuoteLine.setOnClickListener(null)
      lightQuoteLine.setOnLongClickListener(null)
      lightQuoteLine.isClickable = false
      // `setQuote` has already dismissed the card to GONE on a message with no quote; on one that
      // keeps its container this puts back the geometry a previous bubble-less bind collapsed.
      restoreSignalQuoteCard()
      return
    }

    collapseSignalQuoteCard()

    // While a multi-select is running the item intercepts touches before any child sees them
    // (ConversationItem.onInterceptTouchEvent), so this only ever fires as a plain tap.
    lightQuoteLine.setOnClickListener { lightEventListener?.onQuoteClicked(mediaRecord!!) }
    // Long-press has to reach the row, or selecting a reply by holding its quote would do nothing --
    // the same passthrough ConversationItem gives Signal's own quote card.
    lightQuoteLine.setOnLongClickListener {
      performLongClick()
      true
    }
  }
}
