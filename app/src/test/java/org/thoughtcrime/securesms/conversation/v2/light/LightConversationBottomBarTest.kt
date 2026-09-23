/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.light

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.thoughtcrime.securesms.light.MollyLightTheme

/**
 * [com.thelightphone.sdk.ui.LightBottomBar] enforces its item limits with `require()`, which throws
 * when the bar is *composed* -- a fourth slot is a crash on the device, not a build failure. These
 * tests compose the real bar so that a regression fails here instead.
 *
 * They also pin the thing that cannot be seen in a build at all: whether the call slot is there, and
 * whether attach and compose stay where they are when it is not.
 */
@RunWith(RobolectricTestRunner::class)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp -- the same numbers the SDK's own previews use.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class LightConversationBottomBarTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `a callable thread renders all three slots`() {
    setBottomBar(showCall = true)

    composeTestRule.onNodeWithContentDescription("Call").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Add attachment").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Write a message").assertIsDisplayed()
  }

  /**
   * A thread with neither a voice nor a video call available must not offer a button that opens an
   * empty menu.
   */
  @Test
  fun `a thread with no call available drops the call slot`() {
    setBottomBar(showCall = false)

    composeTestRule.onNodeWithContentDescription("Call").assertDoesNotExist()
    composeTestRule.onNodeWithContentDescription("Add attachment").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Write a message").assertIsDisplayed()
  }

  /**
   * The missing call slot is passed as `null`, not dropped, so that `LightBottomBar` still lays the
   * bar out as start/centre/end rather than collapsing to its two-item edge layout. Otherwise attach
   * and compose would visibly jump between one conversation and the next.
   */
  @Test
  fun `attach and compose do not move when the call slot is absent`() {
    val showCall = mutableStateOf(true)

    composeTestRule.setContent {
      MollyLightTheme {
        LightConversationBottomBar(
          showCall = showCall.value,
          onCallClick = {},
          onAddClick = {},
          onComposeClick = {}
        )
      }
    }

    val withCall = slotCentres()

    composeTestRule.runOnIdle { showCall.value = false }
    composeTestRule.waitForIdle()

    val withoutCall = slotCentres()

    assertThat(withoutCall.first.value).isBetween(withCall.first.value - 1f, withCall.first.value + 1f)
    assertThat(withoutCall.second.value).isBetween(withCall.second.value - 1f, withCall.second.value + 1f)
  }

  @Test
  fun `each slot routes through to its own callback`() {
    var tapped: String? = null

    composeTestRule.setContent {
      MollyLightTheme {
        LightConversationBottomBar(
          showCall = true,
          onCallClick = { tapped = "call" },
          onAddClick = { tapped = "add" },
          onComposeClick = { tapped = "compose" }
        )
      }
    }

    composeTestRule.onNodeWithContentDescription("Call").performClick()
    assertThat(tapped).isEqualTo("call")

    composeTestRule.onNodeWithContentDescription("Add attachment").performClick()
    assertThat(tapped).isEqualTo("add")

    composeTestRule.onNodeWithContentDescription("Write a message").performClick()
    assertThat(tapped).isEqualTo("compose")
  }

  /**
   * The bar's whole point is that its geometry comes from `LightBottomBar` rather than from tuned dp,
   * which is what makes it line up with the thread's rows. `LightBottomBar` insets its content by two
   * grid units.
   */
  @Test
  fun `the first slot sits on the bar's two-grid-unit inset`() {
    var gridUnit = 0.dp

    composeTestRule.setContent {
      gridUnit = (LocalConfiguration.current.screenWidthDp / 27f).dp

      MollyLightTheme {
        LightConversationBottomBar(
          showCall = true,
          onCallClick = {},
          onAddClick = {},
          onComposeClick = {}
        )
      }
    }

    val callLeft = composeTestRule.onNodeWithContentDescription("Call").getUnclippedBoundsInRoot().left

    assertThat(callLeft.value).isBetween((gridUnit * 2f).value - 1f, (gridUnit * 2f).value + 1f)
  }

  /** Centres of the attach and compose slots, which must not move with the call slot's presence. */
  private fun slotCentres(): Pair<androidx.compose.ui.unit.Dp, androidx.compose.ui.unit.Dp> {
    val add = composeTestRule.onNodeWithContentDescription("Add attachment").getUnclippedBoundsInRoot()
    val compose = composeTestRule.onNodeWithContentDescription("Write a message").getUnclippedBoundsInRoot()

    return (add.left + add.right) / 2f to (compose.left + compose.right) / 2f
  }

  private fun setBottomBar(showCall: Boolean) {
    composeTestRule.setContent {
      MollyLightTheme {
        LightConversationBottomBar(
          showCall = showCall,
          onCallClick = {},
          onAddClick = {},
          onComposeClick = {}
        )
      }
    }
  }
}
