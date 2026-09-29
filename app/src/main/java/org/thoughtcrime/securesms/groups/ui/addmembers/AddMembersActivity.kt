/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.addmembers

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import org.signal.core.ui.compose.AllDevicePreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.getParcelableArrayListExtraCompat
import org.signal.core.util.getParcelableExtraCompat
import org.signal.core.util.nullIfBlank
import org.thoughtcrime.securesms.PassphraseRequiredActivity
import org.thoughtcrime.securesms.PushContactSelectionActivity
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.conversation.ConversationSettingsEvent
import org.thoughtcrime.securesms.contacts.SelectedContact
import org.thoughtcrime.securesms.database.model.GroupRecord
import org.thoughtcrime.securesms.groups.GroupId
import org.thoughtcrime.securesms.groups.SelectionLimits
import org.thoughtcrime.securesms.groups.ui.addmembers.AddMembersUiState.UserMessage
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.light.lightScrim
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.ui.RecipientLookupFailureMessage
import org.thoughtcrime.securesms.recipients.ui.RecipientPicker
import org.thoughtcrime.securesms.recipients.ui.RecipientPickerCallbacks
import org.thoughtcrime.securesms.recipients.ui.RecipientPickerScaffold
import org.thoughtcrime.securesms.recipients.ui.RecipientSelection
import org.thoughtcrime.securesms.recipients.ui.findby.FindByActivity
import org.thoughtcrime.securesms.recipients.ui.findby.FindByMode
import java.text.NumberFormat

/**
 * Allows members to be added to an existing Signal group by selecting from a list of recipients.
 */
class AddMembersActivity : PassphraseRequiredActivity() {
  companion object {
    private const val EXTRA_GROUP_ID = "group_id"
    private const val EXTRA_SELECTION_LIMITS = "selection_limits"
    private const val EXTRA_PRESELECTED_RECIPIENTS = "preselected_recipients"

    fun createIntent(
      context: Context,
      event: ConversationSettingsEvent.AddMembersToGroup
    ): Intent {
      return Intent(context, AddMembersActivity::class.java).apply {
        putExtra(EXTRA_GROUP_ID, event.groupId)
        putExtra(EXTRA_SELECTION_LIMITS, event.selectionLimits)
        putParcelableArrayListExtra(EXTRA_PRESELECTED_RECIPIENTS, ArrayList(event.groupMembersWithoutSelf))
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState, ready)

    setContent {
      SignalTheme {
        AddMembersScreen(
          viewModel = viewModel {
            AddMembersViewModel(
              groupId = intent.getParcelableExtraCompat(EXTRA_GROUP_ID, GroupId::class.java)!!,
              existingMembersMinusSelf = intent.getParcelableArrayListExtraCompat(EXTRA_PRESELECTED_RECIPIENTS, RecipientId::class.java)!!.toSet(),
              selectionLimits = intent.getParcelableExtraCompat(EXTRA_SELECTION_LIMITS, SelectionLimits::class.java)!!
            )
          },
          activityIntent = intent,
          closeScreen = { result ->
            setResult(result.resultCode, result.data)
            onBackPressedDispatcher.onBackPressed()
          }
        )
      }
    }
  }
}

@Composable
private fun AddMembersScreen(
  viewModel: AddMembersViewModel,
  activityIntent: Intent,
  closeScreen: (result: ActivityResult) -> Unit
) {
  val findByLauncher: ActivityResultLauncher<FindByMode> = rememberLauncherForActivityResult(
    contract = FindByActivity.Contract(),
    onResult = { id -> id?.let(viewModel::selectRecipient) }
  )

  val callbacks = remember {
    object : AddMembersUiCallbacks {
      override fun onSearchQueryChanged(query: String) = viewModel.onSearchQueryChanged(query)
      override fun onFindByUsername() = findByLauncher.launch(FindByMode.USERNAME)
      override fun onFindByPhoneNumber() = findByLauncher.launch(FindByMode.PHONE_NUMBER)
      override suspend fun shouldAllowSelection(selection: RecipientSelection): Boolean = viewModel.shouldAllowSelection(selection)
      override fun onSelectionChanged(newSelections: List<SelectedContact>) = viewModel.onSelectionChanged(newSelections)
      override fun onPendingRecipientSelectionsConsumed() = viewModel.clearPendingRecipientSelections()
      override fun onDoneClicked() = viewModel.addSelectedMembers()
      override fun onAddConfirmed(recipientIds: Set<RecipientId>) {
        val resultIntent = activityIntent.apply {
          putParcelableArrayListExtra(
            PushContactSelectionActivity.KEY_SELECTED_RECIPIENTS,
            ArrayList(recipientIds.toList())
          )
        }
        closeScreen(ActivityResult(RESULT_OK, resultIntent))
      }

      override fun onUserMessageDismissed(userMessage: UserMessage) = viewModel.clearUserMessage()
      override fun onBackPressed() = closeScreen(ActivityResult(RESULT_CANCELED, null))
    }
  }

  val uiState by viewModel.uiState.collectAsStateWithLifecycle()

  AddMembersScreenUi(
    uiState = uiState,
    callbacks = callbacks
  )
}

@Composable
private fun AddMembersScreenUi(
  uiState: AddMembersUiState,
  callbacks: AddMembersUiCallbacks
) {
  val title = if (uiState.totalMembersCount > 0) {
    pluralStringResource(
      id = R.plurals.CreateGroupActivity__s_members,
      count = uiState.totalMembersCount,
      NumberFormat.getInstance().format(uiState.totalMembersCount)
    )
  } else {
    stringResource(R.string.AddMembersActivity__add_members)
  }
  val doneEnabled = addMembersPrimaryAction(
    selectionCount = uiState.newSelections.size,
    isLookingUpRecipient = uiState.isLookingUpRecipient
  ).enabled && uiState.userMessage !is UserMessage.GroupAddConfirmation

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
          AddMembersRecipientPicker(
            uiState = uiState,
            callbacks = callbacks
          )

          UserMessagesHost(
            userMessage = uiState.userMessage,
            onAddConfirmed = callbacks::onAddConfirmed,
            onDismiss = callbacks::onUserMessageDismissed
          )

          if (uiState.isLookingUpRecipient) {
            // The Material dialog this replaced set dismissOnBackPress = dismissOnClickOutside =
            // false, so both a stray tap and a Back press stopped here. A translucent background on
            // its own blocks neither, and the recipient picker underneath stays fully interactive.
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
                text = stringResource(R.string.AddMembersActivity__done),
                onClick = if (doneEnabled) callbacks::onDoneClicked else null
              )
            ),
            modifier = Modifier.fillMaxWidth()
          )
        }
      }
    }
  )
}

