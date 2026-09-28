package com.rockhard.blocker.homevisits.house

import com.rockhard.blocker.homevisits.HomeClock

typealias SpotId = String
typealias RoomId = String
typealias ItemId = String

enum class SpotKind { START, FRONT_DOOR, BENCH, TABLE, SEAT, BED, WALL, FLOOR }
enum class RoomKind { ENTRY, KITCHEN, DINING, LOUNGE, BEDROOM, HALL, STUDY, OUTSIDE, OTHER } // never a bathroom
enum class SurfaceKind { FLOOR, BENCH, TABLE, SEAT, WALL }

data class Pose4(val x: Float, val y: Float, val z: Float, val yaw: Float)
data class Surface(val kind: SurfaceKind, val centre: Pose4, val halfWidth: Float, val halfDepth: Float)
data class Vec3(val x: Float, val y: Float, val z: Float)

data class Spot(
    val id: SpotId,
    val kind: SpotKind,
    val room: RoomId?,           // null until the story names it
    val storey: Int,             // 0 is the front door's storey
    val floorY: Float,           // the floor, in this spot's frame
    val surfaces: List<Surface>, // in this spot's frame
    val made: HomeClock,
    val lastConfirmed: HomeClock
)

data class Room(val id: RoomId, val kind: RoomKind, val name: String)

data class Walk(
    val from: SpotId, val to: SpotId, val at: HomeClock, val seconds: Int,
    val path: List<Vec3>,        // in from's frame, a point about every 25 cm
    val gaps: List<IntRange>,    // stretches of the path where tracking was lost
    val climb: Float             // net change in height
)

data class Link(
    val a: SpotId, val b: SpotId,
    val walks: Int, val lastWalked: HomeClock,
    val path: List<Vec3>,        // the best walk's path, in a's frame
    val bInA: Pose4?,            // where b is, seen from a (averaged over walks); null if unknown
    val length: Float,
    val rough: Boolean,          // no gap-free walk yet
    val blockedUntil: HomeClock?
)

data class Placement(val item: ItemId, val spot: SpotId, val pose: Pose4, val on: SurfaceKind)

// Basic interface for the HouseMap which enforces mapping rules
interface HouseMap {
    val spots: List<Spot>
    val rooms: List<Room>
    val links: List<Link>
    val placements: List<Placement>

    fun addSpot(spot: Spot)
    fun nameRoom(roomId: RoomId, name: String)
    fun recordWalk(walk: Walk)
    fun block(link: Link, until: HomeClock)
    fun place(placement: Placement)
    fun move(item: ItemId, newPlacement: Placement)
    fun remove(item: ItemId)
}
