/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.signal.core.ui.compose.SignalIcons
import org.thoughtcrime.securesms.R

/** Callback-only restore method row in the Light settings/list grammar. */
@Composable
fun RestoreRow(
  icon: Painter,
  title: String,
  subtitle: String,
  onRowClick: () -> Unit = {}
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .fillMaxWidth()
      .lightClickable(onClick = onRowClick)
      .padding(vertical = 0.75f.gridUnitsAsDp())
  ) {
    Icon(
      painter = icon,
      tint = LightThemeTokens.colors.content,
      contentDescription = null,
      modifier = Modifier.size(2f.gridUnitsAsDp())
    )

    Column(modifier = Modifier.padding(start = 1f.gridUnitsAsDp())) {
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
private fun RestoreMethodRowPreview() {
  Previews.Preview {
    RestoreRow(
      icon = SignalIcons.Backup.painter,
      title = stringResource(R.string.SelectRestoreMethodFragment__restore_signal_backup),
      subtitle = stringResource(R.string.SelectRestoreMethodFragment__restore_your_text_messages_and_media_from)
    )
  }
}
