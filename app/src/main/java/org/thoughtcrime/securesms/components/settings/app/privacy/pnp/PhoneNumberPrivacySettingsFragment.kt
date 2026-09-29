package org.thoughtcrime.securesms.components.settings.app.privacy.pnp

import android.content.res.Configuration
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.Snackbars
import org.signal.core.ui.compose.showSnackbar
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.compose.StatusBarColorNestedScrollConnection
import org.thoughtcrime.securesms.light.LightConfirmationDialog
import org.thoughtcrime.securesms.light.LightSettingsDivider
import org.thoughtcrime.securesms.light.LightSettingsRow
import org.thoughtcrime.securesms.light.LightSettingsScreen
import org.thoughtcrime.securesms.light.LightSettingsSection
import org.thoughtcrime.securesms.light.MollyLightTheme

class PhoneNumberPrivacySettingsFragment : ComposeFragment() {

  private val viewModel: PhoneNumberPrivacySettingsViewModel by viewModels()
  private lateinit var statusBarNestedScrollConnection: StatusBarColorNestedScrollConnection

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    statusBarNestedScrollConnection = StatusBarColorNestedScrollConnection(requireActivity())
  }

  override fun onResume() {
    super.onResume()
    statusBarNestedScrollConnection.setColorImmediate()
  }

  @Composable
  override fun FragmentContent() {
    val state: PhoneNumberPrivacySettingsState by viewModel.state
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMessage = stringResource(id = R.string.PhoneNumberPrivacySettingsFragment__to_change_this_setting)
    var showNobodyCanFindMeConfirmation by rememberSaveable { mutableStateOf(false) }

    Screen(
      state = state,
      snackbarHostState = snackbarHostState,
      onNavigationClick = { findNavController().popBackStack() },
      statusBarNestedScrollConnection = statusBarNestedScrollConnection,
      onEveryoneCanSeeMyNumberClicked = viewModel::setEveryoneCanSeeMyNumber,
      onNobodyCanSeeMyNumberClicked = viewModel::setNobodyCanSeeMyNumber,
      onEveryoneCanFindMeByNumberClicked = viewModel::setEveryoneCanFindMeByMyNumber,
      onNobodyCanFindMeByNumberClicked = {
        if (!state.phoneNumberSharing) {
          showNobodyCanFindMeConfirmation = true
        } else {
          lifecycleScope.launch {
            snackbarHostState.showSnackbar(
              message = snackbarMessage,
              duration = Snackbars.Duration.SHORT
            )
          }
        }
      }
    )

    if (showNobodyCanFindMeConfirmation) {
      LightConfirmationDialog(
        title = stringResource(R.string.PhoneNumberPrivacySettingsFragment__nobody_can_find_me_warning_title),
        body = stringResource(R.string.PhoneNumberPrivacySettingsFragment__nobody_can_find_me_warning_message),
        confirmLabel = stringResource(android.R.string.ok),
        dismissLabel = stringResource(R.string.PhoneNumberPrivacySettingsFragment__cancel),
        onConfirm = viewModel::setNobodyCanFindMeByMyNumber,
        onDismiss = { showNobodyCanFindMeConfirmation = false }
      )
    }
  }
}

