package com.rockhard.blocker.homevisits.mind

import com.rockhard.blocker.homevisits.house.ItemId
import com.rockhard.blocker.homevisits.house.RoomKind
import com.rockhard.blocker.homevisits.story.CharId
import kotlin.math.abs

/**
 * The OCC emotions the engine uses, with their PAD values from Gebhard's ALMA model. [negative] means
 * it feels bad: a bad mood and neuroticism make these stronger.
 */
enum class EmotionKind(p: Float, a: Float, d: Float, val negative: Boolean) {
    JOY(0.4f, 0.2f, 0.1f, false),
    DISTRESS(-0.4f, -0.2f, -0.5f, true),
    HAPPY_FOR(0.4f, 0.2f, 0.2f, false),
    SORRY_FOR(-0.4f, -0.2f, -0.5f, true),
    RESENTMENT(-0.2f, -0.3f, -0.2f, true),
    GLOATING(0.3f, -0.3f, -0.1f, false),
    HOPE(0.2f, 0.2f, -0.1f, false),
    FEAR(-0.64f, 0.6f, -0.43f, true),
    RELIEF(0.2f, -0.3f, 0.4f, false),
    DISAPPOINTMENT(-0.3f, 0.1f, -0.4f, true),
    PRIDE(0.4f, 0.3f, 0.3f, false),
    SHAME(-0.3f, 0.1f, -0.6f, true),
    ADMIRATION(0.5f, 0.3f, -0.2f, false),
    REPROACH(-0.3f, -0.1f, 0.4f, true),
    LIKING(0.4f, 0.16f, -0.24f, false),
    DISLIKING(-0.4f, 0.2f, 0.1f, true),
    GRATITUDE(0.4f, 0.2f, -0.3f, false),
    ANGER(-0.51f, 0.59f, 0.25f, true);

    val pad = Pad(p, a, d)
}

/** One emotion felt in a scene: towards someone ([toward]) and/or about an [item], caused by event [cause]. */
data class Emotion(val kind: EmotionKind, val intensity: Float, val toward: Who?, val item: ItemId?, val cause: Long)

/** What a scene (or one off-screen event) has stirred up but not yet settled. */
internal class Pending {
    val emotions = mutableListOf<Pair<CharId, Emotion>>()
    val direct = mutableListOf<Delta>()
    val perceived = mutableListOf<Pair<CharId, Event>>()
    var playerSees = false
}

/** A change to a relationship number that doesn't go through an emotion (hearing gossip, being confided in). */
internal class Delta(val from: CharId, val to: Who, val field: Field, val amount: Float, val cause: Long)

/** Starting values from the step 06 tables; tune them with the soak. */
object Appraisal {
    const val NOVELTY_SEEN = 0.3f
    const val GIFT_ON_SHOW = 0.6f
    const val PROMINENT = 1.3f
    const val FLATTERY_TRUST = -30f
    const val MOOD_PULL = 0.3f
    const val GRUDGE_IMPACT = -3f
    const val GRUDGE_SALIENCE = 0.25f
    const val STRONG_REACTION = 0.5f
    const val CONCERN_STEP = 0.34f
    const val TENSION_EASE = 0.85f
    val PROMINENT_ROOMS = setOf(RoomKind.LOUNGE, RoomKind.ENTRY, RoomKind.KITCHEN)
}

/** How well [tags] match [who]'s tastes, −1 to 1: the strongest counts most (t1 + 0.3 t2 + 0.1 t3). */
internal fun CharSheet.tasteScore(tags: List<Tag>): Float {
    val v = tags.distinct().map { taste(it) }.sortedByDescending { abs(it) }
    var s = 0f
    val w = floatArrayOf(1f, 0.3f, 0.1f)
    for (i in 0 until minOf(3, v.size)) s += w[i] * v[i]
    return s.coerceIn(-1f, 1f)
}

/** The fact a social action about someone implies ("you stuck up for Alfred"), so it can travel. */
internal fun autoFact(e: Event): Fact? {
    if (e.kind != EventKind.ACTION || e.about == null || e.by == null || e.about == e.by) return null
    val s = when (e.act) {
        Act.PRAISE -> 0.5f
        Act.CREDIT -> 0.6f
        Act.DEFEND -> 0.8f
        Act.CRITICISE -> -0.6f
        Act.AGREE, Act.SIDE_WITH -> if (e.sentiment < 0f) -0.4f else if (e.sentiment > 0f) 0.3f else return null
        Act.DISAGREE -> if (e.sentiment < 0f) 0.3f else if (e.sentiment > 0f) -0.3f else return null
        else -> return null
    }
    return Fact("e${e.id}", by = e.by, about = e.about, sentiment = s * e.magnitude, tags = e.tags)
}

