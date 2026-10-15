package com.rockhard.blocker.homevisits.story

import com.rockhard.blocker.homevisits.HomeClock
import com.rockhard.blocker.homevisits.house.HouseMap
import com.rockhard.blocker.homevisits.house.MAX_WALK_SECONDS
import com.rockhard.blocker.homevisits.house.Pose4
import com.rockhard.blocker.homevisits.house.RoomId
import com.rockhard.blocker.homevisits.house.Route
import com.rockhard.blocker.homevisits.house.Spot
import com.rockhard.blocker.homevisits.house.SpotId
import com.rockhard.blocker.homevisits.house.SpotKind
import com.rockhard.blocker.homevisits.house.Surface
import com.rockhard.blocker.homevisits.house.SurfaceKind
import com.rockhard.blocker.homevisits.house.Vec3
import com.rockhard.blocker.homevisits.house.Walk

/**
 * What the app saw while beats ran. Times are seconds on the app's session
 * clock (monotonic; the core never reads a clock), poses are in the AR session
 * frame.
 */
sealed interface Observation {
    /** About five times a second. */
    data class Camera(val t: Float, val pose: Pose4, val tracking: Boolean) : Observation
    /** A call, the screen going off, the app leaving the front. */
    data class Paused(val t: Float) : Observation
    /** Back in front. `sameFrame` when ARCore recovered tracking in the frame it had. */
    data class Resumed(val t: Float, val sameFrame: Boolean) : Observation
}

/**
 * Turns observations into walks and spots through [HouseMap] (03's rule 1).
 *
 * It knows where each spot's frame is in this session (`frames`). A walk runs
 * from the last spot the player was confirmed at: it starts when they're 1.5 m
 * from it, records a point every 25 cm in that spot's frame, notes where
 * tracking dropped out, and counts only if the app stayed in front, it took at
 * most three minutes, and tracking was good at both ends.
 */
class SessionLog(val map: HouseMap) {
    private val frames = LinkedHashMap<SpotId, Pose4>()

    /** The spot the current walk starts from, if a walk can start (null after a pause). */
    var lastSpot: SpotId? = null
        private set

    private var lastCamera: Observation.Camera? = null
    private var walk: Draft? = null

    /** Walks recorded and walks thrown away this session, for the debug report and tests. */
    var walksRecorded = 0
        private set
    var walksThrownAway = 0
        private set

    private class Draft(val from: SpotId, val fromFrame: Pose4) {
        var startT = -1f
        val path = ArrayList<Vec3>()
        val gaps = ArrayList<IntRange>()
        var gapStart = -1
        var broken = false
        val started get() = startT >= 0f
    }

    val camera: Pose4? get() = lastCamera?.pose
    val tracking: Boolean get() = lastCamera?.tracking == true

    fun frameOf(spot: SpotId): Pose4? = frames[spot]
    fun framed(spot: SpotId) = spot in frames
    val framedSpots: Set<SpotId> get() = frames.keys

    fun observe(o: Observation) {
        when (o) {
            is Observation.Camera -> camera(o)
            is Observation.Paused -> {
                walk?.let { if (it.started) walksThrownAway++ }
                walk = null
                lastSpot = null
            }
            is Observation.Resumed -> if (!o.sameFrame) frames.clear()
        }
    }

    private fun camera(o: Observation.Camera) {
        lastCamera = o
        val w = walk ?: return
        val local = w.fromFrame.toInner(o.pose.position)
        if (!w.started) {
            if (!o.tracking) return
            if (local.flat(Vec3(0f, 0f, 0f)) < START_DISTANCE) return
            w.startT = o.t
            w.path.add(Vec3(0f, 0f, 0f))
        }
        if (o.t - w.startT > MAX_WALK_SECONDS) w.broken = true
        if (w.broken) return
        if (!o.tracking) {
            if (w.gapStart < 0) w.gapStart = w.path.size - 1
            return
        }
        if (w.gapStart >= 0) {
            // Back from a gap: jump straight to here, and mark the stretch as rough.
            w.path.add(local)
            w.gaps.add(w.gapStart until w.path.size)
            w.gapStart = -1
            return
        }
        // A point every 25 cm, between reports if the player moved further than that.
        while (local.flat(w.path.last()) >= POINT_SPACING) {
            val last = w.path.last()
            val k = POINT_SPACING / local.flat(last)
            w.path.add(last + (local - last) * k)
        }
    }

    /**
     * The player is confirmed at a known spot (they tapped arrive, or tapped a
     * surface there). Records the walk that got them here, if it counts, and
     * makes this spot the start of the next one. `pose` is the camera, and puts
     * the spot's frame back if it was lost.
     */
    fun arrive(spot: SpotId, now: HomeClock, pose: Pose4? = null): Walk? {
        if (spot !in frames && pose != null) frames[spot] = pose
        val recorded = finishWalk(spot, now)
        map.confirm(spot, now)
        startFrom(spot)
        return recorded
    }

