/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.shared

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.registration.ui.phonenumber.PhoneNumberNotice
import org.thoughtcrime.securesms.registration.ui.phonenumber.PhoneNumberNoticeAction
import org.thoughtcrime.securesms.registration.ui.phonenumber.PhoneNumberNoticeModel

const val REGISTRATION_BUSY_TAG = "registration-busy"

/** The centred-status gutter, as on the restore progress screen. */
private const val BUSY_GUTTER_UNITS = 2f

/** The Light grammar's only progress affordance when there is nothing more specific to say. */
private const val BUSY_LABEL = "…"

/**
 * A full-screen Light notice in its own window: what a `Dialogs.SimpleAlertDialog`,
 * `AdvancedAlertDialog` or bottom sheet becomes on the restore and recovery-key screens.
 *
 * The body is the phone number step's [PhoneNumberNotice], unchanged, so every registration notice
 * reads the same: the title in the top bar beside BACK, the copy and the actions centred below. It
 * is hosted in a window rather than layered over the screen because its callers raise it from
 * wherever the Material dialog used to be composed -- often inside a screen's scrolling column, and
 * in `RegistrationErrorDialogs`' case from the local-backup key screen too -- and a window is the
 * one presentation that covers the whole screen from any of those places.
 *
 * Each action runs exactly what its Material button ran, in the same order; the caller builds them
 * so (dismiss, then act). [onBack] is what the dialog's cancel did. A notice cannot refuse BACK, so
 * the upstream dialogs that were not cancelable pass the exit their only button took, or the
 * dismissal their "Cancel" ran.
 *
 * The keyboard is put away as the notice opens: the recovery-key field is usually focused when an
 * error arrives, and the IME would otherwise sit over the notice's actions.
 */
@Composable
fun RegistrationNoticeDialog(
  title: String,
  message: String,
  actions: List<PhoneNumberNoticeAction>,
  onBack: () -> Unit,
  backDescription: String
) {
  val keyboardController = LocalSoftwareKeyboardController.current
  LaunchedEffect(Unit) {
    keyboardController?.hide()
  }

  Dialog(
    onDismissRequest = onBack,
    properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)
  ) {
    PhoneNumberNotice(
      notice = PhoneNumberNoticeModel(
        title = title,
        message = message,
        actions = actions,
        onBack = onBack,
        backDescription = backDescription
      ),
      onAction = { action -> action.onSelected() },
      onBack = onBack
    )
  }
}

/**
 * Work in flight that the user has to wait for: what `Dialogs.IndeterminateProgressDialog` becomes.
 *
 * Black to the edges with one centred `Heading` status line -- [message], or an ellipsis when there
 * is nothing more specific to say -- rather than a spinner in a rounded card. Like the dialog it
 * replaces it cannot be dismissed: back is swallowed and so is every tap.
 */
@Composable
fun RegistrationBusyDialog(message: String? = null) {
  Dialog(
    onDismissRequest = {},
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      dismissOnBackPress = false,
      dismissOnClickOutside = false
    )
  ) {
    BackHandler(onBack = {})

    MollyLightTheme {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(LightThemeTokens.colors.background)
          .pointerInput(Unit) {
            awaitEachGesture {
              awaitFirstDown(requireUnconsumed = false).consume()
              do {
                val event = awaitPointerEvent()
                event.changes.forEach { it.consume() }
              } while (event.changes.any { it.pressed })
            }
          }
          .padding(horizontal = BUSY_GUTTER_UNITS.gridUnitsAsDp())
          .testTag(REGISTRATION_BUSY_TAG),
        contentAlignment = Alignment.Center
      ) {
        LightText(
          text = message ?: BUSY_LABEL,
          variant = LightTextVariant.Heading,
          align = TextAlign.Center,
          modifier = Modifier.semantics {
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
          }
        )
      }
    }
  }
}
