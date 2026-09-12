/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items.light

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.RelativeSizeSpan
import android.view.View
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.compose.ui.graphics.toArgb
import androidx.core.text.inSpans
import com.thelightphone.sdk.ui.LightTextVariant
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.Quote
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.MediaUtil

/**
 * The two ends of a reply, each rendered as one dimmed line beside the body it belongs to.
 *
 * ```
 *   ↶ Steinar: see you at three      <- [present], on the reply
 *   Perfect, I'll be there
 *
 *   See you at three                 <- [presentRepliesIndicator], on the message replied to
 *   ↶ Replies
 * ```
 *
 * Signal draws a quote as a filled, cornered card with a coloured rule down its start edge
 * ([org.thoughtcrime.securesms.components.QuoteView]) -- a Material convention, and a box inside a
 * box once the message bubble is gone. The Light Phone's grammar signals a relationship between
 * messages with a glyph and a step down the type scale instead, never with a container: the
 * reference client marks a *forwarded* message with a `↷` beside `Paragraph` text and no container
 * at all (`ForwardedArrowGlyph` in its `ThreadScreen`). This is the same move for the other
 * direction -- no fill, no border, no rule, no indent; the glyph and the dimming carry it.
 */
object LightQuoteLine {

  /**
   * `↶ U+21B6 ANTICLOCKWISE TOP SEMICIRCLE ARROW` -- the exact mirror of the reference client's
   * forward glyph (`↷ U+21B7`), so reply and forward read as one pair.
   *
   * [com.thelightphone.sdk.ui.LightIcons] carries no reply, undo or curved-arrow asset; the nearest
   * are `BACK` (a plain chevron) and `DIRECTIONS_U_TURN_LEFT` (a road sign), neither of which says
   * "reply". The reference client reaches for a text character for its own glyph for exactly this
   * reason.
   *
   * Deliberately *not* `↩ U+21A9`: that code point has an emoji presentation, so it is liable to
   * come back as a colour sprite from the platform's emoji font, and a coloured glyph has no place
   * in a monochrome thread.
   *
   * One glyph serves both directions: `↶` means "reply" wherever it appears in the thread, and
   * whether a line is the message being answered or the note that answers exist is said by where the
   * line sits and what it reads, not by a second symbol. The reference client's `↷` is *not*
   * borrowed for the other direction -- there it means forwarded.
   */
  const val GLYPH = "↶"

  /**
   * One typographic step below the body, which is the whole of the signal that this was quoted
   * rather than said. `Detail` (20sp design units) rather than `Superfine` (16) because the
   * timestamp directly above is already `Superfine`, and two adjacent lines at one size read as a
   * single block.
   */
  private val VARIANT = LightTextVariant.Detail

  /**
   * The glyph comes from a fallback font (the LP3's Akkurat has no `U+21B6`), whose run sits small
   * in the line box. The reference client scales its own glyph 2.1x for the same reason; this is the
   * equivalent against a line that is itself a step smaller.
   */
  private const val GLYPH_SCALE = 1.6f

  /**
   * Styles [view] once, at view-holder construction: resolving the typeface walks the system font
   * list, which must not happen per bind.
   *
   * The line height is deliberately *not* pinned -- the scaled glyph would be clipped by a fixed
   * line box, and with `maxLines = 1` the view sizes to the tallest run anyway.
   */
  fun style(view: TextView) {
    LightItemStyle.apply(view, VARIANT, applyLineHeight = false)
    view.setTextColor(LightItemStyle.colors(view.context).contentSecondary.toArgb())
    view.maxLines = 1
    view.ellipsize = TextUtils.TruncateAt.END
  }

  /**
   * Shows [view] as the "this message has replies" affordance, or hides it when it has none.
   *
   * Signal marks a message that has been quoted with a filled circular chip hung off the edge of its
   * bubble (`ConversationItem.setHasBeenQuoted`), which is bubble vocabulary twice over: a fill, and
   * a position defined by an edge that a Light row does not have. This says the same thing the way
   * the thread says everything else -- the reply glyph, one step down the type scale, dimmed, with no
   * container -- and carries the same tap target, which opens the replies sheet.
   *
   * Styled by [style], exactly as the quote line is: the two are a matched pair and must not drift.
   *
   * @return whether the affordance was shown.
   */
  fun presentRepliesIndicator(view: TextView, hasBeenQuoted: Boolean): Boolean {
    if (!hasBeenQuoted) {
      view.visibility = View.GONE
      return false
    }

    view.text = buildRepliesLine(view.context)
    view.visibility = View.VISIBLE
    return true
  }

  /** Split out from [presentRepliesIndicator] so that it can be asserted on directly. */
  @VisibleForTesting
  fun buildRepliesLine(context: Context): CharSequence {
    return SpannableStringBuilder()
      .inSpans(RelativeSizeSpan(GLYPH_SCALE)) { append(GLYPH) }
      .append(" ")
      .append(context.getString(R.string.MessageQuotesBottomSheet_replies))
  }

