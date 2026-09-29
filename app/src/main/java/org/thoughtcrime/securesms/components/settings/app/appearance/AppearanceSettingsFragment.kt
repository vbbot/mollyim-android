package org.thoughtcrime.securesms.components.settings.app.appearance

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.integerArrayResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import org.signal.core.ui.compose.ComposeFragment
import org.signal.core.ui.compose.DayNightPreviews
import org.signal.core.ui.compose.Previews
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.appearance.navbar.ChooseNavigationBarStyleFragment
import org.thoughtcrime.securesms.keyvalue.SettingsValues
import org.thoughtcrime.securesms.light.LightConfirmationDialog
import org.thoughtcrime.securesms.light.LightSettingsRow
import org.thoughtcrime.securesms.light.LightSettingsScreen
import org.thoughtcrime.securesms.light.LightSettingsSection
import org.thoughtcrime.securesms.light.LightSettingsToggleRow
import org.thoughtcrime.securesms.light.LightSettingsValueRow
import org.thoughtcrime.securesms.light.LightSingleChoiceDialog
import org.thoughtcrime.securesms.light.MollyLightTheme
import org.thoughtcrime.securesms.util.DynamicTheme
import org.thoughtcrime.securesms.util.navigation.safeNavigate

/**
 * Allows the user to change language, theme, etc. from application settings.
 */
class AppearanceSettingsFragment : ComposeFragment() {

  private val viewModel: AppearanceSettingsViewModel by viewModels()

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    childFragmentManager.setFragmentResultListener(ChooseNavigationBarStyleFragment.REQUEST_KEY, viewLifecycleOwner) { key, bundle ->
      if (bundle.getBoolean(key, false)) {
        viewModel.refreshState()
      }
    }
  }

  @Composable
  override fun FragmentContent() {
    val callbacks = remember { Callbacks() }
    val state by viewModel.state.collectAsStateWithLifecycle()

    AppearanceSettingsScreen(
      state = state,
      callbacks = callbacks
    )
  }

  private inner class Callbacks : AppearanceSettingsCallbacks {
    override fun onNavigationClick() {
      requireActivity().onBackPressedDispatcher.onBackPressed()
    }

    override fun onLanguageSelected(selection: String) {
      viewModel.setLanguage(selection)
    }

    override fun onThemeSelected(selection: String, useDynamicColors: Boolean) {
      viewModel.setTheme(activity, SettingsValues.Theme.deserialize(selection), useDynamicColors)
    }

    override fun onChatColorAndWallpaperClick() {
      findNavController().safeNavigate(R.id.action_appearanceSettings_to_wallpaperActivity)
    }

    override fun onAppIconClick() {
      findNavController().safeNavigate(R.id.action_appearanceSettings_to_appIconActivity)
    }

    override fun onMessageFontSizeSelected(selection: String) {
      viewModel.setMessageFontSize(selection.toInt())
    }

    override fun onNavigationBarSizeClick() {
      ChooseNavigationBarStyleFragment().show(childFragmentManager, null)
    }
  }
}

interface AppearanceSettingsCallbacks {
  fun onNavigationClick() = Unit
  fun onLanguageSelected(selection: String) = Unit
  fun onThemeSelected(selection: String, useDynamicColors: Boolean) = Unit
  fun onChatColorAndWallpaperClick() = Unit
  fun onAppIconClick() = Unit
  fun onMessageFontSizeSelected(selection: String) = Unit
  fun onNavigationBarSizeClick() = Unit

  object Empty : AppearanceSettingsCallbacks
}

private enum class AppearanceChoice {
  LANGUAGE,
  THEME,
  MESSAGE_FONT_SIZE
}

