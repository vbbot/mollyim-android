/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.phonenumber

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test

class PhoneNumberEntryTest {

  @Test
  fun `+1 numbers resolve to the region the number belongs to`() {
    assertParsed("+1 415 555 0132", countryCode = 1, regionCode = "US", nationalNumber = "4155550132")
    assertParsed("+1 416 967 1111", countryCode = 1, regionCode = "CA", nationalNumber = "4169671111")
  }

  @Test
  fun `a NANP area code with its own region resolves to that region`() {
    assertParsed("+1 268 460 1234", countryCode = 1, regionCode = "AG", nationalNumber = "2684601234")
    // Antigua's leading digits are enough to place it before the number is complete.
    assertParsed("+1268", countryCode = 1, regionCode = "AG", nationalNumber = "268")
  }

  @Test
  fun `+44 resolves to the UK, and a shared code falls back to its main region`() {
    assertParsed("+44 7400 123456", countryCode = 44, regionCode = "GB", nationalNumber = "7400123456")
    // 07700 900xxx is a reserved drama range libphonenumber cannot place, so the main region is used.
    assertParsed("+44 7700 900123", countryCode = 44, regionCode = "GB", nationalNumber = "7700900123")
    assertParsed("+44 7797 712345", countryCode = 44, regionCode = "JE", nationalNumber = "7797712345")
  }

  @Test
  fun `+7 distinguishes Russia from Kazakhstan`() {
    assertParsed("+7 495 123-45-67", countryCode = 7, regionCode = "RU", nationalNumber = "4951234567")
    assertParsed("+7 701 123 4567", countryCode = 7, regionCode = "KZ", nationalNumber = "7011234567")
  }

  @Test
  fun `three-digit calling codes are extracted whole`() {
    assertParsed("+358 40 1234567", countryCode = 358, regionCode = "FI", nationalNumber = "401234567")
    assertParsed("+420 212 345 678", countryCode = 420, regionCode = "CZ", nationalNumber = "212345678")
    assertParsed("+380 44 123 4567", countryCode = 380, regionCode = "UA", nationalNumber = "441234567")
  }

  @Test
  fun `a region hint is kept for an incomplete number with the same code`() {
    assertParsed("+1", countryCode = 1, regionCode = "US", nationalNumber = "")
    assertParsed("+1", countryCode = 1, regionCode = "CA", nationalNumber = "", regionHint = "CA")
    assertParsed("+44 77", countryCode = 44, regionCode = "JE", nationalNumber = "77", regionHint = "JE")
    // A hint for a different code is ignored.
    assertParsed("+44", countryCode = 44, regionCode = "GB", nationalNumber = "", regionHint = "US")
    // The number itself wins over the hint once it can be placed.
    assertParsed("+1 415 555 0132", countryCode = 1, regionCode = "US", nationalNumber = "4155550132", regionHint = "CA")
  }

  @Test
  fun `an invalid calling code yields no country`() {
    val parsed = PhoneNumberEntry.parse("+999 123")
    assertThat(parsed.text).isEqualTo("+999123")
    assertThat(parsed.countryCode).isNull()
    assertThat(parsed.regionCode).isNull()
    assertThat(parsed.nationalNumber).isEqualTo("")
  }

  @Test
  fun `empty input and a lone plus yield no country`() {
    PhoneNumberEntry.parse("").let {
      assertThat(it.text).isEqualTo("")
      assertThat(it.countryCode).isNull()
    }
    PhoneNumberEntry.parse("+").let {
      assertThat(it.text).isEqualTo("+")
      assertThat(it.countryCode).isNull()
    }
  }

  @Test
  fun `spaces, dashes and other characters are dropped`() {
    assertThat(PhoneNumberEntry.sanitize("+1 (415) 555-0132")).isEqualTo("+14155550132")
    assertThat(PhoneNumberEntry.sanitize("44 7700-900 123")).isEqualTo("+447700900123")
    assertThat(PhoneNumberEntry.sanitize("+4+4")).isEqualTo("+44")
    assertParsed("+1 (415) 555-0132", countryCode = 1, regionCode = "US", nationalNumber = "4155550132")
  }

  @Test
  fun `numbers are formatted internationally`() {
    assertThat(PhoneNumberEntry.format("+14155550132")).isEqualTo("+1 415-555-0132")
    assertThat(PhoneNumberEntry.format("+447700900123")).isEqualTo("+44 7700 900123")
    assertThat(PhoneNumberEntry.format("+380441234567")).isEqualTo("+380 44 123 4567")
    assertThat(PhoneNumberEntry.format("")).isEqualTo("")
    assertThat(PhoneNumberEntry.format("+")).isEqualTo("+")
  }

  @Test
  fun `compose rewrites the calling code and keeps the national digits`() {
    assertThat(PhoneNumberEntry.compose(44, "7700 900123")).isEqualTo("+447700900123")
    assertThat(PhoneNumberEntry.compose(1, "")).isEqualTo("+1")
  }

  @Test
  fun `typing at the end keeps the cursor at the end after formatting`() {
    var text = ""
    "+14155550132".forEach { typed ->
      val edit = PhoneNumberEntry.reformat(previous = text, raw = text + typed, cursor = text.length + 1)
      assertThat(edit.cursor).isEqualTo(edit.text.length)
      text = edit.text
    }
    assertThat(text).isEqualTo("+1 415-555-0132")
  }

  @Test
  fun `a first digit gains the plus and the cursor follows the digit`() {
    val edit = PhoneNumberEntry.reformat(previous = "", raw = "4", cursor = 1)
    assertThat(edit).isEqualTo(PhoneNumberEntry.Edit("+4", 2))
  }

  @Test
  fun `typing in the middle keeps the cursor after the typed digit`() {
    // "+1 415-555" with a 9 typed after "+1 4".
    val edit = PhoneNumberEntry.reformat(previous = "+1 415-555", raw = "+1 4915-555", cursor = 5)
    assertThat(edit.text).isEqualTo("+1 491-555-5")
    assertThat(edit.cursor).isEqualTo(5)
  }

  @Test
  fun `backspace over a separator removes the digit before it`() {
    // Cursor after the dash in "+1 415-55"; backspace removes only the dash.
    val edit = PhoneNumberEntry.reformat(previous = "+1 415-55", raw = "+1 41555", cursor = 6)
    assertThat(edit).isEqualTo(PhoneNumberEntry.Edit("+1 415-5", 5))
  }

  @Test
  fun `backspace over the trailing space after a code removes the code's last digit`() {
    val edit = PhoneNumberEntry.reformat(previous = "+44 ", raw = "+44", cursor = 3)
    assertThat(edit).isEqualTo(PhoneNumberEntry.Edit("+4", 2))
  }

  private fun assertParsed(
    input: String,
    countryCode: Int,
    regionCode: String,
    nationalNumber: String,
    regionHint: String? = null
  ) {
    val parsed = PhoneNumberEntry.parse(input, regionHint)
    assertThat(parsed.countryCode).isEqualTo(countryCode)
    assertThat(parsed.regionCode).isEqualTo(regionCode)
    assertThat(parsed.nationalNumber).isEqualTo(nationalNumber)
  }
}
