package com.rockhard.blocker
import kotlin.random.Random

object SkillEngine {
    // --- THE MASTER SKILL DATABASE ---
    // You can easily change the effects, chances, and types here without hunting for code!
    data class SkillDef(
        val name: String,
        val effect: String = "None",
        val effectChance: Int = 0,
        val isAntiAir: Boolean = false,
        val isDoubleAir: Boolean = false,
        val type: String = "Standard",
    )

    val SKILL_DATABASE =
        mapOf(
            "Ping" to SkillDef("Ping", "DoubleStrike", 15, isAntiAir = true),
            "Glitch" to SkillDef("Glitch", "Stun", 15),
            "Data Drain" to SkillDef("Data Drain", "Lifesteal", 20),
            "Overclock" to SkillDef("Overclock", "Shield", 20),
            "Static" to SkillDef("Static", "Poison", 25, isDoubleAir = true),
            "Timeshift" to SkillDef("Timeshift", "None", 0, type = "Timeshift"),
            "Tweet" to SkillDef("Tweet", "DoubleStrike", 15, isAntiAir = true),
            "Cancel" to SkillDef("Cancel", "Stun", 15),
            "Doxx" to SkillDef("Doxx", "StealCoins", 25),
            "Doomscroll" to SkillDef("Doomscroll", "StealSpray", 10),
            "Ratio" to SkillDef("Ratio", "Poison", 25),
            "Annihilate" to SkillDef("Annihilate", type = "Ultimate"),
            "Cleanse" to SkillDef("Cleanse", "PurgeEnemy", 100, type = "Utility"),
            "Basic Attack" to SkillDef("Basic Attack", "PercentCurrentHP", 100, type = "Basic"),
            "Spam Click" to SkillDef("Spam Click", "DoubleStrike", 15, isAntiAir = true),
            "Rage Quit" to SkillDef("Rage Quit", "Stun", 15),
            "G-Fuel" to SkillDef("G-Fuel", "Poison", 25),
            "XP Boost" to SkillDef("XP Boost", "Shield", 20),
            "Loot Box" to SkillDef("Loot Box", "StealNet", 10),
            "Lag" to SkillDef("Lag", "DoubleStrike", 15, isAntiAir = true),
            "Skip" to SkillDef("Skip", "Terrify", 10),
            "Binge" to SkillDef("Binge", "Lifesteal", 20),
            "Autoplay" to SkillDef("Autoplay", "Shield", 20),
            "Ad Break" to SkillDef("Ad Break", "StealCoins", 25),
            "Cyber Strike" to SkillDef("Cyber Strike", "DoubleStrike", 15, isAntiAir = true),
            "Aero Beam" to SkillDef("Aero Beam", "Terrify", 10),
            "Mecha Dash" to SkillDef("Mecha Dash", "Lifesteal", 20),
            "Pixel Slash" to SkillDef("Pixel Slash", "Terrify", 10),
            "Light Pulse" to SkillDef("Light Pulse", "StealPotion", 10, isAntiAir = true),
            "Tackle" to SkillDef("Tackle", "DoubleStrike", 15, isAntiAir = true),
            "Bite" to SkillDef("Bite", "Lifesteal", 20, isAntiAir = true),
            "Scratch" to SkillDef("Scratch", "StealCoins", 25, isAntiAir = true),
            "Growl" to SkillDef("Growl", "Terrify", 10),
            "Swipe" to SkillDef("Swipe", "Lifesteal", 20, isAntiAir = true),
            "System Wipe" to SkillDef("System Wipe", "Stun", 15),
            "Firewall" to SkillDef("Firewall", "Shield", 20),
            "Viral Surge" to SkillDef("Viral Surge", "Poison", 25),
            "Deplatform" to SkillDef("Deplatform", "Stun", 15),
            "Critical Strike" to SkillDef("Critical Strike", "Terrify", 10),
            "Parry" to SkillDef("Parry", "Shield", 20),
            "Marathon" to SkillDef("Marathon", "Terrify", 10),
            "Hypnotize" to SkillDef("Hypnotize", "Terrify", 10),
            "Chrono Blast" to SkillDef("Chrono Blast", "Terrify", 10),
            "Nova Shield" to SkillDef("Nova Shield", "Shield", 20),
            "Ambush" to SkillDef("Ambush", "Terrify", 10),
            "Feral Strike" to SkillDef("Feral Strike", "Terrify", 10),
            "Fatal Exception" to SkillDef("Fatal Exception", type = "Ultimate"),
            "The Algorithm" to SkillDef("The Algorithm", type = "Ultimate"),
            "Tryhard Mode" to SkillDef("Tryhard Mode", type = "Ultimate"),
            "DMCA Takedown" to SkillDef("DMCA Takedown", type = "Ultimate"),
            "Orbital Cannon" to SkillDef("Orbital Cannon", type = "Ultimate"),
            "Sky-Breaker" to SkillDef("Sky-Breaker", isDoubleAir = true, type = "Ultimate"),
            "Apex Predator" to SkillDef("Apex Predator", type = "Ultimate"),
            "Cataclysm" to SkillDef("Cataclysm", "Terrify", 20),
            "Obliterate" to SkillDef("Obliterate", "Shield", 30),
        )

