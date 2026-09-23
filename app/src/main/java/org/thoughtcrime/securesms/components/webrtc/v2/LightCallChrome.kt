/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightBottomBarItem
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.webrtc.WebRtcAudioOutput
import org.thoughtcrime.securesms.light.rememberLightTintedPainter

/** User-visible actions in the active-call chrome. Business call state stays in the existing owners. */
enum class LightCallControlAction {
  AUDIO_OUTPUT,
  VIDEO,
  MICROPHONE,
  GROUP_RING,
  MORE,
  END_CALL
}

/**
 * Immutable projection of [CallControlsState] used only to draw and route the Light controls.
 *
 * No call policy is decided here: visibility still comes from [CallControlsState.fromViewModelData],
 * permission checks still happen in [CallControls], and audio-device behavior remains in
 * [AudioOutputPickerController]. Keeping the projection primitive also lets the chrome be composed in
 * tests without creating RingRTC or a call service.
 */
@Immutable
data class LightCallControlsPresentation(
  val controls: List<Control> = emptyList(),
  val startCallText: String? = null,
  val startWithVideo: Boolean = false
) {
  @Immutable
  data class Control(
    val action: LightCallControlAction,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val ringingAllowed: Boolean = false
  )

  companion object {
    fun from(
      state: CallControlsState,
      hasCameraPermission: Boolean,
      hasRecordAudioPermission: Boolean,
      startCallText: String?
    ): LightCallControlsPresentation {
      val controls = buildList {
        if (state.displayAudioOutputToggle) {
          add(Control(LightCallControlAction.AUDIO_OUTPUT, selected = state.audioOutput != WebRtcAudioOutput.HANDSET, enabled = !state.isAudioOutputChangePending))
        }
        if (state.displayVideoToggle && !state.isLocalScreenSharing) {
          add(Control(LightCallControlAction.VIDEO, selected = state.isVideoEnabled && hasCameraPermission))
        }
        if (state.displayMicToggle) {
          add(Control(LightCallControlAction.MICROPHONE, selected = state.isMicEnabled && hasRecordAudioPermission))
        }
        if (state.displayGroupRingingToggle) {
          add(Control(LightCallControlAction.GROUP_RING, selected = state.isGroupRingingEnabled, ringingAllowed = state.isGroupRingingAllowed))
        }
        if (state.displayAdditionalActions) {
          add(Control(LightCallControlAction.MORE))
        }
        if (state.displayEndCallButton) {
          add(Control(LightCallControlAction.END_CALL))
        }
      }

      return LightCallControlsPresentation(
        controls = controls.toList(),
        startCallText = startCallText.takeIf { state.displayStartCallButton },
        startWithVideo = state.isVideoEnabled
      )
    }
  }
}

/** Stable callback boundary between the pure Light chrome and the existing listener/controller owners. */
@Immutable
data class LightCallControlCallbacks(
  val onAudioOutput: () -> Unit = {},
  val onVideoChanged: (Boolean) -> Unit = {},
  val onMicrophoneChanged: (Boolean) -> Unit = {},
  val onGroupRingChanged: (Boolean, Boolean) -> Unit = { _, _ -> },
  val onMore: () -> Unit = {},
  val onEndCall: () -> Unit = {},
  val onStartCall: (Boolean) -> Unit = {},
  val onVideoTooltipDismissed: () -> Unit = {}
) {
  fun invoke(control: LightCallControlsPresentation.Control) {
    if (!control.enabled) return

    when (control.action) {
      LightCallControlAction.AUDIO_OUTPUT -> onAudioOutput()
      LightCallControlAction.VIDEO -> onVideoChanged(!control.selected)
      LightCallControlAction.MICROPHONE -> onMicrophoneChanged(!control.selected)
      LightCallControlAction.GROUP_RING -> onGroupRingChanged(!control.selected, control.ringingAllowed)
      LightCallControlAction.MORE -> onMore()
      LightCallControlAction.END_CALL -> onEndCall()
    }
  }
}

