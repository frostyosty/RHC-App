package com.rockhard.blocker.homevisits.house

import com.rockhard.blocker.homevisits.HomeClock
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The home as the game knows it. Everything that changes the map goes through
 * here, and this class enforces 03's rules: links only come from unbroken walks,
 * backwards is the same link, distance never makes a route, rough links are
 * flagged, blocked ways stay blocked, and spots in one room reach each other
 * without a path.
 *
 * Maps keep insertion order, so routes and saves come out the same every run.
 */
class HouseMap(
    spots: List<Spot> = emptyList(),
    rooms: List<Room> = emptyList(),
    links: List<Link> = emptyList(),
    placements: List<Placement> = emptyList(),
    /** The next number `newSpotId` and `addRoom` hand out. */
    var nextId: Int = 1,
) {
    private val spotMap = LinkedHashMap<SpotId, Spot>().apply { spots.forEach { put(it.id, it) } }
    private val roomMap = LinkedHashMap<RoomId, Room>().apply { rooms.forEach { put(it.id, it) } }
    private val linkList = links.toMutableList()
    private val placementMap = LinkedHashMap<ItemId, Placement>().apply { placements.forEach { put(it.item, it) } }

    val spots: List<Spot> get() = spotMap.values.toList()
    val rooms: List<Room> get() = roomMap.values.toList()
    val links: List<Link> get() = linkList.toList()
    val placements: List<Placement> get() = placementMap.values.toList()

    fun spot(id: SpotId): Spot? = spotMap[id]
    fun room(id: RoomId): Room? = roomMap[id]
    fun placement(item: ItemId): Placement? = placementMap[item]
    fun link(x: SpotId, y: SpotId): Link? = linkList.firstOrNull { it.joins(x, y) }
    fun spotsOfKind(kind: SpotKind): List<Spot> = spotMap.values.filter { it.kind == kind }
    fun roomOfKind(kind: RoomKind): Room? = roomMap.values.firstOrNull { it.kind == kind }

    // ---- Changing it ----

    fun newSpotId(): SpotId = "s${nextId++}"

    fun addSpot(
        kind: SpotKind,
        at: HomeClock,
        room: RoomId? = null,
        storey: Int = 0,
        floorY: Float = 0f,
        surfaces: List<Surface> = emptyList(),
    ): Spot {
        require(room == null || room in roomMap) { "unknown room $room" }
        val s = Spot(newSpotId(), kind, room, storey, floorY, surfaces, at, at)
        spotMap[s.id] = s
        return s
    }

    /** A room the story named ("your bedroom"). Rule 9: the game never guesses one. */
    fun addRoom(kind: RoomKind, name: String): Room {
        val r = Room("r${nextId++}", kind, name)
        roomMap[r.id] = r
        return r
    }

    /** Puts a spot in a room, when the story or the player said so. */
    fun nameRoom(spot: SpotId, room: RoomId) {
        val s = spotMap[spot] ?: return
        require(room in roomMap) { "unknown room $room" }
        spotMap[spot] = s.copy(room = room)
    }

    fun addSurface(spot: SpotId, surface: Surface) {
        val s = spotMap[spot] ?: return
        spotMap[spot] = s.copy(surfaces = s.surfaces + surface)
    }

    fun setFloor(spot: SpotId, floorY: Float) {
        val s = spotMap[spot] ?: return
        spotMap[spot] = s.copy(floorY = floorY)
    }

    fun confirm(spot: SpotId, at: HomeClock) {
        val s = spotMap[spot] ?: return
        spotMap[spot] = s.copy(lastConfirmed = at)
    }

    /** The storey a walk from `from` that climbed `climb` ends on (rule 8). */
    fun storeyAfter(from: SpotId, climb: Float): Int {
        val base = spotMap[from]?.storey ?: 0
        return base + when {
            climb > STOREY_CLIMB -> 1
            climb < -STOREY_CLIMB -> -1
            else -> 0
        }
    }

    /**
     * Rules 1–3 and 5: adds an unbroken walk to the map. The caller (SessionLog)
     * has already thrown away walks that were paused or lost tracking at an end;
     * this still refuses anything that can't be a walk. Walking a link the other
     * way is the same link. A gap-free walk replaces a rough path. Walking a way
     * that was blocked unblocks it. Returns the link, or null if the walk was
     * refused.
     */
    fun recordWalk(walk: Walk): Link? {
        if (walk.from == walk.to) return null
        if (walk.from !in spotMap || walk.to !in spotMap) return null
        if (walk.seconds < 0 || walk.seconds > MAX_WALK_SECONDS) return null
        if (walk.path.size < 2) return null
        val gapFree = walk.gaps.isEmpty()
        val i = linkList.indexOfFirst { it.joins(walk.from, walk.to) }
        if (i < 0) {
            val link = Link(
                a = walk.from, b = walk.to, walks = 1, lastWalked = walk.at,
                path = walk.path, bInA = walk.toInFrom, length = pathLength(walk.path),
                rough = !gapFree, blockedUntil = null, climb = walk.climb,
            )
            linkList.add(link)
            return link
        }
        val old = linkList[i]
        val forwards = old.a == walk.from
        // This walk's idea of where b is, seen from a.
        val bInA: Pose4? = when {
            walk.toInFrom == null -> null
            forwards -> walk.toInFrom
            else -> walk.toInFrom.inverse()
        }
        // The path in a's frame, from a to b.
        val path: List<Vec3>? = if (forwards) walk.path else {
            val bFrame = old.bInA ?: bInA
            bFrame?.let { f -> walk.path.map { f.toOuter(it) }.reversed() }
        }
        val gaps = if (forwards) walk.gaps else walk.gaps.map { r ->
            (walk.path.size - 1 - r.last)..(walk.path.size - 1 - r.first)
        }
        val replace = path != null && (
            (old.rough && gapFree) || (old.rough == !gapFree && pathLength(path) < old.length * 0.9f)
        )
        val newPath = if (replace) path!! else old.path
        linkList[i] = old.copy(
            walks = old.walks + 1,
            lastWalked = walk.at,
            path = newPath,
            bInA = average(old.bInA, old.walks, bInA),
            length = pathLength(newPath),
            rough = old.rough && !(replace && gaps.isEmpty()),
            climb = if (forwards) walk.climb else -walk.climb,
            blockedUntil = null,
            blockedTillWalked = false,
        )
        return linkList[i]
    }

    /**
     * Rule 6: the player said this way is blocked. It stays blocked until the end
     * of `today`; three times in two weeks and it stays blocked until it's walked.
     */
    fun block(x: SpotId, y: SpotId, today: Int): Boolean {
        val i = linkList.indexOfFirst { it.joins(x, y) }
        if (i < 0) return false
        val old = linkList[i]
        val recent = (old.blocks.filter { it > today - 14 } + today).distinct()
        linkList[i] = old.copy(
            blockedUntil = HomeClock(today, MINUTES_PER_DAY),
            blocks = recent,
            blockedTillWalked = old.blockedTillWalked || recent.size >= 3,
        )
        return true
    }

    fun isBlocked(link: Link, now: HomeClock): Boolean =
        link.blockedTillWalked || (link.blockedUntil?.let { before(now, it) } ?: false)

    fun place(item: ItemId, spot: SpotId, pose: Pose4, on: SurfaceKind): Placement {
        require(spot in spotMap) { "unknown spot $spot" }
        val p = Placement(item, spot, pose, on)
        placementMap[item] = p
        return p
    }

    fun move(item: ItemId, spot: SpotId, pose: Pose4, on: SurfaceKind): Placement? =
        if (item in placementMap) place(item, spot, pose, on) else null

    fun remove(item: ItemId): Boolean = placementMap.remove(item) != null

    /** "Forget my home". */
    fun clear() {
        spotMap.clear(); roomMap.clear(); linkList.clear(); placementMap.clear(); nextId = 1
    }

    // ---- Asking it ----

    fun spotsIn(room: RoomId): List<Spot> = spotMap.values.filter { it.room == room }

    /** Wall surfaces in a room with nothing hung on them yet, with the spot they belong to. */
    fun freeWallSpace(room: RoomId): List<Pair<SpotId, Surface>> =
        spotsIn(room).flatMap { s ->
            s.surfaces.filter { it.kind == SurfaceKind.WALL }
                .filter { w -> placementMap.values.none { it.spot == s.id && it.on == SurfaceKind.WALL && it.pose.position.flat(w.centre.position) < w.halfWidth + 0.3f } }
                .map { s.id to it }
        }

    /**
     * The cheapest known way from `from` to `to`, or null when there's no known
     * way (rule 3): the caller must then use an open beat, never a straight line.
     * Each leg's path is in that leg's `from` frame. A leg costs its length, × 1.3
     * if rough and × 1.2 if walked only once. Same-room hops (rule 7) have no path.
     */
    fun route(from: SpotId, to: SpotId, now: HomeClock, avoidStairs: Boolean = false): Route? {
        if (from !in spotMap || to !in spotMap) return null
        if (from == to) return Route(emptyList(), 0f)
        val ids = spotMap.keys.toList()
        val dist = HashMap<SpotId, Float>().apply { put(from, 0f) }
        val via = HashMap<SpotId, Pair<SpotId, Link?>>()
        val done = HashSet<SpotId>()
        while (true) {
            // The closest unfinished spot; ties go to the one made first.
            val u = ids.filter { it !in done && it in dist }.minWithOrNull(compareBy { dist[it]!! }) ?: break
            if (u == to) break
            done.add(u)
            for ((v, link, cost) in edges(u, now, avoidStairs)) {
                if (v in done) continue
                val d = dist[u]!! + cost
                if (d < (dist[v] ?: Float.MAX_VALUE)) { dist[v] = d; via[v] = u to link }
            }
        }
        val total = dist[to] ?: return null
        val legs = ArrayList<Leg>()
        var at = to
        while (at != from) {
            val (prev, link) = via[at]!!
            legs.add(leg(prev, at, link))
            at = prev
        }
        legs.reverse()
        return Route(legs, total)
    }

    private data class Edge(val to: SpotId, val link: Link?, val cost: Float)

    private fun edges(u: SpotId, now: HomeClock, avoidStairs: Boolean): List<Edge> {
        val out = ArrayList<Edge>()
        for (l in linkList) {
            if (l.a != u && l.b != u) continue
            if (isBlocked(l, now)) continue
            if (avoidStairs && abs(l.climb) > STOREY_CLIMB) continue
            var cost = l.length
            if (l.rough) cost *= 1.3f
            if (l.walks == 1) cost *= 1.2f
            out.add(Edge(l.other(u), l, cost))
        }
        val room = spotMap[u]?.room
        if (room != null) for (s in spotMap.values) {
            if (s.id == u || s.room != room) continue
            if (out.any { it.to == s.id }) continue // a walked link beats an in-room hop
            out.add(Edge(s.id, null, IN_ROOM_COST))
        }
        return out
    }

    private fun leg(from: SpotId, to: SpotId, link: Link?): Leg {
        if (link == null) return Leg(from, to, emptyList(), rough = false, inRoom = true)
        if (link.a == from) return Leg(from, to, link.path, link.rough)
        // Walking it backwards: the path is in a's frame; turn it into b's.
        val f = link.bInA ?: return Leg(from, to, emptyList(), rough = true)
        return Leg(from, to, link.path.map { f.toInner(it) }.reversed(), link.rough)
    }

    /**
     * Where the arrow points on this leg: the first point along the stored path
     * that's at least `ahead` metres from the player, past the point they're
     * nearest. Never straight at the destination. Null on a leg with no path.
     * `player` is in the leg's `from` frame.
     */
    fun nextWaypoint(leg: Leg, player: Vec3, ahead: Float = 1f): Vec3? = nextWaypoint(leg.path, player, ahead)

    /**
     * Where each spot roughly is, chaining links out from `origin` (rule 4: for
     * drawing, predicting and "it's roughly that way" only, never routes).
     */
    fun chainedPoses(origin: SpotId): Map<SpotId, Pose4> {
        val out = LinkedHashMap<SpotId, Pose4>()
        if (origin !in spotMap) return out
        out[origin] = Pose4.ORIGIN
        val queue = ArrayDeque(listOf(origin))
        while (queue.isNotEmpty()) {
            val u = queue.removeFirst()
            val pu = out[u]!!
            for (l in linkList) {
                val bInA = l.bInA ?: continue
                val (v, rel) = when (u) {
                    l.a -> l.b to bInA
                    l.b -> l.a to bInA.inverse()
                    else -> continue
                }
                if (v in out) continue
                out[v] = pu.then(rel)
                queue.addLast(v)
            }
        }
        return out
    }

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
        /** What a same-room hop costs in a route, since distance means nothing (rule 4). */
        const val IN_ROOM_COST = 3f

        fun pathLength(path: List<Vec3>): Float {
            var sum = 0f
            for (i in 1 until path.size) sum += (path[i] - path[i - 1]).length
            return sum
        }

        fun before(a: HomeClock, b: HomeClock): Boolean = a.day < b.day || (a.day == b.day && a.minute < b.minute)

        /** A running average of where b is, weighting the old estimate by its walk count. */
        private fun average(old: Pose4?, n: Int, new: Pose4?): Pose4? {
            if (old == null) return new
            if (new == null) return old
            val w = 1f / (n + 1)
            val s = sin(old.yaw) * (1 - w) + sin(new.yaw) * w
            val c = cos(old.yaw) * (1 - w) + cos(new.yaw) * w
            return Pose4(
                old.x + (new.x - old.x) * w, old.y + (new.y - old.y) * w, old.z + (new.z - old.z) * w,
                atan2(s, c),
            )
        }
    }
}

/**
 * Where the arrow points along `path`: the first point at least `ahead` metres
 * from the player, past the point they're nearest. Never straight at the end of
 * the path, and null for no path. `player` is in the path's frame.
 */
fun nextWaypoint(path: List<Vec3>, player: Vec3, ahead: Float = 1f): Vec3? {
    if (path.isEmpty()) return null
    var nearest = 0
    for (i in path.indices) if (path[i].flat(player) < path[nearest].flat(player)) nearest = i
    for (i in nearest until path.size) if (path[i].flat(player) >= ahead) return path[i]
    return path.last()
}
