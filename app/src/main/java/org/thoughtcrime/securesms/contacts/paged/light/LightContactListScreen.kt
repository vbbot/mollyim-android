/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.paged.light

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
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
 * single uniform item height, so every row kind is held to exactly this on LP3 -- see
 * [LightContactRow].
 */
private const val ROW_HEIGHT_UNITS = 4.5f

/** Leading slot that holds the selection checkbox. Always present, on every row kind. */
private const val MARKER_SLOT_UNITS = 1f

/** Gap between the marker slot and the name, and between the name and the detail. */
private const val COLUMN_GAP_UNITS = 0.25f

/** Row side margin. */
private const val ROW_HORIZONTAL_PADDING_UNITS = 0.5f

/** Row vertical padding, straight from the reference client. */
private val ROW_VERTICAL_PADDING = 12.dp

/**
 * The Light Phone contact picker.
 *
 * The row is the chat list's row (see `conversationlist/light/LightConversationListScreen.kt`),
 * which is in turn the Light reference client's `RoomRow`: a fixed one-grid-unit marker slot, the
 * name in `Heading`, a `Fine` token on the right, 0.5 grid units of side margin, 4.5 grid units
 * tall. This screen is reached straight off the chat list and the call log, so it has to land on
 * their columns exactly or the transition reads as the screen twitching.
 *
 * Four row kinds share those columns, told apart by type alone rather than by rules, chrome or
 * colour -- which is all the Light design gives you, and is enough:
 *
 * - **A contact** is `Heading`, the largest text on the screen, because picking one is what the
 *   screen is for. Its marker slot carries [LightIcons.SELECT_ON] / [LightIcons.SELECT_OFF] while
 *   the screen is selecting.
 * - **An action** -- "NEW GROUP", "FIND BY PHONE NUMBER", "INVITE TO SIGNAL", "VIEW MORE" -- is
 *   upper-case `Button`, the SDK's own register for a thing you can do, the same one the bottom bar
 *   and the action panel use. Visibly smaller than a name, so the eye skips the actions when it is
 *   looking for a person and finds them when it is not.
 * - **A section label** is upper-case `Fine`, lightened. Smaller and dimmer than everything else on
 *   the screen, which is what makes it read as a label rather than as a row you can tap.
 * - **A message** -- Signal's "No results for ..." -- is centred `Copy`.
 *
 * **Why the section labels were kept**, when the chat list dropped its own. The chat list is one
 * list; this screen is several concatenated -- recent chats, then contacts, then groups, then the
 * "More" actions -- and without labels a name that appears twice (once under recents, once under
 * contacts) looks like a duplicate rather than like the two lists it is. Signal already suppresses
 * most of them here (`ContactSelectionListFragment.mapStateToConfiguration` hides the contacts
 * header whenever there is no query, and always in the call picker), so what is left is a handful
 * of genuine dividers, and they are cheap.
 *
 * The letter headers -- Signal's A/B/C dividers down a long contacts list -- are **not** kept. They
 * exist to serve the fast scroller, and the Light list has [LightLazyScrollView]'s own scrollbar
 * instead; the Light reference client's contacts panel likewise has neither. Search is the answer
 * to a long list here, which is why this screen's search field is the one that got real work.
 *
 * The caller is responsible for wrapping this in [com.thelightphone.sdk.ui.LightTheme]; see
 * [LightContactSearchView], which is the only production caller.
 */
@Composable
fun LightContactListScreen(
  state: LightContactState,
  statusText: String = "",
  listState: LazyListState = rememberLazyListState(),
  onClick: (LightContactItem) -> Unit = {},
  onLongClick: (LightContactItem) -> Unit = {},
  onDataNeededAtSourceIndex: (Int) -> Unit = {},
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      // Painted unconditionally: the surrounding Molly chrome uses the Material colour scheme, and
      // LightText always draws in LightThemeTokens.colors.content. Without this the two can end up
      // white-on-white / black-on-black.
      .background(LightThemeTokens.colors.background)
  ) {
    if (state.rows.isEmpty()) {
      LightStatusText(statusText)
      return@Box
    }

    val currentOnDataNeeded by rememberUpdatedState(onDataNeededAtSourceIndex)
    val currentState by rememberUpdatedState(state)

    // Drives the paging controller exactly as `MappingLazyList.PagerEffect` did: every visible row
    // asks for data around itself. Mapping back through sourceIndex matters because the rendered
    // list is not the source list -- it keeps the source's unloaded holes but splits the contacts
    // permission banner into two rows, so the two indices drift apart in both directions.
    LaunchedEffect(listState) {
      snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
        .distinctUntilChanged()
        .collect { renderedIndices ->
          renderedIndices.forEach { renderedIndex ->
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
        LightContactRow(
          item = item,
          selectionMode = state.selectionMode,
          onClick = onClick,
          onLongClick = onLongClick
        )
      }
    }
  }
}

