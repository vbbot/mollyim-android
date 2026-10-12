package org.thoughtcrime.securesms.lock.v2

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.TemporaryScreenshotSecurity
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
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
 * megaphone bookkeeping and the post-save sync are all exactly where they were. What changed is
 * presentation: the failure is a panel on this screen rather than a `MaterialAlertDialog`, the save
 * in flight is a full-screen progress state, and a saved PIN is announced by a full-screen success
 * state for [SUCCESS_PAUSE_MS] before the screen acts on it -- where upstream played its success
 * animation, and where the Light version used to leave without a word.
 */
internal class ConfirmSvrPinFragment : BaseSvrPinFragment<ConfirmSvrPinViewModel>() {

  companion object {
    /** How long the success state is up before the screen leaves: long enough to read the heading. */
    const val SUCCESS_PAUSE_MS = 1500L
  }

  private lateinit var confirmViewModel: ConfirmSvrPinViewModel

  /**
   * Swallows back while the PIN is being saved, while the success state is up, and while the
   * failure notice is up.
   *
   * The `MaterialAlertDialogBuilder` the notice replaces was `setCancelable(false)`, so back did
   * nothing while it was showing. The notice is not a window, so this is what keeps that true: a
   * bare back press would otherwise pop the confirm screen and leave the user on the create screen
   * holding a PIN that was never saved and no explanation of why. The save and the success state are
   * held for the same reason: popping the screen mid-save drops the result on the floor, and popping
   * it during the success state would skip what a saved PIN has to do next.
   */
  private val blocksBack = object : OnBackPressedCallback(false) {
    override fun handleOnBackPressed() = Unit
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    TemporaryScreenshotSecurity.bindToViewLifecycleOwner(this)
    requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, blocksBack)
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
    pinView.updateFieldPlaceholder(getString(R.string.ConfirmKbsPinFragment__re_enter_your_pin))
    pinView.updateBusyHeading(getString(R.string.ConfirmKbsPinFragment__creating_pin))
    setConfirmEnabled(true)
  }

  override fun initializeViewModel(): ConfirmSvrPinViewModel {
    val args = ConfirmSvrPinFragmentArgs.fromBundle(requireArguments())
    val userEntry = args.userEntry!!
    val keyboard = args.keyboard
    val factory = ConfirmSvrPinViewModel.Factory(userEntry, keyboard)
    val viewModel = ViewModelProvider(this, factory)[ConfirmSvrPinViewModel::class.java]
    confirmViewModel = viewModel
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

    // While the save is in flight the screen is the full-screen progress state ("Creating PIN..."),
    // which is what `CircularProgressMaterialButton.setSpinning()` stood for. The confirm action is
    // inert throughout: the view model has already refused a second call by leaving the value alone.
    setConfirmBusy(animation == SaveAnimation.LOADING)
    blocksBack.isEnabled = animation == SaveAnimation.LOADING || animation == SaveAnimation.SUCCESS

    when (animation) {
      SaveAnimation.NONE, SaveAnimation.LOADING -> Unit
      SaveAnimation.SUCCESS -> {
        pinView.showSuccess(getString(R.string.ConfirmKbsPinFragment__pin_created), successMessage())
        finishAfterSuccessPause()
      }

      SaveAnimation.FAILURE -> {
        RegistrationUtil.maybeMarkRegistrationComplete()
        displayFailedNotice()
      }
    }
  }

  /** What a new PIN means from here on, when it means anything: reminders, if they are on. */
  private fun successMessage(): String? {
    return if (SignalStore.pin.arePinRemindersEnabled()) {
      getString(R.string.KbsReminderDialog__to_help_you_memorize_your_pin)
    } else {
      null
    }
  }

  /**
   * Leaves the screen once the success state has been up for [SUCCESS_PAUSE_MS], with exactly the
   * side effects, in exactly the order, that SUCCESS always had.
   *
   * Exactly once, whatever happens in the pause. The wait belongs to this view's lifecycle and only
   * runs while it is started, so a view destroyed mid-pause takes its wait with it and a stopped
   * screen does not launch the next activity from the background; a recreated view is handed SUCCESS
   * again by the view model and waits again. Whichever wait finishes first claims the side effects
   * from the view model, which outlives every view, and any later one finds them taken.
   */
  private fun finishAfterSuccessPause() {
    val owner = viewLifecycleOwner
    owner.lifecycleScope.launch {
      owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        delay(SUCCESS_PAUSE_MS)
        if (confirmViewModel.markSuccessHandled()) {
          requireActivity().setResult(Activity.RESULT_OK)
          closeNavGraphBranch()
          RegistrationUtil.maybeMarkRegistrationComplete()
          StorageSyncHelper.scheduleSyncForDataChange()
        }
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
    blocksBack.isEnabled = true
  }

  private fun dismissFailedNotice() {
    pinView.updateNotice(null, "", emptyList())
    blocksBack.isEnabled = false

    markMegaphoneSeenIfNecessary()
    requireActivity().setResult(Activity.RESULT_CANCELED)
    closeNavGraphBranch()
  }

  private fun markMegaphoneSeenIfNecessary() {
    AppDependencies.megaphoneRepository.markInteractedWith(Megaphones.Event.PINS_FOR_ALL)
  }
}