    fun isAntiAir(move: String) = SKILL_DATABASE[move]?.isAntiAir == true

    fun isAntiFlyingDouble(move: String) = SKILL_DATABASE[move]?.isDoubleAir == true

    fun generateMoves(
        type: String,
        evoStage: Int,
    ): Triple<String, String, String> {
        val p1 =
            when (type) {
                "Tech" -> listOf("Ping", "Glitch", "Data Drain", "Overclock", "Static", "Timeshift").shuffled()
                "Social" -> listOf("Tweet", "Cancel", "Doxx", "Doomscroll", "Ratio").shuffled()
                "Gaming" -> listOf("Spam Click", "Rage Quit", "G-Fuel", "XP Boost", "Loot Box").shuffled()
                "Streaming" -> listOf("Lag", "Skip", "Binge", "Autoplay", "Ad Break").shuffled()
                "Reclaimed" -> listOf("Cyber Strike", "Aero Beam", "Mecha Dash", "Pixel Slash", "Light Pulse").shuffled()
                "Flying" -> listOf("Tackle", "Swipe", "Bite").shuffled()
                else -> listOf("Tackle", "Bite", "Scratch", "Growl", "Swipe").shuffled()
            }
        val p2 =
            when (type) {
                "Tech" -> listOf("System Wipe", "Firewall").random()
                "Social" -> listOf("Viral Surge", "Deplatform").random()
                "Gaming" -> listOf("Critical Strike", "Parry").random()
                "Streaming" -> listOf("Marathon", "Hypnotize").random()
                "Reclaimed" -> listOf("Chrono Blast", "Nova Shield").random()
                "Flying" -> listOf("Aero Beam", "Chrono Blast").random()
                else -> listOf("Ambush", "Feral Strike").random()
            }
        val ultimate =
            when (type) {
                "Tech" -> "Fatal Exception"
                "Social" -> "The Algorithm"
                "Gaming" -> "Tryhard Mode"
                "Streaming" -> "DMCA Takedown"
                "Reclaimed" -> "Orbital Cannon"
                "Flying" -> "Sky-Breaker"
                else -> "Apex Predator"
            }
        return when (evoStage) {
            1 -> Triple(p1[0], p1[1], p1[2])
            2 -> Triple(p1[0], p1[1], p2)
            else -> Triple(p1[0], p2, ultimate)
        }
    }

    fun getDetails(
        move: String,
        maxHp: Int,
    ): String {
        val b = (maxHp * 0.25).toInt()
        val def = SKILL_DATABASE[move] ?: SkillDef(move)

        if (def.type == "Ultimate") return "ULTIMATE: Deals ${b * 3} dmg if HP < 50%. Otherwise ${(b * 0.5).toInt()} dmg."
        if (def.type == "Timeshift") return "✨ Restores 2:00 Aether instead of attacking."
        if (def.type == "Utility" && move == "Cleanse") return "✨ Purges ALL traits, mutations, and infusions from the enemy!"
        val baseText = "30% ${(b * 0.8).toInt()} dmg, 50% $b dmg, 20% ${(b * 1.2).toInt()} dmg."
        var bonus = ""

        when (def.effect) {
            "Lifesteal" -> bonus = "✨ ${def.effectChance}% chance to Lifesteal."
            "DoubleStrike" -> bonus = "✨ ${def.effectChance}% chance to Double Strike."
            "Stun" -> bonus = "✨ ${def.effectChance}% chance to Stun enemy."
            "Poison" -> bonus = "✨ ${def.effectChance}% chance to Poison enemy."
            "Shield" -> bonus = "✨ ${def.effectChance}% chance to gain Temp Shield."
            "StealSpray" -> bonus = "✨ ${def.effectChance}% chance to steal a Spray."
            "StealNet" -> bonus = "✨ ${def.effectChance}% chance to steal a Net."
            "StealPotion" -> bonus = "✨ ${def.effectChance}% chance to steal a Potion."
            "StealCoins" -> bonus = "✨ ${def.effectChance}% chance to steal Coins."
            "Terrify" -> bonus = "✨ ${def.effectChance}% chance to Terrify (Instant Win)."
        }

        if (def.isAntiAir) bonus += "\n  ↳ ✈️ Can hit Flying beasts."
        if (def.isDoubleAir) bonus += "\n  ↳ ✈️💥 DOUBLE DAMAGE vs Flying!"

        return "$baseText\n  ↳ $bonus"
    }

