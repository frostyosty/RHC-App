package com.rockhard.blocker.homevisits.mind

import com.rockhard.blocker.homevisits.HomeClock
import com.rockhard.blocker.homevisits.story.CharId
import kotlin.math.abs
import kotlin.math.sign

/** How what people say travels: hearing a fact, passing it on, and balance between friends. */
object Gossip {
    /** Δaffinity(listener → subject) = K_ABOUT × sentiment × belief × relevance (× stability when it settles). */
    const val K_ABOUT = 25f
    /** Hearing what someone said or did about you. */
    const val K_SUBJECT = 20f
    /** Hearing what someone said or did about a friend (or, at half strength and reversed, an enemy). */
    const val K_SIDE = 8f
    const val SIDE_AT = 20f
    /** The tuning knob for how fast news travels (Ask in step 06: "gentle, with some friction"). */
    const val RATE = 1f
    const val PASS_AT = 0.1f
    const val PASS_MAX = 2
    /** Off-screen, characters this familiar can tell how the other feels about people. */
    const val READS_VIEWS_AT = 40f
    const val BALANCE_AT = 30f
    /** The nudge a day at full strength. */
    const val BALANCE_RATE = 0.5f
    const val MEET_FAMILIARITY = 2f
}

/** 0.2 to 1: how much [p] cares about [x] and the topic. */
internal fun Mind.relevance(p: CharId, x: Who, tags: List<Tag>): Float {
    if (x.key == p) return 1f // it's about them
    val r = relationship(p, x)
    val topic = tags.maxOfOrNull { abs(cast[p].taste(it)) } ?: 0f
    return (0.4f + abs(r.affinity) / 250f + r.familiarity / 250f + 0.2f * topic).coerceIn(0.2f, 1f)
}

private fun Mind.belief(p: CharId, w: Who?) = if (w == null) 1f else (relationship(p, w).trust / 100f).coerceIn(0f, 1f)

/**
 * [p] perceives [fact] through [e]: from whoever said it ([Event.by]), or by seeing it. Their view of the
 * subject moves, their view of whoever said or did it moves, and gossip pleases people who dislike the subject.
 */
internal fun Mind.hear(p: CharId, fact: Fact, e: Event, out: Pending) {
    val teller = e.by
    if (!know(Who.Char(p), fact, teller, e.at.day)) return
    val s = fact.sentiment
    if (s == 0f) return
    val x = fact.about
    val b = fact.by
    if (b == null) { // plain news: only luck
        if (x != null && e.kind != EventKind.LIFE_EVENT) luck(p, x, s, e, out)
        return
    }
    val told = e.act == Act.TELL
    val believe = belief(p, teller.takeIf { e.kind == EventKind.ACTION })
    if (x != null && x.key != p && x != b && x != teller)
        out.direct += Delta(p, x, Field.AFFINITY, Gossip.K_ABOUT * s * believe * relevance(p, x, fact.tags), e.id)
    if (x != null && b.key != p && b != x) {
        val sure = if (told && teller != b) believe else 1f
        if (x.key == p) {
            // Said to their face: the action rules already did this.
            if (told || teller != b) out.direct += Delta(p, b, Field.AFFINITY, Gossip.K_SUBJECT * s * sure, e.id)
        } else {
            val a = aff(p, x)
            if (a >= Gossip.SIDE_AT) out.direct += Delta(p, b, Field.AFFINITY, Gossip.K_SIDE * s * a / 100f * sure, e.id)
            else if (a <= -Gossip.SIDE_AT) out.direct += Delta(p, b, Field.AFFINITY, -0.5f * Gossip.K_SIDE * s * (-a / 100f) * sure, e.id)
        }
    }
    if (told && teller != null && teller.key != p && s < 0f && x != null && x.key != p) {
        val a = aff(p, x)
        if (a <= -Gossip.SIDE_AT) feel(p, EmotionKind.JOY, 0.3f * -s * (-a / 100f), e, out, teller)
    }
}

/**
 * [teller] passes on to [listener] up to [Gossip.PASS_MAX] facts the listener doesn't know, chosen by
 * salience × relevance × (1 − discretion) × how close they are. Maya spreads news in a day; Ruth doesn't.
 */
