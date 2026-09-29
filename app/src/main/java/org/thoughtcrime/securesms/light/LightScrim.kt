/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.light

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Swallows every pointer event that lands on this node, and does nothing else.
 *
 * The Light design language requires that a panel owns the area it covers: a background alone
 * consumes nothing in Compose, so an opaque modal drawn over live content will pass any tap in an
 * empty region straight through to the row underneath it. This is that same reasoning as
 * `LightPanelFrame`'s, lifted out of the panel and into one modifier, because the group surfaces
 * need it in five places and the mistake is easy to make again in the sixth.
 *
 * Apply it to a scrim that is a *sibling* of the modal's content, never to the modal's own root:
 * a parent that consumes competes with its children for the same hit path, whereas a sibling
 * scrim only ever sees the region nothing else claimed.
 */
fun Modifier.lightScrim(): Modifier = pointerInput(Unit) {
  awaitEachGesture {
    awaitFirstDown(requireUnconsumed = false).consume()
    while (true) {
      val event = awaitPointerEvent()
      event.changes.forEach { it.consume() }
      if (event.changes.none { it.pressed }) break
    }
  }
}
