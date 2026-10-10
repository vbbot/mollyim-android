/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.phonenumber

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import android.app.Application
import com.google.i18n.phonenumbers.PhoneNumberUtil
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.registration.ui.countrycode.Country

// Robolectric because the view model exposes its state as LiveData, which needs a main Looper.
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class EnterPhoneNumberViewModelTest {

  private val util = PhoneNumberUtil.getInstance()

  @Test
  fun `restoring a prefilled number sets the country so the number is usable`() {
    val viewModel = EnterPhoneNumberViewModel()
    val prefilled = util.parse("+14169671111", null)

    viewModel.restoreState(prefilled)

    val state = viewModel.state
    assertThat(state.country).isNotNull()
    assertThat(state.country!!.countryCode).isEqualTo(1)
    assertThat(state.country!!.regionCode).isEqualTo("CA")
    assertThat(state.phoneNumber).isEqualTo("4169671111")
    assertThat(state.internationalNumber).isEqualTo("+14169671111")
    assertThat(viewModel.isEnteredNumberPossible(state)).isTrue()
    assertThat(viewModel.parsePhoneNumber(state)).isEqualTo(prefilled)
  }

  @Test
  fun `restoring a number with an unplaceable national part falls back to the code's main region`() {
    val viewModel = EnterPhoneNumberViewModel()

    viewModel.restoreState(util.parse("+447700900123", null))

    assertThat(viewModel.state.country?.regionCode).isEqualTo("GB")
    assertThat(viewModel.isEnteredNumberPossible(viewModel.state)).isTrue()
  }

  @Test
  fun `an edited international number sets country and national number together`() {
    val viewModel = EnterPhoneNumberViewModel()

    viewModel.setInternationalNumber("+7 701 123 4567")

    val state = viewModel.state
    assertThat(state.country?.countryCode).isEqualTo(7)
    assertThat(state.country?.regionCode).isEqualTo("KZ")
    assertThat(state.phoneNumber).isEqualTo("7011234567")
    assertThat(viewModel.isEnteredNumberPossible(state)).isTrue()
    assertThat(viewModel.parsePhoneNumber(state).countryCode).isEqualTo(7)
    assertThat(viewModel.parsePhoneNumber(state).nationalNumber).isEqualTo(7011234567L)
  }

  @Test
  fun `an invalid calling code clears the country`() {
    val viewModel = EnterPhoneNumberViewModel()
    viewModel.setInternationalNumber("+44 7700 900123")

    viewModel.setInternationalNumber("+999 123")

    assertThat(viewModel.state.country).isNull()
    assertThat(viewModel.state.internationalNumber).isEqualTo("+999123")
    assertThat(viewModel.isEnteredNumberPossible(viewModel.state)).isFalse()
  }

  @Test
  fun `picking a country rewrites the code and keeps the national digits`() {
    val viewModel = EnterPhoneNumberViewModel()
    viewModel.setInternationalNumber("+44 7700 900123")

    viewModel.setCountry(1, Country(emoji = "", name = "Canada", countryCode = 1, regionCode = "CA"))

    assertThat(viewModel.state.internationalNumber).isEqualTo("+17700900123")
    assertThat(viewModel.state.phoneNumber).isEqualTo("7700900123")
    assertThat(viewModel.state.country?.regionCode).isEqualTo("CA")
  }

  @Test
  fun `a picked country is kept while the number cannot yet be placed`() {
    val viewModel = EnterPhoneNumberViewModel()
    viewModel.setCountry(1, Country(emoji = "", name = "Canada", countryCode = 1, regionCode = "CA"))

    viewModel.setInternationalNumber("+1 41")

    assertThat(viewModel.state.country?.regionCode).isEqualTo("CA")
  }
}
