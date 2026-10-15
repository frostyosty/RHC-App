package com.rockhard.blocker.homevisits.mind

import com.rockhard.blocker.homevisits.HomeClock
import com.rockhard.blocker.homevisits.Rng
import com.rockhard.blocker.homevisits.house.RoomKind
import com.rockhard.blocker.homevisits.story.CharId
import kotlin.math.roundToLong

/** The three pretend players of the 90-day soak. */
enum class SoakPlayer { KIND, GOSSIP, RANDOM }

class SoakResult(
    val player: SoakPlayer,
    val seed: Long,
    /** The daily "what happened" log, one line per day. */
    val log: List<String>,
    /** Every relationship at the end of each day, by "from>to" key: for the per-relationship charts. */
    val history: List<Map<String, Relationship>>,
    /** FNV-1a over the whole history: the same seed must give the same hash. */
    val hash: Long,
    /** Anything that went out of range. Empty when all is well. */
    val problems: List<String>,
    val mind: Mind
)

/**
 * Runs the engine for [days] simulated days with a pretend player, without step 08's visit planner:
 * each day up to two characters who want to visit come round, chat for three rounds and leave.
 * Pure, so tools/homevisits_sim can draw its charts from [SoakResult.history].
 */
object MindSoak {
    fun run(seed: Long, player: SoakPlayer, days: Int = 90, cast: Cast = TestCast.cast): SoakResult {
        val rng = Rng(seed)
        val mind = Mind(cast, MindState(), rng)
        val log = mutableListOf<String>()
        val history = mutableListOf<Map<String, Relationship>>()
        val problems = mutableListOf<String>()
        var hash = -0x340d631b7bdddcdbL
        fun mix(s: String) { for (c in s) hash = (hash xor c.code.toLong()) * 0x100000001b3L }
        var factN = 0

        for (day in 1..days) {
            mind.advanceTo(HomeClock(day, 8 * 60))
            val roll = rng.stream("soak.visit", day)
            val visitors = cast.ids.map { it to mind.wantsToVisit(it, day) }
                .filter { roll.nextFloat() < it.second * 0.6f }
                .sortedByDescending { it.second }.take(2).map { it.first }.sorted()
            val line = StringBuilder("day $day: ")
            if (visitors.isEmpty()) line.append("nobody came.")
            else {
                var minute = 15 * 60
                fun at() = HomeClock(day, minute)
                mind.advanceTo(at())
                mind.startScene(visitors, RoomKind.LOUNGE)
                line.append(visitors.joinToString(" and ")).append(" came round. ")
                for (v in visitors) mind.perceive(Event(EventKind.ARRIVED, at(), by = Who.Char(v)))
                if (day % 4 == 0) {
                    val r = rng.stream("soak.item", day)
                    val tags = listOf(Tag.entries[r.nextInt(Tag.entries.size)], Tag.entries[r.nextInt(Tag.entries.size)]).distinct()
                    mind.perceive(Event(EventKind.SAW_ITEM, at(), item = "item$day", tags = tags, room = RoomKind.LOUNGE))
                    line.append("You had something new (${tags.joinToString { it.word }}). ")
                }
                val r = rng.stream("soak.player", day)
                for (round in 0 until 3) for (v in visitors) {
                    if (v !in (mind.state.scene?.present ?: emptyList())) continue
                    minute += 5
                    val said = mind.choose(v, characterOptions(mind, v, visitors))
                    mind.perceive(said.event(Who.Char(v), at()))
                    line.append("$v: ${describe(said)}. ")
                    if (said.act == Act.LEAVE_EARLY) { mind.perceive(Event(EventKind.LEFT, at(), by = Who.Char(v))); continue }
                    minute += 2
                    val reply = playerReply(player, mind, v, said, visitors, day, round, r) { "soak:${day}:${factN++}" }
                    mind.perceive(reply.event(Who.Player, at()))
                    line.append("You: ${describe(reply)}. ")
                }
                minute += 5
                for (v in mind.state.scene?.present.orEmpty()) mind.perceive(Event(EventKind.LEFT, at(), by = Who.Char(v)))
                val summary = mind.endScene()
                val big = summary.biggest(2).joinToString { "${it.from}→${it.to.key.removePrefix("@")} ${it.field.name.lowercase()} ${fmt(it.delta)}" }
                if (big.isNotEmpty()) line.append("Biggest changes: $big.")
            }
            log += line.toString().trim()

            val snap = mind.state.relationships.toList().sortedBy { it.first }.toMap()
            history += snap
            mix("d$day")
            for ((k, rel) in snap) {
                mix(k)
                for (f in Field.entries) {
                    val v = rel[f]
                    if (v.isNaN() || v < f.min || v > f.max) problems += "day $day $k ${f.name}=$v"
                    mix((v * 100f).roundToLong().toString())
                }
            }
            for (c in cast.ids) {
                val m = mind.mood(c)
                if (listOf(m.p, m.a, m.d).any { it.isNaN() || it < -1f || it > 1f }) problems += "day $day mood $c=$m"
            }
        }
        return SoakResult(player, seed, log, history, hash, problems, mind)
    }

