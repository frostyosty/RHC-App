package com.rockhard.blocker.world.battle

import com.rockhard.blocker.Netbeast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** A netbeast for a test. [maxHp] sets how hard it hits (a quarter of it); give it more [hp] and it never falls. */
internal fun nb(name: String, type: String = "Fire", maxHp: Int = 40, hp: Int = maxHp, m1: String = "Tackle", m2: String = m1, m3: String = m1) =
    Netbeast(name, type, hp, maxHp, m1, m2, m3, 0L, 0, 0, 0, false, "None", 0)

/** A new fight, its sides in order. */
internal fun fightOf(seed: Long, vararg sides: SideSetup, style: FightStyle = FightStyle.SCREEN, weather: String = "Clear", killingBlowCaptures: Boolean = false) =
    Fight.create(1, FightSetup(style, weather, killingBlowCaptures = killingBlowCaptures), sides.toList(), seed)

/**
 * Runs [f] from its start for up to [ticks] (or until it closes), giving each tick whatever [script]
 * says each side does then. In the Wilds a cage opens 45 ticks after it's thrown, as in the World.
 */
internal fun play(f: Fight, ticks: Int, script: (tick: Long) -> Map<Int, FightInput> = { emptyMap() }): List<FightEvent> {
    val out = f.start().toMutableList()
    val opening = mutableMapOf<Int, Long>()
    fun cages(evs: List<FightEvent>) { if (f.wilds) for (e in evs) if (e is FightEvent.SentOut) opening[e.side] = f.tick + 45 }
    cages(out)
    repeat(ticks) {
        if (f.closed) return out
        for ((side, at) in opening.toList()) if (f.tick + 1 >= at) { opening.remove(side); f.petOut(side) }
        val evs = f.step(script(f.tick + 1))
        cages(evs)
        out += evs
    }
    return out
}

internal fun List<FightEvent>.logText() = filterIsInstance<FightEvent.Log>().joinToString("\n") { it.text }

/** Side 0 does [what] every [every] ticks. */
internal fun every(every: Int, what: FightInput): (Long) -> Map<Int, FightInput> = { t -> if (t % every == 0L) mapOf(0 to what) else emptyMap() }

class FightTest {
    private val me = { party: List<Netbeast> -> SideSetup(SideKind.PLAYER, party) }
    private fun wild(beast: Netbeast) = SideSetup(SideKind.WILD, listOf(beast))

    @Test
    fun theSameSeedAndInputsGiveTheSameFight() {
        fun once(seed: Long) = play(fightOf(seed, me(listOf(nb("Cacheon", maxHp = 60))), wild(nb("Bytelet", maxHp = 60))), 3000, every(50, FightInput(move = 1))).map { it.toString() }
        assertEquals(once(7), once(7))
        assertNotEquals(once(7), once(8))
    }

    /** Restored from a snapshot mid-fight, a fight plays on exactly as the original does. */
    @Test
    fun aFightSurvivesASnapshot() {
        val script = every(40, FightInput(move = 2))
        val a = fightOf(11, me(listOf(nb("Cacheon", maxHp = 80), nb("Bytelet", maxHp = 50))), wild(nb("Chirplet", maxHp = 120)))
        play(a, 200, script)
        val b = Fight.restore(a.snapshot())
        val ea = mutableListOf<String>(); val eb = mutableListOf<String>()
        repeat(3000) {
            if (!a.closed) ea += a.step(script(a.tick + 1)).map { it.toString() }
            if (!b.closed) eb += b.step(script(b.tick + 1)).map { it.toString() }
        }
        assertTrue(a.closed, "the fight should play out")
        assertEquals(ea, eb)
    }

    /** Four of the same move in a row teach [Persistence]; four different ones in a row, [Dynamism]. */
    @Test
    fun streaksTeachPersistenceAndDynamism() {
        // hp far over maxHp: they hit softly and never fall
        val same = fightOf(3, me(listOf(nb("Cacheon", maxHp = 40, hp = 99999))), wild(nb("Bytelet", maxHp = 40, hp = 99999)))
        val log = play(same, 60 * 6, every(60, FightInput(move = 1))).logText()
        assertTrue("learned [Persistence]" in log, log)
        assertTrue(same.sides[0].party[0].name.startsWith("[Persistence]"))

        val mixed = fightOf(3, me(listOf(nb("Cacheon", maxHp = 40, hp = 99999, m1 = "A", m2 = "B", m3 = "C"))), wild(nb("Bytelet", maxHp = 40, hp = 99999)))
        var n = 0
        val log2 = play(mixed, 60 * 6) { t -> if (t % 60 == 0L) mapOf(0 to FightInput(move = 1 + n++ % 3)) else emptyMap() }.logText()
        assertTrue("learned [Dynamism]" in log2, log2)
    }

