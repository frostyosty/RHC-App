package com.rockhard.blocker

import java.util.UUID
import kotlin.random.Random

internal fun GameActivity.loadSaveData() {
    sprays = prefs.getInt("SPRAYS", 0)
    potions = prefs.getInt("POTIONS", 0)
    nets = prefs.getInt("NETS", 0)
    focusCoins = prefs.getInt("COINS", 0)
    transfusers = prefs.getInt("TRANSFUSERS", 0)
    smallHpPots = prefs.getInt("SMALL_HP_POTS", 0)
    largeHpPots = prefs.getInt("LARGE_HP_POTS", 0)
    activePetIndex = prefs.getInt("ACTIVE_PET_INDEX", 0)

    playerId = prefs.getString("PLAYER_ID", "") ?: ""
    playerName = prefs.getString("PLAYER_NAME", "") ?: ""

    if (playerId.isEmpty()) {
        playerId = UUID.randomUUID().toString()
        val adjs = listOf("Neon", "Cyber", "Void", "Quantum", "Shadow", "Rogue", "Iron", "Aura")
        val nouns = listOf("Runner", "Ghost", "Paladin", "Hacker", "Samurai", "Vanguard", "Ninja")
        playerName = "${adjs.random()}_${nouns.random()}_${Random.nextInt(10, 99)}"
        prefs.edit().putString("PLAYER_ID", playerId).putString("PLAYER_NAME", playerName).apply()
    }

    party = SaveManager.loadParty(prefs, "PARTY_DATA")
    forSaleParty = SaveManager.loadParty(prefs, "FORSALE_DATA")
    marketBeasts = SaveManager.loadParty(prefs, "MARKET_DATA")
    if (activePetIndex >= party.size) activePetIndex = 0

    activeExpeditions.clear()
    activeExpeditions.putAll(SaveManager.loadExpeditions(prefs))
}

internal fun GameActivity.wipeCorruptSave() {
    prefs.edit().clear().apply()
    party.clear()
    party.add(Netbeast("Cacheon", "Tech", 120, 120, "Digital Swipe", "Overclock", "System Wipe", 0L, 0, 0, 0, false, "None", 0, 0, 0, 0, "None", 0))
    activeExpeditions.clear()
    sprays = 0; potions = 0; nets = 0; focusCoins = 0; transfusers = 0; smallHpPots = 0; largeHpPots = 0
    SaveManager.saveParty(prefs, "PARTY_DATA", party)
    saveItems()
    printLog("> ⚠️ WARNING: Save file wiped. Recovery Protocol Initialized.")
}

internal fun GameActivity.processFleePenalty() {
    if (prefs.getBoolean("FLED_BATTLE", false)) {
        prefs.edit().putBoolean("FLED_BATTLE", false).apply()
        val fBoss = prefs.getString("FLED_BOSS", "A Corrupted Beast") ?: "A Corrupted Beast"
        nets /= 2; potions /= 2; sprays /= 2; focusCoins /= 2; smallHpPots /= 2; largeHpPots /= 2
        
        var killedCount = 0
        party.sortBy { it.maxHp }
        val removedBeasts = mutableListOf<String>()
        while (party.isNotEmpty() && killedCount < 3) {
            removedBeasts.add(party[0].name)
            party.removeAt(0)
            killedCount++
        }
        activePetIndex = 0
        prefs.edit().putInt("ACTIVE_PET_INDEX", 0).apply()
        saveParty(); saveItems(); updateBagScreen(); updatePartyScreen(); updateBattleUI()
        
        printLog("\n==================================")
        printLog("⚠️ DEVASTATION REPORT ⚠️")
        printLog("Your Netbeasts were attacked by $fBoss while you fled!")
        printLog("Your stockpiles were ransacked (Lost 50% of items/coins).")
        if (removedBeasts.isNotEmpty()) printLog("You lost: ${removedBeasts.joinToString(", ")}.")
        if (party.isEmpty()) printLog("You have no Netbeasts left...")
        printLog("==================================\n")
    }
}

internal fun GameActivity.generateMarket() {
    if (marketBeasts.isEmpty()) {
        for (i in 0 until 3) {
            val b = GameData.beasts.random()
            val lvl = Random.nextInt(5, 20)
            val cost = (lvl * 5) + Random.nextInt(-10, 10)
            marketBeasts.add(Netbeast(b.name, b.type, lvl * 10, lvl * 10, b.m1, b.m2, "Tackle", System.currentTimeMillis(), 0, 0, 0, true, "None", 0, cost.coerceAtLeast(10), 0, 0, "None", 0))
        }
        SaveManager.saveParty(prefs, "MARKET_DATA", marketBeasts)
    }
}