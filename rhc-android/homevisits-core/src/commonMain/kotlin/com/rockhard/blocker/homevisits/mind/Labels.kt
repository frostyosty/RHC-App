package com.rockhard.blocker.homevisits.mind

import com.rockhard.blocker.homevisits.story.CharId

/** Words for a relationship, for the screens (08) and for conditions (07). */
enum class Label { FAMILY, CLOSE_FRIENDS, LOOKS_UP_TO, FRIENDS, GETTING_ON, ACQUAINTANCES, DONT_GET_ON, RIVALS, CANT_STAND }

/** The thresholds, all in one table. Checked top to bottom; the first that fits wins. */
object Labels {
    const val FAMILY_AT_LEAST = -20f
    const val CLOSE = 65f
    const val CLOSE_FAMILIARITY = 50f
    const val LOOKS_UP_RESPECT = 60f
    const val LOOKS_UP_AFFINITY = 20f
    const val FRIENDS = 35f
    const val GETTING_ON = 10f
    const val ACQUAINTANCES = -10f
    const val CANT_STAND = -60f
    const val RIVAL_RESPECT = 20f

    fun of(r: Relationship, bond: BondKind): Label = when {
        bond == BondKind.FAMILY && r.affinity >= FAMILY_AT_LEAST -> Label.FAMILY
        r.affinity >= CLOSE && r.familiarity >= CLOSE_FAMILIARITY -> Label.CLOSE_FRIENDS
        r.respect >= LOOKS_UP_RESPECT && r.affinity >= LOOKS_UP_AFFINITY && r.respect > r.affinity + 20f -> Label.LOOKS_UP_TO
        r.affinity >= FRIENDS -> Label.FRIENDS
        r.affinity >= GETTING_ON -> Label.GETTING_ON
        r.affinity > ACQUAINTANCES -> Label.ACQUAINTANCES
        r.affinity <= CANT_STAND -> Label.CANT_STAND
        r.respect >= RIVAL_RESPECT -> Label.RIVALS
        else -> Label.DONT_GET_ON
    }

    /** At least as warm as "getting on" (friends, close friends, family, looks up to). */
    fun warm(l: Label) = l in setOf(Label.FAMILY, Label.CLOSE_FRIENDS, Label.LOOKS_UP_TO, Label.FRIENDS, Label.GETTING_ON)
}

private fun faceFor(kind: EmotionKind, cause: Event?, intensity: Float): Expression = when (kind) {
    EmotionKind.JOY -> if (cause?.act == Act.JOKE) Expression.LAUGH else Expression.HAPPY
    EmotionKind.GLOATING -> Expression.LAUGH
    EmotionKind.GRATITUDE -> Expression.TOUCHED
    EmotionKind.LIKING -> if (intensity >= 0.8f) Expression.SURPRISED else Expression.HAPPY
    EmotionKind.ADMIRATION, EmotionKind.HOPE, EmotionKind.RELIEF, EmotionKind.PRIDE, EmotionKind.HAPPY_FOR -> Expression.HAPPY
    EmotionKind.DISTRESS, EmotionKind.SORRY_FOR, EmotionKind.DISAPPOINTMENT, EmotionKind.FEAR -> Expression.SAD
    EmotionKind.ANGER -> Expression.ANGRY
    EmotionKind.REPROACH, EmotionKind.DISLIKING, EmotionKind.RESENTMENT -> Expression.FROWN
    EmotionKind.SHAME -> Expression.SHY
}

private fun emoteFor(kind: EmotionKind): Emote = when (kind) {
    EmotionKind.GRATITUDE, EmotionKind.LIKING -> Emote.HEART
    EmotionKind.ANGER -> Emote.ANGER
    EmotionKind.DISTRESS, EmotionKind.FEAR, EmotionKind.SHAME, EmotionKind.DISAPPOINTMENT -> Emote.SWEAT
    EmotionKind.JOY, EmotionKind.ADMIRATION, EmotionKind.PRIDE, EmotionKind.HAPPY_FOR -> Emote.SPARKLE
    else -> Emote.NONE
}

/** The scene's strongest emotion for [who], if it's above [Faces.SHOWS] (the latest wins ties). */
private fun Mind.strongest(who: CharId): Emotion? {
    val mine = state.scene?.pending?.emotions?.filter { it.first == who }?.map { it.second } ?: return null
    val top = mine.maxOfOrNull { it.intensity } ?: return null
    return mine.lastOrNull { it.intensity >= top }?.takeIf { it.intensity > Faces.SHOWS }
}

object Faces {
    const val SHOWS = 0.3f
    const val MOOD_FACE = 0.25f
    const val EMOTE = 0.5f
}

internal fun Mind.faceOf(who: CharId): Expression {
    strongest(who)?.let { e -> return faceFor(e.kind, state.scene?.events?.firstOrNull { it.id == e.cause }, e.intensity) }
    val m = mood(who)
    return when {
        m.p >= Faces.MOOD_FACE -> Expression.HAPPY
        m.p <= -Faces.MOOD_FACE -> if (m.d > 0f) Expression.FROWN else Expression.SAD
        m.d <= -0.3f -> Expression.SHY
        else -> Expression.NEUTRAL
    }
}

internal fun Mind.emoteOf(who: CharId): Emote {
    val e = strongest(who) ?: return Emote.NONE
    if (e.intensity < Faces.EMOTE) return Emote.NONE
    val cause = state.scene?.events?.firstOrNull { it.id == e.cause }
    if (cause?.act == Act.ASK_ABOUT && e.kind == EmotionKind.JOY) return Emote.QUESTION
    return emoteFor(e.kind)
}
