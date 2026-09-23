/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.light

import android.content.Context
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.light.LightPanelAction
import java.util.Locale

/**
 * The first level of the Light context window: what a long press offers for one message.
 *
 * Molly already decides which actions apply to a message, and decides it correctly --
 * `ConversationReactionOverlay.buildMessageMenu` returns a gated list of [ActionItem], each carrying
 * an icon, a label and the thing to do. This takes that list, drops the icon, and hands the rest to
 * the panel. Nothing here knows what REPLY or PIN mean; an action added upstream arrives as a row
 * without this file changing.
 *
 * The reaction rows are the one thing added on top, because Signal puts reactions in a scrubber
 * above the menu rather than in it, and the Light Phone has no scrubber -- they become rows like
 * everything else, at the head of the list where they stay reachable without scrolling.
 */
object LightMessageMenu {

  /**
   * @param actions Molly's gated menu, in Molly's order.
   * @param canReact whether this message can be reacted to at all -- false for release notes and for
   *   a group you have left, where the rest of the menu still stands.
   * @param hasOwnReaction whether you have already reacted to this message. One reaction at a time,
   *   as Signal models it, so an existing one turns REACT into EDIT REACTION and adds a way off.
   * @param onOpenReactionKeys descends to the panel's second level. The only row that does not
   *   complete, and so the only one that must not dismiss.
   * @param onRemoveReaction takes your existing reaction off the message.
   * @param onDismiss closes the panel. Run *before* the action, as the call menu does: an action
   *   that opens the composer or enters multi-select is laying claim to the same bottom slot the
   *   panel is sitting in.
   */
  @JvmStatic
  fun rows(
    context: Context,
    actions: List<ActionItem>,
    canReact: Boolean,
    hasOwnReaction: Boolean,
    onOpenReactionKeys: () -> Unit,
    onRemoveReaction: () -> Unit,
    onDismiss: () -> Unit
  ): List<LightPanelAction> {
    return buildList {
      if (canReact) {
        val reactLabel = if (hasOwnReaction) R.string.LightMessageMenu__edit_reaction else R.string.LightMessageMenu__react
        add(LightPanelAction(context.getString(reactLabel), onOpenReactionKeys))

        if (hasOwnReaction) {
          add(
            LightPanelAction(context.getString(R.string.LightMessageMenu__remove_reaction)) {
              onDismiss()
              onRemoveReaction()
            }
          )
        }
      }

      actions.forEach { action ->
        // Upper case at the display edge rather than in the strings, because these are Signal's own
        // menu labels and they are shared with the dropdown, the selection toolbar and every
        // translation of both. Locale-sensitive: an invariant upper case mangles Turkish.
        add(
          LightPanelAction(action.title.toString().uppercase(Locale.getDefault())) {
            onDismiss()
            action.action.run()
          }
        )
      }
    }
  }
}
