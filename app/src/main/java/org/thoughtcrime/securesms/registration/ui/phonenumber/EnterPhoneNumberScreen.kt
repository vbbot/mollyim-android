/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.phonenumber

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollBarPosition
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
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
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationAction
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationBottomActions

@Composable
fun EnterPhoneNumberScreen(
  countryEmoji: String?,
  countryName: String,
  countryCode: String,
  phoneNumber: String,
  nextEnabled: Boolean,
  inProgress: Boolean,
  showCancel: Boolean,
  controlsEnabled: Boolean,
  onBackClicked: () -> Unit,
  onCountryClicked: () -> Unit,
  onCountryCodeChanged: (String) -> Unit,
  onPhoneNumberChanged: (String) -> Unit,
  onNextClicked: () -> Unit,
  onCancelClicked: () -> Unit,
  onProxyClicked: () -> Unit,
) {
  MollyLightTheme {
    val colors = LightThemeTokens.colors
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(colors.background),
    ) {
      LightTopBar(
        leftButton = LightBarButton.LightIcon(
          icon = LightIcons.BACK,
          onClick = onBackClicked,
          contentDescription = stringResource(android.R.string.cancel),
        ),
        center = LightTopBarCenter.Text(
          stringResource(R.string.RegistrationActivity_phone_number).uppercase(),
        ),
        rightButton = LightBarButton.Text(
          text = "PROXY",
          onClick = onProxyClicked,
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
            .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 2f.gridUnitsAsDp()),
        ) {
          CountryPickerRow(
            emoji = countryEmoji,
            name = countryName,
            enabled = controlsEnabled,
            onClick = onCountryClicked,
          )

          Spacer(modifier = Modifier.height(2f.gridUnitsAsDp()))

          PhoneInputRow(
            countryCode = countryCode,
            phoneNumber = phoneNumber,
            onCountryCodeChanged = onCountryCodeChanged,
            onPhoneNumberChanged = onPhoneNumberChanged,
            enabled = controlsEnabled,
            onDone = onNextClicked,
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
      )
    }
  }
}

@Composable
private fun CountryPickerRow(
  emoji: String?,
  name: String,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .lightClickable(enabled = enabled, onClick = onClick)
      .padding(vertical = 1f.gridUnitsAsDp()),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (!emoji.isNullOrEmpty()) {
      LightText(text = emoji, variant = LightTextVariant.Copy, maxLines = 1)
      Spacer(modifier = Modifier.width(1f.gridUnitsAsDp()))
    }
    LightText(
      text = name,
      variant = LightTextVariant.Copy,
      lighten = !enabled,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    Spacer(modifier = Modifier.width(1f.gridUnitsAsDp()))
    LightText(text = "▾", variant = LightTextVariant.Detail, lighten = true)
  }
}

@Composable
private fun PhoneInputRow(
  countryCode: String,
  phoneNumber: String,
  onCountryCodeChanged: (String) -> Unit,
  onPhoneNumberChanged: (String) -> Unit,
  enabled: Boolean,
  onDone: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.Bottom,
  ) {
    PhonePartField(
      value = countryCode,
      prefix = "+",
      onValueChange = { onCountryCodeChanged(it.filter(Char::isDigit).take(3)) },
      keyboardType = KeyboardType.Number,
      imeAction = ImeAction.Next,
      enabled = enabled,
      modifier = Modifier.width(5f.gridUnitsAsDp()),
    )

    Spacer(modifier = Modifier.width(1f.gridUnitsAsDp()))

    PhonePartField(
      value = phoneNumber,
      prefix = "",
      onValueChange = onPhoneNumberChanged,
      keyboardType = KeyboardType.Phone,
      imeAction = ImeAction.Done,
      onDone = onDone,
      enabled = enabled,
      modifier = Modifier.weight(1f),
    )
  }
}

@Composable
private fun PhonePartField(
  value: String,
  prefix: String,
  onValueChange: (String) -> Unit,
  keyboardType: KeyboardType,
  imeAction: ImeAction,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  onDone: (() -> Unit)? = null,
) {
  val colors = LightThemeTokens.colors
  val baseStyle = LightThemeTokens.typography.copy
  val textStyle = baseStyle.copy(
    fontSize = baseStyle.fontSize.scaled(),
    lineHeight = baseStyle.lineHeight.scaled(),
    letterSpacing = baseStyle.letterSpacing.scaled(),
    color = if (enabled) colors.content else colors.contentSecondary,
  )

  Column(modifier = modifier) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (prefix.isNotEmpty()) {
        LightText(text = prefix, variant = LightTextVariant.Copy, lighten = !enabled)
      }
      BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        textStyle = textStyle,
        cursorBrush = SolidColor(colors.content),
        modifier = Modifier.fillMaxWidth(),
      )
    }
    Spacer(modifier = Modifier.height(0.5f.gridUnitsAsDp()))
    Spacer(
      modifier = Modifier
        .fillMaxWidth(0.8f)
        .height(3f.designVerticalPxToDp())
        .background(colors.content),
    )
  }
}

@Composable
private fun TextUnit.scaled(): TextUnit =
  if (this == TextUnit.Unspecified) this else value.designVerticalPxToSp()
