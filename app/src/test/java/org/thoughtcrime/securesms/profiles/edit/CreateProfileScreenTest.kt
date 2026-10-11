/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.edit

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
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
import assertk.assertions.isEmpty
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
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.profiles.manage.EditProfileNameFragment

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class CreateProfileScreenTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  private val nameLimit = CreateProfileFieldLimit(EditProfileNameFragment.NAME_MAX_GLYPHS, ProfileName.MAX_PART_LENGTH)

  /** As getIntentForUserProfile: first run, no back. */
  private val userConfig = CreateProfileConfig(
    isGroup = false,
    title = R.string.CreateProfileActivity__profile,
    showBack = false,
    nextLabel = R.string.CreateProfileActivity_next,
    showFamilyField = true,
    showMmsHint = false,
    givenNameLimit = nameLimit,
    familyNameLimit = nameLimit
  )

  /** As getIntentForGroupProfile for a GV2 group. */
  private val groupConfig = CreateProfileConfig(
    isGroup = true,
    title = R.string.EditProfileFragment__edit_group,
    showBack = true,
    nextLabel = R.string.save,
    showFamilyField = true,
    showMmsHint = false,
    givenNameLimit = CreateProfileFieldLimit(32),
    familyNameLimit = CreateProfileFieldLimit(480, 8192)
  )

  private val clickedFields = mutableListOf<CreateProfileField>()
  private val committed = mutableListOf<Pair<CreateProfileField, String>>()
  private var backClicked = false
  private var learnMoreClicked = false
  private var avatarClicked = false
  private var whoCanFindMeClicked = false
  private var editorClosed = false
  private var nextClicked = false

  @Test
  fun `first run shows the title in the top bar, every row, and no back`() {
    setScreen(userConfig)

    composeTestRule.onNodeWithText("Profile").assertExists()
    composeTestRule.onNodeWithText("Profiles are visible to people you message, contacts, and groups.").assertExists()
    composeTestRule.onNodeWithText("LEARN MORE").assertExists()
    composeTestRule.onNodeWithText("Add photo").assertExists()
    composeTestRule.onNodeWithText("First name (required)").assertExists()
    composeTestRule.onNodeWithText("Last name (optional)").assertExists()
    composeTestRule.onNodeWithText("Who can find me by number?").assertExists()
    composeTestRule.onNodeWithText("Everyone").assertExists()
    composeTestRule.onNodeWithContentDescription("Close without saving").assertDoesNotExist()
  }

  @Test
  fun `NEXT is disabled while the form is invalid`() {
    setScreen(userConfig, nextEnabled = false)

    composeTestRule.onNodeWithText("NEXT").assertIsNotEnabled().performClick()

    assertThat(nextClicked).isEqualTo(false)
  }

  @Test
  fun `NEXT is enabled for a valid form and reports the tap`() {
    setScreen(userConfig, givenName = "Alice", nextEnabled = true)

    composeTestRule.onNodeWithText("NEXT").assertIsEnabled().performClick()

    assertThat(nextClicked).isTrue()
  }

  @Test
  fun `an upload in flight shows an ellipsis in place of NEXT`() {
    setScreen(userConfig, givenName = "Alice", nextEnabled = true, uploading = true)

    composeTestRule.onNodeWithText("NEXT").assertDoesNotExist()
    composeTestRule.onNodeWithText("…").assertIsNotEnabled()
  }

  @Test
  fun `tapping a name field opens its editor`() {
    setScreen(userConfig)

    composeTestRule.onNodeWithText("Add first name").performClick()
    composeTestRule.onNodeWithText("Add last name").performClick()

    assertThat(clickedFields).containsExactly(CreateProfileField.GIVEN_NAME, CreateProfileField.FAMILY_NAME)
  }

  @Test
  fun `editor DONE commits what was typed`() {
    setScreen(userConfig, givenName = "Al", editing = CreateProfileField.GIVEN_NAME)

    composeTestRule.onNode(hasSetTextAction()).performTextInput("ice")
    composeTestRule.onNodeWithText("DONE").performClick()

    assertThat(committed).containsExactly(CreateProfileField.GIVEN_NAME to "Alice")
  }

  @Test
  fun `editor BACK discards what was typed`() {
    setScreen(userConfig, familyName = "Smith", editing = CreateProfileField.FAMILY_NAME)

    composeTestRule.onNode(hasSetTextAction()).performTextInput("son")
    composeTestRule.onNodeWithContentDescription("Close without saving").performClick()

    assertThat(editorClosed).isTrue()
    assertThat(committed).isEmpty()
  }

  @Test
  fun `the name editor stops at NAME_MAX_GLYPHS`() {
    setScreen(userConfig, editing = CreateProfileField.GIVEN_NAME)

    composeTestRule.onNode(hasSetTextAction()).performTextInput("a".repeat(40))
    composeTestRule.onNode(hasSetTextAction()).assertTextEquals("a".repeat(EditProfileNameFragment.NAME_MAX_GLYPHS))
    composeTestRule.onNodeWithText("DONE").performClick()

    assertThat(committed).containsExactly(CreateProfileField.GIVEN_NAME to "a".repeat(EditProfileNameFragment.NAME_MAX_GLYPHS))
  }

  @Test
  fun `the photo row opens the avatar picker and reads Edit photo once there is one`() {
    setScreen(userConfig, hasAvatar = true)

    composeTestRule.onNodeWithText("Edit photo").performClick()

    assertThat(avatarClicked).isTrue()
  }

  @Test
  fun `the who can find me row and learn more report their taps`() {
    setScreen(userConfig)

    composeTestRule.onNodeWithText("Who can find me by number?").performClick()
    composeTestRule.onNodeWithText("LEARN MORE").performClick()

    assertThat(whoCanFindMeClicked).isTrue()
    assertThat(learnMoreClicked).isTrue()
  }

  @Test
  fun `group editing has back, the group fields, SAVE, and no profile rows`() {
    setScreen(groupConfig, whoCanFindMe = null)

    composeTestRule.onNodeWithText("Edit group").assertExists()
    composeTestRule.onNodeWithText("Group name").assertExists()
    composeTestRule.onNodeWithText("Group description").assertExists()
    composeTestRule.onNodeWithText("SAVE").assertExists()
    composeTestRule.onNodeWithText("Who can find me by number?").assertDoesNotExist()
    composeTestRule.onNodeWithText("LEARN MORE").assertDoesNotExist()

    composeTestRule.onNodeWithContentDescription("Close without saving").performClick()

    assertThat(backClicked).isTrue()
  }

  @Test
  fun `an MMS group has only a name, with its caption`() {
    setScreen(groupConfig.copy(showFamilyField = false, showMmsHint = true), whoCanFindMe = null)

    composeTestRule.onNodeWithText("Custom MMS group names and photos will only be visible to you.").assertExists()
    composeTestRule.onNodeWithText("Group description").assertDoesNotExist()
  }

  private fun setScreen(
    config: CreateProfileConfig,
    givenName: String = "",
    familyName: String = "",
    hasAvatar: Boolean = false,
    whoCanFindMe: String? = "Everyone",
    editing: CreateProfileField? = null,
    nextEnabled: Boolean = false,
    uploading: Boolean = false
  ) {
    composeTestRule.setContent {
      CreateProfileScreen(
        config = config,
        givenName = givenName,
        familyName = familyName,
        hasAvatar = hasAvatar,
        avatarImage = null,
        whoCanFindMe = whoCanFindMe,
        editing = editing,
        nextEnabled = nextEnabled,
        uploading = uploading,
        onBackClicked = { backClicked = true },
        onLearnMoreClicked = { learnMoreClicked = true },
        onAvatarClicked = { avatarClicked = true },
        onFieldClicked = { clickedFields += it },
        onWhoCanFindMeClicked = { whoCanFindMeClicked = true },
        onEditorBack = { editorClosed = true },
        onEditorDone = { field, text -> committed += field to text },
        onNextClicked = { nextClicked = true }
      )
    }
  }
}
