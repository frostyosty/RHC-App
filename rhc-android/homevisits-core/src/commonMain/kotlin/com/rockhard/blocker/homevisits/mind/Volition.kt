package com.rockhard.blocker.homevisits.mind

import com.rockhard.blocker.homevisits.story.CharId

/** How much one option appeals: base + traits + liking for whoever it's to / about + mood. */
internal class Weights(
    val base: Float,
    val o: Float = 0f, val c: Float = 0f, val e: Float = 0f, val a: Float = 0f, val n: Float = 0f,
    val pride: Float = 0f, val d: Float = 0f,
    val likeTo: Float = 0f, val likeAbout: Float = 0f,
    val moodP: Float = 0f, val moodA: Float = 0f,
    val drama: Boolean = false
)

/** What characters choose to do in group scenes (07, 10) and off-screen meetings (08). All the weights are here. */
object Volition {
    /**
     * How much drama (Ask in step 06; default "gentle, with some friction"). 0 is none, 1 is a soap
     * opera. Dramatic options lose (1 − DRAMA) × [DRAMA_PENALTY].
     */
    const val DRAMA = 0.4f
    const val DRAMA_PENALTY = 0.5f
    const val VARIETY_PENALTY = 0.3f
    const val RECENT = 3
    const val NOISE = 0.1f
    const val CONCERN_BONUS = 0.3f
    const val DISLIKED_PRESENT = 0.8f

    internal val TABLE: Map<Act, Weights> = mapOf(
        Act.GREET to Weights(0.3f, e = 0.2f, a = 0.1f),
        Act.THANK to Weights(0.2f, c = 0.1f, a = 0.2f, likeTo = 0.2f),
        Act.JOKE to Weights(0.15f, o = 0.1f, e = 0.3f, moodP = 0.3f),
        Act.TEASE to Weights(0.05f, e = 0.2f, a = -0.2f, pride = 0.1f, likeTo = 0.2f, drama = true),
        Act.COMFORT to Weights(0.15f, a = 0.4f, likeTo = 0.3f),
        Act.CONFIDE to Weights(0f, e = 0.1f, d = -0.2f, likeTo = 0.4f, moodP = -0.2f),
        Act.APOLOGISE to Weights(0f, c = 0.1f, a = 0.3f, pride = -0.3f),
        Act.PRAISE to Weights(0.2f, a = 0.3f, likeTo = 0.3f, likeAbout = 0.3f),
        Act.CRITICISE to Weights(-0.1f, a = -0.4f, n = 0.3f, pride = 0.2f, likeTo = -0.4f, likeAbout = -0.5f, moodP = -0.3f, drama = true),
        Act.AGREE to Weights(0.15f, a = 0.3f, likeTo = 0.3f),
        Act.DISAGREE to Weights(0f, a = -0.3f, pride = 0.2f, likeTo = -0.3f),
        Act.DEFEND to Weights(0f, a = 0.2f, likeAbout = 0.5f),
        Act.SIDE_WITH to Weights(0f, likeTo = 0.3f, likeAbout = -0.3f),
        Act.CREDIT to Weights(0f, a = 0.2f, pride = -0.2f, likeAbout = 0.3f),
        Act.ASK_ABOUT to Weights(0.2f, o = 0.3f, e = 0.1f, likeTo = 0.1f),
        Act.TELL to Weights(0.05f, e = 0.2f, d = -0.5f, likeTo = 0.2f),
        Act.KEEP_SECRET to Weights(0f, d = 0.5f),
        Act.CHANGE_SUBJECT to Weights(0.05f, a = 0.2f, moodA = 0.2f),
        Act.GIVE to Weights(0f, a = 0.2f, likeTo = 0.3f),
        Act.DEDICATE to Weights(-0.1f, a = 0.2f, likeTo = 0.3f),
        Act.INVITE to Weights(0f, e = 0.3f, likeTo = 0.3f),
        Act.PROMISE to Weights(-0.1f, c = 0.3f, likeTo = 0.2f),
        Act.KEEP_PROMISE to Weights(0.3f, c = 0.5f),
        Act.BREAK_PROMISE to Weights(-0.5f, c = -0.4f),
        Act.LEAVE_EARLY to Weights(-0.4f, e = -0.2f, drama = true),
        Act.PRAY_FOR to Weights(-0.2f, likeTo = 0.2f)
    )
}

