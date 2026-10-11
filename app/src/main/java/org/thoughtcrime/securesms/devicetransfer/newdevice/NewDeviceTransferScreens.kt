/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.devicetransfer.newdevice

import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationAction
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationBack
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationBottomActions
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen

/**
 * What to do on the old phone before the new one starts listening. Was a 28sp headline, a numbered
 * grid, a looping Lottie of the Settings screen and a filled pill button. Now the registration
 * grammar: "Transfer Account" in the top bar, the headline's instruction as the explanatory line, the
 * three steps as Copy, and CONTINUE. The animation is dropped: it illustrated the steps the text
 * already gives, and a looping animation is chrome on a Light surface.
 *
 * BACK runs [onBack], which the fragment points at system back, so it does exactly what back did.
 */
@Composable
fun NewDeviceTransferInstructionsScreen(
  onContinue: () -> Unit,
  onBack: () -> Unit
) {
  RegistrationScreen(
    title = stringResource(R.string.OldDeviceTransferInstructions__transfer_account),
    subtitle = stringResource(R.string.NewDeviceTransferInstructions__open_signal_on_your_old_android_phone),
    bottomContent = {
      RegistrationBottomActions(
        center = RegistrationAction(stringResource(R.string.NewDeviceTransferInstructions__continue), onContinue)
      )
    },
    back = RegistrationBack(
      contentDescription = stringResource(R.string.RegistrationScreen__back_to_previous_screen),
      onBack = onBack
    )
  ) {
    InstructionStep(
      bullet = stringResource(R.string.NewDeviceTransferInstructions__first_bullet),
      text = stringResource(R.string.NewDeviceTransferInstructions__tap_on_your_profile_photo_in_the_top_left_to_open_settings)
    )
    InstructionStep(
      bullet = stringResource(R.string.NewDeviceTransferInstructions__second_bullet),
      text = stringResource(R.string.NewDeviceTransferInstructions__tap_on_account)
    )
    InstructionStep(
      bullet = stringResource(R.string.NewDeviceTransferInstructions__third_bullet),
      text = stringResource(R.string.NewDeviceTransferInstructions__tap_transfer_account_and_then_continue_on_both_devices)
    )
  }
}

@Composable
private fun InstructionStep(bullet: String, text: String) {
  LightText(
    text = "$bullet $text",
    variant = LightTextVariant.Copy,
    modifier = Modifier.padding(bottom = 0.75f.gridUnitsAsDp())
  )
}

/**
 * Shown once the new device has received everything. Was a 28sp headline, a green check in a circle
 * and a filled pill button; now the result in the top bar, the one thing left to do as the
 * explanatory line, and CONTINUE REGISTRATION. There is no BACK: the fragment swallows back here,
 * because there is nothing to go back to.
 */
@Composable
fun NewDeviceTransferCompleteScreen(onContinueRegistration: () -> Unit) {
  RegistrationScreen(
    title = stringResource(R.string.NewDeviceTransferComplete__transfer_successful),
    subtitle = stringResource(R.string.NewDeviceTransferComplete__to_complete_the_transfer_process_you_must_continue_registration),
    bottomContent = {
      RegistrationBottomActions(
        center = RegistrationAction(stringResource(R.string.NewDeviceTransferComplete__continue_registration), onContinueRegistration)
      )
    }
  ) {
    // The layout's "Transfer complete" caption under the check said what the top bar now says.
  }
}

/** Compose hosts for the Java fragments, which cannot build a `ComposeView`'s content themselves. */
object NewDeviceTransferViews {
  @JvmStatic
  fun complete(context: Context, onContinueRegistration: Runnable): View {
    return lightComposeView(context) {
      NewDeviceTransferCompleteScreen(onContinueRegistration = { onContinueRegistration.run() })
    }
  }

  @JvmStatic
  fun instructions(context: Context, onContinue: Runnable, onBack: Runnable): View {
    return lightComposeView(context) {
      NewDeviceTransferInstructionsScreen(
        onContinue = { onContinue.run() },
        onBack = { onBack.run() }
      )
    }
  }

  private fun lightComposeView(context: Context, content: @Composable () -> Unit): View {
    return ComposeView(context).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setBackgroundColor(Color.BLACK)
      setContent(content)
    }
  }
}

@DayNightPreviews
@Composable
private fun NewDeviceTransferInstructionsScreenPreview() {
  Previews.Preview {
    NewDeviceTransferInstructionsScreen(onContinue = {}, onBack = {})
  }
}

@DayNightPreviews
@Composable
private fun NewDeviceTransferCompleteScreenPreview() {
  Previews.Preview {
    NewDeviceTransferCompleteScreen(onContinueRegistration = {})
  }
}
