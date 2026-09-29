/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.components.settings

import android.graphics.Color as AndroidColor
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.models.AsyncSwitch
import org.thoughtcrime.securesms.components.settings.models.Button
import org.thoughtcrime.securesms.components.settings.models.Space
import org.thoughtcrime.securesms.components.settings.models.Text
import org.thoughtcrime.securesms.light.LightMultiChoiceDialog
import org.thoughtcrime.securesms.light.LightSettingsDivider
import org.thoughtcrime.securesms.light.LightSettingsProgressRow
import org.thoughtcrime.securesms.light.LightSettingsRadioRow
import org.thoughtcrime.securesms.light.LightSettingsRow
import org.thoughtcrime.securesms.light.LightSettingsSection
import org.thoughtcrime.securesms.light.LightSettingsToggleRow
import org.thoughtcrime.securesms.light.LightSingleChoiceDialog
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.light.lightTextSp
import org.thoughtcrime.securesms.util.CommunicationActions
import org.thoughtcrime.securesms.util.adapter.mapping.Factory
import org.thoughtcrime.securesms.util.adapter.mapping.MappingAdapter
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder

private const val MULTI_SELECT_SEPARATOR = ", "
private const val LEADING_ICON_UNITS = 2f
private const val TRAILING_ICON_UNITS = 1.25f

/**
 * Explicit Light renderer for the standard settings DSL model set.
 *
 * Opt-in is per screen: a fragment extends [LightDSLSettingsFragment] (or
 * [LightDSLSettingsBottomSheetFragment]) instead of registering this adapter globally, because
 * `MappingAdapter` matches on the model's exact runtime class and a screen that adds its own private
 * model has to register a factory for it, either here or through [registerLightPreference] after the
 * adapter is built.
 *
 * An unmapped model is a hard, loud failure. Rendering it as an empty row would silently delete a
 * working setting, which is worse than a crash on a screen the converter has not finished.
 */
class LightDSLSettingsAdapter : DSLSettingsAdapter() {
  init {
    registerLightPreference(this, ClickPreference::class.java) { model -> ClickPreferenceContent(model) }
    registerLightPreference(this, LongClickPreference::class.java) { model -> LongClickPreferenceContent(model) }
    registerLightPreference(this, TextPreference::class.java) { model -> TextPreferenceContent(model) }
    registerLightPreference(this, LearnMoreTextPreference::class.java) { model -> LearnMorePreferenceContent(model) }
    registerLightPreference(this, RadioListPreference::class.java) { model -> RadioListPreferenceContent(model) }
    registerLightPreference(this, MultiSelectListPreference::class.java) { model -> MultiSelectPreferenceContent(model) }
    registerLightPreference(this, ExternalLinkPreference::class.java) { model -> ExternalLinkPreferenceContent(model) }
    registerLightPreference(this, DividerPreference::class.java) { LightSettingsDivider() }
    registerLightPreference(this, SectionHeaderPreference::class.java) { model -> SectionHeaderPreferenceContent(model) }
    registerLightPreference(this, SwitchPreference::class.java) { model -> SwitchPreferenceContent(model) }
    registerLightPreference(this, RadioPreference::class.java) { model -> RadioPreferenceContent(model) }
    registerLightPreference(this, Text.Model::class.java) { model -> ResolvedText(model.paddableText.text.resolve(LocalContext.current), padded = false) }
    registerLightPreference(this, Space.Model::class.java) { model ->
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(with(LocalDensity.current) { model.space.pixels.toDp() })
      )
    }
    registerLightPreference(this, Button.Model.Primary::class.java) { model -> ButtonPreferenceContent(model) }
    registerLightPreference(this, Button.Model.PrimaryWrapped::class.java) { model -> ButtonPreferenceContent(model) }
    registerLightPreference(this, Button.Model.Tonal::class.java) { model -> ButtonPreferenceContent(model) }
    registerLightPreference(this, Button.Model.TonalWrapped::class.java) { model -> ButtonPreferenceContent(model) }
    registerLightPreference(this, Button.Model.SecondaryNoOutline::class.java) { model -> ButtonPreferenceContent(model) }
    registerLightPreference(this, AsyncSwitch.Model::class.java) { model -> AsyncSwitchPreferenceContent(model) }
  }

  override fun getItemViewType(position: Int): Int = requireLightViewType(getItemTypes(), getItem(position))

  private companion object {
    private val TAG = Log.tag(RadioListPreference::class.java)
  }
}

