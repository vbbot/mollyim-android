/*
 * Copyright 2024 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.registration.ui.shared

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
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
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.logsubmit.SubmitDebugLogActivity
import java.util.Locale

private const val TAP_TARGET = 8

/** A callback-only action rendered in the fixed LP3 action area. */
data class RegistrationAction(
  val label: String,
  val onClick: () -> Unit,
  val enabled: Boolean = true
)

/**
 * The common Light registration action row. The slots deliberately mirror the LP3's three-action
 * grammar while keeping disabled actions visible and exposed as disabled to accessibility services.
 */
@Composable
fun RegistrationBottomActions(
  left: RegistrationAction? = null,
  center: RegistrationAction? = null,
  right: RegistrationAction? = null,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(4f.gridUnitsAsDp())
      .padding(horizontal = 2f.gridUnitsAsDp()),
    verticalAlignment = Alignment.CenterVertically
  ) {
    val populated = listOf(
      left to Alignment.CenterStart,
      center to Alignment.Center,
      right to Alignment.CenterEnd
    ).filter { it.first != null }

    if (populated.size < 3) {
      populated.forEach { (action, alignment) ->
        RegistrationActionSlot(action, alignment)
      }
    } else {
      RegistrationActionSlot(left, Alignment.CenterStart)
      RegistrationActionSlot(center, Alignment.Center)
      RegistrationActionSlot(right, Alignment.CenterEnd)
    }
  }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.RegistrationActionSlot(
  action: RegistrationAction?,
  alignment: Alignment
) {
  Box(
    modifier = Modifier
      .weight(1f)
      .fillMaxHeight(),
    contentAlignment = alignment
  ) {
    if (action != null) {
      Box(
        modifier = Modifier
          .semantics {
            role = Role.Button
            if (!action.enabled) disabled()
          }
          .lightClickable(enabled = action.enabled, onClick = action.onClick)
          .padding(horizontal = 0.5f.gridUnitsAsDp(), vertical = 0.5f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center
      ) {
        LightText(
          text = action.label.uppercase(Locale.getDefault()),
          variant = LightTextVariant.Button,
          lighten = !action.enabled,
          maxLines = 1
        )
      }
    }
  }
}

/** Small, non-blocking progress presentation for an action that is already in flight. */
@Composable
fun RegistrationProgress(modifier: Modifier = Modifier) {
  LightText(
    text = "…",
    variant = LightTextVariant.Button,
    modifier = modifier.semantics {
      progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate
    }
  )
}

/**
 * An editable Light field that keeps Android's system IME, keyboard options/actions, visual
 * transformation and caller-supplied modifiers. It intentionally owns presentation only: callers
 * continue to own normalization, validation, autofill, persistence and submission.
 */
@Composable
fun RegistrationTextField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  inputModifier: Modifier = Modifier,
  placeholder: String = "",
  enabled: Boolean = true,
  singleLine: Boolean = true,
  minLines: Int = 1,
  isError: Boolean = false,
  supportingText: String? = null,
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
  visualTransformation: VisualTransformation = VisualTransformation.None,
  textStyle: TextStyle = LightThemeTokens.typography.copy,
  trailingContent: (@Composable () -> Unit)? = null
) {
  val colors = LightThemeTokens.colors
  val scaledTextStyle = textStyle.copy(
    fontSize = textStyle.fontSize.scaledDesignTextUnit(),
    lineHeight = textStyle.lineHeight.scaledDesignTextUnit(),
    letterSpacing = textStyle.letterSpacing.scaledDesignTextUnit()
  )
  Column(modifier = modifier.fillMaxWidth()) {
    LightText(
      text = label,
      variant = LightTextVariant.Detail,
      lighten = !enabled,
      modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp())
    )
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        textStyle = scaledTextStyle.copy(color = colors.content),
        cursorBrush = SolidColor(colors.content),
        modifier = inputModifier
          .weight(1f)
          .padding(top = 0.25f.gridUnitsAsDp()),
        decorationBox = { innerTextField ->
          Box {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
              LightText(
                text = placeholder,
                variant = LightTextVariant.Copy,
                lighten = true
              )
            }
            innerTextField()
          }
        }
      )
      trailingContent?.invoke()
    }
    Spacer(Modifier.height(0.5f.gridUnitsAsDp()))
    Spacer(
      modifier = Modifier
        .fillMaxWidth(0.8f)
        .height(3f.designVerticalPxToDp())
        .background(if (isError) colors.contentSecondary else colors.content)
    )
    if (supportingText != null) {
      LightText(
        text = supportingText,
        variant = LightTextVariant.Detail,
        lighten = !isError,
        modifier = Modifier.padding(top = 0.25f.gridUnitsAsDp())
      )
    }
  }
}

@Composable
private fun TextUnit.scaledDesignTextUnit(): TextUnit {
  return if (this == TextUnit.Unspecified) this else value.designVerticalPxToSp()
}

/**
 * The top bar's back affordance. Back is always the SDK's [LightIcons.BACK] glyph, never a text
 * label, and [contentDescription] names where it goes ("Back to welcome screen"), not the glyph.
 */
data class RegistrationBack(
  val contentDescription: String,
  val onBack: () -> Unit
)

/** A base framework for rendering the active v3 registration screens. */
@Composable
fun RegistrationScreen(
  title: String,
  subtitle: String,
  bottomContent: @Composable (BoxScope.() -> Unit),
  back: RegistrationBack? = null,
  mainContent: @Composable ColumnScope.() -> Unit
) {
  RegistrationScreen(title, AnnotatedString(subtitle), bottomContent, back, mainContent)
}

