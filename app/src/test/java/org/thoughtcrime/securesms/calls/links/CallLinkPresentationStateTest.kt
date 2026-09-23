/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.links

import android.app.Application
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.calls.links.create.CreateCallLinkLightState
import org.thoughtcrime.securesms.calls.links.details.CallLinkDetailsLightState

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CallLinkPresentationStateTest {

  @Test
  fun `presentation states need only immutable display values`() {
    val row = SignalCallRowState(
      name = "Weekend call",
      url = "https://signal.link/call/#key=display-only"
    )

    val create = CreateCallLinkLightState(
      call = row,
      approvalRequired = true
    )
    val details = CallLinkDetailsLightState(
      call = row,
      canModify = true,
      approvalRequired = true
    )

    assertThat(create.call).isEqualTo(row)
    assertThat(details.call).isEqualTo(row)
    assertThat(create.approvalChangeInFlight).isEqualTo(false)
    assertThat(details.showRevocationConfirmation).isEqualTo(false)
  }

  @Test
  fun `name limit is exactly 32 grapheme clusters`() {
    val grapheme = "👨‍👩‍👧‍👦"

    val truncated = truncateCallLinkName(grapheme.repeat(33))

    assertThat(truncated).isEqualTo(grapheme.repeat(32))
  }

  @Test
  fun `name limit does not split a combining grapheme`() {
    val grapheme = "é"

    val truncated = truncateCallLinkName(grapheme.repeat(32) + "x")

    assertThat(truncated).isEqualTo(grapheme.repeat(32))
    assertThat(truncated.toList()).hasSize(grapheme.length * 32)
  }

  @Test
  fun `truncation clamps selection to the retained text`() {
    val value = TextFieldValue(
      text = "a".repeat(33),
      selection = TextRange(33)
    )

    val limited = limitCallLinkName(value)

    assertThat(limited.text).isEqualTo("a".repeat(32))
    assertThat(limited.selection).isEqualTo(TextRange(32))
  }
}
