/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.light

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.LifecycleOwner
import com.bumptech.glide.RequestManager
import com.thelightphone.sdk.ui.LightTextVariant
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.AudioView
import org.thoughtcrime.securesms.components.ConversationItemFooter
import org.thoughtcrime.securesms.components.ConversationItemThumbnail
import org.thoughtcrime.securesms.components.LinkPreviewView
import org.thoughtcrime.securesms.components.OutlinedThumbnailView
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
import org.thoughtcrime.securesms.conversation.v2.items.light.LightMessageShape
import org.thoughtcrime.securesms.conversation.v2.items.light.LightQuoteLine
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.ProjectionList
import org.thoughtcrime.securesms.util.ViewUtil
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
 * ### Two ways a bubble gets drawn
 *
 * Refusing the bubble *drawable* is only half of it, and missing the other half is what put a
 * chat-colour bubble back around every outgoing media row on the device. A bubble on an **outgoing**
 * message is not painted by the message at all: `RecyclerViewColorizer` fills the whole list with the
 * chat colour, punches a hole through it for each item's `getColorizerProjections`, and the projection
 * `ConversationItem` hands back is the body bubble's rectangle and corner radii -- computed from the
 * view's geometry, with no reference to its background. [LightConversationItemBodyBubble] can make the
 * drawable transparent; only [getColorizerProjections] can stop the list painting the shape.
 *
 * ### Why not every subtype loses its bubble
 *
 * Signal picks its foreground colours to sit on a filled chat-colour bubble, so an outgoing caption
 * is near-white; drawn bubble-less on the LP3's white background that is invisible text. Everything
 * [LightMessageShape.isBubbleless] returns true for is re-coloured here to match; the subtypes it
 * returns *false* for keep their container, and the reasoning is stated there.
 */
class LightConversationItem @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : ConversationItem(context, attrs) {

  companion object {
    /** See [applyLightLinkPreviewStyle]. */
    private const val LINK_PREVIEW_DESCRIPTION_LINES = 2
  }

  private lateinit var lightBodyBubble: LightConversationItemBodyBubble
  private lateinit var lightBodyText: EmojiTextView
  private lateinit var lightFooter: ConversationItemFooter
  private lateinit var lightQuoteLine: TextView
  private var lightStickerFooter: ConversationItemFooter? = null
  private var lightQuoteView: QuoteView? = null
  private var lightContactPhotoHolder: View? = null
  private var lightSenderName: SenderNameWithLabelView? = null
  private var lightQuotedIndicator: ImageView? = null

  /** The chip's own fill and glyph tint, kept so that a row which keeps its bubble gets them back. */
  private var quotedIndicatorFill: Drawable? = null
  private var quotedIndicatorTint: ColorStateList? = null

  /** The bubble's own horizontal inset, as the source layout states it. Restored on rows that keep one. */
  private var bubbleInset: Int = 0

  /** Signal's quote card's own inset, likewise. */
  private var quoteCardInset: Int = 0

  /** Recomputed per bind; drives every post-bind fix-up below. */
  private var bubbleless: Boolean = false

  /** Handed to the colorizer in place of the bubble's outline on a row that has none. Never filled. */
  private val noProjections = ProjectionList()

