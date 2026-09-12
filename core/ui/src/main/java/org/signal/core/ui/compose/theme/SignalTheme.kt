package org.signal.core.ui.compose.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import org.signal.core.ui.CoreUiDependencies
import org.signal.core.ui.R
import org.signal.core.ui.compose.ProvideIncognitoKeyboard

private val typography = Typography().run {
  copy(
    headlineLarge = headlineLarge.copy(
      fontSize = 32.sp,
      lineHeight = 40.sp,
      letterSpacing = 0.sp
    ),
    headlineMedium = headlineMedium.copy(
      fontSize = 28.sp,
      lineHeight = 36.sp,
      letterSpacing = 0.sp
    ),
    titleLarge = titleLarge.copy(
      fontSize = 22.sp,
      lineHeight = 28.sp,
      letterSpacing = 0.sp
    ),
    titleMedium = titleMedium.copy(
      fontSize = 18.sp,
      lineHeight = 24.sp,
      letterSpacing = 0.0125.sp,
      fontFamily = FontFamily.SansSerif,
      fontStyle = FontStyle.Normal
    ),
    titleSmall = titleSmall.copy(
      fontSize = 16.sp,
      lineHeight = 22.sp,
      letterSpacing = 0.0125.sp
    ),
    bodyLarge = bodyLarge.copy(
      fontSize = 16.sp,
      lineHeight = 22.sp,
      letterSpacing = 0.0125.sp
    ),
    bodyMedium = bodyMedium.copy(
      fontSize = 14.sp,
      lineHeight = 20.sp,
      letterSpacing = 0.0107.sp
    ),
    bodySmall = bodySmall.copy(
      fontSize = 13.sp,
      lineHeight = 16.sp,
      letterSpacing = 0.0192.sp
    ),
    labelLarge = labelLarge.copy(
      fontSize = 14.sp,
      lineHeight = 20.sp,
      letterSpacing = 0.0107.sp
    ),
    labelMedium = labelMedium.copy(
      fontSize = 13.sp,
      lineHeight = 16.sp,
      letterSpacing = 0.0192.sp
    ),
    labelSmall = labelSmall.copy(
      fontSize = 12.sp,
      lineHeight = 16.sp,
      letterSpacing = 0.025.sp
    )
  )
}

/**
 * LIGHT PHONE MONOCHROME PALETTE.
 *
 * The Compose half of the same palette core/ui/res/values/molly_colors.xml installs for View-land:
 * true-black surfaces and a neutral grey ramp, with `error` folded onto the same tone pairing as
 * `primary` because hue carries no information on the LP3's display.
 *
 * This is byte-identical to [darkColorScheme] on purpose. The Light Phone III has no day mode, and
 * `SignalTheme` picks between the two from `LocalConfiguration.uiMode`, which follows the system
 * setting -- so the two schemes have to agree or the app changes appearance when the system does.
 */
private val lightColorScheme = lightColorScheme(
  inversePrimary = Color(0xFF1A1A1A),
  surfaceDim = Color(0xFF000000),
  inverseSurface = Color(0xFFE2E2E2),
  surfaceBright = Color(0xFF141414),
  surfaceContainerLowest = Color(0xFF020202),
  surfaceContainerLow = Color(0xFF050505),
  surfaceContainer = Color(0xFF0A0A0A),
  surfaceContainerHigh = Color(0xFF0F0F0F),
  surfaceContainerHighest = Color(0xFF141414),
  inverseOnSurface = Color(0xFF303030),
  outlineVariant = Color(0xFF474747),
  onError = Color(0xFF1A1A1A),
  onErrorContainer = Color(0xFFE9E9E9),
  tertiary = Color(0xFFB9B9B9),
  onTertiary = Color(0xFF303030),
  tertiaryContainer = Color(0xFF141414),
  onTertiaryContainer = Color(0xFFE5E5E5),
  primary = Color(0xFFE9E9E9),
  primaryContainer = Color(0xFF141414),
  secondary = Color(0xFFC6C6C6),
  secondaryContainer = Color(0xFF141414),
  surface = Color(0xFF000000),
  surfaceVariant = Color(0xFF0F0F0F),
  background = Color(0xFF000000),
  error = Color(0xFFE9E9E9),
  errorContainer = Color(0xFF141414),
  onPrimary = Color(0xFF1A1A1A),
  onPrimaryContainer = Color(0xFFE9E9E9),
  onSecondary = Color(0xFF262626),
  onSecondaryContainer = Color(0xFFE2E2E2),
  onSurface = Color(0xFFE9E9E9),
  onSurfaceVariant = Color(0xFFC6C6C6),
  onBackground = Color(0xFFE9E9E9),
  outline = Color(0xFF5E5E5E)
)

