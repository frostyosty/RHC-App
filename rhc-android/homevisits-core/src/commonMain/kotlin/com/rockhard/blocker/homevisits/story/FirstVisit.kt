package com.rockhard.blocker.homevisits.story

import com.rockhard.blocker.homevisits.HomeClock
import com.rockhard.blocker.homevisits.house.HouseMap
import com.rockhard.blocker.homevisits.house.Pose4
import com.rockhard.blocker.homevisits.house.Room
import com.rockhard.blocker.homevisits.house.RoomKind
import com.rockhard.blocker.homevisits.house.Route
import com.rockhard.blocker.homevisits.house.Spot
import com.rockhard.blocker.homevisits.house.SpotKind
import com.rockhard.blocker.homevisits.house.SurfaceKind
import com.rockhard.blocker.homevisits.save.HomeSave
import com.rockhard.blocker.homevisits.story.FirstVisitLines as L
import com.rockhard.blocker.homevisits.story.StoryState.Companion as S
import kotlin.math.PI

/** What the director needs from outside the core: the time, and whether it's dark (clock or light estimate). */
interface VisitEnv {
    fun now(): HomeClock
    fun isDark(): Boolean
}

/**
 * The first visit (04), as a script with branches: parcel at the door, the
 * bench, the painting, the knock, Rachel's tour, goodbye. It records the map
 * through [SessionLog] and never assumes a way it hasn't seen: the knock's
 * arrow and the tour only follow `HouseMap.route`.
 *
 * A closed app resumes from the last checkpoint (`firstVisit.at`); a paused one
 * repeats the beat it was on, and asks for a tap (`AlignByTap`) when a spot it
 * needs was lost with the session's frame.
 */
class FirstVisit(val save: HomeSave, val log: SessionLog, private val env: VisitEnv) : Director {

    enum class Step {
        TO_DOOR, PARCEL, HOLD, BENCH, PUT_DOWN, UNWRAP, HANG_ASK, ROOM_PICK, HANG, MOMENT,
        KNOCK, ARRIVE, TALK1, NAME, NICE, TALK3, TOUR, CHOICE, REPLY, GOODBYE, LEAVE,
        END_DONE, END_LATER, OVER,
    }

    var step: Step = resumeStep()
        private set

    /** Every route the visit used (the knock's arrow, the tour, leaving), for tests and the debug report. */
    val routesUsed = ArrayList<Route>()

    private val house: HouseMap get() = save.house
    private val story: StoryState get() = save.story
    private var startMade = false
    private var insideOnly = false
    private var hangKind = RoomKind.BEDROOM
    private var hangName = "your bedroom"
    private var benchTap: SurfaceTap? = null
    private var answer = 0

    private fun resumeStep(): Step = when (save.story.flag(S.FIRST_VISIT_AT)) {
        null -> Step.TO_DOOR
        "door" -> Step.HOLD
        "bench" -> Step.UNWRAP
        "unwrapped" -> Step.HANG_ASK
        else -> Step.KNOCK // "hung"
    }

    private fun checkpoint(at: String) = story.set(S.FIRST_VISIT_AT, at)

    private val door: Spot? get() = house.spotsOfKind(SpotKind.FRONT_DOOR).firstOrNull()
    private val bench: Spot? get() = house.spots.firstOrNull { it.kind == SpotKind.BENCH || it.kind == SpotKind.TABLE }
    private val wall: Spot? get() = house.placement(L.PAINTING)?.let { house.spot(it.spot) }
    private val wallRoom: Room? get() = wall?.room?.let { house.room(it) }

    override fun observe(o: Observation) {
        log.observe(o)
        // The START spot is where tracking began, on the way to the door (the first session only).
        if (!startMade && step == Step.TO_DOOR && log.tracking && house.spotsOfKind(SpotKind.START).isEmpty()) {
            startMade = log.startHere(env.now()) != null
        }
    }

