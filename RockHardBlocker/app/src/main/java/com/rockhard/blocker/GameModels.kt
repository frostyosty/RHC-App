package com.rockhard.blocker

data class Netbeast(
    val name: String, val type: String, var hp: Int, val maxHp: Int, 
    var move1: String, var move2: String, val boughtAt: Long, 
    var eqNets: Int, var eqPots: Int, var eqSprays: Int, 
    var isNew: Boolean, var infusionEl: String, var infusionStacks: Int, var listedPrice: Int = 0
) {
    fun isGrouchy(): Boolean = (System.currentTimeMillis() - boughtAt) < (24 * 60 * 60 * 1000) && boughtAt > 0
}

object GameData {
    val bossNames = listOf("Pornosaur", "GoreIlla", "Fleshwire", "Lustrot", "The Dark One")
    data class BeastDef(val name: String, val type: String, val baseHp: Int, val m1: String, val m2: String, val category: String, val evoStage: Int)
    data class TraitDef(val name: String, val rarityWeight: Int, val trigger: String, val hpMult: Double)

    val traits = listOf(
        TraitDef("Ordinary", 100, "None", 1.0), TraitDef("Frail", 40, "None", 0.8),
        TraitDef("Strong", 30, "None", 1.2), TraitDef("Savage", 10, "None", 1.5),
        TraitDef("Slick", 20, "Rain", 1.15), TraitDef("Radiant", 20, "Clear", 1.25),
        TraitDef("Frostbitten", 20, "Snow", 1.1), TraitDef("Charged", 15, "Storm", 1.4),
        TraitDef("Aggressive", 25, "Gaming", 1.3), TraitDef("Skittish", 25, "Social", 0.85),
        TraitDef("Glitchy", 15, "Tech", 1.35), TraitDef("Lethargic", 30, "Streaming", 0.9)
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
        BeastDef("Roamlet", "Travel", 55, "Wander", "Pack", "Travel", 1),
        BeastDef("Cartini", "Shopping", 45, "Browse", "Wishlist", "Shopping", 1)
    )
}
