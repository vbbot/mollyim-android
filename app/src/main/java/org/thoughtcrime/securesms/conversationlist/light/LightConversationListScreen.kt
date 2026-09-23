/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversationlist.light

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightLazyScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Grid units of height for every row. [LightLazyScrollView] derives its scrollbar geometry from a
 * single uniform item height, so rows are held to exactly this on LP3 -- see [LightConversationRow].
 */
private const val ROW_HEIGHT_UNITS = 4.5f

/** Leading slot that holds the unread asterisk / selection checkbox. Always present. */
private const val MARKER_SLOT_UNITS = 1f

/** Gap between the marker slot and the name. */
private const val MARKER_GAP_UNITS = 0.25f

/** Row side margin. */
private const val ROW_HORIZONTAL_PADDING_UNITS = 0.5f

/** Row vertical padding, straight from the reference client. */
private val ROW_VERTICAL_PADDING = 12.dp

/**
 * Height of the bare spacer that stands in for a top bar. The Light chat list is a "list home"
 * screen: it has no persistent top bar, just this 2-unit breathing space.
 */
private const val HEADER_SPACER_UNITS = 2f

/**
 * The Light Phone conversation list.
 *
 * This is a direct port of the Light reference client's chat list
 * (`chats/app/src/main/kotlin/com/lightphone/chats/screens/ChatListScreen.kt`), fed from Molly's
 * existing `ConversationListViewModel`. Deliberately kept boring: no avatars, no snippets, no
 * animations -- exactly the row the reference draws.
 *
 * The caller is responsible for wrapping this in [com.thelightphone.sdk.ui.LightTheme]; see
 * [LightConversationListView], which is the only production caller.
 */
@Composable
fun LightConversationListScreen(
  state: LightConversationListState,
  selectionMode: Boolean,
  listState: LazyListState = rememberLazyListState(),
  onClick: (LightConversationListItem) -> Unit = {},
  onLongClick: (LightConversationListItem, Rect) -> Unit = { _, _ -> },
  onDataNeededAtSourceIndex: (Int) -> Unit = {},
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      // Painted unconditionally: the surrounding Molly chrome uses the Material colour scheme, and
      // LightText always draws in LightThemeTokens.colors.content. Without this the two can end up
      // white-on-white / black-on-black.
      .background(LightThemeTokens.colors.background)
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(HEADER_SPACER_UNITS.gridUnitsAsDp())
    )

    Box(modifier = Modifier.weight(1f)) {
      if (state.rows.isEmpty()) {
        LightStatusText(state.emptyText.orEmpty())
      } else {
        val currentOnDataNeeded by rememberUpdatedState(onDataNeededAtSourceIndex)
        val currentState by rememberUpdatedState(state)

        // Drives the paging controller the same way ConversationListAdapter.getItem() used to:
        // whatever the deepest row the user has reached is, ask for data around it. Mapping back
        // through sourceIndex matters because the rendered list drops the source's header rows.
        LaunchedEffect(listState) {
          snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { renderedIndex ->
              if (renderedIndex != null) {
                val sourceIndex = currentState.pagingIndexFor(renderedIndex)
                if (sourceIndex >= 0) {
                  currentOnDataNeeded(sourceIndex)
                }
              }
            }
        }

        LightLazyScrollView(
          modifier = Modifier.fillMaxSize(),
          listState = listState,
          uniformItemHeightGridUnits = ROW_HEIGHT_UNITS
        ) {
          items(state.rows, key = { it.key }) { item ->
            LightConversationRow(
              item = item,
              selectionMode = selectionMode,
              onClick = onClick,
              onLongClick = onLongClick
            )
          }
        }
      }
    }
  }
}

/**
 * A single conversation row: `[marker] name .......... time`.
 *
 * The reference client sizes this row purely from 12dp of vertical padding around one line of
 * `Heading` text, which lands on ~4.5 grid units on LP3 hardware. [LightLazyScrollView] needs rows
 * to actually *be* [ROW_HEIGHT_UNITS] tall or its scrollbar geometry drifts, so the padding is
 * backed by an explicit minimum.
 *
 * It is a minimum rather than a fixed height on purpose. Grid units come from the screen *width*
 * while the SDK's type scale comes from the screen *height*, so on a tall (non-LP3) screen the text
 * outgrows 4.5 grid units. A fixed height would clip it there; a minimum keeps LP3 exactly uniform
 * and merely makes rows taller than the scrollbar assumes everywhere else.
 */
