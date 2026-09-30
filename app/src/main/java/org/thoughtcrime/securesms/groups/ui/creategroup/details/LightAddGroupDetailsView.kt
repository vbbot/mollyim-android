/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.creategroup.details

import android.content.Context
import android.graphics.BitmapFactory
import android.util.AttributeSet
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.lightClickable
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.groups.ui.GroupMemberEntry
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.light.lightScrim
import org.thoughtcrime.securesms.recipients.Recipient

/**
 * View/Compose boundary for the group-details controller.
 *
 * The fragment and its ViewModel remain the owners of avatar selection, timers, member removal,
 * duplicate-group navigation and group creation. This view owns only immutable presentation data and
 * forwards intents. Keeping that boundary is especially important for create: a busy tap can never
 * submit twice and an error simply re-enables the same ViewModel operation.
 */
class LightAddGroupDetailsView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : AbstractComposeView(context, attrs) {

  data class MemberUi(
    val recipient: Recipient,
    val name: String,
    val isInvited: Boolean
  )

  data class DuplicateGroupUi(
    val recipient: Recipient,
    val name: String
  )

  private sealed interface Confirmation {
    data class RemoveMember(val recipient: Recipient, val name: String) : Confirmation
    data class OpenDuplicate(val recipient: Recipient, val name: String) : Confirmation
  }

  interface Callbacks {
    fun onBack()
    fun onAvatarClick()
    fun onTimerClick()
    fun onNameChanged(name: String)
    fun onCreateClick()
    fun onRemoveMemberConfirmed(recipient: Recipient)
    fun onOpenDuplicateConfirmed(recipient: Recipient)
  }

  private object EmptyCallbacks : Callbacks {
    override fun onBack() = Unit
    override fun onAvatarClick() = Unit
    override fun onTimerClick() = Unit
    override fun onNameChanged(name: String) = Unit
    override fun onCreateClick() = Unit
    override fun onRemoveMemberConfirmed(recipient: Recipient) = Unit
    override fun onOpenDuplicateConfirmed(recipient: Recipient) = Unit
  }

  private var callbacks: Callbacks = EmptyCallbacks

  fun setCallbacks(value: Callbacks) {
    callbacks = value
  }

  /**
   * Presentation state, kept private so this class exposes exactly the Java-facing setters below and
   * nothing else.
   *
   * Kotlin *does* generate accessors for a private delegated property -- they are simply private.
   * That still matters, because a private `setBusy(Boolean)` and a public `setBusy(Boolean)` share
   * one JVM signature and are a platform declaration clash, not a shadowing that Kotlin resolves.
   * `mmsEnabled` and `busyState` are named to avoid the clash; the setters below keep the short
   * names the host calls.
   */
  private var name by mutableStateOf("")
  private var avatarBytes by mutableStateOf<ByteArray?>(null)
  private var timerText by mutableStateOf("")
  private var memberItems by mutableStateOf(emptyList<MemberUi>())
  private var duplicateGroupItems by mutableStateOf(emptyList<DuplicateGroupUi>())
  private var mmsEnabled by mutableStateOf(false)
  private var submitAllowed by mutableStateOf(false)
  private var busyState by mutableStateOf(false)
  private var errorMessage by mutableStateOf<String?>(null)
  private var confirmation by mutableStateOf<Confirmation?>(null)

  init {
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
  }

  fun setGroupName(value: String) {
    if (name != value) name = value
  }

  fun setAvatar(value: ByteArray?) {
    avatarBytes = value
  }

  fun setTimerValue(value: String) {
    timerText = value
  }

  fun setMembers(value: List<GroupMemberEntry.NewGroupCandidate>) {
    memberItems = value.map { candidate ->
      val recipient = candidate.member
      MemberUi(
        recipient = recipient,
        name = recipient.getDisplayName(context),
        isInvited = !recipient.isRegistered
      )
    }
  }

  fun setDuplicateGroups(value: List<Recipient>) {
    duplicateGroupItems = value.map { recipient ->
      DuplicateGroupUi(recipient, recipient.getDisplayName(context))
    }
  }

