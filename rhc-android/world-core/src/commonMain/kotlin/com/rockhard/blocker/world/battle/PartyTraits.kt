package com.rockhard.blocker.world.battle

import com.rockhard.blocker.Netbeast

// Party traits aren't written into the name like [Swift]: they're worked out from the
// party each time they're needed, so they come and go as the party changes.
object PartyTraits {
    /** How many netbeasts in [party] share [pet]'s species (counting itself). */
    fun herdSize(party: List<Netbeast>, pet: Netbeast): Int = pet.species.let { s -> party.count { it.species == s } }

    /** [Herd]: 3+ of a species gives 50% extra double-strike chance, +5% per extra one. 0 without it. */
    fun herdChance(party: List<Netbeast>, pet: Netbeast): Int {
        val n = herdSize(party, pet)
        return if (n >= 3) (50 + 5 * (n - 3)).coerceAtMost(100) else 0
    }

    /** [Hoard]: with more than 5 netbeasts, swapping costs no turn. */
    fun hasHoard(party: List<Netbeast>): Boolean = party.size > 5

    /** The party traits [pet] has right now, for display. */
    fun of(party: List<Netbeast>, pet: Netbeast): List<String> =
        listOfNotNull(if (herdChance(party, pet) > 0) "Herd" else null, if (hasHoard(party)) "Hoard" else null)
}
