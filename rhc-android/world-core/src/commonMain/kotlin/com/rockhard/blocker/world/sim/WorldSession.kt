package com.rockhard.blocker.world.sim

import com.rockhard.blocker.world.battle.FightInput
import com.rockhard.blocker.world.battle.FightSetup
import com.rockhard.blocker.world.battle.SideSetup

/**
 * The seam between the UI and wherever the authoritative World runs.
 * The view only ever talks to a session, so multiplayer means adding a
 * NetWorldSession (send inputs up, apply snapshots down) without touching
 * the renderer or the game screens.
 */
interface WorldSession {
    val world: World
    val localPlayerId: Int
    fun steer(lookDelta: Double)
    /** While walking: toss this netbeast's cage down. It roams for the rest of the walk, after coins if [forage]. */
    fun letOut(species: String, forage: Boolean)
    /** Advance by real elapsed time; returns events that happened this frame. */
    fun update(elapsedSeconds: Double): List<WorldEvent>
    /** Ends your encounter without the rules (see World.resolveEncounter). */
    fun resolveEncounter(beastId: Int, outcome: EncounterOutcome)
    fun setCompanion(species: String?)
    fun setCloak(beasts: Int)
    /** Puts a visitor (a poacher, a boss) in your path a little way ahead (see World.summon); returns its id. */
    fun summon(kind: EntityKind, species: String, size: Double): Int
    /** Your fight with [foeId] begins (see World.startFight); returns its id, or -1. */
    fun startFight(foeId: Int, setup: FightSetup, mine: SideSetup, foe: SideSetup): Int
    /** What you do in your fight: applied on a coming tick, one a tick, in the order given. */
    fun fight(input: FightInput)
}

/** Single-player: the world runs right here with a fixed-timestep accumulator. */
class LocalWorldSession(override val world: World, ownerId: String, companion: String?, exploreSeconds: Int) : WorldSession {
    override val localPlayerId = world.addPlayer(ownerId, companion, exploreSeconds).id
    private var pendingLook = 0.0
    private val pendingOut = ArrayDeque<Pair<String, Boolean>>() // cages to toss down, one a tick
    private val pendingFight = ArrayDeque<FightInput>()
    private var accumulator = 0.0

    /** Swipes add up until the next tick applies them. */
    override fun steer(lookDelta: Double) { pendingLook += lookDelta }

    override fun letOut(species: String, forage: Boolean) { pendingOut.addLast(species to forage) }

    override fun fight(input: FightInput) { pendingFight.addLast(input) }

    override fun update(elapsedSeconds: Double): List<WorldEvent> {
        accumulator += elapsedSeconds.coerceAtMost(0.25) // don't spiral after a pause
        val events = mutableListOf<WorldEvent>()
        while (accumulator >= World.DT) {
            accumulator -= World.DT
            val out = pendingOut.removeFirstOrNull()
            events += world.step(mapOf(localPlayerId to PlayerInput(pendingLook, pendingFight.removeFirstOrNull(), out?.first, out?.second == true)))
            pendingLook = 0.0
        }
        return events
    }

    override fun resolveEncounter(beastId: Int, outcome: EncounterOutcome) {
        pendingFight.clear()
        world.resolveEncounter(localPlayerId, beastId, outcome)
    }

    override fun setCompanion(species: String?) = world.setCompanion(localPlayerId, species)

    override fun setCloak(beasts: Int) = world.setCloak(localPlayerId, beasts)

    override fun summon(kind: EntityKind, species: String, size: Double) = world.summon(localPlayerId, kind, species, size)

    override fun startFight(foeId: Int, setup: FightSetup, mine: SideSetup, foe: SideSetup): Int {
        pendingFight.clear()
        return world.startFight(localPlayerId, foeId, setup, mine, foe)
    }
}
