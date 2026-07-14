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
            val child = node.getChild(i)
            if (child != null) {
                extractTextRecursive(child, sb)
                child.recycle()
            }
        }
    }

    fun extractUrlBarText(node: AccessibilityNodeInfo?): String? {
        if (node == null) return null
        
        val resName = node.viewIdResourceName?.lowercase() ?: ""
        val className = node.className?.toString()?.lowercase() ?: ""

        val isUrlBarCandidate = resName.contains("url") || resName.contains("address") ||
                                resName.contains("omnibox") || resName.contains("search_box") ||
                                resName.contains("location") || resName.contains("searchbar") ||
                                (className.contains("edittext") && (resName.contains("search") || resName.contains("input") || resName.contains("query") || resName.contains("text")))
        
        val txt = node.text?.toString() ?: node.contentDescription?.toString() ?: ""
        val looksLikeUrl = txt.isNotBlank() && (txt.startsWith("http") || 
                           (txt.contains(".") && !txt.contains(" ") && txt.length > 4 && 
                            (txt.endsWith(".com") || txt.endsWith(".org") || txt.endsWith(".net") || txt.contains(".com/") || txt.contains(".org/") || txt.contains(".net/"))))

        if (isUrlBarCandidate || (className.contains("edittext") && looksLikeUrl)) {
            val txtStr = node.text?.toString() ?: node.contentDescription?.toString()
            if (!txtStr.isNullOrBlank()) {
                return txtStr
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val urlText = extractUrlBarText(child)
                child.recycle()
                if (urlText != null) return urlText
            }
        }
        return null
    }

    fun countImages(node: AccessibilityNodeInfo?): Int {
        if (node == null) return 0
        var count = 0
        val className = node.className?.toString()?.lowercase() ?: ""
        if (className.contains("imageview") || className.contains("image") || node.viewIdResourceName?.lowercase()?.contains("image") == true) {
            count++
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                count += countImages(child)
                child.recycle()
            }
        }
        return count
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
                val parentNode = currentNode.parent
                if (currentNode != node) {
                    currentNode.recycle()
                }
                currentNode = parentNode
            }

            if (isInteractable) {
                val index = text.indexOf(word, ignoreCase = true)
                val start = (index - 40).coerceAtLeast(0)
                val end = (index + word.length + 40).coerceAtMost(text.length)
                return "...${text.substring(start, end).replace('\n', ' ')}..."
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val res = extractDangerousContext(child, word)
                child.recycle()
                if (res != null) return res
            }
        }
        return null
    }
}
