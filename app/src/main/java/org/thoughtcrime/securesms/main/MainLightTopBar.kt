/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.calls.log.CallLogFilter
import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.light.LightActionPanelDialog
import org.thoughtcrime.securesms.light.LightPanelAction
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.light.rememberMollyLightColors

/**
 * The Light Phone top bar over the list destinations: search on the left, the app's name in the
 * centre, and the overflow panel on the right.
 *
 * Search and overflow used to be the last two slots of [MainLightBottomBar]. They moved up here so
 * the bottom bar carries only destinations and the one contextual create action. Like the bottom
 * bar, it is drawn only in [MainToolbarMode.FULL]; search and multi-select replace it with their own
 * header (see [MainToolbar]).
 *
 * The top bar *replaces* the bare 2 grid unit spacer the list screens used to draw, as in the
 * reference client's chat list -- it does not sit on top of it.
 */
@Composable
fun MainLightTopBar(
  toolbarState: MainToolbarState,
  toolbarCallback: MainToolbarCallback,
  floatingActionButtonsCallback: MainFloatingActionButtonsCallback,
  modifier: Modifier = Modifier
) {
  var overflowOpen by remember { mutableStateOf(false) }

  MollyLightTheme {
    LightTopBar(
      leftButton = LightBarButton.LightIcon(
        icon = LightIcons.SEARCH,
        onClick = toolbarCallback::onSearchClick,
        contentDescription = stringResource(R.string.conversation_list_search_description)
      ),
      center = LightTopBarCenter.Text(stringResource(R.string.app_name)),
      rightButton = LightBarButton.LightIcon(
        icon = LightIcons.ELLIPSES,
        onClick = { overflowOpen = true },
        contentDescription = stringResource(R.string.MainToolbar__more_options_content_description)
      ),
      // The SDK bar paints no background. Painted before the caller's status bar inset so the strip
      // behind the status bar is true black too.
      modifier = Modifier
        .fillMaxWidth()
        .background(rememberMollyLightColors().background)
        .then(modifier)
    )
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
