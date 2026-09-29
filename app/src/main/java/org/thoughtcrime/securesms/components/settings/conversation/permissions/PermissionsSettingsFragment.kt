package org.thoughtcrime.securesms.components.settings.conversation.permissions

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.observeAsState
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.lightClickable
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.groups.ui.GroupErrors
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.light.lightScrim

class PermissionsSettingsFragment : ComposeFragment() {

  private val viewModel: PermissionsSettingsViewModel by viewModels(
    factoryProducer = {
      val args = PermissionsSettingsFragmentArgs.fromBundle(requireArguments())
      PermissionsSettingsViewModel.Factory(
        groupId = args.groupId,
        repository = PermissionsSettingsRepository(requireContext())
      )
    }
  )

  private var showMemberLabelWarning by mutableStateOf(false)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    showMemberLabelWarning = savedInstanceState?.getBoolean(STATE_SHOW_MEMBER_LABEL_WARNING) ?: false
    super.onViewCreated(view, savedInstanceState)

    viewModel.events.observe(viewLifecycleOwner) { event ->
      when (event) {
        is PermissionsSettingsEvents.GroupChangeError -> {
          Toast.makeText(context, GroupErrors.getUserDisplayMessage(event.reason), Toast.LENGTH_LONG).show()
        }

        PermissionsSettingsEvents.ShowMemberLabelsWillBeRemovedWarning -> {
          showMemberLabelWarning = true
        }
      }
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    outState.putBoolean(STATE_SHOW_MEMBER_LABEL_WARNING, showMemberLabelWarning)
    super.onSaveInstanceState(outState)
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.observeAsState(PermissionsSettingsState())
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    MollyLightTheme {
      PermissionsSettingsScreen(
        state = state,
        showMemberLabelWarning = showMemberLabelWarning,
        onBack = { backDispatcher?.onBackPressed() },
        onSelectionConfirmed = ::onSelectionConfirmed,
        onMemberLabelWarningDismissed = { showMemberLabelWarning = false },
        onMemberLabelRestrictionConfirmed = {
          showMemberLabelWarning = false
          viewModel.onRestrictMemberLabelsToAdminsConfirmed()
        }
      )
    }
  }

  private fun onSelectionConfirmed(permission: GroupPermission, selectedIndex: Int) {
    when (permission) {
      GroupPermission.ADD_MEMBERS -> viewModel.setNonAdminCanAddMembers(selectedIndex == ALL_MEMBERS_INDEX)
      GroupPermission.EDIT_GROUP_INFO -> viewModel.setNonAdminCanEditGroupInfo(selectedIndex == ALL_MEMBERS_INDEX)
      GroupPermission.SEND_MESSAGES -> viewModel.setAnnouncementGroup(selectedIndex == ONLY_ADMINS_INDEX)
      GroupPermission.ADD_MEMBER_LABELS -> {
        viewModel.onMemberLabelPermissionChangeRequested(selectedIndex == ALL_MEMBERS_INDEX)
      }
    }
  }

  private companion object {
    const val STATE_SHOW_MEMBER_LABEL_WARNING = "show_member_label_warning"
  }
}

enum class GroupPermission {
  ADD_MEMBERS,
  EDIT_GROUP_INFO,
  SEND_MESSAGES,
  ADD_MEMBER_LABELS
}

const val ONLY_ADMINS_INDEX = 0
const val ALL_MEMBERS_INDEX = 1

fun PermissionsSettingsState.selectedIndex(permission: GroupPermission): Int {
  val nonAdminAllowed = when (permission) {
    GroupPermission.ADD_MEMBERS -> nonAdminCanAddMembers
    GroupPermission.EDIT_GROUP_INFO -> nonAdminCanEditGroupInfo
    GroupPermission.SEND_MESSAGES -> !announcementGroup
    GroupPermission.ADD_MEMBER_LABELS -> nonAdminCanSetMemberLabel
  }
  return if (nonAdminAllowed) ALL_MEMBERS_INDEX else ONLY_ADMINS_INDEX
}

