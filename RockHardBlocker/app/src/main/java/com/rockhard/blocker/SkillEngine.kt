package com.rockhard.blocker
import kotlin.random.Random

object SkillEngine {

    fun generateMoves(type: String, evoStage: Int): Triple<String, String, String> {
        val p1 = when(type) {
            "Tech" -> listOf("Ping", "Glitch", "Data Drain", "Overclock", "Static").shuffled()
            "Social" -> listOf("Tweet", "Cancel", "Doxx", "Doomscroll", "Ratio").shuffled()
            "Gaming" -> listOf("Spam Click", "Rage Quit", "G-Fuel", "XP Boost", "Loot Box").shuffled()
            "Streaming" -> listOf("Lag", "Skip", "Binge", "Autoplay", "Ad Break").shuffled()
            "Reclaimed" -> listOf("Cyber Strike", "Aero Beam", "Mecha Dash", "Pixel Slash", "Light Pulse").shuffled()
            else -> listOf("Tackle", "Bite", "Scratch", "Growl", "Swipe").shuffled()
        }
        val p2 = when(type) {
            "Tech" -> listOf("System Wipe", "Firewall").random()
            "Social" -> listOf("Viral Surge", "Deplatform").random()
            "Gaming" -> listOf("Critical Strike", "Parry").random()
            "Streaming" -> listOf("Marathon", "Hypnotize").random()
            "Reclaimed" -> listOf("Chrono Blast", "Nova Shield").random()
            else -> listOf("Ambush", "Feral Strike").random()
        }
        val ultimate = when(type) {
            "Tech" -> "Fatal Exception"
            "Social" -> "The Algorithm"
            "Gaming" -> "Tryhard Mode"
            "Streaming" -> "DMCA Takedown"
            "Reclaimed" -> "Orbital Cannon"
            else -> "Apex Predator"
        }

        return when (evoStage) {
            1 -> Triple(p1[0], p1[1], p1[2])
            2 -> Triple(p1[0], p1[1], p2)
            else -> Triple(p1[0], p2, ultimate)
        }
    }

    // Modal Details Text
    fun getDetails(move: String, maxHp: Int): String {
        val b = (maxHp * 0.25).toInt()
        return when (move) {
            "Ping", "Tweet", "Spam Click", "Lag", "Cyber Strike", "Tackle", "Bite" -> "30% ${(b*0.8).toInt()} dmg, 50% $b dmg, 20% ${(b*1.2).toInt()} dmg."
            "Data Drain", "Ad Break" -> "Deals $b dmg. 20% chance to steal Coins."
            "Doomscroll" -> "Deals $b dmg. 10% chance to steal a Spray."
            "Loot Box" -> "Deals $b dmg. 15% chance to steal a Net."
            "Mecha Dash" -> "Deals $b dmg. 10% chance to steal a Potion."
            "Fatal Exception", "The Algorithm", "Tryhard Mode", "DMCA Takedown", "Orbital Cannon", "Apex Predator" -> "ULTIMATE: Deals ${b*3} dmg if HP < 50%. Otherwise ${(b*0.5).toInt()} dmg."
            else -> "Deals $b dmg."
        }
    }

    // Combat Resolution
    fun resolveSkill(act: GameActivity, pet: Netbeast, move: String): Pair<Int, String> {
        val b = (pet.maxHp * 0.25).toInt()
        var finalDmg = b
        var log = ""

        when (move) {
            "Ping", "Tweet", "Spam Click", "Lag", "Cyber Strike", "Tackle", "Bite" -> {
                val roll = Random.nextInt(100)
                if (roll < 30) { finalDmg = (b * 0.8).toInt(); log = "> 📉 Weak hit!" }
                else if (roll < 80) { finalDmg = b; log = "> ⚔️ Solid hit!" }
                else { finalDmg = (b * 1.2).toInt(); log = "> 💥 Critical strike!" }
            }
            "Data Drain", "Ad Break" -> {
                if (Random.nextInt(100) < 20) { val s = Random.nextInt(5, 15); act.focusCoins += s; log = "> 🪙 STOLE $s Coins!" }
            }
            "Doomscroll" -> if (Random.nextInt(100) < 10) { act.sprays++; log = "> 💨 STOLE a Repel Spray!" }
            "Loot Box" -> if (Random.nextInt(100) < 15) { act.nets++; log = "> 🕸️ STOLE a Net!" }
            "Mecha Dash" -> if (Random.nextInt(100) < 10) { act.potions++; log = "> 🧪 STOLE a Potion!" }
            "Fatal Exception", "The Algorithm", "Tryhard Mode", "DMCA Takedown", "Orbital Cannon", "Apex Predator" -> {
                if (pet.hp <= pet.maxHp / 2) { finalDmg = b * 3; log = "> 🛑 ULTIMATE ACTIVATED! DEVASTATING DAMAGE!" } 
                else { finalDmg = (b * 0.5).toInt(); log = "> ⚠️ Ultimate failed... requires HP below 50%!" }
            }
        }
        return Pair(finalDmg.coerceAtLeast(1), log)
    }
}