/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.light

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.children
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputLayout
import com.thelightphone.sdk.ui.LightTextVariant
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.v2.items.light.LightItemStyle
import org.thoughtcrime.securesms.util.views.CircularProgressMaterialButton
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Applies the Light SDK's black, type-led presentation to the active XML registration flow.
 *
 * This deliberately styles existing, visible controls instead of layering a Compose facade over
 * hidden Views. The controls therefore retain their original listeners, autofill behavior, timers,
 * validation and accessibility nodes.
 */
object LightRegistrationViewStyle {

  private val enabledDisabledStates = arrayOf(
    intArrayOf(android.R.attr.state_enabled),
    intArrayOf(-android.R.attr.state_enabled)
  )

  fun surface(root: View) {
    root.setBackgroundColor(LightItemStyle.backgroundColor(root.context))
  }

  fun toolbar(toolbar: Toolbar) {
    surface(toolbar)
    toolbar.setTitleTextColor(content(toolbar))
    toolbar.setSubtitleTextColor(secondary(toolbar))
    toolbar.navigationIcon = toolbar.navigationIcon?.tinted(content(toolbar))
    toolbar.overflowIcon = toolbar.overflowIcon?.tinted(content(toolbar))
  }

  fun title(view: TextView) {
    text(view, LightTextVariant.Heading)
  }

  fun body(view: TextView) {
    text(view, LightTextVariant.Paragraph, secondary = true)
  }

  fun detail(view: TextView, secondary: Boolean = true) {
    text(view, LightTextVariant.Detail, secondary)
  }

  fun text(view: TextView, variant: LightTextVariant, secondary: Boolean = false) {
    LightItemStyle.apply(view, variant)
    view.setTextColor(if (secondary) secondary(view) else content(view))
  }

  fun action(button: MaterialButton) {
    LightItemStyle.apply(button, LightTextVariant.Button)
    button.isAllCaps = true
    button.text = button.text?.toString()?.uppercase(Locale.getDefault())
    button.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
    button.strokeWidth = 0
    button.cornerRadius = 0
    button.insetTop = 0
    button.insetBottom = 0
    button.elevation = 0f
    button.minimumHeight = (
      LightRegistrationGeometry.minimumTouchDp(button.resources.configuration.screenWidthDp.toFloat()) *
        button.resources.displayMetrics.density
      ).roundToInt()
    button.iconTint = actionColors(button)
    button.setTextColor(actionColors(button))
  }

  fun action(button: CircularProgressMaterialButton) {
    val materialButton = button.findViewById<MaterialButton>(R.id.button)
    action(materialButton)

    // The CircularProgressIndicator lives behind the MaterialButton. When the button
    // is enabled, CircularProgressMaterialButton sets the indicator to VISIBLE — which
    // is normally harmless because the opaque button covers it. Our transparent button
    // exposes it as a dot. Make the button opaque BLACK so it hides the indicator in
    // BUTTON state; when spinning the button goes INVISIBLE and the indicator shows.
    materialButton.backgroundTintList = ColorStateList.valueOf(Color.BLACK)

    button.findViewById<CircularProgressIndicator>(R.id.progress_indicator)?.apply {
      setIndicatorColor(content(button))
      trackColor = Color.TRANSPARENT
    }
  }

  fun input(layout: TextInputLayout) {
    val foreground = content(layout)
    val secondary = secondary(layout)

    val density = layout.resources.displayMetrics.density

    // Switch to outline mode so the filled background is completely removed.
    layout.boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
    layout.setBoxBackgroundColor(Color.TRANSPARENT)
    layout.setBoxCornerRadii(0f, 0f, 0f, 0f)
    layout.boxStrokeColor = foreground
    layout.setBoxStrokeWidth((1f * density).roundToInt())
    layout.setBoxStrokeWidthFocused((2f * density).roundToInt())

    // Disable the floating label. In outline mode the label cuts through the top
    // border and forces the EditText downward so text is no longer vertically centred.
    // Move the hint to the EditText instead so it still shows as placeholder text.
    val savedHint = layout.hint
    layout.isHintEnabled = false
    layout.isHintAnimationEnabled = false
    if (!savedHint.isNullOrEmpty()) {
      layout.editText?.hint = savedHint
    }

    layout.editText?.let(::input)
  }

  fun input(editText: EditText) {
    text(editText, LightTextVariant.Copy)
    editText.setHintTextColor(secondary(editText))
    editText.background = null
    editText.backgroundTintList = null
    // Force vertical centering — TextInputLayout wrapping can leave internal top/bottom
    // padding that pushes the text down, misaligning it with adjacent bare EditTexts.
    editText.gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
    editText.setPadding(editText.paddingLeft, 0, editText.paddingRight, 0)
  }

  /** Styles all six OTP cells without replacing their paste watcher or completion callback. */
  fun verificationCode(codeView: ViewGroup) {
    codeView.walk { child ->
      when (child) {
        is TextInputLayout -> input(child)
        is EditText -> input(child)
        is TextView -> text(child, LightTextVariant.Copy)
      }
    }
  }

  /** Styles the existing registration keypad while leaving its key listener and state views intact. */
  fun verificationKeyboard(keyboard: ViewGroup) {
    surface(keyboard)
    keyboard.walk { child ->
      when (child) {
        is TextView -> text(child, LightTextVariant.Heading)
        is ImageView -> child.imageTintList = ColorStateList.valueOf(content(child))
        is CircularProgressIndicator -> child.setIndicatorColor(content(child))
        is ProgressBar -> child.indeterminateTintList = ColorStateList.valueOf(content(child))
      }
    }
  }

  fun icon(image: ImageView, secondary: Boolean = false) {
    image.imageTintList = ColorStateList.valueOf(if (secondary) secondary(image) else content(image))
  }

  private fun ViewGroup.walk(block: (View) -> Unit) {
    children.forEach { child ->
      block(child)
      if (child is ViewGroup) child.walk(block)
    }
  }

  private fun actionColors(view: View): ColorStateList = ColorStateList(
    enabledDisabledStates,
    intArrayOf(content(view), secondary(view))
  )

  private fun content(view: View): Int = LightItemStyle.contentColor(view.context)

  private fun secondary(view: View): Int = LightItemStyle.contentSecondaryColor(view.context)

  private fun Drawable.tinted(color: Int): Drawable = DrawableCompat.wrap(mutate()).also {
    DrawableCompat.setTint(it, color)
  }
}
