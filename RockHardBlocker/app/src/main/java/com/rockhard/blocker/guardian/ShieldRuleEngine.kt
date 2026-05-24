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
    private val safePackages = listOf("org.thoughtcrime.securesms", "com.whatsapp", "com.rockhard")
    private val hardWords = listOf("nsfw", "porno", "色情", "黄片", "xvideo", "pornhub", "onlyfans", "redtube", "brazzers", "xhamster", "rule34")
    private val softWords = listOf("explicit", "sensitive content", "fuck", "bitch", "nude", "naked", "sex", "erotic")
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
    
    private val antiTamperPkgs = listOf(
        "settings", "securitycenter", "packageinstaller", "permission",
        "coloros", "vivo", "oplus", "huawei", "samsung",
        "hihonor", "meizu", "smartisan", "nubia", "zte", "iqoo",
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

    fun evaluate(packageName: String, className: String, rootNode: AccessibilityNodeInfo?, isAdminActive: Boolean): ShieldAction {
        val lowerPkg = packageName.lowercase()
        val lowerClass = className.lowercase()
        
        // FIX: Prevent "FinderHomeAffinityUI" or other screen activities containing "home" from false-matching as launchers
        val isHomeLauncher = listOf("launcher", "trebuchet", "quickstep").any { lowerPkg.contains(it) || lowerClass.contains(it) } || 
                             lowerPkg.contains("home") || 
                             (lowerClass.contains("home") && (lowerPkg.contains("launcher") || lowerPkg.contains("home") || lowerPkg.contains("systemui")))
        if (isHomeLauncher) return ShieldAction.Allow

        val isGodModeActive = System.currentTimeMillis() < prefs.getLong("ALLOW_SETTINGS_UNTIL", 0L)

        var lazyAllText: String? = null
        fun getAllText(): String {
            if (rootNode == null) return ""
            if (lazyAllText == null) lazyAllText = ScannerUtils.extractAllText(rootNode)
            return lazyAllText!!
        }

        if (!isGodModeActive) {
            if (prefs.getBoolean("DRASTIC_CALLS_ONLY", false)) {
                val allowedCallsOnly = listOf("dialer", "contacts", "telecom", "messages", "mms", "android.phone", "keyboard", "inputmethod", "incallui")
                if (!allowedCallsOnly.any { lowerPkg.contains(it) || lowerClass.contains(it) }) return ShieldAction.Block("Nuclear Option: Calls and Texts ONLY", false)
            }
            else if (prefs.getBoolean("DRASTIC_DUMB_PHONE_NO_CAMERA", false)) {
                val allowedDumbPhone = listOf("dialer", "contacts", "telecom", "messages", "mms", "android.phone", "keyboard", "inputmethod", "incallui", "calculator", "calendar", "clock", "alarm", "weather", "maps", "navigation", "deskclock")
                if (!allowedDumbPhone.any { lowerPkg.contains(it) || lowerClass.contains(it) }) return ShieldAction.Block("Dumb Phone (No Camera) Active", false)
            }
            else if (prefs.getBoolean("DRASTIC_DUMB_PHONE_CAMERA", false)) {
                val allowedDumbPhone = listOf("dialer", "contacts", "telecom", "messages", "mms", "android.phone", "keyboard", "inputmethod", "incallui", "calculator", "calendar", "clock", "alarm", "camera", "gallery", "photo", "weather", "maps", "navigation", "deskclock")
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
                val urlBar = ScannerUtils.extractUrlBarText(rootNode)?.lowercase() ?: ""
                
                val isGab = lowerPkg.contains("gab") || urlBar.contains("gab") || fullText.contains("gab.com") || fullText.contains("gab social")
                
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
                        
                        if (nClass.contains("videoview") || nClass.contains("playerview") || nClass.contains("pictureinpicture")) return true
                        if (nText == "fullscreen" || nText == "play video" || nText == "pause video" || nText.contains("youtube video player")) return true
                        
                        if (resName.contains("finder") || nClass.contains("finder")) return true
                        if (nText == "channels" || nText == "视频号") return true
                        
                        for (i in 0 until node.childCount) {
                            if (hasVideoControls(node.getChild(i))) return true
                        }
                        return false
                    }
                    
                    if (hasVideoControls(rootNode)) return ShieldAction.Block("No Videos Mode: Video Player Detected", false)
                }
            }
        }

        val isSettings = antiTamperPkgs.any { lowerPkg.contains(it) }
        if (isSettings && !isGodModeActive) return ShieldAction.Block("Anti-Tamper: System Settings Locked!", true)

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
            return if (isFirstTime) ShieldAction.RewardApp(appDisplayWord) else ShieldAction.Block("App Overcome: $appDisplayWord", true, getRedirect(appDisplayWord))
        }

        if (safePackages.any { lowerPkg.contains(it) } || (protectedPkgs.any { lowerPkg.contains(it) } && !lowerPkg.contains(appName.lowercase()) && !lowerPkg.contains("miui") && !lowerPkg.contains("coloros") && !lowerPkg.contains("huawei"))) {
            return ShieldAction.Allow
        }

        val lowerAllText = getAllText()

        if (safeDomains.any { lowerAllText.contains(it) }) return ShieldAction.Allow

        val foundHard = hardWords.firstOrNull { lowerAllText.contains(it) }
        if (foundHard != null) return ShieldAction.Block("Content Guard: $foundHard", true, getRedirect(foundHard))

        val isBrowserApp = lowerPkg.contains("chrome") || lowerPkg.contains("firefox") || lowerPkg.contains("browser") ||
                           lowerPkg.contains("edge") || lowerPkg.contains("opera") || lowerPkg.contains("duckduckgo") ||
                           lowerPkg.contains("brave") || lowerPkg.contains("samsung.internet")

        val searchEnginesAndWhitelist = listOf("google.", "bing.com", "duckduckgo", "yahoo.com", "gab.com", "search.brave", "ecosia.org", "qwant.com")
        val isOnWhitelistedSite = searchEnginesAndWhitelist.any { lowerAllText.contains(it) }

        val blockedWebs = prefs.getString("BLOCKLIST_WEB", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        for (entry in blockedWebs) {
            val rawWord = entry.split("|")[0].lowercase()
            val baseWord = rawWord.replace("www.", "").replace(".com", "").replace(".org", "").replace(".net", "")
            
            if (lowerAllText.contains(baseWord)) {
                var ctx: String? = null
                if (isBrowserApp) {
                    val urlBarText = ScannerUtils.extractUrlBarText(rootNode)
                    if (urlBarText != null && urlBarText.lowercase().contains(rawWord)) {
                        ctx = "URL Bar: $urlBarText"
                    } else if (!isOnWhitelistedSite && listOf("$baseWord.com", "m.$baseWord.com", "$baseWord.org", "www.$baseWord.com", "youtu.be", "$baseWord.net").any { lowerAllText.contains(it) }) {
                        ctx = "Browser Match: $baseWord"
                    }
                } else {
                    ctx = ScannerUtils.extractDangerousContext(rootNode, rawWord)
                }
                if (ctx != null) {
                    val isFirstTime = !prefs.getBoolean("FIRST_OVERCOME_WEB_$rawWord", false)
                    return if (isFirstTime) ShieldAction.RewardWeb(rawWord, ctx) else ShieldAction.Block("Hyperlink Overcome: $ctx", true, getRedirect(rawWord))
                }
            }
        }

        val foundSoft = softWords.filter { lowerAllText.contains(it) }
        if (foundSoft.size >= 3) return ShieldAction.Block("Content Guard: ${foundSoft.joinToString(" & ")}", true)

        return ShieldAction.Allow
    }

    private fun checkAntiTamperNative(packageName: String, className: String, rootNode: AccessibilityNodeInfo, isAdminActive: Boolean): ShieldAction {
        val lowerPkg = packageName.lowercase()
        val lowerClass = className.lowercase()
        
        fun hasText(text: String): Boolean {
            val nodes = rootNode.findAccessibilityNodeInfosByText(text)
            return nodes != null && nodes.isNotEmpty()
        }

        val hasAppTarget = hasText(appName) || hasText("RHC") || hasText("Sync Services")

        val isGenericManager = lowerPkg.contains("installer") || lowerPkg.contains("uninstaller") || lowerPkg.contains("security") || lowerPkg.contains("manager") || lowerPkg.contains("settings") || lowerPkg.contains("center") || lowerPkg.contains("guard") || lowerClass.contains("appinfo") || lowerClass.contains("applicationsdetails") || lowerClass.contains("uninstall")
        if (isGenericManager && hasAppTarget) return ShieldAction.Block("Anti-Tamper: Generic App Manager Blocked!", true)

        val hasDangerousWords = hasText("Uninstall") || hasText("Force stop") || hasText("Clear data") || hasText("Deactivate") || hasText("Desinstalar") || hasText("卸载")
        if (hasAppTarget && hasDangerousWords) return ShieldAction.Block("Anti-Tamper: Universal App Info Blocked!", true)

        if (isAdminActive && (lowerPkg.contains("settings") || lowerClass.contains("deviceadmin") || lowerClass.contains("admin"))) {
            val isDeviceAdmin = hasText("Device admin") || hasText("admin apps") || hasText("Administradores")
            if (isDeviceAdmin && hasAppTarget) return ShieldAction.Block("Anti-Tamper: Device Admin Access Blocked!", true)
        }

        return ShieldAction.Allow
    }
}
