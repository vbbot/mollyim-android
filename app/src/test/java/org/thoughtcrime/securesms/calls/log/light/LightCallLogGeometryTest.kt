/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.log.light

import android.app.Application
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
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
 * Pins the call log's columns to the chat list's, which are in turn pinned to the Light reference
 * client's in `LightConversationListGeometryTest`.
 *
 * The two tabs are the same list drawn from different data, and the user flips between them with one
 * tap on the bottom bar, so a name or a time that lands half a grid unit apart between them reads as
 * the screen twitching. The numbers, at LP3 geometry (360dp / 27 grid units, so 1 gu = 13.333dp):
 *
 * | edge | grid units |
 * |---|---|
 * | name, from the left | 1.75 (0.5 row padding + 1.0 marker slot + 0.25 gap) |
 * | timestamp, from the right | 2.5 (0.5 row padding + 2.0 scrollbar track) |
 *
 * The call row carries one thing the chat row does not -- the `Fine` direction/medium token between
 * the name and the time -- so the interesting case, and the one most likely to regress, is that the
 * token does *not* push the time column around: it is inserted before the timestamp inside a
 * `fillMaxWidth` row, and the name's `weight(1f)` is what has to absorb it.
 */
@RunWith(RobolectricTestRunner::class)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp -- the same numbers the SDK's own previews use.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class LightCallLogGeometryTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `the name and the timestamp land on the chat list's edges`() {
    val gridUnit = render(rows())

    assertThat(nameLeftInsetUnits(gridUnit, 0)).isCloseTo(NAME_LEFT_UNITS, TOLERANCE_UNITS)
    assertThat(timestampRightInsetUnits(gridUnit, 0)).isCloseTo(TIMESTAMP_RIGHT_UNITS, TOLERANCE_UNITS)
  }

  /**
   * The marker slot is a fixed width whether or not it has a glyph in it, which is what stops the
   * names sliding sideways as a screenful of answered calls picks up a missed one.
   */
  @Test
  fun `a missed row's name starts exactly where an answered row's does`() {
    val gridUnit = render(rows())

    (0 until VISIBLE_ROW_COUNT).forEach { index ->
      assertThat(nameLeftInsetUnits(gridUnit, index)).isCloseTo(NAME_LEFT_UNITS, TOLERANCE_UNITS)
    }
  }

  /** The time column is pinned to the right edge, not to the end of whatever precedes it. */
  @Test
  fun `the detail token does not move the time column`() {
    val gridUnit = render(rows())

    (0 until VISIBLE_ROW_COUNT).forEach { index ->
      assertThat(timestampRightInsetUnits(gridUnit, index)).isCloseTo(TIMESTAMP_RIGHT_UNITS, TOLERANCE_UNITS)
    }
  }

  /**
   * Characterises the gutter, exactly as the chat list's test does: `LightLazyScrollView` reserves
   * its two-unit scrollbar track unconditionally, so a log short enough to fit the screen keeps the
   * same time column as one long enough to scroll.
   */
  @Test
  fun `a log too short to scroll keeps the same gutter as one that does`() {
    val gridUnit = render(rows(STATIC_ROW_COUNT))

    assertThat(nameLeftInsetUnits(gridUnit, 0)).isCloseTo(NAME_LEFT_UNITS, TOLERANCE_UNITS)
    assertThat(timestampRightInsetUnits(gridUnit, 0)).isCloseTo(TIMESTAMP_RIGHT_UNITS, TOLERANCE_UNITS)
  }

  private fun render(rows: List<LightCallLogItem>): Dp {
    var gridUnit = 0.dp

    composeTestRule.setContent {
      gridUnit = 1f.gridUnitsAsDp()
      MollyLightTheme {
        LightCallLogScreen(state = LightCallLogState(rows = rows))
      }
    }

    return gridUnit
  }

  /** Left edge of a row's name, in grid units from the screen's left edge. */
  private fun nameLeftInsetUnits(gridUnit: Dp, index: Int): Float {
    val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    val name = composeTestRule.onNodeWithText(nameFor(index), useUnmergedTree = true).getUnclippedBoundsInRoot()
    return (name.left - root.left).value / gridUnit.value
  }

  /** Right edge of a row's timestamp, in grid units from the screen's right edge. */
  private fun timestampRightInsetUnits(gridUnit: Dp, index: Int): Float {
    val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    val time = composeTestRule.onNodeWithText(timestampFor(index), useUnmergedTree = true).getUnclippedBoundsInRoot()
    return (root.right - time.right).value / gridUnit.value
  }

  /**
   * One row per shape the log can take: every token in the vocabulary, plus a row with no token at
   * all (a missed voice call), plus a missed row so the marker slot is exercised.
   */
  private fun rows(count: Int = SCROLLING_ROW_COUNT): List<LightCallLogItem> = (0 until count).map { index ->
    LightCallLogItem(
      key = "row:$index",
      sourceIndex = index,
      kind = LightCallLogItem.Kind.CALL,
      name = nameFor(index),
      detail = SAMPLE_DETAILS[index % SAMPLE_DETAILS.size],
      timestamp = timestampFor(index),
      timestampDescription = "",
      missed = index % 3 == 0,
      selected = false,
      row = null
    )
  }

  private fun nameFor(index: Int) = "Caller $index"

  private fun timestampFor(index: Int) = "%02d:%02d".format(index % 24, index)

  companion object {
    /** Comfortably more than the ~6 rows an LP3 screen holds, so the list scrolls. */
    private const val SCROLLING_ROW_COUNT = 12

    /**
     * Rows a 413dp-tall LP3 screen definitely has composed at 4.5 grid units (60dp) each. Anything
     * past the viewport is not in the tree to be measured, so assertions stop here.
     */
    private const val VISIBLE_ROW_COUNT = 5

    /** Fewer rows than an LP3 screen holds, so the list does not scroll. */
    private const val STATIC_ROW_COUNT = 3
    private const val NAME_LEFT_UNITS = 1.75f
    private const val TIMESTAMP_RIGHT_UNITS = 2.5f

    /** A twentieth of a grid unit, i.e. two thirds of a dp on LP3. */
    private const val TOLERANCE_UNITS = 0.05f

    /** Every token [LightCallLogItem.detailFor] can emit, plus the empty one. */
    private val SAMPLE_DETAILS = listOf("", "IN", "OUT", "VIDEO", "VIDEO IN", "VIDEO OUT", "GROUP", "LINK", "JOIN")
  }
}
