/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.entercode

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
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
import org.thoughtcrime.securesms.light.rememberLightTintedPainter
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogActivity
import java.util.Locale
import kotlin.time.Duration

const val ENTER_CODE_FIELD_TAG = "enter-code-field"
const val ENTER_CODE_PANEL_TAG = "enter-code-panel"

/** The LightTextField / LightTextInputEditor underline: 3 design px, 0.5 gu below the value, 80% wide. */
private const val UNDERLINE_THICKNESS_PX = 3f
private const val UNDERLINE_GAP_GRID_UNITS = 0.5f
private const val UNDERLINE_WIDTH_FRACTION = 0.8f

/** Spread the six digits so they read as a code rather than a number. */
private val CODE_LETTER_SPACING = 0.25.em

/** Same counts as RegistrationViewDelegate.setDebugLogSubmitMultiTapView, which the upstream header used. */
private const val DEBUG_TAP_TARGET = 8
private const val DEBUG_TAP_ANNOUNCE = 4

/** LightActionPanel's geometry: the bottom half of the screen, bottom-bar-height rows, a 38dp chevron zone. */
private const val PANEL_HEIGHT_FRACTION = 0.5f
private val PANEL_ROW_HEIGHT = 44.dp
private val CHEVRON_ZONE = 38.dp
private val CHEVRON_TOUCH_WIDTH = 48.dp
private val CHEVRON_WIDTH = 17.dp
private val CHEVRON_HEIGHT = 10.dp

/** One row of the overflow panel. A disabled row is drawn lightened and ignores taps. */
data class EnterCodePanelRow(val label: String, val enabled: Boolean, val onSelected: () -> Unit)

/**
 * The verification code step as a Light screen sized for the LP3 with its system keyboard open: the
 * top bar, one Detail line naming the number, the code field and a status line, all at the top so the
 * keyboard can take the rest. Resend, call, wrong number and support sit behind the overflow.
 *
 * The fragment owns the state; this renders it and reports intents. [now] is read only inside the
 * panel, so the fragment's countdown clock recomposes the panel rows and nothing else.
 *
 * @param covered a notice is drawn over the screen: the keyboard goes down until it is gone.
 */
@Composable
fun EnterCodeScreen(
  sentTo: String,
  field: VerificationCodeFieldState,
  inProgress: Boolean,
  incorrectCode: Boolean,
  nextSmsTimestamp: Duration,
  nextCallTimestamp: Duration,
  showHavingTrouble: Boolean,
  covered: Boolean,
  now: () -> Duration,
  onBack: () -> Unit,
  onCodeComplete: (String) -> Unit,
  onResendSms: () -> Unit,
  onCall: () -> Unit,
  onWrongNumber: () -> Unit,
  onHavingTrouble: () -> Unit,
) {
  MollyLightTheme {
    val colors = LightThemeTokens.colors
    val title = stringResource(R.string.RegistrationActivity_verification_code)
    val onTitleTapped = rememberDebugLogTapHandler()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var panelOpen by rememberSaveable { mutableStateOf(false) }

    // Focus and the keyboard on open, after each reset (upstream's displayKeyboard()), and whenever
    // the panel or a notice goes away; down while either is up, so neither is squeezed by the IME.
    LaunchedEffect(covered, panelOpen, field.focusRequests) {
      if (covered || panelOpen) {
        keyboardController?.hide()
      } else {
        focusRequester.requestFocus()
        keyboardController?.show()
      }
    }

    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(colors.background),
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
          leftButton = LightBarButton.LightIcon(
            icon = LightIcons.BACK,
            onClick = onBack,
            contentDescription = stringResource(R.string.EnterCodeFragment__back_to_phone_number),
          ),
          // The title keeps upstream's eight-tap debug log, which sat on the in-body header.
          center = LightTopBarCenter.Text(text = title, onClick = onTitleTapped),
          rightButton = LightBarButton.LightIcon(
            icon = LightIcons.ELLIPSES,
            onClick = { panelOpen = true },
            contentDescription = stringResource(R.string.MainToolbar__more_options_content_description),
          ),
          modifier = Modifier.statusBarsPadding(),
        )

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2f.gridUnitsAsDp())
            .padding(top = 0.5f.gridUnitsAsDp()),
        ) {
          if (sentTo.isNotEmpty()) {
            LightText(text = sentTo, variant = LightTextVariant.Detail)
            Spacer(modifier = Modifier.height(0.75f.gridUnitsAsDp()))
          }

          CodeField(
            field = field,
            label = title,
            inProgress = inProgress,
            focusRequester = focusRequester,
            onCodeComplete = onCodeComplete,
          )

          val status = when {
            inProgress -> stringResource(R.string.EnterCodeFragment__checking_code)
            incorrectCode && field.code.isEmpty() -> stringResource(R.string.RegistrationActivity_incorrect_code)
            else -> null
          }
          if (status != null) {
            LightText(
              text = status,
              variant = LightTextVariant.Detail,
              lighten = inProgress,
              modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()),
            )
          }
        }
      }

      if (panelOpen) {
        val close = { panelOpen = false }
        EnterCodePanel(
          rows = panelRows(
            now = now,
            nextSmsTimestamp = nextSmsTimestamp,
            nextCallTimestamp = nextCallTimestamp,
            showHavingTrouble = showHavingTrouble,
            onResendSms = onResendSms,
            onCall = onCall,
            onWrongNumber = onWrongNumber,
            onHavingTrouble = onHavingTrouble,
          ),
          onClose = close,
        )
      }
    }
  }
}

