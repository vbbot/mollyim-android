/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.restore.local

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightProgressBar
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
import org.thoughtcrime.securesms.light.LightTextAction
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.light.rememberMollyLightColors

const val LIGHT_RESTORE_NOTICE_TAG = "light-restore-notice"
const val LIGHT_RESTORE_PROGRESS_TAG = "light-restore-progress"
const val LIGHT_RESTORE_STATUS_TAG = "light-restore-status"

/** The chats panel gutter for a block of centred status text, as on the phone number notices. */
private const val NOTICE_GUTTER_UNITS = 3f

/** Copy to the first action, and status to the progress bar and its detail line. */
private const val NOTICE_ACTIONS_TOP_UNITS = 1f
private const val STATUS_SPACING_UNITS = 0.75f

/** One action on a [LightRestoreNotice]. [label] is given in prose and shown upper case. */
data class LightRestoreNoticeAction(val label: String, val onSelected: () -> Unit)

/**
 * What the restore and transfer flows' alert dialogs become: a full-screen Light notice in the same
 * grammar as the phone number step's -- the title in the top bar, the copy and the actions centred
 * below -- opaque and touch-swallowing so nothing underneath can take a tap.
 *
 * Unlike `PhoneNumberNotice`, back is optional, because several of the dialogs this replaces could
 * not be backed out of:
 *
 * - [onBack] is what system back does while the notice is up. Null swallows back, which is what a
 *   dialog window whose dismiss request did nothing amounted to.
 * - [backDescription] additionally puts the BACK glyph in the top bar, running the same [onBack].
 *   It is only offered where back is a genuine "never mind", never where it would run a decision
 *   (a dialog whose outside tap picked its negative button keeps that on system back only).
 * - [ownsSystemBack] false leaves system back to the host entirely, for a host -- a fragment with
 *   its own `OnBackPressedCallback` -- that already decides what back does on this screen.
 *
 * Actions are listed top to bottom in the order the dialog had its buttons left to right, and each
 * runs only its own callback: hosts clear their dialog state in those callbacks, as they did before.
 */
@Composable
fun LightRestoreNotice(
  title: String,
  message: String,
  actions: List<LightRestoreNoticeAction>,
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  backDescription: String? = null,
  messageVariant: LightTextVariant = LightTextVariant.Copy,
  ownsSystemBack: Boolean = true
) {
  BackHandler(enabled = ownsSystemBack, onBack = onBack ?: {})

  MollyLightTheme {
    val colors = LightThemeTokens.colors

    Column(
      modifier = modifier
        .fillMaxSize()
        .background(colors.background)
        .consumeAllPointerEvents()
        .testTag(LIGHT_RESTORE_NOTICE_TAG)
    ) {
      LightTopBar(
        leftButton = if (onBack != null && backDescription != null) {
          LightBarButton.LightIcon(
            icon = LightIcons.BACK,
            onClick = onBack,
            contentDescription = backDescription
          )
        } else {
          null
        },
        center = LightTopBarCenter.Text(text = title),
        modifier = Modifier.statusBarsPadding()
      )

      BoxWithConstraints(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .navigationBarsPadding()
      ) {
        val viewport = maxHeight

        LightScrollView(
          scrollBarPosition = LightScrollBarPosition.Inside,
          modifier = Modifier.fillMaxSize()
        ) {
          // At least the viewport tall, so a short notice centres and a long one scrolls rather than
          // pushing its actions off the screen.
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = viewport)
              .padding(horizontal = NOTICE_GUTTER_UNITS.gridUnitsAsDp(), vertical = 1f.gridUnitsAsDp()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            if (message.isNotEmpty()) {
              LightText(
                text = message,
                variant = messageVariant,
                align = TextAlign.Center
              )
            }

            Spacer(modifier = Modifier.height(NOTICE_ACTIONS_TOP_UNITS.gridUnitsAsDp()))

            actions.forEach { action ->
              LightTextAction(
                label = action.label,
                onClick = Runnable { action.onSelected() },
                align = Alignment.Center
              )
            }
          }
        }
      }
    }
  }
}