    override fun next(): Beat? = when (step) {
        Step.TO_DOOR -> Beat.GoNew(
            SpotKind.FRONT_DOOR, RoomKind.ENTRY, L.TO_DOOR, L.AT_DOOR,
            tip = if (story.isSet(S.SEEN_CAMERA_TIP)) null else L.TIP_HOLD_UP,
        )
        Step.PARCEL -> if (insideOnly || env.isDark()) Beat.Show(
            L.PARCEL, Where.FloorAhead(0.5f, 1.5f), if (insideOnly) L.JUST_INSIDE else L.DARK_INSIDE,
            hints = listOf(Hint(L.LOOK_DOWN, 0, instant = true)), take = true, bounce = true,
        ) else Beat.Show(
            L.PARCEL, Where.FloorAhead(0.5f, 1.5f), L.OPEN_DOOR,
            hints = listOf(Hint(L.LOOK_DOWN, 8), Hint(L.JUST_INSIDE, 15, instant = true)),
            wayOut = L.RATHER_NOT, take = true, bounce = true, found = L.FOUND_IT,
        )
        Step.HOLD -> Beat.Hold(L.PARCEL)
        Step.BENCH -> Beat.FindSurface(
            setOf(SurfaceKind.BENCH, SurfaceKind.TABLE), 0.8f..1.0f, L.TO_BENCH, L.TAP_BENCH,
            listOf(
                Hint(L.STEP_BACK, 15),
                Hint(L.ANY_TABLE, 30, widen = 0.55f..1.1f),
                Hint(L.TAP_ANYWHERE, 45, instant = true),
            ),
        )
        Step.PUT_DOWN -> benchTap?.let { Beat.Place(L.PARCEL, it.pose) } ?: Beat.Hold(null)
        Step.UNWRAP -> Beat.Unwrap(L.PARCEL, L.PAINTING, L.CARD)
        Step.HANG_ASK -> Beat.Say(null, L.HANG_ASK, listOf(L.BEDROOM, L.ELSEWHERE))
        Step.ROOM_PICK -> Beat.Say(null, L.WHICH_ROOM, L.ROOMS)
        Step.HANG -> Beat.Hang(
            L.PAINTING, L.takeItTo(hangName), L.TAP_WALL,
            listOf(Hint(L.STEP_FROM_WALL, 20), Hint(L.TAP_WALL, 35, instant = true)),
            L.LOOKS_GOOD,
        )
        Step.MOMENT -> Beat.Pause(5, L.PERFECT, sparkleOn = L.PAINTING)
        Step.KNOCK -> knock()
        Step.ARRIVE -> Beat.Arrive(L.RACHEL, rachelAtDoor())
        Step.TALK1 -> Beat.Say(L.RACHEL, L.HI, L.HI_ANSWERS)
        Step.NAME -> Beat.Say(L.RACHEL, L.YOUR_NAME, listOf(L.JUST_NEIGHBOUR), input = true)
        Step.NICE -> Beat.Say(L.RACHEL, L.nice(story.flag(S.PLAYER_NAME)), listOf(L.OK))
        Step.TALK3 -> Beat.Say(L.RACHEL, L.CAN_I_SEE, listOf(L.COME_IN))
        Step.TOUR -> tour()
        Step.CHOICE -> Beat.Say(L.RACHEL, L.hungIn(wallRoom?.name ?: hangName, wallRoom?.kind == RoomKind.BEDROOM), L.PAINTING_ANSWERS)
        Step.REPLY -> Beat.Say(L.RACHEL, L.PAINTING_REPLIES[answer.coerceIn(0, 2)], listOf(L.OK))
        Step.GOODBYE -> Beat.Say(L.RACHEL, L.GOODBYE, listOf(L.BYE))
        Step.LEAVE -> Beat.Leave(L.RACHEL, leavePath())
        Step.END_DONE -> Beat.End(L.AFTER_CARD, finished = true)
        Step.END_LATER -> Beat.End(L.LATER_CARD, finished = false)
        Step.OVER -> null
    }

