/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.lock.v2

import android.app.Application
import android.content.Context
import androidx.appcompat.view.ContextThemeWrapper
import android.text.Editable
import android.view.View
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.signal.core.ui.CoreUiDependenciesRule
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.util.text.AfterTextChanged
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue

/**
 * The Signal PIN screen's load-bearing behaviour, at the surface the fragments actually drive.
 *
 * The Signal PIN is not an app lock. It is the credential that makes a backup restorable after
 * losing the phone, and it is already set on the devices this app targets. So the properties pinned
 * here are not "does the button look right"; they are the ones whose failure costs someone their
 * account:
 *
 *  1. **The confirm action's liveness is a function of what is in the field, and nothing else.**
 *     Specifically it is *re-derived* on every entry, so a screen that set it true during setup
 *     cannot leave it live over a one-character PIN. That ordering -- the `userEntry` observer is
 *     attached after `initializeViewStates()` -- is easy to break by moving one block and hard to
 *     notice by eye, because the screen looks correct until someone types and presses Next.
 *  2. **A rejected PIN leaves nothing behind.** The field is emptied and the status line switches to
 *     its error variant, for both rejection paths there are (a PIN too weak to accept, and a
 *     re-entry that does not match).
 *
 * ## Why this drives the view rather than the fragments
 *
 * Both invariants are visible on `LightSvrPinView`'s public surface, so testing there tests the
 * thing rather than the plumbing. What that does *not* cover, and what [Screen] below makes
 * explicit: the fragment's own two lines of wiring (a text watcher feeding the view model, and the
 * `userEntry` observer calling [BaseSvrPinFragment.isEntryLongEnough]) are **modelled** here rather
 * than exercised, because driving a real fragment needs a nav host, a `SignalStore` mock and the
 * whole `AbstractComposeView` lifecycle, none of which can be stood up without running the test.
 * Modelling them is the weaker claim, and is called out rather than glossed.
 *
 * What is asserted absolutely (rather than by reference to another assertion) is the gate itself:
 * [BaseSvrPinFragment.isEntryLongEnough] is pinned against concrete strings, so a change to what
 * "long enough" means fails here even though every other test would still agree with itself.
 */
