/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.accountlocked

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.fragment.app.FragmentActivity
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.signal.core.ui.CoreUiDependenciesRule
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.PinValues
import org.thoughtcrime.securesms.keyvalue.RegistrationValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType
import java.util.Locale

/**
 * "Account locked" in the Light grammar: the title in the top bar, the days-remaining copy, and the
 * two actions the screen always had, each running only its own callback. The fragment test at the
 * end pins the one behaviour that lives outside the composable: back closes registration, exactly
 * as "next" does.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class AccountLockedScreenTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  private val context = RuntimeEnvironment.getApplication()

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `title is in the top bar and the copy says how many days remain`() {
    composeTestRule.setContent {
      AccountLockedScreen(daysRemaining = 7, onNext = {}, onLearnMore = {})
    }

    composeTestRule.onNodeWithText(context.getString(R.string.AccountLockedFragment__account_locked)).assertIsDisplayed()
    composeTestRule
      .onNodeWithText(context.getString(R.string.AccountLockedFragment__your_account_has_been_locked_to_protect_your_privacy, 7L))
      .assertIsDisplayed()
  }

  @Test
  fun `no days yet draws no explanation rather than a made-up number`() {
    composeTestRule.setContent {
      AccountLockedScreen(daysRemaining = null, onNext = {}, onLearnMore = {})
    }

    composeTestRule
      .onNodeWithText(context.getString(R.string.AccountLockedFragment__your_account_has_been_locked_to_protect_your_privacy, 7L))
      .assertDoesNotExist()
  }

  @Test
  fun `each action runs only its own callback`() {
    var next = 0
    var learnMore = 0

    composeTestRule.setContent {
      AccountLockedScreen(daysRemaining = 3, onNext = { next++ }, onLearnMore = { learnMore++ })
    }

    composeTestRule.onNodeWithText(label(R.string.AccountLockedFragment__learn_more)).performClick()
    assertThat(learnMore).isEqualTo(1)
    assertThat(next).isEqualTo(0)

    composeTestRule.onNodeWithText(label(R.string.AccountLockedFragment__next)).performClick()
    assertThat(learnMore).isEqualTo(1)
    assertThat(next).isEqualTo(1)
  }

  @Test
  fun `back closes registration, as next does`() {
    mockkObject(SignalStore)
    every { SignalStore.pin } returns mockk<PinValues>(relaxed = true) {
      every { keyboardType } returns PinKeyboardType.NUMERIC
    }
    every { SignalStore.registration } returns mockk<RegistrationValues>(relaxed = true) {
      every { sessionE164 } returns null
    }

    val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    activity.supportFragmentManager.beginTransaction().add(android.R.id.content, AccountLockedFragment()).commitNow()

    activity.onBackPressedDispatcher.onBackPressed()

    assertThat(activity.isFinishing).isTrue()
  }

  /** Bottom actions are drawn upper-cased, as the Light `Button` variant always is. */
  private fun label(id: Int): String = context.getString(id).uppercase(Locale.getDefault())
}
