/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.accountlocked

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.fragment.app.activityViewModels
import org.signal.core.ui.compose.ComposeFragment
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import kotlin.time.Duration.Companion.milliseconds

/**
 * Screen educating the user that they need to wait some number of days to register.
 *
 * Presentation is [AccountLockedScreen]: the title in the top bar, where it also keeps the debug-log
 * multi-tap the old title `TextView` carried, the explanation as body copy, and "learn more" and
 * "next" as the screen's two bottom actions. What they do, the days-remaining arithmetic and the
 * back behaviour are unchanged.
 */
class AccountLockedFragment : ComposeFragment() {
  private val viewModel by activityViewModels<RegistrationViewModel>()

  @Composable
  override fun FragmentContent() {
    val timeRemaining: Long? by viewModel.lockedTimeRemaining.observeAsState()

    AccountLockedScreen(
      daysRemaining = timeRemaining?.let(::durationToDays),
      onNext = ::onNext,
      onLearnMore = ::learnMore
    )
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    requireActivity().onBackPressedDispatcher.addCallback(
      viewLifecycleOwner,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          onNext()
        }
      }
    )
  }

  private fun learnMore() {
    val intent = Intent(Intent.ACTION_VIEW)
    intent.setData(Uri.parse(getString(R.string.AccountLockedFragment__learn_more_url)))
    startActivity(intent)
  }

  fun onNext() {
    requireActivity().finish()
  }

  private fun durationToDays(duration: Long): Long {
    return if (duration != 0L) getLockoutDays(duration).toLong() else 7
  }

  private fun getLockoutDays(timeRemainingMs: Long): Int {
    return timeRemainingMs.milliseconds.inWholeDays.toInt() + 1
  }
}