@Composable
private fun AppearanceSettingsScreen(
  state: AppearanceSettingsState,
  callbacks: AppearanceSettingsCallbacks
) {
  val languageLabels = stringArrayResource(R.array.language_entries).toList()
  val languageValues = stringArrayResource(R.array.language_values).toList()
  val themeLabels = stringArrayResource(R.array.pref_theme_entries).toList()
  val themeValues = stringArrayResource(R.array.pref_theme_values).toList()
  val messageSizeLabels = stringArrayResource(R.array.pref_message_font_size_entries).toList()
  val messageSizeValues = integerArrayResource(R.array.pref_message_font_size_values).map(Int::toString)
  var choice by remember { mutableStateOf<AppearanceChoice?>(null) }
  var pendingLanguage by remember { mutableStateOf<String?>(null) }

  fun labelFor(values: List<String>, labels: List<String>, selected: String): String {
    return labels.getOrElse(values.indexOf(selected)) { selected }
  }

  MollyLightTheme {
    LightSettingsScreen(
      title = stringResource(R.string.preferences__appearance),
      onBack = callbacks::onNavigationClick
    ) {
      LightSettingsValueRow(
        title = stringResource(R.string.preferences__language),
        value = labelFor(languageValues, languageLabels, state.language),
        valueContentDescription = labelFor(languageValues, languageLabels, state.language),
        onClick = { choice = AppearanceChoice.LANGUAGE }
      )

      LightSettingsValueRow(
        title = stringResource(R.string.preferences__theme),
        value = labelFor(themeValues, themeLabels, state.theme.serialize()),
        valueContentDescription = labelFor(themeValues, themeLabels, state.theme.serialize()),
        onClick = { choice = AppearanceChoice.THEME }
      )

      if (DynamicTheme.isDynamicColorsAvailable()) {
        LightSettingsToggleRow(
          title = stringResource(R.string.preferences_appearance__dynamic_colors),
          summary = stringResource(R.string.preferences_appearance__use_system_colors_for_the_app_theme),
          checked = state.dynamicColors,
          onCheckedChange = { callbacks.onThemeSelected(state.theme.serialize(), it) }
        )
      }

      LightSettingsRow(
        title = stringResource(R.string.preferences__chat_color_and_wallpaper),
        onClick = callbacks::onChatColorAndWallpaperClick
      )

      if (Build.VERSION.SDK_INT >= 26) {
        LightSettingsRow(
          title = stringResource(R.string.preferences__app_icon),
          onClick = callbacks::onAppIconClick
        )
      }

      LightSettingsValueRow(
        title = stringResource(R.string.preferences_chats__message_text_size),
        value = labelFor(messageSizeValues, messageSizeLabels, state.messageFontSize.toString()),
        valueContentDescription = labelFor(messageSizeValues, messageSizeLabels, state.messageFontSize.toString()),
        onClick = { choice = AppearanceChoice.MESSAGE_FONT_SIZE }
      )

      LightSettingsSection(stringResource(R.string.preferences_appearance__navigation_bar))

      val navigationBarLabel = if (state.isCompactNavigationBar) {
        stringResource(R.string.preferences_compact)
      } else {
        stringResource(R.string.preferences_normal)
      }
      LightSettingsValueRow(
        title = stringResource(R.string.preferences_navigation_bar_size),
        value = navigationBarLabel,
        valueContentDescription = navigationBarLabel,
        onClick = callbacks::onNavigationBarSizeClick
      )
    }

    when (choice) {
      AppearanceChoice.LANGUAGE -> LightSingleChoiceDialog(
        title = stringResource(R.string.preferences__language),
        items = languageLabels,
        selected = languageValues.indexOf(state.language),
        confirmAction = false,
        onSelected = { index ->
          pendingLanguage = languageValues[index]
          choice = null
        },
        onDismiss = { choice = null }
      )

      AppearanceChoice.THEME -> LightSingleChoiceDialog(
        title = stringResource(R.string.preferences__theme),
        items = themeLabels,
        selected = themeValues.indexOf(state.theme.serialize()),
        confirmAction = false,
        onSelected = { index -> callbacks.onThemeSelected(themeValues[index], state.dynamicColors) },
        onDismiss = { choice = null }
      )

      AppearanceChoice.MESSAGE_FONT_SIZE -> LightSingleChoiceDialog(
        title = stringResource(R.string.preferences_chats__message_text_size),
        items = messageSizeLabels,
        selected = messageSizeValues.indexOf(state.messageFontSize.toString()),
        confirmAction = false,
        onSelected = { index -> callbacks.onMessageFontSizeSelected(messageSizeValues[index]) },
        onDismiss = { choice = null }
      )

      null -> Unit
    }

    pendingLanguage?.let { language ->
      LightConfirmationDialog(
        title = stringResource(R.string.preferences__language),
        body = stringResource(R.string.preferences_language_change_confirmation_message),
        confirmLabel = stringResource(android.R.string.ok),
        dismissLabel = stringResource(android.R.string.cancel),
        onConfirm = { callbacks.onLanguageSelected(language) },
        onDismiss = { pendingLanguage = null }
      )
    }
  }
}

@DayNightPreviews
@Composable
private fun AppearanceSettingsScreenPreview() {
  Previews.Preview {
    AppearanceSettingsScreen(
      state = AppearanceSettingsState(
        theme = SettingsValues.Theme.SYSTEM,
        dynamicColors = true,
        messageFontSize = 0,
        language = "en-US",
        isCompactNavigationBar = false
      ),
      callbacks = AppearanceSettingsCallbacks.Empty
    )
  }
}
