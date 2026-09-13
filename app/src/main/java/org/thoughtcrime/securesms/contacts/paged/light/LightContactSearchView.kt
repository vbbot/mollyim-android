/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.paged.light

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.ContactSelectionListModels
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.paged.ContactSearchAdapter
import org.thoughtcrime.securesms.contacts.paged.ContactSearchCallbacks
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration
import org.thoughtcrime.securesms.contacts.paged.ContactSearchData
import org.thoughtcrime.securesms.contacts.paged.ContactSearchState
import org.thoughtcrime.securesms.contacts.paged.ContactSearchViewModel
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.util.ViewUtil

/**
 * The contact picker's list, rendered with The Light Phone SDK's Compose components.
 *
 * This is the Light counterpart of `contacts/paged/ContactSearchView`, and it is a drop-in for it:
 * `contact_selection_list_fragment.xml` names this instead, and `ContactSelectionListFragment` binds
 * it with the same view model, the same `mapStateToConfiguration`, and the same callback objects it
 * already had. Everything below the rendering is untouched -- `ContactSearchRepository`, the paged
 * data source, the configuration builder, the selection set, the selection limits, and the
 * fragment's own `ListClickListener`, which is still the single place that decides what a tap on a
 * contact means.
 *
 * `ContactSearchView` itself is left in place and still serves the screens that are not part of this
 * milestone (the story audience sheets), so nothing outside the picker changes.
 *
 * The Light theme is scoped to this view only, via [MollyLightTheme]: Molly's `SignalTheme` and the
 * SDK's `LightTheme` both install a Material 3 `ColorScheme`, so they must not be nested; everything
 * outside this view stays on Molly's theme.
 */
class LightContactSearchView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs) {

  /**
   * State-backed, because setting it in [bind] is what starts the composition doing any work at all
   * -- the same trick `ContactSearchView` uses. Everything else can be a plain field, because
   * nothing reads it until the view model is set.
   */
  private var viewModel: ContactSearchViewModel? by mutableStateOf(null)

  private var displayCheckBox: Boolean = false
  private var mapStateToConfiguration: ((ContactSearchState) -> ContactSearchConfiguration)? = null
  private var clickCallbacks: ContactSearchAdapter.ClickCallbacks? = null
  private var longClickCallbacks: ContactSearchAdapter.LongClickCallbacks? = null
  private var arbitraryCallback: ContactSelectionListModels.Callback? = null
  private var callbacks: ContactSearchCallbacks = ContactSearchCallbacks.Simple()

  private var state by mutableStateOf(LightContactState())

  /**
   * The whole-screen state, shown only while the list is genuinely empty -- which in this screen
   * means "still loading", because a search that matches nothing is answered with an `Empty` *row*
   * (see [LightContactListScreen]). `ContactSelectionListFragment` swaps it for "No contacts" once
   * the first list has been committed, which is what it always did to the (never laid out) empty
   * TextView in `contact_selection_list_fragment.xml`.
   */
  var statusText: String by mutableStateOf(context.getString(R.string.contact_selection_group_activity__finding_contacts))

