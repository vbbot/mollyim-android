/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.phonenumber

import android.content.Intent
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollBarPosition
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextField
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.designVerticalPxToDp
import com.thelightphone.sdk.ui.designVerticalPxToSp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogActivity
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationAction
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationBottomActions

/** The LightTextField / LightTextInputEditor underline: 3 design px, 0.5 gu below the value, 80% wide. */
private const val UNDERLINE_THICKNESS_PX = 3f
private const val UNDERLINE_GAP_GRID_UNITS = 0.5f
private const val UNDERLINE_WIDTH_FRACTION = 0.8f

/** Same counts as RegistrationViewDelegate.setDebugLogSubmitMultiTapView, which the upstream header used. */
private const val DEBUG_TAP_TARGET = 8
private const val DEBUG_TAP_ANNOUNCE = 4

/**
 * The phone number step as a Light form: a Country value row and a display-only number field that
 * opens [PhoneNumberEditor], a full-screen editor over the form. The fragment owns all state; this
 * only renders it and reports taps.
 */
@Composable
fun EnterPhoneNumberScreen(
  countryEmoji: String?,
  countryName: String,
  countrySelected: Boolean,
  phoneNumber: String,
  editorOpen: Boolean,
  nextEnabled: Boolean,
  inProgress: Boolean,
  showCancel: Boolean,
  controlsEnabled: Boolean,
  onBackClicked: () -> Unit,
  onCountryClicked: () -> Unit,
  onPhoneNumberClicked: () -> Unit,
  onEditorBack: () -> Unit,
  onEditorDone: (String) -> Unit,
  onNextClicked: () -> Unit,
  onCancelClicked: () -> Unit,
  onProxyClicked: () -> Unit,
) {
  MollyLightTheme {
    val colors = LightThemeTokens.colors
    val title = stringResource(R.string.RegistrationActivity_phone_number)
    val onTitleTapped = rememberDebugLogTapHandler()

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(colors.background),
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
          leftButton = LightBarButton.LightIcon(
            icon = LightIcons.BACK,
            onClick = onBackClicked,
            contentDescription = stringResource(R.string.EnterPhoneNumberFragment__back_to_previous_step),
          ),
          // The title keeps upstream's eight-tap debug log. The bar's buttons sit above the center in
          // their own layer, so the taps cannot take BACK or settings.
          center = LightTopBarCenter.Text(text = title, onClick = onTitleTapped),
          rightButton = LightBarButton.LightIcon(
            icon = LightIcons.SETTINGS,
            onClick = onProxyClicked,
            contentDescription = stringResource(R.string.RegistrationActivity_network_settings),
          ),
          modifier = Modifier.statusBarsPadding(),
        )

        LightScrollView(
          scrollBarPosition = LightScrollBarPosition.Outside,
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 2f.gridUnitsAsDp()),
          ) {
            CountryRow(
              emoji = countryEmoji,
              name = countryName,
              selected = countrySelected,
              enabled = controlsEnabled,
              onClick = onCountryClicked,
            )

            LightTextField(
              label = title,
              value = phoneNumber,
              placeholder = stringResource(R.string.EnterPhoneNumberFragment__enter_your_number),
              onClick = { if (controlsEnabled) onPhoneNumberClicked() },
              modifier = Modifier.padding(vertical = 0.75f.gridUnitsAsDp()),
            )
          }
        }

        RegistrationBottomActions(
          left = if (showCancel) RegistrationAction(
            label = stringResource(android.R.string.cancel).uppercase(),
            onClick = onCancelClicked,
          ) else null,
          right = RegistrationAction(
            label = if (inProgress) "…" else stringResource(R.string.RegistrationActivity_next).uppercase(),
            onClick = if (inProgress) ({}) else onNextClicked,
            enabled = !inProgress && nextEnabled,
          ),
          modifier = Modifier.navigationBarsPadding(),
        )
      }

      if (editorOpen) {
        PhoneNumberEditor(
          title = title,
          initialNumber = phoneNumber,
          onBack = onEditorBack,
          onDone = onEditorDone,
        )
      }
    }
  }
}

