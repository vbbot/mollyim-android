/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.edit

import android.text.SpannedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.signal.core.util.GraphemeClusterLimitFilter
import org.signal.core.util.StringUtil
import kotlin.math.min

/**
 * The input limits the profile screen's fields enforced as `EditText` filters, applied to a Compose
 * edit instead.
 *
 * [maxGraphemes] is what `EditTextUtil.addGraphemeClusterLimitFilter` did: the edit is run through the
 * very same [GraphemeClusterLimitFilter], so an over-long insertion is cut back to whole grapheme
 * clusters (one emoji, however many code points, is one). [maxBytes], when set, is what
 * `EditProfileNameFragment.trimFieldToMaxByteLength` did afterwards: drop whole clusters off the end
 * until the UTF-8 encoding fits.
 */
data class CreateProfileFieldLimit @JvmOverloads constructor(
  val maxGraphemes: Int,
  val maxBytes: Int? = null
) {

  /**
   * [edited] as it may stand after [previous]. An edit the limits do not touch comes back as it is,
   * selection and composition included; one they cut puts the cursor after what survived of it.
   */
  fun apply(previous: TextFieldValue, edited: TextFieldValue): TextFieldValue {
    val (text, cursor) = limit(previous.text, edited.text)

    return if (text == edited.text) {
      edited
    } else {
      TextFieldValue(text, selection = TextRange(cursor))
    }
  }

  /** The text-only form of [apply]. */
  fun apply(previous: String, edited: String): String = limit(previous, edited).first

  /** The limited text, and the end of the inserted text within it. */
  private fun limit(previous: String, edited: String): Pair<String, Int> {
    if (previous == edited) {
      return edited to edited.length
    }

    // An InputFilter is told which span of the old text was replaced and with what. A Compose field
    // only reports the result, so recover the replaced span as whatever lies between the common head
    // and the common tail.
    val (head, tail) = commonEnds(previous, edited)
    val source = edited.substring(head, edited.length - tail)
    val filtered = GraphemeClusterLimitFilter(maxGraphemes)
      .filter(source, 0, source.length, SpannedString(previous), head, previous.length - tail)
      ?: source

    val limited = previous.substring(0, head) + filtered + previous.substring(previous.length - tail)
    val trimmed = if (maxBytes != null) {
      limited.substring(0, StringUtil.trimToFit(limited, maxBytes).length)
    } else {
      limited
    }

    return trimmed to min(head + filtered.length, trimmed.length)
  }

  companion object {
    /** Lengths of the common prefix and suffix of [a] and [b], never overlapping in the shorter one. */
    private fun commonEnds(a: String, b: String): Pair<Int, Int> {
      val shorter = min(a.length, b.length)

      var head = 0
      while (head < shorter && a[head] == b[head]) {
        head++
      }

      var tail = 0
      while (tail < shorter - head && a[a.length - 1 - tail] == b[b.length - 1 - tail]) {
        tail++
      }

      return head to tail
    }
  }
}
