/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.entercode

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.thoughtcrime.securesms.registration.sms.ReceivedSmsEvent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The verification code as typed on the system keyboard, standing in for the six cells of
 * `VerificationCodeView` and Molly's own keypad.
 *
 * Digits only, at most [length] of them. [edit] and [fill] return the code when that change is the
 * one that completes it, which is the moment `VerificationCodeView` fired its complete listener; a
 * change that leaves the code complete (a seventh digit typed onto a full field) does not return it
 * again, so a code is submitted once per completion.
 */
@Stable
class VerificationCodeFieldState(val length: Int = ReceivedSmsEvent.CODE_LENGTH) {

  var value: TextFieldValue by mutableStateOf(TextFieldValue(""))
    private set

  /** Bumped to ask the screen to focus the field and raise the keyboard, as `displayKeyboard()` did. */
  var focusRequests: Int by mutableIntStateOf(0)
    private set

  val code: String
    get() = value.text

  val isComplete: Boolean
    get() = value.text.length == length

  /** Applies a keyboard edit. Returns the code if this edit completed it, else null. */
  fun edit(edited: TextFieldValue): String? {
    val wasComplete = isComplete
    val digits = edited.text.filter { it in '0'..'9' }.take(length)
    // The cursor always sits after the last digit, as the cells' insertion point did.
    value = TextFieldValue(digits, selection = TextRange(digits.length))
    return if (!wasComplete && isComplete) digits else null
  }

  /** Replaces the field with [code] (the SMS retriever's), returning it if it is a complete code. */
  fun fill(code: String): String? {
    clear()
    return edit(TextFieldValue(code))
  }

  fun clear() {
    value = TextFieldValue("")
  }

  fun requestFocus() {
    focusRequests++
  }
}

/** Where a resend-SMS or call-me request stands, from the session's next-request timestamp. */
sealed interface CodeRequestAvailability {
  /** The session gave no time for another request: shown, but inert. */
  data object Unavailable : CodeRequestAvailability

  /** Available in [minutes]:[seconds]. */
  data class Waiting(val minutes: Int, val seconds: Int) : CodeRequestAvailability

  data object Available : CodeRequestAvailability

  val enabled: Boolean
    get() = this == Available

  companion object {
    /**
     * `ActionCountDownButton.startCountDownTo`, as data: a timestamp of zero or less never enables,
     * more than a second to go counts down, anything else is available.
     */
    fun of(nextRequest: Duration, now: Duration): CodeRequestAvailability {
      if (nextRequest <= 0.seconds) {
        return Unavailable
      }
      val remaining = nextRequest - now
      return if (remaining > 1.seconds) {
        val totalSeconds = remaining.inWholeSeconds.toInt()
        Waiting(minutes = totalSeconds / 60, seconds = totalSeconds % 60)
      } else {
        Available
      }
    }
  }
}
