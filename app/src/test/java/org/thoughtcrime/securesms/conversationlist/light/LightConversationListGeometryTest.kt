/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversationlist.light

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import com.thelightphone.sdk.ui.LightLazyScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.thoughtcrime.securesms.light.MollyLightTheme

/**
 * Pins the horizontal geometry of [LightConversationListScreen] to the Light reference chat client's
 * (`chats/app/src/main/kotlin/com/lightphone/chats/screens/ChatListScreen.kt`), which is the design
 * authority for this screen.
 *
 * The reference app cannot be compiled into Molly, so its row and container chain are reproduced
 * verbatim below as [ReferenceChatList] / [ReferenceRoomRow] and measured under identical
 * constraints. Anything that moves our columns without moving the reference's fails here.
 *
 * The numbers, at LP3 geometry (360dp / 27 grid units, so 1 gu = 13.333dp):
 *
 * | edge | grid units |
 * |---|---|
 * | name, from the left | 1.75 (0.5 row padding + 1.0 marker slot + 0.25 gap) |
 * | timestamp, from the right | 2.5 (0.5 row padding + 2.0 scrollbar track) |
 *
 * Upstream `LightLazyScrollView` laid the scrollbar out as a 2-unit `Row` sibling *and* padded the
 * `LazyColumn` by another 2 units, reserving the gutter twice and only while the list was long
 * enough to scroll -- 4.5 units when scrollable, 0.5 when not. See the `MOLLY-VENDOR:` note in
 * LightScrollView.kt. The slot is now reserved once and unconditionally, so these numbers hold
 * whether or not the scrollbar is showing.
 */
