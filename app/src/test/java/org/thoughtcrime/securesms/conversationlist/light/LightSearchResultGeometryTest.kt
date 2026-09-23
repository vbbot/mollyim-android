/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversationlist.light

import android.app.Application
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.designVerticalPxToSp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.thoughtcrime.securesms.contacts.paged.light.LightContactItem
import org.thoughtcrime.securesms.contacts.paged.light.LightContactListScreen
import org.thoughtcrime.securesms.contacts.paged.light.LightContactState
import org.thoughtcrime.securesms.contacts.paged.light.ROW_HEIGHT_UNITS
import org.thoughtcrime.securesms.contacts.paged.light.ROW_VERTICAL_PADDING
import org.thoughtcrime.securesms.contacts.paged.light.ROW_HEIGHT_WITH_SNIPPET_UNITS
import org.thoughtcrime.securesms.light.MollyLightTheme

/**
 * The search results' one new shape: the two-line message hit.
 *
 * Everything else in this list is the contact picker's row, already pinned by
 * `LightContactGeometryTest`, so what is worth pinning here is only what search added -- that the
 * snippet is a *second line* rather than a second column, that it starts on the same left edge as
 * the name above it, and that the date still lands in the same right-hand column a one-line row's
 * detail does. A snippet that crept into the right-hand column, or a name that shifted sideways on
 * the rows that have one, would both read as the list coming apart between sections.
 *
 * These are Compose-only measurements on purpose. The row this replaced was a `ConversationListItem`
 * containing an `EmojiTextView`, which Robolectric cannot inflate at all -- it blocks forever in
 * `EmojiSource.getLatest` -- so the Material search row was untestable here in a way the Light one
 * is not.
 */
@RunWith(RobolectricTestRunner::class)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class LightSearchResultGeometryTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  /**
   * The snippet is a second line, not a second column: it sits *below* the conversation name and
   * flush with it, which is what makes the pair read as one row about one message.
   */
  @Test
  fun `the snippet sits under the name, on the name's left edge`() {
    val gridUnit = render(listOf(messageHit()))

    assertThat(leftInsetUnits(gridUnit, NAME)).isCloseTo(NAME_LEFT_UNITS, TOLERANCE_UNITS)
    assertThat(leftInsetUnits(gridUnit, SNIPPET)).isCloseTo(NAME_LEFT_UNITS, TOLERANCE_UNITS)
    assertThat(topUnits(gridUnit, SNIPPET)).isGreaterThan(topUnits(gridUnit, NAME))
  }

  /** The date stays in the column every other row's right-hand token uses. */
  @Test
  fun `the date lands in the list's right-hand column`() {
    val gridUnit = render(listOf(messageHit()))

    assertThat(rightInsetUnits(gridUnit, DATE)).isCloseTo(DETAIL_RIGHT_UNITS, TOLERANCE_UNITS)
  }

  /**
   * The two-line row's height, pinned against the SDK's own type tokens rather than against
   * rendered text.
   *
   * This is the number [com.thelightphone.sdk.ui.LightLazyScrollView] sizes its scrollbar from, and
   * it cannot be measured here: Robolectric stubs font metrics and reports a `Heading` line box and
   * a `Superfine` one at exactly the same height, which makes a two-line row measure 4.5 units in
   * this environment and 5.4 on the device. So the arithmetic is done from the tokens -- which are
   * real -- and only the rendering is left to the device.
   *
   * If this fails, the type scale or the row padding moved and `ROW_HEIGHT_WITH_SNIPPET_UNITS` has
   * silently stopped describing the row the scrollbar is scrolling.
   */
  @Test
  fun `the scrollbar is told the height a two-line row actually has`() {
    var expected = 0f

    composeTestRule.setContent {
      MollyLightTheme {
        val typography = LightThemeTokens.typography
        val gridUnit = 1f.gridUnitsAsDp()
        val density = LocalDensity.current

        with(density) {
          val nameLine = typography.heading.lineHeight.value.designVerticalPxToSp().toDp()
          val snippetLine = typography.superfine.lineHeight.value.designVerticalPxToSp().toDp()
          expected = (nameLine + snippetLine + ROW_VERTICAL_PADDING * 2).value / gridUnit.value
        }
      }
    }

    assertThat(expected).isCloseTo(ROW_HEIGHT_WITH_SNIPPET_UNITS, HEIGHT_TOLERANCE_UNITS)
  }

  /**
   * And the one-line estimate still describes a one-line row, which is what makes the *other*
   * branch of that choice correct. A `Heading` plus the row's padding comes in just under the
   * explicit 4.5-unit minimum, which is where that number came from in the first place.
   */
  @Test
  fun `a one-line row is still the height the picker's rows are`() {
    var oneLine = 0f

    composeTestRule.setContent {
      MollyLightTheme {
        val typography = LightThemeTokens.typography
        val gridUnit = 1f.gridUnitsAsDp()
        val density = LocalDensity.current

        with(density) {
          val nameLine = typography.heading.lineHeight.value.designVerticalPxToSp().toDp()
          oneLine = (nameLine + ROW_VERTICAL_PADDING * 2).value / gridUnit.value
        }
      }
    }

    // Under the minimum, so the minimum is what the row actually measures.
    assertThat(oneLine).isLessThan(ROW_HEIGHT_UNITS)
    assertThat(ROW_HEIGHT_UNITS).isLessThan(ROW_HEIGHT_WITH_SNIPPET_UNITS)
  }

  private fun render(rows: List<LightContactItem>): Dp {
    var gridUnit = 0.dp

    composeTestRule.setContent {
      gridUnit = 1f.gridUnitsAsDp()
      MollyLightTheme {
        LightContactListScreen(state = LightContactState(rows = rows, hasSnippets = rows.any { it.snippet.isNotEmpty() }))
      }
    }

    return gridUnit
  }

  private fun leftInsetUnits(gridUnit: Dp, text: String): Float {
    val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    val node = composeTestRule.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()
    return (node.left - root.left).value / gridUnit.value
  }

  private fun rightInsetUnits(gridUnit: Dp, text: String): Float {
    val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    val node = composeTestRule.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()
    return (root.right - node.right).value / gridUnit.value
  }

  private fun topUnits(gridUnit: Dp, text: String): Float {
    val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    val node = composeTestRule.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()
    return (node.top.value - root.top.value) / gridUnit.value
  }

  private fun name(index: Int) = if (index == 0) NAME else "$NAME $index"

  private fun messageHit(index: Int = 0) = LightContactItem(
    key = "message:$index",
    sourceIndex = index,
    kind = LightContactItem.Kind.MESSAGE_HIT,
    name = name(index),
    detail = DATE,
    snippet = if (index == 0) SNIPPET else "$SNIPPET $index",
    selected = false,
    enabled = true,
    action = null,
    data = null
  )

  companion object {
    private const val NAME = "Ada Lovelace"
    private const val SNIPPET = "about the analytical engine"
    private const val DATE = "Aug 12"

    private const val NAME_LEFT_UNITS = 1.75f
    private const val DETAIL_RIGHT_UNITS = 2.5f
    private const val TOLERANCE_UNITS = 0.05f

    /** Looser than the column tolerance: row height depends on the type scale, not on fixed insets. */
    private const val HEIGHT_TOLERANCE_UNITS = 0.15f
  }
}