    /** A new spot, made where the camera is (its frame), then arrived at. */
    fun makeSpot(
        kind: SpotKind,
        room: RoomId?,
        camera: Pose4,
        now: HomeClock,
        floorY: Float? = null,
        surfaces: List<Pair<SurfaceKind, SurfaceTap>> = emptyList(),
    ): Spot {
        val from = lastSpot
        val climb = from?.let { f -> frames[f]?.let { camera.y - it.y } } ?: 0f
        val storey = if (from != null) map.storeyAfter(from, climb) else 0
        val frame = Pose4(camera.x, camera.y, camera.z, camera.yaw)
        val spot = map.addSpot(
            kind, now, room, storey,
            floorY = (floorY ?: (camera.y - PHONE_HEIGHT)) - camera.y,
            surfaces = surfaces.map { (k, tap) -> surfaceIn(frame, k, tap) },
        )
        frames[spot.id] = frame
        arrive(spot.id, now)
        return map.spot(spot.id)!!
    }

    fun addSurface(spot: SpotId, kind: SurfaceKind, tap: SurfaceTap) {
        val f = frames[spot] ?: return
        map.addSurface(spot, surfaceIn(f, kind, tap))
    }

    fun setFloor(spot: SpotId, floorY: Float) {
        val f = frames[spot] ?: return
        map.setFloor(spot, floorY - f.y)
    }

    /** `pose` (session) in `spot`'s frame. */
    fun toSpot(spot: SpotId, pose: Pose4): Pose4? = frames[spot]?.let { f -> f.inverse().then(pose) }

    /** `inSpot` (in `spot`'s frame) in the session. */
    fun toSession(spot: SpotId, inSpot: Pose4): Pose4? = frames[spot]?.then(inSpot)

    /**
     * Puts a lost spot back from one tap (AlignByTap): `tapped` is where the
     * player says `knownInSpot` is now. The wall's direction gives the yaw.
     */
    fun align(spot: SpotId, knownInSpot: Pose4, tapped: Pose4) {
        frames[spot] = tapped.then(knownInSpot.inverse())
    }

    /** Starts a fresh session at the first good tracking pose, with a START spot (first visit only). */
    fun startHere(now: HomeClock): Spot? {
        val c = lastCamera ?: return null
        if (!c.tracking) return null
        return makeSpot(SpotKind.START, null, c.pose, now)
    }

    /**
     * A route as one path in the session frame, for the arrow. Legs with no
     * path (a same-room hop) aim at the next spot. Stops at the first leg whose
     * frame isn't known this session; null if not even the first is.
     */
    fun sessionPath(route: Route): List<Vec3>? {
        val out = ArrayList<Vec3>()
        for (leg in route.legs) {
            val f = frames[leg.from] ?: break
            if (leg.path.isEmpty()) {
                val to = frames[leg.to] ?: break
                out.add(to.position)
            } else leg.path.forEach { out.add(f.toOuter(it)) }
        }
        return out.ifEmpty { null }
    }

    /** One leg's path in the session frame, or null if its frame isn't known or it has none. */
    fun sessionPath(from: SpotId, path: List<Vec3>): List<Vec3>? {
        if (path.isEmpty()) return null
        val f = frames[from] ?: return null
        return path.map { f.toOuter(it) }
    }

    private fun startFrom(spot: SpotId) {
        val f = frames[spot]
        lastSpot = if (f != null) spot else null
        walk = if (f != null) Draft(spot, f) else null
    }

    private fun finishWalk(to: SpotId, now: HomeClock): Walk? {
        val w = walk ?: return null
        walk = null
        if (!w.started) return null // never left the last spot: no walk
        val cam = lastCamera
        val toFrame = frames[to]
        val ok = !w.broken && cam != null && cam.tracking && w.gapStart < 0 && toFrame != null && w.from != to &&
            cam.t - w.startT <= MAX_WALK_SECONDS
        if (!ok) { walksThrownAway++; return null }
        val end = w.fromFrame.toInner(cam!!.pose.position)
        if (w.path.last().flat(end) > 0.05f) w.path.add(end)
        val walk = Walk(
            from = w.from, to = to, at = now, seconds = (cam.t - w.startT).toInt(),
            path = w.path.toList(), gaps = w.gaps.toList(),
            climb = w.fromFrame.toInner(toFrame!!.position).y,
            toInFrom = w.fromFrame.inverse().then(toFrame),
        )
        return if (map.recordWalk(walk) != null) { walksRecorded++; walk } else { walksThrownAway++; null }
    }

    private fun surfaceIn(frame: Pose4, kind: SurfaceKind, tap: SurfaceTap): Surface =
        Surface(kind, frame.inverse().then(tap.pose), tap.halfWidth, tap.halfDepth)

    companion object {
        /** A walk starts when the player is this far from the spot they left (rule 1). */
        const val START_DISTANCE = 1.5f
        /** A point about every 25 cm. */
        const val POINT_SPACING = 0.25f
        /** Where the floor is guessed to be when it wasn't seen: the phone held up in front. */
        const val PHONE_HEIGHT = 1.4f
    }
}
