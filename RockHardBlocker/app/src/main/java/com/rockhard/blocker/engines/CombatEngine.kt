package com.rockhard.blocker

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import kotlin.random.Random

internal fun GameActivity.vibratePhone(durationMs: Long) {
    if (prefs.getBoolean("VIBRATION", true)) {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        else @Suppress("DEPRECATION") v.vibrate(durationMs)
    }
}

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
        printLog("> 🏆 VICTORY! The wild ${currentEnemy?.name} fainted!")
        
        participatingPets.forEach { idx ->
            if (idx < party.size && idx >= 0) {
                val p = party[idx]
                p.maxHp += 10
                p.hp = (p.hp + 10).coerceAtMost(p.maxHp)
                p.expedsDone++ 
                
                printLog("> 📈 LEVEL UP! ${p.name} grew to Lvl ${p.maxHp / 10}!")

                if (!p.name.contains("]") && Random.nextInt(100) < 15) {
                    val newTrait = listOf("Vampiric", "Thick-Skinned", "Frenzy", "Swift", "Looter").random()
                    p.name = "[$newTrait] ${p.name}"
                    printLog("> ✨ MUTATION! ${p.name} acquired the [$newTrait] trait!")
                }

                if (p.name.contains("[Looter]")) {
                    val loot = Random.nextInt(5, 15)
                    focusCoins += loot
                    printLog("> 💰 [Looter] found $loot Coins!")
                }

                if (p.expedsDone >= 2) evolveBeast(idx)
            }
        }
        saveItems(); updateBagScreen(); endBattle()
    }
}

