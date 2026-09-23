/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.link

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextAlign
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollBarPosition
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Dialogs
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.usernamelinks.QrCode
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.registration.ui.RegistrationViewModel
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationAction
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationBottomActions

/**
 * Presents a QR code the user's existing Signal device can scan to link this LP3 as a linked device.
 */
class RegisterLinkDeviceQrFragment : ComposeFragment() {

  private val sharedViewModel by activityViewModels<RegistrationViewModel>()
  private val viewModel: RegisterLinkDeviceQrViewModel by viewModels()

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
      viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        viewModel
          .state
          .mapNotNull { it.provisionMessage }
          .distinctUntilChanged()
          .collect { message ->
            withContext(Dispatchers.IO) {
              val result = sharedViewModel.registerAsLinkedDevice(requireContext().applicationContext, message)

              when (result) {
                RegisterLinkDeviceResult.Success -> Unit
                else -> viewModel.setRegisterAsLinkedDeviceError(result)
              }
            }
          }
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val state by viewModel.state.collectAsState()

    RegisterLinkDeviceQrScreen(
      state = state,
      onRetryQrCode = viewModel::restartProvisioningSocket,
      onErrorDismiss = viewModel::clearErrors,
      onCancel = { findNavController().popBackStack() }
    )
  }
}

@Composable
private fun RegisterLinkDeviceQrScreen(
  state: RegisterLinkDeviceQrViewModel.RegisterLinkDeviceState,
  onRetryQrCode: () -> Unit = {},
  onErrorDismiss: () -> Unit = {},
  onCancel: () -> Unit = {}
) {
  MollyLightTheme {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(LightThemeTokens.colors.background)
    ) {
      LightTopBar(
        leftButton = LightBarButton.LightIcon(
          icon = LightIcons.BACK,
          onClick = onCancel,
          contentDescription = stringResource(android.R.string.cancel)
        ),
        center = LightTopBarCenter.Text(stringResource(R.string.RegisterLinkDeviceQrFragment__link_device)),
        modifier = Modifier.statusBarsPadding()
      )

      LightScrollView(
        scrollBarPosition = LightScrollBarPosition.Outside,
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 1f.gridUnitsAsDp())
        ) {
          // QR code on white background — deliberate light inner surface for scanner contrast.
          Box(
            modifier = Modifier
              .fillMaxWidth(0.7f)
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
                is RegisterLinkDeviceQrViewModel.QrState.Loaded -> {
                  QrCode(
                    data = qrState.qrData,
                    foregroundColor = Color.Black,
                    modifier = Modifier.fillMaxSize()
                  )
                }

                RegisterLinkDeviceQrViewModel.QrState.Loading -> {
                  CircularProgressIndicator(
                    modifier = Modifier.size(3f.gridUnitsAsDp()),
                    color = Color.Black
                  )
                }

                is RegisterLinkDeviceQrViewModel.QrState.Scanned,
                RegisterLinkDeviceQrViewModel.QrState.Failed -> {
                  Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(1f.gridUnitsAsDp())
                  ) {
                    val status = if (qrState is RegisterLinkDeviceQrViewModel.QrState.Scanned) {
                      stringResource(R.string.RegisterLinkDeviceQrFragment__scanned)
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

          Spacer(modifier = Modifier.height(1.5f.gridUnitsAsDp()))

          InstructionRow(
            icon = SignalIcons.Settings.painter,
            instruction = stringResource(R.string.RegisterLinkDeviceQrFragment__open_signal_settings)
          )
          InstructionRow(
            icon = SignalIcons.Link.painter,
            instruction = stringResource(R.string.RegisterLinkDeviceQrFragment__tap_linked_devices)
          )
          InstructionRow(
            icon = SignalIcons.QrCode.painter,
            instruction = stringResource(R.string.RegisterLinkDeviceQrFragment__tap_link_and_scan)
          )
        }
      }

      RegistrationBottomActions(
        center = RegistrationAction(
          label = stringResource(android.R.string.cancel),
          onClick = onCancel
        )
      )
    }

    if (state.isRegistering) {
      Dialogs.IndeterminateProgressDialog()
    } else if (state.showProvisioningError) {
      Dialogs.SimpleMessageDialog(
        message = stringResource(R.string.RegisterLinkDeviceQrFragment__provisioning_error),
        onDismiss = onErrorDismiss,
        dismiss = stringResource(android.R.string.ok)
      )
    } else if (state.registrationErrorResult != null) {
      val message = when (state.registrationErrorResult) {
        RegisterLinkDeviceResult.IncorrectVerification -> stringResource(R.string.RegisterLinkDeviceQrFragment__error_incorrect_verification)
        RegisterLinkDeviceResult.InvalidRequest -> stringResource(R.string.RegisterLinkDeviceQrFragment__error_invalid_request)
        RegisterLinkDeviceResult.MaxLinkedDevices -> stringResource(R.string.RegisterLinkDeviceQrFragment__error_max_devices)
        RegisterLinkDeviceResult.MissingCapability -> stringResource(R.string.RegisterLinkDeviceQrFragment__error_missing_capability)
        is RegisterLinkDeviceResult.NetworkException -> stringResource(R.string.RegisterLinkDeviceQrFragment__error_network)
        is RegisterLinkDeviceResult.RateLimited -> stringResource(R.string.RegisterLinkDeviceQrFragment__error_rate_limited)
        is RegisterLinkDeviceResult.UnexpectedException -> stringResource(R.string.RegisterLinkDeviceQrFragment__error_unexpected)
        RegisterLinkDeviceResult.Success -> throw IllegalStateException()
      }
      Dialogs.SimpleMessageDialog(
        message = message,
        onDismiss = onErrorDismiss,
        dismiss = stringResource(android.R.string.ok)
      )
    }
  }
}

@Composable
private fun InstructionRow(
  icon: Painter,
  instruction: String
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 0.75f.gridUnitsAsDp())
  ) {
    Icon(
      painter = icon,
      contentDescription = null,
      tint = LightThemeTokens.colors.contentSecondary,
      modifier = Modifier.size(1.5f.gridUnitsAsDp())
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
private fun RegisterLinkDeviceQrScreenPreview() {
  Previews.Preview {
    RegisterLinkDeviceQrScreen(
      state = RegisterLinkDeviceQrViewModel.RegisterLinkDeviceState()
    )
  }
}
