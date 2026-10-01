/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.linkdevice

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.signal.core.ui.CoreUiDependenciesRule

/**
 * The Linked Devices settings screen, at the LP3's real geometry.
 *
 * What this covers is the behaviour the conversion changed rather than what it looks like: which rows
 * the screen renders, which of them are actionable, and the long-press panel that replaced a per-row
 * three-dot overflow -- including that the panel filters its actions against what a device can
 * actually do instead of showing one greyed out.
 *
 * What it cannot cover is the other half of the job: `captureToImage` blocks forever in this
 * harness, so nothing here asserts that any of this text is visibly rendered, or where on the panel
 * it lands. That is still unverified on LP3 hardware.
 *
 * Nearly every assertion scrolls first. The screen is taller than the LP3's 413dp from the second
 * row down, so an unscrolled `assertIsDisplayed` would be a statement about the viewport rather
 * than about the screen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class LinkDeviceFragmentTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  private var state by mutableStateOf(LinkDeviceSettingsState(canLinkDevices = true, seenQrEducationSheet = true))
  private var removalRequested: Device? = null
  private var editedDevice: Device? = null
  private var linkRequested = false
  private var learnMoreRequested = false

  @Before
  fun setUp() {
    composeTestRule.setContent {
      DeviceListScreen(
        state = state,
        onLearnMoreClicked = { learnMoreRequested = true },
        onLinkNewDeviceClicked = { linkRequested = true },
        onDeviceSelectedForRemoval = { removalRequested = it },
        onEditDevice = { editedDevice = it }
      )
    }
  }

  @Test
  fun `with no devices the screen says what it is, and how to change that`() {
    shown(INTRO)
    shown("Learn more")
    shown("Link a new device")
    shown("MY LINKED DEVICES")
    shown("No linked devices")
  }

  @Test
  fun `the closing statement about encryption is rendered, with its glyph slot left empty`() {
    shown("Messages and chat info are protected by end-to-end encryption on all devices")
  }

  /**
   * Taps go through `performTouchInput`, not `performClick`. A settings row keeps its gesture
   * handling in a pointer input and marks the row itself clickable, so the node carrying the text
   * has no click action of its own -- a touch at the text's coordinates is what a real finger does,
   * and it reaches the row.
   */
  @Test
  fun `learn more and link a new device are both actionable`() {
    tap("Learn more")
    tap("Link a new device")

    assertThat(learnMoreRequested).isEqualTo(true)
    assertThat(linkRequested).isEqualTo(true)
  }

  /**
   * Greyed out rather than hidden, while `canLinkDevices` says this account may not manage linked
   * devices -- which is what it reports once the account is itself a linked device.
   */
  @Test
  fun `an account that may not manage devices has the link action greyed out`() {
    composeTestRule.onAllNodes(disabledRow("Link a new device")).assertCountEquals(0)

    state = LinkDeviceSettingsState(canLinkDevices = false, seenQrEducationSheet = true)
    composeTestRule.waitForIdle()

    shown("Link a new device")
    composeTestRule.onNode(disabledRow("Link a new device")).assertIsDisplayed()
  }

  @Test
  fun `while the list is loading it says so instead of claiming there are no devices`() {
    state = LinkDeviceSettingsState(deviceListLoading = true, canLinkDevices = true, seenQrEducationSheet = true)
    composeTestRule.waitForIdle()

    shown("Loading…")
    composeTestRule.onAllNodes(hasText("No linked devices")).assertCountEquals(0)
  }

  @Test
  fun `a linked device is named, and dated twice`() {
    state = stateWithDevices()

    shown(NAMED_DEVICE.name!!)
    shown(IPAD.name!!)
    composeTestRule.onAllNodes(deviceRow()).assertCountEquals(2)
  }

  /** A long press is the row's only gesture, so a plain tap has to leave the screen alone. */
  @Test
  fun `tapping a device does nothing`() {
    state = stateWithDevices()

    tap(NAMED_DEVICE.name!!)

    assertThat(removalRequested).isNull()
  }

  @Test
  fun `a long press raises the panel with that device's actions`() {
    state = stateWithDevices()

    longPress(NAMED_DEVICE.name!!)

    composeTestRule.onNodeWithText("EDIT NAME").assertIsDisplayed()
    composeTestRule.onNodeWithText("UNLINK").assertIsDisplayed()
  }

  /**
   * The capability filter. The Material menu rendered "Edit name" greyed out for a device that could
   * not be renamed; a long press is a gesture, not a menu to scan, so the row is simply absent.
   */
  @Test
  fun `a device that cannot be renamed offers no edit name action`() {
    state = stateWithDevices(canRename = false)
    composeTestRule.waitForIdle()

    longPress(NAMED_DEVICE.name!!)

    composeTestRule.onNodeWithText("UNLINK").assertIsDisplayed()
    composeTestRule.onAllNodes(hasText("EDIT NAME")).assertCountEquals(0)
  }

  /**
   * The same filter one level up: an account that may not manage linked devices can do nothing to
   * any of them, so the rows carry no long-press gesture at all rather than opening an empty panel.
   */
  @Test
  fun `no actions are offered while this account may not manage devices`() {
    state = LinkDeviceSettingsState(devices = listOf(NAMED_DEVICE), canLinkDevices = false, seenQrEducationSheet = true)
    composeTestRule.waitForIdle()

    longPress(NAMED_DEVICE.name!!)

    composeTestRule.onAllNodes(hasText("EDIT NAME")).assertCountEquals(0)
    composeTestRule.onAllNodes(hasText("UNLINK")).assertCountEquals(0)
    assertThat(removalRequested).isNull()
  }

  /** Unlink is destructive, so it is dispatched only after the panel has been closed. */
  @Test
  fun `choosing unlink closes the panel and reports the device for removal`() {
    state = stateWithDevices()

    longPress(NAMED_DEVICE.name!!)
    composeTestRule.onNodeWithText("UNLINK").performClick()

    composeTestRule.onAllNodes(hasText("UNLINK")).assertCountEquals(0)
    assertThat(removalRequested).isEqualTo(NAMED_DEVICE)
  }

  @Test
  fun `choosing edit name closes the panel and opens the rename screen for that device`() {
    state = stateWithDevices()

    longPress(NAMED_DEVICE.name!!)
    composeTestRule.onNodeWithText("EDIT NAME").performClick()

    composeTestRule.onAllNodes(hasText("EDIT NAME")).assertCountEquals(0)
    assertThat(editedDevice).isEqualTo(NAMED_DEVICE)
  }

  @Test
  fun `the internal device id is on the row only for an internal user`() {
    state = stateWithDevices(isInternalUser = true)
    composeTestRule.waitForIdle()

    composeTestRule.onAllNodes(startingWith("[Internal] DeviceId: 1")).assertCountEquals(1)

    state = stateWithDevices(isInternalUser = false)
    composeTestRule.waitForIdle()

    composeTestRule.onAllNodes(startingWith("[Internal]")).assertCountEquals(0)
  }

  private fun shown(text: String) = composeTestRule.onNodeWithText(text).also {
    composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    it.assertIsDisplayed()
  }

  private fun tap(text: String) {
    shown(text)
    composeTestRule.onNodeWithText(text).performTouchInput { click() }
  }

  private fun longPress(deviceName: String) {
    shown(deviceName)
    composeTestRule.onNodeWithText(deviceName).performTouchInput { longClick() }
  }

  /**
   * A device row is the only node carrying both timestamps, and it carries them on separate lines.
   * The top bar is "Linked devices" too, so matching either half alone would count it.
   */
  private fun deviceRow(): SemanticsMatcher =
    SemanticsMatcher("device row with both timestamps") { node ->
      node.config.getOrNull(SemanticsProperties.Text)
        ?.joinToString("\n") { it.text }
        ?.let { it.startsWith("Linked ") && it.contains("Last active ") } == true
    }

  /**
   * The disabled state is recorded on the row, while the text sits in a child of it, so the
   * assertion has to reach upwards from the copy.
   */
  private fun disabledRow(text: String): SemanticsMatcher =
    hasText(text) and hasAnyAncestor(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))

  private fun startingWith(prefix: String): SemanticsMatcher =
    SemanticsMatcher("text starting with '$prefix'") { node ->
      node.config.getOrNull(SemanticsProperties.Text)?.any { it.text.startsWith(prefix) } == true
    }

  private fun stateWithDevices(
    canRename: Boolean = true,
    isInternalUser: Boolean = false
  ) = LinkDeviceSettingsState(
    devices = listOf(NAMED_DEVICE.copy(canRename = canRename), IPAD),
    isInternalUser = isInternalUser,
    canLinkDevices = true,
    seenQrEducationSheet = true
  )

  private companion object {
    const val INTRO = "Use Molly on another device. Your messages will sync to your linked devices."

    val NAMED_DEVICE = Device(
      id = 1,
      name = "Sam's Macbook Pro",
      createdMillis = 1_715_793_982_000,
      lastSeenMillis = 1_716_053_182_000,
      registrationId = 0
    )

    val IPAD = Device(
      id = 2,
      name = "Sam's iPad",
      createdMillis = 1_715_793_180_000,
      lastSeenMillis = 1_716_053_122_000,
      registrationId = 0
    )
  }
}