/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.links

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.delay
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.MollyLightTheme

internal const val SIGNAL_CALL_ROW_TAG = "call-link:call-row"
internal const val SIGNAL_CALL_JOIN_TAG = "call-link:join"

/**
 * Everything the call row needs to draw, detached from database and RingRTC models.
 *
 * Keeping this projection immutable lets previews and unit tests exercise the complete Light row
 * without generating call-link credentials (which initializes RingRTC).
 */
@Immutable
data class SignalCallRowState(
  val name: String,
  val url: String,
  val isJoined: Boolean = false,
  val showJoin: Boolean = true
)

/** A compact Light call summary used by both call-link creation and details. */
@Composable
fun SignalCallRow(
  state: SignalCallRowState,
  onJoinClicked: () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = 5f.gridUnitsAsDp())
      .padding(horizontal = 1f.gridUnitsAsDp())
      .testTag(SIGNAL_CALL_ROW_TAG),
    verticalAlignment = Alignment.CenterVertically
  ) {
    LightIcon(
      icon = LightIcons.CALL,
      size = 2f,
      contentDescription = null
    )

    Spacer(modifier = Modifier.width(0.75f.gridUnitsAsDp()))

    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.Center
    ) {
      LightText(
        text = state.name.ifEmpty {
          stringResource(R.string.CreateCallLinkBottomSheetDialogFragment__signal_call)
        },
        variant = LightTextVariant.Copy,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      LightText(
        text = state.url,
        variant = LightTextVariant.Detail,
        lighten = true,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }

    if (state.showJoin) {
      Spacer(modifier = Modifier.width(0.75f.gridUnitsAsDp()))
      LightText(
        text = stringResource(
          if (state.isJoined) {
            R.string.CallLogAdapter__return
          } else {
            R.string.CreateCallLinkBottomSheetDialogFragment__join
          }
        ),
        variant = LightTextVariant.Button,
        maxLines = 1,
        modifier = Modifier
          .lightClickable(onClick = onJoinClicked)
          .padding(vertical = 1f.gridUnitsAsDp())
          .testTag(SIGNAL_CALL_JOIN_TAG)
      )
    }
  }
}

/** A full-width, three-grid-unit Light action target. */
@Composable
fun CallLinkActionRow(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(3f.gridUnitsAsDp())
      .lightClickable(enabled = enabled, onClick = onClick)
      .padding(horizontal = 1f.gridUnitsAsDp()),
    verticalAlignment = Alignment.CenterVertically
  ) {
    LightText(
      text = label,
      variant = LightTextVariant.Button,
      lighten = !enabled,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

/**
 * Light's deliberately plain approval switch. Loading disables the entire target and replaces the
 * mark with an indeterminate ellipsis, so repeated taps cannot race the repository mutation.
 */
@Composable
fun CallLinkApprovalRow(
  label: String,
  checked: Boolean,
  loading: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  val enabled = !loading
  val colors = LightThemeTokens.colors

  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(3f.gridUnitsAsDp())
      .semantics {
        role = Role.Switch
        toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
        if (!enabled) disabled()
      }
      .lightClickable(enabled = enabled, role = Role.Switch) {
        onCheckedChange(!checked)
      }
      .padding(horizontal = 1f.gridUnitsAsDp()),
    verticalAlignment = Alignment.CenterVertically
  ) {
    LightText(
      text = label,
      variant = LightTextVariant.Copy,
      lighten = !enabled,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f)
    )

    Spacer(modifier = Modifier.width(0.75f.gridUnitsAsDp()))

    if (loading) {
      LightText(
        text = "…",
        variant = LightTextVariant.Button,
        modifier = Modifier.semantics {
          progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        }
      )
    } else {
      Box(
        modifier = Modifier
          .size(1.25f.gridUnitsAsDp())
          .border(1.dp, colors.content),
        contentAlignment = Alignment.Center
      ) {
        if (checked) {
          Box(
            modifier = Modifier
              .size(0.65f.gridUnitsAsDp())
              .background(colors.content)
          )
        }
      }
    }
  }
}

/** A transient, theme-native replacement for Material snackbars on the Light call-link surfaces. */
@Composable
fun CallLinkFeedbackBanner(
  message: String?,
  modifier: Modifier = Modifier
) {
  var visible by remember(message) { mutableStateOf(message != null) }

  LaunchedEffect(message) {
    visible = message != null
    if (message != null) {
      delay(4_000)
      visible = false
    }
  }

  if (visible && message != null) {
    Box(
      modifier = modifier
        .fillMaxWidth()
        .background(LightThemeTokens.colors.background)
        .border(1.dp, LightThemeTokens.colors.content)
        .padding(1f.gridUnitsAsDp()),
      contentAlignment = Alignment.Center
    ) {
      LightText(
        text = message,
        variant = LightTextVariant.Detail,
        maxLines = 3
      )
    }
  }
}

@Preview(widthDp = 360, heightDp = 160, showBackground = true)
@Composable
private fun SignalCallRowPreview() {
  MollyLightTheme {
    Column {
      SignalCallRow(
        state = SignalCallRowState(
          name = "Call Name",
          url = "https://signal.link/call/#key=example"
        ),
        onJoinClicked = {}
      )
      SignalCallRow(
        state = SignalCallRowState(
          name = "Call Name",
          url = "https://signal.link/call/#key=example",
          isJoined = true
        ),
        onJoinClicked = {}
      )
    }
  }
}
