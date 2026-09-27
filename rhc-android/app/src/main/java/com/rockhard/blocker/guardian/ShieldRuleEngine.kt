package com.rockhard.blocker

import android.content.SharedPreferences
import android.view.accessibility.AccessibilityNodeInfo
import java.util.*

sealed class ShieldAction {
    object Allow : ShieldAction()
    data class Block(val reason: String, val canDefend: Boolean, val redirectTarget: String? = null) : ShieldAction()
    data class RewardApp(val triggerWord: String) : ShieldAction()
    data class RewardWeb(val triggerWord: String, val context: String) : ShieldAction()
    data class WeatherBuff(val element: String) : ShieldAction()
}

class ShieldRuleEngine(private val prefs: SharedPreferences, private val appName: String) {

    companion object {
        /** True inside the Nightfall hours (NIGHTFALL_START..NIGHTFALL_END, minutes past midnight, may wrap midnight). */
        fun isNightfall(prefs: SharedPreferences, cal: Calendar = Calendar.getInstance()): Boolean {
            val nfStart = prefs.getInt("NIGHTFALL_START", -1)
            val nfEnd = prefs.getInt("NIGHTFALL_END", -1)
            if (nfStart == -1 || nfEnd == -1 || nfStart == nfEnd) return false
            val currentMins = (cal.get(Calendar.HOUR_OF_DAY) * 60) + cal.get(Calendar.MINUTE)
            return if (nfStart < nfEnd) currentMins in nfStart..nfEnd else currentMins >= nfStart || currentMins <= nfEnd
        }
    }

    private val safeDomains = listOf("aistudio", "github", "codespaces")
    private val safePackages = listOf(
            "org.thoughtcrime.securesms", "com.whatsapp", "com.rockhard",
            "notion", "evernote", "simplenote", "zoho.notebook", "onenote", 
            "microsoft.notes", "microsoft.office", "keep", "docs.google", 
            "notes", "notepad", "journal", "journey", "dayone", "todoist", 
            "ticktick", "anydo", "wordpress", "medium.reader", "slack", "teams"
        )
    
    // Comprehensive NSFW / Explicit keyword listings
    private val hardWords = listOf(
        "nsfw", "porno", "porn", "pornography", "pornstar", "hentai", "milf", "xnxx", "xvideo", "xvideos", 
        "pornhub", "onlyfans", "redtube", "brazzers", "xhamster", "rule34", "orgasm", "orgasms", "horny", 
        "masturbate", "masturbation", "chaturbate", "fap", "fapping", "jerkoff", "jerk off", "blowjob", "blowjobs",
        "cumshot", "cumshots", "creampie", "deepthroat", "gangbang", "cunnilingus", "fellatio", "ejaculation",
        "vagina", "clitoris", "penis", "erotica", "escort", "escorts", "striptease", "stripclub", "playboy", "beeg",
        "spankbang", "eporner", "bdsmd", "色情", "黄片"
    )
    
    private val softWords = listOf(
        "explicit", "sensitive content", "fuck", "fucking", "bitch", "nude", "naked", "sex", "erotic",
        "cleavage", "lingerie", "bikini", "breast", "breasts", "boobs", "tits", "ass", "butt", "booty",
        "intimacy", "intimate", "adult", "mature", "romance", "dildo", "underwear", "strip", "sensual"
    )

    private val protectedPkgs = listOf(
        "systemui", "nexus", "pixel", "gallery", "camera", "dialer", "contacts",
        "note", "keyboard", "inputmethod", "swiftkey", "clock", "alarm",
        "calculator", "calendar", "messages", "files", "weather", "compass", "radio", "bluetooth",
        "nfc", "telecom", "updater", "print", "sim", "theme",
        "com.google.android", "android.system", "com.android", "com.samsung.android",
        "com.huawei", "com.xiaomi", "com.oppo", "com.vivo", "com.realme", "com.oneplus",
        "com.transsion", "com.lge.systemui", "com.android.permissioncontroller", "com.android.documentsui",
        "launcher", "home", "trebuchet", "quickstep", "nova", "apex", "smartlauncher", "actionlauncher"
    )
    
