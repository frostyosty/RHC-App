package com.rockhard.blocker.homevisits

data class HomeClock(val day: Int, val minute: Int)

class Rng(seed: Long) {
    private val random = kotlin.random.Random(seed)
    fun stream(name: String, modifier: Int): kotlin.random.Random {
        return kotlin.random.Random(random.nextLong() xor name.hashCode().toLong() xor modifier.toLong())
    }
}
