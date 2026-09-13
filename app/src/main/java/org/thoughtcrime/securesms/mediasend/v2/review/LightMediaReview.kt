/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediasend.v2.review

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIconConfiguration
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextField
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.LocalHapticsEnabled
import com.thelightphone.sdk.ui.designVerticalPxToDp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.MollyLightTheme

/** The focused attachment's presentation category. Business media objects stay in the ViewModel. */
enum class LightMediaReviewMediaType {
  IMAGE,
  VIDEO,
  GIF,
  DOCUMENT
}

/** Persistent actions shown in the four-grid-unit review action bar. */
enum class LightMediaReviewAction {
  ADD,
  SELECT_OFF,
  SELECT_ON,
  ELLIPSES,
  SEND
}

/** Secondary actions shown by the existing app-local [org.thoughtcrime.securesms.light.LightActionPanelView]. */
enum class LightMediaReviewTool {
  DRAW,
  CROP_AND_ROTATE,
  QUALITY,
  SAVE
}

/**
 * Immutable projection of media-review state. The pager, editors, trim data and send pipeline remain
 * owned by [MediaReviewFragment]; this state contains only what the two Light chrome regions render.
 */
data class LightMediaReviewState(
  val destinationLabel: String = "",
  val message: String = "",
  val messagePlaceholder: String = "",
  val isTouchEnabled: Boolean = true,
  val isViewOnce: Boolean = false,
  val sendEnabled: Boolean = false,
  val scheduledSendEnabled: Boolean = false,
  val actions: List<LightMediaReviewAction> = emptyList(),
  val secondaryTools: List<LightMediaReviewTool> = emptyList()
) {
  val captionEnabled: Boolean
    get() = !isViewOnce

  companion object {
    /** Pure policy projection, kept here so action visibility can be exhaustively unit tested. */
    fun map(
      destinationLabel: String,
      message: CharSequence?,
      messagePlaceholder: String,
      mediaType: LightMediaReviewMediaType,
      selectedCount: Int,
      isStory: Boolean,
      isTouchEnabled: Boolean,
      isViewOnce: Boolean,
      sendEnabled: Boolean
    ): LightMediaReviewState {
      val tools = if (isTouchEnabled) {
        buildList {
          when (mediaType) {
            LightMediaReviewMediaType.IMAGE -> {
              add(LightMediaReviewTool.DRAW)
              add(LightMediaReviewTool.CROP_AND_ROTATE)
              if (!isStory) add(LightMediaReviewTool.QUALITY)
              add(LightMediaReviewTool.SAVE)
            }
            LightMediaReviewMediaType.VIDEO -> if (!isStory) add(LightMediaReviewTool.QUALITY)
            LightMediaReviewMediaType.GIF -> {
              if (!isStory) add(LightMediaReviewTool.QUALITY)
              add(LightMediaReviewTool.SAVE)
            }
            LightMediaReviewMediaType.DOCUMENT -> Unit
          }
        }
      } else {
        emptyList()
      }

      val actions = if (isTouchEnabled) {
        buildList {
          if (selectedCount == 1 && mediaType != LightMediaReviewMediaType.DOCUMENT && !isViewOnce) {
            add(LightMediaReviewAction.ADD)
          }
          if (selectedCount == 1 && !isStory && mediaType != LightMediaReviewMediaType.DOCUMENT) {
            add(if (isViewOnce) LightMediaReviewAction.SELECT_ON else LightMediaReviewAction.SELECT_OFF)
          }
          if (tools.isNotEmpty()) add(LightMediaReviewAction.ELLIPSES)
          add(LightMediaReviewAction.SEND)
        }
      } else {
        emptyList()
      }

      check(actions.size <= MAX_ACTIONS)

      return LightMediaReviewState(
        destinationLabel = destinationLabel,
        message = message?.toString().orEmpty(),
        messagePlaceholder = messagePlaceholder,
        isTouchEnabled = isTouchEnabled,
        isViewOnce = isViewOnce,
        sendEnabled = sendEnabled,
        scheduledSendEnabled = sendEnabled && !isStory,
        actions = actions.toList(),
        secondaryTools = tools.toList()
      )
    }
  }
}

