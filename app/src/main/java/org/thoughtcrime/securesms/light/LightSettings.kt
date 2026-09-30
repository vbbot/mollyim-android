/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.light

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.onLongClick as semanticsOnLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.content.Context
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightHapticFeedback
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LocalHapticsEnabled
import com.thelightphone.sdk.ui.LightScrollBarPosition
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.designVerticalPxToSp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightTypography
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import org.thoughtcrime.securesms.R

private val LONG_PRESS_HAPTIC = 150.milliseconds

/**
 * App-local Light presentation for settings.
 *
 * Settings keep their existing fragment, ViewModel and navigation owners. These composables only
 * replace app-owned chrome and rows; callers continue to own validation, side effects and results.
 */
@Composable
fun LightSettingsScreen(
  title: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit
) {
  Column(
    modifier = modifier
      .fillMaxSize()
      .background(LightThemeTokens.colors.background)
  ) {
    LightTopBar(
      leftButton = LightBarButton.LightIcon(
        icon = LightIcons.BACK,
        onClick = onBack,
        contentDescription = stringResource(R.string.CallScreenTopBar__go_back)
      ),
      center = LightTopBarCenter.Text(title.toString())
    )

    LightScrollView(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(),
      scrollBarPosition = LightScrollBarPosition.Inside,
      scrollState = rememberScrollState(),
      content = content
    )
  }
}

@Composable
fun LightSettingsSection(title: CharSequence, modifier: Modifier = Modifier) {
  LightSettingsText(
    text = if (title is Spanned) title else title.toString().uppercase(Locale.getDefault()),
    variant = LightTextVariant.Superfine,
    color = LightThemeTokens.colors.contentSecondary,
    modifier = modifier
      .fillMaxWidth()
      .padding(
        start = 1.5f.gridUnitsAsDp(),
        end = 1.5f.gridUnitsAsDp(),
        top = 1.25f.gridUnitsAsDp(),
        bottom = 0.5f.gridUnitsAsDp()
      )
  )
}

@Composable
fun LightSettingsDivider(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 1.5f.gridUnitsAsDp(), vertical = 0.5f.gridUnitsAsDp())
      .height(1.dp)
      .background(LightThemeTokens.colors.contentSecondary)
  )
}

/**
 * A single settings row.
 *
 * Behaviour is deliberately modelled on the legacy `dsl_preference_item` view holders:
 *
 *  - a disabled row stays actionable when it carries an [onDisabledClick] explanation. Legacy
 *    `ClickPreferenceViewHolder` re-enables `itemView` in exactly that case so the explanation is
 *    reachable, so the row is deliberately *not* marked `disabled()` in the semantics tree while it
 *    is actionable — marking it disabled makes TalkBack drop the action.
 *  - long click follows `View.OnLongClickListener`: the callback returns whether it consumed the
 *    gesture, and returning `false` still delivers the click, exactly as the legacy holder did with
 *    `setOnLongClickListener { model.onLongClick?.invoke() ?: false }`.
 *  - long click respects [enabled]; a disabled row never fires it.
 */
