package com.rockhard.blocker.homevisits.mind

/**
 * Seven numbers from −1 to 1, from the cast sheet, that never change. The first five are the Big Five;
 * pride and discretion are the two this game's drama needs.
 */
data class Personality(
    val openness: Float,
    val conscientiousness: Float,
    val extraversion: Float,
    val agreeableness: Float,
    val neuroticism: Float,
    val pride: Float,
    val discretion: Float
) {
    init {
        for (v in listOf(openness, conscientiousness, extraversion, agreeableness, neuroticism, pride, discretion))
            require(v in -1f..1f) { "traits run from -1 to 1, got $v" }
    }

    /**
     * Where mood rests between scenes: Mehrabian's (1996) mapping of the Big Five onto pleasure, arousal
     * and dominance (with stability = −neuroticism), halved so nobody rests at an extreme.
     */
    fun restingMood(): Pad {
        val s = -neuroticism
        return Pad(
            0.5f * (0.21f * extraversion + 0.59f * agreeableness + 0.19f * s),
            0.5f * (0.15f * openness + 0.30f * agreeableness - 0.57f * s),
            0.5f * (0.25f * openness + 0.17f * conscientiousness + 0.60f * extraversion - 0.32f * agreeableness)
        ).clamped()
    }

    /** Hours for mood to get halfway back to rest: 8 × (1 + neuroticism), and at least 2. */
    val moodHalfLifeHours: Float get() = (8f * (1f + neuroticism)).coerceAtLeast(2f)
}

/** Mood as pleasure, arousal and dominance, each −1 to 1. */
data class Pad(val p: Float, val a: Float, val d: Float) {
    fun clamped() = Pad(p.coerceIn(-1f, 1f), a.coerceIn(-1f, 1f), d.coerceIn(-1f, 1f))

    /** Moves [t] of the way towards [to]. */
    fun towards(to: Pad, t: Float) = Pad(p + (to.p - p) * t, a + (to.a - a) * t, d + (to.d - d) * t).clamped()
}
