package com.rockhard.blocker

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.rockhard.blocker.world.WorldView
import com.rockhard.blocker.world.sim.Action
import com.rockhard.blocker.world.sim.EncounterOutcome
import com.rockhard.blocker.world.sim.Role
import com.rockhard.blocker.world.sim.WorldEvent
import kotlin.random.Random

// Fights in the 3D Wilds, without leaving the world. A wild beast squares up
// and you circle each other; you pick a netbeast from the cages down the left
// and throw it; it comes out and the two beasts circle each other while you
// circle them, shouting moves from the buttons on the left. Take too long and
// the beast acts on its own: before a cage it lunges at you and knocks one
// loose, after that it gets a free hit on your netbeast.
//
// A poacher or the invasion boss put in your path (WorldVisitors.kt) is fought
// the same way; the poacher throws his netbeasts' cages one at a time, and
// your cages wait until his netbeast is out.
//
// The rules are the normal battle code in engines/combat (executePlayerMove,
// triggerEnemyCounterAttack, the item buttons): the world only presents them.
// The hooks are playSpriteAnim/playFx (poses and effects on the fighters),
// showBattleArena (a new netbeast goes in), the battle timer (the beast's
// patience) and printLog (captions over the world).

/** How long the wild beast waits for you before it acts on its own. */
private const val PATIENCE_MS = 7000L
private const val TICK_MS = 100L
/** With no netbeasts left, the beast strikes you this long after it turns on you (punches don't slow it). */
private const val LAST_STAND_MS = 3000L
/** Punches can be thrown this fast (they take no turn). */
private const val PUNCH_GAP_MS = 150L
/** A beat so you see your netbeast fall before the next cage flies. */
private const val NEXT_CAGE_DELAY_MS = 1200L

internal class WorldFight(val beastId: Int) {
    var outName: String? = null     // the netbeast that's out (or whose cage is in the air)
    var cageInAir = false           // the panel waits for CompanionOut
    var freeHit = false             // the beast knocked the cage loose: it strikes as soon as the netbeast is out
    var patienceMs = PATIENCE_MS
    var busyUntil = 0L              // an exchange is playing out: patience waits
    var lastTap = 0L                // no double-taps while a move resolves
    var lastStand = false           // its clock is LAST_STAND_MS now, and punches don't reset it
    var clobbered = false           // it hit YOU (a last stand lost): you're knocked flat when the fight ends
    var partyLead: Netbeast? = null // the party's own lead, put back after the fight
    var foeOut: Netbeast? = null    // a poacher's netbeast that's out in the world (or whose cage is in the air)
    var foeCageInAir = false        // ...the fight waits for RivalOut
    var foesOut = 0                 // how many of his have come out
    var tick: Runnable? = null
}

internal fun GameActivity.onWorldEncounter(e: WorldEvent.Encounter) {
    val visitor = worldVisitors[e.beastId]
    val partyLead = party.getOrNull(activePetIndex)
    val lead = if (party.isEmpty()) -1 else walkLeadIndex() // the top cage in the walk's column
    if (visitor != null) setUpVisitorBattle(visitor) else {
        val def = GameData.beasts.find { it.name == e.species } ?: GameData.beasts.random()
        val refHp = if (lead == -1) 20 else party[lead].maxHp
        // Territory further from the start (higher evo stage) hits harder
        val pl = (refHp * (0.6 + 0.2 * e.stage) * Random.nextDouble(0.85, 1.15)).toInt().coerceAtLeast(10)
        val name = if (Random.nextInt(100) < 10) "[Obscure] ${def.name}" else def.name
        currentEnemy = Netbeast(name, "Wild", pl, pl, def.m1, def.m2, "Tackle", 0L, 0, 0, 0, false, "None", 0, 0, 0, 0, "None", 0)
        isWildBattle = true; isTrainerBattle = false; battleOver = false
    }
    val enemy = currentEnemy ?: return
    playerLastStand = party.isEmpty(); participatingPets.clear()
    if (lead >= 0) activePetIndex = lead
    resetArenaRules()
    val f = WorldFight(e.beastId).also { it.partyLead = partyLead }
    worldFight = f
    qteContainer.visibility = View.GONE // nothing else starts a battle mid-fight
    val cagesOrFists = if (party.isEmpty()) "> You have no netbeasts. Get your fists up!" else "> Quick! Pick a cage to throw!"
    when (visitor?.kind) {
        VisitorKind.POACHER -> {
            printLog("\n> 🦹 POACHER: 'Come and take it!'\n> He throws a cage...")
            f.foeOut = enemy; f.foeCageInAir = true
            worldSession?.rivalSendOut(speciesOf(enemy))
        }
        VisitorKind.BOSS -> {
            printLog("\n> 👑 ${speciesOf(enemy)} (HP: ${enemy.maxHp}) rears up in front of you! Defend $currentCity!")
            printLog(cagesOrFists)
        }
        null -> {
            printLog("\n> ⚠️ A wild ${enemy.name} (HP: ${enemy.maxHp}) squares up to you! You circle each other...")
            printLog(cagesOrFists)
        }
    }
    vibratePhone(80)
    refreshWorldFightPanel()
    startPatience()
}