/** The pure three-grid-unit review header. */
@Composable
fun LightMediaReviewTopBar(
  destinationLabel: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  LightTopBar(
    leftButton = LightBarButton.LightIcon(
      icon = LightIcons.BACK,
      onClick = onBack,
      contentDescription = stringResource(R.string.ConversationFragment__content_description_back_button)
    ),
    center = LightTopBarCenter.Text(destinationLabel),
    modifier = modifier
      .background(LightThemeTokens.colors.background)
      .testTag(TOP_BAR_TAG)
  )
}

/** The pure caption and four-grid-unit action region below the media/timeline/selection rail. */
@Composable
fun LightMediaReviewBottom(
  state: LightMediaReviewState,
  onCaptionClick: () -> Unit,
  onActionClick: (LightMediaReviewAction) -> Unit,
  onSendLongClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  if (!state.isTouchEnabled) return

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(LightThemeTokens.colors.background)
      .testTag(BOTTOM_REGION_TAG)
  ) {
    if (state.captionEnabled) {
      LightTextField(
        label = stringResource(R.string.LightMediaReview__message),
        value = state.message,
        placeholder = state.messagePlaceholder,
        onClick = onCaptionClick,
        modifier = Modifier
          .padding(horizontal = 2f.gridUnitsAsDp())
          .testTag(CAPTION_FIELD_TAG)
      )
    } else {
      DisabledCaptionField(
        value = stringResource(R.string.MediaReviewFragment__view_once_message),
        modifier = Modifier
          .padding(horizontal = 2f.gridUnitsAsDp())
          .testTag(CAPTION_FIELD_TAG)
      )
    }

    LightMediaReviewActionBar(
      state = state,
      onActionClick = onActionClick,
      onSendLongClick = onSendLongClick
    )
  }
}

@Composable
private fun DisabledCaptionField(
  value: String,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .semantics { disabled() }
  ) {
    LightText(
      text = stringResource(R.string.LightMediaReview__message),
      variant = LightTextVariant.Detail,
      modifier = Modifier.padding(top = 1f.gridUnitsAsDp())
    )
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 0.25f.gridUnitsAsDp())
    ) {
      LightText(
        text = value,
        variant = LightTextVariant.Copy,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth()
      )
      Spacer(modifier = Modifier.height(0.5f.gridUnitsAsDp()))
      Spacer(
        modifier = Modifier
          .fillMaxWidth(0.8f)
          .height(3f.designVerticalPxToDp())
          .background(LightThemeTokens.colors.contentSecondary)
      )
    }
  }
}

@Composable
private fun LightMediaReviewActionBar(
  state: LightMediaReviewState,
  onActionClick: (LightMediaReviewAction) -> Unit,
  onSendLongClick: () -> Unit
) {
  check(state.actions.size <= MAX_ACTIONS)

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(ACTION_BAR_HEIGHT_UNITS.gridUnitsAsDp())
      .padding(horizontal = 2f.gridUnitsAsDp())
      .testTag(ACTION_BAR_TAG),
    horizontalArrangement = if (state.actions.size > 1) Arrangement.SpaceBetween else Arrangement.End,
    verticalAlignment = Alignment.CenterVertically
  ) {
    state.actions.forEach { action ->
      val enabled = action != LightMediaReviewAction.SEND || state.sendEnabled
      LightMediaReviewActionButton(
        action = action,
        enabled = enabled,
        onClick = { onActionClick(action) },
        onLongClick = if (action == LightMediaReviewAction.SEND && state.scheduledSendEnabled) onSendLongClick else null
      )
    }
  }
}

