/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.links.create

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ShareCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightScrollBarPosition
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import io.reactivex.rxjava3.kotlin.subscribeBy
import org.signal.core.ui.compose.ComposeBottomSheetDialogFragment
import org.signal.core.util.Util
import org.signal.core.util.concurrent.LifecycleDisposable
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.calls.links.CallLinkActionRow
import org.thoughtcrime.securesms.calls.links.CallLinkApprovalRow
import org.thoughtcrime.securesms.calls.links.CallLinkFeedbackBanner
import org.thoughtcrime.securesms.calls.links.CallLinks
import org.thoughtcrime.securesms.calls.links.EditCallLinkNameDialogFragment
import org.thoughtcrime.securesms.calls.links.SignalCallRow
import org.thoughtcrime.securesms.calls.links.SignalCallRowState
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.service.webrtc.links.CreateCallLinkResult
import org.thoughtcrime.securesms.service.webrtc.links.UpdateCallLinkResult
import org.thoughtcrime.securesms.sharing.v2.ShareActivity
import org.thoughtcrime.securesms.util.CommunicationActions

internal const val CREATE_CALL_LINK_SCREEN_TAG = "call-link:create"
internal const val CREATE_CALL_LINK_NAME_TAG = "call-link:create:name"
internal const val CREATE_CALL_LINK_APPROVAL_TAG = "call-link:create:approval"
internal const val CREATE_CALL_LINK_SHARE_SIGNAL_TAG = "call-link:create:share-signal"
internal const val CREATE_CALL_LINK_COPY_TAG = "call-link:create:copy"
internal const val CREATE_CALL_LINK_SHARE_TAG = "call-link:create:share"

/** Pure presentation state; constructing it never creates credentials or touches RingRTC. */
@Immutable
data class CreateCallLinkLightState(
  val call: SignalCallRowState,
  val approvalRequired: Boolean,
  val approvalChangeInFlight: Boolean = false,
  val alreadyInCall: Boolean = false
)

interface CreateCallLinkCallbacks {
  fun onJoinClicked() = Unit
  fun onNameClicked() = Unit
  fun onApprovalChanged(required: Boolean) = Unit
  fun onShareViaSignalClicked() = Unit
  fun onCopyClicked() = Unit
  fun onShareClicked() = Unit
  fun onDoneClicked() = Unit

  object Empty : CreateCallLinkCallbacks
}

/** Bottom sheet for creating call links. */
class CreateCallLinkBottomSheetDialogFragment : ComposeBottomSheetDialogFragment() {

  companion object {
    private val TAG = Log.tag(CreateCallLinkBottomSheetDialogFragment::class.java)
  }

  private val viewModel: CreateCallLinkViewModel by viewModels()
  private val lifecycleDisposable = LifecycleDisposable()

  private val callbacks = object : CreateCallLinkCallbacks {
    override fun onJoinClicked() = this@CreateCallLinkBottomSheetDialogFragment.onJoinClicked()
    override fun onNameClicked() = onAddACallNameClicked()
    override fun onApprovalChanged(required: Boolean) = setApproveAllMembers(required)
    override fun onShareViaSignalClicked() = this@CreateCallLinkBottomSheetDialogFragment.onShareViaSignalClicked()
    override fun onCopyClicked() = onCopyLinkClicked()
    override fun onShareClicked() = onShareLinkClicked()
    override fun onDoneClicked() = this@CreateCallLinkBottomSheetDialogFragment.onDoneClicked()
  }

