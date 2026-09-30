/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.mutiselect.forward

import android.app.Application
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.width
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThanOrEqualTo
import java.util.Locale
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.contacts.paged.ContactSearchConfiguration
import org.thoughtcrime.securesms.contacts.paged.ContactSearchKey
import org.thoughtcrime.securesms.light.MollyLightTheme

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class MultiselectForwardBottomBarGeometryTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `LP3 summary caption and send action remain vertically ordered and bounded`() {
    render(isSplitPane = false)

    val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    val summary = composeTestRule.onNodeWithText(RECIPIENT).getUnclippedBoundsInRoot()
    val caption = composeTestRule.onNodeWithText(CAPTION).getUnclippedBoundsInRoot()
    val send = composeTestRule.onNodeWithText(sendLabel()).getUnclippedBoundsInRoot()

    assertThat(caption.top).isGreaterThan(summary.top)
    assertThat(send.top).isGreaterThan(caption.top)
    assertThat(send.bottom).isLessThanOrEqualTo(root.bottom)
    assertThat(send.left).isGreaterThanOrEqualTo(root.left)
    assertThat(send.right).isLessThanOrEqualTo(root.right)
  }

  @Test
  fun `split pane keeps the forwarding actions in the right half`() {
    render(isSplitPane = true)

    val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    val send = composeTestRule.onNodeWithText(sendLabel()).getUnclippedBoundsInRoot()

    assertThat(send.left).isGreaterThanOrEqualTo(root.left + root.width / 2 - 1.dp)
  }

  private fun render(isSplitPane: Boolean) {
    composeTestRule.setContent {
      MollyLightTheme {
        MultiselectForwardBottomBar(
          isSplitPane = isSplitPane,
          state = MultiselectForwardBottomBarState(
            selection = listOf(
              MultiselectForwardBottomBarState.SelectedContact.UnknownRecipient(
                selectionKey = ContactSearchKey.UnknownRecipientKey(
                  ContactSearchConfiguration.SectionKey.PHONE_NUMBER,
                  RECIPIENT
                ),
                e164 = RECIPIENT
              )
            ),
            message = CAPTION,
            isSendButtonEnabled = true,
            isSendButtonVisible = true,
            isAddMessageVisible = true
          ),
          events = {}
        )
      }
    }
  }

  private fun sendLabel(): String = RuntimeEnvironment.getApplication()
    .getString(R.string.ShareActivity__send)
    .uppercase(Locale.getDefault())

  companion object {
    private const val RECIPIENT = "+15551234567"
    private const val CAPTION = "Caption"
  }
}
