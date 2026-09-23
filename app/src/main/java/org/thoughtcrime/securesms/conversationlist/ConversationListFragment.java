/*
 * Copyright (C) 2015 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.conversationlist;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.compose.ui.platform.ComposeView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.signal.core.ui.BottomSheetUtil;
import org.signal.core.ui.WindowSizeClassExtensionsKt;
import org.signal.core.ui.compose.Snackbars;
import org.signal.core.ui.view.Stub;
import org.signal.core.util.AppForegroundObserver;
import org.signal.core.util.DimensionUnit;
import org.signal.core.util.ServiceUtil;
import org.signal.core.util.Stopwatch;
import org.signal.core.util.concurrent.LifecycleDisposable;
import org.signal.core.util.concurrent.SignalExecutors;
import org.signal.core.util.concurrent.SimpleTask;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.MainFragment;
import org.thoughtcrime.securesms.MainNavigator;
import org.thoughtcrime.securesms.MuteDialog;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.backup.ArchiveUploadProgress;
import org.thoughtcrime.securesms.backup.RestoreState;
import org.thoughtcrime.securesms.backup.v2.ArchiveRestoreProgress;
import org.thoughtcrime.securesms.backup.v2.ArchiveRestoreProgressState;
import org.thoughtcrime.securesms.backup.v2.ui.BackupAlert;
import org.thoughtcrime.securesms.backup.v2.ui.BackupAlertBottomSheet;
import org.thoughtcrime.securesms.backup.v2.ui.BackupAlertDelegate;
import org.thoughtcrime.securesms.banner.Banner;
import org.thoughtcrime.securesms.banner.BannerManager;
import org.thoughtcrime.securesms.banner.banners.ArchiveRestoreStatusBanner;
import org.thoughtcrime.securesms.banner.banners.ArchiveUploadStatusBanner;
import org.thoughtcrime.securesms.banner.banners.CdsPermanentErrorBanner;
import org.thoughtcrime.securesms.banner.banners.CdsTemporaryErrorBanner;
import org.thoughtcrime.securesms.banner.banners.DeprecatedBuildBanner;
import org.thoughtcrime.securesms.banner.banners.DeprecatedSdkBanner;
import org.thoughtcrime.securesms.banner.banners.DozeBanner;
import org.thoughtcrime.securesms.banner.banners.OutdatedBuildBanner;
import org.thoughtcrime.securesms.banner.banners.ServiceOutageBanner;
import org.thoughtcrime.securesms.banner.banners.UnauthorizedBanner;
import org.thoughtcrime.securesms.banner.banners.UsernameOutOfSyncBanner;
import org.thoughtcrime.securesms.components.SignalProgressDialog;
import org.thoughtcrime.securesms.components.compose.DeleteSyncEducationDialog;
import org.thoughtcrime.securesms.components.menu.ActionItem;
import org.thoughtcrime.securesms.components.menu.SignalBottomActionBar;
import org.thoughtcrime.securesms.components.menu.SignalContextMenu;
import org.thoughtcrime.securesms.components.settings.app.AppSettingsActivity;
import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFolderRecord;
import org.thoughtcrime.securesms.components.snackbars.SnackbarState;
import org.thoughtcrime.securesms.components.spoiler.SpoilerAnnotation;
import org.thoughtcrime.securesms.components.voice.VoiceNoteMediaControllerOwner;
import org.thoughtcrime.securesms.components.voice.VoiceNotePlayerView;
import org.thoughtcrime.securesms.contacts.ContactSelectionDisplayMode;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchAdapter;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchData;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchPagedDataSourceRepository;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchRepository;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchState;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchViewModel;
import org.thoughtcrime.securesms.contacts.paged.ContactSearchViewModelKt;
import org.thoughtcrime.securesms.contacts.selection.ContactSelectionArguments;
import org.thoughtcrime.securesms.conversation.ConversationUpdateTick;
import org.thoughtcrime.securesms.conversationlist.chatfilter.ConversationFilterRequest;
import org.thoughtcrime.securesms.conversationlist.chatfilter.ConversationFilterSource;
import org.thoughtcrime.securesms.conversationlist.chatfilter.ConversationListFilterPullView;
import org.thoughtcrime.securesms.conversationlist.chatfilter.FilterLerp;
import org.thoughtcrime.securesms.conversationlist.light.LightConversationListView;
import org.thoughtcrime.securesms.conversationlist.model.Conversation;
import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter;
import org.thoughtcrime.securesms.database.MessageTable.MarkedMessageInfo;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.database.ThreadTable;
import org.thoughtcrime.securesms.database.model.ThreadWithRecipient;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.groups.SelectionLimits;
import org.thoughtcrime.securesms.jobs.RefreshOwnProfileJob;
import org.thoughtcrime.securesms.keyvalue.AccountValues;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.main.MainNavigationListLocation;
import org.thoughtcrime.securesms.main.MainNavigationViewModel;
import org.thoughtcrime.securesms.main.MainSnackbarHostKey;
import org.thoughtcrime.securesms.main.MainToolbarMode;
import org.thoughtcrime.securesms.main.MainToolbarViewModel;
import org.thoughtcrime.securesms.main.Material3OnScrollHelperBinder;
import org.thoughtcrime.securesms.notifications.MarkReadReceiver;
import org.thoughtcrime.securesms.profiles.manage.UsernameEditFragment;
import org.thoughtcrime.securesms.ratelimit.RecaptchaProofBottomSheetFragment;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.search.MessageResult;
import org.thoughtcrime.securesms.search.SearchFilter;
import org.thoughtcrime.securesms.search.SearchFilterBottomSheet;
import org.thoughtcrime.securesms.search.SearchRepository;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.util.AppStartup;
import org.thoughtcrime.securesms.util.CachedInflater;
import org.thoughtcrime.securesms.util.ConversationUtil;
import org.thoughtcrime.securesms.util.RemoteConfig;
import org.thoughtcrime.securesms.util.SignalLocalMetrics;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.adapter.mapping.PagingMappingAdapter;
import org.thoughtcrime.securesms.verify.SelfVerificationFailureSheet;
import org.whispersystems.signalservice.api.websocket.WebSocketConnectionState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import kotlin.Unit;

import static org.signal.core.ui.WindowSizeClassExtensionsKt.getWindowSizeClass;


public class ConversationListFragment extends MainFragment implements ClearFilterViewHolder.OnClearFilterClickListener,
                                                                      ChatFolderAdapter.Callbacks
{
  public static final short MESSAGE_REQUESTS_REQUEST_CODE_CREATE_NAME = 32562;
  public static final short SMS_ROLE_REQUEST_CODE                     = 32563;

  private static final int LIST_SMOOTH_SCROLL_TO_TOP_THRESHOLD = 25;

  private static final String TAG = Log.tag(ConversationListFragment.class);

  private static final long SEARCH_LOADING_SHOW_DELAY_MS = 150L;

  private static final int MAX_CHATS_ABOVE_FOLD             = 7;
  private static final int MAX_CONTACTS_ABOVE_FOLD          = 5;
  private static final int MAX_GROUP_MEMBERSHIPS_ABOVE_FOLD = 5;
  private View                                   coordinator;
  private RecyclerView                           chatFolderList;
  private RecyclerView                           list;
  private View                                   searchLoading;
  private boolean                                searchInProgress;
  private Stub<ComposeView>                      bannerView;
  private ConversationListFilterPullView         pullView;
  private AppBarLayout                           pullViewAppBarLayout;
  private ConversationListViewModel              viewModel;
  private LightConversationListView              conversationList;
  private ViewGroup                              listContainer;
  private View                                   contextMenuAnchor;
  private boolean                                showingSearchResults;
  private boolean                                reportedFirstDataSet;
  private PagingMappingAdapter<ContactSearchKey> searchAdapter;
  private final Runnable                         showSearchLoadingRunnable = () -> {
    if (searchLoading != null && searchInProgress && showingSearchResults) {
      searchLoading.setVisibility(View.VISIBLE);
    }
  };
  private Drawable                               archiveDrawable;
  private AppForegroundObserver.Listener         appForegroundObserver;
  private VoiceNoteMediaControllerOwner          mediaControllerOwner;
  private Stub<FrameLayout>                      voiceNotePlayerViewStub;
  private VoiceNotePlayerView                    voiceNotePlayerView;
  private SignalBottomActionBar                  bottomActionBar;
  private SignalContextMenu                      activeContextMenu;
  private LifecycleDisposable                    lifecycleDisposable;
  private ChatFolderAdapter                      chatFolderAdapter;
  private RecyclerView.SmoothScroller            smoothScroller;

  private   Stopwatch                             startupStopwatch;
  private   ContactSearchViewModel                contactSearchViewModel;
  private   MainToolbarViewModel                  mainToolbarViewModel;
  private   ChatListBackHandler                   chatListBackHandler;

  private   SearchFilter                   activeSearchFilter = SearchFilter.EMPTY;
  private   ActivityResultLauncher<Intent> searchFilterContactPickerLauncher;

  private static final String EXTRA_FILTER_START_DATE = "filter_start_date";
  private static final String EXTRA_FILTER_END_DATE   = "filter_end_date";
  private static final String EXTRA_FILTER_AUTHOR_ID  = "filter_author_id";

  private BannerManager bannerManager;
  private SignalProgressDialog progressDialog;

  protected MainNavigationViewModel mainNavigationViewModel;

  public static ConversationListFragment newInstance() {
    return new ConversationListFragment();
  }

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);

    if (context instanceof VoiceNoteMediaControllerOwner) {
      mediaControllerOwner = (VoiceNoteMediaControllerOwner) context;
    } else {
      throw new ClassCastException("Expected context to be a Listener");
    }
  }

  @Override
  public void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    startupStopwatch        = new Stopwatch("startup");
    mainToolbarViewModel    = new ViewModelProvider(requireActivity()).get(MainToolbarViewModel.class);
    mainNavigationViewModel = new ViewModelProvider(requireActivity(), new MainNavigationViewModel.Factory()).get(MainNavigationViewModel.class);

    searchFilterContactPickerLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
          Intent launchIntent = result.getData();
          long   startDate    = launchIntent != null ? launchIntent.getLongExtra(EXTRA_FILTER_START_DATE, -1) : -1;
          long   endDate      = launchIntent != null ? launchIntent.getLongExtra(EXTRA_FILTER_END_DATE, -1) : -1;
          String authorStr    = launchIntent != null ? launchIntent.getStringExtra(EXTRA_FILTER_AUTHOR_ID) : null;

          RecipientId selectedAuthor = authorStr != null ? RecipientId.from(Long.parseLong(authorStr)) : null;
          if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            List<RecipientId> recipients = result.getData().getParcelableArrayListExtra(
                org.thoughtcrime.securesms.search.SingleContactSelectionActivity.KEY_SELECTED_RECIPIENT
            );
            if (recipients != null && !recipients.isEmpty()) {
              selectedAuthor = recipients.get(0);
            }
          }

          SearchFilterBottomSheet.show(
              getParentFragmentManager(),
              startDate != -1 ? startDate : null,
              endDate != -1 ? endDate : null,
              selectedAuthor
          );
        }
    );
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle bundle) {
    return inflater.inflate(R.layout.conversation_list_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    BackupAlertDelegate.delegate(getParentFragmentManager(), getViewLifecycleOwner().getLifecycle());

    lifecycleDisposable = new LifecycleDisposable();
    lifecycleDisposable.bindTo(getViewLifecycleOwner());

    chatFolderList          = view.findViewById(R.id.chat_folder_list);
    list                    = view.findViewById(R.id.list);
    listContainer           = view.findViewById(R.id.list_container);
    conversationList        = view.findViewById(R.id.light_conversation_list);
    contextMenuAnchor       = view.findViewById(R.id.conversation_list_context_menu_anchor);
    searchLoading           = view.findViewById(R.id.search_loading);
    bottomActionBar         = view.findViewById(R.id.conversation_list_bottom_action_bar);
    bannerView              = new Stub<>(view.findViewById(R.id.banner_compose_view));
    voiceNotePlayerViewStub = new Stub<>(view.findViewById(R.id.voice_note_player));
    pullView                = view.findViewById(R.id.pull_view);
    pullViewAppBarLayout    = view.findViewById(R.id.recycler_coordinator_app_bar);

    contactSearchViewModel = new ViewModelProvider(this, new ContactSearchViewModel.Factory(
        SelectionLimits.NO_LIMITS,
        false,
        new ContactSearchRepository(),
        false,
        new ConversationListSearchAdapter.ChatFilterRepository(),
        new SearchRepository(requireContext().getString(R.string.note_to_self)),
        new ContactSearchPagedDataSourceRepository(requireContext(), requireContext().getString(R.string.note_to_self)),
        Collections.emptySet(),
        true
    )).get(ContactSearchViewModel.class);

    searchAdapter = new ConversationListSearchAdapter(
        requireContext(),
        Collections.emptySet(),
        new ContactSearchAdapter.DisplayOptions(false, ContactSearchAdapter.DisplaySecondaryInformation.NEVER, false, false),
        new ContactSearchClickCallbacks(),
        new ContactSearchAdapter.LongClickCallbacksAdapter(),
        new ContactSearchAdapter.StoryContextMenuCallbacks() {
          @Override public void onOpenStorySettings(@NonNull ContactSearchData.Story story) {}
          @Override public void onRemoveGroupStory(@NonNull ContactSearchData.Story story, boolean isSelected) {}
          @Override public void onDeletePrivateStory(@NonNull ContactSearchData.Story story, boolean isSelected) {}
        },
        ContactSearchAdapter.EmptyCallButtonClickCallbacks.INSTANCE,
        getViewLifecycleOwner(),
        Glide.with(this)
    );

    ContactSearchViewModelKt.bindAdapterToLifecycle(contactSearchViewModel, getViewLifecycleOwner(), searchAdapter, this::mapSearchStateToConfiguration);
    ContactSearchViewModelKt.bindSearchInProgressToLifecycle(contactSearchViewModel, getViewLifecycleOwner(), inProgress -> {
      searchInProgress = inProgress;
      updateSearchLoadingVisibility();
      return Unit.INSTANCE;
    });

    initializeSearchFilterListener();

    if (WindowSizeClassExtensionsKt.isHeightCompact(getWindowSizeClass(getResources()))) {
      ViewUtil.setBottomMargin(bottomActionBar, ViewUtil.getNavigationBarHeight(bottomActionBar));
    }

    CollapsingToolbarLayout collapsingToolbarLayout = view.findViewById(R.id.collapsing_toolbar);
    int                     openHeight              = (int) DimensionUnit.DP.toPixels(FilterLerp.FILTER_OPEN_HEIGHT);

    pullView.setOnFilterStateChanged((state, source) -> {
      switch (state) {
        case CLOSING:
          viewModel.setFiltered(false, source);
          mainToolbarViewModel.setChatFilter(ConversationFilter.OFF);
          break;
        case OPENING:
          ViewUtil.setMinimumHeight(collapsingToolbarLayout, openHeight);
          viewModel.setFiltered(true, source);
          mainToolbarViewModel.setChatFilter(ConversationFilter.UNREAD);
          break;
        case OPEN_APEX:
          if (source == ConversationFilterSource.DRAG) {
            SignalStore.uiHints().incrementNeverDisplayPullToFilterTip();
          }
          break;
        case CLOSE_APEX:
          ViewUtil.setMinimumHeight(collapsingToolbarLayout, 0);
          break;
      }
    });

    pullView.setOnCloseClicked(this::onClearFilterClick);

    ConversationFilterBehavior conversationFilterBehavior = Objects.requireNonNull((ConversationFilterBehavior) ((CoordinatorLayout.LayoutParams) pullViewAppBarLayout.getLayoutParams()).getBehavior());
    conversationFilterBehavior.setCallback(new ConversationFilterBehavior.Callback() {
      @Override
      public void onStopNestedScroll() {
        pullView.onUserDragFinished();
      }

      @Override
      public boolean canStartNestedScroll() {
        return !isSearchOpen() || pullView.isCloseable();
      }
    });

    pullViewAppBarLayout.addOnOffsetChangedListener((layout, verticalOffset) -> {
      float progress = 1 - ((float) verticalOffset) / (-layout.getHeight());
      pullView.onUserDrag(progress);
    });

    chatFolderAdapter = new ChatFolderAdapter(this);
    DefaultItemAnimator chatFolderItemAnimator = getChatFolderItemAnimator();

    chatFolderList.setLayoutManager(new LinearLayoutManager(requireActivity(), LinearLayoutManager.HORIZONTAL, false));
    chatFolderList.setAdapter(chatFolderAdapter);
    chatFolderList.setItemAnimator(chatFolderItemAnimator);

    // @id/list now only ever renders search results; the conversation list itself is the Compose
    // LightConversationListView above it.
    list.setLayoutManager(new LinearLayoutManager(requireActivity()));
    CachedInflater.from(list.getContext()).clear();

    initializeViewModel();
    initializeListAdapters();
    initializeVoiceNotePlayer();
    initializeBanners();
    maybeScheduleRefreshProfileJob();
    ConversationListFragmentExtensionsKt.listenToEventBusWhileResumed(this, mainNavigationViewModel.getDetailLocation());

    String query = contactSearchViewModel.getQuery().getValue();
    if (query != null) {
      onSearchQueryUpdated(query);
    }

    if (SignalStore.account().isRegistered() &&
        !TextSecurePreferences.isUnauthorizedReceived(requireContext()) &&
        SignalStore.settings().getAutomaticVerificationEnabled() &&
        SignalStore.misc().getHasKeyTransparencyFailure() &&
        !SignalStore.misc().getHasSeenKeyTransparencyFailure()) {
      SelfVerificationFailureSheet.show(getParentFragmentManager());
    }

    chatListBackHandler = new ChatListBackHandler(false);
    requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), chatListBackHandler);

    lifecycleDisposable.bindTo(getViewLifecycleOwner());
    lifecycleDisposable.add(mainNavigationViewModel.getTabClickEventsObservable().filter(tab -> tab == MainNavigationListLocation.CHATS)
                                                   .subscribe(unused -> {
                                                     Log.d(TAG, "Scroll to top please");
                                                     if (conversationList != null) {
                                                       int firstVisibleItemPosition = conversationList.firstCompletelyVisibleItemPosition();
                                                       conversationList.scrollToTop(firstVisibleItemPosition <= LIST_SMOOTH_SCROLL_TO_TOP_THRESHOLD);
                                                     }
                                                   }));

    // The Light row has no "currently open conversation" highlight -- it is name + time + unread
    // marker and nothing else -- so the split-pane active-recipient tracking has nothing to drive.

    requireCallback().bindScrollHelper(list, getViewLifecycleOwner(), chatFolderList, color -> {
      for (int i = 0; i < chatFolderList.getChildCount(); i++) {
        View child = chatFolderList.getChildAt(i);
        if (child != null && child.isSelected()) {
          child.setBackgroundTintList(ColorStateList.valueOf(color));
        }
      }
      return Unit.INSTANCE;
    });

    smoothScroller = new LinearSmoothScroller(requireContext()) {
      @Override
      protected int calculateTimeForScrolling(int dx) {
        return 150;
      }
    };
  }

  private @NonNull DefaultItemAnimator getChatFolderItemAnimator() {
    int                 duration = 150;
    DefaultItemAnimator animator = new DefaultItemAnimator();
    animator.setAddDuration(duration);
    animator.setMoveDuration(duration);
    animator.setRemoveDuration(duration);
    animator.setChangeDuration(duration);
    return animator;
  }

  @Override
  public void onDestroyView() {
    if (activeContextMenu != null) {
      activeContextMenu.dismiss();
      activeContextMenu = null;
    }

    coordinator             = null;
    list                    = null;
    listContainer           = null;
    contextMenuAnchor       = null;
    bottomActionBar         = null;
    voiceNotePlayerViewStub = null;

    if (conversationList != null) {
      conversationList.setCallback(null);
      conversationList = null;
    }

    searchAdapter = null;

    if (searchLoading != null) {
      searchLoading.removeCallbacks(showSearchLoadingRunnable);
      searchLoading = null;
    }

    dismissProgressDialog();

    super.onDestroyView();
  }

  @Override
  public void onResume() {
    super.onResume();

    initializeSearchListener();
    initializeFilterListener();
    SpoilerAnnotation.resetRevealedSpoilers();

    if (mainToolbarViewModel.getState().getValue().getMode() != MainToolbarMode.SEARCH && showingSearchResults) {
      showConversationList();
    }

    if (SignalStore.rateLimit().needsRecaptcha()) {
      Log.i(TAG, "Recaptcha required.");
      RecaptchaProofBottomSheetFragment.show(getChildFragmentManager());
    }

    if (this.bannerManager != null) {
      this.bannerManager.updateContent(bannerView.get());
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    AppForegroundObserver.addListener(appForegroundObserver);
  }

  @Override
  public void onStop() {
    super.onStop();
    AppForegroundObserver.removeListener(appForegroundObserver);
  }

  @Override
  public void onConfigurationChanged(@NonNull Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
  }

  private ContactSearchConfiguration mapSearchStateToConfiguration(@NonNull ContactSearchState state) {
    if (TextUtils.isEmpty(state.getQuery())) {
      return ContactSearchConfiguration.build(b -> Unit.INSTANCE);
    } else {
      return ContactSearchConfiguration.build(builder -> {
        ConversationFilterRequest conversationFilterRequest = state.getConversationFilterRequest();
        boolean                   unreadOnly                = conversationFilterRequest != null && conversationFilterRequest.getFilter() == ConversationFilter.UNREAD;

        builder.setQuery(state.getQuery());
        builder.setSearchFilter(state.getSearchFilter());
        builder.addSection(new ContactSearchConfiguration.Section.Chats(
            unreadOnly,
            true,
            new ContactSearchConfiguration.ExpandConfig(
                state.getExpandedSections().contains(ContactSearchConfiguration.SectionKey.CHATS),
                (a) -> MAX_CHATS_ABOVE_FOLD
            )
        ));

        if (!unreadOnly) {
          builder.addSection(new ContactSearchConfiguration.Section.GroupsWithMembers(
              true,
              new ContactSearchConfiguration.ExpandConfig(
                  state.getExpandedSections().contains(ContactSearchConfiguration.SectionKey.GROUPS_WITH_MEMBERS),
                  (a) -> MAX_GROUP_MEMBERSHIPS_ABOVE_FOLD
              )
          ));

          builder.addSection(new ContactSearchConfiguration.Section.ContactsWithoutThreads(
              true,
              new ContactSearchConfiguration.ExpandConfig(
                  state.getExpandedSections().contains(ContactSearchConfiguration.SectionKey.CONTACTS_WITHOUT_THREADS),
                  (a) -> MAX_CONTACTS_ABOVE_FOLD
              )
          ));

          builder.addSection(new ContactSearchConfiguration.Section.Messages(
              true,
              null
          ));

          builder.withEmptyState(emptyStateBuilder -> {
            emptyStateBuilder.addSection(ContactSearchConfiguration.Section.Empty.INSTANCE);
            return Unit.INSTANCE;
          });
        } else {
          builder.arbitrary(
              conversationFilterRequest.getSource() == ConversationFilterSource.DRAG
              ? ConversationListSearchModels.ChatFilterOptions.WITHOUT_TIP.getCode()
              : ConversationListSearchModels.ChatFilterOptions.WITH_TIP.getCode()
          );
        }

        return Unit.INSTANCE;
      });
    }
  }

  private boolean isSearchOpen() {
    return isSearchVisible() || showingSearchResults;
  }

  private boolean isSearchVisible() {
    return mainToolbarViewModel.getState().getValue().getMode() == MainToolbarMode.SEARCH;
  }

  private void closeSearchIfOpen() {
    if (isSearchOpen()) {
      showConversationList();
      mainToolbarViewModel.setToolbarMode(MainToolbarMode.FULL);
      chatListBackHandler.setEnabled(false);
    }
  }

  private void onConversationClicked(@NonNull ThreadWithRecipient threadRecord) {
    hideKeyboard();
    getNavigator().goToConversation(threadRecord.getRecipient().getId(),
                                    threadRecord.getThreadId(),
                                    threadRecord.getDistributionType(),
                                    -1);
  }

  private void onShowArchiveClick() {
    if (viewModel.currentSelectedConversations().isEmpty()) {
      mainNavigationViewModel.goTo(MainNavigationListLocation.ARCHIVE);
    }
  }

  private void onContactClicked(@NonNull Recipient contact) {
    SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
      return SignalDatabase.threads().getThreadIdIfExistsFor(contact.getId());
    }, threadId -> {
      hideKeyboard();
      getNavigator().goToConversation(contact.getId(),
                                      threadId,
                                      ThreadTable.DistributionTypes.DEFAULT,
                                      -1);
    });
  }

  private void onMessageClicked(@NonNull MessageResult message) {
    SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
      int startingPosition = SignalDatabase.messages().getMessagePositionInConversation(message.getThreadId(), message.getReceivedTimestampMs());
      return Math.max(0, startingPosition);
    }, startingPosition -> {
      hideKeyboard();
      getNavigator().goToConversation(message.getConversationRecipient().getId(),
                                      message.getThreadId(),
                                      ThreadTable.DistributionTypes.DEFAULT,
                                      startingPosition);
    });
  }

  private void hideKeyboard() {
    InputMethodManager imm = ServiceUtil.getInputMethodManager(requireContext());
    imm.hideSoftInputFromWindow(requireView().getWindowToken(), 0);
  }

  private void initializeSearchListener() {
    lifecycleDisposable.add(
        viewModel.getFilterRequestState().subscribe(request -> {
          updateSearchToolbarHint(request);
          contactSearchViewModel.setConversationFilterRequest(request);
        })
    );

    lifecycleDisposable.add(
        mainToolbarViewModel.getSearchEventsFlowable().subscribe(event -> {
          if (event instanceof MainToolbarViewModel.Event.Search.Open) {
            onSearchOpen();
          } if (event instanceof MainToolbarViewModel.Event.Search.Close) {
            onSearchClose();
          } else if (event instanceof MainToolbarViewModel.Event.Search.Query) {
            onSearchQueryUpdated(((MainToolbarViewModel.Event.Search.Query) event).getQuery());
          }
        })
    );
  }

  private void initializeFilterListener() {
    lifecycleDisposable.add(
        mainToolbarViewModel.getChatEventsFlowable().subscribe(event -> {
          if (event instanceof MainToolbarViewModel.Event.Chats.ApplyFilter) {
            handleFilterUnreadChats();
          } else if (event instanceof MainToolbarViewModel.Event.Chats.ClearFilter) {
            onClearFilterClick();
          } else if (event instanceof MainToolbarViewModel.Event.Chats.CloseArchive) {
            mainNavigationViewModel.goTo(MainNavigationListLocation.CHATS);
          }
        })
    );
  }

  private void initializeSearchFilterListener() {
    getParentFragmentManager().setFragmentResultListener(
        SearchFilterBottomSheet.REQUEST_KEY,
        getViewLifecycleOwner(),
        (requestKey, result) -> {
          String action = result.getString(SearchFilterBottomSheet.RESULT_ACTION, "");

          switch (action) {
            case SearchFilterBottomSheet.ACTION_APPLY:
              long   startDate   = result.getLong(SearchFilterBottomSheet.RESULT_START_DATE, -1);
              long   endDate     = result.getLong(SearchFilterBottomSheet.RESULT_END_DATE, -1);
              String authorIdStr = result.getString(SearchFilterBottomSheet.RESULT_AUTHOR_ID);

              activeSearchFilter = new SearchFilter(
                  startDate != -1 ? startDate : null,
                  endDate != -1 ? endDate : null,
                  authorIdStr != null ? RecipientId.from(Long.parseLong(authorIdStr)) : null
              );
              mainToolbarViewModel.setHasActiveSearchFilter(!activeSearchFilter.isEmpty());
              contactSearchViewModel.setSearchFilter(activeSearchFilter);
              break;

            case SearchFilterBottomSheet.ACTION_CLEAR:
              activeSearchFilter = SearchFilter.EMPTY;
              mainToolbarViewModel.setHasActiveSearchFilter(false);
              contactSearchViewModel.setSearchFilter(activeSearchFilter);
              break;

            case SearchFilterBottomSheet.ACTION_SELECT_AUTHOR:
              searchFilterContactPickerLauncher.launch(new Intent(requireContext(), org.thoughtcrime.securesms.search.SingleContactSelectionActivity.class)
                  .putExtra(ContactSelectionArguments.DISPLAY_MODE, ContactSelectionDisplayMode.FLAG_PUSH | ContactSelectionDisplayMode.FLAG_ACTIVE_GROUPS)
                  .putExtra(EXTRA_FILTER_START_DATE, result.getLong(SearchFilterBottomSheet.RESULT_START_DATE, -1))
                  .putExtra(EXTRA_FILTER_END_DATE, result.getLong(SearchFilterBottomSheet.RESULT_END_DATE, -1))
                  .putExtra(EXTRA_FILTER_AUTHOR_ID, result.getString(SearchFilterBottomSheet.RESULT_AUTHOR_ID)));
              break;
          }
        }
    );
  }

  private void updateSearchToolbarHint(@NonNull ConversationFilterRequest conversationFilterRequest) {
    mainToolbarViewModel.setSearchHint(
        conversationFilterRequest.getFilter() == ConversationFilter.OFF ? R.string.SearchToolbar_search : R.string.SearchToolbar_search_unread_chats
    );
  }

  private void initializeVoiceNotePlayer() {
    mediaControllerOwner.getVoiceNoteMediaController().getVoiceNotePlayerViewState().observe(getViewLifecycleOwner(), state -> {
      if (state.isPresent()) {
        requireVoiceNotePlayerView().setState(state.get());
        requireVoiceNotePlayerView().show();
      } else if (voiceNotePlayerViewStub.resolved()) {
        requireVoiceNotePlayerView().hide();
      }
    });
  }

  private void initializeBanners() {
    List<Banner<?>> bannerRepositories = List.of(
        new DeprecatedSdkBanner(),
        new DeprecatedBuildBanner(),
        new UnauthorizedBanner(requireContext()),
        new ServiceOutageBanner(requireContext()),
        new OutdatedBuildBanner(),
        new DozeBanner(requireContext()),
        new CdsTemporaryErrorBanner(getChildFragmentManager()),
        new CdsPermanentErrorBanner(getChildFragmentManager()),
        new UsernameOutOfSyncBanner((usernameSyncState) -> {
          if (usernameSyncState == AccountValues.UsernameSyncState.USERNAME_AND_LINK_CORRUPTED) {
            startActivityForResult(AppSettingsActivity.usernameRecovery(requireContext()), UsernameEditFragment.REQUEST_CODE);
          } else {
            startActivity(AppSettingsActivity.usernameLinkSettings(requireContext()));
          }
          return Unit.INSTANCE;
        }),
        new ArchiveRestoreStatusBanner(new ArchiveRestoreStatusBanner.RestoreProgressBannerListener() {
          @Override
          public void onBannerClick() {
            startActivity(AppSettingsActivity.backupsSettings(requireContext()));
          }

          @Override
          public void onActionClick(@NonNull ArchiveRestoreProgressState data) {
            if (data.getRestoreStatus() == ArchiveRestoreProgressState.RestoreStatus.NOT_ENOUGH_DISK_SPACE) {
              BackupAlertBottomSheet.create(new BackupAlert.DiskFull(data.getRemainingRestoreSize().toUnitString())).show(getParentFragmentManager(), null);
            } else if (data.getRestoreState() == RestoreState.RESTORING_MEDIA && data.getRestoreStatus() == ArchiveRestoreProgressState.RestoreStatus.WAITING_FOR_WIFI) {
              new MaterialAlertDialogBuilder(requireContext())
                  .setTitle(R.string.ResumeRestoreCellular_resume_using_cellular_title)
                  .setMessage(R.string.ResumeRestoreCellular_resume_using_cellular_message)
                  .setNegativeButton(android.R.string.cancel, null)
                  .setPositiveButton(R.string.BackupStatus__resume, (d, w) -> {
                    SignalExecutors.BOUNDED.execute(() -> {
                      SignalStore.backup().setRestoreWithCellular(true);
                      ArchiveRestoreProgress.forceUpdate();
                    });
                  })
                  .show();
            }
          }
        }),
        new ArchiveUploadStatusBanner(new ArchiveUploadStatusBanner.UploadProgressBannerListener() {
          @Override
          public void onBannerClick() {
            startActivity(AppSettingsActivity.remoteBackups(requireContext()));
          }

          @Override
          public void onCancelClicked() {
            new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.CancelBackupDialog_title)
                .setMessage(R.string.CancelBackupDialog_body)
                .setNegativeButton(R.string.CancelBackupDialog_continue_action, null)
                .setPositiveButton(R.string.CancelBackupDialog_cancel_action, (d, w) -> {
                  ArchiveUploadProgress.INSTANCE.cancel();
                })
                .show();
          }
        })
    );

    this.bannerManager = new BannerManager(bannerRepositories);
    this.bannerManager.updateContent(bannerView.get());
  }

  private void maybeScheduleRefreshProfileJob() {
    switch (SignalStore.account().getUsernameSyncState()) {
      case USERNAME_AND_LINK_CORRUPTED, LINK_CORRUPTED -> AppDependencies.getJobManager().add(new RefreshOwnProfileJob());
      case IN_SYNC -> {}
    }
  }

  private @NonNull VoiceNotePlayerView requireVoiceNotePlayerView() {
    if (voiceNotePlayerView == null) {
      voiceNotePlayerView = voiceNotePlayerViewStub.get().findViewById(R.id.voice_note_player_view);
      voiceNotePlayerView.setListener(new VoiceNotePlayerViewListener());
    }

    return voiceNotePlayerView;
  }


  private void initializeListAdapters() {
    conversationList.setCallback(new LightConversationListCallback());

    showConversationList();

    ConversationUpdateTick conversationUpdateTick = new ConversationUpdateTick(() -> {
      if (conversationList != null) {
        conversationList.refreshTimestamps();
      }
    });
    getViewLifecycleOwner().getLifecycle().addObserver(conversationUpdateTick);
  }

  /** Shows the Compose conversation list and tears down whatever the search RecyclerView was showing. */
  private void showConversationList() {
    if (list == null || conversationList == null) {
      return;
    }

    if (showingSearchResults) {
      list.setAdapter(null);
    }

    showingSearchResults = false;
    list.setVisibility(View.GONE);
    conversationList.setVisibility(View.VISIBLE);

    updateSearchLoadingVisibility();
  }

  /** Swaps the Compose conversation list out for the legacy search-results RecyclerView. */
  private void showSearchResults() {
    if (list == null || conversationList == null) {
      return;
    }

    if (!showingSearchResults) {
      showingSearchResults = true;
      list.setAdapter(searchAdapter);
    }

    conversationList.setVisibility(View.GONE);
    list.setVisibility(View.VISIBLE);

    updateSearchLoadingVisibility();
  }

  private void updateSearchLoadingVisibility() {
    if (searchLoading == null) {
      return;
    }

    boolean shouldShow = searchInProgress && showingSearchResults;
    searchLoading.removeCallbacks(showSearchLoadingRunnable);

    if (shouldShow) {
      if (searchLoading.getVisibility() != View.VISIBLE) {
        searchLoading.postDelayed(showSearchLoadingRunnable, SEARCH_LOADING_SHOW_DELAY_MS);
      }
    } else {
      searchLoading.setVisibility(View.GONE);
    }
  }

  protected boolean isArchived() {
    return false;
  }

  private void initializeViewModel() {
    Class<? extends ConversationListViewModel> viewModelClass = isArchived() ? ConversationListViewModel.ArchivedConversationListViewModel.class : ConversationListViewModel.UnarchivedConversationListViewModel.class;
    viewModel = new ViewModelProvider(requireActivity(), new ConversationListViewModel.Factory(isArchived())).get(viewModelClass);

    lifecycleDisposable.add(viewModel.getConversationsState().subscribe(this::onConversationListChanged));
    lifecycleDisposable.add(viewModel.getHasNoConversations().subscribe(this::updateEmptyState));
    lifecycleDisposable.add(viewModel.getWebSocketState().subscribe(pipeState -> requireCallback().updateProxyStatus(pipeState)));
    lifecycleDisposable.add(viewModel.getChatFolderState().subscribe(this::onChatFoldersChanged));

    if (viewModel.getConversationFilterRequest().getFilter() == ConversationFilter.UNREAD) {
      pullView.openAfterNextLayout();
    }

    appForegroundObserver = new AppForegroundObserver.Listener() {
      @Override
      public void onForeground() {
        viewModel.onVisible();
      }

      @Override
      public void onBackground() {}
    };

    lifecycleDisposable.add(
        viewModel.getSelectedState().subscribe(conversations -> {
          if (conversationList != null) {
            conversationList.setSelectedConversations(conversations);
          }
          if (conversations.isEmpty()) {
            endActionModeIfActive();
          } else {
            startActionModeIfNotActive();
            updateMultiSelectState();
          }
        })
    );
  }

  private void onFirstRender() {
    AppStartup.getInstance().onCriticalRenderEventEnd();
    startupStopwatch.split("first-render");
    startupStopwatch.stop(TAG);
    mediaControllerOwner.getVoiceNoteMediaController().finishPostpone();

    Context context = getContext();
    if (context != null) {
      FrameLayout parent = new FrameLayout(context);
      parent.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

      CachedInflater.from(context).cacheUntilLimit(R.layout.v2_conversation_item_text_only_incoming, parent, 25);
      CachedInflater.from(context).cacheUntilLimit(R.layout.v2_conversation_item_text_only_outgoing, parent, 25);
      CachedInflater.from(context).cacheUntilLimit(R.layout.conversation_item_received_multimedia, parent, 10);
      CachedInflater.from(context).cacheUntilLimit(R.layout.conversation_item_sent_multimedia, parent, 10);
      CachedInflater.from(context).cacheUntilLimit(R.layout.conversation_item_update, parent, 5);
      CachedInflater.from(context).cacheUntilLimit(R.layout.cursor_adapter_header_footer_view, parent, 2);
    }
  }

  private void onConversationListChanged(@NonNull List<Conversation> conversations) {
    if (conversationList == null) {
      return;
    }

    int firstVisibleItem = conversationList.firstCompletelyVisibleItemPosition();

    conversationList.submitList(conversations);

    // A newly bumped conversation reorders the list. When the user was already parked at the very
    // top, follow the reorder so the new top row stays in view.
    if (firstVisibleItem == 0) {
      conversationList.scrollToTop(false);
    }

    if (!reportedFirstDataSet && !conversations.isEmpty()) {
      reportedFirstDataSet = true;
      startupStopwatch.split("data-set");
      SignalLocalMetrics.ColdStart.onConversationListDataLoaded();
      if (requireActivity() instanceof MainNavigator.NavigatorProvider) {
        ((MainNavigator.NavigatorProvider) requireActivity()).onFirstRender();
      }
      conversationList.post(this::onFirstRender);
    }

    onPostSubmitList(conversations.size());
  }

  private void onChatFoldersChanged(List<ChatFolderMappingModel> folders) {
    chatFolderList.setVisibility(folders.size() > 1 && !isArchived() ? View.VISIBLE : View.GONE);
    chatFolderAdapter.submitList(new ArrayList<>(folders));
  }

  private void handleMarkAsRead(@NonNull Collection<Long> ids) {
    Context   context   = requireContext();
    Stopwatch stopwatch = new Stopwatch("mark-read");

    SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
      stopwatch.split("task-start");

      List<MarkedMessageInfo> messageIds = SignalDatabase.threads().setRead(ids);
      stopwatch.split("db");

      AppDependencies.getMessageNotifier().updateNotification(context);
      stopwatch.split("notification");

      MarkReadReceiver.process(messageIds);
      stopwatch.split("process");

      return null;
    }, none -> {
      endActionModeIfActive();
      stopwatch.stop(TAG);

    });
  }

  private void handleMarkAsUnread(@NonNull Collection<Long> ids) {
    SimpleTask.run(getViewLifecycleOwner().getLifecycle(), () -> {
      SignalDatabase.threads().setForcedUnread(ids);
      return null;
    }, none -> endActionModeIfActive());
  }

  private void handleFilterUnreadChats() {
    pullView.toggle();
    pullViewAppBarLayout.setExpanded(false, true);
  }

  @SuppressLint("StaticFieldLeak")
  private void handleArchive(@NonNull Collection<Long> ids) {
    Set<Long> selectedConversations = new HashSet<>(ids);
    int       count                 = selectedConversations.size();
    String    snackBarTitle         = getResources().getQuantityString(R.plurals.ConversationListFragment_conversations_archived, count, count);
    boolean   showProgress          = count > 1;

    dismissProgressDialog();
    if (showProgress) {
      progressDialog = SignalProgressDialog.show(requireContext(), null, null, true, false, null);
    }

    lifecycleDisposable.add(Completable
        .fromAction(() -> SignalDatabase.threads().setArchived(selectedConversations, true))
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(() -> {
          dismissProgressDialog();
          endActionModeIfActive();

          mainNavigationViewModel.getSnackbarRegistry().emit(new SnackbarState(
              snackBarTitle,
              new SnackbarState.ActionState(
                  getString(R.string.ConversationListFragment_undo),
                  R.color.amber_500,
                  () -> {
                    handleUnarchive(selectedConversations);
                    return Unit.INSTANCE;
                  }
              ),
              Snackbars.Duration.LONG,
              MainSnackbarHostKey.MainChrome.INSTANCE,
              null
          ));
        }));
  }

  private void handleUnarchive(@NonNull Set<Long> threadIds) {
    int     count        = threadIds.size();
    String  snackBarTitle = getResources().getQuantityString(R.plurals.ConversationListFragment_moved_conversations_to_inbox, count, count);
    boolean showProgress = count > 1;

    dismissProgressDialog();
    if (showProgress) {
      progressDialog = SignalProgressDialog.show(requireContext(), null, null, true, false, null);
    }

    lifecycleDisposable.add(Completable
        .fromAction(() -> SignalDatabase.threads().setArchived(threadIds, false))
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(() -> {
          dismissProgressDialog();
          endActionModeIfActive();

          mainNavigationViewModel.getSnackbarRegistry().emit(new SnackbarState(
              snackBarTitle,
              new SnackbarState.ActionState(
                  getString(R.string.ConversationListFragment_undo),
                  R.color.amber_500,
                  () -> {
                    handleArchive(threadIds);
                    return Unit.INSTANCE;
                  }
              ),
              Snackbars.Duration.LONG,
              MainSnackbarHostKey.MainChrome.INSTANCE,
              null
          ));
        }));
  }

  private void dismissProgressDialog() {
    if (progressDialog != null) {
      progressDialog.dismiss();
      progressDialog = null;
    }
  }

  @SuppressLint("StaticFieldLeak")
  private void handleDelete(@NonNull Collection<Long> ids) {
    if (DeleteSyncEducationDialog.shouldShow()) {
      lifecycleDisposable.add(
          DeleteSyncEducationDialog.show(getChildFragmentManager())
                                   .subscribe(() -> handleDelete(ids))
      );

      return;
    }

    int                        conversationsCount = ids.size();
    MaterialAlertDialogBuilder alert              = new MaterialAlertDialogBuilder(requireActivity());
    Context                    context            = requireContext();

    alert.setTitle(context.getResources().getQuantityString(R.plurals.ConversationListFragment_delete_selected_conversations,
                                                            conversationsCount, conversationsCount));

    if (SignalStore.account().isMultiDevice()) {
      alert.setMessage(context.getResources().getQuantityString(R.plurals.ConversationListFragment_this_will_permanently_delete_all_n_selected_conversations_linked_device,
                                                                conversationsCount, conversationsCount));
    } else {
      alert.setMessage(context.getResources().getQuantityString(R.plurals.ConversationListFragment_this_will_permanently_delete_all_n_selected_conversations,
                                                                conversationsCount, conversationsCount));
    }

    alert.setCancelable(true);

    alert.setPositiveButton(R.string.delete, (dialog, which) -> {
      final Set<Long> selectedConversations = new HashSet<>(ids);

      if (!selectedConversations.isEmpty()) {
        new AsyncTask<Void, Void, Void>() {
          private SignalProgressDialog dialog;

          @Override
          protected void onPreExecute() {
            dialog = SignalProgressDialog.show(requireActivity(),
                                               context.getString(R.string.ConversationListFragment_deleting),
                                               context.getResources().getQuantityString(R.plurals.ConversationListFragment_deleting_selected_conversations, conversationsCount),
                                               true,
                                               false);
          }

          @Override
          protected Void doInBackground(Void... params) {
            Log.d(TAG, "[handleDelete] Deleting " + selectedConversations.size() + " chats");
            SignalDatabase.threads().deleteConversations(selectedConversations, true);
            AppDependencies.getMessageNotifier().updateNotification(AppDependencies.getApplication());
            Log.d(TAG, "[handleDelete] Delete complete");
            return null;
          }

          @Override
          protected void onPostExecute(Void result) {
            dialog.dismiss();
            endActionModeIfActive();
          }
        }.executeOnExecutor(SignalExecutors.BOUNDED);
      }
    });

    alert.setNegativeButton(android.R.string.cancel, null);
    alert.show();
  }

  private void handlePin(@NonNull Collection<Conversation> conversations) {
    final Set<Long> toPin = new LinkedHashSet<>(conversations.stream()
                                                      .filter(conversation -> !conversation.getThreadRecord().isPinned())
                                                      .map(conversation -> conversation.getThreadRecord().getThreadId())
                                                      .collect(Collectors.toList()));

    if (toPin.size() + viewModel.getPinnedCount() > RemoteConfig.pinnedChatLimit()) {
      mainNavigationViewModel.getSnackbarRegistry().emit(new SnackbarState(
          getString(R.string.conversation_list__you_can_only_pin_up_to_d_chats, RemoteConfig.pinnedChatLimit()),
          null,
          Snackbars.Duration.LONG,
          MainSnackbarHostKey.MainChrome.INSTANCE,
          null
      ));

      endActionModeIfActive();
      return;
    }

    SimpleTask.run(SignalExecutors.BOUNDED, () -> {
      ThreadTable db = SignalDatabase.threads();

      db.pinConversations(toPin);
      ConversationUtil.refreshRecipientShortcuts();

      return null;
    }, unused -> {
      endActionModeIfActive();
    });
  }

  private void handleUnpin(@NonNull Collection<Long> ids) {
    SimpleTask.run(SignalExecutors.BOUNDED, () -> {
      ThreadTable db = SignalDatabase.threads();

      db.unpinConversations(ids);
      ConversationUtil.refreshRecipientShortcuts();

      return null;
    }, unused -> {
      endActionModeIfActive();
    });
  }

  private void handleMute(@NonNull Collection<Conversation> conversations) {
    MuteDialog.show(requireContext(), getChildFragmentManager(), getViewLifecycleOwner(), until -> updateMute(conversations, until));
  }

  private void handleUnmute(@NonNull Collection<Conversation> conversations) {
    updateMute(conversations, 0);
  }

  private void updateMute(@NonNull Collection<Conversation> conversations, long until) {
    dismissProgressDialog();
    progressDialog = SignalProgressDialog.show(requireContext(), null, null, true, false, null);

    SimpleTask.run(SignalExecutors.BOUNDED, () -> {
      List<RecipientId> recipientIds = conversations.stream()
                                                    .map(conversation -> conversation.getThreadRecord().getRecipient().live().get())
                                                    .filter(r -> r.getMuteUntil() != until)
                                                    .map(Recipient::getId)
                                                    .collect(Collectors.toList());
      SignalDatabase.recipients().setMuted(recipientIds, until);
      return null;
    }, unused -> {
      endActionModeIfActive();
      dismissProgressDialog();
    });
  }

  private void handleCreateConversation(long threadId, Recipient recipient, int distributionType) {
    getNavigator().goToConversation(recipient.getId(), threadId, distributionType, -1);
  }

  private void handleOpenIncognito(@NonNull Conversation conversation) {
    long      threadId         = conversation.getThreadRecord().getThreadId();
    Recipient recipient        = conversation.getThreadRecord().getRecipient();
    int       distributionType = conversation.getThreadRecord().getDistributionType();

    getNavigator().goToConversation(recipient.getId(), threadId, distributionType, -1, true);
  }

  private void startActionModeIfNotActive() {
    if (!mainToolbarViewModel.isInActionMode()) {
      startActionMode();
    }
  }

  private void startActionMode() {
    ViewUtil.animateIn(bottomActionBar, bottomActionBar.getEnterAnimation());
    requireCallback().onMultiSelectStarted();
  }

  public void endActionModeIfActive() {
    if (mainToolbarViewModel.isInActionMode()) {
      endActionMode();
    }
  }

  private void endActionMode() {
    ViewUtil.animateOut(bottomActionBar, bottomActionBar.getExitAnimation());
    requireCallback().onMultiSelectFinished();
    viewModel.endSelection();
  }

  void updateEmptyState(boolean isConversationEmpty) {
    if (isConversationEmpty) {
      Log.i(TAG, "Received an empty data set.");
    }
  }

  protected void onPostSubmitList(int conversationCount) {
  }

  private void onConversationClick(@NonNull Conversation conversation) {
    if (!mainToolbarViewModel.isInActionMode()) {
      handleCreateConversation(conversation.getThreadRecord().getThreadId(), conversation.getThreadRecord().getRecipient(), conversation.getThreadRecord().getDistributionType());
    } else {
      viewModel.toggleConversationSelected(conversation);
    }
  }

  /**
   * Long press on a Compose row. Compose rows have no {@link View} of their own, so we park the
   * invisible {@code @id/conversation_list_context_menu_anchor} over the row's bounds and anchor
   * {@link SignalContextMenu} to that -- the menu then drops down from exactly where the row is,
   * the same as it did from a RecyclerView item view.
   */
  private void onConversationLongClick(@NonNull Conversation conversation, @NonNull Rect bounds) {
    if (contextMenuAnchor == null || listContainer == null) {
      return;
    }

    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) contextMenuAnchor.getLayoutParams();
    params.width      = Math.max(1, bounds.width());
    params.height     = Math.max(1, bounds.height());
    params.leftMargin = bounds.left;
    params.topMargin  = bounds.top;
    contextMenuAnchor.setLayoutParams(params);

    // Wait for the anchor to actually be laid out at its new bounds before measuring against it.
    contextMenuAnchor.post(() -> {
      if (contextMenuAnchor != null && listContainer != null) {
        showConversationContextMenu(conversation, contextMenuAnchor, listContainer, false);
      }
    });
  }

  private boolean showConversationContextMenu(@NonNull Conversation conversation, @NonNull View view, @NonNull ViewGroup container, boolean isFromSearch) {
    if (mainToolbarViewModel.isInActionMode()) {
      onConversationClick(conversation);
      return true;
    }

    if (activeContextMenu != null) {
      Log.w(TAG, "Already showing a context menu.");
      return true;
    }

    view.setSelected(true);

    Set<Long> id = Collections.singleton(conversation.getThreadRecord().getThreadId());

    List<ActionItem> items = new ArrayList<>();

    if (!conversation.getThreadRecord().isArchived()) {
      if (conversation.getThreadRecord().isRead()) {
        items.add(new ActionItem(R.drawable.symbol_chat_badge_24, getResources().getQuantityString(R.plurals.ConversationListFragment_unread_plural, 1), () -> handleMarkAsUnread(id)));
      } else {
        items.add(new ActionItem(R.drawable.symbol_chat_24, getResources().getQuantityString(R.plurals.ConversationListFragment_read_plural, 1), () -> handleMarkAsRead(id)));
      }

      if (conversation.getThreadRecord().isPinned()) {
        items.add(new ActionItem(R.drawable.symbol_pin_slash_24, getResources().getString(R.string.ConversationListFragment_unpin), () -> handleUnpin(id)));
      } else {
        items.add(new ActionItem(R.drawable.symbol_pin_24, getResources().getString(R.string.ConversationListFragment_pin), () -> handlePin(Collections.singleton(conversation))));
      }

      if (conversation.getThreadRecord().getRecipient().live().get().isMuted()) {
        items.add(new ActionItem(R.drawable.symbol_bell_24, getResources().getString(R.string.ConversationListFragment_unmute), () -> handleUnmute(Collections.singleton(conversation))));
      } else {
        items.add(new ActionItem(R.drawable.symbol_bell_slash_24, getResources().getString(R.string.ConversationListFragment_mute), () -> handleMute(Collections.singleton(conversation))));
      }

      if (SignalStore.labs().getIncognito()) {
        items.add(new ActionItem(R.drawable.symbol_view_once_24, "Open Incognito (Labs)", () -> handleOpenIncognito(conversation)));
      }
    }

    if (!isFromSearch) {
      items.add(new ActionItem(org.signal.core.ui.R.drawable.symbol_check_circle_24, getString(R.string.ConversationListFragment_select), () -> {
        viewModel.startSelection(conversation);
        startActionMode();
      }));
    }

    if (conversation.getThreadRecord().isArchived()) {
      items.add(new ActionItem(R.drawable.symbol_archive_up_24, getResources().getString(R.string.ConversationListFragment_unarchive), () -> handleUnarchive(id)));
    } else {
      if (!isFromSearch) {
        if (viewModel.getCurrentFolder().getFolderType() == ChatFolderRecord.FolderType.ALL &&
            (conversation.getThreadRecord().getRecipient().isIndividual() ||
             conversation.getThreadRecord().getRecipient().isPushV2Group()))
        {
          items.add(new ActionItem(R.drawable.symbol_folder_add, getString(R.string.ConversationListFragment_add_to_folder), () ->
              showAddToFolderBottomSheet(conversation)
          ));
        } else if (viewModel.getCurrentFolder().getFolderType() != ChatFolderRecord.FolderType.ALL) {
          items.add(new ActionItem(R.drawable.symbol_folder_minus, getString(R.string.ConversationListFragment_remove_from_folder), () -> viewModel.removeChatFromFolder(conversation.getThreadRecord().getThreadId())));
        }
      }
      items.add(new ActionItem(R.drawable.symbol_archive_24, getResources().getString(R.string.ConversationListFragment_archive), () -> handleArchive(id)));
    }

    items.add(new ActionItem(org.signal.core.ui.R.drawable.symbol_trash_24, getResources().getString(R.string.ConversationListFragment_delete), () -> handleDelete(id)));

    // Only the search RecyclerView needs its layout frozen under the menu; the Compose list is
    // anchored to a static overlay view instead.
    final boolean suppressListLayout = container == list;

    activeContextMenu = new SignalContextMenu.Builder(view, container)
        .offsetX(ViewUtil.dpToPx(12))
        .offsetY(ViewUtil.dpToPx(12))
        .onDismiss(() -> {
          activeContextMenu = null;
          view.setSelected(false);
          if (suppressListLayout && list != null) {
            list.suppressLayout(false);
          }
        })
        .show(items);

    if (suppressListLayout) {
      list.suppressLayout(true);
    }

    return true;
  }

  @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
  public void onEvent(MessageSender.MessageSentEvent event) {
    EventBus.getDefault().removeStickyEvent(event);
    closeSearchIfOpen();
  }

  private void showAddToFolderBottomSheet(Conversation conversation) {
    showAddToFolderBottomSheet(
        Collections.singletonList(conversation.getThreadRecord().getThreadId()),
        Collections.singletonList(getThreadType(conversation))
    );
  }

  private void showAddToFolderBottomSheet(Set<Conversation> conversations) {
    List<Long>    threadIds   = new ArrayList<>();
    List<Integer> threadTypes = new ArrayList<>();

    for (Conversation conversation : conversations) {
      threadIds.add(conversation.getThreadRecord().getThreadId());
      threadTypes.add(getThreadType(conversation));
    }

    showAddToFolderBottomSheet(
        threadIds,
        threadTypes
    );
  }

  private int getThreadType(Conversation conversation) {
    boolean isIndividual = conversation.getThreadRecord().getRecipient().isIndividual();
    boolean isGroup      = conversation.getThreadRecord().getRecipient().isPushGroup();
    int     type;
    if (isIndividual) {
      type = AddToFolderBottomSheet.ThreadType.INDIVIDUAL.getValue();
    } else if (isGroup) {
      type = AddToFolderBottomSheet.ThreadType.GROUP.getValue();
    } else {
      type = AddToFolderBottomSheet.ThreadType.OTHER.getValue();
    }
    return type;
  }

  private void showAddToFolderBottomSheet(List<Long> threadIds, List<Integer> threadTypes) {
    List<ChatFolderRecord> folders = viewModel.getFolders().stream().map(ChatFolderMappingModel::getChatFolder).collect(Collectors.toList());
    AddToFolderBottomSheet.showChatFolderSheet(
        folders,
        threadIds,
        threadTypes,
        this::endActionModeIfActive
    ).show(getParentFragmentManager(), BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG);
  }

  private void updateMultiSelectState() {
    int     count       = viewModel.currentSelectedConversations().size();
    boolean hasUnread   = viewModel.currentSelectedConversations().stream().anyMatch(conversation -> !conversation.getThreadRecord().isRead());
    boolean hasUnpinned = viewModel.currentSelectedConversations().stream().anyMatch(conversation -> !conversation.getThreadRecord().isPinned());
    boolean hasUnmuted  = viewModel.currentSelectedConversations().stream().anyMatch(conversation -> !conversation.getThreadRecord().getRecipient().live().get().isMuted());
    boolean canPin      = viewModel.getPinnedCount() < RemoteConfig.pinnedChatLimit();

    if (mainToolbarViewModel.isInActionMode()) {
      mainToolbarViewModel.setActionModeCount(count);
    }

    List<ActionItem> items = new ArrayList<>();

    Set<Long> selectionIds = viewModel.currentSelectedConversations()
                                      .stream()
                                      .map(conversation -> conversation.getThreadRecord().getThreadId())
                                      .collect(Collectors.toSet());

    if (hasUnread) {
      items.add(new ActionItem(R.drawable.symbol_chat_24, getResources().getQuantityString(R.plurals.ConversationListFragment_read_plural, count), () -> handleMarkAsRead(selectionIds)));
    } else {
      items.add(new ActionItem(R.drawable.symbol_chat_badge_24, getResources().getQuantityString(R.plurals.ConversationListFragment_unread_plural, count), () -> handleMarkAsUnread(selectionIds)));
    }

    if (!isArchived() && hasUnpinned && canPin) {
      items.add(new ActionItem(R.drawable.symbol_pin_24, getResources().getString(R.string.ConversationListFragment_pin), () -> handlePin(viewModel.currentSelectedConversations())));
    } else if (!isArchived() && !hasUnpinned) {
      items.add(new ActionItem(R.drawable.symbol_pin_slash_24, getResources().getString(R.string.ConversationListFragment_unpin), () -> handleUnpin(selectionIds)));
    }

    if (isArchived()) {
      items.add(new ActionItem(R.drawable.symbol_archive_up_24, getResources().getString(R.string.ConversationListFragment_unarchive), () -> handleUnarchive(selectionIds)));
    } else {
      items.add(new ActionItem(R.drawable.symbol_archive_24, getResources().getString(R.string.ConversationListFragment_archive), () -> handleArchive(selectionIds)));
    }

    items.add(new ActionItem(org.signal.core.ui.R.drawable.symbol_trash_24, getResources().getString(R.string.ConversationListFragment_delete), () -> handleDelete(selectionIds)));

    if (hasUnmuted) {
      items.add(new ActionItem(R.drawable.symbol_bell_slash_24, getResources().getString(R.string.ConversationListFragment_mute), () -> handleMute(viewModel.currentSelectedConversations())));
    } else {
      items.add(new ActionItem(R.drawable.symbol_bell_24, getResources().getString(R.string.ConversationListFragment_unmute), () -> handleUnmute(viewModel.currentSelectedConversations())));
    }

    items.add(new ActionItem(org.signal.core.ui.R.drawable.symbol_check_circle_24, getString(R.string.ConversationListFragment_select_all), viewModel::onSelectAllClick));

    if (!isArchived()) {
      items.add(new ActionItem(R.drawable.symbol_folder_add, getString(R.string.ConversationListFragment_add_to_folder), () -> {
        showAddToFolderBottomSheet(viewModel.currentSelectedConversations());
      }));
    }

    bottomActionBar.setItems(items);
  }

  protected Callback requireCallback() {
    return ((Callback) requireActivity());
  }

  protected @DrawableRes int getArchiveIconRes() {
    return R.drawable.symbol_archive_24;
  }

  @Override
  public void onClearFilterClick() {
    pullView.toggle();
    pullViewAppBarLayout.setExpanded(false, true);
  }

  @Override
  public boolean isScrolled() {
    return conversationList != null && conversationList.isScrolled();
  }

  @Override
  public void onChatFolderClicked(@NonNull ChatFolderRecord chatFolder) {
    int oldIndex = -1;
    int newIndex = -1;

    for (int i = 0; i < viewModel.getFolders().size(); i++) {
      if (oldIndex != -1 && newIndex != -1) {
        break;
      }

      ChatFolderMappingModel folder = viewModel.getFolders().get(i);
      if (folder.isSelected()) {
        oldIndex = i;
      }
      if (folder.getChatFolder().getId() == chatFolder.getId()) {
        newIndex = i;
      }
    }

    if (isScrolled()) {
      conversationList.scrollToTop(true);
    }

    if (oldIndex == newIndex) {
      return;
    }

    if (oldIndex < newIndex) {
      smoothScroller.setTargetPosition(Math.min(newIndex + 1, viewModel.getFolders().size()));
    } else {
      smoothScroller.setTargetPosition(Math.max(newIndex - 1, 0));
    }

    if (chatFolderList.getLayoutManager() != null) {
      chatFolderList.getLayoutManager().startSmoothScroll(smoothScroller);
    }

    // The Light list has no item animations to suppress when the folder changes -- it just swaps
    // its contents -- so the old ItemAnimator dance is gone.
    viewModel.select(chatFolder);
  }

  @Override
  public void onEdit(@NonNull ChatFolderRecord chatFolder) {
    startActivity(AppSettingsActivity.createChatFolder(requireContext(), chatFolder.getId(), null));
  }

  @Override
  public void onMuteAll(@NonNull ChatFolderRecord chatFolder) {
    MuteDialog.show(requireContext(), getChildFragmentManager(), getViewLifecycleOwner(), until -> viewModel.onUpdateMute(chatFolder, until));
  }

  @Override
  public void onUnmuteAll(@NonNull ChatFolderRecord chatFolder) {
    viewModel.onUpdateMute(chatFolder, 0);
  }

  @Override
  public void onReadAll(@NonNull ChatFolderRecord chatFolder) {
    if (chatFolder.getFolderType() == ChatFolderRecord.FolderType.ALL) {
      mainToolbarViewModel.markAllMessagesRead();
    } else {
      viewModel.markChatFolderRead(chatFolder);
    }
  }

  @Override
  public void onFolderSettings() {
    startActivity(AppSettingsActivity.chatFolders(requireContext()));
  }

  public void showSearchFilterBottomSheet() {
    SearchFilterBottomSheet.show(
        getParentFragmentManager(),
        activeSearchFilter.getStartDate(),
        activeSearchFilter.getEndDate(),
        activeSearchFilter.getAuthor()
    );
  }

  private void onSearchOpen() {
    chatListBackHandler.setEnabled(true);
  }

  private void onSearchClose() {
    showConversationList();

    activeSearchFilter = SearchFilter.EMPTY;
    mainToolbarViewModel.setHasActiveSearchFilter(false);
    contactSearchViewModel.setSearchFilter(activeSearchFilter);

    chatListBackHandler.setEnabled(false);
  }

  private void onSearchQueryUpdated(@NonNull String query) {
    String trimmed = query.trim();

    contactSearchViewModel.setQuery(trimmed);

    if (!trimmed.isEmpty()) {
      showSearchResults();
    } else {
      showConversationList();
    }
  }

  /**
   * Bridges the Compose conversation list back into this fragment. Replaces
   * {@code ConversationListAdapter.OnConversationClickListener} plus the adapter's
   * {@code setPagingController} hookup -- the paging controller is still Molly's own
   * {@code org.signal.paging} controller, it is just driven by the last visible Compose row now
   * instead of by {@code ConversationListAdapter.getItem}.
   */
  private final class LightConversationListCallback implements LightConversationListView.Callback {
    @Override
    public void onConversationClick(@NonNull Conversation conversation) {
      ConversationListFragment.this.onConversationClick(conversation);
    }

    @Override
    public void onConversationLongClick(@NonNull Conversation conversation, @NonNull Rect bounds) {
      ConversationListFragment.this.onConversationLongClick(conversation, bounds);
    }

    @Override
    public void onShowArchiveClick() {
      ConversationListFragment.this.onShowArchiveClick();
    }

    @Override
    public void onDataNeededAroundIndex(int index) {
      if (viewModel != null) {
        viewModel.getController().onDataNeededAroundIndex(index);
      }
    }
  }

  private final class VoiceNotePlayerViewListener implements VoiceNotePlayerView.Listener {

    @Override
    public void onCloseRequested(@NonNull Uri uri) {
      if (voiceNotePlayerViewStub.resolved()) {
        mediaControllerOwner.getVoiceNoteMediaController().stopPlaybackAndReset(uri);
      }
    }

    @Override
    public void onSpeedChangeRequested(@NonNull Uri uri, float speed) {
      mediaControllerOwner.getVoiceNoteMediaController().setPlaybackSpeed(uri, speed);
    }

    @Override
    public void onPlay(@NonNull Uri uri, long messageId, double position) {
      mediaControllerOwner.getVoiceNoteMediaController().startSinglePlayback(uri, messageId, position);
    }

    @Override
    public void onPause(@NonNull Uri uri) {
      mediaControllerOwner.getVoiceNoteMediaController().pausePlayback(uri);
    }

    @Override
    public void onNavigateToMessage(long threadId, @NonNull RecipientId threadRecipientId, @NonNull RecipientId senderId, long messageSentAt, long messagePositionInThread) {
      MainNavigator.get(requireActivity()).goToConversation(threadRecipientId, threadId, ThreadTable.DistributionTypes.DEFAULT, (int) messagePositionInThread);
    }
  }

  private class ContactSearchClickCallbacks implements ConversationListSearchAdapter.ConversationListSearchClickCallbacks {

    @Override
    public void onThreadClicked(@NonNull View view, @NonNull ContactSearchData.Thread thread, boolean isSelected) {
      onConversationClicked(thread.getThreadWithRecipient());
    }

    @Override
    public boolean onThreadLongClicked(@NonNull View view, @NonNull ContactSearchData.Thread thread) {
      return showConversationContextMenu(new Conversation(thread.getThreadWithRecipient()), view, list, true);
    }

    @Override
    public void onMessageClicked(@NonNull View view, @NonNull ContactSearchData.Message thread, boolean isSelected) {
      ConversationListFragment.this.onMessageClicked(thread.getMessageResult());
    }

    @Override
    public void onGroupWithMembersClicked(@NonNull View view, @NonNull ContactSearchData.GroupWithMembers groupWithMembers, boolean isSelected) {
      onContactClicked(Recipient.resolved(groupWithMembers.getGroupRecord().getRecipientId()));
    }

    @Override
    public void onClearFilterClicked() {
      onClearFilterClick();
    }

    @Override
    public void onStoryClicked(@NonNull View view, @NonNull ContactSearchData.Story story, boolean isSelected) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void onKnownRecipientClicked(@NonNull View view, @NonNull ContactSearchData.KnownRecipient knownRecipient, boolean isSelected) {
      onContactClicked(knownRecipient.getRecipient());
    }

    @Override
    public void onExpandClicked(@NonNull ContactSearchData.Expand expand) {
      contactSearchViewModel.expandSection(expand.getSectionKey());
    }

    @Override
    public void onUnknownRecipientClicked(@NonNull View view, @NonNull ContactSearchData.UnknownRecipient unknownRecipient, boolean isSelected) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void onChatTypeClicked(@NonNull View view, @NonNull ContactSearchData.ChatTypeRow chatTypeRow, boolean isSelected) {
      throw new UnsupportedOperationException();
    }
  }

  private class ChatListBackHandler extends OnBackPressedCallback {

    public ChatListBackHandler(boolean enabled) {
      super(enabled);
    }

    @Override
    public void handleOnBackPressed() {
      closeSearchIfOpen();
    }
  }

  public interface Callback extends Material3OnScrollHelperBinder {
    void updateProxyStatus(@NonNull WebSocketConnectionState state);

    void onMultiSelectStarted();

    void onMultiSelectFinished();
  }

}


