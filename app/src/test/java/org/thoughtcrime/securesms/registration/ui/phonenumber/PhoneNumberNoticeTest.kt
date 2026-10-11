/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.phonenumber

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.signal.core.ui.CoreUiDependenciesRule
import org.thoughtcrime.securesms.R

/**
 * The Light notices that replace the phone number step's `MaterialAlertDialog`s.
 *
 * The confirm-number notice is the one with three distinct exits, and each has to run its own
 * callback and nothing else -- the fragment wires them to:
 *
 *  - OK -> `onUserConfirmedPhoneNumber` (and must *not* clear the in-progress state, or the NEXT
 *    button comes back live while the code request is still going);
 *  - EDIT NUMBER -> `setInProgress(false)` and reopen the editor;
 *  - BACK icon / system back (Material's cancel) -> `setInProgress(false)` only.
 *
 * The fragment's lambdas are one-liners onto the view model; what can go wrong is which lambda a
 * gesture reaches, and that is what is pinned here, through [confirmNumberNotice] and the host
 * exactly as the fragment uses them.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class PhoneNumberNoticeTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  private val calls = mutableListOf<String>()
  private val notices = PhoneNumberNotices()
  private lateinit var backDispatcher: OnBackPressedDispatcher

  private val backLabel: String
    get() = RuntimeEnvironment.getApplication().getString(R.string.EnterPhoneNumberFragment__back_to_phone_number)

  @Test
  fun `confirm OK confirms and does nothing else`() {
    showConfirm()

    composeTestRule.onNodeWithText("OK").performClick()

    assertThat(calls).containsExactly("confirm")
    assertNoticeClosed()
  }

  @Test
  fun `confirm EDIT NUMBER only returns to editing`() {
    showConfirm()

    composeTestRule.onNodeWithText("EDIT NUMBER").performClick()

    assertThat(calls).containsExactly("edit")
    assertNoticeClosed()
  }

  @Test
  fun `confirm BACK icon cancels`() {
    showConfirm()

    composeTestRule.onNodeWithContentDescription(backLabel).performClick()

    assertThat(calls).containsExactly("cancel")
    assertNoticeClosed()
  }

  @Test
  fun `system back on the confirm notice cancels`() {
    showConfirm()

    composeTestRule.runOnIdle { backDispatcher.onBackPressed() }

    assertThat(calls).containsExactly("cancel")
    assertNoticeClosed()
  }

  @Test
  fun `confirm shows the number and the code line only when a code will be sent`() {
    showConfirm(codeWillBeSent = "A verification code will be sent to this number.")

    composeTestRule.onNodeWithText("Is the phone number below correct?").assertIsDisplayed()
    composeTestRule.onNodeWithText("+1 415-555-0132").assertIsDisplayed()
    composeTestRule.onNodeWithText("A verification code will be sent to this number.").assertIsDisplayed()
  }

  @Test
  fun `confirm omits the code line when SMS can be skipped`() {
    showConfirm(codeWillBeSent = null)

    composeTestRule.onNodeWithText("+1 415-555-0132").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("A verification code will be sent to this number.").assertCountEquals(0)
  }

  /** An error notice: OK runs its action (the fragment's `clearError`), BACK runs `onBack`. */
  @Test
  fun `an error notice runs OK and BACK separately`() {
    val error = PhoneNumberNoticeModel(
      title = "Invalid number",
      message = "The number you specified is invalid.",
      actions = listOf(PhoneNumberNoticeAction("OK") { calls += "ok" }),
      onBack = { calls += "back" }
    )

    setHost()
    composeTestRule.runOnIdle { notices.show(error) }
    composeTestRule.onNodeWithText("OK").performClick()

    composeTestRule.runOnIdle { notices.show(error) }
    composeTestRule.onNodeWithContentDescription(backLabel).performClick()

    assertThat(calls).containsExactly("ok", "back")
    assertNoticeClosed()
  }

  /** Material dialogs stacked as windows; the newest is on top and the previous one is back after it. */
  @Test
  fun `the newest notice is on top and the previous one returns after it`() {
    setHost()
    composeTestRule.runOnIdle {
      notices.show(PhoneNumberNoticeModel(title = "First", message = "first", actions = listOf(PhoneNumberNoticeAction("OK") {})))
      notices.show(PhoneNumberNoticeModel(title = "Second", message = "second", actions = listOf(PhoneNumberNoticeAction("OK") {})))
    }

    composeTestRule.onNodeWithText("second").assertIsDisplayed()
    composeTestRule.onAllNodesWithText("first").assertCountEquals(0)

    composeTestRule.onNodeWithText("OK").performClick()

    composeTestRule.onNodeWithText("first").assertIsDisplayed()
  }

  /** A tap on the notice's empty space must not reach the form underneath. */
  @Test
  fun `the notice consumes taps in its empty space`() {
    var formTapped = 0
    composeTestRule.setContent {
      backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
      Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().clickable { formTapped++ })
        PhoneNumberNoticeHost(notices)
      }
    }
    composeTestRule.runOnIdle { showConfirmModel() }

    composeTestRule.onNodeWithTag(PHONE_NUMBER_NOTICE_TAG).performTouchInput {
      click(Offset(x = 4f, y = height - 4f))
    }

    assertThat(formTapped).isEqualTo(0)
    assertThat(calls).isEmpty()
  }

  @Test
  fun `clearing drops every notice without running anything`() {
    setHost()
    composeTestRule.runOnIdle {
      showConfirmModel()
      notices.clear()
    }

    assertNoticeClosed()
    assertThat(calls).isEmpty()
  }

  private fun showConfirm(codeWillBeSent: String? = "A verification code will be sent to this number.") {
    setHost()
    composeTestRule.runOnIdle { showConfirmModel(codeWillBeSent) }
  }

  private fun showConfirmModel(codeWillBeSent: String? = "A verification code will be sent to this number.") {
    notices.show(
      confirmNumberNotice(
        title = "Is the phone number below correct?",
        number = "+1 415-555-0132",
        codeWillBeSent = codeWillBeSent,
        okLabel = "OK",
        editNumberLabel = "Edit number",
        onConfirm = { calls += "confirm" },
        onEditNumber = { calls += "edit" },
        onCancel = { calls += "cancel" }
      )
    )
  }

  private fun setHost() {
    composeTestRule.setContent {
      backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
      PhoneNumberNoticeHost(notices)
    }
  }

  private fun assertNoticeClosed() {
    composeTestRule.onAllNodesWithTag(PHONE_NUMBER_NOTICE_TAG).assertCountEquals(0)
    assertThat(notices.current).isNull()
  }
}
