package org.thoughtcrime.securesms.linkdevice

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.result.ActivityResultLauncher
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.BiometricDeviceAuthentication
import org.thoughtcrime.securesms.BiometricDeviceLockContract
import org.thoughtcrime.securesms.DevicePinAuthEducationSheet
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.linkdevice.LinkDeviceSettingsState.DialogState
import org.thoughtcrime.securesms.light.LightActionDialog
import org.thoughtcrime.securesms.light.LightActionPanelOverlay
import org.thoughtcrime.securesms.light.LightConfirmationDialog
import org.thoughtcrime.securesms.light.LightDialogAction
import org.thoughtcrime.securesms.light.LightNoticeDialog
import org.thoughtcrime.securesms.light.LightPanelAction
import org.thoughtcrime.securesms.light.LightProgressDialog
import org.thoughtcrime.securesms.light.LightSettingsDivider
import org.thoughtcrime.securesms.light.LightSettingsProgressRow
import org.thoughtcrime.securesms.light.LightSettingsRow
import org.thoughtcrime.securesms.light.LightSettingsScreen
import org.thoughtcrime.securesms.light.LightSettingsSection
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.DateUtils
import org.thoughtcrime.securesms.util.SupportEmailUtil
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import java.util.Locale

/**
 * The Linked Devices settings screen: the devices this account is linked to, and the way to link
 * another one.
 *
 * This is the settings sub-screen the user reaches from the row in the settings root, and it was the
 * last screen in the settings tree still wearing Signal's own material: a `Scaffolds.Settings` top
 * bar, a `CircularProgressIndicator` tinted with `colorScheme.primary`, a filled `Buttons.LargeTonal`
 * pill 300dp wide, rows whose avatars were grey circles, and every margin in raw dp.
 *
 * What replaced each of those, and why:
 *
 *  - The scaffold is [org.thoughtcrime.securesms.light.LightSettingsScreen]: the Light top bar with
 *    the back chevron and the centred title, over a Light scroll view on true black.
 *  - The three-dot overflow on a device is gone. A long press raises
 *    [org.thoughtcrime.securesms.light.LightActionPanel] instead, which is how the Light Phone
 *    opens any context window, and the device row's actions are filtered against what that device
 *    can actually do -- the Signal menu rendered "Edit name" greyed out for a device that could not
 *    be renamed, and a long press is a gesture, not a menu, so there is nothing to read a greyed
 *    row against.
 *  - The filled pill is a settings row. On a monochrome panel a filled rectangle is a solid block
 *    with text in it; the Light Phone states an action with type on black.
 *  - The row avatars are gone. They were decorative, and grey circles are the one thing the design
 *    language has no place for.
 *  - The inline lock glyph in the closing footnote is gone, because the string that holds it puts a
 *    glyph in the middle of a sentence and the sentence already says "end-to-end encryption" in
 *    words.
 *
 * The linking flow itself is untouched: the biometric prompt, the QR scanner, the education sheet
 * and the progress states are all still driven by [LinkDeviceViewModel], and the dialogs those
 * states raise are still the same questions asked in the same words -- only their container changed.
 */
class LinkDeviceFragment : ComposeFragment() {

  companion object {
    private val TAG = Log.tag(LinkDeviceFragment::class)
  }

  private val viewModel: LinkDeviceViewModel by activityViewModels()
  private lateinit var biometricAuth: BiometricDeviceAuthentication
  private lateinit var biometricDeviceLockLauncher: ActivityResultLauncher<String>
  private lateinit var linkDeviceWakeLock: LinkDeviceWakeLock

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    viewModel.initialize()

    biometricDeviceLockLauncher = registerForActivityResult(BiometricDeviceLockContract()) { result: Int ->
      if (result == BiometricDeviceAuthentication.AUTHENTICATED) {
        findNavController().safeNavigate(R.id.action_linkDeviceFragment_to_linkDeviceIntroBottomSheet)
      }
    }

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
      .setAllowedAuthenticators(BiometricDeviceAuthentication.ALLOWED_AUTHENTICATORS)
      .setTitle(requireContext().getString(R.string.LinkDeviceFragment__unlock_to_link))
      .setConfirmationRequired(true)
      .build()
    biometricAuth = BiometricDeviceAuthentication(
      BiometricManager.from(requireActivity()),
      BiometricPrompt(requireActivity(), BiometricAuthenticationListener()),
      promptInfo
    )