internal fun GameActivity.onWorldCompanionOut(e: WorldEvent.CompanionOut) {
    val f = worldFight ?: return
    f.cageInAir = false
    val p = party.getOrNull(activePetIndex) ?: return
    printLog("> ${p.name} bursts out to face ${foeLabel()}!")
    updateBattleUI() // the moves appear, and the beast's patience starts over
    if (f.freeHit) {
        f.freeHit = false
        cancelBattleTimer()
        mainHandler.postDelayed({ if (worldFight === f && !battleOver) triggerEnemyCounterAttack() }, 500)
    }
}

/** The poacher's netbeast is out of its cage: it's the one you fight now, and its patience starts full. */
internal fun GameActivity.onWorldRivalOut(e: WorldEvent.RivalOut) {
    val f = worldFight ?: return
    f.foeCageInAir = false
    f.patienceMs = if (f.lastStand) LAST_STAND_MS else PATIENCE_MS
    if (f.foesOut++ == 0) {
        printLog("> The poacher's ${currentEnemy?.name} bursts out of its cage!")
        printLog(if (party.isEmpty()) "> You have no netbeasts. Get your fists up!" else "> Quick! Pick a cage to throw!")
    }
    refreshWorldFightPanel()
}

/** A poacher's netbeast is on its way: his last one fell or he swapped, and the new one isn't out yet. */
private fun GameActivity.foeArriving(f: WorldFight) = f.foeCageInAir || (isTrainerBattle && currentEnemy !== f.foeOut)

/** Who you're up against, for the log: "the wild Cacheon", "the poacher's Cacheon", or the boss by name. */
private fun GameActivity.foeLabel(): String {
    val e = currentEnemy ?: return "the beast"
    return when {
        isTrainerBattle -> "the poacher's ${e.name}"
        e.type == "EventBoss" -> speciesOf(e)
        else -> "the wild ${e.name}"
    }
}

/** Called when the battle ends (endBattle -> HUB) or the walk does: the walk goes on. */
internal fun GameActivity.endWorldFight() {
    val f = worldFight ?: return
    f.tick?.let { mainHandler.removeCallbacks(it) }
    val enemy = currentEnemy
    // beaten, or caught in a net (then it's in your party now)
    val won = enemy != null && (enemy.hp <= 0 || party.any { it === enemy })
    val outcome = when {
        won -> EncounterOutcome.BEAST_DEFEATED
        f.clobbered -> EncounterOutcome.PLAYER_BEATEN // you pick yourself up off the ground
        else -> EncounterOutcome.PLAYER_FLED
    }
    worldSession?.resolveEncounter(f.beastId, outcome)
    // the fight sent out netbeasts in walk order; the party's lead is unchanged (unless it fell)
    activePetIndex = party.indexOfFirst { it === f.partyLead }.takeIf { it >= 0 } ?: 0
    prefs.edit().putInt("ACTIVE_PET_INDEX", activePetIndex).apply()
    worldFight = null
    if (!battleOver) { battleOver = true; isWildBattle = false }
    findViewById<View>(R.id.worldFightPanel)?.visibility = View.GONE
    refreshWalkCages()
}

