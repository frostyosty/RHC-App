package com.rockhard.blocker.world.sim

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Pure Kotlin (no Android imports) so a future server can run the same sim.

enum class EntityKind { PLAYER, BEAST, CAGE, COMPANION }

enum class EntityState { WALKING, PATROL, PAUSE, CHASE, RETURN, ENGAGED, THROWN, LANDED, GONE, DONE }

/**
 * Anything in the world. Players are keyed by [ownerId] (a device/account id
 * once multiplayer exists); beasts belong to a [zoneId]; a CAGE is a thrown
 * cage in flight or on the ground; a COMPANION is the player's netbeast that
 * came out of it.
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
    var companion: String? = null   // player: species waiting in the lead cage
    var exploreTicks = 0        // player: auto-walk time left
    var grace = 0               // player: ticks before beasts can engage again
}

/**
 * One tick of intent from a player; this is what a client would send to a
 * host. The player always walks forward, so the only input is steering:
 * a one-off turn in radians (swipe-to-look), applied on a single tick.
 */
data class PlayerInput(val lookDelta: Double = 0.0)

sealed class WorldEvent {
    /** A beast reached a player. The cage is thrown; the battle starts at BattleReady. */
    data class Encounter(val playerId: Int, val beastId: Int, val species: String, val stage: Int) : WorldEvent()
    /** The companion is out (or the player faces it alone): hand over to the battle. */
    data class BattleReady(val playerId: Int, val beastId: Int, val species: String, val stage: Int) : WorldEvent()
    /** The player's exploration timer ran out. */
    data class ExplorationOver(val playerId: Int) : WorldEvent()
}

enum class EncounterOutcome { BEAST_DEFEATED, PLAYER_FLED }

data class EntitySnapshot(
    val id: Int, val kind: EntityKind, val x: Double, val y: Double, val z: Double, val angle: Double,
    val species: String, val size: Double, val ownerId: String?, val zoneId: Int,
    val moving: Boolean, val state: EntityState, val link: Int,
)