    linkDeviceWakeLock = LinkDeviceWakeLock(requireActivity())
  }

  override fun onPause() {
    super.onPause()
    biometricAuth.cancelAuthentication()
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val navController: NavController by remember { mutableStateOf(findNavController()) }

    LaunchedEffect(state.dialogState) {
      when (state.dialogState) {
        DialogState.None, is DialogState.SyncingFailed, DialogState.SyncingTimedOut -> {
          Log.i(TAG, "Releasing wake lock for linked device")
          linkDeviceWakeLock.release()
        }
        is DialogState.SyncingMessages, DialogState.Linking -> {
          Log.i(TAG, "Acquiring wake lock for linked device")
          linkDeviceWakeLock.acquire()
        }
        DialogState.Unlinking, is DialogState.DeviceUnlinked, DialogState.ContactSupport, DialogState.LoadingDebugLog -> Unit
      }
    }

    LaunchedEffect(state.oneTimeEvent) {
      when (val event = state.oneTimeEvent) {
        LinkDeviceSettingsState.OneTimeEvent.None -> {
          Unit
        }
        is LinkDeviceSettingsState.OneTimeEvent.ToastLinked -> {
          Toast.makeText(requireContext(), getString(R.string.LinkDeviceFragment__s_linked, event.name), Toast.LENGTH_LONG).show()
        }
        is LinkDeviceSettingsState.OneTimeEvent.ToastUnlinked -> {
          Toast.makeText(requireContext(), getString(R.string.LinkDeviceFragment__s_unlinked, event.name), Toast.LENGTH_LONG).show()
        }
        LinkDeviceSettingsState.OneTimeEvent.SnackbarLinkCancelled -> {
          // A Toast, as the two events above already are. This one arrived as a Material Snackbar,
          // which put a rounded, accent-tinted card across the bottom of a black screen -- the last
          // Material widget left on it, for a message identical in kind to the two beside it.
          Toast.makeText(requireContext(), getString(R.string.LinkDeviceFragment__linking_cancelled), Toast.LENGTH_LONG).show()
        }
        is LinkDeviceSettingsState.OneTimeEvent.ToastNetworkFailed -> {
          Toast.makeText(requireContext(), getString(R.string.DeviceListActivity_network_failed), Toast.LENGTH_LONG).show()
        }
        is LinkDeviceSettingsState.OneTimeEvent.LaunchQrCodeScanner -> {
          navController.navigateToQrScannerIfAuthed()
        }
        LinkDeviceSettingsState.OneTimeEvent.ShowFinishedSheet -> {
          navController.safeNavigate(R.id.action_linkDeviceFragment_to_linkDeviceFinishedSheet)
        }
        LinkDeviceSettingsState.OneTimeEvent.HideFinishedSheet -> {
          if (navController.currentDestination?.id == R.id.linkDeviceFinishedSheet) {
            navController.popBackStack()
          }
        }
        LinkDeviceSettingsState.OneTimeEvent.SnackbarNameChangeFailure -> Unit
        LinkDeviceSettingsState.OneTimeEvent.SnackbarNameChangeSuccess -> Unit
        LinkDeviceSettingsState.OneTimeEvent.LaunchEmail -> {
          val subject = getString(R.string.LinkDeviceFragment__link_sync_failure_support_email)
          val body = getEmailBody(state.debugLogUrl)
          CommunicationActions.openEmail(requireContext(), SupportEmailUtil.getSupportEmailAddress(requireContext()), subject, body)
        }
      }

      if (state.oneTimeEvent != LinkDeviceSettingsState.OneTimeEvent.None) {
        viewModel.clearOneTimeEvent()
      }
    }

    DeviceListScreen(
      state = state,
      onNavigationClick = { navController.popOrFinish() },
      onLearnMoreClicked = { navController.safeNavigate(R.id.action_linkDeviceFragment_to_linkDeviceLearnMoreBottomSheet) },
      onLinkNewDeviceClicked = {
        viewModel.stopExistingPolling()
        navController.navigateToQrScannerIfAuthed()
      },
      onDeviceSelectedForRemoval = { device -> viewModel.setDeviceToRemove(device) },
      onDeviceRemovalConfirmed = { device -> viewModel.removeDevice(device) },
      onSyncFailureRetryRequested = { viewModel.onSyncErrorRetryRequested() },
      onSyncFailureIgnored = { viewModel.onSyncErrorIgnored() },
      onSyncFailureLearnMore = { CommunicationActions.openBrowserLink(requireContext(), requireContext().getString(R.string.LinkDeviceFragment__learn_more_url)) },
      onSyncFailureContactSupport = { viewModel.onSyncErrorContactSupport() },
      onSyncCancelled = { viewModel.onSyncCancelled() },
      onEditDevice = { device ->
        viewModel.setDeviceToEdit(device)
        navController.safeNavigate(R.id.action_linkDeviceFragment_to_editDeviceNameFragment)
      },
      onDialogDismissed = { viewModel.onDialogDismissed() },
      onContactWithLogs = { viewModel.onContactSupport(includeLogs = true) },
      onContactWithoutLogs = { viewModel.onContactSupport(includeLogs = false) }
    )
  }

  private fun getEmailBody(debugLog: String?): String {
    val filter = R.string.LinkDeviceFragment__link_sync_failure_support_email_filter
    val prefix = StringBuilder()
    if (debugLog != null) {
      prefix.append("\n")
      prefix.append(getString(R.string.HelpFragment__debug_log)).append(" ").append(debugLog).append("\n\n")
    }
    return SupportEmailUtil.generateSupportEmailBody(requireContext(), filter, prefix.toString(), null)
  }

  private fun NavController.navigateToQrScannerIfAuthed() {
    if (biometricAuth.shouldShowEducationSheet(requireContext())) {
      DevicePinAuthEducationSheet.show(getString(R.string.LinkDeviceFragment__before_linking), parentFragmentManager)
      parentFragmentManager.setFragmentResultListener(DevicePinAuthEducationSheet.REQUEST_KEY, viewLifecycleOwner) { _, _ ->
        if (!biometricAuth.authenticate(requireContext(), true) { biometricDeviceLockLauncher.launch(getString(R.string.LinkDeviceFragment__unlock_to_link)) }) {
          this.safeNavigate(R.id.action_linkDeviceFragment_to_linkDeviceIntroBottomSheet)
        }
      }
    } else if (!biometricAuth.authenticate(requireContext(), true) { biometricDeviceLockLauncher.launch(getString(R.string.LinkDeviceFragment__unlock_to_link)) }) {
      this.safeNavigate(R.id.action_linkDeviceFragment_to_linkDeviceIntroBottomSheet)
    }
  }

  private fun NavController.popOrFinish() {
    if (!popBackStack()) {
      requireActivity().finishAfterTransition()
    }
  }

  private inner class BiometricAuthenticationListener : BiometricPrompt.AuthenticationCallback() {
    override fun onAuthenticationError(errorCode: Int, errorString: CharSequence) {
      Log.w(TAG, "Authentication error: $errorCode")
      if (errorCode == BiometricPrompt.ERROR_CANCELED) {
        biometricDeviceLockLauncher.launch(getString(R.string.LinkDeviceFragment__unlock_to_link))
      } else {
        onAuthenticationFailed()
      }
    }

    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
      Log.i(TAG, "Authentication succeeded")
      findNavController().safeNavigate(R.id.action_linkDeviceFragment_to_linkDeviceIntroBottomSheet)
    }

    override fun onAuthenticationFailed() {
      Log.w(TAG, "Unable to authenticate")
    }
  }
}

