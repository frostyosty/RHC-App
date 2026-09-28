package com.rockhard.blocker.homevisits.story

import com.rockhard.blocker.homevisits.house.ItemId
import com.rockhard.blocker.homevisits.house.RoomKind
import com.rockhard.blocker.homevisits.house.SpotId
import com.rockhard.blocker.homevisits.house.SpotKind
import com.rockhard.blocker.homevisits.house.SurfaceKind

typealias CharId = String
typealias Route = List<SpotId>
typealias Where = SpotId // Simplified for the skeleton

data class Hint(val text: String, val waitSeconds: Int)
data class SurfaceTap(val kind: SurfaceKind, val x: Float, val y: Float, val z: Float)
data class Line(val speaker: CharId, val text: String)

sealed interface Beat {
    data class Say(val who: CharId?, val text: String, val answers: List<String>) : Beat
    data class Sound(val name: String) : Beat
    data class GoTo(val spot: SpotId, val route: Route?, val line: String) : Beat
    data class GoNew(val want: SpotKind, val room: RoomKind?, val line: String) : Beat
    data class FindSurface(
        val kinds: Set<SurfaceKind>,
        val heights: ClosedFloatingPointRange<Float>?,
        val line: String,
        val hints: List<Hint>
    ) : Beat
    data class Show(val item: ItemId, val at: Where) : Beat
    data class Hold(val item: ItemId?) : Beat
    data class Unwrap(val item: ItemId) : Beat
    data class Hang(val item: ItemId) : Beat
    data class Arrive(val who: CharId, val at: SpotId) : Beat
    data class Tour(val who: CharId, val route: Route, val stops: List<SpotId>) : Beat
    data class Talk(val lines: List<Line>) : Beat
    data class Leave(val who: CharId) : Beat
    data class AlignByTap(val spot: SpotId, val item: ItemId?) : Beat
}

sealed interface BeatResult {
    data class Done(val answer: Int? = null, val tap: SurfaceTap? = null, val spot: SpotId? = null) : BeatResult
    object Declined : BeatResult
    data class Failed(val why: String) : BeatResult
    object Interrupted : BeatResult
}

interface Director {
    fun next(): Beat
    fun done(beat: Beat, result: BeatResult)
}
