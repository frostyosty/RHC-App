package com.rockhard.blocker

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

internal fun GameActivity.updateHealthBars() {
    val pbPlayer = findViewById<ProgressBar>(R.id.pbPlayerHp)
    val pbEnemy = findViewById<ProgressBar>(R.id.pbEnemyHp)
    if (party.isNotEmpty()) { pbPlayer.max = party[activePetIndex].maxHp; pbPlayer.progress = party[activePetIndex].hp.coerceAtLeast(0) }
    if (currentEnemy != null) { pbEnemy.max = currentEnemy!!.maxHp; pbEnemy.progress = currentEnemy!!.hp.coerceAtLeast(0) }
}

internal fun GameActivity.showBattleArena(playerName: String, enemyName: String) {
    val arena = findViewById<View>(R.id.battleArena)
    val pContainer = findViewById<View>(R.id.spritePlayerContainer)
    val eContainer = findViewById<View>(R.id.spriteEnemyContainer)
    val pSprite = findViewById<TextView>(R.id.spritePlayer)
    val eSprite = findViewById<TextView>(R.id.spriteEnemy)
    val tvEnemyTraits = findViewById<TextView>(R.id.tvEnemyTraits)

    CombatState.reset(); arena.visibility = View.VISIBLE
    if (currentEnemy != null && currentEnemy!!.type != "Poacher" && currentEnemy!!.infusionEl == "None" && currentWeather != "Clear" && currentWeather != "Offline" && kotlin.random.Random.nextInt(100) < 25) {
        currentEnemy!!.infusionEl = currentWeather; currentEnemy!!.infusionStacks = kotlin.random.Random.nextInt(1, 4)
    }
    
    var pScale = 1.0f; var eScale = 1.0f
    val pLvl = if (!playerLastStand) party[activePetIndex].maxHp / 10 else 1
    val eLvl = currentEnemy?.maxHp?.div(10) ?: 1
    
    val cleanPName = playerName.replace(Regex("\\[.*?\\]"), "").trim()
    val cleanEName = enemyName.replace(Regex("\\[.*?\\]"), "").trim()

    val displayPName = if (playerName.contains("[Obscure]")) "[Obscure] $cleanPName" else playerName
    val displayEName = if (enemyName.contains("[Obscure]")) "[Obscure] $cleanEName" else enemyName
    pSprite.text = "$displayPName Lvl $pLvl\n(Player)"; eSprite.text = "$displayEName Lvl $eLvl\n(Enemy)"
    pSprite.alpha = 1f; eSprite.alpha = 1f

// PARSE ENEMY TRAITS
    var eTraits = Regex("\\[(.*?)\\]").findAll(enemyName).map { it.groupValues[1] }.toList()
    if (eTraits.contains("Obscure")) eTraits = listOf("Obscure") // Masks all other traits!
    
    if (eTraits.isNotEmpty()) {
        tvEnemyTraits.visibility = View.VISIBLE
        tvEnemyTraits.setOnClickListener {
            var desc = ""
            eTraits.forEach { tName ->
                val def = GameData.traits.find { it.name == tName }
                if (def != null) desc += "🔹 [${def.name}]: ${def.desc}\n\n"
            }
            DialogUtils.showCustomDialog(this, "Enemy Traits", desc.trim(), false, "CLOSE", null)
        }
    } else tvEnemyTraits.visibility = View.GONE

    if (currentEnemy != null && !playerLastStand) {
        val pEvo = GameData.beasts.find { it.name.contains(cleanPName.split(" ").last()) }?.evoStage ?: 1
        val eEvo = GameData.beasts.find { it.name.contains(cleanEName.split(" ").last()) }?.evoStage ?: 1
        if (pLvl > eLvl) eScale = (1.0f - (((pLvl - eLvl) * 0.03f) / eEvo)).coerceAtLeast(0.3f)
        else if (eLvl > pLvl) pScale = (1.0f - (((eLvl - pLvl) * 0.03f) / pEvo)).coerceAtLeast(0.3f)
    }
    pSprite.scaleX = pScale; pSprite.scaleY = pScale; eSprite.scaleX = eScale; eSprite.scaleY = eScale
    updateHealthBars()

    pContainer.translationX = -400f; eContainer.translationX = 400f
    pContainer.animate().translationX(20f).setDuration(500).start()
    eContainer.animate().translationX(-20f).setDuration(500).start()
}

internal fun GameActivity.hideBattleArena() {
    val pContainer = findViewById<View>(R.id.spritePlayerContainer)
    val eContainer = findViewById<View>(R.id.spriteEnemyContainer)
    pContainer.animate().translationX(-400f).setDuration(500).start()
    eContainer.animate().translationX(400f).setDuration(500).withEndAction { findViewById<View>(R.id.battleArena).visibility = View.GONE }.start()
}

internal fun GameActivity.updateDispatchButton() {
    val btn = findViewById<Button>(R.id.btnDispatch) ?: return
    if (party.isEmpty()) {
        if (activeExpeditions.containsKey(-1)) { btn.text = "YOU ARE EXPLORING"; btn.isEnabled = false } 
        else { btn.text = "GO EXPLORE (No Beasts)"; btn.isEnabled = true }
    } else {
        if (activeExpeditions.size >= party.size) { btn.text = "ALL NETBEASTS DEPLOYED"; btn.isEnabled = false } 
        else { btn.text = "DISPATCH ALL (${party.size - activeExpeditions.size} idle)"; btn.isEnabled = true }
    }
    val isExploring = activeExpeditions.isNotEmpty()
    findViewById<Button>(R.id.btnFightAether)?.apply { isEnabled = !isExploring; alpha = if (isExploring) 0.5f else 1.0f }
}

