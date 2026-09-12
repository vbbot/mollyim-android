/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightBottomBarItem
import com.thelightphone.sdk.ui.LightIcons
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.rememberIsSplitPane
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.light.rememberLightTintedPainter
import org.thoughtcrime.securesms.light.rememberMollyLightColors

/**
 * The Light Phone bottom bar: the app's only persistent chrome.
 *
 * Replaces Molly's Material `NavigationBar`. There is deliberately no hand-tuned geometry here --
 * every dimension comes from [LightBottomBar] itself (4 grid units tall, 2 grid units of horizontal
 * inset, 2-grid-unit icons, evenly distributed), which is what makes it line up with
 * `LightConversationListScreen`'s rows and read at the same weight as the LightOS action bar.
 *
 * It also absorbs everything the removed top bar and the removed floating action buttons used to
 * own: search, the overflow menu, the compose action and (inside the overflow) the camera.
 *
 * **Exactly five items.** [LightBottomBar] calls `require(items.size <= 5)`, which throws at
 * *runtime*, not compile time. Anything else that wants a place in the chrome has to go into the
 * overflow menu instead.
 */
@Composable
fun MainLightBottomBar(
  toolbarState: MainToolbarState,
  toolbarCallback: MainToolbarCallback,
  floatingActionButtonsCallback: MainFloatingActionButtonsCallback,
  onDestinationSelected: (MainNavigationListLocation) -> Unit,
  modifier: Modifier = Modifier
) {
  val colors = rememberMollyLightColors()
  val overflowController = remember { DropdownMenus.MenuController() }

  Box(
    modifier = modifier
      .fillMaxWidth()
      // Painted out here rather than inside MollyLightTheme so that the gesture-navigation strip
      // below the bar is the same colour as the bar itself.
      .background(colors.background)
  ) {
    MollyLightTheme {
      Column {
        LightBottomBar(
          items = bottomBarItems(
            toolbarState = toolbarState,
            toolbarCallback = toolbarCallback,
            floatingActionButtonsCallback = floatingActionButtonsCallback,
            onDestinationSelected = onDestinationSelected,
            overflowController = overflowController
          )
        )

        if (!LocalResources.current.rememberIsSplitPane()) {
          Spacer(modifier = Modifier.navigationBarsPadding())
        }
      }
    }

    // Zero-size anchor pinned under the overflow icon. The menu is deliberately composed *outside*
    // MollyLightTheme: DropdownMenus.Menu paints itself with SignalTheme's surface colour, so under
    // the Light colour scheme its text and its background would be sampled from two different
    // palettes.
    Box(modifier = Modifier.align(Alignment.BottomEnd)) {
      MainLightOverflowMenu(
        controller = overflowController,
        toolbarState = toolbarState,
        toolbarCallback = toolbarCallback,
        floatingActionButtonsCallback = floatingActionButtonsCallback
      )
    }
  }
}

/**
 * The five bottom bar slots: chats, calls, create, search, overflow.
 *
 * The create slot follows the current tab, exactly as the removed primary floating action button
 * did -- otherwise "start a new call" would have no entry point left anywhere in the app.
 */
@Composable
private fun bottomBarItems(
  toolbarState: MainToolbarState,
  toolbarCallback: MainToolbarCallback,
  floatingActionButtonsCallback: MainFloatingActionButtonsCallback,
  onDestinationSelected: (MainNavigationListLocation) -> Unit,
  overflowController: DropdownMenus.MenuController
): List<LightBottomBarItem?> {
  val chats = LightBarButton.Icon(
    // The SDK has no chat-bubble glyph, so this is Molly's own. It is drawn at
    // LightBarButtonDefaults.ICON_SIZE_UNITS (the default), i.e. the same 2 grid units as its
    // neighbours, so it does not read larger or smaller than the genuine SDK assets.
    painter = rememberLightTintedPainter(R.drawable.symbol_chat_24),
    onClick = { onDestinationSelected(MainNavigationListLocation.CHATS) },
    contentDescription = stringResource(R.string.ConversationListTabs__chats)
  )

  val calls = LightBarButton.LightIcon(
    icon = LightIcons.CALL,
    onClick = { onDestinationSelected(MainNavigationListLocation.CALLS) },
    contentDescription = stringResource(R.string.ConversationListTabs__calls)
  )

  val create = when (toolbarState.destination) {
    MainNavigationListLocation.CHATS, MainNavigationListLocation.ARCHIVE -> LightBarButton.LightIcon(
      icon = LightIcons.COMPOSE_MESSAGE,
      onClick = floatingActionButtonsCallback::onNewChatClick,
      contentDescription = stringResource(R.string.conversation_list_fragment__fab_content_description)
    )

    MainNavigationListLocation.CALLS -> LightBarButton.LightIcon(
      icon = LightIcons.DIALPAD,
      onClick = floatingActionButtonsCallback::onNewCallClick,
      contentDescription = stringResource(R.string.CallLogFragment__start_a_new_call)
    )

    MainNavigationListLocation.STORIES -> LightBarButton.LightIcon(
      icon = LightIcons.CAMERA,
      onClick = { floatingActionButtonsCallback.onCameraClick(MainNavigationListLocation.STORIES) },
      contentDescription = stringResource(R.string.conversation_list_fragment__open_camera_description)
    )
  }

  val search = LightBarButton.LightIcon(
    icon = LightIcons.SEARCH,
    onClick = toolbarCallback::onSearchClick,
    contentDescription = stringResource(R.string.conversation_list_search_description)
  )

  val overflow = LightBarButton.LightIcon(
    icon = LightIcons.ELLIPSES,
    onClick = { overflowController.show() },
    contentDescription = stringResource(R.string.MainToolbar__more_options_content_description)
  )

  return listOf(chats, calls, create, search, overflow)
}

/**
 * The overflow menu, re-anchored from the removed top bar into the bottom bar.
 *
 * Every entry the top bar's overflow had is still here. It additionally carries Camera -- a
 * secondary action that no longer earns a slot of its own -- and Proxy, which used to be a
 * conditional top bar icon.
 */
@Composable
private fun MainLightOverflowMenu(
  controller: DropdownMenus.MenuController,
  toolbarState: MainToolbarState,
  toolbarCallback: MainToolbarCallback,
  floatingActionButtonsCallback: MainFloatingActionButtonsCallback
) {
  DropdownMenus.Menu(
    controller = controller,
    // The default gutter offset would push a bottom-end anchored menu off the right edge.
    offsetX = 0.dp
  ) { menuController ->
    val dismiss = { menuController.hide() }

    DropdownMenus.Item(
      text = {
        Text(text = stringResource(R.string.conversation_list_fragment__open_camera_description))
      },
      onClick = {
        floatingActionButtonsCallback.onCameraClick(MainNavigationListLocation.CHATS)
        dismiss()
      }
    )

    if (toolbarState.proxyState != MainToolbarState.ProxyState.NONE) {
      DropdownMenus.Item(
        text = {
          Text(text = stringResource(R.string.MainToolbar__proxy_content_description))
        },
        onClick = {
          toolbarCallback.onProxyClick()
          dismiss()
        }
      )
    }

    when (toolbarState.destination) {
      MainNavigationListLocation.ARCHIVE -> Unit
      MainNavigationListLocation.CHATS -> ChatDropdownItems(toolbarState, toolbarCallback, dismiss)
      MainNavigationListLocation.CALLS -> CallDropdownItems(toolbarState.callFilter, toolbarCallback, dismiss)
      MainNavigationListLocation.STORIES -> StoryDropDownItems(toolbarCallback, dismiss)
    }
  }
}
