/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.contacts.paged.light

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import java.util.Locale
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.paged.ContactSearchAdapter
import org.thoughtcrime.securesms.contacts.paged.ContactSearchCallbacks
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration
import org.thoughtcrime.securesms.contacts.paged.ContactSearchData
import org.thoughtcrime.securesms.contacts.paged.ContactSearchState
import org.thoughtcrime.securesms.contacts.paged.ContactSearchViewModel
import org.thoughtcrime.securesms.contacts.paged.rememberDefaultContactSearchItemClickCallbacks
import org.thoughtcrime.securesms.contacts.paged.rememberDefaultContactSearchItemLongClickCallbacks
import org.thoughtcrime.securesms.contacts.paged.rememberDefaultContactSearchItemStoryContextMenuCallbacks
import org.thoughtcrime.securesms.light.LightActionPanel
import org.thoughtcrime.securesms.light.LightPanelAction

/**
 * Latest action payloads for a Light list. Render equality intentionally ignores invisible source
 * data, so gesture handlers resolve a stable visible key here instead of retaining the row instance
 * that happened to compose first.
 */
internal class LightContactPayloadRegistry {
  private var rows: Map<String, LightContactItem> = emptyMap()

  fun update(state: LightContactState) {
    rows = state.rows.associateBy(LightContactItem::key)
  }

  fun resolve(key: String): LightContactItem? = rows[key]

  fun clear() {
    rows = emptyMap()
  }
}

/**
 * Reusable Light presentation host for the paged [ContactSearchViewModel]. Selection, limits,
 * selectability, story setup, paging and configuration remain owned by the existing framework.
 *
 * The caller is responsible for the Light theme (see [MollyLightTheme]), because this composable is
 * only ever the list *inside* a screen that has already chosen one -- wrapping it here would put a
 * second Material colour scheme in the middle of the caller's tree.
 *
 * Every collector is keyed on [viewModel] alone and starts as soon as the composition does, rather
 * than on the host's lifecycle reaching STARTED. That is the same shape as
 * [LightContactSearchView]'s, and it matters: a composition can be created before the host has been
 * started, and a `repeatOnLifecycle(STARTED)` block would then not run until it was -- leaving a
 * screen whose configuration was never applied and whose list never loaded anything.
 */
