package com.rockhard.blocker.homevisits

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class BasicTest {
    @Test
    fun testClock() {
        val clock = HomeClock(1, 120)
        assertEquals(1, clock.day)
        assertEquals(120, clock.minute)
    }

    private fun rolls(r: kotlin.random.Random) = List(5) { r.nextInt() }

    /** A stream rolls the same whatever other streams were asked for first. */
    @Test
    fun streamsDontShiftEachOther() {
        val alone = rolls(Rng(42).stream("visits", 3))
        val busy = Rng(42).apply { rolls(stream("shop", 3)); rolls(stream("visits", 2)) }
        assertEquals(alone, rolls(busy.stream("visits", 3)))
    }

    @Test
    fun streamsDifferByNameNumberAndSeed() {
        val base = rolls(Rng(42).stream("visits", 3))
        assertNotEquals(base, rolls(Rng(42).stream("shop", 3)))
        assertNotEquals(base, rolls(Rng(42).stream("visits", 4)))
        assertNotEquals(base, rolls(Rng(43).stream("visits", 3)))
    }
}
