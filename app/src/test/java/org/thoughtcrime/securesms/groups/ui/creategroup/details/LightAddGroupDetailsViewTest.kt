/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui.creategroup.details

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import assertk.assertThat
import assertk.assertions.isBetween
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
import org.thoughtcrime.securesms.recipients.Recipient

@RunWith(RobolectricTestRunner::class)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class LightAddGroupDetailsViewTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `busy creation keeps the create action visible but disabled`() {
    setScreen(state(isBusy = true, canSubmit = true))

    composeTestRule
      .onNodeWithText(context.getString(R.string.AddGroupDetailsFragment__creating))
      .assertIsDisplayed()
      .assertIsNotEnabled()
  }

  @Test
  fun `LP3 keeps fixed create action inside the viewport`() {
    setScreen(state(canSubmit = true))

    val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    val create = composeTestRule
      .onNodeWithText(context.getString(R.string.AddGroupDetailsFragment__create))
      .getUnclippedBoundsInRoot()

    assertThat(create.top.value).isBetween(root.top.value, root.bottom.value)
    assertThat(create.bottom.value).isBetween(root.top.value, root.bottom.value)
  }

  @Test
  fun `long member list remains scroll reachable on LP3`() {
    val members = (1..12).map { index ->
      LightAddGroupDetailsView.MemberUi(
        recipient = Recipient.UNKNOWN,
        name = "Member $index",
        isInvited = false
      )
    }
    setScreen(state(members = members))

    composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText("Member 12"))
    composeTestRule.onNodeWithText("Member 12").assertIsDisplayed()
  }

  @Test
  fun `visible controls route to exactly their own callbacks`() {
    val callbacks = mutableListOf<String>()
    setScreen(
      state(canSubmit = true),
      onBack = { callbacks += "back" },
      onAvatarClick = { callbacks += "avatar" },
      onTimerClick = { callbacks += "timer" },
      onCreateClick = { callbacks += "create" }
    )

    composeTestRule
      .onNodeWithContentDescription(context.getString(R.string.AddGroupDetailsFragment__group_avatar))
      .performClick()
    assertThat(callbacks).isEqualTo(listOf("avatar"))

    composeTestRule
      .onNodeWithText(context.getString(R.string.PrivacySettingsFragment__disappearing_messages))
      .performClick()
    assertThat(callbacks).isEqualTo(listOf("avatar", "timer"))

    composeTestRule
      .onNodeWithText(context.getString(R.string.AddGroupDetailsFragment__create))
      .performClick()
    assertThat(callbacks).isEqualTo(listOf("avatar", "timer", "create"))

    composeTestRule
      .onNodeWithContentDescription(context.getString(R.string.DefaultTopAppBar__navigate_up_content_description))
      .performClick()
    assertThat(callbacks).isEqualTo(listOf("avatar", "timer", "create", "back"))
  }

  private fun setScreen(
    state: LightAddGroupDetailsState,
    onBack: () -> Unit = {},
    onAvatarClick: () -> Unit = {},
    onTimerClick: () -> Unit = {},
    onCreateClick: () -> Unit = {}
  ) {
    composeTestRule.setContent {
      MollyLightTheme {
        LightAddGroupDetailsScreen(
          state = state,
          onBack = onBack,
          onAvatarClick = onAvatarClick,
          onTimerClick = onTimerClick,
          onNameChanged = {},
          onMemberClick = {},
          onDuplicateClick = {},
          onCreateClick = onCreateClick
        )
      }
    }
  }

  private fun state(
    isBusy: Boolean = false,
    canSubmit: Boolean = false,
    members: List<LightAddGroupDetailsView.MemberUi> = emptyList()
  ): LightAddGroupDetailsState {
    return LightAddGroupDetailsState(
      name = "Group name",
      avatarBytes = null,
      timerValue = "Off",
      members = members,
      duplicateGroups = emptyList(),
      isMms = false,
      canSubmit = canSubmit,
      isBusy = isBusy,
      errorMessage = null
    )
  }

  private val context get() = RuntimeEnvironment.getApplication<Application>()
}
