/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.registrationlock

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.logging.LoggingFragment
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.LightConfirmAction
import org.thoughtcrime.securesms.lock.v2.LightSvrPinMenuItem
import org.thoughtcrime.securesms.lock.v2.LightSvrPinView
import org.thoughtcrime.securesms.lock.v2.SvrConstants
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import org.thoughtcrime.securesms.registration.ui.light.LightRegistrationContracts
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.SupportEmailUtil
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import java.util.concurrent.TimeUnit
import org.signal.registration.R as RegistrationR

/**
 * "Enter your PIN", for an account with registration lock turned on.
 *
 * Presentation is [LightSvrPinView]'s, the same view the PIN restore, create and confirm screens use:
 * the title in the top bar, the body copy, the credential field and its status line, one confirm
 * action, and notices drawn in place of the Material dialogs. Everything that decides what happens
 * to a PIN -- the length gate, the verification call, every result branch and where each one leads --
 * is unchanged.
 *
 * The secondary actions ("switch keyboard", and "forgot PIN?" once it is offered) are in the
 * overflow panel rather than stacked above the confirm action. The LP3 screen is 413dp tall, and with
 * the system keyboard up every action row in the fixed bottom block is one more row the PIN field
 * has to be pushed out of view by. "Switch keyboard" is also in the PIN editor, under the field,
 * where it is needed while typing.
 *
 * The PIN is typed in the view's full-screen editor, which opens by itself on arrival unless a notice
 * got there first. The keyboard's Done closes the editor; the PIN is submitted from the form's confirm
 * action, and while it is verified the screen is a full-screen progress state. A rejection returns to
 * the form, with the reason on the status line.
 *
 * As on the PIN restore screen, the debug-log multi-tap on the title did not survive the move: it
 * needed a title `TextView` by id, and the title is now drawn in the top bar.
 */
class RegistrationLockFragment : LoggingFragment() {
  companion object {
    private val TAG = Log.tag(RegistrationLockFragment::class.java)
  }

  private val viewModel by activityViewModels<RegistrationViewModel>()

  private var timeRemaining: Long = 0

  private lateinit var pinView: LightSvrPinView

  /** Whether "forgot PIN?" is offered. Upstream this was a button made visible once and never hidden. */
  private var forgotPinOffered = false

  /**
   * Back closes a notice rather than the screen. Every dialog this screen raised upstream was
   * cancelable, so back dismissed it and left the PIN screen as it was; the notice is drawn in place
   * rather than in a window, so this callback is what keeps that true.
   */
  private val dismissNoticeOnBack = object : OnBackPressedCallback(false) {
    override fun handleOnBackPressed() {
      clearNotice()
    }
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return LightSvrPinView(requireContext()).apply { id = R.id.light_svr_pin_view }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    pinView = view as LightSvrPinView

    val args: RegistrationLockFragmentArgs = RegistrationLockFragmentArgs.fromBundle(requireArguments())

    timeRemaining = args.getTimeRemaining()

    pinView.updateHeader(
      title = getString(R.string.RegistrationLockFragment__enter_your_pin),
      description = getString(R.string.RegistrationLockFragment__enter_the_pin_you_created),
      learnMoreVisible = false
    )
    pinView.updateConfirm(label = getString(RegistrationR.string.RegistrationActivity_continue), enabled = true, busy = false)
    pinView.updateLabel("", isError = false)
    pinView.updateFieldPlaceholder(getString(R.string.RegistrationLockFragment__enter_your_pin))
    pinView.updateBusyHeading(getString(R.string.LightSvrPinView__checking_pin))
    // In the editor only; on the form it is the overflow item below.
    pinView.updateKeyboardToggle(getString(R.string.RegistrationLockFragment__switch_keyboard), onForm = false)
    pinView.onKeyboardToggle = Runnable { viewModel.togglePinKeyboardType() }
    pinView.updateSupport(null)
    pinView.updateBackContentDescription(getString(R.string.RegistrationScreen__back_to_previous_screen))
    // Exactly what system back did here: the icon only makes it visible.
    pinView.onBack = Runnable { requireActivity().onBackPressedDispatcher.onBackPressed() }
    updateOverflow()

    requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, dismissNoticeOnBack)

    val input = pinView.input
    // The layout this replaced declared the field `numberPassword`; the view model's keyboard type
    // is applied again by the observer below, but the field is never anything but a password field.
    viewModel.state.value.pinKeyboardType.applyInputTypeTo(input)

    // The IME action is `LightSvrPinView`'s: Done closes the editor and keeps the entry. Submitting
    // is the form's confirm action's, and no longer the keyboard's.
    enablePinEntry()

    pinView.onConfirm = Runnable {
      ViewUtil.hideKeyboard(requireContext(), pinView)
      handlePinEntry()
    }

    viewModel.lockedTimeRemaining.observe(viewLifecycleOwner) { t: Long -> timeRemaining = t }

    val triesRemaining: Int = viewModel.svrTriesRemaining

