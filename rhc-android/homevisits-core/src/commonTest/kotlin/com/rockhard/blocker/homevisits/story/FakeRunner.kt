package com.rockhard.blocker.homevisits.story

import com.rockhard.blocker.homevisits.HomeClock
import com.rockhard.blocker.homevisits.house.Cell
import com.rockhard.blocker.homevisits.house.FakeHouse
import com.rockhard.blocker.homevisits.house.FakePlayer
import com.rockhard.blocker.homevisits.house.Pose4
import com.rockhard.blocker.homevisits.house.SpotId
import com.rockhard.blocker.homevisits.house.SpotKind
import com.rockhard.blocker.homevisits.house.SurfaceKind
import com.rockhard.blocker.homevisits.save.HomeCodec
import com.rockhard.blocker.homevisits.save.HomeSave
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Acts out the director's beats against a [FakeHouse] with a [FakePlayer], the
 * way `ar/BeatRunner.kt` does on a phone. It also keeps the truth the game
 * can't see: which spots the player really walked between without a break, so
 * the tests can check the map never invents a link.
 */
class FakeRunner(val house: FakeHouse, seed: Long, val habits: Habits = Habits()) {
    data class Habits(
        val refuse: Float = 0.02f,      // "Not now" on any beat
        val interrupt: Float = 0.04f,   // a call mid-beat
        val otherWay: Float = 0.2f,     // walks somewhere else on the way
        val closeApp: Float = 0.01f,    // the app is closed mid-visit
        val wayOut: Float = 0.1f,       // takes a beat's way out ("I'd rather not open the door")
    )

    val rnd = Random(seed)
    var save = HomeSave()
        private set
    var clock = HomeClock(100, 16 * 60)
    val dark = rnd.nextFloat() < 0.2f
    private val env = object : VisitEnv {
        override fun now() = clock
        override fun isDark() = dark
    }
    var log = SessionLog(save.house)
        private set
    var director = FirstVisit(save, log, env)
        private set
    val player = FakePlayer(house, rnd) { o -> director.observe(o); noteStart() }

    /** Where each spot really is, in the plan. */
    val truth = LinkedHashMap<SpotId, Cell>()
    /** Spot pairs the player walked between in one unbroken stretch (no pause, no restart). */
    val walkedPairs = HashSet<Set<SpotId>>()
    /** Every route any director used, across restarts. */
    val routes = ArrayList<com.rockhard.blocker.homevisits.house.Route>()
    val transcript = ArrayList<String>()
    var beats = 0
        private set
    var interruptions = 0
        private set
    var restarts = 0
        private set

    private var segment = 0
    private var lastVisit: Pair<SpotId, Int>? = null
    private var hangRoom = 'B'
    private var painting: Pose4? = null
    private var paintingCell: Cell? = null

    init {
        val rooms = house.roomOf.keys.toList()
        player.placeAt(rooms[rnd.nextInt(rooms.size)], rnd.nextFloat() * 6f - 3f)
        player.newFrame()
    }

    /** Plays until the visit finishes, retrying an unfinished one up to `sessions` times. */
    fun run(sessions: Int = 4, maxBeats: Int = 300): Boolean {
        repeat(sessions) {
            player.stand(2f)
            while (beats < maxBeats) {
                val beat = director.next() ?: break
                beats++
                transcript.add("${director.step}: ${beat::class.simpleName}")
                if (rnd.nextFloat() < habits.closeApp && beat !is Beat.End) { closeApp(); continue }
                if (rnd.nextFloat() < habits.interrupt && beat !is Beat.End) {
                    interruptions++
                    val rooms = house.roomOf.keys.toList()
                    player.interrupt(if (rnd.nextBoolean()) rooms[rnd.nextInt(rooms.size)] else null, sameFrame = rnd.nextBoolean())
                    segment++
                    director.done(beat, BeatResult.Interrupted)
                    continue
                }
                val result = act(beat)
                director.done(beat, result)
                if (result is BeatResult.Done && arrives(beat)) visit()
            }
            routes.addAll(director.routesUsed)
            if (save.story.isSet(StoryState.FIRST_VISIT_DONE)) return true
            closeApp() // the doorbell rings again another day
            clock = HomeClock(clock.day + 1, 16 * 60)
        }
        return save.story.isSet(StoryState.FIRST_VISIT_DONE)
    }

    /** Saves, loads, and starts a fresh session: a new ARCore frame and a new director. */
    private fun closeApp() {
        routes.addAll(director.routesUsed)
        restarts++
        save = HomeCodec.decode(HomeCodec.encode(save))
        log = SessionLog(save.house)
        director = FirstVisit(save, log, env)
        player.newFrame()
        segment++
        lastVisit = null
    }

    private fun arrives(b: Beat) = b is Beat.GoNew || b is Beat.FindSurface || b is Beat.Hang ||
        b is Beat.GoTo || b is Beat.AlignByTap || b is Beat.Tour || b is Beat.Follow

    private fun noteStart() {
        val start = log.lastSpot ?: return
        if (start in truth) return
        if (save.house.spot(start)?.kind == SpotKind.START) visit()
    }

    private fun visit() {
        val s = log.lastSpot ?: return
        truth.getOrPut(s) { player.cell }
        val last = lastVisit
        if (last != null && last.second == segment && last.first != s) walkedPairs.add(setOf(last.first, s))
        lastVisit = s to segment
    }

