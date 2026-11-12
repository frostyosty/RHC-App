package com.rockhard.blocker.world.sim

import com.rockhard.blocker.Netbeast
import com.rockhard.blocker.world.battle.FightEvent
import com.rockhard.blocker.world.battle.FightInput
import com.rockhard.blocker.world.battle.FightOutcome
import com.rockhard.blocker.world.battle.FightResult
import com.rockhard.blocker.world.battle.FightSetup
import com.rockhard.blocker.world.battle.FightStyle
import com.rockhard.blocker.world.battle.SideKind
import com.rockhard.blocker.world.battle.SideSetup
import com.rockhard.blocker.world.battle.WildSpec
import com.rockhard.blocker.world.battle.FightItem
import com.rockhard.blocker.world.battle.fightOf
import com.rockhard.blocker.world.battle.play
import com.rockhard.blocker.world.battle.threeSided
import com.rockhard.blocker.world.battle.threeSidedScript
import kotlin.test.Test
import kotlin.test.assertEquals

// Runs on every target (jvm + wasmJs). The fingerprints are pinned, so if a
// platform ever builds a different map or sim from the same seed, the web and
// Android clients would disagree about the world and this fails. The scripted
// fights go through the real battle rules (world/battle), so the pins cover
// them too (re-pinned on purpose in NETBEASTS_WEB_PLAN step 03).
class DeterminismTest {
    private val species = listOf("Zephyrlet" to 1, "Airstream" to 2, "Stratolord" to 3, "Cartini" to 1)
        .map { Species(it.first, it.second) }
    private val seed = 20260923L * 2654435761L

    private fun fnv(h: Long, v: Long) = (h xor v) * 0x100000001b3L

    /** A fight event, folded into a fingerprint (its text: ints, strings and enums only). */
    private fun fnvText(h0: Long, s: String): Long { var h = h0; for (c in s) h = fnv(h, c.code.toLong()); return h }

    /** One of your netbeasts, or a foe's, for the scripted fights. */
    private fun nb(name: String, type: String, hp: Int, move: String = "Tackle") =
        Netbeast(name, type, hp, hp, move, move, move, 0L, 0, 0, 0, false, "None", 0)

    /** The tile you start in and two further off (one of them at negative coordinates), and the ground across their edges. */
    private fun mapPrint(map: WorldMap): Long {
        var h = -0x340d631b7bdddcdbL
        for (t in listOf(map.home, map.tile(1, 0), map.tile(-2, -1))) {
            t.terrain.forEach { h = fnv(h, it.toLong()) }
            t.props.forEach { h = fnv(fnv(fnv(h, it.x.toRawBits()), it.y.toRawBits()), it.kind.ordinal.toLong()) }
            t.zones.forEach { h = fnv(fnv(fnv(fnv(h, it.id.toLong()), it.x.toRawBits()), it.y.toRawBits()), it.radius.toRawBits()) }
            t.coins.forEach { h = fnv(fnv(h, it.x.toRawBits()), it.y.toRawBits()) }
        }
        for (i in -64 until 64) h = fnv(h, map.groundAt(i * 2.37, i * 3.11).toRawBits())
        return h
    }

    @Test
    fun mapIsIdenticalOnEveryPlatform() {
        assertEquals(MAP_PRINT, mapPrint(WorldMap.generate(seed, species)))
    }

    /** A tile is the same whichever tiles were made before it, so it doesn't matter which way anyone walked. */
    @Test
    fun tilesDontDependOnTheOrderTheyreMadeIn() {
        val far = WorldMap.generate(seed, species)
        for (t in listOf(5 to -3, -2 to -1, 1 to 0, 1 to 1)) far.tile(t.first, t.second)
        assertEquals(MAP_PRINT, mapPrint(far))
    }

    /** A coastal NZ region (Tauranga) takes the sea-band, forest and NZ-flora paths. */
    @Test
    fun regionalMapIsIdenticalOnEveryPlatform() {
        assertEquals(COAST_PRINT, mapPrint(WorldMap.generate(seed, species, Region(30, 0, 2, 2, Flora.NZ, HouseStyle.NZ))))
    }