@Composable
fun DeviceListScreen(
  state: LinkDeviceSettingsState,
  modifier: Modifier = Modifier,
  onNavigationClick: () -> Unit = {},
  onLearnMoreClicked: () -> Unit = {},
  onLinkNewDeviceClicked: () -> Unit = {},
  onDeviceSelectedForRemoval: (Device?) -> Unit = {},
  onDeviceRemovalConfirmed: (Device) -> Unit = {},
  onSyncFailureRetryRequested: () -> Unit = {},
  onSyncFailureIgnored: () -> Unit = {},
  onSyncFailureLearnMore: () -> Unit = {},
  onSyncFailureContactSupport: () -> Unit = {},
  onSyncCancelled: () -> Unit = {},
  onEditDevice: (Device) -> Unit = {},
  onDialogDismissed: () -> Unit = {},
  onContactWithLogs: () -> Unit = {},
  onContactWithoutLogs: () -> Unit = {}
) {
  LinkDeviceDialogs(
    state = state,
    onDeviceSelectedForRemoval = onDeviceSelectedForRemoval,
    onDeviceRemovalConfirmed = onDeviceRemovalConfirmed,
    onSyncFailureRetryRequested = onSyncFailureRetryRequested,
    onSyncFailureIgnored = onSyncFailureIgnored,
    onSyncFailureLearnMore = onSyncFailureLearnMore,
    onSyncFailureContactSupport = onSyncFailureContactSupport,
    onSyncCancelled = onSyncCancelled,
    onDialogDismissed = onDialogDismissed,
    onContactWithLogs = onContactWithLogs,
    onContactWithoutLogs = onContactWithoutLogs
  )

  // Declared here rather than inside the panel so the panel stays a stateless view of one device:
  // closing it drops the target, and the next long press starts from nothing.
  var actionsForDevice by remember { mutableStateOf<Device?>(null) }
  val panelDevice = actionsForDevice

  MollyLightTheme {
    val unlinkLabel = stringResource(id = R.string.LinkDeviceFragment__unlink).uppercase(Locale.getDefault())
    val editNameLabel = stringResource(id = R.string.LinkDeviceFragment__edit_name).uppercase(Locale.getDefault())

    // Panel rows for one linked device, in the order the Light Phone's panels use: the ordinary
    // action first, the destructive one last and on its own.
    //
    // Filtered rather than disabled, which is the difference the design language asks for. A long
    // press is a gesture with no menu to scan, so a row the user cannot use is not something to
    // render at a lower contrast and wait to be read -- it is simply not there. An account that may
    // not manage linked devices at all therefore gets an empty list, and the rows that would open
    // the panel carry no long-press gesture to open it with.
    fun actionsFor(device: Device): List<LightPanelAction> = buildList {
      if (state.canLinkDevices) {
        if (device.canRename) {
          add(
            LightPanelAction(label = editNameLabel) {
              actionsForDevice = null
              onEditDevice(device)
            }
          )
        }

        add(
          LightPanelAction(label = unlinkLabel) {
            actionsForDevice = null
            onDeviceSelectedForRemoval(device)
          }
        )
      }
    }

    Box(modifier = modifier.fillMaxSize()) {
      LightSettingsScreen(
        title = stringResource(id = R.string.preferences__linked_devices),
        onBack = onNavigationClick
      ) {
        LightSettingsRow(
          title = stringResource(id = R.string.LinkDeviceFragment__use_molly_on_another_devices),
          onClick = null
        )
        LightSettingsRow(
          title = stringResource(id = R.string.LearnMoreTextView_learn_more),
          onClick = onLearnMoreClicked
        )

        LightSettingsDivider()

        LightSettingsRow(
          title = stringResource(id = R.string.LinkDeviceFragment__link_a_new_device),
          enabled = state.canLinkDevices,
          onClick = onLinkNewDeviceClicked
        )

        LightSettingsDivider()

        LightSettingsSection(title = stringResource(id = R.string.LinkDeviceFragment__my_linked_devices))

        when {
          state.deviceListLoading -> {
            LightSettingsProgressRow(title = stringResource(id = R.string.LinkDeviceFragment__loading))
          }
          state.devices.isEmpty() -> {
            LightSettingsRow(
              title = stringResource(id = R.string.LinkDeviceFragment__no_linked_devices),
              onClick = null
            )
          }
          else -> {
            state.devices.forEach { device ->
              DeviceRow(
                device = device,
                isInternalUser = state.isInternalUser,
                actions = actionsFor(device),
                onShowActions = { actionsForDevice = it }
              )
            }
          }
        }

        LightSettingsDivider()

        LightSettingsRow(
          // The string carries a `%1$s` slot for an inline lock glyph. There is no glyph here: the
          // design language is monochrome and the sentence names the protection in words, so the
          // slot is filled empty and the space it leaves behind is trimmed off.
          title = stringResource(id = R.string.LinkDeviceFragment__messages_and_chat_info_are_protected, "").trim(),
          onClick = null
        )
      }

      val device = panelDevice
      if (device != null) {
        // The panel sits inside the content area rather than over the whole screen, which is what
        // leaves the top bar -- and the back chevron on it -- reachable while the panel is open.
        BackHandler { actionsForDevice = null }

        LightActionPanelOverlay(
          actions = actionsFor(device),
          onDismiss = { actionsForDevice = null }
        )
      }
    }
  }
}

