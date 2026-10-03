package com.rockhard.blocker.world.sim

import com.rockhard.blocker.world.sim.DetMath.atan2
import com.rockhard.blocker.world.sim.DetMath.cos
import com.rockhard.blocker.world.sim.DetMath.hypot
import com.rockhard.blocker.world.sim.DetMath.sin
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.random.Random

// Pure Kotlin (no Android imports) so a future server can run the same sim.
// Trig comes from DetMath, which is bit-identical on every platform.

enum class EntityKind { PLAYER, BEAST, CAGE, COMPANION, RIVAL }

enum class EntityState { WALKING, PATROL, PAUSE, SHY, RETURN, ENGAGED, THROWN, LANDED, GONE, DONE, WAIT, LEAVE, ROAM }

/**
 * A one-off pose the host asks for when the battle rules resolve something
 * (a move lands, a hit, fainting). Rendered with the spr_<name>_<anim> GIFs;
 * FAINT holds until the encounter is resolved.
 */
enum class Action(val anim: String, val ticks: Int) {
    NONE("", 0), ATTACK("attack", 14), HIT("hit", 12), FAINT("faint", 1), VICTORY("victory", 45)
}

/** Someone in a player's fight: the player, the wild beast, or the player's netbeast. */
enum class Role { PLAYER, BEAST, COMPANION }

/**
 * Anything in the world. Players are keyed by [ownerId] (a device/account id
 * once multiplayer exists); beasts belong to a [zoneId]; a CAGE is a thrown
 * cage in flight or on the ground; a COMPANION is the player's netbeast that
 * came out of it. A RIVAL is a person with netbeasts of their own (a poacher);
 * a rival's netbeasts, and a boss, are beasts with no zone (-1). Those are
 * visitors: see [World.summon]. A cage tossed down on a walk, and the netbeast
 * that comes out of it to ROAM, are in nobody's fight: they have no [link] and
 * are found by [ownerId].
 */
class Entity(
    val id: Int,
    val kind: EntityKind,
    var x: Double,
    var y: Double,
    var angle: Double,
    var species: String,
    var size: Double,
    val ownerId: String? = null,
    val zoneId: Int = -1,
) {
    var z = 0.0                 // height above the surface (cage arcs)
    var moving = false
    var state = EntityState.PATROL
    var targetX = x
    var targetY = y
    var timer = 0               // ticks left in the current state
    var link = -1               // chased player / engaged beast / owning player
    var foe = -1                // who it squares up to in a fight
    var companion: String? = null   // player: the netbeast that's out (or the lead, before a fight); a cage tossed down on a walk: who's in it
    var forage = false          // a roaming netbeast (and its cage before it): it goes after coins
    var heelF = 0.0             // a roaming netbeast: the spot it's heading for, this far ahead of its owner
    var heelS = 0.0             // ...and this far to their right
    var exploreTicks = 0        // player: auto-walk time left
    var grace = 0               // player: ticks before beasts can engage again
    var cloak = 0               // player: beasts that will still let you walk past (Cloak of Christ)
    var downTicks = 0           // player: knocked flat after losing a fight, picking yourself up
    var recoverTicks = 0        // player: after a fight you walk on slowly, back to full pace when this runs out

    // While ENGAGED everyone circles a centre, facing their foe
    var orbitX = 0.0
    var orbitY = 0.0
    var orbitA = 0.0
    var orbitR = 0.0
    var orbitDir = 1            // the way you're meant to be circling
    var orbitSpin = 0.0         // how you're actually circling (-1..1): eases toward orbitDir, so a reversal slows first
    var swapTicks = 0           // player: ticks until the fight circles the other way

    var action = Action.NONE
    var actionTicks = 0
    var fx: String? = null      // an effect playing over this entity (fx_<name>.gif)
    var fxAge = 0               // ticks since it started

    // A hit (and its effect) held back until the attacker's lunge reaches you
    var pendingAction = Action.NONE
    var pendingFx: String? = null
    var pendingTicks = 0
}

/**
 * One tick of intent from a player; this is what a client would send to a
 * host. While walking the only input is steering (a one-off turn in
 * radians). In a fight, steering picks which way you circle, and
 * [throwCage] names the netbeast whose cage you throw (a new throw
 * recalls the one that's out). [recall] puts it back with nothing
 * thrown in its place. While walking, [letOut] names a netbeast whose cage
 * you toss down: it comes out and roams for the rest of the walk, going
 * after coins if [forage] is set.
 */
data class PlayerInput(
    val lookDelta: Double = 0.0, val throwCage: String? = null, val recall: Boolean = false,
    val letOut: String? = null, val forage: Boolean = false,
)

sealed class WorldEvent {
    /** A beast squared up to a player. You circle each other until a cage is thrown. */
    data class Encounter(val playerId: Int, val beastId: Int, val species: String, val stage: Int) : WorldEvent()
    /** A thrown netbeast is out of its cage and facing the wild one. */
    data class CompanionOut(val playerId: Int, val beastId: Int, val species: String) : WorldEvent()
    /** The rival's netbeast [beastId] is out of its cage: it's the one the player fights now. */
    data class RivalOut(val playerId: Int, val beastId: Int, val species: String) : WorldEvent()
    /**
     * Coin [coin] (its id: MapTile.coinId, WorldMap.coin) was picked up for the player: it's worth one
     * coin. [finderId] is the player, who walked into it, or their roaming netbeast that found it.
     */
    data class CoinPicked(val playerId: Int, val coin: Int, val finderId: Int = playerId) : WorldEvent()
    /** A beast would have squared up to a cloaked player, but never noticed them. */
    data class SlippedPast(val playerId: Int, val beastId: Int, val species: String) : WorldEvent()
    /** The player's exploration timer ran out. */
    data class ExplorationOver(val playerId: Int) : WorldEvent()
}

/** How a fight ended. PLAYER_BEATEN: it clobbered you (a last stand lost), so you're knocked flat first. */
enum class EncounterOutcome { BEAST_DEFEATED, PLAYER_FLED, PLAYER_BEATEN }

data class EntitySnapshot(
    val id: Int, val kind: EntityKind, val x: Double, val y: Double, val z: Double, val angle: Double,
    val species: String, val size: Double, val ownerId: String?, val zoneId: Int,
    val moving: Boolean, val state: EntityState, val link: Int,
    val foe: Int = -1, val action: Action = Action.NONE, val actionTicks: Int = 0, val fx: String? = null, val fxAge: Int = 0,
    val orbitX: Double = 0.0, val orbitY: Double = 0.0, val orbitR: Double = 0.0,
    val downTicks: Int = 0, val recoverTicks: Int = 0,
)

/** A picked-up coin (its id: MapTile.coinId) and the ticks until it's back. */
data class CoinGone(val coin: Int, val ticks: Int)

/**
 * Everything a remote client needs besides the seed and region: small enough to send often.
 * [awake] is the tiles whose beasts are out (WorldMap.key), in the order they woke.
 */
data class WorldSnapshot(
    val tick: Long, val entities: List<EntitySnapshot>, val coinsGone: List<CoinGone> = emptyList(), val awake: List<Long> = emptyList(),
)

/**
 * The authoritative simulation. Advances in fixed ticks ([TICK_HZ]) from
 * inputs only, with its own seeded RNG consumed in entity-id order, so the
 * same seed + inputs give the same world on every machine.
 */
