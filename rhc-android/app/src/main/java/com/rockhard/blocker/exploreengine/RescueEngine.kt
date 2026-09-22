package com.rockhard.blocker

import android.content.Context
import android.content.SharedPreferences

object RescueEngine {
    fun captureBeast(prefs: SharedPreferences, beast: Netbeast, targetLocation: String) {
        val capturedData = prefs.getString("CAPTURED_DATA", "") ?: ""
        val beastString = "${beast.name},${beast.type},${beast.hp},${beast.maxHp},${beast.move1},${beast.move2},${beast.move3},${beast.boughtAt},${beast.eqNets},${beast.eqPots},${beast.eqSprays},${beast.isNew},${beast.infusionEl},${beast.infusionStacks},${beast.listedPrice},${beast.expedsDone},${beast.amulets},${beast.lastMove},${beast.moveStreak},$targetLocation"
        
        val newData = if (capturedData.isEmpty()) beastString else "$capturedData;$beastString"
        prefs.edit().putString("CAPTURED_DATA", newData).apply()
    }

    fun getCapturedNearby(prefs: SharedPreferences, currentSuburb: String, currentCity: String): List<Netbeast> {
        val capturedData = prefs.getString("CAPTURED_DATA", "") ?: ""
        if (capturedData.isEmpty()) return emptyList()

        val foundBeasts = mutableListOf<Netbeast>()
        capturedData.split(";").forEach { bStr ->
            val p = bStr.split(",")
            if (p.size >= 20) {
                val targetLoc = p[19]
                
                // FALLBACK MATH: Checks exact suburb, OR if the target contains the current overarching City!
                val matchesSuburb = currentSuburb.isNotEmpty() && targetLoc.equals(currentSuburb, ignoreCase = true)
                val matchesCity = currentCity.isNotEmpty() && targetLoc.contains(currentCity, ignoreCase = true)
                
                if (matchesSuburb || matchesCity || targetLoc == "The Outskirts") {
                    foundBeasts.add(Netbeast(p[0], p[1], p[2].toInt(), p[3].toInt(), p[4], p[5], p[6], p[7].toLong(), p[8].toInt(), p[9].toInt(), p[10].toInt(), p[11].toBoolean(), p[12], p[13].toInt(), p[14].toInt(), p[15].toInt(), p[16].toInt(), p[17], p[18].toInt()))
                }
            }
        }
        return foundBeasts
    }

    fun removeCapturedBeast(prefs: SharedPreferences, beastName: String) {
        val capturedData = prefs.getString("CAPTURED_DATA", "") ?: ""
        val remaining = capturedData.split(";").filter { !it.startsWith(beastName) }
        prefs.edit().putString("CAPTURED_DATA", remaining.joinToString(";")).apply()
    }
}