  override val peekHeightPercentage: Float = 1f

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    lifecycleDisposable.bindTo(viewLifecycleOwner)
    parentFragmentManager.setFragmentResultListener(EditCallLinkNameDialogFragment.RESULT_KEY, viewLifecycleOwner) { resultKey, bundle ->
      if (bundle.containsKey(resultKey)) {
        setCallName(bundle.getString(resultKey)!!)
      }
    }
  }

  @Composable
  override fun SheetContent() {
    val callLink by viewModel.callLink
    val alreadyInCall by viewModel.showAlreadyInACall.collectAsStateWithLifecycle(false)
    val approvalChangeInFlight by viewModel.isLoadingAdminApprovalChange.collectAsStateWithLifecycle(false)

    val state = CreateCallLinkLightState(
      call = SignalCallRowState(
        name = callLink.state.name,
        url = CallLinks.url(viewModel.linkKeyBytes)
      ),
      approvalRequired = callLink.state.restrictions == org.signal.ringrtc.CallLinkState.Restrictions.ADMIN_APPROVAL,
      approvalChangeInFlight = approvalChangeInFlight,
      alreadyInCall = alreadyInCall
    )

    MollyLightTheme {
      CreateCallLinkBottomSheetContent(state = state, callbacks = callbacks)
    }
  }

  private fun setCallName(callName: String) {
    // CreateCallLinkViewModel commits the draft before applying this mutation.
    lifecycleDisposable += viewModel.setCallName(callName).subscribeBy(
      onSuccess = {
        if (it !is UpdateCallLinkResult.Update) {
          Log.w(TAG, "Failed to update call link name")
          toastFailure()
        }
      },
      onError = this::handleError
    )
  }

  private fun setApproveAllMembers(approveAllMembers: Boolean) {
    // CreateCallLinkViewModel commits the draft before applying this mutation and owns loading state.
    lifecycleDisposable += viewModel.setApproveAllMembers(approveAllMembers).subscribeBy(
      onSuccess = {
        if (it !is UpdateCallLinkResult.Update) {
          Log.w(TAG, "Failed to update call link restrictions")
          toastFailure()
        }
      },
      onError = this::handleError
    )
  }

  private fun onAddACallNameClicked() {
    val snapshot = viewModel.callLink.value
    EditCallLinkNameDialogFragment().apply {
      arguments = bundleOf(EditCallLinkNameDialogFragment.ARG_NAME to snapshot.state.name)
    }.show(parentFragmentManager, null)
  }

  private fun onJoinClicked() {
    lifecycleDisposable += viewModel.commitCallLink().subscribeBy(
      onSuccess = {
        when (it) {
          is EnsureCallLinkCreatedResult.Success -> {
            CommunicationActions.startVideoCall(requireActivity(), it.recipient) {
              viewModel.setShowAlreadyInACall(true)
            }
            dismissAllowingStateLoss()
          }

          is EnsureCallLinkCreatedResult.Failure -> handleCreateCallLinkFailure(it.failure)
        }
      },
      onError = this::handleError
    )
  }

  private fun onDoneClicked() {
    lifecycleDisposable += viewModel.commitCallLink().subscribeBy(
      onSuccess = {
        when (it) {
          is EnsureCallLinkCreatedResult.Success -> dismissAllowingStateLoss()
          is EnsureCallLinkCreatedResult.Failure -> handleCreateCallLinkFailure(it.failure)
        }
      },
      onError = this::handleError
    )
  }

  private fun onShareViaSignalClicked() {
    lifecycleDisposable += viewModel.commitCallLink().subscribeBy(
      onSuccess = {
        when (it) {
          is EnsureCallLinkCreatedResult.Success -> {
            startActivity(
              ShareActivity.sendSimpleText(
                requireContext(),
                getString(R.string.CreateCallLink__use_this_link_to_join_a_signal_call, CallLinks.url(viewModel.linkKeyBytes))
              )
            )
          }

          is EnsureCallLinkCreatedResult.Failure -> handleCreateCallLinkFailure(it.failure)
        }
      },
      onError = this::handleError
    )
  }

  private fun onCopyLinkClicked() {
    lifecycleDisposable += viewModel.commitCallLink().subscribeBy(
      onSuccess = {
        when (it) {
          is EnsureCallLinkCreatedResult.Success -> {
            Util.copyToClipboard(requireContext(), CallLinks.url(viewModel.linkKeyBytes))
            Toast.makeText(requireContext(), R.string.CreateCallLinkBottomSheetDialogFragment__copied_to_clipboard, Toast.LENGTH_LONG).show()
          }

          is EnsureCallLinkCreatedResult.Failure -> handleCreateCallLinkFailure(it.failure)
        }
      },
      onError = this::handleError
    )
  }

  private fun onShareLinkClicked() {
    lifecycleDisposable += viewModel.commitCallLink().subscribeBy(
      onSuccess = {
        when (it) {
          is EnsureCallLinkCreatedResult.Success -> {
            val mimeType = Intent.normalizeMimeType("text/plain")
            val shareIntent = ShareCompat.IntentBuilder(requireContext())
              .setText(CallLinks.url(viewModel.linkKeyBytes))
              .setType(mimeType)
              .createChooserIntent()

            try {
              startActivity(shareIntent)
            } catch (_: ActivityNotFoundException) {
              Toast.makeText(requireContext(), R.string.CreateCallLinkBottomSheetDialogFragment__failed_to_open_share_sheet, Toast.LENGTH_LONG).show()
            }
          }

          is EnsureCallLinkCreatedResult.Failure -> handleCreateCallLinkFailure(it.failure)
        }
      },
      onError = this::handleError
    )
  }

  private fun handleCreateCallLinkFailure(failure: CreateCallLinkResult.Failure) {
    Log.w(TAG, "Failed to create call link: $failure")
    toastFailure()
  }

  private fun handleError(throwable: Throwable) {
    Log.w(TAG, "Failed to create call link.", throwable)
    toastFailure()
  }

  private fun toastFailure() {
    Toast.makeText(requireContext(), R.string.CallLinkDetailsFragment__couldnt_save_changes, Toast.LENGTH_LONG).show()
  }
}