internal fun GameActivity.executePlayerMove(moveName: String) {
    if (battleOver) return
    val activePet = party[activePetIndex]
    
    participatingPets.add(activePetIndex)
    
    printLog("\n--- PLAYER TURN ---")
    if (currentWeather == "Rain" && Random.nextInt(100) < 15) { printLog("> 🌧️ The ground is slick from the rain!\n> ${activePet.name} slipped and missed!"); AnimUtils.animEvade(findViewById(R.id.spriteEnemy), false); mainHandler.postDelayed({ triggerEnemyCounterAttack() }, 1500); return }
    if (activePet.isGrouchy() && Random.nextInt(100) < 30) { printLog("> 😡 ${activePet.name} is feeling[Grouchy]!\n> ${activePet.name} ignored your command!"); mainHandler.postDelayed({ triggerEnemyCounterAttack() }, 1500); return }

    printLog("> ${activePet.name} attempts[$moveName]")
    AnimUtils.animAttack(findViewById(R.id.spritePlayer), true)
    com.rockhard.blocker.engines.AudioEngine.playSfx(this, "spr_${activePet.name.substringAfter("]").trim().lowercase()}_attack")
    com.rockhard.blocker.engines.AudioEngine.playSfx(this, "fx_${moveName.lowercase()}")
    AnimUtils.fireProjectile(findViewById(R.id.spriteVfx), true, activePet.type, currentEnemy?.type ?: "Normal", moveName)

    val isEnemyFlying = currentEnemy?.type == "Flying"
    if (isEnemyFlying && !SkillEngine.isAntiAir(moveName) && !SkillEngine.isAntiFlyingDouble(moveName)) {
        if (Random.nextInt(100) < 90) { printLog("> ✈️ ${currentEnemy?.name} is FLYING! The attack missed entirely!"); AnimUtils.animEvade(findViewById(R.id.spriteEnemy), false); mainHandler.postDelayed({ triggerEnemyCounterAttack() }, 1500); return }
    }

    mainHandler.postDelayed({
        if (isUnderAttack && currentEnemy?.type != "EventBoss") { printLog("> ${currentEnemy?.name} EVADED! It is invincible!"); AnimUtils.animEvade(findViewById(R.id.spriteEnemy), false); triggerEnemyCounterAttack() } 
        else {
            val (skillDmg, skillLog) = SkillEngine.resolveSkill(this, activePet, moveName)
            var baseDmg = skillDmg

            if (moveName == activePet.lastMove) { if (activePet.moveStreak < 0) activePet.moveStreak = 1 else activePet.moveStreak++ } 
            else { if (activePet.moveStreak > 0) activePet.moveStreak = -1 else activePet.moveStreak-- }
            activePet.lastMove = moveName

            val currentTrait = if (activePet.name.contains("]")) activePet.name.substringBefore("]").substringAfter("[") else "Ordinary"
            if (activePet.moveStreak >= 4 && currentTrait != "Persistence") { val cleanName = if (activePet.name.contains("]")) activePet.name.substringAfter("]").trim() else activePet.name; activePet.name = "[Persistence] $cleanName"; printLog("> 🔄 $cleanName learned [Persistence] from relentless repetition!") } 
            else if (activePet.moveStreak <= -4 && currentTrait != "Dynamism") { val cleanName = if (activePet.name.contains("]")) activePet.name.substringAfter("]").trim() else activePet.name; activePet.name = "[Dynamism] $cleanName"; printLog("> 🔄 $cleanName learned[Dynamism] from unpredictable tactics!") }

            val updatedTrait = if (activePet.name.contains("]")) activePet.name.substringBefore("]").substringAfter("[") else "Ordinary"
            if (updatedTrait == "Persistence" && activePet.moveStreak > 1) { baseDmg = (baseDmg * (1.0 + (0.15 * activePet.moveStreak))).toInt(); printLog("> 🔁 [Persistence] amplified damage by ${(0.15 * activePet.moveStreak * 100).toInt()}%!") } 
            else if (updatedTrait == "Dynamism" && activePet.moveStreak < -1) { baseDmg = (baseDmg * (1.0 + (0.15 * Math.abs(activePet.moveStreak)))).toInt(); printLog("> 🔀 [Dynamism] amplified damage by ${(0.15 * Math.abs(activePet.moveStreak) * 100).toInt()}%!") }

            if (currentEnemy?.type == "EventBoss") {
                val weakness = prefs.getString("EVENT_WEAKNESS", "") ?: ""
                if (activePet.name.contains(weakness)) { baseDmg = (currentEnemy!!.maxHp * 0.50).toInt(); printLog("> 🎯 EXPLOITED WEAKNESS! Massive damage!") } 
                else { baseDmg = (currentEnemy!!.maxHp * 0.125).toInt(); printLog("> 🛡️ Thick armor! Standard attacks are weak!") }
            }

            if (isEnemyFlying && SkillEngine.isAntiFlyingDouble(moveName)) { baseDmg *= 2; printLog("> 💥 CRITICAL WEAKNESS! Double damage against Flying beasts!") }

            var critChance = 15
            if (activePet.name.contains("[Frenzy]")) critChance += 15

            if (activePet.infusionStacks > 0) {
                if (activePet.infusionEl == "Storm") { baseDmg *= 2; printLog("> ⚡ STORM INFUSION! DOUBLE DAMAGE!") }
                else if (activePet.infusionEl == "Rain") { critChance += 20; printLog("> 🌧️ RAIN INFUSION! High critical chance!") } 
                else if (activePet.infusionEl == currentWeather) { val bonus = activePet.infusionStacks * 5; baseDmg += bonus; printLog("> 🌟 TACTICAL ADVANTAGE! $currentWeather boosts damage by $bonus!") }
            }
            if (activePet.amulets > 0) { baseDmg = (baseDmg * (1.0 + (0.30 * activePet.amulets))).toInt(); printLog("> 🔮 Amulet of the Titan amplifies power (+30%)!") }
            
            var finalDmg = baseDmg
            if (playerHasTripleStrike) { finalDmg = baseDmg * 3; playerHasTripleStrike = false; printLog("> ⚔️ TRIPLE STRIKE ACTIVATED!") } 
            else if (Random.nextInt(100) < critChance) { finalDmg = (baseDmg * 1.5).toInt(); printLog("> 💥 CRITICAL HIT!"); vibratePhone(150) }
            
            if (activePet.name.contains("[Vampiric]")) { val heal = (finalDmg * 0.15).toInt().coerceAtLeast(1); activePet.hp = (activePet.hp + heal).coerceAtMost(activePet.maxHp); printLog("> 🧛[Vampiric] stole $heal HP!") }

            currentEnemy!!.hp -= finalDmg; updateHealthBars(); AnimUtils.animShake(findViewById(R.id.spriteEnemy)); vibratePhone(50)
            if (skillLog.isNotEmpty()) printLog(skillLog)

            if (CombatState.terrified && currentEnemy?.type == "Wild") { CombatState.terrified = false; currentEnemy!!.hp = 0; printLog("> 👻 The wild ${currentEnemy!!.name} is TERRIFIED and gives up!") }
            if (currentEnemy!!.hp > 0) printLog("> Hit! ${currentEnemy?.name} takes $finalDmg damage. (HP: ${currentEnemy!!.hp.coerceAtLeast(0)}/${currentEnemy!!.maxHp})")

            if (currentEnemy!!.hp <= 0) processEnemyVictory() else triggerEnemyCounterAttack() 
        }
    }, 300)
}