    /**
     * Walk toward the nearest beast until you meet one; the fight runs through the battle rules,
     * in the rain: a second in you throw your cage, then shout a move every two seconds. When
     * it's over you hunt the next one.
     */
    @Test
    fun scriptedWalkIsIdenticalOnEveryPlatform() {
        val world = World(WorldMap.generate(seed, species))
        val me = world.addPlayer("test", "Zephyrlet", 180).id
        var h = 0L
        var fightAt = -1
        var fights = 0
        var closed = 0
        repeat(World.TICK_HZ * 40) { t ->
            val inFight = world.entities[me]!!.state == EntityState.ENGAGED
            val input = when {
                !inFight || fightAt < 0 -> null
                t == fightAt + 30 -> FightInput(cage = 0)
                t > fightAt + 30 && (t - fightAt) % 60 == 0 -> FightInput(move = 1 + (t / 60) % 3)
                else -> null
            }
            val look = if (!inFight) huntSteer(world, world.entities[me]!!) else if (t % 60 < 30) 0.02 else -0.015
            for (e in world.step(mapOf(me to PlayerInput(look, input)))) {
                if (e is WorldEvent.Encounter) {
                    fightAt = t; fights++
                    world.startFight(me, e.beastId, FightSetup(FightStyle.WILDS, "Rain", place = "Tauranga"),
                        SideSetup(SideKind.PLAYER, listOf(nb("Zephyrlet", "Wind", 120, "Ping"))), SideSetup(SideKind.WILD, wild = WildSpec(e.species, e.stage)))
                }
                if (e is WorldEvent.Fighting) { h = fnvText(h, e.event.toString()); if (e.event is FightEvent.Closed) closed++ }
            }
            world.snapshot().entities.forEach { e -> h = fnv(fnv(fnv(h, e.id.toLong()), e.x.toRawBits()), e.y.toRawBits()) }
            world.coinGone.forEach { h = fnv(fnv(h, it.key.toLong()), it.value.toLong()) }
        }
        assertEquals(true, fights >= 1, "the scripted walk should meet a beast")
        assertEquals(true, closed >= 1, "the fight should play out through the rules")
        assertEquals(WALK_PRINT, h)
    }

    /**
     * A poacher put in your path: you walk straight into him, he throws his cage, yours goes in,
     * his first netbeast falls and he sends out another where it lay, that one falls too, and he
     * walks off beaten. You throw the cage and shout moves; the rules do the rest.
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
        var result: FightResult? = null
        repeat(World.TICK_HZ * 40) { t ->
            if (t == 30) poacher = world.summon(me, EntityKind.RIVAL, "Poacher", 0.7)
            since++
            val input = when {
                stage == 2 && since == 30 -> FightInput(cage = 0)
                stage in 3..4 && since % 45 == 30 -> FightInput(move = 1)
                else -> null
            }
            val look = if (stage !in 1..4) 0.0 else if (t % 90 < 45) 0.02 else -0.015
            for (e in world.step(mapOf(me to PlayerInput(look, input)))) {
                when (e) {
                    is WorldEvent.Encounter -> if (e.beastId == poacher) {
                        stage = 1
                        world.startFight(me, poacher, FightSetup(FightStyle.WILDS),
                            SideSetup(SideKind.PLAYER, listOf(nb("Zephyrlet", "Wind", 400, "Ping"))),
                            SideSetup(SideKind.POACHER, listOf(nb("Airstream", "Wind", 60), nb("Stratolord", "Wind", 60)), captive = nb("Cartini", "Earth", 30)))
                    }
                    is WorldEvent.RivalOut -> stage = if (stage == 1) 2 else 4
                    is WorldEvent.CompanionOut -> stage = 3
                    is WorldEvent.Fighting -> {
                        h = fnvText(h, e.event.toString())
                        (e.event as? FightEvent.Ended)?.let { result = it.result }
                        if (e.event is FightEvent.Closed) stage = 5
                        continue
                    }
                    else -> continue
                }
                since = 0
            }
            world.snapshot().entities.forEach { e -> h = fnv(fnv(fnv(fnv(h, e.id.toLong()), e.x.toRawBits()), e.y.toRawBits()), e.state.ordinal.toLong()) }
        }
        assertEquals(5, stage, "the scripted poacher fight should play out")
        assertEquals(FightOutcome.WON, result?.outcomes?.get(0), "you beat him")
        assertEquals("[Will to Live] Cartini", result?.rescued?.name, "and get his prisoner back")
        assertEquals(null, world.entities[poacher], "beaten, the poacher walks off")
        assertEquals(POACHER_PRINT, h)
    }

    /**
     * The invasion boss put in your path (a summoned beast, not one from a territory): you walk
     * straight into it, your cage goes in and you wait; it loses patience and strikes, yours falls,
     * it turns on you and clobbers you, and it waits there again without squaring up to you for
     * the next 6 seconds.
     */
    @Test
    fun scriptedBossFightIsIdenticalOnEveryPlatform() {
        val world = World(WorldMap.generate(seed, species))
        world.entities.values.removeAll { it.kind == EntityKind.BEAST } // just the boss
        val me = world.addPlayer("test", "Zephyrlet", 180).id
        var boss = -1
        var stage = 0 // walking, met it, yours out, lost
        var since = 0
        var lostAt = -1
        var metAgain = false
        var knockedFlat = false
        var h = 0L
        repeat(World.TICK_HZ * 40) { t ->
            if (t == 30) boss = world.summon(me, EntityKind.BEAST, "Stratolord", 1.5)
            since++
            val input = if (stage == 1 && since == 30) FightInput(cage = 0) else null
            val look = if (stage !in 1..2) 0.0 else if (t % 90 < 45) 0.02 else -0.015
            for (e in world.step(mapOf(me to PlayerInput(look, input)))) {
                when (e) {
                    is WorldEvent.Encounter -> if (e.beastId == boss) {
                        if (stage < 3) {
                            stage = 1
                            world.startFight(me, boss, FightSetup(FightStyle.WILDS, place = "Tauranga"),
                                SideSetup(SideKind.PLAYER, listOf(nb("Zephyrlet", "Wind", 20))),
                                SideSetup(SideKind.BOSS, listOf(nb("Stratolord", "EventBoss", 900)), weakness = "Nobody"))
                        } else if (t - lostAt < World.TICK_HZ * 6) metAgain = true
                    }
                    is WorldEvent.CompanionOut -> stage = 2
                    is WorldEvent.Fighting -> {
                        h = fnvText(h, e.event.toString())
                        if (e.event is FightEvent.Closed) { stage = 3; lostAt = t; knockedFlat = world.entities[me]!!.downTicks > 0 }
                        continue
                    }
                    else -> continue
                }
                since = 0
            }
            if (lostAt >= 0 && t == lostAt + World.TICK_HZ * 6) assertEquals(EntityState.WAIT, world.entities[boss]?.state, "a boss you lose to waits there again")
            world.snapshot().entities.forEach { e -> h = fnv(fnv(fnv(fnv(h, e.id.toLong()), e.x.toRawBits()), e.y.toRawBits()), e.state.ordinal.toLong()) }
        }
        assertEquals(3, stage, "the scripted boss fight should play out")
        assertEquals(true, knockedFlat, "it clobbered you, so you're knocked flat")
        assertEquals(false, metAgain, "the boss squared up again straight after")
        assertEquals(BOSS_PRINT, h)
    }

