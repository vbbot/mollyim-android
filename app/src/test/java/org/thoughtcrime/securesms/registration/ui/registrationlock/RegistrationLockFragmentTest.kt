/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.registrationlock

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
import org.robolectric.shadows.ShadowToast
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

/**
 * The registration-lock PIN screen on [LightSvrPinView]: the chrome it is given, the credential
 * field's semantics, where the secondary actions went, and the result branches that raise notices
 * or navigate.
 *
 * The real fragment runs against the real shared [RegistrationViewModel]. Its state is private and
 * only the network calls move it, so [updateState] reaches the backing flow by reflection to put the
 * screen into the states those calls would. Nothing here makes a network call: every path driven
 * either stops at the length gate or starts from a result already in state.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class RegistrationLockFragmentTest {

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
    val screen = launch()

    assertThat(screen.pinView.title).isEqualTo(context.getString(R.string.RegistrationLockFragment__enter_your_pin))
    assertThat(screen.pinView.description).isEqualTo(context.getString(R.string.RegistrationLockFragment__enter_the_pin_you_created))
    assertThat(screen.pinView.onBack).isNotNull()
    assertThat(screen.pinView.backContentDescription).isEqualTo(context.getString(R.string.RegistrationScreen__back_to_previous_screen))
  }

  @Test
  fun `the field is a masked password field that cannot be autofilled and submits on Done`() {
    val input = launch().pinView.input

    assertThat(input).isInstanceOf(NoAutofillEditText::class)
    assertThat(input.autofillType).isEqualTo(View.AUTOFILL_TYPE_NONE)
    assertThat(input.inputType and InputType.TYPE_MASK_CLASS).isEqualTo(InputType.TYPE_CLASS_NUMBER)
    assertThat(input.inputType and InputType.TYPE_MASK_VARIATION).isEqualTo(InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    assertThat(input.transformationMethod).isInstanceOf(PasswordTransformationMethod::class)
    assertThat(input.imeOptions and EditorInfo.IME_MASK_ACTION).isEqualTo(EditorInfo.IME_ACTION_DONE)
  }

  @Test
  fun `one confirm action, and the secondary actions are in the overflow`() {
    val pinView = launch().pinView

    assertThat(pinView.confirmLabel).isEqualTo(context.getString(RegistrationR.string.RegistrationActivity_continue))
    assertThat(pinView.confirmEnabled).isTrue()
    assertThat(pinView.keyboardToggleLabel).isNull()
    assertThat(pinView.supportLabel).isNull()
    // "Forgot PIN?" is not offered until a wrong guess leaves five or fewer.
    assertThat(pinView.overflowItems.map { it.label })
      .containsExactly(context.getString(R.string.RegistrationLockFragment__switch_keyboard))
  }

  @Test
  fun `switch keyboard flips the field to alphanumeric and keeps it a password`() {
    val screen = launch()

    screen.overflow(R.string.RegistrationLockFragment__switch_keyboard).action.run()
    idle()

    assertThat(screen.viewModel.state.value.pinKeyboardType).isEqualTo(PinKeyboardType.ALPHA_NUMERIC)
    val input = screen.pinView.input
    assertThat(input.inputType and InputType.TYPE_MASK_CLASS).isEqualTo(InputType.TYPE_CLASS_TEXT)
    assertThat(input.inputType and InputType.TYPE_MASK_VARIATION).isEqualTo(InputType.TYPE_TEXT_VARIATION_PASSWORD)
    assertThat(input.transformationMethod).isInstanceOf(PasswordTransformationMethod::class)
  }

  @Test
  fun `an in-flight verification makes the confirm action inert until it settles`() {
    val screen = launch()

    screen.viewModel.setInProgress(true)
    idle()
    assertThat(screen.pinView.busy).isTrue()

    screen.viewModel.setInProgress(false)
    idle()
    assertThat(screen.pinView.busy).isFalse()
  }

  @Test
  fun `an empty PIN is refused at the gate and the field is usable again`() {
    val screen = launch()

    screen.pinView.onConfirm!!.run()
    idle()

    assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo(context.getString(R.string.RegistrationActivity_you_must_enter_your_registration_lock_PIN))
    assertThat(screen.pinView.busy).isFalse()
    assertThat(screen.pinView.input.isEnabled).isTrue()
  }

  @Test
  fun `few tries left raises a notice offering OK and contact support`() {
    val screen = launch(before = { it.updateState { state -> state.copy(svrTriesRemaining = 3) } })

    assertThat(screen.pinView.failureTitle).isEqualTo(context.getString(R.string.RegistrationLockFragment__not_many_tries_left))
    assertThat(screen.pinView.failureActions.map { it.label }).containsExactly(
      context.getString(android.R.string.ok),
      context.getString(R.string.PinRestoreEntryFragment_contact_support)
    )
    assertThat(screen.pinView.label.toString()).isEqualTo(
      context.resources.getQuantityString(R.plurals.RegistrationLockFragment__d_attempts_remaining, 3, 3)
    )

    screen.pinView.failureActions.first().onSelected.run()
    assertThat(screen.pinView.failureTitle).isNull()
  }

  @Test
  fun `back closes a notice instead of leaving the screen`() {
    val screen = launch(before = { it.updateState { state -> state.copy(svrTriesRemaining = 2) } })
    assertThat(screen.pinView.failureTitle).isNotNull()

    screen.activity.onBackPressedDispatcher.onBackPressed()

    assertThat(screen.pinView.failureTitle).isNull()
    assertThat(screen.activity.isFinishing).isFalse()
  }

  @Test
  fun `a wrong PIN with few tries left reports them and offers forgot PIN`() {
    val screen = launch()

    screen.viewModel.updateState { it.copy(registerAccountError = RegisterAccountResult.SvrWrongPin(SvrWrongPinException(4))) }
    idle()

    assertThat(screen.pinView.labelIsError).isTrue()
    assertThat(screen.pinView.label.toString()).isEqualTo(
      context.resources.getQuantityString(R.plurals.RegistrationLockFragment__incorrect_pin_d_attempts_remaining, 4, 4)
    )
    assertThat(screen.pinView.overflowItems.map { it.label }).containsExactly(
      context.getString(R.string.RegistrationLockFragment__switch_keyboard),
      context.getString(R.string.RegistrationLockFragment__forgot_pin)
    )

    screen.overflow(R.string.RegistrationLockFragment__forgot_pin).action.run()

    assertThat(screen.pinView.failureTitle).isEqualTo(context.getString(R.string.RegistrationLockFragment__forgot_your_pin))
    assertThat(screen.pinView.failureActions.map { it.label }).containsExactly(
      context.getString(android.R.string.ok),
      context.getString(R.string.PinRestoreEntryFragment_contact_support)
    )
  }

  @Test
  fun `a wrong PIN with many tries left says try again and offers nothing more`() {
    val screen = launch()

    screen.viewModel.updateState { it.copy(registerAccountError = RegisterAccountResult.SvrWrongPin(SvrWrongPinException(8))) }
    idle()

    assertThat(screen.pinView.label.toString()).isEqualTo(context.getString(R.string.RegistrationLockFragment__incorrect_pin_try_again))
    assertThat(screen.pinView.overflowItems.map { it.label })
      .containsExactly(context.getString(R.string.RegistrationLockFragment__switch_keyboard))
  }

  @Test
  fun `the third-from-last wrong PIN raises the incorrect PIN notice`() {
    val screen = launch()

    screen.viewModel.updateState { it.copy(registerAccountError = RegisterAccountResult.SvrWrongPin(SvrWrongPinException(3))) }
    idle()

    assertThat(screen.pinView.failureTitle).isEqualTo(context.getString(R.string.RegistrationLockFragment__incorrect_pin))
    assertThat(screen.pinView.failureActions.map { it.label }).containsExactly(context.getString(android.R.string.ok))
  }

  @Test
  fun `rate limiting raises the too many attempts notice`() {
    val screen = launch()

    screen.viewModel.updateState { it.copy(registerAccountError = RegisterAccountResult.RateLimited(Exception(), 0L)) }
    idle()

    assertThat(screen.pinView.failureTitle).isEqualTo(context.getString(R.string.RegistrationActivity_too_many_attempts))
    assertThat(screen.pinView.busy).isFalse()
  }

  @Test
  fun `running out of tries goes to account locked`() {
    val screen = launch()

    screen.viewModel.updateState { it.copy(registerAccountError = RegisterAccountResult.SvrWrongPin(SvrWrongPinException(0))) }
    idle()

    verify { screen.navController.navigate(match<NavDirections> { it.actionId == R.id.action_accountLocked }) }
  }

  private class Screen(
    val activity: FragmentActivity,
    val pinView: LightSvrPinView,
    val viewModel: RegistrationViewModel,
    val navController: NavController
  ) {
    fun overflow(label: Int) = pinView.overflowItems.single { it.label == activity.getString(label) }
  }

  private fun launch(timeRemaining: Long = 0L, before: (RegistrationViewModel) -> Unit = {}): Screen {
    val controller = Robolectric.buildActivity(FragmentActivity::class.java)
    // `LightSvrPinView`'s field is a Material `TextInputEditText`, which needs a Material theme.
    controller.get().setTheme(R.style.TextSecure_LightRegistrationTheme)
    val activity = controller.setup().get()

    val viewModel = ViewModelProvider(activity)[RegistrationViewModel::class.java]
    before(viewModel)

    val fragment = RegistrationLockFragment()
    fragment.arguments = RegistrationLockFragmentArgs.Builder(timeRemaining).build().toBundle()
    activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()

    // findNavController() falls back to the controller set on the fragment's view.
    val navController = mockk<NavController>(relaxed = true)
    Navigation.setViewNavController(fragment.requireView(), navController)
    idle()

    return Screen(activity, fragment.requireView() as LightSvrPinView, viewModel, navController)
  }

  private fun idle() {
    shadowOf(Looper.getMainLooper()).idle()
  }

  /** Moves the shared view model's private state, as the network results it stands in for would. */
  @Suppress("UNCHECKED_CAST")
  private fun RegistrationViewModel.updateState(transform: (RegistrationState) -> RegistrationState) {
    val store = RegistrationViewModel::class.java.getDeclaredField("store").apply { isAccessible = true }
    (store.get(this) as MutableStateFlow<RegistrationState>).update(transform)
  }
}
