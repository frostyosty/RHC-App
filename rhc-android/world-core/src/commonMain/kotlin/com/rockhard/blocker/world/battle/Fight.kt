package com.rockhard.blocker.world.battle

import com.rockhard.blocker.GameData
import com.rockhard.blocker.Netbeast
import com.rockhard.blocker.SkillEngine
import kotlin.math.abs

// The battle rules: one copy for the phone (the Wilds and the old battle
// screen), the web client and the server. Moved here from app's
// engines/combat/ and SkillEngine in NETBEASTS_WEB_PLAN step 03.
//
// Pure and deterministic, like the sim: 30 Hz ticks ([TICK_HZ]), inputs only,
// its own seeded dice ([FightRng]), no clock (the host's time comes in once,
// in FightSetup.nowMs). What used to be a postDelayed is a [Pending] step
// counting down in ticks, with the same lengths as before (1500 ms is 45 ticks).
// The rules never touch the screen or the save: they emit [FightEvent]s, and
// the host shows them and writes the save down.
//
// A fight has any number of sides. Today's fights are a player against a wild
// beast, a boss, a poacher or the red-wall invader; multiplayer adds more
// players to the same fight. Each side keeps its own shield, poison, stun,
// terrify, triple strike, evasion and last stand. A move has an attacker and a
// target side; a beast run by the rules strikes back at whoever provoked it,
// or switches to another player who has a netbeast out.

/** A step waiting to happen, [ticks] from now. Plain data, so it goes in a snapshot. */
data class Pending(
    val kind: Step,
    var ticks: Int,
    val side: Int,
    val target: Int = -1,
    val move: String = "",
    val n: Int = 0,
    val pet: Int = -1,
)

enum class Step {
    /** [side] (run by the rules) takes its turn, provoked by [target]. */
    COUNTER,
    /** [side]'s [move] lands on [target]. */
    MOVE_LAND,
    /** [side]'s [move] lands on [target]'s netbeast [pet] for [n] before defences. */
    COUNTER_LAND,
    PUNCH_LAND,
    SWORD_LAND,
    /** A lost last stand: [side] lunges at the player of [target]... */
    OBLITERATE,
    /** ...and the blow lands. */
    OBLITERATE_LAND,
    /** [side] (a poacher) sends out its netbeast [pet]. */
    SEND_NEXT,
    /** [side] (a player in the Wilds) throws the cage of netbeast [pet]. */
    THROW_NEXT,
    /** [side]'s fallen netbeast is put away (the Wilds). */
    RECALL,
    /** [side] strikes [target]'s new netbeast, which came out after a swap. */
    FREE_HIT,
    /** The invader fades into the shadows. */
    FADE,
    CLOSE,
}

/** One side's state: what CombatState and GameActivity's battle fields held, once per side. */
class Side(
    val index: Int,
    val kind: SideKind,
    val party: MutableList<Netbeast>,
    val benched: Set<Int>,
    val entity: Int,
    val ownerId: String,
    val difficulty: Int,
    val sword: Boolean,
    val amulet: Boolean,
    val location: String,
    val weakness: String,
    val captive: Netbeast?,
) {
    /** The netbeast that's out (or whose cage is in the air), as an index into [party]; -1 for none. */
    var active = -1
    /** Its cage is in the air: it isn't out yet. */
    var arriving = false
    val fallen = LinkedHashSet<Int>()
    var lastStand = false
    var outcome: FightOutcome? = null

    var shield = 0
    var poison = 0
    var stunned = false
    var terrified = false
    var tripleStrike = false
    var evasion = false
    var bagNets = 0

    /** The Wilds: ticks until the beast loses patience with this player, and ticks before that starts counting. */
    var patience = 0
    var busy = 0
    /** The battle screen: ticks until a Basic Attack is shouted for you (-1: off). */
    var timer = -1
    /** The battle screen, in a last stand: ticks until the beast strikes you (-1: off). */
    var strike = -1
    /** The Wilds: the beast strikes the next netbeast as soon as it's out (a swap, or a cage knocked loose). */
    var freeHit = false
    var lastPunch = -100L
    /** A poacher: how many of his have come out of their cages. */
    var foesOut = 0
    /** A player's netbeasts that fought: they level up if the fight is won. */
    val participated = LinkedHashSet<Int>()

    val isPlayer get() = kind == SideKind.PLAYER
    val inFight get() = outcome == null

    /** The netbeast fighting for this side (out, or its cage in the air), or null. */
    fun pet(): Netbeast? = if (active >= 0 && !lastStand && active !in fallen) party[active] else null

    fun living(): List<Int> = party.indices.filter { it !in fallen }

    /** Netbeasts that can still be sent in: alive and not out roaming. */
    fun available(): List<Int> = living().filter { it !in benched }

    fun copy(): Side {
        val s = Side(index, kind, party.map { it.copy() }.toMutableList(), benched, entity, ownerId, difficulty, sword, amulet, location, weakness, captive?.copy())
        s.active = active; s.arriving = arriving; s.fallen += fallen; s.lastStand = lastStand; s.outcome = outcome
        s.shield = shield; s.poison = poison; s.stunned = stunned; s.terrified = terrified
        s.tripleStrike = tripleStrike; s.evasion = evasion; s.bagNets = bagNets
        s.patience = patience; s.busy = busy; s.timer = timer; s.strike = strike; s.freeHit = freeHit
        s.lastPunch = lastPunch; s.foesOut = foesOut; s.participated += participated
        return s
    }
}

/** Everything a fight is, for a snapshot (copies, so the fight can go on without changing it). */
data class FightSnapshot(
    val id: Int, val setup: FightSetup, val tick: Long, val rng: Long, val sides: List<Side>,
    val pending: List<Pending>, val over: Boolean, val closed: Boolean, val lastHit: Int, val result: FightResult?,
)

class Fight private constructor(val id: Int, val setup: FightSetup, val sides: List<Side>, val rng: FightRng) {
    companion object {
        const val TICK_HZ = 30
        /** The beast's patience in the Wilds before it acts on its own (7 s), and in a last stand (3 s). */
        const val PATIENCE = 7 * TICK_HZ
        const val LAST_STAND_PATIENCE = 3 * TICK_HZ
        /** After you act, patience waits this long for the exchange to play out (1.8 s). */
        const val BUSY = 54
        /** The battle screen's timer: 6 s, shown for its last 3. */
        const val TIMER = 6 * TICK_HZ
        const val TIMER_SHOWN = 3 * TICK_HZ
        /** A beat between a move and its effect, or a lunge and its blow (300 ms). */
        const val LAND = 9
        /** A slip, a grouchy refusal or a miss: the beast's turn comes this long after (1500 ms). */
        const val MISS_PAUSE = 45
        /** A net that broke or bounced off (1000 ms); a poacher's next netbeast. */
        const val NET_PAUSE = 30
        const val NEXT_FOE = 30
        /** The Wilds: a beat so you see your netbeast fall before the next cage flies (1200 ms). */
        const val NEXT_CAGE = 36
        /** The Wilds: the beast strikes a netbeast that came out after a swap (500 ms). */
        const val FREE_HIT_PAUSE = 15
        /** The Wilds: a lost last stand's lunge and its blow (450 ms). */
        const val WILDS_OBLITERATE = 14
        /** Punches can be thrown this fast (150 ms; they take no turn). */
        const val PUNCH_GAP = 5
        /** From the end to the walk going on (or back to the Hub). */
        const val CLOSE_PAUSE = 60

        private val MUTATIONS = listOf("Vampiric", "Thick-Skinned", "Frenzy", "Swift", "Looter", "Obscure")

        /** 15 + int(25 * e^(-0.05 n)) for n nets, as a table so no platform's exp can round it differently. */
        private val CATCH_BASE = intArrayOf(
            40, 38, 37, 36, 35, 34, 33, 32, 31, 30, 30, 29, 28, 28, 27, 26, 26, 25, 25, 24, 24, 23, 23, 22, 22, 22,
            21, 21, 21, 20, 20, 20, 20, 19, 19, 19, 19, 18, 18, 18, 18, 18, 18, 17, 17, 17, 17, 17, 17, 17, 17, 16,
            16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16,
        )

        fun catchBase(totalNets: Int) = CATCH_BASE.getOrElse(totalNets.coerceAtLeast(0)) { 15 }

        /** A new fight. Call [start] for its opening events. */
        fun create(id: Int, setup: FightSetup, sides: List<SideSetup>, seed: Long): Fight {
            val built = sides.mapIndexed { i, s ->
                Side(i, s.kind, s.party.toMutableList(), s.benched, s.entity, s.ownerId, s.difficulty, s.sword, s.amulet, s.location, s.weakness, s.captive).also {
                    it.bagNets = s.bagNets; it.tripleStrike = s.tripleStrike; it.evasion = s.evasion
                }
            }
            val f = Fight(id, setup, built, FightRng(seed))
            f.pendingStart = sides
            return f
        }

        fun restore(s: FightSnapshot): Fight {
            val f = Fight(s.id, s.setup, s.sides.map { it.copy() }, FightRng(s.rng))
            f.tick = s.tick; f.pending += s.pending.map { it.copy() }
            f.over = s.over; f.closed = s.closed; f.lastHit = s.lastHit; f.result = s.result
            f.pendingStart = null
            return f
        }
    }

