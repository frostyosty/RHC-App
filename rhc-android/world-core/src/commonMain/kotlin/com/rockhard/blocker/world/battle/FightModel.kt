package com.rockhard.blocker.world.battle

import com.rockhard.blocker.Netbeast

// What goes into a fight and what comes out of it: the setup the host gives
// at its start, a player's input each tick, the events the rules emit for the
// screen, and the snapshot a server sends. See Fight.kt for the rules.

/** Who a side is. Everyone but PLAYER is run by the rules. INVADER is the red-wall Defend boss. */
enum class SideKind { PLAYER, WILD, BOSS, POACHER, INVADER }

/**
 * How the fight is shown, which changes its pacing. WILDS: in the 3D world, netbeasts come out of
 * thrown cages and the beast's patience runs out if you wait. SCREEN: the old battle screen, the
 * netbeast is out at once and a timer shouts a Basic Attack for you.
 */
enum class FightStyle { WILDS, SCREEN }

enum class FightItem { NET, POTION, SPRAY }

/** How the fight ended for one side. LEFT: it ended without you (someone else won it). */
enum class FightOutcome { WON, CAUGHT, FOE_RAN, ABANDONED, BEATEN, LEFT }

/** A move on the battle screen's sprites (AnimUtils). */
enum class Motion { ATTACK, SHAKE, EVADE, DEATH }

/** The whole fight's setting: the same for every side. [nowMs] is the host's clock at the start (for [Grouchy]). */
data class FightSetup(
    val style: FightStyle,
    val weather: String = "Clear",
    val nowMs: Long = 0L,
    /** The town you're defending, for the boss's line. */
    val place: String = "",
    /** Multiplayer: a player's killing blow on a wild beast captures it instead (agreed 2026-11-12, multiplayer only). */
    val killingBlowCaptures: Boolean = false,
)

/** A wild beast the rules roll at the start, from the player's lead ([World] fights). */
data class WildSpec(val species: String, val stage: Int)

/**
 * One side as the host gives it. A player's [party] is all their netbeasts in the order their
 * cages are offered (walk order in the Wilds, party order on the battle screen); [benched] are the
 * ones that can't fight (out roaming). A foe's [party] is its fighters, the first out first.
 */
data class SideSetup(
    val kind: SideKind,
    val party: List<Netbeast> = emptyList(),
    val benched: Set<Int> = emptySet(),
    /** SCREEN: who's out at the start (-1: nobody, so a last stand). WILDS players start with nobody out. */
    val active: Int = 0,
    /** The World entity: the player, or the beast or rival they fight. */
    val entity: Int = -1,
    val ownerId: String = "",
    // a player's bag and relics
    val bagNets: Int = 0,
    val difficulty: Int = 50,
    val sword: Boolean = false,
    val amulet: Boolean = false,
    val tripleStrike: Boolean = false,
    val evasion: Boolean = false,
    /** Where a poacher's chopper takes a netbeast of yours, for the log. */
    val location: String = "",
    // a foe's
    /** The event boss's weakness: a netbeast whose name contains it does massive damage. */
    val weakness: String = "",
    /** A poacher's prisoner: beat him and it's yours again. */
    val captive: Netbeast? = null,
    /** Roll a wild beast at the start instead of [party]. */
    val wild: WildSpec? = null,
)

/**
 * A player's intent for one tick, applied on the next tick like everything else. [move] is 1-3
 * (with no netbeasts left, any of them is a punch); [cage] throws or swaps to that netbeast (an
 * index into your side's party); [target] is the side to aim at (-1: the first foe).
 */
data class FightInput(
    val move: Int = 0,
    val cage: Int = -1,
    val item: FightItem? = null,
    val abandon: Boolean = false,
    val target: Int = -1,
)

sealed class FightEvent {
    /** A line for the log (or the captions over the Wilds), emoji and all. [side] -1 is everyone. */
    data class Log(val text: String, val side: Int = -1) : FightEvent()
    /** A pose on a fighter: [person] is the player (in a last stand), else the side's netbeast. [hold] keeps it (faint, victory). */
    data class Pose(val side: Int, val person: Boolean, val anim: String, val hold: Boolean = false) : FightEvent()
    data class Move(val side: Int, val person: Boolean, val motion: Motion) : FightEvent()
    /** fx_<fx> over a fighter. */
    data class Fx(val side: Int, val person: Boolean, val fx: String) : FightEvent()
    data class Projectile(val from: Int, val to: Int, val fromType: String, val toType: String, val move: String) : FightEvent()
    data class Sound(val key: String) : FightEvent()
    data class Buzz(val ms: Long, val side: Int = -1) : FightEvent()
    /** [side] sends netbeast [index] in (a cage flies, in the Wilds). */
    data class SentOut(val side: Int, val index: Int) : FightEvent()
    /** [side]'s netbeast went back in its cage (or lies where it fell). */
    data class Recalled(val side: Int) : FightEvent()
    /** [side]'s netbeast [index] fell: killed, or [captured] by a poacher's chopper (to [Side.location]). */
    data class Fell(val side: Int, val index: Int, val captured: Boolean) : FightEvent()
    data class Abandoned(val side: Int, val index: Int) : FightEvent()
    /** [side] has no netbeasts left in the fight: it's fists now. */
    data class LastStand(val side: Int) : FightEvent()
    /** Something for [side]'s bag: "coins", "nets", "potions", "sprays", "aether" (seconds) or "amulets". */
    data class Loot(val side: Int, val item: String, val amount: Int) : FightEvent()
    /** [side] caught [beast] (a net, or a killing blow in multiplayer). */
    data class Caught(val side: Int, val beast: Netbeast) : FightEvent()
    /** [side]'s netbeast [index] has fought enough to evolve (the host evolves it). */
    data class Evolve(val side: Int, val index: Int) : FightEvent()
    /** [side]'s netbeasts changed (health, items, names): save them. */
    data class PartyChanged(val side: Int) : FightEvent()
    /** It's decided. The save is written from this; the fight lingers until [Closed]. */
    data class Ended(val result: FightResult) : FightEvent()
    /** Two seconds later: the fight is over and gone (in the Wilds, the walk goes on). */
    data object Closed : FightEvent()
}

/**
 * What the fight hands back. [outcomes] by side index; [foe] is the beaten or caught foe (the
 * graveyard, the catch); [rescued] a poacher's prisoner won back (already renamed).
 */
data class FightResult(
    val outcomes: Map<Int, FightOutcome>,
    val lastHit: Int,
    val foeKind: SideKind,
    val foe: Netbeast?,
    val rescued: Netbeast? = null,
)
