/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.contactsupport

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.signal.core.ui.CoreUiDependenciesRule
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.registration.ui.shared.REGISTRATION_BUSY_TAG
import java.util.Locale

/**
 * The contact support choice as a Light notice. It is shared beyond registration (the backup alert
 * sheets raise it through [ContactSupportDialogFragment]), so each of its three exits has to reach
 * exactly the callback the Material dialog's button did, and BACK has to cancel as the dialog's
 * dismissal did.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class ContactSupportDialogTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  private val calls = mutableListOf<String>()

  private val callbacks = object : ContactSupportCallbacks {
    override fun submitWithDebuglog() {
      calls += "with log"
    }

    override fun submitWithoutDebuglog() {
      calls += "without log"
    }

    override fun cancel() {
      calls += "cancel"
    }
  }

  @Test
  fun `the notice keeps the dialog's title and explanation`() {
    show(showInProgress = false)

    composeTestRule.onNodeWithText(string(R.string.ContactSupportDialog_submit_debug_log)).assertExists()
    composeTestRule.onNodeWithText(string(R.string.ContactSupportDialog_your_debug_logs)).assertExists()
  }

  @Test
  fun `INCLUDE LOG submits with the debug log`() {
    show(showInProgress = false)

    composeTestRule.onNodeWithText(action(R.string.ContactSupportDialog__include_log)).performClick()

    assertThat(calls).containsExactly("with log")
  }

  @Test
  fun `SKIP LOG submits without the debug log`() {
    show(showInProgress = false)

    composeTestRule.onNodeWithText(action(R.string.ContactSupportDialog__skip_log)).performClick()

    assertThat(calls).containsExactly("without log")
  }

  @Test
  fun `CANCEL cancels`() {
    show(showInProgress = false)

    composeTestRule.onNodeWithText(action(android.R.string.cancel)).performClick()

    assertThat(calls).containsExactly("cancel")
  }

  @Test
  fun `BACK cancels`() {
    show(showInProgress = false)

    composeTestRule.onNodeWithContentDescription(string(android.R.string.cancel)).performClick()

    assertThat(calls).containsExactly("cancel")
  }

  @Test
  fun `in progress is a status line with no actions`() {
    show(showInProgress = true)

    composeTestRule.onNodeWithTag(REGISTRATION_BUSY_TAG).assertExists()
    composeTestRule.onNodeWithText(string(R.string.ContactSupportDialog__preparing_debug_log)).assertExists()
    composeTestRule.onNodeWithText(action(R.string.ContactSupportDialog__include_log)).assertDoesNotExist()
    assertThat(calls).isEmpty()
  }

  private fun show(showInProgress: Boolean) {
    composeTestRule.setContent {
      ContactSupportDialog(showInProgress = showInProgress, callbacks = callbacks)
    }
  }

  private fun string(@StringRes id: Int): String = RuntimeEnvironment.getApplication().getString(id)

  private fun action(@StringRes id: Int): String = string(id).uppercase(Locale.getDefault())
}