    var tick = 0L
        private set
    /** Decided: [result] is set and nothing more happens but the close. */
    var over = false
        private set
    /** Gone: the host drops it. */
    var closed = false
        private set
    /** The side that landed the last hit (the killing blow, once it's over). */
    var lastHit = -1
        private set
    var result: FightResult? = null
        private set

    private val pending = mutableListOf<Pending>()
    private val events = mutableListOf<FightEvent>()
    private var pendingStart: List<SideSetup>? = null

    fun snapshot() = FightSnapshot(id, setup, tick, rng.state, sides.map { it.copy() }, pending.map { it.copy() }, over, closed, lastHit, result)

    val wilds get() = setup.style == FightStyle.WILDS

    // ------------------------------------------------------------------ the host's side

    /** The opening: rolls a wild foe, sends the first fighters in and says so. Call once, right after [create]. */
    fun start(): List<FightEvent> {
        val setups = pendingStart ?: return drain()
        pendingStart = null
        for (s in sides) {
            val spec = setups[s.index].wild
            if (spec != null) s.party += rollWild(spec)
        }
        for (s in sides) {
            when {
                s.isPlayer && wilds -> { s.active = -1; s.patience = PATIENCE }
                s.isPlayer -> {
                    val a = setups[s.index].active
                    if (a in s.available()) s.active = a
                }
                s.kind == SideKind.POACHER && wilds -> { s.active = 0; s.arriving = true }
                else -> s.active = 0
            }
        }
        resetArena()
        val foe = sides.firstOrNull { !it.isPlayer }
        for (s in sides.filter { it.isPlayer }) {
            if (wilds && foe != null) {
                val f = foe.pet()
                val cagesOrFists = if (s.available().isEmpty()) noCagesLine(s) else "> Quick! Pick a cage to throw!"
                when {
                    foe.kind == SideKind.POACHER -> log("\n> 🦹 POACHER: 'Come and take it!'\n> He throws a cage...", s.index)
                    f != null && f.type == "EventBoss" -> { log("\n> 👑 ${f.species} (HP: ${f.maxHp}) rears up in front of you! Defend ${setup.place}!", s.index); log(cagesOrFists, s.index) }
                    f != null -> { log("\n> ⚠️ A wild ${f.name} (HP: ${f.maxHp}) squares up to you! You circle each other...", s.index); log(cagesOrFists, s.index) }
                }
                events += FightEvent.Buzz(80, s.index)
            }
            if (s.available().isEmpty() || (!wilds && s.pet() == null)) enterLastStand(s, announce = false) else refreshTurn(s)
        }
        if (wilds) for (f in sides.filter { it.kind == SideKind.POACHER }) events += FightEvent.SentOut(f.index, f.active)
        return drain()
    }

    /** One tick: [inputs] by side index, then whatever was waiting, then the beasts' patience. */
    fun step(inputs: Map<Int, FightInput> = emptyMap()): List<FightEvent> {
        if (closed) return drain()
        tick++
        for (s in sides) inputs[s.index]?.let { apply(s, it) }
        val due = mutableListOf<Pending>()
        val it = pending.iterator()
        while (it.hasNext()) { val p = it.next(); if (--p.ticks <= 0) { due += p; it.remove() } }
        for (p in due) run(p)
        for (s in sides) if (s.isPlayer) clocks(s)
        return drain()
    }

    /**
     * [side]'s netbeast is out of its cage (the World says so when the cage opens). For a player
     * it faces the foe and the beast's patience starts over; for a poacher it's the one you fight now.
     */
    fun petOut(side: Int) {
        val s = sides.getOrNull(side) ?: return
        if (!s.arriving) return
        s.arriving = false
        if (s.isPlayer) {
            val p = s.pet() ?: return
            val foe = foeOf(s)
            log("> ${p.name} bursts out to face ${foe?.let { foeLabel(it) } ?: "the beast"}!", s.index)
            refreshTurn(s)
            if (s.freeHit && foe != null) {
                s.freeHit = false
                acted(s)
                schedule(Pending(Step.FREE_HIT, FREE_HIT_PAUSE, foe.index, s.index))
            }
        } else {
            for (pl in sides.filter { it.isPlayer && it.inFight }) {
                pl.patience = if (pl.lastStand) LAST_STAND_PATIENCE else PATIENCE
                if (s.foesOut == 0) {
                    log("> The poacher's ${s.pet()?.name} bursts out of its cage!", pl.index)
                    log(if (pl.available().isEmpty()) noCagesLine(pl) else "> Quick! Pick a cage to throw!", pl.index)
                }
            }
            s.foesOut++
        }
    }

    /** Health for the HUD and the battle screen's bars. */
    fun fighter(side: Int): Netbeast? = sides.getOrNull(side)?.pet()

    /** The foe a player is up against (the first side the rules run that's still in it). */
    fun foeOf(s: Side): Side? = sides.firstOrNull { !it.isPlayer && it.inFight }

    /** A foe's netbeast is on its way (a poacher's cage in the air): you can't act on it yet. */
    fun foeArriving(s: Side) = foeOf(s)?.let { it.arriving || it.pet() == null } == true

    /** The Wilds' patience bar: 1 full .. 0 it acts; negative when it isn't counting. */
    fun patienceShown(s: Side): Float {
        if (!wilds || over || s.arriving || foeArriving(s)) return -1f
        return (s.patience.toFloat() / if (s.lastStand) LAST_STAND_PATIENCE else PATIENCE).coerceIn(0f, 1f)
    }

    /** The battle screen's timer bar: 1 .. 0 for its last 3 seconds; negative while it's hidden. */
    fun timerShown(s: Side): Float = if (s.timer in 1..TIMER_SHOWN) s.timer.toFloat() / TIMER_SHOWN else -1f

    /** Whether [s] can act now (a move, an item, a cage): nothing of theirs in flight, nobody arriving. */
    fun canAct(s: Side) = !over && s.inFight && !s.arriving && !foeArriving(s) && !exchanging(s)

    // ------------------------------------------------------------------ inputs

