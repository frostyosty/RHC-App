package com.rockhard.blocker.homevisits.save

import com.rockhard.blocker.homevisits.HomeClock
import com.rockhard.blocker.homevisits.house.HouseMap
import com.rockhard.blocker.homevisits.house.Link
import com.rockhard.blocker.homevisits.house.Placement
import com.rockhard.blocker.homevisits.house.Pose4
import com.rockhard.blocker.homevisits.house.Room
import com.rockhard.blocker.homevisits.house.RoomKind
import com.rockhard.blocker.homevisits.house.Spot
import com.rockhard.blocker.homevisits.house.SpotKind
import com.rockhard.blocker.homevisits.house.Surface
import com.rockhard.blocker.homevisits.house.SurfaceKind
import com.rockhard.blocker.homevisits.house.Vec3
import com.rockhard.blocker.homevisits.story.Promise
import com.rockhard.blocker.homevisits.story.StoryState

/** Everything in `home.json`. */
class HomeSave(val house: HouseMap = HouseMap(), val story: StoryState = StoryState())

/** The save was written by a newer version of the app: leave the file alone and ask for an update. */
class NewerSaveError(val version: Int) : Exception("home.json is version $version; this app reads up to ${HomeCodec.VERSION}")

/**
 * `home.json`, version 1. The app's `HomeStore` does the file work (write
 * `.tmp`, then rename); this only turns a save into text and back.
 */
object HomeCodec {
    const val VERSION = 1

    fun encode(save: HomeSave): String = Json.write(
        obj(
            "version" to num(VERSION),
            "house" to house(save.house),
            "story" to story(save.story),
        )
    )

    /** Throws [NewerSaveError] for a newer file and [JsonError] for a broken one. */
    fun decode(text: String): HomeSave {
        val root = Json.parse(text).obj()
        val v = root["version"].int()
        if (v > VERSION) throw NewerSaveError(v)
        return HomeSave(house(root["house"].obj()), story(root["story"] as? Json.Obj))
    }

    // ---- house ----

    private fun house(h: HouseMap) = obj(
        "nextId" to num(h.nextId),
        "rooms" to arr(h.rooms.map { obj("id" to str(it.id), "kind" to str(it.kind.name), "name" to str(it.name)) }),
        "spots" to arr(h.spots.map { s ->
            obj(
                "id" to str(s.id), "kind" to str(s.kind.name), "room" to str(s.room),
                "storey" to num(s.storey), "floorY" to num(s.floorY),
                "surfaces" to arr(s.surfaces.map { surface(it) }),
                "made" to clock(s.made), "confirmed" to clock(s.lastConfirmed),
            )
        }),
        "links" to arr(h.links.map { l ->
            obj(
                "a" to str(l.a), "b" to str(l.b), "walks" to num(l.walks), "lastWalked" to clock(l.lastWalked),
                "path" to path(l.path), "bInA" to l.bInA?.let { pose(it) }, "length" to num(l.length),
                "rough" to bool(l.rough), "blockedUntil" to l.blockedUntil?.let { clock(it) },
                "climb" to num(l.climb), "blockedTillWalked" to bool(l.blockedTillWalked),
                "blocks" to arr(l.blocks.map { Json.Num(it.toDouble()) }),
            )
        }),
        "placements" to arr(h.placements.map { p ->
            obj("item" to str(p.item), "spot" to str(p.spot), "pose" to pose(p.pose), "on" to str(p.on.name))
        }),
    )

    private fun house(j: Json.Obj): HouseMap {
        val rooms = j["rooms"].list().map { r ->
            val o = r.obj()
            Room(o["id"].str(), enumOf(o["kind"].str(), RoomKind.OTHER), o["name"].str())
        }
        val spots = j["spots"].list().map { s ->
            val o = s.obj()
            Spot(
                id = o["id"].str(), kind = enumOf(o["kind"].str(), SpotKind.FLOOR), room = o["room"].strOrNull(),
                storey = o["storey"].int(), floorY = o["floorY"].float(),
                surfaces = o["surfaces"].list().map { surface(it.obj()) },
                made = clock(o["made"].obj()), lastConfirmed = clock(o["confirmed"].obj()),
            )
        }
        val links = j["links"].list().map { l ->
            val o = l.obj()
            Link(
                a = o["a"].str(), b = o["b"].str(), walks = o["walks"].int(), lastWalked = clock(o["lastWalked"].obj()),
                path = path(o["path"]), bInA = (o["bInA"] as? Json.Arr)?.let { pose(it) }, length = o["length"].float(),
                rough = o["rough"].bool(), blockedUntil = (o["blockedUntil"] as? Json.Obj)?.let { clock(it) },
                climb = o["climb"].float(), blockedTillWalked = o["blockedTillWalked"].bool(),
                blocks = o["blocks"].list().map { it.int() },
            )
        }
        val placements = j["placements"].list().map { p ->
            val o = p.obj()
            Placement(o["item"].str(), o["spot"].str(), pose(o["pose"]), enumOf(o["on"].str(), SurfaceKind.FLOOR))
        }
        return HouseMap(spots, rooms, links, placements, j["nextId"].int())
    }

    private fun surface(s: Surface) = obj(
        "kind" to str(s.kind.name), "centre" to pose(s.centre), "hw" to num(s.halfWidth), "hd" to num(s.halfDepth),
    )

    private fun surface(o: Json.Obj) =
        Surface(enumOf(o["kind"].str(), SurfaceKind.FLOOR), pose(o["centre"]), o["hw"].float(), o["hd"].float())

    private fun pose(p: Pose4) = arr(listOf(p.x, p.y, p.z, p.yaw).map { num(it)!! })
    private fun pose(o: Json?): Pose4 {
        val n = o.list().map { it.float() }
        if (n.size != 4) throw JsonError("a pose needs 4 numbers")
        return Pose4(n[0], n[1], n[2], n[3])
    }

    /** A path as one flat array, x y z x y z …, which keeps the file small. */
    private fun path(p: List<Vec3>) = arr(p.flatMap { listOf(num(it.x)!!, num(it.y)!!, num(it.z)!!) })
    private fun path(j: Json?): List<Vec3> {
        val n = j.list().map { it.float() }
        return List(n.size / 3) { Vec3(n[it * 3], n[it * 3 + 1], n[it * 3 + 2]) }
    }

    private fun clock(c: HomeClock) = obj("day" to num(c.day), "minute" to num(c.minute))
    private fun clock(o: Json.Obj) = HomeClock(o["day"].int(), o["minute"].int())

    // ---- story ----

    private fun story(s: StoryState) = obj(
        "flags" to Json.Obj(LinkedHashMap<String, Json>().apply { s.flags.forEach { (k, v) -> put(k, Json.Str(v)) } }),
        "promises" to arr(s.promises.map { obj("who" to str(it.who), "day" to num(it.day), "what" to str(it.what)) }),
    )

    private fun story(o: Json.Obj?): StoryState {
        val st = StoryState()
        if (o == null) return st
        (o["flags"] as? Json.Obj)?.fields?.forEach { (k, v) -> v.strOrNull()?.let { st.flags[k] = it } }
        o["promises"].list().forEach { p ->
            val x = p.obj()
            st.promises.add(Promise(x["who"].str(), x["day"].int(), x["what"].str()))
        }
        return st
    }

    /** An unknown name (from a save made by a later version that added one) becomes `fallback`. */
    private inline fun <reified E : Enum<E>> enumOf(name: String, fallback: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: fallback
}