/**
 * A Light progress or status screen, after `RemoteRestoreActivity`'s: the title in the top bar, the
 * optional explanatory [prose] under it, then a centred Heading [status] line with, when there is
 * measurable progress, a [LightProgressBar] and a Detail line under it.
 *
 * [progress] null means indeterminate: there is no spinner in this design language, so the status
 * line (which already ends in an ellipsis wherever upstream had one) carries it, and accessibility
 * services are told the progress is indeterminate.
 *
 * This owns no back behaviour of its own. Hosts that had a back rule keep it themselves, so a
 * progress screen that used to let back through still does.
 */
@Composable
fun LightRestoreProgress(
  title: String,
  status: String,
  modifier: Modifier = Modifier,
  prose: String? = null,
  progress: Float? = null,
  detail: String? = null,
  actions: List<LightBarButton> = emptyList(),
  back: LightBarButton? = null
) {
  MollyLightTheme {
    val colors = LightThemeTokens.colors

    Column(
      modifier = modifier
        .fillMaxSize()
        .background(colors.background)
        .testTag(LIGHT_RESTORE_PROGRESS_TAG)
    ) {
      LightTopBar(
        leftButton = back,
        center = LightTopBarCenter.Text(text = title),
        modifier = Modifier.statusBarsPadding()
      )

      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .padding(horizontal = 2f.gridUnitsAsDp())
      ) {
        if (prose != null) {
          LightText(
            text = prose,
            variant = LightTextVariant.Paragraph,
            modifier = Modifier.padding(top = 1f.gridUnitsAsDp())
          )
        }

        Column(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .semantics {
              progressBarRangeInfo = if (progress == null) {
                ProgressBarRangeInfo.Indeterminate
              } else {
                ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
              }
            }
            .testTag(LIGHT_RESTORE_STATUS_TAG),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          LightText(
            text = status,
            variant = LightTextVariant.Heading,
            align = TextAlign.Center
          )

          if (progress != null) {
            Spacer(modifier = Modifier.height(STATUS_SPACING_UNITS.gridUnitsAsDp()))
            LightProgressBar(
              colors = rememberMollyLightColors(),
              progress = progress
            )
          }

          if (detail != null) {
            LightText(
              text = detail,
              variant = LightTextVariant.Detail,
              align = TextAlign.Center,
              lighten = true,
              modifier = Modifier.padding(top = STATUS_SPACING_UNITS.gridUnitsAsDp())
            )
          }
        }
      }

      if (actions.isNotEmpty()) {
        LightBottomBar(
          items = actions,
          modifier = Modifier.navigationBarsPadding()
        )
      } else {
        Spacer(modifier = Modifier.navigationBarsPadding())
      }
    }
  }
}

/**
 * Eats every pointer event that reaches this element, so a tap on a notice's empty space cannot fall
 * through to the screen beneath. Children see events first, so the notice's own actions still work.
 */
internal fun Modifier.consumeAllPointerEvents(): Modifier = pointerInput(Unit) {
  awaitEachGesture {
    awaitFirstDown(requireUnconsumed = false).consume()
    while (true) {
      val event = awaitPointerEvent()
      event.changes.forEach { it.consume() }
      if (event.changes.none { it.pressed }) break
    }
  }
}

@DayNightPreviews
@Composable
private fun LightRestoreNoticePreview() {
  Previews.Preview {
    LightRestoreNotice(
      title = "Skip restore?",
      message = "If you skip restore now you will not be able to restore later.",
      actions = listOf(
        LightRestoreNoticeAction("Cancel") {},
        LightRestoreNoticeAction("Skip restore") {}
      ),
      onBack = {},
      backDescription = "Back"
    )
  }
}

@DayNightPreviews
@Composable
private fun LightRestoreProgressPreview() {
  Previews.Preview {
    LightRestoreProgress(
      title = "Restoring backup",
      prose = "Depending on the size of your backup, this could take a few minutes.",
      status = "Restoring messages…",
      progress = 0.4f,
      detail = "40 MB of 100 MB (40%)"
    )
  }
}