    fun resolveSkill(
        act: GameActivity,
        pet: Netbeast,
        move: String,
    ): Pair<Int, String> {
        val def = SKILL_DATABASE[move] ?: SkillDef(move)

        if (def.type ==
            "Timeshift"
        ) {
            act.aetherSeconds += 120
            return Pair(0, "> ⏳ TIMESHIFT! Manipulated the realm to restore 2:00 Aether!")
        }
        if (def.type == "Basic") {
            val dmg = (act.currentEnemy!!.hp * 0.10).toInt().coerceAtLeast(1)
            return Pair(dmg, "> ⏱️ Time ran out! Basic Attack stripped 10% of their current HP!")
        }
        val b = (pet.maxHp * 0.25).toInt()
        val roll = Random.nextInt(100)
        var dmg =
            if (roll < 30) {
                (b * 0.8).toInt()
            } else if (roll < 80) {
                b
            } else {
                (b * 1.2).toInt()
            }
        var log = ""

        if (def.type == "Ultimate") {
            if (pet.hp <= pet.maxHp / 2) {
                dmg = b * 3
                log = "> 🛑 ULTIMATE ACTIVATED! DEVASTATING DAMAGE!"
            } else {
                dmg = (b * 0.5).toInt()
                log = "> ⚠️ Ultimate failed... requires HP below 50%!"
            }
            return Pair(dmg.coerceAtLeast(1), log)
        }

        log =
            if (roll < 30) {
                "> 📉 Weak hit!"
            } else if (roll < 80) {
                "> ⚔️ Solid hit!"
            } else {
                "> 💥 Critical strike!"
            }
        val bonusRoll = Random.nextInt(100)

        if (bonusRoll < def.effectChance) {
            when (def.effect) {
                "PurgeEnemy" -> {
                    val cleanName =
                        act.currentEnemy!!
                            .name
                            .replace(Regex("\\[.*?\\]"), "")
                            .trim()
                    act.currentEnemy!!.name = cleanName
                    act.currentEnemy!!.infusionStacks = 0
                    act.currentEnemy!!.infusionEl = "None"
                    log += "\n> ✝️ CLEANSED! The enemy was stripped of all unnatural power!"
                }

                "Lifesteal" -> {
                    val heal = (dmg * 0.5).toInt().coerceAtLeast(1)
                    pet.hp = (pet.hp + heal).coerceAtMost(pet.maxHp)
                    log +=
                        "\n> 🧛 LIFESTEAL! Recovered $heal HP!"
                }

                "DoubleStrike" -> {
                    dmg *= 2
                    log += "\n> ⚔️⚔️ DOUBLE STRIKE! Hit twice in a row!"
                }

                "Stun" -> {
                    CombatState.enemyStunned = true
                    log += "\n> ⚡ STUNNED! Enemy skips turn!"
                }

                "Poison" -> {
                    CombatState.enemyPoisonStacks++
                    log += "\n> ☠️ POISONED! Enemy takes passive damage!"
                }

                "Shield" -> {
                    val shieldAmt = (pet.maxHp * 0.2).toInt()
                    CombatState.playerShield += shieldAmt
                    log +=
                        "\n> 🛡️ SHIELDED! Gained $shieldAmt Temp HP!"
                }

                "StealSpray" -> {
                    act.sprays++
                    log += "\n> 💨 STOLE a Repel Spray!"
                }

                "StealNet" -> {
                    act.nets++
                    log += "\n> 🕸️ STOLE a Net!"
                }

                "StealPotion" -> {
                    act.potions++
                    log += "\n> 🧪 STOLE a Potion!"
                }

                "StealCoins" -> {
                    val c = Random.nextInt(5, 15)
                    act.focusCoins += c
                    log += "\n> 🪙 STOLE $c Coins!"
                }

                "Terrify" -> {
                    CombatState.terrified = true
                    log += "\n> 👻 TERRIFIED! Enemy breaks formation!"
                }
            }
        }
        return Pair(dmg.coerceAtLeast(1), log)
    }
}
