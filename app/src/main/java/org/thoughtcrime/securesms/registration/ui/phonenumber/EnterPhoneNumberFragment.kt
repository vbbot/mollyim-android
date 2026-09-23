/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.phonenumber

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GooglePlayServicesUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.util.ThreadUtil
import org.signal.core.util.getParcelableCompat
import org.signal.core.util.isNotNullOrBlank
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.registration.data.RegistrationRepository
import org.thoughtcrime.securesms.registration.data.network.Challenge
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.thoughtcrime.securesms.registration.data.network.RegistrationResult
import org.thoughtcrime.securesms.registration.data.network.RegistrationSessionCheckResult
import org.thoughtcrime.securesms.registration.data.network.RegistrationSessionCreationResult
import org.thoughtcrime.securesms.registration.data.network.RegistrationSessionResult
import org.thoughtcrime.securesms.registration.data.network.VerificationCodeRequestResult
import org.thoughtcrime.securesms.registration.ui.RegistrationCheckpoint
import org.thoughtcrime.securesms.registration.ui.RegistrationState
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import org.thoughtcrime.securesms.registration.ui.countrycode.Country
import org.thoughtcrime.securesms.registration.ui.countrycode.CountryCodeFragment
import org.thoughtcrime.securesms.registration.ui.light.LightRegistrationContracts
import org.thoughtcrime.securesms.registration.ui.toE164
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.Dialogs
import org.thoughtcrime.securesms.util.SignalE164Util
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.SupportEmailUtil
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import kotlin.time.Duration.Companion.milliseconds

class EnterPhoneNumberFragment : ComposeFragment() {

  private val TAG = Log.tag(EnterPhoneNumberFragment::class.java)
  private val sharedViewModel by activityViewModels<RegistrationViewModel>()
  private val fragmentViewModel by viewModels<EnterPhoneNumberViewModel>()
  private val args by navArgs<EnterPhoneNumberFragmentArgs>()

  private val enterPhoneNumberMode: EnterPhoneNumberMode by lazy { args.enterPhoneNumberMode }
  private var processedResumeMode: Boolean = false

  private val skipToNextScreen: DialogInterface.OnClickListener =
    DialogInterface.OnClickListener { _, _ -> moveToVerificationEntryScreen() }

  private var currentPhoneNumberFormatter: com.google.i18n.phonenumbers.AsYouTypeFormatter? = null

  // Compose-observable UI state
  private var countryEmoji by mutableStateOf<String?>(null)
  private var countryName by mutableStateOf("")
  private var countryCode by mutableStateOf("")
  private var phoneNumber by mutableStateOf("")
  private var nextEnabled by mutableStateOf(false)
  private var inProgress by mutableStateOf(false)
  private var showCancel by mutableStateOf(false)
  private var controlsEnabled by mutableStateOf(true)

  @Composable
  override fun FragmentContent() {
    EnterPhoneNumberScreen(
      countryEmoji = countryEmoji,
      countryName = countryName,
      countryCode = countryCode,
      phoneNumber = phoneNumber,
      nextEnabled = nextEnabled,
      inProgress = inProgress,
      showCancel = showCancel,
      controlsEnabled = controlsEnabled,
      onBackClicked = ::popBackStack,
      onCountryClicked = ::moveToCountryPickerScreen,
      onCountryCodeChanged = ::onCountryCodeChanged,
      onPhoneNumberChanged = ::onPhoneNumberChanged,
      onNextClicked = ::onRegistrationButtonClicked,
      onCancelClicked = ::popBackStack,
      onProxyClicked = {
        NavHostFragment.findNavController(this@EnterPhoneNumberFragment)
          .safeNavigate(EnterPhoneNumberFragmentDirections.actionEditProxy())
      },
    )
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    requireActivity().onBackPressedDispatcher.addCallback(
      viewLifecycleOwner,
      object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() = popBackStack()
      }
    )

    countryName = getString(R.string.RegistrationActivity_select_a_country)

    parentFragmentManager.setFragmentResultListener(
      CountryCodeFragment.REQUEST_KEY_COUNTRY, this
    ) { _, bundle ->
      val country: Country = bundle.getParcelableCompat(
        CountryCodeFragment.RESULT_COUNTRY, Country::class.java
      )!!
      fragmentViewModel.setCountry(country.countryCode, country)
    }

