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
            @Suppress("DEPRECATION")
            v.vibrate(durationMs)
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

    printLog("> ${activePet.name} attempts[$moveName]")

    // PLAYER LUNGES FORWARD!
    AnimUtils.animAttack(findViewById(R.id.spritePlayer), true)

    mainHandler.postDelayed({
        if (isUnderAttack) {
            printLog("> ${currentEnemy?.name} EVADED! It is invincible!")
            AnimUtils.animEvade(findViewById(R.id.spriteEnemy), false)
            triggerEnemyCounterAttack()
        } else {
            val (skillDmg, skillLog) = SkillEngine.resolveSkill(this, activePet, moveName)
            var baseDmg = skillDmg

            // 1. PATTERN TRACKING (Persistence & Dynamism)
            if (moveName == activePet.lastMove) {
                if (activePet.moveStreak < 0) activePet.moveStreak = 1 else activePet.moveStreak++
            } else {
                if (activePet.moveStreak > 0) activePet.moveStreak = -1 else activePet.moveStreak--
            }
            activePet.lastMove = moveName

            val currentTrait = if (activePet.name.contains("]")) activePet.name.substringBefore("]").substringAfter("[") else "Ordinary"
            if (activePet.moveStreak >= 4 && currentTrait != "Persistence") {
                val cleanName = if (activePet.name.contains("]")) activePet.name.substringAfter("]").trim() else activePet.name
                activePet.name = "[Persistence] $cleanName"
                printLog("> 🔄 ${cleanName} learned [Persistence] from relentless repetition!")
            } else if (activePet.moveStreak <= -4 && currentTrait != "Dynamism") {
                val cleanName = if (activePet.name.contains("]")) activePet.name.substringAfter("]").trim() else activePet.name
                activePet.name = "[Dynamism] $cleanName"
                printLog("> 🔄 ${cleanName} learned [Dynamism] from unpredictable tactics!")
            }

            val updatedTrait = if (activePet.name.contains("]")) activePet.name.substringBefore("]").substringAfter("[") else "Ordinary"
            if (updatedTrait == "Persistence" && activePet.moveStreak > 1) {
                baseDmg = (baseDmg * (1.0 + (0.15 * activePet.moveStreak))).toInt()
                printLog("> 🔁[Persistence] amplified damage by ${(0.15 * activePet.moveStreak * 100).toInt()}%!")
            } else if (updatedTrait == "Dynamism" && activePet.moveStreak < -1) {
                baseDmg = (baseDmg * (1.0 + (0.15 * Math.abs(activePet.moveStreak)))).toInt()
                printLog("> 🔀[Dynamism] amplified damage by ${(0.15 * Math.abs(activePet.moveStreak) * 100).toInt()}%!")
            }

            // 2. EVENT BOSS SCALING MATH
            if (currentEnemy?.type == "EventBoss") {
                val weakness = prefs.getString("EVENT_WEAKNESS", "") ?: ""
                if (activePet.name.contains(weakness)) {
                    baseDmg = (currentEnemy!!.maxHp * 0.50).toInt() // 50% HP Damage!
                    printLog("> 🎯 EXPLOITED WEAKNESS! Massive damage!")
                } else {
                    baseDmg = (currentEnemy!!.maxHp * 0.125).toInt() // 1/8th HP Damage!
                    printLog("> 🛡️ $currentWeather resistant armor! Standard attacks are weak!")
                }
            }

            // 3. WEATHER INFUSION
            if (activePet.infusionEl == currentWeather && activePet.infusionStacks > 0) { 
                val bonus = activePet.infusionStacks * 5
                baseDmg += bonus
                printLog("> 🌟 TACTICAL ADVANTAGE! $currentWeather boosts damage by $bonus!") 
            }
            
            // 4. AMULET BUFF
            if (activePet.amulets > 0) {
                baseDmg = (baseDmg * (1.0 + (0.30 * activePet.amulets))).toInt()
                printLog("> 🔮 Amulet of the Titan amplifies power (+30%)!")
            }
            
            var finalDmg = baseDmg
            if (playerHasInstaKill) {
                finalDmg = 9999
                playerHasInstaKill = false
                printLog("> ☠️ INSTA-KILL ACTIVATED!")
            } else if (kotlin.random.Random.nextInt(100) < 15) { 
                finalDmg = (baseDmg * 1.5).toInt()
                printLog("> 💥 CRITICAL HIT!") 
                vibratePhone(150)
            }
            
            currentEnemy!!.hp -= finalDmg
            updateHealthBars()
            AnimUtils.animShake(findViewById(R.id.spriteEnemy))
            vibratePhone(50)
            
            if (skillLog.isNotEmpty()) printLog(skillLog)
            
            printLog("> Hit! ${currentEnemy?.name} takes $finalDmg damage. (HP: ${currentEnemy!!.hp.coerceAtLeast(0)}/${currentEnemy!!.maxHp})")
            
            if (currentEnemy!!.hp <= 0) { 
                AnimUtils.animDeath(findViewById(R.id.spriteEnemy))
                vibratePhone(300)
                
                // --- EVENT BOSS DEATH REWARD ---
                if (currentEnemy?.type == "EventBoss") {
                    val ams = prefs.getInt("PLAYER_UNCLAIMED_AMULETS", 0)
                    prefs.edit().putInt("PLAYER_UNCLAIMED_AMULETS", ams + 1).putBoolean("EVENT_ACTIVE", false).apply()
                    printLog("> 👑 TITAN SLAIN! You obtained a [Titan's Amulet]! Equip it from your bag.")
                } else {
                    var coinsWon = kotlin.random.Random.nextInt(10, 25)
                    val diff = currentEnemy!!.maxHp - activePet.maxHp
                    if (diff > 0) { val goliathBonus = diff / 2; coinsWon += goliathBonus; printLog("> 🏆 GOLIATH BONUS! (+${goliathBonus}c)") }
                    focusCoins += coinsWon; saveItems(); updateBagScreen()
                    printLog("> 🏆 VICTORY! The wild ${currentEnemy?.name} fainted! You found $coinsWon Coins!")
                }
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

    val hasPlayerAmulet = prefs.getBoolean("PLAYER_HAS_AMULET", false)
    var dmg = kotlin.random.Random.nextInt(1, 5)

    mainHandler.postDelayed({
        AnimUtils.animShake(findViewById(R.id.spriteEnemy))
        vibratePhone(50)

        if (hasPlayerAmulet) {
            dmg = (currentEnemy!!.maxHp * 0.25).toInt().coerceAtLeast(100) // PUNCH WITH THE MIGHT OF GOD
            printLog("> 🔮 YOUR AMULET GLOWS! You strike with the force of a TITAN! $dmg damage!")
        } else {
            printLog("> It barely connects... $dmg damage.\n> ${currentEnemy?.name} glares at you.")
        }

        currentEnemy!!.hp -= dmg
        updateHealthBars()

        if (currentEnemy!!.hp <= 0) {
            printLog("> 👑 UNBELIEVABLE! YOU KILLED IT WITH YOUR BARE HANDS!")
            endBattle()
        } else {
            mainHandler.postDelayed({
                AnimUtils.animAttack(findViewById(R.id.spriteEnemy), false)
                mainHandler.postDelayed({
                    AnimUtils.animDeath(findViewById(R.id.spritePlayer))
                    vibratePhone(1000)
                    printLog("\n--- ENEMY TURN ---\n> ${currentEnemy?.name} obliterates you for 9,999 damage.\n💀 YOU DIED.")
                    endBattle()
                }, 300)
            }, 1500)
        }
    }, 300)
}

internal fun GameActivity.triggerEnemyCounterAttack() {
    if (battleOver) return
    if (party.isEmpty() || activePetIndex >= party.size) {
        playerLastStand = true
        updateBattleUI()
        printLog("\n> ⚠️ ALL NETBEASTS HAVE FALLEN!\n> There is no one left to protect you. It's your turn.")
        return
    }

    val target = party[activePetIndex]
    val damage = if (isUnderAttack) Random.nextInt(40, 80) else (currentEnemy!!.maxHp * 0.25).toInt() + Random.nextInt(-5, 5)
    printLog("\n--- ENEMY TURN ---")
    printLog("> ${currentEnemy?.name} attempts[Brutal Strike] on ${target.name}...")

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
                    playerLastStand = true
                    updateBattleUI()
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

    // REGENERATIVE TRAIT PASSIVE HEALING
    party.forEach { p ->
        if (p.name.contains("[Regenerative]")) {
            val healAmount = (p.maxHp * 0.20).toInt()
            p.hp = (p.hp + healAmount).coerceAtMost(p.maxHp)
            printLog("> 💚 ${p.name}'s Regenerative trait restored $healAmount HP!")
        }
    }
    updatePartyScreen()
    saveParty()

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