internal fun Mind.score(who: CharId, o: SocialAction): Float {
    val w = Volition.TABLE[o.act] ?: Weights(0f)
    val sheet = cast[who]
    val p = sheet.personality
    val m = mood(who)
    var s = w.base + w.o * p.openness + w.c * p.conscientiousness + w.e * p.extraversion + w.a * p.agreeableness +
        w.n * p.neuroticism + w.pride * p.pride + w.d * p.discretion + w.moodP * m.p + w.moodA * m.a
    o.to?.let { if (it.key != who) s += w.likeTo * aff(who, it) / 100f }
    o.about?.let { if (it.key != who) s += w.likeAbout * aff(who, it) / 100f }
    when (o.act) {
        Act.LEAVE_EARLY -> {
            val worst = state.scene?.present.orEmpty().filter { it != who }.maxOfOrNull { -aff(who, Who.Char(it)) / 100f } ?: 0f
            s += Volition.DISLIKED_PRESENT * maxOf(0f, worst) * (1f - p.extraversion) / 2f
        }
        Act.PRAY_FOR -> s += 0.6f * sheet.standard(Standard.FAITH)
        Act.CONFIDE -> if (o.to == Who.Player) s -= 0.4f * maxOf(0f, impression(who, Impression.GOSSIP))
        Act.TELL -> o.fact?.let { f ->
            s += 0.2f * kotlin.math.abs(f.sentiment)
            if (f.secret) s -= 0.5f * (1f + p.discretion)
            if (f.sentiment < 0f) s -= (1f - Volition.DRAMA) * Volition.DRAMA_PENALTY
        }
        else -> {}
    }
    if (w.drama) s -= (1f - Volition.DRAMA) * Volition.DRAMA_PENALTY
    val probe = o.event(Who.Char(who), state.clock)
    for (c in activeConcerns(who)) if (c.helpedBy.any { it.matches(probe, who) }) s += Volition.CONCERN_BONUS * c.weight
    if (o.act in state.recentActs[who].orEmpty()) s -= Volition.VARIETY_PENALTY
    return s
}

/** The best-scoring option, with a little seeded noise. Ties go to the earlier option. */
internal fun Mind.chooseAction(who: CharId, options: List<SocialAction>): SocialAction {
    require(options.isNotEmpty()) { "nothing to choose from" }
    val r = rng.stream("mind.choose", state.chooseCount++)
    var best = options[0]
    var bestScore = Float.NEGATIVE_INFINITY
    for (o in options) {
        val s = score(who, o) + r.nextFloat() * Volition.NOISE
        if (s > bestScore) { best = o; bestScore = s }
    }
    val recent = state.recentActs.getOrPut(who) { mutableListOf() }
    recent += best.act
    while (recent.size > Volition.RECENT) recent.removeAt(0)
    return best
}

/**
 * Wanting to visit, 0.05 to 1: liking you, sociability, mood, missing you a little after days apart,
 * and a promise they're looking forward to. Never 0, and being away never makes it fall.
 */
internal fun Mind.visitWish(who: CharId, day: Int): Float {
    val r = relationship(who, Who.Player)
    val p = cast[who].personality
    val apart = (day - (state.lastVisit[who] ?: 0)).coerceIn(0, 14)
    var w = 0.3f + 0.35f * r.affinity / 100f + 0.15f * p.extraversion + 0.1f * mood(who).p +
        0.02f * apart * maxOf(0f, r.affinity) / 100f
    if (state.expectations.any { it.who == who && it.concern == "promise:${Who.Player.KEY}" }) w += 0.2f
    return w.coerceIn(0.05f, 1f)
}