    // Packageinstaller removed to permit update screens
    private val antiTamperPkgs = listOf(
        "settings", "securitycenter", "permissioncontroller",
        "com.coloros.safecenter", "com.coloros.securitypermission", "com.vivo.permissionmanager",
        "com.oplus.safecenter", "com.huawei.systemmanager", "com.samsung.android.sm", "com.samsung.android.lool",
        "com.hihonor.systemmanager", "com.meizu.safe", "com.iqoo.secure",
        "systemmanager", "safecenter", "appmanager"
    )

    private val popularAppPackageMap = mapOf(
        "tiktok" to "com.zhiliaoapp.musically", "instagram" to "com.instagram.android",
        "snapchat" to "com.snapchat.android", "youtube" to "com.google.android.youtube",
        "facebook" to "com.facebook.katana", "twitter" to "com.twitter.android",
        "x" to "com.twitter.android", "reddit" to "com.reddit.frontpage",
        "wechat" to "com.tencent.mm", "telegram" to "org.telegram.messenger",
        "tinder" to "com.tinder", "discord" to "com.discord",
        "twitch" to "tv.twitch.android.app", "spotify" to "com.spotify.music"
    )

    private val notesApps = listOf(
        "notion",
        "evernote",
        "simplenote",
        "zoho",
        "onenote",
        "microsoft.notes",
        "keep",
        "docs.google",
        "notes",
        "notepad",
        "journal",
        "journey",
        "dayone",
        "obsidian",
        "standardnotes",
        "logseq",
        "upnote",
        "joplin",
        "bear",
        "craft",
        "notability",
        "goodnotes",
        "todoist",
        "ticktick",
        "anydo",
        "tasks"
    )

    private val permanentSafeHavens = listOf(
        "calculator",
        "calc",
        "clock",
        "alarm",
        "timer",
        "stopwatch",
        "deskclock",
        "recorder",
        "voicememo",
        "soundrecorder",
        "audiorecorder",
        "bank",
        "finance",
        "banking",
        "paypal",
        "venmo",
        "cashapp",
        "monzo",
        "revolut",
        "chase",
        "wellsfargo",
        "citi",
        "amex",
        "discover",
        "barclays",
        "santander",
        "hsbc",
        "capitalone",
        "pay",
        "wallet",
        "stripe",
        "square",
        "maps",
        "navigation",
        "waze",
        "uber",
        "lyft",
        "bolt",
        "grab",
        "transit",
        "authenticator",
        "2fa",
        "authy",
        "duosecurity",
        "okta",
        "bitwarden",
        "1password",
        "lastpass",
        "dashlane"
    )

    private val homeLauncherWords = listOf("launcher", "trebuchet", "quickstep")
    private val explicitUrlWords = listOf("sexy", "porn", "nude", "naked", "nsfw", "porno", "onlyfans", "xvideo", "pornhub", "rule34", "erotic")
    private val nonAlphanumeric = Regex("[^a-zA-Z0-9]")
    private val wholeWordRequired = listOf("ass", "butt", "strip", "sex", "tits", "fap", "milf", "anal", "breast", "breasts", "mature", "nude", "naked", "dick", "cock", "pussy", "cum")
    // Fixed safe phrases are erased before any matching
    private val safePhrases = listOf("chicken breast", "turkey breast", "breast cancer", "weather stripping", "power strip", "comic strip", "strip mall", "sex education", "fair sex")
    // If a word is found, check the surrounding words. If they match these, it's safe.
    private val safeContextMap = mapOf(
        "breast" to listOf("chicken", "turkey", "duck", "cancer", "feed", "pump", "milk", "meat", "recipe", "roast", "fried", "bone", "fillet"),
        "breasts" to listOf("chicken", "turkey", "duck", "cancer", "feed", "pump", "milk", "meat", "recipe", "roast", "fried", "bone", "fillet"),
        "mature" to listOf("cheese", "cheddar", "tree", "forest", "nature", "audience", "rating", "market", "economy", "student", "age"),
        "strip" to listOf("weather", "power", "comic", "mall", "led", "light", "bacon", "steak", "pork", "beef", "wood", "metal", "plastic", "stripes"),
        "naked" to listOf("eye", "truth", "mole rat", "gun", "snake", "bike", "motorcycle", "short", "option"),
        "nude" to listOf("lipstick", "makeup", "color", "colour", "shoe", "heels", "palette", "nails", "painting", "art", "museum")
    )
    private val searchEnginesAndWhitelist = listOf("google.", "bing.com", "duckduckgo", "yahoo.com", "gab.com", "search.brave", "ecosia.org", "qwant.com")

