package org.thoughtcrime.securesms.mediasend.v2.review

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.signal.core.models.media.Media
import org.signal.core.ui.BottomSheetUtil
import org.signal.core.ui.permissions.Permissions
import org.signal.core.util.bytes
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.concurrent.SimpleTask
import org.signal.core.util.logging.Log
import org.signal.mediasend.MediaConstraints
import org.signal.mediasend.SentMediaQuality
import org.signal.mediasend.edit.video.VideoThumbnailsRangeSelectorView
import org.signal.mediasend.edit.video.VideoTrimData
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.conversation.ReenableScheduledMessagesDialogFragment
import org.thoughtcrime.securesms.conversation.ScheduleMessageContextMenu
import org.thoughtcrime.securesms.conversation.ScheduleMessageDialogCallback
import org.thoughtcrime.securesms.conversation.ScheduleMessageTimePickerBottomSheet
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardActivity
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.light.LightActionPanelView
import org.thoughtcrime.securesms.light.LightPanelAction
import org.thoughtcrime.securesms.media.DecryptableUriMediaInput
import org.thoughtcrime.securesms.mediasend.MediaSendActivityResult
import org.thoughtcrime.securesms.mediasend.v2.HudCommand
import org.thoughtcrime.securesms.mediasend.v2.MediaAnimations
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionDestination
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionNavigator
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionState
import org.thoughtcrime.securesms.mediasend.v2.MediaSelectionViewModel
import org.thoughtcrime.securesms.mediasend.v2.stories.StoriesMultiselectForwardActivity
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.scribbles.ImageEditorFragment
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.SystemWindowInsetsSetter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.fragments.requireListener
import org.thoughtcrime.securesms.util.views.TouchInterceptingFrameLayout
import org.thoughtcrime.securesms.util.visible
import org.thoughtcrime.securesms.video.TranscodingQuality
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.signal.core.ui.R as CoreUiR

/** Allows the user to view, edit and send selected media with Light review chrome. */
class MediaReviewFragment : Fragment(R.layout.v2_media_review_fragment), ScheduleMessageTimePickerBottomSheet.ScheduleCallback, ScheduleMessageDialogCallback, VideoThumbnailsRangeSelectorView.RangeDragListener {

  private val sharedViewModel: MediaSelectionViewModel by viewModels(
    ownerProducer = { requireActivity() }
  )

  private lateinit var callback: Callback
  private lateinit var topView: LightMediaReviewTopView
  private lateinit var bottomView: LightMediaReviewBottomView
  private lateinit var actionPanel: LightActionPanelView
  private lateinit var pager: ViewPager2
  private lateinit var controls: ViewGroup
  private lateinit var selectionRecycler: RecyclerView
  private lateinit var videoTimeLine: VideoThumbnailsRangeSelectorView
  private lateinit var videoSizeHint: TextView
  private lateinit var videoTimelinePlaceholder: View
  private lateinit var progress: ProgressBar
  private lateinit var progressWrapper: TouchInterceptingFrameLayout
  private lateinit var multiselectLauncher: ActivityResultLauncher<MultiselectForwardFragmentArgs>
  private lateinit var storiesLauncher: ActivityResultLauncher<StoriesMultiselectForwardActivity.Args>

  private val exclusionZone = listOf(Rect())
  private val navigator = MediaSelectionNavigator(
    toGallery = R.id.action_mediaReviewFragment_to_mediaGalleryFragment
  )

  private var disposables: LifecycleDisposable = LifecycleDisposable()
  private var sentMediaQuality: SentMediaQuality = SignalStore.settings.sentMediaQuality
  private var viewOnceToggleState: MediaSelectionState.ViewOnceToggleState = MediaSelectionState.ViewOnceToggleState.default
  private var scheduledSendTime: Long? = null
  private var readyToSend = true

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    postponeEnterTransition()
    SystemWindowInsetsSetter.attach(view, viewLifecycleOwner)
    disposables.bindTo(viewLifecycleOwner)

