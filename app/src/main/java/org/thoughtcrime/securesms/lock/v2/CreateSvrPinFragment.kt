package org.thoughtcrime.securesms.lock.v2

import android.os.Bundle
import android.view.View
import androidx.annotation.PluralsRes
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation.findNavController
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.TemporaryScreenshotSecurity
import org.thoughtcrime.securesms.lock.v2.CreateSvrPinViewModel.NavigationEvent
import org.thoughtcrime.securesms.lock.v2.CreateSvrPinViewModel.PinErrorEvent
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * "Create your PIN."
 *
 * Everything here is presentation. The checks that decide whether a PIN is acceptable live in
 * [CreateSvrPinViewModel], and have not moved: the weak-PIN rejection, the navigation that carries
 * the typed PIN forward as a nav argument, and the fact that the PIN reaches the confirm screen as a
 * [SvrPin] rather than as loose text.
 */
class CreateSvrPinFragment : BaseSvrPinFragment<CreateSvrPinViewModel?>() {
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    TemporaryScreenshotSecurity.bindToViewLifecycleOwner(this)
  }

  override fun initializeViewStates() {
    val args = CreateSvrPinFragmentArgs.fromBundle(requireArguments())
    if (args.isPinChange) {
      initializeViewStatesForPinChange()
    } else {
      initializeViewStatesForPinCreate()
    }

    pinView.updateLabel(getPinLengthRestrictionText(R.plurals.CreateKbsPinFragment__pin_must_be_at_least_digits), false)
    setConfirmEnabled(false)
  }

  private fun initializeViewStatesForPinChange() {
    pinView.updateHeader(
      getString(R.string.CreateKbsPinFragment__create_a_new_pin),
      getString(R.string.CreateKbsPinFragment__you_can_choose_a_new_pin_as_long_as_this_device_is_registered),
      true
    )
    pinView.updateFieldPlaceholder(getString(R.string.CreateKbsPinFragment__create_a_new_pin))
  }

  private fun initializeViewStatesForPinCreate() {
    pinView.updateHeader(
      getString(R.string.CreateKbsPinFragment__create_your_pin),
      getString(R.string.CreateKbsPinFragment__pins_can_help_you_restore_your_account),
      true
    )
    pinView.updateFieldPlaceholder(getString(R.string.CreateKbsPinFragment__create_your_pin))
  }

  override fun initializeViewModel(): CreateSvrPinViewModel {
    val viewModel = ViewModelProvider(this)[CreateSvrPinViewModel::class.java]
    val args = CreateSvrPinFragmentArgs.fromBundle(requireArguments())
    viewModel.navigationEvents.observe(viewLifecycleOwner) { e: NavigationEvent -> onConfirmPin(e.userEntry, e.keyboard, args.isPinChange) }
    viewModel.errorEvents.observe(viewLifecycleOwner) { e: PinErrorEvent ->
      if (e == PinErrorEvent.WEAK_PIN) {
        // No colour on the Light Phone, so the failure is carried by ink: white where the neutral
        // status line is #BBBBBB. The rejection itself is unchanged: the PIN is nudged, and only
        // then cleared, so it is not wiped out from under a half-finished shake.
        pinView.updateLabel(getString(R.string.CreateKbsPinFragment__choose_a_stronger_pin), true)
        pinView.shakeEntry { pinView.clearInput() }
      } else {
        throw AssertionError("Unexpected PIN error!")
      }
    }
    viewModel.keyboard.observe(viewLifecycleOwner) { k: PinKeyboardType ->
      pinView.updateLabel(getLabelText(k), false)
      pinView.clearInput()
    }
    return viewModel
  }

  private fun onConfirmPin(userEntry: SvrPin, keyboard: PinKeyboardType, isPinChange: Boolean) {
    val action = CreateSvrPinFragmentDirections.actionConfirmPin()
    action.userEntry = userEntry
    action.keyboard = keyboard
    action.isPinChange = isPinChange
    findNavController(requireView()).safeNavigate(action)
  }

  private fun getLabelText(keyboard: PinKeyboardType): String {
    return if (keyboard == PinKeyboardType.ALPHA_NUMERIC) {
      getPinLengthRestrictionText(R.plurals.CreateKbsPinFragment__pin_must_be_at_least_characters)
    } else {
      getPinLengthRestrictionText(R.plurals.CreateKbsPinFragment__pin_must_be_at_least_digits)
    }
  }

  private fun getPinLengthRestrictionText(@PluralsRes plurals: Int): String {
    return resources.getQuantityString(plurals, SvrConstants.MINIMUM_PIN_LENGTH, SvrConstants.MINIMUM_PIN_LENGTH)
  }
}