/**
 * Exact-runtime-class lookup. There is no fallback row on purpose: a functional setting rendered as
 * an empty placeholder is a silently deleted feature, so an unmapped model is named out loud.
 */
internal fun requireLightViewType(registered: Map<Class<*>, Int>, model: Any): Int =
  registered[model.javaClass] ?: throw IllegalStateException(
    "No Light settings factory for ${model.javaClass.name}. A screen opted into " +
      "LightDSLSettingsAdapter has to register every model it renders, including its own " +
      "private ones, via registerLightPreference()."
  )

/** Mirrors `MultiSelectListPreferenceViewHolder`: checked labels, comma separated, order preserved. */
internal fun multiSelectSummary(listItems: Array<String>, selected: BooleanArray): String =
  selected.indices
    .mapNotNull { index -> listItems.getOrNull(index)?.takeIf { selected[index] } }
    .joinToString(MULTI_SELECT_SEPARATOR)

/** Owner-local seam for private or screen-specific DSL models. Last registration intentionally wins. */
internal fun <T : MappingModel<T>> registerLightPreference(
  adapter: MappingAdapter,
  modelClass: Class<T>,
  content: @Composable (T) -> Unit
) {
  adapter.registerFactory(modelClass, LightComposeFactory(content))
}

private class LightComposeFactory<T : MappingModel<T>>(
  private val content: @Composable (T) -> Unit
) : Factory<T> {
  override fun createViewHolder(parent: ViewGroup): MappingViewHolder<T> {
    return LightComposeViewHolder(ComposeView(parent.context), content)
  }
}

private class LightComposeViewHolder<T : MappingModel<T>>(
  composeView: ComposeView,
  private val content: @Composable (T) -> Unit
) : MappingViewHolder<T>(composeView) {
  private var model: T? by mutableStateOf(null)

  init {
    composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
    composeView.setContent {
      val current = model ?: return@setContent
      MollyLightTheme { content(current) }
    }
  }

  override fun bind(model: T) {
    this.model = model
  }
}

@Composable
private fun ClickPreferenceContent(model: ClickPreference) {
  LightSettingsRow(
    title = model.title.resolve(LocalContext.current),
    summary = model.summary?.resolve(LocalContext.current),
    enabled = model.isEnabled,
    onClick = model.onClick,
    onDisabledClick = model.onDisabledClicked,
    onLongClick = model.onLongClick,
    leading = model.icon?.let { icon -> { LightResolvedSettingsIcon(icon) } },
    trailing = model.iconEnd?.let { icon -> { LightResolvedSettingsIcon(icon) } }
  )
}

@Composable
private fun LongClickPreferenceContent(model: LongClickPreference) {
  LightSettingsRow(
    title = model.title.resolve(LocalContext.current),
    summary = model.summary?.resolve(LocalContext.current),
    enabled = model.isEnabled,
    onClick = null,
    // The legacy holder's long click always consumed the gesture.
    onLongClick = {
      model.onLongClick()
      true
    },
    leading = model.icon?.let { icon -> { LightResolvedSettingsIcon(icon) } }
  )
}