    // The blocklists are split again only when their saved text changes
    private var appListRaw: String? = null
    private var appList: List<String> = emptyList()
    private var webListRaw: String? = null
    private var webList: List<String> = emptyList()

    private fun blockedApps(): List<String> {
        val raw = prefs.getString("BLOCKLIST_APP", "") ?: ""
        if (raw != appListRaw) { appListRaw = raw; appList = raw.split(",").filter { it.isNotEmpty() } }
        return appList
    }

    private fun blockedWebs(): List<String> {
        val raw = prefs.getString("BLOCKLIST_WEB", "") ?: ""
        if (raw != webListRaw) { webListRaw = raw; webList = raw.split(",").filter { it.isNotEmpty() } }
        return webList
    }

    // Everything the text rules read. The same screen gives the same answer, so a screen that was
    // allowed and hasn't changed since is allowed again without matching every word against it
    private data class TextRulesInput(val pkg: String, val url: String?, val images: Int, val webList: String?, val text: String)
    private var lastAllowedText: TextRulesInput? = null

    private fun getRedirect(triggerWord: String): String? {
        val redirects = prefs.getString("REDIRECTS", "") ?: ""
        for (r in redirects.split(",")) {
            val parts = r.split("|")
            if (parts.size >= 2) {
                val trigger = parts[0].trim()
                val dest = parts[1].trim()
                if (triggerWord.trim().equals(trigger, ignoreCase = true) || triggerWord.trim().contains(trigger, ignoreCase = true)) {
                    return dest
                }
            }
        }
        return null
    }

    private var lastPackage: String = ""
    private var lastKnownUrl: String? = null