@Composable
fun PermissionsSettingsScreen(
  state: PermissionsSettingsState,
  showMemberLabelWarning: Boolean,
  onBack: () -> Unit,
  onSelectionConfirmed: (GroupPermission, Int) -> Unit,
  onMemberLabelWarningDismissed: () -> Unit,
  onMemberLabelRestrictionConfirmed: () -> Unit,
  modifier: Modifier = Modifier
) {
  var pendingPermission by rememberSaveable { mutableStateOf<GroupPermission?>(null) }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(LightThemeTokens.colors.background)
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      LightTopBar(
        leftButton = LightBarButton.LightIcon(
          icon = LightIcons.BACK,
          onClick = onBack,
          contentDescription = stringResource(R.string.DefaultTopAppBar__navigate_up_content_description)
        ),
        center = LightTopBarCenter.Text(stringResource(R.string.ConversationSettingsFragment__permissions))
      )

      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
      ) {
        GroupPermission.entries.forEach { permission ->
          PermissionRow(
            permission = permission,
            selectedIndex = state.selectedIndex(permission),
            enabled = state.selfCanEditSettings,
            onClick = { pendingPermission = permission }
          )
        }
      }
    }

    pendingPermission?.let { permission ->
      PermissionChoice(
        permission = permission,
        initialSelection = state.selectedIndex(permission),
        onDismiss = { pendingPermission = null },
        onConfirm = { selectedIndex ->
          pendingPermission = null
          onSelectionConfirmed(permission, selectedIndex)
        }
      )
    }

    if (showMemberLabelWarning) {
      MemberLabelWarning(
        onDismiss = onMemberLabelWarningDismissed,
        onConfirm = onMemberLabelRestrictionConfirmed
      )
    }
  }
}

@Composable
private fun PermissionRow(
  permission: GroupPermission,
  selectedIndex: Int,
  enabled: Boolean,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(64.dp)
      .semantics {
        role = Role.Button
        if (!enabled) disabled()
      }
      .lightClickable(enabled = enabled, role = Role.Button, onClick = onClick)
      .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(modifier = Modifier.weight(1f)) {
      LightText(text = permission.title(), variant = LightTextVariant.Copy)
      LightText(
        text = permission.option(selectedIndex),
        variant = LightTextVariant.Detail,
        modifier = Modifier.padding(top = 2.dp)
      )
    }
    LightText(text = "›", variant = LightTextVariant.Copy)
  }
}

@Composable
private fun PermissionChoice(
  permission: GroupPermission,
  initialSelection: Int,
  onDismiss: () -> Unit,
  onConfirm: (Int) -> Unit
) {
  var selectedIndex by rememberSaveable(permission, initialSelection) { mutableStateOf(initialSelection) }
  var submitted by rememberSaveable(permission) { mutableStateOf(false) }
  val dismissOnce = {
    if (!submitted) {
      submitted = true
      onDismiss()
    }
  }
  BackHandler(enabled = !submitted, onBack = dismissOnce)

  Box(modifier = Modifier.fillMaxSize()) {
    // Sibling scrim rather than a consuming parent, so the bottom bar keeps its own taps, and so a
    // tap in the empty space between the options cannot reach the permission row underneath.
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(LightThemeTokens.colors.background)
        .lightScrim()
    )

    Column(modifier = Modifier.fillMaxSize()) {
      LightTopBar(
        leftButton = LightBarButton.LightIcon(
          icon = LightIcons.BACK,
          onClick = dismissOnce,
          contentDescription = stringResource(R.string.DefaultTopAppBar__navigate_up_content_description)
        ),
        center = LightTopBarCenter.Text(permission.question())
      )
      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
        verticalArrangement = Arrangement.Center
      ) {
        PermissionOption(
          label = permission.option(ONLY_ADMINS_INDEX),
          selected = selectedIndex == ONLY_ADMINS_INDEX,
          onClick = { selectedIndex = ONLY_ADMINS_INDEX }
        )
        PermissionOption(
          label = permission.option(ALL_MEMBERS_INDEX),
          selected = selectedIndex == ALL_MEMBERS_INDEX,
          onClick = { selectedIndex = ALL_MEMBERS_INDEX }
        )
      }
      LightBottomBar(
        items = listOf(
          LightBarButton.Text(
            text = stringResource(android.R.string.cancel),
            onClick = if (submitted) null else dismissOnce
          ),
          LightBarButton.Text(
            text = stringResource(android.R.string.ok),
            onClick = if (submitted) null else {
              {
                submitted = true
                onConfirm(selectedIndex)
              }
            }
          )
        )
      )
    }
  }
}

