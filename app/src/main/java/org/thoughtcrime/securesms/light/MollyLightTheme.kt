/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.light

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.thelightphone.sdk.ui.LightColors
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeColors
import com.thelightphone.sdk.ui.LocalHapticsEnabled
import org.signal.core.ui.util.ThemeUtil

/**
 * Molly's single entry point into The Light Phone SDK's design system.
 *
 * [LightTheme] installs its own Material 3 `ColorScheme`, so it must never wrap something that also
 * expects `SignalTheme`'s scheme. Every caller therefore scopes this as tightly as it can -- around
 * the conversation list view, around the bottom bar -- rather than around the activity.
 */
@Composable
fun MollyLightTheme(content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalHapticsEnabled provides rememberSystemHapticsEnabled()) {
    LightTheme(
      colors = rememberMollyLightColors(),
      content = content
    )
  }
}

/**
 * Follows Molly's own light/dark setting rather than pinning to the Light Phone's black-on-white.
 * The SDK palettes are pure black/white either way, which is what the LP3 display wants.
 *
 * Molly recreates its activities when the theme changes, so caching this per-[LocalContext] is
 * enough to stay in sync.
 */
@Composable
fun rememberMollyLightColors(): LightColors {
  val context = LocalContext.current
  return remember(context) {
    if (ThemeUtil.isDarkTheme(context)) LightThemeColors.Dark else LightThemeColors.Light
  }
}

/**
 * The SDK's [LocalHapticsEnabled] defaults to `false`, which silently disables haptics in every
 * `Modifier.lightClickable` in the tree. On the Light Phone it is fed from a server preference;
 * here the closest equivalent is the platform's own haptic feedback setting.
 */
@Composable
fun rememberSystemHapticsEnabled(): Boolean {
  val context = LocalContext.current
  return remember(context) {
    Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
  }
}
