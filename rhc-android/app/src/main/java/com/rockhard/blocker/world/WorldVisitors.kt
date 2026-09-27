package com.rockhard.blocker

import com.rockhard.blocker.world.sim.EntityKind
import com.rockhard.blocker.world.sim.EntityState
import com.rockhard.blocker.world.sim.World

// Poachers and the invasion boss in the 3D Wilds. With 3D exploring on they
// don't pop up as prompts that take you to the battle screen. While you walk,
// the next one due is put in your path a little way ahead (World.summon) and
// waits there. Keep walking and you walk into it, and the fight is an ordinary
// in-world fight (WorldFight.kt) on the same battle rules: the boss squares up
// like a wild beast, and the poacher throws his netbeasts' cages one at a time.
// Steer round one and you've left it for another walk. Text-log exploring keeps
// the prompts (QteManager.kt, BossEventQte.kt).

/** A poacher is a person: as tall as you. */
private const val POACHER_SIZE = 0.7
/** Half as tall again as the biggest wild beast, still under the trees. */
private const val BOSS_SIZE = 1.5
/** The Legendary row stands in for the boss, whose name is made up for each invasion and has no art. */
private const val BOSS_SPRITE = "Titan"
/** Another comes only once none is waiting this close (tiles), so they come one at a time. */
private const val VISITOR_GAP = 12.0
/** With less of the walk left than this (seconds), there's no time to reach one. */
private const val VISITOR_MIN_SECONDS = 15

internal enum class VisitorKind { BOSS, POACHER }

/** One that's due or put in this walk. [key] says which, so each comes once a walk; a poacher holds [captive]. */
internal class WorldVisitor(val kind: VisitorKind, val key: String, val captive: Netbeast? = null)

/** Who's due: the boss on its day, then a poacher for each of your netbeasts held near here. */
private fun GameActivity.dueVisitors(): List<WorldVisitor> {
    val due = mutableListOf<WorldVisitor>()
    if (BossEventEngine.isDue(prefs)) due += WorldVisitor(VisitorKind.BOSS, "boss:${bossName()}")
    val city = prefs.getString("CURRENT_CITY", "") ?: ""
    for (b in RescueEngine.getCapturedNearby(prefs, LocationEngine.currentSuburb(this), city)) {
        due += WorldVisitor(VisitorKind.POACHER, "poacher:${b.boughtAt}:${b.name}", b)
    }
    return due
}

private fun GameActivity.bossName() = prefs.getString("EVENT_BOSS_NAME", "Unknown Titan") ?: "Unknown Titan"
private fun GameActivity.bossWeakness() = prefs.getString("EVENT_WEAKNESS", "Cacheon") ?: "Cacheon"

/** From the global tick when 3D exploring is on: out walking, the next one due goes in your path; otherwise, says who's waiting. */
internal fun GameActivity.tickWorldVisitors() {
    if (inWorld) summonWorldVisitor() else announceWorldVisitors()
}

private fun GameActivity.summonWorldVisitor() {
    val s = worldSession ?: return
    if (worldFight != null) return
    val me = s.world.entities[s.localPlayerId] ?: return
    if (me.state != EntityState.WALKING || me.downTicks > 0 || me.exploreTicks < VISITOR_MIN_SECONDS * World.TICK_HZ) return
    val waiting = worldVisitors.keys.any { id ->
        s.world.entities[id]?.let { it.state == EntityState.WAIT && s.world.map.distance(me.x, me.y, it.x, it.y) < VISITOR_GAP } == true
    }
    if (waiting) return
    val v = dueVisitors().firstOrNull { d -> worldVisitors.values.none { it.key == d.key } } ?: return
    val id = when (v.kind) {
        VisitorKind.BOSS -> s.summon(EntityKind.BEAST, BOSS_SPRITE, BOSS_SIZE)
        VisitorKind.POACHER -> s.summon(EntityKind.RIVAL, "Poacher", POACHER_SIZE)
    }
    if (id < 0) return
    worldVisitors[id] = v
    announcedVisitors += v.key
    vibratePhone(60)
    when (v.kind) {
        VisitorKind.BOSS -> printLog("\n🚨 KAIJU SIGHTED! 🚨\n> ${bossName()} blocks the path ahead! Keep walking to defend $currentCity.\n> Its weakness: ${bossWeakness()}.")
        VisitorKind.POACHER -> printLog("\n🚨 DISTRESS BEACON! 🚨\n> A poacher holding ${v.captive?.name} is on the path ahead.\n> Keep walking to take it back!")
    }
}

/** Off the walk, each one due is mentioned once: it's waiting for you in the Wilds. */
private fun GameActivity.announceWorldVisitors() {
    for (v in dueVisitors()) {
        if (!announcedVisitors.add(v.key)) continue
        when (v.kind) {
            VisitorKind.BOSS -> printLog("\n🚨 KAIJU SIGHTED! 🚨\n> ${bossName()} has breached the perimeter! It's waiting for you in the Wilds. (Weakness: ${bossWeakness()})")
            VisitorKind.POACHER -> printLog("\n🚨 DISTRESS BEACON DETECTED! 🚨\n> ${v.captive?.name} is being held by a Poacher nearby! Set off into the Wilds to rescue it.")
        }
    }
}

/** The battle rules for walking into [v]: the boss, or the poacher's netbeasts (his first one comes out of a cage). */
internal fun GameActivity.setUpVisitorBattle(v: WorldVisitor) {
    when (v.kind) {
        VisitorKind.BOSS -> { currentEnemy = makeEventBoss(); isWildBattle = true; isTrainerBattle = false; battleOver = false }
        VisitorKind.POACHER -> setUpPoacherBattle(v.captive!!)
    }
}
