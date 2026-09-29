package org.thoughtcrime.securesms.components.settings.app.privacy.advanced

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.View
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.util.requireDrawable
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.LightSettingsDivider
import org.thoughtcrime.securesms.light.LightSettingsRow
import org.thoughtcrime.securesms.light.LightSettingsScreen
import org.thoughtcrime.securesms.light.LightSettingsSection
import org.thoughtcrime.securesms.light.LightSettingsToggleRow
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.SecurePreferenceManager
import org.thoughtcrime.securesms.util.SpanUtil
import org.thoughtcrime.securesms.util.viewModel
import com.thelightphone.sdk.ui.LightThemeTokens

/**
 * Displays advanced privacy controls such as call relaying and
 * censorship circumvention to the user.
 */
class AdvancedPrivacySettingsFragment : ComposeFragment() {

  private val viewModel: AdvancedPrivacySettingsViewModel by viewModel {
    val repository = AdvancedPrivacySettingsRepository(requireContext())
    val preferences = SecurePreferenceManager.getSecurePreferences(requireContext())

    AdvancedPrivacySettingsViewModel(
      preferences,
      repository
    )
  }

  private var networkReceiver: NetworkReceiver? = null

  override fun onResume() {
    super.onResume()
    viewModel.refresh()
    registerNetworkReceiver()
  }

