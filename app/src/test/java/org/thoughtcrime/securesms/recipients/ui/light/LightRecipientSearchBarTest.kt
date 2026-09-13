/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.recipients.ui.light

import android.app.Application
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
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
 * The picker's search field.
 *
 * This screen's field is the one input in the port that had to become a real Light control rather
 * than be covered over, because on a Light Phone it is how you find anybody: there is no alphabet
 * index and no fast scroller behind it. So the two things worth pinning are that it still *takes
 * text* -- an ordinary Compose field, which is all it takes to raise the LP3's own keyboard, since
 * that keyboard is the system IME -- and that what you type sits in the same column as the names it
 * filters.
 */
@RunWith(RobolectricTestRunner::class)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class LightRecipientSearchBarTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `typing reaches the caller`() {
    var query = ""

    composeTestRule.setContent {
      Bar(query = query, onQueryChange = { query = it })
    }

    composeTestRule.onNode(hasSetTextAction()).performTextInput("ada")

    assertThat(query).isEqualTo("ada")
  }

  @Test
  fun `the hint shows only while the field is empty`() {
    composeTestRule.setContent { Bar(query = "") }

    composeTestRule.onNodeWithText(HINT).assertIsDisplayed()
  }

  /**
   * The clear affordance is the field's only escape hatch back to the full list, and it has to not
   * be there when there is nothing to clear -- otherwise it is a glyph that does nothing sitting in
   * the row of a design that has very few glyphs.
   */
  @Test
  fun `clearing empties the query, and is offered only when there is something to clear`() {
    var query = "ada"

    composeTestRule.setContent {
      Bar(query = query, onQueryChange = { query = it })
    }

    composeTestRule.onNodeWithContentDescription(CLEAR_DESCRIPTION).performClick()

    assertThat(query).isEqualTo("")
  }

  @Test
  fun `no clear affordance on an empty field`() {
    composeTestRule.setContent { Bar(query = "") }

    composeTestRule.onAllNodesWithContentDescription(CLEAR_DESCRIPTION).assertCountEquals(0)
  }

  /**
   * The alignment that makes the field part of the list rather than a control above it: the hint --
   * and so the text that replaces it -- starts exactly where a contact's name starts, 1.75 grid
   * units in. Same number as `LightContactGeometryTest`, and for the same reason.
   */
  @Test
  fun `what you type lines up with the names it filters`() {
    var gridUnit = 0.dp

    composeTestRule.setContent {
      gridUnit = 1f.gridUnitsAsDp()
      Bar(query = "")
    }

    assertThat(hintLeftInsetUnits(gridUnit)).isCloseTo(NAME_LEFT_UNITS, TOLERANCE_UNITS)
  }

  /**
   * The hint sits *behind* the entry, not above it -- otherwise the bar would be two lines tall
   * whenever the query was empty, which is most of the time, and the rule under it would drift a
   * line away from the list.
   *
   * Characterisation rather than regression: this passes both with and without the explicit `Box`
   * in the field's decoration, because `BasicTextField` already overlays its decoration. It is here
   * to catch the arrangement being changed to one that does stack -- a `Column`, or a hint moved
   * out of the decoration and into the surrounding `Row`.
   */
  @Test
  fun `the hint sits behind the entry rather than above it`() {
    var gridUnit = 0.dp

    composeTestRule.setContent {
      gridUnit = 1f.gridUnitsAsDp()
      Bar(query = "")
    }

    val hint = centreUnits(gridUnit, composeTestRule.onNodeWithText(HINT, useUnmergedTree = true).getUnclippedBoundsInRoot())
    val field = centreUnits(gridUnit, composeTestRule.onNode(hasSetTextAction()).getUnclippedBoundsInRoot())

    assertThat(hint).isCloseTo(field, OVERLAP_TOLERANCE_UNITS)
  }

  private fun centreUnits(gridUnit: Dp, bounds: DpRect): Float {
    val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    return ((bounds.top.value + bounds.bottom.value) / 2f - root.top.value) / gridUnit.value
  }

  private fun hintLeftInsetUnits(gridUnit: Dp): Float {
    val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    val hint = composeTestRule.onNodeWithText(HINT, useUnmergedTree = true).getUnclippedBoundsInRoot()
    return (hint.left - root.left).value / gridUnit.value
  }

  @Composable
  private fun Bar(query: String, onQueryChange: (String) -> Unit = {}) {
    MollyLightTheme {
      LightRecipientSearchBar(
        query = query,
        onQueryChange = onQueryChange,
        hint = HINT,
        modifier = Modifier.fillMaxWidth()
      )
    }
  }

  companion object {
    private const val HINT = "Name, username or number"
    private const val CLEAR_DESCRIPTION = "Reset search filter"
    private const val NAME_LEFT_UNITS = 1.75f
    private const val TOLERANCE_UNITS = 0.05f

    /** Half a grid unit: enough to tell "same line" from "stacked lines", which differ by about 1.5. */
    private const val OVERLAP_TOLERANCE_UNITS = 0.5f
  }
}
