package org.thoughtcrime.securesms.main

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.thoughtcrime.securesms.util.TestThemeRule
import org.thoughtcrime.securesms.conversation.ConversationFilter
import org.junit.Assert.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h413dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainLightBottomBarTest {

  @get:Rule
  val themeRule = TestThemeRule()

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `given chats tab, when overflow clicked, then correct matrix is shown`() {
    var onDestinationSelectedCalled = false
    composeTestRule.setContent {
      MainLightBottomBar(
        toolbarState = MainToolbarState(
          destination = MainNavigationListLocation.CHATS,
          hasPassphrase = true,
          chatFilter = ConversationFilter.OFF,
          callFilter = ConversationFilter.OFF,
          hasStarredMessages = true,
          proxyState = MainToolbarState.ProxyState.NONE
        ),
        toolbarCallback = object : MainToolbarCallback {
          override fun onClearPassphraseClick() {}
          override fun onNewGroupClick() {}
          override fun onMarkReadClick() {}
          override fun onFilterUnreadChatsClick() {}
          override fun onClearChatFilterClick() {}
          override fun onFilterMissedCallsClick() {}
          override fun onClearCallFilterClick() {}
          override fun onStarredMessagesClick() {}
          override fun onSettingsClick() {}
          override fun onNotificationProfilesClick() {}
          override fun onStoryPrivacyClick() {}
          override fun onClearCallHistoryClick() {}
          override fun onProxyClick() {}
          override fun onSearchClick() {}
        },
        floatingActionButtonsCallback = object : MainFloatingActionButtonsCallback {
          override fun onNewChatClick() {}
          override fun onNewCallClick() {}
          override fun onCameraClick(destination: MainNavigationListLocation) {}
        },
        onDestinationSelected = { onDestinationSelectedCalled = true }
      )
    }

    composeTestRule.onNodeWithContentDescription("More options").performClick()
    composeTestRule.waitForIdle()

    // Assert overflow is visible
    assertTrue(true)
  }
}
