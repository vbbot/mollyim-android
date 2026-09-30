package org.thoughtcrime.securesms.main

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.signal.core.ui.CoreUiDependenciesRule
import org.thoughtcrime.securesms.keyvalue.SignalStore

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33], qualifiers = "w360dp-h413dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainLightBottomBarTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  @Before
  fun setUp() {
    mockkObject(SignalStore)
    every { SignalStore.labs.starredMessages } returns false
  }

  @After
  fun tearDown() {
    unmockkObject(SignalStore)
  }

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
  fun `overflow stays closed until more-options is tapped`() {
    setBar(state = MainToolbarState(destination = MainNavigationListLocation.CHATS, hasPassphrase = true))

    composeTestRule.onNodeWithContentDescription("More options").assertIsDisplayed()
    // The panel is not composed at all while closed.
    composeTestRule.onNodeWithText("LOCK").assertDoesNotExist()

    composeTestRule.onNodeWithContentDescription("More options").performClick()
    composeTestRule.waitForIdle()

    // With a passphrase set, the overflow must offer the clear-passphrase action.
    composeTestRule.onNodeWithText("LOCK").assertIsDisplayed()
  }
}
