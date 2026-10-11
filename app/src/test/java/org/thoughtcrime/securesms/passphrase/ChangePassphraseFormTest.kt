/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.passphrase

import android.app.Application
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.thoughtcrime.securesms.ChangePassphraseDialogFragment
import org.thoughtcrime.securesms.R

/**
 * The non-UI parts of the passphrase form: which fields each mode asks for, and the new-passphrase
 * checks. Robolectric only because [ChangePassphraseFormState] holds Compose text field state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ChangePassphraseFormTest {

  @Test
  fun `change asks for the old passphrase and a new one`() {
    val form = ChangePassphraseForm.forMode(ChangePassphraseDialogFragment.MODE_CHANGE)

    assertThat(form.title).isEqualTo(R.string.AndroidManifest__change_passphrase)
    assertThat(form.submitLabel).isEqualTo(android.R.string.ok)
    assertThat(form.currentPassphraseLabel).isEqualTo(R.string.change_passphrase_activity__old_passphrase)
    assertThat(form.explanation).isNull()
    assertThat(form.asksForNewPassphrase).isTrue()
    assertThat(form.hidesFieldsWhileInProgress).isTrue()
  }

  @Test
  fun `enable asks only for a new passphrase`() {
    val form = ChangePassphraseForm.forMode(ChangePassphraseDialogFragment.MODE_ENABLE)

    assertThat(form.title).isEqualTo(R.string.AndroidManifest__create_passphrase)
    assertThat(form.submitLabel).isEqualTo(android.R.string.ok)
    assertThat(form.currentPassphraseLabel).isNull()
    assertThat(form.asksForNewPassphrase).isTrue()
  }

  @Test
  fun `disable asks only for the current passphrase and keeps its field during progress`() {
    val form = ChangePassphraseForm.forMode(ChangePassphraseDialogFragment.MODE_DISABLE)

    assertThat(form.title).isEqualTo(R.string.ApplicationPreferencesActivity_disable_passphrase)
    assertThat(form.submitLabel).isEqualTo(R.string.ApplicationPreferencesActivity_disable)
    assertThat(form.currentPassphraseLabel).isEqualTo(R.string.ChangePassphraseDialogFragment_passphrase)
    assertThat(form.asksForNewPassphrase).isFalse()
    assertThat(form.hidesFieldsWhileInProgress).isFalse()
  }

  @Test
  fun `the no-argument factory's default mode is change`() {
    assertThat(ChangePassphraseForm.forMode(ChangePassphraseDialogFragment.MODE_CHANGE))
      .isEqualTo(ChangePassphraseForm.forMode(-1))
  }

  @Test
  fun `an empty new passphrase is reported before a mismatch`() {
    assertThat(NewPassphraseEntry.check(CharArray(0), "a".toCharArray())).isEqualTo(NewPassphraseEntry.EMPTY)
    assertThat(NewPassphraseEntry.check(CharArray(0), CharArray(0))).isEqualTo(NewPassphraseEntry.EMPTY)
  }

  @Test
  fun `a repeat that differs is a mismatch`() {
    assertThat(NewPassphraseEntry.check("correct horse".toCharArray(), "correct hors".toCharArray()))
      .isEqualTo(NewPassphraseEntry.MISMATCH)
  }

  @Test
  fun `matching passphrases are valid`() {
    assertThat(NewPassphraseEntry.check("correct horse".toCharArray(), "correct horse".toCharArray()))
      .isEqualTo(NewPassphraseEntry.VALID)
  }

  @Test
  fun `copyOf returns the field's characters`() {
    val state = ChangePassphraseFormState()
    state.newPassphrase.setTextAndPlaceCursorAtEnd("s3cret")

    assertThat(ChangePassphraseFormState.copyOf(state.newPassphrase).contentEquals("s3cret".toCharArray())).isTrue()
    assertThat(ChangePassphraseFormState.copyOf(state.currentPassphrase).size).isEqualTo(0)
  }

  @Test
  fun `clearErrors clears every field's error`() {
    val state = ChangePassphraseFormState()
    state.currentPassphraseError = "a"
    state.newPassphraseError = "b"
    state.repeatPassphraseError = "c"

    state.clearErrors()

    assertThat(state.currentPassphraseError).isNull()
    assertThat(state.newPassphraseError).isNull()
    assertThat(state.repeatPassphraseError).isNull()
  }
}
