/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items.light

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.core.widget.TextViewCompat
import com.thelightphone.sdk.ui.LightColors
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeColors
import com.thelightphone.sdk.ui.LightTypography
import com.thelightphone.sdk.ui.designVerticalPxToSp
import com.thelightphone.sdk.ui.lightTypography

/**
 * Applies The Light Phone SDK's typography scale to plain Android [TextView]s.
 *
 * The SDK is Compose-only and its `LightText` takes a `String`, so it cannot render the conversation
 * thread's message bodies -- those are `CharSequence`s carrying Signal's emoji image spans, mention
 * annotations, spoilers, formatting and link spans, and they must stay on `EmojiTextView` to survive.
 * The thread's date headers have the same problem from the other direction: `ConversationItemDecorations`
 * inflates a View and draws it straight into the `RecyclerView` canvas, which a `ComposeView` cannot do.
 *
 * So rather than transcribing the Light type scale into XML -- which would silently drift from the SDK
 * and, worse, could not name the Akkurat font at all (the LP3 supplies it as a *system* font file; Molly
 * bundles no copy) -- this reads the very same [lightTypography] tokens the Compose components read and
 * resolves the very same `FontFamily` through Compose's own font resolver. One seam, no second source of
 * truth.
 */
object LightItemStyle {

  /**
   * Resolved once for the process. Font resolution walks the system font list and building the scale
   * allocates a `FontFamily` per weight, neither of which can happen per bind while a `RecyclerView`
   * is flinging. Keyed off the *application* context: none of this varies by Activity, and holding an
   * Activity in a process-lifetime cache would leak it.
   */
  private class Resolved(context: Context) {
    val typography: LightTypography = lightTypography(context)

    val typeface: Typeface = createFontFamilyResolver(context)
      .resolve(
        fontFamily = typography.paragraph.fontFamily,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Normal,
        fontSynthesis = FontSynthesis.All
      )
      .value as? Typeface ?: Typeface.DEFAULT
  }

  @Volatile
  private var resolved: Resolved? = null

  private fun resolved(context: Context): Resolved {
    return resolved ?: synchronized(this) {
      resolved ?: Resolved(context.applicationContext).also { resolved = it }
    }
  }

  /**
   * The Akkurat [Typeface] behind the SDK's `FontFamily`, or the platform default when the device has
   * no Akkurat (any non-LP3 build, and Robolectric).
   */
  private fun typeface(context: Context): Typeface = resolved(context).typeface

  /** The [TextStyle] token backing [variant], unscaled. */
  fun style(context: Context, variant: LightTextVariant): TextStyle {
    val typography = resolved(context).typography
    return when (variant) {
      LightTextVariant.Title -> typography.title
      LightTextVariant.Subtitle -> typography.subtitle
      LightTextVariant.Heading -> typography.heading
      LightTextVariant.Subheading -> typography.subheading
      LightTextVariant.Copy -> typography.copy
      LightTextVariant.Button -> typography.button
      LightTextVariant.Paragraph -> typography.paragraph
      LightTextVariant.ParagraphWide -> typography.paragraphWide
      LightTextVariant.Detail -> typography.detail
      LightTextVariant.Fine -> typography.fine
      LightTextVariant.Superfine -> typography.superfine
      LightTextVariant.Micro -> typography.micro
    }
  }

  /**
   * [variant]'s token with the SDK's screen-height scaling already folded in, i.e. exactly what
   * `LightText` would render. Used for the one row element that is Compose rather than View: the
   * group sender name, which Signal draws with `SenderNameWithLabelView`.
   */
  fun composeStyle(context: Context, variant: LightTextVariant): TextStyle {
    val style = style(context, variant)
    return style.copy(
      fontSize = style.fontSize.value.designVerticalPxToSp(context).sp,
      lineHeight = if (style.lineHeight.isSpecified) style.lineHeight.value.designVerticalPxToSp(context).sp else style.lineHeight,
      letterSpacing = if (style.letterSpacing.isSpecified) style.letterSpacing.value.designVerticalPxToSp(context).sp else style.letterSpacing
    )
  }

  /**
   * Styles [textView] as [variant] would render through `LightText`: same family, same size, same line
   * height, same tracking.
   *
   * `LightText` expresses tracking in `sp` (an absolute offset) while [TextView.setLetterSpacing] takes
   * `em` (a multiple of the text size), hence the division.
   *
   * [applyLineHeight] pins the line box to the token's line height, which is what makes stacked
   * paragraphs sit on the Light rhythm. Pass `false` for a single line that carries a scaled-up
   * glyph -- see [LightQuoteLine] -- where a fixed line box would clip the glyph instead.
   */
  @JvmOverloads
  fun apply(textView: TextView, variant: LightTextVariant, applyLineHeight: Boolean = true) {
    val context = textView.context
    val style = style(context, variant)

    textView.typeface = typeface(context)

    val sizeSp = style.fontSize.value.designVerticalPxToSp(context)
    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)

    val lineHeight = style.lineHeight
    if (applyLineHeight && lineHeight.isSpecified) {
      val lineHeightSp = lineHeight.value.designVerticalPxToSp(context)
      TextViewCompat.setLineHeight(textView, TypedValue.COMPLEX_UNIT_SP, lineHeightSp)
    }

    val letterSpacing = style.letterSpacing
    textView.letterSpacing = if (letterSpacing.isSpecified && style.fontSize.value != 0f) {
      letterSpacing.value / style.fontSize.value
    } else {
      0f
    }
  }

  /**
   * The palette `MollyLightTheme` installs on the Compose side, so View-land and Compose-land agree.
   *
   * Always [LightThemeColors.Dark]. This used to follow Molly's own light/dark setting; the Light
   * Phone III has no day mode and the rest of the app is now pinned black to match, so there is no
   * longer anything for it to follow. [context] is kept so that the accessors below -- and the rest
   * of this object, whose typography genuinely is context-derived -- keep one shape.
   */
  fun colors(context: Context): LightColors = LightThemeColors.Dark

  /**
   * The foreground colour for bubble-less message content.
   *
   * This one matters more than it looks: Signal picks body and footer colours to sit on a *filled*
   * chat-colour bubble, so an outgoing message is normally near-white. Drawn bubble-less on the LP3's
   * white background that is invisible text, so every Light row re-colours its content to the theme's
   * `content` token. Supplied through `LightConversationItemTheme` so that the item's presenters
   * paint it directly, rather than being corrected after the fact.
   */
  fun contentColor(context: Context): Int = colors(context).content.toArgb()

  /**
   * The dimmed foreground, for content that is subordinate to a message rather than part of it --
   * today just the quoted line of a reply. See [LightQuoteLine].
   */
  fun contentSecondaryColor(context: Context): Int = colors(context).contentSecondary.toArgb()

  /** The surface colour, for the few elements that have to occlude what is behind them. */
  fun backgroundColor(context: Context): Int = colors(context).background.toArgb()
}