    private fun apply(s: Side, inp: FightInput) {
        if (over || !s.isPlayer || !s.inFight) return
        val t = targetFor(s, inp.target) ?: return
        when {
            inp.abandon -> abandon(s)
            inp.item != null -> if (canAct(s) && !s.lastStand) item(s, t, inp.item)
            inp.cage >= 0 -> cage(s, t, inp.cage)
            inp.move in 1..3 -> {
                if (s.lastStand || (s.pet() == null && s.available().isEmpty())) {
                    if (tick - s.lastPunch >= PUNCH_GAP && !foeArriving(s)) { s.lastPunch = tick; punch(s, t) }
                    return
                }
                val pet = s.pet() ?: return
                if (!canAct(s) || t.arriving) return
                playerMove(s, t, when (inp.move) { 1 -> pet.move1; 2 -> pet.move2; else -> pet.move3 })
            }
        }
    }

    /** The side [s] aims at: [wanted] if it's another side still in it with someone to hit, else its foe. */
    private fun targetFor(s: Side, wanted: Int): Side? {
        val w = sides.getOrNull(wanted)
        if (w != null && w !== s && w.inFight && (!w.isPlayer || w.pet() != null)) return w
        return foeOf(s)
    }

    private fun exchanging(s: Side) = pending.any {
        (it.kind == Step.COUNTER || it.kind == Step.MOVE_LAND || it.kind == Step.COUNTER_LAND || it.kind == Step.FREE_HIT) &&
            (it.side == s.index || it.target == s.index)
    }

    private fun cage(s: Side, t: Side, i: Int) {
        if (i !in s.available() || s.lastStand) return
        if (wilds) {
            if (!canAct(s)) return
            if (s.active < 0) {
                acted(s)
                log("> You throw ${s.party[i].name}'s cage!", s.index)
                throwCage(s, i)
            } else if (i != s.active) {
                // a swap costs a turn, as on the battle screen: the beast strikes once the new one is out
                acted(s)
                log("\n> You swapped to ${s.party[i].name}!")
                resetArena()
                s.freeHit = true
                throwCage(s, i)
            }
            return
        }
        acted(s)
        if (s.party.size - s.fallen.size <= 1 || s.pet() == null) return
        if (i == s.active) { log("\n> ${s.party[i].name} is already fighting!", s.index); return }
        if (!canAct(s)) return
        s.active = i
        log("\n> You swapped to ${s.party[i].name}!")
        sendOut(s, i)
        refreshTurn(s)
        if (PartyTraits.hasHoard(livingParty(s))) log("> 📦 [Hoard] The swap cost no turn!") else counter(t, s)
    }

    private fun throwCage(s: Side, i: Int) {
        s.active = i; s.arriving = true
        events += FightEvent.SentOut(s.index, i)
    }

    private fun item(s: Side, t: Side, item: FightItem) {
        val pet = s.pet() ?: return
        val foe = t.pet()
        when (item) {
            FightItem.POTION -> {
                acted(s)
                if (pet.eqPots <= 0) return
                pet.eqPots--
                when (rng.nextInt(3)) {
                    0 -> { s.tripleStrike = true; log("\n> 🧪 Potion glows RED! Next attack will be a TRIPLE STRIKE!") }
                    1 -> { s.evasion = true; log("\n> 🧪 Potion glows BLUE! Guaranteed EVASION!") }
                    else -> { pet.maxHp += 10; pet.hp = (pet.hp + 10).coerceAtMost(pet.maxHp); log("\n> 🧪 Potion glows GREEN! Max HP increased!") }
                }
                changed(s); refreshTurn(s)
                counter(t, s)
            }
            FightItem.SPRAY -> {
                acted(s)
                if (pet.eqSprays <= 0) return
                pet.eqSprays--
                log("\n> 💨 ${pet.name} used a Repel Spray!")
                changed(s); refreshTurn(s)
                if (t.kind == SideKind.INVADER && rng.nextInt(100) < 5) { log("> CRITICAL SUCCESS! Boss flees."); end(FightOutcome.FOE_RAN, s, t) }
                else if ((t.kind == SideKind.WILD || t.kind == SideKind.BOSS) && rng.nextInt(100) < 60) { log("> The wild ${foe?.name} was blinded and ran away!"); end(FightOutcome.FOE_RAN, s, t) }
                else { log("> No effect..."); counter(t, s) }
            }
            FightItem.NET -> {
                acted(s)
                if (foe == null || pet.eqNets <= 0) return
                pet.eqNets--
                changed(s); refreshTurn(s)
                log("\n--- PLAYER TURN ---")
                log("> You threw a Net at ${foe.name}!")
                events += FightEvent.Fx(t.index, false, "net")
                if (foe.type == "EventBoss" || t.kind == SideKind.POACHER || t.isPlayer) {
                    log("> ❌ You cannot catch this beast! The net bounced off!")
                    reply(t, s, NET_PAUSE)
                    return
                }
                val totalNets = s.bagNets + s.party.sumOf { it.eqNets }
                val isClear = pet.infusionStacks > 0 && pet.infusionEl == "Clear"
                val catchPenalty = (s.difficulty / 100f * 20).toInt()
                val catchChance = catchBase(totalNets).let { if (isClear) it + 15 else it } - catchPenalty
                if (rng.nextInt(100) < catchChance) {
                    log("> 🌟 SUCCESS! You caught ${foe.name} mid-battle!")
                    caught(s, t, foe)
                } else {
                    log("> 💢 The net broke! ${foe.name} breaks free!")
                    reply(t, s, NET_PAUSE)
                }
            }
        }
    }

    private fun caught(s: Side, t: Side, foe: Netbeast) {
        foe.isNew = true
        t.fallen += t.active
        events += FightEvent.Caught(s.index, foe)
        end(FightOutcome.CAUGHT, s, t)
    }

    private fun abandon(s: Side) {
        acted(s)
        val pet = s.pet() ?: return
        if (s.lastStand) return
        log("\n> You ABANDONED ${pet.name} to save yourself!")
        events += FightEvent.Move(s.index, false, Motion.DEATH)
        s.fallen += s.active
        events += FightEvent.Abandoned(s.index, s.active)
        s.active = -1
        leave(s, FightOutcome.ABANDONED)
    }

    // ------------------------------------------------------------------ a player's turn

    private fun playerMove(s: Side, t: Side, moveName: String) {
        acted(s)
        if (over) return
        val pet = s.pet()
        if (s.lastStand || pet == null) { punch(s, t); return }
        s.participated += s.active

        log("\n--- PLAYER TURN ---")
        if (s.stunned) { s.stunned = false; log("> ⚡ ${pet.name} is STUNNED and can't move!"); reply(t, s, MISS_PAUSE); return }
        if (s.poison > 0 && poisonTick(s, t)) return
        if (setup.weather == "Rain" && rng.nextInt(100) < 15) {
            log("> 🌧️ The ground is slick from the rain!\n> ${pet.name} slipped and missed!")
            events += FightEvent.Move(t.index, false, Motion.EVADE)
            reply(t, s, MISS_PAUSE); return
        }
        if (pet.isGrouchy(setup.nowMs) && rng.nextInt(100) < 30) {
            log("> 😡 ${pet.name} is feeling [Grouchy]!\n> ${pet.name} ignored your command!")
            reply(t, s, MISS_PAUSE); return
        }

        log("> ${pet.name} attempts [$moveName]")
        events += FightEvent.Move(s.index, false, Motion.ATTACK)
        pose(s, false, "attack")
        events += FightEvent.Sound("spr_${pet.name.substringAfter("]").trim().lowercase()}_attack")
        events += FightEvent.Sound("fx_${moveName.lowercase()}")
        val foe = t.pet()
        events += FightEvent.Projectile(s.index, t.index, pet.type, foe?.type ?: "Normal", moveName)

