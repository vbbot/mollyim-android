/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.passphrase

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import assertk.assertThat
import assertk.assertions.containsExactly
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
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class PassphraseCreateScreenTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  private val clicks = mutableListOf<String>()

  @Test
  fun `title is in the top bar and both paragraphs are shown`() {
    setScreen()

    composeTestRule.onNodeWithText("Database encryption").assertExists()
    composeTestRule.onNodeWithText("Now you can set a passphrase", substring = true).assertExists()
    composeTestRule.onNodeWithText("Require passphrase to unlock Molly?").assertExists()
  }

  @Test
  fun `the first-run root has no back`() {
    setScreen()

    composeTestRule.onAllNodes(hasContentDescription("Back", substring = true, ignoreCase = true)).assertCountEquals(0)
  }

  @Test
  fun `ENCRYPT and NOT NOW report their own choice`() {
    setScreen()

    composeTestRule.onNodeWithText("ENCRYPT").performClick()
    composeTestRule.onNodeWithText("NOT NOW").performClick()

    assertThat(clicks).containsExactly("encrypt", "not now")
  }

  private fun setScreen() {
    composeTestRule.setContent {
      PassphraseCreateScreen(
        onNotNowClicked = { clicks += "not now" },
        onEncryptClicked = { clicks += "encrypt" },
      )
    }
  }
}
