/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.mutiselect.forward

import androidx.compose.runtime.annotation.RememberInComposition
import org.thoughtcrime.securesms.color.ViewColorSet
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.recipients.Recipient

data class MultiselectForwardBottomBarState @RememberInComposition constructor(
  val selection: List<SelectedContact> = emptyList(),
  val message: String = "",
  val sendButtonColors: ViewColorSet = ViewColorSet.PRIMARY,
  val isSendButtonEnabled: Boolean = true,
  val isSendButtonVisible: Boolean = false,
  val isAddMessageVisible: Boolean = false
) {
  sealed interface SelectedContact {

    val key: String

    data class KnownRecipient(
      val destination: ContactSearchKey.RecipientSearchKey,
      val recipient: Recipient
    ) : SelectedContact {
      override val key: String = "recipient:${destination.recipientId.toLong()}:story=${destination.isStory}"
    }

    data class UnknownRecipient(
      val selectionKey: ContactSearchKey,
      val e164: String
    ) : SelectedContact {
      override val key: String = "unknown:${selectionKey}:$e164"
    }
  }
}