    private fun fmt(v: Float): String { val r = (v * 10f).roundToLong() / 10.0; return if (r >= 0) "+$r" else "$r" }

    private fun describe(a: SocialAction): String {
        val to = a.to?.let { if (it == Who.Player) " you" else " ${it.key}" } ?: ""
        val about = a.about?.let { " about ${if (it == Who.Player) "you" else it.key}" } ?: ""
        val tags = if (a.tags.isNotEmpty()) " (${a.tags.joinToString { it.word }})" else ""
        return "${a.act.name.lowercase().replace('_', ' ')}$to$about$tags"
    }

    private fun characterOptions(mind: Mind, v: CharId, visitors: List<CharId>): List<SocialAction> {
        val sheet = mind.cast[v]
        val fav = sheet.tastes.entries.maxByOrNull { it.value }?.key
        val others = mind.cast.ids.filter { it != v }
        val disliked = others.minByOrNull { mind.relationship(v, Who.Char(it)).affinity }!!
        val liked = others.maxByOrNull { mind.relationship(v, Who.Char(it)).affinity }!!
        val out = mutableListOf(
            SocialAction(Act.GREET, to = Who.Player),
            SocialAction(Act.JOKE),
            SocialAction(Act.ASK_ABOUT, to = Who.Player, tags = listOfNotNull(fav)),
            SocialAction(Act.CONFIDE, to = Who.Player),
            SocialAction(Act.CRITICISE, to = Who.Player, about = Who.Char(disliked)),
            SocialAction(Act.PRAISE, to = Who.Player, about = Who.Char(liked)),
            SocialAction(Act.LEAVE_EARLY)
        )
        visitors.filter { it != v }.forEach { out += SocialAction(Act.COMFORT, to = Who.Char(it)) }
        val known = mind.state.known[v].orEmpty().keys.mapNotNull { mind.state.facts[it] }
            .filter { it.about != null && it.about != Who.Player && !mind.knows(Who.Player, it.id) }
            .maxByOrNull { kotlin.math.abs(it.sentiment) }
        if (known != null) out += SocialAction(Act.TELL, to = Who.Player, fact = known)
        return out
    }

    private fun playerReply(
        player: SoakPlayer, mind: Mind, v: CharId, said: SocialAction, visitors: List<CharId>,
        day: Int, round: Int, r: kotlin.random.Random, newFact: () -> String
    ): SocialAction {
        val me = Who.Char(v)
        val absent = mind.cast.ids.filter { it !in visitors }
        val target = (absent.ifEmpty { mind.cast.ids.filter { it != v } }).let { it[r.nextInt(it.size)] }
        val tag = Tag.entries[r.nextInt(Tag.entries.size)]
        val complaint = said.act == Act.CRITICISE && said.about is Who.Char
        return when (player) {
            SoakPlayer.KIND -> if (complaint) SocialAction(Act.DEFEND, to = me, about = said.about)
            else when ((day + round) % 5) {
                0 -> SocialAction(Act.PRAISE, to = me, tags = listOf(tag))
                1 -> SocialAction(Act.THANK, to = me)
                2 -> SocialAction(Act.ASK_ABOUT, to = me, tags = listOf(tag))
                3 -> SocialAction(Act.COMFORT, to = me)
                else -> SocialAction(Act.INVITE, to = me)
            }
            SoakPlayer.GOSSIP -> if (complaint) SocialAction(Act.SIDE_WITH, to = me, about = said.about, sentiment = -0.5f)
            else when ((day + round) % 4) {
                0, 2 -> SocialAction(Act.TELL, to = me, fact = Fact(newFact(), by = Who.Player, about = Who.Char(target), sentiment = -0.6f, tags = listOf(tag)))
                1 -> SocialAction(Act.TEASE, to = me)
                else -> SocialAction(Act.CRITICISE, to = me, about = Who.Char(target))
            }
            SoakPlayer.RANDOM -> when (r.nextInt(9)) {
                0 -> SocialAction(Act.PRAISE, to = me, tags = listOf(tag))
                1 -> SocialAction(Act.THANK, to = me)
                2 -> SocialAction(Act.TEASE, to = me)
                3 -> SocialAction(Act.CRITICISE, to = me, about = Who.Char(target))
                4 -> SocialAction(Act.TELL, to = me, fact = Fact(newFact(), by = Who.Player, about = Who.Char(target), sentiment = -0.6f, tags = listOf(tag)))
                5 -> SocialAction(Act.DEFEND, to = me, about = Who.Char(target))
                6 -> SocialAction(Act.JOKE)
                7 -> SocialAction(Act.ASK_ABOUT, to = me, tags = listOf(tag))
                else -> SocialAction(Act.CHANGE_SUBJECT)
            }
        }
    }
}
