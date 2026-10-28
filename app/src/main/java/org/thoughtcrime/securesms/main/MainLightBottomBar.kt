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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightBottomBarItem
import com.thelightphone.sdk.ui.LightIcons
import org.signal.core.ui.rememberIsSplitPane
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.light.rememberLightTintedPainter
import org.thoughtcrime.securesms.light.rememberMollyLightColors

/**
 * The Light Phone bottom bar: chats, calls, and the destination's create action. Search and the
 * overflow panel live in [MainLightTopBar].
 */
@Composable
fun MainLightBottomBar(
  toolbarState: MainToolbarState,
  floatingActionButtonsCallback: MainFloatingActionButtonsCallback,
  onDestinationSelected: (MainNavigationListLocation) -> Unit,
  modifier: Modifier = Modifier
) {
  val colors = rememberMollyLightColors()

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
            floatingActionButtonsCallback = floatingActionButtonsCallback,
            onDestinationSelected = onDestinationSelected
          )
        )

        if (!LocalResources.current.rememberIsSplitPane()) {
          Spacer(modifier = Modifier.navigationBarsPadding())
        }
      }
    }
  }
}

@Composable
private fun bottomBarItems(
  toolbarState: MainToolbarState,
  floatingActionButtonsCallback: MainFloatingActionButtonsCallback,
  onDestinationSelected: (MainNavigationListLocation) -> Unit
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

  return listOf(chats, calls, create)
}
