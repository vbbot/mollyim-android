/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.passphrase

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.thelightphone.sdk.ui.LightScrollBarPosition
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationAction
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationBottomActions

/**
 * The first-run database encryption choice. Only reached by routing from PassphraseRequiredActivity,
 * as the root of its own task, so the top bar has no back: system back finishes the activity, as it
 * always has. The activity owns both outcomes; this only renders them.
 */
@Composable
fun PassphraseCreateScreen(
  onNotNowClicked: () -> Unit,
  onEncryptClicked: () -> Unit,
) {
  MollyLightTheme {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(LightThemeTokens.colors.background),
    ) {
      LightTopBar(
        center = LightTopBarCenter.Text(text = stringResource(R.string.preferences__passphrase_lock)),
        modifier = Modifier.statusBarsPadding(),
      )

      LightScrollView(
        scrollBarPosition = LightScrollBarPosition.Outside,
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
      ) {
        // Start padding only: the Outside scroll bar already reserves a 2 grid unit gutter at the end.
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2f.gridUnitsAsDp(), top = 1f.gridUnitsAsDp(), bottom = 1f.gridUnitsAsDp()),
        ) {
          LightText(
            text = stringResource(R.string.PassphraseCreateActivity_now_you_can_set_a_passphrase_to_encrypt_the_database),
            variant = LightTextVariant.Copy,
          )
          Spacer(modifier = Modifier.height(1f.gridUnitsAsDp()))
          LightText(
            text = stringResource(R.string.PassphraseCreateActivity_require_passphrase_to_unlock_molly),
            variant = LightTextVariant.Copy,
          )
        }
      }

      // Short labels on purpose: at Button size with its 0.15em tracking, each half of the bar holds
      // about eight capitals, which "Turn on database encryption" and "No thanks" both overran.
      RegistrationBottomActions(
        left = RegistrationAction(
          label = stringResource(R.string.PassphraseCreateActivity__not_now),
          onClick = onNotNowClicked,
        ),
        right = RegistrationAction(
          label = stringResource(R.string.PassphraseCreateActivity__encrypt),
          onClick = onEncryptClicked,
        ),
        modifier = Modifier.navigationBarsPadding(),
      )
    }
  }
}

/** Java entry point: [PassphraseCreateScreen] as [activity]'s content. */
object PassphraseCreateContent {
  @JvmStatic
  fun setContent(activity: ComponentActivity, onNotNowClicked: Runnable, onEncryptClicked: Runnable) {
    activity.setContent {
      PassphraseCreateScreen(
        onNotNowClicked = onNotNowClicked::run,
        onEncryptClicked = onEncryptClicked::run,
      )
    }
  }
}
