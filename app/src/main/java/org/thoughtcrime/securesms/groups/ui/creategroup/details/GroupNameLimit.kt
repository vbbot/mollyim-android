/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.creategroup.details

import org.signal.core.util.BreakIteratorCompat
import org.signal.core.util.StringUtil
import org.thoughtcrime.securesms.util.RemoteConfig

/**
 * The group-name length rule, applied to the value a text field is about to accept.
 *
 * The pre-Light screen installed `EditTextUtil.addGraphemeClusterLimitFilter`, which trimmed the
 * *incoming* fragment whenever an edit pushed the field past the limit. The Compose field reports the
 * whole proposed value instead of a fragment, so the equivalent is to keep the first [limit]
 * graphemes of that value.
 *
 * Trimming rather than rejecting is the part that matters to the user. A field that simply ignores
 * an over-limit edit cannot be shortened by typing: with the caret at the end, every keystroke is
 * dropped and the name is stuck. Trimming reproduces what the `InputFilter` did, and it iterates
 * with [BreakIteratorCompat] for the same reason the filter did -- a UTF-16 code-point cut would
 * split a combining sequence or a ZWJ emoji in half.
 *
 * [RemoteConfig.maxGroupNameGraphemeLength] is only read when [limit] is not supplied, so callers
 * (and tests) can pin the limit without touching remote config.
 */
fun enforceGroupNameGraphemeLimit(
  value: String,
  limit: Int = RemoteConfig.maxGroupNameGraphemeLength
): String {
  if (limit < 0 || StringUtil.getGraphemeCount(value) <= limit) {
    return value
  }

  val iterator = BreakIteratorCompat.getInstance()
  iterator.setText(value)
  return iterator.take(limit).toString()
}
