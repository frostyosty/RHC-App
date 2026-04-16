package com.rockhard.blocker

import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

internal fun GameActivity.setupItemEquipping() {
    fun setupAutoRepeat(btn: Button?, itemType: String) {
        if (btn == null) return
        var isPressing = false
        val repeatRunnable = object : Runnable {
            override fun run() {
                if (isPressing && party.isNotEmpty()) {
                    val p = party[activePetIndex]
                    var didEquip = false
                    if (itemType == "NET" && nets > 0) { nets--; p.eqNets++; didEquip = true }
                    if (itemType == "POT" && potions > 0) { potions--; p.eqPots++; didEquip = true }
                    if (itemType == "SPRAY" && sprays > 0) { sprays--; p.eqSprays++; didEquip = true }
                    if (didEquip) {
                        saveItems(); saveParty(); updatePartyScreen(); updateBagScreen()
                        mainHandler.postDelayed(this, 150)
                    }
                }
            }
        }
        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (party.isEmpty()) { Toast.makeText(this@setupItemEquipping, "No Netbeast selected!", Toast.LENGTH_SHORT).show(); return@setOnTouchListener false }
                    isPressing = true; val p = party[activePetIndex]; var didEquip = false
                    if (itemType == "NET" && nets > 0) { nets--; p.eqNets++; didEquip = true } 
                    else if (itemType == "POT" && potions > 0) { potions--; p.eqPots++; didEquip = true } 
                    else if (itemType == "SPRAY" && sprays > 0) { sprays--; p.eqSprays++; didEquip = true } 
                    else { Toast.makeText(this@setupItemEquipping, "Out of $itemType!", Toast.LENGTH_SHORT).show() }
                    
                    if (didEquip) { saveItems(); saveParty(); updatePartyScreen(); updateBagScreen() }
                    mainHandler.postDelayed(repeatRunnable, 500)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { isPressing = false; mainHandler.removeCallbacks(repeatRunnable) }
            }
            false
        }
    }

    setupAutoRepeat(findViewById(R.id.btnEqNet), "NET")
    setupAutoRepeat(findViewById(R.id.btnEqPot), "POT")
    setupAutoRepeat(findViewById(R.id.btnEqSpray), "SPRAY")

    findViewById<Button>(R.id.btnUnequip)?.setOnClickListener {
        if (party.isEmpty()) return@setOnClickListener
        val p = party[activePetIndex]
        nets += p.eqNets; potions += p.eqPots; sprays += p.eqSprays
        p.eqNets = 0; p.eqPots = 0; p.eqSprays = 0
        saveItems(); saveParty(); updatePartyScreen(); updateBagScreen()
    }
}

internal fun GameActivity.setupDistributeButton() {
    val btnDistribute = findViewById<Button>(R.id.btnDistribute) ?: return
    
    if (party.size > 1 || prefs.getInt("PLAYER_UNCLAIMED_AMULETS", 0) > 0) {
        btnDistribute.visibility = View.VISIBLE
        btnDistribute.setOnClickListener {
            val unclaimed = prefs.getInt("PLAYER_UNCLAIMED_AMULETS", 0)
            if (unclaimed > 0) {
                DialogUtils.showCustomDialog(this, "Equip Titan Amulet", "Permanently grants +30% Damage. Who gets it?", true, "CANCEL", null) { content, dialog ->
                    val btnMe = Button(this).apply {
                        text = "EQUIP TO MYSELF (God Punch)"; setBackgroundResource(R.drawable.bg_btn_accent); setTextColor(Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
                        setOnClickListener {
                            prefs.edit().putBoolean("PLAYER_HAS_AMULET", true).putInt("PLAYER_UNCLAIMED_AMULETS", unclaimed - 1).apply()
                            dialog.dismiss(); updateBagScreen()
                            Toast.makeText(this@setupDistributeButton, "You feel absolute power surging...", Toast.LENGTH_SHORT).show()
                        }
                    }
                    val btnPet = Button(this).apply {
                        text = "EQUIP TO ${party[activePetIndex].name}"; setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
                        setOnClickListener {
                            party[activePetIndex].amulets++
                            prefs.edit().putInt("PLAYER_UNCLAIMED_AMULETS", unclaimed - 1).apply()
                            saveParty(); dialog.dismiss(); updateBagScreen(); updatePartyScreen()
                            Toast.makeText(this@setupDistributeButton, "Amulet equipped!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    content.addView(btnMe); content.addView(btnPet)
                }
                return@setOnClickListener
            }
            if (party.size > 1) {
                party.forEach { p -> nets += p.eqNets; potions += p.eqPots; sprays += p.eqSprays; p.eqNets = 0; p.eqPots = 0; p.eqSprays = 0 }
                val splitNets = nets / party.size; val splitPots = potions / party.size; val splitSprays = sprays / party.size
                party.forEach { p -> p.eqNets = splitNets; p.eqPots = splitPots; p.eqSprays = splitSprays }
                nets %= party.size; potions %= party.size; sprays %= party.size
                saveItems(); saveParty(); updatePartyScreen(); updateBagScreen()
                Toast.makeText(this, "Items distributed evenly!", Toast.LENGTH_SHORT).show()
            }
        }
    } else {
        btnDistribute.visibility = View.GONE
    }
}