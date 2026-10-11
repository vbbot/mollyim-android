/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.phonenumber

import android.content.Context
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
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.map
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GooglePlayServicesUtil
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
import org.thoughtcrime.securesms.registration.ui.toE164
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.SignalE164Util
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

  private val skipToNextScreen: () -> Unit = { moveToVerificationEntryScreen() }

  // Every dialog this step raises, drawn over the form as a full-screen Light notice in place of a
  // MaterialAlertDialogBuilder window. Cleared with the view, so leaving the step never brings a stale
  // notice back with it.
  private val notices = PhoneNumberNotices()

  /** The local error whose notice is up, so a re-emitted state does not raise it twice. */
  private var shownLocalError = EnterPhoneNumberState.Error.NONE

  // Compose-observable UI state. The number is only ever a formatted copy of the view model's; edits
  // go back through applyPhoneNumber so the view models never hold a stale number or a null country.
  private var countryEmoji by mutableStateOf<String?>(null)
  private var countryName by mutableStateOf("")
  private var countrySelected by mutableStateOf(false)
  private var phoneNumber by mutableStateOf("")
  private var editorOpen by mutableStateOf(false)
  private var nextEnabled by mutableStateOf(false)
  private var inProgress by mutableStateOf(false)
  private var showCancel by mutableStateOf(false)
  private var controlsEnabled by mutableStateOf(true)

  @Composable
  override fun FragmentContent() {
    Box(modifier = Modifier.fillMaxSize()) {
      PhoneNumberForm()
      PhoneNumberNoticeHost(notices)
    }
  }

  @Composable
  private fun PhoneNumberForm() {
    EnterPhoneNumberScreen(
      countryEmoji = countryEmoji,
      countryName = countryName,
      countrySelected = countrySelected,
      phoneNumber = phoneNumber,
      editorOpen = editorOpen,
      nextEnabled = nextEnabled,
      inProgress = inProgress,
      showCancel = showCancel,
      controlsEnabled = controlsEnabled,
      onBackClicked = ::popBackStack,
      onCountryClicked = ::moveToCountryPickerScreen,
      onPhoneNumberClicked = ::openEditor,
      onEditorBack = ::closeEditor,
      onEditorDone = ::onEditorDone,
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
        // The editor is an in-screen state, not a destination: system back discards it like its BACK icon.
        override fun handleOnBackPressed() = if (editorOpen) closeEditor() else popBackStack()
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

    fragmentViewModel.uiState.observe(viewLifecycleOwner) { fragmentState ->
      publishPhoneNumber(fragmentState)

      // Derived from the view model on every change, so a country picked by hand (which rewrites the
      // "+code") and a server-normalized number both show up without a second, drifting copy.
      phoneNumber = PhoneNumberEntry.format(fragmentState.internationalNumber)
      updateCountrySelection(fragmentState.country)

      // Raise each local error once. The state is re-emitted for unrelated changes while an error is
      // still set, and each emission would otherwise stack another copy of the same notice.
      if (fragmentState.error != shownLocalError) {
        shownLocalError = fragmentState.error
        presentLocalError(fragmentState)
      }
    }

    // Restore a previously entered or SIM-prefilled number (RegistrationViewModel.maybePrefillE164), or
    // start from the default country's code. Both set the country as well as the number.
    val existingPhoneNumber = sharedViewModel.phoneNumber
    val existingNationalNumber = sharedViewModel.nationalNumber
    if (existingPhoneNumber != null) {
      applyPhoneNumber(existingPhoneNumber)
    } else if (fragmentViewModel.internationalNumber.isEmpty()) {
      val defaultCountryCode = fragmentViewModel.getDefaultCountryCode(requireContext())
      applyPhoneNumber(PhoneNumberEntry.compose(defaultCountryCode, existingNationalNumber))
    }

    if (enterPhoneNumberMode == EnterPhoneNumberMode.RESTART_AFTER_COLLECTION &&
      savedInstanceState == null && !processedResumeMode
    ) {
      processedResumeMode = true
      startNormalRegistration()
    }
  }

  override fun onDestroyView() {
    // A local error's notice is the only thing that clears it (Material did so on any dismissal), so
    // clear it with the notices; otherwise it would be raised again when the user comes back.
    notices.clear()
    fragmentViewModel.clearError()
    shownLocalError = EnterPhoneNumberState.Error.NONE
    super.onDestroyView()
  }

  private fun openEditor() {
    if (controlsEnabled) editorOpen = true
  }

  private fun closeEditor() {
    editorOpen = false
  }

  private fun onEditorDone(text: String) {
    applyPhoneNumber(text)
    closeEditor()
  }

  /**
   * The one way a whole number reaches the view models: the editor, the default code, and (through the
   * [PhoneNumber] overload) a restored, prefilled or server-normalized number. The fragment view model
   * derives and sets the country; the shared view model is then updated at once rather than on the next
   * observer pass, because callers such as the non-normalized "Yes" request a code straight after.
   */
  private fun applyPhoneNumber(international: String) {
    fragmentViewModel.setInternationalNumber(international)
    onPhoneNumberApplied()
  }

  private fun applyPhoneNumber(number: PhoneNumber) {
    fragmentViewModel.restoreState(number)
    onPhoneNumberApplied()
  }

  private fun onPhoneNumberApplied() {
    val state = fragmentViewModel.state
    sharedViewModel.nationalNumber = state.phoneNumber
    publishPhoneNumber(state)
  }

  private fun publishPhoneNumber(fragmentState: EnterPhoneNumberState) {
    if (fragmentViewModel.isEnteredNumberPossible(fragmentState)) {
      sharedViewModel.setPhoneNumber(fragmentViewModel.parsePhoneNumber(fragmentState))
      sharedViewModel.nationalNumber = ""
    } else {
      sharedViewModel.setPhoneNumber(null)
    }
  }

  private fun updateCountrySelection(country: Country?) {
    if (country != null) {
      countryEmoji = country.emoji
      countryName = country.name
      countrySelected = true
    } else {
      countryEmoji = null
      countryName = getString(R.string.RegistrationActivity_select_a_country)
      countrySelected = false
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
      // Material cleared the error on OK, cancel and dismiss alike; here OK and BACK are the only exits.
      EnterPhoneNumberState.Error.INVALID_PHONE_NUMBER -> showNotice(
        title = getString(R.string.RegistrationActivity_invalid_number),
        message = String.format(
          getString(R.string.RegistrationActivity_the_number_you_specified_s_is_invalid),
          state.phoneNumber
        ),
        actions = listOf(noticeAction(android.R.string.ok) { fragmentViewModel.clearError() }),
        onBack = { fragmentViewModel.clearError() }
      )
      EnterPhoneNumberState.Error.PLAY_SERVICES_TRANSIENT -> showNotice(
        title = getString(R.string.RegistrationActivity_play_services_error),
        message = getString(R.string.RegistrationActivity_google_play_services_is_updating_or_unavailable),
        actions = listOf(noticeAction(android.R.string.ok) { fragmentViewModel.clearError() }),
        onBack = { fragmentViewModel.clearError() }
      )
    }
  }

  private fun presentNetworkError(networkError: Throwable) {
    Log.i(TAG, "Unknown error during verification code request", networkError)
    showNotice(message = getString(R.string.RegistrationActivity_unable_to_connect_to_service))
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
      is VerificationCodeRequestResult.ImpossibleNumber -> showNotice(
        message = getString(R.string.RegistrationActivity_the_number_you_specified_s_is_invalid, fragmentViewModel.phoneNumber?.toE164())
      )
      is VerificationCodeRequestResult.InvalidTransportModeFailure -> showNotice(
        message = getString(R.string.RegistrationActivity_we_couldnt_send_you_a_verification_code),
        actions = listOf(
          noticeAction(R.string.RegistrationActivity_cancel),
          noticeAction(R.string.RegistrationActivity_voice_call) { sharedViewModel.requestVerificationCall(requireContext()) }
        )
      )
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
        presentRemoteErrorDialog(getString(R.string.RegistrationActivity_we_need_to_verify_that_youre_human)) { moveToCaptcha() }
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

  /** OK runs [onOk]; BACK only dismisses, as cancelling the Material dialog skipped its positive listener. */
  private fun presentRemoteErrorDialog(message: String, onOk: () -> Unit = {}) {
    showNotice(message = message, actions = listOf(noticeAction(android.R.string.ok, onOk)))
  }

  /**
   * Raises a Light notice in place of a `MaterialAlertDialog`. Upstream gave most of these no title;
   * the top bar then names the step they belong to rather than standing empty.
   */
  private fun showNotice(
    message: String,
    title: String = getString(R.string.RegistrationActivity_phone_number),
    actions: List<PhoneNumberNoticeAction> = listOf(noticeAction(android.R.string.ok)),
    onBack: () -> Unit = {}
  ) {
    notices.show(PhoneNumberNoticeModel(title = title, message = message, actions = actions, onBack = onBack))
  }

  private fun noticeAction(@StringRes label: Int, onSelected: () -> Unit = {}): PhoneNumberNoticeAction =
    PhoneNumberNoticeAction(label = getString(label), onSelected = onSelected)

  private fun handleRequestVerificationCodeRateLimited(result: VerificationCodeRequestResult.RequestVerificationCodeRateLimited) {
    if (result.willBeAbleToRequestAgain) {
      Log.i(TAG, "Rate limited but can retry soon, moving to enter code.")
      moveToVerificationEntryScreen()
    } else {
      Log.w(TAG, "Unable to request new verification code.")
      showNotice(
        message = getString(R.string.RegistrationActivity_sms_provider_error),
        actions = listOf(
          noticeAction(android.R.string.cancel),
          noticeAction(R.string.NetworkFailure__retry) { onRegistrationButtonClicked() }
        )
      )
    }
  }

  private fun handleNonNormalizedNumberError(
    originalNumber: String,
    normalizedNumber: String,
    mode: RegistrationRepository.E164VerificationMode
  ) {
    try {
      val phoneNumber = PhoneNumberUtil.getInstance().parse(normalizedNumber, null)
      showNotice(
        title = getString(R.string.RegistrationActivity_non_standard_number_format),
        message = getString(R.string.RegistrationActivity_the_number_you_entered_appears_to_be_a_non_standard, originalNumber, normalizedNumber),
        // Material's neutral, negative, positive -- left to right there, top to bottom here.
        actions = listOf(
          noticeAction(R.string.RegistrationActivity_contact_signal_support) {
            val subject = getString(R.string.RegistrationActivity_signal_android_phone_number_format)
            val body = SupportEmailUtil.generateSupportEmailBody(requireContext(), R.string.RegistrationActivity_signal_android_phone_number_format, null, null)
            CommunicationActions.openEmail(requireContext(), SupportEmailUtil.getSupportEmailAddress(requireContext()), subject, body)
          },
          noticeAction(android.R.string.no),
          noticeAction(R.string.yes) {
            // Sent to the view models, not just displayed: the request below reads the shared number.
            applyPhoneNumber(phoneNumber)
            when (mode) {
              RegistrationRepository.E164VerificationMode.SMS_WITH_LISTENER,
              RegistrationRepository.E164VerificationMode.SMS_WITHOUT_LISTENER -> sharedViewModel.requestSmsCode(requireContext())
              RegistrationRepository.E164VerificationMode.PHONE_CALL -> sharedViewModel.requestVerificationCall(requireContext())
            }
          }
        )
      )
    } catch (e: NumberParseException) {
      Log.w(TAG, "Failed to parse number!", e)
      showNotice(
        title = getString(R.string.RegistrationActivity_invalid_number),
        message = getString(R.string.RegistrationActivity_the_number_you_specified_s_is_invalid, fragmentViewModel.phoneNumber?.toE164())
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
    notices.show(
      confirmNumberNotice(
        title = getString(title),
        // Upstream set this in bold; the notice gives it its own line at Heading instead.
        number = SignalE164Util.prettyPrint(phoneNumber.toE164()),
        codeWillBeSent = if (canSkipSms) null else getString(R.string.RegistrationActivity_a_verification_code_will_be_sent_to_this_number),
        okLabel = getString(android.R.string.ok),
        editNumberLabel = getString(R.string.RegistrationActivity_edit_number),
        onConfirm = {
          Log.d(TAG, "User confirmed number.")
          sharedViewModel.onUserConfirmedPhoneNumber(requireContext())
        },
        onEditNumber = {
          Log.d(TAG, "User canceled confirm number, returning to edit.")
          sharedViewModel.setInProgress(false)
          // Upstream refocused the number with the keyboard up; here that is the editor.
          editorOpen = true
        },
        onCancel = { sharedViewModel.setInProgress(false) }
      )
    )
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
