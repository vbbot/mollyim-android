/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.mutiselect.forward

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.designVerticalPxToDp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import java.util.Locale
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.rememberRecipientField

private const val SUMMARY_HEIGHT_UNITS = 3f
private const val MESSAGE_HEIGHT_UNITS = 4.5f

@Composable
fun MultiselectForwardBottomBar(
  isSplitPane: Boolean,
  state: MultiselectForwardBottomBarState,
  events: (MultiselectForwardBottomBarEvent) -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .background(LightThemeTokens.colors.background)
  ) {
    if (isSplitPane) {
      Spacer(modifier = Modifier.weight(1f))
    }

    Column(modifier = Modifier.weight(1f)) {
      SelectionSummary(state)

      if (state.isAddMessageVisible) {
        AddMessage(
          message = state.message,
          onMessageChanged = { events(MultiselectForwardBottomBarEvent.AddMessageUpdate(it)) }
        )
      }

      if (state.isSendButtonVisible) {
        Send(state = state, events = events)
      }
    }
  }
}

/**
 * The send action, in the bottom bar's own grammar.
 *
 * `LightBottomBar` has no first-class disabled state: a null `onClick` makes a bar item inert but
 * leaves it drawn exactly as the enabled one, which on the Light Phone's black-and-white palette is
 * a SEND button that looks live and does nothing. That matters here in a way it would not in a
 * Material screen -- `MultiselectForwardState` disables sending while the safety-number or
 * expiring-message confirmation is up, and a bar that reads as live during a confirmation is
 * inviting a tap the user believes will send.
 *
 * So the enabled item is a real `LightBottomBar` item (it inherits the bar's exact height, padding
 * and centring), and the disabled one is the same label drawn in the Light list's disabled register
 * -- lightened, with `disabled()` in the semantics tree -- in a bar-shaped slot that reserves
 * `LightBottomBar`'s own height so that nothing moves when the state flips.
 */
@Composable
private fun Send(
  state: MultiselectForwardBottomBarState,
  events: (MultiselectForwardBottomBarEvent) -> Unit
) {
  val sendLabel = stringResource(R.string.ShareActivity__send).uppercase(Locale.getDefault())

  if (state.isSendButtonEnabled) {
    LightBottomBar(
      items = listOf(
        LightBarButton.Text(
          text = sendLabel,
          contentDescription = stringResource(R.string.ShareActivity__send),
          onClick = { events(MultiselectForwardBottomBarEvent.SendClick) }
        )
      )
    )
  } else {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(3f.gridUnitsAsDp())
        .semantics {
          role = Role.Button
          disabled()
        },
      contentAlignment = Alignment.Center
    ) {
      LightText(
        text = sendLabel,
        variant = LightTextVariant.Button,
        lighten = true,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
private fun SelectionSummary(state: MultiselectForwardBottomBarState) {
  LazyRow(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = SUMMARY_HEIGHT_UNITS.gridUnitsAsDp())
      .padding(horizontal = 0.5f.gridUnitsAsDp()),
    verticalAlignment = Alignment.CenterVertically
  ) {
    itemsIndexed(items = state.selection, key = { _, contact -> contact.key }) { index, contact ->
      val name by rememberDisplayName(contact)
      LightText(
        text = if (index == state.selection.lastIndex) name else "$name, ",
        variant = LightTextVariant.Fine,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

@Composable
private fun AddMessage(
  message: String,
  onMessageChanged: (String) -> Unit
) {
  val colors = LightThemeTokens.colors
  val keyboard = LocalSoftwareKeyboardController.current

  Column(
    modifier = Modifier
      .fillMaxWidth()
      .background(colors.background)
  ) {
    BasicTextField(
      value = message,
      onValueChange = onMessageChanged,
      singleLine = true,
      textStyle = LightThemeTokens.typography.copy.copy(color = colors.content),
      cursorBrush = SolidColor(colors.content),
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
      keyboardActions = KeyboardActions(onSend = { keyboard?.hide() }),
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = MESSAGE_HEIGHT_UNITS.gridUnitsAsDp())
        .padding(horizontal = 1.75f.gridUnitsAsDp()),
      decorationBox = { innerTextField ->
        Box(contentAlignment = Alignment.CenterStart) {
          if (message.isEmpty()) {
            LightText(
              text = stringResource(R.string.MultiselectForwardFragment__add_a_message),
              variant = LightTextVariant.Copy,
              lighten = true,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          innerTextField()
        }
      }
    )

    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(3f.designVerticalPxToDp())
        .background(colors.content)
    )
  }
}

@Composable
private fun rememberDisplayName(contact: MultiselectForwardBottomBarState.SelectedContact): State<String> = when (contact) {
  is MultiselectForwardBottomBarState.SelectedContact.KnownRecipient -> {
    val context = LocalContext.current
    if (contact.recipient.isSelf) {
      val noteToSelf = stringResource(R.string.note_to_self)
      rememberUpdatedState(noteToSelf)
    } else {
      rememberRecipientField(contact.recipient) { getShortDisplayName(context) }
    }
  }

  is MultiselectForwardBottomBarState.SelectedContact.UnknownRecipient -> rememberUpdatedState(contact.e164)
}

@DayNightPreviews
@Composable
private fun MultiselectForwardBottomBarPreview() {
  Previews.Preview {
    MultiselectForwardBottomBar(
      isSplitPane = false,
      state = rememberPreviewState(isSendButtonVisible = true, isAddMessageVisible = true),
      events = {},
      modifier = Modifier.fillMaxWidth()
    )
  }
}

@Composable
private fun rememberPreviewState(
  isSendButtonVisible: Boolean = false,
  isAddMessageVisible: Boolean = false
): MultiselectForwardBottomBarState {
  return remember {
    MultiselectForwardBottomBarState(
      selection = listOf(
        MultiselectForwardBottomBarState.SelectedContact.KnownRecipient(
          ContactSearchKey.RecipientSearchKey(RecipientId.from(1), false),
          Recipient(id = RecipientId.from(1), isResolving = false, systemContactName = "Miles")
        ),
        MultiselectForwardBottomBarState.SelectedContact.KnownRecipient(
          ContactSearchKey.RecipientSearchKey(RecipientId.from(2), true),
          Recipient(id = RecipientId.from(2), isResolving = false, systemContactName = "Peter")
        )
      ),
      isSendButtonVisible = isSendButtonVisible,
      isAddMessageVisible = isAddMessageVisible
    )
  }
}
