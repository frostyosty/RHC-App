package com.rockhard.blocker.homevisits.save

import com.rockhard.blocker.homevisits.HomeClock
import com.rockhard.blocker.homevisits.house.HouseMap
import com.rockhard.blocker.homevisits.house.Pose4
import com.rockhard.blocker.homevisits.house.RoomKind
import com.rockhard.blocker.homevisits.house.SpotKind
import com.rockhard.blocker.homevisits.house.Surface
import com.rockhard.blocker.homevisits.house.SurfaceKind
import com.rockhard.blocker.homevisits.house.Vec3
import com.rockhard.blocker.homevisits.house.Walk
import com.rockhard.blocker.homevisits.story.Promise
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SaveTest {
    @Test
    fun jsonRoundTrips() {
        val text = """{"a":[1,2.5,-3e2,true,false,null],"s":"quote \" slash \\ line\n tab\t é ☕","o":{}}"""
        val v = Json.parse(text)
        assertEquals(v, Json.parse(Json.write(v)))
        val s = (v as Json.Obj)["s"].str()
        assertEquals("quote \" slash \\ line\n tab\t é ☕", s)
        assertEquals(-300.0, ((v["a"] as Json.Arr).items[2] as Json.Num).value)
    }

    @Test
    fun brokenJsonIsAnError() {
        for (bad in listOf("{", "[1,]", "{\"a\" 1}", "tru", "\"open", "{} x")) {
            assertFailsWith<JsonError>(bad) { Json.parse(bad) }
        }
    }

    @Test
    fun floatsComeBackExactly() {
        val r = Random(3)
        repeat(1000) {
            val f = (r.nextFloat() - 0.5f) * r.nextInt(1, 1000)
            assertEquals(f, Json.parse(Json.write(num(f)!!)).float())
        }
    }

    private fun randomSave(seed: Int): HomeSave {
        val r = Random(seed)
        fun f() = (r.nextFloat() - 0.5f) * 10f
        val m = HouseMap()
        val rooms = List(r.nextInt(1, 4)) { m.addRoom(RoomKind.values()[r.nextInt(RoomKind.values().size)], "room $it") }
        val spots = List(r.nextInt(2, 8)) {
            m.addSpot(
                SpotKind.values()[r.nextInt(SpotKind.values().size)], HomeClock(r.nextInt(100), r.nextInt(1440)),
                if (r.nextBoolean()) rooms[r.nextInt(rooms.size)].id else null, r.nextInt(2), f(),
                List(r.nextInt(3)) { Surface(SurfaceKind.values()[r.nextInt(5)], Pose4(f(), f(), f(), f()), r.nextFloat(), r.nextFloat()) },
            )
        }
        repeat(r.nextInt(1, 8)) {
            val a = spots[r.nextInt(spots.size)].id; val b = spots[r.nextInt(spots.size)].id
            m.recordWalk(Walk(a, b, HomeClock(5, 5), 20, List(r.nextInt(2, 10)) { Vec3(f(), f(), f()) },
                if (r.nextBoolean()) listOf(1..2) else emptyList(), f(), if (r.nextBoolean()) Pose4(f(), f(), f(), f()) else null))
        }
        if (m.links.isNotEmpty()) m.block(m.links[0].a, m.links[0].b, 7)
        m.place("painting", spots[0].id, Pose4(f(), f(), f(), f()), SurfaceKind.WALL)
        val save = HomeSave(m)
        save.story.set("firstVisit.done", true)
        save.story.set("player.name", "Ana \"Nan\" Te Rangi")
        save.story.promises.add(Promise("rachel", 101, "visit"))
        return save
    }

    @Test
    fun saveThenLoadGivesTheSameMap() {
        repeat(200) { seed ->
            val save = randomSave(seed)
            val text = HomeCodec.encode(save)
            val back = HomeCodec.decode(text)
            assertEquals(save.house.spots, back.house.spots)
            assertEquals(save.house.rooms, back.house.rooms)
            assertEquals(save.house.links, back.house.links)
            assertEquals(save.house.placements, back.house.placements)
            assertEquals(save.house.nextId, back.house.nextId)
            assertEquals(save.story.flags, back.story.flags)
            assertEquals(save.story.promises, back.story.promises)
            assertEquals(text, HomeCodec.encode(back), "byte for byte the same")
        }
    }

    @Test
    fun aNewerFileIsRefused() {
        val text = HomeCodec.encode(randomSave(1)).replaceFirst("\"version\":1", "\"version\":2")
        val e = assertFailsWith<NewerSaveError> { HomeCodec.decode(text) }
        assertEquals(2, e.version)
    }

    @Test
    fun anOldFileWithoutAStoryStillLoads() {
        val back = HomeCodec.decode("""{"version":1,"house":{"nextId":1,"rooms":[],"spots":[],"links":[],"placements":[]}}""")
        assertTrue(back.house.spots.isEmpty())
        assertTrue(back.story.flags.isEmpty())
    }
}