data class AddMembersPrimaryAction(val enabled: Boolean)

fun addMembersPrimaryAction(selectionCount: Int, isLookingUpRecipient: Boolean): AddMembersPrimaryAction {
  require(selectionCount >= 0)
  return AddMembersPrimaryAction(enabled = selectionCount > 0 && !isLookingUpRecipient)
}

enum class AddMembersConfirmationAction {
  DISMISS,
  CONFIRM
}

enum class AddMembersConfirmationEffect {
  DISMISS,
  RETURN_SELECTED_IDS
}

data class AddMembersConfirmationTransition(
  val submitted: Boolean,
  val effect: AddMembersConfirmationEffect?
)

/**
 * Reduces confirmation taps to a one-shot effect. The activity's confirm effect only returns IDs;
 * ConversationSettings remains the sole owner of the group mutation.
 */
fun reduceAddMembersConfirmation(
  submitted: Boolean,
  action: AddMembersConfirmationAction
): AddMembersConfirmationTransition {
  if (submitted) return AddMembersConfirmationTransition(submitted = true, effect = null)

  return AddMembersConfirmationTransition(
    submitted = true,
    effect = when (action) {
      AddMembersConfirmationAction.DISMISS -> AddMembersConfirmationEffect.DISMISS
      AddMembersConfirmationAction.CONFIRM -> AddMembersConfirmationEffect.RETURN_SELECTED_IDS
    }
  )
}

@Composable
private fun AddMembersRecipientPicker(
  uiState: AddMembersUiState,
  callbacks: AddMembersUiCallbacks,
  modifier: Modifier = Modifier
) {
  RecipientPicker(
    searchQuery = uiState.searchQuery,
    displayModes = setOf(RecipientPicker.DisplayMode.PUSH),
    selectionLimits = uiState.selectionLimits,
    preselectedRecipients = uiState.existingMembersMinusSelf,
    pendingRecipientSelections = uiState.pendingRecipientSelections,
    isRefreshing = false,
    listBottomPadding = 64.dp,
    clipListToPadding = false,
    callbacks = RecipientPickerCallbacks(
      listActions = callbacks,
      findByUsername = callbacks,
      findByPhoneNumber = callbacks
    ),
    modifier = modifier.fillMaxSize()
  )
}