/**
 * A single picker row: `[marker] name ... [detail]`.
 *
 * The reference client sizes this row purely from 12dp of vertical padding around one line of
 * `Heading` text, which lands on ~4.5 grid units on LP3 hardware. [LightLazyScrollView] needs rows
 * to actually *be* [ROW_HEIGHT_UNITS] tall or its scrollbar geometry drifts, so the padding is
 * backed by an explicit minimum -- and the shorter row kinds (labels, actions) get the same minimum
 * rather than hugging their smaller text, for the same reason.
 *
 * It is a minimum rather than a fixed height on purpose. Grid units come from the screen *width*
 * while the SDK's type scale comes from the screen *height*, so on a tall (non-LP3) screen the text
 * outgrows 4.5 grid units. A fixed height would clip it there; a minimum keeps LP3 exactly uniform
 * and merely makes rows taller than the scrollbar assumes everywhere else.
 */
@Composable
private fun LightContactRow(
  item: LightContactItem,
  selectionMode: Boolean,
  onClick: (LightContactItem) -> Unit,
  onLongClick: (LightContactItem) -> Unit
) {
  if (item.kind == LightContactItem.Kind.PLACEHOLDER) {
    // A page the paging controller has not filled in yet. Occupies exactly one row so indices and
    // scrollbar geometry stay honest while it loads.
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(ROW_HEIGHT_UNITS.gridUnitsAsDp())
    )
    return
  }

  if (item.kind == LightContactItem.Kind.MESSAGE) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = ROW_HEIGHT_UNITS.gridUnitsAsDp())
        .padding(horizontal = 2f.gridUnitsAsDp()),
      contentAlignment = Alignment.Center
    ) {
      LightText(
        text = item.name,
        variant = LightTextVariant.Copy,
        align = TextAlign.Center,
        lighten = true
      )
    }
    return
  }

  val haptics = LocalHapticFeedback.current
  val currentOnClick by rememberUpdatedState(onClick)
  val currentOnLongClick by rememberUpdatedState(onLongClick)
  val currentItem by rememberUpdatedState(item)
  val clickable = item.clickable

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = ROW_HEIGHT_UNITS.gridUnitsAsDp())
      .then(
        if (clickable) {
          // Tap and long-press only, like the reference: a scroll drag never reaches onTap, so
          // flinging the list never picks a contact. The long-press buzz mirrors what the old
          // RecyclerView rows got for free from View.setOnLongClickListener.
          Modifier
            .pointerInput(Unit) {
              detectTapGestures(
                onTap = { currentOnClick(currentItem) },
                onLongPress = {
                  haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                  currentOnLongClick(currentItem)
                }
              )
            }
            // detectTapGestures carries no semantics of its own, so the row would otherwise be
            // invisible to TalkBack -- the old RecyclerView item views got this for free from
            // setOnClickListener.
            .semantics(mergeDescendants = true) {
              role = Role.Button
              if (selectionMode && currentItem.kind == LightContactItem.Kind.CONTACT) {
                selected = currentItem.selected
              }
              onClick {
                currentOnClick(currentItem)
                true
              }
            }
        } else {
          Modifier
        }
      )
      .padding(horizontal = ROW_HORIZONTAL_PADDING_UNITS.gridUnitsAsDp(), vertical = ROW_VERTICAL_PADDING),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Fixed-width leading slot, present on every row kind whether or not it has anything in it, so
    // that names, actions and section labels all share one left edge and nothing shifts sideways as
    // contacts are selected and deselected.
    Box(
      modifier = Modifier.width(MARKER_SLOT_UNITS.gridUnitsAsDp()),
      contentAlignment = Alignment.CenterStart
    ) {
      if (selectionMode && item.kind == LightContactItem.Kind.CONTACT) {
        LightIcon(
          icon = if (item.selected) LightIcons.SELECT_ON else LightIcons.SELECT_OFF,
          size = MARKER_SLOT_UNITS,
          contentDescription = null
        )
      }
    }

    Box(modifier = Modifier.width(COLUMN_GAP_UNITS.gridUnitsAsDp()))

    Box(modifier = Modifier.weight(1f)) {
      LightText(
        text = item.name,
        variant = when (item.kind) {
          LightContactItem.Kind.HEADER -> LightTextVariant.Fine
          LightContactItem.Kind.ACTION -> LightTextVariant.Button
          else -> LightTextVariant.Heading
        },
        // Dimmed for the two kinds of row that are on the screen to be read rather than tapped: a
        // section label, and a fixed contact -- an existing group member, say -- which Signal shows
        // permanently checked and inert. Dimming is all the Light design has to say "not this one",
        // and it is what the disabled checkbox used to say.
        lighten = item.kind == LightContactItem.Kind.HEADER ||
          (item.kind == LightContactItem.Kind.CONTACT && !item.enabled),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }

    if (item.detail.isNotEmpty()) {
      // Not in the reference client, which never has names long enough to reach the second column.
      // Molly's group names routinely do, and an ellipsis butted straight up against the detail
      // reads as one run-on string.
      Box(modifier = Modifier.width(COLUMN_GAP_UNITS.gridUnitsAsDp()))
      LightText(
        text = item.detail,
        variant = LightTextVariant.Fine,
        maxLines = 1
      )
    }
  }
}

/**
 * Centred copy for the whole-screen state, per the reference client.
 *
 * This is only ever the *loading* state. A search that genuinely matches nothing does not land here:
 * Signal's configuration answers it with an `Empty` row plus the "More" actions beneath it, so the
 * list is non-empty and "No results for ..." is drawn as a [LightContactItem.Kind.MESSAGE] row with
 * refresh and invite still reachable below it.
 */
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
