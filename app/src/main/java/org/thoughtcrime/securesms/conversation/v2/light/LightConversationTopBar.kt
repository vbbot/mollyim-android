/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.light

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarButton
import com.thelightphone.sdk.ui.LightTopBarCenter
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.light.rememberLightTintedPainter

/**
 * What the conversation thread's top bar puts in its left slot.
 *
 * The thread is reached three ways and each wants something different: a normal push goes back, a
 * split-pane list/detail layout has nothing to go back *to*, and a chat bubble's only escape is into
 * the app proper.
 */
enum class LightConversationTopBarLeftAction {
  NONE,
  BACK,
  LAUNCH_MAIN_APP
}

/**
 * The conversation thread's top bar, in the Light Phone pattern: a back chevron, the contact or
 * group name centred, and the thread's overflow menu.
 *
 * This replaces what `ConversationTitleView` used to draw inside Molly's Material toolbar (avatar,
 * name, subtitle, verified tick, disappearing-messages badge, story ring). The reference client's
 * `ThreadScreen` top bar is a chevron and a name and nothing else; the overflow is the one addition,
 * because Signal's thread menu carries items -- disappearing messages, mute, search in conversation,
 * conversation settings, and now the voice and video call actions -- that have no other entry point.
 *
 * **It does not own the menu.** The `Toolbar` this covers is still the `MenuProvider` host, still
 * builds the menu eagerly (which is what drives the expiring-messages callbacks), and still owns the
 * collapsible `SearchView` action view that "Search in conversation" expands into. [onOverflowClick]
 * is expected to call through to `Toolbar.showOverflowMenu()` so the popup anchors on the real
 * overflow button, which is laid out directly behind this bar's ellipses. See `ConversationFragment`.
 */
@Composable
fun LightConversationTopBar(
  title: String?,
  leftAction: LightConversationTopBarLeftAction,
  hasOverflow: Boolean,
  onLeftClick: () -> Unit,
  onTitleClick: (() -> Unit)?,
  onOverflowClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    // Opaque: the toolbar behind this -- whose only remaining job is to host the menu -- must not
    // show through, and neither must a chat wallpaper, which is not part of the Light design.
    modifier = modifier.background(LightThemeTokens.colors.background)
  ) {
    LightTopBar(
      leftButton = leftButton(leftAction, onLeftClick),
      center = title?.let { name ->
        LightTopBarCenter.Text(text = name, onClick = onTitleClick)
      },
      rightButton = if (hasOverflow) {
        LightBarButton.LightIcon(
          icon = LightIcons.ELLIPSES,
          onClick = onOverflowClick,
          contentDescription = stringResource(R.string.MainToolbar__more_options_content_description)
        )
      } else {
        null
      },
      // `LightTopBar` is 3 grid units tall while this box is the height of the toolbar it covers,
      // which is taller. Top-aligning puts the bar's content immediately below the status bar, where
      // the reference client has it, and leaves the surplus as black padding above the first message
      // rather than pushing the name down into the thread.
      modifier = Modifier.align(Alignment.TopCenter)
    )
  }
}

@Composable
private fun leftButton(
  leftAction: LightConversationTopBarLeftAction,
  onLeftClick: () -> Unit
): LightTopBarButton? = when (leftAction) {
  LightConversationTopBarLeftAction.NONE -> null

  LightConversationTopBarLeftAction.BACK -> LightBarButton.LightIcon(
    icon = LightIcons.BACK,
    onClick = onLeftClick,
    contentDescription = stringResource(R.string.ConversationFragment__content_description_back_button)
  )

  // A bubble has no back stack of its own; this is Signal's "leave the bubble and open the app"
  // affordance. There is no SDK glyph for it, so Molly's own notification icon is tinted to match
  // the SDK's icons rather than drawn in the accent colour it used on the Material toolbar.
  LightConversationTopBarLeftAction.LAUNCH_MAIN_APP -> LightBarButton.Icon(
    painter = rememberLightTintedPainter(R.drawable.ic_notification),
    onClick = onLeftClick,
    contentDescription = stringResource(R.string.ConversationFragment__content_description_launch_signal_button)
  )
}

/**
 * View-land host for [LightConversationTopBar], so that `ConversationFragment` can keep driving the
 * bar the way it drove `ConversationTitleView` and the `Toolbar`.
 */
class LightConversationTopBarView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs) {

  var title: String? by mutableStateOf(null)
  var leftAction: LightConversationTopBarLeftAction by mutableStateOf(LightConversationTopBarLeftAction.NONE)

  /** Whether to offer the overflow. False in the popup screen type, which builds no menu. */
  var hasOverflow: Boolean by mutableStateOf(false)

  /**
   * State-backed rather than plain fields: whether the name is clickable at all is decided during
   * composition, so a handler installed after the first frame has to invalidate it.
   */
  var onLeftClick: (() -> Unit)? by mutableStateOf<(() -> Unit)?>(null)
  var onTitleClick: (() -> Unit)? by mutableStateOf<(() -> Unit)?>(null)
  var onOverflowClick: (() -> Unit)? by mutableStateOf<(() -> Unit)?>(null)

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  }

  @Composable
  override fun Content() {
    MollyLightTheme {
      LightConversationTopBar(
        title = title,
        leftAction = leftAction,
        hasOverflow = hasOverflow,
        onLeftClick = { onLeftClick?.invoke() },
        onTitleClick = onTitleClick?.let { { it() } },
        onOverflowClick = { onOverflowClick?.invoke() },
        modifier = Modifier.fillMaxSize()
      )
    }
  }
}
