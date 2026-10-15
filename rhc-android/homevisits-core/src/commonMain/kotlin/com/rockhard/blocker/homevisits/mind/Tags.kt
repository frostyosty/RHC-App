package com.rockhard.blocker.homevisits.mind

/**
 * The one shared list of tags, for items (11), topics (07) and places. Tastes map these to how much a
 * character likes them. Content files may only use tags from this list (07's validator checks it).
 */
enum class Tag {
    PAINTING, LANDSCAPE, SEASCAPE, PORTRAIT, ABSTRACT, FLOWERS, PLANTS, BOOKS, MUSIC,
    CANDLES, COSY, BRIGHT, TIDY, HANDMADE, VINTAGE, MODERN, PHOTOS, FOOD, BAKING, GARDEN,
    SPORT, RUGBY, ART, CHURCH, TRAVEL, FAMILY, NEWS, WEATHER;

    /** The word content files use: "painting". */
    val word: String get() = name.lowercase()

    companion object {
        private val byWord = entries.associateBy { it.word }
        fun of(word: String): Tag? = byWord[word]
    }
}

/**
 * What a character thinks is right, weighted 0 to 1 on their sheet. Actions (anyone's, the player's too)
 * uphold or break these. DISCRETION isn't on the sheets: it comes from the discretion trait (see
 * [CharSheet.standard]), because "tell" and "keep a secret" need someone to care about it.
 */
enum class Standard { HONESTY, KINDNESS, LOYALTY, GENEROSITY, RELIABILITY, MODESTY, TIDINESS, FAITH, DISCRETION }