    parentFragmentManager.setFragmentResultListener(AddMessageDialogFragment.REQUEST_KEY, viewLifecycleOwner) { _, bundle ->
      if (bundle.getBoolean(AddMessageDialogFragment.RESULT_INCREMENT_VIEW_ONCE_STATE)) {
        sharedViewModel.setMessage(null)
        sharedViewModel.incrementViewOnceState()
      } else {
        sharedViewModel.setMessage(bundle.getCharSequence(AddMessageDialogFragment.RESULT_MESSAGE, null))
      }
    }

    callback = requireListener()
    topView = view.findViewById(R.id.light_review_top)
    bottomView = view.findViewById(R.id.light_review_bottom)
    actionPanel = view.findViewById(R.id.light_action_panel)
    pager = view.findViewById(R.id.media_pager)
    controls = view.findViewById(R.id.controls)
    selectionRecycler = view.findViewById(R.id.selection_recycler)
    progress = view.findViewById(R.id.progress)
    progressWrapper = view.findViewById(R.id.progress_wrapper)
    videoTimeLine = view.findViewById(R.id.video_timeline)
    videoSizeHint = view.findViewById(R.id.video_size_hint)
    videoTimelinePlaceholder = view.findViewById(R.id.timeline_placeholder)

    progress.indeterminateDrawable.setTint(Color.WHITE)
    progressWrapper.setOnInterceptTouchEventListener { true }

    topView.onBack = ::onTopBackPressed
    bottomView.onCaptionClick = ::onCaptionClicked
    bottomView.onActionClick = ::onLightActionClicked
    bottomView.onSendLongClick = ::onSendLongClicked
    actionPanel.onDismiss = ::closeActionPanel

    val pagerAdapter = MediaReviewFragmentPagerAdapter(this)
    pager.adapter = pagerAdapter

    disposables += sharedViewModel.hudCommands.subscribe {
      when (it) {
        HudCommand.ResumeEntryTransition -> startPostponedEnterTransition()
        else -> Unit
      }
    }

