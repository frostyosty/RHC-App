package com.rockhard.blocker.homevisits.story

/** A character said they'd do something on a day ("I'll come round again tomorrow!"). Step 08 keeps them. */
data class Promise(val who: CharId, val day: Int, val what: String)

/**
 * The story's part of the save: flags (`firstVisit.done`,
 * `firstVisit.paintingAnswer`, `player.name`, …), promises, and where an
 * unfinished visit got to, so a closed app picks up from the first unfinished
 * beat.
 */
class StoryState(
    val flags: MutableMap<String, String> = LinkedHashMap(),
    val promises: MutableList<Promise> = ArrayList(),
) {
    fun flag(key: String): String? = flags[key]
    fun isSet(key: String) = flags[key] == "true"
    fun set(key: String, value: String?) { if (value == null) flags.remove(key) else flags[key] = value }
    fun set(key: String, value: Boolean) = set(key, if (value) "true" else null)

    companion object {
        const val FIRST_VISIT_DONE = "firstVisit.done"
        const val FIRST_VISIT_AT = "firstVisit.at"               // the checkpoint an unfinished visit resumes from
        const val PAINTING_ANSWER = "firstVisit.paintingAnswer"  // 0 love it, 1 perfect there, 2 wasn't sure
        const val PLAYER_NAME = "player.name"
        const val SEEN_CAMERA_TIP = "tips.holdPhoneUp"
    }
}