/** App-local Light action target with disabled and accessible long-click behavior missing from LightBarButton. */
@Composable
private fun LightMediaReviewActionButton(
  action: LightMediaReviewAction,
  enabled: Boolean,
  onClick: () -> Unit,
  onLongClick: (() -> Unit)?
) {
  val currentOnClick by rememberUpdatedState(onClick)
  val currentOnLongClick by rememberUpdatedState(onLongClick)
  val haptics = LocalHapticFeedback.current
  val hapticsEnabled = LocalHapticsEnabled.current
  val description = actionDescription(action)
  val scheduleLabel = stringResource(R.string.ScheduleMessageTimePickerBottomSheet__schedule_send)

  val gestureModifier = if (enabled) {
    Modifier.pointerInput(action, onLongClick != null) {
      detectTapGestures(
        onTap = { currentOnClick() },
        onLongPress = currentOnLongClick?.let {
          {
            if (hapticsEnabled) {
              haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            currentOnLongClick?.invoke()
          }
        }
      )
    }
  } else {
    Modifier
  }

  Box(
    modifier = gestureModifier
      .width(ACTION_TOUCH_WIDTH_UNITS.gridUnitsAsDp())
      .fillMaxHeight()
      .semantics(mergeDescendants = true) {
        contentDescription = description
        role = Role.Button
        if (enabled) {
          onClick {
            currentOnClick()
            true
          }
          if (currentOnLongClick != null) {
            onLongClick(label = scheduleLabel) {
              currentOnLongClick?.invoke()
              true
            }
          }
        } else {
          disabled()
        }
      }
      .testTag(actionTestTag(action)),
    contentAlignment = Alignment.Center
  ) {
    LightIcon(
      icon = actionIcon(action),
      contentDescription = null
    )
  }
}

@Composable
private fun actionDescription(action: LightMediaReviewAction): String {
  return when (action) {
    LightMediaReviewAction.ADD -> stringResource(R.string.MediaReviewFragment__add_media_accessibility_label)
    LightMediaReviewAction.SELECT_OFF,
    LightMediaReviewAction.SELECT_ON -> stringResource(R.string.MediaReviewFragment__view_once_toggle_accessibility_label)
    LightMediaReviewAction.ELLIPSES -> stringResource(R.string.LightMediaReview__more_actions)
    LightMediaReviewAction.SEND -> stringResource(R.string.MediaReviewFragment__send_media_accessibility_label)
  }
}

private fun actionIcon(action: LightMediaReviewAction): LightIconConfiguration {
  return when (action) {
    LightMediaReviewAction.ADD -> LightIcons.ADD
    LightMediaReviewAction.SELECT_OFF -> LightIcons.SELECT_OFF
    LightMediaReviewAction.SELECT_ON -> LightIcons.SELECT_ON
    LightMediaReviewAction.ELLIPSES -> LightIcons.ELLIPSES
    LightMediaReviewAction.SEND -> LightIcons.SEND
  }
}

/** View host for only the top chrome; it never overlays the pager/editor. */
class LightMediaReviewTopView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs) {

  private var state: LightMediaReviewState? by mutableStateOf(null)

  var onBack: (() -> Unit)? = null

  val submittedDestinationLabel: String?
    get() = state?.destinationLabel

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  }

  fun submit(state: LightMediaReviewState) {
    this.state = state
  }

  @Composable
  override fun Content() {
    val snapshot = state ?: return
    MollyLightTheme {
      LightMediaReviewTopBar(
        destinationLabel = snapshot.destinationLabel,
        onBack = { onBack?.invoke() }
      )
    }
  }
}

/** View host for only the bottom chrome; it never overlays the pager/editor. */
class LightMediaReviewBottomView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs) {

  private var state: LightMediaReviewState? by mutableStateOf(null)

  var onCaptionClick: (() -> Unit)? = null
  var onActionClick: ((LightMediaReviewAction) -> Unit)? = null
  var onSendLongClick: (() -> Unit)? = null

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  }

  fun submit(state: LightMediaReviewState) {
    this.state = state
  }

  @Composable
  override fun Content() {
    val snapshot = state ?: return
    MollyLightTheme {
      LightMediaReviewBottom(
        state = snapshot,
        onCaptionClick = { onCaptionClick?.invoke() },
        onActionClick = { onActionClick?.invoke(it) },
        onSendLongClick = { onSendLongClick?.invoke() }
      )
    }
  }
}

internal const val TOP_BAR_TAG = "media-review-top-bar"
internal const val BOTTOM_REGION_TAG = "media-review-bottom-region"
internal const val CAPTION_FIELD_TAG = "media-review-caption"
internal const val ACTION_BAR_TAG = "media-review-action-bar"
internal fun actionTestTag(action: LightMediaReviewAction): String = "media-review-action-${action.name}"

private const val MAX_ACTIONS = 4
private const val ACTION_BAR_HEIGHT_UNITS = 4f
private const val ACTION_TOUCH_WIDTH_UNITS = 3f
