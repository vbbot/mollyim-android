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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.conversationlist.model.Conversation
import org.thoughtcrime.securesms.conversationlist.model.ConversationSet
import org.thoughtcrime.securesms.light.MollyLightTheme
import kotlin.math.roundToInt

/**
 * The conversation list, rendered with The Light Phone SDK's Compose components.
 *
 * This replaces the `ConversationListAdapter` / `ConversationListItem` RecyclerView path. It is an
 * [AbstractComposeView] rather than a plain composable so that the (Java) `ConversationListFragment`
 * can keep owning the surrounding screen -- banners, chat folders, search, the filter pull view, the
 * multi-select bottom action bar -- and drive this the same way it used to drive an adapter.
 *
 * The Light theme is scoped to this view only, via [MollyLightTheme]: Molly's `SignalTheme` and the
 * SDK's `LightTheme` both install a Material 3 `ColorScheme`, so they must not be nested; everything
 * outside this view stays on Molly's theme.
 */
class LightConversationListView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs) {

  /**
   * The Light equivalent of `ConversationListAdapter.OnConversationClickListener`. Long clicks hand
   * back the row's bounds *in this view's coordinate space* so that the caller can park an anchor
   * where the row is and show `SignalContextMenu` against it -- Compose rows have no `View` of their
   * own to anchor to.
   */
  interface Callback {
    fun onConversationClick(conversation: Conversation)
    fun onConversationLongClick(conversation: Conversation, bounds: Rect)
    fun onShowArchiveClick()
    fun onDataNeededAroundIndex(index: Int)
  }

  var callback: Callback? = null

  private var state by mutableStateOf(LightConversationListState())
  private var selection by mutableStateOf(ConversationSet())
  private var conversations: List<Conversation?> = emptyList()

  /**
   * Bumped by [refreshTimestamps] to force the relative timestamps to be recomputed. Stands in for
   * `ConversationListAdapter.notifyTimestampPayloadUpdate()`.
   */
  private var timestampEpoch: Long = System.currentTimeMillis()

  private val listState = LazyListState()

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  }

  /** Replaces the rendered contents. The list may contain `null` holes for unloaded pages. */
  fun submitList(conversations: List<Conversation?>) {
    this.conversations = conversations
    remap()
  }

  /** Highlights the multi-select checkboxes. Mirrors `setSelectedConversations` on the old adapter. */
  fun setSelectedConversations(selection: ConversationSet) {
    this.selection = selection
    remap()
  }

  /** Recomputes the relative timestamps; called on the same minute tick the adapter used. */
  fun refreshTimestamps() {
    timestampEpoch = System.currentTimeMillis()
    remap()
  }

  /** True when the list is scrolled away from the top, for `ConversationListFragment.isScrolled()`. */
  fun isScrolled(): Boolean = listState.canScrollBackward

  /** Index of the first fully visible row, or -1. Used to decide whether to snap back to the top. */
  fun firstCompletelyVisibleItemPosition(): Int {
    val info = listState.layoutInfo
    val first = info.visibleItemsInfo.firstOrNull() ?: return -1
    return if (first.offset >= info.viewportStartOffset) first.index else first.index + 1
  }

  fun scrollToTop(smooth: Boolean) {
    val scope = findViewTreeLifecycleOwner()?.lifecycleScope ?: return
    scope.launch {
      if (smooth) {
        listState.animateScrollToItem(0)
      } else {
        listState.scrollToItem(0)
      }
    }
  }

  private fun remap() {
    state = LightConversationListItem.map(
      context = context,
      conversations = conversations,
      selection = selection,
      nowMs = timestampEpoch
    )
  }

  @Composable
  override fun Content() {
    MollyLightTheme {
      LightConversationListScreen(
        state = state,
        selectionMode = selection.isNotEmpty(),
        listState = listState,
        onClick = { item ->
          when (item.kind) {
            LightConversationListItem.Kind.ARCHIVE -> callback?.onShowArchiveClick()
            LightConversationListItem.Kind.THREAD -> item.conversation?.let { callback?.onConversationClick(it) }
            LightConversationListItem.Kind.PLACEHOLDER -> Unit
          }
        },
        onLongClick = { item, bounds ->
          if (item.kind == LightConversationListItem.Kind.THREAD) {
            item.conversation?.let {
              callback?.onConversationLongClick(
                it,
                Rect(
                  left + bounds.left.roundToInt(),
                  top + bounds.top.roundToInt(),
                  left + bounds.right.roundToInt(),
                  top + bounds.bottom.roundToInt()
                )
              )
            }
          }
        },
        onDataNeededAtSourceIndex = { index -> callback?.onDataNeededAroundIndex(index) },
        // Keeps the CoordinatorLayout above us (the collapsing toolbar + the pull-to-filter
        // AppBarLayout) driven by this list's scrolling, the way the RecyclerView used to.
        modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection())
      )
    }
  }
}