@Composable
fun LightSettingsRow(
  title: CharSequence,
  onClick: (() -> Unit)?,
  modifier: Modifier = Modifier,
  summary: CharSequence? = null,
  enabled: Boolean = true,
  onDisabledClick: (() -> Unit)? = null,
  onLongClick: (() -> Boolean)? = null,
  leading: (@Composable () -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
  role: Role? = null
) {
  val click = if (enabled) onClick else onDisabledClick
  val longClick = if (enabled) onLongClick else null
  val actionable = click != null || longClick != null
  val contentColor = if (enabled) LightThemeTokens.colors.content else LightThemeTokens.colors.contentSecondary

  val currentClick by rememberUpdatedState(click)
  val currentLongClick by rememberUpdatedState(longClick)
  val longPressLabel = stringResource(R.string.StickerManagement_accessibility_open_context_menu)
  val context = LocalContext.current
  val hapticsEnabled = LocalHapticsEnabled.current

  Row(
    modifier = modifier
      .fillMaxWidth()
      .then(
        if (actionable) {
          Modifier.pointerInput(enabled, actionable) {
            detectTapGestures(
              onTap = {
                if (hapticsEnabled) {
                  LightHapticFeedback.click(context)
                }
                currentClick?.invoke()
              },
              onLongPress = currentLongClick?.let { longPress ->
                {
                  if (hapticsEnabled) {
                    LightHapticFeedback.vibrateForDuration(context, LONG_PRESS_HAPTIC)
                  }
                  if (!longPress()) {
                    // Not consumed: the legacy listener contract still delivers the click.
                    currentClick?.invoke()
                  }
                }
              }
            )
          }
        } else {
          Modifier
        }
      )
      .then(
        if (actionable || !enabled) {
          Modifier.semantics {
            if (role != null) {
              this.role = role
            }
            val action = click
            if (action != null) {
              semanticsOnClick {
                action()
                true
              }
            }
            val longPress = longClick
            if (longPress != null) {
              semanticsOnLongClick(label = longPressLabel) { longPress() }
            }
            // Marked disabled whenever the row is disabled, not only when it is inert: a row that
            // explains *why* it is unavailable is still unavailable, and `disabled()` only reports
            // state -- it does not suppress the click, so the explanation is still delivered.
            if (!enabled) {
              disabled()
            }
          }
        } else {
          Modifier
        }
      )
      .padding(horizontal = 1.5f.gridUnitsAsDp(), vertical = 0.7f.gridUnitsAsDp()),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (leading != null) {
      Box(modifier = Modifier.size(2f.gridUnitsAsDp()), contentAlignment = Alignment.Center) {
        leading()
      }
      Spacer(modifier = Modifier.width(0.75f.gridUnitsAsDp()))
    }

    Column(modifier = Modifier.weight(1f)) {
      LightSettingsText(
        text = title,
        variant = LightTextVariant.Copy,
        color = contentColor
      )
      if (!summary.isNullOrBlank()) {
        LightSettingsText(
          text = summary,
          variant = LightTextVariant.Detail,
          color = if (enabled) LightThemeTokens.colors.contentSecondary else contentColor,
          modifier = Modifier.padding(top = 2.dp)
        )
      }
    }

    if (trailing != null) {
      Spacer(modifier = Modifier.width(0.75f.gridUnitsAsDp()))
      trailing()
    }
  }
}

@Composable
fun LightSettingsValueRow(
  title: CharSequence,
  value: CharSequence,
  valueContentDescription: CharSequence,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  summary: CharSequence? = null,
  enabled: Boolean = true,
  onDisabledClick: (() -> Unit)? = null,
  onLongClick: (() -> Boolean)? = null
) {
  LightSettingsRow(
    title = title,
    summary = summary,
    enabled = enabled,
    onClick = onClick,
    onDisabledClick = onDisabledClick,
    onLongClick = onLongClick,
    modifier = modifier,
    trailing = {
      LightText(
        text = value.toString(),
        variant = LightTextVariant.Detail,
        modifier = Modifier.semantics { contentDescription = valueContentDescription.toString() }
      )
    }
  )
}

@Composable
private fun LightSettingsText(
  text: CharSequence,
  variant: LightTextVariant,
  color: Color,
  modifier: Modifier = Modifier
) {
  if (text is Spanned) {
    val hasLinks = text.getSpans(0, text.length, ClickableSpan::class.java).isNotEmpty()
    val colorArgb = color.toArgb()
    // The spans are only preserved by a real TextView, so the type scale has to be applied by hand.
    // The SDK scales every variant by screen height; reuse that arithmetic so a spanned summary does
    // not drift away from the unscaled Compose text beside it.
    val textSizeSp = lightTextSp(LocalContext.current, variant)
    AndroidView(
      factory = { context ->
        TextView(context).apply {
          setBackgroundColor(android.graphics.Color.TRANSPARENT)
          includeFontPadding = false
        }
      },
      update = { view ->
        view.text = text
        view.setTextColor(colorArgb)
        view.setLinkTextColor(colorArgb)
        view.textSize = textSizeSp
        view.movementMethod = if (hasLinks) LinkMovementMethod.getInstance() else null
      },
      modifier = modifier.fillMaxWidth()
    )
  } else {
    LightText(
      text = text.toString(),
      variant = variant,
      color = color,
      modifier = modifier
    )
  }
}

/** The size [LightText] would use for [variant], in sp, for a plain Android [TextView]. */
internal fun lightTextSp(context: Context, variant: LightTextVariant): Float {
  val typography = lightTypography(context)
  val style = when (variant) {
    LightTextVariant.Title -> typography.title
    LightTextVariant.Subtitle -> typography.subtitle
    LightTextVariant.Heading -> typography.heading
    LightTextVariant.Subheading -> typography.subheading
    LightTextVariant.Copy -> typography.copy
    LightTextVariant.Button -> typography.button
    LightTextVariant.Paragraph -> typography.paragraph
    LightTextVariant.ParagraphWide -> typography.paragraphWide
    LightTextVariant.Detail -> typography.detail
    LightTextVariant.Fine -> typography.fine
    LightTextVariant.Superfine -> typography.superfine
    LightTextVariant.Micro -> typography.micro
  }
  return style.fontSize.value.designVerticalPxToSp(context)
}

@Composable
fun LightSettingsToggleRow(
  title: CharSequence,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  summary: CharSequence? = null,
  enabled: Boolean = true,
  onDisabledClick: (() -> Unit)? = null,
  leading: (@Composable () -> Unit)? = null
) {
  LightSettingsRow(
    title = title,
    summary = summary,
    enabled = enabled,
    onClick = { onCheckedChange(!checked) },
    onDisabledClick = onDisabledClick,
    leading = leading,
    role = Role.Switch,
    modifier = modifier.semantics { toggleableState = ToggleableState(checked) },
    trailing = {
      LightText(
        text = stringResource(if (checked) R.string.preferences_on else R.string.preferences_off).uppercase(Locale.getDefault()),
        variant = LightTextVariant.Fine,
        lighten = !enabled
      )
    }
  )
}

@Composable
fun LightSettingsRadioRow(
  title: CharSequence,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  summary: CharSequence? = null,
  enabled: Boolean = true
) {
  LightSettingsRow(
    title = title,
    summary = summary,
    enabled = enabled,
    onClick = onClick,
    role = Role.RadioButton,
    modifier = modifier.semantics { this.selected = selected },
    trailing = {
      LightText(
        text = if (selected) "●" else "○",
        variant = LightTextVariant.Copy,
        lighten = !enabled
      )
    }
  )
}

@Composable
fun LightSettingsProgressRow(
  title: CharSequence,
  modifier: Modifier = Modifier,
  summary: String? = null
) {
  LightSettingsRow(
    title = title,
    summary = summary,
    onClick = null,
    modifier = modifier,
    trailing = {
      CircularProgressIndicator(
        modifier = Modifier.size(1.5f.gridUnitsAsDp()),
        color = LightThemeTokens.colors.content,
        strokeWidth = 2.dp
      )
    }
  )
}

/** Full-screen Light confirmation. Destructive work is still performed only by [onConfirm]. */
@Composable
fun LightConfirmationDialog(
  title: String,
  body: String,
  confirmLabel: String,
  dismissLabel: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
  destructive: Boolean = false
) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    MollyLightTheme {
      Column(
        modifier = modifier
          .fillMaxSize()
          .background(LightThemeTokens.colors.background)
      ) {
        LightTopBar(
          leftButton = LightBarButton.LightIcon(
            icon = LightIcons.CLOSE,
            onClick = onDismiss,
            contentDescription = dismissLabel
          ),
          center = LightTopBarCenter.Text(title.toString())
        )
        Column(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 2f.gridUnitsAsDp()),
          verticalArrangement = Arrangement.Center,
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          LightText(
            text = body,
            variant = LightTextVariant.Paragraph,
            align = TextAlign.Center,
            color = if (destructive) Color(0xFFFF6B6B) else null
          )
        }
        LightBottomBar(
          items = listOf(
            LightBarButton.Text(dismissLabel.uppercase(Locale.getDefault()), onClick = onDismiss),
            LightBarButton.Text(
              confirmLabel.uppercase(Locale.getDefault()),
              onClick = {
                onConfirm()
                onDismiss()
              }
            )
          )
        )
      }
    }
  }
}

