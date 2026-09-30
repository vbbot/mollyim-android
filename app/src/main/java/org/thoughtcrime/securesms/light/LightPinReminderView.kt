/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.light

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.TextView
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.verticalGridUnitsAsDp
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.NoAutofillEditText
import org.thoughtcrime.securesms.conversation.v2.items.light.LightItemStyle
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The periodic PIN reminder's content, in the Light Phone III design language.
 *
 * Signal's `lock/SignalPinReminderDialog` hands this to a `MaterialAlertDialogBuilder` and then
 * drives it entirely through `findViewById`: it looks up [R.id.pin], [R.id.pin_status],
 * [R.id.kbs_reminder_body], [R.id.skip] and [R.id.submit], calls `setEnabled` and
 * `setOnClickListener` on the last two, and puts a `ClickableSpan` and a `LinkMovementMethod` on the
 * body. None of that is presentation, so none of it has moved. What changed is that the hierarchy is
 * now built here rather than in XML, because the Light type scale, the rule under the field and the
 * grid sizing cannot be expressed in a `ConstraintLayout` without either hand-transcribing the SDK's
 * tokens (which would drift) or reaching for raw dp.
 *
 * The three things that make this safe rather than merely different:
 *
 * - **The credential field is a real [NoAutofillEditText].** [LightPinFieldStyle] touches its
 *   typeface, size, colours, background and gravity, and nothing else: no input type, no
 *   transformation method, no IME options, no autofill. The dialog still chooses the keyboard type
 *   itself, from `SignalStore.pin().getKeyboardType()`, and still suppresses autofill the way it
 *   always has.
 * - **The ids are load-bearing.** They are declared in `res/values/ids.xml` rather than in a layout,
 *   because a layout that no longer mentions them would stop generating them and break the host.
 * - **The body is still a plain [TextView].** `LinkMovementMethod` and `ClickableSpan` need one, and
 *   it is the only way the "Forgot PIN?" affordance can be reached.
 */
class LightPinReminderView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

  init {
    orientation = VERTICAL

    // The dialog window still wears Material's chrome (its host is outside this change), so the
    // content area states its own surface rather than inheriting whatever the overlay resolves to.
    setBackgroundColor(LightItemStyle.backgroundColor(context))

    val inset = (SCREEN_PADDING_UNITS * 1f.gridUnitsAsDp(context)).roundToInt()
    setPadding(inset, inset, inset, inset)

    addView(titleView())
    addView(pinField(), spacedBy(TITLE_TO_FIELD_UNITS))
    addView(fieldRule(), spacedBy(RULE_TO_STATUS_UNITS))
    addView(statusView(), spacedBy(STATUS_TO_BODY_UNITS))
    addView(bodyView(), spacedBy(BODY_TO_ACTIONS_UNITS))
    addView(actions())
  }

  private fun titleView(): TextView =
    TextView(context).apply {
      id = R.id.pin_reminder_title
      setText(R.string.KbsReminderDialog__enter_your_signal_pin)
      styleAs(LightTextVariant.Heading)
    }

  /**
   * The credential field.
   *
   * Only presentation is set here, and [LightPinFieldStyle] is explicit about what that means: the
   * dialog still picks the keyboard type, the transformation method and the focus itself.
   */
  private fun pinField(): View =
    NoAutofillEditText(context).apply {
      id = R.id.pin
      LightPinFieldStyle.apply(this)
      // Tall enough to be a target, not just a line of dots.
      minimumHeight = (LightPinFieldStyle.MINIMUM_TOUCH_UNITS * 1f.verticalGridUnitsAsDp(context)).roundToInt()
      layoutParams = fullWidth()
    }

  /** The rule beneath the field that replaces the Material input box. */
  private fun fieldRule(): View =
    View(context).apply {
      setBackgroundColor(LightItemStyle.contentColor(context))
      layoutParams = LayoutParams(
        LayoutParams.MATCH_PARENT,
        max(1, (RULE_UNITS * 1f.verticalGridUnitsAsDp(context)).roundToInt())
      )
    }

  /**
   * Empty until the dialog says something. [LightTextVariant.Copy] rather than a quieter token,
   * because this is where a rejected PIN is reported and it should not read as an aside.
   */
  private fun statusView(): TextView =
    TextView(context).apply {
      id = R.id.pin_status
      styleAs(LightTextVariant.Copy)
    }

  /**
   * Carries the reminder copy and the `ClickableSpan`-ed "Forgot PIN?" the dialog appends. The token
   * carries its own line height, so there is no second `lineSpacingMultiplier` to keep in step.
   */
  private fun bodyView(): TextView =
    TextView(context).apply {
      id = R.id.kbs_reminder_body
      styleAs(LightTextVariant.Paragraph)
    }

  /**
   * The action pair, end-aligned as it was in the `ConstraintLayout`.
   *
   * Baseline alignment is off: the two labels are the same token, so the platform's baseline pass
   * would have nothing to do and could only introduce a difference from a font fallback.
   */
  private fun actions(): View =
    LinearLayout(context).apply {
      orientation = HORIZONTAL
      gravity = Gravity.END
      isBaselineAligned = false
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

      addView(
        LightTextButton(context).apply {
          id = R.id.skip
          setText(R.string.KbsReminderDialog__skip)
          layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).also {
            it.marginEnd = (ACTION_GAP_UNITS * 1f.gridUnitsAsDp(context)).roundToInt()
          }
        }
      )

      addView(
        LightTextButton(context).apply {
          id = R.id.submit
          setText(R.string.KbsReminderDialog__submit)
          layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }
      )
    }

  /**
   * Types a line of copy from the SDK's own scale and centres it.
   *
   * Full width, because a `wrap_content` `TextView` in a vertical `LinearLayout` is left-aligned as
   * a box however `CENTER_HORIZONTAL` is set, so centring the text inside it centres nothing.
   */
  private fun TextView.styleAs(variant: LightTextVariant) {
    LightItemStyle.apply(this, variant)
    setTextColor(LightItemStyle.contentColor(context))
    gravity = Gravity.CENTER_HORIZONTAL
    layoutParams = fullWidth()
  }

  private fun fullWidth(): LayoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

  /** A full-width block hanging [topUnits] of vertical grid below whatever came before it. */
  private fun spacedBy(topUnits: Float): LayoutParams =
    fullWidth().also { it.topMargin = (topUnits * 1f.verticalGridUnitsAsDp(context)).roundToInt() }

  private companion object {
    /** Matches the padding the old layout carried as a flat `20dp`. */
    const val SCREEN_PADDING_UNITS = 2f

    const val TITLE_TO_FIELD_UNITS = 1.5f
    const val RULE_TO_STATUS_UNITS = 0.5f
    const val STATUS_TO_BODY_UNITS = 1.5f
    const val BODY_TO_ACTIONS_UNITS = 2f

    const val RULE_UNITS = 0.15f
    const val ACTION_GAP_UNITS = 1.5f
  }
}