/**
 * The bounded active-call chrome. Only this measured strip handles control gestures; it deliberately
 * has no full-screen pointer modifier, so taps and swipes elsewhere continue to reach participant
 * paging, video focus and long-press handling.
 */
@Composable
fun LightCallControls(
  presentation: LightCallControlsPresentation,
  callbacks: LightCallControlCallbacks,
  audioOutputIcon: Painter,
  displayVideoTooltip: Boolean,
  moreButtonModifier: Modifier = Modifier,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(LightThemeTokens.colors.background)
      .testTag(LIGHT_CALL_CONTROLS_TAG),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    if (presentation.controls.isNotEmpty()) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .height(ACTIVE_CONTROLS_HEIGHT_UNITS.gridUnitsAsDp()),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        presentation.controls.forEach { control ->
          val controlModifier = Modifier
            .weight(1f)
            .then(if (control.action == LightCallControlAction.MORE) moreButtonModifier else Modifier)

          if (control.action == LightCallControlAction.VIDEO) {
            // Modifier.weight() must land on a plain layout node: TooltipBox's SubcomposeLayout does not
            // honor the Row's forced-width weight constraint (its reported/placed bounds span the full
            // row instead of its slot), which let every other button's click resolve to this one. Giving
            // the Box the weight and TooltipBox a fixed, already-resolved size keeps TooltipBox out of the
            // weight protocol entirely.
            Box(modifier = controlModifier) {
              CallScreenTooltipBox(
                text = stringResource(R.string.WebRtcCallActivity__tap_here_to_turn_on_your_video),
                displayTooltip = displayVideoTooltip,
                onTooltipDismissed = callbacks.onVideoTooltipDismissed,
                modifier = Modifier.fillMaxSize()
              ) {
                LightCallControlButton(
                  control = control,
                  audioOutputIcon = audioOutputIcon,
                  onClick = { callbacks.invoke(control) },
                  modifier = Modifier.fillMaxSize()
                )
              }
            }
          } else {
            LightCallControlButton(
              control = control,
              audioOutputIcon = audioOutputIcon,
              onClick = { callbacks.invoke(control) },
              modifier = controlModifier
            )
          }
        }
      }
    }

    val startText = presentation.startCallText
    if (startText != null) {
      LightBottomBar(
        items = listOf(
          LightBarButton.Text(
            text = startText,
            contentDescription = startText,
            onClick = { callbacks.onStartCall(presentation.startWithVideo) }
          )
        ),
        modifier = Modifier.testTag(LIGHT_CALL_START_BAR_TAG)
      )
    }
  }
}

@Composable
private fun LightCallControlButton(
  control: LightCallControlsPresentation.Control,
  audioOutputIcon: Painter,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val currentOnClick = rememberUpdatedState(onClick)
  val description = controlDescription(control.action)
  val painter = if (control.action == LightCallControlAction.AUDIO_OUTPUT) {
    audioOutputIcon
  } else {
    rememberLightTintedPainter(controlIcon(control))
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .lightClickable(enabled = control.enabled, role = Role.Button) { currentOnClick.value() }
      .semantics(mergeDescendants = true) {
        contentDescription = description
        role = Role.Button
        if (!control.enabled) disabled()
      }
      .testTag(lightCallControlTag(control.action)),
    contentAlignment = Alignment.Center
  ) {
    Image(
      painter = painter,
      contentDescription = null,
      modifier = Modifier.size(CONTROL_ICON_SIZE_UNITS.gridUnitsAsDp())
    )
  }
}

@Composable
private fun controlDescription(action: LightCallControlAction): String = when (action) {
  LightCallControlAction.AUDIO_OUTPUT -> stringResource(R.string.WebRtcAudioOutputToggle__audio_output)
  LightCallControlAction.VIDEO -> stringResource(R.string.WebRtcCallView__toggle_camera)
  LightCallControlAction.MICROPHONE -> stringResource(R.string.WebRtcCallView__toggle_mute)
  LightCallControlAction.GROUP_RING -> stringResource(R.string.WebRtcCallView__toggle_group_ringing)
  LightCallControlAction.MORE -> stringResource(R.string.WebRtcCallView__additional_actions)
  LightCallControlAction.END_CALL -> stringResource(R.string.WebRtcCallView__end_call)
}

