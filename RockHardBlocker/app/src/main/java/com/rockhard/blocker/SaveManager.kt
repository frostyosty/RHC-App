package com.rockhard.blocker

import android.content.SharedPreferences

object SaveManager {
    fun loadParty(prefs: SharedPreferences, key: String): MutableList<Netbeast> {
        val list = mutableListOf<Netbeast>()
        val data = prefs.getString(key, "") ?: ""
        if (data.isNotEmpty()) {
            data.split(";").forEach {
                val p = it.split(",")
                if (p.size >= 14) list.add(Netbeast(p[0], p[1], p[2].toInt(), p[3].toInt(), p[4], p[5], p[6].toLong(), p[7].toInt(), p[8].toInt(), p[9].toInt(), p[10].toBoolean(), p[11], p[12].toInt(), p[13].toInt()))
            }
        }
        return list
    }

    fun saveParty(prefs: SharedPreferences, key: String, party: List<Netbeast>) {
        val str = party.joinToString(";") { "${it.name},${it.type},${it.hp},${it.maxHp},${it.move1},${it.move2},${it.boughtAt},${it.eqNets},${it.eqPots},${it.eqSprays},${it.isNew},${it.infusionEl},${it.infusionStacks},${it.listedPrice}" }
        prefs.edit().putString(key, str).apply()
    }

    fun loadExpeditions(prefs: SharedPreferences): MutableMap<Int, Long> {
        val map = mutableMapOf<Int, Long>()
        val data = prefs.getString("EXP_DATA", "") ?: ""
        if (data.isNotEmpty()) {
            data.split(";").forEach {
                val p = it.split(":")
                if (p.size == 2) map[p[0].toInt()] = p[1].toLong()
            }
        }
        return map
    }

    fun saveExpeditions(prefs: SharedPreferences, activeExpeditions: Map<Int, Long>) {
        prefs.edit().putString("EXP_DATA", activeExpeditions.entries.joinToString(";") { "${it.key}:${it.value}" }).apply()
    }
}
