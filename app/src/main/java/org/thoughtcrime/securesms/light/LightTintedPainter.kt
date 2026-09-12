/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.light

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightThemeTokens

/**
 * [LightBarButton.Icon] draws its painter with `Image`, which applies no tint: SDK icons are white
 * glyphs that `LightIcon` recolours, but Molly's own drawables carry their own colours and would be
 * invisible (or off-palette) on the Light theme's black background. This recolours a Molly drawable
 * the same way the SDK recolours its own, so the two can sit side by side in one bar.
 */
@Composable
fun rememberLightTintedPainter(@DrawableRes id: Int): Painter {
  val painter = painterResource(id)
  val tint = LightThemeTokens.colors.content
  return remember(painter, tint) { LightTintedPainter(painter, tint) }
}

private class LightTintedPainter(private val delegate: Painter, tint: Color) : Painter() {
  private val tintFilter = ColorFilter.tint(tint)

  override val intrinsicSize: Size
    get() = delegate.intrinsicSize

  override fun DrawScope.onDraw() {
    with(delegate) {
      draw(size = size, colorFilter = tintFilter)
    }
  }
}
