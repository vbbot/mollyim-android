/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.light

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

/**
 * The thread's bottom strip has three claimants and room for one, and `ConversationFragment` can
 * change the answer from a dozen places -- every `setHideFor...` call site, the composer opening and
 * closing, the recorder's four callbacks, and the voice-note draft subscription. Getting it wrong
 * either stacks the Light bar on top of Signal's chrome or leaves the thread with no way to type, and
 * neither shows up in a build.
 */
class LightThreadBottomSlotTest {

  @Test
  fun `an idle thread gives the strip to the Light bar`() {
    assertThat(slot()).isEqualTo(LightThreadBottomSlot.LIGHT_BAR)
  }

  @Test
  fun `the composer takes the strip, because the entry it holds is the real one`() {
    assertThat(slot(composerOpen = true)).isEqualTo(LightThreadBottomSlot.INPUT_PANEL)
  }

  @Test
  fun `a recording takes the strip, because the timer and cancel live in the panel`() {
    assertThat(slot(recording = true)).isEqualTo(LightThreadBottomSlot.INPUT_PANEL)
  }

  @Test
  fun `a voice-note draft takes the strip, or there is no way to play or send it`() {
    assertThat(slot(hasVoiceNoteDraft = true)).isEqualTo(LightThreadBottomSlot.INPUT_PANEL)
  }

  /**
   * Search, multi-select and disabled input each put their own bar in this slot and hide the input
   * panel with it. The Light bar has to go with it -- it would otherwise float over the search
   * navigation or the message-request buttons.
   */
  @Test
  fun `a hidden input panel takes the Light bar with it`() {
    assertThat(slot(inputPanelHidden = true)).isEqualTo(LightThreadBottomSlot.NEITHER)
  }

  @Test
  fun `hiding the input panel beats everything else that wants the strip`() {
    assertThat(slot(inputPanelHidden = true, composerOpen = true)).isEqualTo(LightThreadBottomSlot.NEITHER)
    assertThat(slot(inputPanelHidden = true, recording = true)).isEqualTo(LightThreadBottomSlot.NEITHER)
    assertThat(slot(inputPanelHidden = true, hasVoiceNoteDraft = true)).isEqualTo(LightThreadBottomSlot.NEITHER)
  }

  /**
   * Recording is started from the attachment keyboard, which is opened from the bar, which is only
   * reachable with the composer closed -- but the panel is the answer either way, so the two can
   * never end up fighting over it.
   */
  @Test
  fun `the panel wins whichever of its own reasons apply`() {
    assertThat(slot(composerOpen = true, recording = true, hasVoiceNoteDraft = true))
      .isEqualTo(LightThreadBottomSlot.INPUT_PANEL)
  }

  private fun slot(
    inputPanelHidden: Boolean = false,
    composerOpen: Boolean = false,
    recording: Boolean = false,
    hasVoiceNoteDraft: Boolean = false
  ): LightThreadBottomSlot {
    return LightThreadBottomSlot.forState(
      inputPanelHidden = inputPanelHidden,
      composerOpen = composerOpen,
      recording = recording,
      hasVoiceNoteDraft = hasVoiceNoteDraft
    )
  }
}