    fun evaluate(packageName: String, className: String, rootNode: AccessibilityNodeInfo?, isAdminActive: () -> Boolean): ShieldAction {
        val lowerPkg = packageName.lowercase()
        val lowerClass = className.lowercase()
        
        val rootPkg = rootNode?.packageName?.toString()?.lowercase() ?: ""
        if (lowerPkg.contains("com.rockhard.blocker") || lowerPkg.contains("com.rockhard") ||
            rootPkg.contains("com.rockhard.blocker") || rootPkg.contains("com.rockhard")) {
            return ShieldAction.Allow
        }

        // Android's permission prompt while the app is asking for one (LocationEngine.requestPermission).
        // Only the prompt's own package: Settings stays locked
        if ((lowerPkg.contains("permissioncontroller") || lowerPkg.contains("packageinstaller")) &&
            System.currentTimeMillis() < prefs.getLong("ALLOW_PERMISSION_PROMPT_UNTIL", 0L)) {
            return ShieldAction.Allow
        }

        val isHomeLauncher = homeLauncherWords.any { lowerPkg.contains(it) || lowerClass.contains(it) } || 
                             lowerPkg.contains("home") || 
                             (lowerClass.contains("home") && (lowerPkg.contains("launcher") || lowerPkg.contains("home") || lowerPkg.contains("systemui")))
        if (isHomeLauncher) return ShieldAction.Allow

        // PERMANENT SAFE HAVENS: Productivity, Utilities, Banks, Navigation, and Security
    val allowNotesAtNight =
        prefs.getBoolean("NIGHTFALL_ALLOW_NOTES", true)

    if (permanentSafeHavens.any { lowerPkg.contains(it) }) {
        return ShieldAction.Allow
    }

    // Check Nightfall timing early for Notes logic.
    val isNightfallActive = isNightfall(prefs)

    if (notesApps.any { lowerPkg.contains(it) }) {
        if (!isNightfallActive || allowNotesAtNight) {
            return ShieldAction.Allow
        }
    }

        val isGodModeActive = System.currentTimeMillis() < prefs.getLong("ALLOW_SETTINGS_UNTIL", 0L)

        val isBrowserApp = lowerPkg.contains("chrome") || lowerPkg.contains("firefox") || lowerPkg.contains("browser") ||
                           lowerPkg.contains("edge") || lowerPkg.contains("opera") || lowerPkg.contains("duckduckgo") ||
                           lowerPkg.contains("brave") || lowerPkg.contains("samsung.internet")

        if (lowerPkg != lastPackage) {
            lastPackage = lowerPkg
            lastKnownUrl = null
            GuardianService.addLog("📱 Focused App: " + lowerPkg + " (" + className + ")")
        }

        // One walk of the screen gives its text, image count and (in browsers) the address bar. It's only
        // done when a rule below needs it, because every walk calls into the app on screen
        val page by lazy(LazyThreadSafetyMode.NONE) { ScannerUtils.scanPage(rootNode, findUrlBar = isBrowserApp) }

        if (isBrowserApp && rootNode != null) {
            val currentUrl = page.urlBarText?.lowercase()
                        if (currentUrl != null && currentUrl.isNotBlank() && currentUrl != lastKnownUrl) {
                            lastKnownUrl = currentUrl
                            GuardianService.addLog("🎯 Browser navigated to: " + currentUrl)
                        }
        }
        val urlBarText = lastKnownUrl

        val lowerAllText by lazy(LazyThreadSafetyMode.NONE) { page.text }

        if (isBrowserApp && urlBarText != null) {
            val cleanUrl = urlBarText.trim()
            val hasExplicit = explicitUrlWords.any { cleanUrl.contains(it) } || 
                              cleanUrl.split(nonAlphanumeric).contains("sex")
            
            if (hasExplicit) {
                GuardianService.addLog("Explicit block: matched query '" + cleanUrl + "'")
                return ShieldAction.Block("Explicit Input/Query: " + cleanUrl, true)
            }
        }


        if (!isGodModeActive) {
            if (isNightfallActive) {
                val allowedCallsOnly = listOf("dialer", "contacts", "telecom", "android.phone", "keyboard", "inputmethod", "incallui", "systemui", "swiftkey", "honeyboard", "gboard", "touchpal", "sogou", "baidu")
                if (!allowedCallsOnly.any { lowerPkg.contains(it) || lowerClass.contains(it) }) {
                    return ShieldAction.Block("Nightfall Mode: Restricted App!", false)
                }
            }
            else if (prefs.getBoolean("DRASTIC_CALLS_ONLY", false)) {
                val allowedCallsOnly = listOf("dialer", "contacts", "telecom", "messages", "mms", "android.phone", "keyboard", "inputmethod", "incallui", "systemui", "permission")
                if (!allowedCallsOnly.any { lowerPkg.contains(it) || lowerClass.contains(it) }) return ShieldAction.Block("Nuclear Option: Calls and Texts ONLY", false)
            }
            else if (prefs.getBoolean("DRASTIC_DUMB_PHONE_NO_CAMERA", false)) {
                val allowedDumbPhone = listOf("dialer", "contacts", "telecom", "messages", "mms", "android.phone", "keyboard", "inputmethod", "incallui", "calculator", "calendar", "clock", "alarm", "weather", "maps", "navigation", "deskclock", "systemui", "permission")
                if (!allowedDumbPhone.any { lowerPkg.contains(it) || lowerClass.contains(it) }) return ShieldAction.Block("Dumb Phone (No Camera) Active", false)
            }
            else if (prefs.getBoolean("DRASTIC_DUMB_PHONE_CAMERA", false)) {
                val allowedDumbPhone = listOf("dialer", "contacts", "telecom", "messages", "mms", "android.phone", "keyboard", "inputmethod", "incallui", "calculator", "calendar", "clock", "alarm", "camera", "gallery", "photo", "cam", "lens", "imaging", "weather", "maps", "navigation", "deskclock", "systemui", "permission")
                if (!allowedDumbPhone.any { lowerPkg.contains(it) || lowerClass.contains(it) }) return ShieldAction.Block("Dumb Phone (With Camera) Active", false)
            }
            else if (prefs.getBoolean("DRASTIC_NO_INTERNET", false)) {
                val internetPkgs = listOf("chrome", "firefox", "browser", "duckduckgo", "edge", "opera", "brave", "samsung.internet", "vending", "play.store", "galaxy.store", "appmarket", "market", "youtube", "netflix", "tiktok", "instagram", "facebook", "twitter", "reddit", "snapchat")
                if (internetPkgs.any { lowerPkg.contains(it) || lowerClass.contains(it) }) return ShieldAction.Block("No Internet Mode Active", false)
            }
            else if (prefs.getBoolean("DRASTIC_NO_VIDEOS", false)) {
                val videoPkgs = listOf("youtube", "netflix", "hulu", "twitch", "primevideo", "disney", "max", "crunchyroll", "mxtech.videoplayer")
                if (videoPkgs.any { lowerPkg.contains(it) || lowerClass.contains(it) }) return ShieldAction.Block("No Videos Mode: Video App Blocked", false)

                val fullText = lowerAllText
                val isGab = lowerPkg.contains("gab") || (urlBarText != null && urlBarText.contains("gab")) || fullText.contains("gab.com") || fullText.contains("gab social")
                
                if (!isGab) {
                    if (lowerPkg.contains("tencent.mm") && lowerClass.contains("finder")) {
                        return ShieldAction.Block("No Videos Mode: WeChat Video Feed Detected", false)
                    }

                    if (lowerPkg.contains("tencent.mm")) {
                        val hasEng = fullText.contains("follow") && fullText.contains("friends") && fullText.contains("hot")
                        val hasChi = fullText.contains("关注") && fullText.contains("朋友") && fullText.contains("推荐")
                        if (hasEng || hasChi) return ShieldAction.Block("No Videos Mode: WeChat Video Text Detected", false)
                    }

                    fun hasVideoControls(node: AccessibilityNodeInfo?): Boolean {
                        if (node == null) return false
                        val nClass = node.className?.toString()?.lowercase() ?: ""
                        val nText = (node.contentDescription ?: node.text)?.toString()?.lowercase() ?: ""
                        val resName = node.viewIdResourceName?.lowercase() ?: ""
                        
                        if (nClass.contains("videoview") || nClass.contains("playerview") || nClass.contains("pictureinpicture") || nClass.contains("exoplayer")) return true
                        
                        // Softened string-containment matches for reliable detection of custom players
                        if (nText.contains("fullscreen") || nText.contains("full screen") || 
                            nText.contains("play video") || nText.contains("pause video") || 
                            nText.contains("youtube video player") || nText.contains("play/pause")) return true
                        
                        if (resName.contains("finder") || nClass.contains("finder")) return true
                        if (nText == "channels" || nText == "视频号") return true
                        
                        for (i in 0 until node.childCount) {
                            val child = node.getChild(i) ?: continue
                            if (hasVideoControls(child)) {
                                child.recycle()
                                return true
                            }
                            child.recycle()
                        }
                        return false
                    }
                    
                    if (hasVideoControls(rootNode)) return ShieldAction.Block("No Videos Mode: Video Player Detected", false)
                }
            }
        }

        val isStockSettings = lowerPkg.contains("settings")
        val isVendorManager = antiTamperPkgs.any { lowerPkg.contains(it) } && !isStockSettings

        val isDangerousSettingsScreen = isStockSettings && (
            lowerClass.contains("accessibility") ||
            lowerClass.contains("appinfo") ||
            lowerClass.contains("applications") ||
            lowerClass.contains("manageapplications") ||
            lowerClass.contains("installedapps") ||
            lowerClass.contains("applist") ||
            lowerClass.contains("appdetails") ||
            lowerClass.contains("deviceadmin") ||
            lowerClass.contains("admin") ||
            lowerClass.contains("device_admin") ||
            lowerClass.contains("uninstaller") ||
            lowerClass.contains("appmanager")
        )

        if (((isStockSettings && isDangerousSettingsScreen) || isVendorManager) && !isGodModeActive) {
            return ShieldAction.Block("Anti-Tamper: System Settings Locked!", true)
        }

        if (rootNode == null) return ShieldAction.Allow

        if (Config.UNINSTALL_PROTECTION_ENABLED && isGodModeActive) {
            val tamperAction = checkAntiTamperNative(packageName, className, rootNode, isAdminActive())
            if (tamperAction is ShieldAction.Block) return tamperAction
        }

        val triggeredAppEntry = blockedApps().firstOrNull { blockEntry ->
            val parts = blockEntry.split("|")
            val targetPkg = parts.getOrNull(0)?.trim()?.lowercase() ?: ""
            val displayName = parts.getOrNull(3)?.trim()?.lowercase() ?: targetPkg
            
            if (targetPkg.isNotEmpty() && lowerPkg.contains(targetPkg)) {
                true
            } else {
                val actualBlockPkg = popularAppPackageMap[displayName] ?: displayName
                lowerPkg.contains(actualBlockPkg) || lowerPkg.contains(targetPkg)
            }
        }

        if (triggeredAppEntry != null) {
            val appDisplayWord = triggeredAppEntry.split("|")[0].trim()
            val isFirstTime = !prefs.getBoolean("FIRST_OVERCOME_APP_$appDisplayWord", false)
            return if (isFirstTime) ShieldAction.RewardApp(appDisplayWord) else {
                GuardianService.addLog("App block: matched package '" + packageName + "'")
                ShieldAction.Block("App Overcome: " + appDisplayWord, true, getRedirect(appDisplayWord))
            }
        }

        if (!isBrowserApp && (safePackages.any { lowerPkg.contains(it) } || (protectedPkgs.any { lowerPkg.contains(it) } && !lowerPkg.contains(appName.lowercase()) && !lowerPkg.contains("miui") && !lowerPkg.contains("coloros") && !lowerPkg.contains("huawei")))) {
            return ShieldAction.Allow
        }

        val blockedWebs = blockedWebs()
        val textInput = TextRulesInput(lowerPkg, urlBarText, page.imageCount, webListRaw, lowerAllText)
        if (textInput == lastAllowedText) return ShieldAction.Allow

        if (safeDomains.any { lowerAllText.contains(it) }) { lastAllowedText = textInput; return ShieldAction.Allow }

        // --- PRE-PROCESSING: SAFE PHRASES ---
        var sanitizedText = lowerAllText
        for (phrase in safePhrases) {
            sanitizedText = sanitizedText.replace(phrase, "***")
        }

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
        if (foundHard != null) return ShieldAction.Block("Content Guard: " + foundHard, true, getRedirect(foundHard))

        val isOnWhitelistedSite = urlBarText != null && searchEnginesAndWhitelist.any { urlBarText.contains(it) }

        val isSearchEngineUrl = urlBarText != null && isOnWhitelistedSite && 
                               (urlBarText.contains("/search") || urlBarText.contains("?q=") || urlBarText.contains("&q=") || 
                                !urlBarText.contains(".") || urlBarText.contains(" "))

        for (entry in blockedWebs) {
            val parts = entry.split("|")
            val targetDomain = parts.getOrNull(0)?.lowercase()?.trim() ?: continue
            val displayWord = parts.getOrNull(3)?.lowercase()?.trim() ?: targetDomain

            val baseWord = targetDomain
                .replace("www.", "")
                .replace(".com", "")
                .replace(".org", "")
                .replace(".net", "")
                .replace(".tv", "")

            if (lowerAllText.contains(baseWord) || lowerAllText.contains(displayWord) || lowerAllText.contains(targetDomain)) {
                var ctx: String? = null

                if (isBrowserApp) {
                    if (urlBarText != null && !isSearchEngineUrl) {
                        if (urlBarText.contains(targetDomain) || urlBarText.contains(baseWord + ".com") || urlBarText.contains("m." + baseWord)) {
                            ctx = "URL Bar: " + urlBarText
                        }
                    } 
                    
                    if (ctx == null && !isOnWhitelistedSite) {
                        val variants = listOf(baseWord + ".com", "m." + baseWord + ".com", baseWord + ".org", "www." + baseWord + ".com", "youtu.be", baseWord + ".net", baseWord + ".tv")
                        if (variants.any { lowerAllText.contains(it) }) {
                            ctx = "Browser Match: " + baseWord
                        }
                    }
                } else {
                    ctx = ScannerUtils.extractDangerousContext(rootNode, baseWord)
                }

            if (ctx != null) {
                val isFirstTime =
                    !prefs.getBoolean(
                        "FIRST_OVERCOME_WEB_$displayWord",
                        false
                    )

                return if (isFirstTime) {
                    ShieldAction.RewardWeb(
                        displayWord,
                        ctx
                    )
                } else {
                    GuardianService.addLog(
                        "Web block: matched '$displayWord' context '$ctx'"
                    )

                    ShieldAction.Block(
                        "Hyperlink Overcome: " + ctx,
                        true,
                        getRedirect(displayWord)
                    )
                }
            }
        }
    }

    val imageCount = page.imageCount
        val softThreshold = if (imageCount >= 8) 2 else 4

        var softCount = 0
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
        if (softCount >= softThreshold) {
            GuardianService.addLog("Web block: matched soft words limit (" + softCount + "/" + softThreshold + ") with " + imageCount + " images on page")
            return ShieldAction.Block("Content Guard: " + caughtWords.distinct().joinToString(" & ") + " (detected " + softCount + " times)", true)
        }

        lastAllowedText = textInput
        return ShieldAction.Allow
    }