/** What the HUD needs: both fighters' health and how patient the beast still is. */
internal fun GameActivity.worldFightHud() = object : WorldView.FightHud {
    override fun enemy() = currentEnemy?.takeIf { worldFight?.let { f -> !foeArriving(f) } == true }?.let {
        WorldView.Fighter(speciesOf(it), it.maxHp / 10, it.hp, it.maxHp)
    }
    override fun mine() = party.getOrNull(activePetIndex)?.takeIf { worldFight?.outName != null && !playerLastStand }?.let {
        WorldView.Fighter(speciesOf(it), it.maxHp / 10, it.hp, it.maxHp)
    }
    override fun patience(): Float {
        val f = worldFight ?: return -1f
        return if (f.cageInAir || battleOver || foeArriving(f)) -1f else (f.patienceMs.toFloat() / if (f.lastStand) LAST_STAND_MS else PATIENCE_MS).coerceIn(0f, 1f)
    }
}

// ---------------------------------------------------------------- hooks from the battle code

/** From cancelBattleTimer: you acted, so the beast's patience starts over once the exchange plays out. */
internal fun GameActivity.worldPatienceReset() {
    val f = worldFight ?: return
    if (f.lastStand) return
    f.patienceMs = PATIENCE_MS
    f.busyUntil = SystemClock.uptimeMillis() + 1800
}

/** From playSpriteAnim: the same pose on the fighter in the world. */
internal fun GameActivity.worldAct(isPlayer: Boolean, name: String, anim: String) {
    if (worldFight == null) return
    val action = when (anim) { "attack" -> Action.ATTACK; "hit" -> Action.HIT; "faint" -> Action.FAINT; "victory" -> Action.VICTORY; else -> return }
    val role = roleFor(isPlayer, name)
    // in the world the beast only ever hits YOU with the blow that ends a lost last stand
    if (role == Role.PLAYER && action == Action.HIT) worldFight?.clobbered = true
    worldSession?.perform(role, action)
}

/** From playFx: the effect over the fighter in the world. */
internal fun GameActivity.worldFx(onPlayer: Boolean, fx: String) {
    if (worldFight == null) return
    worldSession?.effect(roleFor(onPlayer, if (playerLastStand) "YOU" else ""), fx)
}

private fun GameActivity.roleFor(isPlayer: Boolean, name: String) = when {
    !isPlayer -> Role.BEAST
    playerLastStand || name.equals("YOU", ignoreCase = true) -> Role.PLAYER
    else -> Role.COMPANION
}

/**
 * From showBattleArena: a different netbeast is fighting now (yours fell and
 * the next was sent out). After a beat so you see it fall, its cage flies.
 */
internal fun GameActivity.worldFighterChanged(name: String) {
    val f = worldFight ?: return
    // or the poacher sent out another (his last one fell, or he swapped): its cage flies in
    val enemy = currentEnemy
    if (isTrainerBattle && enemy != null && enemy !== f.foeOut) {
        f.foeOut = enemy; f.foeCageInAir = true
        worldSession?.rivalSendOut(speciesOf(enemy))
        refreshWorldFightPanel()
        return
    }
    if (name == "YOU" || playerLastStand) return // refreshWorldFightPanel recalls the fallen one
    val p = party.getOrNull(activePetIndex) ?: return
    if (f.outName == p.name && !f.cageInAir) return
    f.cageInAir = true
    refreshWorldFightPanel()
    mainHandler.postDelayed({ if (worldFight === f && !battleOver) throwWorldCage(activePetIndex) }, NEXT_CAGE_DELAY_MS)
}

/** From printLog: battle log lines as captions over the world. */
internal fun GameActivity.worldCaption(msg: String) {
    val view = findViewById<WorldView>(R.id.worldView) ?: return
    msg.split('\n').forEach { view.caption(it) }
}

