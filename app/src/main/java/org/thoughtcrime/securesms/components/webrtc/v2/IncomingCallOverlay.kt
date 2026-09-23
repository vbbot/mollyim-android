/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * A full-screen TYPE_ACCESSIBILITY_OVERLAY window shown during an incoming call.
 *
 * LightOS's launcher (Luma, com.vandam.luma) draws its lock screen as a TYPE_ACCESSIBILITY_OVERLAY,
 * which structurally outranks TYPE_APPLICATION_OVERLAY — so an ordinary app overlay can never appear
 * over it while locked. The only window layer that can match Luma's is TYPE_ACCESSIBILITY_OVERLAY,
 * which requires an AccessibilityService. [context] must therefore be [LightCallAccessibilityService]
 * (the window is added through its context/token); this class is driven by that service, which is
 * itself triggered from ActiveCallManager on TYPE_INCOMING_RINGING and dismissed when the call state
 * moves on (answered, declined, missed) or the call manager shuts down.
 */
class IncomingCallOverlay(private val context: Context) {

  companion object {
    private val TAG = Log.tag(IncomingCallOverlay::class.java)
  }

  private val mainHandler = Handler(Looper.getMainLooper())

  private var windowManager: WindowManager? = null
  private var composeView: ComposeView? = null
  private var lifecycleOwner: OverlayLifecycleOwner? = null

  fun show(recipientId: RecipientId, isVideoCall: Boolean) {
    // WindowManager and LifecycleRegistry both require the main thread.
    mainHandler.post { showOnMainThread(recipientId, isVideoCall) }
  }

  private fun showOnMainThread(recipientId: RecipientId, isVideoCall: Boolean) {
    dismiss()

    val recipient = Recipient.resolved(recipientId)
    val callerName = recipient.getDisplayName(context)

    val owner = OverlayLifecycleOwner().also { it.start() }

    val view = ComposeView(context).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
      setViewTreeLifecycleOwner(owner)
      setViewTreeSavedStateRegistryOwner(owner)
      setContent {
        MollyLightTheme {
          LightIncomingCallContent(
            callerName = callerName,
            isVideoCall = isVideoCall,
            onAnswer = ::onAnswer,
            onAnswerVideo = ::onAnswerVideo,
            onDecline = ::onDecline,
          )
        }
      }
    }

    @Suppress("DEPRECATION")
    val params = WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
      WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.OPAQUE,
    ).apply {
      if (Build.VERSION.SDK_INT >= 28) {
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
      }
    }

    val wm = context.getSystemService(WindowManager::class.java)
    lifecycleOwner = owner
    composeView = view
    windowManager = wm
    try {
      wm.addView(view, params)
      Log.i(TAG, "Overlay shown for ${recipient.getDisplayName(context)}")
    } catch (e: Exception) {
      Log.w(TAG, "Failed to add overlay view", e)
      dismiss()
    }
  }

  fun dismiss() {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      dismissOnMainThread()
    } else {
      mainHandler.post { dismissOnMainThread() }
    }
  }

  private fun dismissOnMainThread() {
    lifecycleOwner?.stop()
    lifecycleOwner = null

    val v = composeView ?: return
    composeView = null
    try {
      windowManager?.removeViewImmediate(v)
      Log.i(TAG, "Overlay dismissed")
    } catch (e: Exception) {
      Log.w(TAG, "Failed to remove overlay view", e)
    }
    windowManager = null
  }

  private fun onAnswer() {
    dismiss()
    context.startActivity(
      CallIntent.Builder(context)
        .withAction(CallIntent.Action.ANSWER_AUDIO)
        .withStartedFromFullScreen(true)
        .withAddedIntentFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .build()
    )
  }

  private fun onAnswerVideo() {
    dismiss()
    context.startActivity(
      CallIntent.Builder(context)
        .withAction(CallIntent.Action.ANSWER_VIDEO)
        .withStartedFromFullScreen(true)
        .withAddedIntentFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .build()
    )
  }

  private fun onDecline() {
    dismiss()
    AppDependencies.signalCallManager.denyCall()
  }

  /** Minimal lifecycle owner required for Compose to function inside a WindowManager overlay. */
  private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun start() {
      savedStateRegistryController.performRestore(null)
      lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun stop() {
      lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
  }
}

@Composable
private fun LightIncomingCallContent(
  callerName: String,
  isVideoCall: Boolean,
  onAnswer: () -> Unit,
  onAnswerVideo: () -> Unit,
  onDecline: () -> Unit,
) {
  val colors = LightThemeTokens.colors
  val callTypeLabel = if (isVideoCall) "signal video call" else "signal call"

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.background),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(horizontal = 1f.gridUnitsAsDp()),
      contentAlignment = Alignment.Center,
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LightText(
          text = callerName,
          variant = LightTextVariant.Heading,
          align = TextAlign.Center,
        )
        LightText(
          text = callTypeLabel,
          variant = LightTextVariant.Copy,
          align = TextAlign.Center,
        )
      }
    }

    LightBottomBar(
      items = if (isVideoCall) {
        listOf(
          LightBarButton.LightIcon(icon = LightIcons.CLOSE, onClick = onDecline),
          LightBarButton.LightIcon(icon = LightIcons.CALL, onClick = onAnswer),
          LightBarButton.LightIcon(icon = LightIcons.ACCEPT, onClick = onAnswerVideo),
        )
      } else {
        listOf(
          LightBarButton.LightIcon(icon = LightIcons.CLOSE, onClick = onDecline),
          LightBarButton.LightIcon(icon = LightIcons.CALL, onClick = onAnswer),
        )
      },
    )
  }
}
