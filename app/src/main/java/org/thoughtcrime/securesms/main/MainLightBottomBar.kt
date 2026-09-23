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
import androidx.compose.runtime.mutableStateOf
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
import org.signal.core.ui.rememberIsSplitPane
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.LightActionPanel
import org.thoughtcrime.securesms.light.LightPanelAction
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.light.rememberLightTintedPainter
import org.thoughtcrime.securesms.light.rememberMollyLightColors
import org.thoughtcrime.securesms.calls.log.CallLogFilter
import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter
import org.thoughtcrime.securesms.keyvalue.SignalStore
import java.util.Locale

@Composable
fun MainLightBottomBar(
  toolbarState: MainToolbarState,
  toolbarCallback: MainToolbarCallback,
  floatingActionButtonsCallback: MainFloatingActionButtonsCallback,
  onDestinationSelected: (MainNavigationListLocation) -> Unit,
  modifier: Modifier = Modifier
) {
  val colors = rememberMollyLightColors()
  val showOverflow = remember { mutableStateOf(false) }

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
            onOverflowClick = { showOverflow.value = true }
          )
        )

        if (!LocalResources.current.rememberIsSplitPane()) {
          Spacer(modifier = Modifier.navigationBarsPadding())
        }
      }
    }

    if (showOverflow.value) {
      Box(modifier = Modifier.align(Alignment.BottomEnd)) {
        MainLightOverflowMenu(
          toolbarState = toolbarState,
          toolbarCallback = toolbarCallback,
          floatingActionButtonsCallback = floatingActionButtonsCallback,
          onDismiss = { showOverflow.value = false }
        )
      }
    }
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
private fun MainLightOverflowMenu(
  toolbarState: MainToolbarState,
  toolbarCallback: MainToolbarCallback,
  floatingActionButtonsCallback: MainFloatingActionButtonsCallback,
  onDismiss: () -> Unit
) {
  val resources = LocalResources.current
  val actions = mutableListOf<LightPanelAction>()

  actions.add(
    LightPanelAction(
      label = stringResource(R.string.conversation_list_fragment__open_camera_description).uppercase(Locale.getDefault()),
      onSelected = {
        onDismiss()
        floatingActionButtonsCallback.onCameraClick(MainNavigationListLocation.CHATS)
      }
    )
  )

  if (toolbarState.proxyState != MainToolbarState.ProxyState.NONE) {
    actions.add(
      LightPanelAction(
        label = stringResource(R.string.MainToolbar__proxy_content_description).uppercase(Locale.getDefault()),
        onSelected = {
          onDismiss()
          toolbarCallback.onProxyClick()
        }
      )
    )
  }

  when (toolbarState.destination) {
    MainNavigationListLocation.ARCHIVE -> Unit
    MainNavigationListLocation.CHATS -> {
      if (toolbarState.hasPassphrase) {
        actions.add(
          LightPanelAction(
            label = stringResource(R.string.text_secure_normal__menu_clear_passphrase).uppercase(Locale.getDefault()),
            onSelected = {
              onDismiss()
              toolbarCallback.onClearPassphraseClick()
            }
          )
        )
      }
      actions.add(
        LightPanelAction(
          label = stringResource(R.string.text_secure_normal__menu_new_group).uppercase(Locale.getDefault()),
          onSelected = {
            onDismiss()
            toolbarCallback.onNewGroupClick()
          }
        )
      )
      actions.add(
        LightPanelAction(
          label = stringResource(R.string.text_secure_normal__mark_all_as_read).uppercase(Locale.getDefault()),
          onSelected = {
            onDismiss()
            toolbarCallback.onMarkReadClick()
          }
        )
      )
      if (toolbarState.chatFilter == ConversationFilter.OFF) {
        actions.add(
          LightPanelAction(
            label = stringResource(R.string.text_secure_normal__filter_unread_chats).uppercase(Locale.getDefault()),
            onSelected = {
              onDismiss()
              toolbarCallback.onFilterUnreadChatsClick()
            }
          )
        )
      } else {
        actions.add(
          LightPanelAction(
            label = stringResource(R.string.ConversationListFragment__clear_filter).uppercase(Locale.getDefault()),
            onSelected = {
              onDismiss()
              toolbarCallback.onClearUnreadChatsFilterClick()
            }
          )
        )
      }
      if (SignalStore.labs.starredMessages) {
        actions.add(
          LightPanelAction(
            label = stringResource(R.string.text_secure_normal__starred_messages).uppercase(Locale.getDefault()),
            onSelected = {
              onDismiss()
              toolbarCallback.onStarredMessagesClick()
            }
          )
        )
      }
      actions.add(
        LightPanelAction(
          label = stringResource(R.string.text_secure_normal__menu_settings).uppercase(Locale.getDefault()),
          onSelected = {
            onDismiss()
            toolbarCallback.onSettingsClick()
          }
        )
      )
      actions.add(
        LightPanelAction(
          label = stringResource(R.string.NotificationProfilesFragment__notification_profiles).uppercase(Locale.getDefault()),
          onSelected = {
            onDismiss()
            toolbarCallback.onNotificationProfileClick()
          }
        )
      )
    }
    MainNavigationListLocation.CALLS -> {
      actions.add(
        LightPanelAction(
          label = stringResource(R.string.CallLogFragment__clear_call_history).uppercase(Locale.getDefault()),
          onSelected = {
            onDismiss()
            toolbarCallback.onClearCallHistoryClick()
          }
        )
      )
      if (toolbarState.callFilter == CallLogFilter.ALL) {
        actions.add(
          LightPanelAction(
            label = stringResource(R.string.CallLogFragment__filter_missed_calls).uppercase(Locale.getDefault()),
            onSelected = {
              onDismiss()
              toolbarCallback.onFilterMissedCallsClick()
            }
          )
        )
      } else {
        actions.add(
          LightPanelAction(
            label = stringResource(R.string.CallLogFragment__clear_filter).uppercase(Locale.getDefault()),
            onSelected = {
              onDismiss()
              toolbarCallback.onClearCallFilterClick()
            }
          )
        )
      }
      actions.add(
        LightPanelAction(
          label = stringResource(R.string.text_secure_normal__menu_settings).uppercase(Locale.getDefault()),
          onSelected = {
            onDismiss()
            toolbarCallback.onSettingsClick()
          }
        )
      )
      actions.add(
        LightPanelAction(
          label = stringResource(R.string.NotificationProfilesFragment__notification_profiles).uppercase(Locale.getDefault()),
          onSelected = {
            onDismiss()
            toolbarCallback.onNotificationProfileClick()
          }
        )
      )
    }
    MainNavigationListLocation.STORIES -> {
      actions.add(
        LightPanelAction(
          label = stringResource(R.string.StoriesLandingFragment__story_privacy).uppercase(Locale.getDefault()),
          onSelected = {
            onDismiss()
            toolbarCallback.onStoryPrivacyClick()
          }
        )
      )
    }
  }

  MollyLightTheme {
    LightActionPanel(
      actions = actions,
      onDismiss = onDismiss
    )
  }
}
