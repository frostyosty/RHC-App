package com.rockhard.blocker.world.battle

/**
 * The fight's own dice: SplitMix64 over one Long, so its whole state goes in a
 * snapshot (kotlin.random.Random's doesn't) and it rolls the same on the JVM and
 * in wasm (Long arithmetic only; doubles are built from 53 exact bits).
 */
class FightRng(var state: Long) {
    fun nextLong(): Long {
        state += -0x61c8864680b583ebL // 0x9E3779B97F4A7C15
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L // 0xBF58476D1CE4E5B9
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L // 0x94D049BB133111EB
        return z xor (z ushr 31)
    }

    /** 0 until [bound]. */
    fun nextInt(bound: Int): Int {
        require(bound > 0)
        return ((nextLong() ushr 1) % bound).toInt()
    }

    /** [from] until [until], like kotlin.random's. */
    fun nextInt(from: Int, until: Int): Int = from + nextInt(until - from)

    fun nextBoolean(): Boolean = nextLong() < 0

    fun nextDouble(): Double = (nextLong() ushr 11).toDouble() * UNIT

    fun nextDouble(from: Double, until: Double): Double = from + (until - from) * nextDouble()

    fun <T> pick(list: List<T>): T = list[nextInt(list.size)]

    private companion object {
        const val UNIT = 1.0 / (1L shl 53)
    }
}