class World(val map: WorldMap) {
    companion object {
        const val TICK_HZ = 30
        const val DT = 1.0 / TICK_HZ
        const val WALK_SPEED = 2.4        // tiles/s, auto-walk on grass
        const val PATROL_SPEED = 0.9
        // Beasts never ambush you. One that notices you walks off out of your path, slower than
        // you walk, so a fight is always your choice: head for it, keep steering at it, catch it.
        const val NOTICE_RANGE = 8.0
        const val SHY_SPEED = 1.0         // tiles/s: well under WALK_SPEED
        const val ENGAGE_RANGE = 2.4      // catch up to this close and you square up and start circling
        const val ENGAGE_CONE = 0.64      // ...with it in front of you (cos 50 degrees): never at your side or back
        const val RESPAWN_TICKS = 45 * TICK_HZ
        const val GRACE_TICKS = 3 * TICK_HZ
        const val CAGE_FLIGHT_TICKS = 15   // 0.5s arc
        const val CAGE_OPEN_TICKS = 30     // 1s on the ground before the netbeast emerges
        const val NETBEAST_SIZE = 0.65     // a netbeast out of its cage, yours or a rival's

        // Visitors (a poacher, a boss) wait this far ahead of you: a few seconds' walk, far
        // enough to see them coming out of the haze
        const val SUMMON_AHEAD = 12.0
        const val RIVAL_LAND = 0.35       // a rival's first cage lands this share of the way to you
        const val LEAVE_SPEED = 1.2       // tiles/s: a visitor walking off, under your pace
        const val LEAVE_TICKS = 8 * TICK_HZ

        // The fight's circles: you and the beast before a cage, then the two beasts, with you further out
        const val STANDOFF_R = 1.2
        const val WATCH_R = 2.3
        const val DUEL_R = 0.6
        const val STANDOFF_SPEED = 0.8    // tiles/s along the circle
        const val WATCH_SPEED = 0.7
        const val DUEL_SPEED = 0.4
        const val FADE_TICKS = 8 * TICK_HZ // a fainted beast lies there this long after the fight
        const val BODY_TICKS = 8 * TICK_HZ // ...and so does a fallen netbeast of yours, counted from the fight's end
        // Nobody circles one way for long (it's dizzying): 3s plus up to 3s, then everyone
        // slows to a stop and circles back the other way
        const val SWAP_MIN_TICKS = 3 * TICK_HZ
        const val SWAP_EXTRA_TICKS = 3 * TICK_HZ
        const val SPIN_RATE = 1.0 / 18    // orbitSpin change per tick: 0.6s from full speed to a stop

        // After a fight: knocked flat (a lost last stand) you lie there, then get up; either way
        // you walk on at RECOVER_PACE and ease back to full pace
        const val LIE_TICKS = 15          // 0.5s flat on the ground
        const val DOWN_TICKS = 45         // ...then 1s getting up
        const val RECOVER_TICKS = 8 * TICK_HZ
        const val RECOVER_PACE = 0.6

        const val COIN_REACH = 0.5          // walk within this (tiles) of a coin and it's yours
        const val COIN_RESPAWN_TICKS = 300 * TICK_HZ  // longer than a walk: a coin is only yours once per walk

        // A tile's beasts come out when a player gets this close to it: further than anyone can
        // see (the renderer draws 30 tiles), and under half a tile, so at the start only the
        // tile you're in is awake
        const val WAKE_RANGE = 40.0
        // ...and go back in once every player is this far from it, so a long walk doesn't
        // leave a trail of beasts patrolling tiles nobody is near. Come back and it wakes afresh
        const val SLEEP_RANGE = 72.0

        // A netbeast let out on a walk: its cage lands ahead and off to one side, far enough that
        // you're still coming up to it when it opens. It trots to a spot near you, stops for a
        // look around while you walk past, and trots on to the next
        const val LET_OUT_AHEAD = 6.5
        const val LET_OUT_SIDE = 1.0
        const val ROAM_SPEED = 2.9        // tiles/s: a little over your best pace, so it gets ahead of you
        const val ROAM_RUN = 3.6          // ...and catching up from further back than ROAM_NEAR, or after a coin
        const val ROAM_NEAR = 2.5
        const val ROAM_AHEAD_MIN = 1.5    // the spots it picks: this far ahead of you
        const val ROAM_AHEAD_MAX = 5.0
        const val ROAM_SIDE = 3.0         // ...and up to this far to either side
        const val ROAM_LOOK_MIN = 20      // ticks it stands looking around
        const val ROAM_LOOK_MAX = 60
        const val ROAM_MILL = 2.0         // while you're not walking it potters about within this of where it is
        const val ROAM_CLEAR = 4.0        // ...and this far from the middle of your fight
        const val FORAGE_RANGE = 3.0      // a forager goes for a coin this close to it
        const val FORAGE_LEASH = 6.0     // ...unless that coin is further than this from you

        // The sidestep: the auto-walk leans around trunks instead of walking into them
        const val PROBE = 1.2             // a trunk this far ahead (tiles) is in the way
        const val CLEARANCE = 0.35        // ...if you'd pass it closer than this beyond its trunk radius
        const val STRAFE_MAX = 0.9        // tiles/s sideways at most
        const val MIN_FORWARD = 0.7       // never slower than this share of the walking pace
        const val GAP_GAIN = 4.0          // how briskly you centre in a gap between two trunks (1/s)

        fun speedFactor(terrain: Int) = when (terrain) {
            Terrain.WATER -> 0.55; Terrain.MUD -> 0.8; Terrain.TALL_GRASS -> 0.85; Terrain.SAND -> 0.9; Terrain.FOREST -> 0.9
            Terrain.PATH -> 1.1; else -> 1.0
        }
    }

    var tick = 0L
        private set
    private val rng = Random(map.seed xor 0x5EED)
    private var nextId = 1
    val entities = LinkedHashMap<Int, Entity>()

    /** The coins that have been picked up, by id: ticks until each is back. One that isn't here is lying there. */
    val coinGone = LinkedHashMap<Int, Int>()

    /** The tiles whose beasts are out (WorldMap.key), in the order they woke. */
    private val awake = LinkedHashSet<Long>()

    init {
        wake(map.spawnX, map.spawnY)
    }

    /**
     * Brings out the beasts of every tile within [WAKE_RANGE] of (x, y) that
     * hasn't had them yet. It goes by where the players are and nothing else
     * (not by which tiles happen to have been made or drawn), so every
     * machine wakes the same tiles on the same tick, in the same order.
     */
    private fun wake(x: Double, y: Double) {
        map.tilesNear(x, y, WAKE_RANGE) { t ->
            if (awake.add(WorldMap.key(t.tx, t.ty))) for (z in t.zones) repeat(if (z.stage >= 3) 1 else 2) { spawnBeast(z) }
        }
    }

