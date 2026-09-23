/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversationlist.light

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration
import org.thoughtcrime.securesms.contacts.paged.ContactSearchData
import org.thoughtcrime.securesms.contacts.paged.ContactSearchState
import org.thoughtcrime.securesms.contacts.paged.ContactSearchViewModel
import org.thoughtcrime.securesms.contacts.paged.light.LightContactItem
import org.thoughtcrime.securesms.contacts.paged.light.LightContactListScreen
import org.thoughtcrime.securesms.contacts.paged.light.LightContactState
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.util.ViewUtil
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Rect as ComposeRect

/**
 * The chat list's **search results**, rendered with The Light Phone SDK's Compose components.
 *
 * This replaces the `@id/list` RecyclerView plus `ConversationListSearchAdapter`, which drew each
 * hit as a full `ConversationListItem` -- avatar, sender, snippet, delivery state, unread pill. That
 * row is the one the Light chat list had already dropped everywhere else, so with the list behind it
 * rebuilt, search was the last place in the app where the Material chat row survived.
 *
 * **It is deliberately not a new list.** The rows, the row geometry and the projection are the
 * contact picker's, from milestone 4: `ContactSearchViewModel` is the *same* view model class both
 * screens already used, `ContactSearchData` the same row types, so this view binds
 * [LightContactListScreen] and [LightContactItem] rather than growing a parallel set. What is new
 * here is only what search has and the picker does not -- message hits (the one two-line row in the
 * port; see [LightContactItem.Kind.MESSAGE_HIT]) and a different set of callbacks to route taps to.
 *
 * **Nothing below the rendering is touched**: `SearchRepository`, the FTS layer, the paged data
 * source, `mapSearchStateToConfiguration` and every click handler in `ConversationListFragment` are
 * exactly as they were, which is what keeps a tap on each row kind doing what it did before.
 *
 * The Light theme is scoped to this view via [MollyLightTheme], because Molly's `SignalTheme` and
 * the SDK's `LightTheme` both install a Material 3 `ColorScheme` and must not be nested.
 */
class LightSearchResultsView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs) {

  /**
   * What a tap on each row kind means. Deliberately the same four questions
   * `ConversationListSearchAdapter.ConversationListSearchClickCallbacks` asked, so that
   * `ConversationListFragment` answers them with the code it already had.
   */
  interface Callback {
    fun onThreadClicked(thread: ContactSearchData.Thread)

    /**
     * Long press on a result. Compose rows have no [android.view.View] of their own, so the row's
     * bounds *in this view's coordinate space* come back with it and the caller parks an anchor
     * there -- the same contract as [LightConversationListView.Callback.onConversationLongClick].
     */
    fun onThreadLongClicked(thread: ContactSearchData.Thread, bounds: Rect)
    fun onMessageClicked(message: ContactSearchData.Message)
    fun onContactClicked(contact: ContactSearchData.KnownRecipient)
    fun onGroupWithMembersClicked(groupWithMembers: ContactSearchData.GroupWithMembers)
    fun onExpandClicked(expand: ContactSearchData.Expand)
    fun onClearFilterClicked()
  }

  var callback: Callback? = null

  /**
   * State-backed, because setting it in [bind] is what starts the composition doing any work at all
   * -- the same trick `LightContactSearchView` uses.
   */
  private var viewModel: ContactSearchViewModel? by mutableStateOf(null)

  private var mapStateToConfiguration: ((ContactSearchState) -> ContactSearchConfiguration)? = null

  private var state by mutableStateOf(LightContactState())

  /**
   * Shown only while there is nothing at all to draw, which for this screen means "the query has
   * been typed but no rows have come back yet".
   *
   * A search that genuinely matches nothing does *not* land here: Signal's configuration answers it
   * with an `Empty` row, which [LightContactItem] renders as a centred line of `Copy` inside the
   * list. This is the Light replacement for the Material `CircularProgressIndicator` that used to
   * be thrown over the top of the list -- a spinner being about the least Light object available,
   * and one drawn in `?attr/colorPrimary` at that.
   */
  private var searchInProgress by mutableStateOf(false)

