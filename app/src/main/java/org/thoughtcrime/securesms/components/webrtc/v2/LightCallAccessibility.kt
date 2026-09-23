/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import org.signal.core.util.logging.Log

/**
 * Enables [LightCallAccessibilityService] without the user-facing Accessibility toggle.
 *
 * The service only exists to draw the incoming-call overlay at the TYPE_ACCESSIBILITY_OVERLAY layer;
 * requiring the user to walk through the "view and control your screen" enable flow would be poor UX.
 * When WRITE_SECURE_SETTINGS is held (granted on the Light Phone III build via
 * `adb shell pm grant im.molly.app android.permission.WRITE_SECURE_SETTINGS`), we can append our
 * component to Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES ourselves.
 *
 * If the permission is not held the write throws SecurityException and we simply leave the service
 * disabled — the caller falls back to the existing fullscreen-notification path.
 */
object LightCallAccessibility {

  private val TAG = Log.tag(LightCallAccessibility::class.java)

  @JvmStatic
  fun ensureServiceEnabled(context: Context) {
    val component = ComponentName(context, LightCallAccessibilityService::class.java)
    if (isServiceEnabled(context, component)) {
      return
    }

    val resolver = context.contentResolver
    val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    val updated = if (current.isEmpty()) component.flattenToString() else "$current:${component.flattenToString()}"

    try {
      Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated)
      Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
      Log.i(TAG, "Enabled LightCallAccessibilityService via secure settings")
    } catch (e: SecurityException) {
      Log.w(TAG, "WRITE_SECURE_SETTINGS not granted; accessibility overlay unavailable until enabled manually")
    }
  }

  private fun isServiceEnabled(context: Context, component: ComponentName): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
      ?: return false
    val flattened = component.flattenToString()
    val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
    return splitter.any { it.equals(flattened, ignoreCase = true) }
  }
}