@Composable
private fun CodeField(
  field: VerificationCodeFieldState,
  label: String,
  inProgress: Boolean,
  focusRequester: FocusRequester,
  onCodeComplete: (String) -> Unit,
) {
  val colors = LightThemeTokens.colors
  val baseStyle = LightThemeTokens.typography.heading
  val textStyle = baseStyle.copy(
    fontSize = baseStyle.fontSize.scaled(),
    lineHeight = baseStyle.lineHeight.scaled(),
    letterSpacing = CODE_LETTER_SPACING,
    color = colors.content,
  )

  BasicTextField(
    value = field.value,
    onValueChange = { edited -> field.edit(edited)?.let(onCodeComplete) },
    // Read-only rather than disabled while the code is checked, which is when upstream swapped its
    // keypad for a spinner: a disabled field drops focus and the keyboard would bounce.
    readOnly = inProgress,
    singleLine = true,
    // NumberPassword asks for the bare 0-9 PIN pad on Gboard, Samsung and AOSP keyboards, where
    // Number adds punctuation; the masking is a VisualTransformation, which is not set, so every
    // digit stays visible. It also keeps the code out of the IME's suggestions and learning.
    keyboardOptions = KeyboardOptions(
      keyboardType = KeyboardType.NumberPassword,
      autoCorrectEnabled = false,
      imeAction = ImeAction.Done,
    ),
    // Done resubmits a complete code the last attempt left in place (a network error keeps it,
    // as the cells did); the sixth digit already submitted it once.
    keyboardActions = KeyboardActions(
      onDone = { if (field.isComplete && !inProgress) onCodeComplete(field.code) },
    ),
    textStyle = textStyle,
    cursorBrush = SolidColor(colors.content),
    modifier = Modifier
      .fillMaxWidth()
      .focusRequester(focusRequester)
      .semantics { contentDescription = label }
      .testTag(ENTER_CODE_FIELD_TAG),
  )
  Spacer(modifier = Modifier.height(UNDERLINE_GAP_GRID_UNITS.gridUnitsAsDp()))
  Spacer(
    modifier = Modifier
      .fillMaxWidth(UNDERLINE_WIDTH_FRACTION)
      .height(UNDERLINE_THICKNESS_PX.designVerticalPxToDp())
      .background(colors.content),
  )
}

/**
 * The panel's rows, in upstream's button order. Resend and call carry their countdown in the label,
 * as ActionCountDownButton did, and are inert until it runs out. Support is only offered once three
 * codes have been wrong, as upstream's button was.
 */
@Composable
private fun panelRows(
  now: () -> Duration,
  nextSmsTimestamp: Duration,
  nextCallTimestamp: Duration,
  showHavingTrouble: Boolean,
  onResendSms: () -> Unit,
  onCall: () -> Unit,
  onWrongNumber: () -> Unit,
  onHavingTrouble: () -> Unit,
): List<EnterCodePanelRow> {
  val currentTime = now()
  val sms = CodeRequestAvailability.of(nextSmsTimestamp, currentTime)
  val call = CodeRequestAvailability.of(nextCallTimestamp, currentTime)
  val locale = Locale.getDefault()

  val smsLabel = if (sms is CodeRequestAvailability.Waiting) {
    stringResource(R.string.RegistrationActivity_resend_sms_available_in, sms.minutes, sms.seconds)
  } else {
    stringResource(R.string.RegistrationActivity_resend_code)
  }
  val callLabel = if (call is CodeRequestAvailability.Waiting) {
    stringResource(R.string.RegistrationActivity_call_me_instead_available_in, call.minutes, call.seconds)
  } else {
    stringResource(R.string.RegistrationActivity_call)
  }

  val wrongNumberLabel = stringResource(R.string.RegistrationActivity_wrong_number)
  val havingTroubleLabel = stringResource(R.string.RegistrationActivity_support_bottom_sheet_title)

  return listOfNotNull(
    EnterCodePanelRow(smsLabel.uppercase(locale), sms.enabled, onResendSms),
    EnterCodePanelRow(callLabel.uppercase(locale), call.enabled, onCall),
    EnterCodePanelRow(wrongNumberLabel.uppercase(locale), true, onWrongNumber),
    if (showHavingTrouble) EnterCodePanelRow(havingTroubleLabel.uppercase(locale), true, onHavingTrouble) else null,
  )
}

