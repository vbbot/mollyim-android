/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.lock.v2

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.android.controller.ActivityController
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.signal.core.ui.CoreUiDependenciesRule
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.keyvalue.PinValues
import org.thoughtcrime.securesms.keyvalue.RegistrationValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.keyvalue.SvrValues
import org.thoughtcrime.securesms.lock.v2.ConfirmSvrPinViewModel.SaveAnimation
import org.thoughtcrime.securesms.registration.util.RegistrationUtil
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.DefaultValueLiveData
import org.thoughtcrime.securesms.util.TextSecurePreferences
import java.time.Duration

/**
 * The confirm-PIN screen's save, as the user sees it: the full-screen progress state while the PIN
 * is saved, the success state when it lands, and -- after the success state has been up for
 * [ConfirmSvrPinFragment.SUCCESS_PAUSE_MS] -- the side effects SUCCESS always had, in their order,
 * exactly once.
 *
 * The save itself is a network call, so the view model's save state is moved by reflection to stand
 * in for its result; the time is Robolectric's main-looper clock, advanced by hand.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class ConfirmSvrPinFragmentTest {

  @get:Rule
  val coreUiDependenciesRule = CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  private val context = RuntimeEnvironment.getApplication()

  @Before
  fun setUp() {
    mockkObject(SignalStore)
    every { SignalStore.svr } returns mockk<SvrValues>(relaxed = true)
    // The post-save sync reads registration state; the real store is not set up under Robolectric.
    every { SignalStore.registration } returns mockk<RegistrationValues>(relaxed = true)
    every { SignalStore.pin } returns mockk<PinValues>(relaxed = true) {
      every { arePinRemindersEnabled() } returns true
    }

    // The screen holds FLAG_SECURE while it is resumed; whether it adds one is not under test.
    mockkStatic(TextSecurePreferences::class)
    every { TextSecurePreferences.isScreenSecurityEnabled(any()) } returns true

    mockkStatic(RegistrationUtil::class)
    every { RegistrationUtil.maybeMarkRegistrationComplete() } just runs

    // @JvmStatic, so Kotlin callers invoke it statically: an object mock alone would not intercept it.
    mockkObject(StorageSyncHelper)
    mockkStatic(StorageSyncHelper::class)
    every { StorageSyncHelper.scheduleSyncForDataChange() } just runs
  }

  /** Every activity a test launched, so none outlives its test. */
  private val controllers = mutableListOf<ActivityController<FragmentActivity>>()

  @After
  fun tearDown() {
    // Destroyed before the mocks go: a success pause still pending at the end of a test would
    // otherwise run the real registration and sync calls in whichever test runs next.
    // Let any pause still pending finish while the mocks are in place, then close every screen.
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ConfirmSvrPinFragment.SUCCESS_PAUSE_MS * 2))
    controllers.forEach { controller -> runCatching { controller.pause().stop().destroy() } }
    shadowOf(Looper.getMainLooper()).idle()
    controllers.clear()
    unmockkAll()
  }

  @Test
  fun `the editor opens on arrival with the re-entry placeholder behind it`() {
    val screen = launch()

    assertThat(screen.pinView.editorOpen).isTrue()
    assertThat(screen.pinView.fieldPlaceholder).isEqualTo(context.getString(R.string.ConfirmKbsPinFragment__re_enter_your_pin))
  }

  @Test
  fun `the keyboard's Done closes the editor and does not save`() {
    val screen = launch()

    screen.pinView.input.setText(PIN)
    screen.pinView.input.onEditorAction(EditorInfo.IME_ACTION_DONE)
    idle()

    assertThat(screen.pinView.editorOpen).isFalse()
    assertThat(screen.pinView.busy).isFalse()
    assertThat(screen.pinView.successTitle).isNull()
  }

  @Test
  fun `saving replaces the screen with the progress state, and back is held`() {
    val screen = launch()

    screen.setSaveAnimation(SaveAnimation.LOADING)

    assertThat(screen.pinView.busy).isTrue()
    assertThat(screen.pinView.busyHeading).isEqualTo(context.getString(R.string.ConfirmKbsPinFragment__creating_pin))
    assertThat(screen.pinView.inputVisible).isFalse()
    assertThat(screen.pinView.editorOpen).isFalse()

    screen.activity.onBackPressedDispatcher.onBackPressed()
    assertThat(screen.activity.isFinishing).isFalse()
  }

  @Test
  fun `a saved PIN shows the success state, then leaves once, in the original order`() {
    val screen = launch()

    screen.setSaveAnimation(SaveAnimation.LOADING)
    screen.setSaveAnimation(SaveAnimation.SUCCESS)

    assertThat(screen.pinView.busy).isFalse()
    assertThat(screen.pinView.successTitle).isEqualTo(context.getString(R.string.ConfirmKbsPinFragment__pin_created))
    assertThat(screen.pinView.successMessage).isEqualTo(context.getString(R.string.KbsReminderDialog__to_help_you_memorize_your_pin))

    // Nothing has happened yet: the success state is up, and back does not cut it short.
    screen.activity.onBackPressedDispatcher.onBackPressed()
    advance(ConfirmSvrPinFragment.SUCCESS_PAUSE_MS - 100)
    assertThat(screen.activity.isFinishing).isFalse()
    verify(exactly = 0) { RegistrationUtil.maybeMarkRegistrationComplete() }
    verify(exactly = 0) { StorageSyncHelper.scheduleSyncForDataChange() }

    advance(100)

    assertThat(shadowOf(screen.activity).resultCode).isEqualTo(Activity.RESULT_OK)
    assertThat(screen.activity.isFinishing).isTrue()
    verifyOrder {
      RegistrationUtil.maybeMarkRegistrationComplete()
      StorageSyncHelper.scheduleSyncForDataChange()
    }

    advance(ConfirmSvrPinFragment.SUCCESS_PAUSE_MS * 2)
    verify(exactly = 1) { RegistrationUtil.maybeMarkRegistrationComplete() }
    verify(exactly = 1) { StorageSyncHelper.scheduleSyncForDataChange() }
  }

  /**
   * The view is destroyed and rebuilt half-way through the pause, as a configuration change would.
   * The first wait dies with its view; the rebuilt view is handed SUCCESS again and waits again; the
   * side effects run once, after the second wait, and never again.
   */
  @Test
  fun `a view rebuilt during the pause still leaves exactly once`() {
    val screen = launch()

    screen.setSaveAnimation(SaveAnimation.SUCCESS)
    advance(ConfirmSvrPinFragment.SUCCESS_PAUSE_MS / 2)

    screen.activity.supportFragmentManager.beginTransaction().detach(screen.fragment).commitNow()
    screen.activity.supportFragmentManager.beginTransaction().attach(screen.fragment).commitNow()
    idle()

    val rebuilt = screen.fragment.requireView().findViewById<LightSvrPinView>(R.id.light_svr_pin_view)
    assertThat(rebuilt.successTitle).isEqualTo(context.getString(R.string.ConfirmKbsPinFragment__pin_created))

    advance(ConfirmSvrPinFragment.SUCCESS_PAUSE_MS / 2)
    verify(exactly = 0) { RegistrationUtil.maybeMarkRegistrationComplete() }

    advance(ConfirmSvrPinFragment.SUCCESS_PAUSE_MS)
    verify(exactly = 1) { RegistrationUtil.maybeMarkRegistrationComplete() }
    verify(exactly = 1) { StorageSyncHelper.scheduleSyncForDataChange() }
    assertThat(screen.activity.isFinishing).isTrue()

    advance(ConfirmSvrPinFragment.SUCCESS_PAUSE_MS * 2)
    verify(exactly = 1) { RegistrationUtil.maybeMarkRegistrationComplete() }
  }

  @Test
  fun `a failed save keeps its notice and its path`() {
    val screen = launch()

    screen.setSaveAnimation(SaveAnimation.LOADING)
    screen.setSaveAnimation(SaveAnimation.FAILURE)

    assertThat(screen.pinView.busy).isFalse()
    assertThat(screen.pinView.successTitle).isNull()
    assertThat(screen.pinView.failureTitle).isEqualTo(context.getString(R.string.ConfirmKbsPinFragment__pin_creation_failed))
    verify(exactly = 1) { RegistrationUtil.maybeMarkRegistrationComplete() }
    verify(exactly = 0) { StorageSyncHelper.scheduleSyncForDataChange() }
  }

  private class Screen(
    val activity: FragmentActivity,
    val fragment: ConfirmSvrPinFragment,
    val viewModel: ConfirmSvrPinViewModel
  ) {
    val pinView: LightSvrPinView
      get() = fragment.requireView().findViewById(R.id.light_svr_pin_view)

    /** Moves the view model's private save state, as the save's result would. */
    @Suppress("UNCHECKED_CAST")
    fun setSaveAnimation(animation: SaveAnimation) {
      val field = ConfirmSvrPinViewModel::class.java.getDeclaredField("saveAnimation").apply { isAccessible = true }
      (field.get(viewModel) as DefaultValueLiveData<SaveAnimation>).setValue(animation)
      shadowOf(Looper.getMainLooper()).idle()
    }
  }

  private fun launch(): Screen {
    val controller = Robolectric.buildActivity(FragmentActivity::class.java)
    controllers += controller
    // `LightSvrPinView`'s field is a Material `TextInputEditText`, which needs a Material theme.
    controller.get().setTheme(R.style.TextSecure_LightRegistrationTheme)
    val activity = controller.setup().get()

    val fragment = ConfirmSvrPinFragment()
    fragment.arguments = ConfirmSvrPinFragmentArgs.Builder()
      .setUserEntry(SvrPin.from(PIN))
      .setKeyboard(PinKeyboardType.NUMERIC)
      .build()
      .toBundle()
    activity.supportFragmentManager.beginTransaction().add(android.R.id.content, fragment).commitNow()
    idle()

    // The fragment's own instance: it was created with a factory, and this finds it in the store.
    val viewModel = ViewModelProvider(fragment)[ConfirmSvrPinViewModel::class.java]

    return Screen(activity, fragment, viewModel)
  }

  private fun idle() {
    shadowOf(Looper.getMainLooper()).idle()
  }

  private fun advance(millis: Long) {
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))
  }

  private companion object {
    const val PIN = "5824"
  }
}