    override fun done(beat: Beat, result: BeatResult) {
        val now = env.now()
        when (result) {
            BeatResult.Interrupted -> return // resume at the same beat
            is BeatResult.Failed -> { step = if (step >= Step.END_DONE) Step.OVER else Step.END_LATER; return }
            BeatResult.Declined -> {
                step = when {
                    step == Step.PARCEL -> { insideOnly = true; Step.PARCEL }
                    step >= Step.END_DONE -> Step.OVER
                    else -> Step.END_LATER
                }
                return
            }
            is BeatResult.Done -> Unit
        }
        val r = result as BeatResult.Done
        step = when (step) {
            Step.TO_DOOR -> {
                val at = r.at ?: log.camera ?: return fail()
                val entry = house.roomOfKind(RoomKind.ENTRY) ?: house.addRoom(RoomKind.ENTRY, "the entry")
                log.makeSpot(SpotKind.FRONT_DOOR, entry.id, at, now)
                story.set(S.SEEN_CAMERA_TIP, true)
                Step.PARCEL
            }
            Step.PARCEL -> {
                val d = door
                if (d != null && r.at != null) log.setFloor(d.id, r.at.y)
                checkpoint("door")
                Step.HOLD
            }
            Step.HOLD -> Step.BENCH
            Step.BENCH -> {
                val tap = r.tap ?: return fail()
                val isBench = tap.kind == SurfaceKind.BENCH && !tap.fallback
                // Rule 9: a bench means the kitchen; anything else isn't assumed (a character asks later).
                val room = if (isBench) house.roomOfKind(RoomKind.KITCHEN) ?: house.addRoom(RoomKind.KITCHEN, "the kitchen") else null
                val kind = if (tap.kind == SurfaceKind.BENCH) SpotKind.BENCH else SpotKind.TABLE
                log.makeSpot(kind, room?.id, tap.camera, now, tap.floorY, listOf(tap.kind to tap))
                benchTap = tap
                checkpoint("bench")
                Step.PUT_DOWN
            }
            Step.PUT_DOWN -> Step.UNWRAP
            Step.UNWRAP -> { checkpoint("unwrapped"); Step.HANG_ASK }
            Step.HANG_ASK -> if (r.answer == 0) { hangKind = RoomKind.BEDROOM; hangName = "your bedroom"; Step.HANG } else Step.ROOM_PICK
            Step.ROOM_PICK -> {
                when (r.answer) {
                    0 -> { hangKind = RoomKind.LOUNGE; hangName = "the lounge" }
                    1 -> { hangKind = RoomKind.HALL; hangName = "the hallway" }
                    2 -> { hangKind = RoomKind.KITCHEN; hangName = "the kitchen" }
                    else -> { hangKind = RoomKind.OTHER; hangName = "that room" }
                }
                Step.HANG
            }
            Step.HANG -> {
                val tap = r.tap ?: return fail()
                val room = (if (hangKind != RoomKind.OTHER) house.roomOfKind(hangKind) else null) ?: house.addRoom(hangKind, hangName)
                val spot = log.makeSpot(SpotKind.WALL, room.id, tap.camera, now, tap.floorY, listOf(SurfaceKind.WALL to tap))
                house.place(L.PAINTING, spot.id, log.toSpot(spot.id, tap.pose)!!, SurfaceKind.WALL)
                checkpoint("hung")
                Step.MOMENT
            }
            Step.MOMENT -> Step.KNOCK
            Step.KNOCK -> {
                val d = door ?: return fail()
                if (beat is Beat.AlignByTap) {
                    val tap = r.tap ?: return fail()
                    log.align(d.id, DOOR_IN_SPOT, tap.pose)
                    log.arrive(d.id, now)
                } else log.arrive(d.id, now, r.at)
                Step.ARRIVE
            }
            Step.ARRIVE -> Step.TALK1
            Step.TALK1 -> Step.NAME
            Step.NAME -> {
                val name = r.text?.trim()?.take(30)?.ifBlank { null }
                story.set(S.PLAYER_NAME, if (r.answer == 0) null else name)
                Step.NICE
            }
            Step.NICE -> Step.TALK3
            Step.TALK3 -> Step.TOUR
            Step.TOUR -> {
                if (beat is Beat.AlignByTap) {
                    val w = wall; val p = house.placement(L.PAINTING); val tap = r.tap
                    if (w == null || p == null || tap == null) return fail()
                    log.align(w.id, p.pose, tap.pose)
                    log.arrive(w.id, now)
                    Step.TOUR
                } else {
                    wall?.let { log.arrive(it.id, now, r.at) }
                    Step.CHOICE
                }
            }
            Step.CHOICE -> {
                answer = r.answer ?: 0
                story.set(S.PAINTING_ANSWER, answer.toString())
                Step.REPLY
            }
            Step.REPLY -> Step.GOODBYE
            Step.GOODBYE -> {
                story.promises.add(Promise(L.RACHEL, now.day + 1, "visit"))
                Step.LEAVE
            }
            Step.LEAVE -> Step.END_DONE
            Step.END_DONE -> {
                story.set(S.FIRST_VISIT_DONE, true)
                story.set(S.FIRST_VISIT_AT, null)
                Step.OVER
            }
            Step.END_LATER, Step.OVER -> Step.OVER
        }
    }

