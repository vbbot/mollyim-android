package org.thoughtcrime.securesms.components.settings.app

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dividers
import org.signal.core.ui.compose.IconButtons
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Rows
import org.signal.core.ui.compose.Scaffolds
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.horizontalGutters
import org.signal.core.ui.compose.theme.SignalTheme
import org.signal.core.util.Util
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.backup.v2.BackupRepository
import org.thoughtcrime.securesms.banner.Banner
import org.thoughtcrime.securesms.banner.BannerManager
import org.thoughtcrime.securesms.banner.banners.DeprecatedBuildBanner
import org.thoughtcrime.securesms.banner.banners.UnauthorizedBanner
import org.thoughtcrime.securesms.banner.ui.compose.Action
import org.thoughtcrime.securesms.banner.ui.compose.DefaultBanner
import org.thoughtcrime.securesms.banner.ui.compose.Importance
import org.thoughtcrime.securesms.components.emoji.Emojifier
import org.thoughtcrime.securesms.components.settings.app.routes.AppSettingsRoute
import org.thoughtcrime.securesms.components.settings.app.routes.AppSettingsRouter
import org.thoughtcrime.securesms.components.settings.app.subscription.BadgeImageMedium
import org.thoughtcrime.securesms.components.settings.app.subscription.InAppPaymentsRepository
import org.thoughtcrime.securesms.components.settings.app.subscription.completed.InAppPaymentsBottomSheetDelegate
import org.thoughtcrime.securesms.compose.rememberStatusBarColorNestedScrollModifier
import org.thoughtcrime.securesms.database.model.InAppPaymentSubscriberRecord
import org.thoughtcrime.securesms.profiles.ProfileName
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.SignalE164Util
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.signal.core.ui.R as CoreUiR

class AppSettingsFragment : ComposeFragment(), Callbacks {

  private val viewModel: AppSettingsViewModel by viewModels()
  private val appSettingsRouter by viewModels<AppSettingsRouter>()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    viewLifecycleOwner.lifecycle.addObserver(InAppPaymentsBottomSheetDelegate(childFragmentManager, viewLifecycleOwner))

    viewLifecycleOwner.lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.RESUMED) {
        appSettingsRouter.currentRoute.collect { route ->
          when (route) {
            is AppSettingsRoute.BackupsRoute.Remote -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_remoteBackupsSettingsFragment)
            is AppSettingsRoute.AccountRoute.Account -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_accountSettingsFragment)
            is AppSettingsRoute.LinkDeviceRoute.LinkDevice -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_linkDeviceFragment)
            is AppSettingsRoute.DonationsRoute.Donations -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_manageDonationsFragment)
            is AppSettingsRoute.AppearanceRoute.Appearance -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_appearanceSettingsFragment)
            is AppSettingsRoute.ChatsRoute.Chats -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_chatsSettingsFragment)
            is AppSettingsRoute.StoriesRoute.Privacy -> findNavController().safeNavigate(AppSettingsFragmentDirections.actionAppSettingsFragmentToStoryPrivacySettings(route.titleId))
            is AppSettingsRoute.NotificationsRoute.Notifications -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_notificationsSettingsFragment)
            is AppSettingsRoute.PrivacyRoute.Privacy -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_privacySettingsFragment)
            is AppSettingsRoute.BackupsRoute.Backups -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_backupsSettingsFragment)
            is AppSettingsRoute.DataAndStorageRoute.DataAndStorage -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_dataAndStorageSettingsFragment)
            is AppSettingsRoute.DataAndStorageRoute.Proxy -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_networkPreferenceFragment)
            is AppSettingsRoute.AppUpdates -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_appUpdatesSettingsFragment)
            is AppSettingsRoute.HelpRoute.Settings -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_helpSettingsFragment)
            is AppSettingsRoute.Invite -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_inviteFragment)
            is AppSettingsRoute.LabsRoute.Labs -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_labsSettingsFragment)
            is AppSettingsRoute.InternalRoute.Internal -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_internalSettingsFragment)
            is AppSettingsRoute.AccountRoute.ManageProfile -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_manageProfileActivity)
            is AppSettingsRoute.UsernameLinkRoute.UsernameLink -> findNavController().safeNavigate(R.id.action_appSettingsFragment_to_usernameLinkSettingsFragment)
            else -> error("Unsupported route: ${route.javaClass.name}")
          }
        }
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.observeAsState()
    val self by viewModel.self.observeAsState()

    if (state == null) return
    if (self == null) return

    val context = LocalContext.current
    val bannerManager = remember {
      BannerManager(
        banners = listOf(
          DeprecatedBuildBanner(),
          UnauthorizedBanner(context)
        )
      )
    }

    AppSettingsContent(
      self = self!!,
      state = state!!,
      bannerManager = bannerManager,
      callbacks = this
    )
  }

  override fun onNavigationClick() {
    requireActivity().finishAfterTransition()
  }

  override fun navigate(route: AppSettingsRoute) {
    appSettingsRouter.navigateTo(route)
  }

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
    viewModel.refreshDeprecatedOrUnregistered()
  }

  override fun copyDonorBadgeSubscriberIdToClipboard() {
    copySubscriberIdToClipboard(
      subscriberType = InAppPaymentSubscriberRecord.Type.DONATION,
      toastSuccessStringRes = R.string.AppSettingsFragment__copied_donor_subscriber_id_to_clipboard
    )
  }

  override fun copyRemoteBackupsSubscriberIdToClipboard() {
    copySubscriberIdToClipboard(
      subscriberType = InAppPaymentSubscriberRecord.Type.BACKUP,
      toastSuccessStringRes = R.string.AppSettingsFragment__copied_backups_subscriber_id_to_clipboard
    )
  }

  private fun copySubscriberIdToClipboard(
    subscriberType: InAppPaymentSubscriberRecord.Type,
    @StringRes toastSuccessStringRes: Int
  ) {
    lifecycleScope.launch {
      val subscriber = withContext(Dispatchers.Default) {
        InAppPaymentsRepository.getSubscriber(subscriberType)
      }

      withContext(Dispatchers.Main) {
        if (subscriber != null) {
          Toast.makeText(requireContext(), toastSuccessStringRes, Toast.LENGTH_LONG).show()
          Util.copyToClipboard(requireContext(), subscriber.subscriberId.serialize())
        }
      }
    }
  }
}

