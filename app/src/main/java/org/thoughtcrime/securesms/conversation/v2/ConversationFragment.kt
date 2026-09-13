/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Browser
import android.provider.ContactsContract
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.TextView.OnEditorActionListener
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SearchView
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentResultListener
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.ConversationLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.R as MaterialR
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.BaseTransientBottomBar.Duration
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import com.thelightphone.sdk.ui.LightTextVariant
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.models.media.Media
import org.signal.core.models.media.TransformProperties
import org.signal.core.ui.BottomSheetUtil
import org.signal.core.ui.getWindowSizeClass
import org.signal.core.ui.isSplitPane
import org.signal.core.ui.logging.LoggingFragment
import org.signal.core.ui.permissions.Permissions
import org.signal.core.ui.util.ThemeUtil
import org.signal.core.ui.view.Stub
import org.signal.core.util.ByteLimitInputFilter
import org.signal.core.util.Debouncer
import org.signal.core.util.DrawableUtil
import org.signal.core.util.PendingIntentFlags
import org.signal.core.util.Result
import org.signal.core.util.ThreadUtil
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.concurrent.ListenableFuture
import org.signal.core.util.concurrent.addTo
import org.signal.core.util.dp
import org.signal.core.util.logging.Log
import org.signal.core.util.orNull
import org.signal.core.util.requireParcelableCompat
import org.signal.ringrtc.CallLinkRootKey
import org.thoughtcrime.securesms.BlockUnblockDialog
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.MuteDialog
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.attachments.AttachmentSaver
import org.thoughtcrime.securesms.audio.AudioRecorder
import org.thoughtcrime.securesms.backup.v2.ui.subscription.BackupUpgradeAvailabilityChecker
import org.thoughtcrime.securesms.backup.v2.ui.warning.guardAgainstRecoveryKeyPaste
import org.thoughtcrime.securesms.billing.upgrade.UpgradeToStartMediaBackupSheet
import org.thoughtcrime.securesms.calls.YouAreAlreadyInACallSnackbar
import org.thoughtcrime.securesms.components.AnimatingToggle
import org.thoughtcrime.securesms.components.ComposeText
import org.thoughtcrime.securesms.components.ConversationSearchBottomBar
import org.thoughtcrime.securesms.components.HidingLinearLayout
import org.thoughtcrime.securesms.components.InputAwareConstraintLayout
import org.thoughtcrime.securesms.components.InputPanel
import org.thoughtcrime.securesms.components.InsetAwareConstraintLayout
import org.thoughtcrime.securesms.components.ProgressCardDialogFragment
import org.thoughtcrime.securesms.components.RotatedTiledDrawable
import org.thoughtcrime.securesms.components.ScrollToPositionDelegate
import org.thoughtcrime.securesms.components.SendButton
import org.thoughtcrime.securesms.components.SignalProgressDialog
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.components.compose.ActionModeTopBarView
import org.thoughtcrime.securesms.components.compose.DeleteSyncEducationDialog
import org.thoughtcrime.securesms.components.emoji.EmojiEventListener
import org.thoughtcrime.securesms.components.emoji.MediaKeyboard
import org.thoughtcrime.securesms.components.emoji.RecentEmojiPageModel
import org.thoughtcrime.securesms.components.location.SignalPlace
import org.thoughtcrime.securesms.components.mention.MentionAnnotation
import org.thoughtcrime.securesms.components.menu.ActionItem
import org.thoughtcrime.securesms.components.menu.SignalBottomActionBar
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity
import org.thoughtcrime.securesms.components.snackbars.makeSnackbar
import org.thoughtcrime.securesms.components.spoiler.SpoilerAnnotation
import org.thoughtcrime.securesms.components.voice.VoiceNoteDraft
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaControllerOwner
import org.thoughtcrime.securesms.components.voice.VoiceNotePlaybackState
import org.thoughtcrime.securesms.components.voice.VoiceNotePlayerView
import org.thoughtcrime.securesms.compose.FragmentBackPressedInfo
import org.thoughtcrime.securesms.compose.FragmentBackPressedInfoProvider
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey.RecipientSearchKey
import org.thoughtcrime.securesms.contactshare.Contact
import org.thoughtcrime.securesms.contactshare.ContactUtil
import org.thoughtcrime.securesms.contactshare.SharedContactDetailsActivity
import org.thoughtcrime.securesms.conversation.AttachmentKeyboardButton
import org.thoughtcrime.securesms.conversation.BadDecryptLearnMoreDialog
import org.thoughtcrime.securesms.conversation.ConversationAdapter
import org.thoughtcrime.securesms.conversation.ConversationArgs
import org.thoughtcrime.securesms.conversation.ConversationBottomSheetCallback
import org.thoughtcrime.securesms.conversation.ConversationData
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.conversation.ConversationIntents.ConversationScreenType
import org.thoughtcrime.securesms.conversation.ConversationItem
import org.thoughtcrime.securesms.conversation.ConversationItemSwipeCallback
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.conversation.ConversationOptionsMenu
import org.thoughtcrime.securesms.conversation.ConversationReactionOverlay
import org.thoughtcrime.securesms.conversation.ConversationReactionOverlay.OnActionSelectedListener
import org.thoughtcrime.securesms.conversation.ConversationSearchViewModel
import org.thoughtcrime.securesms.conversation.ConversationUpdateTick
import org.thoughtcrime.securesms.conversation.MarkReadHelper
import org.thoughtcrime.securesms.conversation.MenuState
import org.thoughtcrime.securesms.conversation.MessageSendType
import org.thoughtcrime.securesms.conversation.MessageStyler.getStyling
import org.thoughtcrime.securesms.conversation.PinnedMessagesBottomSheet
import org.thoughtcrime.securesms.conversation.ReenableScheduledMessagesDialogFragment
import org.thoughtcrime.securesms.conversation.ScheduleMessageContextMenu
import org.thoughtcrime.securesms.conversation.ScheduleMessageDialogCallback
import org.thoughtcrime.securesms.conversation.ScheduleMessageTimePickerBottomSheet
import org.thoughtcrime.securesms.conversation.ScheduleMessageTimePickerBottomSheet.Companion.showSchedule
import org.thoughtcrime.securesms.conversation.ScheduledMessagesBottomSheet
import org.thoughtcrime.securesms.conversation.ScheduledMessagesRepository
import org.thoughtcrime.securesms.conversation.ShowAdminsBottomSheetDialog
import org.thoughtcrime.securesms.conversation.clicklisteners.PollVotesFragment
import org.thoughtcrime.securesms.conversation.colors.ChatColors
import org.thoughtcrime.securesms.conversation.colors.ColorizerV2
import org.thoughtcrime.securesms.conversation.colors.RecyclerViewColorizer
import org.thoughtcrime.securesms.conversation.drafts.DraftRepository
import org.thoughtcrime.securesms.conversation.drafts.DraftRepository.ShareOrDraftData
import org.thoughtcrime.securesms.conversation.drafts.DraftViewModel
import org.thoughtcrime.securesms.conversation.mutiselect.ConversationItemAnimator
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectItemDecoration
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectPart
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardBottomSheet
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragment
import org.thoughtcrime.securesms.conversation.mutiselect.forward.MultiselectForwardFragmentArgs
import org.thoughtcrime.securesms.conversation.quotes.MessageQuotesBottomSheet
import org.thoughtcrime.securesms.conversation.ui.edit.EditMessageHistoryDialog
import org.thoughtcrime.securesms.conversation.ui.error.EnableCallNotificationSettingsDialog
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQuery
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQueryChangedListener
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQueryReplacement
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQueryResultsControllerV2
import org.thoughtcrime.securesms.conversation.ui.inlinequery.InlineQueryViewModelV2
import org.thoughtcrime.securesms.conversation.v2.computed.ConversationMessageComputeWorkers
import org.thoughtcrime.securesms.conversation.v2.data.AvatarDownloadStateCache
import org.thoughtcrime.securesms.conversation.v2.data.ConversationMessageElement
import org.thoughtcrime.securesms.conversation.v2.groups.ConversationGroupCallViewModel
import org.thoughtcrime.securesms.conversation.v2.groups.ConversationGroupViewModel
import org.thoughtcrime.securesms.conversation.v2.items.ChatColorsDrawable
import org.thoughtcrime.securesms.conversation.v2.items.light.LightItemStyle
import org.thoughtcrime.securesms.conversation.v2.items.light.LightQuoteLine
import org.thoughtcrime.securesms.conversation.v2.keyboard.AttachmentKeyboardFragment
import org.thoughtcrime.securesms.conversation.v2.light.LightComposerView
import org.thoughtcrime.securesms.conversation.v2.light.LightConversationBottomBarView
import org.thoughtcrime.securesms.conversation.v2.light.LightConversationTopBarLeftAction
import org.thoughtcrime.securesms.conversation.v2.light.LightInputPanelChrome
import org.thoughtcrime.securesms.conversation.v2.light.LightMessageMenu
import org.thoughtcrime.securesms.conversation.v2.light.LightThreadBottomSlot
import org.thoughtcrime.securesms.database.DraftTable
import org.thoughtcrime.securesms.database.model.IdentityRecord
import org.thoughtcrime.securesms.database.model.InMemoryMessageRecord
import org.thoughtcrime.securesms.database.model.Mention
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.database.model.MmsMessageRecord
import org.thoughtcrime.securesms.database.model.Quote
import org.thoughtcrime.securesms.database.model.StickerRecord
import org.thoughtcrime.securesms.database.model.databaseprotos.BodyRangeList
import org.thoughtcrime.securesms.databinding.V2ConversationFragmentBinding
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.events.GroupCallPeekEvent
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4ItemDecoration
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4PlaybackController
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4PlaybackPolicy
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4ProjectionPlayerHolder
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4ProjectionRecycler
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.GroupMigrationMembershipChange
import org.thoughtcrime.securesms.groups.memberlabel.MemberLabelActivity
import org.thoughtcrime.securesms.groups.memberlabel.MemberLabelEducationSheet
import org.thoughtcrime.securesms.groups.ui.GroupChangeFailureReason
import org.thoughtcrime.securesms.groups.ui.GroupErrors
import org.thoughtcrime.securesms.groups.ui.LeaveGroupDialog
import org.thoughtcrime.securesms.groups.ui.invitesandrequests.ManagePendingAndRequestingMembersActivity
import org.thoughtcrime.securesms.groups.ui.invitesandrequests.invite.GroupLinkInviteFriendsBottomSheetDialogFragment
import org.thoughtcrime.securesms.groups.ui.managegroup.dialogs.GroupDescriptionDialog
import org.thoughtcrime.securesms.groups.ui.migration.GroupsV1MigrationInfoBottomSheetDialogFragment
import org.thoughtcrime.securesms.groups.ui.migration.GroupsV1MigrationSuggestionsDialog
import org.thoughtcrime.securesms.groups.v2.GroupBlockJoinRequestResult
import org.thoughtcrime.securesms.invites.InviteActions
import org.thoughtcrime.securesms.jobs.AttachmentBackfill
import org.thoughtcrime.securesms.jobs.ServiceOutageDetectionJob
import org.thoughtcrime.securesms.keyboard.KeyboardPage
import org.thoughtcrime.securesms.keyboard.KeyboardPagerFragment
import org.thoughtcrime.securesms.keyboard.KeyboardPagerViewModel
import org.thoughtcrime.securesms.keyboard.KeyboardUtil
import org.thoughtcrime.securesms.keyboard.emoji.EmojiKeyboardPageFragment
import org.thoughtcrime.securesms.keyboard.emoji.search.EmojiSearchFragment
import org.thoughtcrime.securesms.keyboard.gif.GifKeyboardPageFragment
import org.thoughtcrime.securesms.keyboard.sticker.StickerKeyboardPageFragment
import org.thoughtcrime.securesms.keyboard.sticker.StickerSearchDialogFragment
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.light.LightActionPanelView
import org.thoughtcrime.securesms.light.LightPanelAction
import org.thoughtcrime.securesms.linkpreview.LinkPreview
import org.thoughtcrime.securesms.linkpreview.LinkPreviewViewModelV2
import org.thoughtcrime.securesms.longmessage.LongMessageFragment
import org.thoughtcrime.securesms.main.MainNavigationChatDetailRouter
import org.thoughtcrime.securesms.main.MainNavigationDetailLocation
import org.thoughtcrime.securesms.main.MainNavigationListLocation
import org.thoughtcrime.securesms.main.MainNavigationViewModel
import org.thoughtcrime.securesms.main.MainSnackbarHostKey
import org.thoughtcrime.securesms.mediaoverview.MediaOverviewActivity
import org.thoughtcrime.securesms.mediapreview.MediaIntentFactory
import org.thoughtcrime.securesms.mediapreview.MediaPreviewV2Activity
import org.thoughtcrime.securesms.mediasend.MediaSendActivityResult
import org.thoughtcrime.securesms.messagerequests.MessageRequestRepository
import org.thoughtcrime.securesms.mms.AttachmentManager
import org.thoughtcrime.securesms.mms.AudioSlide
import org.thoughtcrime.securesms.mms.DocumentSlide
import org.thoughtcrime.securesms.mms.GifSlide
import org.thoughtcrime.securesms.mms.ImageSlide
import org.thoughtcrime.securesms.mms.PushMediaConstraints
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.mms.Slide
import org.thoughtcrime.securesms.mms.SlideDeck
import org.thoughtcrime.securesms.mms.SlideFactory
import org.thoughtcrime.securesms.mms.StickerSlide
import org.thoughtcrime.securesms.mms.VideoSlide
import org.thoughtcrime.securesms.nicknames.NicknameActivity
import org.thoughtcrime.securesms.notifications.v2.ConversationId
import org.thoughtcrime.securesms.polls.Poll
import org.thoughtcrime.securesms.polls.PollOption
import org.thoughtcrime.securesms.polls.PollRecord
import org.thoughtcrime.securesms.profiles.manage.EditProfileActivity
import org.thoughtcrime.securesms.profiles.spoofing.ReviewCardDialogFragment
import org.thoughtcrime.securesms.ratelimit.RecaptchaProofBottomSheetFragment
import org.thoughtcrime.securesms.ratelimit.RecaptchaRequiredEvent
import org.thoughtcrime.securesms.reactions.ReactionsBottomSheetDialogFragment
import org.thoughtcrime.securesms.reactions.any.ReactWithAnyEmojiBottomSheetDialogFragment
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientExporter
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.ui.about.AboutSheet
import org.thoughtcrime.securesms.recipients.ui.bottomsheet.RecipientBottomSheetDialogFragment
import org.thoughtcrime.securesms.recipients.ui.disappearingmessages.RecipientDisappearingMessagesActivity
import org.thoughtcrime.securesms.registration.ui.RegistrationActivity
import org.thoughtcrime.securesms.revealable.ViewOnceMessageActivity
import org.thoughtcrime.securesms.revealable.ViewOnceUtil
import org.thoughtcrime.securesms.safety.SafetyNumberBottomSheet
import org.thoughtcrime.securesms.sharing.v2.ShareActivity
import org.thoughtcrime.securesms.sms.MessageSender
import org.thoughtcrime.securesms.stickers.StickerEventListener
import org.thoughtcrime.securesms.stickers.StickerLocator
import org.thoughtcrime.securesms.stickers.StickerPackInstallEvent
import org.thoughtcrime.securesms.stickers.manage.StickerManagementScreen
import org.thoughtcrime.securesms.stickers.preview.StickerPackPreviewActivity
import org.thoughtcrime.securesms.stories.StoryViewerArgs
import org.thoughtcrime.securesms.stories.viewer.StoryViewerActivity
import org.thoughtcrime.securesms.util.BubbleUtil
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.ConversationUtil
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.DateUtils.is24HourFormat
import org.thoughtcrime.securesms.util.DeleteDialog
import org.thoughtcrime.securesms.util.Dialogs
import org.thoughtcrime.securesms.util.DoubleClickDebouncer
import org.thoughtcrime.securesms.util.FileProviderUtil
import org.thoughtcrime.securesms.util.FullscreenHelper
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.util.MessageConstraintsUtil
import org.thoughtcrime.securesms.util.MessageConstraintsUtil.getEditMessageThresholdHours
import org.thoughtcrime.securesms.util.MessageConstraintsUtil.isValidEditMessageSend
import org.thoughtcrime.securesms.util.MessageUtil
import org.thoughtcrime.securesms.util.PlayStoreUtil
import org.thoughtcrime.securesms.util.RemoteConfig
import org.thoughtcrime.securesms.util.SignalLocalMetrics
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.atMidnight
import org.thoughtcrime.securesms.util.atUTC
import org.thoughtcrime.securesms.util.doAfterNextLayout
import org.thoughtcrime.securesms.util.fragments.requireListener
import org.thoughtcrime.securesms.util.getPoll
import org.thoughtcrime.securesms.util.getQuote
import org.thoughtcrime.securesms.util.getRecordQuoteType
import org.thoughtcrime.securesms.util.hasAudio
import org.thoughtcrime.securesms.util.hasLinkPreview
import org.thoughtcrime.securesms.util.hasNonTextSlide
import org.thoughtcrime.securesms.util.isValidReactionTarget
import org.thoughtcrime.securesms.util.padding
import org.thoughtcrime.securesms.util.setIncognitoKeyboardEnabled
import org.thoughtcrime.securesms.util.toMillis
import org.thoughtcrime.securesms.util.viewModel
import org.thoughtcrime.securesms.util.visible
import org.thoughtcrime.securesms.verify.VerifyIdentityActivity
import org.thoughtcrime.securesms.wallpaper.ChatWallpaper
import org.thoughtcrime.securesms.wallpaper.ChatWallpaperDimLevelUtil
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.Optional
import java.util.concurrent.ExecutionException
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import org.signal.core.ui.R as CoreUiR

/**
 * A single unified fragment for Conversations.
 */
