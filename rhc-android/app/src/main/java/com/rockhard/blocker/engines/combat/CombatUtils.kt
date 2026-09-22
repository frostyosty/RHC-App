package com.rockhard.blocker

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

internal fun GameActivity.vibratePhone(durationMs: Long) {
    if (prefs.getBoolean("VIBRATION", true)) {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") v.vibrate(durationMs)
        }
    }
}