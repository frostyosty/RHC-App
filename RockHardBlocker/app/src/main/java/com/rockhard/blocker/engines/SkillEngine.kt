package com.rockhard.blocker
import kotlin.random.Random

object SkillEngine {
    fun isAntiAir(move: String) = listOf("Tackle", "Ping", "Tweet", "Spam Click", "Lag", "Cyber Strike", "Bite", "Scratch", "Swipe", "Light Pulse").contains(move)
    fun isAntiFlyingDouble(move: String) = listOf("Static", "Sky-Breaker").contains(move)

    fun generateMoves(type: String, evoStage: Int): Triple<String, String, String> {
        val p1 = when(type) {
            "Tech" -> listOf("Ping", "Glitch", "Data Drain", "Overclock", "Static", "Timeshift").shuffled()
            "Social" -> listOf("Tweet", "Cancel", "Doxx", "Doomscroll", "Ratio").shuffled()
            "Gaming" -> listOf("Spam Click", "Rage Quit", "G-Fuel", "XP Boost", "Loot Box").shuffled()
            "Streaming" -> listOf("Lag", "Skip", "Binge", "Autoplay", "Ad Break").shuffled()
            "Reclaimed" -> listOf("Cyber Strike", "Aero Beam", "Mecha Dash", "Pixel Slash", "Light Pulse").shuffled()
            "Flying" -> listOf("Tackle", "Swipe", "Bite").shuffled()
            else -> listOf("Tackle", "Bite", "Scratch", "Growl", "Swipe").shuffled()
        }
        val p2 = when(type) {
            "Tech" -> listOf("System Wipe", "Firewall").random()
            "Social" -> listOf("Viral Surge", "Deplatform").random()
            "Gaming" -> listOf("Critical Strike", "Parry").random()
            "Streaming" -> listOf("Marathon", "Hypnotize").random()
            "Reclaimed" -> listOf("Chrono Blast", "Nova Shield").random()
            "Flying" -> listOf("Aero Beam", "Chrono Blast").random()
            else -> listOf("Ambush", "Feral Strike").random()
        }
        val ultimate = when(type) {
            "Tech" -> "Fatal Exception"
            "Social" -> "The Algorithm"
            "Gaming" -> "Tryhard Mode"
            "Streaming" -> "DMCA Takedown"
            "Reclaimed" -> "Orbital Cannon"
            "Flying" -> "Sky-Breaker"
            else -> "Apex Predator"
        }
        return when (evoStage) { 1 -> Triple(p1[0], p1[1], p1[2]); 2 -> Triple(p1[0], p1[1], p2); else -> Triple(p1[0], p2, ultimate) }
    }

    fun getDetails(move: String, maxHp: Int): String {
        val b = (maxHp * 0.25).toInt()
        val baseText = "30% ${(b*0.8).toInt()} dmg, 50% $b dmg, 20% ${(b*1.2).toInt()} dmg."
        
        var bonus = when (move) {
            "Bite", "Swipe", "Data Drain", "Binge", "Mecha Dash" -> "✨ 20% chance to Lifesteal."
            "Spam Click", "Ping", "Tweet", "Cyber Strike", "Tackle" -> "✨ 15% chance to Double Strike."
            "Glitch", "Cancel", "Rage Quit", "System Wipe", "Deplatform" -> "✨ 15% chance to Stun enemy."
            "Static", "Ratio", "G-Fuel", "Viral Surge" -> "✨ 25% chance to Poison enemy."
            "Overclock", "Autoplay", "Nova Shield", "Parry", "Firewall", "XP Boost" -> "✨ 20% chance to gain Temp Shield."
            "Timeshift" -> "✨ Restores 2:00 Aether instead of attacking."
            "Doomscroll" -> "✨ 10% chance to steal a Spray."
            "Loot Box" -> "✨ 10% chance to steal a Net."
            "Light Pulse" -> "✨ 10% chance to steal a Potion."
            "Ad Break", "Scratch", "Doxx" -> "✨ 25% chance to steal Coins."
            "Ambush", "Feral Strike", "Hypnotize", "Marathon", "Chrono Blast", "Aero Beam", "Skip", "Pixel Slash", "Growl", "Critical Strike" -> "✨ 10% chance to Terrify (Instant Win)."
            "Fatal Exception", "The Algorithm", "Tryhard Mode", "DMCA Takedown", "Orbital Cannon", "Apex Predator", "Sky-Breaker" -> return "ULTIMATE: Deals ${b*3} dmg if HP < 50%. Otherwise ${(b*0.5).toInt()} dmg."
            else -> ""
        }
        if (isAntiAir(move)) bonus += "\n  ↳ ✈️ Can hit Flying beasts."
        if (isAntiFlyingDouble(move)) bonus += "\n  ↳ ✈️💥 DOUBLE DAMAGE vs Flying!"
        return "$baseText\n  ↳ $bonus"
    }

