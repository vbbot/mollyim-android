/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.light

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.v2.items.light.LightQuoteLine
import org.thoughtcrime.securesms.light.MollyLightTheme

/**
 * The full-screen Light composer, minus the text entry itself.
 *
 * The text entry is the real `ComposeText` inside the real `InputPanel`, still in its own view
 * hierarchy, sitting directly below this. That is the whole point of the arrangement: an
 * `EmojiEditText` that never moves keeps @-mention autocomplete (the `InlineQuery` popups anchor to
 * it), text styling and spoilers, paste-an-image through `ViewCompat.setOnReceiveContentListener`,
 * and draft save/restore -- all of which a separate screen would break by moving the editor or by
 * mirroring text across a boundary. So this composable is everything *around* the entry: an opaque
 * black field over the message list, the top bar, and the reply line.
 *
 * The entry is bottom-aligned and grows upward into this field as it wraps ("notes-style", as the
 * reference client's `ComposerScreen` calls it), which is why the reply line hangs off the *bottom*
 * of this view rather than being stacked from the top.
 *
 * The return key inserts a newline and does not send; `SEND` in the top bar is the only send
 * affordance. That needs no work here -- `ComposeText` is already `textMultiLine` with
 * `flagNoEnterAction`, so the IME never raises `IME_ACTION_SEND` from the return key.
 */
@Composable
fun LightComposer(
  title: String?,
  quote: CharSequence?,
  onBack: () -> Unit,
  onSend: () -> Unit,
  onClearQuote: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      // Opaque and touch-swallowing: the message list is still laid out behind this and would both
      // show through and take taps otherwise. Compose dispatches a pointer event to everything under
      // the finger, so a background alone is not enough; the top bar's buttons are children and see
      // each pass first.
      .background(LightThemeTokens.colors.background)
      .pointerInput(Unit) {
        awaitEachGesture {
          awaitFirstDown(requireUnconsumed = false).consume()
          while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
            if (event.changes.none { it.pressed }) break
          }
        }
      }
  ) {
    LightTopBar(
      leftButton = LightBarButton.LightIcon(
        icon = LightIcons.BACK,
        onClick = onBack,
        contentDescription = stringResource(R.string.LightComposer__close_composer)
      ),
      center = title?.let { LightTopBarCenter.Text(text = it) },
      rightButton = LightBarButton.Text(
        text = stringResource(R.string.LightComposer__send),
        onClick = onSend
      ),
      modifier = Modifier.align(Alignment.TopCenter)
    )

    if (quote != null) {
      Row(
        modifier = Modifier
          .align(Alignment.BottomStart)
          .fillMaxWidth()
          // Inset to exactly where the compose text below starts, so that the reply line and the
          // reply share a left edge. Nothing in the panel is moved to achieve that -- the dimen
          // tracks the panel's own geometry.
          .padding(
            start = dimensionResource(R.dimen.light_composer_text_inset),
            end = dimensionResource(R.dimen.light_composer_text_inset),
            bottom = 0.5f.gridUnitsAsDp()
          ),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Rendered as a View rather than `LightText` because `LightQuoteLine` builds a `CharSequence`
        // carrying a `RelativeSizeSpan` on the reply glyph -- the glyph comes from a fallback font and
        // sits small in the line box without it -- and `LightText` takes a `String`. Using the same
        // `LightQuoteLine.style` the thread's own reply lines use is what keeps the two identical.
        AndroidView(
          factory = { context -> TextView(context).also { LightQuoteLine.style(it) } },
          update = { it.text = quote },
          modifier = Modifier.weight(1f)
        )

        // The reply's only escape hatch. Signal put a dismiss X on the quote card in the inline
        // input bar; that card is hidden in the Light chrome, so without this the quote could be
        // set but never cleared -- and because the quote is saved as a draft it outlived the
        // composer, the thread and the app. Sized under the top bar's buttons so it reads as part
        // of the reply line rather than as a third bar action.
        Box(
          modifier = Modifier
            .lightClickable(onClick = onClearQuote)
            .padding(start = 0.5f.gridUnitsAsDp())
        ) {
          LightIcon(
            icon = LightIcons.CLOSE,
            size = 1.5f,
            contentDescription = stringResource(R.string.LightComposer__clear_reply)
          )
        }
      }
    }
  }
}

/**
 * View-land host for [LightComposer], laid out from the top of the screen down to the top of the
 * input panel so that the real `ComposeText` sits immediately below it.
 */
class LightComposerView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs) {

  /** The contact or group name, or the edit-mode title. */
  var title: String? by mutableStateOf(null)

  /** The pending reply's line, already built by [LightQuoteLine], or null when this is not a reply. */
  var quote: CharSequence? by mutableStateOf(null)

  var onBack: (() -> Unit)? by mutableStateOf<(() -> Unit)?>(null)
  var onSend: (() -> Unit)? by mutableStateOf<(() -> Unit)?>(null)

  /** Clears the pending reply, leaving the composer open and whatever has been typed intact. */
  var onClearQuote: (() -> Unit)? by mutableStateOf<(() -> Unit)?>(null)

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  }

  @Composable
  override fun Content() {
    MollyLightTheme {
      LightComposer(
        title = title,
        quote = quote,
        onBack = { onBack?.invoke() },
        onSend = { onSend?.invoke() },
        onClearQuote = { onClearQuote?.invoke() },
        modifier = Modifier.fillMaxSize()
      )
    }
  }
}
