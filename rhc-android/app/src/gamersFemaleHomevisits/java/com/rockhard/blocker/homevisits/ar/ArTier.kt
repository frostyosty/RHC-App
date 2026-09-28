package com.rockhard.blocker.homevisits.ar

import android.content.Context
import com.google.ar.core.ArCoreApk

enum class ArTier { FULL, BASIC, NONE }

object ArTierChecker {
    fun detect(context: Context): ArTier {
        val availability = ArCoreApk.getInstance().checkAvailability(context)
        if (availability.isTransient) {
            return ArTier.NONE // Still checking in background; caller can retry
        }
        if (availability.isSupported) {
            return ArTier.BASIC // Upgraded to FULL in ArSession if depth is supported
        }
        return ArTier.NONE
    }
}