internal fun Mind.appraise(e: Event, perceivers: List<CharId>, out: Pending) {
    val fact = e.fact ?: autoFact(e)
    if (fact != null && fact.id !in state.facts) state.facts[fact.id] = fact
    val actor = e.by
    if (actor is Who.Char && fact != null) know(actor, fact, null, e.at.day)
    for (p in perceivers) {
        out.perceived += p to e
        val self = actor?.key == p
        if (self && e.kind != EventKind.ARRIVED && e.kind != EventKind.EXPECTED) continue
        when (e.kind) {
            EventKind.ARRIVED -> arrival(p, e, out)
            EventKind.MET -> met(p, e, out)
            EventKind.SAW_ITEM -> sawItem(p, e, out)
            EventKind.GIFT_ON_SHOW -> giftOnShow(p, e, out)
            EventKind.ACTION -> action(p, e, out)
            EventKind.LIFE_EVENT -> luck(p, e.about, e.sentiment * e.magnitude, e, out)
            EventKind.PLAYER_DAY -> if (e.sentiment > 0f && aff(p, Who.Player) > 0f)
                feel(p, EmotionKind.HAPPY_FOR, 0.3f * e.sentiment * e.magnitude, e, out, Who.Player)
            EventKind.EXPECTED -> if (self) expect(p, e, out)
            else -> {}
        }
        if (self) continue
        judgeStandards(p, e, out)
        concerns(p, e, out)
        if (fact != null) hear(p, fact, e, out)
    }
    if (e.act == Act.CHANGE_SUBJECT) easeTension(perceivers, out)
    if (out.playerSees) {
        if (fact != null) know(Who.Player, fact, e.by, e.at.day)
        if (e.act == Act.ASK_ABOUT && actor == Who.Player) (e.to as? Who.Char)?.let { c -> e.tags.forEach { learnTaste(c.id, it) } }
    }
    if (actor == Who.Player) playerActed(e, fact, perceivers)
}

/** Records [kind] in [p], scaled by mood and neuroticism, and lets it pull their mood. */
internal fun Mind.feel(p: CharId, kind: EmotionKind, raw: Float, e: Event, out: Pending, toward: Who? = null, item: ItemId? = null) {
    if (raw <= 0.01f || toward?.key == p) return
    val mood = mood(p)
    val n = cast[p].personality.neuroticism
    val scaled = if (kind.negative) raw * (1f + 0.5f * maxOf(0f, -mood.p)) * (1f + 0.3f * n)
    else raw * (1f + 0.25f * maxOf(0f, mood.p))
    val i = scaled.coerceIn(0f, 1f)
    out.emotions += p to Emotion(kind, i, toward, item, e.id)
    state.mood[p] = mood.towards(kind.pad, i * Appraisal.MOOD_PULL)
    if (out.playerSees && toward != null && i >= 0.2f) state.viewsKnownToPlayer += "$p>${toward.key}"
}

private fun Mind.hasGrudge(p: CharId, against: Who): Boolean =
    state.memories[p].orEmpty().any { (it.impact[against.key] ?: 0f) <= Appraisal.GRUDGE_IMPACT && it.salience >= Appraisal.GRUDGE_SALIENCE }

/** Seeing someone turn up: joy if they like them; distress (and anger, with a grudge) if they don't. */
private fun Mind.arrival(p: CharId, e: Event, out: Pending) {
    val x = e.by as? Who.Char ?: return
    val others: List<Who> = if (x.id == p) (state.scene?.present.orEmpty().filter { it != p }.map { Who.Char(it) } + Who.Player) else listOf(x)
    for (o in others) {
        val a = aff(p, o)
        if (a > 0f) feel(p, EmotionKind.JOY, a / 100f, e, out, o)
        else if (a < 0f) {
            feel(p, EmotionKind.DISTRESS, -a / 100f, e, out, o)
            if (hasGrudge(p, o)) feel(p, EmotionKind.ANGER, -a / 200f, e, out, o)
        }
    }
}