  fun setMms(value: Boolean) {
    mmsEnabled = value
  }

  fun setCanSubmit(value: Boolean) {
    submitAllowed = value
  }

  fun setBusy(value: Boolean) {
    busyState = value
    if (value) errorMessage = null
  }

  fun showError(message: String) {
    busyState = false
    errorMessage = message
  }

  @Composable
  override fun Content() {
    MollyLightTheme {
      // The confirmation is a sibling of the screen inside a Box rather than a second root node.
      // Two roots under a ComposeView rely on the view's stack-the-children measure policy to
      // order them; inside a Box the modal is unambiguously above the content it covers.
      Box(modifier = Modifier.fillMaxSize()) {
        LightAddGroupDetailsScreen(
          state = LightAddGroupDetailsState(
            name = name,
            avatarBytes = avatarBytes,
            timerValue = timerText,
            members = memberItems,
            duplicateGroups = duplicateGroupItems,
            isMms = mmsEnabled,
            canSubmit = submitAllowed,
            isBusy = busyState,
            errorMessage = errorMessage
          ),
          onBack = callbacks::onBack,
          onAvatarClick = callbacks::onAvatarClick,
          onTimerClick = callbacks::onTimerClick,
          onNameChanged = { newName ->
            val accepted = enforceGroupNameGraphemeLimit(newName)
            if (accepted != name) {
              name = accepted
              // Editing clears a failed-create message, matching the EditText error it replaced.
              errorMessage = null
              callbacks.onNameChanged(accepted)
            }
          },
          onMemberClick = { member ->
            if (!busyState) confirmation = Confirmation.RemoveMember(member.recipient, member.name)
          },
          onDuplicateClick = { group ->
            if (!busyState) confirmation = Confirmation.OpenDuplicate(group.recipient, group.name)
          },
          onCreateClick = {
            if (submitAllowed && !busyState) callbacks.onCreateClick()
          }
        )

        confirmation?.let { pending ->
          LightGroupConfirmation(
            title = when (pending) {
              is Confirmation.RemoveMember -> stringResource(R.string.AddGroupDetailsFragment__remove_s_from_this_group, pending.name)
              is Confirmation.OpenDuplicate -> stringResource(R.string.AddGroupDetailsFragment__discard_group)
            },
            detail = when (pending) {
              is Confirmation.RemoveMember -> null
              is Confirmation.OpenDuplicate -> stringResource(
                R.string.AddGroupDetailsFragment__if_you_continue_to_the_group_s_your_changes_wont_be_saved,
                pending.name
              )
            },
            confirmLabel = when (pending) {
              is Confirmation.RemoveMember -> stringResource(R.string.AddGroupDetailsFragment__remove)
              is Confirmation.OpenDuplicate -> stringResource(R.string.AddGroupDetailsFragment__discard)
            },
            onCancel = { confirmation = null },
            onConfirm = {
              // Clear before dispatch so lifecycle or state callbacks cannot submit the same prompt twice.
              confirmation = null
              when (pending) {
                is Confirmation.RemoveMember -> callbacks.onRemoveMemberConfirmed(pending.recipient)
                is Confirmation.OpenDuplicate -> callbacks.onOpenDuplicateConfirmed(pending.recipient)
              }
            }
          )
        }
      }
    }
  }
}

data class LightAddGroupDetailsState(
  val name: String,
  val avatarBytes: ByteArray?,
  val timerValue: String,
  val members: List<LightAddGroupDetailsView.MemberUi>,
  val duplicateGroups: List<LightAddGroupDetailsView.DuplicateGroupUi>,
  val isMms: Boolean,
  val canSubmit: Boolean,
  val isBusy: Boolean,
  val errorMessage: String?
) {
  val submitEnabled: Boolean get() = canSubmit && !isBusy
  val showTimer: Boolean get() = !isMms
  val showAddLater: Boolean get() = members.isEmpty()
}

