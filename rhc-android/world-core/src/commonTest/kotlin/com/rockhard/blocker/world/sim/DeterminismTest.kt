package com.rockhard.blocker.world.sim

import kotlin.test.Test
import kotlin.test.assertEquals

// Runs on every target (jvm + wasmJs). The fingerprints are pinned, so if a
// platform ever builds a different map or sim from the same seed, the web and
// Android clients would disagree about the world and this fails.
class DeterminismTest {
    private val species = listOf("Zephyrlet" to 1, "Airstream" to 2, "Stratolord" to 3, "Cartini" to 1)
        .map { Species(it.first, it.second) }
    private val seed = 20260923L * 2654435761L

    private fun fnv(h: Long, v: Long) = (h xor v) * 0x100000001b3L

    private fun mapPrint(map: WorldMap): Long {
        var h = -0x340d631b7bdddcdbL
        map.terrain.forEach { h = fnv(h, it.toLong()) }
        map.props.forEach { h = fnv(fnv(fnv(h, it.x.toRawBits()), it.y.toRawBits()), it.kind.ordinal.toLong()) }
        map.zones.forEach { h = fnv(fnv(fnv(h, it.x.toRawBits()), it.y.toRawBits()), it.radius.toRawBits()) }
        map.coins.forEach { h = fnv(fnv(h, it.x.toRawBits()), it.y.toRawBits()) }
        for (i in 0 until 64) h = fnv(h, map.groundAt(i * 1.37, i * 2.11).toRawBits())
        return h
    }

    @Test
    fun mapIsIdenticalOnEveryPlatform() {
        assertEquals(MAP_PRINT, mapPrint(WorldMap.generate(seed, species)))
    }

    /** A coastal NZ region (Tauranga) takes the sea-band, forest and NZ-flora paths. */
    @Test
    fun regionalMapIsIdenticalOnEveryPlatform() {
        assertEquals(COAST_PRINT, mapPrint(WorldMap.generate(seed, species, Region(30, 0, 2, 2, Flora.NZ, HouseStyle.NZ))))
    }

    @Test
    fun scriptedWalkIsIdenticalOnEveryPlatform() {
        val world = World(WorldMap.generate(seed, species))
        val me = world.addPlayer("test", "Zephyrlet", 180).id
        var h = 0L
        var fightAt = -1
        repeat(World.TICK_HZ * 40) { t ->
            // head for the nearest beast (they shy away, so a fight takes steering), and in a
            // fight throw a cage after a second, then circle the other way
            val cage = if (fightAt >= 0 && t == fightAt + 30) "Zephyrlet" else null
            val look = if (fightAt < 0) huntSteer(world, world.entities[me]!!) else if (t % 60 < 30) 0.02 else -0.015
            val events = world.step(mapOf(me to PlayerInput(look, cage)))
            if (fightAt < 0 && events.any { it is WorldEvent.Encounter }) fightAt = t
            world.snapshot().entities.forEach { e -> h = fnv(fnv(fnv(h, e.id.toLong()), e.x.toRawBits()), e.y.toRawBits()) }
            world.coinGone.forEach { h = fnv(h, it.toLong()) }
        }
        assertEquals(true, fightAt >= 0, "the scripted walk should meet a beast")
        assertEquals(WALK_PRINT, h)
    }

    /**
     * A poacher put in your path: you walk straight into him, he throws his cage, yours goes in,
     * his first netbeast falls and he sends out another where it lay, that one falls too, and he
     * walks off beaten.
     */
    @Test
    fun scriptedPoacherFightIsIdenticalOnEveryPlatform() {
        val world = World(WorldMap.generate(seed, species))
        world.entities.values.removeAll { it.kind == EntityKind.BEAST } // just him
        val me = world.addPlayer("test", "Zephyrlet", 180).id
        var poacher = -1
        var stage = 0 // walking, met him, his first out, yours out, his second out, over
        var since = 0
        var h = 0L
        repeat(World.TICK_HZ * 40) { t ->
            if (t == 30) poacher = world.summon(me, EntityKind.RIVAL, "Poacher", 0.7)
            since++
            var cage: String? = null
            when {
                stage == 1 && since == 1 -> world.rivalSendOut(me, "Airstream")
                stage == 2 && since == 30 -> cage = "Zephyrlet"
                stage == 3 && since == 30 -> world.perform(me, Role.COMPANION, Action.ATTACK)
                stage == 3 && since == 34 -> world.perform(me, Role.BEAST, Action.HIT)
                stage == 3 && since == 60 -> world.perform(me, Role.BEAST, Action.FAINT)
                stage == 3 && since == 90 -> world.rivalSendOut(me, "Stratolord")
                stage == 4 && since == 30 -> world.perform(me, Role.BEAST, Action.ATTACK)
                stage == 4 && since == 34 -> world.perform(me, Role.COMPANION, Action.HIT)
                stage == 4 && since == 60 -> world.perform(me, Role.BEAST, Action.FAINT)
                stage == 4 && since == 90 -> { world.resolveEncounter(me, poacher, EncounterOutcome.BEAST_DEFEATED); stage = 5 }
            }
            val look = if (stage !in 1..4) 0.0 else if (t % 90 < 45) 0.02 else -0.015
            for (e in world.step(mapOf(me to PlayerInput(look, cage)))) {
                when (e) {
                    is WorldEvent.Encounter -> if (e.beastId == poacher) stage = 1
                    is WorldEvent.RivalOut -> stage = if (stage == 1) 2 else 4
                    is WorldEvent.CompanionOut -> stage = 3
                    else -> continue
                }
                since = 0
            }
            world.snapshot().entities.forEach { e -> h = fnv(fnv(fnv(fnv(h, e.id.toLong()), e.x.toRawBits()), e.y.toRawBits()), e.state.ordinal.toLong()) }
        }
        assertEquals(5, stage, "the scripted poacher fight should play out")
        assertEquals(null, world.entities[poacher], "beaten, the poacher walks off")
        assertEquals(POACHER_PRINT, h)
    }

    /** Turn toward the nearest beast, at most 0.08 rad a tick (DetMath, so it's the same everywhere). */
    private fun huntSteer(world: World, me: Entity): Double {
        val b = world.entities.values.filter { it.kind == EntityKind.BEAST }
            .minByOrNull { world.map.distance(me.x, me.y, it.x, it.y) } ?: return 0.0
        var diff = DetMath.atan2(world.map.delta(me.y, b.y), world.map.delta(me.x, b.x)) - me.angle
        while (diff > kotlin.math.PI) diff -= 2 * kotlin.math.PI
        while (diff < -kotlin.math.PI) diff += 2 * kotlin.math.PI
        return diff.coerceIn(-0.08, 0.08)
    }

    companion object {
        const val MAP_PRINT = 1753128500768626353L
        const val COAST_PRINT = 6737657199655346600L
        const val WALK_PRINT = -8272280016561550702L
        const val POACHER_PRINT = -2272195723785925189L
    }
}