/**
 * LIGHT PHONE MONOCHROME PALETTE.
 *
 * `SignalTheme.colors` is a second, parallel token set to the Material `ColorScheme` above -- it is
 * what paints dropdown menus, the surface-elevation ramp and the wallpaper-overlay tints -- so it
 * has to be converted too or those surfaces keep Signal's lavender. Declared as a getter rather
 * than a copy so the day and night token sets cannot drift apart, and so it resolves after
 * [darkExtendedColors] is initialised.
 */
private val lightExtendedColors: ExtendedColors get() = darkExtendedColors

private val darkExtendedColors = ExtendedColors(
  neutralSurface = Color(0x14FFFFFF),
  colorOnCustom = Color(0xFFFFFFFF),
  colorOnCustomVariant = Color(0xB3FFFFFF),
  colorSurface1 = Color(0xFF050505),
  colorSurface2 = Color(0xFF0A0A0A),
  colorSurface3 = Color(0xFF0F0F0F),
  colorSurface4 = Color(0xFF141414),
  colorSurface5 = Color(0xFF181818),
  colorTransparent1 = Color(0x0AFFFFFF),
  colorTransparent2 = Color(0x1FFFFFFF),
  colorTransparent3 = Color(0x29FFFFFF),
  colorTransparent4 = Color(0x7AFFFFFF),
  colorTransparent5 = Color(0xB8FFFFFF),
  colorNeutral = Color(0xFF000000),
  colorNeutralVariant = Color(0xFF5C5C5C),
  colorTransparentInverse1 = Color(0x0A000000),
  colorTransparentInverse2 = Color(0x14000000),
  colorTransparentInverse3 = Color(0x29000000),
  colorTransparentInverse4 = Color(0xB8000000),
  colorTransparentInverse5 = Color(0xF5000000),
  colorNeutralInverse = Color(0xE0FFFFFF),
  colorNeutralVariantInverse = Color(0xA3FFFFFF),
  colorWarning = Color(0x1FC6C6C6),
  colorOnWarning = Color(0xFFC6C6C6)
)

/** @see lightColorScheme -- the two are deliberately identical. */
private val darkColorScheme = darkColorScheme(
  inversePrimary = Color(0xFF1A1A1A),
  surfaceDim = Color(0xFF000000),
  inverseSurface = Color(0xFFE2E2E2),
  surfaceBright = Color(0xFF141414),
  surfaceContainerLowest = Color(0xFF020202),
  surfaceContainerLow = Color(0xFF050505),
  surfaceContainer = Color(0xFF0A0A0A),
  surfaceContainerHigh = Color(0xFF0F0F0F),
  surfaceContainerHighest = Color(0xFF141414),
  inverseOnSurface = Color(0xFF303030),
  outlineVariant = Color(0xFF474747),
  onError = Color(0xFF1A1A1A),
  onErrorContainer = Color(0xFFE9E9E9),
  tertiary = Color(0xFFB9B9B9),
  onTertiary = Color(0xFF303030),
  tertiaryContainer = Color(0xFF141414),
  onTertiaryContainer = Color(0xFFE5E5E5),
  primary = Color(0xFFE9E9E9),
  primaryContainer = Color(0xFF141414),
  secondary = Color(0xFFC6C6C6),
  secondaryContainer = Color(0xFF141414),
  surface = Color(0xFF000000),
  surfaceVariant = Color(0xFF0F0F0F),
  background = Color(0xFF000000),
  error = Color(0xFFE9E9E9),
  errorContainer = Color(0xFF141414),
  onPrimary = Color(0xFF1A1A1A),
  onPrimaryContainer = Color(0xFFE9E9E9),
  onSecondary = Color(0xFF262626),
  onSecondaryContainer = Color(0xFFE2E2E2),
  onSurface = Color(0xFFE9E9E9),
  onSurfaceVariant = Color(0xFFC6C6C6),
  onBackground = Color(0xFFE9E9E9),
  outline = Color(0xFF5E5E5E)
)

