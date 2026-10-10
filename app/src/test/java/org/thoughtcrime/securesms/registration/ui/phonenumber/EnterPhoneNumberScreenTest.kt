/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.phonenumber

import android.app.Application
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.signal.core.ui.CoreUiDependenciesRule

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class EnterPhoneNumberScreenTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  private val committed = mutableListOf<String>()
  private var editorClosed = false
  private var numberTapped = false
  private var settingsTapped = false

  @Test
  fun `typed digits are formatted and DONE commits the whole number`() {
    setScreen(phoneNumber = "+1", editorOpen = true)

    composeTestRule.onNode(hasSetTextAction()).performTextInput("4155550132")
    composeTestRule.onNode(hasSetTextAction()).assertTextEquals("+1 415-555-0132")
    composeTestRule.onNodeWithText("DONE").performClick()

    assertThat(committed).containsExactly("+1 415-555-0132")
  }

  @Test
  fun `editor BACK discards without committing`() {
    setScreen(phoneNumber = "+44", editorOpen = true)

    composeTestRule.onNode(hasSetTextAction()).performTextInput("7700")
    composeTestRule.onNodeWithContentDescription("Close without saving").performClick()

    assertThat(editorClosed).isTrue()
    assertThat(committed.size).isEqualTo(0)
  }

  @Test
  fun `form shows the number, opens the editor, and keeps the settings action`() {
    setScreen(phoneNumber = "+44 7700 900123", editorOpen = false, nextEnabled = false)

    composeTestRule.onNodeWithText("+44 7700 900123").performClick()
    composeTestRule.onNodeWithContentDescription("Network settings").performClick()
    composeTestRule.onNodeWithText("NEXT").assertIsNotEnabled()

    assertThat(numberTapped).isTrue()
    assertThat(settingsTapped).isTrue()
  }

  private fun setScreen(phoneNumber: String, editorOpen: Boolean, nextEnabled: Boolean = true) {
    composeTestRule.setContent {
      EnterPhoneNumberScreen(
        countryEmoji = null,
        countryName = "United Kingdom",
        countrySelected = true,
        phoneNumber = phoneNumber,
        editorOpen = editorOpen,
        nextEnabled = nextEnabled,
        inProgress = false,
        showCancel = false,
        controlsEnabled = true,
        onBackClicked = {},
        onCountryClicked = {},
        onPhoneNumberClicked = { numberTapped = true },
        onEditorBack = { editorClosed = true },
        onEditorDone = { committed += it },
        onNextClicked = {},
        onCancelClicked = {},
        onProxyClicked = { settingsTapped = true },
      )
    }
  }
}