// ---------------------------------------------------------------- the panel

internal fun GameActivity.setupWorldFightControls() {
    findViewById<Button>(R.id.worldMove1)?.setOnClickListener { worldMove(1) }
    findViewById<Button>(R.id.worldMove2)?.setOnClickListener { worldMove(2) }
    findViewById<Button>(R.id.worldMove3)?.setOnClickListener { worldMove(3) }
    // hold a move for what it does
    for ((id, n) in listOf(R.id.worldMove1 to 1, R.id.worldMove2 to 2, R.id.worldMove3 to 3)) {
        findViewById<Button>(id)?.setOnLongClickListener {
            val p = party.getOrNull(activePetIndex)
            if (p != null && !playerLastStand) worldCaption(SkillEngine.getDetails(moveOf(p, n), p.maxHp))
            true
        }
    }
    // the items reuse the battle screen's buttons, so the rules stay in one place
    findViewById<Button>(R.id.worldNet)?.setOnClickListener { worldItem("nets", { it.eqNets }, R.id.btnBattleNet) }
    findViewById<Button>(R.id.worldPot)?.setOnClickListener { worldItem("potions", { it.eqPots }, R.id.btnBattlePot) }
    findViewById<Button>(R.id.worldSpray)?.setOnClickListener { worldItem("sprays", { it.eqSprays }, R.id.btnBattleSpray) }
}

/**
 * An item button: uses it through the battle screen's button, or, when the netbeast that's
 * out has none equipped, says where to get some (without passing the tap on, which would
 * reset the beast's patience).
 */
private fun GameActivity.worldItem(what: String, count: (Netbeast) -> Int, battleButton: Int) {
    val p = party.getOrNull(activePetIndex) ?: return
    if (count(p) <= 0) { worldCaption("${speciesOf(p)} has no $what equipped. Equip them in the Bag."); return }
    worldTap { findViewById<Button>(battleButton)?.performClick() }
}

private fun moveOf(p: Netbeast, n: Int) = when (n) { 1 -> p.move1; 2 -> p.move2; else -> p.move3 }

/** Runs a tap on the panel unless a cage (yours or his) is in the air or you just tapped. */
private inline fun GameActivity.worldTap(action: () -> Unit) {
    val f = worldFight ?: return
    val now = SystemClock.uptimeMillis()
    if (battleOver || f.cageInAir || foeArriving(f) || now - f.lastTap < 900) return
    f.lastTap = now
    action()
}

private fun GameActivity.worldMove(n: Int) {
    val f = worldFight ?: return
    if (playerLastStand) {
        val now = SystemClock.uptimeMillis()
        if (battleOver || foeArriving(f) || now - f.lastTap < PUNCH_GAP_MS) return
        f.lastTap = now
        executeHumanPunch()
    } else worldTap { party.getOrNull(activePetIndex)?.let { executePlayerMove(moveOf(it, n)) } }
}

private fun GameActivity.worldCageTapped(i: Int) = worldTap {
    val f = worldFight ?: return@worldTap
    if (i !in party.indices) return@worldTap
    if (f.outName == null) {
        cancelBattleTimer()
        printLog("> You throw ${party[i].name}'s cage!")
        throwWorldCage(i)
    } else if (i != activePetIndex) {
        // a swap costs a turn, as on the battle screen: the beast strikes once the new one is out
        cancelBattleTimer()
        printLog("\n> You swapped to ${party[i].name}!")
        resetArenaRules()
        f.freeHit = true
        throwWorldCage(i)
    }
}

private fun GameActivity.throwWorldCage(i: Int) {
    val f = worldFight ?: return
    val p = party.getOrNull(i) ?: return
    activePetIndex = i
    f.outName = p.name; f.cageInAir = true
    worldSession?.throwCage(speciesOf(p))
    refreshWorldFightPanel()
}