internal fun GameActivity.executeHumanPunch() {
    if (battleOver) return
    printLog("\n--- PLAYER TURN ---\n> YOU THROW A PUNCH!")
    AnimUtils.animAttack(findViewById(R.id.spritePlayer), true)
    com.rockhard.blocker.engines.AudioEngine.playSfx(this, "spr_${activePet.name.substringAfter("]").trim().lowercase()}_attack")
    com.rockhard.blocker.engines.AudioEngine.playSfx(this, "fx_${moveName.lowercase()}")
    AnimUtils.fireProjectile(findViewById(R.id.spriteVfx), true, activePet.type, currentEnemy?.type ?: "Normal", moveName)
    val hasPlayerAmulet = prefs.getBoolean("PLAYER_HAS_AMULET", false); var dmg = Random.nextInt(1, 5)
    
    mainHandler.postDelayed({ 
        AnimUtils.animShake(findViewById(R.id.spriteEnemy)); vibratePhone(50)
        if (hasPlayerAmulet) { dmg = (currentEnemy!!.maxHp * 0.25).toInt().coerceAtLeast(100); printLog("> 🔮 YOUR AMULET GLOWS! You strike with the force of a TITAN! $dmg damage!") } 
        else printLog("> It barely connects... $dmg damage.\n> ${currentEnemy?.name} looks at you with pity.")
        
        currentEnemy!!.hp -= dmg; updateHealthBars()
        if (currentEnemy!!.hp <= 0) { printLog("> 👑 UNBELIEVABLE! YOU KILLED IT WITH YOUR BARE HANDS!"); processEnemyVictory() } 
        else { mainHandler.postDelayed({ AnimUtils.animAttack(findViewById(R.id.spriteEnemy), false); mainHandler.postDelayed({ AnimUtils.animDeath(findViewById(R.id.spritePlayer)); vibratePhone(1000); printLog("\n--- ENEMY TURN ---\n> ${currentEnemy?.name} obliterates you for 9,999 damage.\n💀 YOU DIED."); endBattle() }, 300) }, 1500) }
    com.rockhard.blocker.engines.AudioEngine.playSfx(this, "spr_${currentEnemy?.name?.lowercase()}_attack")
    com.rockhard.blocker.engines.AudioEngine.playSfx(this, "fx_${enemyMove.lowercase()}")
    AnimUtils.fireProjectile(findViewById(R.id.spriteVfx), false, currentEnemy?.type ?: "Normal", target.type, enemyMove)
    }, 300)
}

