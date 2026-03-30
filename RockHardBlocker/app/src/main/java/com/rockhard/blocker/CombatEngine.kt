package com.rockhard.blocker

import android.os.Handler
import android.os.Looper
import kotlin.random.Random

internal fun GameActivity.executePlayerMove(moveName: String) {
    if (battleOver) return
    val activePet = party[activePetIndex]
    printLog("\n--- PLAYER TURN ---")
    
    if (currentWeather == "Rain" && Random.nextInt(100) < 15) { 
        printLog("> \uD83C\uDF27\uFE0F The ground is slick from the rain!")
        printLog("> ${activePet.name} slipped and missed!")
        AnimUtils.animEvade(findViewById(R.id.spriteEnemy), false)
        mainHandler.postDelayed({ triggerEnemyCounterAttack() }, 1500)
        return 
    }
    
    if (activePet.isGrouchy() && Random.nextInt(100) < 30) { 
        printLog("> \uD83D\uDE21 ${activePet.name} is feeling [Grouchy]!")
        printLog("> ${activePet.name} ignored your command!")
        mainHandler.postDelayed({ triggerEnemyCounterAttack() }, 1500)
        return 
    }

    printLog("> ${activePet.name} attempts [$moveName]")
    if (isUnderAttack) { 
        mainHandler.postDelayed({ 
            printLog("> ${currentEnemy?.name} EVADED! It is invincible!")
            AnimUtils.animEvade(findViewById(R.id.spriteEnemy), false)
            triggerEnemyCounterAttack() 
        }, 1000) 
    } else {
        var baseDmg = (activePet.maxHp * 0.25).toInt() + Random.nextInt(-5, 5)
        if (activePet.infusionEl == currentWeather && activePet.infusionStacks > 0) { 
            val mult = when(currentWeather) { "Clear" -> 1; "Cloudy" -> 2; "Rain" -> 3; "Storm" -> 5; "Snow" -> 10; else -> 0 }
            val bonus = activePet.infusionStacks * mult
            baseDmg += bonus
            printLog("> \uD83C\uDF1F TACTICAL ADVANTAGE! $currentWeather boosts damage by $bonus!") 
        }
        var finalDmg = baseDmg
        if (Random.nextInt(100) < 15) { 
            finalDmg = (baseDmg * 1.5).toInt()
            printLog("> \uD83D\uDCA5 CRITICAL HIT!") 
        }
        currentEnemy!!.hp -= finalDmg
        
        mainHandler.postDelayed({
            AnimUtils.animShake(findViewById(R.id.spriteEnemy))
            printLog("> Hit! ${currentEnemy?.name} takes $finalDmg damage. (HP: ${currentEnemy!!.hp.coerceAtLeast(0)}/${currentEnemy!!.maxHp})")
            if (currentEnemy!!.hp <= 0) { 
                AnimUtils.animDeath(findViewById(R.id.spriteEnemy))
                var coinsWon = Random.nextInt(10, 25)
                val diff = currentEnemy!!.maxHp - activePet.maxHp
                if (diff > 0) { 
                    val goliathBonus = diff / 2; coinsWon += goliathBonus
                    printLog("> \uD83C\uDFC6 GOLIATH BONUS! (+$goliathBonus c)") 
                }
                focusCoins += coinsWon
                saveItems(); updateBagScreen()
                printLog("> \uD83C\uDFC6 VICTORY! The wild ${currentEnemy?.name} fainted! You found $coinsWon Coins!")
                endBattle() 
            } else {
                triggerEnemyCounterAttack() 
            }
        }, 1000)
    }
}

internal fun GameActivity.executeHumanPunch() {
    if (battleOver) return
    printLog("\n--- PLAYER TURN ---\n> YOU THROW A PUNCH!")
    val dmg = Random.nextInt(1, 5)
    mainHandler.postDelayed({ 
        AnimUtils.animShake(findViewById(R.id.spriteEnemy))
        printLog("> It barely connects... $dmg damage.\n> ${currentEnemy?.name} looks at you with pity.")
        mainHandler.postDelayed({ 
            AnimUtils.animDeath(findViewById(R.id.spritePlayer))
            printLog("\n--- ENEMY TURN ---\n> ${currentEnemy?.name} obliterates you for 9,999 damage.\n\uD83D\uDC80 YOU DIED.")
            endBattle() 
        }, 1500) 
    }, 1000)
}

internal fun GameActivity.triggerEnemyCounterAttack() {
    if (battleOver) return
    if (party.isEmpty() || activePetIndex >= party.size) { 
        playerLastStand = true; updateBattleUI()
        printLog("\n> ⚠️ ALL NETBEASTS HAVE FALLEN!\n> There is no one left to protect you. It's your turn.")
        return 
    }

    val target = party[activePetIndex]
    val damage = if (isUnderAttack) Random.nextInt(40, 80) else (currentEnemy!!.maxHp * 0.25).toInt() + Random.nextInt(-5, 5)
    printLog("\n--- ENEMY TURN ---")
    printLog("> ${currentEnemy?.name} attempts[Brutal Strike] on ${target.name}...")
    
    mainHandler.postDelayed({
        target.hp -= damage
        AnimUtils.animShake(findViewById(R.id.spritePlayer))
        printLog("> \uD83E\uDE78 Hit! ${target.name} takes $damage damage.")
        printLog("> ${target.name} gains [Winded] status (-10 Speed).")
        
        if (target.hp <= 0) {
            AnimUtils.animDeath(findViewById(R.id.spritePlayer))
            printLog("> \uD83D\uDC80 ${target.name} HAS BEEN KILLED!")
            party.removeAt(activePetIndex)
            activePetIndex = 0
            prefs.edit().putInt("ACTIVE_PET_INDEX", 0).apply()
            if (party.isEmpty()) { 
                playerLastStand = true; updateBattleUI()
                printLog("\n> ⚠️ ALL NETBEASTS HAVE FALLEN!\n> ${currentEnemy?.name} turns its gaze slowly toward YOU.") 
            } else { 
                updateBattleUI()
                printLog("> You send out ${party[activePetIndex].name} in desperation!")
                showBattleArena(party[activePetIndex].name, currentEnemy!!.name)
            }
        }
        updatePartyScreen()
        saveParty()
        
        if (isUnderAttack && !battleOver && !playerLastStand && Random.nextBoolean()) {
            mainHandler.postDelayed({ 
                printLog("\n> ${currentEnemy?.name} fades into the shadows...")
                endBattle() 
            }, 2000)
        }
    }, 1500)
}

internal fun GameActivity.endBattle() {
    battleOver = true; isUnderAttack = false; isWildBattle = false; playerLastStand = false
    hideBattleArena()
    mainHandler.postDelayed({
        if (party.isEmpty()) { printLog("> You blacked out and returned to the Hub."); setUIState("HUB") } 
        else { printLog("> Returning to exploration..."); setUIState("HUB") }
    }, 2000)
}
