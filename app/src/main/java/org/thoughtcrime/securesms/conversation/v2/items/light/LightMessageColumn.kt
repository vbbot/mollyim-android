/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.items.light

import com.thelightphone.sdk.ui.LightGrid

/**
 * Where a message's text column sits across the width of a thread row.
 *
 * The reference client builds this out of a row padding plus a width cap:
 *
 * ```
 * BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 1.5.gu, end = if (isMine) 2.5.gu else 1.5.gu)) {
 *   Column(Modifier.fillMaxWidth(0.875f).align(if (isMine) CenterEnd else CenterStart)) { ... }
 * }
 * ```
 *
 * Grid units are themselves a fraction of the screen width, so the resulting edges are *constant*
 * fractions of the row -- which is what lets the layouts express them as ConstraintLayout guidelines.
 * They live here rather than in the XML so there is one copy of the arithmetic and
 * `LightMessageRowGeometryTest` can pin it against the reference.
 */
object LightMessageColumn {

  /** Buffer between the row edge and the message on the side the message hangs off. */
  const val GUTTER_UNITS = 1.5f

  /**
   * Outgoing messages get a wider buffer on the *far* edge so the text stays clear of the thread
   * scrollbar, which is two grid units wide.
   */
  const val OUTGOING_END_GUTTER_UNITS = 2.5f

  /** Cap on the message column, as a fraction of the row's content width. */
  const val MESSAGE_WIDTH_FRACTION = 0.875f

  private fun unitsAsFraction(units: Float): Float = units / LightGrid.WIDTH

  /** Fraction of the row taken by the two gutters' leftovers, i.e. the content band. */
  private fun contentFraction(isIncoming: Boolean): Float {
    val endGutter = if (isIncoming) GUTTER_UNITS else OUTGOING_END_GUTTER_UNITS
    return 1f - unitsAsFraction(GUTTER_UNITS) - unitsAsFraction(endGutter)
  }

  /** Left edge of the message column, as a fraction of the row width. */
  fun startFraction(isIncoming: Boolean): Float {
    return if (isIncoming) {
      unitsAsFraction(GUTTER_UNITS)
    } else {
      endFraction(false) - MESSAGE_WIDTH_FRACTION * contentFraction(false)
    }
  }

  /** Right edge of the message column, as a fraction of the row width. */
  fun endFraction(isIncoming: Boolean): Float {
    return if (isIncoming) {
      unitsAsFraction(GUTTER_UNITS) + MESSAGE_WIDTH_FRACTION * contentFraction(true)
    } else {
      1f - unitsAsFraction(OUTGOING_END_GUTTER_UNITS)
    }
  }
}
