package org.thoughtcrime.securesms.profiles.edit;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import org.signal.core.models.media.Media;
import org.signal.core.ui.logging.LoggingFragment;
import org.signal.core.util.StreamUtil;
import org.signal.core.util.concurrent.SimpleTask;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.avatar.picker.AvatarPickerFragment;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.groups.GroupId;
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.profiles.ProfileName;
import org.thoughtcrime.securesms.profiles.edit.pnp.WhoCanFindMeByPhoneNumberFragment;
import org.thoughtcrime.securesms.profiles.manage.EditProfileNameFragment;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.RemoteConfig;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.navigation.SafeNavigation;

import java.io.IOException;
import java.io.InputStream;

import static org.thoughtcrime.securesms.profiles.edit.CreateProfileActivity.EXCLUDE_SYSTEM;
import static org.thoughtcrime.securesms.profiles.edit.CreateProfileActivity.GROUP_ID;
import static org.thoughtcrime.securesms.profiles.edit.CreateProfileActivity.IS_DESCRIPTION_FOCUSED;
import static org.thoughtcrime.securesms.profiles.edit.CreateProfileActivity.NEXT_BUTTON_TEXT;
import static org.thoughtcrime.securesms.profiles.edit.CreateProfileActivity.NEXT_INTENT;
import static org.thoughtcrime.securesms.profiles.edit.CreateProfileActivity.SHOW_TOOLBAR;

/**
 * Used for profile creation during registration, and for editing a group's name, description and
 * photo. The UI is the Compose {@code CreateProfileScreen}; this owns the view model, the navigation
 * and the upload.
 */
public class CreateProfileFragment extends LoggingFragment implements CreateProfileCallbacks {

  private static final String TAG                    = Log.tag(CreateProfileFragment.class);
  private static final int    MAX_DESCRIPTION_GLYPHS = 480;
  private static final int    MAX_DESCRIPTION_BYTES  = 8192;

  private Intent  nextIntent;
  private boolean focusedDescriptionOpened;

  private EditProfileViewModel  viewModel;
  private CreateProfileState    state;
  private OnBackPressedCallback closeEditorOnBack;