/**
 * One linked device.
 *
 * The long press is the row's only gesture, and it is installed only when there is something for it
 * to do -- a device belonging to an account that may not manage linked devices has no actions, so
 * its row is inert rather than opening an empty panel.
 */
@Composable
private fun DeviceRow(
  device: Device,
  isInternalUser: Boolean,
  actions: List<LightPanelAction>,
  onShowActions: (Device) -> Unit
) {
  val locale = LocalLocale.current.platformLocale
  val title = if (device.name.isNullOrEmpty()) {
    stringResource(id = R.string.DeviceListItem_unnamed_device)
  } else {
    device.name
  }
  val detail = listOfNotNull(
    "[Internal] DeviceId: ${device.id}".takeIf { isInternalUser },
    device.createdMillis?.let {
      stringResource(id = R.string.DeviceListItem_linked_s, DateUtils.getDayPrecisionTimeSpanString(LocalContext.current, locale, device.createdMillis))
    },
    stringResource(
      id = R.string.DeviceListItem_last_active_s,
      DateUtils.getDayPrecisionTimeSpanString(LocalContext.current, locale, device.lastSeenMillis)
    )
  ).joinToString("\n")

  LightSettingsRow(
    title = title,
    summary = detail,
    // The row itself does nothing, so the tap is inert and the long press is the whole gesture.
    // Returning true marks it consumed, which is also what stops the tap that ends a long press
    // from being delivered as a click.
    onClick = null,
    onLongClick = if (actions.isEmpty()) {
      null
    } else {
      {
        onShowActions(device)
        true
      }
    }
  )
}

