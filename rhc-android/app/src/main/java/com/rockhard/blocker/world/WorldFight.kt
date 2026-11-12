package com.rockhard.blocker

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.rockhard.blocker.world.WorldView
import com.rockhard.blocker.world.battle.FightInput
import com.rockhard.blocker.world.battle.FightItem
import com.rockhard.blocker.world.battle.FightOutcome
import com.rockhard.blocker.world.battle.FightSetup
import com.rockhard.blocker.world.battle.FightStyle
import com.rockhard.blocker.world.battle.SideKind
import com.rockhard.blocker.world.battle.SideSetup
import com.rockhard.blocker.world.battle.WildSpec
import com.rockhard.blocker.world.sim.EncounterOutcome
import com.rockhard.blocker.world.sim.WorldEvent

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
// The World runs the fight (world-core's battle rules, the same as the
// battle screen's): the phone starts it, sends the panel's taps in as
// FightInputs, and shows what the rules say (FightHost.kt's applyFight; the
// World poses the fighters and throws the cages itself). When it closes, the
// World has already resolved the encounter and the walk goes on.

internal class WorldFight(val beastId: Int, val fightId: Int, val view: FightView) {
    var partyLead: Netbeast? = null // the party's own lead, put back after the fight
    var closed = false              // the rules closed it (the World resolved the encounter)
}

internal fun GameActivity.onWorldEncounter(e: WorldEvent.Encounter) {
    val s = worldSession ?: return
    val visitor = worldVisitors[e.beastId]
    val foe = if (visitor != null) {
        setUpVisitorBattle(visitor)
        foeSide() ?: return
    } else {
        currentEnemy = null; isWildBattle = true; isTrainerBattle = false
        SideSetup(SideKind.WILD, wild = WildSpec(e.species, e.stage)) // the rules roll it from your lead
    }
    val cages = walkOrder()
    val setup = FightSetup(FightStyle.WILDS, currentWeather, System.currentTimeMillis(), currentCity)
    val id = s.startFight(e.beastId, setup, mySide(cages, -1), foe)
    val fight = s.world.fights[id]
    if (fight == null) { s.resolveEncounter(e.beastId, EncounterOutcome.PLAYER_FLED); return }
    val v = FightView(fight, s.world.sideOf(s.localPlayerId)?.second ?: 0, wilds = true)
    v.rescueName = capturedRescueTarget?.name
    currentEnemy = fight.sides.firstOrNull { !it.isPlayer }?.party?.firstOrNull()
    playerLastStand = v.me.lastStand
    battleOver = false
    worldFight = WorldFight(e.beastId, id, v).also { it.partyLead = party.getOrNull(activePetIndex) }
    qteContainer.visibility = View.GONE // nothing else starts a battle mid-fight
    refreshWorldFightPanel()
}

/** What the rules said about our fight, from the World. */
internal fun GameActivity.onWorldFighting(e: WorldEvent.Fighting) {
    val f = worldFight ?: return
    if (e.fightId == f.fightId) applyFight(f.view, listOf(e.event))
}

internal fun GameActivity.onWorldCompanionOut(@Suppress("UNUSED_PARAMETER") e: WorldEvent.CompanionOut) = refreshWorldFightPanel()

/** The poacher's netbeast is out of its cage: it's the one you fight now. */
internal fun GameActivity.onWorldRivalOut(@Suppress("UNUSED_PARAMETER") e: WorldEvent.RivalOut) = refreshWorldFightPanel()

/**
 * Called when the fight closes (closed -> HUB) or the walk ends: the walk goes on. If the rules
 * hadn't closed it, the encounter ends here, as the rules left it.
 */
internal fun GameActivity.endWorldFight() {
    val f = worldFight ?: return
    if (!f.closed) {
        val r = f.view.fight.result
        val outcome = when (r?.outcomes?.get(f.view.side)) {
            FightOutcome.WON, FightOutcome.CAUGHT -> EncounterOutcome.BEAST_DEFEATED
            FightOutcome.BEATEN -> EncounterOutcome.PLAYER_BEATEN
            else -> EncounterOutcome.PLAYER_FLED
        }
        worldSession?.resolveEncounter(f.beastId, outcome)
    }
    // the fight sent out netbeasts in walk order; the party's lead is unchanged (unless it fell)
    activePetIndex = party.indexOfFirst { it === f.partyLead }.takeIf { it >= 0 } ?: 0
    prefs.edit().putInt("ACTIVE_PET_INDEX", activePetIndex).apply()
    worldFight = null
    if (!battleOver) { battleOver = true; isWildBattle = false; isTrainerBattle = false }
    playerLastStand = false
    findViewById<View>(R.id.worldFightPanel)?.visibility = View.GONE
    refreshWalkCages()
}

/** What the HUD needs: both fighters' health and how patient the beast still is. */
internal fun GameActivity.worldFightHud() = object : WorldView.FightHud {
    override fun enemy() = worldFight?.view?.let { v ->
        if (v.fight.foeArriving(v.me)) null else (v.fight.foeOf(v.me) ?: v.fight.sides.firstOrNull { !it.isPlayer })?.pet()
    }?.let { WorldView.Fighter(speciesOf(it), it.maxHp / 10, it.hp, it.maxHp) }
    override fun mine() = worldFight?.view?.me?.pet()?.let { WorldView.Fighter(speciesOf(it), it.maxHp / 10, it.hp, it.maxHp) }
    override fun patience(): Float = worldFight?.view?.let { it.fight.patienceShown(it.me) } ?: -1f
}

