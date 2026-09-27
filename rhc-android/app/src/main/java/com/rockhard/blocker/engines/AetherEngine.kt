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

    fun today(): String = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())

    /**
     * Seconds of Aether left today. The day's budget is set the first time the game
     * opens that day (CURRENT_REMNANTS says how much of it was remnants); after that
     * what's left is saved, so closing and reopening the game doesn't refill it.
     */
    fun loadToday(prefs: SharedPreferences): Int {
        val today = today()
        if (prefs.getString("AETHER_DAY", "") == today) {
            prefs.edit().putInt("CURRENT_REMNANTS", 0).apply()
            return prefs.getInt("AETHER_LEFT", 0).coerceAtLeast(0)
        }
        val seconds = calculateStartingAether(prefs)
        save(prefs, today, seconds)
        return seconds
    }

    /** What's left of the day [day] (the day it was loaded on, so a session past midnight doesn't spend the next day's). */
    fun save(prefs: SharedPreferences, day: String, seconds: Int) {
        prefs.edit().putString("AETHER_DAY", day).putInt("AETHER_LEFT", seconds.coerceAtLeast(0)).apply()
    }

    /** Why Nightfall shuts the game right now, or null. A shield Pause (System Override) lifts it, as it lifts the rest of Nightfall. */
    fun nightfallReason(prefs: SharedPreferences): String? {
        if (System.currentTimeMillis() < prefs.getLong("SYSTEM_PAUSE_UNTIL", 0L)) return null
        if (!ShieldRuleEngine.isNightfall(prefs)) return null
        val end = prefs.getInt("NIGHTFALL_END", 0)
        return "🌙 Nightfall. The Wilds are closed until ${String.format("%d:%02d", end / 60, end % 60)}."
    }

    /** Why the game can't be played right now (Nightfall, or today's Aether spent), or null if it can. */
    fun closedReason(prefs: SharedPreferences): String? {
        nightfallReason(prefs)?.let { return it }
        if (prefs.getString("AETHER_DAY", "") == today() && prefs.getInt("AETHER_LEFT", 1) <= 0) {
            return "Aether depleted. Return tomorrow."
        }
        return null
    }
}