    /** Puts away the beasts of every woken tile that all [players] are [SLEEP_RANGE] or more from (not one in a fight). */
    private fun sleep(players: List<Entity>) {
        val keys = awake.iterator()
        while (keys.hasNext()) {
            val key = keys.next()
            val x0 = (key shr 32).toInt() * map.size; val y0 = key.toInt() * map.size
            if (players.any { it.x > x0 - SLEEP_RANGE && it.x < x0 + map.size + SLEEP_RANGE && it.y > y0 - SLEEP_RANGE && it.y < y0 + map.size + SLEEP_RANGE }) continue
            entities.values.removeAll { it.kind == EntityKind.BEAST && it.zoneId >= 0 && it.state != EntityState.ENGAGED && zoneOf(it).let { z -> z.x >= x0 && z.x < x0 + map.size && z.y >= y0 && z.y < y0 + map.size } }
            keys.remove()
        }
    }

    /** [companion] is the species in the player's lead cage, or null to explore alone. */
    fun addPlayer(ownerId: String, companion: String?, exploreSeconds: Int): Entity {
        val e = Entity(nextId++, EntityKind.PLAYER, map.spawnX, map.spawnY, rng.nextDouble(0.0, PI * 2), "player", 0.7, ownerId = ownerId)
        e.state = EntityState.WALKING
        e.companion = companion
        e.exploreTicks = exploreSeconds * TICK_HZ
        e.grace = GRACE_TICKS
        entities[e.id] = e
        return e
    }

    /** How many beasts will let this player walk past unseen (the Cloak of Christ). */
    fun setCloak(playerId: Int, beasts: Int) { entities[playerId]?.cloak = beasts }

    fun setCompanion(playerId: Int, companion: String?) { entities[playerId]?.companion = companion }

    fun removePlayer(id: Int) { entities.remove(id) }

    private fun spawnBeast(z: Zone): Entity {
        val a = rng.nextDouble(0.0, PI * 2); val r = rng.nextDouble(0.0, z.radius)
        val size = when (z.stage) { 1 -> 0.55; 2 -> 0.75; else -> 1.0 }
        val e = Entity(nextId++, EntityKind.BEAST, z.x + cos(a) * r, z.y + sin(a) * r, rng.nextDouble(0.0, PI * 2), z.species, size, zoneId = z.id)
        pickWaypoint(e, z)
        entities[e.id] = e
        return e
    }

    private fun zoneOf(e: Entity) = map.zone(e.zoneId)

    private fun pickWaypoint(e: Entity, z: Zone) {
        val a = rng.nextDouble(0.0, PI * 2); val r = rng.nextDouble(0.0, z.radius)
        e.targetX = z.x + cos(a) * r; e.targetY = z.y + sin(a) * r
        e.state = EntityState.PATROL
    }

    /**
     * Puts a visitor in [playerId]'s path, [ahead] tiles straight in front of
     * them: a RIVAL (a poacher, with netbeasts of his own) or a BEAST from no
     * territory (a boss). It stands there watching the player and never moves
     * toward them; catch it in front of you, as with any beast, and you square
     * up. Keep walking and you can't miss it; steer round it and you've left it.
     * Uses no RNG. Returns its id.
     */
    fun summon(playerId: Int, kind: EntityKind, species: String, size: Double, ahead: Double = SUMMON_AHEAD): Int {
        require(kind == EntityKind.RIVAL || kind == EntityKind.BEAST)
        val p = entities[playerId] ?: return -1
        val v = Entity(nextId++, kind, p.x + cos(p.angle) * ahead, p.y + sin(p.angle) * ahead, p.angle + PI, species, size)
        v.state = EntityState.WAIT; v.link = p.id
        entities[v.id] = v
        return v.id
    }

    /** Advance one tick. [inputs] maps player entity id -> that player's input. */
    fun step(inputs: Map<Int, PlayerInput>): List<WorldEvent> {
        tick++
        val events = mutableListOf<WorldEvent>()
        val gone = coinGone.entries.iterator()
        while (gone.hasNext()) { val g = gone.next(); if (g.value <= 1) gone.remove() else g.setValue(g.value - 1) }
        val players = entities.values.filter { it.kind == EntityKind.PLAYER }
        for (p in players) wake(p.x, p.y)
        if (tick % TICK_HZ == 0L && players.isNotEmpty()) sleep(players)

        for (p in players) stepPlayer(p, inputs[p.id] ?: PlayerInput(), events)
        for (e in entities.values.toList()) {
            when (e.kind) {
                EntityKind.BEAST -> if (e.zoneId < 0) stepVisitor(e, events) else stepBeast(e, players, events)
                EntityKind.RIVAL -> stepVisitor(e, events)
                EntityKind.CAGE -> stepCage(e, events)
                EntityKind.COMPANION -> when (e.state) {
                    EntityState.GONE -> { stepBody(e); continue }
                    EntityState.ROAM -> stepRoamer(e, events)
                    else -> {}
                }
                else -> {}
            }
            if (e.actionTicks > 0 && e.action != Action.FAINT && --e.actionTicks == 0) e.action = Action.NONE
            if (e.fx != null && ++e.fxAge > 3 * TICK_HZ) e.fx = null
            if (e.pendingTicks > 0 && --e.pendingTicks == 0) {
                if (e.pendingAction != Action.NONE && e.action != Action.FAINT) { e.action = e.pendingAction; e.actionTicks = e.pendingAction.ticks }
                e.pendingFx?.let { e.fx = it; e.fxAge = 0 }
                e.pendingAction = Action.NONE; e.pendingFx = null
            }
        }
        return events
    }

    private fun stepPlayer(p: Entity, input: PlayerInput, events: MutableList<WorldEvent>) {
        when (p.state) {
            EntityState.WALKING -> {
                p.angle += input.lookDelta
                if (input.letOut != null) letOut(p, input.letOut, input.forage)
                if (p.downTicks > 0) {
                    // knocked flat: you pick yourself up before walking on (you can look round meanwhile)
                    p.downTicks--; p.moving = false
                } else {
                    val hx = cos(p.angle); val hy = sin(p.angle)
                    val rx = -hy; val ry = hx // your right (y points down the map)
                    val strafe = sidestep(p, hx, hy, rx, ry)
                    val forward = WALK_SPEED * speedFactor(map.terrainAt(p.x, p.y)) * (1 - (1 - MIN_FORWARD) * dodgeCloseness) * recoveryPace(p)
                    p.x += (hx * forward + rx * strafe) * DT
                    p.y += (hy * forward + ry * strafe) * DT
                    p.moving = true
                    if (p.recoverTicks > 0) p.recoverTicks--
                    pickUpCoins(p, events)
                }
                if (p.grace > 0) p.grace--
                if (--p.exploreTicks <= 0) {
                    p.state = EntityState.DONE; p.moving = false
                    events += WorldEvent.ExplorationOver(p.id)
                }
            }
            EntityState.ENGAGED -> {
                val b = entities[p.link]
                if (b == null) { p.state = EntityState.WALKING; p.link = -1; return }
                if (input.throwCage != null) throwCage(p, b, input.throwCage)
                else if (input.recall) recall(p, b)
                // swiping picks which way you circle (drag right and you step to your right) and holds it
                // for a while; otherwise the fight turns round every few seconds
                if (abs(input.lookDelta) > 1e-4) {
                    p.orbitDir = if (input.lookDelta > 0) -1 else 1
                    p.swapTicks = maxOf(p.swapTicks, SWAP_MIN_TICKS)
                } else if (--p.swapTicks <= 0) {
                    p.orbitDir = -p.orbitDir
                    p.swapTicks = nextSwap()
                }
                val inFight = entities.values.any { (it.kind == EntityKind.COMPANION || it.kind == EntityKind.CAGE) && it.link == p.id }
                if (inFight) {
                    // the fight moved to between the cage and the beast: drift your circle there
                    p.orbitX += (p.targetX - p.orbitX) * 0.06
                    p.orbitY += (p.targetY - p.orbitY) * 0.06
                }
                orbit(p, if (inFight) WATCH_R else STANDOFF_R, if (inFight) WATCH_SPEED else STANDOFF_SPEED, true)
                face(p, p.orbitX, p.orbitY)
            }
            else -> p.moving = false
        }
    }