    controls.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
      val outRect = exclusionZone[0]
      outRect.set(0, 0, videoTimeLine.width, videoTimeLine.height)
      controls.offsetDescendantRectToMyCoords(videoTimeLine, outRect)
      outRect.left = 0
      outRect.right = controls.width
      androidx.core.view.ViewCompat.setSystemGestureExclusionRects(controls, exclusionZone)
    }

    val multiselectContract = MultiselectForwardActivity.SelectionContract()
    val storiesContract = StoriesMultiselectForwardActivity.SelectionContract()

    multiselectLauncher = registerForActivityResult(multiselectContract) { keys ->
      if (keys.isNotEmpty()) {
        Log.d(TAG, "Performing send from multi-select activity result.")
        performSend(keys)
      } else {
        setReadyToSend(true)
      }
    }

    storiesLauncher = registerForActivityResult(storiesContract) { keys ->
      if (keys.isNotEmpty()) {
        Log.d(TAG, "Performing send from stories activity result.")
        performSend(keys)
      } else {
        setReadyToSend(true)
      }
    }

    pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
      override fun onPageSelected(position: Int) {
        sharedViewModel.onPageChanged(position)
      }
    })

    if (MediaConstraints.isVideoTranscodeAvailable()) {
      videoTimeLine.registerEditorOnRangeChangeListener(this)
    }

    val selectionAdapter = MappingAdapter(false)
    MediaReviewAddItem.register(selectionAdapter, ::launchGallery)
    MediaReviewSelectedItem.register(selectionAdapter) { media, isSelected ->
      if (isSelected) {
        sharedViewModel.removeMedia(media)
      } else {
        sharedViewModel.onPageChanged(media)
      }
    }
    selectionRecycler.adapter = selectionAdapter
    ItemTouchHelper(MediaSelectionItemTouchHelper(sharedViewModel)).attachToRecyclerView(selectionRecycler)

    sharedViewModel.state.observe(viewLifecycleOwner) { state ->
      pagerAdapter.submitMedia(state.selectedMedia)
      selectionAdapter.submitList(
        state.selectedMedia.map {
          val trimStartTimeUs = (state.editorStateMap[it.uri] as? VideoTrimData)?.startTimeUs ?: 0L
          MediaReviewSelectedItem.Model(it, state.focusedMedia == it, trimStartTimeUs)
        } + MediaReviewAddItem.Model
      )

      presentPager(state)
      presentLightChrome(state)
      if (state.quality != sentMediaQuality) {
        presentQualityToggleToast(state)
      }
      sentMediaQuality = state.quality

      if (state.viewOnceToggleState != viewOnceToggleState &&
        state.viewOnceToggleState == MediaSelectionState.ViewOnceToggleState.ONCE &&
        state.selectedMedia.size == 1
      ) {
        presentViewOnceToggleToast(MediaUtil.isNonGifVideo(state.selectedMedia[0]))
      }
      viewOnceToggleState = state.viewOnceToggleState

      presentVideoTimeline(state)
      presentVideoSizeHint(state)
      presentVisibleLegacyRegions(state)
    }

    requireActivity().onBackPressedDispatcher.addCallback(
      viewLifecycleOwner,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          if (actionPanel.isOpen) {
            closeActionPanel()
          } else {
            callback.onPopFromReview()
          }
        }
      }
    )

  }

  private fun onTopBackPressed() {
    // Dispatch rather than directly popping so an active ImageEditorFragment callback can unwind its
    // higher-priority crop/draw/text mode before this fragment's origin-aware callback runs.
    requireActivity().onBackPressedDispatcher.onBackPressed()
  }

  private fun onCaptionClicked() {
    val state = sharedViewModel.state.value ?: return
    AddMessageDialogFragment.show(
      parentFragmentManager,
      state.message,
      false,
      state.selectedMedia.size == 1 && !state.isStory && !MediaUtil.isDocumentType(state.focusedMedia?.contentType),
      sharedViewModel.destination.getRecipientSearchKey()?.recipientId
    )
  }

  private fun onLightActionClicked(action: LightMediaReviewAction) {
    when (action) {
      LightMediaReviewAction.ADD -> launchGallery()
      LightMediaReviewAction.SELECT_OFF,
      LightMediaReviewAction.SELECT_ON -> sharedViewModel.incrementViewOnceState()
      LightMediaReviewAction.ELLIPSES -> showMoreActions()
      LightMediaReviewAction.SEND -> onSendRequested()
    }
  }

  private fun showMoreActions() {
    val tools = sharedViewModel.state.value?.let(::createLightState)?.secondaryTools.orEmpty()
    if (tools.isEmpty()) return

    actionPanel.show(
      tools.map { tool ->
        LightPanelAction(
          label = getToolLabel(tool).uppercase(Locale.getDefault()),
          onSelected = {
            closeActionPanel()
            onToolSelected(tool)
          }
        )
      }
    )
  }

  private fun getToolLabel(tool: LightMediaReviewTool): String {
    return when (tool) {
      LightMediaReviewTool.DRAW -> getString(R.string.ImageEditorHud__draw)
      LightMediaReviewTool.CROP_AND_ROTATE -> getString(R.string.MediaReviewFragment__crop_rotate_accessibility_label)
      LightMediaReviewTool.QUALITY -> getString(R.string.QualitySelectorBottomSheetDialog__media_quality)
      LightMediaReviewTool.SAVE -> getString(R.string.save)
    }
  }

  private fun onToolSelected(tool: LightMediaReviewTool) {
    when (tool) {
      LightMediaReviewTool.DRAW -> sharedViewModel.sendCommand(HudCommand.StartDraw)
      LightMediaReviewTool.CROP_AND_ROTATE -> sharedViewModel.sendCommand(HudCommand.StartCropAndRotate)
      LightMediaReviewTool.QUALITY -> QualitySelectorBottomSheet().show(parentFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
      LightMediaReviewTool.SAVE -> sharedViewModel.sendCommand(HudCommand.SaveMedia)
    }
  }

  private fun closeActionPanel() {
    actionPanel.close()
  }

  private fun onSendLongClicked() {
    if (!readyToSend || sharedViewModel.isStory()) return
    ScheduleMessageContextMenu.show(bottomView, requireView() as ViewGroup) { time: Long ->
      if (time == -1L) {
        scheduledSendTime = null
        ScheduleMessageTimePickerBottomSheet.showSchedule(childFragmentManager)
      } else {
        startScheduledSend(time)
      }
    }
  }

  private fun onSendRequested() {
    if (!readyToSend) {
      Log.d(TAG, "Attachment send button not currently enabled. Ignoring click event.")
      return
    }

    Log.d(TAG, "Attachment send button enabled. Processing click event.")
    setReadyToSend(false)

    val viewOnce = sharedViewModel.state.value?.viewOnceToggleState == MediaSelectionState.ViewOnceToggleState.ONCE

    if (sharedViewModel.isContactSelectionRequired) {
      val args = MultiselectForwardFragmentArgs(
        title = R.string.MediaReviewFragment__send_to,
        storySendRequirements = sharedViewModel.getStorySendRequirements(),
        isSearchEnabled = !sharedViewModel.isStory(),
        isViewOnce = viewOnce
      )

      if (sharedViewModel.isStory()) {
        val snapshot = sharedViewModel.state.value
        if (snapshot != null) {
          setReadyToSend(false)
          SimpleTask.run(viewLifecycleOwner.lifecycle, {
            snapshot.selectedMedia.take(2).map { media ->
              val editorData = snapshot.editorStateMap[media.uri]
              if (MediaUtil.isImageType(media.contentType) && editorData is ImageEditorFragment.Data) {
                val model = editorData.readModel()
                if (model != null) {
                  ImageEditorFragment.renderToSingleSessionBlob(requireContext(), model)
                } else {
                  media.uri
                }
              } else {
                media.uri
              }
            }
          }, {
            storiesLauncher.launch(StoriesMultiselectForwardActivity.Args(args, it))
          })
        } else {
          storiesLauncher.launch(StoriesMultiselectForwardActivity.Args(args, emptyList()))
        }
        scheduledSendTime = null
      } else {
        multiselectLauncher.launch(args)
      }
    } else if (sharedViewModel.isAddToGroupStoryFlow) {
      MaterialAlertDialogBuilder(requireContext())
        .setMessage(getString(R.string.MediaReviewFragment__add_to_the_group_story, sharedViewModel.state.value!!.recipient!!.getDisplayName(requireContext())))
        .setPositiveButton(R.string.MediaReviewFragment__add_to_story) { _, _ ->
          Log.d(TAG, "Performing send add to group story dialog.")
          performSend()
        }
        .setNegativeButton(android.R.string.cancel) { _, _ -> setReadyToSend(true) }
        .setOnCancelListener { setReadyToSend(true) }
        .setOnDismissListener { setReadyToSend(true) }
        .show()
      scheduledSendTime = null
    } else {
      Log.d(TAG, "Performing send from send action.")
      performSend()
    }
  }

  private fun presentLightChrome(state: MediaSelectionState) {
    val projected = createLightState(state)
    topView.submit(projected)
    bottomView.submit(projected)

    if (!state.isTouchEnabled && actionPanel.isOpen) {
      closeActionPanel()
    }
  }

  private fun createLightState(state: MediaSelectionState): LightMediaReviewState {
    val focused = state.focusedMedia
    val mediaType = when {
      MediaUtil.isDocumentType(focused?.contentType) -> LightMediaReviewMediaType.DOCUMENT
      MediaUtil.isGif(focused?.contentType) -> LightMediaReviewMediaType.GIF
      MediaUtil.isVideoType(focused?.contentType) -> LightMediaReviewMediaType.VIDEO
      else -> LightMediaReviewMediaType.IMAGE
    }

    return LightMediaReviewState.map(
      destinationLabel = resolveDestinationLabel(state),
      message = state.message,
      messagePlaceholder = getString(
        if (sharedViewModel.isReply) R.string.MediaReviewFragment__add_a_reply else R.string.MediaReviewFragment__add_a_message
      ),
      mediaType = mediaType,
      selectedCount = state.selectedMedia.size,
      isStory = state.isStory,
      isTouchEnabled = state.isTouchEnabled,
      isViewOnce = state.viewOnceToggleState == MediaSelectionState.ViewOnceToggleState.ONCE,
      sendEnabled = readyToSend && state.canSend
    )
  }

  private fun resolveDestinationLabel(state: MediaSelectionState): String {
    state.recipient?.let { recipient ->
      return if (recipient.isSelf) getString(R.string.note_to_self) else recipient.getDisplayName(requireContext())
    }

    return when (sharedViewModel.destination) {
      MediaSelectionDestination.Wallpaper -> getString(R.string.ChatWallpaperFragment__set_wallpaper)
      MediaSelectionDestination.Avatar -> getString(R.string.ContactShareEditActivity__avatar)
      else -> getString(R.string.MediaReviewFragment__send_to)
    }
  }

  private fun setReadyToSend(ready: Boolean) {
    readyToSend = ready
    sharedViewModel.state.value?.let(::presentLightChrome)
  }

  private fun presentVisibleLegacyRegions(state: MediaSelectionState) {
    val isDocument = MediaUtil.isDocumentType(state.focusedMedia?.contentType)
    val showSelectionRail = state.isTouchEnabled &&
      state.viewOnceToggleState != MediaSelectionState.ViewOnceToggleState.ONCE &&
      !isDocument &&
      state.selectedMedia.size > 1

    selectionRecycler.visible = showSelectionRail
    bottomView.visible = state.isTouchEnabled
    videoTimelinePlaceholder.visible = state.isVideoTrimmingVisible
    videoTimeLine.visible = state.isVideoTrimmingVisible
    videoSizeHint.visible = state.isVideoTrimmingVisible && state.isTouchEnabled
  }

  private fun presentViewOnceToggleToast(isVideo: Boolean) {
    val description = if (isVideo) {
      getString(R.string.MediaReviewFragment__video_set_to_view_once)
    } else {
      getString(R.string.MediaReviewFragment__photo_set_to_view_once)
    }
    MediaReviewToastPopupWindow.show(controls, R.drawable.symbol_view_once_24, description)
  }

  private fun presentQualityToggleToast(state: MediaSelectionState) {
    val mediaList = state.selectedMedia
    if (mediaList.isEmpty()) return

    val description = if (mediaList.size == 1) {
      val media = mediaList[0]
      if (MediaUtil.isNonGifVideo(media)) {
        if (state.quality == SentMediaQuality.HIGH) {
          getString(R.string.MediaReviewFragment__video_set_to_high_quality)
        } else {
          getString(R.string.MediaReviewFragment__video_set_to_standard_quality)
        }
      } else if (MediaUtil.isImageType(media.contentType)) {
        if (state.quality == SentMediaQuality.HIGH) {
          getString(R.string.MediaReviewFragment__photo_set_to_high_quality)
        } else {
          getString(R.string.MediaReviewFragment__photo_set_to_standard_quality)
        }
      } else {
        Log.i(TAG, "Could not display quality toggle toast for attachment of type: ${media.contentType}")
        return
      }
    } else if (state.quality == SentMediaQuality.HIGH) {
      resources.getQuantityString(R.plurals.MediaReviewFragment__items_set_to_high_quality, mediaList.size, mediaList.size)
    } else {
      resources.getQuantityString(R.plurals.MediaReviewFragment__items_set_to_standard_quality, mediaList.size, mediaList.size)
    }

    val icon = when (state.quality) {
      SentMediaQuality.HIGH -> CoreUiR.drawable.symbol_quality_high_24
      else -> CoreUiR.drawable.symbol_quality_high_slash_24
    }
    MediaReviewToastPopupWindow.show(controls, icon, description)
  }

  override fun onResume() {
    super.onResume()
    sharedViewModel.kick()
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
  }

  private fun launchGallery() {
    navigator.goToGallery(findNavController())
  }

  private fun performSend(selection: List<ContactSearchKey> = listOf()) {
    Log.d(TAG, "Performing attachment send.")
    setReadyToSend(false)
    progressWrapper.visible = true
    progressWrapper.animate()
      .setStartDelay(300)
      .setInterpolator(MediaAnimations.interpolator)
      .alpha(1f)

    sharedViewModel
      .send(selection.filterIsInstance<ContactSearchKey.RecipientSearchKey>(), scheduledSendTime)
      .subscribe(
        { result ->
          callback.onSentWithResult(result)
          setReadyToSend(true)
        },
        { error ->
          callback.onSendError(error)
          setReadyToSend(true)
        },
        {
          callback.onSentWithoutResult()
          setReadyToSend(true)
        }
      )
  }

  private fun presentPager(state: MediaSelectionState) {
    pager.isUserInputEnabled = state.isTouchEnabled
    val indexOfSelectedItem = state.selectedMedia.indexOf(state.focusedMedia)
    if (pager.currentItem == indexOfSelectedItem) return
    if (indexOfSelectedItem != -1) {
      pager.setCurrentItem(indexOfSelectedItem, false)
    } else {
      pager.setCurrentItem(0, false)
    }
  }

  private fun presentVideoTimeline(state: MediaSelectionState) {
    val mediaItem = state.focusedMedia ?: return
    if (!MediaUtil.isVideoType(mediaItem.contentType) || !MediaConstraints.isVideoTranscodeAvailable()) return

    val uri = mediaItem.uri
    val updatedInputInTimeline = videoTimeLine.setInput(uri, DecryptableUriMediaInput)
    if (updatedInputInTimeline) {
      videoTimeLine.unregisterDragListener()
    }
    val size = tryGetUriSize(requireContext(), uri, Long.MAX_VALUE)
    val maxSend = sharedViewModel.getMediaConstraints().getEditorVideoMaxSize()
    if (size > maxSend) {
      videoTimeLine.setTimeLimit(state.transcodingPreset.calculateMaxVideoUploadDurationInSeconds(maxSend), TimeUnit.SECONDS)
    }

    if (state.isTouchEnabled) {
      val data = state.getOrCreateVideoTrimData(uri)
      if (data.totalInputDurationUs > 0) {
        videoTimeLine.setRange(data.startTimeUs, data.endTimeUs)
      }
    }
  }

  private fun presentVideoSizeHint(state: MediaSelectionState) {
    val focusedMedia = state.focusedMedia ?: return
    val trimData = state.getOrCreateVideoTrimData(focusedMedia.uri)
    videoSizeHint.text = if (state.isVideoTrimmingVisible) {
      val seconds = trimData.getDuration().inWholeSeconds
      val bytes = TranscodingQuality.createFromPreset(state.transcodingPreset, trimData.getDuration().inWholeMilliseconds).byteCountEstimate
      String.format(Locale.getDefault(), "%d:%02d • %s", seconds / 60, seconds % 60, bytes.bytes.toUnitString())
    } else {
      null
    }
  }

  override fun onScheduleSend(scheduledTime: Long) {
    startScheduledSend(scheduledTime)
  }

  override fun onSchedulePermissionsGranted(metricId: String?, scheduledDate: Long) {
    scheduledSendTime = scheduledDate
    onSendRequested()
  }

  private fun startScheduledSend(scheduledTime: Long) {
    if (ReenableScheduledMessagesDialogFragment.showIfNeeded(requireContext(), childFragmentManager, null, scheduledTime)) {
      return
    }
    scheduledSendTime = scheduledTime
    onSendRequested()
  }

  override fun onRangeDrag(minValue: Long, maxValue: Long, duration: Long, end: Boolean) {
    sharedViewModel.onEditVideoDuration(totalDurationUs = duration, startTimeUs = minValue, endTimeUs = maxValue, touchEnabled = end)
  }

  companion object {
    private val TAG = Log.tag(MediaReviewFragment::class.java)

    @JvmStatic
    private fun tryGetUriSize(context: Context, uri: Uri, defaultValue: Long): Long {
      return try {
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null).use { cursor ->
          if (cursor != null && cursor.moveToFirst() && cursor.getColumnIndex(OpenableColumns.SIZE) >= 0) {
            size = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))
          }
        }
        if (size <= 0) size = MediaUtil.getMediaSize(context, uri)
        size
      } catch (e: IOException) {
        Log.w(TAG, e)
        defaultValue
      }
    }
  }

  interface Callback {
    fun onSentWithResult(mediaSendActivityResult: MediaSendActivityResult)
    fun onSentWithoutResult()
    fun onSendError(error: Throwable)
    fun onNoMediaSelected()
    fun onPopFromReview()
  }
}
