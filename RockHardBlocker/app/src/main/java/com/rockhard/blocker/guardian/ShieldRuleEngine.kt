package com.rockhard.blocker

import android.content.SharedPreferences
import android.view.accessibility.AccessibilityNodeInfo
import java.util.*

sealed class ShieldAction {
    object Allow : ShieldAction()
    data class Block(val reason: String, val canDefend: Boolean) : ShieldAction()
    data class RewardApp(val triggerWord: String) : ShieldAction()
    data class RewardWeb(val triggerWord: String, val context: String) : ShieldAction()
    data class WeatherBuff(val element: String) : ShieldAction()
}

class ShieldRuleEngine(private val prefs: SharedPreferences, private val appName: String) {

    private val safeDomains = listOf("aistudio", "github", "codespaces")
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
    
    // FIX: Removed "sec" because it was catching "org.thoughtcrime.securesms" (Signal) and "com.sec.android.app.launcher" (Samsung Home)
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

    fun evaluate(packageName: String, className: String, rootNode: AccessibilityNodeInfo?, isAdminActive: Boolean): ShieldAction {
        val lowerPkg = packageName.lowercase()
        val lowerClass = className.lowercase()
        
        // 1. Core System Whitelist (Always allow the launcher & our app so the phone isn't hard-bricked)
        val isHomeLauncher = listOf("launcher", "home", "trebuchet", "quickstep").any { lowerPkg.contains(it) || lowerClass.contains(it) }
        if (isHomeLauncher) return ShieldAction.Allow
        if (lowerPkg.contains("com.rockhard.blocker") || (protectedPkgs.any { lowerPkg.contains(it) } && !lowerPkg.contains(appName.lowercase()) && !lowerPkg.contains("miui") && !lowerPkg.contains("coloros") && !lowerPkg.contains("huawei"))) {
            return ShieldAction.Allow
        }

        // 2. Nightfall Protocol
        val action = checkNightfall(lowerPkg)
        if (action is ShieldAction.Block) return action

        val isGodModeActive = System.currentTimeMillis() < prefs.getLong("ALLOW_SETTINGS_UNTIL", 0L)

        // 3. DRASTIC OVERCOMING MODES (Enforced ONLY if God Mode is OFF)
        if (!isGodModeActive) {
            // DRASTIC A: Calls and Texts ONLY
            if (prefs.getBoolean("DRASTIC_CALLS_ONLY", false)) {
                val allowedCallsOnly = listOf("dialer", "contacts", "telecom", "messages", "mms", "android.phone", "keyboard", "inputmethod", "incallui")
                if (!allowedCallsOnly.any { lowerPkg.contains(it) || lowerClass.contains(it) }) return ShieldAction.Block("Nuclear Option: Calls and Texts ONLY", false)
            }
            // DRASTIC B: Dumb Phone Mode
            else if (prefs.getBoolean("DRASTIC_DUMB_PHONE", false)) {
                val allowedDumbPhone = listOf("dialer", "contacts", "telecom", "messages", "mms", "android.phone", "keyboard", "inputmethod", "incallui", "calculator", "calendar", "clock", "alarm", "camera", "gallery", "weather", "maps", "navigation", "deskclock")
                if (!allowedDumbPhone.any { lowerPkg.contains(it) || lowerClass.contains(it) }) return ShieldAction.Block("Dumb Phone Mode Active", false)
            }
            // DRASTIC C: No Internet
            else if (prefs.getBoolean("DRASTIC_NO_INTERNET", false)) {
                val internetPkgs = listOf("chrome", "firefox", "browser", "duckduckgo", "edge", "opera", "brave", "samsung.internet", "vending", "play.store", "galaxy.store", "appmarket", "market", "youtube", "netflix", "tiktok", "instagram", "facebook", "twitter", "reddit", "snapchat")
                if (internetPkgs.any { lowerPkg.contains(it) || lowerClass.contains(it) }) return ShieldAction.Block("No Internet Mode Active", false)
            }
        }

        // 4. Anti-Tamper Core
        val isSettings = antiTamperPkgs.any { lowerPkg.contains(it) }
        if (isSettings && !isGodModeActive) return ShieldAction.Block("Anti-Tamper: System Settings Locked!", true)

        if (rootNode == null) return ShieldAction.Allow

        if (Config.UNINSTALL_PROTECTION_ENABLED && isGodModeActive) {
            val tamperAction = checkAntiTamperNative(packageName, className, rootNode, isAdminActive)
            if (tamperAction is ShieldAction.Block) return tamperAction
        }

        // 5. Standard User Blocklists
        val blockedApps = prefs.getString("BLOCKLIST_APP", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        val triggeredAppEntry = blockedApps.firstOrNull { blockEntry ->
            val blockTarget = blockEntry.split("|")[0].lowercase()
            val actualBlockPkg = popularAppPackageMap[blockTarget] ?: blockTarget
            lowerPkg.contains(actualBlockPkg)
        }

        if (triggeredAppEntry != null) {
            val appDisplayWord = triggeredAppEntry.split("|")[0]
            val isFirstTime = !prefs.getBoolean("FIRST_OVERCOME_APP_$appDisplayWord", false)
            return if (isFirstTime) ShieldAction.RewardApp(appDisplayWord) else ShieldAction.Block("App Overcome: $appDisplayWord", true)
        }

        var lazyAllText: String? = null
        fun getAllText(): String {
            if (lazyAllText == null) lazyAllText = ScannerUtils.extractAllText(rootNode)
            return lazyAllText!!
        }
        val lowerAllText = getAllText()

        // FIX: Hard Words checked BEFORE Safe Domains!
        val foundHard = hardWords.firstOrNull { lowerAllText.contains(it) }
        if (foundHard != null) return ShieldAction.Block("Content Guard: $foundHard", true)

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
                    return if (isFirstTime) ShieldAction.RewardWeb(rawWord, ctx) else ShieldAction.Block("Hyperlink Overcome: $ctx", true)
                }
            }
        }

        // FIX: Safe Domains checked after blocklists, but before soft words
        if (safeDomains.any { lowerAllText.contains(it) }) return ShieldAction.Allow

        val foundSoft = softWords.filter { lowerAllText.contains(it) }
        if (foundSoft.size >= 3) return ShieldAction.Block("Content Guard: ${foundSoft.joinToString(" & ")}", true)

        return ShieldAction.Allow
    }

    private fun checkNightfall(packageName: String): ShieldAction {
        val nfStart = prefs.getInt("NIGHTFALL_START", -1)
        val nfEnd = prefs.getInt("NIGHTFALL_END", -1)
        if (nfStart != -1 && nfEnd != -1) {
            val cal = Calendar.getInstance()
            val currentMins = (cal.get(Calendar.HOUR_OF_DAY) * 60) + cal.get(Calendar.MINUTE)
            val isNightfall = if (nfStart < nfEnd) { currentMins in nfStart..nfEnd } else { currentMins >= nfStart || currentMins <= nfEnd }
            if (isNightfall) {
                val sleepAllowed = listOf("systemui", "launcher", "nexus", "pixel", "clock", "alarm", "dialer", "contacts", "com.android.phone", "com.google.android.dialer")
                if (!sleepAllowed.any { packageName.contains(it, ignoreCase = true) }) {
                    return ShieldAction.Block("Nightfall Protocol Active. Go to sleep.", false)
                }
            }
        }
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
