package com.rockhard.blocker.homevisits.mind

import com.rockhard.blocker.homevisits.HomeClock
import com.rockhard.blocker.homevisits.house.ItemId
import com.rockhard.blocker.homevisits.house.RoomKind
import com.rockhard.blocker.homevisits.story.CharId

typealias FactId = String

/** A character, or the player. [key] is how state maps store it. */
sealed interface Who {
    val key: String

    data class Char(val id: CharId) : Who {
        override val key: String get() = id
    }

    object Player : Who {
        const val KEY = "@player"
        override val key: String get() = KEY
        override fun toString() = "Player"
    }

    companion object {
        fun of(key: String): Who = if (key == Player.KEY) Player else Char(key)
    }
}

enum class EventKind {
    // presence
    ARRIVED, LEFT, MET, SAW_ITEM, ENTERED_ROOM,
    /** A social action ([Event.act]), by a character or the player. */
    ACTION,
    // house
    ITEM_BOUGHT, ITEM_PLACED, ITEM_MOVED, ITEM_REMOVED,
    /** A gift ([Event.giftFrom]) on show (sentiment ≥ 0) or taken down (sentiment < 0). */
    GIFT_ON_SHOW,
    // world (days themselves pass through Mind.advanceTo)
    /** The player's day outside the app (step 12): sentiment > 0 for a good one. Never a negative. */
    PLAYER_DAY,
    /** Good or bad luck from a character's story ([Event.about]); may name the [Event.concern] it moves. */
    LIFE_EVENT,
    /** [Event.by] expects something about [Event.concern]: good (sentiment > 0) or bad, with likelihood [Event.magnitude]. */
    EXPECTED,
    /** A starting relationship's backstory, seeded from the cast sheet. */
    HISTORY
}

/** The social actions 07's choices map to, and the standards each one upholds (+) or breaks (−). */
enum class Act(vararg touches: Pair<Standard, Float>) {
    GREET(Standard.KINDNESS to 0.1f),
    THANK(Standard.KINDNESS to 0.2f),
    JOKE,
    TEASE(Standard.KINDNESS to -0.2f),
    COMFORT(Standard.KINDNESS to 0.5f),
    CONFIDE,
    APOLOGISE(Standard.HONESTY to 0.3f, Standard.KINDNESS to 0.2f, Standard.MODESTY to 0.2f),
    PRAISE(Standard.KINDNESS to 0.3f),
    CRITICISE(Standard.KINDNESS to -0.4f),
    AGREE,
    DISAGREE(Standard.HONESTY to 0.1f),
    DEFEND(Standard.LOYALTY to 0.6f, Standard.KINDNESS to 0.5f),
    SIDE_WITH(Standard.LOYALTY to 0.4f),
    CREDIT(Standard.HONESTY to 0.4f, Standard.MODESTY to 0.4f),
    ASK_ABOUT,
    /** Pass on [Event.fact]. Unkind or secret facts break kindness and discretion (see standardsOf). */
    TELL,
    KEEP_SECRET(Standard.DISCRETION to 0.6f, Standard.LOYALTY to 0.4f),
    CHANGE_SUBJECT,
    GIVE(Standard.GENEROSITY to 0.6f),
    DEDICATE(Standard.KINDNESS to 0.5f, Standard.GENEROSITY to 0.3f),
    INVITE(Standard.KINDNESS to 0.2f),
    PROMISE,
    KEEP_PROMISE(Standard.RELIABILITY to 0.8f),
    BREAK_PROMISE(Standard.RELIABILITY to -0.8f, Standard.HONESTY to -0.2f),
    LEAVE_EARLY(Standard.KINDNESS to -0.1f),
    /** Praying for someone (faith is light: it comes up naturally, never preachy). */
    PRAY_FOR(Standard.FAITH to 0.5f, Standard.KINDNESS to 0.3f);

    val standards: Map<Standard, Float> = touches.toMap()
}

/**
 * A claim: "Alfred said Rachel's mural is an eyesore" is by Alfred, about Rachel, sentiment −0.6.
 * News with nobody behind it ("Rachel's exhibition opens") has no [by].
 */
data class Fact(
    val id: FactId,
    val by: Who? = null,
    val about: Who? = null,
    val sentiment: Float = 0f,
    val tags: List<Tag> = emptyList(),
    val secret: Boolean = false,
    val text: String = ""
)

/**
 * Everything that happens. [witnesses] null means everyone in the scene (outside a scene, the
 * characters it was by or to). [playerSaw] null means "if it happened in a scene". [id] is set by
 * [Mind.perceive].
 */
data class Event(
    val kind: EventKind,
    val at: HomeClock,
    val by: Who? = null,
    val to: Who? = null,
    val about: Who? = null,
    val act: Act? = null,
    val item: ItemId? = null,
    val tags: List<Tag> = emptyList(),
    val fact: Fact? = null,
    val room: RoomKind? = null,
    val witnesses: List<CharId>? = null,
    val playerSaw: Boolean? = null,
    /** How big, how clear-cut or how much it mattered, 0 to 1. */
    val magnitude: Float = 1f,
    /** −1 to 1: good or bad (luck, a complaint agreed with, a gift taken down). */
    val sentiment: Float = 0f,
    /** The concern it moves (helps if sentiment > 0, hurts if < 0), for LIFE_EVENT and EXPECTED. */
    val concern: String? = null,
    /** SAW_ITEM / GIFT_ON_SHOW: the character who gave the item. */
    val giftFrom: CharId? = null,
    /** SAW_ITEM: the player chose it with this character in mind. */
    val chosenFor: CharId? = null,
    /** Free text for logs and backstory. */
    val note: String = "",
    val id: Long = 0L
)

/** A social action someone could take, as a choice for [Mind.choose] or a player's answer. */
data class SocialAction(
    val act: Act,
    val to: Who? = null,
    val about: Who? = null,
    val tags: List<Tag> = emptyList(),
    val item: ItemId? = null,
    val fact: Fact? = null,
    val sentiment: Float = 0f,
    val magnitude: Float = 1f
) {
    fun event(by: Who, at: HomeClock, witnesses: List<CharId>? = null, playerSaw: Boolean? = null) = Event(
        EventKind.ACTION, at, by = by, to = to, about = about, act = act, item = item,
        tags = if (tags.isEmpty() && fact != null) fact.tags else tags,
        fact = fact, witnesses = witnesses, playerSaw = playerSaw, magnitude = magnitude, sentiment = sentiment
    )
}
