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

    CombatState.reset()
    arena.visibility = View.VISIBLE
    
    var pScale = 1.0f; var eScale = 1.0f
    val pLvl = if (!playerLastStand) party[activePetIndex].maxHp / 10 else 1
    val eLvl = currentEnemy?.maxHp?.div(10) ?: 1
    
    pSprite.text = "$playerName Lvl $pLvl\n(Player)"
    eSprite.text = "$enemyName Lvl $eLvl\n(Enemy)"
    pSprite.alpha = 1f; eSprite.alpha = 1f

    if (currentEnemy != null && !playerLastStand) {
        val pEvo = GameData.beasts.find { it.name.contains(party[activePetIndex].name.split(" ").last()) }?.evoStage ?: 1
        val eEvo = GameData.beasts.find { it.name.contains(currentEnemy!!.name.split(" ").last()) }?.evoStage ?: 1
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
    
    // Grey out Fight Aether button while exploring!
    val isExploring = activeExpeditions.isNotEmpty()
    findViewById<Button>(R.id.btnFightAether)?.apply {
        isEnabled = !isExploring
        alpha = if (isExploring) 0.5f else 1.0f
    }
}

internal fun GameActivity.setUIState(state: String) {
    val nav = findViewById<View>(R.id.navTabs)
    val peace = findViewById<View>(R.id.peaceControls)
    val battle = findViewById<View>(R.id.battleControls)

    nav.visibility = if (state == "BATTLE") View.GONE else View.VISIBLE
    peace.visibility = if (state == "BATTLE") View.GONE else View.VISIBLE
    battle.visibility = if (state == "BATTLE") View.VISIBLE else View.GONE
    qteContainer.visibility = if (state == "BATTLE") View.GONE else View.VISIBLE
    updateDispatchButton()

    if (state == "BATTLE") {
        findViewById<View>(R.id.viewActivity).visibility = View.VISIBLE
        findViewById<View>(R.id.viewParty).visibility = View.GONE
        findViewById<View>(R.id.viewBag).visibility = View.GONE
    } else hideBattleArena()
}

internal fun GameActivity.updateBattleUI() {
    val btn1 = findViewById<Button>(R.id.btnMove1)
    val btn2 = findViewById<Button>(R.id.btnMove2)
    val parentGroup = btn2.parent as? ViewGroup
    val btn3 = parentGroup?.findViewWithTag<Button>("BTN_MOVE_3")
    val btnSwap = findViewById<Button>(R.id.btnSwap)
    val btnItem = findViewById<Button>(R.id.btnItem)
    val btnAbandon = findViewById<Button>(R.id.btnAbandon)

    if (playerLastStand) {
        btn1.text = "THROW PUNCH"
        btn2.visibility = View.GONE; btn3?.let { it.visibility = View.GONE }; btnSwap.visibility = View.GONE
        btnItem.visibility = View.GONE; btnAbandon.visibility = View.GONE
    } else if (party.isNotEmpty()) {
        btn1.text = party[activePetIndex].move1; btn2.text = party[activePetIndex].move2; btn3?.let { it.text = party[activePetIndex].move3 }
        btn2.visibility = View.VISIBLE; btn3?.let { it.visibility = View.VISIBLE }; btnSwap.visibility = View.VISIBLE
        btnItem.visibility = View.VISIBLE; btnAbandon.visibility = View.VISIBLE

        val p = party[activePetIndex]
        if (p.eqPots <= 0 && p.eqSprays <= 0) btnItem.visibility = View.GONE
        else { btnItem.visibility = View.VISIBLE; btnItem.text = "BAG (${p.eqPots}P/${p.eqSprays}S)" }
    }
}
