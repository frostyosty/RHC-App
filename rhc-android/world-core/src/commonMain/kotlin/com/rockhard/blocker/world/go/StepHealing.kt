package com.rockhard.blocker.world.go

/**
 * Walking heals your netbeasts (Netbeasts Go 02, the user's answer of 2026-10-16: "flying ones
 * can be slow to heal, higher levels heal slower too").
 *
 * Each beast heals in proportion to the steps: [stepsToHeal] steps take it from nothing to full.
 * That's [BASE_STEPS] plus [STEPS_PER_LEVEL] for each level, doubled for a flying beast. So a
 * level 6 Noobit needs 3,200 steps, a level 3 Zephyrlet 2 × 2,600 = 5,200, a level 25 Grindlord 7,000, and a flying level
 * 13 Stratolord 9,200. A day's capped steps (15,000) heal anything fully; a walk round the block
 * (about 1,500) heals a low-level beast by about half and a big flyer by a sixth.
 *
 * Level is the app's `maxHp / 10`.
 */
object StepHealing {
    const val BASE_STEPS = 2_000
    const val STEPS_PER_LEVEL = 200
    const val FLYING_FACTOR = 2

    /** Steps that heal a beast of [level] from 0 to full. */
    fun stepsToHeal(level: Int, flying: Boolean): Int =
        (BASE_STEPS + STEPS_PER_LEVEL * level.coerceAtLeast(0)) * (if (flying) FLYING_FACTOR else 1)

    /** HP that [steps] give back to a beast at [hp] of [maxHp], never past full. */
    fun heal(steps: Int, hp: Int, maxHp: Int, flying: Boolean): Int {
        if (steps <= 0 || maxHp <= 0 || hp >= maxHp) return 0
        val gain = steps.toLong() * maxHp / stepsToHeal(maxHp / 10, flying)
        return minOf(gain, (maxHp - hp.coerceAtLeast(0)).toLong()).toInt()
    }
}