/**
 * Every dialog this screen can raise, in the Light grammar.
 *
 * Which Light dialog a state gets is decided by what it has to say and how many ways out it has:
 * two short actions go in the bottom bar ([LightConfirmationDialog]), more than two stack at full
 * width ([LightActionDialog]), a statement with nothing to decide is a [LightNoticeDialog], and work
 * already in flight is a [LightProgressDialog] with no close unless the work can be cancelled.
 */
@Composable
private fun LinkDeviceDialogs(
  state: LinkDeviceSettingsState,
  onDeviceSelectedForRemoval: (Device?) -> Unit,
  onDeviceRemovalConfirmed: (Device) -> Unit,
  onSyncFailureRetryRequested: () -> Unit,
  onSyncFailureIgnored: () -> Unit,
  onSyncFailureLearnMore: () -> Unit,
  onSyncFailureContactSupport: () -> Unit,
  onSyncCancelled: () -> Unit,
  onDialogDismissed: () -> Unit,
  onContactWithLogs: () -> Unit,
  onContactWithoutLogs: () -> Unit
) {
  // Deliberately outside the bottom-sheet guard below: a device can be unlinked while a linking
  // sheet is still finishing its dismissal, and that question must not be lost to a sheet that is
  // already on its way out.
  state.deviceToRemove?.let { device ->
    val name = if (device.name.isNullOrEmpty()) {
      stringResource(id = R.string.DeviceListItem_unnamed_device)
    } else {
      device.name
    }

    LightConfirmationDialog(
      title = stringResource(id = R.string.DeviceListActivity_unlink_s, name),
      body = stringResource(id = R.string.DeviceListActivity_by_unlinking_this_device_it_will_no_longer_be_able_to_send_or_receive),
      confirmLabel = stringResource(id = R.string.LinkDeviceFragment__unlink),
      dismissLabel = stringResource(id = android.R.string.cancel),
      onConfirm = { onDeviceRemovalConfirmed(device) },
      onDismiss = { onDeviceSelectedForRemoval(null) },
      destructive = true
    )
  }

  // If a bottom sheet is showing, we don't want the spinner underneath
  if (state.bottomSheetVisible) {
    return
  }

  when (val dialogState = state.dialogState) {
    DialogState.None -> Unit

    // Linking, unlinking and fetching a debug log are work the app started and is waiting on. None
    // of them is something the user can back out of, so none of them offers a close -- closing one
    // used to be refused, and a close that only hid the notice would be worse than that.
    DialogState.Linking -> LightProgressDialog(
      title = stringResource(id = R.string.LinkDeviceFragment__linking_device),
      onDismiss = onDialogDismissed,
      dismissable = false
    )
    DialogState.Unlinking -> LightProgressDialog(
      title = stringResource(id = R.string.DeviceListActivity_unlinking_device),
      onDismiss = onDialogDismissed,
      dismissable = false
    )
    DialogState.LoadingDebugLog -> LightProgressDialog(
      title = stringResource(id = R.string.LinkDeviceFragment__loading),
      onDismiss = onDialogDismissed,
      dismissable = false
    )

    is DialogState.SyncingMessages -> LightProgressDialog(
      title = stringResource(id = R.string.LinkDeviceFragment__syncing_messages),
      onDismiss = onDialogDismissed,
      dismissable = false,
      onCancelLabel = stringResource(id = android.R.string.cancel),
      onCancel = onSyncCancelled
    )

    is DialogState.SyncingFailed -> when (dialogState.syncFailType) {
      LinkDeviceSettingsState.SyncFailType.NOT_RETRYABLE -> LightActionDialog(
        title = stringResource(id = R.string.LinkDeviceFragment__sync_failure_title),
        body = stringResource(id = R.string.LinkDeviceFragment__sync_failure_body_unretryable),
        actions = listOf(
          LightDialogAction(stringResource(id = R.string.LinkDeviceFragment__learn_more).uppercase(Locale.getDefault())) {
            onDialogDismissed()
            onSyncFailureLearnMore()
          },
          LightDialogAction(stringResource(id = R.string.LinkDeviceFragment__continue).uppercase(Locale.getDefault())) {
            onDialogDismissed()
            onSyncFailureIgnored()
          },
          LightDialogAction(stringResource(id = R.string.LinkDeviceFragment__contact_support).uppercase(Locale.getDefault())) {
            onDialogDismissed()
            onSyncFailureContactSupport()
          }
        ),
        onDismiss = onDialogDismissed
      )

      LinkDeviceSettingsState.SyncFailType.RETRYABLE -> LightConfirmationDialog(
        title = stringResource(id = R.string.LinkDeviceFragment__sync_failure_title),
        body = stringResource(id = R.string.LinkDeviceFragment__sync_failure_body),
        confirmLabel = stringResource(id = R.string.LinkDeviceFragment__sync_failure_retry_button),
        dismissLabel = stringResource(id = R.string.LinkDeviceFragment__sync_failure_dismiss_button),
        onConfirm = onSyncFailureRetryRequested,
        onDismiss = onSyncFailureIgnored
      )

      LinkDeviceSettingsState.SyncFailType.NOT_ENOUGH_SPACE -> LightNoticeDialog(
        title = stringResource(id = R.string.LinkDeviceFragment__not_enough_storage_space),
        body = stringResource(id = R.string.LinkDeviceFragment__you_dont_have_enough),
        onDismiss = onSyncFailureRetryRequested
      )
    }

    DialogState.SyncingTimedOut -> LightConfirmationDialog(
      title = stringResource(id = R.string.LinkDeviceFragment__sync_failure_title),
      body = stringResource(id = R.string.LinkDeviceFragment__sync_failure_body),
      confirmLabel = stringResource(id = R.string.LinkDeviceFragment__sync_failure_retry_button),
      dismissLabel = stringResource(id = R.string.LinkDeviceFragment__sync_failure_dismiss_button),
      onConfirm = onSyncFailureRetryRequested,
      onDismiss = onSyncFailureIgnored
    )

    is DialogState.DeviceUnlinked -> {
      val locale = LocalLocale.current.platformLocale
      val createdAt = DateUtils.getDateTimeString(LocalContext.current, locale, dialogState.deviceCreatedAt)
      LightNoticeDialog(
        title = stringResource(id = R.string.LinkDeviceFragment__device_unlinked),
        body = stringResource(id = R.string.LinkDeviceFragment__the_device_that_was, createdAt),
        onDismiss = onDialogDismissed
      )
    }

    DialogState.ContactSupport -> LightActionDialog(
      title = stringResource(id = R.string.LinkDeviceFragment__submit_debug_log),
      body = stringResource(id = R.string.LinkDeviceFragment__your_debug_logs),
      actions = listOf(
        LightDialogAction(stringResource(id = R.string.LinkDeviceFragment__cancel).uppercase(Locale.getDefault())) {
          onDialogDismissed()
        },
        LightDialogAction(stringResource(id = R.string.LinkDeviceFragment__submit_without_debug).uppercase(Locale.getDefault())) {
          onDialogDismissed()
          onContactWithoutLogs()
        },
        LightDialogAction(stringResource(id = R.string.LinkDeviceFragment__submit_with_debug).uppercase(Locale.getDefault())) {
          onDialogDismissed()
          onContactWithLogs()
        }
      ),
      onDismiss = onDialogDismissed
    )
  }
}