@Composable
fun LightSingleChoiceDialog(
  title: CharSequence,
  items: List<String>,
  selected: Int,
  confirmAction: Boolean,
  onSelected: (Int) -> Unit,
  onDismiss: () -> Unit
) {
  var pending by remember(items, selected, confirmAction) { mutableStateOf(if (confirmAction) -1 else selected) }
  val cancelLabel = stringResource(android.R.string.cancel)
  val okLabel = stringResource(android.R.string.ok)
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    MollyLightTheme {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(LightThemeTokens.colors.background)
      ) {
        LightTopBar(
          leftButton = LightBarButton.LightIcon(LightIcons.CLOSE, onDismiss, contentDescription = cancelLabel),
          center = LightTopBarCenter.Text(title.toString())
        )
        LightScrollView(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          scrollBarPosition = LightScrollBarPosition.Inside
        ) {
          items.forEachIndexed { index, item ->
            LightSettingsRadioRow(
              title = item,
              selected = (if (pending >= 0) pending else selected) == index,
              onClick = {
                if (confirmAction) {
                  pending = index
                } else {
                  onSelected(index)
                  onDismiss()
                }
              }
            )
          }
        }
        if (confirmAction) {
          LightBottomBar(
            items = listOf(
              LightBarButton.Text(cancelLabel.uppercase(Locale.getDefault()), onClick = onDismiss),
              LightBarButton.Text(
                okLabel.uppercase(Locale.getDefault()),
                onClick = {
                  onSelected(pending)
                  onDismiss()
                }
              )
            )
          )
        }
      }
    }
  }
}