private fun Mind.met(p: CharId, e: Event, out: Pending) {
    val other = listOfNotNull(e.by, e.to).firstOrNull { it.key != p } ?: return
    val r = relationship(p, other)
    if (r.familiarity < 5f) out.direct += Delta(p, other, Field.FAMILIARITY, 5f - r.familiarity, e.id)
    feel(p, EmotionKind.JOY, 0.2f * (1f + 0.5f * cast[p].personality.extraversion), e, out, other)
}

/** Liking or disliking an item: taste match × novelty × (1 + 0.5 openness, if new). */
private fun Mind.sawItem(p: CharId, e: Event, out: Pending) {
    val item = e.item ?: return
    val sheet = cast[p]
    val seen = state.seenItems.getOrPut(p) { mutableSetOf() }
    val new = seen.add(item)
    val match = sheet.tasteScore(e.tags)
    val liking = match * (if (new) 1f + 0.5f * sheet.personality.openness else Appraisal.NOVELTY_SEEN)
    if (liking > 0f) feel(p, EmotionKind.LIKING, liking, e, out, Who.Player, item)
    else feel(p, EmotionKind.DISLIKING, -liking, e, out, Who.Player, item)
    if (e.chosenFor == p && liking > 0f) feel(p, EmotionKind.GRATITUDE, liking * trustFactor(p, Who.Player), e, out, Who.Player)
    if (e.giftFrom == p) giftOnShow(p, e, out)
    if (out.playerSees && abs(liking) >= Appraisal.STRONG_REACTION) {
        val tag = e.tags.distinct().maxByOrNull { abs(sheet.taste(it)) } ?: return
        if (sheet.taste(tag) == 0f) return
        val k = "$p>${tag.word}"
        val n = (state.strongReactions[k] ?: 0) + 1
        state.strongReactions[k] = n
        if (n >= 2) learnTaste(p, tag)
    }
}

private fun Mind.giftOnShow(p: CharId, e: Event, out: Pending) {
    if (e.giftFrom != p) return
    if (e.sentiment < 0f) { feel(p, EmotionKind.DISAPPOINTMENT, 0.3f, e, out, Who.Player); return }
    val i = Appraisal.GIFT_ON_SHOW * (if (e.room in Appraisal.PROMINENT_ROOMS) Appraisal.PROMINENT else 1f)
    feel(p, EmotionKind.GRATITUDE, i, e, out, Who.Player)
    feel(p, EmotionKind.PRIDE, i, e, out, Who.Player)
}

/** 0 to 1: how far [p] believes [w] (trust −100..100 mapped onto 0..1). */
internal fun Mind.trustFactor(p: CharId, w: Who) = ((relationship(p, w).trust + 100f) / 200f).coerceIn(0f, 1f)

