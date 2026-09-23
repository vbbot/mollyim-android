/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.light

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.ui.LightGrid
import com.thelightphone.sdk.ui.LightLazyScrollView
import com.thelightphone.sdk.ui.LightScrollBarPosition
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.lightClickable
import org.thoughtcrime.securesms.R

/**
 * One row of a [LightActionPanel].
 *
 * [label] is shown verbatim, so callers supply it in the Light Phone's bottom-bar grammar -- short
 * and upper case, the same register as `LightBottomBar`'s text items.
 */
data class LightPanelAction(
  val label: String,
  val onSelected: () -> Unit
)

/**
 * The Light Phone's bottom action panel: a black panel over the bottom of the screen carrying a list
 * of labelled actions, and a wide chevron on its bottom edge that dismisses it.
 *
 * This is the reference client's `ContextWindowOverlay` generalised. Its grammar is kept exactly --
 * half-screen black panel, centred `Button`-variant rows, tap targets that hug the label rather than
 * fill the row, a chevron zone the rows keep clear of -- with one change: Molly's lists are longer.
 * The reference only ever shows two to four rows and centres them; a long-press on an ordinary text
 * message here yields Reply/Forward/Copy/Multi-select/Info/Star/Delete, and a half-screen panel on
 * the LP3 (360dp x 413dp) holds about four [ROW_HEIGHT] rows. So the rows sit in a
 * [LightLazyScrollView] that is sized to its content while the content fits and clamped to the panel
 * once it does not, which keeps short lists centred and lets long ones scroll.
 *
 * Two details are not decoration:
 *
 * - **The tap target hugs the label.** The 44dp full-width row is layout spacing only. The reference
 *   client shipped full-row targets first and found them firing on taps far to the side of the text.
 * - **The panel swallows every pointer event in its area.** Compose dispatches pointer events to
 *   everything under the finger, and a background alone consumes nothing, so without this a tap in an
 *   empty region of the panel falls through to whatever sits beneath -- in the reference's case, a
 *   voice-note play button under the first row.
 */
@Composable
fun LightActionPanel(
  actions: List<LightPanelAction>,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .fillMaxHeight(PANEL_HEIGHT_FRACTION)
      .background(LightThemeTokens.colors.background)
      .pointerInput(Unit) {
        awaitEachGesture {
          awaitFirstDown(requireUnconsumed = false).consume()
          while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
            if (event.changes.none { it.pressed }) break
          }
        }
      }
  ) {
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        // The chevron's zone stays clear so that no row's tap target sits under it.
        .padding(bottom = CHEVRON_ZONE)
    ) {
      // `LightLazyScrollView` sizes its scroll thumb from a uniform row height expressed in grid
      // units, so the 44dp row is converted rather than guessed at -- otherwise the thumb would
      // drift from the rows it represents on any screen that is not exactly 360dp wide.
      val gridUnitDp = LocalConfiguration.current.screenWidthDp.toFloat() / LightGrid.WIDTH
      val contentHeight = (ROW_HEIGHT * actions.size).coerceAtMost(maxHeight)

      LightLazyScrollView(
        modifier = Modifier
          // Sizing the list to its content and centring it is what reproduces the reference's
          // vertically centred row stack without a `verticalArrangement` the SDK does not expose.
          .align(Alignment.Center)
          .fillMaxWidth()
          .height(contentHeight),
        // Outside would reserve a two-grid-unit gutter down the right edge and pull the centred
        // labels off centre with it. Inside overlays the track instead.
        scrollBarPosition = LightScrollBarPosition.Inside,
        uniformItemHeightGridUnits = ROW_HEIGHT.value / gridUnitDp
      ) {
        items(actions.size, key = { it }) { index ->
          val action = actions[index]

          Box(
            modifier = Modifier
              .height(ROW_HEIGHT)
              .fillMaxWidth(),
            contentAlignment = Alignment.Center
          ) {
            Box(
              modifier = Modifier.lightClickable(onClick = action.onSelected),
              contentAlignment = Alignment.Center
            ) {
              LightText(
                text = action.label,
                variant = LightTextVariant.Button,
                maxLines = 1
              )
            }
          }
        }
      }
    }

    Box(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .size(width = CHEVRON_TOUCH_WIDTH, height = CHEVRON_ZONE)
        .lightClickable(onClick = onDismiss),
      contentAlignment = Alignment.Center
    ) {
      Image(
        // Tinted rather than drawn as-is: the asset is a white glyph and the panel follows the
        // theme's content colour, exactly as `LightIcon` does for the SDK's own icons.
        painter = rememberLightTintedPainter(R.drawable.ic_light_chevron_down),
        contentDescription = stringResource(R.string.LightActionPanel__close),
        contentScale = ContentScale.FillBounds,
        modifier = Modifier.size(width = CHEVRON_WIDTH, height = CHEVRON_HEIGHT)
      )
    }
  }
}

/**
 * View-land host for [LightActionPanel], sized to the whole screen so that the panel can cover the
 * bottom bar while it is open and a tap anywhere above it can dismiss it.
 *
 * The scrim above the panel is deliberately *not* part of [LightActionPanel]: the reference client
 * keeps it in the screen rather than the overlay for the same reason, so that the panel stays a
 * panel and the screen decides what tapping away from it means.
 */
class LightActionPanelView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs) {

  /** The rows to show. Empty means the panel is not open; the host view hides itself. */
  val actions: MutableList<LightPanelAction> = mutableStateListOf()

  var onDismiss: (() -> Unit)? by mutableStateOf<(() -> Unit)?>(null)

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  }

  @Composable
  override fun Content() {
    if (actions.isEmpty()) {
      return
    }

    MollyLightTheme {
      Box(modifier = Modifier.fillMaxSize()) {
        // A tap in the band between the top bar and the panel dismisses. Top-padded clear of the
        // whole toolbar strip -- the status bar plus the Light top bar sitting under it -- so that
        // back and the contact name stay reachable, and stopping at the panel's top edge so that
        // taps on the panel itself never dismiss.
        Box(
          modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .fillMaxHeight(1f - PANEL_HEIGHT_FRACTION)
            .padding(top = dimensionResource(R.dimen.signal_m3_toolbar_height))
            .pointerInput(Unit) {
              detectTapGestures(onTap = { onDismiss?.invoke() })
            }
        )

        LightActionPanel(
          actions = actions,
          onDismiss = { onDismiss?.invoke() },
          modifier = Modifier.align(Alignment.BottomCenter)
        )
      }
    }
  }
}

/** The reference client's panel covers the bottom half of the screen exactly. */
private const val PANEL_HEIGHT_FRACTION = 0.5f

/** Bottom-bar-height rows, as the reference client uses. */
private val ROW_HEIGHT = 44.dp

/** The chevron's tap zone on the panel's bottom edge. The chevron itself touches the bottom edge. */
private val CHEVRON_ZONE = 38.dp
private val CHEVRON_TOUCH_WIDTH = 48.dp
private val CHEVRON_WIDTH = 17.dp
private val CHEVRON_HEIGHT = 10.dp