    /** Any coin within [COIN_REACH] is picked up: you only have to walk into it. */
    private fun pickUpCoins(p: Entity, events: MutableList<WorldEvent>) {
        map.tilesNear(p.x, p.y, COIN_REACH) { t ->
            val coins = t.coins
            for (i in coins.indices) {
                val c = coins[i]
                val dx = c.x - p.x; if (dx > COIN_REACH || dx < -COIN_REACH) continue
                val dy = c.y - p.y; if (dy > COIN_REACH || dy < -COIN_REACH) continue
                if (dx * dx + dy * dy > COIN_REACH * COIN_REACH || t.coinId(i) in coinGone) continue
                coinGone[t.coinId(i)] = COIN_RESPAWN_TICKS
                events += WorldEvent.CoinPicked(p.id, t.coinId(i))
            }
        }
    }

    /**
     * On a walk: tosses [species]' cage down ahead of you and off to one side. The netbeast
     * that comes out is in nobody's fight: it roams near you for the rest of the walk
     * ([stepRoamer]). Uses no RNG; the cage's id picks the side.
     */
    private fun letOut(p: Entity, species: String, forage: Boolean) {
        val hx = cos(p.angle); val hy = sin(p.angle)
        val side = if (nextId % 2 == 0) LET_OUT_SIDE else -LET_OUT_SIDE
        val cage = Entity(nextId++, EntityKind.CAGE, p.x, p.y, p.angle, "cage", 0.4, ownerId = p.ownerId)
        cage.state = EntityState.THROWN; cage.timer = CAGE_FLIGHT_TICKS; cage.z = 0.6
        cage.targetX = p.x + hx * LET_OUT_AHEAD - hy * side; cage.targetY = p.y + hy * LET_OUT_AHEAD + hx * side
        cage.companion = species; cage.forage = forage
        entities[cage.id] = cage
    }

    private fun ownerOf(e: Entity) = entities.values.firstOrNull { it.kind == EntityKind.PLAYER && it.ownerId == e.ownerId }

    /**
     * A netbeast let out on a walk. It never fights and nothing notices it. While you walk it
     * trots to a spot ahead of you and to one side, stands there looking around while you go
     * past, then trots on to another. While you're not walking (a fight, or flat on your back)
     * it potters about where it is, clear of the fight. A forager goes for the nearest coin it
     * can reach instead, and that coin is yours.
     */
    private fun stepRoamer(c: Entity, events: MutableList<WorldEvent>) {
        val p = ownerOf(c) ?: run { entities.remove(c.id); return }
        if (p.state == EntityState.DONE) { c.moving = false; return }
        if (c.forage) {
            val i = coinFor(c, p)
            if (i >= 0) {
                val k = map.coin(i)
                if (map.distance(c.x, c.y, k.x, k.y) < COIN_REACH) {
                    coinGone[i] = COIN_RESPAWN_TICKS
                    events += WorldEvent.CoinPicked(p.id, i, c.id)
                } else moveToward(c, k.x, k.y, ROAM_RUN)
                c.timer = 0; c.targetX = c.x; c.targetY = c.y
                return
            }
        }
        val walking = p.state == EntityState.WALKING && p.downTicks <= 0
        if (c.timer > 0) {
            c.moving = false
            if (--c.timer > 0) return
            // a new spot: one that moves along with you, and one that stays put for when you're not walking
            c.heelF = rng.nextDouble(ROAM_AHEAD_MIN, ROAM_AHEAD_MAX); c.heelS = rng.nextDouble(-ROAM_SIDE, ROAM_SIDE)
            var ax = c.x; var ay = c.y
            if (p.state == EntityState.ENGAGED) {
                // out from the middle of the fight, on the side it's already on
                val dx = c.x - p.orbitX; val dy = c.y - p.orbitY; val d = hypot(dx, dy)
                val ux = if (d > 1e-6) dx / d else cos(p.orbitA); val uy = if (d > 1e-6) dy / d else sin(p.orbitA)
                ax = p.orbitX + ux * ROAM_CLEAR; ay = p.orbitY + uy * ROAM_CLEAR
            }
            c.targetX = ax + rng.nextDouble(-ROAM_MILL, ROAM_MILL); c.targetY = ay + rng.nextDouble(-ROAM_MILL, ROAM_MILL)
        }
        val arrived = if (walking) {
            val hx = cos(p.angle); val hy = sin(p.angle)
            val tx = p.x + hx * c.heelF - hy * c.heelS; val ty = p.y + hy * c.heelF + hx * c.heelS
            moveToward(c, tx, ty, if (map.distance(c.x, c.y, tx, ty) > ROAM_NEAR) ROAM_RUN else ROAM_SPEED)
        } else moveToward(c, c.targetX, c.targetY, PATROL_SPEED)
        if (arrived) c.timer = rng.nextInt(ROAM_LOOK_MIN, ROAM_LOOK_MAX + 1)
    }

    /** The nearest coin a forager will go for (its id), or -1. */
    private fun coinFor(c: Entity, p: Entity): Int {
        var best = -1; var bestD = FORAGE_RANGE
        map.tilesNear(c.x, c.y, FORAGE_RANGE) { t ->
            val coins = t.coins
            for (i in coins.indices) {
                val k = coins[i]
                val d = map.distance(c.x, c.y, k.x, k.y)
                if (d < bestD && map.distance(p.x, p.y, k.x, k.y) < FORAGE_LEASH && t.coinId(i) !in coinGone) { best = t.coinId(i); bestD = d }
            }
        }
        return best
    }

    /** Your pace after a fight: [RECOVER_PACE] at first, easing back up to 1. */
    private fun recoveryPace(p: Entity): Double {
        if (p.recoverTicks <= 0) return 1.0
        val t = 1 - p.recoverTicks.toDouble() / RECOVER_TICKS
        return RECOVER_PACE + (1 - RECOVER_PACE) * t * t * (3 - 2 * t)
    }

    private fun nextSwap() = SWAP_MIN_TICKS + rng.nextInt(SWAP_EXTRA_TICKS + 1)

    /** How close the trunk being dodged is this tick: 0 (none, or 1.2 tiles off) .. 1 (at your feet). */
    private var dodgeCloseness = 0.0