// MOLLY: Replaced by snackbarColors()

@Composable
fun SignalTheme(
  isDarkMode: Boolean = LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES,
  incognitoKeyboardEnabled: Boolean = CoreUiDependencies.isIncognitoKeyboardEnabled,
  useDynamicColors: Boolean? = null,
  content: @Composable () -> Unit
) {
  val context = LocalContext.current
  val maySupportDynamicColor = Build.VERSION.SDK_INT >= 31
  val dynamicColors = maySupportDynamicColor && (useDynamicColors ?: isThemeUsingDynamicColors(context))

  // MOLLY: Apply dynamic color if supported and enabled:
  // - API 34+: Use Compose's built-in dynamic scheme (matches system Material You).
  // - API 31–33: Use mapped color resources to approximate system dynamic palette, avoiding Compose's more saturated fallback.
  // - Otherwise: Use app light/dark color schemes.
  val colorScheme = when {
    dynamicColors -> {
      if (Build.VERSION.SDK_INT >= 34) {
        if (isDarkMode) dynamicDarkColorScheme(context)
        else dynamicLightColorScheme(context)
      } else {
        if (isDarkMode) systemAlignedDarkColorScheme()
        else systemAlignedLightColorScheme()
      }
    }
    isDarkMode -> darkColorScheme
    else -> lightColorScheme
  }

  val extendedColors = extendedColors(colorScheme, isDarkMode = isDarkMode, isDynamic = dynamicColors)
  val snackbarColors = snackbarColors(colorScheme, isDarkMode = isDarkMode, isDynamic = dynamicColors)

  ProvideIncognitoKeyboard(enabled = incognitoKeyboardEnabled) {
    CompositionLocalProvider(LocalExtendedColors provides extendedColors, LocalSnackbarColors provides snackbarColors) {
      MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
      )
    }
  }
}

