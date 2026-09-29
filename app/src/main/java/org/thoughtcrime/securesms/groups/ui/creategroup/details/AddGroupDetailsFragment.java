package org.thoughtcrime.securesms.groups.ui.creategroup.details;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import org.signal.core.models.media.Media;
import org.signal.core.ui.logging.LoggingFragment;
import org.signal.core.util.bitmaps.BitmapUtil;
import org.signal.glide.decryptableuri.DecryptableUri;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.avatar.picker.AvatarPickerFragment;
import org.thoughtcrime.securesms.components.settings.app.privacy.expire.ExpireTimerSettingsFragment;
import org.thoughtcrime.securesms.conversation.ConversationIntents;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.ui.disappearingmessages.RecipientDisappearingMessagesActivity;
import org.thoughtcrime.securesms.util.ExpirationUtil;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Controller for group creation details.
 *
 * Presentation is delegated to {@link LightAddGroupDetailsView}; this class deliberately retains
 * every navigation, media, timer, repository and activity-result responsibility it had before the
 * Light conversion.
 */
public class AddGroupDetailsFragment extends LoggingFragment {

  private static final short REQUEST_DISAPPEARING_TIMER = 28621;

  private Callback                     callback;
  private AddGroupDetailsViewModel     viewModel;
  private LightAddGroupDetailsView     lightView;

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);

    if (context instanceof Callback) {
      callback = (Callback) context;
    } else {
      throw new ClassCastException("Parent context should implement AddGroupDetailsFragment.Callback");
    }
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater,
                                     @Nullable ViewGroup container,
                                     @Nullable Bundle savedInstanceState)
  {
    return inflater.inflate(R.layout.add_group_details_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    lightView = view.findViewById(R.id.group_details_light_view);
    initializeViewModel();

    lightView.setCallbacks(new LightAddGroupDetailsView.Callbacks() {
      @Override
      public void onBack() {
        callback.onNavigationButtonPressed();
      }

      @Override
      public void onAvatarClick() {
        showAvatarPicker();
      }

      @Override
      public void onTimerClick() {
        startActivityForResult(
            RecipientDisappearingMessagesActivity.forCreateGroup(requireContext(), viewModel.getDisappearingMessagesTimer().getValue()),
            REQUEST_DISAPPEARING_TIMER
        );
      }

      @Override
      public void onNameChanged(@NonNull String name) {
        viewModel.setName(name);
      }

      @Override
      public void onCreateClick() {
        handleCreateClicked();
      }

      @Override
      public void onRemoveMemberConfirmed(@NonNull Recipient recipient) {
        viewModel.delete(recipient.getId());
      }

      @Override
      public void onOpenDuplicateConfirmed(@NonNull Recipient recipient) {
        navigateToConversation(recipient.getId());
      }
    });

    viewModel.getName().observe(getViewLifecycleOwner(), lightView::setGroupName);
    viewModel.getMembers().observe(getViewLifecycleOwner(), lightView::setMembers);
    viewModel.getCanSubmitForm().observe(getViewLifecycleOwner(), lightView::setCanSubmit);
    viewModel.getIsCreating().observe(getViewLifecycleOwner(), lightView::setBusy);
    viewModel.getIsMms().observe(getViewLifecycleOwner(), lightView::setMms);
    viewModel.getAvatar().observe(getViewLifecycleOwner(), lightView::setAvatar);
    viewModel.getSameGroups().observe(getViewLifecycleOwner(), lightView::setDuplicateGroups);
    viewModel.getDisappearingMessagesTimer().observe(getViewLifecycleOwner(), timer -> {
      lightView.setTimerValue(ExpirationUtil.getExpirationDisplayValue(requireContext(), timer));
    });

    getParentFragmentManager().setFragmentResultListener(
        AvatarPickerFragment.REQUEST_KEY_SELECT_AVATAR,
        getViewLifecycleOwner(),
        (key, bundle) -> handleMediaResult(bundle)
    );
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == REQUEST_DISAPPEARING_TIMER && resultCode == Activity.RESULT_OK && data != null) {
      viewModel.setDisappearingMessageTimer(
          data.getIntExtra(
              ExpireTimerSettingsFragment.FOR_RESULT_VALUE,
              SignalStore.settings().getUniversalExpireTimer()
          )
      );
    } else {
      super.onActivityResult(requestCode, resultCode, data);
    }
  }

  private void handleMediaResult(@NonNull Bundle data) {
    if (data.getBoolean(AvatarPickerFragment.SELECT_AVATAR_CLEAR)) {
      viewModel.setAvatarMedia(null);
      viewModel.setAvatar(null);
      return;
    }

    final Media          result         = data.getParcelable(AvatarPickerFragment.SELECT_AVATAR_MEDIA);
    final DecryptableUri decryptableUri = new DecryptableUri(Objects.requireNonNull(result).getUri());

    viewModel.setAvatarMedia(result);

    Glide.with(this)
         .asBitmap()
         .load(decryptableUri)
         .skipMemoryCache(true)
         .diskCacheStrategy(DiskCacheStrategy.NONE)
         .centerCrop()
         .override(AvatarHelper.AVATAR_DIMENSIONS, AvatarHelper.AVATAR_DIMENSIONS)
         .into(new CustomTarget<Bitmap>() {
           @Override
           public void onResourceReady(@NonNull Bitmap resource, Transition<? super Bitmap> transition) {
             viewModel.setAvatar(Objects.requireNonNull(BitmapUtil.toByteArray(resource)));
           }

           @Override
           public void onLoadCleared(@Nullable android.graphics.drawable.Drawable placeholder) {
           }
         });
  }

  private void initializeViewModel() {
    AddGroupDetailsFragmentArgs      args       = AddGroupDetailsFragmentArgs.fromBundle(requireArguments());
    AddGroupDetailsRepository        repository = new AddGroupDetailsRepository(requireContext());
    AddGroupDetailsViewModel.Factory factory    = new AddGroupDetailsViewModel.Factory(Arrays.asList(args.getRecipientIds()), repository);

    viewModel = new ViewModelProvider(this, factory).get(AddGroupDetailsViewModel.class);
    viewModel.getGroupCreateResult().observe(getViewLifecycleOwner(), this::handleGroupCreateResult);
  }

  private void handleCreateClicked() {
    if (Boolean.TRUE.equals(viewModel.getCanSubmitForm().getValue())) {
      viewModel.create();
    }
  }

  private void handleGroupCreateResult(@NonNull GroupCreateResult result) {
    result.consume(this::handleGroupCreateResultSuccess, this::handleGroupCreateResultError);
  }

  private void handleGroupCreateResultSuccess(@NonNull GroupCreateResult.Success success) {
    callback.onGroupCreated(success.getGroupRecipient().getId(), success.getThreadId(), success.getInvitedMembers());
  }

  private void handleGroupCreateResultError(@NonNull GroupCreateResult.Error error) {
    switch (error.getErrorType()) {
      case ERROR_IO:
      case ERROR_BUSY:
        lightView.showError(getString(R.string.AddGroupDetailsFragment__try_again_later));
        break;
      case ERROR_FAILED:
        lightView.showError(getString(R.string.AddGroupDetailsFragment__group_creation_failed));
        break;
      case ERROR_INVALID_NAME:
        lightView.showError(getString(R.string.AddGroupDetailsFragment__this_field_is_required));
        break;
      default:
        throw new IllegalStateException("Unexpected error: " + error.getErrorType().name());
    }
  }

  private void navigateToConversation(@NonNull RecipientId groupRecipientId) {
    ConversationIntents.createBuilder(requireContext(), groupRecipientId, -1L)
                       .subscribe(builder -> {
                         startActivity(builder.build());
                         requireActivity().finish();
                       });
  }

  private void showAvatarPicker() {
    Media media = viewModel.getAvatarMedia();

    SafeNavigation.safeNavigate(
        Navigation.findNavController(requireView()),
        AddGroupDetailsFragmentDirections
            .actionAddGroupDetailsFragmentToAvatarPicker(null, media)
            .setIsNewGroup(true)
    );
  }

  public interface Callback {
    void onGroupCreated(@NonNull RecipientId recipientId, long threadId, @NonNull List<Recipient> invitedMembers);

    void onNavigationButtonPressed();
  }
}
