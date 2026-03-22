package com.rockhard.blocker

import android.accessibilityservice.AccessibilityService
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

class GuardianService : AccessibilityService() {
    companion object { 
        var pauseUntil: Long = 0L 
        val appTimeTrackers = mutableMapOf<String, Long>()
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

        // LIVE TIME TRACKING ENGINE
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) { /* Time logic hidden for brevity */ }

        // 1. TAMPER GUARD (FIXED)
        if (packageName.contains("com.android.settings")) {
            if (findTextInNode(rootNode, "Device admin apps")) { triggerBossInvasion("Settings: Device Admin Blocked"); return }
            if (findTextInNode(rootNode, "Private DNS")) { triggerBossInvasion("Settings: Private DNS Blocked"); return }
            if (findTextInNode(rootNode, "VPN")) { triggerBossInvasion("Settings: VPN Menu Blocked"); return }
            
            // FIX: Only attack if they are on the App Uninstall page!
            if (findTextInNode(rootNode, appName) && (findTextInNode(rootNode, "Uninstall") || findTextInNode(rootNode, "Force stop"))) {
                triggerBossInvasion("Settings: App Uninstall Blocked")
                return
            }
        }
        
        // 2. PLAY STORE VPN HUNTER
        if (packageName == "com.android.vending" && (findTextInNode(rootNode, "vpn") || findTextInNode(rootNode, "proxy"))) {
            triggerBossInvasion("Play Store: VPN Search Blocked")
            return
        }

        // 3. NSFW CONTENT NINJA
        val triggerWords = listOf("Sensitive Content", "NSFW", "Explicit")
        for (word in triggerWords) {
            val badNode = findNodeWithText(rootNode, word)
            if (badNode != null) {
                if (!badNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) performGlobalAction(GLOBAL_ACTION_BACK)
                triggerBossInvasion("Content Guard: Detected '$word'")
                return
            }
        }
    }

    private fun triggerBossInvasion(debugReason: String) {
        Handler(Looper.getMainLooper()).post {
            if (isOverlayShowing) return@post
            urgesDefeatedCount++
            
            val flavor = getString(R.string.flavor_id)
            val bossName = if (flavor == "rhc") listOf("Pornosaur", "GoreIlla", "Fleshwire").random() else "The Dark One"

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.CENTER
            overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_guard, null)

            overlayView?.findViewById<TextView>(R.id.tvOverlayTitle)?.text = "⚠️ INVASION DETECTED ⚠️"
            overlayView?.findViewById<Button>(R.id.btnPauseBlocker)?.text = "DEFEND BOUNCEMONS"
            
            // INJECT DEBUG TEXT
            overlayView?.findViewById<TextView>(R.id.tvDebugReason)?.text = "Debug Trigger: $debugReason"

            val tvMsg = overlayView?.findViewById<TextView>(R.id.tvOverlayMessage)

            bossTimer = object : CountDownTimer(180000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val secLeft = millisUntilFinished / 1000
                    val timeString = String.format("%02d:%02d", secLeft / 60, secLeft % 60)
                    tvMsg?.text = "$bossName is attacking your Bouncemons!\n\nTime remaining to respond:\n$timeString\n\n(If you ignore this, some Bouncemons might die.)"
                }
                override fun onFinish() {
                    Toast.makeText(this@GuardianService, "$bossName killed some Bouncemons!", Toast.LENGTH_LONG).show()
                    removeOverlay(); performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }.start()

            overlayView?.findViewById<Button>(R.id.btnYield)?.setOnClickListener { removeOverlay(); performGlobalAction(GLOBAL_ACTION_HOME) }
            overlayView?.findViewById<Button>(R.id.btnPauseBlocker)?.setOnClickListener {
                removeOverlay()
                startActivity(Intent(this, GameActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); putExtra("UNDER_ATTACK", true) })
            }

            try { windowManager?.addView(overlayView, params); isOverlayShowing = true } catch (e: Exception) { performGlobalAction(GLOBAL_ACTION_HOME) }
        }
    }

    private fun removeOverlay() {
        if (isOverlayShowing && overlayView != null) {
            bossTimer?.cancel(); windowManager?.removeView(overlayView); overlayView = null; isOverlayShowing = false
        }
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
