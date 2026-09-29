/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.drawable.DrawableCompat
import kotlin.math.roundToInt

/** View-side Light top bar used by explicitly opted-in legacy DSL settings screens. */
class LightSettingsToolbar @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : Toolbar(context, attrs) {

  init {
    setBackgroundColor(Color.BLACK)
    setTitleTextColor(Color.WHITE)
    setSubtitleTextColor(Color.LTGRAY)
    setContentInsetsRelative(0, 0)
    contentInsetStartWithNavigation = 0
    contentInsetEndWithActions = 0
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    val width = MeasureSpec.getSize(widthMeasureSpec)
    val lightHeight = (width / 27f * 3f).roundToInt()
    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(lightHeight, MeasureSpec.EXACTLY))
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)

    navigationIcon?.let { icon ->
      DrawableCompat.setTint(DrawableCompat.wrap(icon), Color.WHITE)
    }

    val titleView = (0 until childCount)
      .map(::getChildAt)
      .filterIsInstance<TextView>()
      .firstOrNull { it.text == title }
      ?: return

    titleView.textSize = 13f
    titleView.textAlignment = View.TEXT_ALIGNMENT_CENTER
    val childWidth = titleView.measuredWidth.coerceAtMost(width)
    val childLeft = (width - childWidth) / 2
    val childTop = (height - titleView.measuredHeight) / 2
    titleView.layout(childLeft, childTop, childLeft + childWidth, childTop + titleView.measuredHeight)
  }
}
