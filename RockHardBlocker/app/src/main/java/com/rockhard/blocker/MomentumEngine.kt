package com.rockhard.blocker

import android.content.SharedPreferences

object MomentumEngine {
    private val timeEstimates = mapOf(
        "tiktok" to 45, "youtube" to 40, "instagram" to 30, "facebook" to 20,
        "reddit" to 25, "twitter" to 20, "x" to 20, "snapchat" to 15, "tinder" to 15, 
        "twitch" to 30, "discord" to 20
    )

    fun calculateCurrentMomentum(prefs: SharedPreferences): Int {
        val earnedStr = prefs.getString("MOMENTUM_EARNED_TODAY", "") ?: ""
        val spentStr = prefs.getString("MOMENTUM_SPENT_TODAY", "") ?: ""
        val now = System.currentTimeMillis()

        var totalEarned = 0
        var totalEvaporated = 0

        if (earnedStr.isNotEmpty()) {
            earnedStr.split(",").forEach { entry ->
                val parts = entry.split("|")
                if (parts.size == 3) {
                    val mins = parts[1].toInt()
                    val time = parts[2].toLong()
                    totalEarned += mins

                    val hoursPassed = (now - time) / (1000 * 60 * 60.0)
                    if (hoursPassed > 2) {
                        val decayMins = ((hoursPassed - 2) * 60 / 5).toInt()
                        totalEvaporated += Math.min(decayMins, mins)
                    }
                }
            }
        }

        var totalSpent = 0
        if (spentStr.isNotEmpty()) {
            spentStr.split(",").forEach { entry ->
                val parts = entry.split("|")
                if (parts.size >= 2) totalSpent += parts[1].toInt()
            }
        }

        val sleepBonus = prefs.getInt("SLEEP_MOMENTUM_BONUS", 0)
        return Math.max(0, (totalEarned + sleepBonus) - totalSpent - totalEvaporated)
    }

    fun addEarnedMomentum(prefs: SharedPreferences, source: String, isAdultContent: Boolean) {
        val now = System.currentTimeMillis()
        val mins = if (isAdultContent) kotlin.random.Random.nextInt(15, 28) else kotlin.random.Random.nextInt(10, 20)
        val current = prefs.getString("MOMENTUM_EARNED_TODAY", "") ?: ""
        val newEntry = "$source|$mins|$now"
        prefs.edit().putString("MOMENTUM_EARNED_TODAY", if (current.isEmpty()) newEntry else "$current,$newEntry").apply()
    }

    fun spendMomentum(prefs: SharedPreferences, taskName: String, cost: Int): Boolean {
        if (calculateCurrentMomentum(prefs) < cost) return false
        val now = System.currentTimeMillis()
        val current = prefs.getString("MOMENTUM_SPENT_TODAY", "") ?: ""
        val newEntry = "$taskName|$cost|$now"
        prefs.edit().putString("MOMENTUM_SPENT_TODAY", if (current.isEmpty()) newEntry else "$current,$newEntry").apply()
        return true
    }

    fun resetDailyIfNeeded(prefs: SharedPreferences) {
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date())
        val lastDay = prefs.getString("MOMENTUM_LAST_DAY", "")
        if (today != lastDay) {
            var dailyYieldMins = 0
            
            val apps = prefs.getString("BLOCKLIST_APP", "")?.split(",")?.filter{it.isNotEmpty()} ?: emptyList()
            val webs = prefs.getString("BLOCKLIST_WEB", "")?.split(",")?.filter{it.isNotEmpty()} ?: emptyList()
            
            (apps + webs).forEach { item ->
                val name = item.split("|")[0].lowercase()
                var found = false
                for ((key, value) in timeEstimates) {
                    if (name.contains(key)) { dailyYieldMins += value; found = true; break }
                }
                if (!found) dailyYieldMins += 15 // Fallback generic time
            }

            val newEarned = if (dailyYieldMins > 0) "Daily Overcome Yield|$dailyYieldMins|${System.currentTimeMillis()}" else ""
            prefs.edit().putString("MOMENTUM_EARNED_TODAY", newEarned).putString("MOMENTUM_SPENT_TODAY", "").putString("MOMENTUM_LAST_DAY", today).apply()
        }
    }
}
