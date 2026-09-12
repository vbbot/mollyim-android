/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.light

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule

/**
 * The reusable Light action panel, at the LP3's real geometry.
 *
 * Two of these assertions are about things that only go wrong on a device: that a long list still fits
 * (Molly's long-press menu runs to seven rows, and half of a 413dp screen holds about four), and that a
 * row's tap target hugs its label rather than filling the row -- the reference client shipped full-row
 * targets and found them firing on taps far to the side of the text.
 */
@RunWith(RobolectricTestRunner::class)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class LightActionPanelTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `every action is rendered as its own row`() {
    setPanel(labels = listOf("AUDIO CALL", "VIDEO CALL"))

    composeTestRule.onNodeWithText("AUDIO CALL").assertIsDisplayed()
    composeTestRule.onNodeWithText("VIDEO CALL").assertIsDisplayed()
  }

  /** A group thread offers only a video call, and the menu still opens rather than dialling through. */
  @Test
  fun `a single action still renders as a panel`() {
    setPanel(labels = listOf("VIDEO CALL"))

    composeTestRule.onNodeWithText("VIDEO CALL").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Close").assertIsDisplayed()
  }

  @Test
  fun `tapping a row runs that action and no other`() {
    var chosen: String? = null

    composeTestRule.setContent {
      MollyLightTheme {
        Box(modifier = Modifier.fillMaxSize()) {
          LightActionPanel(
            actions = listOf(
              LightPanelAction("AUDIO CALL") { chosen = "audio" },
              LightPanelAction("VIDEO CALL") { chosen = "video" }
            ),
            onDismiss = { chosen = "dismissed" },
            modifier = Modifier.align(Alignment.BottomCenter)
          )
        }
      }
    }

    composeTestRule.onNodeWithText("VIDEO CALL").performClick()

    assertThat(chosen).isEqualTo("video")
  }

  @Test
  fun `the chevron dismisses`() {
    var dismissed = false

    composeTestRule.setContent {
      MollyLightTheme {
        Box(modifier = Modifier.fillMaxSize()) {
          LightActionPanel(
            actions = listOf(LightPanelAction("AUDIO CALL") {}),
            onDismiss = { dismissed = true },
            modifier = Modifier.align(Alignment.BottomCenter)
          )
        }
      }
    }

    composeTestRule.onNodeWithContentDescription("Close").performClick()

    assertThat(dismissed).isTrue()
  }

  /**
   * The tap target is the label, not the row. Asserted as "narrower than the panel", because a
   * full-width target is exactly the regression this guards against.
   */
  @Test
  fun `a row's tap target hugs its label`() {
    setPanel(labels = listOf("AUDIO CALL"))

    val label = composeTestRule.onNodeWithText("AUDIO CALL").getUnclippedBoundsInRoot()
    val labelWidth = (label.right - label.left).value

    assertThat(labelWidth).isBetween(1f, 300f)
  }

  /**
   * Molly's long-press menu on an ordinary text message is Reply, Forward, Copy, Multi-select, Info,
   * Star and Delete. Half of the LP3's 413dp screen, less the chevron's zone, holds about four 44dp
   * rows, so the list has to scroll -- the reference client's fixed, centred stack would simply lose
   * the rest.
   */
  @Test
  fun `a list longer than the panel scrolls instead of overflowing it`() {
    setPanel(labels = listOf("REPLY", "FORWARD", "COPY", "MULTI-SELECT", "INFO", "STAR", "DELETE"))

    val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    val panelTop = root.bottom - (root.bottom - root.top) / 2f

    val first = composeTestRule.onNodeWithText("REPLY").getUnclippedBoundsInRoot()
    val second = composeTestRule.onNodeWithText("FORWARD").getUnclippedBoundsInRoot()

    // The rows sit inside the panel, on the 44dp rhythm, rather than spilling out above it -- which
    // is what a fixed, centred column of seven rows would do in half a 413dp screen.
    assertThat(first.top.value).isBetween(panelTop.value - 1f, root.bottom.value)
    assertThat((second.top - first.top).value).isBetween(ROW_HEIGHT.value - 1f, ROW_HEIGHT.value + 1f)

    // And the rows that do not fit are reachable by scrolling rather than lost.
    composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText("DELETE"))
    composeTestRule.onNodeWithText("DELETE").assertIsDisplayed()
  }

  private fun setPanel(labels: List<String>) {
    composeTestRule.setContent {
      MollyLightTheme {
        Box(modifier = Modifier.fillMaxSize()) {
          LightActionPanel(
            actions = labels.map { LightPanelAction(it) {} },
            onDismiss = {},
            modifier = Modifier.align(Alignment.BottomCenter)
          )
        }
      }
    }
  }

  companion object {
    private val ROW_HEIGHT = 44.dp
  }
}
