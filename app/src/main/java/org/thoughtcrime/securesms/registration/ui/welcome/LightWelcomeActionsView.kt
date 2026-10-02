/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.welcome

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import org.thoughtcrime.securesms.light.MollyLightTheme
import java.util.Locale

/**
 * One row of the Welcome screen's action stack.
 *
 * [variant] and [lighten] are the whole of the visual hierarchy available on a device with one
 * foreground colour: there is no accent to make a "primary" button, so a row that means more has to
 * be set in the content colour and at the heavier weight, and a row that is a footnote says so.
 */
@Immutable
data class LightWelcomeAction(
  val label: String,
  val onClick: () -> Unit,
  val variant: LightTextVariant = LightTextVariant.Button,
  val lighten: Boolean = false
)

/**
 * The Welcome screen's actions, as a bottom-anchored stack of centred text rows.
 *
 * These were four `MaterialButton`s restyled at runtime by `LightRegistrationViewStyle.action()`:
 * transparent background, no stroke, no elevation, all-caps label, a minimum height read off the
 * grid. That gets the pixels roughly right and leaves the widget underneath wrong -- a Material
 * button still brings its own insets, padding, font and state handling to a screen that is supposed
 * to have none of them. This is the first screen a new user ever sees, so it is the one place
 * where a Material widget in a Light costume is most visible.
 *
 * The grammar is [org.thoughtcrime.securesms.light.LightActionPanel]'s, in particular the part
 * that is easy to get wrong: **the tap target hugs the label.** The 4-unit row is layout spacing;
 * the target inside it is the text. The reference client shipped full-width row targets first and
 * found them firing on taps far to the side of the words.
 *
 * The screen's wordmark and Signal headline are gone, as they are from the whole Light port, so the
 * stack is what this screen *is*, and it is anchored to the bottom with the black above it.
 */
class LightWelcomeActionsView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs) {

  /**
   * The rows, in the order they are presented. Empty renders nothing but the black, which is what
   * the screen looks like for the one frame between inflation and the host setting them.
   */
  var actions: List<LightWelcomeAction> by mutableStateOf(emptyList())

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  }

  @Composable
  override fun Content() {
    MollyLightTheme {
      LightWelcomeActions(actions = actions)
    }
  }
}

/**
 * The stack itself, split out of [LightWelcomeActionsView] so that it can be composed, and so
 * measured, without an Android view in the way.
 */
@Composable
fun LightWelcomeActions(
  actions: List<LightWelcomeAction>,
  modifier: Modifier = Modifier
) {
  Column(
    verticalArrangement = Arrangement.spacedBy(ROW_SPACING_UNITS.gridUnitsAsDp()),
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier
      .fillMaxWidth()
      // Painted explicitly: neither `MollyLightTheme` nor this app's window background is
      // guaranteed to be black, and the resting state of the Light Phone is.
      .background(LightThemeTokens.colors.background)
      .padding(
        start = GUTTER_UNITS.gridUnitsAsDp(),
        end = GUTTER_UNITS.gridUnitsAsDp(),
        bottom = STACK_BOTTOM_UNITS.gridUnitsAsDp()
      )
  ) {
    actions.forEach { action ->
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .height(ROW_HEIGHT_UNITS.gridUnitsAsDp())
          .lightClickable(role = Role.Button, onClick = action.onClick)
      ) {
        LightText(
          // Upper case here rather than at the call site: the labels arrive from the registration
          // feature module as prose ("Continue", "Restore or transfer"), and this is the one place
          // that decides the screen's register. Leaving it to callers made this the only action
          // surface in the app rendering sentence case, next to a panel that uppercases and a
          // bottom bar that uppercases.
          text = action.label.uppercase(Locale.getDefault()),
          variant = action.variant,
          lighten = action.lighten
        )
      }
    }
  }
}

/**
 * 4 grid units: the same action row height the bottom bar uses, and comfortably over the 44dp
 * minimum touch target (3.3 units on the LP3). Read off the grid rather than typed in as dp, so the
 * target tracks the device's width instead of assuming a 360dp screen.
 */
internal const val ROW_HEIGHT_UNITS = 4f

/** The gap between rows. */
internal const val ROW_SPACING_UNITS = 1f

/** Side inset, so a centred label is not flush to the bezel. Matches the registration gutter. */
private const val GUTTER_UNITS = 2f

/**
 * Clearance between the last row and the bottom of the screen.
 *
 * This replaces a `layout_marginBottom="48dp"` in the layout, which was 3.6 grid units -- not a
 * number the grid produces, and one that did not move with the screen. A grid-derived gap keeps the
 * stack off the navigation bar at whatever width the device actually is.
 */
internal const val STACK_BOTTOM_UNITS = 2f
