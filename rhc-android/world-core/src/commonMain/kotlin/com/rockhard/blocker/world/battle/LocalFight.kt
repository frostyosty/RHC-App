package com.rockhard.blocker.world.battle

/**
 * A fight on its own, outside the World (the old battle screen, FightStyle.SCREEN): it runs
 * right here at [Fight.TICK_HZ] from real elapsed time, like LocalWorldSession. Inputs wait
 * for the next tick, one a tick, in the order given.
 */
class LocalFight(val fight: Fight, private val side: Int = 0) {
    private val pending = ArrayDeque<FightInput>()
    private var accumulator = 0.0

    /** The opening: call once, before the first [update]. */
    fun start(): List<FightEvent> = fight.start()

    fun input(i: FightInput) { pending.addLast(i) }

    fun update(elapsedSeconds: Double): List<FightEvent> {
        accumulator += elapsedSeconds.coerceAtMost(0.25) // don't spiral after a pause
        val out = mutableListOf<FightEvent>()
        while (accumulator >= DT && !fight.closed) {
            accumulator -= DT
            val i = pending.removeFirstOrNull()
            out += fight.step(if (i != null) mapOf(side to i) else emptyMap())
        }
        return out
    }

    private companion object {
        const val DT = 1.0 / Fight.TICK_HZ
    }
}
