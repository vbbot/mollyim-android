/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
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
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.registration.R
import org.thoughtcrime.securesms.light.MollyLightTheme
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
          contentDescription = stringResource(android.R.string.cancel)
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
            variant = LightTextVariant.Paragraph,
            lighten = true,
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 1.5f.gridUnitsAsDp())
          )

          if (deviceBuildVersion >= 33) {
            PermissionRow(
              imageVector = ImageVector.vectorResource(id = R.drawable.permission_notification),
              title = stringResource(id = R.string.GrantPermissionsFragment__notifications),
              subtitle = stringResource(id = R.string.GrantPermissionsFragment__get_notified_when)
            )
          }

          PermissionRow(
            imageVector = ImageVector.vectorResource(id = R.drawable.permission_contact),
            title = stringResource(id = R.string.GrantPermissionsFragment__contacts),
            subtitle = stringResource(id = R.string.GrantPermissionsFragment__find_people_you_know)
          )

          if (deviceBuildVersion < 29 || !isBackupSelectionRequired) {
            PermissionRow(
              imageVector = ImageVector.vectorResource(id = R.drawable.permission_file),
              title = stringResource(id = R.string.GrantPermissionsFragment__storage),
              subtitle = stringResource(id = R.string.GrantPermissionsFragment__send_photos_videos_and_files)
            )
          }

          PermissionRow(
            imageVector = ImageVector.vectorResource(id = R.drawable.permission_phone),
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

@Composable
fun PermissionRow(
  imageVector: ImageVector,
  title: String,
  subtitle: String
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(bottom = 1.25f.gridUnitsAsDp()),
    verticalAlignment = Alignment.Top
  ) {
    Icon(
      imageVector = imageVector,
      contentDescription = null,
      tint = LightThemeTokens.colors.content,
      modifier = Modifier.size(2f.gridUnitsAsDp())
    )

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
