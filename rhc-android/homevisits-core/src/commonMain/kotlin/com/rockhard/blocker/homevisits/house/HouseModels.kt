package com.rockhard.blocker.homevisits.house

import com.rockhard.blocker.homevisits.HomeClock
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

typealias SpotId = String
typealias RoomId = String
typealias ItemId = String

enum class SpotKind { START, FRONT_DOOR, BENCH, TABLE, SEAT, BED, WALL, FLOOR }
enum class RoomKind { ENTRY, KITCHEN, DINING, LOUNGE, BEDROOM, HALL, STUDY, OUTSIDE, OTHER } // never a bathroom
enum class SurfaceKind { FLOOR, BENCH, TABLE, SEAT, WALL }

data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Float) = Vec3(x * k, y * k, z * k)
    val length: Float get() = sqrt(x * x + y * y + z * z)
    /** Distance across the floor, ignoring height. */
    fun flat(o: Vec3): Float { val dx = x - o.x; val dz = z - o.z; return sqrt(dx * dx + dz * dz) }
}

/**
 * A position and a turn about the vertical (y is up). As a frame: its origin is
 * (x, y, z) and its +z points along yaw, so yaw 0 is the outer frame's +z, and
 * an object at yaw faces (sin yaw, 0, cos yaw). The same convention as the AR
 * kit's figures.
 */
data class Pose4(val x: Float, val y: Float, val z: Float, val yaw: Float) {
    val position: Vec3 get() = Vec3(x, y, z)

    /** A point given in this frame, in the frame this pose is given in. */
    fun toOuter(p: Vec3): Vec3 {
        val c = cos(yaw); val s = sin(yaw)
        return Vec3(x + c * p.x + s * p.z, y + p.y, z - s * p.x + c * p.z)
    }

    /** A point given in the outer frame, in this frame. */
    fun toInner(p: Vec3): Vec3 {
        val c = cos(yaw); val s = sin(yaw)
        val dx = p.x - x; val dz = p.z - z
        return Vec3(c * dx - s * dz, p.y - y, s * dx + c * dz)
    }

    /** `inner` (given in this frame), in the outer frame. */
    fun then(inner: Pose4): Pose4 {
        val p = toOuter(inner.position)
        return Pose4(p.x, p.y, p.z, angle(yaw + inner.yaw))
    }

    /** This frame's outer frame, seen from this one. */
    fun inverse(): Pose4 {
        val o = toInner(Vec3(0f, 0f, 0f))
        return Pose4(o.x, o.y, o.z, angle(-yaw))
    }

    companion object {
        val ORIGIN = Pose4(0f, 0f, 0f, 0f)
        /** An angle folded into −π..π. */
        fun angle(a: Float): Float = atan2(sin(a), cos(a))
        /** The yaw that faces from `from` towards `to`. */
        fun facing(from: Vec3, to: Vec3): Float = atan2(to.x - from.x, to.z - from.z)
    }
}

data class Surface(val kind: SurfaceKind, val centre: Pose4, val halfWidth: Float, val halfDepth: Float)

data class Spot(
    val id: SpotId,
    val kind: SpotKind,
    val room: RoomId?,           // null until the story names it
    val storey: Int,             // 0 is the front door's storey
    val floorY: Float,           // the floor, in this spot's frame
    val surfaces: List<Surface>, // in this spot's frame
    val made: HomeClock,
    val lastConfirmed: HomeClock,
)

data class Room(val id: RoomId, val kind: RoomKind, val name: String) // as the story put it: "your bedroom"

data class Walk(
    val from: SpotId, val to: SpotId, val at: HomeClock, val seconds: Int,
    val path: List<Vec3>,        // in from's frame, a point about every 25 cm
    val gaps: List<IntRange>,    // stretches of the path where tracking was lost
    val climb: Float,            // net change in height
    val toInFrom: Pose4?,        // where `to`'s frame is, seen from `from`'s; null if it can't be told
)

data class Link(
    val a: SpotId, val b: SpotId,
    val walks: Int, val lastWalked: HomeClock,
    val path: List<Vec3>,        // the best walk's path, in a's frame
    val bInA: Pose4?,            // where b is, seen from a (averaged over walks); null if unknown
    val length: Float,
    val rough: Boolean,          // no gap-free walk yet
    val blockedUntil: HomeClock?,
    val climb: Float = 0f,       // from a to b
    val blockedTillWalked: Boolean = false, // blocked three times in two weeks (rule 6)
    val blocks: List<Int> = emptyList(),    // the days it was said to be blocked
) {
    fun joins(x: SpotId, y: SpotId) = (a == x && b == y) || (a == y && b == x)
    fun other(s: SpotId) = if (s == a) b else a
}

data class Placement(val item: ItemId, val spot: SpotId, val pose: Pose4, val on: SurfaceKind)

/** One leg of a route: walk `path` (in `from`'s frame) from `from` to `to`. */
data class Leg(
    val from: SpotId,
    val to: SpotId,
    val path: List<Vec3>,
    /** The walk had gaps: visitors meet you at `to` instead of walking it (rule 5). */
    val rough: Boolean,
    /** Two spots in the same room: reachable, but there's no path to follow (rule 7). */
    val inRoom: Boolean = false,
)

data class Route(val legs: List<Leg>, val cost: Float) {
    val stops: List<SpotId> get() = if (legs.isEmpty()) emptyList() else listOf(legs.first().from) + legs.map { it.to }
}

/** A walk is a storey change when it climbs or falls more than this (rule 8). */
const val STOREY_CLIMB = 2f
/** The longest walk that counts (rule 1). */
const val MAX_WALK_SECONDS = 180