@DayNightPreviews
@Composable
private fun DeviceListScreenPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        devices = listOf(
          Device(1, "Sam's Macbook Pro", 1715793982000, 1716053182000, 0),
          Device(1, "Sam's iPad", 1715793182000, 1716053122000, 0)
        ),
        seenQrEducationSheet = true
      )
    )
  }
}

@DayNightPreviews
@Composable
private fun DeviceListScreenLoadingPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        deviceListLoading = true,
        seenQrEducationSheet = true
      )
    )
  }
}

@DayNightPreviews
@Composable
private fun DeviceListScreenLinkingPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        dialogState = DialogState.Linking,
        seenQrEducationSheet = true
      )
    )
  }
}

@DayNightPreviews
@Composable
private fun DeviceListScreenUnlinkingPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        dialogState = DialogState.Unlinking,
        seenQrEducationSheet = true
      )
    )
  }
}

@DayNightPreviews
@Composable
private fun DeviceListScreenSyncingMessagesPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        dialogState = DialogState.SyncingMessages(1),
        seenQrEducationSheet = true
      )
    )
  }
}

@DayNightPreviews
@Composable
private fun DeviceListScreenSyncingFailedRetryPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        dialogState = DialogState.SyncingTimedOut,
        seenQrEducationSheet = true
      )
    )
  }
}

