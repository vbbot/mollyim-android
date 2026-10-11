/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.phonenumber

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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.LightTextAction
import org.thoughtcrime.securesms.light.MollyLightTheme

const val PHONE_NUMBER_NOTICE_TAG = "phone-number-notice"

/** The chats panel gutter for a block of centred status text. */
private const val NOTICE_GUTTER_UNITS = 3f

/** Number to copy, and copy to the first action. */
private const val NOTICE_COPY_TOP_UNITS = 0.75f
private const val NOTICE_ACTIONS_TOP_UNITS = 1f

/** One action on a [PhoneNumberNotice]. [label] is given in prose and shown upper case. */
data class PhoneNumberNoticeAction(val label: String, val onSelected: () -> Unit)

/**
 * Everything the phone number step used to raise as a `MaterialAlertDialog`, as data.
 *
 * [title] goes in the top bar's centre. [number] is the confirm dialog's bold E164: `LightText` takes
 * a plain string, so the emphasis is carried by setting the number on its own line at Heading above
 * the Copy [message] rather than by a bold span. [actions] are listed top to bottom in the order the
 * Material dialog had its buttons left to right (neutral, negative, positive). [onBack] is what the
 * Material dialog did on cancel -- the top bar's BACK and system back both run it.
 */
data class PhoneNumberNoticeModel(
  val title: String,
  val message: String,
  val actions: List<PhoneNumberNoticeAction>,
  val number: String? = null,
  val onBack: () -> Unit = {},
  /** What BACK returns to, for screen readers. Null means the phone number form. */
  val backDescription: String? = null,
)

/**
 * The notices the phone number step has raised, newest on top, as Material's stacked dialog windows
 * were. Every exit takes the notice down *before* running its callback -- the callbacks navigate,
 * reopen the editor or raise the next notice, and none of them should find this one still up.
 */
@Stable
class PhoneNumberNotices {
  private val stack = mutableStateListOf<PhoneNumberNoticeModel>()

  val current: PhoneNumberNoticeModel?
    get() = stack.lastOrNull()

  fun show(notice: PhoneNumberNoticeModel) {
    stack += notice
  }

  fun select(notice: PhoneNumberNoticeModel, action: PhoneNumberNoticeAction) {
    take(notice)
    action.onSelected()
  }

  fun back(notice: PhoneNumberNoticeModel) {
    take(notice)
    notice.onBack()
  }

  /** Drops every notice without running anything, for a host whose view is going away. */
  fun clear() {
    stack.clear()
  }

  /** By identity, not equality: two raises of the same error are equal data but separate notices. */
  private fun take(notice: PhoneNumberNoticeModel) {
    val index = stack.indexOfLast { it === notice }
    if (index >= 0) stack.removeAt(index)
  }
}

/**
 * The confirm-number notice, built in one place so its three exits can be tested without the
 * fragment: [onConfirm] (OK), [onEditNumber] and [onCancel] (BACK or system back). Each exit runs only
 * its own callback, as the Material dialog's positive, negative and cancel listeners did.
 */
fun confirmNumberNotice(
  title: String,
  number: String,
  codeWillBeSent: String?,
  okLabel: String,
  editNumberLabel: String,
  onConfirm: () -> Unit,
  onEditNumber: () -> Unit,
  onCancel: () -> Unit,
): PhoneNumberNoticeModel = PhoneNumberNoticeModel(
  title = title,
  number = number,
  message = codeWillBeSent.orEmpty(),
  actions = listOf(
    PhoneNumberNoticeAction(editNumberLabel, onEditNumber),
    PhoneNumberNoticeAction(okLabel, onConfirm),
  ),
  onBack = onCancel,
)

/** Draws [notices]' top notice, if there is one, over whatever is beneath. */
@Composable
fun PhoneNumberNoticeHost(notices: PhoneNumberNotices) {
  val notice = notices.current ?: return
  PhoneNumberNotice(
    notice = notice,
    onAction = { action -> notices.select(notice, action) },
    onBack = { notices.back(notice) },
  )
}

/**
 * A full-screen Light notice: the title in the top bar beside BACK, the copy and the actions centred
 * below, in chats' confirm-panel grammar. Opaque and touch-swallowing like the number editor, so the
 * form underneath can neither show through nor take a tap, and it owns system back while it is up.
 */
@Composable
fun PhoneNumberNotice(
  notice: PhoneNumberNoticeModel,
  onAction: (PhoneNumberNoticeAction) -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)

  MollyLightTheme {
    val colors = LightThemeTokens.colors

    Column(
      modifier = Modifier
        .fillMaxSize()
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
        }
        .testTag(PHONE_NUMBER_NOTICE_TAG),
    ) {
      LightTopBar(
        leftButton = LightBarButton.LightIcon(
          icon = LightIcons.BACK,
          onClick = onBack,
          contentDescription = notice.backDescription ?: stringResource(R.string.EnterPhoneNumberFragment__back_to_phone_number),
        ),
        center = LightTopBarCenter.Text(text = notice.title),
        modifier = Modifier.statusBarsPadding(),
      )

      BoxWithConstraints(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .navigationBarsPadding(),
      ) {
        val viewport = maxHeight

        LightScrollView(
          scrollBarPosition = LightScrollBarPosition.Inside,
          modifier = Modifier.fillMaxSize(),
        ) {
          // At least the viewport tall, so a short notice centres and a long one (the non-standard
          // number explanation) scrolls rather than pushing its actions off the screen.
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = viewport)
              .padding(horizontal = NOTICE_GUTTER_UNITS.gridUnitsAsDp(), vertical = 1f.gridUnitsAsDp()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            notice.number?.let { number ->
              LightText(
                text = number,
                variant = LightTextVariant.Heading,
                align = TextAlign.Center,
                maxLines = 1,
              )
            }

            if (notice.message.isNotEmpty()) {
              LightText(
                text = notice.message,
                variant = LightTextVariant.Copy,
                align = TextAlign.Center,
                modifier = Modifier.padding(top = if (notice.number != null) NOTICE_COPY_TOP_UNITS.gridUnitsAsDp() else 0f.gridUnitsAsDp()),
              )
            }

            Spacer(modifier = Modifier.height(NOTICE_ACTIONS_TOP_UNITS.gridUnitsAsDp()))

            notice.actions.forEach { action ->
              LightTextAction(
                label = action.label,
                onClick = Runnable { onAction(action) },
                align = Alignment.Center,
              )
            }
          }
        }
      }
    }
  }
}
