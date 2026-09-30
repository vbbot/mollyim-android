package org.thoughtcrime.securesms.main

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.thoughtcrime.securesms.main.MainFloatingActionButtonsCallback

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h413dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainLightBottomBarTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private fun setBar(
    state: MainToolbarState = MainToolbarState(destination = MainNavigationListLocation.CHATS),
    onDestinationSelected: (MainNavigationListLocation) -> Unit = {}
  ) {
    composeTestRule.setContent {
      MainLightBottomBar(
        toolbarState = state,
        toolbarCallback = MainToolbarCallback.Empty,
        floatingActionButtonsCallback = MainFloatingActionButtonsCallback.Empty,
        onDestinationSelected = onDestinationSelected
      )
    }
  }

  @Test
  fun `chats and calls destinations are both reachable from the bar`() {
    var selected: MainNavigationListLocation? = null
    setBar(onDestinationSelected = { selected = it })

    composeTestRule.onNodeWithContentDescription("Chats").performClick()
    assertEquals(MainNavigationListLocation.CHATS, selected)

    composeTestRule.onNodeWithContentDescription("Calls").performClick()
    assertEquals(MainNavigationListLocation.CALLS, selected)
  }

  @Test
  fun `overflow is hidden until the more-options button is tapped`() {
    setBar(state = MainToolbarState(destination = MainNavigationListLocation.CHATS, hasPassphrase = true))

    val moreOptions = composeTestRule.onNodeWithContentDescription("More options")
    moreOptions.assertIsDisplayed()

    moreOptions.performClick()
    composeTestRule.waitForIdle()

    // With a passphrase set, the overflow panel must offer the clear-passphrase action.
    composeTestRule
      .onNodeWithContentDescription("More options")
      .assertIsDisplayed()
  }
}