@DayNightPreviews
@Composable
private fun DeviceListScreenSyncingFailedPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        dialogState = DialogState.SyncingFailed(
          deviceId = 1,
          deviceRegistrationId = 1,
          syncFailType = LinkDeviceSettingsState.SyncFailType.NOT_RETRYABLE
        ),
        seenQrEducationSheet = true
      )
    )
  }
}

@DayNightPreviews
@Composable
private fun DeviceListScreenContactSupportPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        dialogState = DialogState.ContactSupport,
        seenQrEducationSheet = true
      )
    )
  }
}

@DayNightPreviews
@Composable
private fun DeviceListScreenDeviceUnlinkedPreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        dialogState = DialogState.DeviceUnlinked(1736454440342),
        seenQrEducationSheet = true
      )
    )
  }
}

@DayNightPreviews
@Composable
private fun DeviceListScreenNotEnoughStoragePreview() {
  Previews.Preview {
    DeviceListScreen(
      state = LinkDeviceSettingsState(
        dialogState = DialogState.SyncingFailed(
          deviceId = 1,
          deviceRegistrationId = 1,
          syncFailType = LinkDeviceSettingsState.SyncFailType.NOT_ENOUGH_SPACE
        ),
        seenQrEducationSheet = true
      )
    )
  }
}