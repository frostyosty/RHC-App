package com.rockhard.blocker

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.ProgressBar
import com.rockhard.blocker.world.battle.Fight
import com.rockhard.blocker.world.battle.FightEvent
import com.rockhard.blocker.world.battle.FightInput
import com.rockhard.blocker.world.battle.FightOutcome
import com.rockhard.blocker.world.battle.FightSetup
import com.rockhard.blocker.world.battle.FightStyle
import com.rockhard.blocker.world.battle.LocalFight
import com.rockhard.blocker.world.battle.Motion
import com.rockhard.blocker.world.battle.SideKind
import com.rockhard.blocker.world.battle.SideSetup
import kotlin.random.Random

// The phone's side of a fight. The rules are world-core's (world/battle/Fight.kt),
// the same for the battle screen, the 3D Wilds, the web and the server: this
// file hands them the sides, passes taps on as FightInputs, and shows what
// they say (FightEvents): the log, the sprites, the sounds, and the save.
//
// The battle screen runs its fight here, a LocalFight on a 30 Hz loop. In the
// Wilds the World runs it (WorldFight.kt) and its events come back through
// WorldView, to the same applyFight.
//
// GameActivity's battle fields (currentEnemy, enemyParty, isTrainerBattle,
// isUnderAttack, playerLastStand) say which fight a starter wants; once it has
// begun they only mirror the rules, for the screens that read them.

/** A fight on show: the rules, and which side is the phone's. */
internal class FightView(val fight: Fight, val side: Int, val wilds: Boolean) {
    /** The name each side's sprite shows, for its poses. */
    val shown = HashMap<Int, String>()
    /** A poacher's prisoner's name before the rules rename it (the rescue list goes by it). */
    var rescueName: String? = null

    val me get() = fight.sides[side]
}

/** The battle screen's fight and its loop. */
internal class ScreenFight(val view: FightView, val local: LocalFight) {
    var last = 0L
}

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

// ---------------------------------------------------------------- the sides

/** Where a poacher's chopper takes a netbeast of yours. */
private fun GameActivity.poachLocation() = LocationEngine.nearbySuburb(this).ifEmpty {
    val city = prefs.getString("CURRENT_CITY", "The Outskirts") ?: "The Outskirts"
    "the outskirts of $city"
}

/** Your side: [cages] in the order they're offered, [active] out first (-1: nobody, a last stand). */
internal fun GameActivity.mySide(cages: List<Netbeast>, active: Int) = SideSetup(
    SideKind.PLAYER, cages, active = active, ownerId = playerId,
    bagNets = nets, difficulty = exploreDifficulty,
    sword = Relics.swordOn(prefs), amulet = prefs.getBoolean("PLAYER_HAS_AMULET", false),
    tripleStrike = playerHasTripleStrike, evasion = playerHasEvasion,
    location = poachLocation(),
)

/** The side a starter set up: a poacher's netbeasts, the invader, the event boss, or [currentEnemy] gone wild. */
internal fun GameActivity.foeSide(): SideSetup? {
    val enemy = currentEnemy ?: return null
    return when {
        isTrainerBattle -> SideSetup(SideKind.POACHER, enemyParty.toList(), captive = capturedRescueTarget)
        isUnderAttack -> SideSetup(SideKind.INVADER, listOf(enemy))
        enemy.type == "EventBoss" -> SideSetup(SideKind.BOSS, listOf(enemy), weakness = prefs.getString("EVENT_WEAKNESS", "") ?: "")
        else -> SideSetup(SideKind.WILD, listOf(enemy))
    }
}

/** What a tap does, wherever the fight is. */
internal fun GameActivity.fightInput(i: FightInput) {
    val sf = screenFight
    if (sf != null) sf.local.input(i) else if (worldFight != null) worldSession?.fight(i)
}

/** The fight on show, if any. */
internal fun GameActivity.fightView(): FightView? = screenFight?.view ?: worldFight?.view

/** The netbeast fighting for you, or null. */
internal fun GameActivity.myFighter(): Netbeast? = fightView()?.me?.pet()

// ---------------------------------------------------------------- the battle screen

/**
 * Starts the fight a starter set up ([currentEnemy] and friends, [activePetIndex] out, or a last
 * stand if [playerLastStand]) on the battle screen, which it has already switched to.
 */