@Composable
fun LightContactSearch(
  viewModel: ContactSearchViewModel,
  mapStateToConfiguration: (ContactSearchState) -> ContactSearchConfiguration,
  displayOptions: ContactSearchAdapter.DisplayOptions = ContactSearchAdapter.DisplayOptions(),
  callbacks: ContactSearchCallbacks = remember { ContactSearchCallbacks.Simple() },
  clickCallbacks: ContactSearchAdapter.ClickCallbacks = rememberDefaultContactSearchItemClickCallbacks(viewModel, callbacks),
  longClickCallbacks: ContactSearchAdapter.LongClickCallbacks = rememberDefaultContactSearchItemLongClickCallbacks(),
  storyContextMenuCallbacks: ContactSearchAdapter.StoryContextMenuCallbacks = rememberDefaultContactSearchItemStoryContextMenuCallbacks(viewModel),
  supportedArbitraryTypes: Set<String> = emptySet(),
  customContent: @Composable (ContactSearchData.Arbitrary) -> Unit = { LightContactCoverageFailure(it.type) },
  onStandardAction: (LightContactItem.Action) -> Unit = {},
  onListCommitted: (Int) -> Unit = {},
  statusText: String = "",
  listState: LazyListState = rememberLazyListState(),
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val view = LocalView.current
  val payloads = remember { LightContactPayloadRegistry() }
  var state by remember { mutableStateOf(LightContactState()) }
  var storyActions by remember { mutableStateOf<List<LightPanelAction>?>(null) }
  val currentMapStateToConfiguration by rememberUpdatedState(mapStateToConfiguration)
  val currentOnListCommitted by rememberUpdatedState(onListCommitted)
  val currentDisplayOptions by rememberUpdatedState(displayOptions)
  val currentSupportedArbitraryTypes by rememberUpdatedState(supportedArbitraryTypes)

  DisposableEffect(viewModel, payloads) {
    onDispose {
      payloads.clear()
      viewModel.setDisplayingContextMenu(false)
    }
  }

  LaunchedEffect(viewModel) {
    launch {
      combine(viewModel.data, viewModel.selectionState) { data, selection -> data to selection }
        .collect { (data, selection) ->
          val mapped = LightContactItem.map(
            context = context,
            data = data,
            selection = selection,
            fixedContacts = viewModel.fixedContacts,
            displayCheckBox = currentDisplayOptions.displayCheckBox,
            supportedArbitraryTypes = currentSupportedArbitraryTypes
          )
          // Before the equality check on `state`, and unconditionally: render equality ignores the
          // payload, so a state write that gets skipped must still refresh what taps dispatch to.
          payloads.update(mapped)
          state = mapped
          currentOnListCommitted(mapped.rows.size)
        }
    }

    launch {
      viewModel.configurationState.collect { configurationState ->
        viewModel.setConfiguration(currentMapStateToConfiguration(configurationState))
      }
    }

    launch {
      viewModel.scrollRequests.collect { listState.scrollToItem(0) }
    }
  }

  fun dismissStoryActions() {
    storyActions = null
    viewModel.setDisplayingContextMenu(false)
  }

  fun showStoryActions(story: ContactSearchData.Story, selected: Boolean) {
    fun action(label: Int, block: () -> Unit): LightPanelAction {
      return LightPanelAction(
        label = context.getString(label).uppercase(Locale.getDefault()),
        onSelected = {
          dismissStoryActions()
          block()
        }
      )
    }

    storyActions = when {
      story.recipient.isMyStory -> listOf(
        action(R.string.ContactSearchItems__story_settings) {
          storyContextMenuCallbacks.onOpenStorySettings(story)
        }
      )

      story.recipient.isGroup -> listOf(
        action(R.string.ContactSearchItems__remove_story) {
          storyContextMenuCallbacks.onRemoveGroupStory(story, selected)
        }
      )

      story.recipient.isDistributionList -> listOf(
        action(R.string.ContactSearchItems__story_settings) {
          storyContextMenuCallbacks.onOpenStorySettings(story)
        },
        action(R.string.ContactSearchItems__delete_story) {
          storyContextMenuCallbacks.onDeletePrivateStory(story, selected)
        }
      )

      else -> emptyList()
    }
    viewModel.setDisplayingContextMenu(storyActions?.isNotEmpty() == true)
  }

  Box(modifier = modifier.fillMaxSize()) {
    LightContactListScreen(
      state = state,
      statusText = statusText,
      listState = listState,
      onDataNeededAtSourceIndex = { index -> viewModel.controller.value?.onDataNeededAroundIndex(index) },
      onClick = { key ->
        val item = payloads.resolve(key) ?: return@LightContactListScreen
        when (val data = item.data) {
          is ContactSearchData.Story -> clickCallbacks.onStoryClicked(view, data, item.selected)
          is ContactSearchData.KnownRecipient -> clickCallbacks.onKnownRecipientClicked(view, data, item.selected)
          is ContactSearchData.UnknownRecipient -> clickCallbacks.onUnknownRecipientClicked(view, data, item.selected)
          is ContactSearchData.ChatTypeRow -> clickCallbacks.onChatTypeClicked(view, data, item.selected)
          is ContactSearchData.Expand -> clickCallbacks.onExpandClicked(data)
          is ContactSearchData.Header -> data.action?.action?.run()
          else -> item.action?.let(onStandardAction)
        }
      },
      onLongClick = { key, _: Rect ->
        val item = payloads.resolve(key) ?: return@LightContactListScreen
        when (val data = item.data) {
          is ContactSearchData.KnownRecipient -> longClickCallbacks.onKnownRecipientLongClick(view, data)
          is ContactSearchData.Story -> showStoryActions(data, item.selected)
          else -> Unit
        }
      },
      customContent = { item ->
        // A caller-rendered row. If the payload is somehow not resolvable there is nothing honest to
        // draw, and a blank row would read as a loading hole, so this says what it is instead.
        val arbitrary = payloads.resolve(item.key)?.data as? ContactSearchData.Arbitrary
        if (arbitrary != null) {
          customContent(arbitrary)
        } else {
          LightContactCoverageFailure(item.name)
        }
      }
    )

    storyActions?.takeIf { it.isNotEmpty() }?.let { actions ->
      // The panel swallows every pointer event in its own area, so a tap either hits a row of it or
      // dismisses it -- but the list behind is still scrollable, where Signal's frozen it with
      // `userScrollEnabled = !isDisplayingContextMenu`. `LightLazyScrollView` does not expose that
      // parameter and `LazyListState` has no such property (checked against the Compose BOM this
      // project resolves), so closing it would mean teaching the vendored SDK. Left open, and
      // recorded as a known difference rather than silently dropped.
      LightActionPanel(
        actions = actions,
        onDismiss = ::dismissStoryActions,
        modifier = Modifier.align(Alignment.BottomCenter)
      )
    }
  }
}
