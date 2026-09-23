/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.keyboard.light

import android.app.Application
import android.net.Uri
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import com.thelightphone.sdk.ui.gridUnitsAsDp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.signal.core.models.media.Media
import org.signal.core.ui.CoreUiDependenciesRule
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.AttachmentKeyboardButton
import org.thoughtcrime.securesms.light.MollyLightTheme

@RunWith(RobolectricTestRunner::class)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class LightAttachmentPickerTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `actions render in product order without camera`() {
    setPicker(fullAccess())

    val actionTops = LIGHT_ATTACHMENT_ACTIONS.map { action ->
      composeTestRule.onNodeWithTag(actionTestTag(action)).getUnclippedBoundsInRoot().top.value
    }

    assertThat(LIGHT_ATTACHMENT_ACTIONS).containsExactly(
      AttachmentKeyboardButton.GALLERY,
      AttachmentKeyboardButton.VOICE_NOTE,
      AttachmentKeyboardButton.FILE,
      AttachmentKeyboardButton.POLL,
      AttachmentKeyboardButton.CONTACT,
      AttachmentKeyboardButton.LOCATION
    )
    assertThat(actionTops.zipWithNext().all { (first, second) -> second > first }).isTrue()
    composeTestRule.onNodeWithText("Camera", substring = false, ignoreCase = true).assertDoesNotExist()
  }

  @Test
  fun `every action reports exactly its own enum value`() {
    val selected = mutableListOf<AttachmentKeyboardButton>()
    setPicker(fullAccess(), onActionSelected = selected::add)

    LIGHT_ATTACHMENT_ACTIONS.forEach { action ->
      composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(actionTestTag(action)))
      composeTestRule.onNodeWithTag(actionTestTag(action)).performClick()
    }

    assertThat(selected).containsExactly(*LIGHT_ATTACHMENT_ACTIONS.toTypedArray())
  }

  @Test
  fun `back is isolated from picker actions`() {
    var backCount = 0
    var selected: AttachmentKeyboardButton? = null
    var permissionsRequested = false
    var manageStart: Boolean? = null

    setPicker(
      state = LightAttachmentPickerState(mediaAccess = LightAttachmentMediaAccess.NoAccess),
      onBack = { backCount++ },
      onActionSelected = { selected = it },
      onPermissionsRequested = { permissionsRequested = true },
      onManageRequested = { manageStart = it }
    )

    composeTestRule
      .onNodeWithContentDescription(application.getString(R.string.ConversationFragment__content_description_back_button))
      .performClick()

    assertThat(backCount).isEqualTo(1)
    assertThat(selected).isNull()
    assertThat(permissionsRequested).isFalse()
    assertThat(manageStart).isNull()
  }

  @Test
  fun `media click preserves the selected media identity`() {
    val expected = media(id = 7)
    var selected: Media? = null
    setPicker(
      state = LightAttachmentPickerState(mediaAccess = LightAttachmentMediaAccess.Full(listOf(expected))),
      onMediaSelected = { selected = it }
    )

    composeTestRule.onNodeWithTag(mediaTestTag(expected)).performClick()

    assertThat(selected).isSameInstanceAs(expected)
  }

  @Test
  fun `limited empty state explains access and requests start-aligned Manage`() {
    var showAtStart: Boolean? = null
    setPicker(
      state = LightAttachmentPickerState(mediaAccess = LightAttachmentMediaAccess.LimitedEmpty),
      onManageRequested = { showAtStart = it }
    )

    composeTestRule.onNodeWithTag(RECENT_MEDIA_RAIL_TAG).assertDoesNotExist()
    composeTestRule.onNodeWithText(application.getString(R.string.AttachmentKeyboard_no_photos_found)).assertIsDisplayed()
    composeTestRule.onNodeWithText(application.getString(R.string.AttachmentKeyboard_manage)).performClick()

    assertThat(showAtStart).isEqualTo(true)
  }

  @Test
  fun `limited media state keeps media explanation and end-aligned Manage`() {
    val item = media(id = 8)
    var showAtStart: Boolean? = null
    setPicker(
      state = LightAttachmentPickerState(mediaAccess = LightAttachmentMediaAccess.Limited(listOf(item))),
      onManageRequested = { showAtStart = it }
    )

    composeTestRule.onNodeWithTag(RECENT_MEDIA_RAIL_TAG).assertIsDisplayed()
    composeTestRule.onNodeWithTag(mediaTestTag(item)).assertIsDisplayed()
    composeTestRule.onNodeWithText(application.getString(R.string.AttachmentKeyboard_signal_has_limited_access)).assertIsDisplayed()
    composeTestRule.onNodeWithText(application.getString(R.string.AttachmentKeyboard_manage)).performClick()

    assertThat(showAtStart).isEqualTo(false)
  }

  @Test
  fun `full access with media shows the rail`() {
    val item = media(id = 9)
    setPicker(LightAttachmentPickerState(mediaAccess = LightAttachmentMediaAccess.Full(listOf(item))))

    composeTestRule.onNodeWithTag(RECENT_MEDIA_RAIL_TAG).assertIsDisplayed()
    composeTestRule.onNodeWithTag(mediaTestTag(item)).assertIsDisplayed()
  }

  @Test
  fun `empty full access omits the entire recent-media block`() {
    setPicker(fullAccess())

    composeTestRule.onNodeWithTag(RECENT_MEDIA_RAIL_TAG).assertDoesNotExist()
    composeTestRule.onNodeWithText(application.getString(R.string.AttachmentKeyboard_no_photos_found)).assertDoesNotExist()
    composeTestRule.onNodeWithText(application.getString(R.string.AttachmentKeyboard_allow_access)).assertDoesNotExist()
  }

  @Test
  fun `no access state explains access and requests permissions`() {
    var permissionRequests = 0
    setPicker(
      state = LightAttachmentPickerState(mediaAccess = LightAttachmentMediaAccess.NoAccess),
      onPermissionsRequested = { permissionRequests++ }
    )

    composeTestRule
      .onNodeWithText(application.getString(R.string.AttachmentKeyboard_Signal_needs_permission_to_show_your_photos_and_videos))
      .assertIsDisplayed()
    composeTestRule.onNodeWithText(application.getString(R.string.AttachmentKeyboard_allow_access)).performClick()

    assertThat(permissionRequests).isEqualTo(1)
  }

  @Test
  fun `state mapping gives limited access precedence over any access`() {
    val item = media(id = 10)

    val limitedEmpty = LightAttachmentPickerState.map(
      actions = LIGHT_ATTACHMENT_ACTIONS,
      media = emptyList(),
      canOnlyReadSelectedMedia = true,
      canReadAnyMedia = true
    )
    val limitedMedia = LightAttachmentPickerState.map(
      actions = LIGHT_ATTACHMENT_ACTIONS,
      media = listOf(item),
      canOnlyReadSelectedMedia = true,
      canReadAnyMedia = true
    )
    val full = LightAttachmentPickerState.map(
      actions = LIGHT_ATTACHMENT_ACTIONS,
      media = listOf(item),
      canOnlyReadSelectedMedia = false,
      canReadAnyMedia = true
    )
    val none = LightAttachmentPickerState.map(
      actions = LIGHT_ATTACHMENT_ACTIONS,
      media = listOf(item),
      canOnlyReadSelectedMedia = false,
      canReadAnyMedia = false
    )

    assertThat(limitedEmpty.mediaAccess).isEqualTo(LightAttachmentMediaAccess.LimitedEmpty)
    assertThat(limitedMedia.mediaAccess).isInstanceOf(LightAttachmentMediaAccess.Limited::class)
    assertThat(full.mediaAccess).isInstanceOf(LightAttachmentMediaAccess.Full::class)
    assertThat(none.mediaAccess).isEqualTo(LightAttachmentMediaAccess.NoAccess)
  }

  @Test
  fun `video duration formatting matches the legacy rail`() {
    assertThat(formatVideoDuration(61_000)).isEqualTo("01:01")
    assertThat(formatVideoDuration(3_661_000)).isEqualTo("01:01:01")

    setPicker(
      LightAttachmentPickerState(
        mediaAccess = LightAttachmentMediaAccess.Full(listOf(media(id = 11, contentType = "video/mp4", duration = 61_000)))
      )
    )
    composeTestRule.onNodeWithText("01:01").assertIsDisplayed()
    composeTestRule.onNodeWithTag(VIDEO_INDICATOR_TAG).assertDoesNotExist()
  }

  @Test
  fun `video without duration keeps the play indicator`() {
    setPicker(
      LightAttachmentPickerState(
        mediaAccess = LightAttachmentMediaAccess.Full(listOf(media(id = 12, contentType = "video/mp4")))
      )
    )

    composeTestRule.onNodeWithTag(VIDEO_INDICATOR_TAG, useUnmergedTree = true).assertIsDisplayed()
  }

  @Test
  fun `LP3 rail is fixed-height and thumbnails are square`() {
    val item = media(id = 13)
    var gridUnit = 0.dp
    composeTestRule.setContent {
      gridUnit = 1f.gridUnitsAsDp()
      MollyLightTheme {
        LightAttachmentPicker(
          state = LightAttachmentPickerState(mediaAccess = LightAttachmentMediaAccess.Full(listOf(item))),
          onBack = {},
          onActionSelected = {},
          onMediaSelected = {},
          onPermissionsRequested = {},
          onManageRequested = {},
          modifier = Modifier
        )
      }
    }

    val rail = composeTestRule.onNodeWithTag(RECENT_MEDIA_RAIL_TAG).getUnclippedBoundsInRoot()
    val thumbnail = composeTestRule.onNodeWithTag(mediaTestTag(item)).getUnclippedBoundsInRoot()

    assertThat((rail.bottom - rail.top).value / gridUnit.value).isCloseTo(6f, 0.05f)
    assertThat((thumbnail.right - thumbnail.left).value / gridUnit.value).isCloseTo(6f, 0.05f)
    assertThat((thumbnail.bottom - thumbnail.top).value / gridUnit.value).isCloseTo(6f, 0.05f)
  }

  @Test
  fun `LP3 rows use reference geometry and every action remains scroll reachable`() {
    var gridUnit: Dp = 0.dp
    composeTestRule.setContent {
      gridUnit = 1f.gridUnitsAsDp()
      MollyLightTheme {
        LightAttachmentPicker(
          state = LightAttachmentPickerState(mediaAccess = LightAttachmentMediaAccess.NoAccess),
          onBack = {},
          onActionSelected = {},
          onMediaSelected = {},
          onPermissionsRequested = {},
          onManageRequested = {}
        )
      }
    }

    val gallery = composeTestRule
      .onNodeWithTag(actionTestTag(AttachmentKeyboardButton.GALLERY), useUnmergedTree = true)
      .getUnclippedBoundsInRoot()
    val galleryLabel = composeTestRule
      .onNodeWithTag(actionLabelTestTag(AttachmentKeyboardButton.GALLERY), useUnmergedTree = true)
      .getUnclippedBoundsInRoot()

    // LightScrollView's outside scrollbar reserves two of the 27 horizontal grid units. Padding is
    // measured against the child rather than Robolectric's stubbed font metrics.
    assertThat((gallery.right - gallery.left).value / gridUnit.value).isCloseTo(25f, 0.05f)
    assertThat((galleryLabel.left - gallery.left).value / gridUnit.value).isCloseTo(2f, 0.05f)
    assertThat((galleryLabel.top - gallery.top).value / gridUnit.value).isCloseTo(1.2f, 0.05f)
    assertThat((gallery.bottom - galleryLabel.bottom).value / gridUnit.value).isCloseTo(1.2f, 0.05f)

    LIGHT_ATTACHMENT_ACTIONS.forEach { action ->
      composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(actionTestTag(action)))
      composeTestRule.onNodeWithTag(actionTestTag(action)).assertIsDisplayed()
    }
  }

  private fun setPicker(
    state: LightAttachmentPickerState,
    onBack: () -> Unit = {},
    onActionSelected: (AttachmentKeyboardButton) -> Unit = {},
    onMediaSelected: (Media) -> Unit = {},
    onPermissionsRequested: () -> Unit = {},
    onManageRequested: (Boolean) -> Unit = {}
  ) {
    composeTestRule.setContent {
      MollyLightTheme {
        LightAttachmentPicker(
          state = state,
          onBack = onBack,
          onActionSelected = onActionSelected,
          onMediaSelected = onMediaSelected,
          onPermissionsRequested = onPermissionsRequested,
          onManageRequested = onManageRequested
        )
      }
    }
  }

  private fun fullAccess(): LightAttachmentPickerState = LightAttachmentPickerState(
    mediaAccess = LightAttachmentMediaAccess.Full(emptyList())
  )

  private fun media(
    id: Int,
    contentType: String = "image/jpeg",
    duration: Long = 0
  ): Media = Media(
    uri = Uri.parse("content://media/$id"),
    contentType = contentType,
    date = id.toLong(),
    width = 100,
    height = 100,
    size = 100,
    duration = duration,
    isBorderless = false,
    isVideoGif = false,
    bucketId = Media.ALL_MEDIA_BUCKET_ID,
    caption = null,
    transformProperties = null,
    fileName = "media-$id"
  )

  private val application: Application
    get() = RuntimeEnvironment.getApplication()
}