  private val listState = LazyListState()

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  }

  /**
   * Configures and activates the list. Mirrors `ContactSearchView.bind`, minus the parameters that
   * only the Material rows had a use for: there is no fragment manager (the story dialogs it existed
   * for cannot be reached from this screen), no `additionalEntries` (the extra rows are Light rows
   * now, resolved in [LightContactItem]), and no call-button callbacks (the Light row has no buttons
   * in it -- a long press opens the action panel, which is where the call actions live).
   *
   * @param arbitraryCallback what the extra rows do. Molly's own `ContactSelectionListModels.Callback`,
   *   unchanged, so a row added upstream arrives here without this file changing.
   */
  fun bind(
    viewModel: ContactSearchViewModel,
    displayOptions: ContactSearchAdapter.DisplayOptions,
    mapStateToConfiguration: (ContactSearchState) -> ContactSearchConfiguration,
    callbacks: ContactSearchCallbacks,
    arbitraryCallback: ContactSelectionListModels.Callback,
    clickCallbacks: ContactSearchAdapter.ClickCallbacks,
    longClickCallbacks: ContactSearchAdapter.LongClickCallbacks
  ) {
    check(this.viewModel == null) { "LightContactSearchView.bind() may only be called once" }

    this.callbacks = callbacks
    this.arbitraryCallback = arbitraryCallback
    this.clickCallbacks = clickCallbacks
    this.longClickCallbacks = longClickCallbacks
    this.displayCheckBox = displayOptions.displayCheckBox
    this.mapStateToConfiguration = mapStateToConfiguration

    // Last, and deliberately: this is the state write that lets Content() past its own null check.
    // The collection is started from the composition rather than from here because bind() is called
    // from Fragment.onCreateView, before the view is attached and therefore before it has a
    // ViewTreeLifecycleOwner to collect against.
    this.viewModel = viewModel
  }

  /**
   * Reports the Compose list's scroll position to the `SwipeRefreshLayout` above us, which would
   * otherwise start a pull-to-refresh the moment you dragged a scrolled list downward. The Material
   * `ContactSearchView` overrides this for the same reason.
   */
  override fun canScrollVertically(direction: Int): Boolean {
    return if (direction < 0) listState.canScrollBackward else listState.canScrollForward
  }

  @Composable
  override fun Content() {
    val vm = viewModel ?: return
    val view = LocalView.current

    // The projection runs off the composition, in the same collector that produced the data: the row
    // objects are plain strings and booleans by the time a composable sees them, exactly as
    // LightConversationListItem and LightCallLogItem are. Collecting `data` rather than
    // `mappingModels` skips the MappingModel indirection entirely -- that layer exists only to feed
    // legacy view holders -- and keeps the positional nulls the paging controller relies on, which
    // `ContactSearchModels.toMappingModelList` drops with filterNotNull.
    LaunchedEffect(vm) {
      launch {
        combine(vm.data, vm.selectionState) { data, selection -> data to selection }
          .collect { (data, selection) ->
            state = LightContactItem.map(
              context = context,
              data = data,
              selection = selection,
              fixedContacts = vm.fixedContacts,
              displayCheckBox = displayCheckBox
            )
            // Stands in for the adapter's submitList commit callback, which is what
            // ContactSelectionListFragment uses to decide whether to snap back to the top.
            callbacks.onAdapterListCommitted(state.rows.size)
          }
      }

      launch {
        val mapper = mapStateToConfiguration
        if (mapper != null) {
          vm.configurationState.collect { configState -> vm.setConfiguration(mapper(configState)) }
        }
      }

      launch {
        // The position is ignored, as it is in the Material path: every caller asks for the top.
        vm.scrollRequests.collect { listState.scrollToItem(0) }
      }
    }

    // Scrolling the list puts the keyboard away, as the Material list did. Worth keeping even though
    // the search field is Light now: the LP3's IME is an ordinary system IME and covers half the
    // screen, so a list you cannot see is a list you cannot pick from.
    LaunchedEffect(Unit) {
      snapshotFlow { listState.isScrollInProgress }
        .filter { it }
        .collect { ViewUtil.hideKeyboard(context, view) }
    }

    MollyLightTheme {
      LightContactListScreen(
        state = state,
        statusText = statusText,
        listState = listState,
        onClick = ::onRowClicked,
        onLongClick = ::onRowLongClicked,
        onDataNeededAtSourceIndex = { index -> vm.controller.value?.onDataNeededAroundIndex(index) },
        // Keeps whatever is above us driven by this list's scrolling, the way the RecyclerView used to.
        modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection())
      )
    }
  }

  /**
   * Routes a tap back into Molly.
   *
   * A contact goes through the *existing* `ContactSearchAdapter.ClickCallbacks` that the fragment
   * already supplies, so the tap lands in `ContactSelectionListFragment.ListClickListener` exactly
   * as it did from the Material row -- which is what keeps the self check, the chat-type toggle, the
   * hard selection limit, the username lookup and `onBeforeContactSelected` working without being
   * reimplemented here. `this` is handed over as the anchor view; the fragment's implementation
   * ignores it, and it is a real attached View for any implementation that does not.
   *
   * Note the `when`-on-type rather than a cast: these rows deliberately share one item class across
   * several `ContactSearchData` subtypes, and an unchecked cast here is exactly the shape of bug
   * that has already shipped a ClassCastException on this screen's layouts.
   */
  private fun onRowClicked(item: LightContactItem) {
    val action = item.action
    if (action != null) {
      onActionClicked(action, item.data)
      return
    }

    val clicks = clickCallbacks ?: return

    when (val data = item.data) {
      is ContactSearchData.KnownRecipient -> clicks.onKnownRecipientClicked(this, data, item.selected)
      is ContactSearchData.UnknownRecipient -> clicks.onUnknownRecipientClicked(this, data, item.selected)
      is ContactSearchData.ChatTypeRow -> clicks.onChatTypeClicked(this, data, item.selected)
      is ContactSearchData.Story -> clicks.onStoryClicked(this, data, item.selected)
      else -> Unit
    }
  }

  private fun onActionClicked(action: LightContactItem.Action, data: ContactSearchData?) {
    val arbitrary = arbitraryCallback

    when (action) {
      LightContactItem.Action.NEW_GROUP -> arbitrary?.onNewGroupClicked()
      LightContactItem.Action.INVITE_TO_SIGNAL -> arbitrary?.onInviteToSignalClicked()
      LightContactItem.Action.FIND_CONTACTS -> arbitrary?.onFindContactsClicked()
      LightContactItem.Action.DISMISS_FIND_CONTACTS_BANNER -> arbitrary?.onDismissFindContactsBannerClicked()
      LightContactItem.Action.REFRESH_CONTACTS -> arbitrary?.onRefreshContactsClicked()
      LightContactItem.Action.FIND_BY_USERNAME -> arbitrary?.onFindByUsernameClicked()
      LightContactItem.Action.FIND_BY_PHONE_NUMBER -> arbitrary?.onFindByPhoneNumberClicked()
      LightContactItem.Action.EXPAND -> {
        val expand = data as? ContactSearchData.Expand ?: return
        clickCallbacks?.onExpandClicked(expand)
      }

      // The conversation-list search's row, which no picker configuration produces. Spelled out
      // rather than swept into an `else` so that the next action added to the enum still fails this
      // `when` at compile time instead of silently doing nothing here.
      LightContactItem.Action.CLEAR_CHAT_FILTER -> Unit
    }
  }

  /** The picker's menu drops down from this view, so the row's bounds are of no use to it. */
  private fun onRowLongClicked(item: LightContactItem, bounds: Rect) {
    val data = item.data as? ContactSearchData.KnownRecipient ?: return
    longClickCallbacks?.onKnownRecipientLongClick(this, data)
  }
}