@Composable
fun LightMultiChoiceDialog(
  title: CharSequence,
  items: List<String>,
  selected: BooleanArray,
  onSelected: (BooleanArray) -> Unit,
  onDismiss: () -> Unit
) {
  var pending by remember(items, selected) { mutableStateOf(selected.copyOf()) }
  val cancelLabel = stringResource(android.R.string.cancel)
  val okLabel = stringResource(android.R.string.ok)
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    MollyLightTheme {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .background(LightThemeTokens.colors.background)
      ) {
        LightTopBar(
          leftButton = LightBarButton.LightIcon(LightIcons.CLOSE, onDismiss, contentDescription = cancelLabel),
          center = LightTopBarCenter.Text(title.toString())
        )
        LightScrollView(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          scrollBarPosition = LightScrollBarPosition.Inside
        ) {
          items.forEachIndexed { index, item ->
            LightSettingsToggleRow(
              title = item,
              checked = pending.getOrElse(index) { false },
              onCheckedChange = { value ->
                pending = pending.copyOf().also { it[index] = value }
              }
            )
          }
        }
        LightBottomBar(
          items = listOf(
            LightBarButton.Text(cancelLabel.uppercase(Locale.getDefault()), onClick = onDismiss),
            LightBarButton.Text(
              okLabel.uppercase(Locale.getDefault()),
              onClick = {
                onSelected(pending.copyOf())
                onDismiss()
              }
            )
          )
        )
      }
    }
  }
}
