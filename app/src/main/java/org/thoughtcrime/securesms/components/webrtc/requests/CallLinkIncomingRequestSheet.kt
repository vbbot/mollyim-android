/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.requests

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rxjava3.subscribeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import org.signal.core.ui.BottomSheetUtil
import org.signal.core.ui.compose.BottomSheets
import org.signal.core.ui.compose.ComposeBottomSheetDialogFragment
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.NightPreview
import org.signal.core.ui.compose.Previews
import org.signal.core.util.getParcelableCompat
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.light.rememberLightTintedPainter
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.viewModel
import org.signal.core.ui.R as CoreUiR

/**
 * Displayed when the user presses the user avatar in the call link join request
 * bar.
 */
class CallLinkIncomingRequestSheet : ComposeBottomSheetDialogFragment() {

  companion object {
    private const val RECIPIENT_ID = "recipient_id"

    @JvmStatic
    fun show(fragmentManager: FragmentManager, recipientId: RecipientId) {
      CallLinkIncomingRequestSheet().apply {
        arguments = bundleOf(
          RECIPIENT_ID to recipientId
        )
      }.show(fragmentManager, BottomSheetUtil.STANDARD_BOTTOM_SHEET_FRAGMENT_TAG)
    }
  }

  override val forceDarkTheme = true

  private val recipientId: RecipientId by lazy {
    requireArguments().getParcelableCompat(RECIPIENT_ID, RecipientId::class.java)!!
  }

  private val viewModel by viewModel {
    CallLinkIncomingRequestViewModel(requireContext(), recipientId)
  }

  @Composable
  override fun SheetContent() {
    val state = viewModel.observeState(LocalContext.current).subscribeAsState(initial = CallLinkIncomingRequestState())
    if (state.value.recipient == Recipient.UNKNOWN) {
      return
    }

    MollyLightTheme {
      CallLinkIncomingRequestSheetContent(
        state = state.value,
        onApproveEntry = this::onApproveEntry,
        onDenyEntry = this::onDenyEntry
      )
    }
  }

  private fun onApproveEntry() {
    AppDependencies.signalCallManager.setCallLinkJoinRequestAccepted(recipientId)
    dismissAllowingStateLoss()
  }

  private fun onDenyEntry() {
    AppDependencies.signalCallManager.setCallLinkJoinRequestRejected(recipientId)
    dismissAllowingStateLoss()
  }
}

@NightPreview
@Composable
private fun CallLinkIncomingRequestSheetContentPreview() {
  Previews.BottomSheetContentPreview {
    MollyLightTheme {
      CallLinkIncomingRequestSheetContent(
        state = CallLinkIncomingRequestState(
          name = "Miles Morales",
          subtitle = "+1 (555) 555-5555",
          groupsInCommon = "Member of Webheads, Group B, Group C, Group D, and 83 others.",
          isSystemContact = true
        ),
        onApproveEntry = {},
        onDenyEntry = {}
      )
    }
  }
}

@Composable
private fun CallLinkIncomingRequestSheetContent(
  state: CallLinkIncomingRequestState,
  onApproveEntry: () -> Unit,
  onDenyEntry: () -> Unit
) {
  LazyColumn(
    modifier = Modifier
      .fillMaxWidth()
      .background(LightThemeTokens.colors.background),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    item { BottomSheets.Handle() }
    item {
      AvatarImage(
        recipient = state.recipient,
        modifier = Modifier
          .padding(top = 1f.gridUnitsAsDp())
          .size(6f.gridUnitsAsDp())
      )
    }
    item {
      Title(
        recipientName = state.name,
        isSystemContact = state.isSystemContact
      )
    }

    if (state.subtitle.isNotEmpty()) {
      item {
        LightText(
          text = state.subtitle,
          variant = LightTextVariant.Detail,
          lighten = true,
          align = TextAlign.Center,
          modifier = Modifier.padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.5f.gridUnitsAsDp())
        )
      }
    }

    if (state.groupsInCommon.isNotEmpty()) {
      item {
        LightText(
          text = state.groupsInCommon,
          variant = LightTextVariant.Paragraph,
          lighten = true,
          align = TextAlign.Center,
          modifier = Modifier.padding(horizontal = 2f.gridUnitsAsDp(), vertical = 0.5f.gridUnitsAsDp())
        )
      }
    }

    item { Dividers.Default() }
    item {
      LightBottomBar(
        items = listOf(
          LightBarButton.Text(
            text = stringResource(R.string.CallLinkIncomingRequestSheet__approve_entry),
            onClick = onApproveEntry
          ),
          LightBarButton.Text(
            text = stringResource(R.string.CallLinkIncomingRequestSheet__deny_entry),
            onClick = onDenyEntry
          )
        )
      )
    }

    item { Spacer(modifier = Modifier.size(1f.gridUnitsAsDp())) }
  }
}

@Composable
private fun Title(
  recipientName: String,
  isSystemContact: Boolean
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(top = 0.75f.gridUnitsAsDp(), start = 2f.gridUnitsAsDp(), end = 2f.gridUnitsAsDp()),
    verticalAlignment = Alignment.CenterVertically
  ) {
    LightText(
      text = recipientName,
      variant = LightTextVariant.Heading,
      align = TextAlign.Center,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f)
    )
    if (isSystemContact) {
      Image(
        painter = rememberLightTintedPainter(CoreUiR.drawable.symbol_person_circle_24),
        contentDescription = null,
        modifier = Modifier
          .padding(start = 0.5f.gridUnitsAsDp())
          .size(1.5f.gridUnitsAsDp())
      )
    }
  }
}
