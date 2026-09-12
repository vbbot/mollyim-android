/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.light

import android.app.Application
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.thoughtcrime.securesms.light.MollyLightTheme

/**
 * The thread's top bar is three things in fixed places -- a back chevron, a centred name and the
 * overflow ellipses -- painted on top of a Material `Toolbar` that is deliberately invisible. If any
 * of the three drifts, nothing fails at compile time and the only symptom on the device is a missing
 * or unreachable control, so they are pinned here.
 *
 * At LP3 geometry (360dp wide, 27 grid units) one grid unit is 13.333dp, so the bar is 3 gu = 40dp
 * tall with 1 gu of horizontal padding.
 */
@RunWith(RobolectricTestRunner::class)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp -- the same numbers the SDK's own previews use.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class LightConversationTopBarTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `renders a back chevron, the name and the overflow`() {
    setTopBar()

    composeTestRule.onNodeWithContentDescription(BACK_DESCRIPTION).assertIsDisplayed()
    composeTestRule.onNodeWithText(NAME).assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription(OVERFLOW_DESCRIPTION).assertIsDisplayed()
  }

  @Test
  fun `the bar is three grid units tall`() {
    setTopBar()

    val bounds = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    val barHeight = (bounds.bottom - bounds.top).value

    assertThat(barHeight).isCloseTo(GRID_UNIT * 3, TOLERANCE)
  }

  /**
   * The name is centred against the *bar*, not against the space left over between the two buttons,
   * which is what a naive `Row` would give and what would leave the name visibly off-centre.
   */
  @Test
  fun `the name is centred on the bar, not between the buttons`() {
    setTopBar()

    assertThat(nameCentreX()).isCloseTo(rootCentreX(), TOLERANCE)
  }

  /**
   * The chevron and the ellipses sit inside the bar's own 1 grid unit of horizontal padding. The
   * ellipses in particular has to stay hard against the right edge: the Material overflow button
   * whose popup it opens is laid out at the toolbar's end, directly behind it.
   */
  @Test
  fun `the buttons sit inside one grid unit of horizontal padding`() {
    setTopBar()

    val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    val back = composeTestRule.onNodeWithContentDescription(BACK_DESCRIPTION).getUnclippedBoundsInRoot()
    val overflow = composeTestRule.onNodeWithContentDescription(OVERFLOW_DESCRIPTION).getUnclippedBoundsInRoot()

    assertThat((back.left - root.left).value).isCloseTo(GRID_UNIT, TOLERANCE)
    assertThat((root.right - overflow.right).value).isCloseTo(GRID_UNIT, TOLERANCE)
  }

  @Test
  fun `each slot routes to its own callback`() {
    var back = false
    var title = false
    var overflow = false

    setTopBar(
      onLeftClick = { back = true },
      onTitleClick = { title = true },
      onOverflowClick = { overflow = true }
    )

    composeTestRule.onNodeWithContentDescription(BACK_DESCRIPTION).performClick()
    composeTestRule.onNodeWithText(NAME).performClick()
    composeTestRule.onNodeWithContentDescription(OVERFLOW_DESCRIPTION).performClick()

    assertThat(back).isTrue()
    assertThat(title).isTrue()
    assertThat(overflow).isTrue()
  }

  /** The popup screen type builds no menu, so an ellipses there would be a dead control. */
  @Test
  fun `without a menu there is no ellipses`() {
    setTopBar(hasOverflow = false)

    assertThat(nodeCount(OVERFLOW_DESCRIPTION)).isEqualTo(0)
  }

  /**
   * A split pane shows the list beside the thread, so there is nothing to go back to. The name has
   * to stay centred anyway: `LightTopBar` reserves the slot whether or not a button is in it.
   */
  @Test
  fun `the left slot can be empty without moving the name`() {
    setTopBar(leftAction = LightConversationTopBarLeftAction.NONE)

    assertThat(nodeCount(BACK_DESCRIPTION)).isEqualTo(0)
    assertThat(nameCentreX()).isCloseTo(rootCentreX(), TOLERANCE)
  }

  /** A bubble's only way out is into the app proper, so the slot holds that action instead. */
  @Test
  fun `a bubble gets the launch action in the left slot`() {
    setTopBar(leftAction = LightConversationTopBarLeftAction.LAUNCH_MAIN_APP)

    composeTestRule.onNodeWithContentDescription(LAUNCH_DESCRIPTION).assertIsDisplayed()
    assertThat(nodeCount(BACK_DESCRIPTION)).isEqualTo(0)
  }

  private fun rootCentreX(): Float {
    val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    return (root.left + root.right).value / 2f
  }

  private fun nameCentreX(): Float {
    val name = composeTestRule.onNodeWithText(NAME).getUnclippedBoundsInRoot()
    return (name.left + name.right).value / 2f
  }

  private fun nodeCount(contentDescription: String): Int =
    composeTestRule.onAllNodesWithContentDescription(contentDescription).fetchSemanticsNodes().size

  private fun setTopBar(
    title: String? = NAME,
    leftAction: LightConversationTopBarLeftAction = LightConversationTopBarLeftAction.BACK,
    hasOverflow: Boolean = true,
    onLeftClick: () -> Unit = {},
    onTitleClick: (() -> Unit)? = {},
    onOverflowClick: () -> Unit = {}
  ) {
    composeTestRule.setContent {
      MollyLightTheme {
        LightConversationTopBar(
          title = title,
          leftAction = leftAction,
          hasOverflow = hasOverflow,
          onLeftClick = onLeftClick,
          onTitleClick = onTitleClick,
          onOverflowClick = onOverflowClick,
          modifier = Modifier.fillMaxWidth()
        )
      }
    }
  }

  companion object {
    private const val NAME = "J. Jonah Jameson"

    /** 360dp across 27 grid units. */
    private val GRID_UNIT = (360.dp / 27).value
    private const val TOLERANCE = 0.5f

    /** `R.string.ConversationFragment__content_description_back_button`. */
    private const val BACK_DESCRIPTION = "Navigate back."

    /** `R.string.ConversationFragment__content_description_launch_signal_button`. */
    private const val LAUNCH_DESCRIPTION = "Open Molly"

    /** `R.string.MainToolbar__more_options_content_description`. */
    private const val OVERFLOW_DESCRIPTION = "More options"
  }
}
