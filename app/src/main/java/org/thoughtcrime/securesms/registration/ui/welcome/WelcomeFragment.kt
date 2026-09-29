/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.welcome

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.logging.LoggingFragment
import org.signal.core.util.getSerializableCompat
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.ViewBinderDelegate
import org.thoughtcrime.securesms.databinding.FragmentRegistrationWelcomeV3Binding
import org.thoughtcrime.securesms.light.LightBarAction
import org.thoughtcrime.securesms.light.LightTopBarView
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.registration.fragments.RegistrationViewDelegate.setDebugLogSubmitMultiTapView
import org.thoughtcrime.securesms.registration.fragments.WelcomePermissions
import org.thoughtcrime.securesms.registration.ui.RegistrationCheckpoint
import com.thelightphone.sdk.ui.LightIcons
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import org.thoughtcrime.securesms.registration.ui.light.LightRegistrationContracts
import org.thoughtcrime.securesms.registration.ui.light.LightRegistrationViewStyle
import org.thoughtcrime.securesms.registration.ui.permissions.GrantPermissionsFragment
import org.thoughtcrime.securesms.registration.ui.phonenumber.EnterPhoneNumberMode
import org.thoughtcrime.securesms.util.BackupUtil
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.visible

/**
 * First screen that is displayed on the very first app launch.
 */
class WelcomeFragment : LoggingFragment(R.layout.fragment_registration_welcome_v3) {
  companion object {
    private val TAG = Log.tag(WelcomeFragment::class.java)
    private const val TERMS_AND_CONDITIONS_URL = "https://signal.org/legal"
  }

  private val sharedViewModel by activityViewModels<RegistrationViewModel>()
  private val binding: FragmentRegistrationWelcomeV3Binding by ViewBinderDelegate(FragmentRegistrationWelcomeV3Binding::bind)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // The eight-tap debug-log gesture used to hang off the wordmark and the Signal headline. Both
    // are hidden on the Light screen, and a GONE view cannot be tapped, so the gesture had quietly
    // stopped working. The root is the only always-present surface here, and the buttons on top of
    // it keep their own listeners, so they still take their own taps.
    setDebugLogSubmitMultiTapView(binding.root)

    LightRegistrationViewStyle.surface(binding.root)
    LightRegistrationViewStyle.action(binding.welcomeContinueButton)
    LightRegistrationViewStyle.action(binding.welcomeTransferOrRestore)
    LightRegistrationViewStyle.action(binding.link)
    LightRegistrationViewStyle.body(binding.welcomeTermsButton)
    binding.welcomeTermsButton.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
    binding.welcomeTermsButton.strokeWidth = 0
    binding.image.visibility = View.GONE
    binding.title.visibility = View.GONE

    binding.welcomeContinueButton.setOnClickListener { onContinueClicked() }
    binding.welcomeTermsButton.setOnClickListener { onTermsClicked() }
    binding.welcomeTransferOrRestore.setOnClickListener { onRestoreOrTransferClicked() }
    binding.welcomeTransferOrRestore.visible = !sharedViewModel.isReregister

    binding.link.setOnClickListener { onLinkDeviceClicked() }

    bindLightTopBar(binding.lightTopBar)

    childFragmentManager.setFragmentResultListener(RestoreWelcomeBottomSheet.REQUEST_KEY, viewLifecycleOwner) { requestKey, bundle ->
      if (requestKey == RestoreWelcomeBottomSheet.REQUEST_KEY) {
        when (val userSelection = bundle.getSerializableCompat(RestoreWelcomeBottomSheet.REQUEST_KEY, WelcomeUserSelection::class.java)) {
          WelcomeUserSelection.RESTORE_WITH_OLD_PHONE,
          WelcomeUserSelection.RESTORE_WITH_NO_PHONE -> route(userSelection.toLightAction())
          else -> Unit
        }
      }
    }

