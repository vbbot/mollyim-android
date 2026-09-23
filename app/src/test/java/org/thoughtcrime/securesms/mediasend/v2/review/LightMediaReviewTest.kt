/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.mediasend.v2.review

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.thelightphone.sdk.ui.gridUnitsAsDp
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
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class LightMediaReviewTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `top bar reports Back and destination`() {
    var backPressed = false
    composeTestRule.setContent {
      MollyLightTheme {
        LightMediaReviewTopBar(destinationLabel = "Ada Lovelace", onBack = { backPressed = true })
      }
    }

    composeTestRule.onNodeWithText("Ada Lovelace").assertIsDisplayed()
    val backDescription = RuntimeEnvironment.getApplication().getString(R.string.ConversationFragment__content_description_back_button)
    composeTestRule.onNodeWithContentDescription(backDescription).performClick()

    assertThat(backPressed).isTrue()
  }

  @Test
  fun `top bar updates destination label`() {
    composeTestRule.setContent {
      MollyLightTheme {
        Column {
          LightMediaReviewTopBar(destinationLabel = "Note to Self", onBack = {})
          LightMediaReviewTopBar(destinationLabel = "Send to", onBack = {})
        }
      }
    }

    composeTestRule.onNodeWithText("Note to Self").assertIsDisplayed()
    composeTestRule.onNodeWithText("Send to").assertIsDisplayed()
  }

  @Test
  fun `caption displays value and reports edit intent`() {
    var edits = 0
    setBottom(state(message = "Meet at eleven"), onCaptionClick = { edits++ })

    composeTestRule.onNodeWithText("Message").assertIsDisplayed()
    composeTestRule.onNodeWithText("Meet at eleven").assertIsDisplayed()
    composeTestRule.onNodeWithTag(CAPTION_FIELD_TAG).performClick()

    assertThat(edits).isEqualTo(1)
  }

  @Test
  fun `blank caption displays supplied placeholder`() {
    setBottom(state(message = "", placeholder = "Add a reply"))

    composeTestRule.onNodeWithText("Add a reply").assertIsDisplayed()
    composeTestRule.onNodeWithText("Add a message").assertDoesNotExist()
  }

  @Test
  fun `view once replaces and disables caption`() {
    var edits = 0
    setBottom(state(isViewOnce = true), onCaptionClick = { edits++ })

    composeTestRule.onNodeWithText("View once media").assertIsDisplayed()
    composeTestRule.onNodeWithTag(CAPTION_FIELD_TAG).assertIsNotEnabled().performTouchInput { click() }

    assertThat(edits).isEqualTo(0)
  }

  @Test
  fun `persistent action matrices exactly follow media policy`() {
    assertActions(
      state(type = LightMediaReviewMediaType.IMAGE),
      LightMediaReviewAction.ADD,
      LightMediaReviewAction.SELECT_OFF,
      LightMediaReviewAction.ELLIPSES,
      LightMediaReviewAction.SEND
    )
    assertActions(
      state(type = LightMediaReviewMediaType.IMAGE, isViewOnce = true),
      LightMediaReviewAction.SELECT_ON,
      LightMediaReviewAction.ELLIPSES,
      LightMediaReviewAction.SEND
    )
    assertActions(
      state(type = LightMediaReviewMediaType.IMAGE, count = 2),
      LightMediaReviewAction.ELLIPSES,
      LightMediaReviewAction.SEND
    )
    assertActions(
      state(type = LightMediaReviewMediaType.IMAGE, isStory = true),
      LightMediaReviewAction.ADD,
      LightMediaReviewAction.ELLIPSES,
      LightMediaReviewAction.SEND
    )
    assertActions(
      state(type = LightMediaReviewMediaType.VIDEO),
      LightMediaReviewAction.ADD,
      LightMediaReviewAction.SELECT_OFF,
      LightMediaReviewAction.ELLIPSES,
      LightMediaReviewAction.SEND
    )
    assertActions(
      state(type = LightMediaReviewMediaType.VIDEO, isStory = true),
      LightMediaReviewAction.ADD,
      LightMediaReviewAction.SEND
    )
    assertActions(
      state(type = LightMediaReviewMediaType.GIF),
      LightMediaReviewAction.ADD,
      LightMediaReviewAction.SELECT_OFF,
      LightMediaReviewAction.ELLIPSES,
      LightMediaReviewAction.SEND
    )
    assertActions(
      state(type = LightMediaReviewMediaType.GIF, isStory = true),
      LightMediaReviewAction.ADD,
      LightMediaReviewAction.ELLIPSES,
      LightMediaReviewAction.SEND
    )
    assertActions(state(type = LightMediaReviewMediaType.DOCUMENT), LightMediaReviewAction.SEND)
    assertActions(state(isTouchEnabled = false))
  }

  @Test
  fun `secondary tools exactly follow media and story policy`() {
    assertTools(
      state(type = LightMediaReviewMediaType.IMAGE),
      LightMediaReviewTool.DRAW,
      LightMediaReviewTool.CROP_AND_ROTATE,
      LightMediaReviewTool.QUALITY,
      LightMediaReviewTool.SAVE
    )
    assertTools(
      state(type = LightMediaReviewMediaType.IMAGE, isStory = true),
      LightMediaReviewTool.DRAW,
      LightMediaReviewTool.CROP_AND_ROTATE,
      LightMediaReviewTool.SAVE
    )
    assertTools(state(type = LightMediaReviewMediaType.VIDEO), LightMediaReviewTool.QUALITY)
    assertTools(state(type = LightMediaReviewMediaType.VIDEO, isStory = true))
    assertTools(state(type = LightMediaReviewMediaType.GIF), LightMediaReviewTool.QUALITY, LightMediaReviewTool.SAVE)
    assertTools(state(type = LightMediaReviewMediaType.GIF, isStory = true), LightMediaReviewTool.SAVE)
    assertTools(state(type = LightMediaReviewMediaType.DOCUMENT))
    assertTools(state(isTouchEnabled = false))
  }

  @Test
  fun `every persistent action reports exactly its own intent`() {
    val selected = mutableListOf<LightMediaReviewAction>()
    setBottom(state(), onActionClick = selected::add)

    state().actions.forEach { action ->
      composeTestRule.onNodeWithTag(actionTestTag(action)).performClick()
    }

    assertThat(selected).containsExactly(*state().actions.toTypedArray())
  }

  @Test
  fun `disabled send exposes disabled semantics and ignores all gestures`() {
    var sends = 0
    var schedules = 0
    setBottom(
      state(sendEnabled = false),
      onActionClick = { if (it == LightMediaReviewAction.SEND) sends++ },
      onSendLongClick = { schedules++ }
    )

    composeTestRule.onNodeWithTag(actionTestTag(LightMediaReviewAction.SEND))
      .assertIsNotEnabled()
      .performTouchInput {
        click()
        longClick()
      }

    assertThat(sends).isEqualTo(0)
    assertThat(schedules).isEqualTo(0)
  }

  @Test
  fun `enabled send click reports send intent`() {
    var sends = 0
    setBottom(
      state(sendEnabled = true),
      onActionClick = { if (it == LightMediaReviewAction.SEND) sends++ }
    )

    composeTestRule.onNodeWithTag(actionTestTag(LightMediaReviewAction.SEND)).performClick()

    assertThat(sends).isEqualTo(1)
  }

  @Test
  fun `eligible non story send exposes scheduled long press`() {
    var sends = 0
    var schedules = 0
    setBottom(
      state(sendEnabled = true),
      onActionClick = { if (it == LightMediaReviewAction.SEND) sends++ },
      onSendLongClick = { schedules++ }
    )

    composeTestRule.onNodeWithTag(actionTestTag(LightMediaReviewAction.SEND))
      .performSemanticsAction(SemanticsActions.OnLongClick)

    assertThat(schedules).isEqualTo(1)
    assertThat(sends).isEqualTo(0)
  }

  @Test
  fun `story send has no scheduled long press`() {
    setBottom(state(isStory = true, sendEnabled = true))

    val node = composeTestRule.onNodeWithTag(actionTestTag(LightMediaReviewAction.SEND)).fetchSemanticsNode()
    assertThat(node.config.contains(SemanticsActions.OnLongClick)).isFalse()
  }

  @Test
  fun `touch disabled hides every lower control`() {
    setBottom(state(isTouchEnabled = false))

    composeTestRule.onNodeWithTag(BOTTOM_REGION_TAG).assertDoesNotExist()
    composeTestRule.onNodeWithTag(CAPTION_FIELD_TAG).assertDoesNotExist()
    composeTestRule.onNodeWithTag(ACTION_BAR_TAG).assertDoesNotExist()
  }

  @Test
  fun `top and action bars use Light grid heights`() {
    setReviewScreen(state())

    val top = composeTestRule.onNodeWithTag(TOP_BAR_TAG).getUnclippedBoundsInRoot()
    val actions = composeTestRule.onNodeWithTag(ACTION_BAR_TAG).getUnclippedBoundsInRoot()

    val context = RuntimeEnvironment.getApplication()
    assertThat((top.bottom - top.top).value).isCloseTo(3f.gridUnitsAsDp(context), 0.5f)
    assertThat((actions.bottom - actions.top).value).isCloseTo(4f.gridUnitsAsDp(context), 0.5f)
  }

  @Test
  fun `review chrome leaves positive unclipped non overlapping pager geometry`() {
    setReviewScreen(state())

    val top = composeTestRule.onNodeWithTag(TOP_BAR_TAG).getUnclippedBoundsInRoot()
    val pager = composeTestRule.onNodeWithTag(PAGER_TAG).getUnclippedBoundsInRoot()
    val bottom = composeTestRule.onNodeWithTag(BOTTOM_REGION_TAG).getUnclippedBoundsInRoot()

    assertThat((pager.bottom - pager.top).value > 0f).isTrue()
    assertThat(top.bottom <= pager.top).isTrue()
    assertThat(pager.bottom <= bottom.top).isTrue()
    listOf(top, pager, bottom).forEach { bounds ->
      assertThat(bounds.left.value >= 0f).isTrue()
      assertThat(bounds.right.value <= 360f).isTrue()
      assertThat(bounds.top.value >= 0f).isTrue()
      assertThat(bounds.bottom.value <= 413f).isTrue()
    }
  }

  private fun setBottom(
    state: LightMediaReviewState,
    onCaptionClick: () -> Unit = {},
    onActionClick: (LightMediaReviewAction) -> Unit = {},
    onSendLongClick: () -> Unit = {}
  ) {
    composeTestRule.setContent {
      MollyLightTheme {
        LightMediaReviewBottom(
          state = state,
          onCaptionClick = onCaptionClick,
          onActionClick = onActionClick,
          onSendLongClick = onSendLongClick
        )
      }
    }
  }

  private fun setReviewScreen(state: LightMediaReviewState) {
    composeTestRule.setContent {
      MollyLightTheme {
        Column(modifier = Modifier.fillMaxSize()) {
          LightMediaReviewTopBar(destinationLabel = state.destinationLabel, onBack = {})
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .testTag(PAGER_TAG)
          )
          LightMediaReviewBottom(
            state = state,
            onCaptionClick = {},
            onActionClick = {},
            onSendLongClick = {}
          )
        }
      }
    }
  }

  private fun state(
    type: LightMediaReviewMediaType = LightMediaReviewMediaType.IMAGE,
    count: Int = 1,
    message: String = "",
    placeholder: String = "Add a message",
    isStory: Boolean = false,
    isTouchEnabled: Boolean = true,
    isViewOnce: Boolean = false,
    sendEnabled: Boolean = true
  ): LightMediaReviewState {
    return LightMediaReviewState.map(
      destinationLabel = "Ada Lovelace",
      message = message,
      messagePlaceholder = placeholder,
      mediaType = type,
      selectedCount = count,
      isStory = isStory,
      isTouchEnabled = isTouchEnabled,
      isViewOnce = isViewOnce,
      sendEnabled = sendEnabled
    )
  }

  private fun assertActions(state: LightMediaReviewState, vararg actions: LightMediaReviewAction) {
    assertThat(state.actions).containsExactly(*actions)
  }

  private fun assertTools(state: LightMediaReviewState, vararg tools: LightMediaReviewTool) {
    assertThat(state.secondaryTools).containsExactly(*tools)
  }

  private companion object {
    const val PAGER_TAG = "media-review-pager-space"
  }
}
