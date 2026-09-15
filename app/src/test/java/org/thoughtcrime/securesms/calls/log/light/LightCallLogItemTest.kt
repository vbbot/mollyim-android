/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.log.light

import android.app.Application
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.calls.log.CallLogRow
import org.thoughtcrime.securesms.calls.log.CallLogSelectionState
import org.thoughtcrime.securesms.database.CallTable

/**
 * The Light call log's row vocabulary, and what the projection does with the rows that are not calls.
 *
 * The vocabulary is the thing worth pinning: it is the whole of what the Light row says about a call
 * beyond who and when, because the Material row's arrow and camera glyphs have no place in this
 * design. See [LightCallLogItem.detailFor] for why it is words.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LightCallLogItemTest {

  private val context = RuntimeEnvironment.getApplication()

  /**
   * A missed voice call says nothing at all: the row's leading glyph is already the marker, and a
   * word beside it would only spend the contrast that makes the glyph readable at a glance.
   */
  @Test
  fun `a missed voice call gets no token, because the glyph is the marker`() {
    assertThat(detail(CallTable.Type.AUDIO_CALL, CallTable.Direction.INCOMING, missed = true)).isEmpty()
  }

  @Test
  fun `an answered voice call says which way it went`() {
    assertThat(detail(CallTable.Type.AUDIO_CALL, CallTable.Direction.INCOMING, missed = false)).isEqualTo("IN")
    assertThat(detail(CallTable.Type.AUDIO_CALL, CallTable.Direction.OUTGOING, missed = false)).isEqualTo("OUT")
  }

  @Test
  fun `a video call says so, and which way it went`() {
    assertThat(detail(CallTable.Type.VIDEO_CALL, CallTable.Direction.INCOMING, missed = false)).isEqualTo("VIDEO IN")
    assertThat(detail(CallTable.Type.VIDEO_CALL, CallTable.Direction.OUTGOING, missed = false)).isEqualTo("VIDEO OUT")
  }

  /** Missed is already on the glyph, so a missed video call only has to say that it was video. */
  @Test
  fun `a missed video call keeps the medium and drops the direction`() {
    assertThat(detail(CallTable.Type.VIDEO_CALL, CallTable.Direction.INCOMING, missed = true)).isEqualTo("VIDEO")
  }

  /** Group and ad-hoc calls have no meaningful direction -- Signal's own row does not show one either. */
  @Test
  fun `group and call link rows name themselves instead of a direction`() {
    assertThat(detail(CallTable.Type.GROUP_CALL, CallTable.Direction.INCOMING, missed = false)).isEqualTo("GROUP")
    assertThat(detail(CallTable.Type.GROUP_CALL, CallTable.Direction.OUTGOING, missed = true)).isEqualTo("GROUP")
    assertThat(detail(CallTable.Type.AD_HOC_CALL, CallTable.Direction.OUTGOING, missed = false)).isEqualTo("LINK")
  }

  /**
   * Holes in the paged list become blank rows rather than being filtered out, so the row a user is
   * looking at keeps the data-source index the paging controller understands.
   */
  @Test
  fun `unloaded pages become placeholders that remember their source index`() {
    val state = map(listOf(null, null, CallLogRow.CreateCallLink))

    assertThat(state.rows.map { it.kind }).containsExactly(
      LightCallLogItem.Kind.PLACEHOLDER,
      LightCallLogItem.Kind.PLACEHOLDER,
      LightCallLogItem.Kind.CREATE_CALL_LINK
    )
    assertThat(state.rows.map { it.sourceIndex }).containsExactly(0, 1, 2)
    assertThat(state.pagingIndexFor(2)).isEqualTo(2)
    assertThat(state.pagingIndexFor(9)).isEqualTo(-1)
  }

  /**
   * The filter footer is a button, not a call. The filter is already cleared from the pull view at
   * the top of the list and from the overflow menu.
   */
  @Test
  fun `the clear filter footer is dropped rather than drawn`() {
    val state = map(listOf(CallLogRow.CreateCallLink, CallLogRow.ClearFilter))

    assertThat(state.rows.map { it.kind }).containsExactly(LightCallLogItem.Kind.CREATE_CALL_LINK)
    assertThat(state.emptyTitle).isNull()
  }

  @Test
  fun `filtering to missed calls and finding none says so instead of drawing a row`() {
    val state = map(listOf(CallLogRow.ClearFilterEmpty))

    assertThat(state.rows).isEmpty()
    assertThat(state.emptyTitle).isEqualTo("No missed calls")
  }

  /** Signal's two-line empty state, kept whole. */
  @Test
  fun `an empty log keeps both lines of Signal's empty state`() {
    val state = map(emptyList())

    assertThat(state.rows).isEmpty()
    assertThat(state.emptyTitle).isEqualTo("No calls.")
    assertThat(state.emptyBody).isNotNull()
  }

  /** "Create a Call Link" is an action row, like the chat list's archive row: no time, no marker. */
  @Test
  fun `the create call link row carries no time and no marker`() {
    val row = map(listOf(CallLogRow.CreateCallLink)).rows.single()

    assertThat(row.timestamp).isEmpty()
    assertThat(row.detail).isEmpty()
    assertThat(row.missed).isFalse()
    assertThat(row.hasActions).isFalse()
  }

  @Test
  fun `accessible timestamp description participates in row equality`() {
    val first = callItem(timestampDescription = "5 minutes ago")
    val second = callItem(timestampDescription = "6 minutes ago")

    assertThat(first).isNotEqualTo(second)
  }

  private fun callItem(timestampDescription: String) = LightCallLogItem(
    key = "call:1",
    sourceIndex = 0,
    kind = LightCallLogItem.Kind.CALL,
    name = "Ada",
    detail = "IN",
    timestamp = "09:05",
    timestampDescription = timestampDescription,
    missed = false,
    selected = false,
    row = null
  )

  private fun detail(type: CallTable.Type, direction: CallTable.Direction, missed: Boolean): String {
    return LightCallLogItem.detailFor(context, type, direction, missed)
  }

  private fun map(rows: List<CallLogRow?>): LightCallLogState {
    return LightCallLogItem.map(
      context = context,
      rows = rows,
      selection = CallLogSelectionState.empty(),
      localDeviceCallRecipientId = null
    )
  }
}
