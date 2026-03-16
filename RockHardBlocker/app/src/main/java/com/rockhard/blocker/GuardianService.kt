package com.rockhard.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button

class GuardianService : AccessibilityService() {

    companion object {
        // This is a global variable. If current time is less than this, the Guardian sleeps!
        var pauseUntil: Long = 0L
    }

    private var currentApp: String = ""
    private var currentAppStartTime: Long = 0L
    private val appTimeTrackers = mutableMapOf<String, Long>()
    private var urgesDefeatedCount = 0

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isOverlayShowing = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // CHECK PAUSE STATE: If they completed the essay, the Guardian ignores the screen
        if (System.currentTimeMillis() < pauseUntil) {
            return
        }

        val packageName = event.packageName?.toString() ?: ""
        val eventType = event.eventType

        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (packageName != currentApp) {
                if (currentApp.isNotEmpty() && currentAppStartTime > 0) {
                    val timeSpent = System.currentTimeMillis() - currentAppStartTime
                    appTimeTrackers[currentApp] = (appTimeTrackers[currentApp] ?: 0L) + timeSpent
                }
                currentApp = packageName
                currentAppStartTime = System.currentTimeMillis()
            }
        }

        val rootNode = rootInActiveWindow ?: return
        val appName = getString(R.string.app_name)

        if (packageName.contains("com.android.settings")) {
            if (findTextInNode(rootNode, appName) || findTextInNode(rootNode, "Device admin apps")) {
                if (!isOverlayShowing) {
                    urgesDefeatedCount++
                    showUncloseableOverlay()
                }
                return
            }
        }

        val triggerWords = listOf("Sensitive Content", "NSFW")
        for (word in triggerWords) {
            val badNode = findNodeWithText(rootNode, word)
            if (badNode != null) {
                urgesDefeatedCount++
                if (!badNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
                return
            }
        }
    }

    private fun showUncloseableOverlay() {
        Handler(Looper.getMainLooper()).post {
            if (isOverlayShowing) return@post

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.CENTER

            val inflater = LayoutInflater.from(this)
            overlayView = inflater.inflate(R.layout.overlay_guard, null)

            // Button 1: Yield (Kick to home screen)
            overlayView?.findViewById<Button>(R.id.btnYield)?.setOnClickListener {
                removeOverlay()
                performGlobalAction(GLOBAL_ACTION_HOME)
            }

            // Button 2: Pause Blocker (Launch the Humiliation Essay)
            overlayView?.findViewById<Button>(R.id.btnPauseBlocker)?.setOnClickListener {
                removeOverlay()
                // Launch the Essay Activity from the background service
                val intent = Intent(this, EssayActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }

            try {
                windowManager?.addView(overlayView, params)
                isOverlayShowing = true
            } catch (e: Exception) {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    private fun removeOverlay() {
        if (isOverlayShowing && overlayView != null) {
            windowManager?.removeView(overlayView)
            overlayView = null
            isOverlayShowing = false
        }
    }

    private fun findNodeWithText(node: AccessibilityNodeInfo?, textToFind: String): AccessibilityNodeInfo? {
        if (node == null) return null
        val nodeText = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        if (nodeText.contains(textToFind, ignoreCase = true) || contentDesc.contains(textToFind, ignoreCase = true)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val foundNode = findNodeWithText(node.getChild(i), textToFind)
            if (foundNode != null) return foundNode
        }
        return null
    }
    private fun findTextInNode(node: AccessibilityNodeInfo?, textToFind: String) = findNodeWithText(node, textToFind) != null
    override fun onInterrupt() {}
}