@Composable
fun LightAddGroupDetailsScreen(
  state: LightAddGroupDetailsState,
  onBack: () -> Unit,
  onAvatarClick: () -> Unit,
  onTimerClick: () -> Unit,
  onNameChanged: (String) -> Unit,
  onMemberClick: (LightAddGroupDetailsView.MemberUi) -> Unit,
  onDuplicateClick: (LightAddGroupDetailsView.DuplicateGroupUi) -> Unit,
  onCreateClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .background(LightThemeTokens.colors.background)
  ) {
    LightTopBar(
      leftButton = LightBarButton.LightIcon(
        icon = LightIcons.BACK,
        onClick = onBack,
        contentDescription = stringResource(R.string.DefaultTopAppBar__navigate_up_content_description)
      ),
      center = LightTopBarCenter.Text(
        stringResource(
          if (state.isMms) R.string.AddGroupDetailsFragment__create_group else R.string.AddGroupDetailsFragment__name_this_group
        )
      )
    )

    Column(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        AvatarButton(state.avatarBytes, enabled = !state.isBusy, onClick = onAvatarClick)
        Spacer(modifier = Modifier.size(16.dp))
        GroupNameField(
          value = state.name,
          placeholder = stringResource(
            if (state.isMms) R.string.AddGroupDetailsFragment__group_name_optional else R.string.AddGroupDetailsFragment__group_name_required
          ),
          enabled = !state.isBusy,
          onValueChange = onNameChanged,
          modifier = Modifier.weight(1f)
        )
      }

      if (state.showTimer) {
        LightValueRow(
          title = stringResource(R.string.PrivacySettingsFragment__disappearing_messages),
          value = state.timerValue,
          enabled = !state.isBusy,
          onClick = onTimerClick
        )
      } else {
        LightNotice(text = stringResource(R.string.AddGroupDetailsFragment__youve_selected_a_contact_that_doesnt_support_signal_groups_mms_removal))
      }

      state.errorMessage?.let { LightNotice(text = it, isError = true) }

      if (state.duplicateGroups.isNotEmpty()) {
        LightText(
          text = pluralStringResource(
            R.plurals.AddGroupDetailsFragment__d_groups_with_same_members,
            state.duplicateGroups.size,
            state.duplicateGroups.size
          ),
          variant = LightTextVariant.Detail,
          modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
        )
        state.duplicateGroups.forEach { group ->
          LightValueRow(
            title = group.name,
            value = "›",
            enabled = !state.isBusy,
            onClick = { onDuplicateClick(group) }
          )
        }
      }

      LightText(
        text = stringResource(R.string.AddGroupDetailsFragment__members),
        variant = LightTextVariant.Heading,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
      )

      if (state.showAddLater) {
        LightText(
          text = stringResource(R.string.AddGroupDetailsFragment__you_can_add_or_invite_friends_after_creating_this_group),
          variant = LightTextVariant.Detail,
          modifier = Modifier.padding(bottom = 12.dp)
        )
      } else {
        state.members.forEach { member ->
          LightValueRow(
            title = member.name,
            value = if (member.isInvited) stringResource(R.string.AddGroupDetailsFragment__will_be_invited) else "",
            enabled = !state.isBusy,
            onClick = { onMemberClick(member) }
          )
        }
      }
    }

    LightBottomBar(
      items = listOf(
        LightBarButton.Text(
          text = if (state.isBusy) stringResource(R.string.AddGroupDetailsFragment__creating) else stringResource(R.string.AddGroupDetailsFragment__create),
          onClick = if (state.submitEnabled) onCreateClick else null
        )
      ),
      modifier = Modifier
        .semantics {
          role = Role.Button
          if (!state.submitEnabled) disabled()
        }
    )
  }
}

