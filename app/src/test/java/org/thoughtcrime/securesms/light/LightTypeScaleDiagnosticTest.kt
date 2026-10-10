/*
 * Copyright 2026 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.light

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isLessThan
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.designVerticalPxToSp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The LP3's real geometry, and the type scale it actually lays out.
 *
 * The SDK's font scale is `screenHeightDp / 600`, which is 0.688 on the LP3's 413dp screen. Any
 * composable that reaches for `LightThemeTokens.typography` directly instead of going through
 * `LightText` (or scaling the unit itself) silently renders at the raw design size -- roughly 45%
 * too big, and visibly out of step with the rest of a screen. That failure is invisible in a build
 * and unmeasured in review, so it is pinned here.
 *
 * `captureToImage` blocks forever in this harness, so this measures the line box the compositor
 * built, not what it looks like. Position, colour and spacing on LP3 still need the device.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// LP3 geometry: 1080x1240 at 3x, i.e. 360dp x 413dp.
@Config(application = Application::class, qualifiers = "w360dp-h413dp-xxhdpi")
class LightTypeScaleDiagnosticTest {

  @get:Rule
  val coreUiDependenciesRule = org.signal.core.ui.CoreUiDependenciesRule(RuntimeEnvironment.getApplication())

  @get:Rule
  val composeTestRule = createComposeRule()

  private val density = 3f

  /** `LightTextVariant -> (font design px, line-height design px)`, from the SDK's buildDefaultTypography. */
  private val tokens = mapOf(
    LightTextVariant.Title to (115f to 115f * 1.10f),
    LightTextVariant.Subtitle to (52f to 52f * 1.20f),
    LightTextVariant.Heading to (38f to 38f * 1.35f),
    LightTextVariant.Subheading to (30f to 30f * 1.25f),
    LightTextVariant.Copy to (30f to 30f * 1.50f),
    LightTextVariant.Button to (30f to 30f * 1.10f),
    LightTextVariant.Paragraph to (24.5f to 24.5f * 1.25f),
    LightTextVariant.ParagraphWide to (25f to 25f * 1.30f),
    LightTextVariant.Detail to (20f to 20f * 1.45f),
    LightTextVariant.Fine to (25f to 25f * 1.15f),
    LightTextVariant.Superfine to (16f to 16f * 1.20f),
    LightTextVariant.Micro to (8f to 8f * 1.20f)
  )

  /**
   * The composited line box is the font's own ascent+descent at the applied size, not the token's
   * declared lineHeight, so the ratio is calibrated once against `Copy` -- known to be scaled, since
   * `LightText` applies the scale itself -- and every measurement is read back as a font size.
   */
  @Test
  fun `every LightText variant applies the screen-height scale`() {
    val scaled = mutableMapOf<LightTextVariant, Float>()
    composeTestRule.setContent {
      LightTextVariant.entries.forEach { variant ->
        scaled[variant] = tokens.getValue(variant).first.designVerticalPxToSp().value
        LightText(text = "x-" + variant.name, variant = variant)
      }
    }
    composeTestRule.waitForIdle()

    val copyBox = heightSp("x-" + LightTextVariant.Copy.name)
    val k = copyBox / scaled.getValue(LightTextVariant.Copy)

    val config = RuntimeEnvironment.getApplication().resources.configuration
    val out = StringBuilder()
    out.appendLine("screen: ${config.screenWidthDp}dp x ${config.screenHeightDp}dp @ ${config.densityDpi}dpi")
    out.appendLine("1 grid unit = %.2fdp wide, %.2fdp tall".format(config.screenWidthDp / 27f, config.screenHeightDp / 31f))
    out.appendLine("font scale = screenHeightDp / 600 = %.4f".format(config.screenHeightDp / 600f))
    out.appendLine("font metric ratio (calibrated on Copy) = %.4f".format(k))
    out.appendLine()
    out.appendLine("variant            scaled-sp  UNscaled-sp  implied-sp  verdict")
    out.appendLine("-".repeat(68))

    for (variant in LightTextVariant.entries) {
      val rawPx = tokens.getValue(variant).first
      val scaledSp = scaled.getValue(variant)
      val implied = heightSp("x-" + variant.name) / k
      val verdict = when {
        abs(implied - scaledSp) <= 1.0f -> "scaled (correct)"
        abs(implied - rawPx) <= 1.0f -> "UNSCALED (%.0f%% too big)".format(100 * (implied - scaledSp) / scaledSp)
        else -> "other"
      }
      out.appendLine(
        variant.name.padEnd(18) +
          "%.1f".format(scaledSp).padEnd(12) +
          "%.1f".format(rawPx).padEnd(14) +
          "%.1f".format(implied).padEnd(13) +
          verdict
      )
    }

    print(out)

    // The point of the test: every variant the SDK scales has to land on the scaled token, so a
    // future bypass shows up as a failure here rather than as "it looks big" on the device.
    for (variant in LightTextVariant.entries) {
      assertThat(
        abs(heightSp("x-" + variant.name) / k - scaled.getValue(variant)),
        variant.name
      ).isLessThan(1.0f)
    }
  }

  /**
   * `LightSearchField` used to hand `LightThemeTokens.typography.copy` straight to `BasicTextField`,
   * bypassing the `scaledForScreenHeight()` that `LightText` applies, so the country picker in the
   * login flow rendered its search text at the raw 30sp design token -- 46% larger than every other
   * line of copy around it. The field now scales its style; this pins that it stays scaled.
   *
   * Both sizes come from the SDK's own `Copy` token as the composition sees it, not from the table
   * above, so a change to the token moves the expectation with it.
   */
  @Test
  fun `LightSearchField scales its text style`() {
    var rawCopy = 0f
    var scaledCopy = 0f
    composeTestRule.setContent {
      rawCopy = LightThemeTokens.typography.copy.fontSize.value
      scaledCopy = rawCopy.designVerticalPxToSp().value
      // The same calibration node the first test uses, so `k` is derived the same way.
      LightText(text = "x-" + LightTextVariant.Copy.name, variant = LightTextVariant.Copy)
      LightSearchField(query = "United Kingdom", onQueryChange = {})
    }
    composeTestRule.waitForIdle()

    val k = heightSp("x-" + LightTextVariant.Copy.name) / scaledCopy
    // The field's own node, not the calibration LightText rendered before it.
    val measured = composeTestRule.onNodeWithText("United Kingdom").layoutHeightSp()
    val implied = measured / k

    val config = RuntimeEnvironment.getApplication().resources.configuration
    val out = StringBuilder()
    out.appendLine("LightSearchField -- used by the country picker in the login flow")
    out.appendLine("  implied font size      = %.1f sp".format(implied))
    out.appendLine("  scaled copy token      = %.1f sp".format(scaledCopy))
    out.appendLine("  raw copy token         = %.1f sp".format(rawCopy))
    if (abs(implied - scaledCopy) <= 1.0f) {
      out.appendLine("  -> correct")
    } else if (abs(implied - rawCopy) <= 1.0f) {
      out.appendLine("  -> UNSCALED: renders at the raw token, %.0f%% too big".format(100 * (implied - scaledCopy) / scaledCopy))
    } else {
      out.appendLine("  -> neither token")
    }
    out.appendLine("  that one line is %.0f%% of the %ddp screen height".format(100 * measured / config.screenHeightDp, config.screenHeightDp))

    print(out)

    assertThat(implied).isCloseTo(scaledCopy, 1.0f)
  }

  private fun heightSp(text: String): Float = composeTestRule.onNodeWithText(text).layoutHeightSp()

  /** The line box the compositor actually built, in sp (Robolectric runs at fontScale 1). */
  private fun SemanticsNodeInteraction.layoutHeightSp(): Float {
    var px = Float.NaN
    performSemanticsAction(SemanticsActions.GetTextLayoutResult) { fetch ->
      val results = mutableListOf<TextLayoutResult>()
      fetch(results)
      val layout = results.first()
      px = layout.getLineBottom(0) - layout.getLineTop(0)
      true
    }
    return px / density
  }
}
