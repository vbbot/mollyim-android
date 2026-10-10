/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.signal.core.ui.compose.horizontalGutters
import org.signal.registration.proto.RegistrationProvisionMessage
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCode
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCodeData
import org.thoughtcrime.securesms.registration.data.network.RegisterAccountResult
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationAction
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationBack
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationBottomActions
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreenSubtitle
import org.thoughtcrime.securesms.util.navigation.safeNavigate
import java.util.Locale

/**
 * Show QR code on new device to allow registration and restore via old device.
 */
class RestoreViaQrFragment : ComposeFragment() {

  private val sharedViewModel by activityViewModels<RegistrationViewModel>()
  private val viewModel: RestoreViaQrViewModel by viewModels()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
      override fun onResume(owner: LifecycleOwner) {
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }

      override fun onPause(owner: LifecycleOwner) {
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
      }
    })

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
        viewModel
          .state
          .mapNotNull { it.provisioningMessage }
          .distinctUntilChanged()
          .collect { message ->
            if (message.platform == RegistrationProvisionMessage.Platform.ANDROID || message.tier != null) {
              sharedViewModel.registerWithBackupKey(requireContext(), message.accountEntropyPool, message.e164, message.pin, message.aciIdentityKeyPair, message.pniIdentityKeyPair)
            } else {
              sharedViewModel.registrationProvisioningMessage = message
              findNavController().safeNavigate(RestoreViaQrFragmentDirections.goToNoBackupToRestore())
            }
          }
      }
    }

    viewLifecycleOwner.lifecycleScope.launch {
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.CREATED) {
        viewModel
          .state
          .mapNotNull { it.registerAccountResult }
          .filter { it !is RegisterAccountResult.Success }
          .distinctUntilChanged()
          .collect { result ->
            when (result) {
              is RegisterAccountResult.AttemptsExhausted -> {
                findNavController().safeNavigate(RestoreViaQrFragmentDirections.goToAccountLocked())
              }

              else -> Unit
            }
          }
      }
    }

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

    RestoreViaQrScreen(
      state = state,
      onRetryQrCode = viewModel::restart,
      onRegistrationErrorDismiss = viewModel::clearRegistrationError,
      onCancel = { findNavController().popBackStack() },
      onUseProxy = { findNavController().safeNavigate(RestoreViaQrFragmentDirections.goToEditProxy()) }
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RestoreViaQrScreen(
  state: RestoreViaQrViewModel.RestoreViaQrState,
  onRetryQrCode: () -> Unit = {},
  onRegistrationErrorDismiss: () -> Unit = {},
  onCancel: () -> Unit = {},
  onUseProxy: () -> Unit = {}
) {
  RegistrationScreen(
    title = stringResource(R.string.RestoreViaQr_top_bar_title),
    // Cancel already pops back to the welcome screen, which is exactly where back goes.
    back = RegistrationBack(
      contentDescription = stringResource(R.string.RegistrationScreen__back_to_welcome),
      onBack = onCancel
    ),
    rightButton = LightBarButton.Text(
      text = stringResource(R.string.preferences_proxy).uppercase(Locale.getDefault()),
      contentDescription = stringResource(R.string.preferences_use_proxy),
      onClick = onUseProxy
    ),
    topContent = {
      // The full instruction no longer fits the top bar, so it leads the body instead.
      RegistrationScreenSubtitle(AnnotatedString(stringResource(R.string.RestoreViaQr_title)))
    },
    bottomContent = {
      RegistrationBottomActions(
        center = RegistrationAction(
          label = stringResource(android.R.string.cancel),
          onClick = onCancel
        )
      )
    }
  ) {
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(space = 2f.gridUnitsAsDp(), alignment = Alignment.CenterHorizontally),
      verticalArrangement = Arrangement.spacedBy(space = 1.5f.gridUnitsAsDp()),
      modifier = Modifier
        .fillMaxWidth()
        .horizontalGutters()
    ) {
      Box(
        modifier = Modifier
          .widthIn(160.dp, 240.dp)
          .aspectRatio(1f)
          .background(Color.White)
          .padding(1f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center
      ) {
        AnimatedContent(
          targetState = state.qrState,
          contentKey = { it::class },
          contentAlignment = Alignment.Center,
          label = "qr-code-progress",
          modifier = Modifier.fillMaxSize()
        ) { qrState ->
          when (qrState) {
            is RestoreViaQrViewModel.QrState.Loaded -> {
              QrCode(
                data = qrState.qrData,
                foregroundColor = Color.Black,
                modifier = Modifier.fillMaxSize()
              )
            }

            RestoreViaQrViewModel.QrState.Loading -> {
              CircularProgressIndicator(
                modifier = Modifier.size(3f.gridUnitsAsDp()),
                color = Color.Black
              )
            }

            is RestoreViaQrViewModel.QrState.Scanned,
            RestoreViaQrViewModel.QrState.Failed -> {
              Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                val status = if (qrState is RestoreViaQrViewModel.QrState.Scanned) {
                  stringResource(R.string.RestoreViaQr_qr_code_scanned)
                } else {
                  stringResource(R.string.RestoreViaQr_qr_code_error)
                }

                LightText(
                  text = status,
                  variant = LightTextVariant.Detail,
                  align = TextAlign.Center,
                  color = Color.Black
                )

                Spacer(modifier = Modifier.height(0.5f.gridUnitsAsDp()))

                LightText(
                  text = stringResource(R.string.RestoreViaQr_retry),
                  variant = LightTextVariant.Button,
                  color = Color.Black,
                  modifier = Modifier
                    .lightClickable(onClick = onRetryQrCode)
                    .padding(0.5f.gridUnitsAsDp())
                )
              }
            }
          }
        }
      }

      Column(
        modifier = Modifier
          .align(alignment = Alignment.CenterVertically)
          .widthIn(160.dp, 320.dp)
      ) {
        InstructionRow(
          icon = SignalIcons.Phone.painter,
          instruction = stringResource(R.string.RestoreViaQr_instruction_1)
        )

        InstructionRow(
          icon = SignalIcons.Camera.painter,
          instruction = stringResource(R.string.RestoreViaQr_instruction_2)
        )

        InstructionRow(
          icon = SignalIcons.QrCode.painter,
          instruction = stringResource(R.string.RestoreViaQr_instruction_3)
        )
      }
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

@DayNightPreviews
@Composable
private fun RestoreViaQrScreenPreview() {
  Previews.Preview {
    RestoreViaQrScreen(
      state = RestoreViaQrViewModel.RestoreViaQrState(
        qrState = RestoreViaQrViewModel.QrState.Loaded(
          QrCodeData.forData("sgnl://rereg?uuid=asdfasdfasdfasdfasdfasdf&pub_key=asdfasdfasdfSDFSsdfsdfSDFSDffd", false)
        )
      )
    )
  }
}

@DayNightPreviews
@Composable
private fun RestoreViaQrScreenLoadingPreview() {
  Previews.Preview {
    RestoreViaQrScreen(
      state = RestoreViaQrViewModel.RestoreViaQrState(qrState = RestoreViaQrViewModel.QrState.Loading)
    )
  }
}

@DayNightPreviews
@Composable
private fun RestoreViaQrScreenFailurePreview() {
  Previews.Preview {
    RestoreViaQrScreen(
      state = RestoreViaQrViewModel.RestoreViaQrState(qrState = RestoreViaQrViewModel.QrState.Failed)
    )
  }
}

@DayNightPreviews
@Composable
private fun RestoreViaQrScreenScannedPreview() {
  Previews.Preview {
    RestoreViaQrScreen(
      state = RestoreViaQrViewModel.RestoreViaQrState(qrState = RestoreViaQrViewModel.QrState.Scanned)
    )
  }
}

@DayNightPreviews
@Composable
private fun RestoreViaQrScreenRegisteringPreview() {
  Previews.Preview {
    RestoreViaQrScreen(
      state = RestoreViaQrViewModel.RestoreViaQrState(isRegistering = true, qrState = RestoreViaQrViewModel.QrState.Scanned)
    )
  }
}

@DayNightPreviews
@Composable
private fun RestoreViaQrScreenRegistrationFailedPreview() {
  Previews.Preview {
    RestoreViaQrScreen(
      state = RestoreViaQrViewModel.RestoreViaQrState(isRegistering = false, showRegistrationError = true, qrState = RestoreViaQrViewModel.QrState.Scanned)
    )
  }
}

@Composable
private fun InstructionRow(
  icon: Painter,
  instruction: String
) {
  Row(
    modifier = Modifier.padding(vertical = 0.75f.gridUnitsAsDp())
  ) {
    Icon(
      painter = icon,
      contentDescription = null,
      tint = LightThemeTokens.colors.contentSecondary
    )

    Spacer(modifier = Modifier.width(1f.gridUnitsAsDp()))

    LightText(
      text = instruction,
      variant = LightTextVariant.Paragraph,
      lighten = true
    )
  }
}

@DayNightPreviews
@Composable
private fun InstructionRowPreview() {
  Previews.Preview {
    InstructionRow(
      icon = SignalIcons.Phone.painter,
      instruction = "Instruction!"
    )
  }
}