  /**
   * Shows [view] as the quote line for [record], or hides it when the message is not a reply.
   *
   * @return whether a quote was shown.
   */
  fun present(view: TextView, record: MmsMessageRecord): Boolean {
    val quote = record.quote
    if (quote == null) {
      view.visibility = View.GONE
      return false
    }

    val context = view.context
    val author = Recipient.live(quote.author).get()
    val name = if (author.isSelf) context.getString(R.string.QuoteView_you) else author.getDisplayName(context)

    view.text = buildLine(context, name, quote)
    view.visibility = View.VISIBLE
    return true
  }

  /**
   * Split out from [present] so that it can be exercised without a resolved [Recipient], which needs
   * the recipient database behind it.
   */
  @VisibleForTesting
  fun buildLine(context: Context, authorName: String, quote: Quote): CharSequence {
    return buildLine(authorName, summarise(context, quote))
  }

  /**
   * The *pending* reply -- the one held in the input panel, waiting to be sent -- as the same line the
   * sent message will carry.
   *
   * The Light composer shows this above its text entry, which is the only place a reply is visible
   * before it is sent: Signal's quote card in the input panel does not draw in this fork (see
   * `InputPanel.setQuote`). Running it through the same [buildLine] the thread's own reply lines use
   * is what keeps composing a reply and reading it back looking like one thing.
   */
  fun buildPendingLine(context: Context, authorName: String, quote: QuoteModel): CharSequence {
    val summary: CharSequence = when {
      quote.isOriginalMissing -> context.getString(R.string.QuoteView_original_missing)
      quote.type == QuoteModel.Type.GIFT_BADGE -> context.getString(R.string.QuoteView__donation_for_a_friend)
      quote.type == QuoteModel.Type.POLL -> context.getString(R.string.Poll__poll_question, quote.text)
      quote.text.isNotBlank() -> quote.text.replace('\n', ' ').trim()
      // A media-only reply carries no body, so the noun stands in -- via the same Slide the sent
      // quote will be summarised from, so the two agree.
      else -> mediaNoun(context, quote.attachment?.let { MediaUtil.getSlideForAttachment(it) })
    }

    return buildLine(authorName, summary)
  }

  /** The glyph, the author and what they said, in the one shape every reply line in the app uses. */
  @VisibleForTesting
  fun buildLine(authorName: String, summary: CharSequence): CharSequence {
    return SpannableStringBuilder()
      .inSpans(RelativeSizeSpan(GLYPH_SCALE)) { append(GLYPH) }
      .append(" ")
      .append(authorName)
      .append(if (summary.isEmpty()) "" else ": ")
      .append(summary)
  }

  /**
   * What the quoted message *said*, in one line.
   *
   * Mirrors `QuoteView.setQuoteText`: a deleted original says so, the body wins when there is one,
   * and a media-kind noun stands in when there is not.
   */
  private fun summarise(context: Context, quote: Quote): CharSequence {
    if (quote.isOriginalMissing) {
      return context.getString(R.string.QuoteView_original_missing)
    }

    val body: CharSequence? = when (quote.quoteType) {
      QuoteModel.Type.GIFT_BADGE -> context.getString(R.string.QuoteView__donation_for_a_friend)
      QuoteModel.Type.POLL -> context.getString(R.string.Poll__poll_question, quote.displayText)
      else -> quote.displayText
    }

    if (!TextUtils.isEmpty(body)) {
      // Flattened to a plain String: the line is a TextView, not an EmojiTextView, so there is no
      // renderer here for emoji image spans, mention annotations or formatting. The characters
      // survive -- only the decoration is dropped -- and newlines fold into the single line.
      return body.toString().replace('\n', ' ').trim()
    }

    return mediaNoun(context, quote.attachment.firstSlide)
  }

  private fun mediaNoun(context: Context, slide: Slide?): String {
    if (slide == null) {
      return ""
    }

    val contentType = slide.quoteTargetContentType

    return when {
      MediaUtil.isViewOnceType(contentType) -> context.getString(R.string.QuoteView_view_once_media)
      MediaUtil.isAudioType(contentType) -> context.getString(R.string.QuoteView_audio)
      MediaUtil.isVideoType(contentType) -> {
        if (slide.isVideoGif) context.getString(R.string.QuoteView_gif) else context.getString(R.string.QuoteView_video)
      }
      slide.hasSticker() -> context.getString(R.string.QuoteView_sticker)
      MediaUtil.isImageType(contentType) -> {
        if (MediaUtil.isGif(contentType)) context.getString(R.string.QuoteView_gif) else context.getString(R.string.QuoteView_photo)
      }
      // Documents and anything else Signal has no noun for: the file name is the only thing that
      // says what was replied to, and an empty summary leaves the line as just the author's name.
      else -> slide.fileName.orElse("")
    }
  }
}
