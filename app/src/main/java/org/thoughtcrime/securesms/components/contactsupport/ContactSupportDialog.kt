/*
 * Copyright 2025 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.contactsupport

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.registration.ui.phonenumber.PhoneNumberNoticeAction
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationBusyDialog
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationNoticeDialog
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.SupportEmailUtil

interface ContactSupportCallbacks {
  fun submitWithDebuglog()
  fun submitWithoutDebuglog()
  fun cancel()

  object Empty : ContactSupportCallbacks {
    override fun submitWithDebuglog() = Unit
    override fun submitWithoutDebuglog() = Unit
    override fun cancel() = Unit
  }

  fun interface StringForReason<Reason> {
    @StringRes
    operator fun invoke(reason: Reason?): Int
  }
}

/**
 * Three-option contact support notice.
 *
 * A full-screen Light notice in its own window, so it covers the screen from wherever a caller
 * composes it, as the Material dialog did. The actions are the dialog's three buttons top to bottom
 * (include the debug log, leave it out, cancel), shortened to fit on one line in capitals; the
 * dialog's own title and explanation say what they choose between. Like the dialog, an action does
 * not dismiss anything itself -- [callbacks] owns that -- and BACK cancels.
 *
 * While the debug log is being collected it is a full-screen status line instead of a spinner.
 */
@Composable
fun ContactSupportDialog(
  showInProgress: Boolean,
  callbacks: ContactSupportCallbacks
) {
  if (showInProgress) {
    RegistrationBusyDialog(message = stringResource(R.string.ContactSupportDialog__preparing_debug_log))
  } else {
    RegistrationNoticeDialog(
      title = stringResource(R.string.ContactSupportDialog_submit_debug_log),
      message = stringResource(R.string.ContactSupportDialog_your_debug_logs),
      actions = listOf(
        PhoneNumberNoticeAction(stringResource(R.string.ContactSupportDialog__include_log)) { callbacks.submitWithDebuglog() },
        PhoneNumberNoticeAction(stringResource(R.string.ContactSupportDialog__skip_log)) { callbacks.submitWithoutDebuglog() },
        PhoneNumberNoticeAction(stringResource(android.R.string.cancel)) { callbacks.cancel() }
      ),
      onBack = { callbacks.cancel() },
      backDescription = stringResource(android.R.string.cancel)
    )
  }
}

/**
 * Used in conjunction with [ContactSupportDialog] and [ContactSupportViewModel] to trigger
 * sending an email when ready.
 */
@Composable
fun <Reason> SendSupportEmailEffect(
  contactSupportState: ContactSupportViewModel.ContactSupportState<Reason>,
  subjectRes: ContactSupportCallbacks.StringForReason<Reason>,
  filterRes: ContactSupportCallbacks.StringForReason<Reason>,
  hide: () -> Unit
) {
  val subject = stringResource(subjectRes(contactSupportState.reason))
  val helpDebugLog = stringResource(R.string.HelpFragment__debug_log)

  val context = LocalContext.current
  LaunchedEffect(contactSupportState.sendEmail) {
    if (contactSupportState.sendEmail) {
      val prefix = if (contactSupportState.debugLogUrl != null) {
        "\n$helpDebugLog ${contactSupportState.debugLogUrl}\n\n"
      } else {
        ""
      }

      val body = SupportEmailUtil.generateSupportEmailBody(context, filterRes(contactSupportState.reason), prefix, null)
      CommunicationActions.openEmail(context, SupportEmailUtil.getSupportEmailAddress(context), subject, body)
      hide()
    }
  }
}

@DayNightPreviews
@Composable
private fun ContactSupportDialogPreview() {
  Previews.Preview {
    ContactSupportDialog(
      false,
      ContactSupportCallbacks.Empty
    )
  }
}