class ConversationFragment :
  LoggingFragment(R.layout.v2_conversation_fragment),
  ReactWithAnyEmojiBottomSheetDialogFragment.Callback,
  ReactionsBottomSheetDialogFragment.Callback,
  EmojiKeyboardPageFragment.Callback,
  EmojiEventListener,
  GifKeyboardPageFragment.Host,
  StickerEventListener,
  StickerKeyboardPageFragment.Callback,
  MediaKeyboard.MediaKeyboardListener,
  EmojiSearchFragment.Callback,
  ScheduleMessageTimePickerBottomSheet.ScheduleCallback,
  ScheduleMessageDialogCallback,
  ConversationBottomSheetCallback,
  SafetyNumberBottomSheet.Callbacks,
  EnableCallNotificationSettingsDialog.Callback,
  MultiselectForwardBottomSheet.Callback,
  DoubleTapEditEducationSheet.Callback,
  FragmentBackPressedInfoProvider {

  companion object {
    private val TAG = Log.tag(ConversationFragment::class.java)
    private val POLL_SPINNER_DELAY = 500.milliseconds
    private val PIN_SPINNER_DELAY = 500.milliseconds

    private const val ACTION_PINNED_SHORTCUT = "action_pinned_shortcut"
    private const val SAVED_STATE_IS_SEARCH_REQUESTED = "is_search_requested"
    private const val EMOJI_SEARCH_FRAGMENT_TAG = "EmojiSearchFragment"
    private const val MESSAGE_DETAILS_TAG = "MessageDetailsFragment"

    private const val SCROLL_HEADER_ANIMATION_DURATION: Long = 100L
    private const val SCROLL_HEADER_CLOSE_DELAY: Long = SCROLL_HEADER_ANIMATION_DURATION * 4
    private const val IS_SCROLLED_TO_BOTTOM_THRESHOLD: Int = 2

    private const val ATTACHMENT_KEYBOARD_FRAGMENT_CREATOR_ID = 1
    private const val MEDIA_KEYBOARD_FRAGMENT_CREATOR_ID = 2

    private val RECEIVE_CONTENT_MIME_TYPES = arrayOf(
      "image/jpeg",
      "image/png",
      "image/gif",
      "image/webp",
      "image/heic",
      "image/heif",
      "image/avif"
    )
  }

  private val args: ConversationArgs by lazy {
    ConversationIntents.readArgsFromBundle(requireArguments())
  }

  private val conversationRecipientRepository: ConversationRecipientRepository by viewModel {
    ConversationRecipientRepository(args.threadId)
  }

  private val messageRequestRepository: MessageRequestRepository by lazy {
    MessageRequestRepository(requireContext())
  }

  private val disposables = LifecycleDisposable()
  private val binding by ViewBinderDelegate(bindingFactory = V2ConversationFragmentBinding::bind, onBindingWillBeDestroyed = { _binding ->
    _binding.conversationInputPanel.embeddedTextEditor.apply {
      setOnEditorActionListener(null)
      setCursorPositionChangedListener(null)
      setOnKeyListener(null)
      removeTextChangedListener(composeTextEventsListener)
      setStylingChangedListener(null)
      setOnClickListener(null)
      ViewCompat.setOnReceiveContentListener(this, null, null)
    }

    dataObserver?.let {
      adapter.unregisterAdapterDataObserver(it)
    }

    scrollListener?.let {
      _binding.conversationItemRecycler.removeOnScrollListener(it)
    }
    scrollListener = null

    _binding.conversationItemRecycler.adapter = null

    textDraftSaveDebouncer.clear()
  })

  private val viewModel: ConversationViewModel by viewModel {
    ConversationViewModel(
      threadId = args.threadId,
      requestedStartingPosition = args.startingPosition,
      repository = ConversationRepository(localContext = requireContext(), isInBubble = args.conversationScreenType == ConversationScreenType.BUBBLE),
      recipientRepository = conversationRecipientRepository,
      messageRequestRepository = messageRequestRepository,
      scheduledMessagesRepository = ScheduledMessagesRepository()
    )
  }

  private val linkPreviewViewModel: LinkPreviewViewModelV2 by viewModel {
    LinkPreviewViewModelV2(it.createSavedStateHandle(), enablePlaceholder = false)
  }

  private val groupCallViewModel: ConversationGroupCallViewModel by viewModel {
    ConversationGroupCallViewModel(conversationRecipientRepository)
  }

  private val conversationGroupViewModel: ConversationGroupViewModel by viewModels(
    factoryProducer = {
      ConversationGroupViewModel.Factory(conversationRecipientRepository)
    }
  )

  private val messageRequestViewModel: MessageRequestViewModel by viewModel {
    MessageRequestViewModel(args.threadId, conversationRecipientRepository, messageRequestRepository)
  }

  private val draftViewModel: DraftViewModel by viewModel {
    DraftViewModel(threadId = args.threadId, repository = DraftRepository(conversationArguments = args))
  }

  private val searchViewModel: ConversationSearchViewModel by viewModel {
    ConversationSearchViewModel(getString(R.string.note_to_self))
  }

  private val keyboardPagerViewModel: KeyboardPagerViewModel by activityViewModels()

  private val stickerViewModel: StickerSuggestionsViewModel by viewModel {
    StickerSuggestionsViewModel()
  }

  private val inlineQueryViewModel: InlineQueryViewModelV2 by viewModel {
    InlineQueryViewModelV2(conversationRecipientRepository)
  }

  private val shareDataTimestampViewModel: ShareDataTimestampViewModel by activityViewModels()

  private val mainNavigationViewModel: MainNavigationViewModel by activityViewModels { MainNavigationViewModel.Factory() }

  private val inlineQueryController: InlineQueryResultsControllerV2 by lazy {
    InlineQueryResultsControllerV2(
      this,
      args.threadId,
      inlineQueryViewModel,
      inputPanel,
      (requireView() as ViewGroup),
      composeText
    )
  }

  private val voiceNotePlayerListener: VoiceNotePlayerView.Listener by lazy {
    VoiceNotePlayerViewListener()
  }

  private val conversationTooltips = ConversationTooltips(this)
  private val colorizer = ColorizerV2()
  private val textDraftSaveDebouncer = Debouncer(500)
  private val doubleTapToEditDebouncer = DoubleClickDebouncer(200)
  private val recentEmojis: RecentEmojiPageModel by lazy { RecentEmojiPageModel(AppDependencies.application, TextSecurePreferences.RECENT_STORAGE_KEY) }
  private val nicknameEditActivityLauncher = registerForActivityResult(NicknameActivity.Contract()) {}
  private val handler = Handler(Looper.getMainLooper())

  private lateinit var layoutManager: ConversationLayoutManager
  private lateinit var markReadHelper: MarkReadHelper
  private lateinit var giphyMp4ProjectionRecycler: GiphyMp4ProjectionRecycler
  private lateinit var addToContactsLauncher: ActivityResultLauncher<Intent>
  private lateinit var conversationActivityResultContracts: ConversationActivityResultContracts
  private lateinit var scrollToPositionDelegate: ScrollToPositionDelegate
  private lateinit var adapter: ConversationAdapterV2
  private lateinit var typingIndicatorAdapter: ConversationTypingIndicatorAdapter
  private lateinit var recyclerViewColorizer: RecyclerViewColorizer
  private lateinit var attachmentManager: AttachmentManager
  private lateinit var multiselectItemDecoration: MultiselectItemDecoration
  private lateinit var conversationHeaderPositionDecoration: ConversationHeaderPositionDecoration
  private lateinit var conversationItemDecorations: ConversationItemDecorations
  private lateinit var optionsMenuCallback: ConversationOptionsMenuCallback

  private lateinit var chatRouter: MainNavigationChatDetailRouter

  private var animationsAllowed = false
  private var pinnedShortcutReceiver: BroadcastReceiver? = null
  private var searchMenuItem: MenuItem? = null

  private var isSearchRequested: Boolean = false
    set(value) {
      field = value
      viewModel.setIsSearchRequested(value)
    }

  private var previousPage: KeyboardPage? = null
  private var previousPages: Set<KeyboardPage>? = null
  private var reShowScheduleMessagesBar: Boolean = false
  private var composeTextEventsListener: ComposeTextEventsListener? = null
  private var dataObserver: DataObserver? = null
  private var menuProvider: ConversationOptionsMenu.Provider? = null
  private var scrollListener: ScrollListener? = null
  private var keyboardEvents: KeyboardEvents? = null
  private var progressDialog: ProgressCardDialogFragment? = null
  private var firstPinRender: Boolean = true
  private var skipNextBackPressHandling: Boolean = false
  private var collapsibleEventScrollPosition: CollapsibleEventScrollPosition? = null
  private var releaseNotesLayoutApplied: Boolean = false
  private var releaseNotesWallpaperApplied: Boolean = false

  /** LIGHT PHONE: whether the full-screen Light composer is up. See [openLightComposer]. */
  private var lightComposerOpen: Boolean = false

  /**
   * LIGHT PHONE: whether a voice note is being recorded, from the moment the recorder starts until it
   * is sent, cancelled or saved as a draft. The input panel has to be expanded for all of it, because
   * Signal's recording chrome -- the timer, cancel and send -- is laid out inside it.
   */
  private var lightRecordingActive: Boolean = false

  /**
   * LIGHT PHONE: which call actions this thread offers, straight from the thread's own options menu.
   * See [ConversationOptionsMenu.Provider.onCreateMenu]; neither means no call button on the bar.
   */
  private var lightCanVoiceCall: Boolean = false
  private var lightCanVideoCall: Boolean = false

  private var applyToolbarPaddingRunnable: Runnable? = null

  private val jumpAndPulseScrollStrategy = object : ScrollToPositionDelegate.ScrollStrategy {
    override fun performScroll(recyclerView: RecyclerView, layoutManager: LinearLayoutManager, position: Int, smooth: Boolean) {
      ScrollToPositionDelegate.JumpToPositionStrategy.performScroll(recyclerView, layoutManager, position, smooth)
      adapter.pulseAtPosition(position)
    }
  }

  private val container: InputAwareConstraintLayout
    get() = requireView() as InputAwareConstraintLayout

  private val inputPanel: InputPanel
    get() = binding.conversationInputPanel.root

  private val composeText: ComposeText
    get() = binding.conversationInputPanel.embeddedTextEditor

  private val sendButton: SendButton
    get() = binding.conversationInputPanel.sendButton

  private val sendEditButton: ImageButton
    get() = binding.conversationInputPanel.sendEditButton

  private val bottomActionBar: SignalBottomActionBar
    get() = binding.conversationBottomActionBar

  private val searchNav: ConversationSearchBottomBar
    get() = binding.conversationSearchBottomBar.root

  private val lightBottomBar: LightConversationBottomBarView
    get() = binding.lightBottomBar

  private val lightComposerView: LightComposerView
    get() = binding.lightComposer

  private val lightActionPanel: LightActionPanelView
    get() = binding.lightActionPanel

  private val actionModeTopBarView: ActionModeTopBarView
    get() = binding.actionModeTopBar

  private val scheduledMessagesStub: Stub<View> by lazy { Stub(binding.scheduledMessagesStub) }

  private lateinit var voiceMessageRecordingDelegate: VoiceMessageRecordingDelegate

  private val internalDidFirstFrameRender = MutableStateFlow(false)
  val didFirstFrameRender: StateFlow<Boolean> = internalDidFirstFrameRender

  //region Android Lifecycle

  override fun onAttach(context: Context) {
    super.onAttach(context)
    chatRouter = context as MainNavigationChatDetailRouter
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    SignalLocalMetrics.ConversationOpen.start()
    registerForResults()
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    viewModel.resetBackPressedState()
    binding.toolbar.isBackInvokedCallbackEnabled = false
    binding.root.setUseWindowTypes(args.conversationScreenType == ConversationScreenType.NORMAL && !resources.isSplitPane())
    if (args.conversationScreenType == ConversationScreenType.BUBBLE) {
      binding.root.setNavigationBarInsetOverride(0)
      view.post {
        if (isAdded && this@ConversationFragment.view != null) {
          ViewCompat.requestApplyInsets(binding.root)
          binding.root.requestLayout()
        }
      }
    }

    disposables.bindTo(viewLifecycleOwner)

    if (requireActivity() is ConversationActivity) {
      FullscreenHelper(requireActivity()).showSystemUI()
    }

    markReadHelper = MarkReadHelper(ConversationId.forConversation(args.threadId), requireContext(), viewLifecycleOwner, args.isIncognito)
    markReadHelper.ignoreViewReveals()

    attachmentManager = AttachmentManager(requireContext(), requireView(), AttachmentManagerListener())

    initializeConversationThreadUi()

    val conversationToolbarOnScrollHelper = ConversationToolbarOnScrollHelper(
      requireActivity(),
      binding.toolbarBackground,
      viewModel::wallpaperSnapshot,
      { viewModel.recipientSnapshot?.isReleaseNotes == true },
      viewLifecycleOwner,
      incognito = args.isIncognito
    )
    conversationToolbarOnScrollHelper.attach(binding.conversationItemRecycler)
    presentConversationTitle(viewModel.recipientSnapshot)
    presentActionBarMenu()

    observeConversationThread()
    observePlaintextExportState()

    viewModel
      .inputReadyState
      .distinctUntilChanged()
      .subscribeBy(
        onNext = this::presentInputReadyState
      )
      .addTo(disposables)

    container.fragmentManager = childFragmentManager

    childFragmentManager.setFragmentResultListener(MemberLabelEducationSheet.RESULT_EDIT_MEMBER_LABEL, viewLifecycleOwner) { _, bundle ->
      val groupId = bundle.requireParcelableCompat(MemberLabelEducationSheet.KEY_GROUP_ID, GroupId.V2::class.java)
      startActivity(MemberLabelActivity.createIntent(requireContext(), groupId))
    }

    childFragmentManager.setFragmentResultListener(AboutSheet.RESULT_EDIT_MEMBER_LABEL, viewLifecycleOwner) { _, bundle ->
      val groupId = bundle.requireParcelableCompat(AboutSheet.RESULT_GROUP_ID, GroupId.V2::class.java)
      startActivity(MemberLabelActivity.createIntent(requireContext(), groupId))
    }

    initializeMediaKeyboard()

    binding.conversationVideoContainer.setClipToOutline(true)

    SpoilerAnnotation.resetRevealedSpoilers()

    val mediaListener = InputPanelMediaListener()
    ViewCompat.setOnReceiveContentListener(composeText, RECEIVE_CONTENT_MIME_TYPES) { _, payload ->
      val split = payload.partition { item -> item.uri != null }
      val uriContent = split.first

      if (uriContent != null) {
        val clip = uriContent.clip
        val mimeType = if (clip.description.mimeTypeCount > 0) {
          clip.description.getMimeType(0)
        } else {
          null
        }
        val uri = clip.getItemAt(0).uri
        if (uri != null) {
          mediaListener.onMediaSelected(uri, mimeType)
        }
      }

      split.second
    }

    binding.conversationItemRecycler.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
      viewModel.onChatBoundsChanged(Rect(left, top, right, bottom))
    }

    binding.toolbar.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
      // Bug: ConstraintLayout can provide a negative value for the toolbar causing RV layout problems
      if (bottom < 0) return@addOnLayoutChangeListener

      // Bug: LinearLayoutManger can get stuck and not layout children under Compose's AndroidFragment if updated too quickly.
      val rv = binding.conversationItemRecycler
      applyToolbarPaddingRunnable?.let { rv.removeCallbacks(it) }
      val runnable = Runnable {
        if (view == null) return@Runnable
        rv.padding(top = bottom)
        if (bottom != oldBottom && ::conversationHeaderPositionDecoration.isInitialized) {
          val newMargin = bottom + 16.dp
          if (conversationHeaderPositionDecoration.toolbarMargin != newMargin) {
            conversationHeaderPositionDecoration.toolbarMargin = newMargin
            rv.invalidateItemDecorations()
          }
        }
      }
      applyToolbarPaddingRunnable = runnable
      rv.post(runnable)
    }

    binding.conversationItemRecycler.addItemDecoration(ChatColorsDrawable.ChatColorsItemDecoration)
  }

  override fun onViewStateRestored(savedInstanceState: Bundle?) {
    super.onViewStateRestored(savedInstanceState)

    isSearchRequested = savedInstanceState?.getBoolean(SAVED_STATE_IS_SEARCH_REQUESTED, false) ?: args.isWithSearchOpen
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)

    outState.putBoolean(SAVED_STATE_IS_SEARCH_REQUESTED, isSearchRequested)
  }

  private var previous24HourFormat: Boolean? = null

  override fun onStart() {
    super.onStart()
    val current24HourFormat = requireContext().is24HourFormat()
    if (current24HourFormat != previous24HourFormat) {
      recomputeMessageDates(forceUpdate = true)
      previous24HourFormat = current24HourFormat
    }
  }

  override fun onResume() {
    super.onResume()

    EventBus.getDefault().register(this)

    groupCallViewModel.peekGroupCall()

    if (!args.conversationScreenType.isInBubble) {
      AppDependencies.messageNotifier.setVisibleThread(ConversationId.forConversation(args.threadId))
    } else {
      AppDependencies.messageNotifier.setVisibleBubbleThread(ConversationId.forConversation(args.threadId))
    }

    viewModel.updateIdentityRecordsInBackground()

    if (args.isFirstTimeInSelfCreatedGroup) {
      conversationGroupViewModel.checkJustSelfInGroup().subscribeBy(
        onSuccess = {
          GroupLinkInviteFriendsBottomSheetDialogFragment.show(childFragmentManager, it)
        }
      ).addTo(disposables)
    }

    conversationGroupViewModel.updateGroupStateIfNeeded()

    if (inputPanel.voiceNoteDraft != null) {
      updateToggleButtonState()
    }

    if (SignalStore.rateLimit.needsRecaptcha()) {
      RecaptchaProofBottomSheetFragment.show(childFragmentManager)
    }
  }

  override fun onPause() {
    super.onPause()

    ConversationUtil.refreshRecipientShortcuts()

    if (!args.conversationScreenType.isInBubble) {
      AppDependencies.messageNotifier.clearVisibleThread(ConversationId.forConversation(args.threadId))
    } else {
      AppDependencies.messageNotifier.clearVisibleBubbleThread()
    }

    if (activity?.isFinishing == true) {
      activity?.overridePendingTransition(R.anim.fade_scale_in, R.anim.slide_to_end)
    }

    inputPanel.onPause()

    EventBus.getDefault().unregister(this)
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    inlineQueryController.onWindowSizeClassChanged(resources.getWindowSizeClass())
  }

  override fun onDestroyView() {
    viewModel.collapseAllEvents()
    keyboardEvents?.let {
      container.removeInputListener(it)
      container.removeKeyboardStateListener(it)
    }
    keyboardEvents = null

    if (!requireActivity().isChangingConfigurations) {
      (requireActivity().supportFragmentManager.findFragmentByTag(MESSAGE_DETAILS_TAG) as? DialogFragment)?.dismissAllowingStateLoss()
    }

    super.onDestroyView()
    if (pinnedShortcutReceiver != null) {
      requireActivity().unregisterReceiver(pinnedShortcutReceiver)
    }
  }

  @Suppress("OVERRIDE_DEPRECATION")
  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults)
  }

  override fun startActivity(intent: Intent) {
    if (intent.getStringArrayExtra(Browser.EXTRA_APPLICATION_ID) != null) {
      intent.removeExtra(Browser.EXTRA_APPLICATION_ID)
    }

    try {
      super.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
      Log.w(TAG, e)
      toast(
        toastTextId = R.string.ConversationActivity_there_is_no_app_available_to_handle_this_link_on_your_device,
        toastDuration = Toast.LENGTH_LONG
      )
    }
  }

  //endregion

  //region Fragment callbacks and listeners

  override fun getConversationAdapterListener(): ConversationAdapter.ItemClickListener {
    return adapter.clickListener
  }

  override fun jumpToMessage(messageRecord: MessageRecord) {
    viewModel
      .moveToMessage(messageRecord)
      .subscribeBy {
        moveToPosition(it)
      }
      .addTo(disposables)
  }

  override fun unpin(conversationMessage: ConversationMessage) {
    handleUnpinMessage(conversationMessage.messageRecord.id)
  }

  override fun copy(conversationMessage: ConversationMessage) {
    handleCopyMessage(conversationMessage.multiselectCollection.toSet())
  }

  override fun delete(conversationMessage: ConversationMessage) {
    handleDeleteMessages(conversationMessage.multiselectCollection.toSet())
  }

  override fun save(conversationMessage: ConversationMessage) {
    handleSaveAttachment(conversationMessage.messageRecord as MmsMessageRecord)
  }

  // The Light build's long press offers a fixed set of reaction keys and no picker, so nothing in
  // this thread opens the "react with any emoji" sheet any more. The callbacks stay because the
  // sheet resolves its callback by casting whatever hosts it, and a child of this fragment opening
  // one would otherwise crash on the cast.
  override fun onReactWithAnyEmojiDialogDismissed() = Unit

  override fun onReactWithAnyEmojiSelected(emoji: String) = Unit

  override fun onReactionsDialogDismissed() {
    clearFocusedItem()
  }

  override fun openEmojiSearch() {
    val fragment = childFragmentManager.findFragmentByTag(EMOJI_SEARCH_FRAGMENT_TAG)
    if (fragment == null) {
      childFragmentManager.commit {
        add(R.id.emoji_search_container, EmojiSearchFragment(), EMOJI_SEARCH_FRAGMENT_TAG)
      }
    }
  }

  override fun closeEmojiSearch() {
    val fragment = childFragmentManager.findFragmentByTag(EMOJI_SEARCH_FRAGMENT_TAG)
    if (fragment != null) {
      childFragmentManager.commit(allowStateLoss = true) {
        remove(fragment)
      }
    }
  }

  override fun onEmojiSelected(emoji: String?) {
    if (emoji != null) {
      inputPanel.onEmojiSelected(emoji)
      recentEmojis.onCodePointSelected(emoji)
    }
  }

  override fun onKeyEvent(keyEvent: KeyEvent?) {
    if (keyEvent != null) {
      inputPanel.onKeyEvent(keyEvent)
    }
  }

  override fun openStickerSearch() {
    StickerSearchDialogFragment.show(childFragmentManager)
  }

  override fun onStickerSelected(sticker: StickerRecord) {
    sendSticker(
      stickerRecord = sticker,
      clearCompose = false
    )
  }

  override fun onStickerManagementClicked() {
    StickerManagementScreen.show(this)
    container.hideInput()
  }

  override fun isMms(): Boolean {
    return false
  }

  override fun openGifSearch() {
    val recipientId = viewModel.recipientSnapshot?.id ?: return
    conversationActivityResultContracts.launchGifSearch(recipientId, composeText.textTrimmed)
  }

  override fun onGifSelectSuccess(blobUri: Uri, width: Int, height: Int) {
    setMedia(
      uri = blobUri,
      mediaType = SlideFactory.MediaType.from(AppDependencies.blobs.getMimeType(blobUri))!!,
      width = width,
      height = height,
      videoGif = true
    )
  }

  override fun onShown() {
    inputPanel.mediaKeyboardListener.onShown()
  }

  override fun onHidden() {
    inputPanel.mediaKeyboardListener.onHidden()
    closeEmojiSearch()
  }

  override fun onKeyboardChanged(page: KeyboardPage) {
    inputPanel.mediaKeyboardListener.onKeyboardChanged(page)
  }

  override fun onScheduleSend(scheduledTime: Long) {
    sendMessage(scheduledDate = scheduledTime)
  }

  override fun onSchedulePermissionsGranted(metricId: String?, scheduledDate: Long) {
    sendMessage(scheduledDate = scheduledDate)
  }

  override fun sendAnywayAfterSafetyNumberChangedInBottomSheet(destinations: List<RecipientSearchKey>) {
    Log.d(TAG, "onSendAnywayAfterSafetyNumberChange")
    viewModel
      .updateIdentityRecords()
      .subscribeBy(
        onError = { t -> Log.w(TAG, "Error sending", t) },
        onComplete = { sendMessage() }
      )
      .addTo(disposables)
  }

  override fun onMessageResentAfterSafetyNumberChangeInBottomSheet() {
    Log.d(TAG, "onMessageResentAfterSafetyNumberChange")
    viewModel.updateIdentityRecordsInBackground()
  }

  override fun onCanceled() = Unit

  override fun onCallNotificationSettingsDialogDismissed() {
    adapter.notifyDataSetChanged()
  }

  override fun onFinishForwardAction() {
    finishActionMode()
  }

  override fun onDismissForwardSheet() = Unit

  override fun getFragmentBackPressedInfo(): Flow<FragmentBackPressedInfo> {
    return viewModel.backPressedState.map {
      if (it.shouldHandleBackPressed()) {
        FragmentBackPressedInfo.Enabled({
          handleBackPressed()
        })
      } else {
        FragmentBackPressedInfo.Disabled
      }
    }
  }

  private fun handleBackPressed() {
    if (skipNextBackPressHandling) {
      skipNextBackPressHandling = false
      return
    }

    Log.d(TAG, "handleBackPressed()")
    val state = viewModel.backPressedState.value

    when {
      state.isLightActionPanelShowing -> dismissLightActionPanel()

      state.isLightComposerShowing -> closeLightComposer(cancelEdit = true)

      state.isSearchRequested -> searchMenuItem?.collapseActionView()

      state.isInActionMode -> finishActionMode()

      state.isMediaKeyboardShowing -> {
        if (container.isInputShowing) {
          container.hideInput()
        } else {
          Log.d(TAG, "handleBackPressed() - media keyboard state was stale, clearing")
          viewModel.setIsMediaKeyboardShowing(false)
        }
      }

      else -> {
        // State has changed since the back handler was enabled. Let the back press proceed
        // to the next handler by triggering onBackPressed again after setting a skip flag
        // to avoid infinite recursion.
        Log.d(TAG, "handleBackPressed() - state changed, forwarding back press")
        skipNextBackPressHandling = true
        requireActivity().onBackPressedDispatcher.onBackPressed()
      }
    }
  }

  //endregion

  private fun startActionMode() {
    viewModel.setIsInActionMode(true)

    actionModeTopBarView.isVisible = true
    actionModeTopBarView.onCloseClick = this::finishActionMode
    actionModeTopBarView.title = calculateSelectedItemCount()

    searchMenuItem?.collapseActionView()
    binding.toolbar.isInvisible = true
    binding.lightTopBar.isInvisible = true
    if (scheduledMessagesStub.isVisible) {
      reShowScheduleMessagesBar = true
      scheduledMessagesStub.visibility = View.GONE
    }

    setCorrectActionModeMenuVisibility()
    binding.conversationItemRecycler.invalidateItemDecorations()
  }

  private fun setActionModeTitle(title: String) {
    actionModeTopBarView.title = title
  }

  private fun isActionModeStarted(): Boolean {
    return actionModeTopBarView.isVisible
  }

  private fun finishActionMode() {
    viewModel.setIsInActionMode(false)

    actionModeTopBarView.isVisible = false

    adapter.clearSelection()
    setBottomActionBarVisibility(false)

    binding.toolbar.isInvisible = false
    binding.lightTopBar.isInvisible = false
    if (reShowScheduleMessagesBar) {
      scheduledMessagesStub.visibility = View.VISIBLE
      reShowScheduleMessagesBar = false
    }

    binding.conversationItemRecycler.invalidateItemDecorations()
  }

  private fun observeConversationThread() {
    var firstRender = true
    disposables += viewModel
      .conversationThreadState
      .subscribeOn(Schedulers.io())
      .doOnSuccess { state ->
        SignalLocalMetrics.ConversationOpen.onDataLoaded()
        conversationItemDecorations.selfRecipientId = Recipient.self().id
        conversationItemDecorations.setUnreadState(state.meta.unreadCount, state.meta.firstUnreadId)
        colorizer.onGroupMembershipChanged(state.meta.groupMemberAcis)
      }
      .observeOn(AndroidSchedulers.mainThread())
      .doOnSuccess { state ->
        updateMessageRequestAcceptedState(state.meta.messageRequestData.isMessageRequestAccepted)
        moveToStartPosition(state.meta)
      }
      .flatMapObservable { it.items.data }
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy(onNext = {
        if (firstRender) {
          SignalLocalMetrics.ConversationOpen.onDataPostedToMain()
        }

        adapter.submitList(it) {
          scrollToPositionDelegate.notifyListCommitted()
          conversationItemDecorations.currentItems = it

          if (firstRender) {
            firstRender = false
            binding.conversationItemRecycler.doAfterNextLayout {
              SignalLocalMetrics.ConversationOpen.onRenderFinished()
              (context as? MainActivity)?.onFirstRender()
              doAfterFirstRender()
            }
          }

          if (collapsibleEventScrollPosition != null) {
            val scrollState = collapsibleEventScrollPosition!!
            val offset = binding.conversationItemRecycler.height - scrollState.top - scrollState.height
            layoutManager.scrollToPositionWithOffset(scrollState.position, offset)
            collapsibleEventScrollPosition = null
          }
        }
      })
  }

  private fun doAfterFirstRender() {
    Log.d(TAG, "doAfterFirstRender")

    if (!isAdded || view == null) {
      Log.w(TAG, "Bailing, fragment no longer added")
      return
    }

    activity?.supportStartPostponedEnterTransition()
    internalDidFirstFrameRender.update { true }

    if (requireActivity() is ConversationActivity) {
      val backPressedCallback = BackPressedCallback()
      requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)

      lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.RESUMED) {
          viewModel.backPressedState.collectLatest {
            backPressedCallback.isEnabled = it.shouldHandleBackPressed()
          }
        }
      }
    }

    mainNavigationViewModel.snackbarRegistry.register(
      MainSnackbarHostKey.Chat,
      viewLifecycleOwner
    ) { state ->
      makeSnackbar(state)
      true
    }

    menuProvider?.afterFirstRenderMode = true

    viewLifecycleOwner.lifecycle.addObserver(LastScrolledPositionUpdater(adapter, layoutManager, viewModel))

    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Default) {
      var wasTerminated: Boolean? = null
      viewModel
        .groupRecordFlow
        .collect { record ->
          val isTerminated = record.isTerminated
          if (wasTerminated == false && isTerminated) {
            val terminatedByRecipientId = record.terminatedByRecipientId
            if (terminatedByRecipientId == null || terminatedByRecipientId != Recipient.self().id) {
              val context = context ?: return@collect
              val adminName = terminatedByRecipientId?.let { Recipient.resolved(it).getDisplayName(context) }
              withContext(Dispatchers.Main) {
                TerminatedGroupBottomSheetDialog.show(childFragmentManager, adminName)
              }
            }
          }
          wasTerminated = isTerminated
        }
    }

    disposables += viewModel.recipient
      .observeOn(AndroidSchedulers.mainThread())
      .distinctUntilChanged { r1, r2 -> r1 === r2 || r1.hasSameContent(r2) }
      .subscribeBy(onNext = this::onRecipientChanged)

    disposables += viewModel.scrollButtonState
      .subscribeBy(onNext = this::presentScrollButtons)

    disposables += viewModel
      .groupMemberServiceIds
      .subscribeBy(onNext = {
        colorizer.onGroupMembershipChanged(it)
        adapter.updateNameColors()
      })

    val disabledInputListener = DisabledInputListener()
    binding.conversationDisabledInput.listener = disabledInputListener

    val sendButtonListener = SendButtonListener()
    composeTextEventsListener = ComposeTextEventsListener()

    composeText.apply {
      setOnEditorActionListener(sendButtonListener)

      setCursorPositionChangedListener(composeTextEventsListener)
      setOnKeyListener(composeTextEventsListener)
      addTextChangedListener(composeTextEventsListener)
      setStylingChangedListener(composeTextEventsListener)
      setOnClickListener(composeTextEventsListener)
      guardAgainstRecoveryKeyPaste(this@ConversationFragment)
      filters += ByteLimitInputFilter(MessageUtil.MAX_TOTAL_BODY_SIZE_BYTES)
    }

    sendButton.apply {
      setPopupContainer(binding.root)
      setOnClickListener(sendButtonListener)
      setScheduledSendListener(sendButtonListener)
      isEnabled = true
    }

    sendEditButton.setOnClickListener { handleSendEditMessage() }

    val attachListener = { _: View ->
      container.toggleInput(AttachmentKeyboardFragmentCreator, composeText)
    }
    binding.conversationInputPanel.attachButton.setOnClickListener(attachListener)
    binding.conversationInputPanel.inlineAttachmentButton.setOnClickListener(attachListener)

    initializeLightInputChrome()

    presentGroupCallJoinButton()

    binding.scrollToBottom.setOnClickListener {
      binding.conversationItemRecycler.stopScroll()
      scrollToPositionDelegate.resetScrollPosition()
    }

    binding.scrollToMention.setOnClickListener {
      binding.conversationItemRecycler.stopScroll()
      scrollToNextMention()
    }

    dataObserver = DataObserver()
    adapter.registerAdapterDataObserver(dataObserver!!)

    keyboardEvents = KeyboardEvents().also {
      container.addInputListener(it)
      container.addKeyboardStateListener(it)
    }

    childFragmentManager.setFragmentResultListener(AttachmentKeyboardFragment.RESULT_KEY, viewLifecycleOwner, AttachmentKeyboardFragmentListener())

    voiceMessageRecordingDelegate = VoiceMessageRecordingDelegate(
      this,
      AudioRecorder(requireContext(), inputPanel),
      VoiceMessageRecordingSessionCallbacks()
    )

    val conversationBannerListener = ConversationBannerListener()
    binding.conversationBanner.listener = conversationBannerListener

    lifecycleScope.launch {
      viewModel
        .getBannerFlows(
          context = requireContext(),
          groupJoinClickListener = conversationBannerListener::reviewJoinRequestsAction,
          onSuggestionAddMembers = {
            conversationGroupViewModel.groupRecordSnapshot?.let { groupRecord ->
              GroupsV1MigrationSuggestionsDialog.show(childFragmentManager, groupRecord.id.requireV2(), groupRecord.gv1MigrationSuggestions)
            }
          },
          onSuggestionNoThanks = conversationGroupViewModel::onSuggestedMembersBannerDismissed,
          bubbleClickListener = conversationBannerListener::changeBubbleSettingAction
        )
        .distinctUntilChanged()
        .flowWithLifecycle(viewLifecycleOwner.lifecycle)
        .flowOn(Dispatchers.Main)
        .collect {
          binding.conversationBanner.collectAndShowBanners(it)
        }
    }

    lifecycleScope.launch {
      viewModel
        .pinnedMessages
        .combine(viewModel.wallpaper) { messages, wallpaper -> messages to wallpaper }
        .flowWithLifecycle(viewLifecycleOwner.lifecycle)
        .flowOn(Dispatchers.Main)
        .collect { (messages, wallpaper) ->
          presentPinnedMessage(pinnedMessages = messages, hasWallpaper = wallpaper != null)
        }
    }

    lifecycleScope.launch {
      lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        val recipient = viewModel.recipientSnapshot
        if (recipient != null) {
          AvatarDownloadStateCache.forRecipient(recipient.id).collect {
            when (it) {
              AvatarDownloadStateCache.DownloadState.NONE,
              AvatarDownloadStateCache.DownloadState.IN_PROGRESS,
              AvatarDownloadStateCache.DownloadState.FINISHED -> {
                viewModel.updateThreadHeader()
              }

              AvatarDownloadStateCache.DownloadState.FAILED -> {
                Snackbar.make(requireView(), R.string.ConversationFragment_photo_failed, Snackbar.LENGTH_LONG).show()
                presentConversationTitle(recipient)
                viewModel.onAvatarDownloadFailed()
              }
            }
          }
        }
      }
    }

    lifecycleScope.launch {
      lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        AttachmentBackfill.failures.collect { failure ->
          if (failure.threadId == args.threadId) {
            showAttachmentBackfillFailureDialog(failure.reason)
          }
        }
      }
    }

    if (TextSecurePreferences.getServiceOutage(context)) {
      AppDependencies.jobManager.add(ServiceOutageDetectionJob())
    }

    viewModel
      .identityRecordsObservable
      .distinctUntilChanged()
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy { presentIdentityRecordsState(it) }
      .addTo(disposables)

    viewModel
      .getRequestReviewState()
      .subscribeBy { presentRequestReviewState(it) }
      .addTo(disposables)

    ConversationItemSwipeCallback(
      SwipeAvailabilityProvider(),
      this::handleReplyToMessage
    ).attachToRecyclerView(binding.conversationItemRecycler)

    viewModel
      .inputReadyState
      .take(1)
      .flatMapMaybe { inputReadyState ->
        draftViewModel.loadShareOrDraftData(shareDataTimestampViewModel.timestamp)
          .map { inputReadyState to it }
      }
      .subscribeBy { (inputReadyState, data) -> handleShareOrDraftData(inputReadyState, data) }
      .addTo(disposables)

    disposables.add(
      draftViewModel
        .state
        .distinctUntilChanged { previous, next -> previous.voiceNoteDraft == next.voiceNoteDraft }
        .subscribe {
          if (!voiceMessageRecordingDelegate.hasActiveSession()) {
            inputPanel.voiceNoteDraft = it.voiceNoteDraft
          }
          updateToggleButtonState()
          updateLightInputChrome()
        }
    )

    initializeSearch()
    initializeLinkPreviews()
    // LIGHT PHONE: no `initializeStickerSuggestions()`. The suggestion strip sat above the input panel
    // and offered stickers for the word being typed; there is no sticker picker in this fork, so
    // nothing subscribes and the strip never populates. `components/emoji` stays -- it is what renders
    // received emoji and stickers, and removing it would break message display.
    initializeInlineSearch()

    inputPanel.setListener(InputPanelListener())

    viewModel
      .getScheduledMessagesCount()
      .subscribeBy { count -> handleScheduledMessagesCountChange(count) }
      .addTo(disposables)

    presentTypingIndicator()

    getVoiceNoteMediaController().finishPostpone()

    getVoiceNoteMediaController()
      .voiceNotePlayerViewState
      .observe(viewLifecycleOwner) { state: Optional<VoiceNotePlayerView.State> ->
        if (state.isPresent) {
          binding.conversationBanner.showVoiceNotePlayer(state.get(), voiceNotePlayerListener)
        } else {
          binding.conversationBanner.clearVoiceNotePlayer()
        }
      }

    getVoiceNoteMediaController().voiceNotePlaybackState.observe(viewLifecycleOwner, inputPanel.playbackStateObserver)

    val conversationUpdateTick = ConversationUpdateTick { recomputeMessageDates() }

    viewLifecycleOwner.lifecycle.addObserver(conversationUpdateTick)

    if (args.conversationScreenType.isInPopup) {
      container.showSoftkey(composeText)
      binding.conversationInputPanel.quickAttachmentToggle.disable()
    }
  }

  private fun recomputeMessageDates(forceUpdate: Boolean = false) {
    disposables += ConversationMessageComputeWorkers
      .recomputeFormattedDate(
        context = requireContext(),
        items = adapter.currentList.filterIsInstance<ConversationMessageElement>(),
        forceUpdate = forceUpdate
      )
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy { adapter.updateTimestamps() }
  }

  private fun initializeInlineSearch() {
    inlineQueryController.onWindowSizeClassChanged(resources.getWindowSizeClass())

    composeText.apply {
      setInlineQueryChangedListener(object : InlineQueryChangedListener {
        override fun onQueryChanged(inlineQuery: InlineQuery) {
          inlineQueryViewModel.onQueryChange(inlineQuery)
        }
      })

      setMentionValidator { annotations ->
        val recipient = viewModel.recipientSnapshot ?: return@setMentionValidator annotations

        val validIds = recipient.participantIds
          .map { MentionAnnotation.idToMentionAnnotationValue(it) }
          .toSet()

        annotations.filterNot { validIds.contains(it.value) }
      }
    }

    inlineQueryViewModel
      .selection
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { r: InlineQueryReplacement -> composeText.replaceText(r) }
      .addTo(disposables)
  }

  private fun presentPinnedMessage(pinnedMessages: List<ConversationMessage>, hasWallpaper: Boolean) {
    if (pinnedMessages.isNotEmpty()) {
      binding.conversationBanner.showPinnedMessageStub(messages = pinnedMessages, canUnpin = conversationGroupViewModel.canEditGroupInfo(), hasWallpaper = hasWallpaper, shouldAnimate = !firstPinRender)
    } else {
      binding.conversationBanner.hidePinnedMessageStub()
    }
    firstPinRender = false
  }

  private fun presentTypingIndicator() {
    typingIndicatorAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
      override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
        if (positionStart == 0 && itemCount == 1 && layoutManager.findFirstCompletelyVisibleItemPosition() == 0) {
          scrollToPositionDelegate.resetScrollPosition()
        }
      }
    })

    AppDependencies.typingStatusRepository.getTypists(args.threadId).observe(viewLifecycleOwner) {
      val recipient = viewModel.recipientSnapshot ?: return@observe

      typingIndicatorAdapter.setState(
        ConversationTypingIndicatorAdapter.State(
          typists = it.typists,
          isGroupThread = recipient.isGroup,
          hasWallpaper = recipient.hasWallpaper,
          isReplacedByIncomingMessage = it.isReplacedByIncomingMessage
        )
      )
    }
  }

  private fun presentInputReadyState(inputReadyState: InputReadyState) {
    presentConversationTitle(inputReadyState.conversationRecipient)

    val disabledInputView = binding.conversationDisabledInput
    val isReleaseNotes = inputReadyState.conversationRecipient.isReleaseNotes
    if (isReleaseNotes) {
      applyReleaseNotesLayout()
    }

    var inputDisabled = true
    when {
      inputReadyState.isClientExpired || inputReadyState.isUnauthorized -> disabledInputView.showAsExpiredOrUnauthorized(inputReadyState.isClientExpired, inputReadyState.isUnauthorized)
      args.isIncognito -> disabledInputView.showAsIncognito()
      inputReadyState.isTerminatedGroup -> disabledInputView.showAsTerminatedGroup()
      !inputReadyState.messageRequestState.isAccepted -> disabledInputView.showAsMessageRequest(inputReadyState.conversationRecipient, inputReadyState.messageRequestState)
      inputReadyState.isRequestingMember == true -> disabledInputView.showAsRequestingMember()
      inputReadyState.isActiveGroup == false -> disabledInputView.showAsNoLongerAMember()
      inputReadyState.isAnnouncementGroup == true && inputReadyState.isAdmin == false -> disabledInputView.showAsAnnouncementGroupAdminsOnly()
      isReleaseNotes -> Unit
      inputReadyState.shouldShowInviteToSignal() -> disabledInputView.showAsInviteToSignal(requireContext(), inputReadyState.conversationRecipient)
      else -> inputDisabled = false
    }

    inputPanel.setHideForMessageRequestState(inputDisabled)
    updateLightInputChrome()

    if (inputDisabled && !isReleaseNotes) {
      binding.navBar.setBackgroundColor(disabledInputView.color)
    } else if (!inputDisabled) {
      disabledInputView.clear()
    }

    composeText.setMessageSendType(MessageSendType.SignalMessageSendType)

    invalidateOptionsMenu()
  }

  private fun applyReleaseNotesLayout() {
    if (releaseNotesLayoutApplied) {
      return
    }
    releaseNotesLayoutApplied = true

    binding.conversationReleaseNotesFloatingLabel.visible = true
    binding.conversationDisabledInput.visible = false

    val navBarInset = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
    binding.conversationItemRecycler.updatePadding(bottom = ViewUtil.dpToPx(72) + navBarInset)
    binding.navBar.setBackgroundColor(Color.TRANSPARENT)

    ConstraintSet().apply {
      clone(binding.root)
      connect(binding.conversationItemRecyclerFrame.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
      applyTo(binding.root)
    }
  }

  private fun presentIdentityRecordsState(identityRecordsState: IdentityRecordsState) {
    if (identityRecordsState.isUnverified) {
      binding.conversationBanner.showUnverifiedBanner(identityRecordsState.identityRecords)
    } else {
      binding.conversationBanner.clearUnverifiedBanner()
    }
  }

  private fun presentRequestReviewState(requestReviewState: RequestReviewState) {
    if (requestReviewState.shouldShowReviewBanner()) {
      binding.conversationBanner.showReviewBanner(requestReviewState)
    } else {
      binding.conversationBanner.clearRequestReview()
    }
  }

  private fun setMedia(uri: Uri, mediaType: SlideFactory.MediaType, width: Int = 0, height: Int = 0, borderless: Boolean = false, videoGif: Boolean = false) {
    val recipientId: RecipientId = viewModel.recipientSnapshot?.id ?: return

    if (mediaType == SlideFactory.MediaType.VCARD) {
      conversationActivityResultContracts.launchContactShareEditor(uri, viewModel.recipientSnapshot!!.chatColors)
    } else {
      val mimeType = MediaUtil.getMimeType(requireContext(), uri) ?: mediaType.toFallbackMimeType()
      val media = Media(
        uri = uri,
        contentType = mimeType,
        date = 0,
        width = width,
        height = height,
        size = 0,
        duration = 0,
        isBorderless = borderless,
        isVideoGif = videoGif,
        bucketId = null,
        caption = null,
        transformProperties = TransformProperties.forSentMediaQuality(SignalStore.settings.sentMediaQuality.code),
        fileName = null
      )
      conversationActivityResultContracts.launchMediaEditor(listOf(media), recipientId, composeText.textTrimmed)
    }
  }

  private fun registerForResults() {
    addToContactsLauncher = registerForActivityResult(AddToContactsContract()) {}
    conversationActivityResultContracts = ConversationActivityResultContracts(this, ActivityResultCallbacks())
  }

  private fun observePlaintextExportState() {
    var progressDialog: SignalProgressDialog? = null

    lifecycleScope.launch {
      viewModel.plaintextExportState.collectLatest { state ->
        val exporting = state is ConversationViewModel.PlaintextExportState.Preparing || state is ConversationViewModel.PlaintextExportState.InProgress
        if (exporting) {
          requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
          requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        when (state) {
          is ConversationViewModel.PlaintextExportState.None -> {
            progressDialog?.dismiss()
            progressDialog = null
          }

          is ConversationViewModel.PlaintextExportState.Preparing -> {
            progressDialog = SignalProgressDialog.show(
              context = requireContext(),
              title = getString(R.string.conversation_export__exporting),
              message = getString(R.string.conversation_export__preparing),
              indeterminate = true,
              cancelable = false,
              negativeButtonText = getString(android.R.string.cancel),
              negativeButtonListener = { _, _ -> viewModel.cancelExport() }
            )
          }

          is ConversationViewModel.PlaintextExportState.InProgress -> {
            progressDialog?.let {
              it.isIndeterminate = false
              it.progress = state.percent
              it.setMessage(state.status)
            }
          }

          is ConversationViewModel.PlaintextExportState.Complete -> {
            progressDialog?.dismiss()
            progressDialog = null

            val uri = FileProviderUtil.getUriFor(requireContext(), state.zipFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
              type = "application/zip"
              putExtra(Intent.EXTRA_STREAM, uri)
              addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooserIntent = Intent.createChooser(shareIntent, getString(R.string.conversation_export__export_complete))
            if (Build.VERSION.SDK_INT < 34) {
              chooserIntent.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(requireContext(), ShareActivity::class.java)))
            }
            startActivity(chooserIntent)

            viewModel.clearPlaintextExportState()
          }

          is ConversationViewModel.PlaintextExportState.Failed -> {
            progressDialog?.dismiss()
            progressDialog = null
            toast(R.string.conversation_export__export_failed, toastDuration = Toast.LENGTH_LONG)
            viewModel.clearPlaintextExportState()
          }

          is ConversationViewModel.PlaintextExportState.Cancelled -> {
            progressDialog?.dismiss()
            progressDialog = null
            toast(R.string.conversation_export__export_cancelled, toastDuration = Toast.LENGTH_SHORT)
            viewModel.clearPlaintextExportState()
          }
        }
      }
    }
  }

  private fun onRecipientChanged(recipient: Recipient) {
    presentWallpaper(recipient)
    presentConversationTitle(recipient)
    presentChatColors(recipient.chatColors)
    invalidateOptionsMenu()
    updateMessageRequestAcceptedState(!viewModel.hasMessageRequestState)

    recyclerViewColorizer.setChatColors(recipient.chatColors)
  }

  @MainThread
  private fun updateMessageRequestAcceptedState(isMessageRequestAccepted: Boolean) {
    adapter.setMessageRequestIsAccepted(isMessageRequestAccepted)
  }

  private fun invalidateOptionsMenu() {
    if (searchMenuItem?.isActionViewExpanded != true || !isSearchRequested) {
      binding.toolbar.invalidateMenu()
    }
  }

  private fun presentActionBarMenu() {
    if (!args.conversationScreenType.isInPopup) {
      optionsMenuCallback = ConversationOptionsMenuCallback()
      menuProvider = ConversationOptionsMenu.Provider(optionsMenuCallback, disposables)
      binding.toolbar.addMenuProvider(menuProvider!!)
      invalidateOptionsMenu()

      binding.lightTopBar.hasOverflow = true
      // The Light bar draws the ellipses; the Toolbar still owns the menu and the popup. Its own
      // overflow button is laid out directly behind that ellipses, so the popup lands under it.
      binding.lightTopBar.onOverflowClick = { binding.toolbar.showOverflowMenu() }
    }

    when (args.conversationScreenType) {
      ConversationScreenType.NORMAL -> presentNavigationIconForNormal()

      ConversationScreenType.BUBBLE,
      ConversationScreenType.POPUP -> presentNavigationIconForBubble()
    }
  }

  private fun presentNavigationIconForNormal() {
    lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.RESUMED) {
        mainNavigationViewModel.isFullScreenPane.collect { isFullScreenPane ->
          updateNavigationIconForNormal(isFullScreenPane)
        }
      }
    }
  }

  private fun updateNavigationIconForNormal(isFullScreenPane: Boolean) {
    binding.lightTopBar.onLeftClick = {
      binding.root.hideKeyboard(composeText)
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    // A split pane already shows the list beside the thread, so there is nothing to go back to.
    binding.lightTopBar.leftAction = if (!resources.isSplitPane() || isFullScreenPane) {
      LightConversationTopBarLeftAction.BACK
    } else {
      LightConversationTopBarLeftAction.NONE
    }
  }

  private fun presentNavigationIconForBubble() {
    binding.lightTopBar.leftAction = LightConversationTopBarLeftAction.LAUNCH_MAIN_APP
    binding.lightTopBar.onLeftClick = {
      startActivity(MainActivity.clearTop(requireContext()))
    }
  }

  /**
   * The Light top bar carries a name and nothing else, so everything the old `ConversationTitleView`
   * layered around it -- avatar, phone-number subtitle, verified tick, disappearing-messages badge,
   * mute and block glyphs -- is gone from the bar. None of that state is lost: it all still lives in
   * conversation settings, which the name still opens.
   */
  private fun presentConversationTitle(recipient: Recipient?) {
    if (recipient == null) {
      return
    }

    binding.lightTopBar.title = if (recipient.isSelf) {
      getString(R.string.note_to_self)
    } else {
      recipient.getDisplayName(requireContext())
    }

    binding.lightTopBar.onTitleClick = if (args.conversationScreenType.isInPopup) {
      null
    } else {
      { optionsMenuCallback.handleConversationSettings() }
    }
  }

  private fun presentWallpaper(recipient: Recipient) {
    val chatWallpaper = recipient.wallpaper
    if (recipient.isReleaseNotes) {
      applyReleaseNotesWallpaper()
    } else {
      applyChatWallpaper(chatWallpaper)
    }

    val wallpaperEnabled = chatWallpaper != null || recipient.isReleaseNotes

    binding.conversationWallpaper.visible = wallpaperEnabled
    binding.scrollToBottom.setWallpaperEnabled(wallpaperEnabled)
    binding.scrollToMention.setWallpaperEnabled(wallpaperEnabled)
    binding.conversationDisabledInput.setWallpaperEnabled(wallpaperEnabled)
    inputPanel.setWallpaperEnabled(wallpaperEnabled)
    // LIGHT PHONE: `setWallpaperEnabled` rewrites the panel's background, the compose bubble's
    // background and the compose text's colours from Signal's Material attributes, which undoes the
    // Light styling every time the wallpaper state is presented. Put it straight back -- black on a
    // Material surface would be exactly the invisible-text failure this fork has already shipped
    // twice. `install` is idempotent.
    LightInputPanelChrome.install(inputPanel)

    val stateChanged = adapter.onHasWallpaperChanged(wallpaperEnabled)
    conversationItemDecorations.hasWallpaper = wallpaperEnabled
    conversationItemDecorations.isReleaseNotes = recipient.isReleaseNotes
    if (stateChanged) {
      binding.conversationItemRecycler.invalidateItemDecorations()
    }

    binding.scrollDateHeader.setBackgroundResource(
      if (wallpaperEnabled) R.drawable.sticky_date_header_background_wallpaper else R.drawable.sticky_date_header_background
    )

    binding.scrollDateHeader.setTextColor(
      ThemeUtil.getThemedColor(
        requireContext(),
        if (wallpaperEnabled) R.color.sticky_header_foreground_wallpaper else MaterialR.attr.colorOnSurfaceVariant
      )
    )

    if (!inputPanel.isHidden) {
      setNavBarBackgroundColor(wallpaperEnabled)
    }
  }

  private fun applyReleaseNotesWallpaper() {
    if (releaseNotesWallpaperApplied) {
      return
    }
    releaseNotesWallpaperApplied = true

    val tinted = DrawableUtil.tint(
      AppCompatResources.getDrawable(requireContext(), R.drawable.release_chat_background)!!,
      ContextCompat.getColor(requireContext(), R.color.release_notes_background_pattern)
    )
    val bitmap = DrawableUtil.toBitmap(tinted, tinted.intrinsicWidth, tinted.intrinsicHeight)

    binding.conversationWallpaper.scaleType = ImageView.ScaleType.MATRIX
    binding.conversationWallpaper.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.release_notes_background))
    binding.conversationWallpaper.setImageDrawable(RotatedTiledDrawable(bitmap, -45f))
    binding.conversationWallpaperDim.visible = false
  }

  private fun applyChatWallpaper(chatWallpaper: ChatWallpaper?) {
    if (chatWallpaper != null) {
      chatWallpaper.loadInto(binding.conversationWallpaper)
      ChatWallpaperDimLevelUtil.applyDimLevelForNightMode(binding.conversationWallpaperDim, chatWallpaper)
    } else {
      binding.conversationWallpaperDim.visible = false
    }
  }

  private fun setNavBarBackgroundColor(hasWallpaper: Boolean) {
    val navColor = if (hasWallpaper) {
      R.color.conversation_navigation_wallpaper
    } else {
      MaterialR.attr.colorSurface
    }

    binding.navBar.setBackgroundColor(ThemeUtil.getThemedColor(requireContext(), navColor))
  }

  private fun presentChatColors(chatColors: ChatColors) {
    recyclerViewColorizer.setChatColors(chatColors)
    binding.scrollToMention.setUnreadCountBackgroundTint(chatColors.asSingleColor())
    binding.scrollToBottom.setUnreadCountBackgroundTint(chatColors.asSingleColor())
    binding.conversationInputPanel.buttonToggle.background.apply {
      colorFilter = PorterDuffColorFilter(chatColors.asSingleColor(), PorterDuff.Mode.MULTIPLY)
      invalidateSelf()
    }
  }

  private fun presentScrollButtons(scrollButtonState: ConversationScrollButtonState) {
    Log.d(TAG, "Update scroll state $scrollButtonState")
    binding.scrollToBottom.setUnreadCount(scrollButtonState.unreadCount)
    binding.scrollToMention.setUnreadCount(0)
    binding.scrollToMention.isShown = scrollButtonState.hasMentions && scrollButtonState.showScrollButtons
    binding.scrollToBottom.isShown = scrollButtonState.showScrollButtons
  }

  private fun presentGroupCallJoinButton() {
    binding.conversationGroupCallJoin.setOnClickListener {
      handleVideoCall()
    }

    disposables += groupCallViewModel
      .state
      .distinctUntilChanged()
      .subscribeBy {
        binding.conversationGroupCallJoin.visible = it.ongoingCall
        binding.conversationGroupCallJoin.setText(if (it.hasCapacity) R.string.ConversationActivity_join else R.string.ConversationActivity_full)
        invalidateOptionsMenu()
      }
  }

  private fun handlePinMessage(conversationMessage: ConversationMessage) {
    if (viewModel.pinnedMessages.value.size >= RemoteConfig.pinLimit) {
      MaterialAlertDialogBuilder(requireContext())
        .setTitle(resources.getString(R.string.ConversationFragment__replace_title))
        .setMessage(resources.getString(R.string.ConversationFragment__replace_body))
        .setPositiveButton(R.string.ConversationFragment__replace) { _, _ ->
          showPinForDialog(conversationMessage)
        }
        .setNegativeButton(R.string.ConversationFragment__cancel) { dialog, _ -> dialog.dismiss() }
        .show()
    } else {
      showPinForDialog(conversationMessage)
    }
  }

  private fun showPinForDialog(conversationMessage: ConversationMessage) {
    val threadRecipient = viewModel.recipientSnapshot ?: return
    var selection = 1
    val labels = resources.getStringArray(R.array.ConversationFragment__pinned_for_labels)
    val values = resources.getIntArray(R.array.ConversationFragment__pinned_for_values)

    MaterialAlertDialogBuilder(requireContext())
      .setTitle(resources.getString(R.string.ConversationFragment__keep_pinned))
      .setSingleChoiceItems(labels, selection) { dialog, which ->
        selection = which
      }
      .setPositiveButton(android.R.string.ok) { dialog, _ ->
        if (conversationMessage.messageRecord.expiresIn > 0 && SignalStore.uiHints.shouldDisplayPinnedSheet()) {
          PinDisappearingMessageBottomSheet.show(childFragmentManager)
          SignalStore.uiHints.incrementSeenPinnedSheetCount()
        }
        disposables += viewModel
          .pinMessage(
            messageRecord = conversationMessage.messageRecord,
            duration = if (values[selection] == -1) kotlin.time.Duration.INFINITE else values[selection].days,
            threadRecipient = threadRecipient
          )
          .doOnSubscribe {
            handler.postDelayed({ showSpinner() }, PIN_SPINNER_DELAY.inWholeMilliseconds)
          }
          .doFinally {
            handler.removeCallbacksAndMessages(null)
            hideSpinner()
          }
          .subscribeBy(
            onError = {
              Log.w(TAG, "Error received during pin message!", it)
              MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.PinnedMessage__couldnt_pin)
                .setMessage(getString(R.string.PinnedMessage__check_connection))
                .setPositiveButton(android.R.string.ok) { dialog: DialogInterface?, which: Int -> dialog!!.dismiss() }
                .show()
            }
          )
        dialog.dismiss()
      }
      .setNegativeButton(android.R.string.cancel) { dialog, _ ->
        dialog.dismiss()
      }
      .show()
  }

  private fun handleUnpinMessage(messageId: Long) {
    disposables += viewModel
      .unpinMessage(messageId)
      .doOnSubscribe {
        handler.postDelayed({ showSpinner() }, PIN_SPINNER_DELAY.inWholeMilliseconds)
      }
      .doFinally {
        handler.removeCallbacksAndMessages(null)
        hideSpinner()
      }
      .subscribeBy(
        onError = {
          Log.w(TAG, "Error received during unpin message!", it)
          MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.PinnedMessage__couldnt_unpin)
            .setMessage(getString(R.string.PinnedMessage__check_connection))
            .setPositiveButton(android.R.string.ok) { dialog: DialogInterface?, which: Int -> dialog!!.dismiss() }
            .show()
        }
      )
  }

  private fun handleStarMessages(messageIds: Set<Long>) {
    disposables += viewModel
      .setMessagesStarred(messageIds, true)
      .subscribeBy(
        onError = { Log.w(TAG, "Error starring message!", it) }
      )
  }

  private fun handleUnstarMessages(messageIds: Set<Long>) {
    disposables += viewModel
      .setMessagesStarred(messageIds, false)
      .subscribeBy(
        onError = { Log.w(TAG, "Error unstarring message!", it) }
      )
  }

  private fun handleVideoCall() {
    val recipient = viewModel.recipientSnapshot ?: return
    if (!recipient.isGroup) {
      CommunicationActions.startVideoCall(this, recipient) {
        YouAreAlreadyInACallSnackbar.show(requireView())
      }
      return
    }

    val hasActiveGroupCall: Single<Boolean> = groupCallViewModel.state.map { it.ongoingCall }.firstOrError()
    val isNonAdminInAnnouncementGroup: Boolean = conversationGroupViewModel.isNonAdminInAnnouncementGroup()
    val cannotCreateGroupCall = hasActiveGroupCall.map { active ->
      recipient to (recipient.isPushV2Group && !active && isNonAdminInAnnouncementGroup)
    }

    disposables += cannotCreateGroupCall
      .observeOn(AndroidSchedulers.mainThread())
      .subscribe { (recipient, notAllowed) ->
        if (notAllowed) {
          ConversationDialogs.displayCannotStartGroupCallDueToPermissionsDialog(requireContext())
        } else {
          CommunicationActions.startVideoCall(this, recipient) {
            YouAreAlreadyInACallSnackbar.show(requireView())
          }
        }
      }
  }

  private fun handleBlockJoinRequest(recipient: Recipient) {
    disposables += conversationGroupViewModel.blockJoinRequests(recipient).subscribeBy { result ->
      if (result.isFailure()) {
        val failureReason = GroupErrors.getUserDisplayMessage((result as GroupBlockJoinRequestResult.Failure).reason)
        Toast.makeText(requireContext(), failureReason, Toast.LENGTH_SHORT).show()
      } else {
        Toast.makeText(requireContext(), R.string.ConversationFragment__blocked, Toast.LENGTH_SHORT).show()
      }
    }
  }

  private fun handleShareOrDraftData(inputReadyState: InputReadyState, data: ShareOrDraftData) {
    shareDataTimestampViewModel.setTimestampFromConversationArgs(args)

    if (inputReadyState.isAnnouncementGroup == true && inputReadyState.isAdmin == false) {
      Toast.makeText(requireContext(), R.string.MultiselectForwardFragment__only_admins_can_send_messages_to_this_group, Toast.LENGTH_SHORT).show()
      draftViewModel.clearDraft()
      return
    } else if (inputReadyState.shouldClearDraft()) {
      draftViewModel.clearDraft()
      return
    }

    when (data) {
      is ShareOrDraftData.SendKeyboardImage -> sendMessageWithoutComposeInput(slide = data.slide, clearCompose = false)

      is ShareOrDraftData.SendSticker -> sendMessageWithoutComposeInput(slide = data.slide, clearCompose = true)

      is ShareOrDraftData.SetText -> {
        composeText.setDraftText(data.text)
        inputPanel.clickOnComposeInput()
      }

      is ShareOrDraftData.SetLocation -> attachmentManager.setLocation(data.location, PushMediaConstraints(null))

      is ShareOrDraftData.SetEditMessage -> {
        composeText.setDraftText(data.draftText)
        inputPanel.enterEditMessageMode(Glide.with(this), data.messageEdit, true, data.clearQuote)
      }

      is ShareOrDraftData.SetMedia -> {
        composeText.setDraftText(data.text)
        setMedia(data.media, data.mediaType)
      }

      is ShareOrDraftData.SetQuote -> {
        composeText.setDraftText(data.draftText)
        // Restoring a saved quote draft, not answering a reply the user just asked for: set the
        // reply up but leave them on the thread. Opening the thread used to land straight in the
        // composer, which read as the app being stuck on a reply they had already walked away from.
        handleReplyToMessage(data.quote, openComposer = false)
      }

      is ShareOrDraftData.StartSendMedia -> {
        val recipientId = viewModel.recipientSnapshot?.id ?: return
        conversationActivityResultContracts.launchMediaEditor(data.mediaList, recipientId, data.text)
      }
    }
  }

  private fun handleScheduledMessagesCountChange(count: Int) {
    if (count <= 0) {
      scheduledMessagesStub.visibility = View.GONE
      reShowScheduleMessagesBar = false
    } else {
      scheduledMessagesStub.get().apply {
        visibility = View.VISIBLE

        findViewById<View>(R.id.scheduled_messages_show_all)
          .setOnClickListener {
            val recipient = viewModel.recipientSnapshot ?: return@setOnClickListener
            container.runAfterAllHidden(composeText) {
              ScheduledMessagesBottomSheet.show(childFragmentManager, args.threadId, recipient.id)
            }
          }

        findViewById<TextView>(R.id.scheduled_messages_text).text = resources.getQuantityString(R.plurals.conversation_scheduled_messages_bar__number_of_messages, count, count)
      }
      reShowScheduleMessagesBar = true
    }
  }

  private fun handleSendEditMessage() {
    if (!inputPanel.inEditMessageMode()) {
      Log.w(TAG, "Not in edit message mode, unknown state, forcing re-exit")
      inputPanel.exitEditMessageMode()
      return
    }

    val editMessage = inputPanel.editMessage
    if (editMessage == null) {
      Log.w(TAG, "No edit message found, forcing exit")
      inputPanel.exitEditMessageMode()
      return
    }

    if (!isValidEditMessageSend(editMessage, System.currentTimeMillis())) {
      Log.i(TAG, "Edit message no longer valid")
      val editDurationHours = getEditMessageThresholdHours()
      Dialogs.showAlertDialog(requireContext(), null, resources.getQuantityString(R.plurals.ConversationActivity_edit_message_too_old, editDurationHours, editDurationHours))
      return
    }

    if (editMessage.body == composeText.editableText.toString().trim() &&
      editMessage.getQuote()?.displayText?.toString() == inputPanel.quote.map { it.text }.orNull() &&
      editMessage.messageRanges == composeText.styling &&
      editMessage.hasLinkPreview() == inputPanel.hasLinkPreview()
    ) {
      Log.d(TAG, "Updated message matches original, exiting edit mode")
      inputPanel.exitEditMessageMode()
      return
    }

    sendMessage()
  }

  private fun getVoiceNoteMediaController() = requireListener<VoiceNoteMediaControllerOwner>().voiceNoteMediaController

  private fun initializeConversationThreadUi() {
    layoutManager = ConversationLayoutManager(requireContext())
    binding.conversationItemRecycler.setHasFixedSize(false)
    binding.conversationItemRecycler.layoutManager = layoutManager
    scrollListener = ScrollListener()
    binding.conversationItemRecycler.addOnScrollListener(scrollListener!!)

    adapter = ConversationAdapterV2(
      lifecycleOwner = viewLifecycleOwner,
      requestManager = Glide.with(this),
      clickListener = ConversationItemClickListener(),
      hasWallpaper = args.hasWallpaper,
      colorizer = colorizer,
      startExpirationTimeout = viewModel::startExpirationTimeout,
      chatColorsDataProvider = viewModel::chatColorsSnapshot,
      displayDialogFragment = { it.show(childFragmentManager, null) }
    )

    typingIndicatorAdapter = ConversationTypingIndicatorAdapter(Glide.with(this))

    scrollToPositionDelegate = ScrollToPositionDelegate(
      recyclerView = binding.conversationItemRecycler,
      canJumpToPosition = adapter::canJumpToPosition
    )

    adapter.setPagingController(viewModel.pagingController)

    recyclerViewColorizer = RecyclerViewColorizer(binding.conversationItemRecycler)
    viewModel.recipientSnapshot?.chatColors?.let { recyclerViewColorizer.setChatColors(it) }

    binding.conversationItemRecycler.adapter = ConcatAdapter(typingIndicatorAdapter, adapter)
    multiselectItemDecoration = MultiselectItemDecoration(
      requireContext()
    ) { viewModel.wallpaperSnapshot }

    binding.conversationItemRecycler.addItemDecoration(multiselectItemDecoration)
    viewLifecycleOwner.lifecycle.addObserver(multiselectItemDecoration)

    giphyMp4ProjectionRecycler = initializeGiphyMp4()

    val layoutTransitionListener = BubbleLayoutTransitionListener(binding.conversationItemRecycler)
    viewLifecycleOwner.lifecycle.addObserver(layoutTransitionListener)

    binding.conversationItemRecycler.itemAnimator = ConversationItemAnimator(
      isInMultiSelectMode = adapter.selectedItems::isNotEmpty,
      shouldPlayMessageAnimations = {
        animationsAllowed && scrollToPositionDelegate.isListCommitted() && binding.conversationItemRecycler.scrollState == RecyclerView.SCROLL_STATE_IDLE
      },
      isParentFilled = {
        binding.conversationItemRecycler.canScrollVertically(1) || binding.conversationItemRecycler.canScrollVertically(-1)
      },
      shouldUseSlideAnimation = { viewHolder ->
        true
      }
    )

    conversationHeaderPositionDecoration = ConversationHeaderPositionDecoration()

    val statusBarInset = ViewCompat.getRootWindowInsets(binding.root)?.getInsets(WindowInsetsCompat.Type.systemBars())?.top ?: 0
    conversationHeaderPositionDecoration.toolbarMargin = statusBarInset + resources.getDimensionPixelSize(R.dimen.signal_m3_toolbar_height) + 16.dp
    binding.conversationItemRecycler.addItemDecoration(conversationHeaderPositionDecoration)

    conversationItemDecorations = ConversationItemDecorations(hasWallpaper = args.hasWallpaper)
    binding.conversationItemRecycler.addItemDecoration(conversationItemDecorations, 0)
  }

  private fun initializeGiphyMp4(): GiphyMp4ProjectionRecycler {
    val maxPlayback = GiphyMp4PlaybackPolicy.maxSimultaneousPlaybackInConversation()
    val holders = GiphyMp4ProjectionPlayerHolder.injectVideoViews(
      requireContext(),
      viewLifecycleOwner.lifecycle,
      binding.conversationVideoContainer,
      maxPlayback
    )

    val callback = GiphyMp4ProjectionRecycler(holders)
    GiphyMp4PlaybackController.attach(binding.conversationItemRecycler, callback, maxPlayback)
    binding.conversationItemRecycler.addItemDecoration(
      GiphyMp4ItemDecoration(callback),
      0
    )
    return callback
  }

  private fun initializeSearch() {
    searchViewModel.searchResults.observe(viewLifecycleOwner) { result ->
      if (result == null) {
        return@observe
      }

      if (result.results.isNotEmpty()) {
        val messageResult = result.results[result.position]
        disposables += viewModel
          .moveToDate(messageResult.receivedTimestampMs)
          .observeOn(AndroidSchedulers.mainThread())
          .subscribeBy {
            moveToPosition(it)
          }
      }

      searchNav.setData(result.position, result.results.size)
    }

    searchNav.setEventListener(SearchEventListener())

    disposables += viewModel.searchQuery.subscribeBy {
      adapter.updateSearchQuery(it)
    }
  }

  private fun initializeLinkPreviews() {
    linkPreviewViewModel.linkPreviewState
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy { state ->
        if (state.isLoading) {
          inputPanel.setLinkPreviewLoading()
        } else if (state.hasLinks() && !state.linkPreview.isPresent) {
          inputPanel.setLinkPreviewNoPreview(state.error)
        } else {
          inputPanel.setLinkPreview(Glide.with(this), state.linkPreview)
        }

        updateToggleButtonState()
      }
      .addTo(disposables)
  }

  private fun initializeMediaKeyboard() {
    val keyboardMode: TextSecurePreferences.MediaKeyboardMode = TextSecurePreferences.getMediaKeyboardMode(requireContext())

    keyboardPagerViewModel.resetPages()
    // LIGHT PHONE: no emoji, sticker or GIF picker in the composer, so the toggle that opened them is
    // gone from the input panel and `MediaKeyboardFragmentCreator` has no way left to be reached. The
    // pager's own state is still primed below, because edit mode saves and restores it.
    inputPanel.showMediaKeyboardToggle(false)

    val keyboardPage = when (keyboardMode) {
      TextSecurePreferences.MediaKeyboardMode.EMOJI -> KeyboardPage.EMOJI
      TextSecurePreferences.MediaKeyboardMode.STICKER -> KeyboardPage.STICKER
      TextSecurePreferences.MediaKeyboardMode.GIF -> if (RemoteConfig.gifSearchAvailable) KeyboardPage.GIF else KeyboardPage.STICKER
    }

    inputPanel.setMediaKeyboardToggleMode(keyboardPage)
    keyboardPagerViewModel.switchToPage(keyboardPage)
  }

  private fun updateLinkPreviewState() {
    if (viewModel.isPushAvailable && !attachmentManager.isAttachmentPresent && context != null && inputPanel.editMessage?.hasNonTextSlide() != true) {
      linkPreviewViewModel.onEnabled()
      linkPreviewViewModel.onTextChanged(composeText.textTrimmed.toString(), composeText.selectionStart, composeText.selectionEnd)
    } else {
      linkPreviewViewModel.onUserCancel()
    }
  }

  private fun updateToggleButtonState() {
    val buttonToggle: AnimatingToggle = binding.conversationInputPanel.buttonToggle
    val quickAttachment: HidingLinearLayout = binding.conversationInputPanel.quickAttachmentToggle
    val inlineAttachment: HidingLinearLayout = binding.conversationInputPanel.inlineAttachmentContainer

    when {
      inputPanel.isRecordingInLockedMode -> {
        buttonToggle.display(sendButton)
        quickAttachment.show()
        inlineAttachment.hide(true)
      }

      inputPanel.inEditMessageMode() -> {
        buttonToggle.display(sendEditButton)
        quickAttachment.hide(false)
        inlineAttachment.hide(false)
      }

      draftViewModel.voiceNoteDraft != null -> {
        buttonToggle.display(sendButton)
        quickAttachment.hide(true)
        inlineAttachment.hide(true)
      }

      composeText.text.isNullOrBlank() && !attachmentManager.isAttachmentPresent -> {
        buttonToggle.display(binding.conversationInputPanel.attachButton)
        quickAttachment.show()
        inlineAttachment.hide(true)
      }

      else -> {
        buttonToggle.display(sendButton)
        quickAttachment.hide(true)

        if (!attachmentManager.isAttachmentPresent && !linkPreviewViewModel.hasLinkPreviewUi) {
          inlineAttachment.show()
        } else {
          inlineAttachment.hide(true)
        }
      }
    }
  }

  //region Light Phone thread chrome

  /**
   * Wires up the thread's Light chrome: the three-icon bottom bar, the full-screen composer, and the
   * action panel the call menu opens into.
   *
   * None of this replaces `InputPanel`. The panel is still the headless owner of the draft, the
   * pending reply, edit mode, voice recording, link previews, mentions and styling -- everything
   * below writes into the same `composeText` and calls the same [sendMessage], which is what makes
   * all of that keep working for free.
   */
  private fun initializeLightInputChrome() {
    LightInputPanelChrome.install(inputPanel)

    lightBottomBar.onCallClick = { showLightCallMenu() }
    // `showSoftKeyOnHide = false` explicitly: the default is "put the keyboard back if it was up
    // before", and there is nothing on the Light thread to put it back *for* -- the entry only exists
    // while the composer is up. Leaving it to the default is how a keyboard ends up hanging over a
    // thread with nothing to type into.
    lightBottomBar.onAddClick = {
      container.toggleInput(AttachmentKeyboardFragmentCreator, composeText, showSoftKeyOnHide = false)
    }
    lightBottomBar.onComposeClick = { openLightComposer() }

    lightComposerView.onBack = { closeLightComposer(cancelEdit = true) }
    lightComposerView.onSend = { sendFromLightComposer() }
    lightComposerView.onClearQuote = {
      inputPanel.clearQuote()
      presentLightComposer()
    }

    lightActionPanel.onDismiss = { dismissLightActionPanel() }

    updateLightInputChrome()
  }

  /**
   * Decides, in one place, which of the input panel and the Light bottom bar holds the thread's
   * bottom slot. They are never both showing.
   *
   * The panel is expanded only when it has something of its own to draw: the composer's text entry,
   * a recording in progress, or a recorded voice-note draft. The rest of the time it collapses out
   * of the layout and the bar stands in for it, while the panel goes on holding the draft and the
   * pending reply behind the scenes.
   *
   * Both step aside together for the states that claim this same slot and hide the panel with one of
   * the `setHideFor...` flags: disabled input and message requests (`conversation_disabled_input`),
   * in-conversation search (`conversation_search_bottom_bar`) and multi-select
   * (`conversation_bottom_action_bar`). `InputPanel.isHidden` is the single predicate over all of
   * them, so the bar cannot drift out of step with the panel it stands in for.
   */
  private fun updateLightInputChrome() {
    // Nothing above may keep the composer up over a state that has taken the slot -- search
    // collapsing the panel out from under an open composer would leave the entry nowhere.
    if (lightComposerOpen && inputPanel.isHidden) {
      closeLightComposer(cancelEdit = false)
      return
    }

    val slot = LightThreadBottomSlot.forState(
      inputPanelHidden = inputPanel.isHidden,
      composerOpen = lightComposerOpen,
      recording = lightRecordingActive,
      hasVoiceNoteDraft = inputPanel.voiceNoteDraft != null
    )

    inputPanel.setLightExpanded(slot == LightThreadBottomSlot.INPUT_PANEL)
    LightInputPanelChrome.setComposerMode(inputPanel, lightComposerOpen)

    lightBottomBar.showCall = lightCanVoiceCall || lightCanVideoCall
    lightBottomBar.visible = slot == LightThreadBottomSlot.LIGHT_BAR
  }

  /**
   * Opens the call menu: the available call actions as rows in a Light action panel.
   *
   * A group offers only a video call, so its menu is a single row -- shown all the same rather than
   * dialled through, so that the gesture means the same thing on every thread. The overflow menu goes
   * on carrying both actions as well; that duplication is deliberate.
   */
  private fun showLightCallMenu() {
    val actions = buildList {
      if (lightCanVoiceCall) {
        add(
          LightPanelAction(getString(R.string.LightCallMenu__audio_call)) {
            dismissLightActionPanel()
            optionsMenuCallback.handleDial()
          }
        )
      }

      if (lightCanVideoCall) {
        add(
          LightPanelAction(getString(R.string.LightCallMenu__video_call)) {
            dismissLightActionPanel()
            optionsMenuCallback.handleVideo()
          }
        )
      }
    }

    showLightActionPanel(actions)
  }

  /**
   * Opens the Light context window over the long-pressed message.
   *
   * The rows are Molly's own menu for that message -- `buildMessageMenu` decides which of the
   * fourteen actions apply and gates them, exactly as it does for the dropdown this replaces, so
   * every one of them (and any the next upstream merge adds) arrives here already correct. This side
   * only adds the reaction rows, which Signal keeps in a scrubber above the menu rather than in it.
   *
   * Reactions go through `updateReaction`, the same call the scrubber made: sending the emoji you
   * already have on the message takes it off, which is both Signal's own toggle and what REMOVE
   * REACTION is built out of. One reaction at a time, so a second one replaces the first.
   */
  private fun showLightMessageMenu(conversationMessage: ConversationMessage) {
    val recipient = viewModel.recipientSnapshot ?: return
    val messageRecord = conversationMessage.messageRecord

    val menu = ConversationReactionOverlay.buildMessageMenu(
      requireContext(),
      recipient,
      conversationMessage,
      conversationGroupViewModel.isNonAdminInAnnouncementGroup(),
      conversationGroupViewModel.canEditGroupInfo(),
      ReactionsToolbarListener(conversationMessage)
    )

    val ownReaction = messageRecord.reactions.firstOrNull { it.author == Recipient.self().id }
    val react: (String) -> Unit = { emoji ->
      disposables += viewModel.updateReaction(messageRecord, emoji).subscribe()
    }

    showLightActionPanel(
      actions = LightMessageMenu.rows(
        context = requireContext(),
        actions = menu.items,
        canReact = menu.shouldShowReactions(),
        hasOwnReaction = ownReaction != null,
        onOpenReactionKeys = { lightActionPanel.showReactionKeys() },
        onRemoveReaction = { ownReaction?.let { react(it.emoji) } },
        onDismiss = { dismissLightActionPanel() }
      ),
      onReactionKeySelected = if (menu.shouldShowReactions()) {
        { emoji ->
          dismissLightActionPanel()
          react(emoji)
        }
      } else {
        null
      }
    )
  }

  /**
   * Opens the Light action panel over the bottom of the thread.
   *
   * [onReactionKeySelected] is what lets a row descend to the reaction keys; the call menu has no
   * second level and passes nothing.
   */
  private fun showLightActionPanel(actions: List<LightPanelAction>, onReactionKeySelected: ((String) -> Unit)? = null) {
    if (actions.isEmpty()) {
      return
    }

    // The panel and the keyboard both want the bottom of the screen, and the keyboard is a window of
    // its own drawn over this one -- raised over an open composer, it would hide the panel entirely.
    // The attachment keyboard goes the same way. The composer itself stays as it is, draft and all,
    // and is still there when the panel closes.
    container.hideAll(composeText)

    lightActionPanel.show(actions, onReactionKeySelected)
    viewModel.setIsLightActionPanelShowing(true)
  }

  private fun dismissLightActionPanel() {
    if (!lightActionPanel.isOpen) {
      return
    }

    lightActionPanel.close()
    viewModel.setIsLightActionPanelShowing(false)
  }

  /**
   * Expands the input into the full-screen Light composer.
   *
   * This is an expansion in place, not a screen: the real `ComposeText` never moves, which is what
   * keeps @-mention autocomplete (the `InlineQuery` popups anchor to that very view), text styling,
   * spoilers, paste-an-image and draft save/restore working. All that changes is that the panel comes
   * back into the layout with its Signal chrome stripped, and [LightComposerView] paints the field,
   * the top bar and the reply line above it.
   */
  private fun openLightComposer() {
    if (lightComposerOpen) {
      // Already up, and something has just changed underneath it -- a reply quote set from the
      // context window or a swipe, an edit entered. Redraw rather than returning, or the composer
      // goes on showing the state it opened with and the reply the user asked for never appears.
      dismissLightActionPanel()
      presentLightComposer()
      ViewUtil.focusAndShowKeyboard(composeText)
      return
    }

    if (inputPanel.isHidden) {
      return
    }

    dismissLightActionPanel()
    if (container.isInputShowing) {
      container.hideInput()
    }

    lightComposerOpen = true
    viewModel.setIsLightComposerShowing(true)
    lightComposerView.visible = true
    updateLightInputChrome()
    presentLightComposer()

    // The panel was collapsed out of the layout until the line above, so the entry cannot take focus
    // or raise the keyboard until it has been laid out again.
    composeText.post {
      if (lightComposerOpen && isAdded && view != null) {
        ViewUtil.focusAndShowKeyboard(composeText)
      }
    }
  }

  /**
   * Closes the composer. The draft survives -- the text, the pending reply and any staged attachment
   * are all still on the panel, and Molly's own draft persistence takes it from there.
   *
   * @param cancelEdit whether an in-progress message edit is abandoned. True when the user asked to
   *   leave (back, or the composer's back chevron); false when the composer is closing because the
   *   message has just gone, or because something else has claimed the bottom of the screen.
   */
  private fun closeLightComposer(cancelEdit: Boolean) {
    if (!lightComposerOpen) {
      return
    }

    if (cancelEdit && inputPanel.inEditMessageMode()) {
      inputPanel.exitEditMessageMode()
    }

    lightComposerOpen = false
    viewModel.setIsLightComposerShowing(false)
    lightComposerView.visible = false

    // Explicitly, and against `composeText` while it is still laid out: the reaction overlay saves
    // and restores focus on this view, and a keyboard left up behind a dismissed composer would come
    // back over the thread with nothing to type into.
    container.hideKeyboard(composeText)

    updateLightInputChrome()
  }

  /** Refreshes what the composer says about itself: whose thread it is, and what it is replying to. */
  private fun presentLightComposer() {
    if (!lightComposerOpen) {
      return
    }

    lightComposerView.title = if (inputPanel.inEditMessageMode()) {
      getString(R.string.LightComposer__editing_message)
    } else {
      binding.lightTopBar.title
    }

    lightComposerView.quote = inputPanel.quote.orNull()?.let { quote ->
      val author = Recipient.resolved(quote.author)
      val name = if (author.isSelf) getString(R.string.QuoteView_you) else author.getDisplayName(requireContext())

      LightQuoteLine.buildPendingLine(requireContext(), name, quote)
    }
  }

  /**
   * Sends what the composer is holding, through exactly the paths the send button used.
   */
  private fun sendFromLightComposer() {
    val hadSomethingToSend = !composeText.text.isNullOrBlank() || attachmentManager.isAttachmentPresent

    if (inputPanel.inEditMessageMode()) {
      handleSendEditMessage()
    } else {
      sendMessage()
    }

    // [sendMessage] clears the compose input the moment a send is accepted, and leaves it alone when
    // one is refused -- an empty body, a recent safety-number change, the scheduled-send dialog. So a
    // cleared input is the signal that the message has gone and the composer's work is done; anything
    // else and it stays up, rather than stranding the draft behind a screen the user can no longer
    // reach.
    if (hadSomethingToSend && composeText.text.isNullOrBlank()) {
      closeLightComposer(cancelEdit = false)
    }
  }

  //endregion Light Phone thread chrome

  private fun sendSticker(
    stickerRecord: StickerRecord,
    clearCompose: Boolean
  ) {
    val stickerLocator = StickerLocator(stickerRecord.packId, stickerRecord.packKey, stickerRecord.stickerId, stickerRecord.emoji)
    val slide = StickerSlide(
      requireContext(),
      stickerRecord.uri,
      stickerRecord.size,
      stickerLocator,
      stickerRecord.contentType
        .takeIf { it.isNotBlank() }
        ?: MediaUtil.IMAGE_WEBP
    )

    val quote = if (SignalStore.labs.stickerReplies) {
      inputPanel.quote.orNull()
    } else {
      null
    }

    sendMessageWithoutComposeInput(slide = slide, quote = quote, clearCompose = clearCompose)

    if (quote != null) {
      inputPanel.clearQuote()
    }

    viewModel.updateStickerLastUsedTime(stickerRecord, System.currentTimeMillis().milliseconds)
  }

  private fun sendMessageWithoutComposeInput(
    slide: Slide? = null,
    contacts: List<Contact> = emptyList(),
    quote: QuoteModel? = null,
    clearCompose: Boolean = true,
    scheduledDate: Long = -1
  ) {
    sendMessage(
      slideDeck = slide?.let { SlideDeck().apply { addSlide(slide) } },
      contacts = contacts,
      clearCompose = clearCompose,
      body = "",
      mentions = emptyList(),
      bodyRanges = null,
      messageToEdit = null,
      quote = quote,
      linkPreviews = emptyList(),
      bypassPreSendSafetyNumberCheck = true,
      scheduledDate = scheduledDate
    )
  }

  private fun sendPoll(recipient: Recipient, poll: Poll) {
    val send = viewModel.sendPoll(recipient, poll)

    disposables += send
      .subscribeBy(
        onComplete = { onSendComplete() },
        onError = { Log.w(TAG, "Error received during poll send!", it) }
      )
  }

  private fun sendMessage(
    body: String = composeText.editableText.toString().trim(),
    mentions: List<Mention> = composeText.mentions,
    bodyRanges: BodyRangeList? = composeText.styling,
    messageToEdit: MessageId? = inputPanel.editMessageId,
    quote: QuoteModel? = inputPanel.quote.orNull(),
    scheduledDate: Long = -1,
    slideDeck: SlideDeck? = if (attachmentManager.isAttachmentPresent) attachmentManager.buildSlideDeck() else null,
    contacts: List<Contact> = emptyList(),
    clearCompose: Boolean = true,
    linkPreviews: List<LinkPreview> = linkPreviewViewModel.onSend(),
    preUploadResults: List<MessageSender.PreUploadResult> = emptyList(),
    bypassPreSendSafetyNumberCheck: Boolean = false,
    isViewOnce: Boolean = false,
    afterSendComplete: () -> Unit = {}
  ) {
    val threadRecipient = viewModel.recipientSnapshot

    if (threadRecipient == null) {
      Log.w(TAG, "Unable to send due to invalid thread recipient")
      toast(R.string.ConversationActivity_recipient_is_not_a_valid_sms_or_email_address_exclamation, Toast.LENGTH_LONG)
      return
    }

    if (scheduledDate != -1L && ReenableScheduledMessagesDialogFragment.showIfNeeded(requireContext(), childFragmentManager, null, scheduledDate)) {
      return
    }

    if (inputPanel.isRecordingInLockedMode) {
      inputPanel.releaseRecordingLockAndSend()
      return
    }

    if (slideDeck == null) {
      val voiceNote: DraftTable.Draft? = draftViewModel.voiceNoteDraft
      if (voiceNote != null) {
        sendMessageWithoutComposeInput(
          slide = AudioSlide.createFromVoiceNoteDraft(voiceNote),
          quote = quote,
          clearCompose = true,
          scheduledDate = scheduledDate
        )
        return
      }
    }

    if (body.isBlank() && slideDeck?.containsMediaSlide() != true && preUploadResults.isEmpty() && contacts.isEmpty()) {
      Log.i(TAG, "Unable to send due to empty message")
      toast(R.string.ConversationActivity_message_is_empty_exclamation)
      return
    }

    if (viewModel.identityRecordsState.hasRecentSafetyNumberChange() && !bypassPreSendSafetyNumberCheck) {
      Log.i(TAG, "Unable to send due to SNC")
      handleRecentSafetyNumberChange(viewModel.identityRecordsState.getRecentSafetyNumberChangeRecords())
      return
    }

    val metricId = viewModel.recipientSnapshot?.let { if (it.isGroup) SignalLocalMetrics.GroupMessageSend.start() else SignalLocalMetrics.IndividualMessageSend.start() }

    val send: Completable = viewModel.sendMessage(
      metricId = metricId,
      threadRecipient = threadRecipient,
      body = body,
      slideDeck = slideDeck,
      scheduledDate = scheduledDate,
      messageToEdit = messageToEdit,
      quote = quote,
      mentions = mentions,
      bodyRanges = bodyRanges,
      contacts = contacts,
      linkPreviews = linkPreviews,
      preUploadResults = preUploadResults,
      isViewOnce = isViewOnce
    )

    disposables += send
      .doOnSubscribe {
        if (clearCompose) {
          AppDependencies.typingStatusSender.onTypingStopped(args.threadId)
          composeTextEventsListener?.typingStatusEnabled = false
          composeText.setText("")
          composeTextEventsListener?.typingStatusEnabled = true
          attachmentManager.clear(Glide.with(this@ConversationFragment), false)
          inputPanel.clearQuote()
        }
        scrollToPositionDelegate.markListCommittedVersion()
      }
      .subscribeBy(
        onComplete = {
          onSendComplete()
          afterSendComplete()
        },
        onError = {
          Log.w(TAG, "Error received during send!", it)
          toast(R.string.ConversationActivity_error_sending_media)
        }
      )
  }

  private fun onSendComplete() {
    if (isDetached || activity?.isFinishing == true) {
      return
    }

    scrollToPositionDelegate.resetScrollPositionAfterMarkListVersionSurpassed()
    attachmentManager.cleanup()

    updateLinkPreviewState()

    draftViewModel.clearDraft()

    inputPanel.exitEditMessageMode()

    if (args.conversationScreenType.isInPopup) {
      activity?.finish()
    }
  }

  private fun handleRecentSafetyNumberChange(changedRecords: List<IdentityRecord>) {
    val recipient = viewModel.recipientSnapshot ?: return
    SafetyNumberBottomSheet
      .forIdentityRecordsAndDestination(changedRecords, RecipientSearchKey(recipient.id, false))
      .show(childFragmentManager)
  }

  private fun toast(@StringRes toastTextId: Int, toastDuration: Int = Toast.LENGTH_SHORT) {
    ThreadUtil.runOnMain {
      if (context != null) {
        Toast.makeText(context, toastTextId, toastDuration).show()
      } else {
        Log.w(TAG, "Dropping toast without context.")
      }
    }
  }

  private fun snackbar(
    @StringRes text: Int,
    anchor: View = binding.conversationItemRecycler,
    @Duration duration: Int = Snackbar.LENGTH_LONG
  ) {
    Snackbar.make(anchor, text, duration).show()
  }

  private fun maybeShowSwipeToReplyTooltip() {
    if (!TextSecurePreferences.hasSeenSwipeToReplyTooltip(requireContext())) {
      val tooltipText = if (ViewUtil.isLtr(requireContext())) {
        R.string.ConversationFragment_you_can_swipe_to_the_right_reply
      } else {
        R.string.ConversationFragment_you_can_swipe_to_the_left_reply
      }

      snackbar(tooltipText)

      TextSecurePreferences.setHasSeenSwipeToReplyTooltip(requireContext(), true)
    }
  }

  private fun calculateSelectedItemCount(): String {
    val count = adapter.selectedItems.map(MultiselectPart::conversationMessage).distinct().count()
    return requireContext().resources.getQuantityString(R.plurals.conversation_context__s_selected, count, count)
  }

  private fun getSelectedConversationMessage(): ConversationMessage {
    val records = adapter.selectedItems.map(MultiselectPart::conversationMessage).distinct().toSet()
    if (records.size == 1) {
      return records.first()
    }

    error("More than one conversation message in set.")
  }

  private fun setCorrectActionModeMenuVisibility() {
    val selectedParts = adapter.selectedItems

    if (isActionModeStarted() && selectedParts.isEmpty()) {
      finishActionMode()
      return
    }

    val recipient = viewModel.recipientSnapshot ?: return
    val menuState = MenuState.getMenuState(
      recipient,
      selectedParts,
      viewModel.hasMessageRequestState,
      conversationGroupViewModel.isNonAdminInAnnouncementGroup(),
      conversationGroupViewModel.canEditGroupInfo()
    )

    val items = arrayListOf<ActionItem>()

    if (menuState.shouldShowReplyAction()) {
      items.add(
        ActionItem(R.drawable.symbol_reply_24, resources.getString(R.string.conversation_selection__menu_reply)) {
          maybeShowSwipeToReplyTooltip()
          handleReplyToMessage(getSelectedConversationMessage())
          finishActionMode()
        }
      )
    }

    if (menuState.shouldShowEditAction()) {
      items.add(
        ActionItem(CoreUiR.drawable.symbol_edit_24, resources.getString(R.string.conversation_selection__menu_edit)) {
          handleEditMessage(getSelectedConversationMessage())
          finishActionMode()
        }
      )
    }

    if (menuState.shouldShowForwardAction()) {
      items.add(
        ActionItem(CoreUiR.drawable.symbol_forward_24, resources.getString(R.string.conversation_selection__menu_forward)) {
          handleForwardMessageParts(selectedParts)
        }
      )
    }

    if (menuState.shouldShowSaveAttachmentAction()) {
      items.add(
        ActionItem(CoreUiR.drawable.symbol_save_android_24, resources.getString(R.string.conversation_selection__menu_save)) {
          handleSaveAttachment(getSelectedConversationMessage().messageRecord as MmsMessageRecord)
          finishActionMode()
        }
      )
    }

    if (menuState.shouldShowCopyAction()) {
      items.add(
        ActionItem(CoreUiR.drawable.symbol_copy_android_24, resources.getString(R.string.conversation_selection__menu_copy)) {
          handleCopyMessage(selectedParts)
          finishActionMode()
        }
      )
    }

    if (menuState.shouldShowDetailsAction()) {
      items.add(
        ActionItem(CoreUiR.drawable.symbol_info_24, resources.getString(R.string.conversation_selection__menu_message_details)) {
          handleDisplayDetails(getSelectedConversationMessage())
          finishActionMode()
        }
      )
    }

    if (menuState.shouldShowStarMessage()) {
      items.add(
        ActionItem(R.drawable.symbol_star_outline_24, resources.getString(R.string.conversation_selection__menu_star)) {
          handleStarMessages(selectedParts.map { it.conversationMessage.messageRecord.id }.toSet())
          finishActionMode()
        }
      )
    }

    if (menuState.shouldShowUnstarMessage()) {
      items.add(
        ActionItem(R.drawable.symbol_star_outline_24, resources.getString(R.string.conversation_selection__menu_unstar)) {
          handleUnstarMessages(selectedParts.map { it.conversationMessage.messageRecord.id }.toSet())
          finishActionMode()
        }
      )
    }

    if (menuState.shouldShowDeleteAction()) {
      items.add(
        ActionItem(CoreUiR.drawable.symbol_trash_24, resources.getString(R.string.conversation_selection__menu_delete)) {
          handleDeleteMessages(selectedParts)
          finishActionMode()
        }
      )
    }

    bottomActionBar.setItems(items)
    setBottomActionBarVisibility(true)
  }

  private fun setBottomActionBarVisibility(isVisible: Boolean) {
    if (view == null) {
      return
    }

    val isCurrentlyVisible = bottomActionBar.isVisible
    if (isVisible == isCurrentlyVisible) {
      return
    }

    val additionalScrollOffset = 54.dp
    if (isVisible) {
      ViewUtil.animateIn(bottomActionBar, bottomActionBar.enterAnimation)
      animationsAllowed = false

      bottomActionBar.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
          if (bottomActionBar.measuredHeight == 0) {
            return
          }

          bottomActionBar.viewTreeObserver.removeOnGlobalLayoutListener(this)

          container.hideInput()
          inputPanel.setHideForSelection(true)
          updateLightInputChrome()

          val bottomPadding = bottomActionBar.measuredHeight + ((bottomActionBar.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 18.dp)
          ViewUtil.setPaddingBottom(binding.conversationItemRecycler, bottomPadding)
          binding.conversationItemRecycler.scrollBy(0, -(bottomPadding - additionalScrollOffset))
          animationsAllowed = true
        }
      })
    } else {
      ViewUtil.animateOut(bottomActionBar, bottomActionBar.exitAnimation)
        .addListener(object : ListenableFuture.Listener<Boolean> {
          override fun onSuccess(result: Boolean?) {
            val scrollOffset = binding.conversationItemRecycler.paddingBottom - additionalScrollOffset
            inputPanel.setHideForSelection(false)
            updateLightInputChrome()
            val bottomPadding = resources.getDimensionPixelSize(R.dimen.conversation_bottom_padding)
            ViewUtil.setPaddingBottom(binding.conversationItemRecycler, bottomPadding)
            binding.conversationItemRecycler.doOnPreDraw {
              it.scrollBy(0, scrollOffset)
            }
          }

          override fun onFailure(e: ExecutionException?) = Unit
        })
    }
  }

  private fun clearFocusedItem() {
    multiselectItemDecoration.setFocusedItem(null)
    binding.conversationItemRecycler.invalidateItemDecorations()
  }

  //region Message Request Helpers

  @SuppressLint("CheckResult")
  private fun onReportSpam() {
    val recipient = viewModel.recipientSnapshot
    if (recipient == null) {
      Log.w(TAG, "[onBlockClicked] No recipient!")
      return
    }

    BlockUnblockDialog.showReportSpamFor(
      requireContext(),
      recipient,
      {
        val disabledInput = binding.conversationDisabledInput
        messageRequestViewModel
          .onReportSpam()
          .doOnSubscribe { disabledInput.showBusy() }
          .doOnTerminate { disabledInput.hideBusy() }
          .subscribeBy {
            Log.d(TAG, "report spam complete")
            toast(R.string.ConversationFragment_reported_as_spam)
          }
      },
      if (recipient.isBlocked) {
        null
      } else {
        Runnable {
          val disabledInput = binding.conversationDisabledInput
          messageRequestViewModel
            .onBlockAndReportSpam()
            .doOnSubscribe { disabledInput.showBusy() }
            .doOnTerminate { disabledInput.hideBusy() }
            .subscribeBy { result ->
              when (result) {
                is Result.Success -> {
                  Log.d(TAG, "report spam complete")
                  toast(R.string.ConversationFragment_reported_as_spam_and_blocked)
                }

                is Result.Failure -> {
                  Log.d(TAG, "report spam failed ${result.failure}")
                  toast(GroupErrors.getUserDisplayMessage(result.failure))
                }
              }
            }
        }
      }
    )
  }

  @SuppressLint("CheckResult")
  private fun onBlock() {
    val recipient = viewModel.recipientSnapshot
    if (recipient == null) {
      Log.w(TAG, "[onBlockClicked] No recipient!")
      return
    }

    BlockUnblockDialog.showBlockFor(
      requireContext(),
      recipient
    ) {
      messageRequestViewModel
        .onBlock()
        .subscribeWithShowProgress("block")
    }
  }

  @SuppressLint("CheckResult")
  private fun onUnblock() {
    val recipient = viewModel.recipientSnapshot
    if (recipient == null) {
      Log.w(TAG, "[onUnblockClicked] No recipient!")
      return
    }

    BlockUnblockDialog.showUnblockFor(
      requireContext(),
      recipient
    ) {
      messageRequestViewModel
        .onUnblock()
        .subscribeWithShowProgress("unblock")
    }
  }

  private fun onMessageRequestAccept() {
    messageRequestViewModel
      .onAccept()
      .subscribeWithShowProgress("accept message request")
  }

  private fun onDeleteConversation() {
    val recipient = viewModel.recipientSnapshot
    if (recipient == null) {
      Log.w(TAG, "[onDeleteConversation] No recipient!")
      return
    }

    ConversationDialogs.displayDeleteDialog(requireContext(), recipient) {
      messageRequestViewModel
        .onDelete()
        .doAfterSuccess { chatRouter.exitDetailLocation() }
        .subscribeWithShowProgress("delete message request")
    }
  }

  private fun Single<Result<Unit, GroupChangeFailureReason>>.subscribeWithShowProgress(logMessage: String): Disposable {
    val disabledInput = binding.conversationDisabledInput
    return doOnSubscribe { disabledInput.showBusy() }
      .doOnTerminate { disabledInput.hideBusy() }
      .subscribeBy { result ->
        when (result) {
          is Result.Success -> Log.d(TAG, "$logMessage complete")

          is Result.Failure -> {
            Log.d(TAG, "$logMessage failed ${result.failure}")
            toast(GroupErrors.getUserDisplayMessage(result.failure))
          }
        }
      }
  }

  private inner class BackPressedCallback : OnBackPressedCallback(false) {
    override fun handleOnBackPressed() {
      handleBackPressed()
    }
  }

  // endregion

  //region Message action handling

  /**
   * @param openComposer whether to take the user straight into the composer. True for a reply the
   *   user just asked for; false when a saved quote draft is merely being restored onto a thread
   *   the user has only opened, where hijacking the screen into a composer is not what they asked
   *   for and leaves no obvious way back to the conversation.
   */
  private fun handleReplyToMessage(conversationMessage: ConversationMessage, openComposer: Boolean = true) {
    if (isSearchRequested) {
      searchMenuItem?.collapseActionView()
    }

    if (inputPanel.inEditMessageMode()) {
      inputPanel.exitEditMessageMode()
    }

    val (slideDeck, body) = viewModel.getSlideDeckAndBodyForReply(requireContext(), conversationMessage)
    val author = conversationMessage.messageRecord.fromRecipient

    inputPanel.setQuote(
      Glide.with(this),
      conversationMessage.messageRecord.dateSent,
      author,
      body,
      slideDeck,
      conversationMessage.messageRecord.getRecordQuoteType()
    )

    // LIGHT PHONE: both swipe-to-reply and the long-press menu's REPLY route through here, so this one
    // redirect covers both. Signal focused the inline input; the Light thread has no inline input to
    // focus, and a pending reply that the user cannot see or answer is worse than no reply at all.
    if (openComposer) {
      openLightComposer()
    }
  }

  private fun handleEditMessage(conversationMessage: ConversationMessage) {
    val isNoteToSelf = viewModel.recipientSnapshot?.isSelf ?: false
    if (!isNoteToSelf && !MessageConstraintsUtil.isWithinMaxEdits(conversationMessage.messageRecord)) {
      Log.i(TAG, "Too many edits to the message")
      Dialogs.showAlertDialog(requireContext(), null, resources.getQuantityString(R.plurals.ConversationActivity_edit_message_too_many_edits, MessageConstraintsUtil.MAX_EDIT_COUNT, MessageConstraintsUtil.MAX_EDIT_COUNT))

      return
    }

    if (isSearchRequested) {
      searchMenuItem?.collapseActionView()
    }

    viewModel.resolveMessageToEdit(conversationMessage)
      .subscribeBy { updatedMessage ->
        inputPanel.enterEditModeIfPossible(Glide.with(this), updatedMessage, false, false)
      }
      .addTo(disposables)
  }

  private fun handleForwardMessageParts(messageParts: Set<MultiselectPart>) {
    inputPanel.clearQuote()

    MultiselectForwardFragmentArgs.create(requireContext(), messageParts) { args ->
      MultiselectForwardFragment.showBottomSheet(childFragmentManager, args)
    }
  }

  private fun handleSaveAttachment(record: MmsMessageRecord) {
    if (record.isViewOnce) {
      error("Cannot save a view-once message")
    }

    lifecycleScope.launch {
      AttachmentSaver(this@ConversationFragment).saveAttachments(record)
    }
  }

  private fun handleCopyMessage(messageParts: Set<MultiselectPart>) {
    viewModel.copyToClipboard(requireContext(), messageParts).subscribe().addTo(disposables)
  }

  private fun handleResend(conversationMessage: ConversationMessage) {
    viewModel.resendMessage(conversationMessage).subscribe()
  }

  private fun handleEnterMultiselect(conversationMessage: ConversationMessage) {
    val parts = conversationMessage.multiselectCollection.toSet()
    parts.forEach { adapter.toggleSelection(it) }
    startActionMode()
  }

  private fun showPaymentTombstoneLearnMoreDialog() {
    val dialogBuilder = MaterialAlertDialogBuilder(requireContext())
    dialogBuilder
      .setTitle(R.string.PaymentTombstoneLearnMoreDialog_title)
      .setMessage(R.string.PaymentTombstoneLearnMoreDialog_message)
      .setPositiveButton(android.R.string.ok, null)

    dialogBuilder.show()
  }

  private fun showAttachmentBackfillFailureDialog(reason: AttachmentBackfill.FailureReason) {
    val messageRes = when (reason) {
      AttachmentBackfill.FailureReason.TIMEOUT -> R.string.ConversationFragment_attachment_backfill_timeout
      AttachmentBackfill.FailureReason.NOT_FOUND -> R.string.ConversationFragment_attachment_backfill_not_found
    }

    MaterialAlertDialogBuilder(requireContext())
      .setTitle(R.string.ConversationFragment_attachment_backfill_failed_title)
      .setMessage(messageRes)
      .setPositiveButton(android.R.string.ok, null)
      .show()
  }

  private fun handleDisplayDetails(conversationMessage: ConversationMessage) {
    val recipientSnapshot = viewModel.recipientSnapshot ?: return
    chatRouter.goToChatDetail(MainNavigationDetailLocation.Chats.MessageDetails(recipientSnapshot.id, MessageId(conversationMessage.messageRecord.id)))
  }

  private fun handleDeleteMessages(messageParts: Set<MultiselectPart>) {
    if (DeleteSyncEducationDialog.shouldShow()) {
      DeleteSyncEducationDialog
        .show(childFragmentManager)
        .subscribe { handleDeleteMessages(messageParts) }
        .addTo(disposables)

      return
    }

    val records = messageParts.map(MultiselectPart::getMessageRecord).toSet()

    disposables += DeleteDialog.show(
      context = requireContext(),
      messageRecords = records,
      title = requireContext().resources.getQuantityString(R.plurals.ConversationFragment_delete_selected_title, records.size, records.size),
      message = requireContext().resources.getQuantityString(R.plurals.ConversationFragment_delete_selected_body, records.size, records.size),
      isAdmin = conversationGroupViewModel.isAdmin()
    ).observeOn(AndroidSchedulers.mainThread())
      .subscribe { (deleted: Boolean, _: Boolean) ->
        if (!deleted) return@subscribe
        val editMessageId = inputPanel.editMessageId?.id
        if (editMessageId != null && records.any { it.id == editMessageId }) {
          inputPanel.exitEditMessageMode()
        }
      }
  }

  private fun handleEndPoll(pollId: Long?) {
    if (pollId == null) {
      Log.w(TAG, "Unable to find poll to end $pollId")
      return
    }

    if (conversationGroupViewModel.groupRecordSnapshot?.isTerminated == true) {
      MaterialAlertDialogBuilder(requireContext())
        .setMessage(R.string.conversation_activity__group_action_not_allowed_group_ended)
        .setPositiveButton(android.R.string.ok) { d, _ -> d.dismiss() }
        .show()
      return
    }

    MaterialAlertDialogBuilder(requireContext())
      .setTitle(getString(R.string.Poll__end_poll_title))
      .setMessage(getString(R.string.Poll__end_poll_body))
      .setPositiveButton(R.string.Poll__end_poll) { _, _ ->
        val endPoll = viewModel.endPoll(pollId)

        disposables += endPoll
          .doOnSubscribe {
            handler.postDelayed({ showSpinner() }, POLL_SPINNER_DELAY.inWholeMilliseconds)
          }
          .doFinally {
            handler.removeCallbacksAndMessages(null)
            hideSpinner()
          }
          .subscribeBy(
            onError = {
              Log.w(TAG, "Error received during poll end!", it)
              MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.Poll__couldnt_end_poll)
                .setMessage(getString(R.string.Poll__check_connection))
                .setPositiveButton(android.R.string.ok) { dialog: DialogInterface?, which: Int -> dialog!!.dismiss() }
                .show()
            }
          )
      }
      .setNegativeButton(android.R.string.cancel) { _, _ -> }
      .show()
  }

  private fun showSpinner() {
    progressDialog = ProgressCardDialogFragment.create(getString(R.string.Poll__waiting_for_network))
    progressDialog?.show(parentFragmentManager, null)
  }

  private fun hideSpinner() {
    progressDialog?.dismissAllowingStateLoss()
    progressDialog = null
  }

  private fun viewPinnedMessage(messageId: Long) {
    disposables += viewModel
      .moveToMessage(messageId)
      .subscribeBy(
        onSuccess = { moveToPosition(it) },
        onError = { Toast.makeText(requireContext(), R.string.PinnedMessage__not_found, Toast.LENGTH_LONG).show() }
      )
  }

  private inner class SwipeAvailabilityProvider : ConversationItemSwipeCallback.SwipeAvailabilityProvider {
    override fun isSwipeAvailable(conversationMessage: ConversationMessage): Boolean {
      val recipient = viewModel.recipientSnapshot ?: return false

      return !isActionModeStarted() &&
        MenuState.canReplyToMessage(
          recipient,
          MenuState.isActionMessage(conversationMessage.messageRecord),
          conversationMessage.messageRecord,
          viewModel.hasMessageRequestState,
          conversationGroupViewModel.isNonAdminInAnnouncementGroup()
        )
    }
  }

  //endregion

  //region Scroll Handling

  private fun moveToStartPosition(meta: ConversationData) {
    if (meta.getStartPosition() == 0) {
      layoutManager.scrollToPositionWithOffset(0, 0) {
        animationsAllowed = true
        markReadHelper.stopIgnoringViewReveals(MarkReadHelper.getLatestTimestamp(adapter, layoutManager).orNull())
      }
    } else {
      binding.toolbar.viewTreeObserver.addOnGlobalLayoutListener(StartPositionScroller(meta))
    }
  }

  /** Helper to scroll the conversation to the correct position and offset based on toolbar height and the type of position */
  private inner class StartPositionScroller(private val meta: ConversationData) : ViewTreeObserver.OnGlobalLayoutListener {

    override fun onGlobalLayout() {
      if (!isAdded || view == null) {
        return
      }

      val rect = Rect()
      binding.toolbar.getGlobalVisibleRect(rect)
      val toolbarOffset = rect.bottom
      binding.toolbar.viewTreeObserver.removeOnGlobalLayoutListener(this)

      val startPosition = meta.getStartPosition()
      Log.d(TAG, "Scrolling to start position $startPosition")

      if (!meta.messageRequestData.isMessageRequestAccepted) {
        // Always scroll to the top to show header in MR state
        layoutManager.scrollToPositionTopAligned(meta.threadSize, toolbarOffset) {
          animationsAllowed = true
          markReadHelper.stopIgnoringViewReveals(MarkReadHelper.getLatestTimestamp(adapter, layoutManager).orNull())
        }
      } else if (meta.shouldScrollToFirstUnread()) {
        // Land the divider just below the toolbar.
        layoutManager.scrollToPositionTopAligned(startPosition, toolbarOffset) {
          animationsAllowed = true
          markReadHelper.stopIgnoringViewReveals(MarkReadHelper.getLatestTimestamp(adapter, layoutManager).orNull())
        }
      } else {
        val offset = when {
          startPosition == 0 -> 0
          meta.shouldJumpToMessage() -> (binding.conversationItemRecycler.height - toolbarOffset) / 4
          else -> binding.conversationItemRecycler.height
        }
        layoutManager.scrollToPositionWithOffset(startPosition, offset) {
          animationsAllowed = true
          markReadHelper.stopIgnoringViewReveals(MarkReadHelper.getLatestTimestamp(adapter, layoutManager).orNull())
          if (meta.shouldJumpToMessage()) {
            binding.conversationItemRecycler.post {
              adapter.pulseAtPosition(startPosition)
            }
          }
        }
      }
    }
  }

  /**
   * Requests a jump to the desired position, and ensures that the position desired will be visible on the screen.
   */
  private fun moveToPosition(position: Int) {
    scrollToPositionDelegate.requestScrollPosition(
      position = position,
      smooth = true,
      scrollStrategy = jumpAndPulseScrollStrategy
    )
  }

  private fun scrollToNextMention() {
    disposables += viewModel.getNextMentionPosition().subscribeBy {
      moveToPosition(it)
    }
  }

  /**
   * The methods used in this method are taken directly from View.canScrollVertically(-1)'s code path.
   */
  private fun isScrolledToBottom(): Boolean {
    return with(binding.conversationItemRecycler) {
      val offset = computeVerticalScrollOffset()
      val range = computeVerticalScrollRange() - computeVerticalScrollExtent()
      val delta = range - offset

      delta <= IS_SCROLLED_TO_BOTTOM_THRESHOLD
    }
  }

  private fun isScrolledPastButtonThreshold(): Boolean {
    return layoutManager.findFirstVisibleItemPosition() > 4
  }

  private fun shouldScrollToBottom(): Boolean {
    return isScrolledToBottom() || layoutManager.findFirstVisibleItemPosition() <= 0
  }

  private fun closeChatSearch() {
    isSearchRequested = false
    searchViewModel.onSearchClosed()
    searchNav.visible = false
    inputPanel.setHideForSearch(false)
    updateLightInputChrome()
    viewModel.setSearchQuery(null)
    binding.conversationDisabledInput.visible = true
    invalidateOptionsMenu()
  }

  private fun scrollToBottom() {
    layoutManager.scrollToPositionWithOffset(0, 0)
    scrollListener?.onScrolled(binding.conversationItemRecycler, 0, 0)
  }

  /**
   * Controls animation and visibility of the scrollDateHeader.
   */
  private inner class ScrollDateHeaderHelper {

    init {
      // The floating chip carries the same string as the thread's own date headers, so it follows them
      // into the Light type scale. It keeps an opaque background rather than the rounded pill: it
      // hovers over message text and has to occlude it, but the Light design has no pills.
      LightItemStyle.apply(binding.scrollDateHeader, LightTextVariant.Superfine)
      binding.scrollDateHeader.setBackgroundColor(LightItemStyle.backgroundColor(requireContext()))
      binding.scrollDateHeader.setTextColor(LightItemStyle.contentColor(requireContext()))
    }

    private val slideIn = AnimationUtils.loadAnimation(
      requireContext(),
      R.anim.slide_from_top
    ).apply {
      duration = SCROLL_HEADER_ANIMATION_DURATION
    }

    private val slideOut = AnimationUtils.loadAnimation(
      requireContext(),
      R.anim.conversation_scroll_date_header_slide_to_top
    ).apply {
      duration = SCROLL_HEADER_ANIMATION_DURATION
    }

    private var pendingHide = false

    fun show() {
      if (binding.scrollDateHeader.text.isNullOrEmpty()) {
        return
      }

      if (pendingHide) {
        pendingHide = false
      } else {
        ViewUtil.animateIn(binding.scrollDateHeader, slideIn)
      }
    }

    fun bind(message: ConversationMessage?) {
      if (message != null) {
        binding.scrollDateHeader.text = DateUtils.getConversationDateHeaderString(requireContext(), Locale.getDefault(), message.conversationTimestamp)
      }
    }

    fun hide() {
      pendingHide = true

      val header = binding.scrollDateHeader

      header.postDelayed({
        if (pendingHide) {
          pendingHide = false
          ViewUtil.animateOut(header, slideOut)
        }
      }, SCROLL_HEADER_CLOSE_DELAY)
    }
  }

  private inner class ScrollListener : RecyclerView.OnScrollListener() {

    private var wasAtBottom = true
    private val scrollDateHeaderHelper = ScrollDateHeaderHelper()

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
      if (isScrolledToBottom()) {
        viewModel.setShowScrollButtonsForScrollPosition(showScrollButtons = false, willScrollToBottomOnNewMessage = true)
      } else if (isScrolledPastButtonThreshold()) {
        viewModel.setShowScrollButtonsForScrollPosition(showScrollButtons = true, willScrollToBottomOnNewMessage = false)
      } else {
        viewModel.setShowScrollButtonsForScrollPosition(showScrollButtons = false, willScrollToBottomOnNewMessage = shouldScrollToBottom())
      }

      presentComposeDivider()

      val message = adapter.getConversationMessage(layoutManager.findLastVisibleItemPosition())
      scrollDateHeaderHelper.bind(message)

      val timestamp = MarkReadHelper.getLatestTimestamp(adapter, layoutManager)
      timestamp.ifPresent(markReadHelper::onViewsRevealed)
    }

    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
      if (newState != RecyclerView.SCROLL_STATE_IDLE) {
        scrollDateHeaderHelper.show()
      } else {
        scrollDateHeaderHelper.hide()
      }
    }

    private fun presentComposeDivider() {
      val isAtBottom = isScrolledToBottom()
      val suppress = viewModel.recipientSnapshot?.isReleaseNotes == true
      if ((isAtBottom && !wasAtBottom) || suppress) {
        ViewUtil.fadeOut(binding.composeDivider, 50, View.INVISIBLE)
      } else if (wasAtBottom && !isAtBottom) {
        ViewUtil.fadeIn(binding.composeDivider, 500)
      }

      wasAtBottom = isAtBottom
    }
  }

  private inner class DataObserver : RecyclerView.AdapterDataObserver() {
    override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
      if (positionStart == 0 && shouldScrollToBottom()) {
        scrollToBottom()
      }
    }

    override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
      if (!isActionModeStarted()) {
        return
      }

      val expired: Set<MultiselectPart> = adapter
        .selectedItems
        .filter { it.isExpired() }
        .toSet()

      adapter.removeFromSelection(expired)

      if (adapter.selectedItems.isEmpty()) {
        finishActionMode()
      } else {
        setActionModeTitle(calculateSelectedItemCount())
      }
    }

    override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
      scrollListener?.onScrolled(binding.conversationItemRecycler, 0, 0)
    }
  }

  fun handleMoveToQuotePosition(quoteId: Long, authorId: RecipientId) {
    disposables += viewModel.getQuotedMessagePosition(quoteId, authorId)
      .subscribeBy {
        if (it >= 0) {
          moveToPosition(it)
        } else {
          toast(R.string.ConversationFragment_quoted_message_no_longer_available)
        }
      }
  }

  //endregion Scroll Handling

  // region Conversation Callbacks

  private inner class ConversationItemClickListener : ConversationAdapter.ItemClickListener {
    override fun onQuoteClicked(messageRecord: MmsMessageRecord) {
      val quote: Quote? = messageRecord.quote
      if (quote == null) {
        Log.w(TAG, "onQuoteClicked: Received an event but there is no quote.")
        return
      }

      if (quote.isOriginalMissing) {
        Log.i(TAG, "onQuoteClicked: Original message is missing.")
        toast(R.string.ConversationFragment_quoted_message_not_found)
        return
      }

      val parentStoryId = messageRecord.parentStoryId
      if (parentStoryId != null) {
        startActivity(
          StoryViewerActivity.createIntent(
            requireContext(),
            StoryViewerArgs.Builder(quote.author, Recipient.resolved(quote.author).shouldHideStory)
              .withStoryId(parentStoryId.asMessageId().id)
              .isFromQuote(true)
              .build()
          )
        )

        return
      }

      handleMoveToQuotePosition(quote.id, quote.author)
    }

    override fun onLinkPreviewClicked(linkPreview: LinkPreview) {
      val activity = activity ?: return
      CommunicationActions.openBrowserLink(activity, linkPreview.url)
    }

    override fun onQuotedIndicatorClicked(messageRecord: MessageRecord) {
      context ?: return
      activity ?: return
      val recipientId = viewModel.recipientSnapshot?.id ?: return

      container.runAfterAllHidden(composeText) {
        MessageQuotesBottomSheet.show(
          childFragmentManager,
          MessageId(messageRecord.id),
          recipientId
        )
      }
    }

    override fun onMoreTextClicked(conversationRecipientId: RecipientId, messageId: Long, isMms: Boolean) {
      context ?: return
      LongMessageFragment.create(messageId, isMms).show(childFragmentManager, null)
    }

    override fun onStickerClicked(stickerLocator: StickerLocator) {
      context ?: return
      startActivity(StickerPackPreviewActivity.getIntent(stickerLocator.packId, stickerLocator.packKey))
    }

    override fun onViewOnceMessageClicked(messageRecord: MmsMessageRecord) {
      if (!messageRecord.isViewOnce) {
        error("Non-revealable message clicked.")
      }

      if (!ViewOnceUtil.isViewable(messageRecord)) {
        val toastText = if (messageRecord.isOutgoing) {
          R.string.ConversationFragment_view_once_media_is_deleted_after_sending
        } else {
          R.string.ConversationFragment_you_already_viewed_this_message
        }

        toast(toastText)
        return
      }

      disposables += viewModel.getTemporaryViewOnceUri(messageRecord).subscribeBy(
        onSuccess = {
          container.hideAll(composeText)
          startActivity(ViewOnceMessageActivity.getIntent(requireContext(), messageRecord.id, it))
        },
        onComplete = {
          toast(R.string.ConversationFragment_failed_to_open_message)
        }
      )
    }

    override fun onSharedContactDetailsClicked(contact: Contact, avatarTransitionView: View) {
      val activity = activity ?: return
      ViewCompat.setTransitionName(avatarTransitionView, "avatar")
      val bundle = ActivityOptionsCompat.makeSceneTransitionAnimation(activity, avatarTransitionView, "avatar").toBundle()
      activity.startActivity(SharedContactDetailsActivity.getIntent(activity, contact), bundle)
    }

    override fun onAddToContactsClicked(contact: Contact) {
      disposables += AddToContactsContract.createIntentAndLaunch(
        this@ConversationFragment,
        addToContactsLauncher,
        contact
      )
    }

    override fun onMessageSharedContactClicked(choices: MutableList<Recipient>) {
      val context = context ?: return
      ContactUtil.selectRecipientThroughDialog(context, choices, Locale.getDefault()) { recipient: Recipient ->
        CommunicationActions.startConversation(context, recipient, null)
      }
    }

    override fun onInviteSharedContactClicked(choices: MutableList<Recipient>) {
      val context = context ?: return
      ContactUtil.selectRecipientThroughDialog(context, choices, Locale.getDefault()) { recipient: Recipient ->
        CommunicationActions.composeSmsThroughDefaultApp(
          context,
          recipient,
          getString(R.string.InviteActivity_lets_switch_to_signal, getString(R.string.install_url))
        )
      }
    }

    override fun onReactionClicked(multiselectPart: MultiselectPart, messageId: Long, isMms: Boolean) {
      context ?: return
      val reactionsTag = "REACTIONS"
      if (parentFragmentManager.findFragmentByTag(reactionsTag) == null) {
        ReactionsBottomSheetDialogFragment.create(messageId, isMms, conversationGroupViewModel.groupRecordSnapshot?.isTerminated == true).show(childFragmentManager, reactionsTag)
      }
    }

    override fun onGroupMemberClicked(recipientId: RecipientId, groupId: GroupId) {
      context ?: return
      RecipientBottomSheetDialogFragment.show(childFragmentManager, recipientId, groupId)
    }

    override fun onItemDoubleClick(item: MultiselectPart) {
      Log.d(TAG, "onItemDoubleClick")
      onDoubleTapToEdit(item.conversationMessage)
    }

    private fun onDoubleTapToEdit(conversationMessage: ConversationMessage) {
      if (!isValidEditMessageSend(conversationMessage.getMessageRecord(), System.currentTimeMillis())) {
        return
      }

      if (SignalStore.uiHints.hasSeenDoubleTapEditEducationSheet) {
        onDoubleTapEditEducationSheetNext(conversationMessage)
        return
      }

      DoubleTapEditEducationSheet(conversationMessage).show(childFragmentManager, DoubleTapEditEducationSheet.KEY)
    }

    override fun onDisplayMediaNoLongerAvailableSheet() {
      viewLifecycleOwner.lifecycleScope.launch {
        val isUpgradeAvailable = BackupUpgradeAvailabilityChecker.isUpgradeAvailable(requireContext())

        if (SignalStore.backup.areBackupsEnabled && isUpgradeAvailable) {
          UpgradeToStartMediaBackupSheet().show(parentFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
        } else {
          MediaNoLongerAvailableBottomSheet().show(parentFragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
        }
      }
    }

    override fun onMessageWithErrorClicked(messageRecord: MessageRecord) {
      val recipientId = viewModel.recipientSnapshot?.id ?: return
      if (conversationGroupViewModel.groupRecordSnapshot?.isTerminated == true) {
        ConversationDialogs.displayTerminatedGroupSendFailedDialog(requireContext(), messageRecord)
      } else if (messageRecord.isFailedAdminDelete) {
        val canRetry = MessageConstraintsUtil.isValidAdminDeleteSend(message = messageRecord, currentTime = System.currentTimeMillis(), isAdmin = conversationGroupViewModel.isAdmin(), isResend = true)
        if (messageRecord.isIdentityMismatchFailure && canRetry) {
          SafetyNumberBottomSheet
            .forIncomingMessageRecord(messageRecord, viewModel.recipientSnapshot!!)
            .show(childFragmentManager)
        } else {
          ConversationDialogs.displayDeletionFailedDialog(requireContext(), messageRecord, canRetry)
        }
      } else if (messageRecord.isIdentityMismatchFailure) {
        SafetyNumberBottomSheet
          .forOutgoingMessageRecord(requireContext(), messageRecord)
          .show(childFragmentManager)
      } else if (messageRecord.hasFailedWithNetworkFailures()) {
        ConversationDialogs.displayMessageCouldNotBeSentDialog(requireContext(), messageRecord)
      } else {
        chatRouter.goToChatDetail(MainNavigationDetailLocation.Chats.MessageDetails(recipientId, MessageId(messageRecord.id)))
      }
    }

    override fun onMessageWithRecaptchaNeededClicked(messageRecord: MessageRecord) {
      RecaptchaProofBottomSheetFragment.show(childFragmentManager)
    }

    override fun onIncomingIdentityMismatchClicked(recipientId: RecipientId) {
      SafetyNumberBottomSheet.forRecipientId(recipientId).show(parentFragmentManager)
    }

    override fun onRegisterVoiceNoteCallbacks(onPlaybackStartObserver: Observer<VoiceNotePlaybackState>) {
      getVoiceNoteMediaController()
        .voiceNotePlaybackState
        .observe(viewLifecycleOwner, onPlaybackStartObserver)
    }

    override fun onUnregisterVoiceNoteCallbacks(onPlaybackStartObserver: Observer<VoiceNotePlaybackState>) {
      getVoiceNoteMediaController()
        .voiceNotePlaybackState
        .removeObserver(onPlaybackStartObserver)
    }

    override fun onVoiceNotePause(uri: Uri) {
      getVoiceNoteMediaController().pausePlayback(uri)
    }

    override fun onVoiceNotePlay(uri: Uri, messageId: Long, position: Double) {
      getVoiceNoteMediaController().startConsecutivePlayback(uri, messageId, position)
    }

    override fun onSingleVoiceNotePlay(uri: Uri, messageId: Long, position: Double) {
      getVoiceNoteMediaController().startSinglePlayback(uri, messageId, position)
    }

    override fun onVoiceNoteSeekTo(uri: Uri, position: Double) {
      getVoiceNoteMediaController().seekToPosition(uri, position)
    }

    override fun onVoiceNotePlaybackSpeedChanged(uri: Uri, speed: Float) {
      getVoiceNoteMediaController().setPlaybackSpeed(uri, speed)
    }

    override fun onGroupMigrationLearnMoreClicked(membershipChange: GroupMigrationMembershipChange) {
      GroupsV1MigrationInfoBottomSheetDialogFragment.show(parentFragmentManager, membershipChange)
    }

    override fun onChatSessionRefreshLearnMoreClicked() {
      ConversationDialogs.displayChatSessionRefreshLearnMoreDialog(requireContext())
    }

    override fun onBadDecryptLearnMoreClicked(author: RecipientId) {
      val isGroup = viewModel.recipientSnapshot?.isGroup ?: return
      val recipientName = Recipient.resolved(author).getDisplayName(requireContext())
      BadDecryptLearnMoreDialog.show(parentFragmentManager, recipientName, isGroup)
    }

    override fun onSafetyNumberLearnMoreClicked(recipient: Recipient) {
      ConversationDialogs.displaySafetyNumberLearnMoreDialog(this@ConversationFragment, recipient)
    }

    override fun onShowUnverifiedProfileSheet(forGroup: Boolean) {
      UnverifiedProfileNameBottomSheet.show(parentFragmentManager, forGroup)
    }

    override fun onUpdateSignalClicked() {
      PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(requireContext())
    }

    override fun onViewResultsClicked(pollId: Long) {
      if (parentFragmentManager.findFragmentByTag(PollVotesFragment.POLL_VOTES_FRAGMENT_TAG) == null) {
        PollVotesFragment.create(pollId, parentFragmentManager)

        parentFragmentManager.setFragmentResultListener(PollVotesFragment.RESULT_KEY, requireActivity()) { _, bundle ->
          val shouldEndPoll = bundle.getBoolean(PollVotesFragment.RESULT_KEY, false)
          if (shouldEndPoll) {
            handleEndPoll(pollId)
          }
        }
      }
    }

    override fun onViewPollClicked(messageId: Long) {
      disposables += viewModel
        .moveToMessage(messageId)
        .subscribeBy(
          onSuccess = { moveToPosition(it) },
          onError = { Toast.makeText(requireContext(), R.string.Poll__unable_poll, Toast.LENGTH_LONG).show() }
        )
    }

    override fun onToggleVote(poll: PollRecord, pollOption: PollOption, isChecked: Boolean) {
      if (conversationGroupViewModel.groupRecordSnapshot?.isTerminated == true) {
        MaterialAlertDialogBuilder(requireContext())
          .setMessage(R.string.conversation_activity__group_action_not_allowed_group_ended)
          .setPositiveButton(android.R.string.ok) { d, _ -> d.dismiss() }
          .show()
        return
      }
      viewModel.toggleVote(poll, pollOption, isChecked)
    }

    override fun onViewPinnedMessage(messageId: Long) {
      viewPinnedMessage(messageId)
    }

    override fun onJoinGroupCallClicked() {
      val activity = activity ?: return
      val recipient = viewModel.recipientSnapshot ?: return
      CommunicationActions.startVideoCall(activity, recipient) {
        YouAreAlreadyInACallSnackbar.show(requireView())
      }
    }

    override fun onInviteFriendsToGroupClicked(groupId: GroupId.V2) {
      if (conversationGroupViewModel.groupRecordSnapshot?.isTerminated == true) {
        MaterialAlertDialogBuilder(requireContext())
          .setMessage(R.string.conversation_activity__group_action_not_allowed_group_ended)
          .setPositiveButton(android.R.string.ok) { d, _ -> d.dismiss() }
          .show()
      } else {
        GroupLinkInviteFriendsBottomSheetDialogFragment.show(requireActivity().supportFragmentManager, groupId)
      }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onEnableCallNotificationsClicked() {
      EnableCallNotificationSettingsDialog.fixAutomatically(requireContext())
      if (EnableCallNotificationSettingsDialog.shouldShow(requireContext())) {
        EnableCallNotificationSettingsDialog.show(childFragmentManager)
      } else {
        adapter.notifyDataSetChanged()
      }
    }

    override fun onPlayInlineContent(conversationMessage: ConversationMessage?) {
      adapter.playInlineContent(conversationMessage)
    }

    override fun onInMemoryMessageClicked(messageRecord: InMemoryMessageRecord) {
      ConversationDialogs.displayInMemoryMessageDialog(requireContext(), messageRecord)
    }

    override fun onViewGroupDescriptionChange(groupId: GroupId?, description: String, isMessageRequestAccepted: Boolean) {
      if (groupId != null) {
        GroupDescriptionDialog.show(childFragmentManager, groupId, description, isMessageRequestAccepted)
      }
    }

    override fun onChangeNumberUpdateContact(recipient: Recipient) {
      startActivity(RecipientExporter.export(recipient).asAddContactIntent())
    }

    override fun onChangeProfileNameUpdateContact(recipient: Recipient) {
      if (recipient.isSystemContact) {
        startActivity(
          Intent(Intent.ACTION_EDIT).apply {
            setDataAndType(recipient.contactUri, ContactsContract.Contacts.CONTENT_ITEM_TYPE)
          }
        )
      } else {
        nicknameEditActivityLauncher.launch(NicknameActivity.Args(recipientId = recipient.id, focusNoteFirst = false))
      }
    }

    override fun onCallToAction(action: String) {
      when (action) {
        "gift_badge" -> Unit  // MOLLY: No action
        "username_edit" -> startActivity(EditProfileActivity.getIntentForUsernameEdit(requireContext()))
        "calls_tab" -> startActivity(MainActivity.clearTopAndOpenTab(requireContext(), MainNavigationListLocation.CALLS))
        "chat_folder" -> startActivity(AppSettingsActivity.chatFolders(requireContext()))
        "remote_backups" -> {
          if (SignalStore.backup.areBackupsEnabled) {
            startActivity(AppSettingsActivity.remoteBackups(requireContext()))
          } else {
            startActivity(AppSettingsActivity.backupsSettings(requireContext(), launchCheckoutFlow = true))
          }
        }
      }
    }

    override fun onDonateClicked() {
    }

    override fun onBlockJoinRequest(recipient: Recipient) {
      if (conversationGroupViewModel.groupRecordSnapshot?.isTerminated == true) {
        MaterialAlertDialogBuilder(requireContext())
          .setMessage(R.string.conversation_activity__group_action_not_allowed_group_ended)
          .setPositiveButton(android.R.string.ok) { d, _ -> d.dismiss() }
          .show()
      } else {
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.ConversationFragment__block_request)
          .setMessage(getString(R.string.ConversationFragment__s_will_not_be_able_to_join_or_request_to_join_this_group_via_the_group_link, recipient.getDisplayName(requireContext())))
          .setNegativeButton(R.string.ConversationFragment__cancel, null)
          .setPositiveButton(R.string.ConversationFragment__block_request_button) { _, _ -> handleBlockJoinRequest(recipient) }
          .show()
      }
    }

    override fun onRecipientNameClicked(target: RecipientId) {
      context ?: return
      disposables += viewModel.recipient.firstOrError().observeOn(AndroidSchedulers.mainThread()).subscribeBy {
        RecipientBottomSheetDialogFragment.show(
          parentFragmentManager,
          target,
          it.groupId.orElse(null)
        )
      }
    }

    override fun onInviteToSignalClicked() {
      InviteActions.inviteUserToSignal(
        requireContext(),
        this@ConversationFragment::startActivity
      )
    }

    override fun onScheduledIndicatorClicked(view: View, conversationMessage: ConversationMessage) = Unit

    override fun onUrlClicked(url: String): Boolean {
      return CommunicationActions.handlePotentialGroupLinkUrl(requireActivity(), url)
    }

    override fun goToMediaPreview(parent: ConversationItem, sharedElement: View, args: MediaIntentFactory.MediaPreviewArgs) {
      if (this@ConversationFragment.args.conversationScreenType.isInBubble) {
        val recipient = viewModel.recipientSnapshot ?: return
        val intent = ConversationIntents.createBuilderSync(requireActivity(), recipient.id, viewModel.threadId)
          .withStartingPosition(binding.conversationItemRecycler.getChildAdapterPosition(parent))
          .build()

        requireActivity().startActivity(intent)
        requireActivity().startActivity(MediaIntentFactory.create(requireActivity(), args.skipSharedElementTransition(true)))
        return
      }

      if (args.isVideoGif) {
        val adapterPosition: Int = binding.conversationItemRecycler.getChildAdapterPosition(parent)
        val holder: GiphyMp4ProjectionPlayerHolder? = giphyMp4ProjectionRecycler.getCurrentHolder(adapterPosition)
        if (holder != null) {
          parent.showProjectionArea()
          holder.hide()
        }
      }

      container.hideAll(composeText)

      sharedElement.transitionName = MediaPreviewV2Activity.SHARED_ELEMENT_TRANSITION_NAME
      requireActivity().setExitSharedElementCallback(MaterialContainerTransformSharedElementCallback())
      val options = ActivityOptions.makeSceneTransitionAnimation(requireActivity(), sharedElement, MediaPreviewV2Activity.SHARED_ELEMENT_TRANSITION_NAME)
      requireActivity().startActivity(MediaIntentFactory.create(requireActivity(), args), options.toBundle())
    }

    override fun onEditedIndicatorClicked(conversationMessage: ConversationMessage) {
      val messageRecord = conversationMessage.messageRecord
      if (conversationMessage.messageRecord.isOutgoing) {
        if (!doubleTapToEditDebouncer.onClick { EditMessageHistoryDialog.show(childFragmentManager, messageRecord.toRecipient.id, messageRecord) }) {
          onDoubleTapToEdit(conversationMessage)
        }
      } else {
        EditMessageHistoryDialog.show(childFragmentManager, messageRecord.fromRecipient.id, messageRecord)
      }
    }

    override fun onExpandEvents(messageId: Long, itemView: View, collapsedSize: Int) {
      val position = binding.conversationItemRecycler.getChildAdapterPosition(itemView)
      if (position != RecyclerView.NO_POSITION && position != 0) {
        collapsibleEventScrollPosition = CollapsibleEventScrollPosition(position = position + (collapsedSize - 1), top = itemView.top, height = itemView.height)
      }
      viewModel.onExpandEvents(messageId)
    }

    override fun onCollapseEvents(messageId: Long, itemView: View, collapsedSize: Int) {
      val position = binding.conversationItemRecycler.getChildAdapterPosition(itemView)
      if (position != RecyclerView.NO_POSITION) {
        collapsibleEventScrollPosition = CollapsibleEventScrollPosition(position = position - (collapsedSize - 1), top = itemView.top, height = itemView.height)
      }
      viewModel.onCollapseEvents(messageId)
    }

    override fun onItemClick(item: MultiselectPart) {
      if (isActionModeStarted()) {
        if (item.conversationMessage.isActiveCollapsedHead) {
          viewModel.onExpandEvents(item.conversationMessage.messageRecord.id)
        } else {
          adapter.toggleSelection(item)
        }
        binding.conversationItemRecycler.invalidateItemDecorations()

        if (adapter.selectedItems.isEmpty()) {
          finishActionMode()
        } else {
          setCorrectActionModeMenuVisibility()
          setActionModeTitle(calculateSelectedItemCount())
        }
      }
    }

    override fun onItemLongClick(itemView: View, item: MultiselectPart) {
      Log.d(TAG, "onItemLongClick")
      if (isActionModeStarted()) {
        return
      }

      if (item.getMessageRecord().isInMemoryMessageRecord) {
        return
      }

      val messageRecord = item.getMessageRecord()
      val recipient = viewModel.recipientSnapshot ?: return

      if (messageRecord.isValidReactionTarget() &&
        !recipient.isBlocked &&
        !viewModel.hasMessageRequestState &&
        (!recipient.isGroup || recipient.isActiveGroup) &&
        adapter.selectedItems.isEmpty()
      ) {
        // The Light context window is a panel over the bottom of the thread, and nothing more. What
        // used to stand here -- snapshotting the row into a floating bitmap, animating it, dimming
        // everything behind a shade, sliding in the emoji scrubber -- is all presentation the panel
        // does not need, and all of it had to be unwound again afterwards. Chief among the things it
        // unwound: the bubble and the reactions strip were set INVISIBLE while the snapshot stood in
        // for them. Not taking a snapshot is what makes it safe not to restore them.
        showLightMessageMenu(item.conversationMessage)
      } else if (item.conversationMessage.isActiveCollapsedHead) {
        viewModel.onExpandEvents(item.conversationMessage.messageRecord.id)
      } else {
        clearFocusedItem()
        adapter.toggleSelection(item)
        startActionMode()
      }
    }

    override fun onShowGroupDescriptionClicked(groupName: String, description: String, shouldLinkifyWebLinks: Boolean) {
      GroupDescriptionDialog.show(childFragmentManager, groupName, description, shouldLinkifyWebLinks)
    }

    override fun onJoinCallLink(callLinkRootKey: CallLinkRootKey) {
      CommunicationActions.startVideoCall(this@ConversationFragment, callLinkRootKey) {
        YouAreAlreadyInACallSnackbar.show(requireView())
      }
    }

    override fun onShowSafetyTips(forGroup: Boolean) {
      SafetyTipsBottomSheetDialog.show(childFragmentManager, forGroup)
    }

    override fun onReportSpamLearnMoreClicked() {
      MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.ConversationFragment_reported_spam)
        .setMessage(R.string.ConversationFragment_reported_spam_message)
        .setPositiveButton(android.R.string.ok, null)
        .show()
    }

    override fun onMessageRequestAcceptOptionsClicked() {
      val recipient: Recipient? = viewModel.recipientSnapshot

      if (recipient != null) {
        MaterialAlertDialogBuilder(requireContext())
          .setMessage(getString(R.string.ConversationFragment_you_accepted_a_message_request_from_s, recipient.getDisplayName(requireContext())))
          .setPositiveButton(R.string.ConversationFragment_block) { _, _ -> onBlock() }
          .setNegativeButton(R.string.ConversationFragment_report_spam) { _, _ -> onReportSpam() }
          .setNeutralButton(R.string.ConversationFragment__cancel, null)
          .show()
      }
    }

    private fun MessageRecord.getAudioUriForLongClick(): Uri? {
      if (!hasAudio()) {
        return null
      }

      val playbackState = getVoiceNoteMediaController().voiceNotePlaybackState.value
      if (playbackState == null || !playbackState.isPlaying) {
        return null
      }

      val uri = (this as MmsMessageRecord).slideDeck.audioSlide?.uri
      return uri.takeIf { it == playbackState.uri }
    }
  }

  private inner class ConversationOptionsMenuCallback : ConversationOptionsMenu.Callback {

    override fun getSnapshot(): ConversationOptionsMenu.Snapshot {
      val recipient: Recipient? = viewModel.recipientSnapshot
      return ConversationOptionsMenu.Snapshot(
        recipient = recipient,
        isPushAvailable = viewModel.isPushAvailable,
        canShowAsBubble = viewModel.canShowAsBubble(requireContext()),
        isActiveGroup = recipient?.isActiveGroup == true,
        isActiveV2Group = recipient?.let { it.isActiveGroup && it.isPushV2Group } == true,
        isInActiveGroup = recipient?.isActiveGroup == false,
        hasActiveGroupCall = groupCallViewModel.hasOngoingGroupCallSnapshot,
        distributionType = args.distributionType,
        threadId = args.threadId,
        messageRequestState = viewModel.messageRequestState,
        isInBubble = args.conversationScreenType.isInBubble
      )
    }

    override fun isTextHighlighted(): Boolean {
      return composeText.isTextHighlighted
    }

    override fun onOptionsMenuCreated(menu: Menu) {
      searchMenuItem = menu.findItem(R.id.menu_search)

      val searchView: SearchView = searchMenuItem!!.actionView as SearchView
      val queryListener: SearchView.OnQueryTextListener = object : SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(query: String): Boolean {
          searchViewModel.onQueryUpdated(query, args.threadId, true)
          searchNav.showLoading()
          viewModel.setSearchQuery(query)
          return true
        }

        override fun onQueryTextChange(newText: String): Boolean {
          searchViewModel.onQueryUpdated(newText, args.threadId, false)
          searchNav.showLoading()
          viewModel.setSearchQuery(newText)
          return true
        }
      }

      searchMenuItem!!.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
        override fun onMenuItemActionExpand(item: MenuItem): Boolean {
          searchView.setIncognitoKeyboardEnabled(TextSecurePreferences.isIncognitoKeyboardEnabled(requireContext()))
          searchView.setOnQueryTextListener(queryListener)
          isSearchRequested = true
          searchViewModel.onSearchOpened()
          searchNav.visible = true
          searchNav.setData(0, 0)
          inputPanel.setHideForSearch(true)
          updateLightInputChrome()
          viewModel.onChatSearchOpened()
          binding.conversationDisabledInput.visible = false
          // The SearchView expands into the Toolbar that the Light bar is painted over, so the
          // Light bar has to step aside for as long as the search is open.
          binding.lightTopBar.isInvisible = true

          (0 until menu.size()).forEach {
            if (menu.getItem(it) != searchMenuItem) {
              menu.getItem(it).isVisible = false
            }
          }

          return true
        }

        override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
          searchView.setOnQueryTextListener(null)
          binding.lightTopBar.isInvisible = false
          closeChatSearch()
          return true
        }
      })

      searchView.maxWidth = Integer.MAX_VALUE

      if (isSearchRequested) {
        if (searchMenuItem!!.expandActionView()) {
          searchViewModel.onSearchOpened()
        }
      }
    }

    override fun onCallActionsAvailable(canVoiceCall: Boolean, canVideoCall: Boolean) {
      lightCanVoiceCall = canVoiceCall
      lightCanVideoCall = canVideoCall
      updateLightInputChrome()
    }

    override fun handleVideo() {
      this@ConversationFragment.handleVideoCall()
    }

    override fun handleDial() {
      val recipient: Recipient = viewModel.recipientSnapshot ?: return
      CommunicationActions.startVoiceCall(this@ConversationFragment, recipient) {
        YouAreAlreadyInACallSnackbar.show(requireView())
      }
    }

    override fun handleViewMedia() {
      startActivity(MediaOverviewActivity.forThread(requireContext(), args.threadId))
    }

    override fun handleAddShortcut() {
      val recipient: Recipient = viewModel.recipientSnapshot ?: return
      Log.i(TAG, "Creating home screen shortcut for recipient ${recipient.id}")

      if (pinnedShortcutReceiver == null) {
        pinnedShortcutReceiver = object : BroadcastReceiver() {
          override fun onReceive(context: Context, intent: Intent?) {
            toast(
              toastTextId = R.string.ConversationActivity_added_to_home_screen,
              toastDuration = Toast.LENGTH_LONG
            )
          }
        }

        ContextCompat.registerReceiver(requireActivity(), pinnedShortcutReceiver, IntentFilter(ACTION_PINNED_SHORTCUT), ContextCompat.RECEIVER_EXPORTED)
      }

      viewModel.getContactPhotoIcon(requireContext(), Glide.with(this@ConversationFragment))
        .subscribe { infoCompat ->
          val intent = Intent(ACTION_PINNED_SHORTCUT).apply { `package` = requireContext().packageName }
          val callback = PendingIntent.getBroadcast(requireContext(), 902, intent, PendingIntentFlags.mutable())
          ShortcutManagerCompat.requestPinShortcut(requireContext(), infoCompat, callback.intentSender)
        }
        .addTo(disposables)
    }

    override fun handleSearch() {
      searchViewModel.onSearchOpened()
    }

    override fun handleAddToContacts() {
      val recipient = viewModel.recipientSnapshot?.takeIf { it.isIndividual } ?: return

      AddToContactsContract.createIntentAndLaunch(
        fragment = this@ConversationFragment,
        launcher = addToContactsLauncher,
        recipient = recipient
      )
    }

    override fun handleManageGroup() {
      viewModel.recipientSnapshot?.let { recipient ->
        container.hideKeyboard(composeText)
        chatRouter.goToChatDetail(MainNavigationDetailLocation.Chats.ConversationSettings(recipient.id))
      }
    }

    override fun handleLeavePushGroup() {
      val recipient = viewModel.recipientSnapshot
      if (recipient == null) {
        toast(R.string.ConversationActivity_invalid_recipient, toastDuration = Toast.LENGTH_LONG)
        return
      }

      LeaveGroupDialog.handleLeavePushGroup(
        requireActivity(),
        recipient.requireGroupId().requirePush()
      ) { requireActivity().finish() }
    }

    override fun handleInviteLink() {
      val recipient = viewModel.recipientSnapshot ?: return

      InviteActions.inviteUserToSignal(
        context = requireContext(),
        launchIntent = this@ConversationFragment::startActivity
      )
    }

    override fun handleMuteNotifications() {
      MuteDialog.show(requireContext(), childFragmentManager, viewLifecycleOwner, viewModel::muteConversation)
    }

    override fun handleUnmuteNotifications() {
      viewModel.muteConversation(0L)
    }

    override fun handleConversationSettings() {
      viewModel.recipientSnapshot?.let { recipient ->
        if (!viewModel.hasMessageRequestState || recipient.isBlocked) {
          container.hideKeyboard(composeText)
          chatRouter.goToChatDetail(MainNavigationDetailLocation.Chats.ConversationSettings(recipient.id))
        }
      }
    }

    override fun handleSelectMessageExpiration() {
      val recipient = viewModel.recipientSnapshot ?: return
      if (recipient.isPushGroup && !recipient.isActiveGroup) {
        return
      }

      startActivity(RecipientDisappearingMessagesActivity.forRecipient(requireContext(), recipient.id))
    }

    override fun handleCreateBubble() {
      val recipientId = viewModel.recipientSnapshot?.id ?: return

      BubbleUtil.displayAsBubble(requireContext(), recipientId, args.threadId)
      requireActivity().finish()
    }

    override fun handleGoHome() {
      requireActivity().finish()
    }

    override fun showExpiring(recipient: Recipient) = Unit
    override fun clearExpiring() = Unit

    override fun handleFormatText(id: Int) {
      composeText.handleFormatText(id)
    }

    override fun handleBlock() {
      onBlock()
    }

    override fun handleUnblock() {
      onUnblock()
    }

    override fun handleReportSpam() {
      onReportSpam()
    }

    override fun handleMessageRequestAccept() {
      onMessageRequestAccept()
    }

    override fun handleDeleteConversation() {
      onDeleteConversation()
    }

    override fun handleExportChat() {
      MaterialAlertDialogBuilder(requireContext())
        .setTitle(R.string.ChatExportDialogs__export_chat_history_title)
        .setMessage(R.string.ChatExportDialogs__export_confirm_body)
        .setPositiveButton(R.string.ChatExportDialogs__export_with_media) { _, _ ->
          viewModel.startPlaintextExport(requireContext().applicationContext, includeMedia = true)
        }
        .setNeutralButton(R.string.ChatExportDialogs__export_without_media) { _, _ ->
          viewModel.startPlaintextExport(requireContext().applicationContext, includeMedia = false)
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
    }
  }

  private inner class ReactionsToolbarListener(
    private val conversationMessage: ConversationMessage
  ) : OnActionSelectedListener {
    override fun onActionSelected(action: ConversationReactionOverlay.Action) {
      when (action) {
        ConversationReactionOverlay.Action.REPLY -> handleReplyToMessage(conversationMessage)
        ConversationReactionOverlay.Action.EDIT -> handleEditMessage(conversationMessage)
        ConversationReactionOverlay.Action.FORWARD -> handleForwardMessageParts(conversationMessage.multiselectCollection.toSet())
        ConversationReactionOverlay.Action.RESEND -> handleResend(conversationMessage)
        ConversationReactionOverlay.Action.DOWNLOAD -> handleSaveAttachment(conversationMessage.messageRecord as MmsMessageRecord)
        ConversationReactionOverlay.Action.COPY -> handleCopyMessage(conversationMessage.multiselectCollection.toSet())
        ConversationReactionOverlay.Action.MULTISELECT -> handleEnterMultiselect(conversationMessage)
        ConversationReactionOverlay.Action.VIEW_INFO -> handleDisplayDetails(conversationMessage)
        ConversationReactionOverlay.Action.DELETE -> handleDeleteMessages(conversationMessage.multiselectCollection.toSet())
        ConversationReactionOverlay.Action.END_POLL -> handleEndPoll(conversationMessage.messageRecord.getPoll()?.id)
        ConversationReactionOverlay.Action.PIN_MESSAGE -> handlePinMessage(conversationMessage)
        ConversationReactionOverlay.Action.UNPIN_MESSAGE -> handleUnpinMessage(conversationMessage.messageRecord.id)
        ConversationReactionOverlay.Action.STAR_MESSAGE -> handleStarMessages(setOf(conversationMessage.messageRecord.id))
        ConversationReactionOverlay.Action.UNSTAR_MESSAGE -> handleUnstarMessages(setOf(conversationMessage.messageRecord.id))
      }
    }
  }
  // endregion Conversation Callbacks

  //region Activity Results Callbacks

  private inner class ActivityResultCallbacks : ConversationActivityResultContracts.Callbacks {
    override fun onSendContacts(contacts: List<Contact>) {
      sendMessageWithoutComposeInput(
        contacts = contacts,
        clearCompose = false
      )
    }

    override fun onMediaSend(result: MediaSendActivityResult?) {
      if (result == null) {
        return
      }

      val recipientSnapshot = viewModel.recipientSnapshot
      if (result.recipientId != recipientSnapshot?.id) {
        Log.w(TAG, "Result's recipientId did not match ours! Result: " + result.recipientId + ", Ours: " + recipientSnapshot?.id)
        toast(R.string.ConversationActivity_error_sending_media)
        return
      }

      if (result.isPushPreUpload) {
        sendPreUploadMediaMessage(result)
        return
      }

      val slides: List<Slide> = result.nonUploadedMedia.mapNotNull {
        when {
          MediaUtil.isVideoType(it.contentType) -> VideoSlide(requireContext(), it.uri, it.size, it.isVideoGif, it.width, it.height, it.caption, it.transformProperties)

          MediaUtil.isGif(it.contentType) -> GifSlide(requireContext(), it.uri, it.size, it.width, it.height, it.isBorderless, it.caption)

          MediaUtil.isImageType(it.contentType) -> ImageSlide(requireContext(), it.uri, it.contentType, it.size, it.width, it.height, it.isBorderless, it.caption, null, it.transformProperties)

          MediaUtil.isDocumentType(it.contentType) -> {
            DocumentSlide(requireContext(), it.uri, it.contentType!!, it.size, it.fileName)
          }

          else -> {
            Log.w(TAG, "Asked to send an unexpected mimeType: '${it.contentType}'. Skipping.")
            null
          }
        }
      }

      sendMessage(
        body = result.body,
        mentions = result.mentions,
        bodyRanges = result.bodyRanges,
        messageToEdit = null,
        quote = if (result.isViewOnce) null else inputPanel.quote.orNull(),
        scheduledDate = result.scheduledTime,
        slideDeck = SlideDeck().apply { slides.forEach { addSlide(it) } },
        contacts = emptyList(),
        clearCompose = true,
        linkPreviews = emptyList(),
        isViewOnce = result.isViewOnce,
        bypassPreSendSafetyNumberCheck = true
      ) {
        viewModel.deleteSlideData(slides)
      }
    }

    private fun sendPreUploadMediaMessage(result: MediaSendActivityResult) {
      sendMessage(
        body = result.body,
        mentions = result.mentions,
        bodyRanges = result.bodyRanges,
        messageToEdit = null,
        quote = if (result.isViewOnce) null else inputPanel.quote.orNull(),
        scheduledDate = result.scheduledTime,
        slideDeck = null,
        contacts = emptyList(),
        clearCompose = true,
        linkPreviews = emptyList(),
        preUploadResults = result.preUploadResults,
        isViewOnce = result.isViewOnce,
        bypassPreSendSafetyNumberCheck = true
      )
    }

    override fun onContactSelect(uri: Uri?) {
      val recipient = viewModel.recipientSnapshot
      if (uri != null && recipient != null) {
        conversationActivityResultContracts.launchContactShareEditor(uri, recipient.chatColors)
      }
    }

    override fun onLocationSelected(place: SignalPlace?, uri: Uri?) {
      if (place != null && uri != null) {
        attachmentManager.setLocation(place, uri)
        draftViewModel.setLocationDraft(place)
      } else {
        Log.w(TAG, "Location missing thumbnail")
      }
    }

    override fun onFileSelected(uri: Uri?) {
      if (uri != null) {
        setMedia(uri, SlideFactory.MediaType.DOCUMENT)
      }
    }
  }

  //endregion

  private class LastScrolledPositionUpdater(
    val adapter: ConversationAdapterV2,
    val layoutManager: LinearLayoutManager,
    val viewModel: ConversationViewModel
  ) : DefaultLifecycleObserver {
    override fun onPause(owner: LifecycleOwner) {
      val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
      val firstVisiblePosition = layoutManager.findFirstCompletelyVisibleItemPosition()
      val lastVisibleMessageTimestamp = if (firstVisiblePosition > 0 && lastVisiblePosition != RecyclerView.NO_POSITION) {
        adapter.getLastVisibleConversationMessage(lastVisiblePosition)?.messageRecord?.dateReceived ?: 0L
      } else {
        0L
      }

      viewModel.setLastScrolled(lastVisibleMessageTimestamp)
    }
  }

  //region Conversation Banner Callbacks

  private inner class ConversationBannerListener : ConversationBannerView.Listener {
    override fun updateAppAction() {
      PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(requireContext())
    }

    override fun reRegisterAction() {
      startActivity(RegistrationActivity.newIntentForReRegistration(requireContext()))
    }

    override fun reviewJoinRequestsAction() {
      viewModel.recipientSnapshot?.let { recipient ->
        val intent = ManagePendingAndRequestingMembersActivity.newIntent(requireContext(), recipient.requireGroupId().requireV2())
        startActivity(intent)
      }
    }

    override fun gv1SuggestionsAction(actionId: Int) {
      if (actionId == R.id.reminder_action_gv1_suggestion_add_members) {
        conversationGroupViewModel.groupRecordSnapshot?.let { groupRecord ->
          GroupsV1MigrationSuggestionsDialog.show(childFragmentManager, groupRecord.id.requireV2(), groupRecord.gv1MigrationSuggestions)
        }
      } else if (actionId == R.id.reminder_action_gv1_suggestion_no_thanks) {
        conversationGroupViewModel.onSuggestedMembersBannerDismissed()
      }
    }

    @SuppressLint("InlinedApi")
    override fun changeBubbleSettingAction(disableSetting: Boolean) {
      SignalStore.tooltips.markBubbleOptOutTooltipSeen()

      if (disableSetting) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS)
          .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
      } else {
        binding.conversationBanner.clearBanner()
      }
    }

    override fun onUnverifiedBannerClicked(unverifiedIdentities: List<IdentityRecord>) {
      if (unverifiedIdentities.size == 1) {
        VerifyIdentityActivity.startOrShowExchangeMessagesDialog(requireContext(), unverifiedIdentities[0], false)
      } else {
        val unverifiedNames = unverifiedIdentities
          .map { Recipient.resolved(it.recipientId).getDisplayName(requireContext()) }
          .toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
          .setIcon(R.drawable.symbol_error_triangle_fill_24)
          .setTitle(R.string.ConversationFragment__no_longer_verified)
          .setItems(unverifiedNames) { _, which: Int -> VerifyIdentityActivity.startOrShowExchangeMessagesDialog(requireContext(), unverifiedIdentities[which], false) }
          .show()
      }
    }

    override fun onUnverifiedBannerDismissed(unverifiedIdentities: List<IdentityRecord>) {
      viewModel.resetVerifiedStatusToDefault(unverifiedIdentities)
    }

    override fun onRequestReviewIndividual(recipientId: RecipientId) {
      ReviewCardDialogFragment.createForReviewRequest(recipientId).show(childFragmentManager, null)
    }

    override fun onReviewGroupMembers(groupId: GroupId.V2) {
      ReviewCardDialogFragment.createForReviewMembers(groupId).show(childFragmentManager, null)
    }

    override fun onDismissReview() {
      viewModel.onDismissReview()
    }

    override fun onUnpinMessage(messageId: Long) {
      handleUnpinMessage(messageId)
    }

    override fun onGoToMessage(messageId: Long) {
      viewPinnedMessage(messageId)
    }

    override fun onViewAllMessages() {
      PinnedMessagesBottomSheet.show(
        childFragmentManager,
        threadId = args.threadId,
        conversationRecipientId = viewModel.recipientSnapshot?.id!!,
        canUnpin = conversationGroupViewModel.canEditGroupInfo()
      )
    }
  }

  //endregion

  //region Disabled Input Callbacks

  private inner class DisabledInputListener : DisabledInputView.Listener {
    override fun onUpdateAppClicked() {
      PlayStoreUtil.openPlayStoreOrOurApkDownloadPage(requireContext())
    }

    override fun onReRegisterClicked() {
      startActivity(RegistrationActivity.newIntentForReRegistration(requireContext()))
    }

    override fun onReLinkDeviceClicked() {
      startActivity(RegistrationActivity.newIntentForReLinkDevice(requireContext()))
    }

    override fun onCancelGroupRequestClicked() {
      conversationGroupViewModel
        .cancelJoinRequest()
        .subscribeBy { result ->
          when (result) {
            is Result.Success -> Log.d(TAG, "Cancel request complete")

            is Result.Failure -> {
              Log.d(TAG, "Cancel join request failed ${result.failure}")
              toast(GroupErrors.getUserDisplayMessage(result.failure))
            }
          }
        }
        .addTo(disposables)
    }

    override fun onShowAdminsBottomSheetDialog() {
      viewModel.recipientSnapshot?.let { recipient ->
        ShowAdminsBottomSheetDialog.show(childFragmentManager, recipient.requireGroupId().requireV2())
      }
    }

    override fun onAcceptMessageRequestClicked() {
      onMessageRequestAccept()
    }

    override fun onDeleteClicked() {
      onDeleteConversation()
    }

    override fun onBlockClicked() {
      onBlock()
    }

    override fun onUnblockClicked() {
      onUnblock()
    }

    override fun onReportSpamClicked() {
      onReportSpam()
    }

    override fun onInviteToSignal(recipient: Recipient) {
      InviteActions.inviteUserToSignal(
        context = requireContext(),
        launchIntent = this@ConversationFragment::startActivity
      )
    }
  }

  //endregion

  //region Compose + Send Callbacks

  private inner class SendButtonListener :
    View.OnClickListener,
    OnEditorActionListener,
    SendButton.ScheduledSendListener {
    override fun onClick(v: View) {
      sendMessage()
    }

    override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent?): Boolean {
      if (actionId == EditorInfo.IME_ACTION_SEND) {
        if (inputPanel.isInEditMode) {
          sendEditButton.performClick()
        } else {
          sendButton.performClick()
        }
        return true
      }
      return false
    }

    override fun onSendScheduled() {
      if (inputPanel.isRecordingInLockedMode) {
        inputPanel.onSaveRecordDraft()
      }

      ScheduleMessageContextMenu.show(sendButton, (requireView() as ViewGroup)) { time ->
        if (time == -1L) {
          showSchedule(childFragmentManager)
        } else {
          sendMessage(scheduledDate = time)
        }
      }
    }
  }

  private inner class ComposeTextEventsListener :
    View.OnKeyListener,
    View.OnClickListener,
    TextWatcher,
    ComposeText.CursorPositionChangedListener,
    ComposeText.StylingChangedListener {

    private var beforeLength = 0
    private var previousText = ""

    var typingStatusEnabled = true

    override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean {
      if (event.action == KeyEvent.ACTION_DOWN) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
          if (SignalStore.settings.isEnterKeySends || event.isCtrlPressed) {
            sendButton.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            sendButton.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            return true
          }
        }
      }
      return false
    }

    override fun onClick(v: View) {
      container.showSoftkey(composeText)
    }

    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
      beforeLength = composeText.textTrimmed.length
    }

    override fun afterTextChanged(s: Editable) {
      if (composeText.textTrimmed.isEmpty() || beforeLength == 0) {
        composeText.postDelayed({
          if (lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            updateToggleButtonState()
          }
        }, 50)
      }

      if (!inputPanel.inEditMessageMode()) {
        stickerViewModel.onInputTextUpdated(s.toString())
      } else {
        stickerViewModel.onInputTextUpdated("")
      }
    }

    override fun onCursorPositionChanged(start: Int, end: Int) {
      linkPreviewViewModel.onTextChanged(composeText.textTrimmed.toString(), start, end)
    }

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
      handleSaveDraftOnTextChange(composeText.textTrimmed)
      handleTypingIndicatorOnTextChange(s.toString())
    }

    private fun handleSaveDraftOnTextChange(text: CharSequence) {
      textDraftSaveDebouncer.publish {
        if (inputPanel.inEditMessageMode()) {
          draftViewModel.setMessageEditDraft(inputPanel.editMessageId!!, text.toString(), MentionAnnotation.getMentionsFromAnnotations(text), getStyling(text))
        } else {
          draftViewModel.setTextDraft(text.toString(), MentionAnnotation.getMentionsFromAnnotations(text), getStyling(text))
        }
      }
    }

    private fun handleTypingIndicatorOnTextChange(text: String) {
      val recipient = viewModel.recipientSnapshot

      if (recipient == null || !typingStatusEnabled || recipient.isBlocked || recipient.isSelf) {
        return
      }

      val typingStatusSender = AppDependencies.typingStatusSender
      if (text.length == 0) {
        typingStatusSender.onTypingStoppedWithNotify(args.threadId)
      } else if (text.length < previousText.length && previousText.contains(text)) {
        typingStatusSender.onTypingStopped(args.threadId)
      } else {
        typingStatusSender.onTypingStarted(args.threadId)
      }

      previousText = text
    }

    override fun onStylingChanged() {
      handleSaveDraftOnTextChange(composeText.textTrimmed)
    }
  }

  //endregion Compose + Send Callbacks

  //region Input Panel Callbacks

  private inner class InputPanelListener : InputPanel.Listener {
    override fun onVoiceNoteDraftPlay(audioUri: Uri, progress: Double) {
      getVoiceNoteMediaController().startSinglePlaybackForDraft(audioUri, args.threadId, progress)
    }

    override fun onVoiceNoteDraftSeekTo(audioUri: Uri, progress: Double) {
      getVoiceNoteMediaController().seekToPosition(audioUri, progress)
    }

    override fun onVoiceNoteDraftPause(audioUri: Uri) {
      getVoiceNoteMediaController().pausePlayback(audioUri)
    }

    override fun onVoiceNoteDraftDelete(audioUri: Uri) {
      getVoiceNoteMediaController().stopPlaybackAndReset(audioUri)
      draftViewModel.deleteVoiceNoteDraft()
    }

    override fun onRecorderStarted() {
      // LIGHT PHONE: the panel has to come back into the layout before `InputPanel` lays the timer,
      // cancel and send across it -- `recording_layout` lives inside the panel, and the Light bottom
      // bar has to stand aside for it.
      lightRecordingActive = true
      updateLightInputChrome()
      voiceMessageRecordingDelegate.onRecorderStarted()
    }

    override fun onRecorderLocked() {
      updateToggleButtonState()
      voiceMessageRecordingDelegate.onRecorderLocked()

      // LIGHT PHONE: a tap-launched voice note presses and locks in the same frame, so `InputPanel`'s
      // fade-out of the send toggle and its fade-in of the very same view land on top of each other,
      // with each fade's listener able to leave the view INVISIBLE on cancellation. Hold-to-record has
      // a human-scale gap between the two and never hit this. Pin the end state rather than trusting
      // two competing animators to resolve it: the send toggle is the only way to finish a voice note,
      // and a recording that cannot be sent is a dead end on the device with nothing to see in a build.
      binding.conversationInputPanel.buttonToggle.apply {
        animate().cancel()
        alpha = 1f
        visibility = View.VISIBLE
      }
    }

    override fun onRecorderFinished() {
      lightRecordingActive = false
      updateToggleButtonState()
      voiceMessageRecordingDelegate.onRecorderFinished()
      updateLightInputChrome()
    }

    override fun onRecorderCanceled(byUser: Boolean) {
      lightRecordingActive = false
      voiceMessageRecordingDelegate.onRecorderCanceled(byUser)
      updateLightInputChrome()
    }

    override fun onRecorderSaveDraft() {
      lightRecordingActive = false
      voiceMessageRecordingDelegate.onRecordSaveDraft()
      inputPanel.voiceNoteDraft = draftViewModel.voiceNoteDraft
      updateLightInputChrome()
    }

    override fun onRecorderPermissionRequired() {
      Permissions
        .with(this@ConversationFragment)
        .request(Manifest.permission.RECORD_AUDIO)
        .ifNecessary()
        .withRationaleDialog(getString(R.string.ConversationActivity_allow_access_microphone), getString(R.string.ConversationActivity_to_send_voice_messages_allow_signal_access_to_your_microphone), R.drawable.ic_mic_24)
        .withPermanentDenialDialog(
          getString(R.string.ConversationActivity_signal_requires_the_microphone_permission_in_order_to_send_audio_messages),
          null,
          R.string.ConversationActivity_allow_access_microphone,
          R.string.ConversationActivity_signal_to_send_audio_messages,
          this@ConversationFragment.parentFragmentManager
        )
        .onAnyDenied { Toast.makeText(this@ConversationFragment.requireContext(), R.string.ConversationActivity_signal_needs_microphone_access_voice_message, Toast.LENGTH_LONG).show() }
        .execute()
    }

    override fun onRecorderAlreadyInUse() {
      toast(R.string.ConversationFragment_cannot_record_voice_message_during_call)
    }

    override fun onEmojiToggle() {
      // LIGHT PHONE: unreachable. The emoji toggle this fires from is hidden for good by
      // `LightInputPanelChrome.install`, and the emoji, sticker and GIF pages it opened have no other
      // entry point. Left as a no-op rather than removed so that `InputPanel.Listener` stays intact.
      Log.d(TAG, "onEmojiToggle() - media keyboard is not available in this fork")
    }

    override fun onLinkPreviewCanceled() {
      linkPreviewViewModel.onUserCancel()
    }

    override fun onStickerSuggestionSelected(sticker: StickerRecord) {
      sendSticker(
        stickerRecord = sticker,
        clearCompose = true
      )
    }

    override fun onQuoteChanged(id: Long, author: RecipientId) {
      draftViewModel.setQuoteDraft(id, author)
      presentLightComposer()
    }

    override fun onQuoteCleared() {
      draftViewModel.clearQuoteDraft()
      presentLightComposer()
    }

    override fun onQuoteClicked(quoteId: Long, authorId: RecipientId) {
      handleMoveToQuotePosition(quoteId = quoteId, authorId = authorId)
    }

    override fun onEnterEditMode() {
      // LIGHT PHONE: editing is composing, so it happens in the composer -- which is also the only
      // thing that can put the entry on screen, and `InputPanel.updateEditModeUi` has just tried to
      // focus it. Covers both entry points: the long-press EDIT action and a restored edit draft.
      openLightComposer()
      presentLightComposer()
      updateToggleButtonState()
      previousPage = keyboardPagerViewModel.page().value
      previousPages = keyboardPagerViewModel.pages().value
      keyboardPagerViewModel.setOnlyPage(KeyboardPage.EMOJI)
      onKeyboardChanged(KeyboardPage.EMOJI)
      stickerViewModel.onInputTextUpdated("")
      updateLinkPreviewState()
    }

    override fun onExitEditMode() {
      presentLightComposer()
      updateToggleButtonState()
      draftViewModel.deleteMessageEditDraft()
      if (previousPages != null) {
        keyboardPagerViewModel.setPages(previousPages!!)
        previousPages = null
      }
      if (previousPage != null) {
        keyboardPagerViewModel.switchToPage(previousPage!!)
        onKeyboardChanged(previousPage!!)
        previousPage = null
      }
      updateLinkPreviewState()
    }

    override fun onQuickCameraToggleClicked() {
      val recipientId = viewModel.recipientSnapshot?.id ?: return
      composeText.clearFocus()
      conversationActivityResultContracts.launchCamera(recipientId, inputPanel.quote.isPresent)
    }
  }

  private inner class InputPanelMediaListener {
    fun onMediaSelected(uri: Uri, contentType: String?) {
      if (inputPanel.inEditMessageMode()) {
        Log.i(TAG, "Disregarding media because we are in edit mode")
      } else if (MediaUtil.isGif(contentType) || MediaUtil.isImageType(contentType)) {
        disposables += viewModel.getKeyboardImageDetails(uri)
          .observeOn(AndroidSchedulers.mainThread())
          .subscribeBy(
            onSuccess = {
              sendKeyboardImage(uri, contentType!!, it)
            },
            onComplete = {
              sendKeyboardImage(uri, contentType!!, null)
            }
          )
      } else if (MediaUtil.isVideoType(contentType)) {
        setMedia(uri, SlideFactory.MediaType.VIDEO)
      } else {
        setMedia(uri, SlideFactory.MediaType.AUDIO)
      }
    }

    private fun sendKeyboardImage(uri: Uri, contentType: String, keyboardImageDetails: KeyboardUtil.ImageDetails?) {
      if (keyboardImageDetails == null || !keyboardImageDetails.isSticker) {
        setMedia(uri, requireNotNull(SlideFactory.MediaType.from(contentType)))
        return
      }

      val slide: Slide = when {
        MediaUtil.isGif(contentType) -> GifSlide(requireContext(), uri, 0, keyboardImageDetails.width, keyboardImageDetails.height, true, null)
        MediaUtil.isImageType(contentType) -> ImageSlide(requireContext(), uri, contentType, 0, keyboardImageDetails.width, keyboardImageDetails.height, true, null, null)
        else -> null
      } ?: error("Only images are supported!")

      sendMessageWithoutComposeInput(slide = slide)
    }
  }

  //endregion

  //region Attachment + Media Keyboard

  private inner class AttachmentManagerListener : AttachmentManager.AttachmentListener {
    override fun onAttachmentChanged() {
      updateToggleButtonState()
      updateLinkPreviewState()
    }

    override fun onLocationRemoved() {
      draftViewModel.clearLocationDraft()
    }
  }

  private object AttachmentKeyboardFragmentCreator : InputAwareConstraintLayout.FragmentCreator {
    override val id: Int = ATTACHMENT_KEYBOARD_FRAGMENT_CREATOR_ID
    override val presentation: InputAwareConstraintLayout.Presentation = InputAwareConstraintLayout.Presentation.FULL_SCREEN
    override fun create(): Fragment = AttachmentKeyboardFragment()
  }

  private inner class AttachmentKeyboardFragmentListener : FragmentResultListener {
    @Suppress("DEPRECATION")
    override fun onFragmentResult(requestKey: String, result: Bundle) {
      val recipient = viewModel.recipientSnapshot ?: return
      val button: AttachmentKeyboardButton? = result.getSerializable(AttachmentKeyboardFragment.BUTTON_RESULT) as? AttachmentKeyboardButton
      val media: Media? = result.getParcelable(AttachmentKeyboardFragment.MEDIA_RESULT)

      if (button != null) {
        when (button) {
          AttachmentKeyboardButton.GALLERY -> conversationActivityResultContracts.launchGallery(recipient.id, composeText.textTrimmed, inputPanel.quote.isPresent)

          AttachmentKeyboardButton.VOICE_NOTE -> binding.conversationInputPanel.recorderView.startLockedRecording()

          AttachmentKeyboardButton.CONTACT -> conversationActivityResultContracts.launchSelectContact()

          AttachmentKeyboardButton.LOCATION -> conversationActivityResultContracts.launchSelectLocation(recipient.chatColors)

          AttachmentKeyboardButton.FILE -> {
            if (!conversationActivityResultContracts.launchSelectFile()) {
              toast(R.string.AttachmentManager_cant_open_media_selection, Toast.LENGTH_LONG)
            }
          }

          AttachmentKeyboardButton.POLL -> {
            CreatePollFragment.show(childFragmentManager)
            childFragmentManager.setFragmentResultListener(CreatePollFragment.REQUEST_KEY, requireActivity()) { _, bundle ->
              sendPoll(recipient, Poll.fromBundle(bundle))
            }
          }
        }
      } else if (media != null) {
        conversationActivityResultContracts.launchMediaEditor(listOf(media), recipient.id, composeText.textTrimmed)
      }

      container.hideInput()
    }
  }

  private object MediaKeyboardFragmentCreator : InputAwareConstraintLayout.FragmentCreator {
    override val id: Int = MEDIA_KEYBOARD_FRAGMENT_CREATOR_ID
    override fun create(): Fragment = KeyboardPagerFragment().apply {
      arguments = bundleOf(KeyboardPagerFragment.ARG_SET_NAV_COLOR to false)
    }
  }

  private inner class KeyboardEvents :
    InputAwareConstraintLayout.Listener,
    InsetAwareConstraintLayout.KeyboardStateListener {

    override fun onInputShown(fragmentCreatorId: Int) {
      when (fragmentCreatorId) {
        ATTACHMENT_KEYBOARD_FRAGMENT_CREATOR_ID -> {
          binding.navBar.setBackgroundColor(Color.BLACK)
        }

        MEDIA_KEYBOARD_FRAGMENT_CREATOR_ID -> {
          binding.navBar.setBackgroundColor(ThemeUtil.getThemedColor(requireContext(), R.attr.mediaKeyboardBottomBarBackgroundColor))
        }

        else -> {
          Log.w(TAG, "Not setting navbar coloring for unknown creator id $fragmentCreatorId")
        }
      }

      viewModel.setIsMediaKeyboardShowing(true)
    }

    override fun onInputHidden() {
      setNavBarBackgroundColor(viewModel.wallpaperSnapshot != null || viewModel.recipientSnapshot?.isReleaseNotes == true)
      viewModel.setIsMediaKeyboardShowing(false)
    }

    override fun onKeyboardShown() {
      if (searchMenuItem?.isActionViewExpanded == true && searchMenuItem?.actionView?.hasFocus() == false) {
        searchMenuItem?.actionView?.requestFocus()
      }
    }

    override fun onKeyboardHidden() {
      if (searchMenuItem?.isActionViewExpanded == true && searchMenuItem?.actionView?.hasFocus() == true) {
        searchMenuItem?.actionView?.clearFocus()
      }
    }

    override fun onKeyboardAnimationEnded() {
      if (view == null) {
        return
      }
      if (!container.isKeyboardShowing) {
        closeEmojiSearch()
      }
    }
  }

  //endregion

  //region Event Bus

  @Subscribe(threadMode = ThreadMode.POSTING)
  fun onIdentityRecordUpdate(event: IdentityRecord?) {
    viewModel.updateIdentityRecordsInBackground()
  }

  @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
  fun onStickerPackInstalled(event: StickerPackInstallEvent?) {
    if (event == null) {
      return
    }

    EventBus.getDefault().removeStickyEvent(event)

    if (!inputPanel.isStickerMode) {
      conversationTooltips.displayStickerPackInstalledTooltip(inputPanel.mediaKeyboardToggleAnchorView, event)
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
  fun onGroupCallPeekEvent(groupCallPeekEvent: GroupCallPeekEvent) {
    groupCallViewModel.onGroupCallPeekEvent(groupCallPeekEvent)
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onRecaptchaRequiredEvent(recaptchaRequiredEvent: RecaptchaRequiredEvent) {
    RecaptchaProofBottomSheetFragment.show(childFragmentManager)
  }

  //endregion

  private inner class SearchEventListener : ConversationSearchBottomBar.EventListener {
    override fun onSearchMoveUpPressed() {
      searchViewModel.onMoveUp()
    }

    override fun onSearchMoveDownPressed() {
      searchViewModel.onMoveDown()
    }

    override fun onDatePickerSelected() {
      disposables += viewModel.getEarliestMessageSentDate().subscribe { earliestDate ->
        val local = LocalDateTime.now()
          .atMidnight()
          .atUTC()
          .toMillis()
        val datePicker =
          MaterialDatePicker.Builder
            .datePicker()
            .setTitleText(getString(R.string.ScheduleMessageTimePickerBottomSheet__select_date_title))
            .setSelection(local)
            .setCalendarConstraints(
              CalendarConstraints.Builder()
                .setValidator(viewModel.jumpToDateValidator)
                .setStart(earliestDate)
                .setEnd(local)
                .build()
            )
            .build()

        datePicker.addOnDismissListener {
          datePicker.clearOnDismissListeners()
          datePicker.clearOnPositiveButtonClickListeners()
        }

        datePicker.addOnPositiveButtonClickListener { selectedDate ->
          if (selectedDate != null) {
            val localMidnightTimestamp = Instant.ofEpochMilli(selectedDate)
              .atZone(ZoneId.systemDefault())
              .toLocalDate()
              .atStartOfDay(ZoneId.systemDefault())
              .toInstant()
              .toEpochMilli()

            disposables += viewModel
              .moveToDate(localMidnightTimestamp)
              .observeOn(AndroidSchedulers.mainThread())
              .subscribeBy { position ->
                moveToPosition(position - 1)
                closeChatSearch()
              }
          }
        }

        datePicker.show(childFragmentManager, "DATE_PICKER")
      }
    }
  }

  private inner class VoiceMessageRecordingSessionCallbacks : VoiceMessageRecordingDelegate.SessionCallback {
    override fun onSessionWillBegin() {
      getVoiceNoteMediaController().pausePlayback()
    }

    override fun sendVoiceNote(draft: VoiceNoteDraft) {
      val audioSlide = AudioSlide(draft.uri, draft.size, MediaUtil.AUDIO_AAC, true)

      sendMessageWithoutComposeInput(
        slide = audioSlide,
        quote = inputPanel.quote.orNull()
      )
    }

    override fun cancelEphemeralVoiceNoteDraft(draft: VoiceNoteDraft) {
      draftViewModel.cancelEphemeralVoiceNoteDraft(draft.asDraft())
    }

    override fun saveEphemeralVoiceNoteDraft(draft: VoiceNoteDraft) {
      draftViewModel.saveEphemeralVoiceNoteDraft(draft.asDraft())
    }
  }

  private inner class VoiceNotePlayerViewListener : VoiceNotePlayerView.Listener {
    override fun onCloseRequested(uri: Uri) {
      getVoiceNoteMediaController().stopPlaybackAndReset(uri)
    }

    override fun onSpeedChangeRequested(uri: Uri, speed: Float) {
      getVoiceNoteMediaController().setPlaybackSpeed(uri, speed)
    }

    override fun onPlay(uri: Uri, messageId: Long, position: Double) {
      getVoiceNoteMediaController().startSinglePlayback(uri, messageId, position)
    }

    override fun onPause(uri: Uri) {
      getVoiceNoteMediaController().pausePlayback(uri)
    }

    override fun onNavigateToMessage(threadId: Long, threadRecipientId: RecipientId, senderId: RecipientId, messageTimestamp: Long, messagePositionInThread: Long) {
      if (threadId != viewModel.threadId) {
        startActivity(
          ConversationIntents.createBuilderSync(requireActivity(), threadRecipientId, threadId)
            .withStartingPosition(messagePositionInThread.toInt())
            .build()
        )
      } else {
        viewModel
          .moveToMessage(messageTimestamp, senderId)
          .subscribeBy {
            moveToPosition(it)
          }
          .addTo(disposables)
      }
    }
  }

  override fun onDoubleTapEditEducationSheetNext(conversationMessage: ConversationMessage) {
    handleEditMessage(conversationMessage)
  }

  /**
   * Tracks the scroll position so that after collapsing/expanding, we can restore it properly
   */
  private data class CollapsibleEventScrollPosition(val position: Int, val top: Int, val height: Int)
}
