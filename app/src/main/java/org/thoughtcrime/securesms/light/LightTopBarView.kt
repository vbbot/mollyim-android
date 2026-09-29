/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.light

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIconConfiguration
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter

/**
 * One slot in a [LightAppTopBar].
 *
 * The design language has no text labels in a bar -- back is an icon, and so is everything else --
 * so an action is always an icon plus a content description and nothing more.
 */
sealed interface LightBarAction {

  val contentDescription: String?
  val onClick: () -> Unit

  /** An icon from the Light SDK's own set, which [com.thelightphone.sdk.ui.LightIcon] tints. */
  data class Sdk(
    override val contentDescription: String?,
    val icon: LightIconConfiguration,
    override val onClick: () -> Unit
  ) : LightBarAction

  /**
   * A drawable of the host's own -- a `MenuItem` icon, typically -- recoloured to the Light content
   * colour, because those carry Signal's Material tint and would be off-palette on black.
   */
  data class Drawable(
    override val contentDescription: String?,
    val drawable: Drawable,
    override val onClick: () -> Unit
  ) : LightBarAction
}

/**
 * The Light Phone top bar, for a screen that is hosted in plain Android views.
 *
 * `LightTopBar` gives a bar three grid units tall with at most one action per side. It is the
 * replacement for the Material `Toolbar` that Molly's pushed screens inherited from Signal, and it
 * exists as a view host because a great many of those screens are still view-hosted: registering a
 * Compose tree over a screen that drives itself through listeners, timers and view models buys
 * nothing and costs the accessibility nodes those controls already have.
 *
 * It paints its own black. `LightTopBar` deliberately supplies no background, and scoping
 * `MollyLightTheme` does not paint surfaces, so a host that did not would show the window behind it.
 *
 * **A null slot is not a dead button.** A root screen passes no [leftAction] -- there is nothing to
 * go back to -- and the bar renders the SDK's spacer there instead of an inert chevron.
 */
@Composable
fun LightAppTopBar(
  title: String?,
  leftAction: LightBarAction?,
  rightAction: LightBarAction?,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .background(LightThemeTokens.colors.background)
  ) {
    LightTopBar(
      leftButton = leftAction?.toBarButton(),
      center = title?.takeIf { it.isNotBlank() }?.let { LightTopBarCenter.Text(it) },
      rightButton = rightAction?.toBarButton()
    )
  }
}

@Composable
private fun LightBarAction.toBarButton(): LightBarButton = when (this) {
  is LightBarAction.Sdk -> LightBarButton.LightIcon(
    icon = icon,
    onClick = onClick,
    contentDescription = contentDescription
  )

  is LightBarAction.Drawable -> LightBarButton.Icon(
    painter = rememberLightTintedDrawablePainter(drawable),
    onClick = onClick,
    contentDescription = contentDescription
  )
}

/**
 * Rasterises a host drawable once through a tinted `Canvas` and hands back a Compose [Painter].
 *
 * `LightBarButton.Icon` draws with `Image`, which applies no tint: SDK icons are white glyphs that
 * `LightIcon` recolours, but a `MenuItem`'s icon is an ordinary framework drawable still carrying
 * Signal's Material tint. `LightTintedPainter` covers the same need for a painter that is already a
 * Compose painter; this covers the case where the source is still an Android `Drawable`.
 *
 * `LightBarButton.Icon` draws with `ContentScale.Fit` into a 2-unit box, so the bitmap only needs to
 * be big enough not to look soft.
 */
@Composable
private fun rememberLightTintedDrawablePainter(drawable: Drawable): Painter {
  val tint = LightThemeTokens.colors.content

  return remember(drawable, tint) {
    val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: DEFAULT_RASTER_PX
    val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: DEFAULT_RASTER_PX

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, width, height)
    drawable.setColorFilter(PorterDuffColorFilter(tint.toArgb(), PorterDuff.Mode.SRC_IN))
    drawable.draw(Canvas(bitmap))

    bitmap.asImageBitmap()
  }
}

/**
 * View-land host for [LightAppTopBar], so a view-hosted screen can keep driving a top bar the way it
 * drove the `Toolbar` -- by assigning fields.
 */
class LightTopBarView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs) {

  var title: String? by mutableStateOf(null)

  var leftAction: LightBarAction? by mutableStateOf(null)
  var rightAction: LightBarAction? by mutableStateOf(null)

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  }

  @Composable
  override fun Content() {
    val left = leftAction
    val right = rightAction

    MollyLightTheme {
      LightAppTopBar(
        title = title,
        leftAction = left,
        rightAction = right
      )
    }
  }

  companion object {
    /**
     * The SDK's own "navigate up" affordance, for a screen the user was pushed to. The content
     * description is required rather than defaulted so no user-facing string is chosen here.
     */
    fun backAction(contentDescription: String, onClick: () -> Unit): LightBarAction =
      LightBarAction.Sdk(
        contentDescription = contentDescription,
        icon = LightIcons.BACK,
        onClick = onClick
      )
  }
}

private const val DEFAULT_RASTER_PX = 96
