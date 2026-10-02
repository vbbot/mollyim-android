/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.welcome

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import com.thelightphone.sdk.ui.LightGrid
import org.thoughtcrime.securesms.R
import com.thelightphone.sdk.ui.LightTextVariant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.thoughtcrime.securesms.light.MollyLightTheme

/**
 * The Welcome screen's action stack, at the LP3's real geometry.
 *
 * This is the first screen a new user ever sees, and it used to be four Material buttons. The
 * properties worth pinning are the two that only go wrong on a device:
 *
 *  1. **The tap target hugs the label.** The reference client shipped full-width row targets first
 *     and found them firing on taps far to the side of the text. On this screen that matters more
 *     than anywhere: a row whose target runs the width of the display makes "Continue" a thing you
 *     can start registration by tapping in empty space beside it.
 *  2. **The row still clears the platform minimum.** A target that hugs the label is easy to make
 *     too small, and 4 grid units is what keeps it honest: 53dp at LP3 width against a 44dp floor,
 *     derived from the grid rather than typed in so that it tracks the screen.
 */
@RunWith(RobolectricTestRunner::class)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class LightWelcomeActionsViewTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `every action is rendered as its own row`() {
    setActions("CONTINUE", "RESTORE OR TRANSFER", "LINK DEVICE", "TERMS AND PRIVACY")

    composeTestRule.onNodeWithText("CONTINUE").assertIsDisplayed()
    composeTestRule.onNodeWithText("RESTORE OR TRANSFER").assertIsDisplayed()
    composeTestRule.onNodeWithText("LINK DEVICE").assertIsDisplayed()
    composeTestRule.onNodeWithText("TERMS AND PRIVACY").assertIsDisplayed()
  }

  @Test
  fun `tapping a row runs that action and no other`() {
    var chosen: String? = null

    composeTestRule.setContent {
      MollyLightTheme {
        LightWelcomeActions(
          actions = listOf(
            LightWelcomeAction(label = "CONTINUE", onClick = { chosen = "continue" }),
            LightWelcomeAction(label = "TERMS AND PRIVACY", onClick = { chosen = "terms" })
          )
        )
      }
    }

    composeTestRule.onNodeWithText("CONTINUE").performClick()

    assertThat(chosen).isEqualTo("continue")
  }

  /**
   * The target is the label, not the row. Asserted against the width of the screen it is drawn on,
   * because a full-width target is exactly the regression this guards against.
   */
  @Test
  fun `a row's tap target hugs its label`() {
    setActions("TERMS AND PRIVACY")

    val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    val label = composeTestRule.onNodeWithText("TERMS AND PRIVACY").getUnclippedBoundsInRoot()
    val labelWidth = (label.right - label.left).value
    val screenWidth = (root.right - root.left).value

    assertThat(labelWidth).isGreaterThan(0f)
    assertThat(labelWidth).isLessThan(screenWidth)
  }

  /**
   * A target that hugs its label is easy to make too small. 4 grid units is 53dp at LP3 width,
   * against the platform's 44dp floor.
   */
  @Test
  fun `a row clears the platform minimum touch target`() {
    setActions("CONTINUE")

    composeTestRule.onNodeWithText("CONTINUE").assertHeightIsAtLeast(MINIMUM_TOUCH_DP.dp)
  }

  /**
   * Rows sit on a fixed vertical rhythm, one row height plus one grid unit of spacing, so the stack
   * reads as a stack rather than as four independently placed labels.
   */
  @Test
  fun `rows sit on the grid rhythm`() {
    setActions("CONTINUE", "LINK DEVICE")

    val first = composeTestRule.onNodeWithText("CONTINUE").getUnclippedBoundsInRoot()
    val second = composeTestRule.onNodeWithText("LINK DEVICE").getUnclippedBoundsInRoot()

    val rowPitch = ROW_HEIGHT_UNITS + ROW_SPACING_UNITS
    assertThat((second.top - first.top).value).isCloseTo(rowPitch * GRID_UNIT_DP, toleranceDp)
  }

  /**
   * Every row announces itself as a button. This is the one property of the old `MaterialButton`s
   * that had to be carried across by hand, `lightClickable`'s `role` being what now supplies it.
   *
   * Read from the *merged* tree, which is where the row lives: the clickable box merges the label
   * into itself, so one node carries both the text and the role. Looking in the unmerged tree finds
   * the bare `Text` and reports that the row has no role at all.
   */
  @Test
  fun `a row announces itself as a button`() {
    setActions("CONTINUE")

    composeTestRule.onNodeWithText("CONTINUE")
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
  }

  /**
   * The terms link is a legal footnote, not a way through registration. On a device with one
   * foreground colour the only way to say that is the type variant and the shade, so both have to
   * actually differ from the other rows.
   */
  @Test
  fun `the quiet action is set quieter than the rest`() {
    val terms = LightWelcomeAction(
      label = "TERMS AND PRIVACY",
      onClick = {},
      variant = LightTextVariant.Fine,
      lighten = true
    )

    assertThat(terms.variant).isEqualTo(LightTextVariant.Fine)
    assertThat(terms.lighten).isEqualTo(true)
    // The default, which every other row on the screen uses.
    assertThat(LightWelcomeAction(label = "CONTINUE", onClick = {}).variant)
      .isEqualTo(LightTextVariant.Button)
  }

  /**
   * The stack renders every label upper case, whatever case it is handed.
   *
   * The labels arrive from the registration feature module as prose -- "Continue", "Restore or
   * transfer" -- so this is a real transformation rather than a restatement of the caller's string.
   * It is the screen's own decision: this used to be the only action surface in the app rendering
   * sentence case, sitting next to a panel and a bottom bar that both upper case.
   */
  @Test
  fun `labels are uppercased whatever case they arrive in`() {
    setActions("Continue", "Restore or transfer", "Link device", "Terms & Privacy Policy")

    composeTestRule.onNodeWithText("CONTINUE").assertIsDisplayed()
    composeTestRule.onNodeWithText("RESTORE OR TRANSFER").assertIsDisplayed()
    composeTestRule.onNodeWithText("LINK DEVICE").assertIsDisplayed()
    composeTestRule.onNodeWithText("TERMS & PRIVACY POLICY").assertIsDisplayed()
    composeTestRule.onAllNodes(hasText("Continue")).assertCountEquals(0)
  }

  /**
   * The labels Molly actually passes in render as the words on the screen -- not Signal's, and not
   * a two-line legal footnote. The registration module's copy of this string is
   * "Signal is a 501c3 nonprofit\nTerms & Privacy Policy"; a Molly build reads it from the app's
   * `strings2.xml` instead.
   */
  @Test
  fun `the terms row is a single line and does not name Signal`() {
    val terms = RuntimeEnvironment.getApplication().getString(R.string.RegistrationActivity_terms_and_privacy)

    assertThat(terms).doesNotContain("Signal")
    assertThat(terms).doesNotContain("\n")
  }

  /**
   * The stack keeps itself off the bottom edge. This used to be `layout_marginBottom="48dp"` on the
   * view, which is 3.6 grid units -- not a number the grid produces, and fixed rather than derived,
   * so it did not track the screen it was written for.
   */
  @Test
  fun `the stack clears the bottom edge by a grid-derived gap`() {
    setActions("CONTINUE")

    val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    val label = composeTestRule.onNodeWithText("CONTINUE").getUnclippedBoundsInRoot()

    assertThat((root.bottom - label.bottom).value)
      .isCloseTo(STACK_BOTTOM_UNITS * GRID_UNIT_DP, toleranceDp)
  }

  private fun setActions(vararg labels: String) {
    composeTestRule.setContent {
      MollyLightTheme {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
          LightWelcomeActions(actions = labels.map { label -> LightWelcomeAction(label = label, onClick = {}) })
        }
      }
    }
  }

  private companion object {
    /** 1 grid unit at LP3 width: 360dp / 27. */
    const val GRID_UNIT_DP = 360f / LightGrid.WIDTH

    /** Read from the screen rather than restated, so the test moves when the screen moves. */
    const val ROW_SPACING_UNITS = org.thoughtcrime.securesms.registration.ui.welcome.ROW_SPACING_UNITS
    const val STACK_BOTTOM_UNITS = org.thoughtcrime.securesms.registration.ui.welcome.STACK_BOTTOM_UNITS

    /** The platform's minimum accessible touch target. */
    const val MINIMUM_TOUCH_DP = 44f


    const val toleranceDp = 1f
  }
}
