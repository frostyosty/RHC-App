package com.rockhard.blocker
import kotlin.random.Random

internal fun GameActivity.executePlayerMove(moveName: String) {
    cancelBattleTimer()
    if (battleOver) return
    
    // SAFEGUARD: If party is dead or empty, redirect to human punch to prevent array index crash
    if (playerLastStand || party.isEmpty() || activePetIndex >= party.size) {
        executeHumanPunch()
        return
    }
    
    val activePet = party[activePetIndex]
    
    participatingPets.add(activePetIndex)
    
    printLog("\n--- PLAYER TURN ---")
    if (currentWeather == "Rain" && Random.nextInt(100) < 15) { printLog("> 🌧️ The ground is slick from the rain!\n> ${activePet.name} slipped and missed!"); AnimUtils.animEvade(findViewById(R.id.spriteEnemy), false); mainHandler.postDelayed({ triggerEnemyCounterAttack() }, 1500); return }
    if (activePet.isGrouchy() && Random.nextInt(100) < 30) { printLog("> 😡 ${activePet.name} is feeling [Grouchy]!\n> ${activePet.name} ignored your command!"); mainHandler.postDelayed({ triggerEnemyCounterAttack() }, 1500); return }

    printLog("> ${activePet.name} attempts [$moveName]")
    AnimUtils.animAttack(findViewById(R.id.spritePlayer), true)
    playSpriteAnim(true, activePet.name, "attack")
    AudioEngine.playSfx(this, "spr_${activePet.name.substringAfter("]").trim().lowercase()}_attack")
    AudioEngine.playSfx(this, "fx_${moveName.lowercase()}")
    AnimUtils.fireProjectile(findViewById(R.id.spriteVfx), true, activePet.type, currentEnemy?.type ?: "Normal", moveName)

    val isEnemyFlying = currentEnemy?.type == "Flying"
    val isElusive = currentEnemy?.name?.contains("[Elusive]") == true
    
    if ((isEnemyFlying && !SkillEngine.isAntiAir(moveName) && !SkillEngine.isAntiFlyingDouble(moveName) && Random.nextInt(100) < 90) || (isElusive && Random.nextInt(100) < 30)) {
        printLog(if (isElusive) "> 💨 ${currentEnemy?.name} is [Elusive] and dodged instantly!" else "> ✈️ ${currentEnemy?.name} is out of reach!")
        AnimUtils.animEvade(findViewById(R.id.spriteEnemy), false)
        mainHandler.postDelayed({ triggerEnemyCounterAttack() }, 1500)
        return
    }

    mainHandler.postDelayed({
        if (isUnderAttack && currentEnemy?.type != "EventBoss") { printLog("> ${currentEnemy?.name} is out of reach!"); AnimUtils.animEvade(findViewById(R.id.spriteEnemy), false); triggerEnemyCounterAttack() } 
        else {
            val (skillDmg, skillLog) = SkillEngine.resolveSkill(this, activePet, moveName)
            var baseDmg = skillDmg

            if (moveName == activePet.lastMove) { if (activePet.moveStreak < 0) activePet.moveStreak = 1 else activePet.moveStreak++ } 
            else { if (activePet.moveStreak > 0) activePet.moveStreak = -1 else activePet.moveStreak-- }
            activePet.lastMove = moveName

            val currentTrait = if (activePet.name.contains("]")) activePet.name.substringBefore("]").substringAfter("[") else "Ordinary"
            if (activePet.moveStreak >= 4 && currentTrait != "Persistence") { val cleanName = if (activePet.name.contains("]")) activePet.name.substringAfter("]").trim() else activePet.name; activePet.name = "[Persistence] $cleanName"; printLog("> 🔄 $cleanName learned [Persistence] from relentless repetition!") } 
            else if (activePet.moveStreak <= -4 && currentTrait != "Dynamism") { val cleanName = if (activePet.name.contains("]")) activePet.name.substringAfter("]").trim() else activePet.name; activePet.name = "[Dynamism] $cleanName"; printLog("> 🔄 $cleanName learned [Dynamism] from unpredictable tactics!") }

            val updatedTrait = if (activePet.name.contains("]")) activePet.name.substringBefore("]").substringAfter("[") else "Ordinary"
            if (updatedTrait == "Persistence" && activePet.moveStreak > 1) { baseDmg = (baseDmg * (1.0 + (0.15 * activePet.moveStreak))).toInt(); printLog("> 🔁 [Persistence] amplified damage by ${(0.15 * activePet.moveStreak * 100).toInt()}%!") } 
            else if (updatedTrait == "Dynamism" && activePet.moveStreak < -1) { baseDmg = (baseDmg * (1.0 + (0.15 * Math.abs(activePet.moveStreak)))).toInt(); printLog("> 🔀[Dynamism] amplified damage by ${(0.15 * Math.abs(activePet.moveStreak) * 100).toInt()}%!") }

            if (currentEnemy?.type == "EventBoss") {
                val weakness = prefs.getString("EVENT_WEAKNESS", "") ?: ""
                if (activePet.name.contains(weakness)) { baseDmg = (currentEnemy!!.maxHp * 0.50).toInt(); printLog("> 🎯 EXPLOITED WEAKNESS! Massive damage!") } 
                else { baseDmg = (currentEnemy!!.maxHp * 0.125).toInt(); printLog("> 🛡️ Thick armor! Standard attacks are weak!") }
            }
            
            if (currentEnemy?.name?.contains("[Enraged]") == true) { baseDmg = (baseDmg * 1.2).toInt() }
            if (isEnemyFlying && SkillEngine.isAntiFlyingDouble(moveName)) { baseDmg *= 2; printLog("> 💥 CRITICAL WEAKNESS! Double damage against Flying beasts!") }

            var critChance = 15
            if (activePet.name.contains("[Wary]")) { baseDmg = (baseDmg * 0.9).toInt(); printLog("> 😨 [Wary] reduced damage by 10%!") }
            if (activePet.name.contains("[Worried]")) { baseDmg = (baseDmg * 0.8).toInt(); printLog("> 😰[Worried] reduced damage by 20%!") }
            if (activePet.name.contains("[Petrified]")) { baseDmg = (baseDmg * 0.5).toInt(); printLog("> 😱 [Petrified] reduced damage by 50%!") }
            if (activePet.name.contains("[Frenzy]")) critChance += 15

            if (activePet.infusionStacks > 0) {
                if (activePet.infusionEl == "Storm") { baseDmg += (baseDmg * 0.5 * activePet.infusionStacks).toInt(); printLog("> ⚡ STORM INFUSION! +${50 * activePet.infusionStacks}% DAMAGE!") }
                else if (activePet.infusionEl == "Rain") { critChance += (20 * activePet.infusionStacks); printLog("> 🌧️ RAIN INFUSION! +${20 * activePet.infusionStacks}% Crit chance!") } 
                else if (activePet.infusionEl == currentWeather) { val bonus = activePet.infusionStacks * 5; baseDmg += bonus; printLog("> 🌟 TACTICAL ADVANTAGE! $currentWeather boosts damage by $bonus!") }
            }
            if (activePet.amulets > 0) { baseDmg = (baseDmg * (1.0 + (0.30 * activePet.amulets))).toInt(); printLog("> 🔮 Amulet of the Titan amplifies power (+30%)!") }
            
            var finalDmg = baseDmg
            if (playerHasTripleStrike) { finalDmg = baseDmg * 3; playerHasTripleStrike = false; printLog("> ⚔️ TRIPLE STRIKE ACTIVATED!") } 
            else if (Random.nextInt(100) < critChance) { finalDmg = (baseDmg * 1.5).toInt(); printLog("> 💥 CRITICAL HIT!"); vibratePhone(150) }
            
            if (activePet.name.contains("[Vampiric]")) { val heal = (finalDmg * 0.15).toInt().coerceAtLeast(1); activePet.hp = (activePet.hp + heal).coerceAtMost(activePet.maxHp); printLog("> 🧛[Vampiric] stole $heal HP!") }

            currentEnemy!!.hp -= finalDmg; updateHealthBars(); AnimUtils.animShake(findViewById(R.id.spriteEnemy)); playSpriteAnim(false, currentEnemy!!.name, "hit"); playAttackFx(false, moveName); vibratePhone(50)
            if (skillLog.isNotEmpty()) printLog(skillLog)
            
            if (currentEnemy!!.name.contains("[Spiked]") && finalDmg > 0) { val recoil = (finalDmg * 0.1).toInt().coerceAtLeast(1); activePet.hp -= recoil; printLog("> 🌵[Spiked] recoil! ${activePet.name} takes $recoil damage!"); updateHealthBars() }
            if (CombatState.terrified && currentEnemy?.type == "Wild") {
                CombatState.terrified = false
                if (currentEnemy!!.name.contains("[Colossal]")) printLog("> 🏔️[Colossal] Immunity! ${currentEnemy!!.name} cannot be Terrified!") 
                else { currentEnemy!!.hp = 0; printLog("> 👻 The wild ${currentEnemy!!.name} is TERRIFIED and gives up!") }
            }

            if (currentEnemy!!.hp > 0) printLog("> Hit! ${currentEnemy?.name} takes $finalDmg damage. (HP: ${currentEnemy!!.hp.coerceAtLeast(0)}/${currentEnemy!!.maxHp})")

            if (currentEnemy!!.hp <= 0) processEnemyVictory() else triggerEnemyCounterAttack() 
        }
    }, 300)
}

