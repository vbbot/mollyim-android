/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.light

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.MollyLightTheme

internal const val MINIMUM_VOICE_MESSAGE_DURATION_MILLIS = 1_000L
internal const val MAXIMUM_VOICE_MESSAGE_DURATION_MILLIS = 60L * 60L * 1_000L

enum class LightVoiceRecordingMode {
  HELD,
  LOCKED
}

/** Immutable presentation state. Capture and lifetime remain owned by AudioRecorder. */
data class LightVoiceRecordingState(
  val mode: LightVoiceRecordingMode,
  val elapsedMillis: Long
) {
  val canSend: Boolean
    get() = mode == LightVoiceRecordingMode.LOCKED && elapsedMillis > MINIMUM_VOICE_MESSAGE_DURATION_MILLIS

  val hasReachedLimit: Boolean
    get() = elapsedMillis >= MAXIMUM_VOICE_MESSAGE_DURATION_MILLIS
}

data class LightVoiceRecordingCallbacks(
  val onDiscard: () -> Unit,
  val onReview: () -> Unit,
  val onSend: () -> Unit
)

/**
 * Light presentation for an active recording.
 *
 * It deliberately has no recorder, clock, or lifecycle of its own.
 * [InputPanel][org.thoughtcrime.securesms.components.InputPanel] supplies its existing one-second clock
 * and routes these intents back through MicrophoneRecorderView,
 * which keeps held/locked terminal-state behavior unchanged.
 */
@Composable
fun LightVoiceRecording(
  state: LightVoiceRecordingState,
  callbacks: LightVoiceRecordingCallbacks,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(LightThemeTokens.colors.background)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(4f.gridUnitsAsDp())
        .padding(horizontal = 2f.gridUnitsAsDp()),
      contentAlignment = Alignment.Center
    ) {
      LightText(
        text = if (state.mode == LightVoiceRecordingMode.HELD) {
          stringResource(R.string.LightVoiceRecording__held, formatVoiceTime(state.elapsedMillis))
        } else {
          stringResource(R.string.LightVoiceRecording__locked, formatVoiceTime(state.elapsedMillis))
        },
        variant = LightTextVariant.Copy,
        align = TextAlign.Center,
        monospace = true
      )
    }

    if (state.mode == LightVoiceRecordingMode.HELD) {
      LightBottomBar(
        items = listOf(
          LightBarButton.Text(
            text = stringResource(R.string.LightVoiceRecording__slide_to_cancel),
            onClick = null
          )
        )
      )
    } else {
      LightBottomBar(
        items = listOf(
          LightBarButton.LightIcon(
            icon = LightIcons.DELETE,
            onClick = callbacks.onDiscard,
            contentDescription = stringResource(R.string.LightVoiceRecording__discard)
          ),
          LightBarButton.LightIcon(
            icon = LightIcons.STOP,
            onClick = callbacks.onReview,
            contentDescription = stringResource(R.string.LightVoiceRecording__review_description)
          ),
          LightBarButton.LightIcon(
            icon = LightIcons.SEND,
            onClick = callbacks.onSend.takeIf { state.canSend },
            contentDescription = stringResource(R.string.LightVoiceRecording__send)
          )
        )
      )
    }
  }
}

/** View bridge used by recording_layout.xml. */
class LightVoiceRecordingView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs) {

  private var recordingState: LightVoiceRecordingState? by mutableStateOf(null)
  private var callbacks: LightVoiceRecordingCallbacks by mutableStateOf(
    LightVoiceRecordingCallbacks({}, {}, {})
  )

  init {
    visibility = View.GONE
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  }

  fun setCallbacks(onDiscard: Runnable, onReview: Runnable, onSend: Runnable) {
    callbacks = LightVoiceRecordingCallbacks(
      onDiscard = onDiscard::run,
      onReview = onReview::run,
      onSend = onSend::run
    )
  }

  fun showHeld() {
    recordingState = LightVoiceRecordingState(LightVoiceRecordingMode.HELD, 0L)
    visibility = View.VISIBLE
  }

  fun showLocked() {
    recordingState = LightVoiceRecordingState(
      mode = LightVoiceRecordingMode.LOCKED,
      elapsedMillis = recordingState?.elapsedMillis ?: 0L
    )
    visibility = View.VISIBLE
  }

  fun setElapsedMillis(elapsedMillis: Long) {
    recordingState = recordingState?.copy(elapsedMillis = elapsedMillis.coerceAtLeast(0L))
  }

  fun hideRecording() {
    recordingState = null
    visibility = View.GONE
  }

  @Composable
  override fun Content() {
    val state = recordingState ?: return
    MollyLightTheme {
      LightVoiceRecording(state = state, callbacks = callbacks)
    }
  }
}

internal fun formatVoiceTime(milliseconds: Long): String {
  val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
  val seconds = (totalSeconds % 60L).toString().padStart(2, '0')
  return "${totalSeconds / 60L}:$seconds"
}
