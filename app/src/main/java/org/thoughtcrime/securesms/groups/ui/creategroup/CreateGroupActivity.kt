/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.creategroup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.theme.SignalTheme
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.SelectedContact
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.groups.ui.creategroup.CreateGroupUiState.NavTarget
import org.thoughtcrime.securesms.groups.ui.creategroup.CreateGroupUiState.UserMessage
import org.thoughtcrime.securesms.groups.ui.creategroup.details.AddGroupDetailsActivity
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.light.lightScrim
import org.thoughtcrime.securesms.recipients.ui.RecipientLookupFailureMessage
import org.thoughtcrime.securesms.recipients.ui.RecipientPicker
import org.thoughtcrime.securesms.recipients.ui.RecipientPickerCallbacks
import org.thoughtcrime.securesms.recipients.ui.RecipientPickerScaffold
import org.thoughtcrime.securesms.recipients.ui.RecipientSelection
import org.thoughtcrime.securesms.recipients.ui.findby.FindByActivity
import org.thoughtcrime.securesms.recipients.ui.findby.FindByMode
import java.text.NumberFormat

/**
 * Allows creation of a Signal group by selecting from a list of recipients.
 */
class CreateGroupActivity : PassphraseRequiredActivity() {
  companion object {
    @JvmStatic
    fun createIntent(context: Context): Intent {
      return Intent(context, CreateGroupActivity::class.java)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState, ready)

    val navigateBack = onBackPressedDispatcher::onBackPressed

    setContent {
      SignalTheme {
        CreateGroupScreen(
          closeScreen = { resultCode ->
            resultCode?.let(::setResult)
            navigateBack()
          }
        )
      }
    }
  }
}

@Composable
private fun CreateGroupScreen(
  viewModel: CreateGroupViewModel = viewModel { CreateGroupViewModel() },
  closeScreen: (resultCode: Int?) -> Unit
) {
  val findByLauncher: ActivityResultLauncher<FindByMode> = rememberLauncherForActivityResult(
    contract = FindByActivity.Contract(),
    onResult = { id -> id?.let(viewModel::selectRecipient) }
  )

  val addDetailsLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult(),
    onResult = { result: ActivityResult ->
      if (result.resultCode == Activity.RESULT_OK) {
        closeScreen(Activity.RESULT_OK)
      }
    }
  )

  val callbacks = remember {
    object : CreateGroupUiCallbacks {
      override fun onSearchQueryChanged(query: String) = viewModel.onSearchQueryChanged(query)
      override fun onFindByUsername() = findByLauncher.launch(FindByMode.USERNAME)
      override fun onFindByPhoneNumber() = findByLauncher.launch(FindByMode.PHONE_NUMBER)
      override suspend fun shouldAllowSelection(selection: RecipientSelection): Boolean = viewModel.shouldAllowSelection(selection)
      override fun onSelectionChanged(newSelections: List<SelectedContact>) = viewModel.onSelectionChanged(newSelections)
      override fun onPendingRecipientSelectionsConsumed() = viewModel.clearPendingRecipientSelections()
      override fun onNextClicked(): Unit = viewModel.continueToGroupDetails()
      override fun onUserMessageDismissed(userMessage: UserMessage) = viewModel.clearUserMessage()
      override fun onPendingDestinationConsumed() = viewModel.clearPendingDestination()
      override fun onBackPressed() = closeScreen(null)
    }
  }

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current

  LaunchedEffect(uiState.pendingDestination) {
    when (val pendingDestination = uiState.pendingDestination) {
      is NavTarget.AddGroupDetails -> {
        addDetailsLauncher.launch(AddGroupDetailsActivity.newIntent(context, pendingDestination.recipientIds))
        callbacks.onPendingDestinationConsumed()
      }

      null -> Unit
    }
  }

  CreateGroupScreenUi(
    uiState = uiState,
    callbacks = callbacks
  )
}