  /**
   * How far a link preview's text stands off its thumbnail, as the shared `link_preview.xml` declares
   * it. Captured from the view the first time one is styled rather than restated as a number here, so
   * that the two cannot drift; `-1` until then.
   */
  private var linkPreviewTextInset: Int = -1

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
    lightQuotedIndicator = findViewById(R.id.quoted_indicator)
    quotedIndicatorFill = lightQuotedIndicator?.background
    quotedIndicatorTint = lightQuotedIndicator?.imageTintList
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
    bubbleless = LightMessageShape.isBubbleless(record)
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
    applyLightLinkPreviewStyle()
    applyLightQuotedIndicatorStyle()
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
   * Stops the list painting a bubble this row does not have.
   *
   * The single most consequential line in this file, and the reason outgoing media rows still came
   * out in chat-colour bubbles after every drawable in the tree had been made transparent. An
   * outgoing bubble is not drawn by the message: `RecyclerViewColorizer` floods the `RecyclerView`
   * with the chat colour and each item cuts a hole in it shaped like the projection returned here
   * (`ConversationItem.getSnapshotProjections` -- the body bubble's rectangle and `bodyBubbleCorners`,
   * derived from geometry, never from the background). With no projection there is no hole, and with
   * no hole there is nothing to show through. This is what the V2 text-only holder does too, and for
   * the same reason.
   *
   * `getSnapshotProjections` is deliberately left alone: it is read by the long-press snapshot and by
   * the jump-to-message pulse, and emptying it would take the pulse with it -- which is exactly the
   * feedback you get after tapping a quote line.
   */
  override fun getColorizerProjections(coordinateRoot: ViewGroup): ProjectionList {
    if (bubbleless) {
      noProjections.clear()
      return noProjections
    }

    return super.getColorizerProjections(coordinateRoot)
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

    // R.id.image_view is a ConversationItemThumbnail on photo, video and album rows, but a
    // BorderlessImageView on sticker and jumbomoji rows, which share this layout. A reified
    // findViewById<ConversationItemThumbnail> compiles to an unchecked cast and threw
    // ClassCastException the moment a thread containing a sticker was opened. Borderless content is
    // already container-less upstream, so it has no corners to square -- only the lift to drop.
    when (val thumbnail = findViewById<View>(R.id.image_view)) {
      null -> Unit

      is ConversationItemThumbnail -> {
        thumbnail.setCorners(0, 0, 0, 0)
        // The stub declares eight dips of elevation, which lifted media off the bubble it sat on.
        // With no bubble it is a drop shadow on bare paper, and Light's surfaces do not cast one.
        thumbnail.elevation = 0f
      }

      else -> thumbnail.elevation = 0f
    }
  }