internal fun GameActivity.beginScreenFight() {
    val foe = foeSide() ?: return
    screenFight?.let { stopScreenFight() }
    val active = if (playerLastStand || party.isEmpty()) -1 else activePetIndex.coerceIn(0, party.size - 1)
    val setup = FightSetup(FightStyle.SCREEN, currentWeather, System.currentTimeMillis(), currentCity)
    val fight = Fight.create(1, setup, listOf(mySide(party.toList(), active), foe), Random.nextLong())
    val v = FightView(fight, 0, wilds = false)
    v.rescueName = capturedRescueTarget?.name
    val sf = ScreenFight(v, LocalFight(fight))
    screenFight = sf
    battleOver = false
    val enemy = currentEnemy!!
    v.shown[0] = if (active < 0) "YOU" else party[active].name
    v.shown[1] = enemy.name
    val opening = sf.local.start()
    playerLastStand = v.me.lastStand
    showBattleArena(v.shown[0]!!, enemy.name)
    applyFight(v, opening)
    updateBattleUI()
    sf.last = SystemClock.uptimeMillis()
    mainHandler.postDelayed(object : Runnable {
        override fun run() {
            if (screenFight !== sf) return
            val now = SystemClock.uptimeMillis()
            val evs = sf.local.update((now - sf.last) / 1000.0)
            sf.last = now
            if (evs.isNotEmpty()) applyFight(v, evs)
            if (screenFight !== sf) return
            showTimer(v)
            mainHandler.postDelayed(this, 33)
        }
    }, 33)
}

private fun GameActivity.stopScreenFight() {
    screenFight = null
    findViewById<ProgressBar>(R.id.pbBattleTimer)?.visibility = View.GONE
}

/** The pressure bar: the rules shout a Basic Attack for you when it runs out. */
private fun GameActivity.showTimer(v: FightView) {
    val pb = findViewById<ProgressBar>(R.id.pbBattleTimer) ?: return
    val t = v.fight.timerShown(v.me)
    if (t < 0f) { pb.visibility = View.GONE; return }
    pb.visibility = View.VISIBLE
    pb.progress = (t * 100).toInt()
}

// ---------------------------------------------------------------- what the rules say

/** The phone's index of [side]'s netbeast [i] (the rules keep their own list), or -1. */
private fun GameActivity.appIndex(v: FightView, side: Int, i: Int): Int {
    val p = v.fight.sides.getOrNull(side)?.party?.getOrNull(i) ?: return -1
    return party.indexOfFirst { it === p }
}

/** Shows [evs] and writes the save. Visual events are the battle screen's: in the Wilds the World poses the fighters itself. */
internal fun GameActivity.applyFight(v: FightView, evs: List<FightEvent>) {
    val me = v.side
    for (e in evs) {
        when (e) {
            is FightEvent.Log -> if (e.side < 0 || e.side == me) printLog(e.text)
            is FightEvent.Sound -> AudioEngine.playSfx(this, e.key)
            is FightEvent.Buzz -> if (e.side < 0 || e.side == me) vibratePhone(e.ms)
            is FightEvent.Pose -> if (!v.wilds) {
                val name = if (e.person) "YOU" else v.shown[e.side] ?: continue
                playSpriteAnim(e.side == me, name, e.anim, revertToIdle = !e.hold)
            }
            is FightEvent.Move -> if (!v.wilds) {
                val view = findViewById<View>(if (e.side == me) R.id.spritePlayer else R.id.spriteEnemy)
                when (e.motion) {
                    Motion.ATTACK -> AnimUtils.animAttack(view, e.side == me)
                    Motion.SHAKE -> AnimUtils.animShake(view)
                    Motion.EVADE -> AnimUtils.animEvade(view, e.side == me)
                    Motion.DEATH -> AnimUtils.animDeath(view)
                }
            }
            is FightEvent.Fx -> if (!v.wilds) playFx(e.side == me, e.fx)
            is FightEvent.Projectile -> if (!v.wilds) AnimUtils.fireProjectile(findViewById(R.id.spriteVfx), e.from == me, e.fromType, e.toType, e.move)
            is FightEvent.SentOut -> sentOut(v, e)
            is FightEvent.Recalled -> if (v.wilds) refreshWorldFightPanel()
            is FightEvent.Fell -> if (e.side == me) {
                val pet = v.fight.sides[me].party[e.index]
                if (e.captured) RescueEngine.captureBeast(prefs, pet, v.me.location)
                lose(v, pet)
            }
            is FightEvent.Abandoned -> if (e.side == me) lose(v, v.fight.sides[me].party[e.index])
            is FightEvent.LastStand -> if (e.side == me) {
                playerLastStand = true
                if (v.wilds) refreshWorldFightPanel() else updateBattleUI()
            }
            is FightEvent.Loot -> if (e.side == me) loot(e.item, e.amount)
            is FightEvent.Caught -> if (e.side == me && party.none { it === e.beast }) {
                e.beast.isNew = true
                party.add(e.beast)
                saveParty(); updatePartyScreen(); updateDispatchButton()
            }
            is FightEvent.Evolve -> if (e.side == me) appIndex(v, e.side, e.index).takeIf { it >= 0 }?.let { evolveBeast(it) }
            is FightEvent.PartyChanged -> if (e.side == me) {
                saveParty(); updatePartyScreen()
                if (v.wilds) refreshWorldFightPanel() else { updateBattleUI(); updateHealthBars() }
            }
            is FightEvent.Ended -> ended(v, e)
            FightEvent.Closed -> closed(v)
        }
    }
    if (!v.wilds && screenFight?.view === v) updateHealthBars()
}

