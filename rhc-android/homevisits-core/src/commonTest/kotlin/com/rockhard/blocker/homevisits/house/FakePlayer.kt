package com.rockhard.blocker.homevisits.house

import com.rockhard.blocker.homevisits.story.Observation
import kotlin.math.PI
import kotlin.random.Random

/**
 * Someone walking a [FakeHouse] with their phone up. It reports what ARCore
 * would: the camera pose in the session's frame, about five times a second,
 * with the problems a real player has:
 * - drift of 1–2% of the distance walked;
 * - tracking drop-outs, more often in dark rooms;
 * - pauses (a call), after which ARCore may start a fresh frame.
 *
 * Truth (`pos`, `yaw`) is in the plan's frame; the session frame is
 * `frame` (where the session's origin is in the plan) plus `drift`.
 */
class FakePlayer(val house: FakeHouse, val rnd: Random, private val sink: (Observation) -> Unit) {
    var cell: Cell = house.find('D')!!
        private set
    var yaw = 0f
        private set
    var t = 0f
        private set
    private var frame = Pose4(0f, 0f, 0f, 0f)
    private var drift = Vec3(0f, 0f, 0f)
    private var driftRate = 0.01f + rnd.nextFloat() * 0.01f
    private var lostUntil = -1f
    /** Metres walked, all told. */
    var walked = 0f
        private set

    val pos: Vec3 get() = eye(cell)
    fun eye(c: Cell): Vec3 = house.world(c) + Vec3(0f, EYE, 0f)

    fun placeAt(c: Cell, facing: Float = 0f) { cell = c; yaw = facing }

    /** A pose in the plan, as the session sees it. */
    fun toSession(p: Pose4): Pose4 {
        val s = frame.inverse().then(p)
        return Pose4(s.x + drift.x, s.y + drift.y, s.z + drift.z, s.yaw)
    }

    /** A session pose, back in the plan (undoing the drift as it is now). */
    fun toPlan(p: Pose4): Pose4 = frame.then(Pose4(p.x - drift.x, p.y - drift.y, p.z - drift.z, p.yaw))

    val tracking: Boolean get() = t >= lostUntil

    fun camera(): Pose4 = toSession(Pose4(pos.x, pos.y, pos.z, yaw))

    private fun emit() = sink(Observation.Camera(t, camera(), tracking))

    fun stand(seconds: Float) {
        var left = seconds
        while (left > 0f) { t += TICK; left -= TICK; emit() }
    }

    fun face(target: Cell) { yaw = Pose4.facing(pos, eye(target)) }

    /** Walks to `to` the shortest way (or round by `via` first); false if there's no way. */
    fun walkTo(to: Cell, via: Cell? = null): Boolean {
        val legs = if (via != null) listOf(via, to) else listOf(to)
        for (target in legs) {
            val path = house.path(cell, target) ?: return false
            for (i in 1 until path.size) step(path[i])
        }
        return true
    }

    private fun step(next: Cell) {
        val a = pos; val b = eye(next)
        val d = (b - a).length
        if (d > 0.01f) yaw = Pose4.facing(a, b)
        // Drift creeps in with distance, in a slowly turning direction.
        val dir = rnd.nextFloat() * 2f * PI.toFloat()
        drift = drift + Vec3(kotlin.math.sin(dir), 0f, kotlin.math.cos(dir)) * (d * driftRate)
        walked += d
        val dark = house.room(next) in house.dark
        if (tracking && rnd.nextFloat() < if (dark) 0.08f else 0.004f) lostUntil = t + 1f + rnd.nextFloat() * 2f
        // About 1 m/s: one tick per 20 cm.
        val ticks = maxOf(1, (d / (WALK_SPEED * TICK)).toInt())
        for (k in 1..ticks) {
            t += TICK
            val f = k.toFloat() / ticks
            val p = a + (b - a) * f
            sink(Observation.Camera(t, toSession(Pose4(p.x, p.y, p.z, yaw)), tracking))
        }
        cell = next
    }

    /** A call: nothing is reported while they wander to `to`, then ARCore resumes (or starts a fresh frame). */
    fun interrupt(to: Cell?, sameFrame: Boolean) {
        sink(Observation.Paused(t))
        t += 30f
        if (to != null) house.path(cell, to)?.let { cell = to }
        if (!sameFrame) newFrame()
        sink(Observation.Resumed(t, sameFrame))
        stand(1f)
    }

    /** ARCore starts a session here: its origin is the camera, turned whichever way the phone faced. */
    fun newFrame() {
        frame = Pose4(pos.x, pos.y, pos.z, yaw)
        drift = Vec3(0f, 0f, 0f)
    }

    companion object {
        const val EYE = 1.4f
        const val TICK = 0.2f
        const val WALK_SPEED = 1f
    }
}