private fun Mind.action(p: CharId, e: Event, out: Pending) {
    val actor = e.by ?: return
    val sheet = cast[p]
    val per = sheet.personality
    val isTo = e.to?.key == p
    val isAbout = e.about?.key == p
    val aboutMe = isAbout || (isTo && e.about == null)
    when (e.act) {
        Act.GREET -> if (isTo || e.to == null) feel(p, EmotionKind.JOY, 0.15f, e, out, actor)
        Act.THANK -> if (isTo) feel(p, EmotionKind.JOY, 0.3f, e, out, actor)
        Act.JOKE -> feel(p, EmotionKind.JOY, 0.25f * (1f + 0.5f * per.extraversion), e, out, actor)
        Act.TEASE -> if (isTo) {
            if (aff(p, actor) >= 30f) feel(p, EmotionKind.JOY, 0.2f, e, out, actor)
            else {
                feel(p, EmotionKind.DISTRESS, 0.2f, e, out, actor)
                feel(p, EmotionKind.ANGER, 0.15f * (1f + 0.5f * per.pride), e, out, actor)
            }
        }
        Act.COMFORT -> if (isTo) feel(p, EmotionKind.GRATITUDE, 0.5f, e, out, actor)
        Act.CONFIDE -> if (isTo) {
            feel(p, EmotionKind.JOY, 0.2f, e, out, actor)
            out.direct += Delta(p, actor, Field.TRUST, 3f, e.id)
            out.direct += Delta(p, actor, Field.FAMILIARITY, 2f, e.id)
        }
        Act.APOLOGISE -> if (isTo) apology(p, actor, e, out)
        Act.PRAISE -> if (aboutMe) praised(p, actor, e, out)
        else if (isTo && e.tags.isNotEmpty()) {
            val m = sheet.tasteScore(e.tags)
            if (m > 0f) feel(p, EmotionKind.JOY, 0.3f * m, e, out, actor) // a taste in common
        }
        Act.CRITICISE -> if (aboutMe) {
            val i = (0.5f + 0.5f * per.pride) * (1f + 0.5f * per.neuroticism)
            feel(p, EmotionKind.DISTRESS, i, e, out, actor)
            feel(p, EmotionKind.ANGER, i, e, out, actor)
        }
        Act.AGREE, Act.SIDE_WITH -> {
            if (isTo) {
                feel(p, EmotionKind.JOY, 0.25f, e, out, actor)
                if (e.about != null && e.sentiment < 0f)
                    feel(p, EmotionKind.GRATITUDE, if (e.act == Act.SIDE_WITH) 0.4f else 0.2f, e, out, actor)
            }
            if (isAbout && e.sentiment < 0f) feel(p, EmotionKind.ANGER, 0.3f * (1f + 0.5f * per.pride), e, out, actor)
        }
        Act.DISAGREE -> if (isTo) feel(p, EmotionKind.DISTRESS, 0.1f * (1f + 0.5f * per.pride), e, out, actor)
        Act.DEFEND -> {
            if (isTo) {
                val x = e.about
                if (x != null) {
                    val a = aff(p, x)
                    // Sticking up for someone they dislike rankles, less so for forgiving people.
                    if (a < 0f) feel(p, EmotionKind.ANGER, (-a / 100f) * 0.4f * (1f - 0.5f * per.agreeableness), e, out, actor)
                }
            }
            if (isAbout) feel(p, EmotionKind.GRATITUDE, 0.6f, e, out, actor)
        }
        Act.CREDIT -> if (isAbout) {
            feel(p, EmotionKind.GRATITUDE, 0.4f, e, out, actor)
            feel(p, EmotionKind.JOY, 0.2f, e, out, actor)
        }
        Act.ASK_ABOUT -> if (isTo) feel(p, EmotionKind.JOY, 0.2f * (0.5f + maxOf(0f, sheet.tasteScore(e.tags))), e, out, actor)
        Act.KEEP_SECRET -> if (isAbout) feel(p, EmotionKind.GRATITUDE, 0.4f, e, out, actor)
        Act.GIVE, Act.DEDICATE -> if (isTo) {
            val liking = sheet.tasteScore(e.tags)
            e.item?.let { state.seenItems.getOrPut(p) { mutableSetOf() } += it }
            val g = if (e.act == Act.GIVE) 0.4f + 0.4f * maxOf(0f, liking)
            else 0.3f + maxOf(0f, liking) * trustFactor(p, actor)
            feel(p, EmotionKind.GRATITUDE, g, e, out, actor)
            if (liking > 0f) feel(p, EmotionKind.LIKING, liking, e, out, null, e.item)
            else feel(p, EmotionKind.DISLIKING, -liking, e, out, null, e.item)
        }
        Act.INVITE -> if (isTo) feel(p, EmotionKind.JOY, 0.3f, e, out, actor)
        Act.PROMISE -> if (isTo) {
            feel(p, EmotionKind.HOPE, 0.2f * e.magnitude, e, out, actor)
            state.expectations += Expectation(p, "promise:${actor.key}", true, e.magnitude)
        }
        Act.KEEP_PROMISE, Act.BREAK_PROMISE -> if (isTo) {
            val i = e.magnitude * (0.5f + per.conscientiousness).coerceAtLeast(0.1f)
            if (e.act == Act.KEEP_PROMISE) feel(p, EmotionKind.GRATITUDE, i, e, out, actor)
            else {
                feel(p, EmotionKind.DISAPPOINTMENT, i, e, out, actor)
                feel(p, EmotionKind.ANGER, 0.6f * i, e, out, actor)
            }
            state.expectations.removeAll { it.who == p && it.concern == "promise:${actor.key}" }
        }
        Act.LEAVE_EARLY -> if (aff(p, actor) > 0f) feel(p, EmotionKind.DISTRESS, 0.1f, e, out, actor)
        Act.PRAY_FOR -> if (isTo) {
            if (sheet.standard(Standard.FAITH) >= 0.3f) feel(p, EmotionKind.GRATITUDE, 0.4f, e, out, actor)
            else feel(p, EmotionKind.JOY, 0.15f, e, out, actor)
        }
        Act.TELL, Act.CHANGE_SUBJECT, null -> {} // TELL is hear() and the standards; CHANGE_SUBJECT is easeTension
    }
}