@Composable
private fun systemAlignedLightColorScheme() = lightColorScheme(
  primary = colorResource(R.color.dynamic_primary_light),
  onPrimary = colorResource(R.color.dynamic_on_primary_light),
  primaryContainer = colorResource(R.color.dynamic_primary_container_light),
  onPrimaryContainer = colorResource(R.color.dynamic_on_primary_container_light),
  inversePrimary = colorResource(R.color.dynamic_primary_inverse_light),
  secondary = colorResource(R.color.dynamic_secondary_light),
  onSecondary = colorResource(R.color.dynamic_on_secondary_light),
  secondaryContainer = colorResource(R.color.dynamic_secondary_container_light),
  onSecondaryContainer = colorResource(R.color.dynamic_on_secondary_container_light),
  tertiary = colorResource(R.color.dynamic_tertiary_light),
  onTertiary = colorResource(R.color.dynamic_on_tertiary_light),
  tertiaryContainer = colorResource(R.color.dynamic_tertiary_container_light),
  onTertiaryContainer = colorResource(R.color.dynamic_on_tertiary_container_light),
  background = colorResource(R.color.dynamic_background_light),
  onBackground = colorResource(R.color.dynamic_on_background_light),
  surface = colorResource(R.color.dynamic_surface_light),
  onSurface = colorResource(R.color.dynamic_on_surface_light),
  surfaceVariant = colorResource(R.color.dynamic_surface_variant_light),
  onSurfaceVariant = colorResource(R.color.dynamic_on_surface_variant_light),
  inverseSurface = colorResource(R.color.dynamic_surface_inverse_light),
  inverseOnSurface = colorResource(R.color.dynamic_on_surface_inverse_light),
  error = colorResource(R.color.dynamic_error_light),
  onError = colorResource(R.color.dynamic_on_error_light),
  errorContainer = colorResource(R.color.dynamic_error_container_light),
  onErrorContainer = colorResource(R.color.dynamic_on_error_container_light),
  outline = colorResource(R.color.dynamic_outline_light),
  outlineVariant = colorResource(R.color.dynamic_outline_variant_light),
  surfaceBright = colorResource(R.color.dynamic_surface_bright_light),
  surfaceContainer = colorResource(R.color.dynamic_surface_container_light),
  surfaceContainerHigh = colorResource(R.color.dynamic_surface_container_high_light),
  surfaceContainerHighest = colorResource(R.color.dynamic_surface_container_highest_light),
  surfaceContainerLow = colorResource(R.color.dynamic_surface_container_low_light),
  surfaceContainerLowest = colorResource(R.color.dynamic_surface_container_lowest_light),
  surfaceDim = colorResource(R.color.dynamic_surface_dim_light),
)

@Composable
private fun systemAlignedDarkColorScheme() = darkColorScheme(
  primary = colorResource(R.color.dynamic_primary_dark),
  onPrimary = colorResource(R.color.dynamic_on_primary_dark),
  primaryContainer = colorResource(R.color.dynamic_primary_container_dark),
  onPrimaryContainer = colorResource(R.color.dynamic_on_primary_container_dark),
  inversePrimary = colorResource(R.color.dynamic_primary_inverse_dark),
  secondary = colorResource(R.color.dynamic_secondary_dark),
  onSecondary = colorResource(R.color.dynamic_on_secondary_dark),
  secondaryContainer = colorResource(R.color.dynamic_secondary_container_dark),
  onSecondaryContainer = colorResource(R.color.dynamic_on_secondary_container_dark),
  tertiary = colorResource(R.color.dynamic_tertiary_dark),
  onTertiary = colorResource(R.color.dynamic_on_tertiary_dark),
  tertiaryContainer = colorResource(R.color.dynamic_tertiary_container_dark),
  onTertiaryContainer = colorResource(R.color.dynamic_on_tertiary_container_dark),
  background = colorResource(R.color.dynamic_background_dark),
  onBackground = colorResource(R.color.dynamic_on_background_dark),
  surface = colorResource(R.color.dynamic_surface_dark),
  onSurface = colorResource(R.color.dynamic_on_surface_dark),
  surfaceVariant = colorResource(R.color.dynamic_surface_variant_dark),
  onSurfaceVariant = colorResource(R.color.dynamic_on_surface_variant_dark),
  inverseSurface = colorResource(R.color.dynamic_surface_inverse_dark),
  inverseOnSurface = colorResource(R.color.dynamic_on_surface_inverse_dark),
  error = colorResource(R.color.dynamic_error_dark),
  onError = colorResource(R.color.dynamic_on_error_dark),
  errorContainer = colorResource(R.color.dynamic_error_container_dark),
  onErrorContainer = colorResource(R.color.dynamic_on_error_container_dark),
  outline = colorResource(R.color.dynamic_outline_dark),
  outlineVariant = colorResource(R.color.dynamic_outline_variant_dark),
  surfaceBright = colorResource(R.color.dynamic_surface_bright_dark),
  surfaceContainer = colorResource(R.color.dynamic_surface_container_dark),
  surfaceContainerHigh = colorResource(R.color.dynamic_surface_container_high_dark),
  surfaceContainerHighest = colorResource(R.color.dynamic_surface_container_highest_dark),
  surfaceContainerLow = colorResource(R.color.dynamic_surface_container_low_dark),
  surfaceContainerLowest = colorResource(R.color.dynamic_surface_container_lowest_dark),
  surfaceDim = colorResource(R.color.dynamic_surface_dim_dark),
)

