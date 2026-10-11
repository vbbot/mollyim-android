/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.reregisterwithpin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.logging.LoggingFragment
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.LightConfirmAction
import org.thoughtcrime.securesms.lock.v2.LightSvrPinMenuItem
import org.thoughtcrime.securesms.lock.v2.LightSvrPinView
import org.thoughtcrime.securesms.lock.v2.PinKeyboardType
import org.thoughtcrime.securesms.lock.v2.SvrConstants
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.thoughtcrime.securesms.registration.ui.RegistrationCheckpoint
import org.thoughtcrime.securesms.registration.ui.RegistrationState
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import org.thoughtcrime.securesms.registration.ui.light.LightRegistrationContracts
import org.thoughtcrime.securesms.registration.ui.phonenumber.EnterPhoneNumberMode
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.SupportEmailUtil
import org.thoughtcrime.securesms.util.ViewUtil
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.signal.registration.R as RegistrationR

/**
 * "Enter your PIN" when re-registering a number whose account can skip SMS verification.
 *
 * Presentation is [LightSvrPinView]'s, the same view the PIN restore, create and confirm screens use:
 * the title in the top bar, the body copy, the credential field and its status line, one confirm
 * action, and notices drawn in place of the Material dialogs. The verification call, the
 * tries-remaining handling and every registration result branch are unchanged.
 *
 * The secondary actions -- "switch keyboard", "need help?" once it is offered, and "skip" -- are in
 * the overflow panel. Upstream they were three full-width buttons, which on the LP3's 413dp screen
 * with the system keyboard up left no room for the PIN field itself.
 *
 * As on the PIN restore screen, the debug-log multi-tap on the title did not survive the move: it
 * needed a title `TextView` by id, and the title is now drawn in the top bar.
 */
class ReRegisterWithPinFragment : LoggingFragment() {
  companion object {
    private val TAG = Log.tag(ReRegisterWithPinFragment::class.java)
  }

  private val registrationViewModel by activityViewModels<RegistrationViewModel>()
  private val reRegisterViewModel by viewModels<ReRegisterWithPinViewModel>()

  private lateinit var pinView: LightSvrPinView

  /** Whether "need help?" is offered. Upstream this was a button made visible and never hidden. */
  private var needHelpOffered = false

  /** Whether back may close the current notice. False only for the account-locked notice. */
  private var noticeCancelable = true

  /**
   * Back closes a notice rather than the screen, as it closed the Material dialogs it replaces. The
   * account-locked notice was `setCancelable(false)`, so while it is up back is swallowed instead.
   */
  private val dismissNoticeOnBack = object : OnBackPressedCallback(false) {
    override fun handleOnBackPressed() {
      if (noticeCancelable) {
        clearNotice()
      }
    }
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return LightSvrPinView(requireContext()).apply { id = R.id.light_svr_pin_view }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    pinView = view as LightSvrPinView

    pinView.updateHeader(
      title = getString(R.string.RegistrationLockFragment__enter_your_pin),
      description = getString(R.string.RegistrationLockFragment__enter_the_pin_you_created_for_your_account),
      learnMoreVisible = false
    )
    pinView.updateConfirm(label = getString(RegistrationR.string.RegistrationActivity_continue), enabled = true, busy = false)
    pinView.updateLabel("", isError = false)
    pinView.updateKeyboardToggle(null)
    pinView.updateSupport(null)
    pinView.updateBackContentDescription(getString(R.string.RegistrationScreen__back_to_previous_screen))
    // Exactly what system back did here: the icon only makes it visible.
    pinView.onBack = Runnable { requireActivity().onBackPressedDispatcher.onBackPressed() }
    updateOverflow()

    requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, dismissNoticeOnBack)

    val input = pinView.input
    // A password field from the first frame. The observer below applies the chosen keyboard type as
    // soon as it is attached; until then the field is masked rather than a plain text field.
    PinKeyboardType.NUMERIC.applyInputTypeTo(input)