    parentFragmentManager.setFragmentResultListener(GrantPermissionsFragment.REQUEST_KEY, viewLifecycleOwner) { requestKey, bundle ->
      if (requestKey == GrantPermissionsFragment.REQUEST_KEY) {
        when (val userSelection = bundle.getSerializableCompat(GrantPermissionsFragment.REQUEST_KEY, WelcomeUserSelection::class.java)) {
          WelcomeUserSelection.RESTORE_WITH_OLD_PHONE,
          WelcomeUserSelection.RESTORE_WITH_NO_PHONE,
          WelcomeUserSelection.CONTINUE,
          WelcomeUserSelection.LINK -> route(userSelection.toLightAction(), hasPermissions = true)
          null -> Unit
        }
      }
    }

  }

  /**
   * Wires the Light top bar.
   *
   * This replaces `UseProxyMenuProvider` and the `Toolbar` it needed. That provider inflated
   * `R.menu.enter_phone_number` -- a single, icon-less "Network settings" item -- into the support
   * action bar, so reaching the proxy editor cost an ellipsis tap and then a Material popup on an
   * otherwise black screen. The bar's right slot now carries that action directly, and the popup
   * goes with it.
   *
   * No left action: welcome is the root of the registration graph, so a back chevron here would
   * navigate to nothing.
   */
  private fun bindLightTopBar(topBar: LightTopBarView) {
    topBar.rightAction = LightBarAction.Sdk(
      contentDescription = getString(R.string.RegistrationActivity_network_settings),
      icon = LightIcons.SETTINGS,
      onClick = ::onUseProxyClicked
    )
  }

  private fun onUseProxyClicked() {
    findNavController().safeNavigate(WelcomeFragmentDirections.actionEditProxy())
  }

  private fun onLinkDeviceClicked() {
    route(LightRegistrationContracts.WelcomeAction.LINK_DEVICE)
  }

  private fun navigateToLinkDevice() {
    enableNetwork()
    findNavController().safeNavigate(WelcomeFragmentDirections.goToLinkViaQr())
  }

  private fun enableNetwork() {
    TextSecurePreferences.setHasSeenNetworkConfig(requireContext(), true)
    AppDependencies.networkManager.setNetworkEnabled(true)
  }

  override fun onResume() {
    super.onResume()
    sharedViewModel.resetRestoreDecision()
  }

  private fun onContinueClicked() {
    route(LightRegistrationContracts.WelcomeAction.CONTINUE)
  }

  private fun navigateToNextScreenViaContinue() {
    sharedViewModel.maybePrefillE164(requireContext())
    findNavController().safeNavigate(WelcomeFragmentDirections.goToEnterPhoneNumber(EnterPhoneNumberMode.NORMAL))
  }

  private fun onTermsClicked() {
    CommunicationActions.openBrowserLink(requireContext(), TERMS_AND_CONDITIONS_URL)
  }

  private fun onRestoreOrTransferClicked() {
    RestoreWelcomeBottomSheet().show(childFragmentManager, null)
  }

  private fun route(
    action: LightRegistrationContracts.WelcomeAction,
    hasPermissions: Boolean = hasAllPermissions()
  ) {
    val route = LightRegistrationContracts.welcomeRoute(action, hasPermissions)
    LightRegistrationContracts.dispatch(
      route,
      LightRegistrationContracts.WelcomeRouteCallbacks(
        requestPermissions = { requestedAction ->
          findNavController().safeNavigate(
            WelcomeFragmentDirections.actionWelcomeFragmentToGrantPermissionsFragment(requestedAction.toUserSelection())
          )
        },
        showPhoneNumber = ::navigateToNextScreenViaContinue,
        showLinkDeviceQr = ::navigateToLinkDevice,
        showRestoreViaQr = { navigateToNextScreenViaRestore(WelcomeUserSelection.RESTORE_WITH_OLD_PHONE) },
        showRestoreMethods = { navigateToNextScreenViaRestore(WelcomeUserSelection.RESTORE_WITH_NO_PHONE) }
      )
    )
  }

  private fun navigateToNextScreenViaRestore(userSelection: WelcomeUserSelection) {
    sharedViewModel.maybePrefillE164(requireContext())
    sharedViewModel.setRegistrationCheckpoint(RegistrationCheckpoint.PERMISSIONS_GRANTED)

    when (userSelection) {
      WelcomeUserSelection.LINK,
      WelcomeUserSelection.CONTINUE -> throw IllegalArgumentException()
      WelcomeUserSelection.RESTORE_WITH_OLD_PHONE -> {
        enableNetwork()
        sharedViewModel.intendToRestore(hasOldDevice = true, fromRemote = true)
        findNavController().safeNavigate(WelcomeFragmentDirections.goToRestoreViaQr())
      }
      WelcomeUserSelection.RESTORE_WITH_NO_PHONE -> {
        sharedViewModel.intendToRestore(hasOldDevice = false, fromRemote = true)
        findNavController().safeNavigate(WelcomeFragmentDirections.goToSelectRestoreMethod(userSelection))
      }
    }
  }

  private fun hasAllPermissions(): Boolean {
    val isUserSelectionRequired = BackupUtil.isUserSelectionRequired(requireContext())
    return WelcomePermissions.getWelcomePermissions(isUserSelectionRequired).all { ContextCompat.checkSelfPermission(requireContext(), it) == PackageManager.PERMISSION_GRANTED }
  }

  private fun WelcomeUserSelection.toLightAction(): LightRegistrationContracts.WelcomeAction = when (this) {
    WelcomeUserSelection.CONTINUE -> LightRegistrationContracts.WelcomeAction.CONTINUE
    WelcomeUserSelection.LINK -> LightRegistrationContracts.WelcomeAction.LINK_DEVICE
    WelcomeUserSelection.RESTORE_WITH_OLD_PHONE -> LightRegistrationContracts.WelcomeAction.RESTORE_WITH_OLD_PHONE
    WelcomeUserSelection.RESTORE_WITH_NO_PHONE -> LightRegistrationContracts.WelcomeAction.RESTORE_WITHOUT_OLD_PHONE
  }

  private fun LightRegistrationContracts.WelcomeAction.toUserSelection(): WelcomeUserSelection = when (this) {
    LightRegistrationContracts.WelcomeAction.CONTINUE -> WelcomeUserSelection.CONTINUE
    LightRegistrationContracts.WelcomeAction.LINK_DEVICE -> WelcomeUserSelection.LINK
    LightRegistrationContracts.WelcomeAction.RESTORE_WITH_OLD_PHONE -> WelcomeUserSelection.RESTORE_WITH_OLD_PHONE
    LightRegistrationContracts.WelcomeAction.RESTORE_WITHOUT_OLD_PHONE -> WelcomeUserSelection.RESTORE_WITH_NO_PHONE
  }

}
