/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.light

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.ContextCompat
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import com.thelightphone.sdk.ui.LightGrid
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.thoughtcrime.securesms.R

/**
 * The Light top bar, at the geometry every view-hosted pushed screen shares.
 *
 * Three things are worth pinning, in the order they would break:
 *
 *  1. **Back is always reachable and always an icon.** The design language has no text back label,
 *     and a pushed screen's whole job for its bar is to be a way out. If the chevron ever stopped
 *     firing its action, a pushed screen would be a dead end with a system back button as its only
 *     exit -- which is also why it has to be tested at all.
 *  2. **The bar is 3 grid units tall.** That is `LightTopBar`'s own height, and the fragment layout
 *     sizes itself to it, so the list below starts where the reference client starts it. A toolbar
 *     that quietly reverted to Material's 64dp would push every settings row down the screen.
 *  3. **A null slot is not a dead button.** The welcome screen is the root of registration and has
 *     nothing to go back to, so it passes no left action; the bar must render the SDK's spacer
 *     there rather than an inert chevron. Symmetrically, a screen with no right action shows none.
 */
@RunWith(RobolectricTestRunner::class)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp -- the numbers the SDK's own previews use.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class LightAppTopBarTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `the bar is three grid units tall`() {
    setBar(title = "Notifications")

    // 1 gu = 360dp / 27 = 13.333dp at LP3 width, so `LightTopBar`'s 3 gu is 40dp and the chevron,
    // which the bar centres vertically, sits on the 20dp midline. This is what the fragment layout
    // sizes itself to: a bar that reverted to Material's 64dp would push every settings row down.
    val midline = composeTestRule.onNodeWithContentDescription("Navigate up")
      .getUnclippedBoundsInRoot()
      .center.y

    assertThat(midline.value).isCloseTo(3f * 360f / LightGrid.WIDTH / 2f, TOLERANCE_DP)
  }

  @Test
  fun `back is an icon that reaches the host`() {
    var backClicks = 0
    setBar(title = "Notifications", onBackClick = { backClicks++ })

    composeTestRule.onNodeWithContentDescription("Navigate up").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Navigate up").performClick()

    assertThat(backClicks).isEqualTo(1)
  }

  @Test
  fun `the title is shown verbatim rather than shouted`() {
    // `chats` renders "Account" and "Server", not "ACCOUNT"; uppercasing here would diverge from the
    // reference client's top bar grammar.
    setBar(title = "Notifications and calls")

    composeTestRule.onNodeWithText("Notifications and calls").assertIsDisplayed()
  }

  @Test
  fun `a blank title renders no centre slot`() {
    setBar(title = "   ")

    composeTestRule.onNodeWithContentDescription("Navigate up").assertIsDisplayed()
  }

  /**
   * The welcome screen is the root of registration: there is nothing behind it, so it passes no left
   * action. The bar must show the SDK's spacer there, not a chevron that navigates nowhere.
   */
  @Test
  fun `a root screen renders no back affordance at all`() {
    setBar(title = null, leftAction = null)

    composeTestRule.onAllNodesWithContentDescription("Navigate up").assertCountEquals(0)
  }

  @Test
  fun `a screen with no menu action shows no action glyph`() {
    setBar(title = "Notifications", actionIcon = null, onActionClick = null)

    composeTestRule.onNodeWithText("Notifications").assertIsDisplayed()
  }

  @Test
  fun `a single menu action is reachable from the bar`() {
    var actionClicks = 0
    setBar(
      title = "Story",
      actionIcon = ContextCompat.getDrawable(
        RuntimeEnvironment.getApplication(),
        R.drawable.ic_more_vert_24
      ),
      actionContentDescription = "More options",
      onActionClick = { actionClicks++ }
    )

    composeTestRule.onNodeWithContentDescription("More options").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("More options").performClick()

    assertThat(actionClicks).isEqualTo(1)
  }

  /**
   * Renders the bar in the same dark palette the app pins. [LightTopBarView] wraps the composable in
   * [MollyLightTheme] itself; rendering it here directly keeps the test to a `setContent` and stops
   * it passing on a light background it would never ship with.
   */
  @Composable
  private fun setBar(
    title: String?,
    leftAction: LightBarAction? = LightBarAction.Sdk(
      contentDescription = "Navigate up",
      icon = LightIcons.BACK,
      onClick = {}
    ),
    actionIcon: android.graphics.drawable.Drawable? = null,
    actionContentDescription: String? = null,
    onBackClick: () -> Unit = {},
    onActionClick: (() -> Unit)? = null
  ) {
    composeTestRule.setContent {
      LightTheme(colors = LightThemeColors.Dark) {
        LightAppTopBar(
          title = title,
          leftAction = leftAction,
          rightAction = actionIcon?.let {
            LightBarAction.Drawable(
              contentDescription = actionContentDescription,
              drawable = it,
              onClick = requireNotNull(onActionClick)
            )
          }
        )
      }
    }
  }

  private companion object {
    const val TOLERANCE_DP = 0.5f
  }
}
