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

    private fun getRedirect(triggerWord: String): String? {
        val redirects = prefs.getString("REDIRECTS", "") ?: ""
        for (r in redirects.split(",")) {
            val parts = r.split("|")
            if (parts.size == 2 && triggerWord.contains(parts[0], ignoreCase = true)) {
                return parts[1]
            }
        }
        return null
    }

    private var lastPackage: String = ""
    private var lastKnownUrl: String? = null

    fun evaluate(packageName: String, className: String, rootNode: AccessibilityNodeInfo?, isAdminActive: Boolean): ShieldAction {
        val lowerPkg = packageName.lowercase()
        val lowerClass = className.lowercase()
        
        val rootPkg = rootNode?.packageName?.toString()?.lowercase() ?: ""
        if (lowerPkg.contains("com.rockhard.blocker") || lowerPkg.contains("com.rockhard") ||
            rootPkg.contains("com.rockhard.blocker") || rootPkg.contains("com.rockhard")) {
            return ShieldAction.Allow
        }
        
        val isHomeLauncher = listOf("launcher", "trebuchet", "quickstep").any { lowerPkg.contains(it) || lowerClass.contains(it) } || 
                             lowerPkg.contains("home") || 
                             (lowerClass.contains("home") && (lowerPkg.contains("launcher") || lowerPkg.contains("home") || lowerPkg.contains("systemui")))
        if (isHomeLauncher) return ShieldAction.Allow

        // PERMANENT SAFE HAVENS: Productivity, Utilities, Banks, Navigation, and Security
        val permanentSafeHavens = listOf(
            // Notes & Tasks
            "notion", "evernote", "simplenote", "zoho", "onenote", "microsoft.notes", 
            "keep", "docs.google", "notes", "notepad", "journal", "journey", "dayone", 
            "obsidian", "standardnotes", "logseq", "upnote", "joplin", "bear", 
            "craft", "notability", "goodnotes", "todoist", "ticktick", "anydo", "tasks",
            
            // Utilities (Clock, Calculator, Recorder)
            "calculator", "calc", "clock", "alarm", "timer", "stopwatch", "deskclock",
            "recorder", "voicememo", "soundrecorder", "audiorecorder",
            
            // Finance, Banking & Wallets
            "bank", "finance", "banking", "paypal", "venmo", "cashapp", "monzo", "revolut", 
            "chase", "wellsfargo", "citi", "amex", "discover", "barclays", "santander", 
            "hsbc", "capitalone", "pay", "wallet", "stripe", "square",
            
            // Navigation & Rideshare (Crucial for Physical Safety)
            "maps", "navigation", "waze", "uber", "lyft", "bolt", "grab", "transit",
            
            // Security, 2FA, & Password Managers
            "authenticator", "2fa", "authy", "duosecurity", "okta", "bitwarden", "1password", "lastpass", "dashlane"
        )
        if (permanentSafeHavens.any { lowerPkg.contains(it) }) return ShieldAction.Allow

        val isGodModeActive = System.currentTimeMillis() < prefs.getLong("ALLOW_SETTINGS_UNTIL", 0L)

        val isBrowserApp = lowerPkg.contains("chrome") || lowerPkg.contains("firefox") || lowerPkg.contains("browser") ||
                           lowerPkg.contains("edge") || lowerPkg.contains("opera") || lowerPkg.contains("duckduckgo") ||
                           lowerPkg.contains("brave") || lowerPkg.contains("samsung.internet")

        if (lowerPkg != lastPackage) {
            lastPackage = lowerPkg
            lastKnownUrl = null
            GuardianService.addLog("📱 Focused App: " + lowerPkg + " (" + className + ")")
        }

        if (isBrowserApp && rootNode != null) {
            val currentUrl = ScannerUtils.extractUrlBarText(rootNode)?.lowercase()
                        if (currentUrl != null && currentUrl.isNotBlank() && currentUrl != lastKnownUrl) {
                            lastKnownUrl = currentUrl
                            GuardianService.addLog("🎯 Browser navigated to: " + currentUrl)
                        }
        }
        val urlBarText = lastKnownUrl

                // --- EXTRACT PAGE TEXT EARLY FOR MULTI-LAYERED SCANNING ---
                var lazyAllText: String? = null
                fun getAllText(): String {
                    if (rootNode == null) return ""
                    if (lazyAllText == null) lazyAllText = ScannerUtils.extractAllText(rootNode)
                    return lazyAllText!!
                }
                val lowerAllText = getAllText()

                // Detect simplified Query-in-Omnibox (where Chrome hides the search URL and only shows query keywords)
                val isQueryInOmnibox = urlBarText != null && !urlBarText.contains(".") && !urlBarText.contains("http")

                // Google Search and Google Images layout identification checks
                val isGoogleImages = (urlBarText != null && urlBarText.contains("google.") && urlBarText.contains("tbm=isch")) ||
                                     (isBrowserApp && isQueryInOmnibox && lowerAllText.contains("google") && (lowerAllText.contains("images") || lowerAllText.contains("图片")))

                val isGoogleSearch = (urlBarText != null && urlBarText.contains("google.") && urlBarText.contains("/search")) ||
                                     (isBrowserApp && isQueryInOmnibox && lowerAllText.contains("google") && !lowerAllText.contains("images") && !lowerAllText.contains("图片"))

                val isBingImages = (urlBarText != null && urlBarText.contains("bing.com") && urlBarText.contains("/images"))
                val isBingSearch = (urlBarText != null && urlBarText.contains("bing.com") && urlBarText.contains("/search"))

                val isDuckImages = (urlBarText != null && urlBarText.contains("duckduckgo.com") && (urlBarText.contains("ia=images") || urlBarText.contains("iax=images")))
                val isDuckSearch = (urlBarText != null && urlBarText.contains("duckduckgo.com") && (urlBarText.contains("?q=") || urlBarText.contains("&q=")))

        if (isBrowserApp && urlBarText != null) {
            val cleanUrl = urlBarText.trim()
            val explicitWords = listOf("sexy", "porn", "nude", "naked", "nsfw", "porno", "onlyfans", "xvideo", "pornhub", "rule34", "erotic")
            val hasExplicit = explicitWords.any { cleanUrl.contains(it) } || 
                              cleanUrl.split(Regex("[^a-zA-Z0-9]")).contains("sex")
            
            if (hasExplicit) {
                GuardianService.addLog("Explicit block: matched query '" + cleanUrl + "'")
                return ShieldAction.Block("Explicit Input/Query: " + cleanUrl, true)
            }
        }


        if (!isGodModeActive) {
            val nfStart = prefs.getInt("NIGHTFALL_START", -1)
            val nfEnd = prefs.getInt("NIGHTFALL_END", -1)
            var isNightfall = false
            if (nfStart != -1 && nfEnd != -1 && nfStart != nfEnd) {
                val cal = Calendar.getInstance()
                val currentMins = (cal.get(Calendar.HOUR_OF_DAY) * 60) + cal.get(Calendar.MINUTE)
                isNightfall = if (nfStart < nfEnd) { currentMins in nfStart..nfEnd } else { currentMins >= nfStart || currentMins <= nfEnd }
            }

            if (isNightfall) {
                val allowedCallsOnly = listOf("dialer", "contacts", "telecom", "android.phone", "keyboard", "inputmethod", "incallui", "systemui")
                if (!allowedCallsOnly.any { lowerPkg.contains(it) || lowerClass.contains(it) }) {
                    return ShieldAction.Block("Nightfall Mode: Only Calls Allowed!", false)
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

                val fullText = getAllText()
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
            val tamperAction = checkAntiTamperNative(packageName, className, rootNode, isAdminActive)
            if (tamperAction is ShieldAction.Block) return tamperAction
        }

        val blockedApps = prefs.getString("BLOCKLIST_APP", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        val triggeredAppEntry = blockedApps.firstOrNull { blockEntry ->
            val blockTarget = blockEntry.split("|")[0].lowercase()
            val actualBlockPkg = popularAppPackageMap[blockTarget] ?: blockTarget
            lowerPkg.contains(actualBlockPkg)
        }

        if (triggeredAppEntry != null) {
            val appDisplayWord = triggeredAppEntry.split("|")[0]
            val isFirstTime = !prefs.getBoolean("FIRST_OVERCOME_APP_$appDisplayWord", false)
            return if (isFirstTime) ShieldAction.RewardApp(appDisplayWord) else {
                GuardianService.addLog("App block: matched package '" + packageName + "'")
                ShieldAction.Block("App Overcome: " + appDisplayWord, true, getRedirect(appDisplayWord))
            }
        }

        if (!isBrowserApp && (safePackages.any { lowerPkg.contains(it) } || (protectedPkgs.any { lowerPkg.contains(it) } && !lowerPkg.contains(appName.lowercase()) && !lowerPkg.contains("miui") && !lowerPkg.contains("coloros") && !lowerPkg.contains("huawei")))) {
            return ShieldAction.Allow
        }

        // Already declared early in evaluate()

        if (safeDomains.any { lowerAllText.contains(it) }) return ShieldAction.Allow

        val wholeWordRequired = listOf("ass", "butt", "strip", "sex", "tits", "fap", "milf", "anal")
        val foundHard = hardWords.firstOrNull { word ->
            var index = 0
            var found = false
            while (true) {
                index = lowerAllText.indexOf(word, index)
                if (index == -1) break
                
                val isMatch = if (wholeWordRequired.contains(word)) {
                    val beforeChar = if (index > 0) lowerAllText[index - 1] else ' '
                    val afterChar = if (index + word.length < lowerAllText.length) lowerAllText[index + word.length] else ' '
                    !beforeChar.isLetterOrDigit() && !afterChar.isLetterOrDigit()
                } else {
                    true
                }
                if (isMatch) {
                    found = true
                    break
                }
                index += word.length
            }
            found
        }
        if (foundHard != null) return ShieldAction.Block("Content Guard: " + foundHard, true, getRedirect(foundHard))

        val searchEnginesAndWhitelist = listOf("google.", "bing.com", "duckduckgo", "yahoo.com", "gab.com", "search.brave", "ecosia.org", "qwant.com")
        
        val isOnWhitelistedSite = urlBarText != null && searchEnginesAndWhitelist.any { urlBarText.contains(it) }

        val isSearchEngineUrl = urlBarText != null && isOnWhitelistedSite && 
                               (urlBarText.contains("/search") || urlBarText.contains("?q=") || urlBarText.contains("&q=") || 
                                !urlBarText.contains(".") || urlBarText.contains(" "))

        val blockedWebs = prefs.getString("BLOCKLIST_WEB", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        for (entry in blockedWebs) {
            val rawWord = entry.split("|")[0].lowercase()
            val baseWord = rawWord.replace("www.", "").replace(".com", "").replace(".org", "").replace(".net", "")
            
            if (lowerAllText.contains(baseWord)) {
                var ctx: String? = null
                if (isBrowserApp) {
                    if (urlBarText != null && !isSearchEngineUrl && urlBarText.contains(rawWord)) {
                        ctx = "URL Bar: " + urlBarText
                    } else if (!isOnWhitelistedSite && listOf(baseWord + ".com", "m." + baseWord + ".com", baseWord + ".org", "www." + baseWord + ".com", "youtu.be", baseWord + ".net").any { lowerAllText.contains(it) }) {
                        ctx = "Browser Match: " + baseWord
                    }
                } else {
                    ctx = ScannerUtils.extractDangerousContext(rootNode, rawWord)
                }
                if (ctx != null) {
                    val isFirstTime = !prefs.getBoolean("FIRST_OVERCOME_WEB_$rawWord", false)
                    return if (isFirstTime) ShieldAction.RewardWeb(rawWord, ctx) else {
                        GuardianService.addLog("Web block: matched '" + rawWord + "' context '" + ctx + "'")
                        ShieldAction.Block("Hyperlink Overcome: " + ctx, true, getRedirect(rawWord))
                    }
                }
            }
        }

        val imageCount = if (rootNode != null) ScannerUtils.countImages(rootNode) else 0
        val softThreshold = if (imageCount >= 8) 2 else 4

        var softCount = 0
        val caughtWords = mutableListOf<String>()
        for (word in softWords) {
            var index = 0
            while (true) {
                index = lowerAllText.indexOf(word, index)
                if (index == -1) break
                
                val isMatch = if (wholeWordRequired.contains(word)) {
                    val beforeChar = if (index > 0) lowerAllText[index - 1] else ' '
                    val afterChar = if (index + word.length < lowerAllText.length) lowerAllText[index + word.length] else ' '
                    !beforeChar.isLetterOrDigit() && !afterChar.isLetterOrDigit()
                } else {
                    true
                }
                
                if (isMatch) {
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

        val hasAppTarget = hasText(appName) || hasText("RHC") || hasText("Sync Services")

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
