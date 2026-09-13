/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.paged.light

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
 * Pins the contact picker's columns to the chat list's and the call log's, which are in turn pinned
 * to the Light reference client's in `LightConversationListGeometryTest`.
 *
 * This screen is reached with one tap off either of those lists and lands straight on top of them,
 * so a name that sits half a grid unit further in than the name you just tapped reads as the screen
 * jumping. The numbers, at LP3 geometry (360dp / 27 grid units, so 1 gu = 13.333dp):
 *
 * | edge | grid units |
 * |---|---|
 * | name, from the left | 1.75 (0.5 row padding + 1.0 marker slot + 0.25 gap) |
 * | detail, from the right | 2.5 (0.5 row padding + 2.0 scrollbar track) |
 *
 * The picker carries something neither of the others does: four different kinds of row in one list,
 * at three different type sizes. The interesting cases, and the ones most likely to regress, are
 * that a section label and an action row still start on the *name's* left edge despite having no
 * marker slot to fill, and that all of them are the same height -- [LightLazyScrollView] derives its
 * scrollbar from one uniform item height, so a shorter header row would quietly skew the thumb.
 */
@RunWith(RobolectricTestRunner::class)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp -- the same numbers the SDK's own previews use.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class LightContactGeometryTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `a contact's name and detail land on the chat list's edges`() {
    val gridUnit = render(rows())

    assertThat(leftInsetUnits(gridUnit, CONTACT_NAME)).isCloseTo(NAME_LEFT_UNITS, TOLERANCE_UNITS)
    assertThat(rightInsetUnits(gridUnit, CONTACT_DETAIL)).isCloseTo(DETAIL_RIGHT_UNITS, TOLERANCE_UNITS)
  }

  /**
   * The whole point of keeping the marker slot on every row kind. A section label has no checkbox
   * and an action row has none either, but both reserve the slot so that the list reads as one
   * column rather than as three lists stacked up with different margins.
   */
  @Test
  fun `labels and actions start on the name's left edge`() {
    val gridUnit = render(rows())

    assertThat(leftInsetUnits(gridUnit, HEADER_NAME)).isCloseTo(NAME_LEFT_UNITS, TOLERANCE_UNITS)
    assertThat(leftInsetUnits(gridUnit, ACTION_NAME)).isCloseTo(NAME_LEFT_UNITS, TOLERANCE_UNITS)
  }

  /**
   * `LightLazyScrollView` sizes its scrollbar from a single uniform item height, so every row kind
   * has to actually be that tall -- including the ones whose text is smaller than a contact's.
   */
  @Test
  fun `every row kind is the same height`() {
    val gridUnit = render(rows())

    // Every row centres its text vertically, so the centres of three consecutive rows are exactly
    // one row height apart -- whatever the type scale of the text in them. Measuring centres rather
    // than top edges is what makes that exact: the three kinds draw at three different sizes, so
    // their top edges are not comparable but their centres are.
    val centres = listOf(HEADER_NAME, CONTACT_NAME, ACTION_NAME).map { text -> centreUnits(gridUnit, text) }

    assertThat(centres[1] - centres[0]).isCloseTo(ROW_HEIGHT_UNITS, TOLERANCE_UNITS)
    assertThat(centres[2] - centres[1]).isCloseTo(ROW_HEIGHT_UNITS, TOLERANCE_UNITS)
  }

  /**
   * Characterises the gutter, exactly as the chat list's and the call log's tests do:
   * `LightLazyScrollView` reserves its two-unit scrollbar track unconditionally, so a picker short
   * enough to fit the screen keeps the same detail column as one long enough to scroll.
   */
  @Test
  fun `a list too short to scroll keeps the same gutter as one that does`() {
    val gridUnit = render(rows() + longList())

    assertThat(leftInsetUnits(gridUnit, CONTACT_NAME)).isCloseTo(NAME_LEFT_UNITS, TOLERANCE_UNITS)
    assertThat(rightInsetUnits(gridUnit, CONTACT_DETAIL)).isCloseTo(DETAIL_RIGHT_UNITS, TOLERANCE_UNITS)
  }

  private fun render(rows: List<LightContactItem>): Dp {
    var gridUnit = 0.dp

    composeTestRule.setContent {
      gridUnit = 1f.gridUnitsAsDp()
      MollyLightTheme {
        LightContactListScreen(state = LightContactState(rows = rows))
      }
    }

    return gridUnit
  }

  private fun leftInsetUnits(gridUnit: Dp, text: String): Float {
    val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    val node = composeTestRule.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()
    return (node.left - root.left).value / gridUnit.value
  }

  /** Vertical centre of a node, in grid units from the top of the screen. */
  private fun centreUnits(gridUnit: Dp, text: String): Float {
    val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    val node = composeTestRule.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()
    return ((node.top.value + node.bottom.value) / 2f - root.top.value) / gridUnit.value
  }

  private fun rightInsetUnits(gridUnit: Dp, text: String): Float {
    val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    val node = composeTestRule.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()
    return (root.right - node.right).value / gridUnit.value
  }

  /** One of each row kind, in the order the picker actually emits them. */
  private fun rows(): List<LightContactItem> = listOf(
    item(key = "header", kind = LightContactItem.Kind.HEADER, name = HEADER_NAME),
    item(key = "contact", kind = LightContactItem.Kind.CONTACT, name = CONTACT_NAME, detail = CONTACT_DETAIL),
    item(key = "action", kind = LightContactItem.Kind.ACTION, name = ACTION_NAME, action = LightContactItem.Action.NEW_GROUP)
  )

  /** Enough further rows to push the list past a 413dp screen and make the scrollbar appear. */
  private fun longList(): List<LightContactItem> = (0 until 12).map { index ->
    item(key = "filler:$index", kind = LightContactItem.Kind.CONTACT, name = "Filler $index")
  }

  private fun item(
    key: String,
    kind: LightContactItem.Kind,
    name: String,
    detail: String = "",
    action: LightContactItem.Action? = null
  ) = LightContactItem(
    key = key,
    sourceIndex = 0,
    kind = kind,
    name = name,
    detail = detail,
    selected = false,
    enabled = kind != LightContactItem.Kind.HEADER,
    action = action,
    data = null
  )

  companion object {
    private const val HEADER_NAME = "CONTACTS"
    private const val CONTACT_NAME = "Ada Lovelace"
    private const val CONTACT_DETAIL = "3 MEMBERS"
    private const val ACTION_NAME = "NEW GROUP"

    private const val NAME_LEFT_UNITS = 1.75f
    private const val DETAIL_RIGHT_UNITS = 2.5f
    private const val ROW_HEIGHT_UNITS = 4.5f

    /** A twentieth of a grid unit, i.e. two thirds of a dp on LP3. */
    private const val TOLERANCE_UNITS = 0.05f
  }
}
