package com.rockhard.blocker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date

class ProcessTextActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Grab the highlighted text from the Android system
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim()?.lowercase()
        
        if (!text.isNullOrEmpty()) {
            val protectedWords = listOf("systemui", "launcher", "nexus", "pixel", "gallery", "camera", "dialer", "contacts", "settings", "note", "keyboard", "inputmethod", "swiftkey", "miui", "clock", "alarm", "calculator", "calendar", "messages", "files", "weather", "compass", "radio", "bluetooth", "nfc", "telecom", "updater", "installer", "security", "print", "sim", "theme")
            
            if (protectedWords.any { text.contains(it) }) {
                Toast.makeText(this, "Cannot overcome a protected system keyword!", Toast.LENGTH_LONG).show()
            } else {
                val prefs = getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE)
                val currentList = prefs.getString("BLOCKLIST_WEB", "") ?: ""
                val items = currentList.split(",").filter { it.isNotEmpty() }.toMutableList()
                
                if (!items.any { it.split("|")[0] == text }) {
                    val dateStr = SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(Date())
                    items.add("$text|$dateStr|0")
                    prefs.edit().putString("BLOCKLIST_WEB", items.joinToString(",")).apply()
                    Toast.makeText(this, "Overcome Locked: $text", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Already overcome!", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        // Finish instantly so no UI ever appears on the screen
        finish()
    }
}