internal fun Mind.passOn(teller: CharId, listener: CharId, at: HomeClock) {
    val known = state.known[teller] ?: return
    val heard = state.known[listener].orEmpty()
    val d = cast[teller].personality.discretion
    val closeness = 0.5f + aff(teller, Who.Char(listener)) / 200f
    val picks = known.entries
        .filter { (id, _) -> id !in heard }
        .mapNotNull { (id, h) ->
            val f = state.facts[id] ?: return@mapNotNull null
            // Not back to whoever said it, not about themselves, and not their own words to the person they were about.
            if (f.by?.key == listener || f.about?.key == teller || (f.by?.key == teller && f.about?.key == listener)) return@mapNotNull null
            val rel = f.about?.let { relevance(listener, it, f.tags) } ?: 0.4f
            var score = h.salience * rel * (1f - d) / 2f * closeness * Gossip.RATE
            if (f.secret) score *= (1f - d) / 2f
            if (score >= Gossip.PASS_AT) f to score else null
        }
        .sortedWith(compareByDescending<Pair<Fact, Float>> { it.second }.thenBy { it.first.id })
        .take(Gossip.PASS_MAX)
    for ((f, _) in picks)
        perceive(Event(EventKind.ACTION, at, by = Who.Char(teller), to = Who.Char(listener), act = Act.TELL, fact = f,
            tags = f.tags, witnesses = listOf(listener), playerSaw = false))
}

internal fun Mind.meetOff(a: CharId, b: CharId, at: HomeClock) {
    for ((x, y) in listOf(a to b, b to a)) {
        val k = relKey(x, Who.Char(y))
        state.relationships[k] = relationship(x, Who.Char(y)).let { it.with(Field.FAMILIARITY, it.familiarity + Gossip.MEET_FAMILIARITY) }
    }
    state.lastTogether[pairKey(a, b)] = at.day
    passOn(a, b, at)
    passOn(b, a, at)
}

/** Until 08 decides who meets whom: sociable, close pairs meet most days. */
internal fun Mind.defaultMeetings(day: Int): List<Pair<CharId, CharId>> {
    val r = rng.stream("mind.meet", day)
    val out = mutableListOf<Pair<CharId, CharId>>()
    val ids = cast.ids
    for (i in ids.indices) for (j in i + 1 until ids.size) {
        val a = ids[i]
        val b = ids[j]
        val e = (cast[a].personality.extraversion + cast[b].personality.extraversion) / 2f
        val close = (aff(a, Who.Char(b)) + aff(b, Who.Char(a))) / 2f
        val p = (0.05f + 0.3f * (e + 1f) / 2f + 0.4f * maxOf(0f, close) / 100f).coerceIn(0f, 0.9f)
        if (r.nextFloat() < p) out += a to b
    }
    return out
}

internal fun Mind.offscreen(day: Int) {
    val at = HomeClock(day, 12 * 60)
    for ((a, b) in meetings?.invoke(day) ?: defaultMeetings(day)) if (a in cast && b in cast && a != b) meetOff(a, b, at)
}

/**
 * Heider's balance: people tend to like their friends' friends and cool on their enemies' friends.
 * For each strong triangle out of balance (the player counts as one of the three), its weakest side
 * is nudged towards balance, about [Gossip.BALANCE_RATE] a day at full strength.
 */
internal fun Mind.balance() {
    val nudges = mutableMapOf<String, Float>()
    val all: List<Who> = cast.ids.map { Who.Char(it) } + Who.Player
    for (a in cast.ids) for (b in all) {
        if (b.key == a) continue
        val ab = aff(a, b)
        if (abs(ab) < Gossip.BALANCE_AT) continue
        for (c in all) {
            if (c.key == a || c == b || (b == Who.Player && c == Who.Player)) continue
            val bc: Float = when {
                b == Who.Player -> if ("$a>${c.key}" in state.stanceKnown) state.playerStance[c.key] ?: 0f else continue
                relationship(a, b).familiarity >= Gossip.READS_VIEWS_AT -> aff((b as Who.Char).id, c)
                else -> continue
            }
            if (abs(bc) < Gossip.BALANCE_AT) continue
            val want = ab.sign * bc.sign
            val ac = aff(a, c)
            val balanced = ac.sign == want && abs(ac) >= 5f
            if (balanced || abs(ac) >= minOf(abs(ab), abs(bc))) continue
            val k = relKey(a, c)
            nudges[k] = (nudges[k] ?: 0f) + Gossip.BALANCE_RATE * want * minOf(abs(ab), abs(bc)) / 100f
        }
    }
    for ((k, v) in nudges) {
        val r = state.relationships[k] ?: continue
        state.relationships[k] = r.with(Field.AFFINITY, r.affinity + v)
    }
}
