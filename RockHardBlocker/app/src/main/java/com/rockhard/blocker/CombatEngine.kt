package com.rockhard.blocker

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import kotlin.random.Random

// HARDWARE VIBRATION HELPER
internal fun GameActivity.vibratePhone(durationMs: Long) {
    if (prefs.getBoolean("VIBRATION", true)) {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") v.vibrate(durationMs)
        }
    }
}

internal fun GameActivity.executePlayerMove(moveName: String) {
    if (battleOver) return
    val activePet = party[activePetIndex]
    printLog("\n--- PLAYER TURN ---")
    
    if (currentWeather == "Rain" && Random.nextInt(100) < 15) { 
        printLog("> 🌧️ The ground is slick from the rain!")
        printLog("> ${activePet.name} slipped and missed!")
        AnimUtils.animEvade(findViewById(R.id.spriteEnemy), false)
        mainHandler.postDelayed({ triggerEnemyCounterAttack() }, 1500)
        return 
    }
    
    if (activePet.isGrouchy() && Random.nextInt(100) < 30) { 
        printLog("> 😡 ${activePet.name} is feeling [Grouchy]!")
        printLog("> ${activePet.name} ignored your command!")
        mainHandler.postDelayed({ triggerEnemyCounterAttack() }, 1500)
        return 
    }

    printLog("> ${activePet.name} attempts [$moveName]")
    AnimUtils.animAttack(findViewById(R.id.spritePlayer), true)

    mainHandler.postDelayed({
        if (isUnderAttack) { 
            printLog("> ${currentEnemy?.name} EVADED! It is invincible!")
            AnimUtils.animEvade(findViewById(R.id.spriteEnemy), false)
            triggerEnemyCounterAttack() 
        } else {
            var baseDmg = (activePet.maxHp * 0.25).toInt() + Random.nextInt(-5, 5)
            if (activePet.infusionEl == currentWeather && activePet.infusionStacks > 0) { 
                val mult = when(currentWeather) { "Clear" -> 1; "Cloudy" -> 2; "Rain" -> 3; "Storm" -> 5; "Snow" -> 10; else -> 0 }
                val bonus = activePet.infusionStacks * mult
                baseDmg += bonus
                printLog("> 🌟 TACTICAL ADVANTAGE! $currentWeather boosts damage by $bonus!") 
            }
            
            var finalDmg = baseDmg
            if (playerHasInstaKill) {
                finalDmg = 9999
                playerHasInstaKill = false
                printLog("> ☠️ INSTA-KILL ACTIVATED!")
            } else if (Random.nextInt(100) < 15) { 
                finalDmg = (baseDmg * 1.5).toInt()
                printLog("> 💥 CRITICAL HIT!") 
                vibratePhone(150)
            }
            
            currentEnemy!!.hp -= finalDmg
            updateHealthBars()
            
            AnimUtils.animShake(findViewById(R.id.spriteEnemy))
            vibratePhone(50)
            
            printLog("> Hit! ${currentEnemy?.name} takes $finalDmg damage. (HP: ${currentEnemy!!.hp.coerceAtLeast(0)}/${currentEnemy!!.maxHp})")
            if (currentEnemy!!.hp <= 0) { 
                AnimUtils.animDeath(findViewById(R.id.spriteEnemy))
                vibratePhone(300)
                var coinsWon = Random.nextInt(10, 25)
                val diff = currentEnemy!!.maxHp - activePet.maxHp
                if (diff > 0) { val goliathBonus = diff / 2; coinsWon += goliathBonus; printLog("> 🏆 GOLIATH BONUS! (+${goliathBonus}c)") }
                focusCoins += coinsWon; saveItems(); updateBagScreen()
                printLog("> 🏆 VICTORY! The wild ${currentEnemy?.name} fainted! You found $coinsWon Coins!")
                endBattle() 
            } else {
                triggerEnemyCounterAttack() 
            }
        }
    }, 300)
}


internal fun GameActivity.executeHumanPunch() {
    if (battleOver) return
    printLog("\n--- PLAYER TURN ---\n> YOU THROW A PUNCH!")
    AnimUtils.animAttack(findViewById(R.id.spritePlayer), true)
    
    val dmg = Random.nextInt(1, 5)
    mainHandler.postDelayed({ 
        AnimUtils.animShake(findViewById(R.id.spriteEnemy))
        vibratePhone(50)
        printLog("> It barely connects... $dmg damage.\n> ${currentEnemy?.name} looks at you with pity.")
        
        mainHandler.postDelayed({ 
            AnimUtils.animAttack(findViewById(R.id.spriteEnemy), false)
            mainHandler.postDelayed({
                AnimUtils.animDeath(findViewById(R.id.spritePlayer))
                vibratePhone(1000)
                printLog("\n--- ENEMY TURN ---\n> ${currentEnemy?.name} obliterates you for 9,999 damage.\n💀 YOU DIED.")
                endBattle() 
            }, 300)
        }, 1500) 
    }, 300)
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
    printLog("> ${currentEnemy?.name} attempts [Brutal Strike] on ${target.name}...")
    
    AnimUtils.animAttack(findViewById(R.id.spriteEnemy), false)
    
    mainHandler.postDelayed({
        if (playerHasEvasion) {
            playerHasEvasion = false
            printLog("> 💨 ${target.name} used POTION EVASION and dodged the attack completely!")
            AnimUtils.animEvade(findViewById(R.id.spritePlayer), true)
        } else {
            target.hp -= damage
            updateHealthBars()
            AnimUtils.animShake(findViewById(R.id.spritePlayer))
            vibratePhone(150)
            printLog("> 🩸 Hit! ${target.name} takes $damage damage.")
            printLog("> ${target.name} gains [Winded] status (-10 Speed).")
            
            if (target.hp <= 0) {
                AnimUtils.animDeath(findViewById(R.id.spritePlayer))
                vibratePhone(500)
                printLog("> 💀 ${target.name} HAS BEEN KILLED!")
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
        }
        updatePartyScreen()
        saveParty()
        
        if (isUnderAttack && !battleOver && !playerLastStand && Random.nextBoolean()) {
            mainHandler.postDelayed({ 
                printLog("\n> ${currentEnemy?.name} fades into the shadows...")
                endBattle() 
            }, 2000)
        }
    }, 300)
}

internal fun GameActivity.endBattle() {
    battleOver = true
    isUnderAttack = false
    isWildBattle = false
    playerLastStand = false
    hideBattleArena()
    mainHandler.postDelayed({
        if (party.isEmpty()) { 
            printLog("> You blacked out and returned to the Hub.")
            setUIState("HUB") 
        } else { 
            printLog("> Returning to exploration...")
            setUIState("HUB") 
        }
    }, 2000)
}