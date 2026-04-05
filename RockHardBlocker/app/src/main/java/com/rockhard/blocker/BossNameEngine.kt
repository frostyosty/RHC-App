package com.rockhard.blocker

object BossNameEngine {
    private val nsfwRoots = listOf("porn", "sex", "nude", "naked", "xxx", "xvideo", "onlyfans", "fans", "nsfw", "hub", "tube")
    private val evilPrefixes = listOf("Void", "Gore", "Rot", "Flesh", "Doom", "Blood", "Blight", "Grim", "Dark")
    private val kaijuSuffixes = listOf("saur", "ity", "odon", "lith", "morph", "demon", "fiend", "beast", "goliath")

    fun generateBossName(triggerSource: String): String {
        // 1. Clean the raw URL / Package Name
        var base = triggerSource.lowercase()
            .replace("https://", "").replace("http://", "")
            .replace("www.", "").replace(".com", "").replace(".org", "").replace(".net", "")
            .replace("com.", "").replace("android.", "").replace("google.", "")
            
        // 2. Extract the main word (e.g., from com.zhiliaoapp.musically -> musically)
        val parts = base.split(".", "/", " ", ":", "-")
        base = parts.maxByOrNull { it.length } ?: base

        // 3. Purge NSFW roots
        for (nsfw in nsfwRoots) {
            if (base.contains(nsfw)) base = base.replace(nsfw, "")
        }

        base = base.trim()

        // 4. If the word is too short or empty after purging, give it an evil prefix
        if (base.length < 3) {
            base = evilPrefixes.random().lowercase() + base
        }

        // 5. Capitalize and add Kaiju Suffix
        val capitalizedBase = base.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        return capitalizedBase + kaijuSuffixes.random()
    }
}