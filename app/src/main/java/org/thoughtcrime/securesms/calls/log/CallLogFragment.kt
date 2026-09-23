package org.thoughtcrime.securesms.calls.log

import android.annotation.SuppressLint
import android.content.res.Resources
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.kotlin.Flowables
import io.reactivex.rxjava3.kotlin.subscribeBy
import kotlinx.coroutines.launch
import org.signal.core.ui.BottomSheetUtil
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.isSplitPane
import org.signal.core.util.DimensionUnit
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.concurrent.addTo
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.MainNavigator
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.calls.links.create.CreateCallLinkBottomSheetDialogFragment
import org.thoughtcrime.securesms.calls.log.light.LightCallLogView
import org.thoughtcrime.securesms.components.ProgressCardDialogFragment
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsActivity
import org.thoughtcrime.securesms.components.snackbars.SnackbarState
import org.thoughtcrime.securesms.conversation.ConversationUpdateTick
import org.thoughtcrime.securesms.conversationlist.ConversationFilterBehavior
import org.thoughtcrime.securesms.conversationlist.chatfilter.ConversationFilterSource
import org.thoughtcrime.securesms.conversationlist.chatfilter.ConversationListFilterPullView.OnCloseClicked
import org.thoughtcrime.securesms.conversationlist.chatfilter.ConversationListFilterPullView.OnFilterStateChanged
import org.thoughtcrime.securesms.conversationlist.chatfilter.FilterLerp
import org.thoughtcrime.securesms.conversationlist.chatfilter.FilterPullState
import org.thoughtcrime.securesms.databinding.CallLogFragmentBinding
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.light.LightPanelAction
import org.thoughtcrime.securesms.light.LightPanelActions
import org.thoughtcrime.securesms.main.MainNavigationDetailLocation
import org.thoughtcrime.securesms.main.MainNavigationListLocation
import org.thoughtcrime.securesms.main.MainNavigationViewModel
import org.thoughtcrime.securesms.main.MainSnackbarHostKey
import org.thoughtcrime.securesms.main.MainToolbarMode
import org.thoughtcrime.securesms.main.MainToolbarViewModel
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.doAfterNextLayout
import org.thoughtcrime.securesms.util.fragments.requireListener
import org.thoughtcrime.securesms.util.visible
import java.util.Objects
import org.signal.core.ui.R as CoreUiR

/**
 * Call Log tab.
 *
 * The list itself is [LightCallLogView], a Compose screen built out of the Light Phone SDK's
 * components; this fragment keeps everything around it -- the pull-to-filter header, search,
 * multi-select and its bottom action bar, and every dialog -- and drives the view the way it used to
 * drive `CallLogAdapter`.
 */
@SuppressLint("DiscouragedApi")
class CallLogFragment : Fragment(R.layout.call_log_fragment), LightCallLogView.Callback, CallLogContextMenu.Callbacks {

  companion object {
    private val TAG = Log.tag(CallLogFragment::class.java)

    /** Rows from the top within which a tap on the Calls tab scrolls smoothly rather than jumping. */
    private const val SMOOTH_SCROLL_TO_TOP_THRESHOLD = 25
  }

  private var filterViewOffsetChangeListener: AppBarLayout.OnOffsetChangedListener? = null

  private val binding: CallLogFragmentBinding by ViewBinderDelegate(CallLogFragmentBinding::bind) {
    binding.recyclerCoordinatorAppBar.removeOnOffsetChangedListener(filterViewOffsetChangeListener)
    // The panel dies with the view it lives in, so the bottom bar has to be given back explicitly --
    // the flag is on an activity-scoped view model and would otherwise outlive this screen.
    mainNavigationViewModel.setBottomBarSuppressed(false)
  }

  private val disposables = LifecycleDisposable()
  private val callLogContextMenu = CallLogContextMenu(this, this)
  private lateinit var callLogActionMode: CallLogActionMode
  private val conversationUpdateTick: ConversationUpdateTick = ConversationUpdateTick(this::onTimestampTick)
  private val backPressedCallback = OnBackPressed()

  private var reportedFirstDataSet = false