  override fun onPause() {
    super.onPause()
    unregisterNetworkReceiver()
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    viewLifecycleOwner.lifecycleScope.launch {
      repeatOnLifecycle(Lifecycle.State.RESUMED) {
        viewModel.events.collect {
          if (it == AdvancedPrivacySettingsViewModel.Event.DISABLE_PUSH_FAILED) {
            Toast.makeText(
              requireContext(),
              R.string.ApplicationPreferencesActivity_error_connecting_to_server,
              Toast.LENGTH_LONG
            ).show()
          }
        }
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsStateWithLifecycle()

    AdvancedPrivacySettingsScreen(
      state = state,
      callbacks = remember { Callbacks() }
    )
  }

  @Suppress("DEPRECATION")
  private fun registerNetworkReceiver() {
    val context: Context? = context
    if (context != null && networkReceiver == null) {
      networkReceiver = NetworkReceiver()
      context.registerReceiver(networkReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
    }
  }

  private fun unregisterNetworkReceiver() {
    val context: Context? = context
    if (context != null && networkReceiver != null) {
      context.unregisterReceiver(networkReceiver)
      networkReceiver = null
    }
  }

  private inner class NetworkReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      viewModel.refresh()
    }
  }

  private inner class Callbacks : AdvancedPrivacySettingsCallbacks {
    override fun onNavigationClick() {
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onAlwaysRelayCallsChanged(enabled: Boolean) {
      viewModel.setAlwaysRelayCalls(enabled)
    }

    override fun onCensorshipCircumventionChanged(enabled: Boolean) {
      viewModel.setCensorshipCircumventionEnabled(enabled)
    }

    override fun onShowStatusIconForSealedSenderChanged(enabled: Boolean) {
      viewModel.setShowStatusIconForSealedSender(enabled)
    }

    override fun onAllowSealedSenderFromAnyoneChanged(enabled: Boolean) {
      viewModel.setAllowSealedSenderFromAnyone(enabled)
    }

    override fun onSealedSenderLearnMoreClick() {
      CommunicationActions.openBrowserLink(
        requireContext(),
        getString(R.string.AdvancedPrivacySettingsFragment__sealed_sender_link)
      )
    }

    override fun onAllowAutomaticVerificationChanged(enabled: Boolean) {
      viewModel.setAllowAutomaticVerification(enabled)
    }

    override fun onAutomaticVerificationLearnMoreClick() {
      CommunicationActions.openBrowserLink(
        requireContext(),
        getString(R.string.verify_display_fragment__link)
      )
    }
  }
}

private interface AdvancedPrivacySettingsCallbacks {

  fun onNavigationClick() = Unit
  fun onAlwaysRelayCallsChanged(enabled: Boolean) = Unit
  fun onCensorshipCircumventionChanged(enabled: Boolean) = Unit
  fun onShowStatusIconForSealedSenderChanged(enabled: Boolean) = Unit
  fun onAllowSealedSenderFromAnyoneChanged(enabled: Boolean) = Unit
  fun onSealedSenderLearnMoreClick() = Unit
  fun onAutomaticVerificationLearnMoreClick() = Unit
  fun onAllowAutomaticVerificationChanged(enabled: Boolean) = Unit

  object Empty : AdvancedPrivacySettingsCallbacks
}

@Composable
private fun AdvancedPrivacySettingsScreen(
  state: AdvancedPrivacySettingsState,
  callbacks: AdvancedPrivacySettingsCallbacks
) {
  MollyLightTheme {
    val context = LocalContext.current
    val secondaryContentColor = LightThemeTokens.colors.contentSecondary.toArgb()
    val sealedSenderTitle = remember(context, secondaryContentColor) {
      SpannableStringBuilder(context.getString(R.string.AdvancedPrivacySettingsFragment__show_status_icon)).apply {
        val drawable = context.requireDrawable(R.drawable.ic_unidentified_delivery).mutate().apply {
          setTint(secondaryContentColor)
        }
        SpanUtil.appendCenteredImageSpan(this, drawable, 20, 15)
      }
    }
    val sealedSenderLearnMore = remember(context, secondaryContentColor, callbacks) {
      SpanUtil.learnMore(context, secondaryContentColor) { callbacks.onSealedSenderLearnMoreClick() }
    }
    val automaticVerificationSummary = remember(context, secondaryContentColor, callbacks) {
      SpannableStringBuilder(context.getString(R.string.preferences_automatic_key_verification_body))
        .append(" ")
        .append(SpanUtil.learnMore(context, secondaryContentColor) { callbacks.onAutomaticVerificationLearnMoreClick() })
    }

    LightSettingsScreen(
      title = stringResource(R.string.preferences__advanced),
      onBack = callbacks::onNavigationClick
    ) {
      LightSettingsToggleRow(
        checked = state.alwaysRelayCalls || state.proxyEnabled,
        enabled = !state.proxyEnabled,
        title = stringResource(R.string.preferences_advanced__always_relay_calls),
        summary = stringResource(R.string.preferences_advanced__relay_all_calls_through_the_signal_server_to_avoid_revealing_your_ip_address),
        onCheckedChange = callbacks::onAlwaysRelayCallsChanged
      )

      LightSettingsDivider()
      LightSettingsSection(stringResource(R.string.preferences_communication__category_censorship_circumvention))

      val censorshipSummaryResId: Int = when (state.censorshipCircumventionState) {
        CensorshipCircumventionState.AVAILABLE -> R.string.preferences_communication__censorship_circumvention_if_enabled_signal_will_attempt_to_circumvent_censorship
        CensorshipCircumventionState.AVAILABLE_MANUALLY_DISABLED -> R.string.preferences_communication__censorship_circumvention_you_have_manually_disabled
        CensorshipCircumventionState.AVAILABLE_AUTOMATICALLY_ENABLED -> R.string.preferences_communication__censorship_circumvention_has_been_activated_based_on_your_accounts_phone_number
        CensorshipCircumventionState.UNAVAILABLE_CONNECTED -> R.string.preferences_communication__censorship_circumvention_is_not_necessary_you_are_already_connected
        CensorshipCircumventionState.UNAVAILABLE_NO_INTERNET -> R.string.preferences_communication__censorship_circumvention_can_only_be_activated_when_connected_to_the_internet
      }
      LightSettingsToggleRow(
        title = stringResource(R.string.preferences_communication__censorship_circumvention),
        summary = stringResource(censorshipSummaryResId),
        checked = state.censorshipCircumventionEnabled,
        enabled = state.censorshipCircumventionState.available,
        onCheckedChange = callbacks::onCensorshipCircumventionChanged
      )

      LightSettingsDivider()
      LightSettingsSection(stringResource(R.string.preferences_communication__category_sealed_sender))
      LightSettingsToggleRow(
        title = sealedSenderTitle,
        summary = stringResource(R.string.AdvancedPrivacySettingsFragment__show_an_icon),
        checked = state.showSealedSenderStatusIcon,
        onCheckedChange = callbacks::onShowStatusIconForSealedSenderChanged
      )
      if (state.isPrimaryDevice) {
        LightSettingsToggleRow(
          checked = state.allowSealedSenderFromAnyone,
          title = stringResource(R.string.preferences_communication__sealed_sender_allow_from_anyone),
          summary = stringResource(R.string.preferences_communication__sealed_sender_allow_from_anyone_description),
          onCheckedChange = callbacks::onAllowSealedSenderFromAnyoneChanged
        )
      }
      LightSettingsRow(
        title = sealedSenderLearnMore,
        onClick = null
      )

      LightSettingsDivider()
      LightSettingsToggleRow(
        checked = state.allowAutomaticKeyVerification,
        title = stringResource(R.string.preferences_automatic_key_verification),
        summary = automaticVerificationSummary,
        onCheckedChange = callbacks::onAllowAutomaticVerificationChanged
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun AdvancedPrivacySettingsScreenPreview() {
  Previews.Preview {
    AdvancedPrivacySettingsScreen(
      state = AdvancedPrivacySettingsState(
        isPushEnabled = true,
        alwaysRelayCalls = false,
        proxyEnabled = false,
        censorshipCircumventionState = CensorshipCircumventionState.UNAVAILABLE_CONNECTED,
        censorshipCircumventionEnabled = false,
        showSealedSenderStatusIcon = false,
        allowSealedSenderFromAnyone = false,
        showProgressSpinner = false,
        allowAutomaticKeyVerification = false,
        isPrimaryDevice = true
      ),
      callbacks = AdvancedPrivacySettingsCallbacks.Empty
    )
  }
}