/** chats' value-row grammar: a Detail label over the value at Heading. Opens the country picker. */
@Composable
private fun CountryRow(
  emoji: String?,
  name: String,
  selected: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .lightClickable(enabled = enabled, onClick = onClick)
      .padding(vertical = 0.75f.gridUnitsAsDp()),
  ) {
    LightText(
      text = stringResource(R.string.EnterPhoneNumberFragment__country),
      variant = LightTextVariant.Detail,
      lighten = !enabled,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
      // Flags are colour emoji from a fallback font; only show one the device can actually draw.
      val flag = emoji.orEmpty()
      val showFlag = remember(flag) { flag.isNotEmpty() && Paint().hasGlyph(flag) }
      if (showFlag) {
        LightText(text = flag, variant = LightTextVariant.Heading, maxLines = 1)
        Spacer(modifier = Modifier.width(0.5f.gridUnitsAsDp()))
      }
      LightText(
        text = name,
        variant = LightTextVariant.Heading,
        lighten = !enabled || !selected,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 * Full-screen editor for the whole international number, in the manner of the Light chat composer
 * and chats' FieldEditorScreen: BACK discards, DONE (or the keyboard's Done) commits. It is drawn over
 * the form, opaque and touch-swallowing, with the field just under the bar so the system keyboard
 * can take the bottom of the screen.
 */
@Composable
private fun PhoneNumberEditor(
  title: String,
  initialNumber: String,
  onBack: () -> Unit,
  onDone: (String) -> Unit,
) {
  val colors = LightThemeTokens.colors
  val focusRequester = remember { FocusRequester() }
  val keyboardController = LocalSoftwareKeyboardController.current

  // A TextFieldValue rather than a String: reformatting changes the text's length, and only an explicit
  // selection keeps the cursor after the digit just typed.
  var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
    mutableStateOf(TextFieldValue(initialNumber, selection = TextRange(initialNumber.length)))
  }

  val commit = {
    keyboardController?.hide()
    onDone(value.text)
  }
  val discard = {
    keyboardController?.hide()
    onBack()
  }

  LaunchedEffect(Unit) {
    focusRequester.requestFocus()
    keyboardController?.show()
  }

  val baseStyle = LightThemeTokens.typography.heading
  val textStyle = baseStyle.copy(
    fontSize = baseStyle.fontSize.scaled(),
    lineHeight = baseStyle.lineHeight.scaled(),
    letterSpacing = baseStyle.letterSpacing.scaled(),
    color = colors.content,
  )

  Column(
    modifier = Modifier
      .fillMaxSize()
      // Opaque and touch-swallowing, as in LightComposer: the form is still laid out underneath and
      // would otherwise show through and take taps. The bar and the field are children, so they see
      // each event first.
      .background(colors.background)
      .pointerInput(Unit) {
        awaitEachGesture {
          awaitFirstDown(requireUnconsumed = false).consume()
          while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
            if (event.changes.none { it.pressed }) break
          }
        }
      },
  ) {
    LightTopBar(
      leftButton = LightBarButton.LightIcon(
        icon = LightIcons.BACK,
        onClick = discard,
        contentDescription = stringResource(R.string.EnterPhoneNumberFragment__close_without_saving),
      ),
      center = LightTopBarCenter.Text(text = title),
      rightButton = LightBarButton.Text(
        text = stringResource(R.string.EnterPhoneNumberFragment__done),
        onClick = commit,
      ),
      modifier = Modifier.statusBarsPadding(),
    )

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 2f.gridUnitsAsDp())
        .padding(top = 1f.gridUnitsAsDp()),
    ) {
      BasicTextField(
        value = value,
        onValueChange = { edited ->
          val edit = PhoneNumberEntry.reformat(previous = value.text, raw = edited.text, cursor = edited.selection.end)
          value = TextFieldValue(edit.text, selection = TextRange(edit.cursor))
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        textStyle = textStyle,
        cursorBrush = SolidColor(colors.content),
        modifier = Modifier
          .fillMaxWidth()
          .focusRequester(focusRequester),
        decorationBox = { innerTextField ->
          Box {
            if (value.text.isEmpty()) {
              LightText(
                text = stringResource(R.string.EnterPhoneNumberFragment__country_code_and_number),
                variant = LightTextVariant.Heading,
                lighten = true,
                maxLines = 1,
              )
            }
            innerTextField()
          }
        },
      )
      Spacer(modifier = Modifier.height(UNDERLINE_GAP_GRID_UNITS.gridUnitsAsDp()))
      Spacer(
        modifier = Modifier
          .fillMaxWidth(UNDERLINE_WIDTH_FRACTION)
          .height(UNDERLINE_THICKNESS_PX.designVerticalPxToDp())
          .background(colors.content),
      )
    }
  }
}

/** Counts title taps; from the fourth a toast counts down, and the eighth opens the debug log. */
@Composable
private fun rememberDebugLogTapHandler(): () -> Unit {
  val context = LocalContext.current
  var taps by remember { mutableIntStateOf(0) }
  var previousToast by remember { mutableStateOf<Toast?>(null) }

  return {
    taps++
    if (taps >= DEBUG_TAP_TARGET) {
      context.startActivity(Intent(context, SubmitDebugLogActivity::class.java))
      previousToast?.cancel()
      previousToast = null
    } else if (taps >= DEBUG_TAP_ANNOUNCE) {
      val remaining = DEBUG_TAP_TARGET - taps
      previousToast?.cancel()
      previousToast = Toast.makeText(
        context,
        context.resources.getQuantityString(R.plurals.RegistrationActivity_debug_log_hint, remaining, remaining),
        Toast.LENGTH_SHORT
      ).apply { show() }
    }
  }
}

/** Editable text does not go through LightText, so the screen-height scaling is applied by hand. */
@Composable
private fun TextUnit.scaled(): TextUnit =
  if (this == TextUnit.Unspecified) this else value.designVerticalPxToSp()