    /**
     * Players only (beasts walk straight through): finds the trunks in the
     * corridor ahead, 0 < forward < [PROBE] and sideways closer than their
     * radius + [CLEARANCE], from the 3x3 cells around the corridor's
     * middle. Returns your sideways speed (tiles/s, + = right) and sets
     * [dodgeCloseness], which slows you down to [MIN_FORWARD] at most. Your
     * heading never changes, so steering and the route aren't disturbed: the
     * path just shifts over and never bounces back. You step away from the
     * trunks; with trunks on both sides you head for the middle of the gap
     * (so toward the side with more room) and carry on. If you still clip a
     * trunk you walk through it: it's a billboard. No allocation, no pairwise
     * checks, deterministic.
     */
    private fun sidestep(p: Entity, hx: Double, hy: Double, rx: Double, ry: Double): Double {
        dodgeCloseness = 0.0
        val mx = floor(p.x + hx * PROBE / 2).toInt(); val my = floor(p.y + hy * PROBE / 2).toInt()
        var nearF = PROBE
        var left = Double.MAX_VALUE; var right = Double.MAX_VALUE // how far out the innermost trunk on each side is
        for (dy in -1..1) for (dx in -1..1) {
            map.treesAt(mx + dx, my + dy) { tree, id ->
                val ox = tree.x - p.x; val oy = tree.y - p.y
                val f = ox * hx + oy * hy
                val lat = ox * rx + oy * ry
                if (f > 0 && f < PROBE && abs(lat) < tree.kind.radius + CLEARANCE) {
                    // dead ahead: the tree's index picks its side, so every machine agrees
                    if (lat > 0 || (lat == 0.0 && id % 2 == 0)) right = minOf(right, abs(lat)) else left = minOf(left, abs(lat))
                    if (f < nearF) nearF = f
                }
            }
        }
        if (nearF >= PROBE) return 0.0
        val c = 1 - nearF / PROBE
        dodgeCloseness = c
        return when {
            right == Double.MAX_VALUE -> STRAFE_MAX * minOf(1.0, c * 1.6)  // trunks only to the left: step right
            left == Double.MAX_VALUE -> -STRAFE_MAX * minOf(1.0, c * 1.6)  // only to the right: step left
            else -> ((right - left) / 2 * GAP_GAIN).coerceIn(-STRAFE_MAX, STRAFE_MAX) // the gap's middle
        }
    }

    /**
     * One tick around [e]'s centre: the radius eases toward [radius]; [advance] = false holds
     * the angle. The speed eases toward [Entity.orbitDir], so turning round means slowing to
     * a stop and picking up the other way.
     */
    private fun orbit(e: Entity, radius: Double, speed: Double, advance: Boolean) {
        e.orbitR += (radius - e.orbitR) * 0.08
        if (advance) {
            val want = e.orbitDir.toDouble()
            e.orbitSpin = if (e.orbitSpin < want) minOf(want, e.orbitSpin + SPIN_RATE) else maxOf(want, e.orbitSpin - SPIN_RATE)
            e.orbitA += e.orbitSpin * speed * DT / e.orbitR.coerceAtLeast(0.5)
        }
        place(e)
        e.moving = advance && abs(e.orbitSpin) > 0.2
    }

    private fun place(e: Entity) {
        e.x = e.orbitX + cos(e.orbitA) * e.orbitR
        e.y = e.orbitY + sin(e.orbitA) * e.orbitR
    }

    private fun face(e: Entity, x: Double, y: Double) { e.angle = atan2(y - e.y, x - e.x) }

    private fun companionOf(p: Entity) = entities.values.firstOrNull { it.kind == EntityKind.COMPANION && it.link == p.id }
    private fun cageOf(p: Entity) = entities.values.firstOrNull { it.kind == EntityKind.CAGE && it.link == p.id }

    private fun stepBeast(b: Entity, players: List<Entity>, events: MutableList<WorldEvent>) {
        val z = zoneOf(b)
        when (b.state) {
            EntityState.GONE -> {
                if (b.actionTicks > 0 && --b.actionTicks == 0) b.action = Action.NONE // the fainted body fades
                if (--b.timer <= 0) { entities.remove(b.id); spawnBeast(z) }
                return
            }
            EntityState.ENGAGED -> { stepEngagedBeast(b); return }
            else -> {}
        }

        // the nearest walking player it can see: catch up to it and you fight, otherwise it shies away
        var near: Entity? = null; var nearD = NOTICE_RANGE
        for (p in players) {
            if (p.state != EntityState.WALKING) continue
            val d = map.distance(b.x, b.y, p.x, p.y)
            if (d < nearD) { near = p; nearD = d }
        }
        if (near != null) {
            val ahead = ((b.x - near.x) * cos(near.angle) + (b.y - near.y) * sin(near.angle)) / nearD.coerceAtLeast(1e-6)
            if (nearD < ENGAGE_RANGE && ahead > ENGAGE_CONE && near.grace <= 0) {
                if (near.cloak <= 0) { engage(near, b, events); return }
                // cloaked: it never notices you, and you walk straight past
                near.cloak--; near.grace = GRACE_TICKS
                events += WorldEvent.SlippedPast(near.id, b.id, b.species)
            }
            b.state = EntityState.SHY; b.link = near.id
        } else if (b.state == EntityState.SHY) { b.state = EntityState.RETURN; b.targetX = z.x; b.targetY = z.y }

        when (b.state) {
            EntityState.PAUSE -> { b.moving = false; if (--b.timer <= 0) pickWaypoint(b, z) }
            EntityState.PATROL, EntityState.RETURN -> {
                if (moveToward(b, b.targetX, b.targetY, PATROL_SPEED)) { b.state = EntityState.PAUSE; b.timer = rng.nextInt(TICK_HZ, 3 * TICK_HZ) }
            }
            EntityState.SHY -> {
                val p = entities[b.link] ?: run { b.state = EntityState.RETURN; return }
                val (dx, dy) = shyAway(b, p)
                moveToward(b, b.x + dx * 2, b.y + dy * 2, SHY_SPEED)
            }
            else -> {}
        }
    }

    /**
     * Which way a shy beast walks (a unit vector): off to the side of the
     * player's path and a little further away when it's ahead of them, so
     * walking in a straight line never runs into one; straight away when
     * it's behind them. Dead ahead, its id picks the side.
     */
    private fun shyAway(b: Entity, p: Entity): Pair<Double, Double> {
        val vx = b.x - p.x; val vy = b.y - p.y
        val d = hypot(vx, vy).coerceAtLeast(1e-6)
        val hx = cos(p.angle); val hy = sin(p.angle)
        if (vx * hx + vy * hy <= 0) return vx / d to vy / d
        val lat = vx * -hy + vy * hx
        val side = if (lat > 0 || (lat == 0.0 && b.id % 2 == 0)) 1.0 else -1.0
        val sx = -hy * side + vx / d * 0.5; val sy = hx * side + vy / d * 0.5
        val n = hypot(sx, sy)
        return sx / n to sy / n
    }

