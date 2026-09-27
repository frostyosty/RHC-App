package com.rockhard.blocker

import android.view.accessibility.AccessibilityNodeInfo

object ScannerUtils {

    class PageScan(val text: String, val imageCount: Int, val urlBarText: String?)

    /**
     * One walk of the screen: all its text (lowercased), how many images it has and, with [findUrlBar],
     * the first address-bar text in the same order a walk of its own would find it. Every child
     * fetched is a call into the app on screen, so the rules share this walk rather than doing their own.
     */
    fun scanPage(root: AccessibilityNodeInfo?, findUrlBar: Boolean): PageScan {
        if (root == null) return PageScan("", 0, null)
        val walk = PageWalk(findUrlBar)
        walk.visit(root)
        return PageScan(walk.text.toString().lowercase(), walk.images, walk.urlBar)
    }

    private class PageWalk(private var lookForUrlBar: Boolean) {
        val text = StringBuilder(2048)
        var images = 0
        var urlBar: String? = null

        fun visit(node: AccessibilityNodeInfo) {
            node.text?.let { text.append(it).append(" ") }
            node.contentDescription?.let { text.append(it).append(" ") }
            if (isImage(node)) images++
            if (lookForUrlBar) urlBarText(node)?.let { urlBar = it; lookForUrlBar = false }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                visit(child)
                child.recycle()
            }
        }
    }

    /** This node's text if it looks like a browser's address bar. */
    private fun urlBarText(node: AccessibilityNodeInfo): String? {
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
        return null
    }

    private fun isImage(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString()?.lowercase() ?: ""
        return className.contains("imageview") || className.contains("image") || node.viewIdResourceName?.lowercase()?.contains("image") == true
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
