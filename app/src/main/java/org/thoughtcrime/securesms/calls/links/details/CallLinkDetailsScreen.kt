/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.links.details

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ShareCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollBarPosition
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.launch
import org.signal.core.ui.rememberIsSplitPane
import org.signal.core.util.Util
import org.signal.ringrtc.CallLinkState.Restrictions
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.calls.links.CallLinkActionRow
import org.thoughtcrime.securesms.calls.links.CallLinkApprovalRow
import org.thoughtcrime.securesms.calls.links.CallLinkFeedbackBanner
import org.thoughtcrime.securesms.calls.links.CallLinks
import org.thoughtcrime.securesms.calls.links.SignalCallRow
import org.thoughtcrime.securesms.calls.links.SignalCallRowState
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.main.MainNavigationCallDetailRouter
import org.thoughtcrime.securesms.main.MainNavigationDetailLocation
import org.thoughtcrime.securesms.main.MainNavigationViewModel
import org.thoughtcrime.securesms.service.webrtc.links.CallLinkRoomId
import org.thoughtcrime.securesms.sharing.v2.ShareActivity
import org.thoughtcrime.securesms.util.CommunicationActions

internal const val CALL_LINK_DETAILS_SCREEN_TAG = "call-link:details"
internal const val CALL_LINK_DETAILS_NAME_TAG = "call-link:details:name"
internal const val CALL_LINK_DETAILS_APPROVAL_TAG = "call-link:details:approval"
internal const val CALL_LINK_DETAILS_SHARE_SIGNAL_TAG = "call-link:details:share-signal"
internal const val CALL_LINK_DETAILS_COPY_TAG = "call-link:details:copy"
internal const val CALL_LINK_DETAILS_SHARE_TAG = "call-link:details:share"
internal const val CALL_LINK_DETAILS_DELETE_TAG = "call-link:details:delete"
internal const val CALL_LINK_DETAILS_CONFIRMATION_TAG = "call-link:details:delete-confirmation"

@Immutable
data class CallLinkDetailsLightState(
  val call: SignalCallRowState? = null,
  val canModify: Boolean = false,
  val approvalRequired: Boolean = false,
  val approvalChangeInFlight: Boolean = false,
  val showRevocationConfirmation: Boolean = false,
  val alreadyInCall: Boolean = false,
  val failure: CallLinkDetailsFailure? = null
)

enum class CallLinkDetailsFailure {
  COULD_NOT_DELETE,
  COULD_NOT_SAVE,
  COULD_NOT_UPDATE_APPROVAL
}

@Composable
fun CallLinkDetailsScreen(
  roomId: CallLinkRoomId,
  viewModel: CallLinkDetailsViewModel = viewModel {
    CallLinkDetailsViewModel(roomId)
  },
  router: MainNavigationCallDetailRouter = viewModel<MainNavigationViewModel>(viewModelStoreOwner = LocalActivity.current as ComponentActivity) {
    error("Should already be created.")
  }
) {
  val activity = LocalActivity.current as FragmentActivity
  val callback = remember(activity, viewModel, router) {
    DefaultCallLinkDetailsCallback(
      activity = activity,
      viewModel = viewModel,
      router = router
    )
  }

  val state by viewModel.state.collectAsStateWithLifecycle(activity)
  val alreadyInCall by viewModel.showAlreadyInACall.collectAsStateWithLifecycle(initialValue = false, lifecycleOwner = activity)
  val callLink = state.callLink

  val lightState = CallLinkDetailsLightState(
    call = callLink?.let {
      SignalCallRowState(
        name = it.state.name,
        url = it.credentials?.let { credentials -> CallLinks.url(credentials.linkKeyBytes) }.orEmpty(),
        isJoined = state.peekInfo?.isJoined == true
      )
    },
    canModify = callLink?.canModify == true,
    approvalRequired = callLink?.state?.restrictions == Restrictions.ADMIN_APPROVAL,
    approvalChangeInFlight = state.isLoadingAdminApprovalChange,
    showRevocationConfirmation = state.displayRevocationDialog,
    alreadyInCall = alreadyInCall,
    failure = when (state.failureSnackbar) {
      CallLinkDetailsState.FailureSnackbar.COULD_NOT_DELETE_CALL_LINK -> CallLinkDetailsFailure.COULD_NOT_DELETE
      CallLinkDetailsState.FailureSnackbar.COULD_NOT_SAVE_CHANGES -> CallLinkDetailsFailure.COULD_NOT_SAVE
      CallLinkDetailsState.FailureSnackbar.COULD_NOT_UPDATE_ADMIN_APPROVAL -> CallLinkDetailsFailure.COULD_NOT_UPDATE_APPROVAL
      null -> null
    }
  )

  MollyLightTheme {
    CallLinkDetailsScreen(
      state = lightState,
      callback = callback,
      showNavigationIcon = !LocalResources.current.rememberIsSplitPane()
    )
  }
}