/** A base framework for rendering the active v3 registration screens. */
@Composable
fun RegistrationScreen(
  title: String,
  subtitle: AnnotatedString?,
  bottomContent: @Composable BoxScope.() -> Unit,
  back: RegistrationBack? = null,
  mainContent: @Composable ColumnScope.() -> Unit
) {
  RegistrationScreen(
    title = title,
    back = back,
    rightButton = null,
    topContent = { RegistrationScreenSubtitle(subtitle) },
    bottomContent = bottomContent,
    mainContent = mainContent
  )
}

/**
 * The explanatory copy under the top bar. An [AnnotatedString] so that callers' link and style
 * spans survive; rendered at the Light `Copy` size, scaled for the screen as `LightText` would.
 */
@Composable
fun RegistrationScreenSubtitle(subtitle: AnnotatedString?) {
  if (subtitle != null) {
    val copyStyle = LightThemeTokens.typography.copy.let { style ->
      style.copy(
        fontSize = style.fontSize.scaledDesignTextUnit(),
        lineHeight = style.lineHeight.scaledDesignTextUnit(),
        letterSpacing = style.letterSpacing.scaledDesignTextUnit()
      )
    }
    Text(
      text = subtitle,
      style = copyStyle,
      color = LightThemeTokens.colors.content,
      modifier = Modifier.fillMaxWidth()
    )
  }

  Spacer(modifier = Modifier.height(1.5f.gridUnitsAsDp()))
}

/**
 * Light shell for the active registration graph.
 *
 * The screen title lives in the top bar, in the SDK's `Fine` centre slot -- the same place the
 * reference client puts every title -- rather than as an in-body heading: `Title` is ~79sp on the
 * LP3 and a two-word heading at that size fills the screen. The centre slot is capped at 18 grid
 * units and ellipsises, so callers pass a title that fits rather than a sentence.
 *
 * The title also carries the existing eight-tap debug-log affordance. It is the centre slot's own
 * click target, so it never overlaps the back glyph's. The fixed bottom slot remains callback-owned
 * by each destination.
 */
@Composable
fun RegistrationScreen(
  title: String,
  back: RegistrationBack?,
  rightButton: LightBarButton?,
  topContent: @Composable ColumnScope.() -> Unit,
  bottomContent: @Composable BoxScope.() -> Unit,
  mainContent: @Composable ColumnScope.() -> Unit
) {
  MollyLightTheme {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    var titleTapCount by remember { mutableIntStateOf(0) }
    var previousToast by remember { mutableStateOf<Toast?>(null) }

    val onTitleTapped: () -> Unit = {
      titleTapCount++

      if (titleTapCount >= TAP_TARGET) {
        context.startActivity(Intent(context, SubmitDebugLogActivity::class.java))
        previousToast?.cancel()
        previousToast = null
      } else {
        val remaining = TAP_TARGET - titleTapCount
        previousToast?.cancel()
        previousToast = Toast.makeText(
          context,
          context.resources.getQuantityString(R.plurals.RegistrationActivity_debug_log_hint, remaining, remaining),
          Toast.LENGTH_SHORT
        ).apply { show() }
      }
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(LightThemeTokens.colors.background)
    ) {
      LightTopBar(
        leftButton = back?.let {
          LightBarButton.LightIcon(
            icon = LightIcons.BACK,
            onClick = it.onBack,
            contentDescription = it.contentDescription
          )
        },
        center = LightTopBarCenter.Text(text = title, onClick = onTitleTapped),
        rightButton = rightButton,
        modifier = Modifier
          .background(LightThemeTokens.colors.background)
          .statusBarsPadding()
      )

      LightScrollView(
        scrollState = scrollState,
        scrollBarPosition = LightScrollBarPosition.Outside,
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
      ) {
        // Start padding only: the Outside scroll bar already reserves a 2 grid unit gutter at the
        // end, which is the other half of the 2 unit content margin.
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2f.gridUnitsAsDp(), top = 1f.gridUnitsAsDp(), bottom = 1f.gridUnitsAsDp())
        ) {
          topContent()

          mainContent()
        }
      }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(LightThemeTokens.colors.background)
          .navigationBarsPadding()
      ) {
        bottomContent()
      }
    }
  }
}

@DayNightPreviews
@Composable
private fun RegistrationScreenPreview() {
  Previews.Preview {
    RegistrationScreen(
      title = "Title",
      subtitle = "Subtitle",
      bottomContent = {
        RegistrationBottomActions(right = RegistrationAction("Next", {}))
      },
      back = RegistrationBack(contentDescription = "Back to welcome screen", onBack = {})
    ) {
      LightText("Main content", LightTextVariant.Copy)
    }
  }
}

@DayNightPreviews
@Composable
private fun RegistrationScreenBackAndActionPreview() {
  Previews.Preview {
    RegistrationScreen(
      title = "Title",
      back = RegistrationBack(contentDescription = "Back to welcome screen", onBack = {}),
      rightButton = LightBarButton.Text(text = "PROXY", onClick = {}),
      topContent = { RegistrationScreenSubtitle(AnnotatedString("Top content")) },
      bottomContent = {
        RegistrationBottomActions(center = RegistrationAction("Done", {}))
      }
    ) {
      LightText("Main content", LightTextVariant.Copy, align = TextAlign.Start)
    }
  }
}
