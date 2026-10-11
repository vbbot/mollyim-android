/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.devicetransfer

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import org.thoughtcrime.securesms.devicetransfer.newdevice.NewDeviceTransferCompleteScreen
import org.thoughtcrime.securesms.devicetransfer.newdevice.NewDeviceTransferInstructionsScreen
import org.thoughtcrime.securesms.registration.ui.restore.local.LIGHT_RESTORE_NOTICE_TAG
import org.thoughtcrime.securesms.restore.devicetransfer.DeviceTransferContent
import org.thoughtcrime.securesms.restore.devicetransfer.DeviceTransferLightView

/**
 * The new device's transfer screens in Light: structure, and which of the fragment's callbacks each
 * button reaches. The state machine itself is untouched in the fragments; these pin the presentation
 * that replaced the layouts and the Material dialogs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class DeviceTransferLightScreensTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  private val context = RuntimeEnvironment.getApplication()
  private val calls = mutableListOf<String>()
  private lateinit var backDispatcher: OnBackPressedDispatcher

  private fun string(id: Int): String = context.getString(id)
  private fun upper(id: Int): String = string(id).uppercase()

  private fun setContent(content: @Composable () -> Unit) {
    composeTestRule.setContent {
      backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
      content()
    }
  }

  private fun setupContent(screen: SetupScreen, status: String = "", notice: SetupNotice? = null) {
    setContent {
      var current by remember { mutableStateOf(notice) }
      DeviceTransferSetupContent(
        screen = screen,
        status = status,
        notice = current,
        onBack = { calls += "back" },
        onCloseNotice = { calls += "close"; current = null }
      )
    }
  }

  @Test
  fun `instructions show the three steps and continue`() {
    setContent {
      NewDeviceTransferInstructionsScreen(onContinue = { calls += "continue" }, onBack = { calls += "back" })
    }

    composeTestRule.onNodeWithText(string(R.string.NewDeviceTransferInstructions__tap_on_account), substring = true).assertIsDisplayed()
    composeTestRule.onNodeWithText(upper(R.string.NewDeviceTransferInstructions__continue)).performClick()
    composeTestRule.onNodeWithContentDescription(string(R.string.RegistrationScreen__back_to_previous_screen)).performClick()

    assertThat(calls).containsExactly("continue", "back")
  }

  @Test
  fun `setup progress shows the status line and routes BACK to the fragment`() {
    setupContent(SetupScreen.Progress, status = "Waiting for old Android device to connect…")

    composeTestRule.onNodeWithText("Waiting for old Android device to connect…").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription(string(R.string.RegistrationScreen__back_to_previous_screen)).performClick()

    assertThat(calls).containsExactly("back")
  }

  @Test
  fun `setup error runs its resolve action`() {
    setupContent(SetupScreen.Error("Molly needs Wi-Fi on", "Turn on Wi-Fi", Runnable { calls += "resolve" }))

    composeTestRule.onNodeWithText("Molly needs Wi-Fi on").assertIsDisplayed()
    composeTestRule.onNodeWithText("TURN ON WI-FI").performClick()

    assertThat(calls).containsExactly("resolve")
  }

  @Test
  fun `verify shows the code and continues or reports a mismatch`() {
    setupContent(SetupScreen.Verify("0012345", Runnable { calls += "match" }, Runnable { calls += "mismatch" }))

    composeTestRule.onNodeWithTag(DEVICE_TRANSFER_SAS_CODE_TAG).assertTextEquals("0012345")
    composeTestRule.onNodeWithText(upper(R.string.DeviceTransferSetup__continue)).performClick()
    composeTestRule.onNodeWithText(upper(R.string.DeviceTransferSetup__the_numbers_do_not_match)).performClick()

    assertThat(calls).containsExactly("match", "mismatch")
  }

  @Test
  fun `numbers do not match notice cancels or stops`() {
    setupContent(
      SetupScreen.Verify("0012345", Runnable {}, Runnable {}),
      notice = SetupNotice.NumbersDoNotMatch(Runnable { calls += "stop" })
    )

    composeTestRule.onNodeWithText(upper(R.string.DeviceTransferSetup__stop_transfer)).performClick()

    assertThat(calls).containsExactly("close", "stop")
    composeTestRule.onAllNodesWithTag(LIGHT_RESTORE_NOTICE_TAG).assertCountEquals(0)
  }

  @Test
  fun `numbers do not match notice closes on back without stopping`() {
    setupContent(
      SetupScreen.Verify("0012345", Runnable {}, Runnable {}),
      notice = SetupNotice.NumbersDoNotMatch(Runnable { calls += "stop" })
    )

    composeTestRule.runOnIdle { backDispatcher.onBackPressed() }

    assertThat(calls).containsExactly("close")
  }

  @Test
  fun `error connecting closes first and then runs each choice`() {
    setupContent(
      SetupScreen.Error("error", "Retry", Runnable {}),
      notice = SetupNotice.ErrorConnecting("An unexpected error occurred", Runnable { calls += "retry" }, Runnable { calls += "cancel" }, Runnable { calls += "logs" })
    )

    composeTestRule.runOnIdle { backDispatcher.onBackPressed() }
    composeTestRule.onNodeWithText(upper(R.string.DeviceTransferSetup__submit_debug_logs)).performClick()

    assertThat(calls).containsExactly("close", "logs")
  }

  @Test
  fun `troubleshooting links reach their settings`() {
    setupContent(
      SetupScreen.Troubleshooting(
        title = "Unable to discover old device",
        onLocationPermission = Runnable { calls += "permission" },
        onLocationServices = Runnable { calls += "location" },
        onWifi = Runnable { calls += "wifi" },
        onWifiDirect = Runnable { calls += "wifiDirect" },
        onSupport = Runnable { calls += "support" },
        onTryAgain = Runnable { calls += "tryAgain" }
      )
    )

    composeTestRule.onNodeWithText(string(R.string.DeviceTransferSetup__location_permission)).performScrollTo().performClick()
    composeTestRule.onNodeWithText(string(R.string.DeviceTransferSetup__wifi_direct_screen)).performScrollTo().performClick()
    composeTestRule.onNodeWithText(upper(R.string.DeviceTransferSetup__try_again)).performClick()

    assertThat(calls).containsExactly("permission", "wifiDirect", "tryAgain")
  }

  @Test
  fun `transfer progress stops through the fragment`() {
    setContent {
      DeviceTransferContent(
        status = "12 messages so far…",
        failure = null,
        stopConfirmation = null,
        onStopRequested = { calls += "stop" },
        onCloseStopConfirmation = {}
      )
    }

    composeTestRule.onNodeWithText(string(R.string.DeviceTransfer__transferring_data)).assertIsDisplayed()
    composeTestRule.onNodeWithText("12 messages so far…").assertIsDisplayed()
    composeTestRule.onNodeWithText(upper(R.string.DeviceTransfer__stop_transfer)).performClick()

    assertThat(calls).containsExactly("stop")
  }

  @Test
  fun `transfer failure offers cancel and try again`() {
    setContent {
      DeviceTransferContent(
        status = "",
        failure = DeviceTransferLightView.Failure("Transfer failed", Runnable { calls += "tryAgain" }, Runnable { calls += "cancel" }),
        stopConfirmation = null,
        onStopRequested = {},
        onCloseStopConfirmation = {}
      )
    }

    composeTestRule.onNodeWithText(string(R.string.DeviceTransfer__unable_to_transfer)).assertIsDisplayed()
    composeTestRule.onNodeWithText(upper(R.string.DeviceTransfer__cancel)).performClick()
    composeTestRule.onNodeWithText(upper(R.string.DeviceTransfer__try_again)).performClick()

    assertThat(calls).containsExactly("cancel", "tryAgain")
  }

  @Test
  fun `complete screen continues registration and offers no back`() {
    setContent {
      NewDeviceTransferCompleteScreen(onContinueRegistration = { calls += "continue" })
    }

    composeTestRule.onNodeWithText(string(R.string.NewDeviceTransferComplete__transfer_successful)).assertIsDisplayed()
    composeTestRule.onNodeWithText(upper(R.string.NewDeviceTransferComplete__continue_registration)).performClick()

    assertThat(calls).containsExactly("continue")
  }

  @Test
  fun `nothing is called on display`() {
    setupContent(SetupScreen.WaitingForOther)

    composeTestRule.onNodeWithText(string(R.string.DeviceTransferSetup__waiting_for_other_device)).assertIsDisplayed()
    assertThat(calls).isEmpty()
  }
}
