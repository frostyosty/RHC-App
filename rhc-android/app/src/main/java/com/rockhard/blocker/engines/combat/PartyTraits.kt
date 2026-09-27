package com.rockhard.blocker

// Party traits aren't written into the name like [Swift]: they're worked out from the
// party each time they're needed, so they come and go as the party changes.

/** How many netbeasts in the party share [pet]'s species (counting itself). */
internal fun GameActivity.herdSize(pet: Netbeast): Int = speciesOf(pet).let { s -> party.count { speciesOf(it) == s } }

/** [Herd]: 3+ of a species gives 50% extra double-strike chance, +5% per extra one. 0 without it. */
internal fun GameActivity.herdChance(pet: Netbeast): Int {
    val n = herdSize(pet)
    return if (n >= 3) (50 + 5 * (n - 3)).coerceAtMost(100) else 0
}

/** [Hoard]: with more than 5 netbeasts, swapping costs no turn. */
internal fun GameActivity.hasHoard(): Boolean = party.size > 5

/** The party traits [pet] has right now, for display. */
internal fun GameActivity.partyTraits(pet: Netbeast): List<String> =
    listOfNotNull(if (herdChance(pet) > 0) "Herd" else null, if (hasHoard()) "Hoard" else null)
