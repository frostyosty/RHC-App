package com.rockhard.blocker.homevisits

/** The day number (days since a fixed date, local time) and the minute of that day. The app passes it in; the core never reads a clock. */
data class HomeClock(val day: Int, val minute: Int)

/**
 * Named random streams: `rng.stream("visits", day)`. Each stream comes only from the seed, its
 * name and its number, never from which streams were asked for before it, so adding randomness to
 * one system doesn't shift another system's rolls.
 */
class Rng(private val seed: Long) {
    fun stream(name: String, n: Int): kotlin.random.Random {
        var h = -0x340d631b7bdddcdbL // FNV-1a over the name's chars, so it doesn't lean on String.hashCode
        for (c in name) h = (h xor c.code.toLong()) * 0x100000001b3L
        return kotlin.random.Random(mix(seed xor mix(h xor (n.toLong() shl 32))))
    }

    private fun mix(v: Long): Long { // splitmix64's finaliser
        var z = v
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }
}