    private fun act(beat: Beat): BeatResult {
        player.stand(1f)
        return when (beat) {
            is Beat.GoNew -> {
                if (rnd.nextFloat() < habits.refuse) return BeatResult.Declined
                toDoor()
                BeatResult.Done(at = player.camera())
            }
            is Beat.Show -> {
                if (beat.wayOut != null && rnd.nextFloat() < habits.wayOut) return BeatResult.Declined
                player.stand(3f)
                val p = ahead(1f)
                BeatResult.Done(at = player.toSession(p))
            }
            is Beat.FindSurface -> bench()
            is Beat.Say -> {
                if (rnd.nextFloat() < habits.refuse) return BeatResult.Declined
                val a = if (beat.input && rnd.nextFloat() < 0.3f) 0 else rnd.nextInt(beat.answers.size)
                if (beat.text == FirstVisitLines.HANG_ASK && a == 0) hangRoom = 'B'
                if (beat.text == FirstVisitLines.WHICH_ROOM) hangRoom = "LHKO"[a]
                if (beat.input && a != 0) BeatResult.Done(text = "Sam") else BeatResult.Done(answer = a)
            }
            is Beat.Hang -> hang()
            is Beat.GoTo -> {
                if (rnd.nextFloat() < habits.refuse) return BeatResult.Declined
                toDoor()
                BeatResult.Done(at = player.camera())
            }
            is Beat.AlignByTap -> align(beat)
            is Beat.Tour, is Beat.Follow -> {
                val c = paintingCell ?: return BeatResult.Failed("no painting")
                player.walkTo(c)
                BeatResult.Done(at = player.camera())
            }
            is Beat.End -> BeatResult.Done()
            else -> { player.stand(2f); BeatResult.Done() }
        }
    }

    private fun maybeVia(): Cell? {
        if (rnd.nextFloat() >= habits.otherWay) return null
        val rooms = house.roomOf.keys.toList()
        return rooms[rnd.nextInt(rooms.size)]
    }

    private fun toDoor() {
        val d = house.find('D')!!
        player.walkTo(d, maybeVia())
        player.face(house.wallBeside(d, door = true)!!)
        player.stand(1f)
    }

    /** A point `d` metres ahead on the floor, in the plan. */
    private fun ahead(d: Float): Pose4 {
        val p = player.pos
        return Pose4(p.x + sin(player.yaw) * d, p.y - FakePlayer.EYE, p.z + cos(player.yaw) * d, player.yaw)
    }

    private fun bench(): BeatResult {
        if (rnd.nextFloat() < habits.refuse) return BeatResult.Declined
        val b = house.find('b'); val t = house.find('t')
        val (target, kind, height, fallback) = when {
            b != null -> Quad(b, SurfaceKind.BENCH, 0.9f, false)
            t != null -> Quad(t, SurfaceKind.TABLE, 0.75f, true)
            else -> Quad(null, SurfaceKind.TABLE, 0.7f, true) // Instant Placement where they stand
        }
        if (target != null) {
            val standAt = if (target == b) b else house.neighbours4(target).first { house.passable(it) }
            player.walkTo(standAt, maybeVia())
            if (target == b) player.face(house.wallBeside(b)!!) else player.face(target)
        }
        player.stand(2f)
        val top = ahead(0.35f).let { Pose4(it.x, it.y + height, it.z, it.yaw) }
        val floor = player.pos.y - FakePlayer.EYE
        return BeatResult.Done(tap = SurfaceTap(
            kind, player.toSession(top), player.camera(), height,
            player.toSession(Pose4(0f, floor, 0f, 0f)).y, fallback,
        ))
    }

    private fun hang(): BeatResult {
        if (rnd.nextFloat() < habits.refuse) return BeatResult.Declined
        val cells = house.wallCells(hangRoom).ifEmpty { house.roomOf.keys.filter { house.wallBeside(it) != null } }
        val c = cells[rnd.nextInt(cells.size)]
        player.walkTo(c, maybeVia())
        val wallCell = house.wallBeside(c)!!
        player.face(wallCell)
        player.stand(2f)
        val face = (house.world(c) + house.world(wallCell)) * 0.5f
        val floor = house.height(c)
        val pose = Pose4(face.x, floor + 1.5f, face.z, Pose4.angle(player.yaw + PI.toFloat()))
        painting = pose; paintingCell = c
        return BeatResult.Done(tap = SurfaceTap(
            SurfaceKind.WALL, player.toSession(pose), player.camera(), 1.5f,
            player.toSession(Pose4(0f, floor, 0f, 0f)).y,
        ))
    }

    private fun align(beat: Beat.AlignByTap): BeatResult {
        if (beat.item != null) {
            val c = paintingCell ?: return BeatResult.Failed("no painting")
            player.walkTo(c)
            player.stand(1f)
            return BeatResult.Done(tap = SurfaceTap(SurfaceKind.WALL, player.toSession(painting!!), player.camera(), 1.5f, null))
        }
        toDoor()
        val d = house.find('D')!!
        val door = house.wallBeside(d, door = true)!!
        val face = (house.world(d) + house.world(door)) * 0.5f
        val pose = Pose4(face.x, 1.2f, face.z, Pose4.facing(house.world(door), house.world(d)))
        return BeatResult.Done(tap = SurfaceTap(SurfaceKind.WALL, player.toSession(pose), player.camera(), 1.2f, null))
    }

    private data class Quad(val cell: Cell?, val kind: SurfaceKind, val height: Float, val fallback: Boolean)
}
