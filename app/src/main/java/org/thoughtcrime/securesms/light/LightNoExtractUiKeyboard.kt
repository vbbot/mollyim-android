/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.light

import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

/**
 * The IME flags that keep the keyboard out of its full-screen "extract" mode.
 *
 * The LP3's screen is 413dp tall. An IME decides for itself when a screen is too short to show the
 * field above the keyboard, and when it does it replaces the whole app with its own extract view --
 * the field's text in a bare editor, with the keyboard underneath. Compose only ever sets
 * [EditorInfo.IME_FLAG_NO_FULLSCREEN]; [EditorInfo.IME_FLAG_NO_EXTRACT_UI] is the one that rules
 * out the extract view itself. Both are presentation-only: neither touches the action, the input
 * type or the incognito flag.
 */
const val LIGHT_NO_EXTRACT_UI_IME_FLAGS: Int = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN

/** ORs [LIGHT_NO_EXTRACT_UI_IME_FLAGS] into [imeOptions], keeping the action and every other flag. */
fun withLightNoExtractUi(imeOptions: Int): Int = imeOptions or LIGHT_NO_EXTRACT_UI_IME_FLAGS

/**
 * [withLightNoExtractUi] for a View-side field.
 *
 * `setImeOptions` replaces the whole word, so a host that assigns an action afterwards
 * (`imeOptions = IME_ACTION_DONE`) drops these flags again. Call this after the host has set its
 * action, or -- for a field this app constructs -- add them in `onCreateInputConnection` instead,
 * which nothing can overwrite.
 */
fun EditText.applyLightNoExtractUi() {
  imeOptions = withLightNoExtractUi(imeOptions)
}

/**
 * Adds [LIGHT_NO_EXTRACT_UI_IME_FLAGS] to every Compose text field within [content].
 *
 * The same [InterceptPlatformTextInput] seam `ProvideIncognitoKeyboard` uses, for the same reason:
 * Compose offers no per-field IME flags (https://issuetracker.google.com/issues/359257538).
 *
 * Interceptors chain rather than replace one another. The innermost runs first and hands its
 * wrapped request to `nextHandler`, which is the next interceptor out; each wrapper calls the
 * request it was given before OR-ing its own flag in. So nested inside `SignalTheme`'s incognito
 * interceptor -- or with one nested inside this -- both flags reach the [EditorInfo], whichever
 * order they sit in.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ProvideLightNoExtractUiKeyboard(content: @Composable () -> Unit) {
  InterceptPlatformTextInput(
    interceptor = { request, nextHandler ->
      val modifiedRequest = PlatformTextInputMethodRequest { outAttributes ->
        request.createInputConnection(outAttributes).also {
          outAttributes.imeOptions = withLightNoExtractUi(outAttributes.imeOptions)
        }
      }
      nextHandler.startInputMethod(modifiedRequest)
    }
  ) {
    content()
  }
}
