/*
 * Copyright (c) 2026 The Light Phone
 * SPDX-License-Identifier: MIT
 *
 * Vendored from the Light Phone SDK (sdk/ui). See core/lightui/README.md.
 */

package com.thelightphone.sdk.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * LP3 grid dimensions from `LightOS/src/ui/constants.ts`.
 */
object LightGrid {
    const val WIDTH = 27
    const val HEIGHT = 31
}

@Composable
fun Float.gridUnitsAsDp(): Dp {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    return (screenWidthDp.toFloat() / LightGrid.WIDTH * this).dp
}

@Composable
fun Float.verticalGridUnitsAsDp(): Dp {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp
    return (screenHeightDp.toFloat() / LightGrid.HEIGHT * this).dp
}

internal const val FONT_VERTICAL_SCALE_BASELINE_PX = 600f

@Composable
fun Float.designVerticalPxToSp(): TextUnit {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.toFloat()
    return (this * screenHeightDp / FONT_VERTICAL_SCALE_BASELINE_PX).sp
}

@Composable
fun Float.designVerticalPxToDp(): Dp {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.toFloat()
    return (this * screenHeightDp / FONT_VERTICAL_SCALE_BASELINE_PX).dp
}

/**
 * MOLLY-VENDOR: non-composable variants of the scale helpers above.
 *
 * Upstream reads the screen size from `LocalConfiguration`, which only exists inside a composition.
 * Molly styles plain Android views from these same tokens (see `lightTypography`), so it needs the
 * identical arithmetic against a [Context]'s configuration. Each delegates to the same constants the
 * composable versions use, so the two can never disagree.
 */
fun Float.gridUnitsAsDp(context: Context): Float =
    context.resources.configuration.screenWidthDp.toFloat() / LightGrid.WIDTH * this

/** @see gridUnitsAsDp */
fun Float.verticalGridUnitsAsDp(context: Context): Float =
    context.resources.configuration.screenHeightDp.toFloat() / LightGrid.HEIGHT * this

/** Design-space vertical pixels to scale-independent pixels. @see designVerticalPxToSp */
fun Float.designVerticalPxToSp(context: Context): Float =
    this * context.resources.configuration.screenHeightDp.toFloat() / FONT_VERTICAL_SCALE_BASELINE_PX
