/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.log.light

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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
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
import org.thoughtcrime.securesms.R

/**
 * Grid units of height for every row. [LightLazyScrollView] derives its scrollbar geometry from a
 * single uniform item height, so rows are held to exactly this on LP3 -- see [LightCallLogRow].
 */
private const val ROW_HEIGHT_UNITS = 4.5f

/** Leading slot that holds the missed marker / selection checkbox. Always present. */
private const val MARKER_SLOT_UNITS = 1f

/** Gap between the marker slot and the name, and between the columns on the row's right. */
private const val COLUMN_GAP_UNITS = 0.25f

/** Row side margin. */
private const val ROW_HORIZONTAL_PADDING_UNITS = 0.5f

/** Row vertical padding, straight from the reference client. */
private val ROW_VERTICAL_PADDING = 12.dp

/**
 * Height of the bare spacer that stands in for a top bar. The Light call log is a "list home"
 * screen, like the chat list: no persistent top bar, just this 2-unit breathing space.
 */
private const val HEADER_SPACER_UNITS = 2f

/** Gap between the two lines of the empty state. */
private const val EMPTY_STATE_GAP_UNITS = 0.5f

/**
 * The Light Phone call log.
 *
 * The row is the chat list's row (see
 * `conversationlist/light/LightConversationListScreen.kt`), which is in turn the Light reference
 * client's `RoomRow`: a fixed one-grid-unit marker slot, the name in `Heading`, the relative time in
 * `Fine` on the right, 0.5 grid units of side margin, 4.5 grid units tall. The two tabs are the same
 * list and must line up column for column when you switch between them.
 *
 * Two things are added to that row, and only two:
 *
 * - **The marker slot carries the missed-call glyph.** Where the chat list puts an unread asterisk.
 *   It is the Light Phone's own recents-row marker ([LightIcons.CALL_MISSED]) rather than red text,
 *   and it earns its salience the same way the asterisk does -- by being the only thing that ever
 *   appears there, so a screen of answered calls is a screen of empty slots.
 * - **A `Fine` token between the name and the time** says which way the call went and whether it was
 *   video. See [LightCallLogItem.detailFor] for the vocabulary and why it is words and not glyphs.
 *
 * The caller is responsible for wrapping this in [com.thelightphone.sdk.ui.LightTheme]; see
 * [LightCallLogView], which is the only production caller.
 */
@Composable
fun LightCallLogScreen(
  state: LightCallLogState,
  listState: LazyListState = rememberLazyListState(),
  onClick: (LightCallLogItem) -> Unit = {},
  onLongClick: (LightCallLogItem) -> Unit = {},
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
        LightStatusText(title = state.emptyTitle.orEmpty(), body = state.emptyBody.orEmpty())
      } else {
        val currentOnDataNeeded by rememberUpdatedState(onDataNeededAtSourceIndex)
        val currentState by rememberUpdatedState(state)

        // Drives the paging controller the same way PagingMappingAdapter.getItem() used to:
        // whatever the deepest row the user has reached is, ask for data around it. Mapping back
        // through sourceIndex matters because the rendered list drops the source's filter footer.
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
            LightCallLogRow(
              item = item,
              selectionMode = state.selectionMode,
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
 * A single call row: `[marker] name ... [detail] time`.
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
private fun LightCallLogRow(
  item: LightCallLogItem,
  selectionMode: Boolean,
  onClick: (LightCallLogItem) -> Unit,
  onLongClick: (LightCallLogItem) -> Unit
) {
  if (item.kind == LightCallLogItem.Kind.PLACEHOLDER) {
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
  val missedDescription = stringResource(R.string.CallLogAdapter__missed)

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = ROW_HEIGHT_UNITS.gridUnitsAsDp())
      // Tap and long-press only, like the reference: a scroll drag never reaches onTap, so
      // flinging the list never opens a call. The long-press buzz mirrors what the old RecyclerView
      // rows got for free from View.setOnLongClickListener.
      .pointerInput(Unit) {
        detectTapGestures(
          onTap = { currentOnClick(currentItem) },
          onLongPress = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            currentOnLongClick(currentItem)
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
    // shift sideways as the log fills with missed and answered calls.
    Box(
      modifier = Modifier.width(MARKER_SLOT_UNITS.gridUnitsAsDp()),
      contentAlignment = Alignment.CenterStart
    ) {
      when {
        selectionMode && item.hasActions -> {
          LightIcon(
            icon = if (item.selected) LightIcons.SELECT_ON else LightIcons.SELECT_OFF,
            size = MARKER_SLOT_UNITS,
            contentDescription = null
          )
        }

        item.missed -> {
          LightIcon(
            icon = LightIcons.CALL_MISSED,
            size = MARKER_SLOT_UNITS,
            contentDescription = missedDescription
          )
        }
      }
    }

    Box(modifier = Modifier.width(COLUMN_GAP_UNITS.gridUnitsAsDp()))

    Box(modifier = Modifier.weight(1f)) {
      LightText(
        text = item.name,
        variant = LightTextVariant.Heading,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }

    if (item.detail.isNotEmpty()) {
      Box(modifier = Modifier.width(COLUMN_GAP_UNITS.gridUnitsAsDp()))
      LightText(
        text = item.detail,
        variant = LightTextVariant.Fine,
        maxLines = 1
      )
    }

    if (item.timestamp.isNotEmpty()) {
      // Not in the reference client, which never has names long enough to reach the time. Molly's
      // group names routinely do, and an ellipsis butted straight up against the timestamp reads
      // as one run-on string.
      Box(modifier = Modifier.width(COLUMN_GAP_UNITS.gridUnitsAsDp()))
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

/**
 * Centred copy for the empty state, per the reference client. Signal's empty state is two lines --
 * "No calls." over "Get started by calling a friend." -- and both are kept, in Light type.
 */
@Composable
private fun LightStatusText(title: String, body: String) {
  if (title.isEmpty() && body.isEmpty()) {
    return
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      // Biases the centre slightly upward -- optical centring, as in the reference.
      .padding(bottom = 4f.gridUnitsAsDp()),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      modifier = Modifier.padding(horizontal = 2f.gridUnitsAsDp())
    ) {
      if (title.isNotEmpty()) {
        LightText(
          text = title,
          variant = LightTextVariant.Heading,
          align = TextAlign.Center
        )
      }

      if (body.isNotEmpty()) {
        Box(modifier = Modifier.height(EMPTY_STATE_GAP_UNITS.gridUnitsAsDp()))
        LightText(
          text = body,
          variant = LightTextVariant.Copy,
          align = TextAlign.Center
        )
      }
    }
  }
}