@DrawableRes
private fun controlIcon(control: LightCallControlsPresentation.Control): Int = when (control.action) {
  LightCallControlAction.AUDIO_OUTPUT -> error("Audio output supplies its current device icon")
  LightCallControlAction.VIDEO -> if (control.selected) R.drawable.symbol_video_fill_24 else R.drawable.symbol_video_slash_fill_24
  LightCallControlAction.MICROPHONE -> if (control.selected) R.drawable.symbol_mic_fill_white_24 else R.drawable.symbol_mic_slash_fill_24
  LightCallControlAction.GROUP_RING -> if (control.selected) R.drawable.symbol_bell_ring_fill_white_24 else R.drawable.symbol_bell_slash_fill_24
  LightCallControlAction.MORE -> R.drawable.symbol_more_white_24
  LightCallControlAction.END_CALL -> R.drawable.symbol_phone_down_fill_24
}

/** Primitive incoming-call copy. The recipient and renderer remain outside this presentation seam. */
@Immutable
data class LightIncomingCallPresentation(
  val callerName: String,
  val callType: String,
  val callStatus: String? = null,
  val isVideoCall: Boolean
) {
  val actions: List<LightIncomingCallAction>
    get() = if (isVideoCall) {
      listOf(LightIncomingCallAction.DECLINE, LightIncomingCallAction.ANSWER_WITHOUT_VIDEO, LightIncomingCallAction.ANSWER)
    } else {
      listOf(LightIncomingCallAction.DECLINE, LightIncomingCallAction.ANSWER)
    }
}

enum class LightIncomingCallAction {
  DECLINE,
  ANSWER_WITHOUT_VIDEO,
  ANSWER
}

@Immutable
data class LightIncomingCallCallbacks(
  val onNavigateUp: () -> Unit = {},
  val onCallInfo: () -> Unit = {},
  val onDecline: () -> Unit = {},
  val onAnswerWithoutVideo: () -> Unit = {},
  val onAnswer: () -> Unit = {}
) {
  fun invoke(action: LightIncomingCallAction) {
    when (action) {
      LightIncomingCallAction.DECLINE -> onDecline()
      LightIncomingCallAction.ANSWER_WITHOUT_VIDEO -> onAnswerWithoutVideo()
      LightIncomingCallAction.ANSWER -> onAnswer()
    }
  }
}

/**
 * Light incoming-call overlay. The central metadata is visual only; pointer handling exists solely on
 * the top and bottom chrome so an enabled local-video preview keeps its gesture surface.
 */