    @Test
    fun herdAndHoardComeFromTheParty() {
        val c = nb("Cacheon")
        assertEquals(0, PartyTraits.herdChance(listOf(c, nb("Cacheon")), c))
        assertEquals(50, PartyTraits.herdChance(listOf(c, nb("[Swift] Cacheon"), nb("Cacheon")), c))
        assertEquals(60, PartyTraits.herdChance(listOf(c, nb("Cacheon"), nb("Cacheon"), nb("Cacheon"), nb("Cacheon")), c))
        assertEquals(false, PartyTraits.hasHoard(List(5) { nb("B$it") }))
        assertEquals(true, PartyTraits.hasHoard(List(6) { nb("B$it") }))
    }

    /** On the battle screen a swap costs a turn (the beast strikes), unless you have a [Hoard]. */
    @Test
    fun aHoardSwapsForFree() {
        fun swap(size: Int): String {
            val f = fightOf(5, me(List(size) { nb("B$it", maxHp = 40, hp = 999) }), wild(nb("Bytelet", maxHp = 40, hp = 999)))
            return play(f, 30) { t -> if (t == 5L) mapOf(0 to FightInput(cage = 2)) else emptyMap() }.logText()
        }
        val hoard = swap(6)
        assertTrue("[Hoard] The swap cost no turn!" in hoard && "ENEMY TURN" !in hoard, hoard)
        val no = swap(5)
        assertTrue("ENEMY TURN" in no, no)
    }

    /** A Flying beast is out of reach of most moves (not anti-air ones); an [Elusive] one dodges some. */
    @Test
    fun flyingAndElusiveBeastsAreHardToHit() {
        fun misses(foe: Netbeast, move: String, line: String): Int {
            val f = fightOf(9, me(listOf(nb("Cacheon", maxHp = 40, hp = 99999, m1 = move))), wild(foe))
            return Regex(Regex.escape(line)).findAll(play(f, 60 * 20, every(60, FightInput(move = 1))).logText()).count()
        }
        val flying = misses(nb("Chirplet", "Flying", 40, 99999), "Glitch", "is out of reach!") // Tackle is anti-air; Glitch isn't
        assertTrue(flying >= 12, "Glitch missed a Flying beast $flying times")
        assertEquals(0, misses(nb("Chirplet", "Flying", 40, 99999), "Ping", "is out of reach!"))
        val dodged = misses(nb("[Elusive] Bytelet", maxHp = 40, hp = 99999), "Tackle", "dodged instantly")
        assertTrue(dodged >= 1, "an Elusive beast dodged $dodged times")
    }

    /** The event boss shrugs off ordinary attacks (an eighth of its health), but its weakness takes half. */
    @Test
    fun theBossHasAWeakness() {
        fun hit(mine: String): String {
            val boss = SideSetup(SideKind.BOSS, listOf(nb("Stratolord", "EventBoss", 800)), weakness = "Zeph")
            return play(fightOf(2, me(listOf(nb(mine, maxHp = 40, hp = 9999))), boss), 30) { t -> if (t == 2L) mapOf(0 to FightInput(move = 1)) else emptyMap() }.logText()
        }
        assertTrue("EXPLOITED WEAKNESS" in hit("Zephyrlet"))
        assertTrue("Thick armor!" in hit("Cartini"))
    }

    @Test
    fun thickSkinSpikesAndVampires() {
        val f = fightOf(4, me(listOf(nb("[Vampiric] [Thick-Skinned] Cacheon", maxHp = 40, hp = 30))), wild(nb("[Spiked] Bytelet", maxHp = 40, hp = 99999)))
        val log = play(f, 60 * 4, every(60, FightInput(move = 1))).logText()
        assertTrue("[Thick-Skinned] reduced damage!" in log, log)
        assertTrue("[Spiked] recoil!" in log, log)
        assertTrue("[Vampiric] stole" in log, log)
    }

    /** With no netbeasts you punch; the beast comes for you on its own clock and you're beaten. */
    @Test
    fun aLastStandIsLost() {
        val f = fightOf(6, me(emptyList()), wild(nb("Bytelet", maxHp = 40, hp = 99999)))
        val evs = play(f, 400, every(10, FightInput(move = 1)))
        val log = evs.logText()
        assertTrue(evs.any { it is FightEvent.LastStand }, "it's fists from the start")
        assertTrue(log.contains("👊"), log)
        assertTrue("obliterates you" in log, log)
        assertEquals(FightOutcome.BEATEN, f.result?.outcomes?.get(0))
        assertTrue(f.closed && evs.last() == FightEvent.Closed)
    }

