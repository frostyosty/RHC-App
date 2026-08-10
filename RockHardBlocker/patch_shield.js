const fs = require('fs');
try {
    const path = 'app/src/main/java/com/rockhard/blocker/guardian/ShieldRuleEngine.kt';
    let code = fs.readFileSync(path, 'utf8');

    // 1. Upgrade the Hard Words block (Injecting Safe Phrases + Proximity Maps + isMatchValid)
    const hardStart = code.indexOf('val wholeWordRequired = listOf(');
    const hardEndStr = 'if (foundHard != null) return ShieldAction.Block("Content Guard: " + foundHard, true, getRedirect(foundHard))';
    const hardEnd = code.indexOf(hardEndStr, hardStart) + hardEndStr.length;

    const newHardBlock = `val wholeWordRequired = listOf("ass", "butt", "strip", "sex", "tits", "fap", "milf", "anal", "breast", "breasts", "mature", "nude", "naked", "dick", "cock", "pussy", "cum")
        
        // --- PRE-PROCESSING: SAFE PHRASES ---
        // Completely erases fixed safe phrases before any logic runs
        val safePhrases = listOf("chicken breast", "turkey breast", "breast cancer", "weather stripping", "power strip", "comic strip", "strip mall", "sex education", "fair sex")
        var sanitizedText = lowerAllText
        for (phrase in safePhrases) {
            sanitizedText = sanitizedText.replace(phrase, "***")
        }

        // --- CONTEXTUAL PROXIMITY MAPPING ---
        // If a word is found, check the surrounding words. If they match these, it's safe.
        val safeContextMap = mapOf(
            "breast" to listOf("chicken", "turkey", "duck", "cancer", "feed", "pump", "milk", "meat", "recipe", "roast", "fried", "bone", "fillet"),
            "breasts" to listOf("chicken", "turkey", "duck", "cancer", "feed", "pump", "milk", "meat", "recipe", "roast", "fried", "bone", "fillet"),
            "mature" to listOf("cheese", "cheddar", "tree", "forest", "nature", "audience", "rating", "market", "economy", "student", "age"),
            "strip" to listOf("weather", "power", "comic", "mall", "led", "light", "bacon", "steak", "pork", "beef", "wood", "metal", "plastic", "stripes"),
            "naked" to listOf("eye", "truth", "mole rat", "gun", "snake", "bike", "motorcycle", "short", "option"),
            "nude" to listOf("lipstick", "makeup", "color", "colour", "shoe", "heels", "palette", "nails", "painting", "art", "museum")
        )

        fun isMatchValid(word: String, index: Int, text: String): Boolean {
            val isWholeWord = if (wholeWordRequired.contains(word)) {
                val beforeChar = if (index > 0) text[index - 1] else ' '
                val afterChar = if (index + word.length < text.length) text[index + word.length] else ' '
                // NOTE: '-' is not a letter/digit, so "-ass-" counts as a valid boundary and WILL be flagged.
                !beforeChar.isLetterOrDigit() && !afterChar.isLetterOrDigit()
            } else true

            if (!isWholeWord) return false

            // Window Proximity Check (Only runs IF the word was found, saving massive battery)
            if (safeContextMap.containsKey(word)) {
                val start = Math.max(0, index - 40)
                val end = Math.min(text.length, index + word.length + 40)
                val window = text.substring(start, end)
                // If any safe modifier is found within 40 characters, invalidate the flag
                if (safeContextMap[word]!!.any { window.contains(it) }) return false
            }
            return true
        }

        val foundHard = hardWords.firstOrNull { word ->
            var index = 0
            var found = false
            while (true) {
                index = sanitizedText.indexOf(word, index)
                if (index == -1) break
                if (isMatchValid(word, index, sanitizedText)) {
                    found = true
                    break
                }
                index += word.length
            }
            found
        }
        if (foundHard != null) return ShieldAction.Block("Content Guard: " + foundHard, true, getRedirect(foundHard))`;

    if (hardStart !== -1 && hardEnd > hardStart) {
        code = code.substring(0, hardStart) + newHardBlock + code.substring(hardEnd);
    } else {
        throw new Error("Could not locate Hard Words block.");
    }

    // 2. Upgrade the Soft Words block (Now uses the same sanitizedText and isMatchValid check)
    const softStart = code.indexOf('var softCount = 0');
    const softEndStr = 'if (softCount >= softThreshold) {';
    const softEnd = code.indexOf(softEndStr, softStart);

    const newSoftBlock = `var softCount = 0
        val caughtWords = mutableListOf<String>()
        for (word in softWords) {
            var index = 0
            while (true) {
                index = sanitizedText.indexOf(word, index)
                if (index == -1) break
                
                if (isMatchValid(word, index, sanitizedText)) {
                    softCount++
                    caughtWords.add(word)
                }
                index += word.length
            }
        }
        `;

    if (softStart !== -1 && softEnd > softStart) {
        code = code.substring(0, softStart) + newSoftBlock + code.substring(softEnd);
    } else {
        throw new Error("Could not locate Soft Words block.");
    }

    fs.writeFileSync(path, code);
    console.log("✅ FIXED: ShieldRuleEngine Scunthorpe logic successfully upgraded!");
} catch (e) {
    console.log("❌ Failed to patch: " + e.message);
}
