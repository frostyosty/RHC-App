package com.rockhard.blocker.homevisits.mind

import com.rockhard.blocker.homevisits.story.CharId

/** A fixed bond from the cast sheet. Long-term drift returns affinity (and, at half the rate, trust and respect) to [rest]. */
enum class BondKind(val rest: Float) {
    NONE(0f), NEIGHBOURS(10f), WORKMATES(10f), CHURCH(15f), FRIENDS(30f), OLD_FRIENDS(45f), FAMILY(55f)
}

/**
 * Where a relationship starts. [trust] defaults to 0.6 × affinity + 10 and [respect] to 0.5 × affinity.
 * [history], when given, becomes a strong memory so `why` can explain the starting numbers
 * ("she still hasn't forgiven him for the council complaint").
 */
data class Bond(
    val to: CharId,
    val affinity: Float,
    val respect: Float? = null,
    val trust: Float? = null,
    val familiarity: Float = 30f,
    val kind: BondKind = BondKind.NONE,
    val history: String? = null
)

/**
 * An event that moves a concern on. Matches when every field that's set matches. For social actions,
 * the action must be to or about [target] (null means the concern's owner, [ANYONE] means anyone).
 */
data class Trigger(
    val act: Act? = null,
    val kind: EventKind? = null,
    val tag: Tag? = null,
    val target: CharId? = null,
    val strength: Float = 0.5f
) {
    fun matches(e: Event, owner: CharId): Boolean {
        if (act == null && kind == null && tag == null) return false
        if (act != null && e.act != act) return false
        if (kind != null && e.kind != kind) return false
        if (tag != null && tag !in e.tags) return false
        if (e.kind == EventKind.ACTION) {
            val t = target ?: owner
            if (t != ANYONE && e.to?.key != t && e.about?.key != t) return false
        }
        return true
    }

    companion object { const val ANYONE = "*" }
}

/** What's on a character's mind, from their story: Rachel's exhibition, Alfred missing June. */
data class Concern(
    val id: String,
    val weight: Float,
    val helpedBy: List<Trigger> = emptyList(),
    val hurtBy: List<Trigger> = emptyList()
)

/** A character's sheet: content, fixed. Step 07 brings the file format; until then sheets are built in Kotlin ([TestCast]). */
data class CharSheet(
    val id: CharId,
    val name: String,
    val personality: Personality,
    val tastes: Map<Tag, Float>,
    val standards: Map<Standard, Float>,
    val concerns: List<Concern>,
    val bonds: List<Bond>,
    val toPlayer: Bond = Bond(Who.Player.KEY, 10f, respect = 10f, trust = 10f, familiarity = 0f, kind = BondKind.NEIGHBOURS)
) {
    fun taste(tag: Tag): Float = tastes[tag] ?: 0f

    /** How much they care about [s]. Discretion is the larger of the sheet's weight and 0.8 × the trait. */
    fun standard(s: Standard): Float = when (s) {
        Standard.DISCRETION -> maxOf(standards[s] ?: 0f, personality.discretion.coerceAtLeast(0f) * 0.8f)
        else -> standards[s] ?: 0f
    }

    fun bondTo(other: CharId): Bond? = bonds.firstOrNull { it.to == other }

    /** Someone warm enough to calm a quarrel just by being in the room (Ruth). */
    val peacemaker: Boolean get() = personality.agreeableness >= 0.7f
}

class Cast(sheets: List<CharSheet>) {
    private val byId = sheets.associateBy { it.id }

    /** Always in id order: the engine processes characters in this order. */
    val ids: List<CharId> = sheets.map { it.id }.sorted()

    init {
        require(byId.size == sheets.size) { "character ids must be unique" }
        for (id in ids) require(id.isNotEmpty() && !id.startsWith("@") && '>' !in id && id != Trigger.ANYONE) { "bad character id '$id'" }
    }

    operator fun get(id: CharId): CharSheet = byId[id] ?: error("no character '$id' in the cast")
    operator fun contains(id: CharId) = id in byId
}