  private val listState = LazyListState()

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  }

  /**
   * Configures and activates the list. Must be called exactly once, with the fragment's existing
   * search view model and its existing `mapSearchStateToConfiguration`.
   */
  fun bind(
    viewModel: ContactSearchViewModel,
    mapStateToConfiguration: (ContactSearchState) -> ContactSearchConfiguration
  ) {
    check(this.viewModel == null) { "LightSearchResultsView.bind() may only be called once" }

    this.mapStateToConfiguration = mapStateToConfiguration

    // Last, deliberately: this is the state write that lets Content() past its own null check.
    this.viewModel = viewModel
  }

  @Composable
  override fun Content() {
    val vm = viewModel ?: return
    val view = LocalView.current

    // The projection runs off the composition, in the collector that produced the data, so a
    // composable only ever sees plain strings and booleans. `data` rather than `mappingModels`:
    // that layer exists only to feed legacy view holders, and it drops the positional nulls the
    // paging controller needs.
    LaunchedEffect(vm) {
      launch {
        vm.data.collect { data ->
          state = LightContactItem.map(
            context = context,
            data = data,
            selection = emptySet()
          )
        }
      }

      launch {
        val mapper = mapStateToConfiguration
        if (mapper != null) {
          vm.configurationState.collect { configState -> vm.setConfiguration(mapper(configState)) }
        }
      }

      launch {
        vm.searchInProgress.collect { searchInProgress = it }
      }

      launch {
        // The position is ignored, as it was in the Material path: every caller asks for the top.
        vm.scrollRequests.collect { listState.scrollToItem(0) }
      }
    }

    // Scrolling the results puts the keyboard away. The LP3's IME is an ordinary system IME and
    // covers half the screen, so a list you cannot see is a list you cannot pick from.
    LaunchedEffect(Unit) {
      snapshotFlow { listState.isScrollInProgress }
        .filter { it }
        .collect { ViewUtil.hideKeyboard(context, view) }
    }

    MollyLightTheme {
      LightContactListScreen(
        state = state,
        statusText = if (searchInProgress) stringResource(R.string.LightSearch__searching) else "",
        listState = listState,
        onClick = ::onRowClicked,
        onLongClick = ::onRowLongClicked,
        onDataNeededAtSourceIndex = { index -> vm.controller.value?.onDataNeededAroundIndex(index) },
        // Keeps the collapsing filter header above us driven by this list's scrolling, the way the
        // RecyclerView used to.
        modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection())
      )
    }
  }

  /**
   * Routes a tap back into `ConversationListFragment`, which still owns what every row kind means.
   *
   * Note the `when`-on-type rather than a cast: these rows share one item class across several
   * [ContactSearchData] subtypes, and an unchecked cast here is exactly the shape of bug that has
   * already shipped a `ClassCastException` on this screen's old layouts.
   */
  private fun onRowClicked(item: LightContactItem) {
    val calls = callback ?: return

    if (item.action == LightContactItem.Action.CLEAR_CHAT_FILTER) {
      calls.onClearFilterClicked()
      return
    }

    when (val data = item.data) {
      is ContactSearchData.Thread -> calls.onThreadClicked(data)
      is ContactSearchData.Message -> calls.onMessageClicked(data)
      is ContactSearchData.KnownRecipient -> calls.onContactClicked(data)
      is ContactSearchData.GroupWithMembers -> calls.onGroupWithMembersClicked(data)
      is ContactSearchData.Expand -> calls.onExpandClicked(data)
      else -> Unit
    }
  }

  /**
   * Only a conversation has a long-press menu, exactly as before: the Material adapter registered a
   * long-click listener on the thread row and on no other kind.
   */
  private fun onRowLongClicked(item: LightContactItem, bounds: ComposeRect) {
    val calls = callback ?: return
    val thread = item.data as? ContactSearchData.Thread ?: return

    calls.onThreadLongClicked(
      thread,
      Rect(
        left + bounds.left.roundToInt(),
        top + bounds.top.roundToInt(),
        left + bounds.right.roundToInt(),
        top + bounds.bottom.roundToInt()
      )
    )
  }
}
