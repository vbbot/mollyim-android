/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import assertk.assertThat
import assertk.assertions.isLessThan
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.signal.core.ui.CoreUiDependenciesRule
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.calls.log.CallLogFilter
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Search and overflow moved from [MainLightBottomBar] to [MainLightTopBar]. These tests pin the
 * layout of the new bar and that the overflow panel it opens is the same one the bottom bar used to.
 */
@RunWith(RobolectricTestRunner::class)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp -- the same numbers the SDK's own previews use.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainLightTopBarTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  @Before
  fun setUp() {
    mockkObject(SignalStore)
    every { SignalStore.labs.starredMessages } returns false
    every { SignalStore.labs.betterSearch } returns false
  }

  @After
  fun tearDown() {
    unmockkObject(SignalStore)
  }

  @Test
  fun `search is on the left, the app name in the centre and overflow on the right`() {
    setTopBar(MainNavigationListLocation.CHATS)

    val appName = RuntimeEnvironment.getApplication().getString(R.string.app_name)
    val search = composeTestRule.onNodeWithContentDescription("Search").assertIsDisplayed().getUnclippedBoundsInRoot()
    val title = composeTestRule.onNodeWithText(appName).assertIsDisplayed().getUnclippedBoundsInRoot()
    val overflow = composeTestRule.onNodeWithContentDescription("More options").assertIsDisplayed().getUnclippedBoundsInRoot()

    assertThat(search.right.value).isLessThan(title.left.value)
    assertThat(title.right.value).isLessThan(overflow.left.value)
  }

  @Test
  fun `search routes to the toolbar callback`() {
    val callback = mockk<MainToolbarCallback>(relaxUnitFun = true)
    setTopBar(state = MainToolbarState(destination = MainNavigationListLocation.CHATS), toolbarCallback = callback)

    composeTestRule.onNodeWithContentDescription("Search").performClick()

    verify(exactly = 1) { callback.onSearchClick() }
  }

  /**
   * Uses the calls destination purely because the chats actions read more of `SignalStore` than
   * this test stubs. The re-anchoring being verified -- the top bar's overflow icon driving the
   * panel that used to hang off the bottom bar -- is the same either way.
   */
  @Test
  fun `tapping overflow opens the menu, carrying camera`() {
    setTopBar(MainNavigationListLocation.CALLS)

    composeTestRule.onNodeWithContentDescription("More options").performClick()

    composeTestRule.onNodeWithText("OPEN CAMERA").assertIsDisplayed()
    composeTestRule.onNodeWithText("SETTINGS").assertIsDisplayed()
    composeTestRule.onNodeWithText("CLEAR CALL HISTORY").assertIsDisplayed()
  }

  @Test
  fun `calls overflow with all calls offers missed filter`() {
    setTopBar(MainNavigationListLocation.CALLS)
    composeTestRule.onNodeWithContentDescription("More options").performClick()

    composeTestRule.onNodeWithText("FILTER MISSED CALLS").assertIsDisplayed()
    composeTestRule.onNodeWithText("CLEAR FILTER").assertDoesNotExist()
  }

  @Test
  fun `calls overflow with filtered calls offers clear filter`() {
    setTopBar(
      state = MainToolbarState(
        destination = MainNavigationListLocation.CALLS,
        callFilter = CallLogFilter.MISSED
      )
    )
    composeTestRule.onNodeWithContentDescription("More options").performClick()

    composeTestRule.onNodeWithText("CLEAR FILTER").assertIsDisplayed()
    composeTestRule.onNodeWithText("FILTER MISSED CALLS").assertDoesNotExist()
  }

  @Test
  fun `story overflow keeps only story privacy destination action`() {
    setTopBar(MainNavigationListLocation.STORIES)
    composeTestRule.onNodeWithContentDescription("More options").performClick()

    composeTestRule.onNodeWithText("STORY PRIVACY").assertIsDisplayed()
    composeTestRule.onNodeWithText("SETTINGS").assertDoesNotExist()
  }

  @Test
  fun `archive overflow keeps no destination-specific actions`() {
    setTopBar(MainNavigationListLocation.ARCHIVE)
    composeTestRule.onNodeWithContentDescription("More options").performClick()

    composeTestRule.onNodeWithText("OPEN CAMERA").assertIsDisplayed()
    composeTestRule.onNodeWithText("SETTINGS").assertDoesNotExist()
    composeTestRule.onNodeWithText("STORY PRIVACY").assertDoesNotExist()
  }

  @Test
  fun `overflow action delegates to toolbar callback and dismisses panel`() {
    val callback = mockk<MainToolbarCallback>(relaxUnitFun = true)
    setTopBar(
      state = MainToolbarState(destination = MainNavigationListLocation.CALLS),
      toolbarCallback = callback
    )
    composeTestRule.onNodeWithContentDescription("More options").performClick()

    composeTestRule.onNodeWithText("CLEAR CALL HISTORY").performClick()

    verify(exactly = 1) { callback.onClearCallHistoryClick() }
    composeTestRule.onNodeWithText("CLEAR CALL HISTORY").assertDoesNotExist()
  }

  @Test
  fun `proxy action is absent when proxy state is none`() {
    setTopBar(MainNavigationListLocation.CALLS)
    composeTestRule.onNodeWithContentDescription("More options").performClick()

    composeTestRule.onNodeWithText("PROXY").assertDoesNotExist()
  }

  @Test
  fun `proxy action is present when proxy has state`() {
    setTopBar(
      state = MainToolbarState(
        destination = MainNavigationListLocation.CALLS,
        proxyState = MainToolbarState.ProxyState.CONNECTED
      )
    )
    composeTestRule.onNodeWithContentDescription("More options").performClick()

    composeTestRule.onNodeWithText("PROXY").assertIsDisplayed()
  }

  @Test
  fun `main toolbar draws the top bar in full mode`() {
    setMainToolbar(MainToolbarMode.FULL)

    composeTestRule.onNodeWithContentDescription("Search").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("More options").assertIsDisplayed()
  }

  @Test
  fun `search mode replaces the top bar rather than stacking under it`() {
    setMainToolbar(MainToolbarMode.SEARCH)

    composeTestRule.onNodeWithContentDescription("More options").assertDoesNotExist()
  }

  @Test
  fun `action mode replaces the top bar`() {
    setMainToolbar(MainToolbarMode.ACTION_MODE)

    composeTestRule.onNodeWithContentDescription("Search").assertDoesNotExist()
    composeTestRule.onNodeWithContentDescription("More options").assertDoesNotExist()
  }

  private fun setMainToolbar(mode: MainToolbarMode) {
    composeTestRule.setContent {
      MainToolbar(
        state = MainToolbarState(mode = mode, actionModeCount = 1),
        callback = MainToolbarCallback.Empty,
        floatingActionButtonsCallback = MainFloatingActionButtonsCallback.Empty
      )
    }
  }

  private fun setTopBar(destination: MainNavigationListLocation) {
    setTopBar(state = MainToolbarState(destination = destination))
  }

  private fun setTopBar(
    state: MainToolbarState,
    toolbarCallback: MainToolbarCallback = MainToolbarCallback.Empty
  ) {
    composeTestRule.setContent {
      MainLightTopBar(
        toolbarState = state,
        toolbarCallback = toolbarCallback,
        floatingActionButtonsCallback = MainFloatingActionButtonsCallback.Empty
      )
    }
  }
}
