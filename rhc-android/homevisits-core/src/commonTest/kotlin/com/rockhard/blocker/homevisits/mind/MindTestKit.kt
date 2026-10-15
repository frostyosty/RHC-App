package com.rockhard.blocker.homevisits.mind

import com.rockhard.blocker.homevisits.HomeClock
import com.rockhard.blocker.homevisits.Rng
import com.rockhard.blocker.homevisits.house.RoomKind
import com.rockhard.blocker.homevisits.mind.TestCast.ALFRED
import com.rockhard.blocker.homevisits.mind.TestCast.MAYA
import com.rockhard.blocker.homevisits.mind.TestCast.RACHEL
import com.rockhard.blocker.homevisits.mind.TestCast.RUTH
import com.rockhard.blocker.homevisits.story.CharId

internal val rachel = Who.Char(RACHEL)
internal val alfred = Who.Char(ALFRED)
internal val maya = Who.Char(MAYA)
internal val ruth = Who.Char(RUTH)
internal val you = Who.Player

internal fun mind(seed: Long = 1L) = Mind(TestCast.cast, MindState(), Rng(seed))

internal fun Mind.set(from: CharId, to: Who, r: Relationship) { state.relationships[relKey(from, to)] = r }

/** A scene builder for tests: events a minute apart, everyone arriving at the start and leaving at the end. */
internal class SceneScript(val mind: Mind, val day: Int, var minute: Int) {
    fun at() = HomeClock(day, minute++)
    fun by(who: Who, a: SocialAction) = mind.perceive(a.event(who, at()))
    fun event(e: Event) = mind.perceive(e.copy(at = at()))
}

internal fun Mind.scene(day: Int, vararg who: CharId, place: RoomKind = RoomKind.LOUNGE, minutes: Int = 30, block: SceneScript.() -> Unit = {}): SceneSummary {
    val start = HomeClock(day, 15 * 60)
    advanceTo(start)
    startScene(who.toList(), place)
    val s = SceneScript(this, day, start.minute)
    for (c in who) s.event(Event(EventKind.ARRIVED, start, by = Who.Char(c)))
    s.block()
    s.minute = maxOf(s.minute, start.minute + minutes)
    for (c in who) s.event(Event(EventKind.LEFT, start, by = Who.Char(c)))
    return endScene()
}

internal fun SceneSummary.felt(who: CharId, kind: EmotionKind, toward: Who? = null): Float =
    emotions[who].orEmpty().filter { it.kind == kind && (toward == null || it.toward == toward) }.maxOfOrNull { it.intensity } ?: 0f
