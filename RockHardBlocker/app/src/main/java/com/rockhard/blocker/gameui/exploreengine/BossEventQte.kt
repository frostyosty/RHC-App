package com.rockhard.blocker

import android.view.View
import android.widget.Button

internal fun GameActivity.spawnEventBossQTE() {
    val bossName = prefs.getString("EVENT_BOSS_NAME", "Unknown Titan") ?: "Unknown Titan"
    val weakness = prefs.getString("EVENT_WEAKNESS", "Cacheon") ?: "Cacheon"
    
    printLog("\n🚨 KAIJU SIGHTED! 🚨\n> $bossName has breached the perimeter!")
    
    val qteView = layoutInflater.inflate(R.layout.item_qte_row, qteContainer, false)
    qteView.findViewById<android.widget.TextView>(R.id.tvQteDesc).text = "$bossName is attacking! (Weakness: $weakness)"
    
    qteView.findViewById<Button>(R.id.btnQteAvoid).apply {
        text = "FLEE (Lose Event)"
        setOnClickListener {
            qteContainer.removeView(qteView)
            prefs.edit().putBoolean("EVENT_ACTIVE", false).apply()
            printLog("> You abandoned the city. $bossName destroyed the area and left.")
            updateBagScreen()
        }
    }
    
    qteView.findViewById<Button>(R.id.btnQteAmbush).apply {
        text = "DEFEND CITY"
        setOnClickListener {
            qteContainer.removeView(qteView)
            
            // Generate the massive boss! (FIX: 19 Variables!)
            val pl = 1500 + (party.size * 200) 
            val eventBoss = Netbeast(bossName, "EventBoss", pl, pl, "Cataclysm", "Obliterate", "Annihilate", 0L, 0, 0, 0, false, "None", 0, 0, 0, 0, "None", 0)
            
            currentEnemy = eventBoss
            isWildBattle = true
            battleOver = false
            setUIState("BATTLE")
            showBattleArena(party[activePetIndex].name, bossName)
            updateBattleUI()
        }
    }
    qteView.findViewById<Button>(R.id.btnQteNet).visibility = View.GONE
    qteContainer.addView(qteView)
}