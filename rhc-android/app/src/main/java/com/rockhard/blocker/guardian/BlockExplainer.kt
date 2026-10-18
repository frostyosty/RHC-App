package com.rockhard.blocker.guardian

/**
 * [summary] is one line for the slide-in toasty; [details] is the "what / why / how to change it"
 * text on the full-screen red wall, which you see after tapping the toasty.
 */
internal class BlockExplanation(val summary: String, val details: String)

/**
 * Turns the reason string a [ShieldAction.Block] carries into plain language. The reasons are written
 * in ShieldRuleEngine; keep the prefixes matched here in step with them.
 */
internal object BlockExplainer {

    fun explain(reason: String, appLabel: String, nightfallEndMins: Int): BlockExplanation {
        val word = reason.substringAfter(": ").substringBefore(" (").trim()
        val clock = if (nightfallEndMins >= 0) String.format("%d:%02d", nightfallEndMins / 60, nightfallEndMins % 60) else null
        val nightfallUntil = if (clock != null) "Nightfall lifts at $clock." else "Nightfall hours are set on the main screen."
        val overrideOnly = "It ends only with a System Override from the main screen."
        val tamperChange = "The Safe App Manager on the main screen can still uninstall your other apps. For the setup steps, use the step buttons there: each one opens Settings for 2 minutes."

        return when {
            reason.startsWith("Explicit Input/Query") -> of(
                "explicit search",
                "$appLabel's address bar or search box had an explicit term in it: \"$word\".",
                "Content Guard reads the address bar and search box of every browser, not just the page.",
                "Content Guard is always on."
            )
            reason.startsWith("Nightfall") -> of(
                "Nightfall",
                "$appLabel was opened during Nightfall hours.",
                "While Nightfall is on, only the phone, contacts and keyboards are allowed.",
                nightfallUntil
            )
            reason.startsWith("Nuclear") -> of(
                "Calls and Texts ONLY",
                "$appLabel was opened while Calls and Texts ONLY is on.",
                "This mode lets through only the phone, contacts, messages and the keyboard.",
                overrideOnly
            )
            reason.startsWith("Dumb Phone (No Camera)") -> of(
                "Dumb Phone",
                "$appLabel was opened while Dumb Phone (no camera) is on.",
                "This mode lets through only calls, messages, calculator, calendar, clock, weather and maps.",
                overrideOnly
            )
            reason.startsWith("Dumb Phone (With Camera)") -> of(
                "Dumb Phone",
                "$appLabel was opened while Dumb Phone (with camera) is on.",
                "This mode lets through only calls, messages, camera and gallery, calculator, calendar, clock, weather and maps.",
                overrideOnly
            )
            reason.startsWith("No Internet") -> of(
                "No Internet",
                "$appLabel was opened while No Internet is on.",
                "This mode blocks every web browser, the app stores, YouTube, Netflix and the big social apps.",
                overrideOnly
            )
            reason.startsWith("No Videos Mode: Video App") -> of(
                "No Videos",
                "$appLabel is a video app, and No Videos is on.",
                "This mode blocks YouTube, Netflix, Twitch and the other video apps.",
                overrideOnly
            )
            reason.startsWith("No Videos Mode: WeChat") -> of(
                "No Videos",
                "WeChat's video feed (Channels) was on screen.",
                "No Videos is on, and it blocks WeChat's video feed while leaving chats alone.",
                overrideOnly
            )
            reason.startsWith("No Videos Mode") -> of(
                "No Videos",
                "A video player was on screen in $appLabel.",
                "No Videos is on, and it blocks any screen with a video player in it.",
                overrideOnly
            )
            reason.startsWith("Anti-Tamper: System Settings") -> of(
                "a locked Settings screen",
                "A Settings screen that can switch the shield off was opened in $appLabel (accessibility, device admin, app info or the installed apps list).",
                "Those screens are locked so the shield can't be turned off in a weak moment.",
                tamperChange
            )
            reason.startsWith("Anti-Tamper: Device Admin") -> of(
                "the device admin screen",
                "The device admin list, with this app on it, was opened in $appLabel.",
                "Turning that off would also remove the uninstall lock.",
                tamperChange
            )
            reason.startsWith("Anti-Tamper") -> of(
                "this app's info page",
                "This app's info page, with Uninstall, Force stop or Clear data on it, was opened in $appLabel.",
                "Those buttons would switch the shield off, so the page is locked.",
                tamperChange
            )
            reason.startsWith("App Overcome") -> of(
                "a blocked app",
                "$appLabel matched \"$word\" on your list of apps to overcome.",
                "Every app on that list is blocked. The first time you overcome one earns a reward, and after that it just blocks.",
                "Edit the list under OVERCOME APPS on the main screen."
            )
            reason.startsWith("Hyperlink Overcome") -> of(
                "a blocked website",
                "A site on your list of websites to overcome showed up in $appLabel (${word.ifEmpty { reason }}).",
                "Every site on that list is blocked, in a browser address bar or as a link or text inside other apps.",
                "Edit the list under OVERCOME WEBSITES on the main screen."
            )
            reason.startsWith("Content Guard") && reason.contains("(detected") -> of(
                "mild words adding up",
                "$appLabel showed several mild flagged words: $word.",
                "None is enough alone, but ${reason.substringAfter("(detected ").substringBefore(")")} on one page crossed the limit. The limit drops from 4 to 2 when the page has 8 or more images.",
                "Content Guard is always on."
            )
            reason.startsWith("Content Guard") -> of(
                "Content Guard",
                "$appLabel showed a word from Content Guard's strict list: \"$word\".",
                "One word from that list is enough, so the screen is blocked straight away.",
                "Content Guard is always on."
            )
            else -> of(
                "the shield",
                "$appLabel triggered a shield rule.",
                reason,
                "Nothing to change."
            )
        }
    }

    private fun of(rule: String, what: String, why: String, change: String) = BlockExplanation(
        summary = "Blocked by $rule",
        details = "What happened\n$what\n\nWhy\n$why\n\nTo change this\n$change"
    )
}
