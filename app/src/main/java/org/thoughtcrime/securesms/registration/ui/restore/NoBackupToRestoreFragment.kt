/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.registration.proto.RegistrationProvisionMessage
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationAction
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationBottomActions
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import org.thoughtcrime.securesms.util.viewModel
import kotlin.getValue
import org.signal.core.ui.R as CoreUiR

/**
 * Shown when the old device is iOS and they are trying to transfer/restore on Android without a Signal Backup.
 */
class NoBackupToRestoreFragment : ComposeFragment() {

  private val sharedViewModel by activityViewModels<RegistrationViewModel>()
  private val viewModel by viewModel {
    NoBackupToRestoreViewModel(sharedViewModel.registrationProvisioningMessage!!)
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
        sharedViewModel
          .state
          .map { it.registerAccountError }
          .filterNotNull()
          .collect {
            sharedViewModel.registerAccountErrorShown()
            viewModel.handleRegistrationFailure(it)
          }
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsState()

    NoBackupToRestoreContent(
      state = state,
      onSkipRestore = {
        viewModel.skipRestoreAndRegister()

        val message = viewModel.state.value.provisioningMessage
        sharedViewModel.registerWithBackupKey(
          context = requireContext(),
          backupKey = message.accountEntropyPool,
          e164 = message.e164,
          pin = message.pin,
          aciIdentityKeyPair = message.aciIdentityKeyPair,
          pniIdentityKeyPair = message.pniIdentityKeyPair
        )
      },
      onCancel = {
        sharedViewModel.registrationProvisioningMessage = null
        findNavController().safeNavigate(NoBackupToRestoreFragmentDirections.restartRegistrationFlow())
      },
      onRegistrationErrorDismiss = viewModel::clearRegistrationError
    )
  }
}

@Composable
private fun NoBackupToRestoreContent(
  state: NoBackupToRestoreViewModel.NoBackupToRestoreState,
  onSkipRestore: () -> Unit = {},
  onCancel: () -> Unit = {},
  onRegistrationErrorDismiss: () -> Unit = {}
) {
  RegistrationScreen(
    title = stringResource(id = R.string.NoBackupToRestore_title),
    subtitle = stringResource(id = R.string.NoBackupToRestore_subtitle),
    bottomContent = {
      RegistrationBottomActions(
        left = RegistrationAction(
          label = stringResource(id = android.R.string.cancel),
          onClick = onCancel
        ),
        right = RegistrationAction(
          label = stringResource(id = R.string.NoBackupToRestore_skip_restore),
          onClick = onSkipRestore
        )
      )
    }
  ) {
    Column(
      verticalArrangement = Arrangement.spacedBy(1.5f.gridUnitsAsDp()),
      modifier = Modifier.padding(horizontal = 2f.gridUnitsAsDp())
    ) {
      StepRow(icon = painterResource(CoreUiR.drawable.symbol_device_phone_24), text = stringResource(id = R.string.NoBackupToRestore_step1))

      StepRow(icon = SignalIcons.Backup.painter, text = stringResource(id = R.string.NoBackupToRestore_step2))

      StepRow(icon = SignalIcons.CheckCircle.painter, text = stringResource(id = R.string.NoBackupToRestore_step3))
    }

    if (state.isRegistering) {
      Dialogs.IndeterminateProgressDialog()
    } else if (state.showRegistrationError) {
      val message = when (state.registerAccountResult) {
        is RegisterAccountResult.IncorrectRecoveryPassword -> stringResource(R.string.RestoreViaQr_registration_error)
        is RegisterAccountResult.RateLimited -> stringResource(R.string.RegistrationActivity_you_have_made_too_many_attempts_please_try_again_later)
        else -> stringResource(R.string.RegistrationActivity_error_connecting_to_service)
      }

      Dialogs.SimpleMessageDialog(
        message = message,
        onDismiss = onRegistrationErrorDismiss,
        dismiss = stringResource(android.R.string.ok)
      )
    }
  }
}

@Composable
private fun StepRow(
  icon: Painter,
  text: String
) {
  Row(
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      painter = icon,
      tint = LightThemeTokens.colors.contentSecondary,
      contentDescription = null
    )

    Spacer(modifier = Modifier.width(1f.gridUnitsAsDp()))

    LightText(
      text = text,
      variant = LightTextVariant.Paragraph,
      lighten = true
    )
  }
}

@DayNightPreviews
@Composable
private fun NoBackupToRestoreContentPreview() {
  Previews.Preview {
    NoBackupToRestoreContent(state = NoBackupToRestoreViewModel.NoBackupToRestoreState(provisioningMessage = RegistrationProvisionMessage()))
  }
}
