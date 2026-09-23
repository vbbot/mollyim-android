/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
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
import org.thoughtcrime.securesms.conversationlist.light.LightConversationListItem
import org.thoughtcrime.securesms.conversationlist.light.LightConversationListScreen
import org.thoughtcrime.securesms.conversationlist.light.LightConversationListState
import org.thoughtcrime.securesms.light.MollyLightTheme

/**
 * [com.thelightphone.sdk.ui.LightBottomBar] enforces its five-item limit with `require()`, which
 * throws when the bar is *composed* -- neither the compiler nor lint can catch a sixth item. These
 * tests compose the real bar so that a regression fails here rather than on the device.
 */
@RunWith(RobolectricTestRunner::class)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp -- the same numbers the SDK's own previews use.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class MainLightBottomBarTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `chats destination renders all five slots`() {
    setBottomBar(MainNavigationListLocation.CHATS)

    assertSlotsDisplayed("Chats", "Calls", "New chat", "Search", "More options")
  }

  @Test
  fun `calls destination swaps the create slot rather than adding one`() {
    setBottomBar(MainNavigationListLocation.CALLS)

    assertSlotsDisplayed("Chats", "Calls", "Start a new call", "Search", "More options")
  }

  @Test
  fun `tapping a destination slot routes through to the callback`() {
    var selected: MainNavigationListLocation? = null
    composeTestRule.setContent {
      MainLightBottomBar(
        toolbarState = MainToolbarState(destination = MainNavigationListLocation.CHATS),
        toolbarCallback = MainToolbarCallback.Empty,
        floatingActionButtonsCallback = MainFloatingActionButtonsCallback.Empty,
        onDestinationSelected = { selected = it }
      )
    }

    composeTestRule.onNodeWithContentDescription("Calls").performClick()

    assertThat(selected).isEqualTo(MainNavigationListLocation.CALLS)
  }

  /**
   * Uses the calls destination purely because `ChatDropdownItems` reads `SignalStore`, which is not
   * initialized in a plain Robolectric test. The re-anchoring being verified -- the bar's overflow
   * icon driving the menu that used to hang off the top bar -- is the same either way.
   */
  @Test
  fun `tapping overflow opens the menu that used to hang off the top bar, now carrying camera`() {
    setBottomBar(MainNavigationListLocation.CALLS)

    composeTestRule.onNodeWithContentDescription("More options").performClick()

    composeTestRule.onNodeWithText("Open Camera").assertIsDisplayed()
    composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    composeTestRule.onNodeWithText("Clear call history").assertIsDisplayed()
  }

  /**
   * The whole point of building the bar out of `LightBottomBar` rather than hand-tuned dp is that
   * its geometry already agrees with the list's. A conversation row's name starts at 1.75 grid units
   * (0.5 row padding + 1.0 unread-marker slot + 0.25 gap) and `LightBottomBar` insets its content at
   * 2.0, so the first icon should sit a quarter of a grid unit to the right of the names -- no more.
   */
  @Test
  fun `first bar icon sits within a quarter grid unit of the list row names`() {
    var gridUnit = 0.dp

    composeTestRule.setContent {
      gridUnit = (LocalConfiguration.current.screenWidthDp / 27f).dp

      Column(modifier = Modifier.fillMaxSize()) {
        MollyLightTheme {
          LightConversationListScreen(
            state = LightConversationListState(rows = listOf(ROW)),
            selectionMode = false,
            modifier = Modifier.weight(1f)
          )
        }

        MainLightBottomBar(
          toolbarState = MainToolbarState(destination = MainNavigationListLocation.CHATS),
          toolbarCallback = MainToolbarCallback.Empty,
          floatingActionButtonsCallback = MainFloatingActionButtonsCallback.Empty,
          onDestinationSelected = {}
        )
      }
    }

    val nameLeft = composeTestRule.onNodeWithText(ROW.name, useUnmergedTree = true).getUnclippedBoundsInRoot().left
    val iconLeft = composeTestRule.onNodeWithContentDescription("Chats").getUnclippedBoundsInRoot().left

    assertThat(nameLeft.value).isBetween((gridUnit * 1.75f).value - 1f, (gridUnit * 1.75f).value + 1f)
    assertThat(iconLeft.value).isBetween((gridUnit * 2f).value - 1f, (gridUnit * 2f).value + 1f)
    assertThat((iconLeft - nameLeft).value).isBetween(0f, (gridUnit * 0.25f).value + 1f)
  }

  private fun setBottomBar(destination: MainNavigationListLocation) {
    composeTestRule.setContent {
      MainLightBottomBar(
        toolbarState = MainToolbarState(destination = destination),
        toolbarCallback = MainToolbarCallback.Empty,
        floatingActionButtonsCallback = MainFloatingActionButtonsCallback.Empty,
        onDestinationSelected = {}
      )
    }
  }

  private fun assertSlotsDisplayed(vararg contentDescriptions: String) {
    contentDescriptions.forEach { description ->
      composeTestRule.onNodeWithContentDescription(description).assertIsDisplayed()
    }
  }

  companion object {
    private val ROW = LightConversationListItem(
      key = 1L,
      sourceIndex = 0,
      kind = LightConversationListItem.Kind.THREAD,
      name = "Mummy",
      timestamp = "12:01",
      timestampDescription = "",
      unread = false,
      selected = false,
      conversation = null
    )
  }
}
