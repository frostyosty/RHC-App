package com.rockhard.blocker

import android.graphics.Color
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

internal fun MainActivity.addBlockItem(key: String, displayName: String, blockTarget: String) {
    val activity = this
    if (blockTarget.isEmpty() || displayName.isEmpty()) { 
        Toast.makeText(activity, "Empty selection!", Toast.LENGTH_LONG).show()
        return 
    }
    
    val protectedPkgs = listOf("systemui", "launcher", "settings", "installer", "permission", "com.android.providers")
    if (protectedPkgs.any { blockTarget.lowercase().contains(it) }) { 
        Toast.makeText(activity, "Cannot overcome essential system apps!", Toast.LENGTH_LONG).show()
        return 
    }
    
    val currentList = prefs.getString(key, "") ?: ""
    val items = currentList.split(",").filter { it.isNotEmpty() }.toMutableList()
    
    if (!items.any { it.split("|")[0] == blockTarget }) {
        val dateStr = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date())
        items.add("$blockTarget|$dateStr|0|$displayName")
        prefs.edit().putString(key, items.joinToString(",")).apply()
        Toast.makeText(activity, "Overcome Locked: $displayName", Toast.LENGTH_SHORT).show()
        refreshUI()
    } else {
        Toast.makeText(activity, "Already overcome!", Toast.LENGTH_SHORT).show()
    }
}

internal fun MainActivity.renderBlockList(ll: LinearLayout?, key: String) {
    val activity = this
    ll?.removeAllViews()
    val data = prefs.getString(key, "") ?: ""
    if (data.isEmpty()) return
    
    data.split(",").filter { it.isNotEmpty() }.forEach { entry ->
        val parts = entry.split("|")
        val target = parts[0]
        val date = if (parts.size > 1) parts[1] else "Unknown Date"
        val triggers = if (parts.size > 2) parts[2] else "0"
        val displayName = if (parts.size > 3) parts[3] else target
        
        ll?.addView(TextView(activity).apply {
            text = "🚫 $displayName\n   ↳ Overcome: $date | Triggers: $triggers"
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 12f
            setPadding(0, 8, 0, 8)
        })
    }
}
