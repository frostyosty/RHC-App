package com.rockhard.blocker.homevisits.story

import com.rockhard.blocker.homevisits.HomeClock
import com.rockhard.blocker.homevisits.house.HouseMap
import com.rockhard.blocker.homevisits.house.Pose4
import com.rockhard.blocker.homevisits.house.SpotKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionLogTest {
    private val now = HomeClock(3, 900)
    private var t = 0f

    /** Walks in a straight line along +z from z0 to z1, 1 m/s, five reports a second. */
    private fun SessionLog.walkZ(z0: Float, z1: Float, tracking: (Float) -> Boolean = { true }) {
        val n = ((z1 - z0) / 0.2f).toInt()
        for (i in 0..n) {
            t += 0.2f
            val z = z0 + (z1 - z0) * i / n
            observe(Observation.Camera(t, Pose4(0f, 1.4f, z, 0f), tracking(z)))
        }
    }

    private fun setup(): Pair<SessionLog, String> {
        val log = SessionLog(HouseMap())
        log.observe(Observation.Camera(0f, Pose4(0f, 1.4f, 0f, 0f), true))
        val a = log.makeSpot(SpotKind.FRONT_DOOR, null, log.camera!!, now)
        return log to a.id
    }

    @Test
    fun anUnbrokenWalkMakesALink() {
        val (log, a) = setup()
        log.walkZ(0f, 6f)
        val b = log.makeSpot(SpotKind.BENCH, null, log.camera!!, now)
        val link = log.map.link(a, b.id)
        assertNotNull(link)
        assertTrue(!link.rough)
        assertTrue(link.path.size >= 6 * 3, "a point about every 25 cm: ${link.path.size}")
        assertTrue((link.bInA!!.position.z - 6f) < 0.01f)
        assertEquals(1, log.walksRecorded)
    }

    @Test
    fun aPauseThrowsTheWalkAway() {
        val (log, a) = setup()
        log.walkZ(0f, 3f)
        log.observe(Observation.Paused(t)); t += 20f
        log.observe(Observation.Resumed(t, sameFrame = true))
        log.walkZ(3f, 6f)
        val b = log.makeSpot(SpotKind.BENCH, null, log.camera!!, now)
        assertNull(log.map.link(a, b.id))
        assertTrue(log.map.links.isEmpty())
    }

    @Test
    fun anOverLongWalkIsThrownAway() {
        val (log, a) = setup()
        log.walkZ(0f, 2f)
        t += 200f // stood about for over three minutes
        log.walkZ(2f, 4f)
        val b = log.makeSpot(SpotKind.BENCH, null, log.camera!!, now)
        assertNull(log.map.link(a, b.id))
    }

    @Test
    fun aShortShuffleIsNoWalk() {
        val (log, a) = setup()
        log.walkZ(0f, 1f)
        val b = log.makeSpot(SpotKind.BENCH, null, log.camera!!, now)
        assertNull(log.map.link(a, b.id), "never 1.5 m from the door")
    }

    @Test
    fun aGapMakesItRough() {
        val (log, a) = setup()
        log.walkZ(0f, 6f) { z -> z !in 2.5f..3.5f }
        val b = log.makeSpot(SpotKind.BENCH, null, log.camera!!, now)
        val link = log.map.link(a, b.id)!!
        assertTrue(link.rough)
    }

    @Test
    fun lostTrackingAtTheEndThrowsItAway() {
        val (log, a) = setup()
        log.walkZ(0f, 6f) { z -> z < 5.5f }
        val b = log.makeSpot(SpotKind.BENCH, null, log.camera!!, now)
        assertNull(log.map.link(a, b.id))
    }

    @Test
    fun aFreshFrameLosesSpotsUntilAligned() {
        val (log, a) = setup()
        log.observe(Observation.Paused(t))
        log.observe(Observation.Resumed(t, sameFrame = false))
        assertTrue(!log.framed(a))
        // The player taps the door; it was 0.6 m ahead of the spot, facing back.
        log.align(a, Pose4(0f, 0f, 0.6f, 3.14159f), Pose4(5f, 1.2f, 5.6f, 3.14159f))
        val f = log.frameOf(a)!!
        assertTrue(f.position.flat(com.rockhard.blocker.homevisits.house.Vec3(5f, 0f, 5f)) < 0.01f, "got $f")
    }
}