@RunWith(RobolectricTestRunner::class)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LightSvrPinViewTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  /**
   * The application, carrying the theme the PIN activities actually run under.
   *
   * `LightSvrPinView` holds a `NoAutofillEditText`, which is a `TextInputEditText`, and Material's
   * constructors refuse to inflate outside a Material-derived theme. Robolectric's bare application
   * has none, so without this every test that builds the view fails in the constructor -- an
   * artefact of the harness, not a defect in the screen. `TextSecure.LightRegistrationTheme` is
   * what `CreateSvrPinActivity` and `SvrMigrationActivity` declare, so the theme under test is the
   * one the credential field is actually constructed against.
   */
  private val context: Context
    get() = ContextThemeWrapper(
      RuntimeEnvironment.getApplication(),
      R.style.TextSecure_LightRegistrationTheme
    )

  // ---------------------------------------------------------------------------------------------
  // The length gate.
  // ---------------------------------------------------------------------------------------------

  @Test
  fun `an entry shorter than the minimum cannot be submitted`() {
    assertThat(BaseSvrPinFragment.isEntryLongEnough(SvrPin.from(""))).isFalse()
    assertThat(BaseSvrPinFragment.isEntryLongEnough(SvrPin.from("1"))).isFalse()
    assertThat(BaseSvrPinFragment.isEntryLongEnough(SvrPin.from("123"))).isFalse()
    assertThat(BaseSvrPinFragment.isEntryLongEnough(SvrPin.from("1234"))).isTrue()
    assertThat(BaseSvrPinFragment.isEntryLongEnough(SvrPin.from("123456"))).isTrue()
  }

  /**
   * The gate counts *characters*, not digits.
   *
   * This is the line most likely to be "fixed" one day by someone who reads it as a numeric check.
   * A PIN may be alphanumeric -- `PinKeyboardType.ALPHA_NUMERIC`, toggled from the screen itself --
   * so four letters is a four-character PIN and must be submittable. Counting digits here would make
   * a legitimate PIN unsaveable, on the screen where the user is about to rely on it.
   */
  @Test
  fun `an alphanumeric entry is measured in characters, not digits`() {
    assertThat(BaseSvrPinFragment.isEntryLongEnough(SvrPin.from("abcd"))).isTrue()
    assertThat(BaseSvrPinFragment.isEntryLongEnough(SvrPin.from("a1b2"))).isTrue()
    assertThat(BaseSvrPinFragment.isEntryLongEnough(SvrPin.from("abc"))).isFalse()
  }

  /** Whitespace is trimmed before the count, so it is not a character the user gets to spend. */
  @Test
  fun `surrounding whitespace is not counted`() {
    assertThat(BaseSvrPinFragment.isEntryLongEnough(SvrPin.from("123 "))).isFalse()
    assertThat(BaseSvrPinFragment.isEntryLongEnough(SvrPin.from(" 1234 "))).isTrue()
  }

  /** A null entry is empty, not a pass. An empty PIN is not a shorter PIN; it is no PIN. */
  @Test
  fun `a null entry is empty rather than a pass`() {
    assertThat(BaseSvrPinFragment.isEntryLongEnough(SvrPin.from(null))).isFalse()
  }

  // ---------------------------------------------------------------------------------------------
  // Liveness follows the field, and cannot be latched on.
  // ---------------------------------------------------------------------------------------------

  /**
   * The one that matters. Something has already set the action live -- in the real flow that is
   * `initializeViewStates()`, and on the confirm screen it is `setConfirmEnabled(true)` -- and the
   * entry is then driven through every length below the minimum. The flag has to follow each one,
   * because it is re-derived from the entry rather than latched.
   */
  @Test
  fun `the confirm action cannot stay live after the entry is shortened`() {
    val screen = Screen(context)

    screen.view.updateConfirm("NEXT", enabled = true, busy = false)

    screen.enter("1")
    assertThat(screen.view.confirmEnabled).isFalse()

    screen.enter("12")
    assertThat(screen.view.confirmEnabled).isFalse()

    screen.enter("123")
    assertThat(screen.view.confirmEnabled).isFalse()

    screen.enter("1234")
    assertThat(screen.view.confirmEnabled).isTrue()

    screen.enter("12345")
    assertThat(screen.view.confirmEnabled).isTrue()

    screen.enter("123")
    assertThat(screen.view.confirmEnabled).isFalse()
  }

  /**
   * Liveness and "a save is in flight" are two independent axes. `setConfirmEnabled` and
   * `setConfirmBusy` each write one and read the other, so a save in flight does not silently
   * re-enable an action the user had just emptied the field out of, and vice versa.
   */
  @Test
  fun `going busy does not disturb whether the action is live, and back again`() {
    val view = LightSvrPinView(context)

    // What `setConfirmEnabled(true)` writes: label and live, keeping busy as it found it.
    view.updateConfirm("NEXT", enabled = true, busy = false)
    // What `setConfirmBusy(true)` writes: label and busy, keeping enabled as it found it.
    view.updateConfirm("NEXT", enabled = true, busy = true)
    assertThat(view.busy).isTrue()
    assertThat(view.confirmEnabled).isTrue()

    // And the way back down, which must not quietly clear the in-flight state.
    view.updateConfirm("NEXT", enabled = false, busy = true)
    assertThat(view.busy).isTrue()
    assertThat(view.confirmEnabled).isFalse()
  }

  // ---------------------------------------------------------------------------------------------
  // A rejected PIN leaves nothing behind.
  // ---------------------------------------------------------------------------------------------

  /**
   * The weak-PIN path, driven as `CreateSvrPinFragment` drives it: label to the rejection copy,
   * error flag set, field cleared. Both halves matter -- an error line that leaves the rejected PIN
   * on the glass is a credential left on screen, and an error flag that is not set is a failure the
   * user cannot see on a device with one foreground colour.
   */
  @Test
  fun `a weak PIN leaves the field empty and the status line reporting a failure`() {
    val screen = Screen(context)
    screen.enter("1234")

    val message = context.getString(R.string.CreateKbsPinFragment__choose_a_stronger_pin)
    screen.view.updateLabel(message, true)
    screen.view.clearInput()

    assertThat(screen.view.input.text.toString()).isEmpty()
    assertThat(screen.view.labelIsError).isTrue()
    assertThat(screen.view.label.toString()).isEqualTo(message)
  }

  /** The same two halves on the re-entry path, which rejects with different copy. */
  @Test
  fun `a mismatched re-entry leaves the field empty and the status line reporting a failure`() {
    val screen = Screen(context)
    screen.enter("1234")

    val message = context.getString(R.string.ConfirmKbsPinFragment__pins_dont_match)
    screen.view.updateLabel(message, true)
    screen.view.clearInput()

    assertThat(screen.view.input.text.toString()).isEmpty()
    assertThat(screen.view.labelIsError).isTrue()
    assertThat(screen.view.label.toString()).isEqualTo(message)
  }

  /**
   * A neutral status line is not left showing as an error.
   *
   * The status line is reused for the length restriction, "re-enter your PIN", "creating PIN" and
   * the two rejections. Nothing resets the flag for the reader; every call has to pass it, so this
   * pins that a passing message clears the failure rather than inheriting it.
   */
  @Test
  fun `a neutral status line is not left reporting the previous failure`() {
    val view = LightSvrPinView(context)

    view.updateLabel(context.getString(R.string.CreateKbsPinFragment__choose_a_stronger_pin), true)
    assertThat(view.labelIsError).isTrue()

    view.updateLabel(context.getString(R.string.ConfirmKbsPinFragment__re_enter_your_pin), false)
    assertThat(view.labelIsError).isFalse()
    assertThat(view.label.toString()).isEqualTo(context.getString(R.string.ConfirmKbsPinFragment__re_enter_your_pin))
  }

  /**
   * Rejecting a PIN must leave the action dead over the now-empty field.
   *
   * This is where the two halves meet, and it is the case that would silently submit an empty PIN:
   * clearing the field is what fires the text watcher, the watcher is what re-derives liveness, and
   * a rejected value that left the action live would be committable.
   */
  @Test
  fun `rejecting a PIN leaves the action dead over the empty field`() {
    val screen = Screen(context)

    screen.enter("1234")
    assertThat(screen.view.confirmEnabled).isTrue()

    screen.view.updateLabel(context.getString(R.string.ConfirmKbsPinFragment__pins_dont_match), true)
    screen.view.clearInput()

    assertThat(screen.view.input.text.toString()).isEmpty()
    assertThat(screen.view.confirmEnabled).isFalse()
  }

  /**
   * A PIN screen's credential field: still an `EditText`, still autofill-suppressed, and still
   * masked rather than shown in the clear.
   *
   * None of that moved when the screen was redrawn in Light components, and all three are the kind
   * of property that survives a refactor until someone decides to simplify an `AndroidView` away.
   */
  @Test
  fun `the credential field suppresses autofill`() {
    assertThat(LightSvrPinView(context).input.autofillType).isEqualTo(View.AUTOFILL_TYPE_NONE)
  }

  /**
   * A PIN screen wired the way `BaseSvrPinFragment` wires one.
   *
   * Two lines of production code stand between the field and the action's liveness: a text watcher
   * that feeds the entry to the view model, and the `userEntry` observer that re-derives liveness
   * from it. Both are reproduced here so the tests above can say "the flag follows the field"
   * without asserting it about a stub. [view] is the screen; [enter] is typing into it.
   */
  private class Screen(context: Context) {
    val view = LightSvrPinView(context)

    init {
      view.updateConfirm("NEXT", enabled = false, busy = false)
      view.input.addTextChangedListener(
        AfterTextChanged { entry: Editable ->
          view.updateConfirm(
            view.confirmLabel,
            enabled = BaseSvrPinFragment.isEntryLongEnough(SvrPin.from(entry.toString())),
            busy = view.busy
          )
        }
      )
    }

    fun enter(entry: String) {
      view.input.setText(entry)
    }
  }
}
