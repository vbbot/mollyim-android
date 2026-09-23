/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversationlist.light

import android.content.Context
import org.thoughtcrime.securesms.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Relative timestamp for the Light conversation list rows.
 *
 * This mirrors `formatRelativeTimestamp` in the Light Phone reference chat client
 * (`chats/app/src/main/kotlin/com/lightphone/chats/Format.kt`) rather than Molly's own
 * [org.thoughtcrime.securesms.util.DateUtils.getBriefRelativeTimeSpanString], which produces a
 * different, much wider vocabulary ("just now", "5 minutes ago", "3 hours ago"). The Light rows
 * only have ~4 grid units of width for the time, so the shapes are deliberately short:
 *
 * | age | example |
 * |---|---|
 * | today | `14:02` (24-hour; the AM/PM label does not fit the row) |
 * | yesterday | `Yest` |
 * | within the last 7 days | `Mon` |
 * | earlier this year | `Aug 12` |
 * | previous years | `Aug 2025` |
 *
 * Unlike the reference, the weekday/month names and the "Yest" label are localised: the weekday and
 * month come from the supplied [Locale] and "Yest" is a string resource. The clock stays on the
 * 24-hour `HH:mm` pattern in every locale, because that is what makes the column fit.
 */
object LightRelativeTimestamp {

  /** "Aug 12" / "Dec 01" -- month abbreviation + zero-padded day. */
  private const val MONTH_DAY_PATTERN = "MMM dd"

  /** "Aug 2025" -- month abbreviation + year, for rows older than the current year. */
  private const val MONTH_YEAR_PATTERN = "MMM yyyy"

  /** "14:02" -- 24-hour clock, locale independent. */
  private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

  @JvmStatic
  @JvmOverloads
  fun format(
    context: Context,
    timestampMs: Long,
    locale: Locale = Locale.getDefault(),
    zone: ZoneId = ZoneId.systemDefault(),
    nowMs: Long = System.currentTimeMillis()
  ): String {
    if (timestampMs <= 0L) {
      return ""
    }

    val dateTime = Instant.ofEpochMilli(timestampMs).atZone(zone)
    val date = dateTime.toLocalDate()
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()

    return when {
      date == today -> dateTime.toLocalTime().format(TIME_FORMAT)
      date == today.minusDays(1) -> context.getString(R.string.LightConversationList__yesterday_short)
      date.isAfter(today.minusDays(7)) -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
      date.year == today.year -> date.format(DateTimeFormatter.ofPattern(MONTH_DAY_PATTERN, locale))
      else -> date.format(DateTimeFormatter.ofPattern(MONTH_YEAR_PATTERN, locale))
    }
  }

  /**
   * Accessible description of [timestampMs] -- the row itself only shows the terse label above, so
   * TalkBack gets the full date instead.
   */
  @JvmStatic
  fun describe(context: Context, timestampMs: Long, locale: Locale = Locale.getDefault()): String {
    if (timestampMs <= 0L) {
      return ""
    }
    return org.thoughtcrime.securesms.util.DateUtils.getBriefRelativeTimeSpanString(context, locale, timestampMs).second
  }

}
