/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.calls.links

import android.app.Application
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isLessThanOrEqualTo
import com.thelightphone.sdk.ui.gridUnitsAsDp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.thoughtcrime.securesms.calls.links.create.CREATE_CALL_LINK_SHARE_TAG
import org.thoughtcrime.securesms.calls.links.create.CreateCallLinkBottomSheetContent
import org.thoughtcrime.securesms.calls.links.create.CreateCallLinkCallbacks
import org.thoughtcrime.securesms.calls.links.create.CreateCallLinkLightState
import org.thoughtcrime.securesms.calls.links.details.CALL_LINK_DETAILS_DELETE_TAG
import org.thoughtcrime.securesms.calls.links.details.CallLinkDetailsCallback
import org.thoughtcrime.securesms.calls.links.details.CallLinkDetailsLightState
import org.thoughtcrime.securesms.calls.links.details.CallLinkDetailsScreen
import org.thoughtcrime.securesms.light.MollyLightTheme

@RunWith(RobolectricTestRunner::class)
// LP3 geometry: 1080 x 1240 at 3x, or 360dp x 413dp.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class CallLinkLightGeometryTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `call summary is five grid units and actions are three`() {
    var gridUnit = 0.dp
    composeTestRule.setContent {
      gridUnit = 1f.gridUnitsAsDp()
      MollyLightTheme {
        CreateCallLinkBottomSheetContent(
          state = createState(),
          callbacks = CreateCallLinkCallbacks.Empty
        )
      }
    }

    val callBounds = composeTestRule.onNodeWithTag(SIGNAL_CALL_ROW_TAG).getUnclippedBoundsInRoot()
    val actionBounds = composeTestRule.onNodeWithTag(CREATE_CALL_LINK_SHARE_TAG).getUnclippedBoundsInRoot()

    assertThat(callBounds.height.value).isCloseTo(5f * gridUnit.value, TOLERANCE_DP)
    assertThat(actionBounds.height.value).isCloseTo(3f * gridUnit.value, TOLERANCE_DP)
  }

  @Test
  fun `complete create surface fits an LP3 viewport`() {
    renderCreate()

    val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    val finalAction = composeTestRule.onNodeWithTag(CREATE_CALL_LINK_SHARE_TAG).getUnclippedBoundsInRoot()

    assertThat(root.width.value).isCloseTo(360f, TOLERANCE_DP)
    assertThat(root.height.value).isCloseTo(413f, TOLERANCE_DP)
    assertThat(finalAction.bottom.value).isLessThanOrEqualTo(root.bottom.value + TOLERANCE_DP)
  }

  @Test
  fun `edit field keeps the Light two-unit LP3 gutter`() {
    var gridUnit = 0.dp
    composeTestRule.setContent {
      gridUnit = 1f.gridUnitsAsDp()
      MollyLightTheme {
        EditCallLinkNameContent(
          state = EditCallLinkNameLightState(TextFieldValue("Weekend call")),
          callbacks = EditCallLinkNameCallbacks.Empty
        )
      }
    }

    val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    val field = composeTestRule.onNodeWithTag(EDIT_CALL_LINK_NAME_FIELD_TAG).getUnclippedBoundsInRoot()

    assertThat((field.left - root.left).value).isCloseTo(2f * gridUnit.value, TOLERANCE_DP)
    assertThat((root.right - field.right).value).isCloseTo(2f * gridUnit.value, TOLERANCE_DP)
    assertThat(field.bottom.value).isLessThanOrEqualTo(root.bottom.value + TOLERANCE_DP)
  }

  @Test
  fun `modifiable details surface fits an LP3 viewport`() {
    composeTestRule.setContent {
      MollyLightTheme {
        CallLinkDetailsScreen(
          state = CallLinkDetailsLightState(
            call = createState().call,
            canModify = true,
            approvalRequired = true
          ),
          callback = CallLinkDetailsCallback.Empty
        )
      }
    }

    val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    val delete = composeTestRule.onNodeWithTag(CALL_LINK_DETAILS_DELETE_TAG).getUnclippedBoundsInRoot()

    assertThat(delete.bottom.value).isLessThanOrEqualTo(root.bottom.value + TOLERANCE_DP)
  }

  private fun renderCreate() {
    composeTestRule.setContent {
      MollyLightTheme {
        CreateCallLinkBottomSheetContent(
          state = createState(),
          callbacks = CreateCallLinkCallbacks.Empty
        )
      }
    }
  }

  private fun createState() = CreateCallLinkLightState(
    call = SignalCallRowState(
      name = "Weekend call",
      url = "https://signal.link/call/#key=display-only"
    ),
    approvalRequired = true
  )

  companion object {
    private const val TOLERANCE_DP = 0.75f
  }
}