/**
 * LightActionPanel's shape -- the bottom half, centred Button rows whose targets hug the label, a
 * chevron on the bottom edge, every pointer event in its area eaten, a tap above it or system back
 * dismissing -- with one addition the shared panel does not have: a row can be disabled, drawn in
 * `contentSecondary` and inert, so a countdown can be shown rather than hidden.
 *
 * Drawn in the screen's own window rather than a dialog so the field underneath keeps its focus; the
 * screen lowers the keyboard while it is open. A row dismisses the panel before it acts.
 */
@Composable
private fun EnterCodePanel(rows: List<EnterCodePanelRow>, onClose: () -> Unit) {
  BackHandler(onBack = onClose)
  val colors = LightThemeTokens.colors

  Box(modifier = Modifier.fillMaxSize()) {
    Box(
      modifier = Modifier
        .align(Alignment.TopCenter)
        .fillMaxWidth()
        .fillMaxHeight(1f - PANEL_HEIGHT_FRACTION)
        .pointerInput(Unit) { detectTapGestures(onTap = { onClose() }) },
    )

    Box(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .fillMaxHeight(PANEL_HEIGHT_FRACTION)
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
        .testTag(ENTER_CODE_PANEL_TAG),
    ) {
      BoxWithConstraints(
        modifier = Modifier
          .fillMaxSize()
          .padding(bottom = CHEVRON_ZONE),
      ) {
        // Four rows are taller than the LP3's half-screen less the chevron, so the list scrolls
        // rather than running under it.
        LightScrollView(
          scrollBarPosition = LightScrollBarPosition.Inside,
          modifier = Modifier
            .align(Alignment.Center)
            .fillMaxWidth()
            .height(min(PANEL_ROW_HEIGHT * rows.size, maxHeight)),
        ) {
          rows.forEach { row ->
            Box(
              modifier = Modifier
                .height(PANEL_ROW_HEIGHT)
                .fillMaxWidth(),
              contentAlignment = Alignment.Center,
            ) {
              Box(
                modifier = Modifier.lightClickable(
                  enabled = row.enabled,
                  onClick = {
                    onClose()
                    row.onSelected()
                  },
                ),
                contentAlignment = Alignment.Center,
              ) {
                LightText(
                  text = row.label,
                  variant = LightTextVariant.Button,
                  lighten = !row.enabled,
                  maxLines = 1,
                )
              }
            }
          }
        }
      }

      Box(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .size(width = CHEVRON_TOUCH_WIDTH, height = CHEVRON_ZONE)
          .lightClickable(onClick = onClose),
        contentAlignment = Alignment.Center,
      ) {
        Image(
          painter = rememberLightTintedPainter(R.drawable.ic_light_chevron_down),
          contentDescription = stringResource(R.string.LightActionPanel__close),
          contentScale = ContentScale.FillBounds,
          modifier = Modifier.size(width = CHEVRON_WIDTH, height = CHEVRON_HEIGHT),
        )
      }
    }
  }
}

/** Counts title taps; from the fourth a toast counts down, and the eighth opens the debug log. */
@Composable
private fun rememberDebugLogTapHandler(): () -> Unit {
  val context = LocalContext.current
  var taps by remember { mutableIntStateOf(0) }
  var previousToast by remember { mutableStateOf<Toast?>(null) }

  return {
    taps++
    if (taps >= DEBUG_TAP_TARGET) {
      context.startActivity(Intent(context, SubmitDebugLogActivity::class.java))
      previousToast?.cancel()
      previousToast = null
    } else if (taps >= DEBUG_TAP_ANNOUNCE) {
      val remaining = DEBUG_TAP_TARGET - taps
      previousToast?.cancel()
      previousToast = Toast.makeText(
        context,
        context.resources.getQuantityString(R.plurals.RegistrationActivity_debug_log_hint, remaining, remaining),
        Toast.LENGTH_SHORT
      ).apply { show() }
    }
  }
}

/** Editable text does not go through LightText, so the screen-height scaling is applied by hand. */
@Composable
private fun TextUnit.scaled(): TextUnit =
  if (this == TextUnit.Unspecified) this else value.designVerticalPxToSp()
