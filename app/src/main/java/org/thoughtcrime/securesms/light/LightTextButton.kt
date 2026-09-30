/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.light

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.thelightphone.sdk.ui.LightHapticFeedback
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.verticalGridUnitsAsDp
import org.thoughtcrime.securesms.conversation.v2.items.light.LightItemStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A Light Phone III text action, for a screen that is hosted in plain Android views.
 *
 * The SDK's actions are Compose (`lightClickable` over `LightText`, and the bar items), so a
 * view-hosted screen has no way to reach them -- and a `MaterialButton` on a black panel brings the
 * whole Material palette with it. This is the app-local control the design language allows for that
 * gap, and it reproduces the Compose behaviour rather than approximating it:
 *
 * - **One buzz on finger-down**, through the SDK's own [LightHapticFeedback.click], gated on the
 *   same system preference `Modifier.lightClickable` is gated on. A tap on the Light Phone is
 *   confirmed by feel, because the user is often not looking straight at the screen.
 * - **No press indication.** `lightClickable` passes `indication = null`; the platform ripple is
 *   suppressed here too, so a press looks like a rest plus a buzz.
 * - **Disabled means inert and still readable** -- `#BBBBBB` on black, not hidden and not an alpha
 *   fade over a background that is already black.
 * - **Announced as a button.** A bare `TextView` with a click listener is announced as text; this
 *   keeps the class name a `MaterialButton` used to supply, so a screen-reader user is not left
 *   guessing that a label is activatable.
 *
 * It stays an ordinary clickable View: `setOnClickListener` and `setEnabled` work as they always
 * did, which is the point -- hosts keep their own listeners and validation.
 */
class LightTextButton @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AppCompatTextView(context, attrs) {

  private val hapticsEnabled: Boolean = systemHapticsEnabled(context)

  init {
    LightItemStyle.apply(this, LightTextVariant.Button)

    setTextColor(
      ColorStateList(
        arrayOf(
          intArrayOf(android.R.attr.state_enabled),
          intArrayOf(-android.R.attr.state_enabled)
        ),
        intArrayOf(
          LightItemStyle.contentColor(context),
          LightItemStyle.contentSecondaryColor(context)
        )
      )
    )

    gravity = Gravity.CENTER
    // Explicitly null: a `TextView` inside a Material dialog can still pick up a background from
    // the theme, and a ripple is not this design's press treatment.
    background = null
    isClickable = true
    isFocusable = true

    // The SDK publishes no minimum touch constant, so the app keeps one. It lives on
    // `LightPinFieldStyle` because that is where the PIN screens already read it from, and 3.3 grid
    // units is 44dp on the LP3 reference screen in both axes.
    minimumWidth = (LightPinFieldStyle.MINIMUM_TOUCH_UNITS * 1f.gridUnitsAsDp(context)).roundToInt()
    minimumHeight = (LightPinFieldStyle.MINIMUM_TOUCH_UNITS * 1f.verticalGridUnitsAsDp(context)).roundToInt()

    val horizontal = (PADDING_UNITS * 1f.gridUnitsAsDp(context)).roundToInt()
    val vertical = (PADDING_UNITS * 1f.verticalGridUnitsAsDp(context)).roundToInt()
    setPaddingRelative(horizontal, vertical, horizontal, vertical)

    setOnTouchListener { _, event ->
      // Fires on finger-down, like `lightClickable`, and returns false so the platform still
      // delivers the click.
      if (event.actionMasked == MotionEvent.ACTION_DOWN && isEnabled && hapticsEnabled) {
        LightHapticFeedback.click(context)
      }
      false
    }

    ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
      override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
        super.onInitializeAccessibilityNodeInfo(host, info)
        info.className = "android.widget.Button"
      }
    })
  }

  /**
   * Upper cases at the display edge rather than in the string, because these labels come from
   * Signal's own resources, which are shared with menus and with every translation of them.
   * Rewriting `text` in place would flatten any styled spans a caller had set and
   * locale-uppercase the stored value, which mangles languages where uppercasing changes the
   * letters (Turkish dotted/dotless i, German sharp s).
   */
  override fun setText(text: CharSequence?, type: BufferType) {
    super.setText(text?.toString()?.uppercase(Locale.getDefault()), type)
  }

  private companion object {
    const val PADDING_UNITS = 0.5f
  }
}
