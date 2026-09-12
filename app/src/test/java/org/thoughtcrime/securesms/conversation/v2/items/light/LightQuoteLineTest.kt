/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items.light

import android.app.Application
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isTrue
import com.thelightphone.sdk.ui.LightTextVariant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.model.Quote
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.mms.SlideDeck
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * The reply line: one dimmed, truncated line above the body, led by a glyph, with no container.
 *
 * Everything asserted here is a design decision that would otherwise only be visible on the device:
 * that the line is *one* line rather than a growing block, that it steps down the type scale and the
 * palette rather than being boxed, and that the glyph is a text character rather than something that
 * can come back as a colour emoji sprite.
 *
 * The row that hosts it cannot be inflated in a unit test -- the layouts carry an `EmojiTextView`,
 * whose constructor calls into `EmojiSource.getLatest` and blocks forever without the app's
 * dependency graph -- so the styling is checked against a bare [TextView] driven through the same
 * entry point the view holders use.
 */
@RunWith(RobolectricTestRunner::class)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class LightQuoteLineTest {

  private val context = RuntimeEnvironment.getApplication()

  private fun quote(
    text: CharSequence? = "see you at three",
    missing: Boolean = false,
    type: QuoteModel.Type = QuoteModel.Type.NORMAL
  ): Quote {
    return Quote(1L, RecipientId.from(1L), text, missing, SlideDeck(), emptyList(), type)
  }

  @Test
  fun `the glyph leads the line and is not an emoji code point`() {
    val line = LightQuoteLine.buildLine(context, "Steinar", quote()).toString()

    assertThat(line).isEqualTo("↶ Steinar: see you at three")

    // U+21B6, the mirror of the reference client's U+21B7 forward glyph. U+21A9 -- the other obvious
    // reply arrow -- carries an emoji presentation and would be liable to render as a colour sprite.
    assertThat(LightQuoteLine.GLYPH.codePointAt(0)).isEqualTo(0x21B6)
  }

  @Test
  fun `a multi-line original folds onto one line`() {
    val line = LightQuoteLine.buildLine(context, "Steinar", quote("see you\nat three\n")).toString()

    assertThat(line).isEqualTo("↶ Steinar: see you at three")
    assertThat(line.contains('\n')).isFalse()
  }

  @Test
  fun `a deleted original says so rather than reading as an empty reply`() {
    val line = LightQuoteLine.buildLine(context, "Steinar", quote(text = null, missing = true)).toString()

    assertThat(line).isEqualTo("↶ Steinar: " + context.getString(R.string.QuoteView_original_missing))
  }

  @Test
  fun `an original with nothing to quote leaves the line as the author alone`() {
    val line = LightQuoteLine.buildLine(context, "Steinar", quote(text = "")).toString()

    // No trailing colon: an empty summary must not leave "Steinar:" dangling.
    assertThat(line).isEqualTo("↶ Steinar")
  }

  @Test
  fun `the line is truncated to one line, dimmed, and a step below the body`() {
    val view = TextView(context)

    LightQuoteLine.style(view)

    assertThat(view.maxLines).isEqualTo(1)
    assertThat(view.ellipsize == TextUtils.TruncateAt.END).isTrue()

    // Dimmed: contentSecondary, not the full-contrast content token the body and timestamp use.
    assertThat(view.currentTextColor).isEqualTo(LightItemStyle.contentSecondaryColor(context))
    assertThat(view.currentTextColor == LightItemStyle.contentColor(context)).isFalse()

    // A step down the scale from the body, and a step up from the timestamp directly above it --
    // three adjacent lines at one size would read as a single block.
    val body = LightItemStyle.style(context, LightTextVariant.Paragraph).fontSize.value
    val quoteLine = LightItemStyle.style(context, LightTextVariant.Detail).fontSize.value
    val timestamp = LightItemStyle.style(context, LightTextVariant.Superfine).fontSize.value

    assertThat(quoteLine).isLessThan(body)
    assertThat(quoteLine).isGreaterThan(timestamp)
  }

  /**
   * The other end of a reply. Upstream keeps a message that has been quoted off the text row
   * altogether because its only affordance for "there are replies to this" is a filled circle hung
   * off a bubble edge -- which on the device meant an ordinary sent message growing a chat-colour
   * bubble the moment someone answered it. The row says it in words instead.
   */
  @Test
  fun `a message that has been replied to says so in the same vocabulary as the quote line`() {
    val line = LightQuoteLine.buildRepliesLine(context).toString()

    assertThat(line).isEqualTo("↶ " + context.getString(R.string.MessageQuotesBottomSheet_replies))

    // The same glyph at both ends: ↶ means "reply" wherever it appears in the thread. The reference
    // client's ↷ is not borrowed, because there it means forwarded.
    assertThat(line.startsWith(LightQuoteLine.GLYPH)).isTrue()
  }

  @Test
  fun `the replies line is shown only when there are replies`() {
    val view = TextView(context)

    assertThat(LightQuoteLine.presentRepliesIndicator(view, hasBeenQuoted = false)).isFalse()
    assertThat(view.visibility).isEqualTo(View.GONE)

    assertThat(LightQuoteLine.presentRepliesIndicator(view, hasBeenQuoted = true)).isTrue()
    assertThat(view.visibility).isEqualTo(View.VISIBLE)
    assertThat(view.text.toString()).isEqualTo(LightQuoteLine.buildRepliesLine(context).toString())

    // A recycled row must not keep the previous message's affordance.
    assertThat(LightQuoteLine.presentRepliesIndicator(view, hasBeenQuoted = false)).isFalse()
    assertThat(view.visibility).isEqualTo(View.GONE)
  }
}
