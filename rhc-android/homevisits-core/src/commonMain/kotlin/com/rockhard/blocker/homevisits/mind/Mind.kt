package com.rockhard.blocker.homevisits.mind

import com.rockhard.blocker.homevisits.HomeClock
import com.rockhard.blocker.homevisits.Rng
import com.rockhard.blocker.homevisits.house.ItemId
import com.rockhard.blocker.homevisits.house.RoomKind
import com.rockhard.blocker.homevisits.story.CharId

/**
 * Everything the engine remembers, saved in game.json. Maps keep insertion order, and the engine only
 * inserts in a fixed order, so nothing depends on hash order. Relationship keys are "from>to".
 */
class MindState {
    var clock = HomeClock(0, 0)
    var nextEventId = 1L
    var chooseCount = 0
    val mood = mutableMapOf<CharId, Pad>()
    val relationships = mutableMapOf<String, Relationship>()
    val memories = mutableMapOf<CharId, MutableList<Memory>>()
    val log = mutableListOf<Event>()
    val facts = mutableMapOf<FactId, Fact>()
    /** Who knows which facts, by [Who.key]. */
    val known = mutableMapOf<String, MutableMap<FactId, Heard>>()
    val seenItems = mutableMapOf<CharId, MutableSet<ItemId>>()
    /** "char>tag": strong reactions to that tag the player has seen. Two teach the player the taste. */
    val strongReactions = mutableMapOf<String, Int>()
    /** "char>tag": tastes the player has learned. */
    val tastesLearned = mutableSetOf<String>()
    /** "from>to": views the player has seen shown. The screens show only these. */
    val viewsKnownToPlayer = mutableSetOf<String>()
    val impressions = mutableMapOf<CharId, MutableMap<Impression, Float>>()
    /** −100 to 100: where the player has been seen to stand on each character. */
    val playerStance = mutableMapOf<CharId, Float>()
    /** "char>subject": the char knows the player's stance on the subject. */
    val stanceKnown = mutableSetOf<String>()
    /** "char>concern": 0 to 1; a concern at 1 is resolved and the next one in the story comes up. */
    val concernProgress = mutableMapOf<String, Float>()
    val expectations = mutableListOf<Expectation>()
    val recentActs = mutableMapOf<CharId, MutableList<Act>>()
    /** "a>b" (either way round, a < b; the player is "@player"): the last day they spent time together. */
    val lastTogether = mutableMapOf<String, Int>()
    val lastVisit = mutableMapOf<CharId, Int>()
    var scene: Scene? = null
}

/** One visit, from startScene to endScene. Times are minutes since [start]. */
class Scene(val place: RoomKind, val start: HomeClock) {
    val joinedAt = mutableMapOf<CharId, Int>()
    val leftAt = mutableMapOf<CharId, Int>()
    var lastMinute = 0
    internal val pending = Pending()
    internal val events = mutableListOf<Event>()

    val present: List<CharId> get() = joinedAt.keys.filter { it !in leftAt }.sorted()
    val everyone: List<CharId> get() = joinedAt.keys.sorted()
}

data class Expectation(val who: CharId, val concern: String, val good: Boolean, val strength: Float)

enum class Impression { KIND, HONEST, LOYAL, GENEROUS, RELIABLE, GOSSIP }

enum class Expression { HAPPY, SAD, ANGRY, SURPRISED, LAUGH, FROWN, SHY, TOUCHED, NEUTRAL }

enum class Emote(val symbol: String) { NONE(""), HEART("❤️"), ANGER("💢"), SWEAT("💦"), SPARKLE("✨"), QUESTION("❓") }

data class Change(val from: CharId, val to: Who, val field: Field, val before: Float, val after: Float) {
    val delta: Float get() = after - before
}

/** What a scene changed, for 07's closing lines and the tools. */
data class SceneSummary(
    val minutes: Int,
    val present: List<CharId>,
    val changes: List<Change>,
    val emotions: Map<CharId, List<Emotion>>
) {
    fun biggest(n: Int): List<Change> =
        changes.filter { it.field != Field.FAMILIARITY }.sortedByDescending { kotlin.math.abs(it.delta) }.take(n)
}

/**
 * The emotions engine: how each character feels about things in your home, about you and about each
 * other, and how that changes. Feed it events; ask it questions. Deterministic: randomness only comes
 * from [rng] streams, characters are processed in id order, and it never reads a clock.
 */
