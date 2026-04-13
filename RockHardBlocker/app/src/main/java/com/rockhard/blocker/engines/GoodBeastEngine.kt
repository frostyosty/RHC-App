package com.rockhard.blocker

object GoodBeastEngine {
    private val nsfwRoots = listOf("porn", "sex", "nude", "naked", "xxx", "xvideo", "onlyfans", "fans", "nsfw", "hub", "tube")
    private val goodPrefixes = listOf("Cyber", "Aero", "Mecha", "Pixel", "Chrono", "Neo", "Nova", "Light")
    private val goodSuffixes = listOf("saurus", "ling", "let", "puff", "wing", "sprite", "bot", "mon", "fox")

    private val lightMoveSuffixes = listOf("Strike", "Beam", "Dash", "Slash", "Zap", "Spark", "Tap")
    private val heavyMoveSuffixes = listOf("Burst", "Blast", "Shield", "Surge", "Wave", "Pulse", "Crash")

    fun generateName(triggerSource: String): String {
        var base = cleanSource(triggerSource)
        if (base.length < 3) base = goodPrefixes.random().lowercase() + base
        
        val capitalizedBase = base.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        return capitalizedBase + goodSuffixes.random()
    }

fun generateMoves(triggerSource: String): Triple<String, String, String> {
        val base = cleanSource(triggerSource)
        val capitalizedBase = base.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        
        val m1 = "$capitalizedBase ${lightMoveSuffixes.random()}"
        val m2 = "$capitalizedBase ${heavyMoveSuffixes.random()}"
        val m3 = "$capitalizedBase Overdrive" // Added a 3rd Ultimate move!
        
        return Triple(m1, m2, m3)
    }

    private fun cleanSource(source: String): String {
        var base = source.lowercase()
            .replace("https://", "").replace("http://", "")
            .replace("www.", "").replace(".com", "").replace(".org", "").replace(".net", "")
            .replace("com.", "").replace("android.", "").replace("google.", "")
            
        val parts = base.split(".", "/", " ", ":", "-")
        base = parts.maxByOrNull { it.length } ?: base
        
        for (nsfw in nsfwRoots) {
            if (base.contains(nsfw)) base = base.replace(nsfw, "")
        }
        return base.trim()
    }
}