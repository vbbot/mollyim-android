/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2.light

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThanOrEqualTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.signal.core.ui.CoreUiDependenciesRule
import org.thoughtcrime.securesms.light.MollyLightTheme
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class LightConversationSearchTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `result navigation keeps Signal ordering and disables both ends`() {
    val newest = LightConversationSearchNavigationState(position = 0, count = 3)
    val middle = LightConversationSearchNavigationState(position = 1, count = 3)
    val oldest = LightConversationSearchNavigationState(position = 2, count = 3)

    assertThat(newest.canMoveToPrevious).isEqualTo(true)
    assertThat(newest.canMoveToNext).isEqualTo(false)
    assertThat(middle.canMoveToPrevious).isEqualTo(true)
    assertThat(middle.canMoveToNext).isEqualTo(true)
    assertThat(oldest.canMoveToPrevious).isEqualTo(false)
    assertThat(oldest.canMoveToNext).isEqualTo(true)
  }

  @Test
  fun `loading disables result movement but keeps date jump available`() {
    var tapped: String? = null

    composeTestRule.setContent {
      MollyLightTheme {
        LightConversationSearchNavigation(
          state = LightConversationSearchNavigationState(position = 1, count = 3, loading = true),
          onMoveToPrevious = { tapped = "previous" },
          onMoveToNext = { tapped = "next" },
          onJumpToDate = { tapped = "date" }
        )
      }
    }

    composeTestRule.onNodeWithText("Searching…").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("Previous result").assertIsNotEnabled()
    composeTestRule.onNodeWithContentDescription("Next result").assertIsNotEnabled()
    composeTestRule.onNodeWithContentDescription("Jump to date").performClick()

    assertThat(tapped).isEqualTo("date")
  }

  @Test
  fun `result movement routes to the distinct callbacks`() {
    var tapped: String? = null

    composeTestRule.setContent {
      MollyLightTheme {
        LightConversationSearchNavigation(
          state = LightConversationSearchNavigationState(position = 1, count = 3),
          onMoveToPrevious = { tapped = "previous" },
          onMoveToNext = { tapped = "next" },
          onJumpToDate = {}
        )
      }
    }

    composeTestRule.onNodeWithContentDescription("Previous result").performClick()
    assertThat(tapped).isEqualTo("previous")

    composeTestRule.onNodeWithContentDescription("Next result").performClick()
    assertThat(tapped).isEqualTo("next")
  }

  @Test
  fun `date state cannot navigate or select outside conversation bounds`() {
    val state = LightConversationDatePickerState(
      earliestDate = LocalDate.of(2026, 1, 20),
      latestDate = LocalDate.of(2026, 2, 10)
    )

    assertThat(state.canShowPreviousMonth).isEqualTo(true)
    assertThat(state.canShowNextMonth).isEqualTo(false)
    assertThat(state.showNextMonth()).isEqualTo(state)
    assertThat(state.select(LocalDate.of(2026, 1, 19))).isEqualTo(state)
    assertThat(state.showPreviousMonth().displayedMonth).isEqualTo(YearMonth.of(2026, 1))
  }

  @Test
  fun `calendar grid is stable and follows the locale week start`() {
    val state = LightConversationDatePickerState(
      earliestDate = LocalDate.of(2026, 2, 1),
      latestDate = LocalDate.of(2026, 2, 28)
    )
    val dates = state.dates(DayOfWeek.MONDAY)

    assertThat(dates.size).isEqualTo(42)
    assertThat(dates.indexOf(LocalDate.of(2026, 2, 1))).isEqualTo(6)
  }

  @Test
  fun `date callback retains Material picker midnight UTC contract`() {
    val date = LocalDate.of(2026, 2, 3)

    assertThat(materialDatePickerTimestamp(date)).isEqualTo(
      date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
  }

  @Test
  fun `selecting a valid date waits for explicit confirmation`() {
    var selectedTimestamp: Long? = null
    val state = mutableStateOf(
      LightConversationDatePickerState(
        earliestDate = LocalDate.of(2026, 2, 1),
        latestDate = LocalDate.of(2026, 2, 28)
      )
    )

    composeTestRule.setContent {
      MollyLightTheme {
        LightConversationDatePicker(
          state = state.value,
          isDateValid = { true },
          onStateChange = { state.value = it },
          onConfirm = { selectedTimestamp = it },
          onBack = {},
          locale = Locale.US
        )
      }
    }

    composeTestRule.onNodeWithContentDescription("Tuesday, February 3, 2026").performClick()
    assertThat(selectedTimestamp).isEqualTo(null)

    composeTestRule.onNodeWithText("Select").performClick()

    assertThat(selectedTimestamp).isEqualTo(materialDatePickerTimestamp(LocalDate.of(2026, 2, 3)))
  }

  @Test
  fun `date selector actions fit inside the LP3 viewport`() {
    composeTestRule.setContent {
      MollyLightTheme {
        LightConversationDatePicker(
          state = LightConversationDatePickerState(
            earliestDate = LocalDate.of(2026, 1, 1),
            latestDate = LocalDate.of(2026, 2, 28)
          ),
          isDateValid = { true },
          onStateChange = {},
          onConfirm = {},
          onBack = {},
          locale = Locale.US
        )
      }
    }

    val rootBottom = composeTestRule.onRoot().getUnclippedBoundsInRoot().bottom
    val lastDayBottom = composeTestRule
      .onNodeWithContentDescription("Saturday, February 28, 2026")
      .getUnclippedBoundsInRoot()
      .bottom

    assertThat(lastDayBottom.value).isLessThanOrEqualTo(rootBottom.value)
  }
}
