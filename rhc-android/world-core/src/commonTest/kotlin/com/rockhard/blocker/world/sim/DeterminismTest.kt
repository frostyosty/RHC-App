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
        for (i in 0 until 64) h = fnv(h, map.groundAt(i * 1.37, i * 2.11).toRawBits())
        return h
    }

    @Test
    fun mapIsIdenticalOnEveryPlatform() {
        assertEquals(MAP_PRINT, mapPrint(WorldMap.generate(seed, species)))
    }

    @Test
    fun scriptedWalkIsIdenticalOnEveryPlatform() {
        val world = World(WorldMap.generate(seed, species))
        val me = world.addPlayer("test", "Zephyrlet", 180).id
        var h = 0L
        repeat(World.TICK_HZ * 20) { t ->
            world.step(mapOf(me to PlayerInput(if (t % 60 < 30) 0.02 else -0.015)))
            world.snapshot().entities.forEach { e -> h = fnv(fnv(fnv(h, e.id.toLong()), e.x.toRawBits()), e.y.toRawBits()) }
        }
        assertEquals(WALK_PRINT, h)
    }

    companion object {
        const val MAP_PRINT = 2596199744769405883L
        const val WALK_PRINT = 8105609390663669207L
    }
}
