/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.light

import android.app.Application
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.signal.core.ui.CoreUiDependenciesRule
import org.signal.core.ui.compose.ProvideIncognitoKeyboard

/**
 * The keyboard must never take over the LP3's 413dp screen with its full-screen extract view.
 *
 * What is pinned is that the flags reach the [EditorInfo] the IME is handed -- for a Compose field
 * inside [MollyLightTheme], alongside (not instead of) the incognito flag, and for a View field
 * without losing the host's IME action. Whether a given IME then honours them is the device's
 * business, and wants checking on the LP3.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class LightNoExtractUiKeyboardTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `a Light compose field asks the IME for no extract UI and no fullscreen`() {
    val editorInfo = focusFieldAndCreateConnection { field -> MollyLightTheme { field() } }

    assertThat(editorInfo.imeOptions and LIGHT_NO_EXTRACT_UI_IME_FLAGS).isEqualTo(LIGHT_NO_EXTRACT_UI_IME_FLAGS)
  }

  @Test
  fun `the incognito flag survives with the incognito interceptor inside the Light theme`() {
    val editorInfo = focusFieldAndCreateConnection { field ->
      MollyLightTheme { ProvideIncognitoKeyboard(enabled = true) { field() } }
    }

    assertThat(editorInfo.imeOptions and LIGHT_NO_EXTRACT_UI_IME_FLAGS).isEqualTo(LIGHT_NO_EXTRACT_UI_IME_FLAGS)
    assertThat(editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING).isEqualTo(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
  }

  /** The order `SignalTheme` produces: its incognito interceptor outside, the Light theme inside. */
  @Test
  fun `the incognito flag survives with the Light theme inside the incognito interceptor`() {
    val editorInfo = focusFieldAndCreateConnection { field ->
      ProvideIncognitoKeyboard(enabled = true) { MollyLightTheme { field() } }
    }

    assertThat(editorInfo.imeOptions and LIGHT_NO_EXTRACT_UI_IME_FLAGS).isEqualTo(LIGHT_NO_EXTRACT_UI_IME_FLAGS)
    assertThat(editorInfo.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING).isEqualTo(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
  }

  @Test
  fun `a View field keeps its action and its other flags`() {
    val editText = EditText(RuntimeEnvironment.getApplication())
    editText.imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING

    editText.applyLightNoExtractUi()

    assertThat(editText.imeOptions and EditorInfo.IME_MASK_ACTION).isEqualTo(EditorInfo.IME_ACTION_DONE)
    assertThat(editText.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING).isEqualTo(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
    assertThat(editText.imeOptions and LIGHT_NO_EXTRACT_UI_IME_FLAGS).isEqualTo(LIGHT_NO_EXTRACT_UI_IME_FLAGS)
  }

  /**
   * Focuses the one text field in [wrap] and asks the host view for an input connection, exactly as
   * the platform does when the IME binds, returning the [EditorInfo] the IME would have been handed.
   */
  private fun focusFieldAndCreateConnection(wrap: @Composable (@Composable () -> Unit) -> Unit): EditorInfo {
    lateinit var view: View

    composeTestRule.setContent {
      view = LocalView.current
      wrap {
        BasicTextField(value = "", onValueChange = {})
      }
    }

    composeTestRule.onNode(hasSetTextAction()).performClick()

    return composeTestRule.runOnIdle {
      EditorInfo().also { view.onCreateInputConnection(it) }
    }
  }
}
