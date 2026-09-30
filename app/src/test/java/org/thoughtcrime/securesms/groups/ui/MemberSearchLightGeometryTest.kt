/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.groups.ui

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class MemberSearchLightGeometryTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `LP3 keeps every filter reachable inside the viewport`() {
    setPicker()

    val root = composeTestRule.onRoot().getUnclippedBoundsInRoot()
    filterLabels.forEach { label ->
      val option = composeTestRule
        .onNodeWithText(label)
        .assertIsDisplayed()
        .getUnclippedBoundsInRoot()

      assertThat(option.top.value).isBetween(root.top.value, root.bottom.value)
      assertThat(option.bottom.value).isBetween(root.top.value, root.bottom.value)
    }
  }

  @Test
  fun `filter selection is visible and routes once`() {
    val selections = mutableListOf<MemberSearchViewModel.MemberFilter>()
    setPicker(onFilterSelected = { selections += it })

    composeTestRule.onNodeWithText(filterLabels.first()).assertIsSelected()
    composeTestRule.onNodeWithText(filterLabels.last()).performClick()

    assertThat(selections).isEqualTo(listOf(MemberSearchViewModel.MemberFilter.CONTACTS))
  }

  private fun setPicker(
    onFilterSelected: (MemberSearchViewModel.MemberFilter) -> Unit = {}
  ) {
    composeTestRule.setContent {
      MollyLightTheme {
        MemberSearchFilterPicker(
          memberFilter = MemberSearchViewModel.MemberFilter.ALL,
          onFilterSelected = onFilterSelected,
          onDismiss = {}
        )
      }
    }
  }

  private val filterLabels: List<String>
    get() = RuntimeEnvironment.getApplication()
      .resources
      .getStringArray(R.array.filter_search_entries)
      .map { it.uppercase(Locale.getDefault()) }
}