    // `LightSvrPinView.input` adds the keyboard's no-extract-UI flags as the input connection is
    // created, so assigning the action outright here does not drop them.
    input.imeOptions = EditorInfo.IME_ACTION_DONE
    input.setOnEditorActionListener { v, actionId, _ ->
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        ViewUtil.hideKeyboard(requireContext(), v!!)
        handlePinEntry()
        return@setOnEditorActionListener true
      }
      false
    }

    enableAndFocusPinEntry()

    pinView.onConfirm = Runnable {
      handlePinEntry()
    }

    LiveDataUtil
      .combineLatest(registrationViewModel.uiState, reRegisterViewModel.uiState) { reg, rereg -> reg to rereg }
      .observe(viewLifecycleOwner) { (registrationState, reRegisterState) -> updateViewState(registrationState, reRegisterState) }
  }

  private fun updateViewState(state: RegistrationState, reRegisterState: ReRegisterWithPinState) {
    if (state.networkError != null) {
      genericErrorDialog()
      registrationViewModel.networkErrorShown()
    } else if (!state.canSkipSms) {
      findNavController().safeNavigate(ReRegisterWithPinFragmentDirections.actionReRegisterWithPinFragmentToEnterPhoneNumberFragment(EnterPhoneNumberMode.NORMAL))
      registrationViewModel.setInProgress(false)
    } else if (state.isRegistrationLockEnabled && state.svrTriesRemaining == 0) {
      Log.w(TAG, "Unable to continue skip flow, KBS is locked")
      onAccountLocked()
    } else {
      presentProgress(state.inProgress)
      presentTriesRemaining(reRegisterState, state.svrTriesRemaining)
    }

    // `PinKeyboardType.applyTo` sets an icon on a Material toggle button, and there is none here;
    // the toggle is the "switch keyboard" overflow item, whose label does not change.
    reRegisterState.pinKeyboardType.applyInputTypeTo(pinView.input)

    state.registerAccountError?.let { error ->
      registrationErrorHandler(error)
      registrationViewModel.registerAccountErrorShown()
    }
  }

  private fun presentProgress(inProgress: Boolean) {
    if (inProgress) {
      ViewUtil.hideKeyboard(requireContext(), pinView.input)
      pinView.input.isEnabled = false
    } else {
      pinView.input.isEnabled = true
    }
    pinView.updateInputState(visible = true, enabled = !inProgress)
    pinView.updateConfirm(label = pinView.confirmLabel, enabled = pinView.confirmEnabled, busy = inProgress)
  }

  private fun handlePinEntry() {
    val pin: String? = pinView.input.text?.toString()

    if (pin.isNullOrBlank()) {
      Toast.makeText(requireContext(), R.string.RegistrationActivity_you_must_enter_your_registration_lock_PIN, Toast.LENGTH_LONG).show()
      enableAndFocusPinEntry()
      return
    }

    if (LightRegistrationContracts.pinLength(pin, LightRegistrationContracts.PinWhitespace.TRIM_EDGES) < SvrConstants.MINIMUM_PIN_LENGTH) {
      Toast.makeText(requireContext(), getString(R.string.RegistrationActivity_your_pin_has_at_least_d_digits_or_characters, SvrConstants.MINIMUM_PIN_LENGTH), Toast.LENGTH_LONG).show()
      enableAndFocusPinEntry()
      return
    }

    registrationViewModel.setRegistrationCheckpoint(RegistrationCheckpoint.PIN_CONFIRMED)

    registrationViewModel.verifyReRegisterWithPin(
      context = requireContext(),
      pin = pin,
      wrongPinHandler = {
        registrationViewModel.setInProgress(false)
        reRegisterViewModel.markIncorrectGuess()
      }
    )
  }

  private fun presentTriesRemaining(reRegisterState: ReRegisterWithPinState, triesRemaining: Int) {
    if (reRegisterState.hasIncorrectGuess) {
      if (triesRemaining == 1 && !reRegisterState.isLocalVerification) {
        showNotice(
          title = getString(R.string.PinRestoreEntryFragment_incorrect_pin),
          message = resources.getQuantityString(R.plurals.PinRestoreEntryFragment_you_have_d_attempt_remaining, triesRemaining, triesRemaining),
          actions = listOf(okAction())
        )
      }

      if (triesRemaining > 5) {
        pinView.updateLabel(getString(R.string.PinRestoreEntryFragment_incorrect_pin), isError = true)
      } else {
        pinView.updateLabel(resources.getQuantityString(R.plurals.RegistrationLockFragment__incorrect_pin_d_attempts_remaining, triesRemaining, triesRemaining), isError = true)
      }
      offerNeedHelp()
    } else {
      if (triesRemaining == 1) {
        offerNeedHelp()
        if (!reRegisterState.isLocalVerification) {
          // No title upstream either; the notice draws none rather than inventing one.
          showNotice(
            title = "",
            message = resources.getQuantityString(R.plurals.PinRestoreEntryFragment_you_have_d_attempt_remaining, triesRemaining, triesRemaining),
            actions = listOf(okAction())
          )
        }
      }
    }

    if (triesRemaining == 0) {
      Log.w(TAG, "Account locked. User out of attempts on KBS.")
      onAccountLocked()
    }
  }

  private fun onAccountLocked() {
    Log.d(TAG, "Showing Incorrect PIN dialog. Is local verification: ${reRegisterViewModel.isLocalVerification}")
    val message = if (reRegisterViewModel.isLocalVerification) R.string.ReRegisterWithPinFragment_out_of_guesses_local else R.string.PinRestoreLockedFragment_youve_run_out_of_pin_guesses

    // Upstream's negative button first and its positive button last, as on the other notices.
    showNotice(
      title = getString(R.string.PinRestoreEntryFragment_incorrect_pin),
      message = getString(message),
      actions = listOf(
        LightConfirmAction(getString(R.string.AccountLockedFragment__learn_more)) {
          clearNotice()
          CommunicationActions.openBrowserLink(requireContext(), getString(R.string.PinRestoreLockedFragment_learn_more_url))
        },
        LightConfirmAction(getString(R.string.ReRegisterWithPinFragment_send_sms_code)) {
          clearNotice()
          onSkipPinEntry()
        }
      ),
      cancelable = false
    )
  }

  private fun enableAndFocusPinEntry() {
    pinView.input.isEnabled = true
    if (pinView.failureTitle != null) {
      // A notice is up and holds the field unfocusable; closing it focuses the field again.
      return
    }
    // Focusable in touch mode, which implies focusable: a notice clears both.
    pinView.input.isFocusableInTouchMode = true
    ViewUtil.focusAndShowKeyboard(pinView.input)
  }

  private fun offerNeedHelp() {
    if (!needHelpOffered) {
      needHelpOffered = true
      updateOverflow()
    }
  }

  /** "Switch keyboard", "need help?" once it is offered, and "skip", in the old buttons' order. */
  private fun updateOverflow() {
    val items = buildList {
      add(LightSvrPinMenuItem(getString(R.string.RegistrationLockFragment__switch_keyboard)) { reRegisterViewModel.toggleKeyboardType() })
      if (needHelpOffered) {
        add(LightSvrPinMenuItem(getString(R.string.PinRestoreEntryFragment_need_help)) { onNeedHelpClicked() })
      }
      add(LightSvrPinMenuItem(getString(R.string.PinRestoreEntryFragment_skip)) { onSkipClicked() })
    }
    pinView.updateOverflowItems(items)
  }

  private fun onNeedHelpClicked() {
    Log.i(TAG, "User clicked need help dialog.")
    val message = if (reRegisterViewModel.isLocalVerification) R.string.ReRegisterWithPinFragment_need_help_local else R.string.PinRestoreEntryFragment_your_pin_is_a_d_digit_code

    // Cancel, the row that does nothing, first; skip, the row that gives up on the PIN, last.
    showNotice(
      title = getString(R.string.PinRestoreEntryFragment_need_help),
      message = getString(message, SvrConstants.MINIMUM_PIN_LENGTH),
      actions = listOf(
        LightConfirmAction(getString(R.string.PinRestoreEntryFragment_cancel)) { clearNotice() },
        LightConfirmAction(getString(R.string.PinRestoreEntryFragment_contact_support)) {
          clearNotice()
          val body = SupportEmailUtil.generateSupportEmailBody(requireContext(), R.string.ReRegisterWithPinFragment_support_email_subject, null, null)

          CommunicationActions.openEmail(
            requireContext(),
            SupportEmailUtil.getSupportEmailAddress(requireContext()),
            getString(R.string.ReRegisterWithPinFragment_support_email_subject),
            body
          )
        },
        LightConfirmAction(getString(R.string.PinRestoreEntryFragment_skip)) {
          clearNotice()
          onSkipPinEntry()
        }
      )
    )
  }

  private fun onSkipClicked() {
    Log.i(TAG, "User clicked the skip PIN button.")
    val message = if (reRegisterViewModel.isLocalVerification) R.string.ReRegisterWithPinFragment_skip_local else R.string.PinRestoreEntryFragment_if_you_cant_remember_your_pin

    showNotice(
      title = getString(R.string.PinRestoreEntryFragment_skip_pin_entry),
      message = getString(message),
      actions = listOf(
        LightConfirmAction(getString(R.string.PinRestoreEntryFragment_cancel)) { clearNotice() },
        LightConfirmAction(getString(R.string.PinRestoreEntryFragment_skip)) {
          clearNotice()
          onSkipPinEntry()
        }
      )
    )
  }

  private fun onSkipPinEntry() {
    Log.d(TAG, "User skipping PIN entry.")
    registrationViewModel.setUserSkippedReRegisterFlow(true)
  }

  private fun presentRateLimitedDialog() {
    showNotice(
      title = getString(R.string.RegistrationActivity_too_many_attempts),
      message = getString(R.string.RegistrationActivity_you_have_made_too_many_attempts_please_try_again_later),
      actions = listOf(okAction())
    )
  }

  private fun genericErrorDialog() {
    showNotice(
      title = "",
      message = getString(R.string.RegistrationActivity_error_connecting_to_service),
      actions = listOf(okAction())
    )
  }

  /**
   * Raises a notice in place of a Material dialog.
   *
   * The keyboard is put away first: the notice is drawn in the same window as the field, and on a
   * 413dp screen a keyboard left up would cover its actions. Hiding it is not enough on its own,
   * because [enableAndFocusPinEntry]'s keyboard request is posted and could land after the hide.
   * Making the field unfocusable drops its focus, and a request for a field without focus raises
   * nothing.
   */
  private fun showNotice(title: String, message: String, actions: List<LightConfirmAction>, cancelable: Boolean = true) {
    pinView.input.isFocusable = false
    ViewUtil.hideKeyboard(requireContext(), pinView.input)
    noticeCancelable = cancelable
    pinView.updateNotice(title, message, actions)
    dismissNoticeOnBack.isEnabled = true
  }

  private fun clearNotice() {
    pinView.updateNotice(null, "", emptyList())
    noticeCancelable = true
    dismissNoticeOnBack.isEnabled = false
    pinView.input.isFocusableInTouchMode = true
    if (pinView.input.isEnabled) {
      ViewUtil.focusAndShowKeyboard(pinView.input)
    }
  }

  /** The Material dialogs' `android.R.string.ok` positive button, which only dismissed. */
  private fun okAction(): LightConfirmAction = LightConfirmAction(getString(android.R.string.ok)) { clearNotice() }

  private fun registrationErrorHandler(result: RegisterAccountResult) {
    when (result) {
      is RegisterAccountResult.Success -> throw IllegalStateException("Register account error handler called on successful response!")
      is RegisterAccountResult.AuthorizationFailed,
      is RegisterAccountResult.MalformedRequest,
      is RegisterAccountResult.UnknownError,
      is RegisterAccountResult.ValidationError,
      is RegisterAccountResult.RegistrationLocked -> {
        Log.i(TAG, "Registration failed.", result.getCause())
        genericErrorDialog()
      }

      is RegisterAccountResult.IncorrectRecoveryPassword -> {
        registrationViewModel.setUserSkippedReRegisterFlow(true)
        findNavController().safeNavigate(ReRegisterWithPinFragmentDirections.actionReRegisterWithPinFragmentToEnterPhoneNumberFragment(EnterPhoneNumberMode.NORMAL))
      }

      is RegisterAccountResult.AttemptsExhausted,
      is RegisterAccountResult.RateLimited -> presentRateLimitedDialog()

      is RegisterAccountResult.SvrNoData -> onAccountLocked()
      is RegisterAccountResult.SvrWrongPin -> {
        reRegisterViewModel.markIncorrectGuess()
        reRegisterViewModel.markAsRemoteVerification()
      }
    }
  }
}
