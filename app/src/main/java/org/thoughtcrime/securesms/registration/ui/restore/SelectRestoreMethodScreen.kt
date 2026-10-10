/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationAction
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationBottomActions
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen

/** Screen showing the restore methods available in the active registration flow. */
@Composable
fun SelectRestoreMethodScreen(
  restoreMethods: List<RestoreMethod>,
  onRestoreMethodClicked: (RestoreMethod) -> Unit = {},
  onSkip: () -> Unit = {},
  extraContent: @Composable ColumnScope.() -> Unit = {}
) {
  RegistrationScreen(
    title = stringResource(id = R.string.SelectRestoreMethodFragment__restore_or_transfer_top_bar),
    subtitle = stringResource(id = R.string.SelectRestoreMethodFragment__get_your_signal_account),
    bottomContent = {
      RegistrationBottomActions(
        center = RegistrationAction(
          label = stringResource(R.string.registration_activity__skip_restore),
          onClick = onSkip
        )
      )
    }
  ) {
    restoreMethods.forEach { method ->
      RestoreRow(
        icon = painterResource(method.iconRes),
        title = stringResource(method.titleRes),
        subtitle = stringResource(method.subtitleRes),
        onRowClick = { onRestoreMethodClicked(method) }
      )
    }

    extraContent()
  }
}

@DayNightPreviews
@Composable
private fun SelectRestoreMethodScreenPreview() {
  Previews.Preview {
    SelectRestoreMethodScreen(listOf(RestoreMethod.FROM_SIGNAL_BACKUPS, RestoreMethod.FROM_OLD_DEVICE, RestoreMethod.FROM_LOCAL_BACKUP_V1))
  }
}