    /** ...unless you carry the Sword of the Spirit: one swing and it's over. */
    @Test
    fun theSwordWinsALastStand() {
        val f = fightOf(6, SideSetup(SideKind.PLAYER, sword = true), wild(nb("Bytelet", maxHp = 40, hp = 99999)))
        play(f, 200) { t -> if (t == 3L) mapOf(0 to FightInput(move = 1)) else emptyMap() }
        assertEquals(FightOutcome.WON, f.result?.outcomes?.get(0))
    }

    /** Wait in the Wilds and the beast loses patience: it knocks a cage loose and gets a free hit. */
    @Test
    fun theWildsBeastLosesPatience() {
        val f = fightOf(8, me(listOf(nb("Cacheon", maxHp = 60))), wild(nb("Bytelet", maxHp = 60, hp = 9999)), style = FightStyle.WILDS)
        val evs = play(f, Fight.PATIENCE + 200)
        val log = evs.logText()
        assertTrue("cage is knocked from your hands" in log, log)
        assertTrue(evs.any { it is FightEvent.SentOut && it.side == 0 })
        assertTrue("ENEMY TURN" in log, "the free hit")
    }

    /** A net can catch a wild beast (not a boss); a catch ends the fight with it in your party. */
    @Test
    fun aNetCatchesWildBeasts() {
        var caught = 0
        for (seed in 1L..20L) {
            val pet = nb("Cacheon", maxHp = 40, hp = 9999).also { it.eqNets = 5 }
            val f = fightOf(seed, me(listOf(pet)), wild(nb("Bytelet", maxHp = 40, hp = 9999)))
            val evs = play(f, 600, every(60, FightInput(item = FightItem.NET)))
            if (evs.any { it is FightEvent.Caught }) { caught++; assertEquals(FightOutcome.CAUGHT, f.result?.outcomes?.get(0)) }
        }
        assertTrue(caught in 3..19, "about a third of nets hold: $caught of 20 fights")
    }

    /**
     * Two players and a wild beast. It goes for whoever provoked it, or the other one; whoever
     * lands the killing blow catches it (multiplayer), and the other player is in it to the end.
     */
    @Test
    fun threeSidesFightItOut() {
        val f = threeSided(21)
        val evs = play(f, 4000, threeSidedScript)
        val log = evs.logText()
        assertTrue("Cacheon takes" in log && "Chirplet takes" in log, "the beast should go for both: $log")
        val r = f.result!!
        // the beast's own side reads CAUGHT too (it was caught); one player caught it
        val winner = r.outcomes.entries.filter { it.key != 2 }.single { it.value == FightOutcome.CAUGHT }.key
        assertEquals(winner, r.lastHit)
        assertEquals(FightOutcome.LEFT, r.outcomes[1 - winner])
        assertEquals(FightOutcome.CAUGHT, r.outcomes[2])
        assertTrue(evs.any { it is FightEvent.Caught && it.side == winner })
    }

    @Test
    fun theCatchTableMatchesTheOldCurve() {
        // 15 + int(25 * e^(-0.05 n)), as engines/combat worked it out
        val expected = listOf(40, 38, 37, 36, 35, 34, 33, 32, 31, 30, 30)
        assertEquals(expected, (0..10).map { Fight.catchBase(it) })
        assertEquals(15, Fight.catchBase(500))
    }
}

/** The fight in [FightTest.threeSidesFightItOut] and DeterminismTest's pin of it. */
internal fun threeSided(seed: Long) = fightOf(seed,
    SideSetup(SideKind.PLAYER, listOf(nb("Cacheon", maxHp = 60, hp = 200)), ownerId = "ana"),
    SideSetup(SideKind.PLAYER, listOf(nb("Chirplet", maxHp = 50, hp = 200, m1 = "Ping")), ownerId = "ben"),
    SideSetup(SideKind.WILD, listOf(nb("Stratolord", "Wild", maxHp = 60, hp = 400))),
    killingBlowCaptures = true)

/** Ana shouts a move every 50 ticks, Ben every 70, each at the beast. */
internal val threeSidedScript: (Long) -> Map<Int, FightInput> = { t ->
    buildMap {
        if (t % 50 == 0L) put(0, FightInput(move = 1, target = 2))
        if (t % 70 == 0L) put(1, FightInput(move = 1, target = 2))
    }
}
