package com.rockhard.blocker.world.go

/**
 * Steps walked with the phone in your pocket, banked until the game next opens (Netbeasts Go 02).
 *
 * It's given the phone's step counter, which counts from when the phone last started up, and a
 * day number (any whole number that goes up by one each day, e.g. the epoch day). It keeps:
 * - [last]: the last counter reading, or -1 before the first one;
 * - [day] and [stepsToday]: today's counted steps, which stop at [DAILY_CAP];
 * - [unpaid]: steps not yet turned into coins, less than [STEPS_PER_COIN] after a pay-out;
 * - [walked]: steps counted since the last pay-out, for the message and for healing.
 *
 * Pure and whole-number only, so the app saves the five numbers and makes it again each time.
 */
class StepBank(
    last: Int = -1,
    day: Int = 0,
    stepsToday: Int = 0,
    unpaid: Int = 0,
    walked: Int = 0,
) {
    var last = last; private set
    var day = day; private set
    var stepsToday = stepsToday; private set
    var unpaid = unpaid; private set
    var walked = walked; private set

    /** Takes a counter reading on [today]. Returns the steps it added to the bank. */
    fun onReading(counter: Int, today: Int): Int {
        if (counter < 0) return 0
        // The first reading only sets where counting starts: the steps since the phone started up aren't ours
        val new = when {
            last < 0 -> 0
            counter < last -> counter // the phone restarted, and the counter with it
            else -> counter - last
        }
        last = counter
        if (today != day) { day = today; stepsToday = 0 }
        val counted = minOf(new, DAILY_CAP - stepsToday).coerceAtLeast(0)
        stepsToday += counted
        unpaid += counted
        walked += counted
        return counted
    }

    /** Turns unpaid steps into coins, leaving the remainder banked, and starts [walked] again. */
    fun payOut(): StepPayout {
        val payout = StepPayout(walked, unpaid / STEPS_PER_COIN)
        unpaid %= STEPS_PER_COIN
        walked = 0
        return payout
    }

    companion object {
        const val STEPS_PER_COIN = 200    // agreed with the user 2026-10-16
        const val DAILY_CAP = 15_000      // steps counted a day (75 coins); shaking the phone isn't worth it
    }
}

/** What a pay-out gave: the steps walked since the last one, and the coins they (and any banked remainder) made. */
data class StepPayout(val steps: Int, val coins: Int)
