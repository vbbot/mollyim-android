/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.light

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightBottomBarItem
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightThemeTokens
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.MollyLightTheme

/**
 * The conversation thread's bottom bar: call, attach, compose.
 *
 * This is what the user sees where Signal draws its input panel. The panel itself is still there and
 * still owns the draft, the pending reply, edit mode, voice recording, link previews, mentions and
 * styling -- it is simply collapsed out of the layout while this bar stands in for its chrome, and
 * expanded again when it has something of its own to show (the composer, a recording, a voice-note
 * draft). See `ConversationFragment.updateLightInputChrome`.
 *
 * **Exactly three icon items.** `LightBottomBar` enforces "at most five items, and at most three if
 * any of them is text" with `require()`, which throws when the bar is *composed* rather than when it
 * is compiled -- a fourth item is a crash on the device, not a build failure. All three here are
 * icons, so the text limit cannot bite either. `LightConversationBottomBarTest` composes the real
 * bar so that a regression fails there instead.
 *
 * The call slot is conditional: a thread with neither a voice nor a video call available (a group
 * that is not an active V2 group, an SMS-only or non-push contact, a blocked contact, Note to Self,
 * release notes) would otherwise offer a button that opens an empty menu. When it is absent the slot
 * is passed as `null` rather than dropped, so that attach and compose keep the positions they have
 * on every other thread -- `LightBottomBar` lays two items out at the edges but three at
 * start/centre/end, and the bar must not visibly reflow between conversations.
 */
@Composable
fun LightConversationBottomBar(
  showCall: Boolean,
  onCallClick: () -> Unit,
  onAddClick: () -> Unit,
  onComposeClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      // Opaque, and it eats every pointer event in its area: the collapsed input panel is not the
      // only thing beneath the bar -- the message list runs right up to it -- and Compose dispatches
      // a touch to everything under the finger unless something consumes it. The bar's own buttons
      // are children and see each pass first, so they keep working.
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
    LightBottomBar(
      items = listOf<LightBottomBarItem?>(
        if (showCall) {
          LightBarButton.LightIcon(
            icon = LightIcons.CALL,
            onClick = onCallClick,
            contentDescription = stringResource(R.string.LightConversationBottomBar__call)
          )
        } else {
          null
        },
        LightBarButton.LightIcon(
          icon = LightIcons.ADD,
          onClick = onAddClick,
          contentDescription = stringResource(R.string.ConversationActivity_add_attachment)
        ),
        LightBarButton.LightIcon(
          icon = LightIcons.COMPOSE_MESSAGE,
          onClick = onComposeClick,
          contentDescription = stringResource(R.string.LightConversationBottomBar__write_message)
        )
      ),
      modifier = Modifier.align(Alignment.Center)
    )
  }
}

/**
 * View-land host for [LightConversationBottomBar], so that `ConversationFragment` can drive it from
 * the same places it drives `InputPanel`.
 */
class LightConversationBottomBarView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs) {

  var showCall: Boolean by mutableStateOf(false)

  /**
   * State-backed rather than plain fields: whether a slot is clickable is decided during
   * composition, so a handler installed after the first frame has to invalidate it.
   */
  var onCallClick: (() -> Unit)? by mutableStateOf<(() -> Unit)?>(null)
  var onAddClick: (() -> Unit)? by mutableStateOf<(() -> Unit)?>(null)
  var onComposeClick: (() -> Unit)? by mutableStateOf<(() -> Unit)?>(null)

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  }

  @Composable
  override fun Content() {
    MollyLightTheme {
      LightConversationBottomBar(
        showCall = showCall,
        onCallClick = { onCallClick?.invoke() },
        onAddClick = { onAddClick?.invoke() },
        onComposeClick = { onComposeClick?.invoke() },
        // Width only. This view is laid out `wrap_content`, so Compose measures its height with an
        // AT_MOST constraint equal to everything left on the screen -- `fillMaxSize` would take all
        // of it and leave a bar the height of the thread.
        modifier = Modifier.fillMaxWidth()
      )
    }
  }
}
