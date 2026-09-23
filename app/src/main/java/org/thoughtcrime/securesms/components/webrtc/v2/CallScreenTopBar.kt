/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Post-prejoin Light top chrome.
 *
 * This intentionally measures to the three-grid-unit bar rather than drawing the former 240dp
 * gradient. The participant pager remains exposed directly below the actual chrome, so the overlay
 * cannot become a hidden tap/swipe interceptor over video.
 */
@Composable
fun CallScreenTopBar(
  callRecipient: Recipient,
  callStatus: String?,
  modifier: Modifier = Modifier,
  onNavigationClick: () -> Unit = {},
  onCallInfoClick: () -> Unit = {}
) {
  CallScreenTopAppBar(
    callRecipient = callRecipient,
    callStatus = callStatus,
    onNavigationClick = onNavigationClick,
    onCallInfoClick = onCallInfoClick,
    modifier = modifier
  )
}