    sharedViewModel.uiState.observe(viewLifecycleOwner) { sharedState ->
      nextEnabled = sharedState.phoneNumber != null &&
        PhoneNumberUtil.getInstance().isPossibleNumber(sharedState.phoneNumber)
      inProgress = sharedState.inProgress
      showCancel = !sharedState.inProgress && sharedState.isReRegister
      controlsEnabled = !sharedState.inProgress

      sharedState.networkError?.let {
        presentNetworkError(it)
        sharedViewModel.networkErrorShown()
      }
      sharedState.sessionCreationError?.let {
        handleSessionCreationError(it)
        sharedViewModel.sessionCreationErrorShown()
      }
      sharedState.sessionStateError?.let {
        handleSessionStateError(it)
        sharedViewModel.sessionStateErrorShown()
      }
      sharedState.registerAccountError?.let {
        handleRegistrationErrorResponse(it)
        sharedViewModel.registerAccountErrorShown()
      }
    }

    sharedViewModel.uiState
      .map { it.toNavigationStateOnly() }
      .distinctUntilChanged()
      .observe(viewLifecycleOwner) { sharedState ->
        if (sharedState.challengesRequested.contains(Challenge.CAPTCHA) &&
          sharedState.captchaToken.isNotNullOrBlank()
        ) {
          sharedViewModel.submitCaptchaToken(requireContext())
        } else if (sharedState.challengesRequested.isNotEmpty()) {
          if (!sharedState.challengeInProgress) handleChallenges(sharedState.challengesRequested)
        } else if (
          sharedState.registrationCheckpoint >= RegistrationCheckpoint.PHONE_NUMBER_CONFIRMED &&
          sharedState.canSkipSms
        ) {
          moveToEnterPinScreen()
        } else if (sharedState.registrationCheckpoint >= RegistrationCheckpoint.VERIFICATION_CODE_REQUESTED) {
          moveToVerificationEntryScreen()
        }
      }

    fragmentViewModel.uiState
      .map { it.phoneNumberRegionCode }
      .distinctUntilChanged()
      .observe(viewLifecycleOwner) { regionCode ->
        if (regionCode.isNotNullOrBlank()) {
          currentPhoneNumberFormatter =
            PhoneNumberUtil.getInstance().getAsYouTypeFormatter(regionCode)
          phoneNumber = reformatForDisplay(phoneNumber)
        }
      }

    fragmentViewModel.uiState.observe(viewLifecycleOwner) { fragmentState ->
      if (fragmentViewModel.isEnteredNumberPossible(fragmentState)) {
        sharedViewModel.setPhoneNumber(fragmentViewModel.parsePhoneNumber(fragmentState))
        sharedViewModel.nationalNumber = ""
      } else {
        sharedViewModel.setPhoneNumber(null)
      }

      updateCountrySelection(fragmentState.country)

      if (fragmentState.error != EnterPhoneNumberState.Error.NONE) {
        presentLocalError(fragmentState)
      }
    }

    // Restore previously entered number
    val existingPhoneNumber = sharedViewModel.phoneNumber
    val existingNationalNumber = sharedViewModel.nationalNumber
    if (existingPhoneNumber != null) {
      fragmentViewModel.restoreState(existingPhoneNumber)
      countryCode = existingPhoneNumber.countryCode.toString()
      phoneNumber = existingPhoneNumber.nationalNumber.toString()
    } else {
      if (countryCode.isEmpty()) {
        countryCode = fragmentViewModel.getDefaultCountryCode(requireContext()).toString()
      }
      phoneNumber = existingNationalNumber ?: ""
    }

