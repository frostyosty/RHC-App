package com.rockhard.blocker

// A netbeast: yours, a wild one, a poacher's or a boss. Shared by the phone, the
// web client and the server (pure data: no Android, no clock). Moved here from
// app's GameModels.kt in NETBEASTS_WEB_PLAN step 03 so the fight rules
// (world/battle/) run the same everywhere. Traits live in the name
// ("[Vampiric] Cacheon"), as they always have.
data class Netbeast(
    var name: String, val type: String, var hp: Int, var maxHp: Int,
    var move1: String, var move2: String, var move3: String, val boughtAt: Long,
    var eqNets: Int, var eqPots: Int, var eqSprays: Int,
    var isNew: Boolean, var infusionEl: String, var infusionStacks: Int, var listedPrice: Int = 0,
    var expedsDone: Int = 0, var amulets: Int = 0,
    var lastMove: String = "None", var moveStreak: Int = 0,
) {
    /** Bought less than a day before [nowMs] (wall-clock ms, given by the host): it may ignore you. */
    fun isGrouchy(nowMs: Long): Boolean = (nowMs - boughtAt) < (24 * 60 * 60 * 1000) && boughtAt > 0

    /** The species, with the trait tags stripped: also its sprite row. */
    val species: String get() = name.replace(TAGS, "").trim()

    fun hasTrait(trait: String) = name.contains("[$trait]")

    private companion object {
        val TAGS = Regex("\\[.*?\\]")
    }
}