@Composable
private fun LightConversationRow(
  item: LightConversationListItem,
  selectionMode: Boolean,
  onClick: (LightConversationListItem) -> Unit,
  onLongClick: (LightConversationListItem, Rect) -> Unit
) {
  if (item.kind == LightConversationListItem.Kind.PLACEHOLDER) {
    // A page the paging controller has not filled in yet. Occupies exactly one row so indices and
    // scrollbar geometry stay honest while it loads.
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(ROW_HEIGHT_UNITS.gridUnitsAsDp())
    )
    return
  }

  val haptics = LocalHapticFeedback.current
  val currentOnClick by rememberUpdatedState(onClick)
  val currentOnLongClick by rememberUpdatedState(onLongClick)
  val currentItem by rememberUpdatedState(item)
  val bounds = remember { FloatArray(4) }

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = ROW_HEIGHT_UNITS.gridUnitsAsDp())
      .onGloballyPositioned { coordinates ->
        val rect = coordinates.boundsInRoot()
        bounds[0] = rect.left
        bounds[1] = rect.top
        bounds[2] = rect.right
        bounds[3] = rect.bottom
      }
      // Tap and long-press only, like the reference: a scroll drag never reaches onTap, so
      // flinging the list never opens a conversation. The long-press buzz mirrors what the old
      // RecyclerView rows got for free from View.setOnLongClickListener.
      .pointerInput(Unit) {
        detectTapGestures(
          onTap = { currentOnClick(currentItem) },
          onLongPress = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            currentOnLongClick(currentItem, Rect(bounds[0], bounds[1], bounds[2], bounds[3]))
          }
        )
      }
      .padding(horizontal = ROW_HORIZONTAL_PADDING_UNITS.gridUnitsAsDp(), vertical = ROW_VERTICAL_PADDING)
      // detectTapGestures carries no semantics of its own, so the row would otherwise be invisible
      // to TalkBack -- the old RecyclerView item views got this for free from setOnClickListener.
      .semantics(mergeDescendants = true) {
        role = Role.Button
        onClick {
          currentOnClick(currentItem)
          true
        }
      },
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Fixed-width leading slot. It is present whether or not it has anything in it, so names never
    // shift sideways as chats are read and unread.
    Box(
      modifier = Modifier.width(MARKER_SLOT_UNITS.gridUnitsAsDp()),
      contentAlignment = Alignment.CenterStart
    ) {
      when {
        selectionMode && item.kind == LightConversationListItem.Kind.THREAD -> {
          LightIcon(
            icon = if (item.selected) LightIcons.SELECT_ON else LightIcons.SELECT_OFF,
            size = MARKER_SLOT_UNITS,
            contentDescription = null
          )
        }
        item.unread -> {
          LightText(
            text = "*",
            variant = LightTextVariant.Heading
          )
        }
      }
    }

    Box(modifier = Modifier.width(MARKER_GAP_UNITS.gridUnitsAsDp()))

    Box(modifier = Modifier.weight(1f)) {
      LightText(
        text = item.name,
        variant = LightTextVariant.Heading,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }

    if (item.timestamp.isNotEmpty()) {
      // Not in the reference client, which never has names long enough to reach the time. Molly's
      // group names routinely do, and an ellipsis butted straight up against the timestamp reads
      // as one run-on string.
      Box(modifier = Modifier.width(MARKER_GAP_UNITS.gridUnitsAsDp()))
      LightText(
        text = item.timestamp,
        variant = LightTextVariant.Fine,
        modifier = if (item.timestampDescription.isEmpty()) {
          Modifier
        } else {
          Modifier.semantics { contentDescription = item.timestampDescription }
        }
      )
    }
  }
}

/** Centred single line of copy for the empty / loading states, per the reference client. */
@Composable
private fun LightStatusText(text: String) {
  if (text.isEmpty()) {
    return
  }
  Box(
    modifier = Modifier
      .fillMaxSize()
      // Biases the centre slightly upward -- optical centring, as in the reference.
      .padding(bottom = 4f.gridUnitsAsDp()),
    contentAlignment = Alignment.Center
  ) {
    LightText(
      text = text,
      variant = LightTextVariant.Heading,
      align = TextAlign.Center,
      modifier = Modifier.padding(horizontal = 2f.gridUnitsAsDp())
    )
  }
}
