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
    }
}