    if (enterPhoneNumberMode == EnterPhoneNumberMode.RESTART_AFTER_COLLECTION &&
      savedInstanceState == null && !processedResumeMode
    ) {
      processedResumeMode = true
      startNormalRegistration()
    }
  }

  private fun onCountryCodeChanged(raw: String) {
    val sanitized = LightRegistrationContracts.sanitizeCountryCode(raw)
    countryCode = sanitized
    if (sanitized.isNotNullOrBlank()) {
      fragmentViewModel.setCountry(sanitized.toInt())
    } else {
      fragmentViewModel.clearCountry()
    }
  }

  private fun onPhoneNumberChanged(raw: String) {
    val formatted = reformatForDisplay(raw)
    phoneNumber = formatted
    fragmentViewModel.setPhoneNumber(formatted)
    sharedViewModel.nationalNumber = formatted
  }

  private fun reformatForDisplay(input: String): String {
    val formatter = currentPhoneNumberFormatter ?: return input
    formatter.clear()
    var formatted = input
    input.forEach { if (it.isDigit()) formatted = formatter.inputDigit(it) }
    return formatted
  }

  private fun updateCountrySelection(country: Country?) {
    if (country != null) {
      countryEmoji = country.emoji
      countryName = country.name
      val codeStr = country.countryCode.toString()
      if (countryCode != codeStr) countryCode = codeStr
    } else {
      countryEmoji = null
      countryName = getString(R.string.RegistrationActivity_select_a_country)
    }
  }

  private fun handleChallenges(remainingChallenges: List<Challenge>) {
    when (remainingChallenges.first()) {
      Challenge.CAPTCHA -> moveToCaptcha()
      Challenge.PUSH -> sharedViewModel.requestAndSubmitPushToken(requireContext())
    }
  }

  private fun onRegistrationButtonClicked() {
    enableNetwork()
    when (enterPhoneNumberMode) {
      EnterPhoneNumberMode.NORMAL,
      EnterPhoneNumberMode.RESTART_AFTER_COLLECTION -> startNormalRegistration()
      EnterPhoneNumberMode.COLLECT_FOR_MANUAL_SIGNAL_BACKUPS_RESTORE ->
        findNavController().safeNavigate(EnterPhoneNumberFragmentDirections.goToEnterBackupKey())
      EnterPhoneNumberMode.COLLECT_FOR_LOCAL_V2_SIGNAL_BACKUPS_RESTORE ->
        findNavController().safeNavigate(EnterPhoneNumberFragmentDirections.goToRestoreLocalBackupFragment())
    }
  }

  private fun enableNetwork() {
    TextSecurePreferences.setHasSeenNetworkConfig(requireContext(), true)
    AppDependencies.networkManager.setNetworkEnabled(true)
  }

  private fun startNormalRegistration() {
    sharedViewModel.setInProgress(true)
    val hasFcm = validateFcmStatus(requireContext())
    if (hasFcm) {
      sharedViewModel.uiState.observe(viewLifecycleOwner, FcmTokenRetrievedObserver())
      sharedViewModel.fetchFcmToken(requireContext())
    } else {
      sharedViewModel.uiState.value?.let { value ->
        val now = System.currentTimeMillis().milliseconds
        if (value.phoneNumber == null) {
          fragmentViewModel.setError(EnterPhoneNumberState.Error.INVALID_PHONE_NUMBER)
          sharedViewModel.setInProgress(false)
        } else if (now < value.nextSmsTimestamp) {
          moveToVerificationEntryScreen()
        } else {
          presentConfirmNumberDialog(
            value.phoneNumber, value.isReRegister, value.canSkipSms,
            missingFcmConsentRequired = true
          )
        }
      }
    }
  }

  private fun onFcmTokenRetrieved(value: RegistrationState) {
    if (value.phoneNumber == null) {
      fragmentViewModel.setError(EnterPhoneNumberState.Error.INVALID_PHONE_NUMBER)
      sharedViewModel.setInProgress(false)
    } else {
      presentConfirmNumberDialog(
        value.phoneNumber, value.isReRegister, value.canSkipSms,
        missingFcmConsentRequired = false
      )
    }
  }

  private fun validateFcmStatus(context: Context): Boolean {
    val fcmStatus = GooglePlayServicesUtil.isGooglePlayServicesAvailable(context)
    Log.d(TAG, "Got $fcmStatus for Play Services status.")
    if (fcmStatus == ConnectionResult.SERVICE_UPDATING) {
      fragmentViewModel.setError(EnterPhoneNumberState.Error.PLAY_SERVICES_TRANSIENT)
    }
    return fcmStatus == ConnectionResult.SUCCESS
  }

  private fun presentLocalError(state: EnterPhoneNumberState) {
    when (state.error) {
      EnterPhoneNumberState.Error.NONE -> Unit
      EnterPhoneNumberState.Error.INVALID_PHONE_NUMBER -> {
        MaterialAlertDialogBuilder(requireContext()).apply {
          setTitle(R.string.RegistrationActivity_invalid_number)
          setMessage(
            String.format(
              getString(R.string.RegistrationActivity_the_number_you_specified_s_is_invalid),
              state.phoneNumber
            )
          )
          setPositiveButton(android.R.string.ok) { _, _ -> fragmentViewModel.clearError() }
          setOnCancelListener { fragmentViewModel.clearError() }
          setOnDismissListener { fragmentViewModel.clearError() }
          show()
        }
      }
      EnterPhoneNumberState.Error.PLAY_SERVICES_TRANSIENT -> {
        MaterialAlertDialogBuilder(requireContext()).apply {
          setTitle(R.string.RegistrationActivity_play_services_error)
          setMessage(R.string.RegistrationActivity_google_play_services_is_updating_or_unavailable)
          setPositiveButton(android.R.string.ok) { _, _ -> fragmentViewModel.clearError() }
          setOnCancelListener { fragmentViewModel.clearError() }
          setOnDismissListener { fragmentViewModel.clearError() }
          show()
        }
      }
    }
  }

  private fun presentNetworkError(networkError: Throwable) {
    Log.i(TAG, "Unknown error during verification code request", networkError)
    MaterialAlertDialogBuilder(requireContext()).apply {
      setMessage(R.string.RegistrationActivity_unable_to_connect_to_service)
      setPositiveButton(android.R.string.ok, null)
      show()
    }
  }

  private fun handleSessionCreationError(result: RegistrationSessionResult) {
    if (!result.isSuccess()) Log.i(TAG, "Session creation error: ${result.javaClass.name}", result.getCause())
    when (result) {
      is RegistrationSessionCheckResult.Success,
      is RegistrationSessionCreationResult.Success ->
        throw IllegalStateException("Session error handler called on successful response!")
      is RegistrationSessionCreationResult.AttemptsExhausted ->
        presentRemoteErrorDialog(getString(R.string.RegistrationActivity_rate_limited_to_service))
      is RegistrationSessionCreationResult.MalformedRequest ->
        presentRemoteErrorDialog(getString(R.string.RegistrationActivity_unable_to_connect_to_service), skipToNextScreen)
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

  private fun handleSessionStateError(result: VerificationCodeRequestResult) {
    if (!result.isSuccess()) Log.i(TAG, "Session state error.", result.getCause())
    when (result) {
      is VerificationCodeRequestResult.Success ->
        throw IllegalStateException("Session error handler called on successful response!")
      is VerificationCodeRequestResult.ChallengeRequired -> handleChallenges(result.challenges)
      is VerificationCodeRequestResult.ExternalServiceFailure ->
        presentRemoteErrorDialog(getString(R.string.RegistrationActivity_sms_provider_error))
      is VerificationCodeRequestResult.ImpossibleNumber -> {
        MaterialAlertDialogBuilder(requireContext()).apply {
          setMessage(getString(R.string.RegistrationActivity_the_number_you_specified_s_is_invalid, fragmentViewModel.phoneNumber?.toE164()))
          setPositiveButton(android.R.string.ok, null)
          show()
        }
      }
      is VerificationCodeRequestResult.InvalidTransportModeFailure -> {
        MaterialAlertDialogBuilder(requireContext()).apply {
          setMessage(R.string.RegistrationActivity_we_couldnt_send_you_a_verification_code)
          setPositiveButton(R.string.RegistrationActivity_voice_call) { _, _ ->
            sharedViewModel.requestVerificationCall(requireContext())
          }
          setNegativeButton(R.string.RegistrationActivity_cancel, null)
          show()
        }
      }
      is VerificationCodeRequestResult.MalformedRequest ->
        presentRemoteErrorDialog(getString(R.string.RegistrationActivity_unable_to_connect_to_service), skipToNextScreen)
      is VerificationCodeRequestResult.RequestVerificationCodeRateLimited -> {
        Log.i(TAG, result.log())
        handleRequestVerificationCodeRateLimited(result)
      }
      is VerificationCodeRequestResult.SubmitVerificationCodeRateLimited -> presentGenericError(result)
      is VerificationCodeRequestResult.NonNormalizedNumber ->
        handleNonNormalizedNumberError(result.originalNumber, result.normalizedNumber, fragmentViewModel.e164VerificationMode)
      is VerificationCodeRequestResult.RateLimited -> {
        val timeRemaining = result.timeRemaining?.milliseconds
        Log.i(TAG, "Session patch rate limited! Next attempt: $timeRemaining")
        if (timeRemaining != null) {
          presentRemoteErrorDialog(getString(R.string.RegistrationActivity_rate_limited_to_try_again, timeRemaining.toString()))
        } else {
          presentRemoteErrorDialog(getString(R.string.RegistrationActivity_you_have_made_too_many_attempts_please_try_again_later))
        }
      }
      is VerificationCodeRequestResult.TokenNotAccepted ->
        presentRemoteErrorDialog(getString(R.string.RegistrationActivity_we_need_to_verify_that_youre_human)) { _, _ -> moveToCaptcha() }
      is VerificationCodeRequestResult.RegistrationLocked -> presentRegistrationLocked(result.timeRemaining)
      is VerificationCodeRequestResult.AlreadyVerified -> presentGenericError(result)
      is VerificationCodeRequestResult.NoSuchSession -> presentGenericError(result)
      is VerificationCodeRequestResult.UnknownError -> presentGenericError(result)
    }
  }

  private fun handleRegistrationErrorResponse(result: RegisterAccountResult) {
    when (result) {
      is RegisterAccountResult.Success ->
        throw IllegalStateException("Register account error handler called on successful response!")
      is RegisterAccountResult.RegistrationLocked -> presentRegistrationLocked(result.timeRemaining)
      is RegisterAccountResult.AttemptsExhausted -> presentAccountLocked()
      is RegisterAccountResult.RateLimited -> presentRateLimitedDialog()
      is RegisterAccountResult.SvrNoData -> presentAccountLocked()
      else -> presentGenericError(result)
    }
  }

  private fun presentGenericError(result: RegistrationResult) {
    Log.i(TAG, "Unhandled response: ${result.javaClass.name}", result.getCause())
    presentRemoteErrorDialog(getString(R.string.RegistrationActivity_unable_to_connect_to_service))
  }

  private fun presentRegistrationLocked(timeRemaining: Long) {
    findNavController().safeNavigate(EnterPhoneNumberFragmentDirections.actionPhoneNumberRegistrationLock(timeRemaining))
    sharedViewModel.setInProgress(false)
  }

  private fun presentRateLimitedDialog() {
    presentRemoteErrorDialog(getString(R.string.RegistrationActivity_rate_limited_to_service))
  }

  private fun presentAccountLocked() {
    findNavController().safeNavigate(EnterPhoneNumberFragmentDirections.actionPhoneNumberAccountLocked())
    ThreadUtil.postToMain { sharedViewModel.setInProgress(false) }
  }

  private fun moveToCaptcha() {
    findNavController().safeNavigate(EnterPhoneNumberFragmentDirections.actionRequestCaptcha())
    ThreadUtil.postToMain { sharedViewModel.setInProgress(false) }
  }

  private fun presentRemoteErrorDialog(message: String, positiveButtonListener: DialogInterface.OnClickListener? = null) {
    MaterialAlertDialogBuilder(requireContext()).apply {
      setMessage(message)
      setPositiveButton(android.R.string.ok, positiveButtonListener)
      show()
    }
  }

  private fun handleRequestVerificationCodeRateLimited(result: VerificationCodeRequestResult.RequestVerificationCodeRateLimited) {
    if (result.willBeAbleToRequestAgain) {
      Log.i(TAG, "Rate limited but can retry soon, moving to enter code.")
      moveToVerificationEntryScreen()
    } else {
      Log.w(TAG, "Unable to request new verification code.")
      MaterialAlertDialogBuilder(requireContext()).apply {
        setMessage(R.string.RegistrationActivity_sms_provider_error)
        setPositiveButton(R.string.NetworkFailure__retry) { _, _ -> onRegistrationButtonClicked() }
        setNegativeButton(android.R.string.cancel, null)
        show()
      }
    }
  }

  private fun handleNonNormalizedNumberError(
    originalNumber: String,
    normalizedNumber: String,
    mode: RegistrationRepository.E164VerificationMode
  ) {
    try {
      val phoneNumber = PhoneNumberUtil.getInstance().parse(normalizedNumber, null)
      MaterialAlertDialogBuilder(requireContext()).apply {
        setTitle(R.string.RegistrationActivity_non_standard_number_format)
        setMessage(getString(R.string.RegistrationActivity_the_number_you_entered_appears_to_be_a_non_standard, originalNumber, normalizedNumber))
        setNegativeButton(android.R.string.no) { d, _ -> d.dismiss() }
        setNeutralButton(R.string.RegistrationActivity_contact_signal_support) { dialogInterface, _ ->
          val subject = getString(R.string.RegistrationActivity_signal_android_phone_number_format)
          val body = SupportEmailUtil.generateSupportEmailBody(requireContext(), R.string.RegistrationActivity_signal_android_phone_number_format, null, null)
          CommunicationActions.openEmail(requireContext(), SupportEmailUtil.getSupportEmailAddress(requireContext()), subject, body)
          dialogInterface.dismiss()
        }
        setPositiveButton(R.string.yes) { dialogInterface, _ ->
          countryCode = phoneNumber.countryCode.toString()
          this@EnterPhoneNumberFragment.phoneNumber = phoneNumber.nationalNumber.toString()
          when (mode) {
            RegistrationRepository.E164VerificationMode.SMS_WITH_LISTENER,
            RegistrationRepository.E164VerificationMode.SMS_WITHOUT_LISTENER -> sharedViewModel.requestSmsCode(requireContext())
            RegistrationRepository.E164VerificationMode.PHONE_CALL -> sharedViewModel.requestVerificationCall(requireContext())
          }
          dialogInterface.dismiss()
        }
        show()
      }
    } catch (e: NumberParseException) {
      Log.w(TAG, "Failed to parse number!", e)
      Dialogs.showAlertDialog(
        requireContext(),
        getString(R.string.RegistrationActivity_invalid_number),
        getString(R.string.RegistrationActivity_the_number_you_specified_s_is_invalid, fragmentViewModel.phoneNumber?.toE164())
      )
    }
  }

  private fun presentConfirmNumberDialog(
    phoneNumber: PhoneNumber,
    isReRegister: Boolean,
    canSkipSms: Boolean,
    missingFcmConsentRequired: Boolean
  ) {
    val title = if (isReRegister) {
      R.string.RegistrationActivity_additional_verification_required
    } else {
      R.string.RegistrationActivity_phone_number_verification_dialog_title
    }
    val message: CharSequence = SpannableStringBuilder().apply {
      append(SpanUtil.bold(SignalE164Util.prettyPrint(phoneNumber.toE164())))
      if (!canSkipSms) {
        append("\n\n")
        append(getString(R.string.RegistrationActivity_a_verification_code_will_be_sent_to_this_number))
      }
    }
    MaterialAlertDialogBuilder(requireContext()).apply {
      setTitle(title)
      setMessage(message)
      setPositiveButton(android.R.string.ok) { _, _ ->
        Log.d(TAG, "User confirmed number.")
        sharedViewModel.onUserConfirmedPhoneNumber(requireContext())
      }
      setNegativeButton(R.string.RegistrationActivity_edit_number) { _, _ ->
        Log.d(TAG, "User canceled confirm number, returning to edit.")
        sharedViewModel.setInProgress(false)
      }
      setOnCancelListener { sharedViewModel.setInProgress(false) }
    }.show()
  }

  private fun moveToEnterPinScreen() {
    findNavController().safeNavigate(EnterPhoneNumberFragmentDirections.actionReRegisterWithPinFragment())
    sharedViewModel.setInProgress(false)
  }

  private fun moveToVerificationEntryScreen() {
    findNavController().safeNavigate(EnterPhoneNumberFragmentDirections.actionEnterVerificationCode())
    sharedViewModel.setInProgress(false)
  }

  private fun moveToCountryPickerScreen() {
    findNavController().safeNavigate(EnterPhoneNumberFragmentDirections.actionCountryPicker(fragmentViewModel.country))
  }

  private fun popBackStack() {
    sharedViewModel.setRegistrationCheckpoint(RegistrationCheckpoint.INITIALIZATION)
    findNavController().popBackStack()
  }

  private inner class FcmTokenRetrievedObserver :
    org.thoughtcrime.securesms.util.livedata.LiveDataObserverCallback<RegistrationState>(sharedViewModel.uiState) {
    override fun onValue(value: RegistrationState): Boolean {
      val fcmRetrieved = value.isFcmSupported
      if (fcmRetrieved) onFcmTokenRetrieved(value)
      return fcmRetrieved
    }
  }
}
