/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.reregisterwithpin

import android.app.Application
import android.os.Looper
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.Navigation
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.signal.core.ui.CoreUiDependenciesRule
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.NoAutofillEditText
import org.thoughtcrime.securesms.keyvalue.PinValues
import org.thoughtcrime.securesms.keyvalue.RegistrationValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.lock.v2.LightSvrPinView
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType
import org.thoughtcrime.securesms.pin.SvrWrongPinException
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.thoughtcrime.securesms.registration.ui.RegistrationState
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import org.signal.registration.R as RegistrationR
import java.time.Duration

/**
 * The re-register-with-PIN screen on [LightSvrPinView]: chrome, the credential field's semantics,
 * the overflow that replaced three stacked buttons, and the notices that replaced its Material
 * dialogs -- including the one that, like its dialog, cannot be dismissed with back.
 *
 * As in `RegistrationLockFragmentTest`, the shared view model's private state is moved by reflection
 * to stand in for the network results; no test here makes a network call.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class ReRegisterWithPinFragmentTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  private val context = RuntimeEnvironment.getApplication()

  @Before
  fun setUp() {
    mockkObject(SignalStore)
    every { SignalStore.pin } returns mockk<PinValues>(relaxed = true) {
      every { keyboardType } returns PinKeyboardType.NUMERIC
    }
    every { SignalStore.registration } returns mockk<RegistrationValues>(relaxed = true) {
      every { sessionE164 } returns null
    }
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  @Test
  fun `title is in the top bar, the body copy leads, and back is an icon`() {
    val pinView = launch().pinView

    assertThat(pinView.title).isEqualTo(context.getString(R.string.RegistrationLockFragment__enter_your_pin))
    assertThat(pinView.description).isEqualTo(context.getString(R.string.RegistrationLockFragment__enter_the_pin_you_created_for_your_account))
    assertThat(pinView.onBack).isNotNull()
    assertThat(pinView.backContentDescription).isEqualTo(context.getString(R.string.RegistrationScreen__back_to_previous_screen))
  }

  @Test
  fun `the field is a masked password field that cannot be autofilled and has a Done action`() {
    val input = launch().pinView.input

    assertThat(input).isInstanceOf(NoAutofillEditText::class)
    assertThat(input.autofillType).isEqualTo(View.AUTOFILL_TYPE_NONE)
    assertThat(input.inputType and InputType.TYPE_MASK_CLASS).isEqualTo(InputType.TYPE_CLASS_NUMBER)
    assertThat(input.inputType and InputType.TYPE_MASK_VARIATION).isEqualTo(InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    assertThat(input.transformationMethod).isInstanceOf(PasswordTransformationMethod::class)
    assertThat(input.imeOptions and EditorInfo.IME_MASK_ACTION).isEqualTo(EditorInfo.IME_ACTION_DONE)
  }

  @Test
  fun `one confirm action, and switch keyboard and skip are in the overflow`() {
    val pinView = launch().pinView

    assertThat(pinView.confirmLabel).isEqualTo(context.getString(RegistrationR.string.RegistrationActivity_continue))
    // "Switch keyboard" is in the editor too, but not on the form, where it is the overflow item.
    assertThat(pinView.keyboardToggleLabel).isEqualTo(context.getString(R.string.RegistrationLockFragment__switch_keyboard))
    assertThat(pinView.keyboardToggleOnForm).isFalse()
    assertThat(pinView.supportLabel).isNull()
    assertThat(pinView.overflowItems.map { it.label }).containsExactly(
      context.getString(R.string.RegistrationLockFragment__switch_keyboard),
      context.getString(R.string.PinRestoreEntryFragment_skip)
    )
  }

  @Test
  fun `switch keyboard flips the field to alphanumeric and keeps it a password`() {
    val screen = launch()

    screen.overflow(R.string.RegistrationLockFragment__switch_keyboard).action.run()
    idle()

    val input = screen.pinView.input
    assertThat(input.inputType and InputType.TYPE_MASK_CLASS).isEqualTo(InputType.TYPE_CLASS_TEXT)
    assertThat(input.inputType and InputType.TYPE_MASK_VARIATION).isEqualTo(InputType.TYPE_TEXT_VARIATION_PASSWORD)
    assertThat(input.transformationMethod).isInstanceOf(PasswordTransformationMethod::class)
  }

  @Test
  fun `an in-flight verification makes the confirm action and the field inert`() {
    val screen = launch()

    screen.viewModel.setInProgress(true)
    idle()
    assertThat(screen.pinView.busy).isTrue()
    assertThat(screen.pinView.input.isEnabled).isFalse()
    // The full-screen progress state, and nothing to type into under it.
    assertThat(screen.pinView.busyHeading).isEqualTo(context.getString(R.string.LightSvrPinView__checking_pin))
    assertThat(screen.pinView.editorOpen).isFalse()

    screen.viewModel.setInProgress(false)
    idle()
    assertThat(screen.pinView.busy).isFalse()
    assertThat(screen.pinView.input.isEnabled).isTrue()
  }

  @Test
  fun `the editor opens on arrival, and the keyboard's Done closes it without verifying`() {
    val screen = launch()
    assertThat(screen.pinView.editorOpen).isTrue()
    // Focus itself is asserted in LightSvrPinViewTest, under the Compose test rule; from a fragment
    // host Robolectric does not settle an AndroidView-hosted field's focus deterministically.

    screen.pinView.input.setText("1234")
    screen.pinView.input.onEditorAction(EditorInfo.IME_ACTION_DONE)
    idle()

    assertThat(screen.pinView.editorOpen).isFalse()
    assertThat(screen.pinView.input.text.toString()).isEqualTo("1234")
    assertThat(screen.pinView.busy).isFalse()
  }

  @Test
  fun `skip asks first, and skipping returns to phone number entry`() {
    val screen = launch()

    screen.overflow(R.string.PinRestoreEntryFragment_skip).action.run()

    assertThat(screen.pinView.failureTitle).isEqualTo(context.getString(R.string.PinRestoreEntryFragment_skip_pin_entry))
    assertThat(screen.pinView.failureActions.map { it.label }).containsExactly(
      context.getString(R.string.PinRestoreEntryFragment_cancel),
      context.getString(R.string.PinRestoreEntryFragment_skip)
    )

    screen.pinView.failureActions.last().onSelected.run()
    idle()

    assertThat(screen.pinView.failureTitle).isNull()
    assertThat(screen.viewModel.state.value.userSkippedReregistration).isTrue()
    verify {
      screen.navController.navigate(match<NavDirections> { it.actionId == R.id.action_reRegisterWithPinFragment_to_enterPhoneNumberFragment })
    }
  }

  @Test
  fun `cancel and back both close a cancelable notice and stay on the screen`() {
    val screen = launch()
    // The overflow is in the form's top bar, which the editor covers: close the editor the screen
    // opens on arrival first, as a user has to before they can reach the overflow at all.
    screen.pinView.commitEditor()
    idle()

    screen.overflow(R.string.PinRestoreEntryFragment_skip).action.run()
    screen.pinView.failureActions.first().onSelected.run()
    assertThat(screen.pinView.failureTitle).isNull()

    screen.overflow(R.string.PinRestoreEntryFragment_skip).action.run()
    // A frame for the notice to take the screen, as there always is between a notice and a press.
    idle()
    screen.activity.onBackPressedDispatcher.onBackPressed()
    assertThat(screen.pinView.failureTitle).isNull()
    assertThat(screen.viewModel.state.value.userSkippedReregistration).isFalse()
  }

  @Test
  fun `a wrong guess with one try left warns and offers need help`() {
    val screen = launch()

    screen.viewModel.updateState {
      it.copy(svrTriesRemaining = 1, registerAccountError = RegisterAccountResult.SvrWrongPin(SvrWrongPinException(1)))
    }
    idle()

    assertThat(screen.pinView.failureTitle).isEqualTo(context.getString(R.string.PinRestoreEntryFragment_incorrect_pin))
    assertThat(screen.pinView.failureMessage).isEqualTo(
      context.resources.getQuantityString(R.plurals.PinRestoreEntryFragment_you_have_d_attempt_remaining, 1, 1)
    )
    assertThat(screen.pinView.labelIsError).isTrue()
    assertThat(screen.pinView.overflowItems.map { it.label }).containsExactly(
      context.getString(R.string.RegistrationLockFragment__switch_keyboard),
      context.getString(R.string.PinRestoreEntryFragment_need_help),
      context.getString(R.string.PinRestoreEntryFragment_skip)
    )

    screen.pinView.failureActions.single().onSelected.run()
    screen.overflow(R.string.PinRestoreEntryFragment_need_help).action.run()

    assertThat(screen.pinView.failureTitle).isEqualTo(context.getString(R.string.PinRestoreEntryFragment_need_help))
    assertThat(screen.pinView.failureActions.map { it.label }).containsExactly(
      context.getString(R.string.PinRestoreEntryFragment_cancel),
      context.getString(R.string.PinRestoreEntryFragment_contact_support),
      context.getString(R.string.PinRestoreEntryFragment_skip)
    )
  }

  @Test
  fun `a locked account raises a notice back cannot dismiss`() {
    val screen = launch()

    screen.viewModel.updateState { it.copy(isRegistrationLockEnabled = true, svrTriesRemaining = 0) }
    idle()

    assertThat(screen.pinView.failureTitle).isEqualTo(context.getString(R.string.PinRestoreEntryFragment_incorrect_pin))
    assertThat(screen.pinView.failureMessage).isEqualTo(context.getString(R.string.PinRestoreLockedFragment_youve_run_out_of_pin_guesses))
    assertThat(screen.pinView.failureActions.map { it.label }).containsExactly(
      context.getString(R.string.AccountLockedFragment__learn_more),
      context.getString(R.string.ReRegisterWithPinFragment_send_sms_code)
    )

    screen.activity.onBackPressedDispatcher.onBackPressed()

    assertThat(screen.pinView.failureTitle).isNotNull()
    assertThat(screen.activity.isFinishing).isFalse()
  }

  @Test
  fun `exhausted attempts raise the too many attempts notice`() {
    val screen = launch()

    screen.viewModel.updateState { it.copy(registerAccountError = RegisterAccountResult.AttemptsExhausted(Exception())) }
    idle()

    assertThat(screen.pinView.failureTitle).isEqualTo(context.getString(R.string.RegistrationActivity_too_many_attempts))
  }

  @Test
  fun `a network error raises an untitled notice with OK`() {
    val screen = launch()

    screen.viewModel.updateState { it.copy(networkError = Exception()) }
    idle()

    assertThat(screen.pinView.failureTitle).isEqualTo("")
    assertThat(screen.pinView.failureMessage).isEqualTo(context.getString(R.string.RegistrationActivity_error_connecting_to_service))
    assertThat(screen.pinView.failureActions.map { it.label }).containsExactly(context.getString(android.R.string.ok))
  }

  private class Screen(
    val activity: FragmentActivity,
    val pinView: LightSvrPinView,
    val viewModel: RegistrationViewModel,
    val navController: NavController
  ) {
    fun overflow(label: Int) = pinView.overflowItems.single { it.label == activity.getString(label) }
  }

  private fun launch(): Screen {
    val controller = Robolectric.buildActivity(FragmentActivity::class.java)
    // `LightSvrPinView`'s field is a Material `TextInputEditText`, which needs a Material theme.
    controller.get().setTheme(R.style.TextSecure_LightRegistrationTheme)
    val activity = controller.setup().get()

    // The screen leaves for phone number entry as soon as it sees an account that cannot skip SMS.
    val viewModel = ViewModelProvider(activity)[RegistrationViewModel::class.java]
    viewModel.updateState { it.copy(canSkipSms = true) }

    val fragment = ReRegisterWithPinFragment()
    activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()

    // findNavController() falls back to the controller set on the fragment's view.
    val navController = mockk<NavController>(relaxed = true)
    Navigation.setViewNavController(fragment.requireView(), navController)
    idle()

    return Screen(activity, fragment.requireView() as LightSvrPinView, viewModel, navController)
  }

  /**
   * Runs what is queued and then lets a few frames pass. Compose recomposes on the frame clock, which
   * a bare idle() never advances, so without the frames whether the screen has caught up -- the
   * editor taking focus, a notice taking back -- would depend on timing.
   */
  private fun idle() {
    shadowOf(Looper.getMainLooper()).idle()
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FRAME_ALLOWANCE_MS))
  }

  /** Moves the shared view model's private state, as the network results it stands in for would. */
  @Suppress("UNCHECKED_CAST")
  private fun RegistrationViewModel.updateState(transform: (RegistrationState) -> RegistrationState) {
    val store = RegistrationViewModel::class.java.getDeclaredField("store").apply { isAccessible = true }
    (store.get(this) as MutableStateFlow<RegistrationState>).update(transform)
  }
}

private const val FRAME_ALLOWANCE_MS = 100L
