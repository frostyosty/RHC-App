package com.rockhard.blocker.homevisits

import kotlin.test.Test
import kotlin.test.assertEquals

class BasicTest {
    @Test
    fun testClock() {
        val clock = HomeClock(1, 120)
        assertEquals(1, clock.day)
        assertEquals(120, clock.minute)
    }
}