  /**
   * A link preview as image, title and domain, with nothing drawn around them.
   *
   * Signal builds one as a card: `linkpreview_container` carries a `signal_neutralSurface` fill, the
   * `LinkPreviewView` itself is then filled again with the bubble colour, its `dispatchDraw` rounds
   * the top corners through a `CornerMask`, and the 72dp thumbnail gets rounded corners and a hairline
   * outline of its own. Four containers deep, all of it Material.
   *
   * Underneath that is a shape the Light thread already speaks: a picture with a line of text under
   * it. So it is taken apart -- fills to transparent, every corner radius to zero, the thumbnail's
   * outline off -- and re-set in type instead, which is the only hierarchy the Light design uses:
   * the title at `Paragraph` in the content colour, exactly as a message body, and the domain at
   * `Superfine` in `contentSecondary`, exactly as a timestamp.
   *
   * The description keeps its place between the two, at `Detail` and dimmed, but capped at two lines.
   * Upstream allows it fifteen, which is survivable inside a card and is not survivable without one:
   * a scraped paragraph with no box around it simply becomes the message. Two lines is what Signal's
   * own compose-box preview allows, and what its condensed mode reduces to.
   *
   * Squared rather than uniformly rounded, and the thumbnail flattened, for the same reason as
   * [applyLightThumbnailStyle]: rounding is bubble vocabulary, and the reference client has none.
   *
   * Re-applied per bind and only on a bubble-less row, because a recycled view carries whatever the
   * previous message left on it and `setLinkPreview` puts the outline and the radii back every time.
   */
  private fun applyLightLinkPreviewStyle() {
    val linkPreview: LinkPreviewView = findViewById(R.id.link_preview) ?: return

    if (!bubbleless || linkPreview.visibility != View.VISIBLE) {
      return
    }

    val content = LightItemStyle.contentColor(context)
    val contentSecondary = LightItemStyle.contentSecondaryColor(context)

    // The bubble-coloured fill ConversationItem.setMediaAttributes just stamped on, and the card's
    // own surface underneath it.
    linkPreview.setBackgroundColor(Color.TRANSPARENT)
    // Zeroes the CornerMask that rounds the card's top corners in dispatchDraw. Its side effect of
    // re-rounding the thumbnail is undone immediately below.
    linkPreview.setCorners(0, 0)

    val container: View? = findViewById(R.id.linkpreview_container)
    container?.setBackgroundColor(Color.TRANSPARENT)
    // The card's own 6dp inset, horizontally: it would indent the preview out of line with the body
    // text beneath it. The vertical half stays -- with no card, that is the only thing separating the
    // preview from the message.
    container?.let { it.setPadding(0, it.paddingTop, 0, it.paddingBottom) }

    // Not resolved at all until a preview with a picture has been bound, and GONE on the big-image
    // variant, where the picture is the row's main thumbnail instead.
    val thumbnail = findViewById<View>(R.id.linkpreview_thumbnail) as? OutlinedThumbnailView
    thumbnail?.setOutlineEnabled(false)

    val title: TextView? = findViewById(R.id.linkpreview_title)
    val description: TextView? = findViewById(R.id.linkpreview_description)
    val site: TextView? = findViewById(R.id.linkpreview_site)

    if (linkPreviewTextInset < 0 && title != null) {
      linkPreviewTextInset = (title.layoutParams as ViewGroup.MarginLayoutParams).marginStart
    }

    // That inset is only earned when there is a thumbnail to stand off. With none, a ConstraintLayout
    // still resolves the chain against the collapsed view and the indent survives, which would leave
    // the title out of line with everything else in the message column.
    val textInset = if (thumbnail?.visibility == View.VISIBLE) linkPreviewTextInset.coerceAtLeast(0) else 0

    title?.let {
      LightItemStyle.apply(it, LightTextVariant.Paragraph)
      it.setTextColor(content)
      it.setTextInset(textInset)
    }

    description?.let {
      LightItemStyle.apply(it, LightTextVariant.Detail)
      it.setTextColor(contentSecondary)
      it.maxLines = LINK_PREVIEW_DESCRIPTION_LINES
      it.setTextInset(textInset)
    }

    site?.let {
      LightItemStyle.apply(it, LightTextVariant.Superfine)
      it.setTextColor(contentSecondary)
      it.setTextInset(textInset)
    }

    // A Signal call link's "Join call" button, which rides along with the preview. Signal paints it
    // for the bubble as well -- near-white text on a semi-transparent white fill on an outgoing row,
    // which is an invisible button once there is no bubble under it. The Light SDK's own buttons
    // (`LightBarButton`) are text in the `Button` step with no fill at all, so that is what it
    // becomes.
    val joinButton: TextView? = findViewById(R.id.join_button)
    if (joinButton != null && joinButton.visibility == View.VISIBLE) {
      LightItemStyle.apply(joinButton, LightTextVariant.Button)
      joinButton.setTextColor(content)
      joinButton.setBackgroundColor(Color.TRANSPARENT)
    }
  }

  /**
   * Moves a link preview's text to [inset] from the start edge.
   *
   * `marginStart` rather than `ViewUtil.setLeftMargin`, which writes `leftMargin`/`rightMargin`
   * directly and so depends on the layout direction having already been resolved -- it has not been
   * on the first bind of a freshly inflated stub.
   */
  private fun View.setTextInset(inset: Int) {
    updateLayoutParams<ViewGroup.MarginLayoutParams> { marginStart = inset }
  }

  /**
   * Signal's "this message has replies" chip, in the Light palette.
   *
   * A plain text message that has been replied to no longer comes through here at all -- it stays on
   * the Light text row, which carries the affordance as a line of its own (see
   * [LightQuoteLine.presentRepliesIndicator]). A *media* message that has been replied to still does,
   * and `setHasBeenQuoted` shows it as a 32dp filled circle in `colorSurfaceVariant` beside the
   * bubble. On a row with no bubble that is the only fill left on screen.
   *
   * The fill goes and the glyph is pulled back to the dimmed content token, which leaves the same
   * 32dp tap target and the same click listener, drawn as a bare mark. The scheduled-message
   * indicator in the same holder is left alone: it is not part of this.
   */
  private fun applyLightQuotedIndicatorStyle() {
    val indicator = lightQuotedIndicator ?: return

    if (bubbleless) {
      indicator.background = null
      indicator.imageTintList = ColorStateList.valueOf(LightItemStyle.contentSecondaryColor(context))
    } else {
      // Recycled back onto a row that kept its container, where the chip is Signal's own again.
      indicator.background = quotedIndicatorFill
      indicator.imageTintList = quotedIndicatorTint
    }
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
