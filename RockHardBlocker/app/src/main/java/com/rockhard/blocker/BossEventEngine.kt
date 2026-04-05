package com.rockhard.blocker

import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object BossEventEngine {
    fun checkAndGenerateEvent(prefs: SharedPreferences, party: List<Netbeast>, city: String): String? {
        val hasEvent = prefs.getBoolean("EVENT_ACTIVE", false)
        if (hasEvent) return null

        // Check if game has been installed/running for roughly 30 mins (we'll track first launch)
        val firstLaunch = prefs.getLong("FIRST_LAUNCH_TIME", 0L)
        if (firstLaunch == 0L) {
            prefs.edit().putLong("FIRST_LAUNCH_TIME", System.currentTimeMillis()).apply()
            return null
        }
        
        // 30 mins = 1800000 ms. (Set to 1000 ms during testing if you want it instantly!)
        if (System.currentTimeMillis() - firstLaunch < 1800000) return null 

        // GENERATE EVENT
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 3)
        val targetDayName = SimpleDateFormat("EEEE", Locale.getDefault()).format(cal.time)
        val targetDateStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(cal.time)

        val bossName = BossNameEngine.generateBossName("Anomaly")
        
        // Find a weakness the player DOES NOT have!
        val availableBeasts = GameData.beasts.map { it.name }
        val playerBeastNames = party.map { it.name.split(" ").last() }
        val weakness = availableBeasts.shuffled().firstOrNull { !playerBeastNames.contains(it) } ?: "Cacheon"

        prefs.edit()
            .putBoolean("EVENT_ACTIVE", true)
            .putString("EVENT_BOSS_NAME", bossName)
            .putString("EVENT_WEAKNESS", weakness)
            .putString("EVENT_TARGET_DAY", targetDayName)
            .putString("EVENT_TARGET_DATE", targetDateStr)
            .apply()

        return "\n⚠️ INVASION WARNING ⚠️\nSatellite detects [$bossName] approaching [$city].\nETA: $targetDayName.\nIntelligence suggests it is highly vulnerable to[$weakness]. Acquire one immediately!"
    }
}