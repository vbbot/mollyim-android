/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.profiles.edit

import android.app.Application
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isSameInstanceAs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.profiles.manage.EditProfileNameFragment

/**
 * The profile fields' limits used to be EditText filters (`GraphemeClusterLimitFilter`, then
 * `trimFieldToMaxByteLength`). These pin the Compose port to the same results. Robolectric for the
 * platform's ICU break iterator, which counts the grapheme clusters.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CreateProfileFieldLimitTest {

  private val nameLimit = CreateProfileFieldLimit(EditProfileNameFragment.NAME_MAX_GLYPHS, ProfileName.MAX_PART_LENGTH)
  private val descriptionLimit = CreateProfileFieldLimit(480, 8192)

  /** One grapheme cluster, two chars: "e" and a combining acute accent. */
  private val accentedE = "é"

  /** One grapheme cluster, seven UTF-8 bytes: "e" and three combining acute accents. */
  private val heavyE = "é́́"

  @Test
  fun `a name is cut at NAME_MAX_GLYPHS characters`() {
    val result = nameLimit.apply("", "a".repeat(30))

    assertThat(result).isEqualTo("a".repeat(EditProfileNameFragment.NAME_MAX_GLYPHS))
  }

  @Test
  fun `the limit counts grapheme clusters, not chars`() {
    val result = nameLimit.apply("", accentedE.repeat(30))

    assertThat(result).isEqualTo(accentedE.repeat(EditProfileNameFragment.NAME_MAX_GLYPHS))
  }

  @Test
  fun `a name is trimmed by whole clusters to the profile byte limit`() {
    val result = nameLimit.apply("", heavyE.repeat(EditProfileNameFragment.NAME_MAX_GLYPHS))
    val fitting = ProfileName.MAX_PART_LENGTH / heavyE.toByteArray(Charsets.UTF_8).size

    assertThat(result).isEqualTo(heavyE.repeat(fitting))
    assertThat(result.toByteArray(Charsets.UTF_8).size).isLessThanOrEqualTo(ProfileName.MAX_PART_LENGTH)
  }

  @Test
  fun `typing into a full field changes nothing, wherever the cursor is`() {
    val full = "abcdefghijklmnopqrstuvwxyz"

    assertThat(nameLimit.apply(full, full + "!")).isEqualTo(full)
    assertThat(nameLimit.apply(full, "abc!defghijklmnopqrstuvwxyz")).isEqualTo(full)
  }

  @Test
  fun `a paste into the middle keeps both ends and only what fits of the paste`() {
    val result = CreateProfileFieldLimit(6).apply("ab", "a1234567b")

    assertThat(result).isEqualTo("a1234b")
  }

  @Test
  fun `deleting is never limited`() {
    val result = nameLimit.apply("abcdef", "abef")

    assertThat(result).isEqualTo("abef")
  }

  @Test
  fun `a group description is cut at 480 clusters`() {
    val result = descriptionLimit.apply("", "a".repeat(500))

    assertThat(result).isEqualTo("a".repeat(480))
  }

  @Test
  fun `a group description is trimmed to 8192 bytes`() {
    // 35 bytes a cluster: 480 of them would be 16800.
    val heavy = "e" + "́".repeat(17)
    val result = descriptionLimit.apply("", heavy.repeat(480))

    assertThat(result).isEqualTo(heavy.repeat(8192 / 35))
  }

  @Test
  fun `a group name has no byte limit`() {
    val result = CreateProfileFieldLimit(32).apply("", heavyE.repeat(40))

    assertThat(result).isEqualTo(heavyE.repeat(32))
  }

  @Test
  fun `an edit within the limits comes back untouched`() {
    val previous = TextFieldValue("Al", selection = TextRange(2))
    val edited = TextFieldValue("Ali", selection = TextRange(3), composition = TextRange(0, 3))

    assertThat(nameLimit.apply(previous, edited)).isSameInstanceAs(edited)
  }

  @Test
  fun `a cut edit puts the cursor after what survived`() {
    val previous = TextFieldValue("ab", selection = TextRange(1))
    val edited = TextFieldValue("a1234567b", selection = TextRange(8))

    val result = CreateProfileFieldLimit(6).apply(previous, edited)

    assertThat(result.text).isEqualTo("a1234b")
    assertThat(result.selection).isEqualTo(TextRange(5))
  }
}
