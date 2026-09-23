/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.log.light

import android.content.Context
import android.util.AttributeSet
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.calls.log.CallLogRow
import org.thoughtcrime.securesms.calls.log.CallLogSelectionState
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.recipients.RecipientId

/**
 * The call log, rendered with The Light Phone SDK's Compose components.
 *
 * This replaces the `CallLogAdapter` RecyclerView path. It is an [AbstractComposeView] rather than a
 * plain composable so that `CallLogFragment` can keep owning the surrounding screen -- the filter
 * pull view, search, multi-select and its bottom action bar, every dialog -- and drive this the same
 * way it used to drive an adapter.
 *
 * The Light theme is scoped to this view only, via [MollyLightTheme]: Molly's `SignalTheme` and the
 * SDK's `LightTheme` both install a Material 3 `ColorScheme`, so they must not be nested; everything
 * outside this view stays on Molly's theme.
 */
class LightCallLogView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs) {

  /**
   * The Light equivalent of `CallLogAdapter.Callbacks`.
   *
   * A long press hands back the whole [CallLogRow] and nothing else -- unlike the chat list, which
   * has to hand back the row's bounds so a `SignalContextMenu` can be anchored over it, the call
   * menu is a `LightActionPanel` pinned to the bottom of the screen and has nothing to anchor to.
   */
  interface Callback {
    fun onCallClicked(call: CallLogRow.Call)
    fun onCallLinkClicked(callLink: CallLogRow.CallLink)
    fun onCreateCallLinkClicked()
    fun onCallLongClicked(row: CallLogRow)
    fun onDataNeededAroundIndex(index: Int)
  }

  var callback: Callback? = null

  private var state by mutableStateOf(LightCallLogState())
  private var selection: CallLogSelectionState = CallLogSelectionState.empty()
  private var rows: List<CallLogRow?> = emptyList()
  private var localDeviceCallRecipientId: RecipientId? = null

  /**
   * Bumped by [refreshTimestamps] to force the relative timestamps to be recomputed. Stands in for
   * `CallLogAdapter.onTimestampTick()`.
   */
  private var timestampEpoch: Long = System.currentTimeMillis()

  private val listState = LazyListState()

  /** Rendered row count, which is what `CallLogSelectionState.isNotEmpty` wants for "select all". */
  val itemCount: Int
    get() = state.rows.size

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  }

  /**
   * Replaces the rendered contents, exactly as `CallLogAdapter.submitCallRows` did and with the same
   * arguments: the rows (which may contain `null` holes for pages the paging controller has not
   * loaded yet), what is selected, and the recipient of the call this device is currently in, which
   * is what decides JOIN from RETURN.
   *
   * One call rather than three setters because every one of them would otherwise re-project the
   * whole list, and all three change together on every emission.
   */
  fun submit(rows: List<CallLogRow?>, selection: CallLogSelectionState, localDeviceCallRecipientId: RecipientId?) {
    this.rows = rows
    this.selection = selection
    this.localDeviceCallRecipientId = localDeviceCallRecipientId
    remap()
  }

  /** Recomputes the relative timestamps; called on the same minute tick the adapter used. */
  fun refreshTimestamps() {
    timestampEpoch = System.currentTimeMillis()
    remap()
  }

  /** True when the list is scrolled away from the top. */
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
    state = LightCallLogItem.map(
      context = context,
      rows = rows,
      selection = selection,
      localDeviceCallRecipientId = localDeviceCallRecipientId,
      nowMs = timestampEpoch
    )
  }

  @Composable
  override fun Content() {
    MollyLightTheme {
      LightCallLogScreen(
        state = state,
        listState = listState,
        onClick = { item ->
          when (val row = item.row) {
            is CallLogRow.Call -> callback?.onCallClicked(row)
            is CallLogRow.CallLink -> callback?.onCallLinkClicked(row)
            is CallLogRow.CreateCallLink -> callback?.onCreateCallLinkClicked()
            else -> Unit
          }
        },
        onLongClick = { item ->
          val row = item.row
          if (item.hasActions && row != null) {
            callback?.onCallLongClicked(row)
          }
        },
        onDataNeededAtSourceIndex = { index -> callback?.onDataNeededAroundIndex(index) },
        // Keeps the CoordinatorLayout above us (the pull-to-filter AppBarLayout) driven by this
        // list's scrolling, the way the RecyclerView used to.
        modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection())
      )
    }
  }
}