@Composable
fun LightIncomingCallChrome(
  presentation: LightIncomingCallPresentation,
  callbacks: LightIncomingCallCallbacks,
  avatar: @Composable () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .testTag(LIGHT_INCOMING_CALL_CHROME_TAG)
  ) {
    LightTopBar(
      leftButton = LightBarButton.LightIcon(
        icon = LightIcons.BACK,
        onClick = callbacks.onNavigateUp,
        contentDescription = stringResource(R.string.CallScreenTopBar__go_back)
      ),
      center = null,
      rightButton = LightBarButton.LightIcon(
        icon = LightIcons.LIST,
        onClick = callbacks.onCallInfo,
        contentDescription = stringResource(R.string.CallScreenTopBar__call_information)
      ),
      modifier = Modifier
        .align(Alignment.TopCenter)
        .statusBarsPadding()
        .background(LightThemeTokens.colors.background.copy(alpha = CHROME_BACKGROUND_ALPHA))
        .testTag(LIGHT_INCOMING_TOP_BAR_TAG)
    )

    Column(
      modifier = Modifier
        .align(Alignment.Center)
        .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 4f.gridUnitsAsDp()),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      LightText(
        text = presentation.callType,
        variant = LightTextVariant.Detail,
        align = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
      avatar()
      Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
      LightText(
        text = presentation.callerName,
        variant = LightTextVariant.Heading,
        align = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
      presentation.callStatus?.takeIf { it.isNotBlank() }?.let { status ->
        Spacer(modifier = Modifier.height(0.5f.gridUnitsAsDp()))
        LightText(
          text = status,
          variant = LightTextVariant.Fine,
          align = TextAlign.Center,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
      }
    }

    LightBottomBar(
      items = presentation.actions.map { action -> incomingActionButton(action, callbacks) },
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .background(LightThemeTokens.colors.background)
        .navigationBarsPadding()
        .testTag(LIGHT_INCOMING_ACTION_BAR_TAG)
    )
  }
}

@Composable
private fun incomingActionButton(
  action: LightIncomingCallAction,
  callbacks: LightIncomingCallCallbacks
): LightBottomBarItem {
  val description = when (action) {
    LightIncomingCallAction.DECLINE -> stringResource(R.string.WebRtcCallScreen__decline)
    LightIncomingCallAction.ANSWER_WITHOUT_VIDEO -> stringResource(R.string.WebRtcCallScreen__answer_without_video)
    LightIncomingCallAction.ANSWER -> stringResource(R.string.WebRtcCallScreen__answer)
  }

  val icon = when (action) {
    LightIncomingCallAction.DECLINE -> LightIcons.DENY
    LightIncomingCallAction.ANSWER_WITHOUT_VIDEO -> LightIcons.MUTE
    LightIncomingCallAction.ANSWER -> LightIcons.ACCEPT
  }

  return LightBarButton.LightIcon(
    icon = icon,
    onClick = { callbacks.invoke(action) },
    contentDescription = description
  )
}

/** Shared Light top chrome used by prejoin, joining and active-call states. */
@Composable
fun LightCallTopBar(
  title: String?,
  status: String?,
  onNavigationClick: () -> Unit,
  onCallInfoClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val center = remember(title, status) {
    when {
      !title.isNullOrBlank() && !status.isNullOrBlank() -> LightTopBarCenter.TwoLineDetail(title, status)
      !title.isNullOrBlank() -> LightTopBarCenter.Text(title)
      !status.isNullOrBlank() -> LightTopBarCenter.Text(status)
      else -> null
    }
  }

  LightTopBar(
    leftButton = LightBarButton.LightIcon(
      icon = LightIcons.BACK,
      onClick = onNavigationClick,
      contentDescription = stringResource(R.string.CallScreenTopBar__go_back)
    ),
    center = center,
    rightButton = LightBarButton.LightIcon(
      icon = LightIcons.LIST,
      onClick = onCallInfoClick,
      contentDescription = stringResource(R.string.CallScreenTopBar__call_information)
    ),
    modifier = modifier
      .background(LightThemeTokens.colors.background.copy(alpha = CHROME_BACKGROUND_ALPHA))
      .testTag(LIGHT_CALL_TOP_BAR_TAG)
  )
}

internal fun lightCallControlTag(action: LightCallControlAction): String = "light-call-control-${action.name.lowercase()}"

internal const val LIGHT_CALL_CONTROLS_TAG = "light-call-controls"
internal const val LIGHT_CALL_START_BAR_TAG = "light-call-start-bar"
internal const val LIGHT_CALL_TOP_BAR_TAG = "light-call-top-bar"
internal const val LIGHT_INCOMING_CALL_CHROME_TAG = "light-incoming-call-chrome"
internal const val LIGHT_INCOMING_TOP_BAR_TAG = "light-incoming-top-bar"
internal const val LIGHT_INCOMING_ACTION_BAR_TAG = "light-incoming-action-bar"

private const val ACTIVE_CONTROLS_HEIGHT_UNITS = 4f
private const val CONTROL_ICON_SIZE_UNITS = 2f
private const val CHROME_BACKGROUND_ALPHA = 0.72f
