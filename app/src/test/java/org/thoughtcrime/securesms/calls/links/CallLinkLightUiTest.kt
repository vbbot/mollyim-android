/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.links

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.calls.links.create.CREATE_CALL_LINK_APPROVAL_TAG
import org.thoughtcrime.securesms.calls.links.create.CREATE_CALL_LINK_COPY_TAG
import org.thoughtcrime.securesms.calls.links.create.CREATE_CALL_LINK_NAME_TAG
import org.thoughtcrime.securesms.calls.links.create.CREATE_CALL_LINK_SHARE_SIGNAL_TAG
import org.thoughtcrime.securesms.calls.links.create.CREATE_CALL_LINK_SHARE_TAG
import org.thoughtcrime.securesms.calls.links.create.CreateCallLinkBottomSheetContent
import org.thoughtcrime.securesms.calls.links.create.CreateCallLinkCallbacks
import org.thoughtcrime.securesms.calls.links.create.CreateCallLinkLightState
import org.thoughtcrime.securesms.calls.links.details.CALL_LINK_DETAILS_APPROVAL_TAG
import org.thoughtcrime.securesms.calls.links.details.CALL_LINK_DETAILS_CONFIRMATION_TAG
import org.thoughtcrime.securesms.calls.links.details.CALL_LINK_DETAILS_DELETE_TAG
import org.thoughtcrime.securesms.calls.links.details.CALL_LINK_DETAILS_NAME_TAG
import org.thoughtcrime.securesms.calls.links.details.CallLinkDetailsCallback
import org.thoughtcrime.securesms.calls.links.details.CallLinkDetailsLightState
import org.thoughtcrime.securesms.calls.links.details.CallLinkDetailsScreen
import org.thoughtcrime.securesms.light.MollyLightTheme

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class CallLinkLightUiTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `create actions leave through the callback seam`() {
    val callbacks = RecordingCreateCallbacks()
    renderCreate(callbacks = callbacks)

    composeTestRule.onNodeWithTag(SIGNAL_CALL_JOIN_TAG).performClick()
    composeTestRule.onNodeWithTag(CREATE_CALL_LINK_NAME_TAG).performClick()
    composeTestRule.onNodeWithTag(CREATE_CALL_LINK_APPROVAL_TAG).performClick()
    composeTestRule.onNodeWithTag(CREATE_CALL_LINK_SHARE_SIGNAL_TAG).performScrollTo().performClick()
    composeTestRule.onNodeWithTag(CREATE_CALL_LINK_COPY_TAG).performScrollTo().performClick()
    composeTestRule.onNodeWithTag(CREATE_CALL_LINK_SHARE_TAG).performScrollTo().performClick()
    composeTestRule.onNodeWithText(string(R.string.CreateCallLinkBottomSheetDialogFragment__done)).performClick()

    check(callbacks.events == listOf("join", "name", "approval:false", "share-signal", "copy", "share", "done"))
  }

  @Test
  fun `approval mutation is disabled while loading`() {
    val callbacks = RecordingCreateCallbacks()
    renderCreate(
      state = createState().copy(approvalChangeInFlight = true),
      callbacks = callbacks
    )

    composeTestRule.onNodeWithTag(CREATE_CALL_LINK_APPROVAL_TAG)
      .assertIsNotEnabled()

    check(callbacks.events.isEmpty())
  }

  @Test
  fun `edit input and top bar actions leave through the callback seam`() {
    val callbacks = RecordingEditCallbacks()
    composeTestRule.setContent {
      MollyLightTheme {
        EditCallLinkNameContent(
          state = EditCallLinkNameLightState(
            value = TextFieldValue("Old name", selection = TextRange(8))
          ),
          callbacks = callbacks
        )
      }
    }

    composeTestRule.onNodeWithTag(EDIT_CALL_LINK_NAME_FIELD_TAG).performTextReplacement("New name")
    composeTestRule.onNodeWithText(string(R.string.EditCallLinkNameDialogFragment__save)).performClick()
    composeTestRule.onNodeWithContentDescription(string(R.string.ConversationFragment__content_description_back_button)).performClick()

    check(callbacks.events == listOf("name:New name", "save", "back"))
  }

  @Test
  fun `split-pane edit surface omits its navigation action`() {
    composeTestRule.setContent {
      MollyLightTheme {
        EditCallLinkNameContent(
          state = EditCallLinkNameLightState(
            value = TextFieldValue("Name"),
            showNavigationIcon = false
          ),
          callbacks = EditCallLinkNameCallbacks.Empty
        )
      }
    }

    composeTestRule
      .onNodeWithContentDescription(string(R.string.ConversationFragment__content_description_back_button))
      .assertDoesNotExist()
  }

  @Test
  fun `split-pane details surface omits its navigation action`() {
    composeTestRule.setContent {
      MollyLightTheme {
        CallLinkDetailsScreen(
          state = detailsState(),
          callback = CallLinkDetailsCallback.Empty,
          showNavigationIcon = false
        )
      }
    }

    composeTestRule
      .onNodeWithContentDescription(string(R.string.ConversationFragment__content_description_back_button))
      .assertDoesNotExist()
  }

  @Test
  fun `read-only details hide every modifying action`() {
    renderDetails(
      state = detailsState().copy(canModify = false),
      callback = CallLinkDetailsCallback.Empty
    )

    composeTestRule.onNodeWithTag(CALL_LINK_DETAILS_NAME_TAG).assertDoesNotExist()
    composeTestRule.onNodeWithTag(CALL_LINK_DETAILS_APPROVAL_TAG).assertDoesNotExist()
    composeTestRule.onNodeWithTag(CALL_LINK_DETAILS_DELETE_TAG).assertDoesNotExist()
  }

  @Test
  fun `details callbacks receive approval and delete requests`() {
    val callbacks = RecordingDetailsCallbacks()
    renderDetails(callback = callbacks)

    composeTestRule.onNodeWithTag(CALL_LINK_DETAILS_APPROVAL_TAG).performClick()
    composeTestRule.onNodeWithTag(CALL_LINK_DETAILS_DELETE_TAG).performScrollTo().performClick()

    check(callbacks.events == listOf("approval:false", "delete"))
  }

  @Test
  fun `revocation confirmation owns confirm and cancel callbacks`() {
    val callbacks = RecordingDetailsCallbacks()
    renderDetails(
      state = detailsState().copy(showRevocationConfirmation = true),
      callback = callbacks
    )

    composeTestRule.onNodeWithTag(CALL_LINK_DETAILS_CONFIRMATION_TAG)
    composeTestRule.onNodeWithText(string(R.string.delete)).performClick()
    composeTestRule.onNodeWithText(string(android.R.string.cancel)).performClick()

    check(callbacks.events == listOf("confirm", "cancel"))
  }

  private fun renderCreate(
    state: CreateCallLinkLightState = createState(),
    callbacks: CreateCallLinkCallbacks
  ) {
    composeTestRule.setContent {
      MollyLightTheme {
        CreateCallLinkBottomSheetContent(state = state, callbacks = callbacks)
      }
    }
  }

  private fun renderDetails(
    state: CallLinkDetailsLightState = detailsState(),
    callback: CallLinkDetailsCallback
  ) {
    composeTestRule.setContent {
      MollyLightTheme {
        CallLinkDetailsScreen(state = state, callback = callback)
      }
    }
  }

  private fun createState() = CreateCallLinkLightState(
    call = SignalCallRowState(
      name = "Weekend call",
      url = "https://signal.link/call/#key=display-only"
    ),
    approvalRequired = true
  )

  private fun detailsState() = CallLinkDetailsLightState(
    call = SignalCallRowState(
      name = "Weekend call",
      url = "https://signal.link/call/#key=display-only"
    ),
    canModify = true,
    approvalRequired = true
  )

  private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

  private class RecordingCreateCallbacks : CreateCallLinkCallbacks {
    val events = mutableListOf<String>()

    override fun onJoinClicked() { events += "join" }
    override fun onNameClicked() { events += "name" }
    override fun onApprovalChanged(required: Boolean) { events += "approval:$required" }
    override fun onShareViaSignalClicked() { events += "share-signal" }
    override fun onCopyClicked() { events += "copy" }
    override fun onShareClicked() { events += "share" }
    override fun onDoneClicked() { events += "done" }
  }

  private class RecordingEditCallbacks : EditCallLinkNameCallbacks {
    val events = mutableListOf<String>()

    override fun onNameChanged(value: TextFieldValue) { events += "name:${value.text}" }
    override fun onSaveClicked() { events += "save" }
    override fun onNavigationClicked() { events += "back" }
  }

  private class RecordingDetailsCallbacks : CallLinkDetailsCallback {
    val events = mutableListOf<String>()

    override fun onApproveAllMembersChanged(checked: Boolean) { events += "approval:$checked" }
    override fun onDeleteClicked() { events += "delete" }
    override fun onDeleteConfirmed() { events += "confirm" }
    override fun onDeleteCanceled() { events += "cancel" }
  }
}
