/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings

import android.content.Context
import androidx.appcompat.widget.Toolbar
import androidx.annotation.LayoutRes
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.Material3OnScrollHelper
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter

/**
 * Explicit opt-in Light host for legacy settings DSL screens.
 *
 * The fragment lifecycle, adapter binding and all callbacks stay in [DSLSettingsFragment]; only the
 * registered presentation factories change. A screen opts in by extending this class and must
 * register a factory for every model it renders — see [registerLightPreference].
 */
abstract class LightDSLSettingsFragment(
  @StringRes titleId: Int = -1,
  @MenuRes menuId: Int = -1,
  @LayoutRes layoutId: Int = R.layout.light_dsl_settings_fragment,
  layoutManagerProducer: (Context) -> RecyclerView.LayoutManager = { context -> LinearLayoutManager(context) }
) : DSLSettingsFragment(titleId, menuId, layoutId, layoutManagerProducer) {
  override fun createAdapters(): Array<MappingAdapter> = arrayOf(LightDSLSettingsAdapter())

  /**
   * The Material on-scroll helper repaints the toolbar with the Signal surface and elevation. The
   * Light toolbar is fixed black, so attaching it would put Signal chrome back on the screen.
   */
  override fun getMaterial3OnScrollHelper(toolbar: Toolbar?): Material3OnScrollHelper? = null
}
