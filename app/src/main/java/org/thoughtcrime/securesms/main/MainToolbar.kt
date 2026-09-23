/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.main

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.DropdownMenus
import org.signal.core.ui.compose.IconButtons
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.calls.log.CallLogFilter
import org.thoughtcrime.securesms.components.compose.ActionModeTopBar
import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.light.LightSearchField
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.light.rememberLightTintedPainter
import org.thoughtcrime.securesms.recipients.Recipient

/** The labs search-filter glyph's slot, matching the field's own leading and clear slots. */
private const val SEARCH_FILTER_SLOT_UNITS = 1f

/** The "a filter is applied" dot, sized off the glyph rather than in absolute dp. */
private const val SEARCH_FILTER_DOT_UNITS = 0.25f

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

        // Swapped in outright rather than revealed. The circular reveal this replaced was a
        // Material motion idiom, and nothing else in the Light port animates a surface into
        // existence -- the tabs, the action panel and every list swap directly. It also read badly
        // here specifically: the field is a full-bleed black row with a white rule under it, so a
        // circular clip wiped that rule on in an arc, which is the one part of the control the eye
        // actually tracks.
        if (state.mode == MainToolbarMode.SEARCH) {
          SearchToolbar(
            state = state,
            callback = callback,
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
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

/**
 * The search field, in the Light idiom.
 *
 * This replaced a Material 3 filled `TextField` -- a grey rounded capsule with a floating
 * placeholder -- which was the last piece of Material chrome on the app's main screen and looked it:
 * the screen behind it has no top bar at all any more, so the capsule appeared out of nowhere,
 * sitting on a surface colour nothing else on the screen used.
 *
 * It is the *same* control as the contact picker's, [LightSearchField], rather than a second field
 * that resembles it -- see that file for the shape and for why an ordinary Compose text field is all
 * it takes to raise the LP3's keyboard.
 *
 * Two things are configured differently from the picker's:
 *
 * - **The leading glyph is BACK, and it is tappable.** In the picker the field filters a list that
 *   is on screen either way, so its leading slot is a decorative magnifier. Here the field *is* the
 *   search mode, and the Light bottom bar is hidden while that mode is up (`MainActivity` only shows
 *   it in `FULL`), so without this the only way out would be the system back gesture.
 * - **The labs search filter keeps its entry point.** `SignalStore.labs.betterSearch` is off by
 *   default and `SearchFilterBottomSheet` is deliberately untouched, so the glyph is drawn through
 *   [rememberLightTintedPainter] rather than left as a raw Signal drawable -- `symbol_filter_24` is
 *   authored with a dark fill, which on the Light theme's true-black background is invisible.
 */
@Composable
private fun SearchToolbar(
  state: MainToolbarState,
  callback: MainToolbarCallback,
  modifier: Modifier = Modifier
) {
  val focusRequester = remember { FocusRequester() }

  MollyLightTheme {
    LightSearchField(
      // Read here and not in MainToolbar's body on purpose: the query changes on every keystroke,
      // and pulling it up would re-invoke the Crossfade's content per character.
      query = state.searchQuery,
      onQueryChange = callback::onSearchQueryUpdated,
      hint = stringResource(state.searchHint),
      focusRequester = focusRequester,
      onBack = callback::onCloseSearchClick,
      trailing = if (SignalStore.labs.betterSearch) {
        { SearchFilterGlyph(hasActiveFilter = state.hasActiveSearchFilter, onClick = callback::onSearchFilterClick) }
      } else {
        null
      },
      modifier = modifier
    )
  }

  // Unconditional, because this composable only exists while the mode *is* SEARCH -- entering
  // search is what creates it. That is also what raises the keyboard: the LP3's IME is the system
  // default, so focusing an ordinary Compose text field brings it up with no further help.
  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }
}

/**
 * The labs search-filter affordance: the glyph, with a dot over it while a filter is applied.
 *
 * The dot is the Light stand-in for Material's `colorPrimary` badge -- there is no accent colour in
 * this design, so "on" is said with the content colour and with size instead.
 */
@Composable
private fun SearchFilterGlyph(
  hasActiveFilter: Boolean,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .size(SEARCH_FILTER_SLOT_UNITS.gridUnitsAsDp())
      .lightClickable(onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    Image(
      painter = rememberLightTintedPainter(R.drawable.symbol_filter_24),
      contentDescription = stringResource(R.string.MainToolbar__search_filter_content_description),
      modifier = Modifier.fillMaxSize()
    )

    if (hasActiveFilter) {
      Box(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .size(SEARCH_FILTER_DOT_UNITS.gridUnitsAsDp())
          .background(color = LightThemeTokens.colors.content, shape = CircleShape)
      )
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


@Composable


@Composable


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