@Composable
private fun AppSettingsContent(
  self: BioRecipientState,
  state: AppSettingsState,
  bannerManager: BannerManager,
  callbacks: Callbacks
) {
  val isRegisteredAndUpToDate by rememberUpdatedState(state.isRegisteredAndUpToDate())
  val context = LocalContext.current

  org.thoughtcrime.securesms.light.MollyLightTheme {
    org.thoughtcrime.securesms.light.LightSettingsScreen(
      title = stringResource(R.string.text_secure_normal__menu_settings),
      onBack = callbacks::onNavigationClick
    ) {
      bannerManager.Banner()

      LightProfileRow(self = self, callbacks = callbacks)

      when (state.backupFailureState) {
        BackupFailureState.SUBSCRIPTION_STATE_MISMATCH -> {
          org.thoughtcrime.securesms.light.LightSettingsRow(
            title = stringResource(R.string.AppSettingsFragment__renew_your_signal_backups_subscription),
            onClick = { callbacks.navigate(AppSettingsRoute.BackupsRoute.Remote()) }
          )
        }
        BackupFailureState.BACKUP_FAILED, BackupFailureState.COULD_NOT_COMPLETE_BACKUP -> {
          org.thoughtcrime.securesms.light.LightSettingsRow(
            title = stringResource(R.string.AppSettingsFragment__couldnt_complete_backup),
            onClick = {
              BackupRepository.markBackupFailedIndicatorClicked()
              callbacks.navigate(AppSettingsRoute.BackupsRoute.Remote())
            }
          )
        }
        BackupFailureState.ALREADY_REDEEMED -> {
          org.thoughtcrime.securesms.light.LightSettingsRow(
            title = stringResource(R.string.AppSettingsFragment__couldnt_redeem_your_backups_subscription),
            onClick = {
              BackupRepository.markBackupAlreadyRedeemedIndicatorClicked()
              callbacks.navigate(AppSettingsRoute.BackupsRoute.Remote())
            }
          )
        }
        BackupFailureState.OUT_OF_STORAGE_SPACE -> {
          org.thoughtcrime.securesms.light.LightSettingsRow(
            title = stringResource(R.string.AppSettingsFragment__backup_storage_limit_reached),
            onClick = { callbacks.navigate(AppSettingsRoute.BackupsRoute.Remote()) }
          )
        }
        BackupFailureState.NONE -> Unit
      }

      org.thoughtcrime.securesms.light.LightSettingsSection(stringResource(R.string.AccountSettingsFragment__account))
      org.thoughtcrime.securesms.light.LightSettingsRow(
        title = stringResource(R.string.AccountSettingsFragment__account),
        onClick = { callbacks.navigate(AppSettingsRoute.AccountRoute.Account) }
      )
      org.thoughtcrime.securesms.light.LightSettingsRow(
        title = stringResource(R.string.preferences__linked_devices),
        enabled = isRegisteredAndUpToDate,
        onClick = { callbacks.navigate(AppSettingsRoute.LinkDeviceRoute.LinkDevice) }
      )
      org.thoughtcrime.securesms.light.LightSettingsRow(
        title = stringResource(R.string.preferences__donate_to_signal),
        onClick = { CommunicationActions.openBrowserLink(context, context.getString(R.string.donate_url)) },
        trailing = {
          com.thelightphone.sdk.ui.LightText(text = "↗", variant = com.thelightphone.sdk.ui.LightTextVariant.Copy)
        }
      )

      org.thoughtcrime.securesms.light.LightSettingsDivider()
      org.thoughtcrime.securesms.light.LightSettingsSection(stringResource(R.string.text_secure_normal__menu_settings))
      org.thoughtcrime.securesms.light.LightSettingsRow(
        title = stringResource(R.string.preferences__appearance),
        onClick = { callbacks.navigate(AppSettingsRoute.AppearanceRoute.Appearance) }
      )
      org.thoughtcrime.securesms.light.LightSettingsRow(
        title = stringResource(R.string.preferences_chats__chats),
        enabled = isRegisteredAndUpToDate,
        onClick = { callbacks.navigate(AppSettingsRoute.ChatsRoute.Chats) }
      )
      org.thoughtcrime.securesms.light.LightSettingsRow(
        title = stringResource(R.string.preferences__stories),
        enabled = isRegisteredAndUpToDate,
        onClick = { callbacks.navigate(AppSettingsRoute.StoriesRoute.Privacy(titleId = R.string.preferences__stories)) }
      )
      org.thoughtcrime.securesms.light.LightSettingsRow(
        title = stringResource(R.string.preferences__notifications),
        enabled = isRegisteredAndUpToDate,
        onClick = { callbacks.navigate(AppSettingsRoute.NotificationsRoute.Notifications) }
      )
      org.thoughtcrime.securesms.light.LightSettingsRow(
        title = stringResource(R.string.preferences__privacy),
        enabled = isRegisteredAndUpToDate,
        onClick = { callbacks.navigate(AppSettingsRoute.PrivacyRoute.Privacy) }
      )
      org.thoughtcrime.securesms.light.LightSettingsRow(
        title = stringResource(R.string.preferences_chats__backups),
        onClick = { callbacks.navigate(AppSettingsRoute.BackupsRoute.Backups()) },
        onLongClick = {
          callbacks.copyRemoteBackupsSubscriberIdToClipboard()
          true
        }
      )
      org.thoughtcrime.securesms.light.LightSettingsRow(
        title = stringResource(R.string.preferences__network),
        onClick = { callbacks.navigate(AppSettingsRoute.DataAndStorageRoute.Proxy) }
      )
      org.thoughtcrime.securesms.light.LightSettingsRow(
        title = stringResource(R.string.preferences__data_and_storage),
        onClick = { callbacks.navigate(AppSettingsRoute.DataAndStorageRoute.DataAndStorage) }
      )
      if (state.showAppUpdates) {
        org.thoughtcrime.securesms.light.LightSettingsRow(
          title = stringResource(R.string.preferences_app_updates__title),
          onClick = { callbacks.navigate(AppSettingsRoute.AppUpdates) }
        )
      }

      org.thoughtcrime.securesms.light.LightSettingsDivider()
      org.thoughtcrime.securesms.light.LightSettingsRow(
        title = stringResource(R.string.preferences__help),
        onClick = { callbacks.navigate(AppSettingsRoute.HelpRoute.Settings()) }
      )
      if (state.showInternalPreferences) {
        org.thoughtcrime.securesms.light.LightSettingsDivider()
        org.thoughtcrime.securesms.light.LightSettingsRow(
          title = "Labs",
          onClick = { callbacks.navigate(AppSettingsRoute.LabsRoute.Labs) }
        )
        org.thoughtcrime.securesms.light.LightSettingsRow(
          title = stringResource(R.string.preferences__internal_preferences),
          onClick = { callbacks.navigate(AppSettingsRoute.InternalRoute.Internal) }
        )
      }
    }
  }
}

