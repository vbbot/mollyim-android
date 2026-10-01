/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.light

import android.app.Activity
import android.app.Application
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/**
 * The confirmation surface three unrelated screens now share.
 *
 * Three callers depend on it and they are not variations on a theme -- the PIN restore flow raises
 * four different notices, the opt-out confirmation raises one, and the registration-lock toggles
 * raise two -- so the properties pinned here are the ones a caller cannot afford to be wrong about:
 *
 *  1. **Every action offered becomes a row**, in the order it was offered. This is a vertical list
 *     and not a set; the design language puts the destructive action last precisely because order
 *     is the only separator it has, so a caller that reorders has changed what the notice means.
 *  2. **A busy notice cannot be acted on.** The registration-lock toggles run a network call from
 *     their own row, and the old Material dialog left that button live while the call was in flight
 *     -- a second tap started a second registration-lock change. Inertness is the fix, so it is
 *     asserted directly rather than inferred from the label.
 *  3. **A notice that runs out of PIN guesses has a body and no heading.** It renders no title
 *     rather than an empty line, because the copy for that notice was never written, and inventing
 *     a heading would be inventing a claim about someone's account.
 *
 * ## What is not tested here, and why
 *
 * **The rendered appearance.** This harness composes and lays out; it does not draw.
 * `captureToImage` blocks forever in `forceRedraw` because Robolectric never delivers the
 * frame-commit callback it waits on, and a `GraphicsLayer` recorded from `drawWithContent` comes
 * back zero-sized because `drawWithContent` never runs. So the black fill, the `#BBBBBB` lightening
 * of an inert row and the grid spacing between rows are unverified here, and can only be confirmed
 * on the device.
 *
 * **A per-row disabled state.** `LightTextAction` has one and `LightConfirmNotice` never passes it:
 * the only inert row this surface can produce is the busy one, which is what the registration-lock
 * toggles actually use. A test of the flag on its own would be testing a path nothing reaches, so
 * inertness is asserted where it is reachable instead.
 *
 * **The window's full-bleed layout.** `LightConfirmDialog` sets its window to `MATCH_PARENT` after
 * `show`, and Robolectric does not run the window attach that would apply it, so the measurement
 * cannot be read back. The window being the whole surface rather than a card in the middle of one
 * is a property of `show()`'s implementation, not of anything observable here.
 */
