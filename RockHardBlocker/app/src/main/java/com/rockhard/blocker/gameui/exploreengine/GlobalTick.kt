package com.rockhard.blocker

import kotlin.random.Random

internal fun GameActivity.performGlobalTick() {
    // 0. CHECK FOR IMPENDING BOSS EVENT
    val eventMsg = BossEventEngine.checkAndGenerateEvent(prefs, party, currentCity)
    if (eventMsg != null) { printLog(eventMsg); updateBagScreen() }

    val isEventActive = prefs.getBoolean("EVENT_ACTIVE", false)
    if (isEventActive && !isUnderAttack && !isWildBattle && activeQTEs.isEmpty()) {
        val todayStr = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date())
        // FIX: Elvis operator handles the nullable String? error!
        val targetDate = prefs.getString("EVENT_TARGET_DATE", "") ?: "" 
        if (todayStr >= targetDate) {
            spawnEventBossQTE()
            return 
        }
    }

    // 1. NPC OFFER GENERATION
    if (forSaleParty.isNotEmpty() && Random.nextInt(100) < 20) {
        val target = forSaleParty.random()
        if (!activeOffers.containsKey(target.name)) {
            val mood = Random.nextInt(100)
            val offer = if (mood < 30) (target.listedPrice * 0.5).toInt() else if (mood < 80) (target.listedPrice * 0.9).toInt() else (target.listedPrice * 1.5).toInt()
            activeOffers[target.name] = offer.coerceAtLeast(1)
            printLog("\n> 📩 MARKET ALERT! You received an offer of ${offer}c for ${target.name}!")
            updateBagScreen()
        }
    }

    // 2. MARKET ROTATION
    if (marketBeasts.isNotEmpty() && Random.nextInt(100) < 15) {
        marketBeasts.removeAt(Random.nextInt(marketBeasts.size))
        SaveManager.saveParty(prefs, "MARKET_DATA", marketBeasts)
    }

    // 3. DYNAMIC SPAWNING (Fixed 19 Variables!)
    val spawnChance = 100 - (marketBeasts.size * 25)
    if (marketBeasts.size < 4 && Random.nextInt(100) < spawnChance) {
        val b = GameData.beasts.random()
        val lvl = Random.nextInt(5, 20)
        val cost = (lvl * 5) + Random.nextInt(-10, 10)
        marketBeasts.add(Netbeast(b.name, b.type, lvl * 10, lvl * 10, b.m1, b.m2, "Tackle", System.currentTimeMillis(), 0, 0, 0, true, "None", 0, cost.coerceAtLeast(10), 0, 0, "None", 0))
        SaveManager.saveParty(prefs, "MARKET_DATA", marketBeasts)
    }

    // 4. EXPEDITION TICKS
    if (activeExpeditions.isEmpty()) return
    val now = System.currentTimeMillis(); val toRemove = mutableListOf<Int>()

    for (petIndex in activeExpeditions.keys.toList()) {
        if (petIndex >= party.size) { toRemove.add(petIndex); continue }
        val pet = party[petIndex]
        if (now >= activeExpeditions[petIndex]!!) {
            pet.expedsDone++ 
            printLog("\n> 🏁 EXPEDITION COMPLETE! ${pet.name} returned. (Expeditions: ${pet.expedsDone}/2)")
            toRemove.add(petIndex); continue
        }
        if (activeQTEs.containsKey(petIndex)) continue

        if (Random.nextInt(100) < 25) {
            if (isUnderAttack || isWildBattle) {
                if (petIndex == activePetIndex) continue 
                if (Random.nextBoolean()) { nets++; printLog("> \uD83D\uDEE1\uFE0F While you battle, ${pet.name} scavenged a Net!") } 
                else { val c = Random.nextInt(5, 15); focusCoins += c; printLog("> \uD83D\uDEE1\uFE0F While you battle, ${pet.name} scavenged $c Coins!") }
                saveItems(); updateBagScreen()
            } else {
                val roll = Random.nextInt(100)
                if (party.size >= 8 && Random.nextInt(100) < 5) { transfusers++; printLog("> 🧬 INCREDIBLE! ${pet.name} uncovered a rare Trait Transfuser!"); saveItems(); updateBagScreen() } 
                else if (roll < 30) { printLog("> ${pet.name} found a Net!"); nets++; saveItems(); updateBagScreen() } 
                else if (roll < 50) { printLog("> ${pet.name} found a Potion!"); potions++; saveItems(); updateBagScreen() } 
                else if (roll < 70) { val c = Random.nextInt(5, 15); focusCoins += c; printLog("> ${pet.name} found $c Focus Coins!"); saveItems(); updateBagScreen() } 
                else spawnWildQTE(petIndex, pet)
            }
        }
    }
    toRemove.forEach { activeExpeditions.remove(it) }
    if (toRemove.isNotEmpty()) { SaveManager.saveExpeditions(prefs, activeExpeditions); updateDispatchButton() }
}

internal fun GameActivity.checkOfflineExpeditions() {
    if (activeExpeditions.isEmpty()) return
    val now = System.currentTimeMillis(); val toRemove = mutableListOf<Int>()
    for ((idx, endTime) in activeExpeditions) {
        if (idx >= party.size) { toRemove.add(idx); continue }
        if (now >= endTime) {
            toRemove.add(idx)
            if (Random.nextInt(100) < 30) {
                val wildName = GameData.beasts.random().name; val pl = Random.nextInt(40, 160)
                // FIX: 19 Variables!
                currentEnemy = Netbeast(wildName, "Wild", pl, pl, "Tackle", "Bite", "Swipe", 0L, 0, 0, 0, false, "None", 0, 0, 0, 0, "None", 0)
                isWildBattle = true; activePetIndex = idx; setUIState("BATTLE"); showBattleArena(party[idx].name, wildName)
                printLog("⚠️ OFFLINE AMBUSH!\n> While you were away, ${party[idx].name} was attacked by a wild $wildName!"); updateBattleUI(); break
            } else {
                val c = Random.nextInt(5, 15); focusCoins += c; printLog("> \uD83C\uDFC1 Offline: ${party[idx].name} returned with $c Focus Coins!")
            }
        } else printLog("> ${party[idx].name} is still exploring...")
    }
    toRemove.forEach { activeExpeditions.remove(it) }
    saveItems(); updateBagScreen(); SaveManager.saveExpeditions(prefs, activeExpeditions); updateDispatchButton()
}