/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.light

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTouchableProgressBar
import com.thelightphone.sdk.ui.gridUnitsAsDp
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.MollyLightTheme

/** Immutable playback presentation. The media controller remains the sole playback owner. */
data class LightVoiceNoteDraftState(
  val positionMillis: Long = 0L,
  val durationMillis: Long = 0L,
  val isPlaying: Boolean = false
) {
  val progress: Float
    get() = if (durationMillis > 0L) {
      (positionMillis.toDouble() / durationMillis.toDouble()).toFloat().coerceIn(0f, 1f)
    } else {
      0f
    }

  val displayPositionMillis: Long
    get() = positionMillis.coerceIn(0L, durationMillis.coerceAtLeast(0L))

  fun progressToSeek(value: Float): Double = value.coerceIn(0f, 1f).toDouble()
}

data class LightVoiceNoteDraftCallbacks(
  val onDelete: () -> Unit,
  val onTogglePlayback: () -> Unit,
  val onSeek: (Double) -> Unit,
  val onSend: () -> Unit
)

/** Light review controls for the persisted voice-note draft owned by DraftViewModel. */
@Composable
fun LightVoiceNoteDraft(
  state: LightVoiceNoteDraftState,
  callbacks: LightVoiceNoteDraftCallbacks,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(LightThemeTokens.colors.background)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(4f.gridUnitsAsDp())
        .padding(horizontal = 2f.gridUnitsAsDp()),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.padding(end = 1f.gridUnitsAsDp())) {
        LightText(
          text = stringResource(R.string.LightVoiceNoteDraft__recorded),
          variant = LightTextVariant.Fine
        )
        LightText(
          text = "${formatVoiceTime(state.displayPositionMillis)} / ${formatVoiceTime(state.durationMillis)}",
          variant = LightTextVariant.Fine,
          monospace = true
        )
      }

      val seekDescription = stringResource(R.string.LightVoiceNoteDraft__seek)
      LightTouchableProgressBar(
        colors = LightThemeTokens.colors,
        progress = state.progress,
        onValueChange = { callbacks.onSeek(state.progressToSeek(it)) },
        modifier = Modifier
          .weight(1f)
          .semantics {
            contentDescription = seekDescription
            progressBarRangeInfo = ProgressBarRangeInfo(state.progress, 0f..1f)
          }
      )
    }

    LightBottomBar(
      items = listOf(
        LightBarButton.LightIcon(
          icon = LightIcons.DELETE,
          onClick = callbacks.onDelete,
          contentDescription = stringResource(R.string.LightVoiceNoteDraft__delete)
        ),
        LightBarButton.LightIcon(
          icon = if (state.isPlaying) LightIcons.PAUSE else LightIcons.PLAY,
          onClick = callbacks.onTogglePlayback,
          contentDescription = stringResource(
            if (state.isPlaying) R.string.LightVoiceNoteDraft__pause else R.string.LightVoiceNoteDraft__play
          )
        ),
        LightBarButton.LightIcon(
          icon = LightIcons.SEND,
          onClick = callbacks.onSend,
          contentDescription = stringResource(R.string.LightVoiceNoteDraft__send)
        )
      )
    )
  }
}

/** View bridge inflated inside VoiceNoteDraftView. */
class LightVoiceNoteDraftHostView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs) {

  var state: LightVoiceNoteDraftState by mutableStateOf(LightVoiceNoteDraftState())
  var callbacks: LightVoiceNoteDraftCallbacks by mutableStateOf(
    LightVoiceNoteDraftCallbacks({}, {}, {}, {})
  )

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  }

  @Composable
  override fun Content() {
    MollyLightTheme {
      LightVoiceNoteDraft(state = state, callbacks = callbacks)
    }
  }
}
