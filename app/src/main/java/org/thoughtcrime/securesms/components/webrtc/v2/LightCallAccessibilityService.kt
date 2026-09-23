/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * Hosts the incoming-call overlay at the TYPE_ACCESSIBILITY_OVERLAY window layer.
 *
 * This is the only way an app can reach the same window layer LightOS's launcher (Luma) uses for its
 * lock-screen "Secure Lock Mask" — an ordinary TYPE_APPLICATION_OVERLAY sits structurally below it
 * and can never appear over it while locked. A TYPE_ACCESSIBILITY_OVERLAY can only be added from a
 * running AccessibilityService's context, so [IncomingCallOverlay] is hosted here.
 *
 * The service does no event processing — it exists solely to own the overlay window. It is enabled
 * silently via WRITE_SECURE_SETTINGS (see [LightCallAccessibility]); if that permission is absent the
 * user must enable it manually under Settings > Accessibility.
 */
class LightCallAccessibilityService : AccessibilityService() {

  companion object {
    private val TAG = Log.tag(LightCallAccessibilityService::class.java)

    @Volatile
    private var instance: LightCallAccessibilityService? = null

    /** True once the service is connected and able to draw the overlay. */
    val isRunning: Boolean get() = instance != null

    /** Shows the incoming-call overlay if the service is running. Returns false if it is not. */
    fun showIncomingCall(recipientId: RecipientId, isVideoCall: Boolean): Boolean {
      val service = instance ?: run {
        Log.w(TAG, "showIncomingCall requested but service not connected")
        return false
      }
      service.showOverlay(recipientId, isVideoCall)
      return true
    }

    fun dismissIncomingCall() {
      instance?.dismissOverlay()
    }
  }

  private var overlay: IncomingCallOverlay? = null

  override fun onServiceConnected() {
    super.onServiceConnected()
    instance = this
    Log.i(TAG, "connected")
  }

  override fun onUnbind(intent: Intent?): Boolean {
    if (instance === this) {
      instance = null
    }
    overlay?.dismiss()
    overlay = null
    Log.i(TAG, "unbound")
    return super.onUnbind(intent)
  }

  override fun onDestroy() {
    if (instance === this) {
      instance = null
    }
    overlay?.dismiss()
    overlay = null
    super.onDestroy()
  }

  override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

  override fun onInterrupt() = Unit

  private fun showOverlay(recipientId: RecipientId, isVideoCall: Boolean) {
    val host = overlay ?: IncomingCallOverlay(this).also { overlay = it }
    host.show(recipientId, isVideoCall)
  }

  private fun dismissOverlay() {
    overlay?.dismiss()
  }
}