@Composable
private fun TextPreferenceContent(model: TextPreference) {
  val context = LocalContext.current
  val title = model.title?.resolve(context)
  val summary = model.summary?.resolve(context)

  if (title != null) {
    LightSettingsRow(
      title = title,
      summary = summary,
      enabled = model.isEnabled,
      onClick = null,
      leading = model.icon?.let { icon -> { LightResolvedSettingsIcon(icon) } }
    )
  } else if (summary != null) {
    ResolvedText(summary, padded = true)
  }
}

@Composable
private fun LearnMorePreferenceContent(model: LearnMoreTextPreference) {
  val context = LocalContext.current
  LightSettingsRow(
    title = model.title?.resolve(context) ?: "",
    summary = model.summary?.resolve(context),
    onClick = model.onClick
  )
}

@Composable
private fun RadioListPreferenceContent(model: RadioListPreference) {
  val context = LocalContext.current
  var showingDialog by remember(model) { mutableStateOf(false) }
  val summary = model.listItems.getOrNull(model.selected)
  if (summary == null) {
    Log.w(Log.tag(LightDSLSettingsAdapter::class.java), "Detected a radio list without a default selection: ${model.dialogTitle}")
  }

  LightSettingsRow(
    title = model.title.resolve(context),
    summary = summary,
    enabled = model.isEnabled,
    onClick = { showingDialog = true },
    leading = model.icon?.let { icon -> { LightResolvedSettingsIcon(icon) } },
    trailing = model.iconEnd?.let { icon -> { LightResolvedSettingsIcon(icon) } }
  )

  if (showingDialog) {
    LightSingleChoiceDialog(
      title = model.dialogTitle.resolve(context),
      items = model.listItems.toList(),
      selected = model.selected,
      confirmAction = model.confirmAction,
      onSelected = model.onSelected,
      onDismiss = { showingDialog = false }
    )
  }
}

@Composable
private fun MultiSelectPreferenceContent(model: MultiSelectListPreference) {
  val context = LocalContext.current
  var showingDialog by remember(model) { mutableStateOf(false) }
  val summary = multiSelectSummary(model.listItems, model.selected)

  LightSettingsRow(
    title = model.title.resolve(context),
    summary = summary.ifBlank { stringResource(R.string.preferences__none) },
    enabled = model.isEnabled,
    onClick = { showingDialog = true }
  )

  if (showingDialog) {
    LightMultiChoiceDialog(
      title = model.title.resolve(context),
      items = model.listItems.toList(),
      selected = model.selected,
      onSelected = model.onSelected,
      onDismiss = { showingDialog = false }
    )
  }
}

@Composable
private fun SwitchPreferenceContent(model: SwitchPreference) {
  val context = LocalContext.current
  LightSettingsToggleRow(
    title = model.title.resolve(context),
    summary = model.summary?.resolve(context),
    checked = model.isChecked,
    enabled = model.isEnabled,
    leading = model.icon?.let { icon -> { LightResolvedSettingsIcon(icon) } },
    onCheckedChange = { target ->
      if (model.onToggle(target)) {
        model.onClick()
      }
    }
  )
}

@Composable
private fun RadioPreferenceContent(model: RadioPreference) {
  val context = LocalContext.current
  LightSettingsRadioRow(
    title = model.title.resolve(context),
    summary = model.summary?.resolve(context),
    selected = model.isChecked,
    enabled = model.isEnabled,
    onClick = model.onClick
  )
}

@Composable
private fun ExternalLinkPreferenceContent(model: ExternalLinkPreference) {
  val context = LocalContext.current
  LightSettingsRow(
    title = model.title.resolve(context),
    onClick = { CommunicationActions.openBrowserLink(context, context.getString(model.linkId)) },
    leading = model.icon?.let { icon -> { LightResolvedSettingsIcon(icon) } },
    trailing = { LightTrailingSettingsIcon(R.drawable.symbol_open_20) }
  )
}