    /**
     * A battle-screen fight in the rain, on the rules alone: you throw a net, then shout moves
     * at a beast that's too strong for your two netbeasts, both fall, and you're beaten.
     */
    @Test
    fun scriptedScreenFightIsIdenticalOnEveryPlatform() {
        val pet = nb("Cartini", "Earth", 50).also { it.eqNets = 1 }
        val f = fightOf(seed, SideSetup(SideKind.PLAYER, listOf(pet, nb("Zephyrlet", "Wind", 40, "Ping"))),
            SideSetup(SideKind.WILD, listOf(nb("Stratolord", "Wind", 260).also { it.hp = 2000 })), weather = "Rain")
        val evs = play(f, 6000) { t ->
            when {
                t == 20L -> mapOf(0 to FightInput(item = FightItem.NET))
                t % 45 == 0L -> mapOf(0 to FightInput(move = 1 + (t / 45 % 3).toInt()))
                else -> emptyMap()
            }
        }
        assertEquals(true, f.closed, "the scripted screen fight should play out")
        assertEquals(FightOutcome.BEATEN, f.result?.outcomes?.get(0))
        var h = -0x340d631b7bdddcdbL
        for (e in evs) h = fnvText(h, e.toString())
        assertEquals(SCREEN_PRINT, h)
    }

    /** Two players and a wild beast, whoever lands the last blow catching it (FightTest.threeSidesFightItOut). */
    @Test
    fun scriptedThreeSidedFightIsIdenticalOnEveryPlatform() {
        val f = threeSided(21)
        var h = -0x340d631b7bdddcdbL
        for (e in play(f, 4000, threeSidedScript)) h = fnvText(h, e.toString())
        assertEquals(true, f.closed, "the three-sided fight should play out")
        assertEquals(THREE_PRINT, h)
    }

    /** Turn toward the nearest beast, at most 0.08 rad a tick (DetMath, so it's the same everywhere). */
    private fun huntSteer(world: World, me: Entity): Double {
        val b = world.entities.values.filter { it.kind == EntityKind.BEAST }
            .minByOrNull { world.map.distance(me.x, me.y, it.x, it.y) } ?: return 0.0
        var diff = DetMath.atan2(b.y - me.y, b.x - me.x) - me.angle
        while (diff > kotlin.math.PI) diff -= 2 * kotlin.math.PI
        while (diff < -kotlin.math.PI) diff += 2 * kotlin.math.PI
        return diff.coerceIn(-0.08, 0.08)
    }

    companion object {
        const val MAP_PRINT = -1623294530827492555L
        const val COAST_PRINT = 2848793715271895972L
        const val WALK_PRINT = -4677524581404405101L
        const val POACHER_PRINT = 5463697191483700637L
        const val BOSS_PRINT = -4270977819063960277L
        const val SCREEN_PRINT = 615517087881252909L
        const val THREE_PRINT = 3499320764698997982L
    }
}