    fun resolveSkill(act: GameActivity, pet: Netbeast, move: String): Pair<Int, String> {
        if (move == "Timeshift") { act.aetherSeconds += 120; return Pair(0, "> ⏳ TIMESHIFT! Manipulated the realm to restore 2:00 Aether!") }

        val b = (pet.maxHp * 0.25).toInt()
        val roll = Random.nextInt(100)
        var dmg = if (roll < 30) (b * 0.8).toInt() else if (roll < 80) b else (b * 1.2).toInt()
        var log = ""

        if (listOf("Fatal Exception", "The Algorithm", "Tryhard Mode", "DMCA Takedown", "Orbital Cannon", "Apex Predator", "Sky-Breaker").contains(move)) {
            if (pet.hp <= pet.maxHp / 2) { dmg = b * 3; log = "> 🛑 ULTIMATE ACTIVATED! DEVASTATING DAMAGE!" } 
            else { dmg = (b * 0.5).toInt(); log = "> ⚠️ Ultimate failed... requires HP below 50%!" }
            return Pair(dmg.coerceAtLeast(1), log)
        }

        log = if (roll < 30) "> 📉 Weak hit!" else if (roll < 80) "> ⚔️ Solid hit!" else "> 💥 Critical strike!"
        val bonusRoll = Random.nextInt(100)

        when (move) {
            "Bite", "Swipe", "Data Drain", "Binge", "Mecha Dash" -> if (bonusRoll < 20) { val heal = (dmg * 0.5).toInt().coerceAtLeast(1); pet.hp = (pet.hp + heal).coerceAtMost(pet.maxHp); log += "\n> 🧛 LIFESTEAL! Recovered $heal HP!" }
            "Spam Click", "Ping", "Tweet", "Cyber Strike", "Tackle" -> if (bonusRoll < 15) { dmg *= 2; log += "\n> ⚔️⚔️ DOUBLE STRIKE! Hit twice in a row!" }
            "Glitch", "Cancel", "Rage Quit", "System Wipe", "Deplatform" -> if (bonusRoll < 15) { CombatState.enemyStunned = true; log += "\n> ⚡ STUNNED! Enemy skips turn!" }
            "Static", "Ratio", "G-Fuel", "Viral Surge" -> if (bonusRoll < 25) { CombatState.enemyPoisonStacks++; log += "\n> ☠️ POISONED! Enemy takes passive damage!" }
            "Overclock", "Autoplay", "Nova Shield", "Parry", "Firewall", "XP Boost" -> if (bonusRoll < 20) { val shieldAmt = (pet.maxHp * 0.2).toInt(); CombatState.playerShield += shieldAmt; log += "\n> 🛡️ SHIELDED! Gained $shieldAmt Temp HP!" }
            "Doomscroll" -> if (bonusRoll < 10) { act.sprays++; log += "\n> 💨 STOLE a Repel Spray!" }
            "Loot Box" -> if (bonusRoll < 10) { act.nets++; log += "\n> 🕸️ STOLE a Net!" }
            "Light Pulse" -> if (bonusRoll < 10) { act.potions++; log += "\n> 🧪 STOLE a Potion!" }
            "Ad Break", "Scratch", "Doxx" -> if (bonusRoll < 25) { val c = Random.nextInt(5,15); act.focusCoins += c; log += "\n> 🪙 STOLE $c Coins!" }
            "Ambush", "Feral Strike", "Hypnotize", "Marathon", "Chrono Blast", "Aero Beam", "Skip", "Pixel Slash", "Growl", "Critical Strike" -> if (bonusRoll < 10) { CombatState.terrified = true; log += "\n> 👻 TERRIFIED! Enemy breaks formation!" }
        }
        return Pair(dmg.coerceAtLeast(1), log)
    }
}