    if (triesRemaining <= 3) {
      val daysRemaining = getLockoutDays(timeRemaining)

      showNotice(
        title = getString(R.string.RegistrationLockFragment__not_many_tries_left),
        message = getTriesRemainingDialogMessage(triesRemaining, daysRemaining),
        actions = listOf(okAction(), contactSupportAction())
      )
    }

    if (triesRemaining < 5) {
      pinView.updateLabel(requireContext().resources.getQuantityString(R.plurals.RegistrationLockFragment__d_attempts_remaining, triesRemaining, triesRemaining), isError = false)
    }

    viewModel.uiState.observe(viewLifecycleOwner) {
      setBusy(it.inProgress)

      it.sessionStateError?.let { error ->
        handleSessionErrorResponse(error)
        viewModel.sessionStateErrorShown()
      }

      it.registerAccountError?.let { error ->
        handleRegistrationErrorResponse(error)
        viewModel.registerAccountErrorShown()
      }

      // `PinKeyboardType.applyTo` sets an icon on a Material toggle button, and there is none here;
      // the toggle is "switch keyboard", in the overflow and the editor, whose label does not change.
      it.pinKeyboardType.applyInputTypeTo(pinView.input)
    }

    // First arrival only, as upstream focused the field then; a recreation shows the form as it was
    // left. A no-op when the "not many tries left" notice above already holds the screen.
    if (savedInstanceState == null) {
      pinView.openEditor()
    }
  }

  private fun handlePinEntry() {
    pinView.input.setEnabled(false)

    val pin: String = pinView.input.getText().toString()

    val trimmedLength = LightRegistrationContracts.pinLength(pin, LightRegistrationContracts.PinWhitespace.REMOVE_SPACES)
    if (trimmedLength == 0) {
      Toast.makeText(requireContext(), R.string.RegistrationActivity_you_must_enter_your_registration_lock_PIN, Toast.LENGTH_LONG).show()
      enablePinEntry()
      return
    }

    if (trimmedLength < SvrConstants.MINIMUM_PIN_LENGTH) {
      Toast.makeText(requireContext(), getString(R.string.RegistrationActivity_your_pin_has_at_least_d_digits_or_characters, SvrConstants.MINIMUM_PIN_LENGTH), Toast.LENGTH_LONG).show()
      enablePinEntry()
      return
    }

    setBusy(true)

    viewModel.verifyCodeAndRegisterAccountWithRegistrationLock(requireContext(), pin)
  }

  private fun handleSessionErrorResponse(requestResult: VerificationCodeRequestResult) {
    when (requestResult) {
      is VerificationCodeRequestResult.Success -> throw IllegalStateException("Session error handler called on successful response!")
      is VerificationCodeRequestResult.RateLimited -> onRateLimited()

      is VerificationCodeRequestResult.RegistrationLocked -> {
        Log.w(TAG, "Registration locked response to verify account!")
        viewModel.setInProgress(false)
        setBusy(false)
        enablePinEntry()
        Toast.makeText(requireContext(), R.string.RegistrationActivity_error_connecting_to_service, Toast.LENGTH_LONG).show()
      }

      else -> {
        Log.w(TAG, "Unable to verify code with registration lock", requestResult.getCause())
        onError()
      }
    }
  }

  private fun handleRegistrationErrorResponse(result: RegisterAccountResult) {
    when (result) {
      is RegisterAccountResult.Success -> throw IllegalStateException("Register account error handler called on successful response!")
      is RegisterAccountResult.RateLimited -> onRateLimited()
      is RegisterAccountResult.AttemptsExhausted -> {
        findNavController().safeNavigate(RegistrationLockFragmentDirections.actionAccountLocked())
      }

      is RegisterAccountResult.RegistrationLocked -> {
        Log.w(TAG, "Registration locked response to register account!")
        viewModel.setInProgress(false)
        setBusy(false)
        enablePinEntry()
        Toast.makeText(requireContext(), R.string.RegistrationActivity_error_connecting_to_service, Toast.LENGTH_LONG).show()
      }

      is RegisterAccountResult.SvrWrongPin -> onIncorrectKbsRegistrationLockPin(result.triesRemaining)
      is RegisterAccountResult.SvrNoData -> {
        findNavController().safeNavigate(RegistrationLockFragmentDirections.actionAccountLocked())
      }

      else -> {
        Log.w(TAG, "Unable to register account with registration lock", result.getCause())
        onError()
      }
    }
  }

  private fun onIncorrectKbsRegistrationLockPin(svrTriesRemaining: Int) {
    setBusy(false)
    pinView.input.getText()?.clear()
    enablePinEntry()

    if (svrTriesRemaining == 0) {
      Log.w(TAG, "Account locked. User out of attempts on KBS.")
      findNavController().safeNavigate(RegistrationLockFragmentDirections.actionAccountLocked())
      return
    }

    if (svrTriesRemaining == 3) {
      val daysRemaining = getLockoutDays(timeRemaining)

      showNotice(
        title = getString(R.string.RegistrationLockFragment__incorrect_pin),
        message = getTriesRemainingDialogMessage(svrTriesRemaining, daysRemaining),
        actions = listOf(okAction())
      )
    }

    if (svrTriesRemaining > 5) {
      pinView.updateLabel(getString(R.string.RegistrationLockFragment__incorrect_pin_try_again), isError = true)
    } else {
      pinView.updateLabel(requireContext().resources.getQuantityString(R.plurals.RegistrationLockFragment__incorrect_pin_d_attempts_remaining, svrTriesRemaining, svrTriesRemaining), isError = true)
      forgotPinOffered = true
      updateOverflow()
    }
  }

  private fun onRateLimited() {
    setBusy(false)
    enablePinEntry()

    showNotice(
      title = getString(R.string.RegistrationActivity_too_many_attempts),
      message = getString(R.string.RegistrationActivity_you_have_made_too_many_incorrect_registration_lock_pin_attempts_please_try_again_in_a_day),
      actions = listOf(okAction())
    )
  }

  fun onError() {
    setBusy(false)
    enablePinEntry()

    Toast.makeText(requireContext(), R.string.RegistrationActivity_error_connecting_to_service, Toast.LENGTH_LONG).show()
  }

  private fun handleForgottenPin(timeRemainingMs: Long) {
    val lockoutDays = getLockoutDays(timeRemainingMs)
    showNotice(
      title = getString(R.string.RegistrationLockFragment__forgot_your_pin),
      message = requireContext().resources.getQuantityString(R.plurals.RegistrationLockFragment__for_your_privacy_and_security_there_is_no_way_to_recover, lockoutDays, lockoutDays),
      actions = listOf(okAction(), contactSupportAction())
    )
  }

  private fun getLockoutDays(timeRemainingMs: Long): Int {
    return TimeUnit.MILLISECONDS.toDays(timeRemainingMs).toInt() + 1
  }

  private fun getTriesRemainingDialogMessage(triesRemaining: Int, daysRemaining: Int): String {
    val resources = requireContext().resources
    val tries = resources.getQuantityString(R.plurals.RegistrationLockFragment__you_have_d_attempts_remaining, triesRemaining, triesRemaining)
    val days = resources.getQuantityString(R.plurals.RegistrationLockFragment__if_you_run_out_of_attempts_your_account_will_be_locked_for_d_days, daysRemaining, daysRemaining)

    return "$tries $days"
  }

  /**
   * Makes the field usable again after a check. The form is left showing, with the reason on the
   * status line, rather than the editor reopened over it: the reason is what the user needs to read
   * next, and the PIN row is one tap from the keyboard.
   */
  private fun enablePinEntry() {
    pinView.input.setEnabled(true)
  }

  /** The confirm action's in-flight state. It replaces the progress button's spin/cancel pair. */
  private fun setBusy(busy: Boolean) {
    pinView.updateConfirm(label = pinView.confirmLabel, enabled = pinView.confirmEnabled, busy = busy)
  }

  /** "Switch keyboard" always, and "forgot PIN?" once a wrong guess has left five or fewer. */
  private fun updateOverflow() {
    val items = buildList {
      add(LightSvrPinMenuItem(getString(R.string.RegistrationLockFragment__switch_keyboard)) { viewModel.togglePinKeyboardType() })
      if (forgotPinOffered) {
        add(LightSvrPinMenuItem(getString(R.string.RegistrationLockFragment__forgot_pin)) { handleForgottenPin(timeRemaining) })
      }
    }
    pinView.updateOverflowItems(items)
  }

  /**
   * Raises a notice in place of a Material dialog.
   *
   * The view closes the PIN editor first, keyboard and all, keeping what was typed: on a 413dp
   * screen a keyboard left up would cover the notice's actions.
   */
  private fun showNotice(title: String, message: String, actions: List<LightConfirmAction>) {
    pinView.updateNotice(title, message, actions)
    dismissNoticeOnBack.isEnabled = true
  }

  /** Closes the notice onto the form. The editor stays closed; the PIN row reopens it. */
  private fun clearNotice() {
    pinView.updateNotice(null, "", emptyList())
    dismissNoticeOnBack.isEnabled = false
  }

  /** The Material dialogs' `android.R.string.ok` positive button, which only dismissed. */
  private fun okAction(): LightConfirmAction = LightConfirmAction(getString(android.R.string.ok)) { clearNotice() }

  /** The Material dialogs' "contact support" neutral button, which dismissed and opened an email. */
  private fun contactSupportAction(): LightConfirmAction {
    return LightConfirmAction(getString(R.string.PinRestoreEntryFragment_contact_support)) {
      clearNotice()
      sendEmailToSupport()
    }
  }

  private fun sendEmailToSupport() {
    val subject = R.string.RegistrationLockFragment__signal_registration_need_help_with_pin_for_android_v2_pin

    val body = SupportEmailUtil.generateSupportEmailBody(
      requireContext(),
      subject,
      null,
      null
    )
    CommunicationActions.openEmail(
      requireContext(),
      SupportEmailUtil.getSupportEmailAddress(requireContext()),
      getString(subject),
      body
    )
  }
}
