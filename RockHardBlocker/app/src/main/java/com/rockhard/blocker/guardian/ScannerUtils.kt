package com.rockhard.blocker

import android.view.accessibility.AccessibilityNodeInfo

object ScannerUtils {

    fun extractAllText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = StringBuilder(2048)
        extractTextRecursive(node, sb)
        return sb.toString().lowercase()
    }

    private fun extractTextRecursive(node: AccessibilityNodeInfo, sb: StringBuilder) {
        node.text?.let { sb.append(it).append(" ") }
        node.contentDescription?.let { sb.append(it).append(" ") }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { extractTextRecursive(it, sb) }
        }
    }

    fun extractUrlBarText(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null
        
        val resName = node.viewIdResourceName?.lowercase() ?: ""
        val className = node.className?.toString()?.lowercase() ?: ""

        val isUrlBarCandidate = resName.contains("url") || resName.contains("address") ||
                                resName.contains("omnibox") || resName.contains("search_box") ||
                                (className.contains("edittext") && (resName.contains("search") || resName.contains("input") || resName.contains("query")))
        
        if (isUrlBarCandidate) {
            return node.text?.toString() ?: node.contentDescription?.toString()
        }

        for (i in 0 until node.childCount) {
            val urlText = extractUrlBarText(node.getChild(i))
            if (urlText != null) return urlText
        }
        return null
    }

    fun extractDangerousContext(node: AccessibilityNodeInfo?, word: String): String? {
        if (node == null) return null
        val text = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
        
        if (text.contains(word, ignoreCase = true)) {
            var currentNode: AccessibilityNodeInfo? = node
            var isInteractable = false
            while(currentNode != null) {
                if (currentNode.isClickable || currentNode.isLongClickable || currentNode.isFocusable || currentNode.className?.contains("EditText") == true) {
                    isInteractable = true
                    break
                }
                currentNode = currentNode.parent
            }

            if (isInteractable) {
                val index = text.indexOf(word, ignoreCase = true)
                val start = (index - 40).coerceAtLeast(0)
                val end = (index + word.length + 40).coerceAtMost(text.length)
                return "...${text.substring(start, end).replace('\n', ' ')}..."
            }
        }
        for (i in 0 until node.childCount) {
            val res = extractDangerousContext(node.getChild(i), word)
            if (res != null) return res
        }
        return null
    }
}