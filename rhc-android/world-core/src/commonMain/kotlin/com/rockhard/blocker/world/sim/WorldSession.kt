package com.rockhard.blocker.world.sim

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
    /** Throw this netbeast's cage into the current fight (recalling the one that's out). */
    fun throwCage(species: String)
    /** Put the netbeast that's out back in its cage. */
    fun recall()
    /** While walking: toss this netbeast's cage down. It roams for the rest of the walk, after coins if [forage]. */
    fun letOut(species: String, forage: Boolean)
    /** Advance by real elapsed time; returns events that happened this frame. */
    fun update(elapsedSeconds: Double): List<WorldEvent>
    fun resolveEncounter(beastId: Int, outcome: EncounterOutcome)
    fun setCompanion(species: String?)
    fun setCloak(beasts: Int)
    /** The battle rules resolved something: show it on whoever has [role] in your fight. */
    fun perform(role: Role, action: Action)
    fun effect(role: Role, fx: String)
    /** Puts a visitor (a poacher, a boss) in your path a little way ahead (see World.summon); returns its id. */
    fun summon(kind: EntityKind, species: String, size: Double): Int
    /** The rival you're fighting sends out his netbeast [species] (see World.rivalSendOut). */
    fun rivalSendOut(species: String)
}

/** Single-player: the world runs right here with a fixed-timestep accumulator. */
class LocalWorldSession(override val world: World, ownerId: String, companion: String?, exploreSeconds: Int) : WorldSession {
    override val localPlayerId = world.addPlayer(ownerId, companion, exploreSeconds).id
    private var pendingLook = 0.0
    private var pendingCage: String? = null
    private var pendingRecall = false
    private val pendingOut = ArrayDeque<Pair<String, Boolean>>() // cages to toss down, one a tick
    private var accumulator = 0.0

    /** Swipes add up until the next tick applies them. */
    override fun steer(lookDelta: Double) { pendingLook += lookDelta }

    override fun throwCage(species: String) { pendingCage = species; pendingRecall = false }

    override fun recall() { pendingRecall = true; pendingCage = null }

    override fun letOut(species: String, forage: Boolean) { pendingOut.addLast(species to forage) }

    override fun update(elapsedSeconds: Double): List<WorldEvent> {
        accumulator += elapsedSeconds.coerceAtMost(0.25) // don't spiral after a pause
        val events = mutableListOf<WorldEvent>()
        while (accumulator >= World.DT) {
            accumulator -= World.DT
            val out = pendingOut.removeFirstOrNull()
            events += world.step(mapOf(localPlayerId to PlayerInput(pendingLook, pendingCage, pendingRecall, out?.first, out?.second == true)))
            pendingLook = 0.0; pendingCage = null; pendingRecall = false
        }
        return events
    }

    override fun resolveEncounter(beastId: Int, outcome: EncounterOutcome) =
        world.resolveEncounter(localPlayerId, beastId, outcome)

    override fun setCompanion(species: String?) = world.setCompanion(localPlayerId, species)

    override fun setCloak(beasts: Int) = world.setCloak(localPlayerId, beasts)

    override fun perform(role: Role, action: Action) = world.perform(localPlayerId, role, action)

    override fun effect(role: Role, fx: String) = world.effect(localPlayerId, role, fx)

    override fun summon(kind: EntityKind, species: String, size: Double) = world.summon(localPlayerId, kind, species, size)

    override fun rivalSendOut(species: String) = world.rivalSendOut(localPlayerId, species)
}
