package com.rockhard.blocker

import android.accessibilityservice.AccessibilityService
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date

class GuardianService : AccessibilityService() {
    companion object {
        var pauseUntil: Long = 0L
        var urgesDefeatedCount = 0
        val appTimeTrackers = mutableMapOf<String, Long>()
        val actionLogs = mutableListOf<String>()

        fun addLog(msg: String) {
            val time = SimpleDateFormat("HH:mm:ss").format(Date())
            actionLogs.add(0, "[$time] $msg")
            if (actionLogs.size > 50) actionLogs.removeLast()
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isOverlayShowing = false
    private var bossTimer: CountDownTimer? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        addLog("Service Connected.")
        Toast.makeText(this, "Guardian Activated!", Toast.LENGTH_LONG).show()

        Handler(Looper.getMainLooper()).postDelayed({
            performGlobalAction(GLOBAL_ACTION_HOME)
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            try { startActivity(intent) } catch (e: Exception) { addLog("Auto-return failed.") }
        }, 500)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || System.currentTimeMillis() < pauseUntil) return
        val packageName = event.packageName?.toString() ?: ""
        val rootNode = rootInActiveWindow ?: return
        val appName = getString(R.string.app_name)

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val time = appTimeTrackers[packageName] ?: 0L
            appTimeTrackers[packageName] = time + 1000L
        }

        val safeDomains = listOf("aistudio", "github", "codespaces")
        for (w in safeDomains) { if (findTextInNode(rootNode, w)) return }

        val allScreenText = extractAllText(rootNode).lowercase()

