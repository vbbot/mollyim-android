/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.light

import android.app.Application
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.height
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.platform.testTag
import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class LightSettingsTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `toggle exposes switch state and delegates the inverse value`() {
    var requested: Boolean? = null
    composeTestRule.setContent {
      MollyLightTheme {
        LightSettingsToggleRow(
          title = "Toggle",
          checked = false,
          onCheckedChange = { requested = it },
          modifier = Modifier.testTag("toggle")
        )
      }
    }

    composeTestRule.onNodeWithTag("toggle")
      .assertIsToggleable()
      .assertIsOff()
      .performClick()

    assertThat(requested).isEqualTo(true)
  }

  @Test
  fun `disabled explanation remains clickable while row stays disabled`() {
    var explanationClicks = 0
    composeTestRule.setContent {
      MollyLightTheme {
        LightSettingsRow(
          title = "Unavailable",
          enabled = false,
          onClick = {},
          onDisabledClick = { explanationClicks++ },
          modifier = Modifier.testTag("disabled")
        )
      }
    }

    composeTestRule.onNodeWithTag("disabled")
      .assertIsNotEnabled()
      .performClick()

    assertThat(explanationClicks).isEqualTo(1)
  }

  @Test
  fun `enabled long press invokes only long-click callback`() {
    var clicks = 0
    var longClicks = 0
    composeTestRule.setContent {
      MollyLightTheme {
        LightSettingsRow(
          title = "Long press",
          onClick = { clicks++ },
          onLongClick = { longClicks++; true },
          modifier = Modifier.testTag("long-press")
        )
      }
    }

    composeTestRule.onNodeWithTag("long-press").performTouchInput { longClick() }

    assertThat(longClicks).isEqualTo(1)
    assertThat(clicks).isEqualTo(0)
  }

  @Test
  fun `radio row exposes selected radio semantics`() {
    composeTestRule.setContent {
      MollyLightTheme {
        LightSettingsRadioRow(
          title = "Selected",
          selected = true,
          onClick = {},
          modifier = Modifier.testTag("radio")
        )
      }
    }

    composeTestRule.onNodeWithTag("radio")
      .assertIsSelected()
      .assertIsDisplayed()
  }

  @Test
  fun `confirmed single choice commits pending value only on ok`() {
    var committed = -1
    composeTestRule.setContent {
      LightSingleChoiceDialog(
        title = "Choice",
        items = listOf("One", "Two"),
        selected = 0,
        confirmAction = true,
        onSelected = { committed = it },
        onDismiss = {}
      )
    }

    composeTestRule.onNodeWithText("Two").performClick()
    assertThat(committed).isEqualTo(-1)
    composeTestRule.onNodeWithText("OK").performClick()

    assertThat(committed).isEqualTo(1)
  }

  @Test
  fun `confirmed single choice cancel discards pending value`() {
    var committed = -1
    composeTestRule.setContent {
      LightSingleChoiceDialog(
        title = "Choice",
        items = listOf("One", "Two"),
        selected = 0,
        confirmAction = true,
        onSelected = { committed = it },
        onDismiss = {}
      )
    }

    composeTestRule.onNodeWithText("Two").performClick()
    composeTestRule.onNodeWithText("CANCEL").performClick()

    assertThat(committed).isEqualTo(-1)
  }

  @Test
  fun `multi choice edits a copy and commits only on ok`() {
    var committed: BooleanArray? = null
    val original = booleanArrayOf(true, false)
    composeTestRule.setContent {
      LightMultiChoiceDialog(
        title = "Multiple",
        items = listOf("One", "Two"),
        selected = original,
        onSelected = { committed = it },
        onDismiss = {}
      )
    }

    composeTestRule.onNodeWithText("Two").performClick()
    assertThat(original.toList()).isEqualTo(listOf(true, false))
    composeTestRule.onNodeWithText("OK").performClick()

    assertThat(committed?.toList()).isEqualTo(listOf(true, true))
  }

  @Test
  fun `LP3 settings screen keeps variable-height row inside viewport width`() {
    composeTestRule.setContent {
      MollyLightTheme {
        LightSettingsScreen(title = "Settings", onBack = {}) {
          LightSettingsRow(
            title = "A setting with a title that wraps on the narrow Light Phone screen",
            summary = "A summary that also wraps instead of assuming a uniform item height.",
            onClick = {},
            modifier = Modifier.testTag("variable-row"),
            role = Role.Button
          )
        }
      }
    }

    val bounds = composeTestRule.onNodeWithTag("variable-row").getUnclippedBoundsInRoot()
    assertThat(bounds.left.value).isBetween(0f, 360f)
    assertThat(bounds.right.value).isBetween(0f, 360f)
    assertThat(bounds.top.value).isBetween(0f, 413f)
    assertThat(bounds.height.value).isBetween(1f, 413f)
  }

  @Test
  fun `checked toggle exposes on state`() {
    composeTestRule.setContent {
      MollyLightTheme {
        LightSettingsToggleRow(
          title = "Toggle",
          checked = true,
          onCheckedChange = {},
          modifier = Modifier.testTag("checked-toggle")
        )
      }
    }

    composeTestRule.onNodeWithTag("checked-toggle").assertIsOn()
  }
}