    /**
     * A visitor ([summon]) waits where it was put, turned to watch the player it
     * came for, until they catch it in front of them; the Cloak of Christ doesn't
     * hide you from one, since you came for it. After a fight a fainted one fades
     * and one that's leaving walks off, and then it's gone for good.
     */
    private fun stepVisitor(v: Entity, events: MutableList<WorldEvent>) {
        val p = entities[v.link]
        if (p == null && v.state != EntityState.GONE) { entities.remove(v.id); return }
        when (v.state) {
            EntityState.WAIT -> {
                v.moving = false
                face(v, p!!.x, p.y)
                if (p.state != EntityState.WALKING || p.grace > 0) return
                val d = map.distance(v.x, v.y, p.x, p.y)
                val ahead = ((v.x - p.x) * cos(p.angle) + (v.y - p.y) * sin(p.angle)) / d.coerceAtLeast(1e-6)
                if (d < ENGAGE_RANGE && ahead > ENGAGE_CONE) engage(p, v, events)
            }
            EntityState.ENGAGED -> if (v.kind == EntityKind.RIVAL) stepEngagedRival(v, p!!) else stepEngagedBeast(v)
            EntityState.LEAVE -> {
                val dx = v.x - p!!.x; val dy = v.y - p.y; val d = hypot(dx, dy).coerceAtLeast(1e-6)
                moveToward(v, v.x + dx / d * 2, v.y + dy / d * 2, LEAVE_SPEED)
                if (--v.timer <= 0) entities.remove(v.id)
            }
            EntityState.GONE -> {
                if (v.actionTicks > 0 && --v.actionTicks == 0) v.action = Action.NONE // the fainted body fades
                if (--v.timer <= 0) entities.remove(v.id)
            }
            else -> {}
        }
    }

    /**
     * The rival in a fight: squared up to you on your circle until he throws a
     * cage, watching it while it flies, then hanging back behind his netbeast,
     * across the fight from you (easing there, so he never jumps).
     */
    private fun stepEngagedRival(r: Entity, p: Entity) {
        val cage = entities.values.firstOrNull { it.kind == EntityKind.CAGE && it.link == r.id }
        val beast = entities[p.link]?.takeIf { it.kind == EntityKind.BEAST }
        r.foe = p.id
        when {
            cage != null -> { r.moving = false; face(r, cage.x, cage.y) }
            beast == null -> {
                r.orbitX = p.orbitX; r.orbitY = p.orbitY; r.orbitR = p.orbitR; r.orbitA = p.orbitA + PI
                place(r); face(r, p.x, p.y); r.moving = p.moving
            }
            else -> {
                val tx = p.orbitX - cos(p.orbitA) * WATCH_R; val ty = p.orbitY - sin(p.orbitA) * WATCH_R
                val sx = (tx - r.x) * 0.08; val sy = (ty - r.y) * 0.08
                r.x += sx; r.y += sy
                r.moving = sx * sx + sy * sy > 1e-5
                face(r, beast.x, beast.y)
            }
        }
    }

    /**
     * Before a cage: stay opposite the player on your shared circle. Cage in
     * the air: stand and watch it. Netbeast out: circle each other, the beast
     * leading and the companion mirroring it, pausing while either acts.
     */
    private fun stepEngagedBeast(b: Entity) {
        val p = entities[b.link] ?: run { b.state = EntityState.RETURN; b.moving = false; return }
        val comp = companionOf(p)
        if (b.action == Action.FAINT) { b.moving = false; comp?.let { it.moving = false; face(it, b.x, b.y) }; return }
        if (comp == null) {
            val cage = cageOf(p)
            if (cage != null) { b.moving = false; face(b, cage.x, cage.y); return }
            b.orbitX = p.orbitX; b.orbitY = p.orbitY; b.orbitR = p.orbitR; b.orbitA = p.orbitA + PI
            place(b); face(b, p.x, p.y); b.moving = p.moving
            b.foe = p.id
            return
        }
        val still = b.actionTicks > 0 || comp.actionTicks > 0 || comp.action == Action.FAINT
        b.orbitDir = -p.orbitDir // against your circle, so they sweep across your view, and turning when you do
        orbit(b, DUEL_R, DUEL_SPEED, !still)
        comp.orbitX = b.orbitX; comp.orbitY = b.orbitY; comp.orbitR = b.orbitR; comp.orbitA = b.orbitA + PI
        comp.orbitDir = b.orbitDir
        if (comp.action != Action.FAINT) { place(comp); comp.moving = b.moving }
        face(b, comp.x, comp.y); face(comp, b.x, b.y)
        b.foe = comp.id; comp.foe = b.id
    }

    /** The beast stops a little way off and you start circling each other around the point between you. */
    private fun engage(p: Entity, b: Entity, events: MutableList<WorldEvent>) {
        val dx = b.x - p.x; val dy = b.y - p.y
        val cx = p.x + dx / 2; val cy = p.y + dy / 2
        val dir = if (rng.nextBoolean()) 1 else -1
        for (e in listOf(p, b)) {
            e.state = EntityState.ENGAGED
            e.orbitX = cx; e.orbitY = cy; e.orbitR = hypot(dx, dy) / 2; e.orbitDir = dir; e.orbitSpin = 0.0
            e.orbitA = atan2(e.y - cy, e.x - cx)
        }
        p.swapTicks = nextSwap(); p.downTicks = 0; p.recoverTicks = 0
        p.link = b.id; b.link = p.id; p.foe = b.id; b.foe = p.id
        p.targetX = cx; p.targetY = cy
        p.timer = 0
        events += WorldEvent.Encounter(p.id, b.id, b.species, if (b.zoneId >= 0) zoneOf(b).stage else 0)
    }

    /**
     * Throws [species]' cage toward the beast. A netbeast already out goes
     * back in its cage and the new one lands where it stood; otherwise the
     * cage lands a little past halfway to the beast. The fight's centre
     * becomes the point between the cage and the beast.
     */
    private fun throwCage(p: Entity, b: Entity, species: String) {
        val old = companionOf(p)?.takeUnless { leaveBody(it) }
        entities.values.removeAll { (it.kind == EntityKind.COMPANION || it.kind == EntityKind.CAGE) && it.link == p.id }
        p.companion = species
        val (lx, ly) = if (old != null) old.x to old.y else {
            val f = 0.55
            p.x + (b.x - p.x) * f to p.y + (b.y - p.y) * f
        }
        val cage = Entity(nextId++, EntityKind.CAGE, p.x, p.y, atan2(ly - p.y, lx - p.x), "cage", 0.4, zoneId = -1)
        cage.state = EntityState.THROWN; cage.link = p.id; cage.timer = CAGE_FLIGHT_TICKS
        cage.targetX = lx; cage.targetY = ly; cage.z = 0.6
        entities[cage.id] = cage
        if (b.action == Action.FAINT) return
        p.targetX = lx + (b.x - lx) / 2; p.targetY = ly + (b.y - ly) / 2
        if (old == null) {
            // the beast now circles the fight's centre instead of the player
            b.orbitX = p.targetX; b.orbitY = p.targetY
            b.orbitR = map.distance(b.x, b.y, b.orbitX, b.orbitY)
            b.orbitA = atan2(b.y - b.orbitY, b.x - b.orbitX)
        }
    }

    /** Your netbeast goes back in its cage: the beast turns on you again. */
    private fun recall(p: Entity, b: Entity) {
        companionOf(p)?.let { leaveBody(it) }
        entities.values.removeAll { (it.kind == EntityKind.COMPANION || it.kind == EntityKind.CAGE) && it.link == p.id }
        p.companion = null
        // circle each other again around the point between you
        val cx = p.x + (b.x - p.x) / 2; val cy = p.y + (b.y - p.y) / 2
        p.orbitX = cx; p.orbitY = cy; p.targetX = cx; p.targetY = cy
        p.orbitR = map.distance(p.x, p.y, cx, cy)
        p.orbitA = atan2(p.y - cy, p.x - cx)
        if (b.action != Action.FAINT) { b.action = Action.NONE; b.actionTicks = 0 }
    }