class DefaultCallLinkDetailsCallback(
  private val activity: FragmentActivity,
  private val viewModel: CallLinkDetailsViewModel,
  private val router: MainNavigationCallDetailRouter
) : CallLinkDetailsCallback {

  override fun onNavigationClicked() {
    activity.onBackPressedDispatcher.onBackPressed()
  }

  override fun onJoinClicked() {
    val recipientSnapshot = viewModel.recipientSnapshot
    if (recipientSnapshot != null) {
      CommunicationActions.startVideoCall(activity, recipientSnapshot) {
        viewModel.showAlreadyInACall(true)
      }
    }
  }

  override fun onEditNameClicked() {
    router.goToCallDetail(
      MainNavigationDetailLocation.Calls.CallLinks.EditCallLinkName(
        callLinkRoomId = viewModel.recipientSnapshot!!.requireCallLinkRoomId(),
        currentName = viewModel.nameSnapshot
      )
    )
  }

  override fun onShareClicked() {
    val mimeType = Intent.normalizeMimeType("text/plain")
    val shareIntent = ShareCompat.IntentBuilder(activity)
      .setText(CallLinks.url(viewModel.rootKeySnapshot))
      .setType(mimeType)
      .createChooserIntent()

    try {
      activity.startActivity(shareIntent)
    } catch (_: ActivityNotFoundException) {
      Toast.makeText(activity, R.string.CreateCallLinkBottomSheetDialogFragment__failed_to_open_share_sheet, Toast.LENGTH_LONG).show()
    }
  }

  override fun onCopyClicked() {
    Util.copyToClipboard(activity, CallLinks.url(viewModel.rootKeySnapshot))
    Toast.makeText(activity, R.string.CreateCallLinkBottomSheetDialogFragment__copied_to_clipboard, Toast.LENGTH_LONG).show()
  }

  override fun onShareLinkViaSignalClicked() {
    activity.startActivity(
      ShareActivity.sendSimpleText(
        activity,
        activity.getString(R.string.CreateCallLink__use_this_link_to_join_a_signal_call, CallLinks.url(viewModel.rootKeySnapshot))
      )
    )
  }

  override fun onDeleteClicked() {
    viewModel.setDisplayRevocationDialog(true)
  }

  override fun onDeleteConfirmed() {
    viewModel.setDisplayRevocationDialog(false)
    activity.lifecycleScope.launch {
      // The detail route exits only after an acknowledged delete. In-use and generic failures stay.
      if (viewModel.delete()) {
        router.exitDetailLocation()
      }
    }
  }

  override fun onDeleteCanceled() {
    viewModel.setDisplayRevocationDialog(false)
  }

  override fun onApproveAllMembersChanged(checked: Boolean) {
    activity.lifecycleScope.launch {
      viewModel.setApproveAllMembers(checked)
    }
  }
}

interface CallLinkDetailsCallback {
  fun onNavigationClicked() = Unit
  fun onJoinClicked() = Unit
  fun onEditNameClicked() = Unit
  fun onShareClicked() = Unit
  fun onCopyClicked() = Unit
  fun onShareLinkViaSignalClicked() = Unit
  fun onDeleteClicked() = Unit
  fun onDeleteConfirmed() = Unit
  fun onDeleteCanceled() = Unit
  fun onApproveAllMembersChanged(checked: Boolean) = Unit

  object Empty : CallLinkDetailsCallback
}