/** Being praised: (0.5 + 0.5 × respect for the praiser) × (1 + 0.5 × pride); flattery from someone distrusted. */
private fun Mind.praised(p: CharId, by: Who, e: Event, out: Pending) {
    val r = relationship(p, by)
    if (r.trust < Appraisal.FLATTERY_TRUST) { feel(p, EmotionKind.REPROACH, 0.3f, e, out, by); return }
    val i = (0.5f + 0.5f * r.respect / 100f) * (1f + 0.5f * cast[p].personality.pride)
    feel(p, EmotionKind.JOY, i, e, out, by)
    feel(p, EmotionKind.GRATITUDE, i, e, out, by)
}

/** Sorry mends: gratitude, plus back some of what the last two weeks cost, and grudges fade. */
private fun Mind.apology(p: CharId, by: Who, e: Event, out: Pending) {
    val agree = cast[p].personality.agreeableness
    feel(p, EmotionKind.GRATITUDE, 0.3f * (1f + 0.5f * agree), e, out, by)
    var hurt = 0f
    for (m in state.memories[p].orEmpty()) {
        val imp = m.impact[by.key] ?: continue
        if (imp < 0f && e.at.day - m.event.at.day <= 14) { hurt += imp; m.salience *= 0.6f }
    }
    if (hurt < 0f) out.direct += Delta(p, by, Field.AFFINITY, minOf(10f, -hurt * 0.5f * (1f + 0.5f * agree)), e.id)
}

/** Good or bad luck: their own, or happy-for / sorry-for (or resentment / gloating) for someone else. */
internal fun Mind.luck(p: CharId, x: Who?, s: Float, e: Event, out: Pending) {
    if (x == null || s == 0f) return
    val mag = abs(s)
    if (x.key == p) {
        feel(p, if (s > 0f) EmotionKind.JOY else EmotionKind.DISTRESS, mag, e, out)
        return
    }
    val a = aff(p, x)
    if (a >= 0f) feel(p, if (s > 0f) EmotionKind.HAPPY_FOR else EmotionKind.SORRY_FOR, mag * maxOf(0.1f, a / 100f), e, out, x)
    else if (s > 0f) feel(p, EmotionKind.RESENTMENT, mag * (-a / 100f), e, out, x)
    else feel(p, EmotionKind.GLOATING, 0.5f * mag * (-a / 100f), e, out, x)
}

private fun Mind.expect(p: CharId, e: Event, out: Pending) {
    val c = e.concern ?: return
    val weight = cast[p].concerns.firstOrNull { it.id == c }?.weight ?: 0.5f
    val good = e.sentiment >= 0f
    feel(p, if (good) EmotionKind.HOPE else EmotionKind.FEAR, e.magnitude * weight, e, out)
    state.expectations.removeAll { it.who == p && it.concern == c }
    state.expectations += Expectation(p, c, good, e.magnitude * weight)
}

/** The standards rule: admiration or reproach, the standard's weight × how clear-cut it was. */
private fun Mind.judgeStandards(p: CharId, e: Event, out: Pending) {
    val actor = e.by ?: return
    val sheet = cast[p]
    var up = 0f
    var down = 0f
    for ((s, d) in standardsOf(e)) {
        val i = sheet.standard(s) * abs(d) * e.magnitude
        if (d > 0f) up += i else down += i
    }
    feel(p, EmotionKind.ADMIRATION, up, e, out, actor)
    feel(p, EmotionKind.REPROACH, down, e, out, actor)
}

/** The standards an action touches, including the ones that depend on what was said. */
internal fun standardsOf(e: Event): Map<Standard, Float> {
    val act = e.act ?: return emptyMap()
    val m = act.standards.toMutableMap()
    fun add(s: Standard, d: Float) { m[s] = (m[s] ?: 0f) + d }
    val f = e.fact
    if (act == Act.TELL && f != null) {
        if (f.sentiment < 0f && f.about != null) { add(Standard.KINDNESS, -0.5f * -f.sentiment); add(Standard.DISCRETION, -0.6f * -f.sentiment) }
        if (f.secret) { add(Standard.DISCRETION, -0.8f); add(Standard.LOYALTY, -0.4f) }
    }
    if ((act == Act.AGREE || act == Act.SIDE_WITH) && e.about != null && e.sentiment < 0f) add(Standard.KINDNESS, -0.2f)
    return m
}