    /**
     * A fainted netbeast stays where it fell instead of going back in its cage: unlinked from
     * its player, it lies there for the rest of the fight and [BODY_TICKS] after. True if it did.
     */
    private fun leaveBody(c: Entity): Boolean {
        if (c.action != Action.FAINT) return false
        c.state = EntityState.GONE; c.link = -1; c.foe = -1; c.moving = false
        c.actionTicks = BODY_TICKS
        c.pendingAction = Action.NONE; c.pendingFx = null; c.pendingTicks = 0
        return true
    }

    /**
     * The rival in [playerId]'s fight sends out [species]: the netbeast he has
     * out goes back in its cage (or, fainted, lies where it fell and fades) and
     * he throws the new one's cage in, where the last one stood or, the first
     * time, [RIVAL_LAND] of the way to you. Until it's out ([WorldEvent.RivalOut])
     * the fight is between you and him.
     */
    fun rivalSendOut(playerId: Int, species: String) {
        val p = entities[playerId] ?: return
        val r = rivalOf(p) ?: return
        entities.values.removeAll { it.kind == EntityKind.CAGE && it.link == r.id }
        val old = entities[p.link]?.takeIf { it.kind == EntityKind.BEAST }
        val (lx, ly) = if (old != null) old.x + (r.x - old.x) * 0.3 to old.y + (r.y - old.y) * 0.3
            else r.x + (p.x - r.x) * RIVAL_LAND to r.y + (p.y - r.y) * RIVAL_LAND
        if (old != null) {
            if (old.action == Action.FAINT) fadeBody(old) else entities.remove(old.id)
            p.link = r.id
        }
        r.companion = species
        val cage = Entity(nextId++, EntityKind.CAGE, r.x, r.y, atan2(ly - r.y, lx - r.x), "cage", 0.4)
        cage.state = EntityState.THROWN; cage.link = r.id; cage.timer = CAGE_FLIGHT_TICKS
        cage.targetX = lx; cage.targetY = ly; cage.z = 0.6
        entities[cage.id] = cage
    }

    private fun rivalOf(p: Entity) = entities.values.firstOrNull { it.kind == EntityKind.RIVAL && it.link == p.id && it.state == EntityState.ENGAGED }

    /**
     * The rival's cage opens: his netbeast is the one you fight now. It faces
     * yours if it's out (the two circle the point between them), your cage if
     * that's in the air, else you (and you circle each other).
     */
    private fun rivalBeastOut(r: Entity, c: Entity, events: MutableList<WorldEvent>) {
        entities.remove(c.id)
        val p = entities[r.link] ?: return
        val b = Entity(nextId++, EntityKind.BEAST, c.x, c.y, c.angle, r.companion ?: r.species, NETBEAST_SIZE)
        b.state = EntityState.ENGAGED; b.link = p.id
        entities[b.id] = b
        p.link = b.id
        val comp = companionOf(p)
        val mine = cageOf(p)
        when {
            comp != null -> {
                val mx = b.x + (comp.x - b.x) / 2; val my = b.y + (comp.y - b.y) / 2
                b.orbitX = mx; b.orbitY = my
                b.orbitR = map.distance(b.x, b.y, mx, my)
                b.orbitA = atan2(b.y - my, b.x - mx)
                b.orbitDir = -p.orbitDir; b.orbitSpin = 0.0
                p.targetX = mx; p.targetY = my
                face(b, comp.x, comp.y); b.foe = comp.id; comp.foe = b.id
            }
            mine != null -> { face(b, mine.x, mine.y); b.foe = p.id }
            else -> {
                val cx = p.x + (b.x - p.x) / 2; val cy = p.y + (b.y - p.y) / 2
                p.orbitX = cx; p.orbitY = cy; p.targetX = cx; p.targetY = cy
                p.orbitR = map.distance(p.x, p.y, cx, cy)
                p.orbitA = atan2(p.y - cy, p.x - cx)
                b.orbitX = cx; b.orbitY = cy; b.orbitR = p.orbitR; b.orbitA = p.orbitA + PI
                face(b, p.x, p.y); b.foe = p.id; p.foe = b.id
            }
        }
        events += WorldEvent.RivalOut(p.id, b.id, b.species)
    }

    /** A fainted beast with no territory to go back to lies there [FADE_TICKS], then it's gone. */
    private fun fadeBody(b: Entity) {
        b.state = EntityState.GONE; b.link = -1; b.foe = -1; b.moving = false
        b.actionTicks = FADE_TICKS; b.timer = FADE_TICKS
        b.pendingAction = Action.NONE; b.pendingFx = null; b.pendingTicks = 0
    }

    private fun stepBody(c: Entity) {
        if (ownerOf(c)?.state == EntityState.ENGAGED) return // the fight goes on around it
        if (--c.actionTicks <= 0) entities.remove(c.id)
    }

    private fun stepCage(c: Entity, events: MutableList<WorldEvent>) {
        val p = entities[c.link]
        val loose = c.link < 0 && c.companion != null // tossed down on a walk: nobody's fight
        if (p == null && !loose) { entities.remove(c.id); return }
        when (c.state) {
            EntityState.THROWN -> {
                val t = 1.0 - c.timer.toDouble() / CAGE_FLIGHT_TICKS
                val sx = c.x; val sy = c.y
                c.x = sx + (c.targetX - sx) / c.timer.coerceAtLeast(1)
                c.y = sy + (c.targetY - sy) / c.timer.coerceAtLeast(1)
                c.z = 0.6 * (1 - t) + 1.2 * t * (1 - t) // lob
                if (--c.timer <= 0) { c.z = 0.0; c.state = EntityState.LANDED; c.timer = CAGE_OPEN_TICKS }
            }
            EntityState.LANDED -> if (--c.timer <= 0) {
                if (p == null) {
                    // it comes out, has a look around, and roams
                    val out = Entity(nextId++, EntityKind.COMPANION, c.x, c.y, c.angle, c.companion ?: "player", NETBEAST_SIZE, ownerId = c.ownerId)
                    out.state = EntityState.ROAM; out.forage = c.forage; out.timer = ROAM_LOOK_MIN
                    entities.remove(c.id)
                    entities[out.id] = out
                    return
                }
                if (p.kind == EntityKind.RIVAL) { rivalBeastOut(p, c, events); return }
                val b = entities[p.link]
                val out = Entity(nextId++, EntityKind.COMPANION, c.x, c.y, c.angle, p.companion ?: "player", NETBEAST_SIZE, ownerId = p.ownerId)
                out.state = EntityState.ENGAGED; out.link = p.id
                entities.remove(c.id)
                entities[out.id] = out
                if (b != null) {
                    face(out, b.x, b.y)
                    out.foe = b.id; b.foe = out.id
                    // the two beasts circle the point between them
                    val mx = out.x + (b.x - out.x) / 2; val my = out.y + (b.y - out.y) / 2
                    b.orbitX = mx; b.orbitY = my
                    b.orbitR = map.distance(b.x, b.y, mx, my)
                    b.orbitA = atan2(b.y - my, b.x - mx)
                    b.orbitDir = -p.orbitDir; b.orbitSpin = 0.0 // against your circle, so they sweep across your view
                    p.targetX = mx; p.targetY = my
                    events += WorldEvent.CompanionOut(p.id, b.id, out.species)
                }
            }
            else -> {}
        }
    }

