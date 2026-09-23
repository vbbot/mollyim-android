/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.keyboard

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.models.media.Media
import org.signal.core.ui.logging.LoggingFragment
import org.signal.core.ui.permissions.Permissions
import org.signal.core.ui.util.StorageUtil
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.concurrent.addTo
import org.signal.core.util.permissions.PermissionCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.AttachmentKeyboardButton
import org.thoughtcrime.securesms.conversation.ManageContextMenu
import org.thoughtcrime.securesms.conversation.v2.ConversationViewModel
import org.thoughtcrime.securesms.conversation.v2.keyboard.light.LIGHT_ATTACHMENT_ACTIONS
import org.thoughtcrime.securesms.conversation.v2.keyboard.light.LightAttachmentPickerState
import org.thoughtcrime.securesms.conversation.v2.keyboard.light.LightAttachmentPickerView
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * Owns attachment-picker behavior while [LightAttachmentPickerView] supplies the Light presentation.
 * Permission APIs, media loading, recipient policy and fragment result shapes deliberately stay here.
 */
class AttachmentKeyboardFragment : LoggingFragment(R.layout.attachment_keyboard_fragment) {

  companion object {
    const val RESULT_KEY = "AttachmentKeyboardFragmentResult"
    const val MEDIA_RESULT = "Media"
    const val BUTTON_RESULT = "Button"
  }

  private val viewModel: AttachmentKeyboardViewModel by viewModels()

  private lateinit var conversationViewModel: ConversationViewModel
  private lateinit var attachmentPickerView: LightAttachmentPickerView

  private val lifecycleDisposable = LifecycleDisposable()
  private var recentMedia: List<Media> = emptyList()

  @Suppress("ReplaceGetOrSet")
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    lifecycleDisposable.bindTo(viewLifecycleOwner)

    attachmentPickerView = view.findViewById(R.id.attachment_keyboard)
    attachmentPickerView.apply {
      onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() }
      onActionSelected = ::onAttachmentSelectorClicked
      onMediaSelected = ::onAttachmentMediaClicked
      onPermissionsRequested = ::onAttachmentPermissionsRequested
      onManageRequested = ::onDisplayMoreContextMenu
    }
    renderState()

    viewModel.getRecentMedia()
      .subscribeBy {
        recentMedia = it
        renderState()
      }
      .addTo(lifecycleDisposable)

    conversationViewModel = ViewModelProvider(requireParentFragment()).get(ConversationViewModel::class.java)

    val snapshot = conversationViewModel.recipientSnapshot
    if (snapshot != null) {
      updateButtonsAvailable(snapshot)
    }

    conversationViewModel
      .recipient
      .observeOn(AndroidSchedulers.mainThread())
      .subscribeBy {
        updateButtonsAvailable(it)
      }
      .addTo(lifecycleDisposable)
  }

  private fun renderState() {
    attachmentPickerView.submit(
      LightAttachmentPickerState.map(
        actions = LIGHT_ATTACHMENT_ACTIONS,
        media = recentMedia,
        canOnlyReadSelectedMedia = StorageUtil.canOnlyReadSelectedMediaStore(),
        canReadAnyMedia = StorageUtil.canReadAnyFromMediaStore()
      )
    )
  }

  private fun onAttachmentMediaClicked(media: Media) {
    setFragmentResult(RESULT_KEY, bundleOf(MEDIA_RESULT to media))
  }

  private fun onAttachmentSelectorClicked(button: AttachmentKeyboardButton) {
    setFragmentResult(RESULT_KEY, bundleOf(BUTTON_RESULT to button))
  }

  private fun onAttachmentPermissionsRequested() {
    Permissions.with(requireParentFragment())
      .request(*PermissionCompat.forImagesAndVideos())
      .ifNecessary()
      .onAnyResult { viewModel.refreshRecentMedia() }
      .withPermanentDenialDialog(getString(R.string.AttachmentManager_signal_requires_the_external_storage_permission_in_order_to_attach_photos_videos_or_audio), null, R.string.AttachmentManager_signal_allow_storage, R.string.AttachmentManager_signal_to_show_photos, true, parentFragmentManager)
      .onSomeDenied {
        val deniedPermissions = PermissionCompat.getRequiredPermissionsForDenial()
        if (it.containsAll(deniedPermissions.toList())) {
          Toast.makeText(requireContext(), R.string.AttachmentManager_signal_needs_storage_access, Toast.LENGTH_LONG).show()
        }
      }
      .execute()
  }

  private fun onDisplayMoreContextMenu(showAtStart: Boolean) {
    ManageContextMenu.show(
      context = requireContext(),
      anchorView = attachmentPickerView,
      showAbove = true,
      showAtStart = showAtStart,
      onSelectMore = { selectMorePhotos() },
      onSettings = { requireContext().startActivity(Permissions.getApplicationSettingsIntent(requireContext())) }
    )
  }

  private fun selectMorePhotos() {
    Permissions.with(requireParentFragment())
      .request(*PermissionCompat.forImagesAndVideos())
      .onAnyResult { viewModel.refreshRecentMedia() }
      .execute()
  }

  private fun updateButtonsAvailable(recipient: Recipient) {
    // MOLLY: No-op
  }
}
