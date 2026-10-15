package com.rockhard.blocker.homevisits.story

import com.rockhard.blocker.homevisits.house.ItemId
import com.rockhard.blocker.homevisits.house.Pose4
import com.rockhard.blocker.homevisits.house.RoomKind
import com.rockhard.blocker.homevisits.house.SpotId
import com.rockhard.blocker.homevisits.house.SpotKind
import com.rockhard.blocker.homevisits.house.SurfaceKind
import com.rockhard.blocker.homevisits.house.Vec3

typealias CharId = String

/*
 * The beat contract (04). The director hands the app one beat at a time; the
 * app acts it out and hands back a result. Every position in a beat or a
 * result is in the AR session's frame (the camera's world): the director turns
 * the map's per-spot frames into session positions through `SessionLog`, so
 * runners never see spot frames and never write to the map.
 *
 * Heights in `FindSurface` are above the floor. Yaw follows `Pose4`: something
 * at yaw faces (sin yaw, 0, cos yaw).
 */

/**
 * Escalating help: shown `waitSeconds` after the beat starts. `widen` replaces
 * the heights a surface may be at; `instant` lets the player tap anywhere
 * (Instant Placement, or the kit's other fallbacks).
 */
data class Hint(
    val text: String,
    val waitSeconds: Int,
    val widen: ClosedFloatingPointRange<Float>? = null,
    val instant: Boolean = false,
)

/**
 * A surface the player tapped. `pose` is the point on it; on a wall its yaw
 * faces out of the wall, on anything flat it faces the way the camera did.
 * `camera` is where the phone was at the tap (a new spot's frame), and
 * `aboveFloor` is the tap's height over the floor when the floor was known.
 */
data class SurfaceTap(
    val kind: SurfaceKind,
    val pose: Pose4,
    val camera: Pose4,
    val aboveFloor: Float?,
    val floorY: Float?,
    /** The tap came from a widened range or Instant Placement rather than what was asked for. */
    val fallback: Boolean = false,
    val halfWidth: Float = 0.3f,
    val halfDepth: Float = 0.3f,
)

data class Line(val speaker: CharId?, val text: String)

/** Where an item appears. */
sealed interface Where {
    data class At(val pose: Pose4) : Where
    /** On the floor, `min`–`max` metres ahead of the camera, facing the player. */
    data class FloorAhead(val min: Float, val max: Float) : Where
}

/**
 * One stretch of a tour: walk `path` (null: "I'll meet you there!") to `stop`,
 * then say `line` there. The path is the camera's, about 1.4 m up; the runner
 * puts feet on the floor. `stop` is on the floor.
 */
data class TourLeg(val path: List<Vec3>?, val stop: Pose4, val line: String?, val looks: Boolean = false, val hearts: Boolean = false)

sealed interface Beat {
    /** A line with answer buttons; one answer means "tap to go on". `input` adds a text box (Done.text). */
    data class Say(val who: CharId?, val text: String, val answers: List<String>, val input: Boolean = false) : Beat
    data class Sound(val name: String) : Beat

    /**
     * Go to a known place. `path` (session frame) is the known way there: the
     * arrow points along it with `nextWaypoint`, never straight at the place.
     * Null when no part of the way can be placed. The player taps `arrive`.
     */
    data class GoTo(val spot: SpotId, val path: List<Vec3>?, val line: String, val arrive: String, val sound: String? = null) : Beat

    /** Go somewhere the map doesn't know yet; the player taps `arrive` there. Done.at is the camera then. */
    data class GoNew(val want: SpotKind, val room: RoomKind?, val line: String, val arrive: String, val tip: String? = null) : Beat

    /** Tap a surface of one of `kinds`. `prompt` shows once the player stops and surfaces glow. */
    data class FindSurface(
        val kinds: Set<SurfaceKind>,
        val heights: ClosedFloatingPointRange<Float>?,
        val line: String,
        val prompt: String,
        val hints: List<Hint>,
        val wayOut: String? = null,
    ) : Beat

    /** An item appears. With `take`, the player taps it to pick it up. Done.at is where it appeared. */
    data class Show(
        val item: ItemId,
        val at: Where,
        val line: String?,
        val hints: List<Hint> = emptyList(),
        val wayOut: String? = null,
        val take: Boolean = false,
        val bounce: Boolean = false,
        val found: String? = null,
    ) : Beat

    /** What the player carries at the bottom of the screen; null puts it down. */
    data class Hold(val item: ItemId?) : Beat

    /** Tap three times (or swipe) to tear the paper; then `into` shows big, with the card. */
    data class Unwrap(val item: ItemId, val into: ItemId, val card: String) : Beat

    /** Carry `item` to a wall and hang it: tap, drag up or down, [Looks good]. Done.tap has its final pose. */
    data class Hang(val item: ItemId, val line: String, val prompt: String, val hints: List<Hint>, val done: String) : Beat

    /** Nothing asked of the player for a moment. */
    data class Pause(val seconds: Int, val line: String?, val sparkleOn: ItemId? = null) : Beat

    data class Arrive(val who: CharId, val at: Pose4, val clip: String = "Wave") : Beat

    /** A visitor walks ahead of the player along the legs, 1.5–2.5 m ahead, waiting at each stop. */
    data class Tour(val who: CharId, val legs: List<TourLeg>, val followLine: String) : Beat

    /** A visitor follows the player's trail, 1.5 m behind, until they tap `arrive` (no known way). */
    data class Follow(val who: CharId, val line: String, val arrive: String) : Beat

    data class Talk(val lines: List<Line>) : Beat // step 07 replaces this with a Conversation

    /** The visitor waves and walks off along `path` (or just turns), fading out after a few steps. */
    data class Leave(val who: CharId, val path: List<Vec3>?) : Beat

    /** Frames were lost: one tap on the thing puts its spot back (05 explains why one tap is enough). */
    data class AlignByTap(val spot: SpotId, val item: ItemId?, val kinds: Set<SurfaceKind>, val line: String) : Beat

    /** An item shown at a place, and out of the player's hands if they were carrying it. */
    data class Place(val item: ItemId, val at: Pose4) : Beat

    /** AR closes; the home screen shows `card`. */
    data class End(val card: String, val finished: Boolean) : Beat
}

sealed interface BeatResult {
    data class Done(
        val answer: Int? = null,
        val tap: SurfaceTap? = null,
        val spot: SpotId? = null,
        /** The camera, or where an item appeared, in the session frame. */
        val at: Pose4? = null,
        val text: String? = null,
    ) : BeatResult
    object Declined : BeatResult      // the player took the way out ("Not now", "Somewhere else")
    data class Failed(val why: String) : BeatResult
    object Interrupted : BeatResult   // the app was paused mid-beat
}

/** Decides the next beat from the save and the session, and hears how each went. */
interface Director {
    /** Null when there's nothing to do (the visit is over). */
    fun next(): Beat?
    fun done(beat: Beat, result: BeatResult)
    /** The app streams these while beats run (pose and tracking about 5 times a second, pauses). */
    fun observe(o: Observation)
}