@Composable
private fun LightProfileRow(self: BioRecipientState, callbacks: Callbacks) {
  val prettyPhoneNumber = if (LocalInspectionMode.current) {
    self.e164
  } else {
    remember(self.e164) { SignalE164Util.prettyPrint(self.e164) }
  }
  val details = buildList {
    add(prettyPhoneNumber)
    self.username.takeIf { it.isNotBlank() }?.let(::add)
    self.combinedAboutAndEmoji?.let(::add)
  }.joinToString(" · ")

  org.thoughtcrime.securesms.light.LightSettingsRow(
    title = self.profileName.toString(),
    summary = details,
    onClick = { callbacks.navigate(AppSettingsRoute.AccountRoute.ManageProfile) },
    leading = {
      Box {
        AvatarImage(
          recipient = self.recipient,
          modifier = Modifier.size(28.dp)
        )
        if (self.featuredBadge != null) {
          BadgeImageMedium(
            badge = self.featuredBadge,
            modifier = Modifier
              .size(10.dp)
              .align(Alignment.BottomEnd)
          )
        }
      }
    }
  )

  if (self.username.isNotBlank()) {
    org.thoughtcrime.securesms.light.LightSettingsRow(
      title = self.username,
      summary = stringResource(R.string.UsernameLinkSettings_qr_description),
      onClick = { callbacks.navigate(AppSettingsRoute.UsernameLinkRoute.UsernameLink) },
      trailing = {
        com.thelightphone.sdk.ui.LightText(text = "QR", variant = com.thelightphone.sdk.ui.LightTextVariant.Fine)
      }
    )
  }
}