private interface AddMembersUiCallbacks :
  RecipientPickerCallbacks.ListActions,
  RecipientPickerCallbacks.FindByUsername,
  RecipientPickerCallbacks.FindByPhoneNumber {

  override fun onRecipientSelected(selection: RecipientSelection) = Unit
  fun onDoneClicked()
  fun onAddConfirmed(recipientIds: Set<RecipientId>)
  fun onUserMessageDismissed(userMessage: UserMessage)
  fun onBackPressed()

  object Empty : AddMembersUiCallbacks {
    override fun onSearchQueryChanged(query: String) = Unit
    override fun onFindByUsername() = Unit
    override fun onFindByPhoneNumber() = Unit
    override suspend fun shouldAllowSelection(selection: RecipientSelection): Boolean = true
    override fun onDoneClicked() = Unit
    override fun onAddConfirmed(recipientIds: Set<RecipientId>) = Unit
    override fun onUserMessageDismissed(userMessage: UserMessage) = Unit
    override fun onBackPressed() = Unit
  }
}

@Composable
private fun UserMessagesHost(
  userMessage: UserMessage?,
  onAddConfirmed: (Set<RecipientId>) -> Unit,
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

    is UserMessage.CantAddRecipientToLegacyGroup -> {
      Toast.makeText(LocalContext.current, R.string.AddMembersActivity__this_person_cant_be_added_to_legacy_groups, Toast.LENGTH_SHORT).show()
      onDismiss(userMessage)
    }

    is UserMessage.GroupAddConfirmation -> {
      GroupAddConfirmationDialog(
        message = userMessage,
        onAddConfirmed = onAddConfirmed,
        onDismiss = onDismiss
      )
    }
  }
}

@Composable
private fun GroupAddConfirmationDialog(
  message: UserMessage.GroupAddConfirmation,
  onAddConfirmed: (Set<RecipientId>) -> Unit,
  onDismiss: (UserMessage) -> Unit
) {
  val context: Context = LocalContext.current
  val bodyText: String = when (message) {
    is UserMessage.ConfirmAddMember -> {
      stringResource(
        id = R.string.AddMembersActivity__add_member_to_s,
        message.recipient.getDisplayName(context),
        message.group.getDisplayTitle(context)
      )
    }

    is UserMessage.ConfirmAddMembers -> {
      pluralStringResource(
        id = R.plurals.AddMembersActivity__add_d_members_to_s,
        message.recipientIds.size,
        message.recipientIds.size,
        message.group.getDisplayTitle(context)
      )
    }
  }

  var submitted by rememberSaveable(message) { mutableStateOf(false) }
  val dismissOnce = {
    val transition = reduceAddMembersConfirmation(submitted, AddMembersConfirmationAction.DISMISS)
    submitted = transition.submitted
    if (transition.effect == AddMembersConfirmationEffect.DISMISS) onDismiss(message)
  }
  BackHandler(enabled = !submitted, onBack = dismissOnce)

  MollyLightTheme {
    Box(modifier = Modifier.fillMaxSize()) {
      // Sibling scrim, not a parent that consumes: the bottom bar underneath is the only other
      // thing in this Box and it has to keep its own taps.
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(LightThemeTokens.colors.background)
          .lightScrim()
      )

      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(20.dp),
        verticalArrangement = Arrangement.Center
      ) {
        LightText(text = bodyText, variant = LightTextVariant.Heading)
      }

      LightBottomBar(
        items = listOf(
          LightBarButton.Text(
            text = stringResource(android.R.string.cancel),
            onClick = if (submitted) null else dismissOnce
          ),
          LightBarButton.Text(
            text = stringResource(R.string.AddMembersActivity__add),
            onClick = if (submitted) null else {
              {
                val transition = reduceAddMembersConfirmation(submitted, AddMembersConfirmationAction.CONFIRM)
                submitted = transition.submitted
                if (transition.effect == AddMembersConfirmationEffect.RETURN_SELECTED_IDS) {
                  onAddConfirmed(message.recipientIds)
                }
              }
            }
          )
        ),
        modifier = Modifier.align(Alignment.BottomCenter)
      )
    }
  }
}

private fun GroupRecord.getDisplayTitle(context: Context): String {
  return this.title.nullIfBlank() ?: context.getString(R.string.Recipient_unknown)
}

@AllDevicePreviews
@Composable
private fun AddMembersScreenPreview() {
  Previews.Preview {
    AddMembersScreenUi(
      uiState = AddMembersUiState(
        forceSplitPane = false,
        selectionLimits = SelectionLimits.NO_LIMITS
      ),
      callbacks = AddMembersUiCallbacks.Empty
    )
  }
}