/** Everything a remote client needs besides the seed: small enough to send often. */
data class WorldSnapshot(val tick: Long, val entities: List<EntitySnapshot>)

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
        const val CHASE_SPEED = 2.9       // faster than walking: if it sees you, it gets you
        const val AGGRO_RANGE = 6.0
        const val TOUCH_RANGE = 0.8
        const val RESPAWN_TICKS = 45 * TICK_HZ
        const val GRACE_TICKS = 3 * TICK_HZ
        const val CAGE_FLIGHT_TICKS = 15   // 0.5s arc
        const val CAGE_OPEN_TICKS = 30     // 1s on the ground before the netbeast emerges
        const val FACE_OFF_TICKS = 24      // pause with both beasts out before the battle

        fun speedFactor(terrain: Int) = when (terrain) { Terrain.WATER -> 0.55; Terrain.TALL_GRASS -> 0.85; Terrain.SAND -> 0.9; else -> 1.0 }
    }

    var tick = 0L
        private set
    private val rng = Random(map.seed xor 0x5EED)
    private var nextId = 1
    val entities = LinkedHashMap<Int, Entity>()

    init {
        for (z in map.zones) repeat(if (z.stage >= 3) 1 else 2) { spawnBeast(z) }
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

    fun setCompanion(playerId: Int, companion: String?) { entities[playerId]?.companion = companion }

    fun removePlayer(id: Int) { entities.remove(id) }

    private fun spawnBeast(z: Zone): Entity {
        val a = rng.nextDouble(0.0, PI * 2); val r = rng.nextDouble(0.0, z.radius)
        val size = when (z.stage) { 1 -> 0.55; 2 -> 0.75; else -> 1.0 }
        val e = Entity(nextId++, EntityKind.BEAST, map.wrap(z.x + cos(a) * r), map.wrap(z.y + sin(a) * r), rng.nextDouble(0.0, PI * 2), z.species, size, zoneId = z.id)
        pickWaypoint(e, z)
        entities[e.id] = e
        return e
    }

    private fun zoneOf(e: Entity) = map.zones[e.zoneId]

    private fun pickWaypoint(e: Entity, z: Zone) {
        val a = rng.nextDouble(0.0, PI * 2); val r = rng.nextDouble(0.0, z.radius)
        e.targetX = map.wrap(z.x + cos(a) * r); e.targetY = map.wrap(z.y + sin(a) * r)
        e.state = EntityState.PATROL
    }

    /** Advance one tick. [inputs] maps player entity id -> that player's input. */
    fun step(inputs: Map<Int, PlayerInput>): List<WorldEvent> {
        tick++
        val events = mutableListOf<WorldEvent>()
        val players = entities.values.filter { it.kind == EntityKind.PLAYER }

        for (p in players) stepPlayer(p, inputs[p.id] ?: PlayerInput(), events)
        for (e in entities.values.toList()) when (e.kind) {
            EntityKind.BEAST -> stepBeast(e, players, events)
            EntityKind.CAGE -> stepCage(e)
            else -> {}
        }
        return events
    }

    private fun stepPlayer(p: Entity, input: PlayerInput, events: MutableList<WorldEvent>) {
        when (p.state) {
            EntityState.WALKING -> {
                p.angle += input.lookDelta
                val speed = WALK_SPEED * speedFactor(map.terrainAt(p.x, p.y)) * DT
                p.x = map.wrap(p.x + cos(p.angle) * speed)
                p.y = map.wrap(p.y + sin(p.angle) * speed)
                p.moving = true
                if (p.grace > 0) p.grace--
                if (--p.exploreTicks <= 0) {
                    p.state = EntityState.DONE; p.moving = false
                    events += WorldEvent.ExplorationOver(p.id)
                }
            }
            EntityState.ENGAGED -> {
                // turn to watch the cage and the fight
                val b = entities[p.link]
                if (b != null) p.angle = turnToward(p.angle, atan2(map.delta(p.y, b.y), map.delta(p.x, b.x)), 0.12)
                p.moving = false
                if (p.timer > 0 && --p.timer == 0 && b != null) {
                    events += WorldEvent.BattleReady(p.id, b.id, b.species, zoneOf(b).stage)
                }
            }
            else -> p.moving = false
        }
    }

    private fun stepBeast(b: Entity, players: List<Entity>, events: MutableList<WorldEvent>) {
        val z = zoneOf(b)
        when (b.state) {
            EntityState.GONE -> { if (--b.timer <= 0) { entities.remove(b.id); spawnBeast(z) }; return }
            EntityState.ENGAGED -> {
                b.moving = false
                entities[b.link]?.let { p -> b.angle = atan2(map.delta(b.y, p.y), map.delta(b.x, p.x)) }
                return
            }
            else -> {}
        }

        val prey = players
            .filter { it.state == EntityState.WALKING && it.grace <= 0 && map.distance(b.x, b.y, it.x, it.y) < AGGRO_RANGE }
            .minByOrNull { map.distance(b.x, b.y, it.x, it.y) }
        val leash = z.radius + 8
        if (prey != null && map.distance(prey.x, prey.y, z.x, z.y) < leash) { b.state = EntityState.CHASE; b.link = prey.id }
        else if (b.state == EntityState.CHASE) { b.state = EntityState.RETURN; b.targetX = z.x; b.targetY = z.y }

        when (b.state) {
            EntityState.PAUSE -> { b.moving = false; if (--b.timer <= 0) pickWaypoint(b, z) }
            EntityState.PATROL, EntityState.RETURN -> {
                if (moveToward(b, b.targetX, b.targetY, PATROL_SPEED)) { b.state = EntityState.PAUSE; b.timer = rng.nextInt(TICK_HZ, 3 * TICK_HZ) }
            }
            EntityState.CHASE -> {
                val p = entities[b.link] ?: run { b.state = EntityState.RETURN; return }
                moveToward(b, p.x, p.y, CHASE_SPEED)
                if (map.distance(p.x, p.y, b.x, b.y) < TOUCH_RANGE) engage(p, b, events)
            }
            else -> {}
        }
    }

    private fun engage(p: Entity, b: Entity, events: MutableList<WorldEvent>) {
        b.state = EntityState.ENGAGED; b.moving = false; b.link = p.id
        p.state = EntityState.ENGAGED; p.moving = false; p.link = b.id
        events += WorldEvent.Encounter(p.id, b.id, b.species, zoneOf(b).stage)
        // back the beast off a little so the cage has room to land between you
        val a = atan2(map.delta(p.y, b.y), map.delta(p.x, b.x))
        b.x = map.wrap(p.x + cos(a) * 2.2); b.y = map.wrap(p.y + sin(a) * 2.2)
        val species = p.companion
        if (species == null) { p.timer = FACE_OFF_TICKS; return }
        val cage = Entity(nextId++, EntityKind.CAGE, p.x, p.y, a, "cage", 0.4, zoneId = -1)
        cage.state = EntityState.THROWN; cage.link = p.id; cage.timer = CAGE_FLIGHT_TICKS
        cage.targetX = map.wrap(p.x + cos(a) * 1.1); cage.targetY = map.wrap(p.y + sin(a) * 1.1)
        cage.z = 0.6
        entities[cage.id] = cage
        p.timer = 0 // BattleReady comes from the cage once the netbeast is out
    }

    private fun stepCage(c: Entity) {
        val p = entities[c.link] ?: run { entities.remove(c.id); return }
        when (c.state) {
            EntityState.THROWN -> {
                val t = 1.0 - c.timer.toDouble() / CAGE_FLIGHT_TICKS
                val sx = c.x; val sy = c.y
                c.x = map.wrap(sx + map.delta(sx, c.targetX) / c.timer.coerceAtLeast(1))
                c.y = map.wrap(sy + map.delta(sy, c.targetY) / c.timer.coerceAtLeast(1))
                c.z = 0.6 * (1 - t) + 1.2 * t * (1 - t) // lob
                if (--c.timer <= 0) { c.z = 0.0; c.state = EntityState.LANDED; c.timer = CAGE_OPEN_TICKS }
            }
            EntityState.LANDED -> if (--c.timer <= 0) {
                val b = entities[p.link]
                val out = Entity(nextId++, EntityKind.COMPANION, c.x, c.y, c.angle, p.companion ?: "player", 0.65, ownerId = p.ownerId)
                out.state = EntityState.ENGAGED; out.link = p.id
                entities.remove(c.id)
                entities[out.id] = out
                if (b != null) out.angle = atan2(map.delta(out.y, b.y), map.delta(out.x, b.x))
                p.timer = FACE_OFF_TICKS
            }
            else -> {}
        }
    }

    /** Returns true when arrived. */
    private fun moveToward(e: Entity, tx: Double, ty: Double, speed: Double): Boolean {
        val dx = map.delta(e.x, tx); val dy = map.delta(e.y, ty)
        val d = kotlin.math.hypot(dx, dy)
        if (d < 0.15) { e.moving = false; return true }
        e.angle = atan2(dy, dx)
        val step = minOf(d, speed * speedFactor(map.terrainAt(e.x, e.y)) * DT)
        e.x = map.wrap(e.x + dx / d * step); e.y = map.wrap(e.y + dy / d * step)
        e.moving = true
        return false
    }

    private fun turnToward(a: Double, target: Double, rate: Double): Double {
        var d = target - a
        while (d > PI) d -= 2 * PI
        while (d < -PI) d += 2 * PI
        return a + d.coerceIn(-rate, rate)
    }

    /** Called by the host once the battle for an Encounter is over. Walking resumes. */
    fun resolveEncounter(playerId: Int, beastId: Int, outcome: EncounterOutcome) {
        entities.values.removeAll { it.kind == EntityKind.COMPANION && it.link == playerId || it.kind == EntityKind.CAGE && it.link == playerId }
        entities[playerId]?.let { p ->
            p.grace = GRACE_TICKS; p.link = -1; p.timer = 0
            if (p.state == EntityState.ENGAGED) p.state = if (p.exploreTicks > 0) EntityState.WALKING else EntityState.DONE
        }
        val b = entities[beastId] ?: return
        when (outcome) {
            EncounterOutcome.BEAST_DEFEATED -> { b.state = EntityState.GONE; b.timer = RESPAWN_TICKS; b.moving = false }
            EncounterOutcome.PLAYER_FLED -> { b.state = EntityState.RETURN; b.targetX = zoneOf(b).x; b.targetY = zoneOf(b).y }
        }
    }

    fun snapshot() = WorldSnapshot(tick, entities.values.map {
        EntitySnapshot(it.id, it.kind, it.x, it.y, it.z, it.angle, it.species, it.size, it.ownerId, it.zoneId, it.moving, it.state, it.link)
    })

    /** For clients: replace local entities with the host's view. */
    fun applySnapshot(s: WorldSnapshot) {
        tick = s.tick
        entities.clear()
        for (st in s.entities) {
            val e = Entity(st.id, st.kind, st.x, st.y, st.angle, st.species, st.size, st.ownerId, st.zoneId)
            e.z = st.z; e.moving = st.moving; e.state = st.state; e.link = st.link
            entities[e.id] = e
            if (e.id >= nextId) nextId = e.id + 1
        }
    }
}