@RunWith(RobolectricTestRunner::class)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp -- the same numbers the SDK's own previews use.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class LightConversationListGeometryTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `our row puts the name and the timestamp exactly where the reference row does`() {
    var gridUnit = 0.dp

    composeTestRule.setContent {
      gridUnit = 1f.gridUnitsAsDp()
      MollyLightTheme {
        LightConversationListScreen(
          state = LightConversationListState(rows = rows()),
          selectionMode = false
        )
      }
    }

    assertThat(nameLeftInsetUnits(gridUnit)).isCloseTo(NAME_LEFT_UNITS, TOLERANCE_UNITS)
    assertThat(timestampRightInsetUnits(gridUnit)).isCloseTo(TIMESTAMP_RIGHT_UNITS, TOLERANCE_UNITS)
  }

  @Test
  fun `the reference row lands on the same edges, whatever the timestamp says`() {
    var gridUnit = 0.dp

    composeTestRule.setContent {
      gridUnit = 1f.gridUnitsAsDp()
      MollyLightTheme {
        ReferenceChatList(rows())
      }
    }

    assertThat(nameLeftInsetUnits(gridUnit)).isCloseTo(NAME_LEFT_UNITS, TOLERANCE_UNITS)
    assertThat(timestampRightInsetUnits(gridUnit)).isCloseTo(TIMESTAMP_RIGHT_UNITS, TOLERANCE_UNITS)
  }

  /**
   * Characterises the one case where the two columns *do* move: `LightLazyScrollView` only reserves
   * its gutter while the scrollbar is out, so a list short enough to fit the screen slides its
   * timestamps four full grid units to the right, out to the row's own 0.5-unit margin. Both clients
   * inherit this, and it is the most likely reason two Light lists side by side disagree about where
   * the time column belongs -- the one with fewer rows wins.
   */
  @Test
  fun `a list too short to scroll keeps the same gutter as one that does`() {
    var gridUnit = 0.dp

    composeTestRule.setContent {
      gridUnit = 1f.gridUnitsAsDp()
      MollyLightTheme {
        LightConversationListScreen(
          state = LightConversationListState(rows = rows(STATIC_ROW_COUNT)),
          selectionMode = false
        )
      }
    }

    assertThat(nameLeftInsetUnits(gridUnit)).isCloseTo(NAME_LEFT_UNITS, TOLERANCE_UNITS)
    assertThat(timestampRightInsetUnits(gridUnit, STATIC_ROW_COUNT)).isCloseTo(TIMESTAMP_RIGHT_UNITS, TOLERANCE_UNITS)
  }

  /** Left edge of the first row's name, in grid units from the screen's left edge. */
  private fun nameLeftInsetUnits(gridUnit: Dp): Float {
    val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    val name = composeTestRule.onNodeWithText(nameFor(0), useUnmergedTree = true).getUnclippedBoundsInRoot()
    return (name.left - root.left).value / gridUnit.value
  }

  /**
   * Right edge of the timestamp, in grid units from the screen's right edge, checked against every
   * shape [LightRelativeTimestamp] can emit: the timestamp is the last child of a `fillMaxWidth`
   * row, so its right edge must not move with its content width.
   */
  private fun timestampRightInsetUnits(gridUnit: Dp, sampleCount: Int = SAMPLE_TIMESTAMPS.size): Float {
    val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    return (0 until sampleCount).map { index ->
      val time = composeTestRule.onNodeWithText(timestampFor(index), useUnmergedTree = true).getUnclippedBoundsInRoot()
      (root.right - time.right).value / gridUnit.value
    }.distinct().single()
  }

  private fun rows(count: Int = SCROLLING_ROW_COUNT): List<LightConversationListItem> = (0 until count).map { index ->
    LightConversationListItem(
      key = index.toLong(),
      sourceIndex = index,
      kind = LightConversationListItem.Kind.THREAD,
      name = nameFor(index),
      timestamp = timestampFor(index),
      timestampDescription = "",
      unread = false,
      selected = false,
      conversation = null
    )
  }

  private fun nameFor(index: Int) = "Chat $index"

  private fun timestampFor(index: Int) = SAMPLE_TIMESTAMPS.getOrElse(index) { "%02d:%02d".format(index, index) }

  /**
   * Layout-faithful reproduction of the reference client's chat list container chain. Only the
   * theming, the offline banner and the bottom bar are dropped -- none of them touches the list's
   * horizontal geometry. Every sizing modifier is copied verbatim from `ChatListScreen.Content`.
   */
  @Composable
  private fun ReferenceChatList(rows: List<LightConversationListItem>) {
    Box(modifier = Modifier.fillMaxSize()) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(LightThemeTokens.colors.background)
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(2f.gridUnitsAsDp())
        )
        Box(modifier = Modifier.weight(1f)) {
          Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
              LightLazyScrollView(uniformItemHeightGridUnits = 4.5f) {
                items(rows, key = { it.key }) { room -> ReferenceRoomRow(room) }
              }
            }
          }
        }
      }
    }
  }

  /**
   * Layout-faithful reproduction of the reference client's `RoomRow`. The tap/long-press
   * `pointerInput` is dropped because it contributes no size.
   */
  @Composable
  private fun ReferenceRoomRow(room: LightConversationListItem) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 0.5f.gridUnitsAsDp(), end = 0.5f.gridUnitsAsDp(), top = 12.dp, bottom = 12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(modifier = Modifier.width(1f.gridUnitsAsDp())) {
          if (room.unread) {
            LightText(text = "*", variant = LightTextVariant.Heading)
          }
        }
        Box(modifier = Modifier.width(0.25f.gridUnitsAsDp()))
        Box(modifier = Modifier.weight(1f)) {
          LightText(
            text = room.name,
            variant = LightTextVariant.Heading,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        LightText(text = room.timestamp, variant = LightTextVariant.Fine)
      }
    }
  }

  companion object {
    /** Comfortably more than the ~6 rows an LP3 screen holds, so the list scrolls. */
    private const val SCROLLING_ROW_COUNT = 24

    /** Fewer rows than an LP3 screen holds, so the list does not scroll. */
    private const val STATIC_ROW_COUNT = 3
    private const val NAME_LEFT_UNITS = 1.75f
    private const val TIMESTAMP_RIGHT_UNITS = 2.5f

    /** A twentieth of a grid unit, i.e. two thirds of a dp on LP3. */
    private const val TOLERANCE_UNITS = 0.05f

    /** Every shape [LightRelativeTimestamp] can emit, narrowest to widest. */
    private val SAMPLE_TIMESTAMPS = listOf("Wed", "Yest", "09:20", "Jun 05")
  }
}