class Mind(val cast: Cast, val state: MindState, internal val rng: Rng) {
    /**
     * Who met whom off-screen on a day. Step 08 sets this; until then [defaultMeetings] guesses from
     * personalities and closeness.
     */
    var meetings: ((day: Int) -> List<Pair<CharId, CharId>>)? = null

    init {
        if (state.relationships.isEmpty()) seed()
    }

    /** Applies mood and relationship drift up to [clock], and runs off-screen days (in full for up to [FULL_DAYS]). */
    fun advanceTo(clock: HomeClock) {
        val from = state.clock
        require(minutesBetween(from, clock) >= 0) { "time only goes forwards ($from to $clock)" }
        if (state.scene != null) {
            require(clock.day == from.day) { "end the scene before moving to another day" }
            state.clock = clock
            return
        }
        for (day in from.day + 1..clock.day) {
            dailyDrift(day)
            if (day - from.day <= FULL_DAYS) {
                balance()
                offscreen(day)
            }
            decayMemories()
            trimLog()
        }
        driftMood(minutesBetween(from, clock))
        state.clock = clock
    }

    fun startScene(present: List<CharId>, place: RoomKind) {
        check(state.scene == null) { "a scene is already running" }
        val s = Scene(place, state.clock)
        for (c in present.distinct().sorted()) { cast[c]; s.joinedAt[c] = 0 }
        state.scene = s
    }

    /** Appraises [event] for everyone who perceives it. Outside a scene it settles straight away. */
    fun perceive(event: Event) {
        val e = event.copy(id = state.nextEventId++)
        state.log += e
        val scene = state.scene
        val perceivers: List<CharId>
        if (scene != null) {
            val m = minutesBetween(scene.start, e.at).coerceAtLeast(scene.lastMinute)
            scene.lastMinute = m
            val by = (e.by as? Who.Char)?.id
            if (e.kind == EventKind.ARRIVED && by != null) { cast[by]; scene.joinedAt.getOrPut(by) { m }; scene.leftAt.remove(by) }
            perceivers = e.witnesses?.filter { it in cast }?.distinct()?.sorted() ?: scene.present
            if (e.kind == EventKind.LEFT && by != null) scene.leftAt[by] = m
            scene.events += e
        } else {
            perceivers = (e.witnesses ?: listOfNotNull((e.by as? Who.Char)?.id, (e.to as? Who.Char)?.id))
                .filter { it in cast }.distinct().sorted()
        }
        val pending = scene?.pending ?: Pending()
        pending.playerSees = e.playerSaw ?: (scene != null)
        appraise(e, perceivers, pending)
        if (scene == null) settle(pending, null)
    }

    /** Settles the scene's emotions into relationships and memories, and says what changed. */
    fun endScene(): SceneSummary {
        val scene = checkNotNull(state.scene) { "no scene is running" }
        jealousy(scene)
        val changes = settle(scene.pending, scene)
        val day = scene.start.day
        val everyone = scene.everyone
        for (c in everyone) {
            state.lastVisit[c] = day
            state.lastTogether[pairKey(c, Who.Player.KEY)] = day
            for (o in everyone) if (c < o) state.lastTogether[pairKey(c, o)] = day
        }
        val emotions = everyone.associateWith { c -> scene.pending.emotions.filter { it.first == c }.map { it.second } }
        state.scene = null
        val end = scene.start.plusMinutes(scene.lastMinute)
        if (minutesBetween(state.clock, end) > 0) state.clock = end
        return SceneSummary(scene.lastMinute, everyone, changes, emotions)
    }

    /** The best of [options] for [who] right now (see Volition.kt). */
    fun choose(who: CharId, options: List<SocialAction>): SocialAction = chooseAction(who, options)

    fun relationship(from: CharId, to: Who): Relationship =
        state.relationships[relKey(from, to)] ?: Relationship.STRANGERS

    fun label(from: CharId, to: Who): Label = Labels.of(relationship(from, to), bondKind(from, to))

    /** The memories that moved [from]'s view of [to] the most, strongest first. */
    fun why(from: CharId, to: Who, n: Int = 3): List<Memory> = whyMemories(from, to, n)

    fun expression(who: CharId): Expression = faceOf(who)

    fun emote(who: CharId): Emote = emoteOf(who)

    fun mood(who: CharId): Pad = state.mood[who] ?: cast[who].personality.restingMood()

    fun knows(who: Who, fact: FactId): Boolean = state.known[who.key]?.containsKey(fact) == true

