/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items.light

import android.app.Application
import android.graphics.Color
import android.widget.TextView
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.thelightphone.sdk.ui.LightTextVariant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The colour half of the Light message row.
 *
 * A bubble-less row has to paint its text in the theme's full-contrast content token. Signal's own
 * body and footer colours assume a filled chat-colour bubble underneath -- outgoing text is near-white
 * so that it reads on blue -- and picking one of those up here would render a sent message invisible
 * against the LP3's white background. That is the single most likely way this screen breaks, and it
 * has a history: this project has already shipped a thread whose message text rendered invisible.
 *
 * Note what is *not* covered here. The layouts themselves cannot be inflated in a unit test:
 * `EmojiTextView`'s constructor calls into `EmojiSource.getLatest`, which blocks forever without the
 * app's dependency graph. So the guideline geometry is pinned through [LightMessageColumn] against the
 * reference client (see `LightMessageRowGeometryTest`) rather than by measuring an inflated row, and
 * the fact that `LightTextOnlyViewHolder` detaches the superclass's `ChatColorsDrawable` is checked by
 * reading the code, not by a test. Both want eyes on a device.
 */
@RunWith(RobolectricTestRunner::class)
// Native graphics, so that a Typeface reports the weight it was really created at.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class LightItemStyleTest {

  private val context = RuntimeEnvironment.getApplication()

  @Test
  fun `content and surface are opaque and sit at opposite ends of the greyscale`() {
    val content = LightItemStyle.contentColor(context)
    val background = LightItemStyle.backgroundColor(context)

    assertThat(Color.alpha(content)).isEqualTo(255)
    assertThat(Color.alpha(background)).isEqualTo(255)

    // Black on white or white on black; the SDK's palettes are pure either way, so the sum of the
    // channels is 0 for one and 3 * 255 for the other. A mid-tone here would mean a secondary or
    // bubble-relative colour had crept in.
    val contentInk = Color.red(content) + Color.green(content) + Color.blue(content)
    val surfaceInk = Color.red(background) + Color.green(background) + Color.blue(background)

    assertThat(minOf(contentInk, surfaceInk)).isEqualTo(0)
    assertThat(maxOf(contentInk, surfaceInk)).isEqualTo(765)
  }

  /**
   * A View-hosted label has to carry its variant's weight, not just its size: `Button` is Medium on
   * the Compose side, so a View-hosted button drawn Regular looks lighter than the Compose one next
   * to it. The expectation is each variant's own token weight, read from the SDK.
   */
  @Test
  fun `a styled view takes its variant's weight`() {
    for (variant in listOf(LightTextVariant.Button, LightTextVariant.Copy, LightTextVariant.Title)) {
      val view = TextView(context)
      LightItemStyle.apply(view, variant)

      val expected = LightItemStyle.style(context, variant).fontWeight!!.weight
      assertThat(view.typeface.weight, variant.name).isEqualTo(expected)
    }
  }
}