    /** Returns true when arrived. */
    private fun moveToward(e: Entity, tx: Double, ty: Double, speed: Double): Boolean {
        val dx = tx - e.x; val dy = ty - e.y
        val d = hypot(dx, dy)
        if (d < 0.15) { e.moving = false; return true }
        e.angle = atan2(dy, dx)
        val step = minOf(d, speed * speedFactor(map.terrainAt(e.x, e.y)) * DT)
        e.x += dx / d * step; e.y += dy / d * step
        e.moving = true
        return false
    }

    /** The one taking [role] in [playerId]'s fight, if they're there. */
    fun roleEntity(playerId: Int, role: Role): Entity? {
        val p = entities[playerId] ?: return null
        return when (role) {
            Role.PLAYER -> p
            Role.BEAST -> entities[p.link]?.takeIf { it.kind == EntityKind.BEAST }
            Role.COMPANION -> companionOf(p)
        }
    }

    /**
     * Called by the host when its battle rules resolve something: shows the pose. A hit
     * waits until its attacker's lunge reaches its peak, so the blow lands on contact.
     */
    fun perform(playerId: Int, role: Role, action: Action) {
        val e = roleEntity(playerId, role) ?: return
        val wait = when (action) {
            Action.HIT -> untilBlowLands(e)
            Action.FAINT -> if (e.pendingAction != Action.NONE) e.pendingTicks else 0 // it drops when that blow lands
            else -> 0
        }
        if (wait > 0) { e.pendingAction = action; e.pendingTicks = maxOf(e.pendingTicks, wait) }
        else { e.action = action; e.actionTicks = action.ticks }
    }

    /** Plays fx_<name> over the entity taking [role], when the blow lands. */
    fun effect(playerId: Int, role: Role, fx: String) {
        val e = roleEntity(playerId, role) ?: return
        val wait = untilBlowLands(e)
        if (wait > 0) { e.pendingFx = fx; e.pendingTicks = maxOf(e.pendingTicks, wait) }
        else { e.fx = fx; e.fxAge = 0 }
    }

    /** Ticks until the lunge of whoever is attacking [e] peaks (0 if nobody is mid-lunge). */
    private fun untilBlowLands(e: Entity): Int {
        val a = entities[e.foe] ?: return 0
        return if (a.action == Action.ATTACK) a.actionTicks - Action.ATTACK.ticks / 2 else 0
    }

    /**
     * Called by the host once the battle for an Encounter is over. Walking resumes. A beaten
     * visitor is gone for good: a boss faints and fades, a rival walks off. Otherwise a visitor
     * waits there again, except a boss you drove off, which walks off.
     */
    fun resolveEncounter(playerId: Int, beastId: Int, outcome: EncounterOutcome) {
        val fought = entities[playerId]?.let { entities[it.link] } // a rival's netbeast, if he had one out
        entities.values.removeAll { it.kind == EntityKind.COMPANION && it.link == playerId || it.kind == EntityKind.CAGE && it.link == playerId }
        entities[playerId]?.let { p ->
            p.grace = GRACE_TICKS; p.link = -1; p.foe = -1; p.timer = 0
            if (p.state == EntityState.ENGAGED) {
                p.state = if (p.exploreTicks > 0) EntityState.WALKING else EntityState.DONE
                p.angle = p.orbitA + p.orbitDir * PI / 2 // walk on the way you were circling
                // pick yourself up (flat on the ground if it clobbered you) and walk on slowly
                p.downTicks = if (outcome == EncounterOutcome.PLAYER_BEATEN) DOWN_TICKS else 0
                p.recoverTicks = RECOVER_TICKS
                p.grace += p.downTicks
            }
            p.action = Action.NONE; p.actionTicks = 0
            p.pendingAction = Action.NONE; p.pendingFx = null; p.pendingTicks = 0
        }
        val b = entities[beastId] ?: return
        b.foe = -1
        b.pendingAction = Action.NONE; b.pendingFx = null; b.pendingTicks = 0
        if (b.kind == EntityKind.RIVAL) { resolveRival(b, fought?.takeIf { it.kind == EntityKind.BEAST }, outcome); return }
        val visitor = b.zoneId < 0
        when (outcome) {
            EncounterOutcome.BEAST_DEFEATED -> {
                b.state = EntityState.GONE; b.timer = if (visitor) FADE_TICKS else RESPAWN_TICKS; b.moving = false
                if (b.action == Action.FAINT) b.actionTicks = FADE_TICKS else { b.action = Action.NONE; b.actionTicks = 0 }
            }
            EncounterOutcome.PLAYER_FLED, EncounterOutcome.PLAYER_BEATEN -> {
                when {
                    !visitor -> { b.state = EntityState.RETURN; b.targetX = zoneOf(b).x; b.targetY = zoneOf(b).y }
                    outcome == EncounterOutcome.PLAYER_FLED -> { b.state = EntityState.LEAVE; b.timer = LEAVE_TICKS }
                    else -> b.state = EntityState.WAIT
                }
                b.action = Action.NONE; b.actionTicks = 0
            }
        }
    }

    /** A rival's fight is over: his netbeast that's out goes back in its cage (or fades, fainted); beaten, he walks off. */
    private fun resolveRival(r: Entity, out: Entity?, outcome: EncounterOutcome) {
        entities.values.removeAll { it.kind == EntityKind.CAGE && it.link == r.id }
        if (out != null) { if (out.action == Action.FAINT) fadeBody(out) else entities.remove(out.id) }
        r.action = Action.NONE; r.actionTicks = 0
        if (outcome == EncounterOutcome.BEAST_DEFEATED) { r.state = EntityState.LEAVE; r.timer = LEAVE_TICKS } else r.state = EntityState.WAIT
    }

    fun snapshot() = WorldSnapshot(tick, entities.values.map {
        EntitySnapshot(it.id, it.kind, it.x, it.y, it.z, it.angle, it.species, it.size, it.ownerId, it.zoneId, it.moving, it.state, it.link,
            it.foe, it.action, it.actionTicks, it.fx, it.fxAge, it.orbitX, it.orbitY, it.orbitR, it.downTicks, it.recoverTicks)
    }, coinGone.map { CoinGone(it.key, it.value) }, awake.toList())

    /** For clients: replace local entities with the host's view. */
    fun applySnapshot(s: WorldSnapshot) {
        tick = s.tick
        entities.clear()
        coinGone.clear()
        for (g in s.coinsGone) coinGone[g.coin] = g.ticks
        awake.clear(); awake += s.awake
        for (st in s.entities) {
            val e = Entity(st.id, st.kind, st.x, st.y, st.angle, st.species, st.size, st.ownerId, st.zoneId)
            e.z = st.z; e.moving = st.moving; e.state = st.state; e.link = st.link
            e.foe = st.foe; e.action = st.action; e.actionTicks = st.actionTicks; e.fx = st.fx; e.fxAge = st.fxAge
            e.orbitX = st.orbitX; e.orbitY = st.orbitY; e.orbitR = st.orbitR
            e.downTicks = st.downTicks; e.recoverTicks = st.recoverTicks
            entities[e.id] = e
            if (e.id >= nextId) nextId = e.id + 1
        }
    }
}