  private val viewModel: CallLogViewModel by activityViewModels()
  private val mainToolbarViewModel: MainToolbarViewModel by activityViewModels()
  private val mainNavigationViewModel: MainNavigationViewModel by activityViewModels()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    viewLifecycleOwner.lifecycle.addObserver(conversationUpdateTick)
    viewLifecycleOwner.lifecycle.addObserver(viewModel.callLogPeekHelper)

    callLogActionMode = CallLogActionMode(CallLogActionModeCallback(), mainToolbarViewModel)

    disposables.bindTo(viewLifecycleOwner)

    disposables += mainToolbarViewModel.getCallLogEventsFlowable().subscribeBy {
      when (it) {
        MainToolbarViewModel.Event.CallLog.ApplyFilter -> filterMissedCalls()
        MainToolbarViewModel.Event.CallLog.ClearFilter -> onClearFilterClicked()
        MainToolbarViewModel.Event.CallLog.ClearHistory -> clearCallHistory()
      }
    }

    binding.lightCallLog.callback = this
    binding.lightActionPanel.onDismiss = { dismissCallMenu() }

    disposables += Flowables.combineLatest(viewModel.data, viewModel.selected)
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { (data, selected) -> onCallLogChanged(data, selected) }

    disposables += Flowables.combineLatest(viewModel.selected, viewModel.totalCount)
      .distinctUntilChanged()
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { (selected, totalCount) ->
        if (selected.isNotEmpty(totalCount)) {
          callLogActionMode.start()
          callLogActionMode.setCount(selected.count(totalCount))
        } else if (mainToolbarViewModel.isInActionMode()) {
          callLogActionMode.end()
        }
      }

    binding.pullView.setPillText(R.string.CallLogFragment__filtered_by_missed)

    binding.bottomActionBar.setItems(
      listOf(
        ActionItem(
          iconRes = CoreUiR.drawable.symbol_check_circle_24,
          title = getString(R.string.CallLogFragment__select_all)
        ) {
          viewModel.selectAll()
        },
        ActionItem(
          iconRes = CoreUiR.drawable.symbol_trash_24,
          title = getString(R.string.CallLogFragment__delete),
          action = this::handleDeleteSelectedRows
        )
      )
    )

    initializePullToFilter()
    initializeTapToScrollToTop()

