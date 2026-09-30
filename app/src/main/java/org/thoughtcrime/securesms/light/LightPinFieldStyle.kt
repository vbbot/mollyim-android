/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.light

import android.view.Gravity
import android.widget.EditText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import org.thoughtcrime.securesms.conversation.v2.items.light.LightItemStyle
import kotlin.math.roundToInt

/**
 * The Light Phone III presentation for a Signal PIN **entry field**, on the View side.
 *
 * There are two of these in the app: the create/confirm PIN screen's field, and the periodic PIN
 * reminder's field. Both are credential entry, and both stay real [EditText]s -- see
 * [org.thoughtcrime.securesms.lock.v2.LightSvrPinView] for why the Light design language does not
 * route them through `LightTextField`.
 *
 * The shape is the SDK's own text-entry shape, taken from `LightTextField`: the value in `Copy`,
 * with a rule beneath it. The create/confirm screen draws that rule in Compose; the reminder dialog
 * draws its own.
 *
 * Every dimension comes from the same [gridUnitsAsDp] token the Compose components read, and the
 * type scale and palette come from [LightItemStyle], which resolves the SDK's own `LightText` tokens
 * (including the Akkurat typeface, which the LP3 supplies as a *system* font that Molly bundles no
 * copy of). One seam, no second source of truth.
 */
object LightPinFieldStyle {

  /** Internal vertical padding, so a one-line PIN is not pinned to the top of its box. */
  private const val PADDING_VERTICAL_UNITS = 0.5f

  /**
   * The SDK's minimum touch target: 3.3 grid units, which is 44dp on the LP3 reference screen
   * (360dp x 413dp) in both axes. Callers pick the axis that matches the dimension being sized --
   * [gridUnitsAsDp] for a width, `verticalGridUnitsAsDp` for a height.
   */
  const val MINIMUM_TOUCH_UNITS = 3.3f

  /**
   * Restyles a PIN [EditText] without touching anything about how it behaves.
   *
   * Deliberately does *not* set the input type, the transformation method, the IME options, the
   * text watcher or the autofill mode. Those belong to the credential flow -- `PinKeyboardType`
   * owns the first two, the fragment that owns the screen owns the rest -- and a styling seam that
   * can reach them is a styling seam that can weaken a PIN.
   */
  @JvmStatic
  @JvmOverloads
  fun apply(editText: EditText, centerAligned: Boolean = true) {
    val context = editText.context

    // Typeface, size, line height and tracking, straight from the SDK's `Copy` token.
    LightItemStyle.apply(editText, LightTextVariant.Copy)
    editText.setTextColor(LightItemStyle.contentColor(context))
    editText.setHintTextColor(LightItemStyle.contentSecondaryColor(context))

    // The Material filled/outline box background is a second border under the rule the host draws,
    // and it carries Material's own tint, which is off-palette on black.
    editText.background = null
    editText.backgroundTintList = null

    editText.gravity = if (centerAligned) {
      Gravity.CENTER_HORIZONTAL or Gravity.CENTER_VERTICAL
    } else {
      Gravity.START or Gravity.CENTER_VERTICAL
    }

    val vertical = PADDING_VERTICAL_UNITS.gridUnitsAsDp(context).roundToInt()
    editText.setPadding(editText.paddingLeft, vertical, editText.paddingRight, vertical)
  }
}
