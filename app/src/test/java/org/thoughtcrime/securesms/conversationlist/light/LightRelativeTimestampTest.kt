/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversationlist.light

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * Pins the shapes of the Light conversation list's timestamp column. These are load-bearing for the
 * layout -- the column is only a few grid units wide -- so a regression to, say, "3 hours ago" would
 * silently squeeze the conversation name off the row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = Application::class)
class LightRelativeTimestampTest {

  private val zone: ZoneId = ZoneId.of("UTC")
  private val context get() = ApplicationProvider.getApplicationContext<Application>()

  /** Wednesday 2026-09-09, 18:30 UTC. */
  private val now = at(2026, 9, 9, 18, 30)

  private fun at(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long {
    return LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()
  }

  private fun format(timestampMs: Long): String {
    return LightRelativeTimestamp.format(context, timestampMs, Locale.US, zone, now)
  }

  @Test
  fun today_is_a_24_hour_clock() {
    assertEquals("09:05", format(at(2026, 9, 9, 9, 5)))
    assertEquals("18:29", format(at(2026, 9, 9, 18, 29)))
    assertEquals("00:00", format(at(2026, 9, 9, 0, 0)))
  }

  @Test
  fun yesterday_is_the_short_label() {
    assertEquals("Yest", format(at(2026, 9, 8, 23, 59)))
    assertEquals("Yest", format(at(2026, 9, 8, 0, 0)))
  }

  @Test
  fun within_the_last_week_is_a_short_weekday() {
    assertEquals("Mon", format(at(2026, 9, 7, 12, 0)))
    assertEquals("Thu", format(at(2026, 9, 3, 12, 0)))
  }

  @Test
  fun seven_days_back_falls_through_to_a_date() {
    // 2026-09-02 is exactly seven days back, which is no longer "within the last week".
    assertEquals("Sep 02", format(at(2026, 9, 2, 12, 0)))
  }

  @Test
  fun earlier_this_year_is_month_and_day() {
    assertEquals("Aug 12", format(at(2026, 8, 12, 12, 0)))
    assertEquals("Jan 01", format(at(2026, 1, 1, 12, 0)))
  }

  @Test
  fun previous_years_are_month_and_year() {
    assertEquals("Dec 2025", format(at(2025, 12, 31, 23, 59)))
    assertEquals("Aug 2025", format(at(2025, 8, 12, 12, 0)))
  }

  @Test
  fun absent_timestamps_render_as_nothing() {
    assertEquals("", format(0L))
    assertEquals("", format(-1L))
  }
}