/** From printLog: battle log lines as captions over the world. */
internal fun GameActivity.worldCaption(msg: String) {
    val view = findViewById<WorldView>(R.id.worldView) ?: return
    msg.split('\n').forEach { view.caption(it) }
}

// ---------------------------------------------------------------- the panel

internal fun GameActivity.setupWorldFightControls() {
    findViewById<Button>(R.id.worldMove1)?.setOnClickListener { fightInput(FightInput(move = 1)) }
    findViewById<Button>(R.id.worldMove2)?.setOnClickListener { fightInput(FightInput(move = 2)) }
    findViewById<Button>(R.id.worldMove3)?.setOnClickListener { fightInput(FightInput(move = 3)) }
    // hold a move for what it does
    for ((id, n) in listOf(R.id.worldMove1 to 1, R.id.worldMove2 to 2, R.id.worldMove3 to 3)) {
        findViewById<Button>(id)?.setOnLongClickListener {
            val p = myFighter()
            if (p != null && !playerLastStand) worldCaption(SkillEngine.getDetails(moveOf(p, n), p.maxHp))
            true
        }
    }
    findViewById<Button>(R.id.worldNet)?.setOnClickListener { worldItem("nets", { it.eqNets }, FightItem.NET) }
    findViewById<Button>(R.id.worldPot)?.setOnClickListener { worldItem("potions", { it.eqPots }, FightItem.POTION) }
    findViewById<Button>(R.id.worldSpray)?.setOnClickListener { worldItem("sprays", { it.eqSprays }, FightItem.SPRAY) }
}

/** An item button: uses it, or, when the netbeast that's out has none equipped, says where to get some. */
private fun GameActivity.worldItem(what: String, count: (Netbeast) -> Int, item: FightItem) {
    val p = myFighter() ?: return
    if (count(p) <= 0) { worldCaption("${speciesOf(p)} has no $what equipped. Equip them in the Bag."); return }
    fightInput(FightInput(item = item))
}

private fun moveOf(p: Netbeast, n: Int) = when (n) { 1 -> p.move1; 2 -> p.move2; else -> p.move3 }

/** Rebuilds the left panel for where the fight is: cages to pick, a cage in the air, or moves. */
internal fun GameActivity.refreshWorldFightPanel() {
    val panel = findViewById<LinearLayout>(R.id.worldFightPanel) ?: return
    val f = worldFight
    if (f == null) { panel.visibility = View.GONE; refreshWalkCages(); return }
    panel.visibility = View.VISIBLE
    refreshWalkCages()
    val fight = f.view.fight
    val me = f.view.me
    val title = findViewById<TextView>(R.id.worldFightTitle)
    val cages = findViewById<LinearLayout>(R.id.worldCages)
    val moves = findViewById<View>(R.id.worldMoves)
    val items = findViewById<View>(R.id.worldItems)
    val b1 = findViewById<Button>(R.id.worldMove1); val b2 = findViewById<Button>(R.id.worldMove2); val b3 = findViewById<Button>(R.id.worldMove3)
    cages.removeAllViews()
    items.visibility = View.GONE
    when {
        fight.over -> {
            title.text = ""
            moves.visibility = View.GONE
        }
        me.lastStand -> {
            // all your netbeasts are down (or you had none): the beast turns on you
            title.text = "NO NETBEASTS LEFT"
            moves.visibility = View.VISIBLE
            b1.text = "👊 THROW PUNCH"; b2.visibility = View.GONE; b3.visibility = View.GONE
        }
        me.active < 0 -> {
            title.text = "THROW A CAGE"
            moves.visibility = View.GONE
            me.available().forEach { i -> cages.addView(cageView(i, me.party[i], compact = false)) }
        }
        me.arriving -> {
            title.text = "${speciesOf(me.party[me.active])} is coming out…"
            moves.visibility = View.GONE
        }
        else -> {
            val p = me.pet() ?: return
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
            me.available().filter { it != me.active }.chunked(4).forEach { rowIdx ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                rowIdx.forEach { row.addView(cageView(it, me.party[it], compact = true)) }
                cages.addView(row)
            }
        }
    }
    // the panel waits until his netbeast is out
    if (fight.foeArriving(me) && !fight.over && !me.lastStand && !me.arriving) title.text = "THE POACHER THROWS A CAGE…"
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
    // slate, not near-black: the beasts are dark, and they need to show as a shape between the bars
    return GradientDrawable().apply { cornerRadius = 8 * d; setColor(Color.argb(200, 64, 74, 88)); setStroke(d.toInt().coerceAtLeast(1), Color.argb(120, 255, 214, 102)) }
}

/** A cage to tap in a fight ([i]: its place in your side of the fight); the full size adds its name, level and health. */
private fun GameActivity.cageView(i: Int, p: Netbeast, compact: Boolean): View {
    val d = resources.displayMetrics.density
    fun px(dp: Int) = (dp * d).toInt()
    val icon = cageIcon(p)
    val bg = cageBackground()
    val tap = View.OnClickListener { fightInput(FightInput(cage = i)) }
    if (compact) {
        icon.background = bg
        icon.layoutParams = LinearLayout.LayoutParams(px(34), px(34)).apply { marginEnd = px(3); bottomMargin = px(3) }
        icon.setOnClickListener(tap)
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
    row.setOnClickListener(tap)
    return row
}
