package org.thoughtcrime.securesms.lock.v2

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.ViewModelProvider
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.TemporaryScreenshotSecurity
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.light.LightConfirmAction
import org.thoughtcrime.securesms.lock.v2.ConfirmSvrPinViewModel.SaveAnimation
import org.thoughtcrime.securesms.megaphone.Megaphones
import org.thoughtcrime.securesms.registration.util.RegistrationUtil
import org.thoughtcrime.securesms.storage.StorageSyncHelper

/**
 * "Confirm your PIN."
 *
 * This is where a PIN is actually committed, so it is also the screen with the most that must not
 * drift. The comparison, the service call, the success and failure paths, the activity result, the
 * megaphone bookkeeping and the post-save sync are all exactly where they were; only the way the
 * failure is presented changed, from a `MaterialAlertDialog` to a panel on this screen.
 */
internal class ConfirmSvrPinFragment : BaseSvrPinFragment<ConfirmSvrPinViewModel>() {

  /**
   * Swallows back while the failure notice is up.
   *
   * The `MaterialAlertDialogBuilder` this replaces was `setCancelable(false)`, so back did nothing
   * while it was showing. The notice is not a window, so this is what keeps that true: a bare back
   * press would otherwise pop the confirm screen and leave the user on the create screen holding a
   * PIN that was never saved and no explanation of why.
   */
  private val failureBlocksBack = object : OnBackPressedCallback(false) {
    override fun handleOnBackPressed() = Unit
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    TemporaryScreenshotSecurity.bindToViewLifecycleOwner(this)
    requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, failureBlocksBack)
  }

  /**
   * Back here steps back to the screen the PIN was typed on, not to the PIN introduction.
   */
  protected override fun getBackContentDescription(): String =
    getString(R.string.BaseKbsPinFragment__back_to_create_pin)

  /**
   * The header is the same whether this is a first PIN or a change -- the copy that stood in both
   * branches upstream was identical -- so there is one of each now and nowhere for them to drift.
   */
  override fun initializeViewStates() {
    pinView.updateHeader(
      getString(R.string.ConfirmKbsPinFragment__confirm_your_pin),
      getString(R.string.ConfirmKbsPinFragment__re_enter_the_pin_you_just_created),
      false
    )

    // The confirm screen takes the keyboard it was handed; there is no toggle to re-decide it.
    pinView.updateKeyboardToggle(null)
    pinView.updateLabel("", false)
    setConfirmEnabled(true)
  }

  override fun initializeViewModel(): ConfirmSvrPinViewModel {
    val args = ConfirmSvrPinFragmentArgs.fromBundle(requireArguments())
    val userEntry = args.userEntry!!
    val keyboard = args.keyboard
    val factory = ConfirmSvrPinViewModel.Factory(userEntry, keyboard)
    val viewModel = ViewModelProvider(this, factory)[ConfirmSvrPinViewModel::class.java]
    viewModel.label.observe(viewLifecycleOwner) { label: ConfirmSvrPinViewModel.LabelState -> updateLabel(label) }
    viewModel.saveAnimation.observe(viewLifecycleOwner) { animation: SaveAnimation -> updateSaveAnimation(animation) }
    return viewModel
  }

  private fun updateLabel(labelState: ConfirmSvrPinViewModel.LabelState) {
    when (labelState) {
      ConfirmSvrPinViewModel.LabelState.EMPTY -> pinView.updateLabel("", false)
      ConfirmSvrPinViewModel.LabelState.CREATING_PIN -> {
        pinView.updateLabel(getString(R.string.ConfirmKbsPinFragment__creating_pin), false)
        pinView.input.isEnabled = false
      }

      ConfirmSvrPinViewModel.LabelState.RE_ENTER_PIN ->
        pinView.updateLabel(getString(R.string.ConfirmKbsPinFragment__re_enter_your_pin), false)

      ConfirmSvrPinViewModel.LabelState.PIN_DOES_NOT_MATCH -> {
        // The field is cleared here, exactly as before. Size carries the failure, because the Light
        // Phone has no red to carry it with.
        pinView.updateLabel(getString(R.string.ConfirmKbsPinFragment__pins_dont_match), true)
        pinView.clearInput()
      }
    }
  }

  private fun updateSaveAnimation(animation: SaveAnimation) {
    updateInputVisibility(animation)

    // The confirm action shows progress in place of its label while the save is in flight, which is
    // what `CircularProgressMaterialButton.setSpinning()` did. It stays inert throughout: the view
    // model has already refused a second call by leaving the value alone, and the base class gates
    // the IME action on the same liveness.
    setConfirmBusy(animation == SaveAnimation.LOADING)

    when (animation) {
      SaveAnimation.NONE, SaveAnimation.LOADING -> Unit
      SaveAnimation.SUCCESS -> {
        requireActivity().setResult(Activity.RESULT_OK)
        closeNavGraphBranch()
        RegistrationUtil.maybeMarkRegistrationComplete()
        StorageSyncHelper.scheduleSyncForDataChange()
      }

      SaveAnimation.FAILURE -> {
        RegistrationUtil.maybeMarkRegistrationComplete()
        displayFailedNotice()
      }
    }
  }

  private fun updateInputVisibility(saveAnimation: SaveAnimation) {
    pinView.updateInputState(
      visible = saveAnimation == SaveAnimation.NONE,
      enabled = saveAnimation == SaveAnimation.NONE
    )
  }

  /**
   * Stands in for the old `MaterialAlertDialogBuilder`, keeping all of its terms: not cancelable,
   * one acknowledgement, and -- after the acknowledgement -- the megaphone bookkeeping, a cancelled
   * activity result, and leaving the graph.
   */
  private fun displayFailedNotice() {
    pinView.updateNotice(
      title = getString(R.string.ConfirmKbsPinFragment__pin_creation_failed),
      message = getString(R.string.ConfirmKbsPinFragment__your_pin_was_not_saved),
      actions = listOf(
        LightConfirmAction(getString(android.R.string.ok), Runnable { dismissFailedNotice() })
      )
    )
    failureBlocksBack.isEnabled = true
  }

  private fun dismissFailedNotice() {
    pinView.updateNotice(null, "", emptyList())
    failureBlocksBack.isEnabled = false

    markMegaphoneSeenIfNecessary()
    requireActivity().setResult(Activity.RESULT_CANCELED)
    closeNavGraphBranch()
  }

  private fun markMegaphoneSeenIfNecessary() {
    AppDependencies.megaphoneRepository.markInteractedWith(Megaphones.Event.PINS_FOR_ALL)
  }
}