private fun GameActivity.sentOut(v: FightView, e: FightEvent.SentOut) {
    val s = v.fight.sides[e.side]
    val p = s.party.getOrNull(e.index) ?: return
    v.shown[e.side] = p.name
    if (e.side == v.side) {
        val i = appIndex(v, e.side, e.index)
        if (i >= 0) {
            activePetIndex = i
            if (!v.wilds) prefs.edit().putInt("ACTIVE_PET_INDEX", i).apply()
        }
    } else currentEnemy = p
    if (v.wilds) { refreshWorldFightPanel(); return }
    showBattleArena(v.shown[v.side] ?: "YOU", currentEnemy?.name ?: p.name)
    updateBattleUI()
}

/** Your netbeast [pet] is gone from your party: it fell, was taken, or you left it behind. */
private fun GameActivity.lose(v: FightView, pet: Netbeast) {
    val before = party.toList()
    if (!party.removeAll { it === pet }) return
    remapExpeditions(before)
    if (!v.wilds) { activePetIndex = 0; prefs.edit().putInt("ACTIVE_PET_INDEX", 0).apply() }
    else activePetIndex = activePetIndex.coerceAtMost((party.size - 1).coerceAtLeast(0))
    saveParty(); updatePartyScreen()
    if (v.wilds) refreshWorldFightPanel()
}

private fun GameActivity.loot(item: String, n: Int) {
    when (item) {
        "coins" -> focusCoins += n
        "nets" -> nets += n
        "potions" -> potions += n
        "sprays" -> sprays += n
        "aether" -> aetherSeconds += n
        "amulets" -> prefs.edit()
            .putInt("PLAYER_UNCLAIMED_AMULETS", prefs.getInt("PLAYER_UNCLAIMED_AMULETS", 0) + n)
            .putBoolean("EVENT_ACTIVE", false).apply()
    }
}

/** It's decided: the save is written now (the fight lingers two seconds, until [closed]). */
private fun GameActivity.ended(v: FightView, e: FightEvent.Ended) {
    val r = e.result
    val mine = r.outcomes[v.side]
    if (mine == FightOutcome.WON && (r.foeKind == SideKind.WILD || r.foeKind == SideKind.INVADER)) r.foe?.let { foe ->
        // the beaten foe goes in the graveyard, the ten strongest kept
        graveyard.add(foe.copy(hp = foe.maxHp, isNew = true, boughtAt = System.currentTimeMillis()))
        graveyard.sortByDescending { it.maxHp }
        if (graveyard.size > 10) graveyard = graveyard.take(10).toMutableList()
        SaveManager.saveParty(prefs, "GRAVEYARD_DATA", graveyard)
    }
    val rescued = r.rescued
    if (mine == FightOutcome.WON && rescued != null) {
        RescueEngine.removeCapturedBeast(prefs, v.rescueName ?: rescued.name)
        rescued.isNew = true
        if (party.none { it === rescued }) party.add(rescued)
    }
    // a potion's red or blue glow that wasn't used carries over to the next fight
    playerHasTripleStrike = v.me.tripleStrike
    playerHasEvasion = v.me.evasion
    battleOver = true; isUnderAttack = false; isWildBattle = false; isTrainerBattle = false; playerLastStand = false
    capturedRescueTarget = null
    enemyParty.clear()
    saveItems(); updateBagScreen(); saveParty(); updatePartyScreen(); updateDispatchButton()
    if (v.wilds) refreshWorldFightPanel()
    else {
        findViewById<ProgressBar>(R.id.pbBattleTimer)?.visibility = View.GONE
        hideBattleArena()
    }
}

/** Two seconds after the end: back to the Hub, or in the Wilds the walk goes on. */
private fun GameActivity.closed(v: FightView) {
    if (v.wilds) {
        val f = worldFight ?: return
        if (f.view !== v) return
        f.closed = true
        printLog(if (party.isEmpty()) "> You got clobbered, but you pick yourself up and walk on." else "> The Wilds go quiet. You walk on.")
    } else {
        if (screenFight?.view !== v) return
        stopScreenFight()
        printLog(if (party.isEmpty()) "> You got clobbered and returned to the Hub." else "> Returning to Hub...")
    }
    setUIState("HUB")
}
