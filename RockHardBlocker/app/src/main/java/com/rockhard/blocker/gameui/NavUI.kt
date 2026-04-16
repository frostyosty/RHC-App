package com.rockhard.blocker

import android.graphics.Color
import android.view.View
import android.widget.Button

internal fun GameActivity.setupTabs() {
    val b1 = findViewById<Button>(R.id.tabActivity)
    val b2 = findViewById<Button>(R.id.tabParty)
    val b3 = findViewById<Button>(R.id.tabBag)
    
    // FIXED: Safely cast as <View> so the app doesn't crash if we change XML layouts later!
    val v1 = findViewById<View>(R.id.viewActivity)
    val v2 = findViewById<View>(R.id.viewParty)
    val v3 = findViewById<View>(R.id.viewBag)

    b1.setOnClickListener {
        v1.visibility = View.VISIBLE; v2.visibility = View.GONE; v3.visibility = View.GONE
        b1.setTextColor(Color.GREEN); b2.setTextColor(Color.GRAY); b3.setTextColor(Color.GRAY)
    }
    b2.setOnClickListener {
        v1.visibility = View.GONE; v2.visibility = View.VISIBLE; v3.visibility = View.GONE
        b1.setTextColor(Color.GRAY); b2.setTextColor(Color.CYAN); b3.setTextColor(Color.GRAY)
        var changed = false
        party.forEach { if (it.isNew) { it.isNew = false; changed = true } }
        if (changed) { saveParty(); updatePartyScreen() }
    }
    b3.setOnClickListener {
        v1.visibility = View.GONE; v2.visibility = View.GONE; v3.visibility = View.VISIBLE
        b1.setTextColor(Color.GRAY); b2.setTextColor(Color.GRAY); b3.setTextColor(Color.YELLOW)
        updateBagScreen()
    }
}
