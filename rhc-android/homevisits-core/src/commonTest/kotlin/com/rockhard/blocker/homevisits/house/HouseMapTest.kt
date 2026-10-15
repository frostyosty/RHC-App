package com.rockhard.blocker.homevisits.house

import com.rockhard.blocker.homevisits.HomeClock
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HouseMapTest {
    private val day = HomeClock(10, 600)

    private fun line(from: Vec3, to: Vec3, n: Int = 8) = List(n + 1) { i -> from + (to - from) * (i.toFloat() / n) }

    private fun walk(map: HouseMap, a: SpotId, b: SpotId, len: Float = 4f, gaps: List<IntRange> = emptyList(), at: HomeClock = day, secs: Int = 10) =
        map.recordWalk(Walk(a, b, at, secs, line(Vec3(0f, 0f, 0f), Vec3(0f, 0f, len)), gaps, 0f, Pose4(0f, 0f, len, 0f)))

    /** The same walk the other way: from b, a is 4 m behind. */
    private fun walkBack(map: HouseMap, b: SpotId, a: SpotId, len: Float = 4f, at: HomeClock = day) =
        map.recordWalk(Walk(b, a, at, 10, line(Vec3(0f, 0f, 0f), Vec3(0f, 0f, -len)), emptyList(), 0f, Pose4(0f, 0f, -len, 0f)))

    private fun threeSpots(): Triple<HouseMap, Spot, Pair<Spot, Spot>> {
        val m = HouseMap()
        val a = m.addSpot(SpotKind.FRONT_DOOR, day)
        val b = m.addSpot(SpotKind.BENCH, day)
        val c = m.addSpot(SpotKind.WALL, day)
        return Triple(m, a, b to c)
    }

    @Test
    fun poseFramesRoundTrip() {
        val f = Pose4(1f, 0.5f, -2f, 0.7f)
        val p = Vec3(0.3f, 1f, 2f)
        val back = f.toInner(f.toOuter(p))
        assertTrue((back - p).length < 1e-5f)
        val g = Pose4(-0.5f, 0f, 3f, -1.2f)
        val composed = f.then(g)
        assertTrue((f.inverse().then(composed).position - g.position).length < 1e-5f)
        // An object at yaw faces (sin, 0, cos), like the AR kit's figures.
        val facing = Pose4(0f, 0f, 0f, (PI / 2).toFloat()).toOuter(Vec3(0f, 0f, 1f))
        assertTrue((facing - Vec3(1f, 0f, 0f)).length < 1e-5f)
    }

    @Test
    fun noWalkNoLinkNoRoute() {
        val (m, a, bc) = threeSpots()
        assertNull(m.route(a.id, bc.first.id, day))
        walk(m, a.id, bc.first.id)
        assertNotNull(m.route(a.id, bc.first.id, day))
        assertNull(m.route(a.id, bc.second.id, day), "distance means nothing: no walk, no way")
    }

    @Test
    fun backwardsIsTheSameLink() {
        val (m, a, bc) = threeSpots()
        walk(m, a.id, bc.first.id)
        walkBack(m, bc.first.id, a.id)
        assertEquals(1, m.links.size)
        assertEquals(2, m.links[0].walks)
        val back = m.route(bc.first.id, a.id, day)!!
        assertEquals(1, back.legs.size)
        // The backwards path starts at b (its origin) and ends at a (4 m back along b's -z... in b's frame).
        val path = back.legs[0].path
        assertTrue(path.first().flat(Vec3(0f, 0f, 0f)) < 0.01f)
        assertTrue(path.last().flat(Vec3(0f, 0f, -4f)) < 0.01f, "got ${path.last()}")
    }

    @Test
    fun brokenWalksAreRefused() {
        val (m, a, bc) = threeSpots()
        assertNull(walk(m, a.id, bc.first.id, secs = MAX_WALK_SECONDS + 1))
        assertNull(walk(m, a.id, a.id))
        assertNull(m.recordWalk(Walk(a.id, "nowhere", day, 5, line(Vec3(0f, 0f, 0f), Vec3(1f, 0f, 0f)), emptyList(), 0f, null)))
        assertTrue(m.links.isEmpty())
    }

    @Test
    fun roughUntilAGapFreeWalk() {
        val (m, a, bc) = threeSpots()
        walk(m, a.id, bc.first.id, gaps = listOf(2..4))
        assertTrue(m.links[0].rough)
        assertTrue(m.route(a.id, bc.first.id, day)!!.legs[0].rough)
        walkBack(m, bc.first.id, a.id)
        assertFalse(m.links[0].rough)
    }

    @Test
    fun blockedTillEndOfDayThenThreeTimesTillWalked() {
        val (m, a, bc) = threeSpots()
        val b = bc.first.id
        walk(m, a.id, b)
        m.block(a.id, b, 10)
        assertNull(m.route(a.id, b, HomeClock(10, 700)))
        assertNotNull(m.route(a.id, b, HomeClock(11, 0)))
        m.block(a.id, b, 14); m.block(a.id, b, 20)
        assertNull(m.route(a.id, b, HomeClock(40, 0)), "three times in two weeks: blocked till walked")
        walkBack(m, b, a.id, at = HomeClock(40, 1))
        assertNotNull(m.route(a.id, b, HomeClock(40, 2)))
        // Three blocks spread over more than two weeks don't stick.
        m.block(a.id, b, 50); m.block(a.id, b, 60); m.block(a.id, b, 70)
        assertNotNull(m.route(a.id, b, HomeClock(71, 0)))
    }

    @Test
    fun inRoomHopsHaveNoPath() {
        val m = HouseMap()
        val k = m.addRoom(RoomKind.KITCHEN, "the kitchen")
        val a = m.addSpot(SpotKind.BENCH, day, k.id)
        val b = m.addSpot(SpotKind.TABLE, day, k.id)
        val r = m.route(a.id, b.id, day)!!
        assertTrue(r.legs.single().inRoom)
        assertTrue(r.legs.single().path.isEmpty())
        assertNull(m.nextWaypoint(r.legs.single(), Vec3(0f, 0f, 0f)))
    }

    @Test
    fun stairsCanBeAvoided() {
        val (m, a, bc) = threeSpots()
        m.recordWalk(Walk(a.id, bc.second.id, day, 20, line(Vec3(0f, 0f, 0f), Vec3(0f, 2.8f, 4f)), emptyList(), 2.8f, Pose4(0f, 2.8f, 4f, 0f)))
        assertNotNull(m.route(a.id, bc.second.id, day))
        assertNull(m.route(a.id, bc.second.id, day, avoidStairs = true))
        assertEquals(1, m.storeyAfter(a.id, 2.8f))
    }

    @Test
    fun wellKnownWaysWin() {
        val (m, a, bc) = threeSpots()
        val (b, c) = bc
        val d = m.addSpot(SpotKind.FLOOR, day)
        // a–b–c, 4 m each, walked many times; a–d–c, 3.5 m each, walked once and rough.
        repeat(3) { walk(m, a.id, b.id); walk(m, b.id, c.id) }
        walk(m, a.id, d.id, len = 3.5f, gaps = listOf(1..2)); walk(m, d.id, c.id, len = 3.5f, gaps = listOf(1..2))
        assertEquals(listOf(a.id, b.id, c.id), m.route(a.id, c.id, day)!!.stops)
    }

    @Test
    fun arrowFollowsThePathNotTheCrowFlight() {
        // An L-shaped walk: 4 m forward, then 4 m right. Standing at the start, the arrow points forward.
        val path = line(Vec3(0f, 0f, 0f), Vec3(0f, 0f, 4f)) + line(Vec3(0f, 0f, 4f), Vec3(4f, 0f, 4f)).drop(1)
        val wp = nextWaypoint(path, Vec3(0f, 0f, 0f))!!
        assertTrue(wp.x < 0.01f && wp.z >= 1f, "got $wp")
        val corner = nextWaypoint(path, Vec3(0f, 0f, 3.8f))!!
        assertTrue(corner.x >= 0.5f, "past the corner it turns: $corner")
    }

    @Test
    fun backToBackOnlyThroughTheHall() {
        // The bench and the bedroom wall are a metre apart through a wall; the player only walks them via the hall.
        val h = FakeHouse.BACK_TO_BACK
        val m = HouseMap()
        val door = m.addSpot(SpotKind.FRONT_DOOR, day)
        val bench = m.addSpot(SpotKind.BENCH, day)
        val wall = m.addSpot(SpotKind.WALL, day)
        val cells = mapOf(door.id to h.find('D')!!, bench.id to h.find('b')!!, wall.id to h.find('w')!!)
        fun walkReal(x: SpotId, y: SpotId) {
            val cs = h.path(cells[x]!!, cells[y]!!)!!
            val origin = h.world(cells[x]!!)
            val pts = cs.map { h.world(it) - origin }
            m.recordWalk(Walk(x, y, day, 30, pts, emptyList(), 0f, Pose4(pts.last().x, 0f, pts.last().z, 0f)))
        }
        walkReal(door.id, bench.id); walkReal(bench.id, wall.id)
        assertTrue(h.world(cells[bench.id]!!).flat(h.world(cells[wall.id]!!)) <= 1.01f)
        val r = m.route(bench.id, wall.id, day)!!
        val origin = h.world(cells[bench.id]!!)
        val inHall = r.legs.single().path.any { h.room(h.cellAt(it + origin)) == 'H' }
        assertTrue(inHall, "the way from the bench to the bedroom wall goes through the hall")
        // And every step of it stays out of the walls.
        val pts = r.legs.single().path.map { it + origin }
        for (i in 1 until pts.size) assertTrue(h.clearLine(pts[i - 1], pts[i]), "through a wall between ${pts[i - 1]} and ${pts[i]}")
    }

    @Test
    fun chainedPosesAreForDrawingOnly() {
        val (m, a, bc) = threeSpots()
        walk(m, a.id, bc.first.id)
        val poses = m.chainedPoses(a.id)
        assertEquals(2, poses.size)
        assertTrue(poses[bc.first.id]!!.position.flat(Vec3(0f, 0f, 4f)) < 0.01f)
    }

    @Test
    fun freeWallSpaceSkipsHungWalls() {
        val m = HouseMap()
        val bed = m.addRoom(RoomKind.BEDROOM, "your bedroom")
        val s = m.addSpot(SpotKind.WALL, day, bed.id, surfaces = listOf(Surface(SurfaceKind.WALL, Pose4(0f, 1.5f, 0.5f, PI.toFloat()), 0.5f, 0.05f)))
        assertEquals(1, m.freeWallSpace(bed.id).size)
        m.place("painting", s.id, Pose4(0f, 1.5f, 0.5f, PI.toFloat()), SurfaceKind.WALL)
        assertEquals(0, m.freeWallSpace(bed.id).size)
    }
}
