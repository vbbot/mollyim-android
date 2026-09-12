/*
 * Copyright (c) 2026 The Light Phone
 * SPDX-License-Identifier: MIT
 *
 * Vendored from the Light Phone SDK (sdk/ui). See core/lightui/README.md.
 */

package com.thelightphone.sdk.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

object LightHapticFeedback {

    // currently optimized for LP3, which has a "slow" motor
    fun click(context: Context) = vibrateForDuration(context, 45.milliseconds)

    fun vibrateForDuration(context: Context, duration: Duration) {
        val vibrator = context.defaultVibrator() ?: return
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createOneShot(duration.inWholeMilliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    // MOLLY-VENDOR: upstream targets minSdk 34 and uses VibratorManager unconditionally, which is
    // API 31+. Molly's minSdk is 27, so fall back to the (now deprecated) Vibrator service.
    private fun Context.defaultVibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
}
