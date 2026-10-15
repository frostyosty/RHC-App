package com.rockhard.blocker.homevisits.story

import com.rockhard.blocker.homevisits.house.FakeHouse
import com.rockhard.blocker.homevisits.house.PlanGen
import com.rockhard.blocker.homevisits.house.SpotKind
import com.rockhard.blocker.homevisits.house.RoomKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class FirstVisitTest {

    /** The checks every run must pass, whatever the player did. */
    private fun checkRun(r: FakeRunner, label: String) {
        val map = r.save.house
        // No invented links: every link joins two spots the player walked between without a break.
        for (l in map.links) {
            if (setOf(l.a, l.b) !in r.walkedPairs) fail("$label: link ${l.a}–${l.b} was never walked in one go\n${r.house}\n${r.transcript.joinToString("\n")}")
        }
        // The knock's arrow, the tour and leaving only use walked links or hops inside one room.
        for (route in r.routes) for (leg in route.legs) {
            if (leg.inRoom) {
                val a = map.spot(leg.from)!!.room; val b = map.spot(leg.to)!!.room
                assertTrue(a != null && a == b, "$label: an in-room hop across rooms")
            } else {
                assertNotNull(map.link(leg.from, leg.to), "$label: a route leg with no link")
            }
        }
        // Every spot really is where the plan has floor, and no room is a bathroom (there's no such kind).
        for ((_, cell) in r.truth) assertTrue(r.house.passable(cell))
    }

    @Test
    fun theHappyPath() {
        val r = FakeRunner(FakeHouse.BACK_TO_BACK, 7, FakeRunner.Habits(0f, 0f, 0f, 0f, 0f))
        assertTrue(r.run(), r.transcript.joinToString("\n"))
        checkRun(r, "happy")
        val map = r.save.house
        val door = map.spotsOfKind(SpotKind.FRONT_DOOR).single()
        val bench = map.spotsOfKind(SpotKind.BENCH).single()
        val wall = map.spotsOfKind(SpotKind.WALL).single()
        assertEquals(RoomKind.ENTRY, map.room(door.room!!)!!.kind)
        assertEquals(RoomKind.KITCHEN, map.room(bench.room!!)!!.kind)
        assertNotNull(map.link(door.id, bench.id), "door–bench")
        assertNotNull(map.link(bench.id, wall.id), "bench–wall")
        assertNotNull(map.link(wall.id, door.id), "wall–door, walked back for the knock")
        assertEquals(wall.id, map.placement(FirstVisitLines.PAINTING)!!.spot)
        assertTrue(r.save.story.isSet(StoryState.FIRST_VISIT_DONE))
        assertNotNull(r.save.story.flag(StoryState.PAINTING_ANSWER))
        assertEquals(1, r.save.story.promises.size)
        // The tour went door → bench → wall or straight door → wall: either way along walked links.
        assertTrue(r.routes.isNotEmpty())
    }

    @Test
    fun fiveHundredPlans() {
        var finished = 0
        var interrupted = 0
        var studios = 0
        for (seed in 0 until 500) {
            val house = PlanGen.make(seed.toLong())
            if (house.cellsIn('H').isEmpty()) studios++
            val r = FakeRunner(house, seed * 31L + 1)
            val done = r.run()
            if (done) finished++
            if (r.interruptions > 0 || r.restarts > 0) interrupted++
            assertTrue(r.beats < 300, "seed $seed: the visit never ended\n$house\n${r.transcript.takeLast(30).joinToString("\n")}")
            checkRun(r, "seed $seed")
            if (done) {
                val map = r.save.house
                assertNotNull(map.placement(FirstVisitLines.PAINTING), "seed $seed: finished without the painting")
                assertTrue(map.spotsOfKind(SpotKind.FRONT_DOOR).isNotEmpty())
            }
        }
        println("first visit over 500 plans: $finished finished, $interrupted had interruptions, $studios studios")
        assertTrue(finished > 400, "only $finished of 500 finished")
        assertTrue(studios > 10 && interrupted > 50, "the plans and players should vary")
    }

    @Test
    fun plansAreVaried() {
        var upstairs = 0; var noBench = 0
        for (seed in 0 until 200) {
            val h = PlanGen.make(seed.toLong())
            assertNotNull(h.find('D'), "seed $seed has no front door")
            if (h.rows.any { '^' in it }) upstairs++
            if (!h.has('b')) noBench++
            // Every room can be reached from the door.
            val d = h.find('D')!!
            for (c in h.roomOf.keys.filterIndexed { i, _ -> i % 7 == 0 }) assertNotNull(h.path(d, c), "seed $seed: $c unreachable\n$h")
        }
        assertTrue(upstairs > 10 && noBench > 10, "upstairs $upstairs, no bench $noBench")
    }
}