@Composable
private fun CreateGroupScreenUi(
  uiState: CreateGroupUiState,
  callbacks: CreateGroupUiCallbacks
) {
  val title = if (uiState.newSelections.isNotEmpty()) {
    pluralStringResource(
      id = R.plurals.CreateGroupActivity__s_members,
      count = uiState.newSelections.size,
      NumberFormat.getInstance().format(uiState.newSelections.size)
    )
  } else {
    stringResource(R.string.CreateGroupActivity__select_members)
  }
  val action = createGroupPrimaryAction(uiState.newSelections.size, uiState.isLookingUpRecipient)

  RecipientPickerScaffold(
    title = title,
    forceSplitPane = uiState.forceSplitPane,
    onNavigateUpClick = callbacks::onBackPressed,
    topAppBarActions = {},
    snackbarHostState = remember { SnackbarHostState() },
    primaryContent = {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black)
      ) {
        Box(modifier = Modifier.weight(1f)) {
          CreateGroupRecipientPicker(
            uiState = uiState,
            callbacks = callbacks
          )

          UserMessagesHost(
            userMessage = uiState.userMessage,
            onDismiss = callbacks::onUserMessageDismissed
          )

          if (uiState.isLookingUpRecipient) {
            // See AddMembersScreenUi: this replaces a Dialogs.IndeterminateProgressDialog, which
            // refused both an outside tap and Back. A translucent background blocks neither.
            BackHandler(enabled = true) { }
            MollyLightTheme {
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .background(LightThemeTokens.colors.background.copy(alpha = 0.9f))
                  .lightScrim(),
                contentAlignment = Alignment.Center
              ) {
                LightText(
                  text = stringResource(R.string.LinkDeviceFragment__loading),
                  variant = LightTextVariant.Heading
                )
              }
            }
          }
        }

        MollyLightTheme {
          LightBottomBar(
            items = listOf(
              LightBarButton.Text(
                text = stringResource(
                  if (action.kind == CreateGroupPrimaryAction.Kind.SKIP) {
                    R.string.CreateGroupActivity__skip
                  } else {
                    R.string.CreateGroupActivity__accessibility_next
                  }
                ),
                onClick = if (action.enabled) callbacks::onNextClicked else null
              )
            ),
            modifier = Modifier.fillMaxWidth()
          )
        }
      }
    }
  )
}

data class CreateGroupPrimaryAction(
  val kind: Kind,
  val enabled: Boolean
) {
  enum class Kind { SKIP, NEXT }
}

fun createGroupPrimaryAction(selectionCount: Int, isLookingUpRecipient: Boolean): CreateGroupPrimaryAction {
  require(selectionCount >= 0)
  return CreateGroupPrimaryAction(
    kind = if (selectionCount == 0) CreateGroupPrimaryAction.Kind.SKIP else CreateGroupPrimaryAction.Kind.NEXT,
    enabled = !isLookingUpRecipient
  )
}

@Composable
private fun CreateGroupRecipientPicker(
  uiState: CreateGroupUiState,
  callbacks: CreateGroupUiCallbacks,
  modifier: Modifier = Modifier
) {
  RecipientPicker(
    searchQuery = uiState.searchQuery,
    displayModes = setOf(RecipientPicker.DisplayMode.PUSH),
    selectionLimits = uiState.selectionLimits,
    pendingRecipientSelections = uiState.pendingRecipientSelections,
    isRefreshing = false,
    listBottomPadding = 64.dp,
    clipListToPadding = false,
    callbacks = remember(callbacks) {
      RecipientPickerCallbacks(
        listActions = callbacks,
        findByUsername = callbacks,
        findByPhoneNumber = callbacks
      )
    },
    modifier = modifier.fillMaxSize()
  )
}

private interface CreateGroupUiCallbacks :
  RecipientPickerCallbacks.ListActions,
  RecipientPickerCallbacks.FindByUsername,
  RecipientPickerCallbacks.FindByPhoneNumber {

  override fun onRecipientSelected(selection: RecipientSelection) = Unit
  fun onNextClicked()
  fun onUserMessageDismissed(userMessage: UserMessage)
  fun onBackPressed()
  fun onPendingDestinationConsumed()

  object Empty : CreateGroupUiCallbacks {
    override fun onSearchQueryChanged(query: String) = Unit
    override fun onFindByUsername() = Unit
    override fun onFindByPhoneNumber() = Unit
    override suspend fun shouldAllowSelection(selection: RecipientSelection): Boolean = true
    override fun onPendingRecipientSelectionsConsumed() = Unit
    override fun onNextClicked() = Unit
    override fun onUserMessageDismissed(userMessage: UserMessage) = Unit
    override fun onBackPressed() = Unit
    override fun onPendingDestinationConsumed() = Unit
  }
}

@Composable
private fun UserMessagesHost(
  userMessage: UserMessage?,
  onDismiss: (UserMessage) -> Unit
) {
  when (userMessage) {
    null -> {}

    is UserMessage.RecipientLookupFailed -> {
      RecipientLookupFailureMessage(
        failure = userMessage.failure,
        onDismissed = { onDismiss(userMessage) }
      )
    }
  }
}

@AllDevicePreviews
@Composable
private fun CreateGroupScreenPreview() {
  Previews.Preview {
    CreateGroupScreenUi(
      uiState = CreateGroupUiState(
        forceSplitPane = false,
        selectionLimits = SelectionLimits.NO_LIMITS
      ),
      callbacks = CreateGroupUiCallbacks.Empty
    )
  }
}