/** Pure Light create surface. Side effects are represented only by [callbacks]. */
@Composable
fun CreateCallLinkBottomSheetContent(
  state: CreateCallLinkLightState,
  callbacks: CreateCallLinkCallbacks,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(LightThemeTokens.colors.background)
      .testTag(CREATE_CALL_LINK_SCREEN_TAG)
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      LightTopBar(
        center = LightTopBarCenter.Text(
          stringResource(R.string.CreateCallLinkBottomSheetDialogFragment__create_call_link)
        ),
        rightButton = LightBarButton.Text(
          text = stringResource(R.string.CreateCallLinkBottomSheetDialogFragment__done),
          onClick = callbacks::onDoneClicked
        )
      )

      LightScrollView(
        modifier = Modifier.weight(1f),
        scrollBarPosition = LightScrollBarPosition.Inside
      ) {
        SignalCallRow(
          state = state.call,
          onJoinClicked = callbacks::onJoinClicked
        )

        CallLinkActionRow(
          label = stringResource(
            if (state.call.name.isEmpty()) {
              R.string.CreateCallLinkBottomSheetDialogFragment__add_call_name
            } else {
              R.string.CreateCallLinkBottomSheetDialogFragment__edit_call_name
            }
          ),
          onClick = callbacks::onNameClicked,
          modifier = Modifier.testTag(CREATE_CALL_LINK_NAME_TAG)
        )

        CallLinkApprovalRow(
          label = stringResource(R.string.CreateCallLinkBottomSheetDialogFragment__require_admin_approval),
          checked = state.approvalRequired,
          loading = state.approvalChangeInFlight,
          onCheckedChange = callbacks::onApprovalChanged,
          modifier = Modifier.testTag(CREATE_CALL_LINK_APPROVAL_TAG)
        )

        CallLinkActionRow(
          label = stringResource(R.string.CreateCallLinkBottomSheetDialogFragment__share_link_via_signal),
          onClick = callbacks::onShareViaSignalClicked,
          modifier = Modifier.testTag(CREATE_CALL_LINK_SHARE_SIGNAL_TAG)
        )
        CallLinkActionRow(
          label = stringResource(R.string.CreateCallLinkBottomSheetDialogFragment__copy_link),
          onClick = callbacks::onCopyClicked,
          modifier = Modifier.testTag(CREATE_CALL_LINK_COPY_TAG)
        )
        CallLinkActionRow(
          label = stringResource(R.string.CreateCallLinkBottomSheetDialogFragment__share_link),
          onClick = callbacks::onShareClicked,
          modifier = Modifier.testTag(CREATE_CALL_LINK_SHARE_TAG)
        )
      }
    }

    CallLinkFeedbackBanner(
      message = if (state.alreadyInCall) {
        stringResource(R.string.CommunicationActions__you_are_already_in_a_call)
      } else {
        null
      },
      modifier = Modifier.align(Alignment.BottomCenter)
    )
  }
}

@Preview(widthDp = 360, heightDp = 413, showBackground = true)
@Composable
private fun CreateCallLinkBottomSheetContentPreview() {
  MollyLightTheme {
    CreateCallLinkBottomSheetContent(
      state = CreateCallLinkLightState(
        call = SignalCallRowState(
          name = "Test Call",
          url = "https://signal.link/call/#key=example"
        ),
        approvalRequired = true
      ),
      callbacks = CreateCallLinkCallbacks.Empty
    )
  }
}
