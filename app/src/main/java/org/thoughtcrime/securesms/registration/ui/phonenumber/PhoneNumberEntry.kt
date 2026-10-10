/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.phonenumber

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil

/**
 * Pure helpers for the Light phone-number editor, which takes the whole international number in one
 * field ("+44 7700 900123"). Splits it into a calling code, region and national number for
 * [EnterPhoneNumberViewModel], and formats it as-you-type with a cursor that survives reformatting.
 */
object PhoneNumberEntry {

  /** Mirrors libphonenumber's package-private `MAX_LENGTH_COUNTRY_CODE`. */
  private const val MAX_COUNTRY_CODE_LENGTH = 3

  /** What libphonenumber returns for a calling code or number it cannot place. */
  private const val UNKNOWN_REGION = "ZZ"

  /**
   * Any valid region works here: the text always starts with '+', so [com.google.i18n.phonenumbers.AsYouTypeFormatter]
   * reads the calling code from the digits and formats internationally. "ZZ" would log a warning per call.
   */
  private const val FORMATTER_REGION = "US"

  /** [text] is sanitized ("+" and digits only); the other fields are null when no valid calling code leads it. */
  data class Parsed(
    val text: String,
    val countryCode: Int?,
    val regionCode: String?,
    val nationalNumber: String
  )

  /** A formatted value and where the cursor belongs in it. */
  data class Edit(val text: String, val cursor: Int)

  /** Keeps digits only, behind a single leading '+'. Empty input stays empty; a lone "+" stays "+". */
  fun sanitize(raw: String): String {
    val digits = raw.filter(Char::isDigit)
    return if (digits.isEmpty() && '+' !in raw) "" else "+$digits"
  }

  /**
   * Splits [raw] into calling code, region and national number.
   *
   * The calling code is found the way libphonenumber's own (package-private) `extractCountryCode`
   * does it: try 1- to 3-digit prefixes and take the first that maps to a region. Calling codes are
   * prefix-free, so the first valid match is also the only one.
   *
   * The region comes from the full number when libphonenumber can place it (+1 416 → CA, +7 701 → KZ,
   * +1 268 → AG). Otherwise [regionHint] is kept if it shares the calling code (so a partially typed
   * number stays in the country picked by hand), and failing that the code's main region.
   */
  fun parse(raw: String, regionHint: String? = null): Parsed {
    val text = sanitize(raw)
    val digits = text.removePrefix("+")
    val util = PhoneNumberUtil.getInstance()

    val codeLength = countryCodeLength(digits) ?: return Parsed(text, null, null, "")
    val countryCode = digits.take(codeLength).toInt()
    val nationalNumber = digits.drop(codeLength)

    val regionFromNumber = if (nationalNumber.isEmpty()) {
      null
    } else {
      try {
        util.getRegionCodeForNumber(util.parse("+$digits", null))
      } catch (e: NumberParseException) {
        null
      }
    }

    val regionCode = regionFromNumber?.takeUnless { it == UNKNOWN_REGION }
      ?: regionHint?.takeIf { it in util.getRegionCodesForCountryCode(countryCode) }
      ?: util.getRegionCodeForCountryCode(countryCode)

    return Parsed(text, countryCode, regionCode, nationalNumber)
  }

  /** The editor text for a calling code and national number, as used when a country is picked. */
  fun compose(countryCode: Int, nationalNumber: String): String = "+$countryCode${nationalNumber.filter(Char::isDigit)}"

  /** Formats sanitized or raw text for display, e.g. "+14155550132" → "+1 415-555-0132". */
  fun format(raw: String): String {
    val text = sanitize(raw)
    if (text.isEmpty()) return ""

    val formatter = PhoneNumberUtil.getInstance().getAsYouTypeFormatter(FORMATTER_REGION)
    var formatted = formatter.inputDigit('+')
    text.drop(1).forEach { formatted = formatter.inputDigit(it) }
    return formatted
  }

  /**
   * Reformats an edit of [previous] into [raw] with the cursor at [cursor] in [raw], returning the
   * formatted text and the cursor position that keeps the same digits to its left.
   *
   * If the edit removed only a separator (a backspace over a space or dash), the digit before the
   * cursor is removed instead; otherwise the formatter would put the separator straight back and
   * backspace would appear to do nothing.
   */
  fun reformat(previous: String, raw: String, cursor: Int): Edit {
    var input = raw
    var inputCursor = cursor.coerceIn(0, raw.length)

    if (raw.length < previous.length && sanitize(raw) == sanitize(previous)) {
      val digitIndex = (inputCursor - 1 downTo 0).firstOrNull { raw[it].isDigit() }
      if (digitIndex != null) {
        input = raw.removeRange(digitIndex, digitIndex + 1)
        inputCursor = digitIndex
      }
    }

    val digitsBeforeCursor = input.take(inputCursor).count(Char::isDigit)
    val totalDigits = input.count(Char::isDigit)
    val formatted = format(input)

    val formattedCursor = when {
      digitsBeforeCursor >= totalDigits -> formatted.length
      digitsBeforeCursor == 0 -> if (formatted.startsWith('+')) 1 else 0
      else -> positionAfterDigit(formatted, digitsBeforeCursor)
    }
    return Edit(formatted, formattedCursor)
  }

  private fun countryCodeLength(digits: String): Int? {
    // Calling codes never start with 0.
    if (digits.isEmpty() || digits[0] == '0') return null

    val util = PhoneNumberUtil.getInstance()
    return (1..minOf(MAX_COUNTRY_CODE_LENGTH, digits.length)).firstOrNull { length ->
      util.getRegionCodeForCountryCode(digits.take(length).toInt()) != UNKNOWN_REGION
    }
  }

  private fun positionAfterDigit(text: String, digitCount: Int): Int {
    var seen = 0
    text.forEachIndexed { index, c ->
      if (c.isDigit()) {
        seen++
        if (seen == digitCount) return index + 1
      }
    }
    return text.length
  }
}
