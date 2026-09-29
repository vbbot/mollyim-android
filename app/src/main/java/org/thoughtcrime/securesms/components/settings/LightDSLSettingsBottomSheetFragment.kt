/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings

import android.content.Context
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.thoughtcrime.securesms.R

/**
 * Explicit opt-in Light host for settings DSL bottom sheets.
 *
 * [DSLSettingsBottomSheetFragment] builds its own [DSLSettingsAdapter] instead of going through
 * `createAdapters()`, so a sheet does *not* pick up [LightDSLSettingsFragment]'s wiring. Sheets have
 * to opt in here, and then register a factory for every model they render, including their own
 * private ones, via [registerLightPreference].
 *
 * The sheet's own chrome — the rounded Signal bottom-sheet surface and its drag handle — is not
 * restyled here. Screens that need a fully Light sheet convert to a Light dialog instead.
 */
abstract class LightDSLSettingsBottomSheetFragment(
  @LayoutRes layoutId: Int = R.layout.dsl_settings_bottom_sheet,
  layoutManagerProducer: (Context) -> RecyclerView.LayoutManager = { context -> LinearLayoutManager(context) },
  peekHeightPercentage: Float = 1f
) : DSLSettingsBottomSheetFragment(layoutId, layoutManagerProducer, peekHeightPercentage) {
  override fun createAdapter(): DSLSettingsAdapter = LightDSLSettingsAdapter()
}