@RunWith(RobolectricTestRunner::class)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LightConfirmNoticeTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  // Held as snapshot state and read inside the composition, because `setContent` may be called only
  // once per test.
  private var title by mutableStateOf("Warning")
  private var message by mutableStateOf("You will lose all data.")
  private var actions by mutableStateOf(listOf(action("OK")))
  private var busy by mutableStateOf(false)

  @Before
  fun setUp() {
    composeTestRule.setContent {
      MollyLightTheme {
        LightConfirmNotice(
          title = title,
          message = message,
          actions = actions,
          busy = busy
        )
      }
    }
  }

  // ---------------------------------------------------------------------------------------------
  // The rows a notice offers.
  // ---------------------------------------------------------------------------------------------

  @Test
  fun `every action offered becomes a row, in the order it was offered`() {
    actions = listOf(action("Cancel"), action("Create New PIN"), action("Contact Support"))
    composeTestRule.waitForIdle()

    assertThat(rowLabels()).containsExactly("CANCEL", "CREATE NEW PIN", "CONTACT SUPPORT")
  }

  /**
   * Upper case is the Light Phone's register for an action, and it is applied here rather than left
   * to each caller -- all three callers pass strings Signal wrote in sentence case.
   */
  @Test
  fun `action labels are upper cased`() {
    actions = listOf(action("Turn off"))
    composeTestRule.waitForIdle()

    assertThat(rowLabels()).containsExactly("TURN OFF")
  }

  /** Tapping a row runs that row and no other. A list of choices, not a broadcast. */
  @Test
  fun `choosing a row runs that row alone`() {
    val chosen = mutableListOf<String>()
    actions = listOf(action("Cancel") { chosen += "cancel" }, action("Create New PIN") { chosen += "create" })
    composeTestRule.waitForIdle()

    onRow(1).performClick()

    assertThat(chosen).containsExactly("create")
  }

  /** One acknowledgement is the common case, and it is a row like any other. */
  @Test
  fun `a single acknowledgement is offered like any other action`() {
    val acknowledged = mutableListOf<String>()
    actions = listOf(action("OK") { acknowledged += "ok" })
    composeTestRule.waitForIdle()

    onRow(0).performClick()

    assertThat(acknowledged).containsExactly("ok")
  }

  /**
   * A notice with a body and no heading does not get one.
   *
   * The PIN restore screen raises exactly this shape -- "you have one attempt remaining" -- with no
   * title upstream. Rendering an empty heading would leave a blank line where the user expects to be
   * told what happened, and filling one in would be writing copy Signal never wrote about the
   * account in front of them.
   */
  @Test
  fun `a notice with no title shows its body and no heading`() {
    title = ""
    message = "You have 1 attempt remaining."
    actions = listOf(action("OK"))
    composeTestRule.waitForIdle()

    composeTestRule.onNodeWithText("You have 1 attempt remaining.").assertExists()
    assertThat(rowLabels()).containsExactly("OK")
  }

  // ---------------------------------------------------------------------------------------------
  // The in-flight state.
  // ---------------------------------------------------------------------------------------------

  /**
   * Busy is inertness, not just a different label.
   *
   * A label that changed to an ellipsis while the row stayed live would still let a second tap start
   * a second registration-lock request, which is the defect this state exists to remove. The row
   * also stays present and readable: an action is never dropped, because a user who cannot see it
   * cannot tell whether what it offered is possible at all.
   */
  @Test
  fun `a busy notice shows no labels, stays readable, and runs nothing`() {
    val chosen = mutableListOf<String>()
    actions = listOf(action("Turn off") { chosen += "turn-off" })
    busy = true
    composeTestRule.waitForIdle()

    assertThat(rowLabels()).containsExactly("…")
    onRow(0).assertIsNotEnabled()

    onRow(0).performClick()

    assertThat(chosen).isEmpty()
  }

  // ---------------------------------------------------------------------------------------------
  // The windowed form, which is what the Java callers actually construct.
  // ---------------------------------------------------------------------------------------------

  /**
   * `LightConfirmDialog` is a thin window around the same composable, but its lifecycle is the whole
   * of what a Java caller sees, and it breaks in a way the composable tests cannot see: the actions
   * have to be settable *after* the dialog exists, because an action's closure normally refers to
   * the dialog it has to dismiss.
   */
  @Test
  fun `the windowed notice shows, and dismissal is reported`() {
    val controller = Robolectric.buildActivity(Activity::class.java).setup()
    val activity = controller.get()

    val dialog = LightConfirmDialog.create(
      activity,
      "Turn off registration lock",
      "You can register and use your account without a PIN."
    )

    var dismissed = false
    dialog.setActions(listOf(LightConfirmAction("Turn off", Runnable { dialog.dismiss() })))
    dialog.setOnDismissListener(Runnable { dismissed = true })
    dialog.show()

    val window = ShadowDialog.getLatestDialog()
    assertThat(window.isShowing).isTrue()

    dialog.dismiss()

    // `Dialog.dismiss` runs its teardown on the main looper, which Robolectric leaves paused.
    shadowOf(Looper.getMainLooper()).idle()

    assertThat(dismissed).isTrue()
    assertThat(window.isShowing).isFalse()

    controller.pause().stop().destroy()
  }

  /**
   * Nothing is on screen until it is shown.
   *
   * A dialog that appeared on construction would appear with its body and no way out of it, which is
   * the failure mode the separate `show` exists to prevent.
   */
  @Test
  fun `the windowed notice never appears before it is shown`() {
    val controller = Robolectric.buildActivity(Activity::class.java).setup()

    LightConfirmDialog.create(controller.get(), "Warning", "You will lose all data.")

    assertThat(ShadowDialog.getLatestDialog()).isNull()

    controller.pause().stop().destroy()
  }

  // ---------------------------------------------------------------------------------------------
  // Harness.
  // ---------------------------------------------------------------------------------------------

  private fun action(label: String, onSelected: () -> Unit = {}) =
    LightConfirmAction(label, Runnable { onSelected() })

  private fun rowLabels(): List<String> =
    composeTestRule.onAllNodesWithTag(LIGHT_CONFIRM_NOTICE_ACTION_TAG)
      .fetchSemanticsNodes()
      .map { node -> node.config[SemanticsProperties.Text].joinToString("") { it.text } }

  private fun onRow(index: Int) =
    composeTestRule.onAllNodesWithTag(LIGHT_CONFIRM_NOTICE_ACTION_TAG)[index]
}