    // Scoped to the *view* lifecycle, not the fragment's: the handler reaches into the binding to
    // see whether the action panel is up, so it must not outlive the view that owns it.
    requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)
    viewLifecycleOwner.lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.RESUMED) {
        mainToolbarViewModel.state.collect {
          updateBackPressedState()
        }
      }
    }

    if (!resources.isSplitPane()) {
      ViewUtil.setBottomMargin(binding.bottomActionBar, ViewUtil.getNavigationBarHeight(binding.bottomActionBar))
    }
  }

  override fun onResume() {
    super.onResume()
    initializeSearchAction()
    AppDependencies.deletedCallEventManager.scheduleIfNecessary()
    viewModel.markAllCallEventsRead()
  }

  private fun onTimestampTick() {
    binding.lightCallLog.refreshTimestamps()
  }

  /**
   * Hands a new page of rows to the Light list.
   *
   * The old adapter dropped the paging source's `null` holes and let `PagingMappingAdapter.getItem`
   * ask for the missing pages as rows were bound. The Compose list keeps the holes as blank rows of
   * the right height and asks for data from the deepest row the user has reached instead, so the
   * scrollbar never lies about how long the list is while a page is in flight.
   */
  private fun onCallLogChanged(rows: List<CallLogRow?>, selection: CallLogSelectionState) {
    val firstVisibleItem = binding.lightCallLog.firstCompletelyVisibleItemPosition()

    binding.lightCallLog.submit(
      rows = rows,
      selection = selection,
      localDeviceCallRecipientId = viewModel.callLogPeekHelper.localDeviceCallRecipientId
    )

    // A new call lands at the top of the log. When the user was already parked there, follow it.
    if (firstVisibleItem == 0) {
      binding.lightCallLog.scrollToTop(smooth = false)
    }

    if (!reportedFirstDataSet && rows.isNotEmpty()) {
      reportedFirstDataSet = true
      (requireActivity() as? MainNavigator.NavigatorProvider)?.onFirstRender()
    }
  }

  private fun initializeTapToScrollToTop() {
    disposables += mainNavigationViewModel.tabClickEventsObservable
      .filter { it == MainNavigationListLocation.CALLS }
      .subscribeBy(onNext = {
        val firstVisibleItem = binding.lightCallLog.firstCompletelyVisibleItemPosition()
        binding.lightCallLog.scrollToTop(smooth = firstVisibleItem <= SMOOTH_SCROLL_TO_TOP_THRESHOLD)
      })
  }

  private fun handleDeleteSelectedRows() {
    val count = callLogActionMode.getCount()
    val selectionState = viewModel.selectionStateSnapshot
    val hasCallLinks = selectionState.isExclusionary() || selectionState.selected().any { it is CallLogRow.Id.CallLink }

    MaterialAlertDialogBuilder(requireContext())
      .setTitle(resources.getQuantityString(R.plurals.CallLogFragment__delete_d_calls, count, count))
      .apply {
        if (hasCallLinks) {
          setMessage(getString(R.string.CallLogFragment__call_links_youve_created))
        }
      }
      .setPositiveButton(R.string.CallLogFragment__delete) { _, _ ->
        performDeletion(count, viewModel.stageSelectionDeletion())
        callLogActionMode.end()
      }
      .setNegativeButton(android.R.string.cancel) { _, _ -> }
      .show()
  }

  private fun initializeSearchAction() {
    disposables += mainToolbarViewModel.getSearchEventsFlowable().subscribeBy {
      when (it) {
        MainToolbarViewModel.Event.Search.Close -> {
          viewModel.setSearchQuery("")
        }

        MainToolbarViewModel.Event.Search.Open -> {
          mainToolbarViewModel.setSearchHint(R.string.SearchToolbar_search)
        }

        is MainToolbarViewModel.Event.Search.Query -> {
          viewModel.setSearchQuery(it.query.trim())
        }
      }
    }
  }

  private fun initializePullToFilter() {
    val collapsingToolbarLayout = binding.collapsingToolbar
    val openHeight = DimensionUnit.DP.toPixels(FilterLerp.FILTER_OPEN_HEIGHT).toInt()

    binding.pullView.onFilterStateChanged = OnFilterStateChanged { state: FilterPullState?, source: ConversationFilterSource ->
      when (state) {
        FilterPullState.CLOSING -> {
          viewModel.setFilter(CallLogFilter.ALL)
          mainToolbarViewModel.setCallLogFilter(CallLogFilter.ALL)
          binding.lightCallLog.doAfterNextLayout {
            binding.lightCallLog.scrollToTop(smooth = false)
          }
        }

        FilterPullState.OPENING -> {
          ViewUtil.setMinimumHeight(collapsingToolbarLayout, openHeight)
          viewModel.setFilter(CallLogFilter.MISSED)
          mainToolbarViewModel.setCallLogFilter(CallLogFilter.MISSED)
        }

        FilterPullState.OPEN_APEX -> if (source === ConversationFilterSource.DRAG) {
          // TODO[alex] -- hint here? SignalStore.uiHints.incrementNeverDisplayPullToFilterTip()
        }

        FilterPullState.CLOSE_APEX -> ViewUtil.setMinimumHeight(collapsingToolbarLayout, 0)
        else -> Unit
      }
    }

    binding.pullView.onCloseClicked = OnCloseClicked {
      onClearFilterClicked()
    }

    val conversationFilterBehavior = Objects.requireNonNull<ConversationFilterBehavior?>((binding.recyclerCoordinatorAppBar.layoutParams as CoordinatorLayout.LayoutParams).behavior as ConversationFilterBehavior?)
    conversationFilterBehavior.callback = object : ConversationFilterBehavior.Callback {
      override fun onStopNestedScroll() {
        binding.pullView.onUserDragFinished()
      }

      override fun canStartNestedScroll(): Boolean {
        return !mainToolbarViewModel.isInActionMode() && !isSearchOpen()
      }
    }

    filterViewOffsetChangeListener = AppBarLayout.OnOffsetChangedListener { layout: AppBarLayout, verticalOffset: Int ->
      val progress = 1 - verticalOffset.toFloat() / -layout.height
      binding.pullView.onUserDrag(progress)
    }

    binding.recyclerCoordinatorAppBar.addOnOffsetChangedListener(filterViewOffsetChangeListener)

    if (viewModel.filterSnapshot != CallLogFilter.ALL) {
      binding.root.doAfterNextLayout {
        binding.pullView.openImmediate()
      }
    }
  }

  override fun onCreateCallLinkClicked() {
    CreateCallLinkBottomSheetDialogFragment().show(parentFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
  }

  override fun onCallClicked(call: CallLogRow.Call) {
    if (viewModel.selectionStateSnapshot.isNotEmpty(binding.lightCallLog.itemCount)) {
      viewModel.toggleSelected(call.id)
    } else if (!call.peer.isCallLink) {
      val intent = ConversationSettingsActivity.forCall(
        requireContext(),
        call.peer,
        (call.id as CallLogRow.Id.Call).children.toLongArray()
      )
      startActivity(intent)
    } else {
      goToCallLinkDetails(call.peer.requireCallLinkRoomId())
    }
  }

  override fun onCallLinkClicked(callLink: CallLogRow.CallLink) {
    if (viewModel.selectionStateSnapshot.isNotEmpty(binding.lightCallLog.itemCount)) {
      viewModel.toggleSelected(callLink.id)
    } else {
      mainNavigationViewModel.goTo(MainNavigationDetailLocation.CallLinkDetails(callLink.record.roomId))
    }
  }

  override fun onCallLongClicked(row: CallLogRow) {
    showCallMenu(row)
  }

  override fun onDataNeededAroundIndex(index: Int) {
    viewModel.controller.onDataNeededAroundIndex(index)
  }

  /**
   * Opens the Light action panel over the long-pressed call.
   *
   * The rows are Molly's own menu for that row: [CallLogContextMenu] decides which of video call,
   * audio call, go to chat, info, select and delete apply and gates them, exactly as it did for the
   * dropdown this replaces, so every one of them (and any the next upstream merge adds) arrives here
   * already correct.
   */
  private fun showCallMenu(row: CallLogRow) {
    val actions = when (row) {
      is CallLogRow.Call -> callLogContextMenu.getActions(row)
      is CallLogRow.CallLink -> callLogContextMenu.getActions(row)
      else -> return
    }

    showCallMenu(LightPanelActions.from(actions, onDismiss = { dismissCallMenu() }))
  }

  private fun showCallMenu(actions: List<LightPanelAction>) {
    if (actions.isEmpty()) {
      return
    }

    binding.lightActionPanel.show(actions)
    // The panel is half of whatever it is drawn in, and this fragment is drawn *above* the app's
    // bottom bar -- so without this the panel would stop a bar's height short of the screen edge and
    // leave its dismiss chevron floating over the tab icons. The bar steps aside instead, the
    // content slot grows to the full screen, and the panel lands on the bottom edge where the
    // reference client's does.
    mainNavigationViewModel.setBottomBarSuppressed(true)
    updateBackPressedState()
  }

  private fun dismissCallMenu() {
    if (!binding.lightActionPanel.isOpen) {
      return
    }

    binding.lightActionPanel.close()
    mainNavigationViewModel.setBottomBarSuppressed(false)
    updateBackPressedState()
  }

  private fun onClearFilterClicked() {
    binding.pullView.toggle()
    binding.recyclerCoordinatorAppBar.setExpanded(false, true)
  }

  override fun startSelection(call: CallLogRow) {
    callLogActionMode.start()
    viewModel.toggleSelected(call.id)
  }

  override fun goToCallLinkDetails(roomId: CallLinkRoomId) {
    mainNavigationViewModel.goTo(MainNavigationDetailLocation.CallLinkDetails(roomId))
  }

  override fun deleteCall(call: CallLogRow) {
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(resources.getQuantityString(R.plurals.CallLogFragment__delete_d_calls, 1, 1))
      .apply {
        if (call is CallLogRow.CallLink) {
          setMessage(getString(R.string.CallLogFragment__call_links_youve_created))
        }
      }
      .setPositiveButton(R.string.CallLogFragment__delete) { _, _ ->
        performDeletion(1, viewModel.stageCallDeletion(call))
      }
      .show()
  }

  private fun filterMissedCalls() {
    binding.pullView.toggle()
    binding.recyclerCoordinatorAppBar.setExpanded(false, true)
  }

  private fun clearCallHistory() {
    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.CallLogFragment__clear_call_history_question)
      .setMessage(R.string.CallLogFragment__this_will_permanently_delete_all_call_history)
      .setPositiveButton(android.R.string.ok) { _, _ ->
        callLogActionMode.end()
        performDeletion(-1, viewModel.stageDeleteAll())
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun isSearchOpen(): Boolean {
    return isSearchVisible() || viewModel.hasSearchQuery
  }

  private fun closeSearchIfOpen(): Boolean {
    if (isSearchOpen()) {
      mainToolbarViewModel.setToolbarMode(MainToolbarMode.FULL)
      return true
    }
    return false
  }

  private fun isSearchVisible(): Boolean {
    return mainToolbarViewModel.state.value.mode == MainToolbarMode.SEARCH
  }

  /**
   * Back has two things to undo on this tab, and the callback has to be enabled for either of them
   * or the press falls straight through to the activity.
   */
  private fun updateBackPressedState() {
    backPressedCallback.isEnabled = binding.lightActionPanel.isOpen || isSearchVisible()
  }

  private fun performDeletion(count: Int, callLogStagedDeletion: CallLogStagedDeletion) {
    var progressDialog: ProgressCardDialogFragment? = null
    var errorDialog: AlertDialog? = null

    fun cleanUp() {
      progressDialog?.dismissAllowingStateLoss()
      progressDialog = null
      errorDialog?.dismiss()
      errorDialog = null
    }

    val snackbarMessage = if (count == -1) {
      getString(R.string.CallLogFragment__cleared_call_history)
    } else {
      resources.getQuantityString(R.plurals.CallLogFragment__d_calls_deleted, count, count)
    }

    viewModel.delete(callLogStagedDeletion)
      .observeOn(AndroidSchedulers.mainThread())
      .doOnSubscribe {
        progressDialog = ProgressCardDialogFragment.create(getString(R.string.CallLogFragment__deleting))
        progressDialog?.show(parentFragmentManager, null)
      }
      .doOnDispose { cleanUp() }
      .subscribeBy {
        cleanUp()
        when (it) {
          CallLogDeletionResult.Empty -> Unit
          is CallLogDeletionResult.FailedToRevoke -> {
            errorDialog = MaterialAlertDialogBuilder(requireContext())
              .setMessage(resources.getQuantityString(R.plurals.CallLogFragment__cant_delete_call_link, it.failedRevocations))
              .setPositiveButton(android.R.string.ok, null)
              .show()
          }

          CallLogDeletionResult.Success -> {
            mainNavigationViewModel.snackbarRegistry.emit(
              SnackbarState(
                message = snackbarMessage,
                duration = Snackbars.Duration.SHORT,
                hostKey = MainSnackbarHostKey.MainChrome
              )
            )
          }

          is CallLogDeletionResult.UnknownFailure -> {
            Log.w(TAG, "Deletion failed.", it.reason)
            Toast.makeText(requireContext(), R.string.CallLogFragment__deletion_failed, Toast.LENGTH_SHORT).show()
          }
        }
      }
      .addTo(disposables)
  }

  inner class CallLogActionModeCallback : CallLogActionMode.Callback {
    override fun startActionMode() {
      requireListener<Callback>().onMultiSelectStarted()
      // The multi-select bar is animated straight in and out, as the chat list's is. The
      // SignalBottomActionBarController that used to do it also re-padded and re-scrolled a
      // RecyclerView underneath, and there is no longer one to re-pad.
      ViewUtil.animateIn(binding.bottomActionBar, binding.bottomActionBar.enterAnimation)
    }

    override fun onActionModeWillEnd() {
      requireListener<Callback>().onMultiSelectFinished()
      if (binding.bottomActionBar.visible) {
        ViewUtil.animateOut(binding.bottomActionBar, binding.bottomActionBar.exitAnimation)
      }
      viewModel.clearSelected()
    }

    override fun getResources(): Resources = resources
    override fun onResetSelectionState() {
      viewModel.clearSelected()
    }
  }

  private inner class OnBackPressed : OnBackPressedCallback(enabled = false) {
    override fun handleOnBackPressed() {
      if (binding.lightActionPanel.isOpen) {
        dismissCallMenu()
        return
      }

      closeSearchIfOpen()
    }
  }

  interface Callback {
    fun onMultiSelectStarted()
    fun onMultiSelectFinished()
  }
}
