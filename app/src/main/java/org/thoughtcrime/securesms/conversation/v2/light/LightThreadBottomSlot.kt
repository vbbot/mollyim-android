/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.light

/**
 * Who holds the strip along the bottom of the conversation thread.
 *
 * Three things want it and only one can have it, so the rule is written down once, here, rather than
 * spread across the half-dozen places in `ConversationFragment` that can change the answer.
 */
enum class LightThreadBottomSlot {

  /**
   * Neither. Something else has claimed the strip and hidden the input panel along with it: disabled
   * input and message requests (`conversation_disabled_input`), in-conversation search
   * (`conversation_search_bottom_bar`), or multi-select (`conversation_bottom_action_bar`).
   */
  NEITHER,

  /**
   * Signal's `InputPanel`, because it has something of its own to draw -- the Light composer's text
   * entry, an in-progress recording, or a recorded voice-note draft.
   */
  INPUT_PANEL,

  /** The Light thread's own three-icon bar: call, attach, compose. */
  LIGHT_BAR;

  companion object {

    /**
     * @param inputPanelHidden `InputPanel.isHidden`, the single predicate over every `setHideFor...`
     *   flag. Reading it rather than restating the three conditions is what stops the bar from
     *   drifting out of step with the panel it stands in for.
     */
    @JvmStatic
    fun forState(
      inputPanelHidden: Boolean,
      composerOpen: Boolean,
      recording: Boolean,
      hasVoiceNoteDraft: Boolean
    ): LightThreadBottomSlot {
      return when {
        inputPanelHidden -> NEITHER
        composerOpen || recording || hasVoiceNoteDraft -> INPUT_PANEL
        else -> LIGHT_BAR
      }
    }
  }
}
