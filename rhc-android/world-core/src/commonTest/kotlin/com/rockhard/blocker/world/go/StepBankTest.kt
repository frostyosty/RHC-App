package com.rockhard.blocker.world.go

import kotlin.test.Test
import kotlin.test.assertEquals

class StepBankTest {
    private val day = 20_377

    @Test
    fun theFirstReadingOnlySetsWhereCountingStarts() {
        val bank = StepBank()
        assertEquals(0, bank.onReading(48_000, day)) // steps since the phone started up aren't ours
        assertEquals(StepPayout(0, 0), bank.payOut())
    }

    @Test
    fun aNormalDay() {
        val bank = StepBank()
        bank.onReading(1_000, day)
        bank.onReading(1_900, day)
        bank.onReading(3_340, day)
        assertEquals(2_340, bank.stepsToday)
        assertEquals(StepPayout(2_340, 11), bank.payOut())
        assertEquals(140, bank.unpaid) // the remainder stays banked...
        bank.onReading(3_400, day)
        assertEquals(StepPayout(60, 1), bank.payOut()) // ...and pays out with the next 60
    }

    @Test
    fun aRestartInTheMiddle() {
        val bank = StepBank(last = 5_000, day = day)
        bank.onReading(6_000, day)
        bank.onReading(300, day) // the counter starts again from 0 after a restart
        bank.onReading(500, day)
        assertEquals(1_500, bank.stepsToday)
        assertEquals(StepPayout(1_500, 7), bank.payOut())
    }

    @Test
    fun theDailyCap() {
        val bank = StepBank(last = 0, day = day)
        bank.onReading(10_000, day)
        assertEquals(5_000, bank.onReading(40_000, day)) // only up to the cap
        assertEquals(0, bank.onReading(41_000, day))
        assertEquals(StepBank.DAILY_CAP, bank.stepsToday)
        assertEquals(StepPayout(15_000, 75), bank.payOut())
        // A new day counts again
        assertEquals(800, bank.onReading(41_800, day + 1))
        assertEquals(800, bank.stepsToday)
    }

    @Test
    fun twoPayOutsInARowPayOnce() {
        val bank = StepBank(last = 0, day = day)
        bank.onReading(1_000, day)
        assertEquals(StepPayout(1_000, 5), bank.payOut())
        assertEquals(StepPayout(0, 0), bank.payOut())
    }

    @Test
    fun itCarriesOnFromItsSavedNumbers() {
        val bank = StepBank(last = 1_000, day = day)
        bank.onReading(2_250, day)
        val saved = StepBank(bank.last, bank.day, bank.stepsToday, bank.unpaid, bank.walked)
        saved.onReading(2_350, day)
        assertEquals(StepPayout(1_350, 6), saved.payOut())
        assertEquals(150, saved.unpaid)
    }

    @Test
    fun healingIsSlowerForFlyersAndHigherLevels() {
        assertEquals(3_200, StepHealing.stepsToHeal(6, flying = false))
        assertEquals(9_200, StepHealing.stepsToHeal(13, flying = true))
        // 1,600 steps: half of a level 6 beast, less for a level 25 one, less again for a flyer of the same size
        assertEquals(30, StepHealing.heal(1_600, hp = 0, maxHp = 60, flying = false))
        assertEquals(57, StepHealing.heal(1_600, hp = 0, maxHp = 250, flying = false))
        assertEquals(28, StepHealing.heal(1_600, hp = 0, maxHp = 250, flying = true))
    }

    @Test
    fun healingStopsAtFull() {
        assertEquals(10, StepHealing.heal(15_000, hp = 50, maxHp = 60, flying = false))
        assertEquals(0, StepHealing.heal(15_000, hp = 60, maxHp = 60, flying = true))
        assertEquals(0, StepHealing.heal(0, hp = 10, maxHp = 60, flying = false))
    }
}