    /** The taste, once the player has learned it; null until then. */
    fun tasteKnownToPlayer(who: CharId, tag: Tag): Float? =
        if ("$who>${tag.word}" in state.tastesLearned) cast[who].taste(tag) else null

    /** Whether the player has seen [from]'s feelings about [to] (the who's-who screen shows only these). */
    fun playerKnowsView(from: CharId, to: Who): Boolean = "$from>${to.key}" in state.viewsKnownToPlayer

    /** 0 to 1. Step 08 combines it with rhythms and promises. Never 0: nobody leaves the game for good. */
    fun wantsToVisit(who: CharId, day: Int): Float = visitWish(who, day)

    /** 0 to 1: how much [a] would rather not be in a visit with [b] (for 08). */
    fun avoids(a: CharId, b: CharId): Float = ((-relationship(a, Who.Char(b)).affinity - 30f) / 70f).coerceIn(0f, 1f)

    /** The character said so, or 07's storylet taught it. */
    fun learnTaste(who: CharId, tag: Tag) { state.tastesLearned += "$who>${tag.word}" }

    fun impression(who: CharId, of: Impression): Float = state.impressions[who]?.get(of) ?: 0f

    fun concernProgress(who: CharId, concern: String): Float = state.concernProgress["$who>$concern"] ?: 0f

    /** For 07's storylets: moves a concern on through the character's story. */
    fun advanceConcern(who: CharId, concern: String, amount: Float) {
        val k = "$who>$concern"
        state.concernProgress[k] = ((state.concernProgress[k] ?: 0f) + amount).coerceIn(0f, 1f)
    }

    /** The concerns on [who]'s mind now: the first [ACTIVE_CONCERNS] of their story not yet resolved. */
    fun activeConcerns(who: CharId): List<Concern> =
        cast[who].concerns.filter { concernProgress(who, it.id) < 1f }.take(ACTIVE_CONCERNS)

    /** Two characters meeting off-screen (08 decides who): they get to know each other and pass on news. */
    fun meetOffscreen(a: CharId, b: CharId, at: HomeClock) = meetOff(a, b, at)

    fun event(id: Long): Event? = state.log.firstOrNull { it.id == id }

    internal fun aff(from: CharId, to: Who) = relationship(from, to).affinity

    internal fun bondKind(from: CharId, to: Who): BondKind = when (to) {
        Who.Player -> cast[from].toPlayer.kind
        is Who.Char -> cast[from].bondTo(to.id)?.kind ?: BondKind.NONE
    }

    private fun seed() {
        for (a in cast.ids) {
            val sheet = cast[a]
            state.mood[a] = sheet.personality.restingMood()
            for (b in cast.ids) {
                if (b == a) continue
                val bond = sheet.bondTo(b) ?: Bond(b, 0f, familiarity = 20f)
                state.relationships[relKey(a, Who.Char(b))] = start(bond)
                if (bond.history != null) {
                    val e = Event(EventKind.HISTORY, state.clock, by = Who.Char(b), about = Who.Char(a), note = bond.history, id = state.nextEventId++)
                    state.log += e
                    state.memories.getOrPut(a) { mutableListOf() } +=
                        Memory(e, 0.8f, 1f, mutableMapOf(b to bond.affinity))
                }
            }
            state.relationships[relKey(a, Who.Player)] = start(sheet.toPlayer)
        }
    }

    private fun start(b: Bond) = Relationship(
        b.affinity, b.trust ?: (0.6f * b.affinity + 10f), b.respect ?: (0.5f * b.affinity), b.familiarity
    ).clamped()

    private fun driftMood(minutes: Int) {
        if (minutes <= 0) return
        for (c in cast.ids) {
            val p = cast[c].personality
            val left = kotlin.math.exp(-kotlin.math.ln(2f) * (minutes / 60f) / p.moodHalfLifeHours)
            state.mood[c] = mood(c).towards(p.restingMood(), 1f - left)
        }
    }

    companion object {
        /** advanceTo runs this many days in full; after that only the drift. */
        const val FULL_DAYS = 14
        const val ACTIVE_CONCERNS = 3
    }
}

internal fun relKey(from: CharId, to: Who) = "$from>${to.key}"

internal fun pairKey(a: String, b: String) = if (a < b) "$a>$b" else "$b>$a"

internal fun minutesBetween(a: HomeClock, b: HomeClock): Int = (b.day - a.day) * 1440 + (b.minute - a.minute)

internal fun HomeClock.plusMinutes(m: Int): HomeClock {
    val t = day * 1440 + minute + m
    return HomeClock(t / 1440, t % 1440)
}