internal fun GameActivity.setUIState(state: String) {
    val nav = findViewById<View>(R.id.navTabs); val peace = findViewById<View>(R.id.peaceControls); val battle = findViewById<View>(R.id.battleControls)
    val btnExit = findViewById<View>(R.id.btnExit); val btnFightAether = findViewById<View>(R.id.btnFightAether)

    nav?.visibility = if (state == "BATTLE") View.GONE else View.VISIBLE
    peace?.visibility = if (state == "BATTLE") View.GONE else View.VISIBLE
    battle?.visibility = if (state == "BATTLE") View.VISIBLE else View.GONE
    qteContainer?.visibility = if (state == "BATTLE") View.GONE else View.VISIBLE
    btnExit?.visibility = if (state == "BATTLE") View.GONE else View.VISIBLE
    btnFightAether?.visibility = if (state == "BATTLE") View.GONE else View.VISIBLE
    
    updateDispatchButton()

if (state == "BATTLE") {
        findViewById<View>(R.id.viewActivity)?.visibility = View.VISIBLE
        findViewById<View>(R.id.viewParty)?.visibility = View.GONE
        findViewById<View>(R.id.viewBag)?.visibility = View.GONE
    } else hideBattleArena()
}

internal fun GameActivity.updateBattleUI() {
    val btn1 = findViewById<Button>(R.id.btnMove1)
    val btn2 = findViewById<Button>(R.id.btnMove2)
    val btn3 = findViewById<Button>(R.id.btnMove3) // Safely grabbed from the new XML!
    val infoRow = findViewById<View>(R.id.infoRow)
    val info1 = findViewById<TextView>(R.id.infoMove1)
    val info2 = findViewById<TextView>(R.id.infoMove2)
    val info3 = findViewById<TextView>(R.id.infoMove3)
    val btnSwap = findViewById<Button>(R.id.btnSwap)
    val btnBattleNet = findViewById<Button>(R.id.btnBattleNet)
    val btnBattlePot = findViewById<Button>(R.id.btnBattlePot)
    val btnBattleSpray = findViewById<Button>(R.id.btnBattleSpray)
    val btnAbandon = findViewById<Button>(R.id.btnAbandon)

    if (playerLastStand) {
        btn1.text = "THROW PUNCH"
        btn2.visibility = View.GONE; btn3?.visibility = View.GONE; infoRow.visibility = View.GONE
        btnSwap.visibility = View.GONE; btnBattleNet.visibility = View.GONE; btnBattlePot.visibility = View.GONE; btnBattleSpray.visibility = View.GONE; btnAbandon.visibility = View.GONE
    } else if (party.isNotEmpty()) {
        val p = party[activePetIndex]
        btn1.text = p.move1; btn2.text = p.move2; btn3?.text = p.move3
        
        info1.setOnClickListener { DialogUtils.showCustomDialog(this, p.move1, SkillEngine.getDetails(p.move1, p.maxHp), false, "CLOSE", null) }
        info2.setOnClickListener { DialogUtils.showCustomDialog(this, p.move2, SkillEngine.getDetails(p.move2, p.maxHp), false, "CLOSE", null) }
        info3.setOnClickListener { DialogUtils.showCustomDialog(this, p.move3, SkillEngine.getDetails(p.move3, p.maxHp), false, "CLOSE", null) }

        btn2.visibility = View.VISIBLE; btn3?.visibility = View.VISIBLE; infoRow.visibility = View.VISIBLE
        btnSwap.visibility = View.VISIBLE; btnAbandon.visibility = View.VISIBLE

        if (p.eqNets > 0) { btnBattleNet.visibility = View.VISIBLE; btnBattleNet.text = "NET (${p.eqNets})" } else btnBattleNet.visibility = View.GONE
        if (p.eqPots > 0) { btnBattlePot.visibility = View.VISIBLE; btnBattlePot.text = "POT (${p.eqPots})" } else btnBattlePot.visibility = View.GONE
        if (p.eqSprays > 0) { btnBattleSpray.visibility = View.VISIBLE; btnBattleSpray.text = "SPRY (${p.eqSprays})" } else btnBattleSpray.visibility = View.GONE
    }
    
    startBattleTimer() // START THE PRESSURE TIMER!
}

internal fun GameActivity.cancelBattleTimer() {
    battleTimerRunnable?.let { mainHandler.removeCallbacks(it) }
    findViewById<ProgressBar>(R.id.pbBattleTimer)?.visibility = View.GONE
}

internal fun GameActivity.startBattleTimer() {
    cancelBattleTimer()
    if (battleOver || playerLastStand || currentEnemy == null) return
    
    var ticks = 0
    battleTimerRunnable = object : Runnable {
        override fun run() {
            if (battleOver) return
            ticks++
            
            val pb = findViewById<ProgressBar>(R.id.pbBattleTimer)
            if (ticks == 60) { // 3 Seconds of invisible delay
                pb?.visibility = View.VISIBLE
                pb?.progress = 100
            } else if (ticks in 61..120) { // 3 Seconds of visible bar depletion
                val remaining = 100 - ((ticks - 60) * (100.0 / 60.0)).toInt()
                pb?.progress = remaining
            } else if (ticks > 120) { // TIME IS UP!
                cancelBattleTimer()
                executePlayerMove("Basic Attack")
                return
            }
            mainHandler.postDelayed(this, 50) // Ticks every 50ms
        }
    }
    mainHandler.postDelayed(battleTimerRunnable!!, 50)
}