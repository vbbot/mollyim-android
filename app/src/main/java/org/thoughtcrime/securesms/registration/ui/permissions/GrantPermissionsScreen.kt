/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.permissions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.registration.R
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationAction
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationBottomActions
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen

/** Layout that explains optional registration permissions without making denial a blocker. */
@Composable
fun GrantPermissionsScreen(
  deviceBuildVersion: Int,
  isBackupSelectionRequired: Boolean,
  onNextClicked: () -> Unit = {},
  onNotNowClicked: () -> Unit = {}
) {
  RegistrationScreen(
    title = stringResource(id = R.string.GrantPermissionsFragment__allow_permissions),
    subtitle = stringResource(id = R.string.GrantPermissionsFragment__to_help_you_message_people_you_know),
    bottomContent = {
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
  ) {
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
