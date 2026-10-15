package com.rockhard.blocker.homevisits.mind

import com.rockhard.blocker.homevisits.story.CharId
import kotlin.math.abs

enum class Field(val min: Float, val max: Float) {
    AFFINITY(-100f, 100f), TRUST(-100f, 100f), RESPECT(-100f, 100f), FAMILIARITY(0f, 100f)
}

/** One character's view of another (or of the player). Directed: Rachel's view of Alfred isn't his of her. */
data class Relationship(val affinity: Float, val trust: Float, val respect: Float, val familiarity: Float) {
    operator fun get(f: Field): Float = when (f) {
        Field.AFFINITY -> affinity
        Field.TRUST -> trust
        Field.RESPECT -> respect
        Field.FAMILIARITY -> familiarity
    }

    fun with(f: Field, v: Float): Relationship {
        val c = v.coerceIn(f.min, f.max)
        return when (f) {
            Field.AFFINITY -> copy(affinity = c)
            Field.TRUST -> copy(trust = c)
            Field.RESPECT -> copy(respect = c)
            Field.FAMILIARITY -> copy(familiarity = c)
        }
    }

    fun clamped(): Relationship = Field.entries.fold(this) { r, f -> r.with(f, r[f]) }

    companion object {
        val STRANGERS = Relationship(0f, 0f, 0f, 0f)
    }
}

/** How emotions settle into relationships at the end of a scene, and how relationships drift by the day. */
object Settle {
    /** No number moves more than this in one scene: one bad visit can't end a friendship. */
    const val CAP = 15f
    const val FAMILIARITY_PER_MINUTE = 0.3f
    /** A peacemaker in the room doubles the good between the others and halves the bad. */
    const val PEACEMAKER_UP = 2f
    const val PEACEMAKER_DOWN = 0.5f
    /** Liking something in your home: people warm to those who share their taste. */
    const val SHARED_TASTE = 8f
    const val DRIFT_DOWN = 0.01f
    const val DRIFT_UP = 0.01f
    const val DRIFT_UP_AGREEABLE = 0.02f
    const val FAMILIARITY_FADE = 0.2f
    const val FADE_AFTER_DAYS = 14
    const val STANCE_FADE = 0.02f

    /** Per unit of intensity: (field, change). The table in step 06. */
    internal fun effects(kind: EmotionKind, hasItem: Boolean): List<Pair<Field, Float>> = when (kind) {
        EmotionKind.GRATITUDE -> listOf(Field.AFFINITY to 8f, Field.TRUST to 3f)
        EmotionKind.ADMIRATION -> listOf(Field.RESPECT to 8f, Field.AFFINITY to 3f)
        EmotionKind.REPROACH -> listOf(Field.RESPECT to -8f, Field.TRUST to -2f)
        EmotionKind.ANGER -> listOf(Field.AFFINITY to -10f, Field.TRUST to -4f)
        EmotionKind.JOY, EmotionKind.PRIDE -> listOf(Field.AFFINITY to 2f)
        EmotionKind.HAPPY_FOR, EmotionKind.SORRY_FOR -> listOf(Field.AFFINITY to 2f)
        EmotionKind.RESENTMENT, EmotionKind.GLOATING -> listOf(Field.AFFINITY to -3f)
        EmotionKind.DISAPPOINTMENT -> listOf(Field.TRUST to -5f)
        EmotionKind.LIKING -> if (hasItem) listOf(Field.AFFINITY to SHARED_TASTE) else emptyList()
        EmotionKind.DISLIKING -> if (hasItem) listOf(Field.AFFINITY to -2f) else emptyList()
        else -> emptyList()
    }
}

private class Acc(val from: CharId, val to: Who, val field: Field) {
    var raw = 0f
    val causes = mutableMapOf<Long, Float>()
}

/**
 * Settles [pending] into relationships and memories. Every change is multiplied by stability
 * (1 − familiarity / 200), positive ones by (1 + 0.3 agreeableness) and negative ones by
 * (1 + 0.3 neuroticism), then capped at [Settle.CAP].
 */