        if (packageName.contains("chrome") || packageName.contains("firefox") || packageName.contains("browser")) {
            val prefs = getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE)
            val activePetIndex = prefs.getInt("ACTIVE_PET_INDEX", 0)
            val partyData = prefs.getString("PARTY_DATA", "") ?: ""
            if (partyData.isNotEmpty()) {
                val party = partyData.split(";")
                if (activePetIndex < party.size) {
                    val activePet = party[activePetIndex].split(",")
                    if (activePet.size >= 13) {
                        val petName = activePet[0]
                        val infusionEl = activePet[11]
                        if (infusionEl != "None") {
                            val targetWord = when (infusionEl) { "Clear" -> "sun"; "Cloudy" -> "cloud"; "Rain" -> "rain"; "Storm" -> "storm"; "Snow" -> "snow"; else -> "" }
                            if (targetWord.isNotEmpty() && isNodeDangerousContext(rootNode, targetWord)) {
                                val today = SimpleDateFormat("yyyyMMdd").format(Date())
                                val buffKey = "LAST_BUFF_${infusionEl}_$today"
                                if (!prefs.getBoolean(buffKey, false)) {
                                    prefs.edit().putBoolean(buffKey, true).apply()
                                    val newStacks = activePet[12].toInt() + 1
                                    val newPetData = "${activePet[0]},${activePet[1]},${activePet[2]},${activePet[3]},${activePet[4]},${activePet[5]},${activePet[6]},${activePet[7]},${activePet[8]},${activePet[9]},${activePet[10]},${activePet[11]},$newStacks,${activePet.getOrElse(13){"0"}}"
                                    val newPartyList = party.toMutableList()
                                    newPartyList[activePetIndex] = newPetData
                                    prefs.edit().putString("PARTY_DATA", newPartyList.joinToString(";")).apply()
                                    Handler(Looper.getMainLooper()).post { Toast.makeText(this, "🌟 $petName absorbed $infusionEl energy! (+1 Stack)", Toast.LENGTH_LONG).show() }
                                    addLog("Scavenged URL for $infusionEl.")
                                }
                            }
                        }
                    }
                }
            }
        }

        val prefs = getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE)
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val compName = ComponentName(this, AdminReceiver::class.java)

        val isAdminActive = dpm.isAdminActive(compName)
        val isDnsVerified = prefs.getBoolean("DNS_VERIFIED", false)
        val isGamificationEnabled = prefs.getBoolean("GAMIFICATION", true)

        val blockedWebs = prefs.getString("BLOCKLIST_WEB", "")?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() } ?: emptyList()
        val blockedApps = prefs.getString("BLOCKLIST_APP", "")?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() } ?: emptyList()

        val protectedPkgs = listOf("com.android.systemui", "launcher", "nexus", "pixel", "gallery", "camera", "dialer", "contacts", "note", "keyboard", "inputmethod", "swiftkey", "miui", "keyboard", "inputmethod", "swiftkey", "miui")
        if (protectedPkgs.any { packageName.contains(it, ignoreCase = true) }) return

        if (blockedApps.any { packageName.lowercase().contains(it) }) { 
            performGlobalAction(GLOBAL_ACTION_HOME)
            val rewardKey = "FIRST_OVERCOME_APP_$packageName"
            if (!prefs.getBoolean(rewardKey, false)) {
                prefs.edit().putBoolean(rewardKey, true).apply()
                val bName = GoodBeastEngine.generateName(packageName)
                val (m1, m2, m3) = GoodBeastEngine.generateMoves(packageName)
                val partyStr = prefs.getString("PARTY_DATA", "") ?: ""
                val newBeast = "$bName,Reclaimed,150,150,$m1,$m2,$m3,0,0,0,0,true,None,0,0,0,0,None,0"
                prefs.edit().putString("PARTY_DATA", if (partyStr.isEmpty()) newBeast else "$partyStr;$newBeast").apply()
                Handler(Looper.getMainLooper()).post { Toast.makeText(this, "You found an orphaned $bName! But careful, don't come back here, there are only demons left lurking!", Toast.LENGTH_LONG).show() }
            } else triggerBossInvasion("App Overcome: $packageName", isGamificationEnabled)
            return 
        }

        if (Config.UNINSTALL_PROTECTION_ENABLED && packageName.contains("com.android.settings")) {
            val ctxDev = extractContext(rootNode, "Developer options") ?: extractContext(rootNode, "USB debugging")
            if (ctxDev != null) { triggerBossInvasion("Anti-ADB Guard: $ctxDev", isGamificationEnabled); return }
            if (isAdminActive) { val ctx = extractContext(rootNode, "Device admin apps"); if (ctx != null) { triggerBossInvasion("Settings Guard: $ctx", isGamificationEnabled); return } }
            if (isDnsVerified) { val ctx = extractContext(rootNode, "Private DNS"); if (ctx != null) { triggerBossInvasion("Settings Guard: $ctx", isGamificationEnabled); return } }
            val ctxVpn = extractContext(rootNode, "VPN"); if (ctxVpn != null) { triggerBossInvasion("Settings Guard: $ctxVpn", isGamificationEnabled); return }
            if (isAdminActive && findTextInNode(rootNode, appName)) { val ctxUn = extractContext(rootNode, "Uninstall") ?: extractContext(rootNode, "Force stop"); if (ctxUn != null) { triggerBossInvasion("Uninstall Guard: $ctxUn", isGamificationEnabled); return } }
        }

        if (packageName == "com.android.vending") {
            val ctx = extractContext(rootNode, "vpn") ?: extractContext(rootNode, "proxy")
            if (ctx != null) { triggerBossInvasion("VPN Search: $ctx", isGamificationEnabled); return }
        }
        if (packageName.contains("com.rockhard.blocker")) return

        for (word in blockedWebs) { 
            if (allScreenText.contains(word)) {
                val ctx = extractDangerousContext(rootNode, word) ?: extractUrlBarContext(rootNode, word)
                if (ctx != null) { 
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    val rewardKey = "FIRST_OVERCOME_WEB_$word"
                    if (!prefs.getBoolean(rewardKey, false)) {
                        prefs.edit().putBoolean(rewardKey, true).apply()
                        val bName = GoodBeastEngine.generateName(word)
                        val (m1, m2, m3) = GoodBeastEngine.generateMoves(word)
                        val partyStr = prefs.getString("PARTY_DATA", "") ?: ""
                        val newBeast = "$bName,Reclaimed,100,100,$m1,$m2,$m3,0,0,0,0,true,None,0,0,0,0,None,0"
                        prefs.edit().putString("PARTY_DATA", if (partyStr.isEmpty()) newBeast else "$partyStr;$newBeast").apply()
                        Handler(Looper.getMainLooper()).post { Toast.makeText(this, "You found an orphaned $bName! But careful, don't come back here, there are only demons left lurking!", Toast.LENGTH_LONG).show() }
                    } else triggerBossInvasion("Hyperlink Overcome: $ctx", isGamificationEnabled)
                    return 
                } 
            }
        }

        val hardWords = listOf("nsfw", "porno", "色情", "黄片", "xvideo", "pornhub", "onlyfans", "redtube", "brazzers", "xhamster", "rule34")
        val softWords = listOf("explicit", "sensitive content", "fuck", "bitch", "nude", "naked", "sex", "erotic")

        val foundHard = hardWords.firstOrNull { allScreenText.contains(it) }
        if (foundHard != null) {
            val ctx = extractContext(rootNode, foundHard) ?: foundHard
            if (!rootNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) performGlobalAction(GLOBAL_ACTION_BACK)
            triggerBossInvasion("Content Guard: $ctx", isGamificationEnabled)
            return
        }

        val foundSoft = softWords.filter { allScreenText.contains(it) }
        if (foundSoft.size >= 2) {
            val ctx = extractContext(rootNode, foundSoft.first()) ?: foundSoft.joinToString(" & ")
            if (!rootNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) performGlobalAction(GLOBAL_ACTION_BACK)
            triggerBossInvasion("Content Guard: $ctx", isGamificationEnabled)
            return
        }
    }

    private fun triggerBossInvasion(debugReason: String, isGamificationEnabled: Boolean) {
        addLog("RED WALL DROPPED. Reason: $debugReason")
        Handler(Looper.getMainLooper()).post {
            if (isOverlayShowing) return@post
            urgesDefeatedCount++
            val params = WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT)
            params.gravity = Gravity.CENTER; overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_guard, null)
            
            overlayView?.findViewById<TextView>(R.id.tvDebugReason)?.text = "Triggered by:\n\"$debugReason\""
            val tvMsg = overlayView?.findViewById<TextView>(R.id.tvOverlayMessage)
            
            val btnDefend = overlayView?.findViewById<Button>(R.id.btnPauseBlocker)
            val btnYield = overlayView?.findViewById<Button>(R.id.btnYield)
            btnDefend?.text = "Defend Netbeasts"
            btnYield?.text = "Flee - leave the netbeasts to die"

            val triggerWord = debugReason.substringAfter(": ").substringBefore(" (").trim()
            val bossName = if (getString(R.string.flavor_id) == "rhc") BossNameEngine.generateBossName(triggerWord) else "The Dark One"
            
            val fleeLogic = {
                getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE).edit().putBoolean("FLED_BATTLE", true).putString("FLED_BOSS", bossName).apply()
                removeOverlay(); performGlobalAction(GLOBAL_ACTION_HOME)
            }

            if (!isGamificationEnabled) { 
                tvMsg?.text = "Access Denied. Stay strong."
                btnDefend?.visibility = View.GONE
                btnYield?.setOnClickListener { fleeLogic() }
                try { windowManager?.addView(overlayView, params); isOverlayShowing = true } catch (e: Exception) {}
                return@post 
            }

            overlayView?.findViewById<TextView>(R.id.tvOverlayTitle)?.text = "⚠️ INVASION DETECTED ⚠️"

            bossTimer = object : CountDownTimer(180000, 1000) {
                override fun onTick(millisUntilFinished: Long) { 
                    val secLeft = millisUntilFinished / 1000
                    tvMsg?.text = "$bossName is attacking!\n\nTime remaining:\n${String.format("%02d:%02d", secLeft / 60, secLeft % 60)}\n\n(If you ignore this, some Netbeasts might die.)" 
                }
                override fun onFinish() { fleeLogic() }
            }.start()

            btnYield?.setOnClickListener { fleeLogic() }
            btnDefend?.setOnClickListener { 
                removeOverlay()
                startActivity(Intent(this, GameActivity::class.java).apply { 
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("UNDER_ATTACK", true)
                    putExtra("TRIGGER_REASON", triggerWord)
                }) 
            }
            try { windowManager?.addView(overlayView, params); isOverlayShowing = true } catch (e: Exception) {}
        }
    }

    private fun removeOverlay() {
        if (isOverlayShowing && overlayView != null) {
            bossTimer?.cancel(); windowManager?.removeView(overlayView); overlayView = null; isOverlayShowing = false
        }
    }

    private fun extractAllText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder()
        if (node.text != null) sb.append(node.text).append(" ")
        if (node.contentDescription != null) sb.append(node.contentDescription).append(" ")
        for (i in 0 until node.childCount) { sb.append(extractAllText(node.getChild(i))) }
        return sb.toString()
    }

    private fun extractUrlBarContext(node: AccessibilityNodeInfo?, word: String): String? {
        if (node == null) return null
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val className = node.className?.toString()?.lowercase() ?: ""
        val resName = node.viewIdResourceName?.lowercase() ?: ""
        
        // This makes sure it catches Chrome URL bars even if the view ID is hidden by Android XML configs!
        val isUrlBar = resName.contains("url") || resName.contains("address") || resName.contains("search") || resName.contains("omnibox") || className.contains("edittext")
        
        if (isUrlBar && (text.contains(word) || desc.contains(word))) {
            return "URL Bar: ${if (text.isNotEmpty()) text else desc}"
        }
        for (i in 0 until node.childCount) {
            val res = extractUrlBarContext(node.getChild(i), word)
            if (res != null) return res
        }
        return null
    }

    private fun extractContext(node: AccessibilityNodeInfo?, word: String): String? {
        if (node == null) return null
        val text = node.text?.toString() ?: node.contentDescription?.toString() ?: return null
        val index = text.indexOf(word, ignoreCase = true)
        if (index != -1) {
            val start = Math.max(0, index - 40); val end = Math.min(text.length, index + word.length + 40)
            return "...${text.substring(start, end).replace('\n', ' ')}..."
        }
        for (i in 0 until node.childCount) { val res = extractContext(node.getChild(i), word); if (res != null) return res }
        return null
    }

    private fun extractDangerousContext(node: AccessibilityNodeInfo?, word: String): String? {
        if (node == null) return null
        val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
        if (text.contains(word, true)) {
            var parent = node.parent
            var isClickable = node.isClickable || node.className?.toString()?.contains("EditText") == true
            while (parent != null && !isClickable) { isClickable = parent.isClickable; parent = parent.parent }
            if (isClickable) {
                val index = text.indexOf(word, ignoreCase = true)
                val start = Math.max(0, index - 40); val end = Math.min(text.length, index + word.length + 40)
                return "...${text.substring(start, end).replace('\n', ' ')}..."
            }
        }
        for (i in 0 until node.childCount) { val res = extractDangerousContext(node.getChild(i), word); if (res != null) return res }
        return null
    }

    private fun isNodeDangerousContext(node: AccessibilityNodeInfo?, textToFind: String): Boolean {
        if (node == null) return false
        val text = node.text?.toString() ?: ""; val desc = node.contentDescription?.toString() ?: ""
        if (text.contains(textToFind, true) || desc.contains(textToFind, true)) {
            var parent = node.parent
            var isClickable = node.isClickable || node.className?.toString()?.contains("EditText") == true
            while (parent != null && !isClickable) { isClickable = parent.isClickable; parent = parent.parent }
            if (isClickable) return true
        }
        for (i in 0 until node.childCount) { if (isNodeDangerousContext(node.getChild(i), textToFind)) return true }
        return false
    }

    private fun findNodeWithText(node: AccessibilityNodeInfo?, textToFind: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString()?.contains(textToFind, ignoreCase = true) == true || node.contentDescription?.toString()?.contains(textToFind, ignoreCase = true) == true) return node
        for (i in 0 until node.childCount) { val found = findNodeWithText(node.getChild(i), textToFind); if (found != null) return found }
        return null
    }

    private fun findTextInNode(node: AccessibilityNodeInfo?, textToFind: String) = findNodeWithText(node, textToFind) != null

    override fun onInterrupt() {}
}
