package com.rockhard.blocker
import kotlin.random.Random

internal fun GameActivity.processEnemyVictory() {
    AnimUtils.animDeath(findViewById(R.id.spriteEnemy))
    vibratePhone(300)

    if (isTrainerBattle) {
        printLog("> Poacher's ${currentEnemy!!.name} fainted!")
        enemyParty.remove(currentEnemy)
        if (enemyParty.isEmpty()) {
            printLog("\n> 🦹 POACHER: 'Gah! Fine! Take your beast back!'")
            RescueEngine.removeCapturedBeast(prefs, capturedRescueTarget!!.name)
            val cleanName = if (capturedRescueTarget!!.name.contains("]")) capturedRescueTarget!!.name.substringAfter("]").trim() else capturedRescueTarget!!.name
            capturedRescueTarget!!.name = "[Will to Live] $cleanName"; capturedRescueTarget!!.isNew = true
            party.add(capturedRescueTarget!!)
            printLog("> 🎉 RESCUE SUCCESS! ${capturedRescueTarget!!.name} has rejoined your party, forever changed by its survival!")
            isTrainerBattle = false; capturedRescueTarget = null
            saveParty(); updatePartyScreen(); endBattle()
        } else {
            currentEnemy = enemyParty[0]
            printLog("> Poacher sends out ${currentEnemy!!.name}!")
            mainHandler.postDelayed({ showBattleArena(if (playerLastStand) "YOU" else party[activePetIndex].name, currentEnemy!!.name); updateHealthBars() }, 1000)
        }
    } else if (currentEnemy?.type == "EventBoss") {
        val ams = prefs.getInt("PLAYER_UNCLAIMED_AMULETS", 0)
        prefs.edit().putInt("PLAYER_UNCLAIMED_AMULETS", ams + 1).putBoolean("EVENT_ACTIVE", false).apply()
        printLog("> 👑 TITAN SLAIN! You obtained a [Titan's Amulet]! Equip it from your bag.")
        endBattle()
    } else {
        val clone = currentEnemy!!.copy(hp = currentEnemy!!.maxHp, isNew = true, boughtAt = System.currentTimeMillis())
        graveyard.add(clone)
        graveyard.sortByDescending { it.maxHp }
        if (graveyard.size > 10) graveyard = graveyard.take(10).toMutableList()
        SaveManager.saveParty(prefs, "GRAVEYARD_DATA", graveyard)

        printLog("> 🏆 VICTORY! The wild ${currentEnemy?.name} fainted!")
        
        if (currentEnemy?.name?.contains("[Ephemeral]") == true) {
            printLog("> 🌫️ [Ephemeral] entity vanquished. No XP could be extracted.")
        } else {
            participatingPets.forEach { idx ->
                if (idx < party.size && idx >= 0) {
                    val p = party[idx]
                    p.maxHp += 10
                    p.hp = (p.hp + 10).coerceAtMost(p.maxHp)
                    p.expedsDone++ 
                    
                    printLog("> 📈 LEVEL UP! ${p.name} grew to Lvl ${p.maxHp / 10}!")

                    if (Random.nextInt(100) < 15) {
                        val newTrait = listOf("Vampiric", "Thick-Skinned", "Frenzy", "Swift", "Looter", "Obscure").random()
                        if (!p.name.contains("[$newTrait]")) {
                            p.name = "[$newTrait] ${p.name}"
                            printLog("> ✨ MUTATION! ${p.name.split(" ").last()} acquired a mysterious new trait...")
                        }
                    }

                    if (p.name.contains("[Looter]")) {
                        val loot = Random.nextInt(5, 15)
                        focusCoins += loot
                        printLog("> 💰 [Looter] found $loot Coins!")
                    }
                    if (p.expedsDone >= 2) evolveBeast(idx)
                }
            }
        }
        saveItems(); updateBagScreen(); endBattle()
    }
}

internal fun GameActivity.endBattle() {
    battleOver = true; isUnderAttack = false; isWildBattle = false; isTrainerBattle = false; playerLastStand = false
    participatingPets.clear()
    hideBattleArena()
    party.forEach { p -> if (p.name.contains("[Regenerative]")) { val healAmount = (p.maxHp * 0.20).toInt(); p.hp = (p.hp + healAmount).coerceAtMost(p.maxHp); printLog("> 💚 ${p.name}'s Regenerative trait restored $healAmount HP!") } }
    updatePartyScreen(); saveParty()
    mainHandler.postDelayed({ if (party.isEmpty()) { printLog("> You blacked out and returned to the Hub."); setUIState("HUB") } else { printLog("> Returning to Hub..."); setUIState("HUB") } }, 2000)
}