/** Pure Light details surface. Every interaction leaves through [callback]. */
@Composable
fun CallLinkDetailsScreen(
  state: CallLinkDetailsLightState,
  callback: CallLinkDetailsCallback,
  showNavigationIcon: Boolean = true,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(LightThemeTokens.colors.background)
      .testTag(CALL_LINK_DETAILS_SCREEN_TAG)
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      LightTopBar(
        leftButton = if (showNavigationIcon) {
          LightBarButton.LightIcon(
            icon = LightIcons.BACK,
            onClick = callback::onNavigationClicked,
            contentDescription = stringResource(R.string.ConversationFragment__content_description_back_button)
          )
        } else {
          null
        },
        center = LightTopBarCenter.Text(
          stringResource(R.string.CallLinkDetailsFragment__call_details)
        )
      )

      LightScrollView(
        modifier = Modifier.weight(1f),
        scrollBarPosition = LightScrollBarPosition.Inside
      ) {
        state.call?.let { call ->
          SignalCallRow(
            state = call,
            onJoinClicked = callback::onJoinClicked
          )

          if (state.canModify) {
            CallLinkActionRow(
              label = stringResource(
                if (call.name.isEmpty()) {
                  R.string.CreateCallLinkBottomSheetDialogFragment__add_call_name
                } else {
                  R.string.CreateCallLinkBottomSheetDialogFragment__edit_call_name
                }
              ),
              onClick = callback::onEditNameClicked,
              modifier = Modifier.testTag(CALL_LINK_DETAILS_NAME_TAG)
            )

            CallLinkApprovalRow(
              label = stringResource(R.string.CallLinkDetailsFragment__require_admin_approval),
              checked = state.approvalRequired,
              loading = state.approvalChangeInFlight,
              onCheckedChange = callback::onApproveAllMembersChanged,
              modifier = Modifier.testTag(CALL_LINK_DETAILS_APPROVAL_TAG)
            )
          }

          CallLinkActionRow(
            label = stringResource(R.string.CreateCallLinkBottomSheetDialogFragment__share_link_via_signal),
            onClick = callback::onShareLinkViaSignalClicked,
            modifier = Modifier.testTag(CALL_LINK_DETAILS_SHARE_SIGNAL_TAG)
          )
          CallLinkActionRow(
            label = stringResource(R.string.CreateCallLinkBottomSheetDialogFragment__copy_link),
            onClick = callback::onCopyClicked,
            modifier = Modifier.testTag(CALL_LINK_DETAILS_COPY_TAG)
          )
          CallLinkActionRow(
            label = stringResource(R.string.CallLinkDetailsFragment__share_link),
            onClick = callback::onShareClicked,
            modifier = Modifier.testTag(CALL_LINK_DETAILS_SHARE_TAG)
          )

          if (state.canModify) {
            CallLinkActionRow(
              label = stringResource(R.string.CallLinkDetailsFragment__delete_call_link),
              onClick = callback::onDeleteClicked,
              modifier = Modifier.testTag(CALL_LINK_DETAILS_DELETE_TAG)
            )
          }
        }
      }
    }

    Column(modifier = Modifier.align(Alignment.BottomCenter)) {
      CallLinkFeedbackBanner(
        message = if (state.alreadyInCall) {
          stringResource(R.string.CommunicationActions__you_are_already_in_a_call)
        } else {
          null
        }
      )
      CallLinkFeedbackBanner(
        message = when (state.failure) {
          CallLinkDetailsFailure.COULD_NOT_DELETE -> stringResource(R.string.CallLinkDetailsFragment__couldnt_delete_call_link)
          CallLinkDetailsFailure.COULD_NOT_SAVE -> stringResource(R.string.CallLinkDetailsFragment__couldnt_save_changes)
          CallLinkDetailsFailure.COULD_NOT_UPDATE_APPROVAL -> stringResource(R.string.CallLinkDetailsFragment__couldnt_update_admin_approval)
          null -> null
        }
      )
    }

    if (state.showRevocationConfirmation) {
      CallLinkRevocationConfirmation(
        onConfirm = callback::onDeleteConfirmed,
        onCancel = callback::onDeleteCanceled
      )
    }
  }
}

@Composable
private fun CallLinkRevocationConfirmation(
  onConfirm: () -> Unit,
  onCancel: () -> Unit
) {
  BackHandler(onBack = onCancel)

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(LightThemeTokens.colors.background)
      .testTag(CALL_LINK_DETAILS_CONFIRMATION_TAG)
  ) {
    LightTopBar(
      center = LightTopBarCenter.Text(
        stringResource(R.string.CallLinkDetailsFragment__delete_link)
      )
    )

    Box(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .padding(horizontal = 2f.gridUnitsAsDp()),
      contentAlignment = Alignment.Center
    ) {
      LightText(
        text = stringResource(R.string.CallLinkDetailsFragment__this_link_will_no_longer_work),
        variant = LightTextVariant.Copy,
        align = TextAlign.Center
      )
    }

    LightBottomBar(
      items = listOf(
        LightBarButton.Text(
          text = stringResource(android.R.string.cancel),
          onClick = onCancel
        ),
        LightBarButton.Text(
          text = stringResource(R.string.delete),
          onClick = onConfirm
        )
      )
    )
  }
}

@Preview(widthDp = 360, heightDp = 413, showBackground = true)
@Composable
private fun CallLinkDetailsScreenPreview() {
  MollyLightTheme {
    CallLinkDetailsScreen(
      state = CallLinkDetailsLightState(
        call = SignalCallRowState(
          name = "Call Name",
          url = "https://signal.link/call/#key=example"
        ),
        canModify = true,
        approvalRequired = false
      ),
      callback = CallLinkDetailsCallback.Empty
    )
  }
}
