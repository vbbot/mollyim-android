/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.IconButtons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.TextFields
import org.signal.core.ui.compose.circularReveal
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.calls.log.CallLogFilter
import org.thoughtcrime.securesms.components.compose.ActionModeTopBar
import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.recipients.Recipient

/** Search is opened from the bottom bar now, so its circular reveal grows from the centre. */
private val SEARCH_REVEAL_ORIGIN = Offset(0.5f, 0.5f)

interface MainToolbarCallback {
  fun onNewGroupClick()
  fun onClearPassphraseClick()
  fun onMarkReadClick()
  fun onFilterUnreadChatsClick()
  fun onClearUnreadChatsFilterClick()
  fun onSettingsClick()
  fun onNotificationProfileClick()
  fun onProxyClick()
  fun onSearchClick()
  fun onClearCallHistoryClick()
  fun onFilterMissedCallsClick()
  fun onClearCallFilterClick()
  fun onStoryPrivacyClick()
  fun onStoryArchiveClick()
  fun onCloseSearchClick()
  fun onCloseArchiveClick()
  fun onCloseActionModeClick()
  fun onSearchQueryUpdated(query: String)
  fun onSearchFilterClick()
  fun onStarredMessagesClick()
  fun onNotificationProfileTooltipDismissed()

  object Empty : MainToolbarCallback {
    override fun onNewGroupClick() = Unit
    override fun onClearPassphraseClick() = Unit
    override fun onMarkReadClick() = Unit
    override fun onFilterUnreadChatsClick() = Unit
    override fun onClearUnreadChatsFilterClick() = Unit
    override fun onSettingsClick() = Unit
    override fun onNotificationProfileClick() = Unit
    override fun onProxyClick() = Unit
    override fun onSearchClick() = Unit
    override fun onClearCallHistoryClick() = Unit
    override fun onFilterMissedCallsClick() = Unit
    override fun onClearCallFilterClick() = Unit
    override fun onStoryPrivacyClick() = Unit
    override fun onStoryArchiveClick() = Unit
    override fun onCloseSearchClick() = Unit
    override fun onCloseArchiveClick() = Unit
    override fun onCloseActionModeClick() = Unit
    override fun onSearchQueryUpdated(query: String) = Unit
    override fun onSearchFilterClick() = Unit
    override fun onStarredMessagesClick() = Unit
    override fun onNotificationProfileTooltipDismissed() = Unit
  }
}

enum class MainToolbarMode(val crossFadeKey: CrossFadeKey) {
  ACTION_MODE(CrossFadeKey.ACTION_MODE),
  FULL(CrossFadeKey.FULL),
  BASIC(CrossFadeKey.BASIC),
  SEARCH(CrossFadeKey.FULL);

  /**
   * Since FULL and SEARCH share the same cross-fade target, we use a shared
   * cross-fade key between them.
   */
  enum class CrossFadeKey {
    ACTION_MODE,
    FULL,
    BASIC
  }
}

