package com.rockhard.blocker.homevisits.mind

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The 90-day soak with the kind, gossip and random players. */
class SoakTest {
    private val runs = SoakPlayer.entries.associateWith { MindSoak.run(42L, it) }

    private fun friends(r: SoakResult) = TestCast.cast.ids.count { r.mind.aff(it, you) >= Labels.FRIENDS }

    @Test
    fun nothingGoesOutOfRange() {
        for (r in runs.values) assertEquals(emptyList(), r.problems, "${r.player}")
    }

    @Test
    fun relationshipsSpreadOutRatherThanAllEndingTheSame() {
        for (r in runs.values) {
            val affs = r.history.last().values.map { it.affinity }
            val mean = affs.average()
            val sd = sqrt(affs.sumOf { (it - mean) * (it - mean) } / affs.size)
            assertTrue(sd > 10.0, "${r.player}: affinities bunched up (sd $sd)")
            val labels = r.history.last().keys.map { k -> r.mind.label(k.substringBefore('>'), Who.of(k.substringAfter('>'))) }.toSet()
            assertTrue(labels.size >= 3, "${r.player}: only $labels")
        }
    }

    @Test
    fun theKindPlayerEndsUpWithMoreFriendsThanTheGossip() {
        val kind = runs.getValue(SoakPlayer.KIND)
        val gossip = runs.getValue(SoakPlayer.GOSSIP)
        assertTrue(friends(kind) > friends(gossip), "kind ${friends(kind)} vs gossip ${friends(gossip)}")
    }

    @Test
    fun theSameSeedGivesTheSameHistory() {
        for (p in SoakPlayer.entries) {
            assertEquals(runs.getValue(p).hash, MindSoak.run(42L, p).hash, "$p")
            assertEquals(runs.getValue(p).log, MindSoak.run(42L, p).log, "$p")
        }
        assertNotEquals(runs.getValue(SoakPlayer.RANDOM).hash, MindSoak.run(43L, SoakPlayer.RANDOM).hash)
    }

    @Test
    fun theLogSaysWhatHappenedEachDay() {
        val log = runs.getValue(SoakPlayer.KIND).log
        assertEquals(90, log.size)
        assertTrue(log.any { "came round" in it })
    }
}