/** In a last stand the beast doesn't wait for your turn: it strikes this long after it turns on you. */
private const val LAST_STAND_STRIKE_MS = 3000L

/**
 * A punch takes no turn: tap as fast as you like. The beast's own clock ([armLastStandStrike],
 * or its patience in the Wilds) decides when it strikes back.
 */
internal fun GameActivity.executeHumanPunch() {
    if (battleOver) return
    val enemy = currentEnemy ?: return
    armLastStandStrike()
    AnimUtils.animAttack(findViewById(R.id.spritePlayer), true)
    playSpriteAnim(true, "YOU", "attack")
    AudioEngine.playSfx(this, "fx_hit")

    if (Relics.swordOn(prefs)) { // Sword of the Spirit: the punch is a sword swing that slays it outright
        playFx(false, "sword_of_the_spirit")
        mainHandler.postDelayed({
            if (battleOver || currentEnemy !== enemy) return@postDelayed
            AnimUtils.animShake(findViewById(R.id.spriteEnemy)); playSpriteAnim(false, enemy.name, "hit"); vibratePhone(60)
            printLog("> ⚔️ You swing the Sword of the Spirit! It cuts clean through ${enemy.name}!")
            enemy.hp = 0; updateHealthBars(); processEnemyVictory()
        }, 300)
        return
    }

    val hasPlayerAmulet = prefs.getBoolean("PLAYER_HAS_AMULET", false); var dmg = Random.nextInt(1, 5)

    mainHandler.postDelayed({
        if (battleOver || currentEnemy !== enemy) return@postDelayed
        AnimUtils.animShake(findViewById(R.id.spriteEnemy)); playSpriteAnim(false, enemy.name, "hit"); vibratePhone(30)
        if (hasPlayerAmulet) { dmg = (enemy.maxHp * 0.25).toInt().coerceAtLeast(100); printLog("> 🔮 POWER PUNCH! Your Titan Amulet surges and you hit for $dmg damage!") }
        else printLog("> 👊 $dmg damage.")

        if (enemy.name.contains("[Enraged]")) { dmg = (dmg * 1.2).toInt() }
        enemy.hp -= dmg; updateHealthBars()

        if (enemy.name.contains("[Spiked]") && dmg > 0) { printLog("> 🌵 [Spiked] recoil! You cut your hand! (Ouch)") }

        if (enemy.hp <= 0) { printLog("> 👑 UNBELIEVABLE! YOU KILLED IT WITH YOUR BARE HANDS!"); processEnemyVictory() }
    }, 300)
}

/** Starts the beast's clock on the battle screen (the Wilds use its patience instead). */
internal fun GameActivity.armLastStandStrike() {
    if (worldFight != null || lastStandStrike != null || battleOver || !playerLastStand) return
    val enemy = currentEnemy ?: return
    val r = Runnable {
        lastStandStrike = null
        if (!battleOver && playerLastStand && currentEnemy === enemy) enemyObliteratesYou()
    }
    lastStandStrike = r
    mainHandler.postDelayed(r, LAST_STAND_STRIKE_MS)
}

private fun GameActivity.enemyObliteratesYou() {
    val enemy = currentEnemy ?: return
    AnimUtils.animAttack(findViewById(R.id.spriteEnemy), false); playSpriteAnim(false, enemy.name, "attack")
    mainHandler.postDelayed({
        if (battleOver || currentEnemy !== enemy) return@postDelayed
        AnimUtils.animDeath(findViewById(R.id.spritePlayer)); playSpriteAnim(true, "YOU", "hit"); vibratePhone(1000)
        printLog("\n--- ENEMY TURN ---\n> ${enemy.name} obliterates you for 9,999 damage."); endBattle()
    }, 300)
}
