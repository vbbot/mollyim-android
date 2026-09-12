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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.sp
import com.thelightphone.sdk.ui.LightGrid
import com.thelightphone.sdk.ui.LightLazyScrollView
import com.thelightphone.sdk.ui.LightScrollBarPosition
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.lightClickable
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.visible

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
 * The reaction keys, in the LP3 emoji panel's exact layout: three rows of eight -- faces, then
 * hands, then symbols.
 *
 * Copied key for key from the reference client, which captured them from the Light Phone's own
 * emoji panel. This is a *fixed* set, and it is what replaces Signal's horizontal scrubber and the
 * "any emoji" slot on its end: the Light Phone offers these twenty-four and no picker. Reactions
 * that arrive from other clients carrying arbitrary emoji are a separate concern and still render
 * through Molly's own emoji pipeline -- this list only bounds what *this* client can send.
 */
val LIGHT_REACTION_KEYS: List<List<String>> = listOf(
  listOf("😅", "😊", "🙄", "😍", "😜", "😂", "😭", "😎"),
  listOf("👏", "👍", "👎", "🤞", "✌️", "👌", "👋", "🙏"),
  listOf("✨", "🔥", "❤️", "💔", "🏆", "🎯", "👑", "👀")
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
  LightPanelFrame(onDismiss = onDismiss, modifier = modifier) {
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
  }
}

/**
 * The panel's second level: [LIGHT_REACTION_KEYS] as a 3x8 grid, one tap to react.
 *
 * This is what stands in for Signal's horizontal emoji scrubber, and for the "any emoji" slot on the
 * end of it that opened a full Material picker. The grid is the whole vocabulary; there is no way
 * further down.
 *
 * The keys sit at the top of the panel rather than centred, as the reference client's do, which is
 * also what keeps them clear of the chevron: three [EMOJI_CELL] rows plus the top padding come to
 * 142dp, and the panel is half of the LP3's 413dp.
 */
@Composable
fun LightReactionKeys(
  onKeySelected: (String) -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  LightPanelFrame(onDismiss = onDismiss, modifier = modifier) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = EMOJI_GRID_TOP_PADDING)
    ) {
      LIGHT_REACTION_KEYS.forEach { row ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .height(EMOJI_CELL)
        ) {
          row.forEach { key ->
            Box(
              modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .lightClickable(onClick = { onKeySelected(key) }),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = key,
                fontSize = EMOJI_FONT_SIZE,
                // Explicit, not inherited. These are colour glyphs on every device that has an
                // emoji font, but a monochrome fallback would take the ambient content colour --
                // and on a panel painted `background` that is exactly how a key ends up invisible.
                color = LightThemeTokens.colors.content
              )
            }
          }
        }
      }
    }
  }
}

/**
 * The panel itself, without its contents: the half-screen black slab, the pointer events it eats,
 * and the chevron that dismisses it. Shared by both of the panel's levels so that descending into
 * the reaction keys cannot change the shape of the thing under the finger.
 */
@Composable
private fun LightPanelFrame(
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit
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
    content()

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
 * View-land host for the two-level panel, sized to the whole screen so that the panel can cover the
 * bottom bar while it is open and a tap anywhere above it can dismiss it.
 *
 * The level lives here rather than in either composable because the panel's rows are supplied from
 * outside: a caller hands [show] a list in which one row descends by calling [showReactionKeys],
 * and the host swaps [LightActionPanel] for [LightReactionKeys] underneath it.
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
  private val actions: MutableList<LightPanelAction> = mutableStateListOf()

  /** Set while the panel has a reaction level to descend to. */
  private var onKeySelected: ((String) -> Unit)? by mutableStateOf<((String) -> Unit)?>(null)

  private var showingReactionKeys: Boolean by mutableStateOf(false)

  var onDismiss: (() -> Unit)? by mutableStateOf<(() -> Unit)?>(null)

  /** Whether the panel is open at all, at either level. */
  val isOpen: Boolean
    get() = actions.isNotEmpty()

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  }

  /**
   * Opens the panel on [actions], and makes itself visible.
   *
   * Always on the action rows: the reaction keys are a level *below* a row, and a panel that
   * reopened already on the keys would leave the caller's action list unreachable. The reference
   * client gets the same guarantee by declaring the level inside the overlay, below its null check,
   * so that closing drops the state; resetting it here is that, made explicit, because this view
   * outlives any one opening of the panel.
   *
   * [onKeySelected] is what makes the second level reachable at all. Leave it null -- as the call
   * menu does -- and [showReactionKeys] does nothing.
   */
  fun show(actions: List<LightPanelAction>, onKeySelected: ((String) -> Unit)? = null) {
    this.actions.clear()
    this.actions.addAll(actions)
    this.onKeySelected = onKeySelected
    this.showingReactionKeys = false
    this.visible = true
  }

  /** Closes the panel at whichever level it is on, and hides itself. */
  fun close() {
    actions.clear()
    onKeySelected = null
    showingReactionKeys = false
    visible = false
  }

  /** Descends to the reaction keys. A row passed to [show] calls this instead of completing. */
  fun showReactionKeys() {
    if (onKeySelected != null) {
      showingReactionKeys = true
    }
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

        val keyHandler = onKeySelected

        if (showingReactionKeys && keyHandler != null) {
          LightReactionKeys(
            onKeySelected = keyHandler,
            onDismiss = { onDismiss?.invoke() },
            modifier = Modifier.align(Alignment.BottomCenter)
          )
        } else {
          LightActionPanel(
            actions = actions,
            onDismiss = { onDismiss?.invoke() },
            modifier = Modifier.align(Alignment.BottomCenter)
          )
        }
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

// Reaction key geometry, measured from the LP3's own emoji panel (1080x1240 at 480dpi, so px / 3).
/** Emoji cell height -- the panel's row centres sit about 140px, i.e. 46.7dp, apart. */
private val EMOJI_CELL = 46.dp
private val EMOJI_GRID_TOP_PADDING = 4.dp

/** The LP3 panel draws its glyphs at about 32dp, which the reference client found too big in use. */
private val EMOJI_FONT_SIZE = 24.sp