internal fun GameActivity.triggerEnemyCounterAttack() {
    if (battleOver) return
    if (party.isEmpty() || activePetIndex >= party.size) { playerLastStand = true; updateBattleUI(); printLog("\n> ⚠️ ALL NETBEASTS HAVE FALLEN!\n> There is no one left to protect you. It's your turn."); return }

    val target = party[activePetIndex]
    
    if (CombatState.enemyPoisonStacks > 0) {
        val pDmg = (currentEnemy!!.maxHp * 0.05 * CombatState.enemyPoisonStacks).toInt().coerceAtLeast(1)
        currentEnemy!!.hp -= pDmg; printLog("> ☠️ POISON DAMAGE! ${currentEnemy!!.name} takes $pDmg damage."); updateHealthBars()
        if (currentEnemy!!.hp <= 0) { processEnemyVictory(); return }
    }

    if (CombatState.enemyStunned) { CombatState.enemyStunned = false; printLog("\n--- ENEMY TURN ---\n> ⚡ ${currentEnemy!!.name} is STUNNED and skips their turn!"); return }

    printLog("\n--- ENEMY TURN ---")

    if (isTrainerBattle) {
        if (currentEnemy!!.hp < currentEnemy!!.maxHp * 0.3 && currentEnemy!!.eqPots > 0) { currentEnemy!!.eqPots--; val heal = (currentEnemy!!.maxHp * 0.4).toInt(); currentEnemy!!.hp = (currentEnemy!!.hp + heal).coerceAtMost(currentEnemy!!.maxHp); updateHealthBars(); printLog("> 🧪 Poacher used a Potion! ${currentEnemy!!.name} recovered $heal HP!"); return }
        if (currentEnemy!!.hp < currentEnemy!!.maxHp * 0.2 && enemyParty.size > 1) {
            val nextBeast = enemyParty.firstOrNull { it != currentEnemy && it.hp > 0 }
            if (nextBeast != null) { printLog("> 🔄 Poacher withdrew ${currentEnemy!!.name} and sent out ${nextBeast.name}!"); currentEnemy = nextBeast; showBattleArena(if (playerLastStand) "YOU" else party[activePetIndex].name, currentEnemy!!.name); updateHealthBars(); return }
        }
    }

    val enemyMove = if (Random.nextBoolean()) currentEnemy!!.move1 else currentEnemy!!.move2
    var damage = if (isUnderAttack) Random.nextInt(40, 80) else (currentEnemy!!.maxHp * 0.25).toInt() + Random.nextInt(-5, 5)
    printLog("> ${currentEnemy?.name} attempts [$enemyMove] on ${target.name}...")
    AnimUtils.animAttack(findViewById(R.id.spriteEnemy), false)
    com.rockhard.blocker.engines.AudioEngine.playSfx(this, "spr_${currentEnemy?.name?.lowercase()}_attack")
    com.rockhard.blocker.engines.AudioEngine.playSfx(this, "fx_${enemyMove.lowercase()}")
    AnimUtils.fireProjectile(findViewById(R.id.spriteVfx), false, currentEnemy?.type ?: "Normal", target.type, enemyMove)
    
    mainHandler.postDelayed({
        var evasionChance = if (playerHasEvasion) 100 else 0
        if (target.name.contains("[Swift]")) evasionChance += 10
        if (target.infusionStacks > 0 && target.infusionEl == "Cloudy") evasionChance += 15

        val isPlayerFlying = target.type == "Flying"
        if (isPlayerFlying && !SkillEngine.isAntiAir(enemyMove) && !SkillEngine.isAntiFlyingDouble(enemyMove)) evasionChance = Math.max(evasionChance, 90)
        if (isPlayerFlying && SkillEngine.isAntiFlyingDouble(enemyMove)) { damage *= 2; printLog("> 💥 CRITICAL WEAKNESS! ${target.name} took double damage from $enemyMove!") }

        if (Random.nextInt(100) < evasionChance) {
            playerHasEvasion = false
            if (evasionChance >= 100) printLog("> 💨 ${target.name} used POTION EVASION and dodged completely!") else printLog("> ☁️ EVASION! ${target.name} dodged the attack!")
            AnimUtils.animEvade(findViewById(R.id.spritePlayer), true)
        } else {
            if (target.name.contains("[Thick-Skinned]")) { damage = (damage * 0.85).toInt(); printLog("> 🛡️[Thick-Skinned] reduced damage!") }
            if (target.infusionStacks > 0 && target.infusionEl == "Snow") { damage = (damage * 0.5).toInt(); printLog("> ❄️ SNOW INFUSION! Damage taken halved!") }
            
            if (CombatState.playerShield > 0) {
                if (damage <= CombatState.playerShield) { CombatState.playerShield -= damage; printLog("> 🛡️ SHIELD ABSORBED the attack! ($damage dmg)"); damage = 0 } 
                else { damage -= CombatState.playerShield; printLog("> 🛡️ SHIELD BROKEN! Absorbed ${CombatState.playerShield} dmg."); CombatState.playerShield = 0 }
            }

            if (damage > 0) { target.hp -= damage; updateHealthBars(); AnimUtils.animShake(findViewById(R.id.spritePlayer)); vibratePhone(150); printLog("> 🩸 Hit! ${target.name} takes $damage damage.") }
            
            if (target.hp <= 0) {
                AnimUtils.animDeath(findViewById(R.id.spritePlayer)); vibratePhone(500)
                val maxPartyHp = party.maxOfOrNull { it.maxHp } ?: 0
                if (target.maxHp >= maxPartyHp) {
                    val targetLoc = if (LocationEngine.hasGPSPermission(this)) { val loc = LocationEngine.getCurrentLocation(this); val lat = loc?.first ?: -36.8485; val lon = loc?.second ?: 174.7633; LocationEngine.generateNearbySuburb(this, lat, lon) } else { val city = prefs.getString("CURRENT_CITY", "The Outskirts") ?: "The Outskirts"; "the outskirts of $city" }
                    RescueEngine.captureBeast(prefs, target, targetLoc); printLog("\n> 🚁 A Poacher chopper swooped in!\n> ⚠️ ${target.name} WAS CAPTURED AND TAKEN TO: ${targetLoc.uppercase()}!")
                } else printLog("> 💀 ${target.name} HAS BEEN KILLED!")

                party.removeAt(activePetIndex); activePetIndex = 0; prefs.edit().putInt("ACTIVE_PET_INDEX", 0).apply()
                if (party.isEmpty()) { playerLastStand = true; updateBattleUI(); printLog("\n> ⚠️ ALL NETBEASTS HAVE FALLEN!\n> ${currentEnemy?.name} turns its gaze slowly toward YOU.") } 
                else { updateBattleUI(); printLog("> You send out ${party[activePetIndex].name} in desperation!"); showBattleArena(party[activePetIndex].name, currentEnemy!!.name) }
            }
        }
        updatePartyScreen(); saveParty()
        if (isUnderAttack && !battleOver && !playerLastStand && Random.nextBoolean() && currentEnemy?.type != "EventBoss") { mainHandler.postDelayed({ printLog("\n> ${currentEnemy?.name} fades into the shadows..."); endBattle() }, 2000) }
    }, 300)
}

internal fun GameActivity.endBattle() {
    battleOver = true; isUnderAttack = false; isWildBattle = false; isTrainerBattle = false; playerLastStand = false
    participatingPets.clear()
    hideBattleArena()
    party.forEach { p -> if (p.name.contains("[Regenerative]")) { val healAmount = (p.maxHp * 0.20).toInt(); p.hp = (p.hp + healAmount).coerceAtMost(p.maxHp); printLog("> 💚 ${p.name}'s Regenerative trait restored $healAmount HP!") } }
    updatePartyScreen(); saveParty()
    mainHandler.postDelayed({ if (party.isEmpty()) { printLog("> You blacked out and returned to the Hub."); setUIState("HUB") } else { printLog("> Returning to exploration..."); setUIState("HUB") } }, 2000)
}
