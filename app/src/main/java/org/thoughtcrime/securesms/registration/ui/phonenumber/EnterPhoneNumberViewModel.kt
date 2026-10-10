/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.phonenumber

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.signal.core.util.E164Util
import org.signal.core.util.Util
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.registration.data.RegistrationRepository
import org.thoughtcrime.securesms.registration.ui.countrycode.Country
import org.thoughtcrime.securesms.registration.ui.countrycode.CountryUtils
import org.thoughtcrime.securesms.registration.util.CountryPrefix

/**
 * ViewModel for the phone number entry screen.
 */
class EnterPhoneNumberViewModel : ViewModel() {

  companion object {
    private val TAG = Log.tag(EnterPhoneNumberViewModel::class.java)
  }

  val supportedCountryPrefixes: List<CountryPrefix> = PhoneNumberUtil.getInstance().supportedCallingCodes
    .map { CountryPrefix(it, PhoneNumberUtil.getInstance().getRegionCodeForCountryCode(it)) }
    .sortedBy { it.digits }

  private val store = MutableStateFlow(
    EnterPhoneNumberState(
      countryPrefixIndex = 0,
      phoneNumberRegionCode = supportedCountryPrefixes[0].regionCode
    )
  )
  val uiState = store.asLiveData()

  /** The current state, read synchronously where an observer would run too late. */
  val state: EnterPhoneNumberState
    get() = store.value

  /** The whole number as the Light editor holds it, e.g. "+447700900123". */
  val internationalNumber: String
    get() = store.value.internationalNumber

  val phoneNumber: PhoneNumber?
    get() = try {
      parsePhoneNumber(store.value)
    } catch (ex: NumberParseException) {
      Log.w(TAG, "Could not parse phone number in current state.", ex)
      null
    }

  var e164VerificationMode: RegistrationRepository.E164VerificationMode
    get() = store.value.mode
    set(value) = store.update {
      it.copy(mode = value)
    }

  fun getDefaultCountryCode(context: Context): Int {
    val existingCountry = store.value.country
    val maybeRegionCode = Util.getNetworkCountryIso(context)
    val regionCode = if (maybeRegionCode != null && supportedCountryPrefixes.any { it.regionCode == maybeRegionCode }) {
      maybeRegionCode
    } else {
      Log.w(TAG, "Could not find region code")
      "US"
    }

    val countryCode = PhoneNumberUtil.getInstance().getCountryCodeForRegion(regionCode)
    val prefixIndex = countryCodeToAdapterIndex(countryCode)

    store.update {
      it.copy(
        countryPrefixIndex = prefixIndex,
        phoneNumberRegionCode = regionCode,
        country = existingCountry ?: Country(
          name = E164Util.getRegionDisplayName(regionCode).orElse(""),
          emoji = CountryUtils.countryToEmoji(regionCode),
          countryCode = countryCode,
          regionCode = regionCode
        )
      )
    }

    return existingCountry?.countryCode ?: countryCode
  }

  val country: Country?
    get() = store.value.country

  fun setPhoneNumber(phoneNumber: String?) {
    store.update { it.copy(phoneNumber = phoneNumber ?: "") }
  }

  fun clearCountry() {
    store.update {
      it.copy(
        country = null,
        phoneNumberRegionCode = "",
        countryPrefixIndex = 0
      )
    }
  }

  fun setCountry(digits: Int, country: Country? = null) {
    if (country == null && digits == store.value.country?.countryCode) {
      return
    }

    val matchingIndex = countryCodeToAdapterIndex(digits)
    if (matchingIndex == -1) {
      Log.d(TAG, "Invalid country code specified $digits")
      store.update {
        it.copy(
          country = null,
          phoneNumberRegionCode = "",
          countryPrefixIndex = 0
        )
      }
      return
    }

    val regionCode = supportedCountryPrefixes[matchingIndex].regionCode
    val matchedCountry = Country(
      name = E164Util.getRegionDisplayName(regionCode).orElse(""),
      emoji = CountryUtils.countryToEmoji(regionCode),
      countryCode = digits,
      regionCode = regionCode
    )

    store.update {
      it.copy(
        countryPrefixIndex = matchingIndex,
        phoneNumberRegionCode = supportedCountryPrefixes[matchingIndex].regionCode,
        country = country ?: matchedCountry,
        // A country picked by hand replaces the "+code" of the editor's number and keeps its national digits.
        internationalNumber = PhoneNumberEntry.compose(digits, it.phoneNumber)
      )
    }
  }

  /**
   * The single entry point for a whole international number ("+44 7700 900123"), from the Light editor,
   * a restored or prefilled number, or a server-normalized one. Sets the national number and, through
   * [setCountry] exactly as the country picker does, the country derived from the calling code, so that
   * [EnterPhoneNumberState.country] is never null while a valid code is present.
   *
   * [regionHint] keeps a shared calling code (+1, +7, +44…) in the region already shown until the
   * number itself says otherwise.
   */
  fun setInternationalNumber(text: String, regionHint: String? = store.value.country?.regionCode) {
    val entry = PhoneNumberEntry.parse(text, regionHint)

    setPhoneNumber(entry.nationalNumber)
    if (entry.countryCode != null && entry.regionCode != null) {
      setCountry(entry.countryCode, countryForRegion(entry.countryCode, entry.regionCode))
    } else {
      clearCountry()
    }
    store.update { it.copy(internationalNumber = entry.text) }
  }

  fun parsePhoneNumber(state: EnterPhoneNumberState): PhoneNumber {
    return PhoneNumberUtil.getInstance().parse(state.phoneNumber, supportedCountryPrefixes[state.countryPrefixIndex].regionCode)
  }

  fun isEnteredNumberPossible(state: EnterPhoneNumberState): Boolean {
    return try {
      state.country != null &&
        PhoneNumberUtil.getInstance().isPossibleNumber(parsePhoneNumber(state))
    } catch (ex: NumberParseException) {
      false
    }
  }

  /**
   * Restores a previously entered or SIM-prefilled number. Goes through [setInternationalNumber] so the
   * country is set too; setting only the number left NEXT disabled until a country was picked by hand.
   */
  fun restoreState(value: PhoneNumber) {
    if (countryCodeToAdapterIndex(value.countryCode) == -1) {
      return
    }

    val util = PhoneNumberUtil.getInstance()
    setInternationalNumber(
      text = PhoneNumberEntry.compose(value.countryCode, util.getNationalSignificantNumber(value)),
      regionHint = util.getRegionCodeForNumber(value)
    )
  }

  private fun countryCodeToAdapterIndex(countryCode: Int): Int {
    return supportedCountryPrefixes.indexOfFirst { prefix -> prefix.digits == countryCode }
  }

  private fun countryForRegion(countryCode: Int, regionCode: String): Country {
    return Country(
      name = E164Util.getRegionDisplayName(regionCode).orElse(""),
      emoji = CountryUtils.countryToEmoji(regionCode),
      countryCode = countryCode,
      regionCode = regionCode
    )
  }

  fun clearError() {
    setError(EnterPhoneNumberState.Error.NONE)
  }

  fun setError(error: EnterPhoneNumberState.Error) {
    store.update {
      it.copy(error = error)
    }
  }
}