        val isFlying = foe?.type == "Flying"
        val isElusive = foe?.hasTrait("Elusive") == true
        if ((isFlying && !SkillEngine.isAntiAir(moveName) && !SkillEngine.isAntiFlyingDouble(moveName) && rng.nextInt(100) < 90) || (isElusive && rng.nextInt(100) < 30)) {
            log(if (isElusive) "> 💨 ${foe?.name} is [Elusive] and dodged instantly!" else "> ✈️ ${foe?.name} is out of reach!")
            events += FightEvent.Move(t.index, false, Motion.EVADE)
            reply(t, s, MISS_PAUSE)
            return
        }
        schedule(Pending(Step.MOVE_LAND, LAND, s.index, t.index, moveName, pet = s.active))
    }

    private fun moveLands(s: Side, t: Side, moveName: String, petIndex: Int) {
        if (over || s.active != petIndex) return
        val pet = s.pet() ?: return
        val foe = t.pet() ?: return
        if (t.kind == SideKind.INVADER) {
            log("> ${foe.name} is out of reach!")
            events += FightEvent.Move(t.index, false, Motion.EVADE)
            counter(t, s)
            return
        }
        val (skillDmg, skillLog) = resolveSkill(s, pet, t, foe, moveName)
        var baseDmg = skillDmg

        if (moveName == pet.lastMove) { if (pet.moveStreak < 0) pet.moveStreak = 1 else pet.moveStreak++ }
        else { if (pet.moveStreak > 0) pet.moveStreak = -1 else pet.moveStreak-- }
        pet.lastMove = moveName

        val currentTrait = leadTrait(pet)
        if (pet.moveStreak >= 4 && currentTrait != "Persistence") {
            val cleanName = cleanName(pet); pet.name = "[Persistence] $cleanName"
            log("> 🔄 $cleanName learned [Persistence] from relentless repetition!")
        } else if (pet.moveStreak <= -4 && currentTrait != "Dynamism") {
            val cleanName = cleanName(pet); pet.name = "[Dynamism] $cleanName"
            log("> 🔄 $cleanName learned [Dynamism] from unpredictable tactics!")
        }

        val updatedTrait = leadTrait(pet)
        if (updatedTrait == "Persistence" && pet.moveStreak > 1) {
            baseDmg = (baseDmg * (1.0 + (0.15 * pet.moveStreak))).toInt()
            log("> 🔁 [Persistence] amplified damage by ${(0.15 * pet.moveStreak * 100).toInt()}%!")
        } else if (updatedTrait == "Dynamism" && pet.moveStreak < -1) {
            baseDmg = (baseDmg * (1.0 + (0.15 * abs(pet.moveStreak)))).toInt()
            log("> 🔀[Dynamism] amplified damage by ${(0.15 * abs(pet.moveStreak) * 100).toInt()}%!")
        }

        if (foe.type == "EventBoss") {
            if (pet.name.contains(t.weakness)) { baseDmg = (foe.maxHp * 0.50).toInt(); log("> 🎯 EXPLOITED WEAKNESS! Massive damage!") }
            else { baseDmg = (foe.maxHp * 0.125).toInt(); log("> 🛡️ Thick armor! Standard attacks are weak!") }
        }

        if (foe.hasTrait("Enraged")) baseDmg = (baseDmg * 1.2).toInt()
        if (foe.type == "Flying" && SkillEngine.isAntiFlyingDouble(moveName)) { baseDmg *= 2; log("> 💥 CRITICAL WEAKNESS! Double damage against Flying beasts!") }

        var critChance = 15
        if (pet.hasTrait("Wary")) { baseDmg = (baseDmg * 0.9).toInt(); log("> 😨 [Wary] reduced damage by 10%!") }
        if (pet.hasTrait("Worried")) { baseDmg = (baseDmg * 0.8).toInt(); log("> 😰[Worried] reduced damage by 20%!") }
        if (pet.hasTrait("Petrified")) { baseDmg = (baseDmg * 0.5).toInt(); log("> 😱 [Petrified] reduced damage by 50%!") }
        if (pet.hasTrait("Frenzy")) critChance += 15

        if (pet.infusionStacks > 0) {
            if (pet.infusionEl == "Storm") { baseDmg += (baseDmg * 0.5 * pet.infusionStacks).toInt(); log("> ⚡ STORM INFUSION! +${50 * pet.infusionStacks}% DAMAGE!") }
            else if (pet.infusionEl == "Rain") { critChance += (20 * pet.infusionStacks); log("> 🌧️ RAIN INFUSION! +${20 * pet.infusionStacks}% Crit chance!") }
            else if (pet.infusionEl == setup.weather) { val bonus = pet.infusionStacks * 5; baseDmg += bonus; log("> 🌟 TACTICAL ADVANTAGE! ${setup.weather} boosts damage by $bonus!") }
        }
        if (pet.amulets > 0) { baseDmg = (baseDmg * (1.0 + (0.30 * pet.amulets))).toInt(); log("> 🔮 Amulet of the Titan amplifies power (+30%)!") }

        var finalDmg = baseDmg
        if (s.tripleStrike) { finalDmg = baseDmg * 3; s.tripleStrike = false; log("> ⚔️ TRIPLE STRIKE ACTIVATED!") }
        else if (rng.nextInt(100) < critChance) { finalDmg = (baseDmg * 1.5).toInt(); log("> 💥 CRITICAL HIT!"); events += FightEvent.Buzz(150, s.index) }

        if (pet.hasTrait("Vampiric")) { val heal = (finalDmg * 0.15).toInt().coerceAtLeast(1); pet.hp = (pet.hp + heal).coerceAtMost(pet.maxHp); log("> 🧛[Vampiric] stole $heal HP!") }

        if (t.isPlayer) finalDmg = absorb(t, finalDmg)
        foe.hp -= finalDmg
        lastHit = s.index
        events += FightEvent.Move(t.index, false, Motion.SHAKE)
        pose(t, false, "hit")
        attackFx(t, false, moveName)
        events += FightEvent.Buzz(50, s.index)
        if (skillLog.isNotEmpty()) log(skillLog)

        if (foe.hasTrait("Spiked") && finalDmg > 0) {
            val recoil = (finalDmg * 0.1).toInt().coerceAtLeast(1); pet.hp -= recoil
            log("> 🌵[Spiked] recoil! ${pet.name} takes $recoil damage!")
        }
        if (t.terrified && foe.type == "Wild") {
            t.terrified = false
            if (foe.hasTrait("Colossal")) log("> 🏔️[Colossal] Immunity! ${foe.name} cannot be Terrified!")
            else { foe.hp = 0; log("> 👻 The wild ${foe.name} is TERRIFIED and gives up!") }
        }

        if (foe.hp > 0) log("> Hit! ${foe.name} takes $finalDmg damage. (HP: ${foe.hp.coerceAtLeast(0)}/${foe.maxHp})")
        changed(s)
        if (t.isPlayer) { changed(t); if (foe.hp <= 0) petFalls(t, s, foe, chopper = false) }
        else if (foe.hp <= 0) foeFainted(t, s) else counter(t, s)
    }

    /** What a move does: its damage before the attacker's traits, and its log line. */
    private fun resolveSkill(s: Side, pet: Netbeast, t: Side, foe: Netbeast, move: String): Pair<Int, String> {
        val def = SkillEngine.def(move)

        if (def.type == "Timeshift") {
            events += FightEvent.Loot(s.index, "aether", 120)
            return Pair(0, "> ⏳ TIMESHIFT! Manipulated the realm to restore 2:00 Aether!")
        }
        if (def.type == "Basic") {
            val dmg = (foe.hp * 0.10).toInt().coerceAtLeast(1)
            return Pair(dmg, "> ⏱️ Time ran out! Basic Attack stripped 10% of their current HP!")
        }
        val b = (pet.maxHp * 0.25).toInt()
        val roll = rng.nextInt(100)
        var dmg = if (roll < 30) (b * 0.8).toInt() else if (roll < 80) b else (b * 1.2).toInt()
        var log: String

        if (def.type == "Ultimate") {
            if (pet.hp <= pet.maxHp / 2) { dmg = b * 3; log = "> 🛑 ULTIMATE ACTIVATED! DEVASTATING DAMAGE!" }
            else { dmg = (b * 0.5).toInt(); log = "> ⚠️ Ultimate failed... requires HP below 50%!" }
            return Pair(dmg.coerceAtLeast(1), log)
        }

        log = if (roll < 30) "> 📉 Weak hit!" else if (roll < 80) "> ⚔️ Solid hit!" else "> 💥 Critical strike!"
        val bonusRoll = rng.nextInt(100)
        if (bonusRoll < def.effectChance) {
            when (def.effect) {
                "PurgeEnemy" -> {
                    foe.name = foe.species
                    foe.infusionStacks = 0
                    foe.infusionEl = "None"
                    log += "\n> ✝️ CLEANSED! The enemy was stripped of all unnatural power!"
                }
                "Lifesteal" -> {
                    val heal = (dmg * 0.5).toInt().coerceAtLeast(1)
                    pet.hp = (pet.hp + heal).coerceAtMost(pet.maxHp)
                    log += "\n> 🧛 LIFESTEAL! Recovered $heal HP!"
                }
                "DoubleStrike" -> { dmg *= 2; log += "\n> ⚔️⚔️ DOUBLE STRIKE! Hit twice in a row!" }
                "Stun" -> { t.stunned = true; log += "\n> ⚡ STUNNED! Enemy skips turn!" }
                "Poison" -> { t.poison++; log += "\n> ☠️ POISONED! Enemy takes passive damage!" }
                "Shield" -> {
                    val shieldAmt = (pet.maxHp * 0.2).toInt()
                    s.shield += shieldAmt
                    log += "\n> 🛡️ SHIELDED! Gained $shieldAmt Temp HP!"
                }
                "StealSpray" -> { events += FightEvent.Loot(s.index, "sprays", 1); log += "\n> 💨 STOLE a Repel Spray!" }
                "StealNet" -> { s.bagNets++; events += FightEvent.Loot(s.index, "nets", 1); log += "\n> 🕸️ STOLE a Net!" }
                "StealPotion" -> { events += FightEvent.Loot(s.index, "potions", 1); log += "\n> 🧪 STOLE a Potion!" }
                "StealCoins" -> { val c = rng.nextInt(5, 15); events += FightEvent.Loot(s.index, "coins", c); log += "\n> 🪙 STOLE $c Coins!" }
                "Terrify" -> { t.terrified = true; log += "\n> 👻 TERRIFIED! Enemy breaks formation!" }
            }
        }
        val struckTwice = bonusRoll < def.effectChance && def.effect == "DoubleStrike"
        val herd = PartyTraits.herdChance(livingParty(s), pet)
        if (!struckTwice && herd > 0 && rng.nextInt(100) < herd) {
            dmg *= 2
            log += "\n> 🐾⚔️ [Herd] DOUBLE STRIKE! Its pack drove it on! ($herd%)"
        }
        return Pair(dmg.coerceAtLeast(1), log)
    }

    // ------------------------------------------------------------------ fists

    /** A punch takes no turn: tap as fast as you like. The beast's own clock decides when it strikes back. */
    private fun punch(s: Side, t: Side) {
        if (over) return
        val foe = t.pet() ?: return
        armStrike(s)
        events += FightEvent.Move(s.index, true, Motion.ATTACK)
        pose(s, true, "attack")
        events += FightEvent.Sound("fx_hit")
        if (s.sword) { // Sword of the Spirit: the punch is a sword swing that slays it outright
            events += FightEvent.Fx(t.index, false, "sword_of_the_spirit")
            schedule(Pending(Step.SWORD_LAND, LAND, s.index, t.index, pet = t.active))
            return
        }
        schedule(Pending(Step.PUNCH_LAND, LAND, s.index, t.index, n = rng.nextInt(1, 5), pet = t.active))
        if (foe.hp <= 0) return
    }

    private fun punchLands(s: Side, t: Side, rolled: Int, foeIndex: Int, sword: Boolean) {
        if (over || t.active != foeIndex) return
        val foe = t.pet() ?: return
        events += FightEvent.Move(t.index, false, Motion.SHAKE)
        pose(t, false, "hit")
        lastHit = s.index
        if (sword) {
            events += FightEvent.Buzz(60, s.index)
            log("> ⚔️ You swing the Sword of the Spirit! It cuts clean through ${foe.name}!")
            foe.hp = 0
            if (t.isPlayer) petFalls(t, s, foe, chopper = false) else foeFainted(t, s)
            return
        }
        events += FightEvent.Buzz(30, s.index)
        var dmg = rolled
        if (s.amulet) { dmg = (foe.maxHp * 0.25).toInt().coerceAtLeast(100); log("> 🔮 POWER PUNCH! Your Titan Amulet surges and you hit for $dmg damage!") }
        else log("> 👊 $dmg damage.")
        if (foe.hasTrait("Enraged")) dmg = (dmg * 1.2).toInt()
        foe.hp -= dmg
        if (foe.hasTrait("Spiked") && dmg > 0) log("> 🌵 [Spiked] recoil! You cut your hand! (Ouch)")
        if (foe.hp <= 0) {
            log("> 👑 UNBELIEVABLE! YOU KILLED IT WITH YOUR BARE HANDS!")
            if (t.isPlayer) petFalls(t, s, foe, chopper = false) else foeFainted(t, s)
        }
    }

    // ------------------------------------------------------------------ the rules' turn

    /** [a] (run by the rules) answers [provoker] after [ticks]; a player answers for themselves. */
    private fun reply(a: Side, provoker: Side, ticks: Int) {
        if (!a.isPlayer) schedule(Pending(Step.COUNTER, ticks, a.index, provoker.index))
    }

    private fun counter(a: Side, provoker: Side) {
        if (a.isPlayer) return
        enemyTurn(a, provoker)
    }

    /** Who [a] goes for: whoever provoked it, or (with more than one player) maybe another with a netbeast out. */
    private fun pickTarget(a: Side, provoker: Side): Side {
        val candidates = sides.filter { it.isPlayer && it.inFight && it.pet() != null && !it.arriving }
        if (candidates.size <= 1) return candidates.firstOrNull() ?: provoker
        return if (provoker in candidates && rng.nextInt(100) < 50) provoker else rng.pick(candidates)
    }

    private fun enemyTurn(a: Side, provoker: Side) {
        if (over || !a.inFight) return
        val foe = a.pet() ?: return
        val target = pickTarget(a, provoker)
        val tp = target.pet()
        if (tp == null) {
            if (!target.lastStand) {
                enterLastStand(target, announce = false)
                log("\n> ⚠️ ALL NETBEASTS HAVE FALLEN!\n> There is no one left to protect you. It's your turn.", target.index)
            }
            return
        }

        if (a.poison > 0 && poisonTick(a, target)) return

        if (a.stunned) {
            a.stunned = false
            if (foe.hasTrait("Colossal")) log("> 🏔️[Colossal] Immunity! ${foe.name} shrugged off the Stun!")
            else { log("\n--- ENEMY TURN ---\n> ⚡ ${foe.name} is STUNNED and skips their turn!"); return }
        }

        log("\n--- ENEMY TURN ---")

        if (a.kind == SideKind.POACHER) {
            if (foe.hp < foe.maxHp * 0.3 && foe.eqPots > 0) {
                foe.eqPots--
                val heal = (foe.maxHp * 0.4).toInt(); foe.hp = (foe.hp + heal).coerceAtMost(foe.maxHp)
                log("> 🧪 Poacher used a Potion! ${foe.name} recovered $heal HP!")
                return
            }
            if (foe.hp < foe.maxHp * 0.2 && a.living().size > 1) {
                val next = a.living().firstOrNull { it != a.active && a.party[it].hp > 0 }
                if (next != null) {
                    log("> 🔄 Poacher withdrew ${foe.name} and sent out ${a.party[next].name}!")
                    a.active = next
                    sendOut(a, next)
                    return
                }
            }
        }

        val enemyMove = if (rng.nextBoolean()) foe.move1 else foe.move2
        var damage = if (a.kind == SideKind.INVADER) rng.nextInt(40, 80) else (foe.maxHp * 0.25).toInt() + rng.nextInt(-5, 5)
        if (foe.hasTrait("Enraged")) damage = (damage * 1.3).toInt()

        log("> ${foe.name} attempts [$enemyMove] on ${tp.name}...")
        events += FightEvent.Move(a.index, false, Motion.ATTACK)
        pose(a, false, "attack")
        events += FightEvent.Sound("spr_${foe.species.lowercase()}_attack")
        events += FightEvent.Sound("fx_${enemyMove.lowercase()}")
        events += FightEvent.Projectile(a.index, target.index, foe.type, tp.type, enemyMove)
        schedule(Pending(Step.COUNTER_LAND, LAND, a.index, target.index, enemyMove, damage, target.active))
    }

    private fun counterLands(a: Side, target: Side, enemyMove: String, rolled: Int, petIndex: Int) {
        if (over || !target.inFight || petIndex !in target.party.indices || petIndex in target.fallen) return
        val foe = a.pet() ?: return
        val tp = target.party[petIndex]
        var damage = rolled
        var evasionChance = if (target.evasion) 100 else 0
        if (tp.hasTrait("Swift")) evasionChance += 10
        if (tp.infusionStacks > 0 && tp.infusionEl == "Cloudy") evasionChance += (15 * tp.infusionStacks)

        val isPlayerFlying = tp.type == "Flying"
        if (isPlayerFlying && !SkillEngine.isAntiAir(enemyMove) && !SkillEngine.isAntiFlyingDouble(enemyMove)) evasionChance = maxOf(evasionChance, 90)
        if (isPlayerFlying && SkillEngine.isAntiFlyingDouble(enemyMove)) { damage *= 2; log("> 💥 CRITICAL WEAKNESS! ${tp.name} took double damage from $enemyMove!") }

        if (rng.nextInt(100) < evasionChance) {
            target.evasion = false
            if (evasionChance >= 100) log("> 💨 ${tp.name} used POTION EVASION and dodged completely!") else log("> ☁️ EVASION! ${tp.name} dodged the attack!")
            events += FightEvent.Move(target.index, false, Motion.EVADE)
        } else {
            attackFx(target, false, enemyMove) // it landed (a shield may still soak it)
            if (tp.hasTrait("Thick-Skinned")) { damage = (damage * 0.85).toInt(); log("> 🛡️ [Thick-Skinned] reduced damage!") }
            if (tp.infusionStacks > 0 && tp.infusionEl == "Snow") { damage = (damage * (1.0 / (tp.infusionStacks + 1))).toInt(); log("> ❄️ SNOW INFUSION! Damage heavily reduced!") }
            damage = absorb(target, damage)
            if (damage > 0) {
                tp.hp -= damage
                lastHit = a.index
                events += FightEvent.Move(target.index, false, Motion.SHAKE)
                pose(target, false, "hit")
                events += FightEvent.Buzz(150, target.index)
                log("> 🩸 Hit! ${tp.name} takes $damage damage.")
            }
            if (tp.hp <= 0) petFalls(target, a, tp, chopper = true)
        }
        changed(target)
        if (a.kind == SideKind.INVADER && !over && !target.lastStand && rng.nextBoolean() && foe.type != "EventBoss") {
            schedule(Pending(Step.FADE, 60, a.index, target.index))
        }
    }

    /** A shield soaks up [damage] first; returns what gets through. */
    private fun absorb(s: Side, damage: Int): Int {
        if (s.shield <= 0) return damage
        return if (damage <= s.shield) { s.shield -= damage; log("> 🛡️ SHIELD ABSORBED the attack! ($damage dmg)"); 0 }
        else { log("> 🛡️ SHIELD BROKEN! Absorbed ${s.shield} dmg."); val left = damage - s.shield; s.shield = 0; left }
    }

    /** Poison at the start of [s]'s turn. True if it finished [s]'s netbeast off. */
    private fun poisonTick(s: Side, other: Side): Boolean {
        val p = s.pet() ?: return false
        val pDmg = (p.maxHp * 0.05 * s.poison).toInt().coerceAtLeast(1)
        p.hp -= pDmg
        log("> ☠️ POISON DAMAGE! ${p.name} takes $pDmg damage.")
        if (p.hp > 0) return false
        if (s.isPlayer) petFalls(s, other, p, chopper = false) else foeFainted(s, other)
        return true
    }

    /**
     * A player's netbeast fell. Struck down by a beast the rules run ([chopper]), your strongest
     * is carried off by a poacher's chopper instead of dying. The next one goes in, or it's fists.
     */
    private fun petFalls(s: Side, by: Side, pet: Netbeast, chopper: Boolean) {
        val idx = s.party.indexOf(pet).takeIf { it >= 0 } ?: s.active
        events += FightEvent.Move(s.index, false, Motion.DEATH)
        pose(s, false, "faint", hold = true)
        events += FightEvent.Buzz(500, s.index)
        val maxPartyHp = livingParty(s).maxOfOrNull { it.maxHp } ?: 0
        val captured = chopper && !by.isPlayer && pet.maxHp >= maxPartyHp
        if (captured) log("\n> 🚁 A Poacher chopper swooped in!\n> ⚠️ ${pet.name} WAS CAPTURED AND TAKEN TO: ${s.location.uppercase()}!")
        else log("> 💀 ${pet.name} HAS BEEN KILLED!")
        s.fallen += idx
        s.participated -= idx
        events += FightEvent.Fell(s.index, idx, captured)
        changed(s)
        val next = s.available().firstOrNull()
        if (next == null) {
            enterLastStand(s, announce = false)
            log("\n> ⚠️ ALL NETBEASTS HAVE FALLEN!\n> ${by.pet()?.name ?: "It"} turns its gaze slowly toward YOU.")
        } else {
            refreshTurn(s)
            log("> You send out ${s.party[next].name} in desperation!")
            s.active = next
            sendOut(s, next)
        }
    }

    /** The foe [a]'s netbeast fainted, at [by]'s hand. */
    private fun foeFainted(a: Side, by: Side) {
        val foe = a.pet() ?: return
        events += FightEvent.Move(a.index, false, Motion.DEATH)
        pose(a, false, "faint", hold = true)
        events += FightEvent.Buzz(300)
        a.fallen += a.active

        if (setup.killingBlowCaptures && by.isPlayer && a.kind == SideKind.WILD) {
            log("> 🌟 ${foe.name} is beaten, and ${if (by.ownerId.isEmpty()) "you" else by.ownerId} catch${if (by.ownerId.isEmpty()) "" else "es"} it!")
            victoryPose(by)
            foe.hp = 1
            foe.isNew = true
            events += FightEvent.Caught(by.index, foe)
            end(FightOutcome.CAUGHT, by, a)
            return
        }

        when {
            a.kind == SideKind.POACHER -> {
                log("> Poacher's ${foe.name} fainted!")
                val next = a.living().firstOrNull()
                if (next == null) {
                    log("\n> 🦹 POACHER: 'Gah! Fine! Take your beast back!'")
                    val c = a.captive
                    if (c != null) {
                        val cleanName = if (c.name.contains("]")) c.name.substringAfter("]").trim() else c.name
                        c.name = "[Will to Live] $cleanName"; c.isNew = true
                        log("> 🎉 RESCUE SUCCESS! ${c.name} has rejoined your party, forever changed by its survival!")
                    }
                    victoryPose(by)
                    end(FightOutcome.WON, by, a)
                } else {
                    a.active = next
                    a.arriving = true
                    log("> Poacher sends out ${a.party[next].name}!")
                    schedule(Pending(Step.SEND_NEXT, NEXT_FOE, a.index, pet = next))
                }
            }
            foe.type == "EventBoss" -> {
                events += FightEvent.Loot(by.index, "amulets", 1)
                log("> 👑 TITAN SLAIN! You obtained a [Titan's Amulet]! Equip it from your bag.")
                victoryPose(by)
                end(FightOutcome.WON, by, a)
            }
            else -> {
                log("> 🏆 VICTORY! The wild ${foe.name} fainted!")
                if (foe.hasTrait("Ephemeral")) log("> 🌫️ [Ephemeral] entity vanquished. No XP could be extracted.")
                else if (by.isPlayer) levelUp(by)
                victoryPose(by)
                end(FightOutcome.WON, by, a)
            }
        }
    }

    private fun levelUp(s: Side) {
        for (idx in s.participated.toList()) {
            if (idx !in s.party.indices || idx in s.fallen) continue
            val p = s.party[idx]
            p.maxHp += 10
            p.hp = (p.hp + 10).coerceAtMost(p.maxHp)
            p.expedsDone++
            log("> 📈 LEVEL UP! ${p.name} grew to Lvl ${p.maxHp / 10}!")
            if (rng.nextInt(100) < 15) {
                val newTrait = rng.pick(MUTATIONS)
                if (!p.hasTrait(newTrait)) {
                    p.name = "[$newTrait] ${p.name}"
                    log("> ✨ MUTATION! ${p.name.split(" ").last()} acquired a mysterious new trait...")
                }
            }
            if (p.hasTrait("Looter")) {
                val loot = rng.nextInt(5, 15)
                events += FightEvent.Loot(s.index, "coins", loot)
                log("> 💰 [Looter] found $loot Coins!")
            }
            if (p.expedsDone >= 2) events += FightEvent.Evolve(s.index, idx)
        }
        changed(s)
    }

    private fun victoryPose(s: Side) {
        if (s.isPlayer && !s.lastStand && s.pet() != null) pose(s, false, "victory", hold = true)
    }

    // ------------------------------------------------------------------ last stands and clocks

    private fun enterLastStand(s: Side, announce: Boolean) {
        if (s.lastStand) return
        s.lastStand = true
        events += FightEvent.LastStand(s.index)
        if (wilds) {
            // back in its cage (or left where it fell), and the beast turns on you
            if (s.active >= 0) {
                s.active = -1; s.arriving = false
                schedule(Pending(Step.RECALL, NEXT_CAGE, s.index))
            }
            s.patience = LAST_STAND_PATIENCE
            s.busy = NEXT_CAGE // it turns on you once yours is gone
        } else {
            s.timer = -1
            armStrike(s)
        }
        if (announce) log(noCagesLine(s), s.index)
    }

    /** The battle screen, in a last stand: the beast comes for you on its own clock, however fast you punch. */
    private fun armStrike(s: Side) {
        if (wilds || s.strike >= 0 || over || !s.lastStand) return
        s.strike = LAST_STAND_PATIENCE
    }

    /** You did something (cancelBattleTimer): the battle screen's timer stops; the Wilds' patience starts over. */
    private fun acted(s: Side) {
        if (wilds) { if (!s.lastStand) { s.patience = PATIENCE; s.busy = BUSY } }
        else s.timer = -1
    }

    /** A new turn for [s] (updateBattleUI): the battle screen's timer starts; in a last stand, the beast's clock. */
    private fun refreshTurn(s: Side) {
        acted(s)
        if (wilds) return
        if (s.lastStand) armStrike(s)
        else if (!over && foeOf(s) != null) s.timer = TIMER
    }

    private fun clocks(s: Side) {
        if (over || !s.inFight) return
        val foe = foeOf(s) ?: return
        if (wilds) {
            if (s.busy > 0) { s.busy--; return }
            if (s.arriving || foe.arriving || foe.pet() == null) return
            if (--s.patience <= 0) { s.patience = PATIENCE; losesPatience(s, foe) }
            return
        }
        if (s.timer > 0 && --s.timer == 0) { s.timer = -1; playerMove(s, foe, "Basic Attack") }
        if (s.strike > 0 && --s.strike == 0) {
            s.strike = -1
            if (s.lastStand) obliterate(s, foe)
        }
    }

    /** The Wilds: you took too long, so the beast acts on its own. */
    private fun losesPatience(s: Side, a: Side) {
        val foe = a.pet() ?: return
        val label = foeLabel(a).replaceFirstChar { it.uppercase() }
        when {
            s.active < 0 && !s.lastStand && s.available().isNotEmpty() -> {
                // it lunges at you and knocks a cage loose: that netbeast has to fight, and takes the first hit
                val i = rng.pick(s.available())
                log("\n> ⏳ You hesitated! $label lunges at you!\n> ${s.party[i].name}'s cage is knocked from your hands and bursts open!")
                pose(a, false, "attack")
                events += FightEvent.Buzz(200, s.index)
                s.freeHit = true
                throwCage(s, i)
            }
            s.lastStand -> {
                log("\n--- ENEMY TURN ---\n> ⏳ You hesitated! $label lunges at YOU!")
                pose(a, false, "attack")
                schedule(Pending(Step.OBLITERATE_LAND, WILDS_OBLITERATE, a.index, s.index))
            }
            else -> {
                acted(s)
                log("\n> ⏳ You hesitated! $label strikes first!")
                if (foe.hp > 0) counter(a, s)
            }
        }
    }

    /** The battle screen: a last stand lost. It lunges, and the blow lands a beat later. */
    private fun obliterate(s: Side, a: Side) {
        events += FightEvent.Move(a.index, false, Motion.ATTACK)
        pose(a, false, "attack")
        schedule(Pending(Step.OBLITERATE_LAND, LAND, a.index, s.index))
    }

    private fun obliterateLands(a: Side, s: Side) {
        if (over || !s.lastStand || !s.inFight) return
        val foe = a.pet() ?: return
        if (!wilds) events += FightEvent.Move(s.index, true, Motion.DEATH)
        pose(s, true, "hit")
        events += FightEvent.Buzz(if (wilds) 800 else 1000, s.index)
        log(if (wilds) "> ${foe.name} obliterates you for 9,999 damage." else "\n--- ENEMY TURN ---\n> ${foe.name} obliterates you for 9,999 damage.")
        leave(s, FightOutcome.BEATEN)
    }

    // ------------------------------------------------------------------ the pending steps

    private fun run(p: Pending) {
        val s = sides[p.side]
        val t = sides.getOrNull(p.target)
        when (p.kind) {
            Step.COUNTER -> if (t != null && !over) counter(s, t)
            Step.MOVE_LAND -> if (t != null) moveLands(s, t, p.move, p.pet)
            Step.COUNTER_LAND -> if (t != null) counterLands(s, t, p.move, p.n, p.pet)
            Step.PUNCH_LAND -> if (t != null) punchLands(s, t, p.n, p.pet, sword = false)
            Step.SWORD_LAND -> if (t != null) punchLands(s, t, 0, p.pet, sword = true)
            Step.OBLITERATE -> if (t != null) obliterate(t, s)
            Step.OBLITERATE_LAND -> if (t != null) obliterateLands(s, t)
            Step.SEND_NEXT -> if (!over && s.active == p.pet) {
                if (!wilds) s.arriving = false
                sendOut(s, p.pet)
            }
            Step.THROW_NEXT -> if (!over && s.active == p.pet && p.pet !in s.fallen) events += FightEvent.SentOut(s.index, p.pet)
            Step.RECALL -> events += FightEvent.Recalled(s.index)
            Step.FREE_HIT -> if (t != null && !over && t.pet() != null) counter(s, t)
            Step.FADE -> if (!over) { log("\n> ${s.pet()?.name} fades into the shadows..."); end(FightOutcome.FOE_RAN, t ?: s, s) }
            Step.CLOSE -> close()
        }
    }

    /**
     * [s] sends netbeast [i] in (showBattleArena): the fight's statuses start over, and the wild
     * one may soak up the weather. On the battle screen it's out at once; in the Wilds a player's
     * cage flies after a beat, and a poacher's straight away.
     */
    private fun sendOut(s: Side, i: Int) {
        resetArena()
        if (!wilds) { events += FightEvent.SentOut(s.index, i); return }
        s.arriving = true
        if (s.isPlayer) schedule(Pending(Step.THROW_NEXT, NEXT_CAGE, s.index, pet = i))
        else events += FightEvent.SentOut(s.index, i)
    }

    /** Fresh statuses for everyone (CombatState.reset), and the foe may soak up the weather. */
    private fun resetArena() {
        for (s in sides) { s.shield = 0; s.poison = 0; s.stunned = false; s.terrified = false }
        val w = setup.weather
        for (a in sides) {
            if (a.isPlayer) continue
            val foe = a.pet() ?: continue
            if (foe.type != "Poacher" && foe.infusionEl == "None" && w != "Clear" && w != "Offline" && rng.nextInt(100) < 25) {
                foe.infusionEl = w; foe.infusionStacks = rng.nextInt(1, 4)
            }
        }
    }

    // ------------------------------------------------------------------ the end

    /** [winner] settled it against [foe]: the fight is over for everyone. */
    private fun end(outcome: FightOutcome, winner: Side, foe: Side) {
        if (over) return
        winner.outcome = outcome
        for (s in sides) if (s.inFight && s !== winner) s.outcome = if (s === foe || !s.isPlayer) loserOf(outcome) else FightOutcome.LEFT
        finish(foe)
    }

    /** [s] is out of it (beaten, or it abandoned a netbeast and fled); the rest fight on if any player is left. */
    private fun leave(s: Side, outcome: FightOutcome) {
        if (over) return
        s.outcome = outcome
        if (sides.any { it.isPlayer && it.inFight }) return
        for (o in sides) if (o.inFight) o.outcome = FightOutcome.WON
        finish(sides.firstOrNull { !it.isPlayer } ?: s)
    }

    private fun loserOf(outcome: FightOutcome) = when (outcome) {
        FightOutcome.WON -> FightOutcome.BEATEN
        FightOutcome.CAUGHT -> FightOutcome.CAUGHT
        FightOutcome.FOE_RAN -> FightOutcome.FOE_RAN
        else -> FightOutcome.LEFT
    }

    private fun finish(foe: Side) {
        over = true
        for (s in sides) { s.timer = -1; s.strike = -1 }
        pending.removeAll { it.kind != Step.RECALL }
        // endBattle: [Regenerative] heals once the fight is over, win or lose
        for (s in sides.filter { it.isPlayer }) {
            for (i in s.living()) {
                val p = s.party[i]
                if (p.hasTrait("Regenerative")) {
                    val healAmount = (p.maxHp * 0.20).toInt(); p.hp = (p.hp + healAmount).coerceAtMost(p.maxHp)
                    log("> 💚 ${p.name}'s Regenerative trait restored $healAmount HP!", s.index)
                }
            }
            changed(s)
        }
        val foePet = foe.active.takeIf { it in foe.party.indices }?.let { foe.party[it] }
        val rescued = if (foe.kind == SideKind.POACHER && foe.living().isEmpty()) foe.captive else null
        result = FightResult(sides.associate { it.index to (it.outcome ?: FightOutcome.LEFT) }, lastHit, foe.kind, foePet, rescued)
        events += FightEvent.Ended(result!!)
        schedule(Pending(Step.CLOSE, CLOSE_PAUSE, foe.index))
    }

    private fun close() {
        for (s in sides.filter { it.isPlayer }) {
            val gained = s.outcome == FightOutcome.CAUGHT || result?.rescued != null && s.outcome == FightOutcome.WON
            val left = s.living().size + if (gained) 1 else 0
            if (left == 0) log(if (wilds) "> You got clobbered, but you pick yourself up and walk on." else "> You got clobbered and returned to the Hub.", s.index)
            else log(if (wilds) "> The Wilds go quiet. You walk on." else "> Returning to Hub...", s.index)
        }
        closed = true
        events += FightEvent.Closed
    }

    // ------------------------------------------------------------------ bits

    /** A wild beast for [spec], about as strong as the first player's lead (WorldFight's old roll). */
    private fun rollWild(spec: WildSpec): Netbeast {
        val lead = sides.firstOrNull { it.isPlayer }?.let { p -> p.available().firstOrNull()?.let { p.party[it] } }
        val def = GameData.beasts.find { it.name == spec.species } ?: rng.pick(GameData.beasts)
        val refHp = lead?.maxHp ?: 20
        // Territory further from the start (higher evo stage) hits harder
        val pl = (refHp * (0.6 + 0.2 * spec.stage) * rng.nextDouble(0.85, 1.15)).toInt().coerceAtLeast(10)
        val name = if (rng.nextInt(100) < 10) "[Obscure] ${def.name}" else def.name
        return Netbeast(name, "Wild", pl, pl, def.m1, def.m2, "Tackle", 0L, 0, 0, 0, false, "None", 0, 0, 0, 0, "None", 0)
    }

    /** Who you're up against, for the log: "the wild Cacheon", "the poacher's Cacheon", or the boss by name. */
    fun foeLabel(a: Side): String {
        val e = a.pet() ?: return "the beast"
        return when {
            a.kind == SideKind.POACHER -> "the poacher's ${e.name}"
            e.type == "EventBoss" -> e.species
            else -> "the wild ${e.name}"
        }
    }

    private fun noCagesLine(s: Side) =
        if (s.party.isEmpty()) "> You have no netbeasts. Get your fists up!" else "> Your netbeasts are all off roaming. Get your fists up!"

    private fun livingParty(s: Side) = s.living().map { s.party[it] }

    private fun leadTrait(p: Netbeast) = if (p.name.contains("]")) p.name.substringBefore("]").substringAfter("[") else "Ordinary"

    private fun cleanName(p: Netbeast) = if (p.name.contains("]")) p.name.substringAfter("]").trim() else p.name

    private fun attackFx(s: Side, person: Boolean, move: String) {
        events += FightEvent.Fx(s.index, person, SkillEngine.fxFor(move)) // the host skips an fx it has no art for
    }

    private fun pose(s: Side, person: Boolean, anim: String, hold: Boolean = false) {
        events += FightEvent.Pose(s.index, person, anim, hold)
    }

    private fun log(text: String, side: Int = -1) { events += FightEvent.Log(text, side) }

    private fun changed(s: Side) {
        if (s.isPlayer && events.none { it is FightEvent.PartyChanged && it.side == s.index }) events += FightEvent.PartyChanged(s.index)
    }

    private fun schedule(p: Pending) { pending += p }

    private fun drain(): List<FightEvent> {
        if (events.isEmpty()) return emptyList()
        val out = events.toList()
        events.clear()
        return out
    }
}
