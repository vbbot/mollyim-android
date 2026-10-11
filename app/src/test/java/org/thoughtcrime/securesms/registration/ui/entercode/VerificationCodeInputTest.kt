/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.entercode

import androidx.compose.ui.text.input.TextFieldValue
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class VerificationCodeInputTest {

  @Test
  fun `edit filters to digits, caps the length, and returns the code only on completion`() {
    val field = VerificationCodeFieldState(length = 6)

    assertThat(field.edit(TextFieldValue("1 2-3"))).isNull()
    assertThat(field.code).isEqualTo("123")
    assertThat(field.edit(TextFieldValue("1234567"))).isEqualTo("123456")
    assertThat(field.code).isEqualTo("123456")
    assertThat(field.edit(TextFieldValue("1234569"))).isNull()
  }

  @Test
  fun `fill replaces the field and returns a complete code`() {
    val field = VerificationCodeFieldState(length = 6)
    field.edit(TextFieldValue("99"))

    assertThat(field.fill("246810")).isEqualTo("246810")
    assertThat(field.code).isEqualTo("246810")
  }

  @Test
  fun `availability matches ActionCountDownButton`() {
    val now = 1_000.seconds

    assertThat(CodeRequestAvailability.of(0.seconds, now)).isEqualTo(CodeRequestAvailability.Unavailable)
    assertThat(CodeRequestAvailability.of(now + 125.seconds, now)).isEqualTo(CodeRequestAvailability.Waiting(minutes = 2, seconds = 5))
    assertThat(CodeRequestAvailability.of(now + 1.seconds, now)).isEqualTo(CodeRequestAvailability.Available)
    assertThat(CodeRequestAvailability.of(now + 1.seconds + 1.milliseconds, now)).isEqualTo(CodeRequestAvailability.Waiting(minutes = 0, seconds = 1))
    assertThat(CodeRequestAvailability.of(now - 5.seconds, now)).isEqualTo(CodeRequestAvailability.Available)
  }
}