internal fun Mind.settle(pending: Pending, scene: Scene?): List<Change> {
    val acc = mutableMapOf<String, Acc>()
    fun add(from: CharId, to: Who, f: Field, amount: Float, cause: Long) {
        if (amount == 0f || to.key == from) return
        val a = acc.getOrPut("${relKey(from, to)}#${f.name}") { Acc(from, to, f) }
        a.raw += amount
        if (cause != 0L) a.causes[cause] = (a.causes[cause] ?: 0f) + amount
    }
    for ((p, em) in pending.emotions) {
        val t = em.toward ?: continue
        for ((f, k) in Settle.effects(em.kind, em.item != null)) add(p, t, f, k * em.intensity, em.cause)
    }
    for (d in pending.direct) add(d.from, d.to, d.field, d.amount, d.cause)
    if (scene != null) {
        val end = scene.lastMinute
        val cs = scene.everyone
        fun span(c: CharId) = scene.joinedAt.getValue(c) to (scene.leftAt[c] ?: end)
        for (c in cs) {
            val (j, l) = span(c)
            add(c, Who.Player, Field.FAMILIARITY, Settle.FAMILIARITY_PER_MINUTE * (l - j), 0L)
            for (o in cs) {
                if (o == c) continue
                val (oj, ol) = span(o)
                val overlap = minOf(l, ol) - maxOf(j, oj)
                if (overlap > 0) add(c, Who.Char(o), Field.FAMILIARITY, Settle.FAMILIARITY_PER_MINUTE * overlap, 0L)
            }
        }
    }

    val changes = mutableListOf<Change>()
    val impacts = mutableMapOf<String, MutableMap<String, Float>>() // "p#event" -> who -> affinity moved
    for (key in acc.keys.sorted()) {
        val a = acc.getValue(key)
        val per = cast[a.from].personality
        val r = relationship(a.from, a.to)
        var v = a.raw
        if (a.field != Field.FAMILIARITY) {
            v *= 1f - r.familiarity / 200f
            v *= if (v > 0f) 1f + 0.3f * per.agreeableness else 1f + 0.3f * per.neuroticism
            if (scene != null && a.to is Who.Char && peacemakerPresent(scene, a.from, a.to.id))
                v *= if (v > 0f) Settle.PEACEMAKER_UP else Settle.PEACEMAKER_DOWN
            // Soft limit: a push further towards an extreme shrinks as it gets there, so the cast doesn't all end up at 100.
            val now = r[a.field] / 100f
            if (v * now > 0f) v *= 1f - now * now
        }
        v = v.coerceIn(-Settle.CAP, Settle.CAP)
        val before = r[a.field]
        val next = r.with(a.field, before + v)
        state.relationships[relKey(a.from, a.to)] = next
        val after = next[a.field]
        if (after != before) changes += Change(a.from, a.to, a.field, before, after)
        if (a.field == Field.AFFINITY && a.raw != 0f) {
            val scale = (after - before) / a.raw
            for ((cause, amt) in a.causes) {
                val m = impacts.getOrPut("${a.from}#$cause") { mutableMapOf() }
                m[a.to.key] = (m[a.to.key] ?: 0f) + amt * scale
            }
        }
    }

    // Memories: salience from the strongest emotion (or change) each event caused in each perceiver.
    val salience = mutableMapOf<String, Float>()
    for ((p, em) in pending.emotions) {
        val k = "$p#${em.cause}"
        salience[k] = maxOf(salience[k] ?: 0f, em.intensity)
    }
    for (d in pending.direct) {
        val k = "${d.from}#${d.cause}"
        salience[k] = maxOf(salience[k] ?: 0f, (abs(d.amount) / Settle.CAP).coerceAtMost(1f))
    }
    val done = mutableSetOf<String>()
    for ((p, e) in pending.perceived) {
        val k = "$p#${e.id}"
        if (!done.add(k)) continue
        remember(p, e, salience[k] ?: 0f, impacts[k].orEmpty())
    }
    return changes
}

private fun Mind.peacemakerPresent(scene: Scene, from: CharId, to: CharId): Boolean = scene.everyone.any { q ->
    q != from && q != to && cast[q].peacemaker && aff(q, Who.Char(from)) >= 0f && aff(q, Who.Char(to)) >= 0f
}

/**
 * A day's drift. Affinity above its bond's resting point drifts 1% of the way back (friends drift apart
 * without contact); below it, (1% + 2% × agreeableness), so grudges fade, faster for forgiving people.
 * Trust and respect drift at half those rates. Familiarity fades after two weeks apart.
 */
internal fun Mind.dailyDrift(day: Int) {
    for (key in state.relationships.keys.toList()) {
        val from = key.substringBefore('>')
        val to = Who.of(key.substringAfter('>'))
        val r = state.relationships.getValue(key)
        val rest = bondKind(from, to).rest
        val up = (Settle.DRIFT_UP + Settle.DRIFT_UP_AGREEABLE * cast[from].personality.agreeableness).coerceAtLeast(0.002f)
        fun drift(v: Float, half: Boolean): Float {
            val rate = (if (v > rest) Settle.DRIFT_DOWN else up) * (if (half) 0.5f else 1f)
            return v + (rest - v) * rate
        }
        val apart = day - (state.lastTogether[pairKey(from, to.key)] ?: 0)
        val fam = if (apart > Settle.FADE_AFTER_DAYS) r.familiarity - Settle.FAMILIARITY_FADE else r.familiarity
        state.relationships[key] = Relationship(drift(r.affinity, false), drift(r.trust, true), drift(r.respect, true), fam).clamped()
    }
    for (c in state.playerStance.keys.toList()) state.playerStance[c] = state.playerStance.getValue(c) * (1f - Settle.STANCE_FADE)
}