@DayNightPreviews
@Composable
private fun AppSettingsContentPreview() {
  Previews.Preview {
    AppSettingsContent(
      self = BioRecipientState(
        Recipient(
          systemContactName = "Miles Morales",
          profileName = ProfileName.fromParts("Miles", "Morales ❤\uFE0F"),
          isSelf = true,
          e164Value = "+15555555555",
          usernameValue = "miles.98",
          aboutEmoji = "❤\uFE0F",
          about = "About",
          isResolving = false
        )
      ),
      state = AppSettingsState(
        isPrimaryDevice = true,
        unreadPaymentsCount = 5,
        userUnregistered = false,
        clientDeprecated = false,
        showInternalPreferences = true,
        showAppUpdates = true,
        backupFailureState = BackupFailureState.OUT_OF_STORAGE_SPACE
      ),
      bannerManager = BannerManager(
        banners = listOf(TestBanner())
      ),
      callbacks = EmptyCallbacks
    )
  }
}

@DayNightPreviews
@Composable
private fun AppSettingsContentUnregisteredPreview() {
  Previews.Preview {
    AppSettingsContent(
      self = BioRecipientState(
        Recipient(
          systemContactName = "Miles Morales",
          profileName = ProfileName.fromParts("Miles", "Morales ❤\uFE0F"),
          isSelf = true,
          e164Value = "+15555555555",
          usernameValue = "miles.98",
          aboutEmoji = "❤\uFE0F",
          about = "About",
          isResolving = false
        )
      ),
      state = AppSettingsState(
        isPrimaryDevice = true,
        unreadPaymentsCount = 5,
        userUnregistered = true,
        clientDeprecated = false,
        showInternalPreferences = true,
        showAppUpdates = true,
        backupFailureState = BackupFailureState.OUT_OF_STORAGE_SPACE
      ),
      bannerManager = BannerManager(
        banners = listOf(TestBanner())
      ),
      callbacks = EmptyCallbacks
    )
  }
}

@DayNightPreviews
@Composable
private fun BioRowPreview() {
  Previews.Preview {
    LightProfileRow(
      self = BioRecipientState(
        Recipient(
          systemContactName = "Miles Morales",
          profileName = ProfileName.fromParts("Miles", "Morales ❤\uFE0F"),
          isSelf = true,
          e164Value = "+15555555555",
          usernameValue = "miles.98",
          aboutEmoji = "❤\uFE0F",
          about = "About",
          isResolving = false
        )
      ),
      callbacks = EmptyCallbacks
    )
  }
}

private interface Callbacks {
  fun onNavigationClick(): Unit = error("Not implemented.")
  fun navigate(route: AppSettingsRoute): Unit = error("Not implemented")
  fun copyDonorBadgeSubscriberIdToClipboard(): Unit = error("Not implemented")
  fun copyRemoteBackupsSubscriberIdToClipboard(): Unit = error("Not implemented")
}

private object EmptyCallbacks : Callbacks

private class TestBanner : Banner<Unit>() {
  override val enabled: Boolean = true
  override val dataFlow: Flow<Unit> = flowOf(Unit)

  @Composable
  override fun DisplayBanner(model: Unit, contentPadding: PaddingValues) {
    DefaultBanner(
      title = "Test Title",
      body = "This is a test body",
      importance = Importance.ERROR,
      actions = listOf(
        Action(android.R.string.ok) {}
      ),
      paddingValues = contentPadding
    )
  }
}