@Composable
private fun PermissionOption(label: String, selected: Boolean, onClick: () -> Unit) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(64.dp)
      .semantics {
        role = Role.RadioButton
        this.selected = selected
      }
      .lightClickable(role = Role.RadioButton, onClick = onClick)
      .padding(horizontal = 24.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    LightText(
      text = if (selected) "●" else "○",
      variant = LightTextVariant.Copy,
      modifier = Modifier.padding(end = 16.dp)
    )
    LightText(text = label, variant = LightTextVariant.Heading)
  }
}

@Composable
private fun MemberLabelWarning(onDismiss: () -> Unit, onConfirm: () -> Unit) {
  var submitted by rememberSaveable { mutableStateOf(false) }
  val dismissOnce = {
    if (!submitted) {
      submitted = true
      onDismiss()
    }
  }
  BackHandler(enabled = !submitted, onBack = dismissOnce)

  Box(modifier = Modifier.fillMaxSize()) {
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
      LightText(
        text = stringResource(R.string.PermissionsSettingsFragment__member_labels_will_be_cleared_title),
        variant = LightTextVariant.Heading
      )
      LightText(
        text = stringResource(R.string.PermissionsSettingsFragment__member_labels_will_be_cleared_body),
        variant = LightTextVariant.Copy,
        modifier = Modifier.padding(top = 12.dp)
      )
    }
    LightBottomBar(
      items = listOf(
        LightBarButton.Text(
          text = stringResource(android.R.string.cancel),
          onClick = if (submitted) null else dismissOnce
        ),
        LightBarButton.Text(
          text = stringResource(R.string.PermissionsSettingsFragment__change_permission),
          onClick = if (submitted) null else {
            {
              submitted = true
              onConfirm()
            }
          }
        )
      ),
      modifier = Modifier.align(Alignment.BottomCenter)
    )
  }
}

@Composable
private fun GroupPermission.title(): String = stringResource(
  when (this) {
    GroupPermission.ADD_MEMBERS -> R.string.PermissionsSettingsFragment__add_members
    GroupPermission.EDIT_GROUP_INFO -> R.string.PermissionsSettingsFragment__edit_group_info
    GroupPermission.SEND_MESSAGES -> R.string.PermissionsSettingsFragment__send_messages
    GroupPermission.ADD_MEMBER_LABELS -> R.string.PermissionsSettingsFragment__add_member_labels
  }
)

@Composable
private fun GroupPermission.question(): String = stringResource(
  when (this) {
    GroupPermission.ADD_MEMBERS -> R.string.PermissionsSettingsFragment__who_can_add_new_members
    GroupPermission.EDIT_GROUP_INFO -> R.string.PermissionsSettingsFragment__who_can_edit_this_groups_info
    GroupPermission.SEND_MESSAGES -> R.string.PermissionsSettingsFragment__who_can_send_messages
    GroupPermission.ADD_MEMBER_LABELS -> R.string.PermissionsSettingsFragment__who_can_add_member_labels
  }
)

@Composable
private fun GroupPermission.option(index: Int): String = stringResource(
  if (index == ALL_MEMBERS_INDEX) {
    R.string.PermissionsSettingsFragment__all_members
  } else {
    R.string.PermissionsSettingsFragment__only_admins
  }
)