/** Concerns helped or hurt: joy and gratitude, or distress and anger; expectations resolve. */
private fun Mind.concerns(p: CharId, e: Event, out: Pending) {
    for (c in activeConcerns(p)) {
        var effect = 0f
        if (e.concern == c.id && (e.about == null || e.about.key == p || e.to?.key == p)) effect += e.sentiment
        effect += c.helpedBy.filter { it.matches(e, p) }.sumOf { it.strength.toDouble() }.toFloat()
        effect -= c.hurtBy.filter { it.matches(e, p) }.sumOf { it.strength.toDouble() }.toFloat()
        if (e.kind == EventKind.EXPECTED) continue
        effect = effect.coerceIn(-1f, 1f)
        if (effect == 0f) continue
        val i = c.weight * abs(effect)
        val actor = e.by?.takeIf { it.key != p }
        val expected = state.expectations.firstOrNull { it.who == p && it.concern == c.id }
        if (expected != null) state.expectations.remove(expected)
        if (effect > 0f) {
            feel(p, if (expected?.good == false) EmotionKind.RELIEF else EmotionKind.JOY, i, e, out)
            if (actor != null) feel(p, EmotionKind.GRATITUDE, i, e, out, actor)
            advanceConcern(p, c.id, effect * Appraisal.CONCERN_STEP)
        } else {
            feel(p, if (expected?.good == true) EmotionKind.DISAPPOINTMENT else EmotionKind.DISTRESS, i, e, out)
            if (actor != null) feel(p, EmotionKind.ANGER, 0.7f * i, e, out, actor)
        }
    }
}

/** Changing the subject: the tension drops for everyone there. */
private fun easeTension(perceivers: List<CharId>, out: Pending) {
    for (i in out.emotions.indices) {
        val (c, em) = out.emotions[i]
        if (c in perceivers && em.kind.negative) out.emotions[i] = c to em.copy(intensity = em.intensity * Appraisal.TENSION_EASE)
    }
}

/** The player's actions build each witness's impression of them, and their stance on whoever it was about. */
private fun Mind.playerActed(e: Event, fact: Fact?, perceivers: List<CharId>) {
    val st = standardsOf(e)
    if (st.isNotEmpty()) for (p in perceivers) {
        val imp = state.impressions.getOrPut(p) { mutableMapOf() }
        for ((s, d) in st) {
            val (which, v) = when (s) {
                Standard.KINDNESS -> Impression.KIND to d
                Standard.HONESTY -> Impression.HONEST to d
                Standard.LOYALTY -> Impression.LOYAL to d
                Standard.GENEROSITY -> Impression.GENEROUS to d
                Standard.RELIABILITY -> Impression.RELIABLE to d
                Standard.DISCRETION -> Impression.GOSSIP to -d
                else -> continue
            }
            imp[which] = ((imp[which] ?: 0f) * 0.85f + v * 0.5f).coerceIn(-1f, 1f)
        }
    }
    val x = fact?.about as? Who.Char ?: return
    if (fact.by != Who.Player) return
    state.playerStance[x.id] = ((state.playerStance[x.id] ?: 0f) + 30f * fact.sentiment).coerceIn(-100f, 100f)
}

private val ATTENTION = setOf(Act.PRAISE, Act.THANK, Act.COMFORT, Act.GIVE, Act.DEDICATE, Act.INVITE, Act.ASK_ABOUT, Act.CONFIDE, Act.CREDIT, Act.PRAY_FOR)

/**
 * At the end of a scene: watching you spend the visit on a rival brings jealousy (resentment towards
 * the rival, and distress), pride × how one-sided it was. It takes at least three kind moves to notice.
 */
internal fun Mind.jealousy(scene: Scene) {
    val given = mutableMapOf<String, Int>()
    var last: Event? = null
    for (e in scene.events) {
        if (e.by != Who.Player || e.act !in ATTENTION) continue
        val to = e.to as? Who.Char ?: continue
        given[to.id] = (given[to.id] ?: 0) + 1
        last = e
    }
    val cause = last ?: return
    val out = scene.pending
    for (c in scene.everyone) {
        val pride = cast[c].personality.pride
        if (pride <= 0f) continue
        for (r in scene.everyone) {
            if (r == c || aff(c, Who.Char(r)) >= 0f) continue
            val nr = given[r] ?: 0
            val nc = given[c] ?: 0
            if (nr + nc < 3 || nr <= nc) continue
            val i = pride * (nr - nc).toFloat() / (nr + nc)
            feel(c, EmotionKind.RESENTMENT, i, cause, out, Who.Char(r))
            feel(c, EmotionKind.DISTRESS, i, cause, out)
        }
    }
}
