/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.entercode

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.util.ThreadUtil
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.v2.registerForLifecycle
import org.thoughtcrime.securesms.registration.data.network.Challenge
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.thoughtcrime.securesms.registration.data.network.RegistrationResult
import org.thoughtcrime.securesms.registration.data.network.RegistrationSessionCheckResult
import org.thoughtcrime.securesms.registration.data.network.RegistrationSessionCreationResult
import org.thoughtcrime.securesms.registration.data.network.RegistrationSessionResult
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult
import org.thoughtcrime.securesms.registration.fragments.ContactSupportBottomSheetFragment
import org.thoughtcrime.securesms.registration.fragments.SignalStrengthPhoneStateListener
import org.thoughtcrime.securesms.registration.sms.ReceivedSmsEvent
import org.thoughtcrime.securesms.registration.ui.RegistrationCheckpoint
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import org.thoughtcrime.securesms.registration.ui.phonenumber.PhoneNumberNoticeAction
import org.thoughtcrime.securesms.registration.ui.phonenumber.PhoneNumberNoticeHost
import org.thoughtcrime.securesms.registration.ui.phonenumber.PhoneNumberNoticeModel
import org.thoughtcrime.securesms.registration.ui.phonenumber.PhoneNumberNotices
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The final screen of account registration, where the user enters their verification code.
 *
 * Drawn by [EnterCodeScreen] for the LP3: the code is typed on the system keyboard into a single
 * field just under the top bar, so it stays in view with the keyboard open, and resend / call /
 * wrong number / support live behind the bar's overflow. Every dialog this step raised is a
 * full-screen Light notice ([PhoneNumberNoticeHost]) with the same buttons and callbacks.
 */
class EnterCodeFragment : ComposeFragment() {

  companion object {
    private val TAG = Log.tag(EnterCodeFragment::class.java)

    private const val BOTTOM_SHEET_TAG = "support_bottom_sheet"

    /** ActionCountDownButton re-read the clock every 250ms; the panel's countdowns tick at the same rate. */
    private const val COUNTDOWN_TICK_MS = 250L
  }

  private val sharedViewModel by activityViewModels<RegistrationViewModel>()
  private val fragmentViewModel by viewModels<EnterCodeViewModel>()
  private val bottomSheet = ContactSupportBottomSheetFragment()

  private lateinit var phoneStateListener: SignalStrengthPhoneStateListener

  /** The dialogs of this step, drawn over the screen as Light notices. Cleared with the view. */
  private val notices = PhoneNumberNotices()

  // Compose-observable UI state, written from the view models' observers below.
  private val codeField = VerificationCodeFieldState()
  private var sentTo by mutableStateOf("")
  private var inProgress by mutableStateOf(false)
  private var incorrectCode by mutableStateOf(false)
  private var nextSmsTimestamp by mutableStateOf(0.seconds)
  private var nextCallTimestamp by mutableStateOf(0.seconds)
  private var showHavingTrouble by mutableStateOf(false)
  private var clock by mutableStateOf(now())

