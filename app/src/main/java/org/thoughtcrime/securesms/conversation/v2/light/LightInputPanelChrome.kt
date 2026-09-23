/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.light

import android.view.Gravity
import android.view.View
import androidx.core.view.isVisible
import com.thelightphone.sdk.ui.LightTextVariant
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ComposeText
import org.thoughtcrime.securesms.components.HidingLinearLayout
import org.thoughtcrime.securesms.components.InputPanel
import org.thoughtcrime.securesms.conversation.v2.items.light.LightItemStyle

/**
 * Restyles Signal's `InputPanel` for the Light Phone, without replacing it.
 *
 * `InputPanel` is 996 lines and `ConversationFragment` touches it 86 times; it stays exactly where it
 * is as the headless owner of the draft, the pending reply, edit mode, voice recording, link previews,
 * mentions and styling. What changes is what it *draws*. Most of the time it draws nothing at all --
 * the panel is collapsed out of the layout and `LightConversationBottomBar` stands in for it -- and it
 * is only expanded again for the three things it alone can show: the full-screen composer, an
 * in-progress recording, and a recorded voice-note draft.
 *
 * This object owns the two jobs that follow from that:
 *
 * - [install], which strips the chrome that has no place anywhere in the Light thread (the emoji
 *   toggle, the quick and inline attachment toggles, the sticker suggestion strip, the edit-mode
 *   header) and puts the real `ComposeText` on the Light type scale and palette.
 * - [setComposerMode], which switches between "the composer is up" (a bare, full-width text entry;
 *   send lives in the composer's top bar) and "Signal's own voice chrome is up" (the send toggle
 *   back, the entry out of the way, because `recording_layout` and `VoiceNoteDraftView` are laid out
 *   across the compose bubble).
 *
 * Everything is reached through `findViewById` rather than by widening `InputPanel`'s API: the ids are
 * public, and keeping the Light presentation in one Light-named file is what stops it from being lost
 * in the next upstream merge of a file that large.
 *
 * Nothing here moves a view or changes a margin. The entry's horizontal inset stays whatever
 * `conversation_input_panel.xml` gives it, and `R.dimen.light_composer_text_inset` is what the
 * composer's reply line uses to line up with it.
 */
object LightInputPanelChrome {

  /**
   * Safe to re-run, and it has to be: `InputPanel.setWallpaperEnabled` rewrites the panel's
   * background, the compose bubble's background and the compose text's colours from Signal's Material
   * attributes every time the thread's wallpaper state is presented.
   */
  @JvmStatic
  fun install(panel: InputPanel) {
    val context = panel.context

    // The panel is the surface behind the composer's text entry, and behind Signal's recording row.
    // Both are Light surfaces, so it is painted from the Light palette rather than from whatever
    // `colorSurface` happens to resolve to.
    panel.setBackgroundColor(LightItemStyle.backgroundColor(context))

    // The compose bubble is a rounded Material fill. The Light design has no containers: the entry is
    // text on black. The view itself stays -- `recording_layout` and the voice-note draft view are
    // constrained to its bounds, so removing it would leave the recording timer unanchored.
    panel.findViewById<View>(R.id.compose_bubble).background = null

    // No emoji, sticker or GIF picker anywhere in the composer, so the toggle that opens them goes.
    panel.showMediaKeyboardToggle(false)

    // Nothing suggests stickers any more -- `ConversationFragment` no longer subscribes -- but the
    // strip is collapsed as well so that a stale suggestion can never push the entry down.
    panel.findViewById<View>(R.id.input_panel_sticker_suggestion).isVisible = false

    // The camera and microphone buttons that live inside the compose bubble are gone: attachments are
    // the bottom bar's ADD slot, and voice notes are an entry in the attachment keyboard.
    // `disable()` rather than `isVisible = false`, because `updateToggleButtonState` calls
    // `show()`/`hide()` on these on every keystroke and would put them straight back.
    panel.findViewById<HidingLinearLayout>(R.id.quick_attachment_toggle).disable()
    panel.findViewById<HidingLinearLayout>(R.id.inline_attachment_container).disable()

    // Edit mode announces itself in the composer's title slot instead, and the composer's back chevron
    // cancels it, so Signal's edit header and its circular X go.
    panel.findViewById<View>(R.id.input_panel_exit_edit_mode).isVisible = false
    panel.findViewById<View>(R.id.edit_message_title).isVisible = false
    panel.findViewById<View>(R.id.edit_message_thumbnail).isVisible = false

    styleComposeText(panel.findViewById(R.id.embedded_text_editor))
  }

  /**
   * @param composer true while the Light composer is up, false while the panel is showing Signal's
   *   own recording chrome or a voice-note draft.
   */
  @JvmStatic
  fun setComposerMode(panel: InputPanel, composer: Boolean) {
    // `recording_layout` puts the timer, "slide to cancel"/cancel and the send toggle across the
    // compose bubble, and `VoiceNoteDraftView` replaces the entry outright. Both need the send toggle
    // that the composer does not, because the composer sends from its own top bar.
    //
    // Restoring it resets the alpha only on the way back from GONE. `InputPanel` fades this view in
    // and out around a recording, and resetting the alpha on every pass would snap a half-faded
    // toggle to full opacity in the middle of one.
    val buttonToggle = panel.findViewById<View>(R.id.button_toggle)
    if (composer) {
      buttonToggle.isVisible = false
    } else if (!buttonToggle.isVisible) {
      buttonToggle.alpha = 1f
      buttonToggle.isVisible = true
    }

    // INVISIBLE, not GONE, when the composer is down: `recording_layout` and `VoiceNoteDraftView` are
    // laid out across the compose bubble, and the bubble is only as tall as the entry that is sitting
    // in it. Collapsing the entry would collapse the row they draw in with it. The alpha, again, is
    // `InputPanel`'s to animate while its voice chrome is up.
    val composeText = panel.findViewById<ComposeText>(R.id.embedded_text_editor)
    if (composer) {
      composeText.alpha = 1f
      composeText.isVisible = true
    } else {
      composeText.visibility = View.INVISIBLE
    }
  }

  /**
   * Puts the real `ComposeText` on the Light type scale and palette.
   *
   * Signal styles it for a filled bubble: `?attr/signal_text_primary` on `compose_background`, centred
   * in a fixed 44dp row. On black with no bubble that is the wrong colour in the wrong place, and
   * getting the colour wrong is invisible in a build and obvious on the device -- this project has
   * shipped black-on-black message text twice already.
   */
  private fun styleComposeText(composeText: ComposeText) {
    val context = composeText.context

    LightItemStyle.apply(composeText, LightTextVariant.Paragraph)
    composeText.setTextColor(LightItemStyle.contentColor(context))
    composeText.setHintTextColor(LightItemStyle.contentSecondaryColor(context))
    composeText.background = null

    // Notes-style, as the reference client's composer calls it: the entry is anchored at the bottom of
    // its box and grows upward into the composer's black field as the text wraps, rather than sitting
    // centred in a fixed row.
    composeText.gravity = Gravity.BOTTOM or Gravity.START
  }
}