    private fun fail(): Unit { step = Step.END_LATER }

    // ---- The knock, the tour, leaving ----

    private fun knock(): Beat {
        val d = door ?: return Beat.GoNew(SpotKind.FRONT_DOOR, RoomKind.ENTRY, L.KNOCK, L.OPEN, tip = null)
        if (!log.framed(d.id)) return Beat.AlignByTap(d.id, null, setOf(SurfaceKind.WALL, SurfaceKind.FLOOR), L.FIND_DOOR)
        val from = log.lastSpot ?: wall?.id
        val route = from?.let { house.route(it, d.id, env.now()) }
        route?.let { routesUsed.add(it) }
        return Beat.GoTo(d.id, route?.let { log.sessionPath(it) }, L.KNOCK, L.OPEN, sound = "knock")
    }

    /** Rachel 1 m out from the door, facing in. The door is about 0.6 m in front of where the phone was. */
    private fun rachelAtDoor(): Pose4 {
        val d = door ?: return log.camera ?: Pose4.ORIGIN
        val p = log.toSession(d.id, Pose4(0f, d.floorY, 1.6f, PI.toFloat()))
        return p ?: log.camera ?: Pose4.ORIGIN
    }

    private fun tour(): Beat {
        val d = door; val w = wall; val placed = house.placement(L.PAINTING)
        if (d == null || w == null || placed == null) return Beat.Follow(L.RACHEL, L.LEAD_ON, L.THERE)
        if (!log.framed(w.id)) return Beat.AlignByTap(w.id, L.PAINTING, setOf(SurfaceKind.WALL), L.FIND_PAINTING)
        val route = house.route(d.id, w.id, env.now()) ?: return Beat.Follow(L.RACHEL, L.LEAD_ON, L.THERE)
        routesUsed.add(route)
        val legs = route.legs.mapIndexed { i, leg ->
            val last = i == route.legs.lastIndex
            val spot = house.spot(leg.to)!!
            val stop = if (last) facingPainting(w, placed.pose) else standAt(spot)
            // Rule 5: a rough leg (or a hop across a room, or one whose frame is lost) isn't walked: she meets you there.
            val path = if (leg.rough || leg.inRoom) null else log.sessionPath(leg.from, leg.path)
            val isBench = spot.kind == SpotKind.BENCH || spot.kind == SpotKind.TABLE
            val line = when {
                last -> null
                isBench -> if (spot.room?.let { house.room(it)?.kind } == RoomKind.KITCHEN) L.NICE_KITCHEN else L.NICE_SPOT
                else -> null
            }
            TourLeg(path, stop ?: log.camera ?: Pose4.ORIGIN, line, looks = isBench && !last, hearts = last)
        }
        return Beat.Tour(L.RACHEL, legs, L.MEET_YOU)
    }

    private fun standAt(spot: Spot): Pose4? = log.toSession(spot.id, Pose4(0f, spot.floorY, 0f, 0f))

    /** About a metre out from the painting, a little to one side, facing it. */
    private fun facingPainting(w: Spot, painting: Pose4): Pose4? {
        val inSpot = painting.then(Pose4(0.4f, 0f, 1.0f, PI.toFloat()))
        return log.toSession(w.id, Pose4(inSpot.x, w.floorY, inSpot.z, inSpot.yaw))
    }

    private fun leavePath(): List<com.rockhard.blocker.homevisits.house.Vec3>? {
        val d = door ?: return null
        val w = wall ?: return null
        val route = house.route(w.id, d.id, env.now()) ?: return null
        routesUsed.add(route)
        val first = route.legs.firstOrNull() ?: return null
        if (first.rough || first.inRoom) return null
        return log.sessionPath(first.from, first.path)
    }

    companion object {
        /** Where the door is in the FRONT_DOOR spot's frame: about 0.6 m ahead, its face looking back in. */
        val DOOR_IN_SPOT = Pose4(0f, 0f, 0.6f, PI.toFloat())

        /** Whether the home screen should ring the doorbell. */
        fun wanted(save: HomeSave) = !save.story.isSet(S.FIRST_VISIT_DONE)
    }
}