    private fun checkAntiTamperNative(packageName: String, className: String, rootNode: AccessibilityNodeInfo, isAdminActive: Boolean): ShieldAction {
        val lowerPkg = packageName.lowercase()
        val lowerClass = className.lowercase()

        // Highly inclusive Device Admin and Accessibility setup screen bypasses (permitted during active God Mode/Setup passes)
        val isDeviceAdminScreen = lowerClass.contains("deviceadmin") || 
                                  lowerClass.contains("device_admin") || 
                                  lowerClass.contains("adminadd") || 
                                  lowerClass.contains("admin_add") || 
                                  (lowerClass.contains("admin") && (lowerClass.contains("add") || lowerClass.contains("active")))

        val isAccessibilityScreen = lowerClass.contains("accessibility")

        if (isAccessibilityScreen) {
            return ShieldAction.Allow
        }

        if (isDeviceAdminScreen && !isAdminActive) {
            return ShieldAction.Allow
        }
        
        fun hasText(text: String): Boolean {
            val nodes = rootNode.findAccessibilityNodeInfosByText(text)
            val exists = nodes != null && nodes.isNotEmpty()
            nodes?.forEach { it.recycle() }
            return exists
        }

        val hasAppTarget = hasText(appName) || hasText("Momentum") || hasText("Sync Services")

        // Only block generic managers if we are explicitly on an App Details or Info page.
        // This prevents blocking list navigation screens like Accessibility Settings (Step 1) and Autostart / Permission center (Step 4).
        val isAppDetailsScreen = lowerClass.contains("appinfo") || 
                                 lowerClass.contains("appdetails") || 
                                 lowerClass.contains("applicationsdetails") || 
                                 lowerClass.contains("installedappdetails") || 
                                 lowerClass.contains("uninstall") || 
                                 lowerClass.contains("details")
        if (isAppDetailsScreen && hasAppTarget) return ShieldAction.Block("Anti-Tamper: Generic App Manager Blocked!", true)

        val hasDangerousWords = hasText("Uninstall") || hasText("Force stop") || hasText("Clear data") || hasText("Deactivate") || hasText("Desinstalar") || hasText("卸载")
        if (hasAppTarget && hasDangerousWords) return ShieldAction.Block("Anti-Tamper: Universal App Info Blocked!", true)

        if (isAdminActive && (lowerPkg.contains("settings") || lowerClass.contains("deviceadmin") || lowerClass.contains("admin"))) {
            val isDeviceAdmin = hasText("Device admin") || hasText("admin apps") || hasText("Administradores")
            if (isDeviceAdmin && hasAppTarget) return ShieldAction.Block("Anti-Tamper: Device Admin Access Blocked!", true)
        }

        return ShieldAction.Allow
    }
}
