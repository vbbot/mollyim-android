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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightBottomBarItem
import com.thelightphone.sdk.ui.LightIcons
import org.signal.core.ui.rememberIsSplitPane
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.calls.log.CallLogFilter
import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.light.LightActionPanelDialog
import org.thoughtcrime.securesms.light.LightPanelAction
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.light.rememberLightTintedPainter
import org.thoughtcrime.securesms.light.rememberMollyLightColors

/** The five-slot Light Phone bottom bar and its window-level Light overflow panel. */
@Composable
fun MainLightBottomBar(
  toolbarState: MainToolbarState,
  toolbarCallback: MainToolbarCallback,
  floatingActionButtonsCallback: MainFloatingActionButtonsCallback,
  onDestinationSelected: (MainNavigationListLocation) -> Unit,
  modifier: Modifier = Modifier
) {
  val colors = rememberMollyLightColors()
  var overflowOpen by remember { mutableStateOf(false) }

  Box(
    modifier = modifier
      .fillMaxWidth()
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
            onOverflowClick = { overflowOpen = true }
          )
        )

        if (!LocalResources.current.rememberIsSplitPane()) {
          Spacer(modifier = Modifier.navigationBarsPadding())
        }
      }
    }
  }

  if (overflowOpen) {
    MainLightOverflowPanel(
      toolbarState = toolbarState,
      toolbarCallback = toolbarCallback,
      floatingActionButtonsCallback = floatingActionButtonsCallback,
      onDismiss = { overflowOpen = false }
    )
  }
}

@Composable
private fun bottomBarItems(
  toolbarState: MainToolbarState,
  toolbarCallback: MainToolbarCallback,
  floatingActionButtonsCallback: MainFloatingActionButtonsCallback,
  onDestinationSelected: (MainNavigationListLocation) -> Unit,
  onOverflowClick: () -> Unit
): List<LightBottomBarItem?> {
  val chats = LightBarButton.Icon(
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
    onClick = onOverflowClick,
    contentDescription = stringResource(R.string.MainToolbar__more_options_content_description)
  )

  return listOf(chats, calls, create, search, overflow)
}

@Composable
private fun MainLightOverflowPanel(
  toolbarState: MainToolbarState,
  toolbarCallback: MainToolbarCallback,
  floatingActionButtonsCallback: MainFloatingActionButtonsCallback,
  onDismiss: () -> Unit
) {
  val actions = mainLightOverflowActions(
    toolbarState = toolbarState,
    toolbarCallback = toolbarCallback,
    floatingActionButtonsCallback = floatingActionButtonsCallback,
    onDismiss = onDismiss
  )
  LightActionPanelDialog(actions = actions, onDismiss = onDismiss)
}

/** Single source of truth for the old toolbar callback gates and action ordering. */
@Composable
internal fun mainLightOverflowActions(
  toolbarState: MainToolbarState,
  toolbarCallback: MainToolbarCallback,
  floatingActionButtonsCallback: MainFloatingActionButtonsCallback,
  onDismiss: () -> Unit
): List<LightPanelAction> {
  val actions = mutableListOf<LightPanelAction>()

  fun add(label: String, action: () -> Unit) {
    actions += LightPanelAction(label.uppercase()) {
      action()
      onDismiss()
    }
  }

  add(stringResource(R.string.conversation_list_fragment__open_camera_description)) {
    floatingActionButtonsCallback.onCameraClick(MainNavigationListLocation.CHATS)
  }

  if (toolbarState.proxyState != MainToolbarState.ProxyState.NONE) {
    add(stringResource(R.string.MainToolbar__proxy_content_description), toolbarCallback::onProxyClick)
  }

  when (toolbarState.destination) {
    MainNavigationListLocation.ARCHIVE -> Unit
    MainNavigationListLocation.STORIES -> {
      add(stringResource(R.string.StoriesLandingFragment__story_privacy), toolbarCallback::onStoryPrivacyClick)
    }
    MainNavigationListLocation.CALLS -> {
      add(stringResource(R.string.CallLogFragment__clear_call_history), toolbarCallback::onClearCallHistoryClick)
      if (toolbarState.callFilter == CallLogFilter.ALL) {
        add(stringResource(R.string.CallLogFragment__filter_missed_calls), toolbarCallback::onFilterMissedCallsClick)
      } else {
        add(stringResource(R.string.CallLogFragment__clear_filter), toolbarCallback::onClearCallFilterClick)
      }
      add(stringResource(R.string.text_secure_normal__menu_settings), toolbarCallback::onSettingsClick)
      add(stringResource(R.string.ConversationListFragment__notification_profile), toolbarCallback::onNotificationProfileClick)
    }
    MainNavigationListLocation.CHATS -> {
      if (toolbarState.hasPassphrase) {
        add(stringResource(R.string.text_secure_normal__menu_clear_passphrase), toolbarCallback::onClearPassphraseClick)
      }
      add(stringResource(R.string.text_secure_normal__menu_new_group), toolbarCallback::onNewGroupClick)
      add(stringResource(R.string.text_secure_normal__mark_all_as_read), toolbarCallback::onMarkReadClick)
      if (toolbarState.chatFilter == ConversationFilter.OFF) {
        add(stringResource(R.string.text_secure_normal__filter_unread_chats), toolbarCallback::onFilterUnreadChatsClick)
      } else {
        add(stringResource(R.string.text_secure_normal__clear_unread_filter), toolbarCallback::onClearUnreadChatsFilterClick)
      }
      if (SignalStore.labs.starredMessages) {
        add(stringResource(R.string.text_secure_normal__starred_messages), toolbarCallback::onStarredMessagesClick)
      }
      add(stringResource(R.string.text_secure_normal__menu_settings), toolbarCallback::onSettingsClick)
      add(stringResource(R.string.ConversationListFragment__notification_profile), toolbarCallback::onNotificationProfileClick)
    }
  }

  return actions
}