@Composable
private fun AvatarButton(bytes: ByteArray?, enabled: Boolean, onClick: () -> Unit) {
  val interactionModifier = if (enabled) {
    Modifier.lightClickable(onClick = onClick)
  } else {
    Modifier.semantics { disabled() }
  }

  Box(
    modifier = Modifier
      .size(64.dp)
      .clip(CircleShape)
      .border(1.dp, LightThemeTokens.colors.content, CircleShape)
      .then(interactionModifier),
    contentAlignment = Alignment.Center
  ) {
    val bitmap = remember(bytes) {
      bytes?.let { value -> BitmapFactory.decodeByteArray(value, 0, value.size) }
    }
    if (bitmap != null) {
      Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = stringResource(R.string.AddGroupDetailsFragment__group_avatar),
        modifier = Modifier.fillMaxSize()
      )
    } else {
      LightIcon(
        icon = LightIcons.CAMERA,
        contentDescription = stringResource(R.string.AddGroupDetailsFragment__group_avatar)
      )
    }
  }
}

@Composable
private fun GroupNameField(
  value: String,
  placeholder: String,
  enabled: Boolean,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
  }

  Column(modifier = modifier) {
    Box(modifier = Modifier.fillMaxWidth()) {
      if (value.isEmpty()) {
        LightText(text = placeholder, variant = LightTextVariant.Copy)
      }
      BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        textStyle = TextStyle(
          color = LightThemeTokens.colors.content,
          fontSize = 16.sp,
          fontWeight = FontWeight.Normal
        ),
        cursorBrush = SolidColor(LightThemeTokens.colors.content),
        modifier = Modifier
          .fillMaxWidth()
          .focusRequester(focusRequester)
      )
    }
    Spacer(
      modifier = Modifier
        .padding(top = 6.dp)
        .fillMaxWidth()
        .height(1.dp)
        .background(LightThemeTokens.colors.content)
    )
  }
}

@Composable
private fun LightValueRow(
  title: String,
  value: String,
  enabled: Boolean,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .height(48.dp)
      .semantics {
        role = Role.Button
        if (!enabled) disabled()
      }
      .lightClickable(enabled = enabled, role = Role.Button, onClick = onClick),
    verticalAlignment = Alignment.CenterVertically
  ) {
    LightText(
      text = title,
      variant = LightTextVariant.Copy,
      maxLines = 1,
      modifier = Modifier.weight(1f)
    )
    if (value.isNotEmpty()) {
      LightText(
        text = value,
        variant = LightTextVariant.Detail,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 8.dp)
      )
    }
  }
}

/**
 * A bordered note, or -- for a failure -- the same block inverted.
 *
 * The inversion is deliberate. The Light palette is true black and white with no red, and every other
 * converted surface in the app is monochrome, so a hand-picked error red here would be the only
 * colour on the device. Filled content-coloured ground with background-coloured text is
 * distinguishable inside the language, and it is a stronger signal than the outline anyway.
 */
@Composable
private fun LightNotice(text: String, isError: Boolean = false) {
  val colors = LightThemeTokens.colors

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .background(if (isError) colors.content else Color.Transparent)
      .then(if (isError) Modifier else Modifier.border(1.dp, colors.content))
      .padding(horizontal = 12.dp, vertical = 12.dp)
  ) {
    LightText(
      text = text,
      variant = LightTextVariant.Detail,
      color = if (isError) colors.background else colors.content
    )
  }
}

@Composable
private fun LightGroupConfirmation(
  title: String,
  detail: String?,
  confirmLabel: String,
  onCancel: () -> Unit,
  onConfirm: () -> Unit
) {
  BackHandler(onBack = onCancel)

  Box(modifier = Modifier.fillMaxSize()) {
    // The scrim is a sibling of the content, not its parent, so consuming here cannot interfere
    // with the bottom bar's own taps. See LightPanelFrame for why a background alone is not enough.
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
      LightText(text = title, variant = LightTextVariant.Heading)
      detail?.let {
        LightText(
          text = it,
          variant = LightTextVariant.Copy,
          modifier = Modifier.padding(top = 12.dp)
        )
      }
    }
    LightBottomBar(
      items = listOf(
        LightBarButton.Text(
          text = stringResource(android.R.string.cancel),
          onClick = onCancel
        ),
        LightBarButton.Text(
          text = confirmLabel,
          onClick = onConfirm
        )
      ),
      modifier = Modifier.align(Alignment.BottomCenter)
    )
  }
}