@Composable
private fun Screen(
  state: PhoneNumberPrivacySettingsState,
  snackbarHostState: SnackbarHostState = SnackbarHostState(),
  onNavigationClick: () -> Unit = {},
  statusBarNestedScrollConnection: StatusBarColorNestedScrollConnection? = null,
  onEveryoneCanSeeMyNumberClicked: () -> Unit = {},
  onNobodyCanSeeMyNumberClicked: () -> Unit = {},
  onEveryoneCanFindMeByNumberClicked: () -> Unit = {},
  onNobodyCanFindMeByNumberClicked: () -> Unit = {}
) {
  MollyLightTheme {
    Box {
      LightSettingsScreen(
        title = stringResource(id = R.string.preferences_app_protection__phone_number),
        onBack = onNavigationClick,
        modifier = statusBarNestedScrollConnection?.let { Modifier.nestedScroll(it) } ?: Modifier
      ) {
        LightSettingsSection(
          stringResource(id = R.string.PhoneNumberPrivacySettingsFragment_who_can_see_my_number_heading)
        )
        LightPrivacyRadioRow(
          selected = state.phoneNumberSharing,
          title = stringResource(id = R.string.PhoneNumberPrivacy_everyone),
          onClick = onEveryoneCanSeeMyNumberClicked
        )
        LightPrivacyRadioRow(
          selected = !state.phoneNumberSharing,
          title = stringResource(id = R.string.PhoneNumberPrivacy_nobody),
          onClick = onNobodyCanSeeMyNumberClicked
        )
        LightSettingsRow(
          title = stringResource(
            id = if (state.phoneNumberSharing) {
              R.string.PhoneNumberPrivacySettingsFragment_sharing_on_description
            } else if (state.discoverableByPhoneNumber) {
              R.string.PhoneNumberPrivacySettingsFragment_sharing_off_discovery_on_description
            } else {
              R.string.PhoneNumberPrivacySettingsFragment_sharing_off_discovery_off_description
            }
          ),
          onClick = null
        )

        LightSettingsDivider()
        LightSettingsSection(
          stringResource(id = R.string.PhoneNumberPrivacySettingsFragment_who_can_find_me_by_number_heading)
        )
        LightPrivacyRadioRow(
          selected = state.discoverableByPhoneNumber,
          title = stringResource(id = R.string.PhoneNumberPrivacy_everyone),
          onClick = onEveryoneCanFindMeByNumberClicked
        )
        LightPrivacyRadioRow(
          enabled = !state.phoneNumberSharing,
          selected = !state.discoverableByPhoneNumber,
          title = stringResource(id = R.string.PhoneNumberPrivacy_nobody),
          onClick = onNobodyCanFindMeByNumberClicked,
          onDisabledClick = onNobodyCanFindMeByNumberClicked
        )
        LightSettingsRow(
          title = stringResource(
            id = if (state.discoverableByPhoneNumber) {
              R.string.PhoneNumberPrivacySettingsFragment_discovery_on_description
            } else {
              R.string.PhoneNumberPrivacySettingsFragment_discovery_off_description
            }
          ),
          onClick = null
        )
      }

      SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter)
      )
    }
  }
}

@Composable
private fun LightPrivacyRadioRow(
  selected: Boolean,
  title: CharSequence,
  onClick: () -> Unit,
  enabled: Boolean = true,
  onDisabledClick: (() -> Unit)? = null
) {
  LightSettingsRow(
    title = title,
    enabled = enabled,
    onClick = onClick,
    onDisabledClick = onDisabledClick,
    role = Role.RadioButton,
    modifier = Modifier.semantics { this.selected = selected },
    trailing = {
      LightText(
        text = if (selected) "●" else "○",
        variant = LightTextVariant.Copy,
        lighten = !enabled
      )
    }
  )
}

@Preview(name = "Light Theme", group = "Screen", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "Screen", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScreenPreviewSharingAndDiscoverable() {
  Previews.Preview {
    Screen(
      state = PhoneNumberPrivacySettingsState(
        phoneNumberSharing = true,
        discoverableByPhoneNumber = true
      )
    )
  }
}

@Preview(name = "Light Theme", group = "Screen", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "Screen", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScreenPreviewNotSharingDiscoverable() {
  Previews.Preview {
    Screen(
      state = PhoneNumberPrivacySettingsState(
        phoneNumberSharing = false,
        discoverableByPhoneNumber = true
      )
    )
  }
}

@Preview(name = "Light Theme", group = "Screen", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "Dark Theme", group = "Screen", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScreenPreviewNotSharingNotDiscoverable() {
  Previews.Preview {
    Screen(
      state = PhoneNumberPrivacySettingsState(
        phoneNumberSharing = false,
        discoverableByPhoneNumber = false
      )
    )
  }
}
