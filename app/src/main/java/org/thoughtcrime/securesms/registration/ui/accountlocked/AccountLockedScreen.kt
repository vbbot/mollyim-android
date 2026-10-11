/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.accountlocked

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationAction
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationBottomActions
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen

/**
 * "Account locked", in the Light Phone III design language.
 *
 * The title is in the top bar, the explanation is body copy, and the two actions the screen always
 * had are the bottom action row: "learn more" on the left and "next", which closes registration, on
 * the right where the LP3 puts the forward action. There is no back icon: this is the end of the
 * registration flow, and back does exactly what "next" does, so a back glyph would be a second copy
 * of the same action pretending to be a different one.
 *
 * @param daysRemaining days until the number can be registered again without the PIN, or null until
 *   the view model has said. Null draws no explanation rather than one with a made-up number, which
 *   is what the old screen's empty description did in the same instant.
 */
@Composable
fun AccountLockedScreen(
  daysRemaining: Long?,
  onNext: () -> Unit,
  onLearnMore: () -> Unit
) {
  val description = daysRemaining?.let {
    AnnotatedString(stringResource(R.string.AccountLockedFragment__your_account_has_been_locked_to_protect_your_privacy, it))
  }

  RegistrationScreen(
    title = stringResource(R.string.AccountLockedFragment__account_locked),
    subtitle = description,
    bottomContent = {
      RegistrationBottomActions(
        left = RegistrationAction(stringResource(R.string.AccountLockedFragment__learn_more), onLearnMore),
        right = RegistrationAction(stringResource(R.string.AccountLockedFragment__next), onNext)
      )
    },
    mainContent = {}
  )
}

@DayNightPreviews
@Composable
private fun AccountLockedScreenPreview() {
  Previews.Preview {
    AccountLockedScreen(daysRemaining = 7, onNext = {}, onLearnMore = {})
  }
}
