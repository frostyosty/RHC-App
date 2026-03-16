package com.rockhard.blocker

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class GuardianService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: ""
        val rootNode = rootInActiveWindow ?: return
        val appName = getString(R.string.app_name)

        if (packageName.contains("com.android.settings")) {
            if (findTextInNode(rootNode, appName) || findTextInNode(rootNode, "Device admin apps")) {
                performGlobalAction(GLOBAL_ACTION_HOME)
                return
            }
        }

        val triggerWords = listOf("Sensitive Content", "NSFW")
        for (word in triggerWords) {
            if (findTextInNode(rootNode, word)) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                return
            }
        }
    }

    private fun findTextInNode(node: AccessibilityNodeInfo?, textToFind: String): Boolean {
        if (node == null) return false
        val nodeText = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        if (nodeText.contains(textToFind, ignoreCase = true) || contentDesc.contains(textToFind, ignoreCase = true)) {
            return true
        }
        for (i in 0 until node.childCount) {
            if (findTextInNode(node.getChild(i), textToFind)) return true
        }
        return false
    }

    override fun onInterrupt() {}
}