  @Composable
  override fun FragmentContent() {
    Box(modifier = Modifier.fillMaxSize()) {
      EnterCodeScreen(
        sentTo = sentTo,
        field = codeField,
        inProgress = inProgress,
        incorrectCode = incorrectCode,
        nextSmsTimestamp = nextSmsTimestamp,
        nextCallTimestamp = nextCallTimestamp,
        showHavingTrouble = showHavingTrouble,
        covered = notices.current != null,
        now = { clock },
        onBack = ::popBackStack,
        onCodeComplete = ::submitCode,
        onResendSms = { sharedViewModel.requestSmsCode(requireContext()) },
        onCall = { sharedViewModel.requestVerificationCall(requireContext()) },
        onWrongNumber = ::popBackStack,
        onHavingTrouble = { bottomSheet.showSafely(childFragmentManager, BOTTOM_SHEET_TAG) },
      )
      PhoneNumberNoticeHost(notices)
    }
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    phoneStateListener = SignalStrengthPhoneStateListener(this, PhoneStateCallback())

    requireActivity().onBackPressedDispatcher.addCallback(
      viewLifecycleOwner,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
          popBackStack()
        }
      }
    )

    sharedViewModel.incorrectCodeAttempts.observe(viewLifecycleOwner) { attempts: Int ->
      if (attempts >= 3) {
        showHavingTrouble = true
      }
    }

    sharedViewModel.uiState.observe(viewLifecycleOwner) { sharedState ->
      sharedState.sessionCreationError?.let { error ->
        handleSessionCreationError(error)
        sharedViewModel.sessionCreationErrorShown()
      }

      sharedState.sessionStateError?.let { error ->
        handleSessionErrorResponse(error)
        sharedViewModel.sessionStateErrorShown()
      }

      sharedState.registerAccountError?.let { error ->
        handleRegistrationErrorResponse(error)
        sharedViewModel.registerAccountErrorShown()
      }

      if (sharedState.challengesRequested.contains(Challenge.CAPTCHA) && sharedState.captchaToken.isNotNullOrBlank()) {
        sharedViewModel.submitCaptchaToken(requireContext())
      } else if (sharedState.challengesRequested.isNotEmpty() && !sharedState.challengeInProgress) {
        handleChallenges(sharedState.challengesRequested)
      }

      // ActionCountDownButton.startCountDownTo(...) for each; the panel derives the same states.
      nextSmsTimestamp = sharedState.nextSmsTimestamp
      nextCallTimestamp = sharedState.nextCallTimestamp
      // Upstream swapped the keypad for a spinner; the field goes read-only and the status line says why.
      inProgress = sharedState.inProgress
    }

    fragmentViewModel.uiState.observe(viewLifecycleOwner) {
      if (it.resetRequiredAfterFailure) {
        codeField.clear()
        codeField.requestFocus()
        fragmentViewModel.allViewsResetCompleted()
      } else if (it.showKeyboard) {
        codeField.requestFocus()
        fragmentViewModel.keyboardShown()
      }
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        while (true) {
          clock = now()
          delay(COUNTDOWN_TICK_MS)
        }
      }
    }

    EventBus.getDefault().registerForLifecycle(subscriber = this, lifecycleOwner = viewLifecycleOwner)
  }

  override fun onResume() {
    super.onResume()
    sharedViewModel.phoneNumber?.let {
      val formatted = PhoneNumberUtil.getInstance().format(it, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
      sentTo = requireContext().getString(R.string.RegistrationActivity_enter_the_code_we_sent_to_s, formatted)
    }
  }

  override fun onDestroyView() {
    // A notice belongs to the view it was raised over; coming back to this step must not bring it back.
    notices.clear()
    // Upstream's cells were rebuilt empty with the view (on the way back from the captcha, say).
    codeField.clear()
    incorrectCode = false
    super.onDestroyView()
  }

  /** The cells' complete listener, unchanged: the sixth digit, typed or auto-filled, submits the code. */
  private fun submitCode(code: String) {
    incorrectCode = false
    sharedViewModel.verifyCodeWithoutRegistrationLock(requireContext(), code)
  }

  private fun handleSessionCreationError(result: RegistrationSessionResult) {
    if (!result.isSuccess()) {
      Log.i(TAG, "[sessionCreateError] Handling error response of ${result.javaClass.name}", result.getCause())
    }
    when (result) {
      is RegistrationSessionCheckResult.Success,
      is RegistrationSessionCreationResult.Success -> throw IllegalStateException("Session error handler called on successful response!")
      is RegistrationSessionCreationResult.AttemptsExhausted -> presentRemoteErrorDialog(getString(R.string.RegistrationActivity_rate_limited_to_service))
      is RegistrationSessionCreationResult.MalformedRequest -> presentRemoteErrorDialog(getString(R.string.RegistrationActivity_unable_to_connect_to_service))

      is RegistrationSessionCreationResult.RateLimited -> {
        val timeRemaining = result.timeRemaining?.milliseconds
        Log.i(TAG, "Session creation rate limited! Next attempt: $timeRemaining")
        if (timeRemaining != null) {
          presentRemoteErrorDialog(getString(R.string.RegistrationActivity_rate_limited_to_try_again, timeRemaining.toString()))
        } else {
          presentRemoteErrorDialog(getString(R.string.RegistrationActivity_you_have_made_too_many_attempts_please_try_again_later))
        }
      }

      is RegistrationSessionCreationResult.ServerUnableToParse -> presentGenericError(result)
      is RegistrationSessionCheckResult.SessionNotFound -> presentGenericError(result)
      is RegistrationSessionCheckResult.UnknownError,
      is RegistrationSessionCreationResult.UnknownError -> presentGenericError(result)
    }
  }

  private fun handleSessionErrorResponse(result: VerificationCodeRequestResult) {
    if (!result.isSuccess()) {
      Log.i(TAG, "[sessionError] Handling error response of ${result.javaClass.name}", result.getCause())
    }

    when (result) {
      is VerificationCodeRequestResult.Success -> throw IllegalStateException("Session error handler called on successful response!")
      is VerificationCodeRequestResult.RateLimited -> {
        val timeRemaining = result.timeRemaining?.milliseconds
        Log.i(TAG, "Session patch rate limited! Next attempt: $timeRemaining")
        if (timeRemaining != null) {
          presentRemoteErrorDialog(getString(R.string.RegistrationActivity_rate_limited_to_try_again, timeRemaining.toString()))
        } else {
          presentRemoteErrorDialog(getString(R.string.RegistrationActivity_you_have_made_too_many_attempts_please_try_again_later))
        }
      }
      is VerificationCodeRequestResult.RegistrationLocked -> presentRegistrationLocked(result.timeRemaining)
      is VerificationCodeRequestResult.ExternalServiceFailure -> presentSmsGenericError(result)
      is VerificationCodeRequestResult.RequestVerificationCodeRateLimited -> {
        Log.i(TAG, result.log())
        handleRequestVerificationCodeRateLimited(result)
      }
      is VerificationCodeRequestResult.SubmitVerificationCodeRateLimited -> presentSubmitVerificationCodeRateLimited()
      is VerificationCodeRequestResult.TokenNotAccepted -> presentRemoteErrorDialog(getString(R.string.RegistrationActivity_we_need_to_verify_that_youre_human)) { moveToCaptcha() }
      else -> presentGenericError(result)
    }
  }

  private fun handleRegistrationErrorResponse(result: RegisterAccountResult) {
    if (!result.isSuccess()) {
      Log.i(TAG, "[registrationError] Handling error response of ${result.javaClass.name}", result.getCause())
    }

    when (result) {
      is RegisterAccountResult.Success -> throw IllegalStateException("Register account error handler called on successful response!")
      is RegisterAccountResult.RegistrationLocked -> presentRegistrationLocked(result.timeRemaining)
      is RegisterAccountResult.AuthorizationFailed -> presentIncorrectCodeDialog()
      is RegisterAccountResult.AttemptsExhausted -> presentAccountLocked()
      is RegisterAccountResult.RateLimited -> presentRateLimitedDialog()

      else -> presentGenericError(result)
    }
  }

  private fun handleChallenges(remainingChallenges: List<Challenge>) {
    when (remainingChallenges.first()) {
      Challenge.CAPTCHA -> moveToCaptcha()
      Challenge.PUSH -> sharedViewModel.requestAndSubmitPushToken(requireContext())
    }
  }

  // The keypad's lock and failure animations used to run before each of these; there is no keypad, so
  // each goes straight to what ran when its animation finished.

  private fun presentAccountLocked() {
    findNavController().safeNavigate(EnterCodeFragmentDirections.actionAccountLocked())
  }

  private fun presentRegistrationLocked(timeRemaining: Long) {
    findNavController().safeNavigate(EnterCodeFragmentDirections.actionRequireKbsLockPin(timeRemaining))
    sharedViewModel.setInProgress(false)
  }

  private fun presentRateLimitedDialog() {
    showNotice(
      title = getString(R.string.RegistrationActivity_too_many_attempts),
      message = getString(R.string.RegistrationActivity_you_have_made_too_many_attempts_please_try_again_later),
      actions = listOf(noticeAction(android.R.string.ok) { fragmentViewModel.resetAllViews() })
    )
  }

  /** Upstream's "Incorrect code" toast is the screen's status line, shown until the next digit. */
  private fun presentIncorrectCodeDialog() {
    sharedViewModel.incrementIncorrectCodeAttempts()
    incorrectCode = true
    fragmentViewModel.resetAllViews()
  }

  private fun presentSmsGenericError(requestResult: RegistrationResult) {
    Log.w(TAG, "Encountered sms provider error!", requestResult.getCause())
    showNotice(
      message = getString(R.string.RegistrationActivity_sms_provider_error),
      actions = listOf(noticeAction(android.R.string.ok) { fragmentViewModel.showKeyboard() })
    )
  }

  /** OK runs [onOk]; BACK only dismisses, as cancelling the Material dialog skipped its positive listener. */
  private fun presentRemoteErrorDialog(message: String, onOk: () -> Unit = {}) {
    showNotice(message = message, actions = listOf(noticeAction(android.R.string.ok, onOk)))
  }

  private fun presentGenericError(requestResult: RegistrationResult) {
    Log.w(TAG, "Encountered unexpected error!", requestResult.getCause())
    showNotice(
      message = getString(R.string.RegistrationActivity_error_connecting_to_service),
      actions = listOf(noticeAction(android.R.string.ok) { fragmentViewModel.showKeyboard() })
    )
  }

  private fun handleRequestVerificationCodeRateLimited(result: VerificationCodeRequestResult.RequestVerificationCodeRateLimited) {
    if (result.willBeAbleToRequestAgain) {
      Log.i(TAG, "Attempted to request new code too soon, timers should be updated")
    } else {
      Log.w(TAG, "Request for new verification code impossible, need to restart registration")
      presentRestartRegistrationNotice()
    }
  }

  private fun presentSubmitVerificationCodeRateLimited() {
    Log.w(TAG, "Submit verification code impossible, need to request a new code and restart registration")
    presentRestartRegistrationNotice()
  }

  /**
   * Upstream's non-cancelable "too many attempts" dialog, whose only exit was OK -> back to the phone
   * number. A notice cannot refuse BACK, so BACK takes that same exit.
   */
  private fun presentRestartRegistrationNotice() {
    showNotice(
      message = getString(R.string.RegistrationActivity_you_have_made_too_many_attempts_please_try_again_later),
      actions = listOf(noticeAction(android.R.string.ok, ::popBackStack)),
      onBack = ::popBackStack
    )
  }

  /**
   * Raises a Light notice in place of a `MaterialAlertDialog`. Upstream gave most of these no title;
   * the top bar then names the step they belong to rather than standing empty. [onBack] is what
   * cancelling the dialog did, which for a cancelable one was nothing.
   */
  private fun showNotice(
    message: String,
    title: String = getString(R.string.RegistrationActivity_verification_code),
    actions: List<PhoneNumberNoticeAction>,
    onBack: () -> Unit = {}
  ) {
    notices.show(
      PhoneNumberNoticeModel(
        title = title,
        message = message,
        actions = actions,
        onBack = onBack,
        backDescription = getString(R.string.EnterCodeFragment__back_to_verification_code)
      )
    )
  }

  private fun noticeAction(@StringRes label: Int, onSelected: () -> Unit = {}): PhoneNumberNoticeAction =
    PhoneNumberNoticeAction(label = getString(label), onSelected = onSelected)

  private fun popBackStack() {
    if (!isAdded) {
      Log.w(TAG, "Fragment is no longer attached, ignoring popBackStack request.")
      return
    }
    sharedViewModel.setRegistrationCheckpoint(RegistrationCheckpoint.PUSH_NETWORK_AUDITED)
    NavHostFragment.findNavController(this).popBackStack()
    sharedViewModel.setInProgress(false)
  }

  private fun now(): Duration = System.currentTimeMillis().milliseconds

  private fun moveToCaptcha() {
    findNavController().safeNavigate(EnterCodeFragmentDirections.actionRequestCaptcha())
    ThreadUtil.postToMain { sharedViewModel.setInProgress(false) }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun onVerificationCodeReceived(event: ReceivedSmsEvent) {
    Log.i(TAG, "Received verification code via EventBus.")
    codeField.clear()

    if (event.code.isBlank() || event.code.length != ReceivedSmsEvent.CODE_LENGTH) {
      Log.i(TAG, "Received invalid code of length ${event.code.length}. Ignoring.")
      return
    }

    if (!event.code.all { it in '0'..'9' }) {
      Log.w(TAG, "Failed to convert code into digits.")
      return
    }

    // Upstream typed the digits into the cells 200ms apart and the sixth fired the complete listener;
    // the field takes the whole code at once and submits it the same way.
    codeField.fill(event.code)?.let(::submitCode)
    Log.i(TAG, "Finished auto-filling code.")
  }

  private inner class PhoneStateCallback : SignalStrengthPhoneStateListener.Callback {
    override fun onNoCellSignalPresent() {
      if (isAdded) {
        bottomSheet.showSafely(childFragmentManager, BOTTOM_SHEET_TAG)
      }
    }

    override fun onCellSignalPresent() {
      if (bottomSheet.isResumed) {
        bottomSheet.dismiss()
      }
    }
  }
}
