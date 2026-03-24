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

class GuardianService : AccessibilityService() {
    companion object { 
        var pauseUntil: Long = 0L 
        var urgesDefeatedCount = 0
    }
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isOverlayShowing = false
    private var bossTimer: CountDownTimer? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || System.currentTimeMillis() < pauseUntil) return
        val packageName = event.packageName?.toString() ?: ""
        val rootNode = rootInActiveWindow ?: return
        val appName = getString(R.string.app_name)

        val prefs = getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE)
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val compName = ComponentName(this, AdminReceiver::class.java)

        val isAdminActive = dpm.isAdminActive(compName)
        val isDnsVerified = prefs.getBoolean("DNS_VERIFIED", false)
        val isGamificationEnabled = prefs.getBoolean("GAMIFICATION", true)
        val blocklist = prefs.getString("BLOCKLIST", "")?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() } ?: emptyList()

        if (blocklist.any { packageName.lowercase().contains(it) }) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            triggerBossInvasion("App Overcome: $packageName", isGamificationEnabled)
            return
        }

        if (packageName.contains("com.android.settings")) {
            if (isAdminActive) { val ctx = extractContext(rootNode, "Device admin apps"); if (ctx != null) { triggerBossInvasion("Settings Guard: $ctx", isGamificationEnabled); return } }
            if (isDnsVerified) { val ctx = extractContext(rootNode, "Private DNS"); if (ctx != null) { triggerBossInvasion("Settings Guard: $ctx", isGamificationEnabled); return } }
            val ctxVpn = extractContext(rootNode, "VPN")
            if (ctxVpn != null) { triggerBossInvasion("Settings Guard: $ctxVpn", isGamificationEnabled); return }
            if (isAdminActive && findTextInNode(rootNode, appName)) {
                val ctxUn = extractContext(rootNode, "Uninstall") ?: extractContext(rootNode, "Force stop")
                if (ctxUn != null) { triggerBossInvasion("Uninstall Guard: $ctxUn", isGamificationEnabled); return }
            }
        }
        
        if (packageName == "com.android.vending") {
            val ctx = extractContext(rootNode, "vpn") ?: extractContext(rootNode, "proxy")
            if (ctx != null) { triggerBossInvasion("VPN Search: $ctx", isGamificationEnabled); return }
        }

        if (packageName.contains("com.rockhard.blocker")) return

        for (word in blocklist) {
            val ctx = extractDangerousContext(rootNode, word)
            if (ctx != null) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                triggerBossInvasion("Hyperlink Overcome: $ctx", isGamificationEnabled)
                return
            }
        }

        val triggerWords = listOf("Sensitive Content", "NSFW", "Explicit", "porno", "色情", "黄片")
        for (word in triggerWords) {
            val badNode = findNodeWithText(rootNode, word)
            if (badNode != null) {
                val ctx = extractContext(badNode, word) ?: word
                if (!badNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) performGlobalAction(GLOBAL_ACTION_BACK)
                triggerBossInvasion("Content Guard: $ctx", isGamificationEnabled)
                return
            }
        }
    }

    private fun triggerBossInvasion(debugReason: String, isGamificationEnabled: Boolean) {
        Handler(Looper.getMainLooper()).post {
            if (isOverlayShowing) return@post
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.CENTER
            overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_guard, null)
            
            overlayView?.findViewById<TextView>(R.id.tvDebugReason)?.text = "Triggered by: \"$debugReason\""
            
            val tvMsg = overlayView?.findViewById<TextView>(R.id.tvOverlayMessage)
            val btnDefend = overlayView?.findViewById<Button>(R.id.btnPauseBlocker)

            if (!isGamificationEnabled) {
                tvMsg?.text = "Access Denied. Stay strong."
                btnDefend?.visibility = View.GONE
                overlayView?.findViewById<Button>(R.id.btnYield)?.setOnClickListener { removeOverlay(); performGlobalAction(GLOBAL_ACTION_HOME) }
                try { windowManager?.addView(overlayView, params); isOverlayShowing = true } catch (e: Exception) {}
                return@post
            }

            val bossName = if (getString(R.string.flavor_id) == "rhc") listOf("Pornosaur", "GoreIlla", "Fleshwire").random() else "The Dark One"
            overlayView?.findViewById<TextView>(R.id.tvOverlayTitle)?.text = "⚠️ INVASION DETECTED ⚠️"
            btnDefend?.text = "DEFEND NETBEASTS"

            bossTimer = object : CountDownTimer(180000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val secLeft = millisUntilFinished / 1000
                    tvMsg?.text = "$bossName is attacking!\n\nTime remaining:\n${String.format("%02d:%02d", secLeft / 60, secLeft % 60)}\n\n(If you ignore this, some Netbeasts might die.)"
                }
                override fun onFinish() { removeOverlay(); performGlobalAction(GLOBAL_ACTION_HOME) }
            }.start()

            overlayView?.findViewById<Button>(R.id.btnYield)?.setOnClickListener { removeOverlay(); performGlobalAction(GLOBAL_ACTION_HOME) }
            btnDefend?.setOnClickListener {
                removeOverlay()
                startActivity(Intent(this, GameActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); putExtra("UNDER_ATTACK", true) })
            }
            try { windowManager?.addView(overlayView, params); isOverlayShowing = true } catch (e: Exception) {}
        }
    }

    private fun removeOverlay() {
        if (isOverlayShowing && overlayView != null) { bossTimer?.cancel(); windowManager?.removeView(overlayView); overlayView = null; isOverlayShowing = false }
    }

    private fun extractContext(node: AccessibilityNodeInfo?, word: String): String? {
        if (node == null) return null
        val text = node.text?.toString() ?: node.contentDescription?.toString() ?: return null
        val index = text.indexOf(word, ignoreCase = true)
        if (index != -1) {
            val start = Math.max(0, index - 15)
            val end = Math.min(text.length, index + word.length + 15)
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
                val start = Math.max(0, index - 15)
                val end = Math.min(text.length, index + word.length + 15)
                return "...${text.substring(start, end).replace('\n', ' ')}..."
            }
        }
        for (i in 0 until node.childCount) { val res = extractDangerousContext(node.getChild(i), word); if (res != null) return res }
        return null
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