data class MainToolbarState(
  val toolbarColor: Color? = null,
  val self: Recipient = Recipient.UNKNOWN,
  val mode: MainToolbarMode = MainToolbarMode.FULL,
  val destination: MainNavigationListLocation = MainNavigationListLocation.CHATS,
  val chatFilter: ConversationFilter = ConversationFilter.OFF,
  val callFilter: CallLogFilter = CallLogFilter.ALL,
  val hasUnreadPayments: Boolean = false,
  val hasFailedBackups: Boolean = false,
  val isOutOfRemoteStorageSpace: Boolean = false,
  val hasEnabledNotificationProfile: Boolean = false,
  val showNotificationProfilesTooltip: Boolean = false,
  val hasPassphrase: Boolean = false,
  val proxyState: ProxyState = ProxyState.NONE,
  @StringRes val searchHint: Int = R.string.SearchToolbar_search,
  val searchQuery: String = "",
  val hasActiveSearchFilter: Boolean = false,
  val actionModeCount: Int = 0
) {
  enum class ProxyState(@DrawableRes val icon: Int) {
    NONE(-1),
    CONNECTING(R.drawable.ic_proxy_connecting_24),
    CONNECTED(R.drawable.ic_proxy_connected_24),
    FAILED(R.drawable.ic_proxy_failed_24)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainToolbar(
  state: MainToolbarState,
  callback: MainToolbarCallback
) {
  Crossfade(
    targetState = state.mode.crossFadeKey
  ) { targetState ->
    when (targetState) {
      MainToolbarMode.CrossFadeKey.FULL -> Box {
        // No persistent top bar. The Light chat list is a "list home" screen: it draws its own
        // 2 grid unit spacer and keeps every affordance in the bottom bar, so all that is left to
        // reserve here is the status bar inset the removed TopAppBar used to consume for us.
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
        )

        AnimatedVisibility(
          visible = state.mode == MainToolbarMode.SEARCH,
          enter = EnterTransition.None,
          exit = ExitTransition.None
        ) {
          val visibility = transition.animateFloat(
            transitionSpec = { tween(durationMillis = 400, easing = LinearOutSlowInEasing) },
            label = "Visibility"
          ) { state ->
            if (state == EnterExitState.Visible) 1f else 0f
          }

          SearchToolbar(
            state = state,
            callback = callback,
            modifier = Modifier
              .windowInsetsPadding(WindowInsets.statusBars)
              // Search is opened from the bottom bar now, so the reveal grows from the centre
              // rather than from the position of a top bar button.
              .circularReveal(visibility, SEARCH_REVEAL_ORIGIN)
          )
        }
      }

      MainToolbarMode.CrossFadeKey.BASIC -> ArchiveToolbar(state, callback)
      MainToolbarMode.CrossFadeKey.ACTION_MODE -> ActionModeToolbar(state, callback)
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionModeToolbar(
  state: MainToolbarState,
  callback: MainToolbarCallback
) {
  ActionModeTopBar(
    title = pluralStringResource(R.plurals.ConversationListFragment_s_selected, state.actionModeCount, state.actionModeCount),
    onCloseClick = callback::onCloseActionModeClick,
    toolbarColor = state.toolbarColor
  )
}

@Composable
private fun SearchToolbar(
  state: MainToolbarState,
  callback: MainToolbarCallback,
  modifier: Modifier = Modifier
) {
  val focusRequester = remember { FocusRequester() }

  CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    TextFields.TextField(
      value = state.searchQuery,
      onValueChange = callback::onSearchQueryUpdated,
      leadingIcon = {
        IconButtons.IconButton(
          onClick = callback::onCloseSearchClick
        ) {
          Icon(
            imageVector = SignalIcons.ArrowStart.imageVector,
            contentDescription = stringResource(R.string.MainToolbar__close_search_content_description)
          )
        }
      },
      trailingIcon = {
        Row {
          if (SignalStore.labs.betterSearch) {
            Box(contentAlignment = Alignment.TopEnd) {
              IconButtons.IconButton(
                onClick = callback::onSearchFilterClick
              ) {
                Icon(
                  imageVector = ImageVector.vectorResource(R.drawable.symbol_filter_24),
                  contentDescription = stringResource(R.string.MainToolbar__search_filter_content_description)
                )
              }
              if (state.hasActiveSearchFilter) {
                Box(
                  modifier = Modifier
                    .padding(top = 8.dp, end = 8.dp)
                    .size(8.dp)
                    .background(color = MaterialTheme.colorScheme.primary, shape = CircleShape)
                )
              }
            }
          }
          if (state.searchQuery.isNotEmpty()) {
            IconButtons.IconButton(
              onClick = {
                callback.onSearchQueryUpdated("")
              }
            ) {
              Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_x_20),
                contentDescription = stringResource(R.string.MainToolbar__clear_search_content_description)
              )
            }
          }
        }
      },
      contentPadding = PaddingValues(0.dp),
      colors = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        errorContainerColor = MaterialTheme.colorScheme.surfaceVariant
      ),
      textStyle = MaterialTheme.typography.bodyLarge,
      shape = RoundedCornerShape(50),
      singleLine = true,
      placeholder = {
        Text(text = stringResource(state.searchHint))
      },
      modifier = modifier
        .background(color = state.toolbarColor ?: MaterialTheme.colorScheme.surface)
        .height(dimensionResource(R.dimen.signal_m3_toolbar_height))
        .padding(horizontal = 16.dp, vertical = 10.dp)
        .fillMaxWidth()
        .focusRequester(focusRequester)
    )
  }

  LaunchedEffect(state.mode) {
    if (state.mode == MainToolbarMode.SEARCH) {
      focusRequester.requestFocus()
    } else {
      focusRequester.freeFocus()
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchiveToolbar(
  state: MainToolbarState,
  callback: MainToolbarCallback
) {
  TopAppBar(
    colors = TopAppBarDefaults.topAppBarColors(
      containerColor = state.toolbarColor ?: MaterialTheme.colorScheme.surface
    ),
    navigationIcon = {
      IconButtons.IconButton(onClick = {
        callback.onCloseArchiveClick()
      }) {
        Icon(
          imageVector = SignalIcons.ArrowStart.imageVector,
          contentDescription = stringResource(R.string.CallScreenTopBar__go_back)
        )
      }
    },
    title = {
      Text(text = stringResource(R.string.AndroidManifest_archived_conversations))
    }
  )
}

@Composable
internal fun StoryDropDownItems(callback: MainToolbarCallback, onOptionSelected: () -> Unit) {
  DropdownMenus.Item(
    text = {
      Text(
        text = stringResource(R.string.StoriesLandingFragment__story_privacy)
      )
    },
    onClick = {
      callback.onStoryPrivacyClick()
      onOptionSelected()
    }
  )
}

@Composable
internal fun CallDropdownItems(callFilter: CallLogFilter, callback: MainToolbarCallback, onOptionSelected: () -> Unit) {
  DropdownMenus.Item(
    text = {
      Text(
        text = stringResource(R.string.CallLogFragment__clear_call_history)
      )
    },
    onClick = {
      callback.onClearCallHistoryClick()
      onOptionSelected()
    }
  )

  if (callFilter == CallLogFilter.ALL) {
    DropdownMenus.Item(
      text = {
        Text(
          text = stringResource(R.string.CallLogFragment__filter_missed_calls)
        )
      },
      onClick = {
        callback.onFilterMissedCallsClick()
        onOptionSelected()
      }
    )
  } else {
    DropdownMenus.Item(
      text = {
        Text(
          text = stringResource(R.string.CallLogFragment__clear_filter)
        )
      },
      onClick = {
        callback.onClearCallFilterClick()
        onOptionSelected()
      }
    )
  }

  DropdownMenus.Item(
    text = {
      Text(
        text = stringResource(R.string.text_secure_normal__menu_settings)
      )
    },
    onClick = {
      callback.onSettingsClick()
      onOptionSelected()
    }
  )

  DropdownMenus.Item(
    text = {
      Text(
        text = stringResource(R.string.ConversationListFragment__notification_profile)
      )
    },
    onClick = {
      callback.onNotificationProfileClick()
      onOptionSelected()
    }
  )
}

@Composable
internal fun ChatDropdownItems(state: MainToolbarState, callback: MainToolbarCallback, onOptionSelected: () -> Unit) {
  if (state.hasPassphrase) {
    DropdownMenus.Item(
      text = {
        Text(
          text = stringResource(R.string.text_secure_normal__menu_clear_passphrase)
        )
      },
      onClick = {
        callback.onClearPassphraseClick()
        onOptionSelected()
      }
    )
  }

  DropdownMenus.Item(
    text = {
      Text(
        text = stringResource(R.string.text_secure_normal__menu_new_group)
      )
    },
    onClick = {
      callback.onNewGroupClick()
      onOptionSelected()
    }
  )

  DropdownMenus.Item(
    text = {
      Text(
        text = stringResource(R.string.text_secure_normal__mark_all_as_read)
      )
    },
    onClick = {
      callback.onMarkReadClick()
      onOptionSelected()
    }
  )

  if (state.chatFilter == ConversationFilter.OFF) {
    DropdownMenus.Item(
      text = {
        Text(
          text = stringResource(R.string.text_secure_normal__filter_unread_chats)
        )
      },
      onClick = {
        callback.onFilterUnreadChatsClick()
        onOptionSelected()
      }
    )
  } else {
    DropdownMenus.Item(
      text = {
        Text(
          text = stringResource(R.string.text_secure_normal__clear_unread_filter)
        )
      },
      onClick = {
        callback.onClearUnreadChatsFilterClick()
        onOptionSelected()
      }
    )
  }

  if (SignalStore.labs.starredMessages) {
    DropdownMenus.Item(
      text = {
        Text(
          text = stringResource(R.string.text_secure_normal__starred_messages)
        )
      },
      onClick = {
        callback.onStarredMessagesClick()
        onOptionSelected()
      }
    )
  }

  DropdownMenus.Item(
    text = {
      Text(
        text = stringResource(R.string.text_secure_normal__menu_settings)
      )
    },
    onClick = {
      callback.onSettingsClick()
      onOptionSelected()
    }
  )

  DropdownMenus.Item(
    text = {
      Text(
        text = stringResource(R.string.ConversationListFragment__notification_profile)
      )
    },
    onClick = {
      callback.onNotificationProfileClick()
      onOptionSelected()
    }
  )
}

@DayNightPreviews
@Composable
private fun FullMainToolbarPreview() {
  Previews.Preview {
    var mode by remember { mutableStateOf(MainToolbarMode.FULL) }

    MainToolbar(
      state = MainToolbarState(
        self = Recipient(isResolving = false),
        mode = mode,
        destination = MainNavigationListLocation.CHATS,
        hasEnabledNotificationProfile = true,
        proxyState = MainToolbarState.ProxyState.CONNECTED,
        hasFailedBackups = true,
        isOutOfRemoteStorageSpace = false
      ),
      callback = object : MainToolbarCallback by MainToolbarCallback.Empty {
        override fun onSearchClick() {
          mode = MainToolbarMode.SEARCH
        }

        override fun onCloseSearchClick() {
          mode = MainToolbarMode.FULL
        }
      }
    )
  }
}

@DayNightPreviews
@Composable
private fun SearchToolbarPreview() {
  Previews.Preview {
    SearchToolbar(
      state = MainToolbarState(
        self = Recipient(isResolving = false, isSelf = true),
        searchQuery = "Test query"
      ),
      callback = MainToolbarCallback.Empty
    )
  }
}

@DayNightPreviews
@Composable
private fun ArchiveToolbarPreview() {
  Previews.Preview {
    ArchiveToolbar(
      state = MainToolbarState(
        self = Recipient(isResolving = false)
      ),
      callback = MainToolbarCallback.Empty
    )
  }
}