/** Rebuilds the left panel for where the fight is: cages to pick, a cage in the air, or moves. */
internal fun GameActivity.refreshWorldFightPanel() {
    val panel = findViewById<LinearLayout>(R.id.worldFightPanel) ?: return
    val f = worldFight
    if (f == null) { panel.visibility = View.GONE; refreshWalkCages(); return }
    panel.visibility = View.VISIBLE
    refreshWalkCages()
    val title = findViewById<TextView>(R.id.worldFightTitle)
    val cages = findViewById<LinearLayout>(R.id.worldCages)
    val moves = findViewById<View>(R.id.worldMoves)
    val items = findViewById<View>(R.id.worldItems)
    val b1 = findViewById<Button>(R.id.worldMove1); val b2 = findViewById<Button>(R.id.worldMove2); val b3 = findViewById<Button>(R.id.worldMove3)
    cages.removeAllViews()
    items.visibility = View.GONE
    when {
        playerLastStand -> {
            // all your netbeasts are down (or you had none): back in its cage, the beast turns on you
            if (f.outName != null) {
                f.outName = null
                mainHandler.postDelayed({ if (worldFight === f) worldSession?.recall() }, NEXT_CAGE_DELAY_MS)
            }
            if (!f.lastStand) {
                f.lastStand = true; f.patienceMs = LAST_STAND_MS
                f.busyUntil = SystemClock.uptimeMillis() + NEXT_CAGE_DELAY_MS // it turns on you once yours is gone
            }
            title.text = "NO NETBEASTS LEFT"
            moves.visibility = View.VISIBLE
            b1.text = "👊 THROW PUNCH"; b2.visibility = View.GONE; b3.visibility = View.GONE
        }
        f.outName == null -> {
            title.text = "THROW A CAGE"
            moves.visibility = View.GONE
            walkOrder().forEach { p -> cages.addView(cageView(party.indexOfFirst { it === p }, p, compact = false)) }
        }
        f.cageInAir -> {
            title.text = "${speciesOf(party.getOrNull(activePetIndex) ?: return)} is coming out…"
            moves.visibility = View.GONE
        }
        else -> {
            val p = party.getOrNull(activePetIndex) ?: return
            title.text = "SHOUT A MOVE"
            moves.visibility = View.VISIBLE
            b1.text = p.move1; b2.text = p.move2; b3.text = p.move3
            b2.visibility = View.VISIBLE; b3.visibility = View.VISIBLE
            // always there once a netbeast is out; dimmed when it has none equipped
            for ((btn, label, n) in listOf(
                Triple(findViewById<Button>(R.id.worldNet), "NET", p.eqNets),
                Triple(findViewById<Button>(R.id.worldPot), "POT", p.eqPots),
                Triple(findViewById<Button>(R.id.worldSpray), "SPRY", p.eqSprays),
            )) { btn.text = "$label $n"; btn.alpha = if (n > 0) 1f else 0.4f }
            items.visibility = View.VISIBLE
            // the others, small, to swap in (costs a turn)
            val others = walkOrder().map { o -> party.indexOfFirst { it === o } }.filter { it >= 0 && it != activePetIndex }
            others.chunked(4).forEach { rowIdx ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                rowIdx.forEach { row.addView(cageView(it, party[it], compact = true)) }
                cages.addView(row)
            }
        }
    }
    // the panel waits (worldTap) until his netbeast is out
    if (foeArriving(f) && !playerLastStand && !f.cageInAir) title.text = "THE POACHER THROWS A CAGE…"
}

