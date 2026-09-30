/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings.conversation.permissions

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.MollyLightTheme

@RunWith(RobolectricTestRunner::class)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class PermissionsSettingsLightGeometryTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `all four permission actions are reachable on LP3`() {
    setScreen(PermissionsSettingsState(selfCanEditSettings = true))

    composeTestRule.onNodeWithText(string(R.string.PermissionsSettingsFragment__add_members)).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.PermissionsSettingsFragment__edit_group_info)).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.PermissionsSettingsFragment__send_messages)).assertIsDisplayed()
    composeTestRule.onNodeWithText(string(R.string.PermissionsSettingsFragment__add_member_labels)).assertIsDisplayed()
  }

  @Test
  fun `non admins see permission state without actionable rows`() {
    setScreen(PermissionsSettingsState(selfCanEditSettings = false))

    composeTestRule
      .onNodeWithText(string(R.string.PermissionsSettingsFragment__add_members))
      .assertIsNotEnabled()
  }

  @Test
  fun `choice confirmation routes one permission and selection`() {
    val selections = mutableListOf<Pair<GroupPermission, Int>>()
    setScreen(
      state = PermissionsSettingsState(selfCanEditSettings = true),
      onSelectionConfirmed = { permission, index -> selections += permission to index }
    )

    composeTestRule.onNodeWithText(string(R.string.PermissionsSettingsFragment__add_members)).performClick()
    composeTestRule.onNodeWithText(string(R.string.PermissionsSettingsFragment__all_members)).performClick()
    composeTestRule.onNodeWithText(string(android.R.string.ok)).performClick()

    assertThat(selections).isEqualTo(listOf(GroupPermission.ADD_MEMBERS to ALL_MEMBERS_INDEX))
  }

  @Test
  fun `label warning cancel and confirmation route separately`() {
    var dismissCount = 0
    var confirmCount = 0
    setScreen(
      state = PermissionsSettingsState(selfCanEditSettings = true),
      showWarning = true,
      onWarningDismissed = { dismissCount++ },
      onWarningConfirmed = { confirmCount++ }
    )

    composeTestRule.onNodeWithText(string(R.string.PermissionsSettingsFragment__change_permission)).performClick()

    assertThat(confirmCount).isEqualTo(1)
    assertThat(dismissCount).isEqualTo(0)
  }

  private fun setScreen(
    state: PermissionsSettingsState,
    showWarning: Boolean = false,
    onSelectionConfirmed: (GroupPermission, Int) -> Unit = { _, _ -> },
    onWarningDismissed: () -> Unit = {},
    onWarningConfirmed: () -> Unit = {}
  ) {
    composeTestRule.setContent {
      MollyLightTheme {
        PermissionsSettingsScreen(
          state = state,
          showMemberLabelWarning = showWarning,
          onBack = {},
          onSelectionConfirmed = onSelectionConfirmed,
          onMemberLabelWarningDismissed = onWarningDismissed,
          onMemberLabelRestrictionConfirmed = onWarningConfirmed
        )
      }
    }
  }

  private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)
}