  private Controller controller;

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);

    if (context instanceof Controller) {
      controller = (Controller) context;
    } else {
      throw new IllegalStateException("Context must subclass Controller");
    }
  }

  @Override
  public @Nullable View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    Bundle  arguments = requireArguments();
    GroupId groupId   = GroupId.parseNullableOrThrow(arguments.getString(GROUP_ID, null));

    initializeViewModel(arguments.getBoolean(EXCLUDE_SYSTEM, false), groupId, savedInstanceState != null);

    state = new CreateProfileState();

    return CreateProfileContent.create(this, createConfig(arguments, groupId), state, this);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    Bundle  arguments = requireArguments();
    GroupId groupId   = GroupId.parseNullableOrThrow(arguments.getString(GROUP_ID, null));

    nextIntent = arguments.getParcelable(NEXT_INTENT);

    // The editor is a state of this screen, not a destination: system back discards it, as its BACK
    // icon does. Registered after the activity's callback, so it is asked first while it is enabled.
    closeEditorOnBack = new OnBackPressedCallback(false) {
      @Override
      public void handleOnBackPressed() {
        onEditorBack();
      }
    };
    requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), closeEditorOnBack);

    initializeProfileName();
    initializeProfileAvatar();
    initializeUpload();

    if (groupId == null) {
      getParentFragmentManager().setFragmentResultListener(WhoCanFindMeByPhoneNumberFragment.REQUEST_KEY, getViewLifecycleOwner(), (requestKey, result) -> {
        if (WhoCanFindMeByPhoneNumberFragment.REQUEST_KEY.equals(requestKey)) {
          presentWhoCanFindMeDescription(SignalStore.phoneNumberPrivacy().getPhoneNumberDiscoverabilityMode());
        }
      });

      presentWhoCanFindMeDescription(SignalStore.phoneNumberPrivacy().getPhoneNumberDiscoverabilityMode());
    } else if (groupId.isV2() && arguments.getBoolean(IS_DESCRIPTION_FOCUSED) && savedInstanceState == null && !focusedDescriptionOpened) {
      // Reached from "edit description": open straight into the description, as the old screen focused
      // it. Once only -- not again when the view comes back from the avatar picker.
      focusedDescriptionOpened = true;
      openEditor(CreateProfileField.FAMILY_NAME);
    }

    getParentFragmentManager().setFragmentResultListener(AvatarPickerFragment.REQUEST_KEY_SELECT_AVATAR, getViewLifecycleOwner(), (key, bundle) -> {
      if (bundle.getBoolean(AvatarPickerFragment.SELECT_AVATAR_CLEAR)) {
        viewModel.setAvatarMedia(null);
        viewModel.setAvatar(null);
      } else {
        Media media = bundle.getParcelable(AvatarPickerFragment.SELECT_AVATAR_MEDIA);
        handleMediaFromResult(media);
      }
    });
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    closeEditorOnBack = null;
  }

  private static @NonNull CreateProfileConfig createConfig(@NonNull Bundle arguments, @Nullable GroupId groupId) {
    boolean isGroup  = groupId != null;
    boolean showBack = arguments.getBoolean(SHOW_TOOLBAR, true);
    int     title    = isGroup ? R.string.EditProfileFragment__edit_group : R.string.CreateProfileActivity__profile;
    int     next     = arguments.getInt(NEXT_BUTTON_TEXT, R.string.CreateProfileActivity_next);

    if (isGroup) {
      return new CreateProfileConfig(true,
                                     title,
                                     showBack,
                                     next,
                                     groupId.isV2(),
                                     groupId.isMms(),
                                     new CreateProfileFieldLimit(RemoteConfig.getMaxGroupNameGraphemeLength()),
                                     new CreateProfileFieldLimit(MAX_DESCRIPTION_GLYPHS, MAX_DESCRIPTION_BYTES));
    } else {
      CreateProfileFieldLimit nameLimit = new CreateProfileFieldLimit(EditProfileNameFragment.NAME_MAX_GLYPHS, ProfileName.MAX_PART_LENGTH);

      return new CreateProfileConfig(false,
                                     title,
                                     showBack,
                                     next,
                                     true,
                                     false,
                                     nameLimit,
                                     nameLimit);
    }
  }

  private void handleMediaFromResult(@NonNull Media media) {
    SimpleTask.run(() -> {
      try {
        InputStream stream = AppDependencies.getBlobs().getStream(requireContext(), media.getUri());

        return StreamUtil.readFully(stream);
      } catch (IOException ioException) {
        Log.w(TAG, ioException);
        return null;
      }
    },
    (avatarBytes) -> {
      if (avatarBytes != null) {
        viewModel.setAvatarMedia(media);
        viewModel.setAvatar(avatarBytes);
      } else {
        Toast.makeText(requireActivity(), R.string.CreateProfileActivity_error_setting_profile_photo, Toast.LENGTH_LONG).show();
      }
    });
  }

  private void initializeViewModel(boolean excludeSystem, @Nullable GroupId groupId, boolean hasSavedInstanceState) {
    EditProfileRepository repository;

    if (groupId != null) {
      repository = new EditGroupProfileRepository(requireContext(), groupId);
    } else {
      repository = new EditSelfProfileRepository(requireContext(), excludeSystem);
    }

    EditProfileViewModel.Factory factory = new EditProfileViewModel.Factory(repository, hasSavedInstanceState, groupId);

    viewModel = new ViewModelProvider(requireActivity(), factory).get(EditProfileViewModel.class);
  }

  private void initializeProfileName() {
    viewModel.isFormValid().observe(getViewLifecycleOwner(), isValid -> state.setFormValid(isValid));
    viewModel.givenName().observe(getViewLifecycleOwner(), givenName -> state.setGivenName(givenName != null ? givenName : ""));
    viewModel.familyName().observe(getViewLifecycleOwner(), familyName -> state.setFamilyName(familyName != null ? familyName : ""));
  }

  private void initializeProfileAvatar() {
    viewModel.avatar().observe(getViewLifecycleOwner(), bytes -> state.setAvatar(bytes));
  }

  private void initializeUpload() {
    viewModel.getUploadResult().observe(getViewLifecycleOwner(), uploadResult -> {
      if (uploadResult == EditProfileRepository.UploadResult.SUCCESS) {
        handleFinished();
      } else {
        Toast.makeText(requireContext(), R.string.CreateProfileActivity_problem_setting_profile, Toast.LENGTH_LONG).show();
        state.setUploading(false);
      }
    });
  }

  private void presentWhoCanFindMeDescription(PhoneNumberPrivacyValues.PhoneNumberDiscoverabilityMode phoneNumberListingMode) {
    switch (phoneNumberListingMode) {
      case DISCOVERABLE:
      case UNDECIDED:
        state.setWhoCanFindMe(getString(R.string.PhoneNumberPrivacy_everyone));
        break;
      case NOT_DISCOVERABLE:
        state.setWhoCanFindMe(getString(R.string.PhoneNumberPrivacy_nobody));
        break;
    }
  }

  private void openEditor(@NonNull CreateProfileField field) {
    state.setEditing(field);
    closeEditorOnBack.setEnabled(true);
  }

  private void closeEditor() {
    state.setEditing(null);
    if (closeEditorOnBack != null) {
      closeEditorOnBack.setEnabled(false);
    }
  }

  /**
   * Leaves the screen the way the upstream reveal animation's end did -- keyboard down, next screen
   * started, then the activity finished -- without the ultramarine flash in front of it.
   */
  private void handleFinished() {
    ViewUtil.hideKeyboard(requireContext(), requireView());
    if (nextIntent != null && getActivity() != null) {
      startActivity(nextIntent);
    }

    controller.onProfileNameUploadCompleted();
  }

  @Override
  public void onBackClicked() {
    requireActivity().finish();
  }

  @Override
  public void onLearnMoreClicked() {
    CommunicationActions.openBrowserLink(requireContext(), getString(R.string.EditProfileFragment__support_link));
  }

  @Override
  public void onAvatarClicked() {
    if (viewModel.isGroup()) {
      SafeNavigation.safeNavigate(NavHostFragment.findNavController(this), CreateProfileFragmentDirections.actionCreateProfileFragmentToAvatarPicker(viewModel.getGroupId(), viewModel.getAvatarMedia()));
    } else {
      SafeNavigation.safeNavigate(NavHostFragment.findNavController(this), CreateProfileFragmentDirections.actionCreateProfileFragmentToAvatarPicker(null, null));
    }
  }

  @Override
  public void onFieldClicked(@NonNull CreateProfileField field) {
    openEditor(field);
  }

  @Override
  public void onWhoCanFindMeClicked() {
    SafeNavigation.safeNavigate(NavHostFragment.findNavController(this), CreateProfileFragmentDirections.actionCreateProfileFragmentToPhoneNumberPrivacy());
  }

  @Override
  public void onEditorBack() {
    closeEditor();
  }

  @Override
  public void onEditorDone(@NonNull CreateProfileField field, @NonNull String text) {
    if (field == CreateProfileField.GIVEN_NAME) {
      viewModel.setGivenName(text);
    } else {
      viewModel.setFamilyName(text);
    }

    closeEditor();
  }

  @Override
  public void onNextClicked() {
    if (state.getUploading()) {
      return;
    }

    state.setUploading(true);
    viewModel.submitProfile();
  }

  public interface Controller {
    void onProfileNameUploadCompleted();
  }
}
