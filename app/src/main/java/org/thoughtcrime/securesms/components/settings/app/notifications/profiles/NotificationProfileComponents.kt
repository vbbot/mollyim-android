/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.app.notifications.profiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.thelightphone.sdk.ui.lightClickable
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.recipients.rememberRecipientField

@Composable
fun NotificationProfileAddMembers(
  onClick: () -> Unit
) {
  org.thoughtcrime.securesms.light.LightSettingsRow(
    title = stringResource(R.string.AddAllowedMembers__add_people_or_groups),
    onClick = onClick
  )
}

@Composable
fun NotificationProfileRecipient(
  recipient: Recipient,
  onRemoveClick: (RecipientId) -> Unit
) {
  val context = LocalContext.current
  val displayName by rememberRecipientField(recipient) { recipient.getDisplayName(context) }

  org.thoughtcrime.securesms.light.LightSettingsRow(
    title = displayName,
    onClick = null,
    trailing = {
      com.thelightphone.sdk.ui.LightText(
        text = stringResource(R.string.delete).uppercase(),
        variant = com.thelightphone.sdk.ui.LightTextVariant.Fine,
        modifier = Modifier.lightClickable { onRemoveClick(recipient.id) }
      )
    }
  )
}

@DayNightPreviews
@Composable
fun NotificationProfileAddMembersPreview() {
  Previews.Preview {
    NotificationProfileAddMembers(
      onClick = {}
    )
  }
}

@DayNightPreviews
@Composable
fun NotificationProfileRecipientPreview() {
  Previews.Preview {
    NotificationProfileRecipient(
      recipient = Recipient(
        id = RecipientId.from(1L),
        isResolving = false,
        registeredValue = RecipientTable.RegisteredState.REGISTERED,
        systemContactName = "Miles Morales"
      ),
      onRemoveClick = {}
    )
  }
}
