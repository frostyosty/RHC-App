package com.rockhard.blocker

import android.content.SharedPreferences
import java.util.concurrent.TimeUnit

object AetherEngine {
    fun calculateStartingAether(prefs: SharedPreferences): Int {
        val installTime = prefs.getLong("INSTALL_TIME", System.currentTimeMillis())
        val now = System.currentTimeMillis()
        
        val daysInstalled = TimeUnit.MILLISECONDS.toDays(now - installTime).toInt()
        val baseMinutes = Math.max(10, 20 - daysInstalled) // Decreases from 20 to 10
        
        val lastPlayed = prefs.getLong("LAST_PLAYED_TIME", now)
        val daysMissed = TimeUnit.MILLISECONDS.toDays(now - lastPlayed).toInt()
        val remnantMinutes = Math.min(5, daysMissed) // Max 5 mins of remnants
        
        // Save the remnant value so we can display the Toast
        prefs.edit().putInt("CURRENT_REMNANTS", remnantMinutes).putLong("LAST_PLAYED_TIME", now).apply()
        
        return (baseMinutes + remnantMinutes) * 60 // Return in seconds
    }
}
