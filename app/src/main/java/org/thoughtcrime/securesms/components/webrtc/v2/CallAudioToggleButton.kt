/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.webrtc.v2

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import org.signal.core.ui.compose.NightPreview
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.webrtc.AudioStateUpdater
import org.thoughtcrime.securesms.components.webrtc.ToggleButtonOutputState
import org.thoughtcrime.securesms.components.webrtc.WebRtcAudioDevice
import org.thoughtcrime.securesms.components.webrtc.WebRtcAudioOutput
import org.thoughtcrime.securesms.components.webrtc.WebRtcAudioPicker31
import org.thoughtcrime.securesms.light.rememberLightTintedPainter

private const val SHOW_PICKER_THRESHOLD = 3

/**
 * Remembered factory for [AudioOutputPickerController].
 *
 * This file used to also hold `CallAudioToggleButton`, the Material icon button that opened the
 * picker. It is gone: [LightCallChrome] draws the audio-output control now, and the only remaining
 * callers of the old button were its own `@Preview`s. The controller and the picker itself stay --
 * `CallControls` still calls `Sheet()` on it.
 */
@Composable
fun rememberAudioOutputPickerController(
  onSelectedDeviceChanged: (WebRtcAudioDevice) -> Unit,
  outputState: ToggleButtonOutputState
): AudioOutputPickerController {
  return remember(onSelectedDeviceChanged, outputState) {
    AudioOutputPickerController(
      onSelectedDeviceChanged,
      outputState
    )
  }
}

/**
 * Controller for the Audio picker which contains different state variables for choosing whether
 * or not to display the sheet.
 */
@Stable
class AudioOutputPickerController(
  private val onSelectedDeviceChanged: (WebRtcAudioDevice) -> Unit,
  val outputState: ToggleButtonOutputState
) {

  var displaySheet: Boolean by mutableStateOf(false)
    private set

  val willDisplayPicker: Boolean by derivedStateOf {
    outputState.availableDevices.size >= SHOW_PICKER_THRESHOLD || !outputState.availableDevices.contains(WebRtcAudioOutput.HANDSET)
  }

  private val newApiController = if (Build.VERSION.SDK_INT >= 31) WebRtcAudioPicker31(
    audioOutputChangedListener = onSelectedDeviceChanged,
    outputState = outputState,
    stateUpdater = object : AudioStateUpdater {
      override fun updateAudioOutputState(audioOutput: WebRtcAudioOutput) {
        outputState.setCurrentOutput(audioOutput)
        displaySheet = false
      }

      override fun hidePicker() {
        displaySheet = false
      }
    }
  ) else null

  fun show() {
    displaySheet = true
  }

  fun hide() {
    displaySheet = false
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun Sheet() {
    if (!displaySheet) {
      return
    }

    val isLegacy = Build.VERSION.SDK_INT < 31
    if (!willDisplayPicker) {
      LaunchedEffect(Unit) {
        displaySheet = false
        if (isLegacy) {
          onSelectedDeviceChanged(WebRtcAudioDevice(outputState.peekNext(), null))
        } else {
          newApiController!!.cycleToNextDevice()
        }
      }
      return
    }

    ModalBottomSheet(
      onDismissRequest = { displaySheet = false }
    ) {
      if (isLegacy) {
        LegacyAudioPickerContent(
          toggleButtonOutputState = outputState,
          onSelectedDeviceChanged = {
            displaySheet = false
            onSelectedDeviceChanged(WebRtcAudioDevice(it, null))
          }
        )
      } else {
        newApiController!!.Picker(threshold = SHOW_PICKER_THRESHOLD)
      }
    }
  }
}

/**
 * Picker for pre-api-31 devices.
 *
 * On API 31 and up the platform picker ([WebRtcAudioPicker31]) is used instead, so this is only
 * reached on older hardware -- but it is still reachable, so it is converted rather than deleted.
 * The row reads as a selection without a radio dot: on a panel with one foreground colour the dot
 * carried the "which one is current" signal, and a filled indicator on the left does it better than
 * an outline that differs only by a pixel of grey.
 */
@Composable
private fun LegacyAudioPickerContent(
  toggleButtonOutputState: ToggleButtonOutputState,
  onSelectedDeviceChanged: (WebRtcAudioOutput) -> Unit
) {
  LightText(
    text = stringResource(R.string.WebRtcAudioOutputToggle__audio_output),
    variant = LightTextVariant.Subheading,
    modifier = Modifier.padding(
      start = GUTTER_UNITS.gridUnitsAsDp(),
      end = GUTTER_UNITS.gridUnitsAsDp(),
      top = 2f.gridUnitsAsDp(),
      bottom = 1f.gridUnitsAsDp()
    )
  )

  LazyColumn(
    modifier = Modifier.background(LightThemeTokens.colors.background)
  ) {
    items(
      items = toggleButtonOutputState.availableDevices
    ) { item ->
      val selected = item == toggleButtonOutputState.currentDevice
      val icon = when (item) {
        WebRtcAudioOutput.HANDSET -> R.drawable.symbol_phone_speaker_outline_24
        WebRtcAudioOutput.SPEAKER -> R.drawable.symbol_speaker_outline_24
        WebRtcAudioOutput.BLUETOOTH_HEADSET -> R.drawable.symbol_speaker_bluetooth_outline_24
        WebRtcAudioOutput.WIRED_HEADSET -> R.drawable.symbol_headphones_outline_24
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .fillMaxWidth()
          .height(ROW_HEIGHT_UNITS.gridUnitsAsDp())
          .lightClickable(role = Role.RadioButton) { onSelectedDeviceChanged(item) }
          .semantics { this.selected = selected }
          .padding(horizontal = GUTTER_UNITS.gridUnitsAsDp())
      ) {
        Image(
          painter = painterResource(id = icon),
          contentDescription = null,
          colorFilter = ColorFilter.tint(LightThemeTokens.colors.content),
          modifier = Modifier.size(ICON_SIZE_UNITS.gridUnitsAsDp())
        )
        Spacer(modifier = Modifier.width(1f.gridUnitsAsDp()))
        // Weight rather than a trailing tick: the device name is the thing being chosen, and on the
        // LP3 "BLUETOOTH HEADSET" is wider than the row it sits in.
        LightText(
          text = stringResource(id = item.labelRes),
          variant = LightTextVariant.Copy,
          lighten = !selected,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f)
        )
        if (selected) {
          Image(
            painter = rememberLightTintedPainter(R.drawable.symbol_check_white_24),
            contentDescription = null,
            modifier = Modifier.size(ICON_SIZE_UNITS.gridUnitsAsDp())
          )
        }
      }
    }
  }
}

private const val ROW_HEIGHT_UNITS = 4f
private const val ICON_SIZE_UNITS = 2f
private const val GUTTER_UNITS = 2f

@NightPreview
@Composable
private fun LegacyAudioPickerContentPreview() {
  val outputState = remember {
    ToggleButtonOutputState().apply {
      isEarpieceAvailable = true
      isBluetoothHeadsetAvailable = true
      isWiredHeadsetAvailable = true
    }
  }

  Previews.BottomSheetContentPreview {
    LegacyAudioPickerContent(
      toggleButtonOutputState = outputState,
      onSelectedDeviceChanged = {}
    )
  }
}
