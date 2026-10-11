/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.permissions

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIconConfiguration
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollBarPosition
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.registration.R
import org.thoughtcrime.securesms.R as AppR
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.light.rememberLightTintedPainter
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationAction
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationBottomActions

/** Layout that explains optional registration permissions without making denial a blocker. */
@Composable
fun GrantPermissionsScreen(
  deviceBuildVersion: Int,
  isBackupSelectionRequired: Boolean,
  onNextClicked: () -> Unit = {},
  onNotNowClicked: () -> Unit = {},
  onBackClicked: () -> Unit = {}
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
          onClick = onBackClicked,
          // Names where BACK goes: this screen is only reached from Welcome.
          contentDescription = stringResource(AppR.string.GrantPermissionsFragment__back_to_welcome)
        ),
        center = LightTopBarCenter.Text(
          stringResource(id = R.string.GrantPermissionsFragment__allow_permissions)
        ),
        modifier = Modifier.statusBarsPadding()
      )

      LightScrollView(
        scrollBarPosition = LightScrollBarPosition.Outside,
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2f.gridUnitsAsDp(), vertical = 1f.gridUnitsAsDp())
        ) {
          LightText(
            text = stringResource(id = R.string.GrantPermissionsFragment__to_help_you_message_people_you_know),
            variant = LightTextVariant.Copy,
            lighten = true,
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 1.5f.gridUnitsAsDp())
          )

          if (deviceBuildVersion >= 33) {
            // The SDK has no notification glyph, so this one keeps the registration artwork, tinted
            // to the content colour as LightIcon tints the SDK's own.
            PermissionRow(
              icon = { PermissionIconImage(painter = rememberLightTintedPainter(R.drawable.permission_notification)) },
              title = stringResource(id = R.string.GrantPermissionsFragment__notifications),
              subtitle = stringResource(id = R.string.GrantPermissionsFragment__get_notified_when)
            )
          }

          PermissionRow(
            icon = { PermissionLightIcon(icon = LightIcons.CONTACTS) },
            title = stringResource(id = R.string.GrantPermissionsFragment__contacts),
            subtitle = stringResource(id = R.string.GrantPermissionsFragment__find_people_you_know)
          )

          if (deviceBuildVersion < 29 || !isBackupSelectionRequired) {
            PermissionRow(
              icon = { PermissionLightIcon(icon = LightIcons.MEDIA) },
              title = stringResource(id = R.string.GrantPermissionsFragment__storage),
              subtitle = stringResource(id = R.string.GrantPermissionsFragment__send_photos_videos_and_files)
            )
          }

          PermissionRow(
            icon = { PermissionLightIcon(icon = LightIcons.CALL) },
            title = stringResource(id = R.string.GrantPermissionsFragment__phone_calls),
            subtitle = stringResource(id = R.string.GrantPermissionsFragment__make_registering_easier)
          )
        }
      }

      RegistrationBottomActions(
        left = RegistrationAction(
          label = stringResource(id = R.string.GrantPermissionsFragment__not_now),
          onClick = onNotNowClicked
        ),
        right = RegistrationAction(
          label = stringResource(id = R.string.GrantPermissionsFragment__next),
          onClick = onNextClicked
        )
      )
    }
  }
}

/** Permission glyphs: the SDK bar icon size, and decorative -- the row title says what each is for. */
private const val PERMISSION_ICON_UNITS = 2f

@Composable
private fun PermissionLightIcon(icon: LightIconConfiguration) {
  LightIcon(icon = icon, size = PERMISSION_ICON_UNITS, contentDescription = null)
}

@Composable
private fun PermissionIconImage(painter: Painter) {
  Image(
    painter = painter,
    contentDescription = null,
    contentScale = ContentScale.Fit,
    modifier = Modifier.size(PERMISSION_ICON_UNITS.gridUnitsAsDp())
  )
}

@Composable
fun PermissionRow(
  icon: @Composable () -> Unit,
  title: String,
  subtitle: String
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(bottom = 1.25f.gridUnitsAsDp()),
    verticalAlignment = Alignment.Top
  ) {
    icon()

    Spacer(modifier = Modifier.size(1f.gridUnitsAsDp()))

    Column(modifier = Modifier.weight(1f)) {
      LightText(text = title, variant = LightTextVariant.Heading)
      LightText(
        text = subtitle,
        variant = LightTextVariant.Detail,
        lighten = true,
        modifier = Modifier.padding(top = 0.25f.gridUnitsAsDp())
      )
    }
  }
}

@DayNightPreviews
@Composable
fun GrantPermissionsScreenPreview() {
  Previews.Preview {
    GrantPermissionsScreen(
      deviceBuildVersion = 33,
      isBackupSelectionRequired = true
    )
  }
}