@Composable
private fun SectionHeaderPreferenceContent(model: SectionHeaderPreference) {
  val context = LocalContext.current
  if (model.onClick == null) {
    LightSettingsSection(model.title.resolve(context))
  } else {
    LightSettingsRow(
      title = model.title.resolve(context),
      onClick = model.onClick,
      trailing = model.iconEnd?.let { icon -> { LightResolvedSettingsIcon(icon) } } ?: {
        LightTrailingSettingsIcon(R.drawable.symbol_chevron_right_compact_bold_16)
      }
    )
  }
}

@Composable
private fun <T : Button.Model<T>> ButtonPreferenceContent(model: T) {
  val context = LocalContext.current
  var clicked by remember(model) { mutableStateOf(false) }
  LightSettingsRow(
    title = model.title?.resolve(context) ?: "",
    enabled = model.isEnabled && !clicked,
    leading = model.icon?.let { icon -> { LightResolvedSettingsIcon(icon) } },
    onClick = {
      if (model.disableOnClick) clicked = true
      model.onClick()
    }
  )
}

@Composable
private fun AsyncSwitchPreferenceContent(model: AsyncSwitch.Model) {
  val title = model.title.resolve(LocalContext.current)
  if (model.isProcessing) {
    LightSettingsProgressRow(title = title)
  } else {
    LightSettingsToggleRow(
      title = title,
      checked = model.isChecked,
      enabled = model.isEnabled,
      onCheckedChange = { model.onClick() }
    )
  }
}

@Composable
internal fun LightResolvedSettingsIcon(icon: DSLSettingsIcon) {
  val context = LocalContext.current
  val drawable = remember(icon, context) { icon.resolve(context) }
  AndroidView(
    factory = { viewContext ->
      ImageView(viewContext).apply { importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO }
    },
    update = { it.setImageDrawable(drawable) },
    modifier = Modifier.size(LEADING_ICON_UNITS.gridUnitsAsDp())
  )
}

/** Decorative trailing glyph; the row's own semantics already describe the action. */
@Composable
private fun LightTrailingSettingsIcon(@DrawableRes drawableRes: Int) {
  val context = LocalContext.current
  val tint = LightThemeTokens.colors.content.toArgb()
  val drawable = remember(drawableRes, context, tint) {
    ContextCompat.getDrawable(context, drawableRes)?.mutate()?.also { DrawableCompat.setTint(it, tint) }
  }
  AndroidView(
    factory = { viewContext ->
      ImageView(viewContext).apply { importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO }
    },
    update = { it.setImageDrawable(drawable) },
    modifier = Modifier.size(TRAILING_ICON_UNITS.gridUnitsAsDp())
  )
}

@Composable
private fun ResolvedText(text: CharSequence, padded: Boolean) {
  if (text is Spanned) {
    val context = LocalContext.current
    val color = LightThemeTokens.colors.content.toArgb()
    val hasLinks = text.getSpans(0, text.length, ClickableSpan::class.java).isNotEmpty()
    val textSizeSp = lightTextSp(context, LightTextVariant.Detail)
    AndroidView(
      factory = { viewContext ->
        TextView(viewContext).apply {
          setTextColor(color)
          setLinkTextColor(color)
          setBackgroundColor(AndroidColor.TRANSPARENT)
          includeFontPadding = false
        }
      },
      update = { view ->
        view.text = text
        view.textSize = textSizeSp
        view.movementMethod = if (hasLinks) LinkMovementMethod.getInstance() else null
      },
      modifier = Modifier
        .fillMaxWidth()
        .then(if (padded) Modifier.padding(horizontal = 1.5f.gridUnitsAsDp(), vertical = 0.7f.gridUnitsAsDp()) else Modifier)
    )
  } else {
    LightText(
      text = text.toString(),
      variant = LightTextVariant.Detail,
      modifier = Modifier
        .fillMaxWidth()
        .then(if (padded) Modifier.padding(horizontal = 1.5f.gridUnitsAsDp(), vertical = 0.7f.gridUnitsAsDp()) else Modifier)
    )
  }
}
