/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.light

import org.thoughtcrime.securesms.components.menu.ActionItem
import java.util.Locale

/**
 * The one seam between Molly's menus and [LightActionPanel].
 *
 * Molly already decides which actions apply where, and decides it correctly -- a long press on a
 * message, on a call, or on a conversation each produce a gated `List<ActionItem>` carrying an icon,
 * a label and the thing to do. This drops the icon and hands the rest to the panel. Nothing here
 * knows what REPLY or DELETE mean; an action added upstream arrives as a row without this file
 * changing.
 *
 * Shared rather than copied per feature so that the two decisions it makes -- the case of the labels
 * and the order of dismiss-then-act -- cannot drift between one panel and the next.
 */
object LightPanelActions {

  /**
   * @param actions Molly's gated menu, in Molly's order.
   * @param onDismiss closes the panel. Run *before* the action: an action that opens the composer,
   *   enters multi-select or raises a dialog is laying claim to the same screen the panel is
   *   sitting on.
   */
  @JvmStatic
  fun from(actions: List<ActionItem>, onDismiss: () -> Unit): List<LightPanelAction> {
    return actions.map { action ->
      // Upper case at the display edge rather than in the strings, because these are Signal's own
      // menu labels and they are shared with the dropdown, the selection toolbar and every
      // translation of both. Locale-sensitive: an invariant upper case mangles Turkish.
      LightPanelAction(action.title.toString().uppercase(Locale.getDefault())) {
        onDismiss()
        action.action.run()
      }
    }
  }
}