/** The netbeast's idle sprite behind the cage bars (also the walk's cage column, WorldCages.kt). */
internal fun GameActivity.cageIcon(p: Netbeast): FrameLayout {
    val icon = FrameLayout(this)
    icon.addView(GifView(this).apply { setGifResource(SpriteUtils.resolveSpriteRes(this@cageIcon, p.name, "idle")) },
        FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    icon.addView(GifView(this).apply { setGifResource(resources.getIdentifier("prop_cage", "drawable", packageName)) },
        FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    return icon
}

internal fun GameActivity.cageBackground(): GradientDrawable {
    val d = resources.displayMetrics.density
    return GradientDrawable().apply { cornerRadius = 8 * d; setColor(Color.argb(170, 16, 20, 24)); setStroke(d.toInt().coerceAtLeast(1), Color.argb(120, 255, 214, 102)) }
}

/** A cage to tap in a fight; the full size adds its name, level and health. */
private fun GameActivity.cageView(i: Int, p: Netbeast, compact: Boolean): View {
    val d = resources.displayMetrics.density
    fun px(dp: Int) = (dp * d).toInt()
    val icon = cageIcon(p)
    val bg = cageBackground()
    if (compact) {
        icon.background = bg
        icon.layoutParams = LinearLayout.LayoutParams(px(34), px(34)).apply { marginEnd = px(3); bottomMargin = px(3) }
        icon.setOnClickListener { worldCageTapped(i) }
        return icon
    }
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = bg; setPadding(px(3), px(3), px(6), px(3))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = px(5) }
    }
    row.addView(icon, LinearLayout.LayoutParams(px(42), px(42)))
    val lvl = p.maxHp / 10
    val diff = lvl - (currentEnemy?.maxHp ?: 0) / 10
    row.addView(TextView(this).apply {
        text = "${speciesOf(p)}\nLv $lvl (${if (diff >= 0) "+$diff" else "$diff"}) · ${p.type}\n❤ ${p.hp}/${p.maxHp}"
        setTextColor(Color.WHITE); textSize = 10f; setShadowLayer(2f, 0f, 0f, Color.BLACK)
        setPadding(px(4), 0, 0, 0)
    })
    row.setOnClickListener { worldCageTapped(i) }
    return row
}

// ---------------------------------------------------------------- the beast's patience

private fun GameActivity.startPatience() {
    val f = worldFight ?: return
    f.tick?.let { mainHandler.removeCallbacks(it) }
    val r = object : Runnable {
        override fun run() {
            if (worldFight !== f || battleOver) return
            val running = findViewById<WorldView>(R.id.worldView)?.isRunning == true // paused while the app is
            if (running && !f.cageInAir && !foeArriving(f) && SystemClock.uptimeMillis() >= f.busyUntil) {
                f.patienceMs -= TICK_MS
                if (f.patienceMs <= 0) { f.patienceMs = PATIENCE_MS; beastLosesPatience(f) }
            }
            mainHandler.postDelayed(this, TICK_MS)
        }
    }
    f.tick = r
    mainHandler.postDelayed(r, TICK_MS)
}

/** You took too long: the beast acts on its own. */
private fun GameActivity.beastLosesPatience(f: WorldFight) {
    val enemy = currentEnemy ?: return
    when {
        f.outName == null && party.isNotEmpty() -> {
            // it lunges at you and knocks a cage loose: that netbeast has to fight, and takes the first hit
            val i = Random.nextInt(party.size)
            printLog("\n> ⏳ You hesitated! ${foeLabel().replaceFirstChar { it.uppercase() }} lunges at you!\n> ${party[i].name}'s cage is knocked from your hands and bursts open!")
            playSpriteAnim(false, enemy.name, "attack")
            vibratePhone(200)
            f.freeHit = true
            throwWorldCage(i)
        }
        playerLastStand -> {
            cancelBattleTimer()
            printLog("\n--- ENEMY TURN ---\n> ⏳ You hesitated! ${foeLabel().replaceFirstChar { it.uppercase() }} lunges at YOU!")
            playSpriteAnim(false, enemy.name, "attack")
            mainHandler.postDelayed({
                if (worldFight !== f || battleOver) return@postDelayed
                playSpriteAnim(true, "YOU", "hit"); vibratePhone(800)
                printLog("> ${enemy.name} obliterates you for 9,999 damage.")
                endBattle()
            }, 450)
        }
        else -> {
            cancelBattleTimer()
            printLog("\n> ⏳ You hesitated! ${foeLabel().replaceFirstChar { it.uppercase() }} strikes first!")
            triggerEnemyCounterAttack()
        }
    }
}
