/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.light

import android.app.Application
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.components.menu.ActionItem

/**
 * The seam between Molly's message menu and the Light context window.
 *
 * The point of this mapping is that it knows nothing: `buildMessageMenu` decides which of the
 * fourteen actions apply to a message and gates them, and this turns whatever comes back into rows.
 * These tests hold that line -- an unknown action still becomes a row, in Molly's order -- and pin
 * the two things the mapping *does* decide: that the reaction rows lead, and that a row which
 * completes closes the panel while the one that descends does not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LightMessageMenuTest {

  private val context = RuntimeEnvironment.getApplication()

  @Test
  fun `every action Molly offers becomes a row, in Molly's order`() {
    val rows = rows(actions = listOf(action("Reply"), action("Copy"), action("Delete")), canReact = false)

    assertThat(rows.map { it.label }).containsExactly("REPLY", "COPY", "DELETE")
  }

  /** The labels are Signal's, shared with the dropdown and the selection toolbar, so the case is ours. */
  @Test
  fun `labels are upper cased for the Light Phone's register`() {
    val rows = rows(actions = listOf(action("End poll"), action("Star (Labs)")), canReact = false)

    assertThat(rows.map { it.label }).containsExactly("END POLL", "STAR (LABS)")
  }

  @Test
  fun `choosing an action closes the panel and then runs it`() {
    val order = mutableListOf<String>()
    val rows = rows(
      actions = listOf(ActionItem(0, "Copy") { order += "action" }),
      canReact = false,
      onDismiss = { order += "dismiss" }
    )

    rows.single().onSelected()

    // Dismiss first: an action that opens the composer or enters multi-select claims the very slot
    // the panel is sitting in.
    assertThat(order).containsExactly("dismiss", "action")
  }

  @Test
  fun `a message you can react to leads with REACT`() {
    val rows = rows(actions = listOf(action("Reply")), canReact = true)

    assertThat(rows.map { it.label }).containsExactly("REACT", "REPLY")
  }

  /**
   * Release notes and a group you have left: the rest of the menu still stands, but there is nothing
   * to react with, and a REACT row that opened an inert grid would be worse than no row.
   */
  @Test
  fun `a message you cannot react to gets no reaction rows at all`() {
    val rows = rows(actions = listOf(action("Reply"), action("Delete")), canReact = false)

    assertThat(rows.map { it.label }).containsExactly("REPLY", "DELETE")
  }

  /** One reaction at a time, as Signal models it, so an existing one is edited or taken off. */
  @Test
  fun `an existing reaction turns REACT into EDIT REACTION and offers a way off`() {
    val rows = rows(actions = listOf(action("Reply")), canReact = true, hasOwnReaction = true)

    assertThat(rows.map { it.label }).containsExactly("EDIT REACTION", "REMOVE REACTION", "REPLY")
  }

  @Test
  fun `REACT descends rather than completing, so it must not close the panel`() {
    var descended = false
    var dismissed = false
    val rows = rows(
      actions = emptyList(),
      canReact = true,
      onOpenReactionKeys = { descended = true },
      onDismiss = { dismissed = true }
    )

    rows.single().onSelected()

    assertThat(descended).isTrue()
    assertThat(dismissed).isFalse()
  }

  @Test
  fun `REMOVE REACTION closes the panel and takes the reaction off`() {
    var removed = false
    var dismissed = false
    val rows = rows(
      actions = emptyList(),
      canReact = true,
      hasOwnReaction = true,
      onRemoveReaction = { removed = true },
      onDismiss = { dismissed = true }
    )

    rows.last().onSelected()

    assertThat(removed).isTrue()
    assertThat(dismissed).isTrue()
  }

  /**
   * The mapping is deliberately blind to which actions exist: whatever the next upstream merge adds
   * to `buildMessageMenu` arrives as a row without this file being touched.
   */
  @Test
  fun `an action this file has never heard of still becomes a row`() {
    val rows = rows(actions = listOf(action("Summon a badger")), canReact = false)

    assertThat(rows.single().label).isEqualTo("SUMMON A BADGER")
  }

  private fun action(title: String) = ActionItem(0, title) {}

  private fun rows(
    actions: List<ActionItem>,
    canReact: Boolean,
    hasOwnReaction: Boolean = false,
    onOpenReactionKeys: () -> Unit = {},
    onRemoveReaction: () -> Unit = {},
    onDismiss: () -> Unit = {}
  ) = LightMessageMenu.rows(
    context = context,
    actions = actions,
    canReact = canReact,
    hasOwnReaction = hasOwnReaction,
    onOpenReactionKeys = onOpenReactionKeys,
    onRemoveReaction = onRemoveReaction,
    onDismiss = onDismiss
  )
}
