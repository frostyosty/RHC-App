package com.rockhard.blocker

data class Netbeast(
    var name: String, val type: String, var hp: Int, var maxHp: Int, 
    var move1: String, var move2: String, var move3: String, val boughtAt: Long, 
    var eqNets: Int, var eqPots: Int, var eqSprays: Int, 
    var isNew: Boolean, var infusionEl: String, var infusionStacks: Int, var listedPrice: Int = 0,
    var expedsDone: Int = 0, var amulets: Int = 0,
    var lastMove: String = "None", var moveStreak: Int = 0
) {
    fun isGrouchy(): Boolean = (System.currentTimeMillis() - boughtAt) < (24 * 60 * 60 * 1000) && boughtAt > 0
}

object GameData {
    val bossNames = listOf("Pronosaur", "Gorella", "Fleshwire", "Lustrot", "The Dark One")

    data class BeastDef(val name: String, val type: String, val baseHp: Int, val m1: String, val m2: String, val category: String, val evoStage: Int)
    data class TraitDef(val name: String, val rarityWeight: Int, val trigger: String, val hpMult: Double, val desc: String)

    val traits = listOf(
        TraitDef("Ordinary", 100, "None", 1.0, "Standard stats."),
        TraitDef("Frail", 40, "None", 0.8, "Reduced maximum HP."),
        TraitDef("Strong", 30, "None", 1.2, "Increased maximum HP."),
        TraitDef("Savage", 10, "None", 1.5, "Massively increased HP."),
        TraitDef("Battle-Hardened", 5, "None", 1.6, "Extremely resilient. High HP."),
        TraitDef("Veteran", 2, "None", 1.8, "A legendary survivor. Incredible HP."),
        TraitDef("Slick", 20, "Rain", 1.15, "Thrives in the rain."),
        TraitDef("Radiant", 20, "Clear", 1.25, "Absorbs solar energy."),
        TraitDef("Frostbitten", 20, "Snow", 1.1, "Numb to the cold."),
        TraitDef("Charged", 15, "Storm", 1.4, "Crackling with storm energy."),
        TraitDef("Aggressive", 25, "Gaming", 1.3, "Hyper-competitive. High HP."),
        TraitDef("Skittish", 25, "Social", 0.85, "Easily distracted. Lower HP."),
        TraitDef("Glitchy", 15, "Tech", 1.35, "Anomalous health readings."),
        TraitDef("Lethargic", 30, "Streaming", 0.9, "Slow to react. Lower HP."),
        TraitDef("Regenerative", 10, "None", 1.0, "Heals 20% of Max HP after surviving a battle."),
        TraitDef("Persistence", 10, "Repetition", 1.0, "+15% Damage per consecutive repeated attack."),
        TraitDef("Dynamism", 10, "Variation", 1.0, "+15% Damage per consecutive alternating attack."),
        TraitDef("Will to Live", 0, "Rescue", 1.0, "Heals 15% of Max HP after returning from exploration."),
        TraitDef("Vampiric", 10, "Combat Mutation", 1.0, "Heals 15% of damage dealt."),
        TraitDef("Thick-Skinned", 10, "Combat Mutation", 1.2, "Takes 15% less damage."),
        TraitDef("Frenzy", 10, "Combat Mutation", 0.9, "+15% Critical Hit Chance."),
        TraitDef("Swift", 15, "Combat Mutation", 0.8, "+10% Evasion."),
        TraitDef("Looter", 15, "Combat Mutation", 1.0, "Finds coins after victories.")
    )

    val beasts = listOf(
        BeastDef("Bytelet", "Tech", 50, "Ping", "Dodge", "Tech", 1),
        BeastDef("Cacheon", "Tech", 120, "Digital Swipe", "Overclock", "Tech", 2),
        BeastDef("Technophasia", "Tech", 220, "System Wipe", "Firewall", "Tech", 3),
        BeastDef("Chirplet", "Social", 40, "Tweet", "Flee", "Social", 1),
        BeastDef("Viralia", "Social", 110, "Viral Surge", "Cancel", "Social", 2),
        BeastDef("Trendrake", "Social", 200, "Ratio", "Doxx", "Social", 3),
        BeastDef("Noobit", "Gaming", 60, "Spam Click", "Cry", "Gaming", 1),
        BeastDef("Skirmalot", "Gaming", 130, "XP Boost", "Critical Strike", "Gaming", 2),
        BeastDef("Grindlord", "Gaming", 250, "Rage Quit", "G-Fuel", "Gaming", 3),
        BeastDef("Bufferoo", "Streaming", 50, "Lag", "Skip", "Streaming", 1),
        BeastDef("Streamlet", "Streaming", 110, "Binge", "Autoplay", "Streaming", 2),
        BeastDef("Bingewyrm", "Streaming", 210, "Marathon", "Hypnotize", "Streaming", 3),
        BeastDef("Zephyrlet", "Flying", 35, "Tackle", "Aero Beam", "Flying", 1),
        BeastDef("Airstream", "Flying", 75, "Swipe", "Chrono Blast", "Flying", 2),
        BeastDef("Stratolord", "Flying", 130, "Bite", "Sky-Breaker", "Flying", 3),
        BeastDef("Cartini", "Shopping", 45, "Browse", "Wishlist", "Shopping", 1)
    )
}
