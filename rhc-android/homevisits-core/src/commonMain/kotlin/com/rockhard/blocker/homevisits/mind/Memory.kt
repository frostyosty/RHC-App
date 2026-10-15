package com.rockhard.blocker.homevisits.mind

import com.rockhard.blocker.homevisits.story.CharId
import kotlin.math.abs
import kotlin.math.pow

/**
 * Something a character saw or was told about. [salience] (0 to 1) comes from the emotion it caused and
 * fades; [impact] is how far it moved their affinity for each person (by [Who.key]), which powers `why`.
 */
class Memory(val event: Event, var salience: Float, val peak: Float, val impact: MutableMap<String, Float>) {
    override fun toString() = "Memory(${event.kind} ${event.act ?: ""} by ${event.by} about ${event.about}, salience=$salience, impact=$impact)"
}

/** Knowing a fact: who told them (null if they saw it), when, and how much it's on their mind. */
data class Heard(val from: Who?, val day: Int, var salience: Float)

object Memories {
    /** Days for salience to halve: 14 × (0.5 + peak), so strong memories last longer. */
    const val HALF_LIFE_DAYS = 14f
    const val FORGET_BELOW = 0.01f
    const val MIN_SALIENCE = 0.05f
    /** The event log keeps this many; the oldest trivial ones go first. */
    const val LOG_CAP = 2000
    const val TRIVIAL = 0.1f
}

internal fun Mind.remember(p: CharId, e: Event, salience: Float, impact: Map<String, Float>) {
    val moved = impact.values.maxOfOrNull { abs(it) } ?: 0f
    val s = maxOf(salience, (moved / Settle.CAP).coerceAtMost(1f))
    if (s < Memories.MIN_SALIENCE) return
    val list = state.memories.getOrPut(p) { mutableListOf() }
    val old = list.firstOrNull { it.event.id == e.id }
    if (old != null) {
        old.salience = maxOf(old.salience, s)
        for ((k, v) in impact) old.impact[k] = (old.impact[k] ?: 0f) + v
    } else list += Memory(e, s, s, impact.toMutableMap())
}

internal fun Mind.decayMemories() {
    for (c in cast.ids) {
        val list = state.memories[c] ?: continue
        for (m in list) m.salience *= 0.5f.pow(1f / (Memories.HALF_LIFE_DAYS * (0.5f + m.peak)))
        list.removeAll { it.salience < Memories.FORGET_BELOW }
    }
    for (k in state.known.values) for (h in k.values) h.salience *= 0.5f.pow(1f / Memories.HALF_LIFE_DAYS)
}

internal fun Mind.trimLog() {
    val log = state.log
    if (log.size <= Memories.LOG_CAP) return
    val strong = state.memories.values.flatten().filter { it.salience >= Memories.TRIVIAL }.map { it.event.id }.toSet()
    val over = log.size - Memories.LOG_CAP
    val drop = log.filter { it.id !in strong }.take(over).map { it.id }.toMutableSet()
    if (drop.size < over) drop += log.filter { it.id !in drop }.take(over - drop.size).map { it.id }
    log.removeAll { it.id in drop }
}

internal fun Mind.whyMemories(from: CharId, to: Who, n: Int): List<Memory> =
    state.memories[from].orEmpty()
        .filter { (it.impact[to.key] ?: 0f) != 0f }
        .sortedWith(compareByDescending<Memory> { abs(it.impact.getValue(to.key)) * (0.5f + it.salience) }.thenBy { it.event.id })
        .take(n)

/** [who] learns [fact] from [from] (null: saw it). Returns true if it's new to them. */
internal fun Mind.know(who: Who, fact: Fact, from: Who?, day: Int): Boolean {
    val k = state.known.getOrPut(who.key) { mutableMapOf() }
    if (fact.id in k) return false
    k[fact.id] = Heard(from, day, maxOf(0.3f, abs(fact.sentiment)))
    val about = fact.about
    if (fact.by == Who.Player && about is Who.Char && who is Who.Char) state.stanceKnown += "${who.id}>${about.id}"
    return true
}
