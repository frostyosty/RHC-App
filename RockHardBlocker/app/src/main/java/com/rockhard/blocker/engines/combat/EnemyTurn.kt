package com.rockhard.blocker
import kotlin.random.Random

internal fun GameActivity.triggerEnemyCounterAttack() {
    if (battleOver) return
    if (party.isEmpty() || activePetIndex >= party.size) { playerLastStand = true; updateBattleUI(); printLog("\n> ⚠️ ALL NETBEASTS HAVE FALLEN!\n> There is no one left to protect you. It's your turn."); return }

    val target = party[activePetIndex]
    
    if (CombatState.enemyPoisonStacks > 0) {
        val pDmg = (currentEnemy!!.maxHp * 0.05 * CombatState.enemyPoisonStacks).toInt().coerceAtLeast(1)
        currentEnemy!!.hp -= pDmg; printLog("> ☠️ POISON DAMAGE! ${currentEnemy!!.name} takes $pDmg damage."); updateHealthBars()
        if (currentEnemy!!.hp <= 0) { processEnemyVictory(); return }
    }

    if (CombatState.enemyStunned) { 
        CombatState.enemyStunned = false
        if (currentEnemy!!.name.contains("[Colossal]")) printLog("> 🏔️[Colossal] Immunity! ${currentEnemy!!.name} shrugged off the Stun!") 
        else { printLog("\n--- ENEMY TURN ---\n> ⚡ ${currentEnemy!!.name} is STUNNED and skips their turn!"); return }
    }

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
    
    if (currentEnemy?.name?.contains("[Enraged]") == true) { damage = (damage * 1.3).toInt() }

    printLog("> ${currentEnemy?.name} attempts [$enemyMove] on ${target.name}...")
    AnimUtils.animAttack(findViewById(R.id.spriteEnemy), false)
    AudioEngine.playSfx(this, "spr_${currentEnemy?.name?.replace(Regex("\\[.*?\\]"), "")?.trim()?.lowercase()}_attack")
    AudioEngine.playSfx(this, "fx_${enemyMove.lowercase()}")
    AnimUtils.fireProjectile(findViewById(R.id.spriteVfx), false, currentEnemy?.type ?: "Normal", target.type, enemyMove)
    
    mainHandler.postDelayed({
        var evasionChance = if (playerHasEvasion) 100 else 0
        if (target.name.contains("[Swift]")) evasionChance += 10
        if (target.infusionStacks > 0 && target.infusionEl == "Cloudy") evasionChance += (15 * target.infusionStacks)

        val isPlayerFlying = target.type == "Flying"
        if (isPlayerFlying && !SkillEngine.isAntiAir(enemyMove) && !SkillEngine.isAntiFlyingDouble(enemyMove)) evasionChance = Math.max(evasionChance, 90)
        if (isPlayerFlying && SkillEngine.isAntiFlyingDouble(enemyMove)) { damage *= 2; printLog("> 💥 CRITICAL WEAKNESS! ${target.name} took double damage from $enemyMove!") }

        if (Random.nextInt(100) < evasionChance) {
            playerHasEvasion = false
            if (evasionChance >= 100) printLog("> 💨 ${target.name} used POTION EVASION and dodged completely!") else printLog("> ☁️ EVASION! ${target.name} dodged the attack!")
            AnimUtils.animEvade(findViewById(R.id.spritePlayer), true)
        } else {
            if (target.name.contains("[Thick-Skinned]")) { damage = (damage * 0.85).toInt(); printLog("> 🛡️ [Thick-Skinned] reduced damage!") }
            if (target.infusionStacks > 0 && target.infusionEl == "Snow") { damage = (damage * (1.0 / (target.infusionStacks + 1))).toInt(); printLog("> ❄️ SNOW INFUSION! Damage heavily reduced!") }
            
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