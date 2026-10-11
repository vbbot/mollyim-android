/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore.local

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.gridUnitsAsDp
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationAction
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationBack
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationBottomActions
import org.thoughtcrime.securesms.registration.ui.shared.RegistrationScreen

/**
 * What to do in the system folder picker, shown before it opens. Was a Material bottom sheet; now a
 * full Light screen in the registration grammar: the title in the top bar, BACK (and system back)
 * return to the backup-type choice, and CONTINUE opens the picker exactly as the sheet's button did.
 */
@Composable
fun SelectYourBackupFolderScreen(
  onContinueClick: () -> Unit,
  onBackClick: () -> Unit
) {
  SelectInstructionsScreen(
    title = stringResource(R.string.SelectInstructionsSheet__select_your_backup_folder),
    instructions = listOf(
      stringResource(R.string.SelectInstructionsSheet__select_the_top_level_folder),
      stringResource(R.string.SelectInstructionsSheet__tap_select_this_folder),
      stringResource(R.string.SelectInstructionsSheet__do_not_select_individual_files)
    ),
    onContinueClick = onContinueClick,
    onBackClick = onBackClick
  )
}

/** The single-file counterpart of [SelectYourBackupFolderScreen]; CONTINUE opens the file picker. */
@Composable
fun SelectYourBackupFileScreen(
  onContinueClick: () -> Unit,
  onBackClick: () -> Unit
) {
  SelectInstructionsScreen(
    title = stringResource(R.string.SelectInstructionsSheet__select_your_backup_file),
    instructions = listOf(
      stringResource(R.string.SelectInstructionsSheet__select_the_top_level_folder),
      stringResource(R.string.SelectInstructionsSheet__tap_on_the_backup_file)
    ),
    onContinueClick = onContinueClick,
    onBackClick = onBackClick
  )
}

/**
 * The instructions are short imperative statements, so each is a `Copy` line. The sheet's
 * decorative glyphs (folder, tap, warning circle) are dropped: every line already says what its
 * glyph meant, and the Light screens carry no ornamental icons.
 */
@Composable
private fun SelectInstructionsScreen(
  title: String,
  instructions: List<String>,
  onContinueClick: () -> Unit,
  onBackClick: () -> Unit
) {
  RegistrationScreen(
    title = title,
    subtitle = stringResource(R.string.SelectInstructionsSheet__after_tapping_continue),
    bottomContent = {
      RegistrationBottomActions(
        center = RegistrationAction(
          stringResource(R.string.SelectInstructionsSheet__continue_button),
          onContinueClick
        )
      )
    },
    back = RegistrationBack(
      contentDescription = stringResource(R.string.RegistrationScreen__back_to_previous_screen),
      onBack = onBackClick
    )
  ) {
    instructions.forEachIndexed { index, instruction ->
      LightText(
        text = "${index + 1}. $instruction",
        variant = LightTextVariant.Copy,
        modifier = Modifier.padding(bottom = 0.75f.gridUnitsAsDp())
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun SelectYourBackupFolderScreenPreview() {
  Previews.Preview {
    SelectYourBackupFolderScreen(onContinueClick = {}, onBackClick = {})
  }
}

@DayNightPreviews
@Composable
private fun SelectYourBackupFileScreenPreview() {
  Previews.Preview {
    SelectYourBackupFileScreen(onContinueClick = {}, onBackClick = {})
  }
}
