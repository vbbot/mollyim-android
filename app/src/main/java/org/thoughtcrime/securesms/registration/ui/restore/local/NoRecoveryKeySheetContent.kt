/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore.local

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.thelightphone.sdk.ui.LightTextVariant
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R

/**
 * Displayed when the user presses the 'No recovery key?' button on the
 * [EnterLocalBackupKeyScreen].
 *
 * Was a Material bottom sheet with a key glyph in a tinted circle and hard-coded English; now a
 * full-screen Light notice with the existing localized strings for the same two paragraphs. The two
 * explanatory paragraphs are prose, so they are `Paragraph` rather than a notice's usual `Copy`.
 * BACK and system back return to key entry, as dismissing the sheet did. LEARN MORE runs
 * [onLearnMoreClick]; SKIP AND DON'T RESTORE runs [onSkipAndDontRestoreClick], last because it is the
 * consequential choice.
 */
@Composable
fun NoRecoveryKeyScreen(
  onSkipAndDontRestoreClick: () -> Unit,
  onBackClick: () -> Unit,
  onLearnMoreClick: () -> Unit = {}
) {
  LightRestoreNotice(
    title = stringResource(R.string.EnterLocalBackupKeyScreen__no_recovery_key),
    message = stringResource(R.string.EnterBackupKey_no_key_paragraph_1) + "\n\n" + stringResource(R.string.EnterBackupKey_no_key_paragraph_2),
    messageVariant = LightTextVariant.Paragraph,
    actions = listOf(
      LightRestoreNoticeAction(stringResource(R.string.LearnMoreTextView_learn_more), onLearnMoreClick),
      LightRestoreNoticeAction(stringResource(R.string.EnterBackupKey_skip_and_dont_restore), onSkipAndDontRestoreClick)
    ),
    onBack = onBackClick,
    backDescription = stringResource(R.string.LightRestore__back_to_recovery_key)
  )
}

@DayNightPreviews
@Composable
fun NoRecoveryKeyScreenPreview() {
  Previews.Preview {
    NoRecoveryKeyScreen(
      onSkipAndDontRestoreClick = {},
      onBackClick = {},
      onLearnMoreClick = {}
    )
  }
}