private fun isThemeUsingDynamicColors(context: Context): Boolean {
  val theme = context.theme
  val typedValue = TypedValue()
  return theme.resolveAttribute(R.attr.dynamic_colors, typedValue, false)
    && typedValue.data != 0
}

private fun extendedColors(colorScheme: ColorScheme, isDarkMode: Boolean, isDynamic: Boolean): ExtendedColors {
  return when {
    isDynamic -> lightExtendedColors.copy(
      colorSurface1 = colorScheme.surfaceContainerLow,
      colorSurface2 = colorScheme.surfaceContainer,
      colorSurface3 = colorScheme.surfaceContainerHigh,
      colorSurface4 = colorScheme.surfaceContainerHighest,
      colorSurface5 = if (isDarkMode) colorScheme.surfaceBright else colorScheme.surfaceDim
    )

    isDarkMode -> darkExtendedColors
    else -> lightExtendedColors
  }
}

private fun snackbarColors(colorScheme: ColorScheme, isDarkMode: Boolean, isDynamic: Boolean): SnackbarColors {
  // val surface = if (!isDynamic) colorScheme.surfaceVariant else colorScheme.surface
  // val onSurface = if (!isDynamic) colorScheme.onSurfaceVariant else colorScheme.onSurface

  return SnackbarColors(
    color = colorScheme.inverseSurface,
    contentColor = colorScheme.inverseOnSurface,
    actionColor = colorScheme.primary,
    actionContentColor = colorScheme.primary,
    dismissActionContentColor = colorScheme.inverseOnSurface
  )
}

@Composable
fun colorAttribute(@AttrRes id: Int): Color {
  val theme = LocalContext.current.theme
  val typedValue = TypedValue()
  return if (theme.resolveAttribute(id, typedValue, true) && typedValue.resourceId != 0) {
    colorResource(typedValue.resourceId)
  } else {
    Color.Unspecified
  }
}

@Preview(showBackground = true)
@Composable
private fun TypographyPreview() {
  SignalTheme(
    isDarkMode = false,
    incognitoKeyboardEnabled = false
  ) {
    Column {
      Text(
        text = "Headline Small",
        style = MaterialTheme.typography.headlineLarge
      )
      Text(
        text = "Headline Small",
        style = MaterialTheme.typography.headlineMedium
      )
      Text(
        text = "Headline Small",
        style = MaterialTheme.typography.headlineSmall
      )
      Text(
        text = "Title Large",
        style = MaterialTheme.typography.titleLarge
      )
      Text(
        text = "Title Medium",
        style = MaterialTheme.typography.titleMedium
      )
      Text(
        text = "Title Small",
        style = MaterialTheme.typography.titleSmall
      )
      Text(
        text = "Body Large",
        style = MaterialTheme.typography.bodyLarge
      )
      Text(
        text = "Body Medium",
        style = MaterialTheme.typography.bodyMedium
      )
      Text(
        text = "Body Small",
        style = MaterialTheme.typography.bodySmall
      )
      Text(
        text = "Label Large",
        style = MaterialTheme.typography.labelLarge
      )
      Text(
        text = "Label Medium",
        style = MaterialTheme.typography.labelMedium
      )
      Text(
        text = "Label Small",
        style = MaterialTheme.typography.labelSmall
      )
    }
  }
}

object SignalTheme {
  val colors: ExtendedColors
    @Composable
    get() = LocalExtendedColors.current
}
