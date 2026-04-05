package com.rockhard.blocker

import android.app.AlertDialog
import android.graphics.Color
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import kotlin.random.Random

internal fun GameActivity.setupEquipPanel() {
    fun setupAutoRepeat(btn: Button, itemType: String) {
        var isPressing = false
        val repeatRunnable = object : Runnable {
            override fun run() {
                if (isPressing && party.isNotEmpty()) {
                    val p = party[activePetIndex]; var didEquip = false
                    if (itemType == "NET" && nets > 0) { nets--; p.eqNets++; didEquip = true }
                    if (itemType == "POT" && potions > 0) { potions--; p.eqPots++; didEquip = true }
                    if (itemType == "SPRAY" && sprays > 0) { sprays--; p.eqSprays++; didEquip = true }
                    if (didEquip) { saveItems(); saveParty(); updatePartyScreen(); updateBagScreen(); mainHandler.postDelayed(this, 150) }
                }
            }
        }
        btn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (party.isEmpty()) { Toast.makeText(this, "No Netbeast selected!", Toast.LENGTH_SHORT).show(); return@setOnTouchListener false }
                    isPressing = true; val p = party[activePetIndex]; var didEquip = false
                    if (itemType == "NET" && nets > 0) { nets--; p.eqNets++; didEquip = true }
                    else if (itemType == "POT" && potions > 0) { potions--; p.eqPots++; didEquip = true }
                    else if (itemType == "SPRAY" && sprays > 0) { sprays--; p.eqSprays++; didEquip = true }
                    else Toast.makeText(this, "Out of $itemType!", Toast.LENGTH_SHORT).show()
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

    findViewById<Button>(R.id.btnUnequip).setOnClickListener {
        if (party.isEmpty()) return@setOnClickListener
        val p = party[activePetIndex]; nets += p.eqNets; potions += p.eqPots; sprays += p.eqSprays
        p.eqNets = 0; p.eqPots = 0; p.eqSprays = 0
        saveItems(); saveParty(); updatePartyScreen(); updateBagScreen()
    }

    // FIXED: COMBINED DISTRIBUTE BUTTON LOGIC!
    val btnDistribute = findViewById<Button>(R.id.btnDistribute)
    if (party.size > 1 || prefs.getInt("PLAYER_UNCLAIMED_AMULETS", 0) > 0) {
        btnDistribute.visibility = android.view.View.VISIBLE
        btnDistribute.setOnClickListener {
            val unclaimed = prefs.getInt("PLAYER_UNCLAIMED_AMULETS", 0)
            if (unclaimed > 0) {
                DialogUtils.showCustomDialog(this, "Equip Titan Amulet", "Permanently grants +30% Damage. Who gets it?", true, "CANCEL", null) { content, dialog ->
                    val btnMe = Button(this).apply { text = "EQUIP TO MYSELF (God Punch)"; setBackgroundResource(R.drawable.bg_btn_accent); setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }; setOnClickListener { prefs.edit().putBoolean("PLAYER_HAS_AMULET", true).putInt("PLAYER_UNCLAIMED_AMULETS", unclaimed - 1).apply(); dialog.dismiss(); updateBagScreen(); Toast.makeText(this@setupEquipPanel, "You feel absolute power surging...", Toast.LENGTH_SHORT).show() } }
                    val btnPet = Button(this).apply { text = "EQUIP TO ${party[activePetIndex].name}"; setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }; setOnClickListener { party[activePetIndex].amulets++; prefs.edit().putInt("PLAYER_UNCLAIMED_AMULETS", unclaimed - 1).apply(); saveParty(); dialog.dismiss(); updateBagScreen(); updatePartyScreen(); Toast.makeText(this@setupEquipPanel, "Amulet equipped!", Toast.LENGTH_SHORT).show() } }
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
    } else btnDistribute.visibility = android.view.View.GONE

    findViewById<Button>(R.id.btnInfuse).setOnClickListener {
        if (party.isEmpty()) return@setOnClickListener; val p = party[activePetIndex]
        if (currentWeather == "Offline" || currentWeather == "Clear" && weatherIcon != "☀️") { Toast.makeText(this, "Weather API Offline", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
        if (p.infusionEl == currentWeather) Toast.makeText(this, "${p.name} is already infused!", Toast.LENGTH_SHORT).show()
        else { val msg = if (p.infusionEl == "None") "Infuse ${p.name} with $currentWeather energy?" else "This will overwrite ${p.name}'s current infusion (${p.infusionEl})."; DialogUtils.showCustomDialog(this, "Infuse $currentWeather?", msg, true, "YES", { p.infusionEl = currentWeather; p.infusionStacks = 0; saveParty(); updatePartyScreen(); Toast.makeText(this, "Infused with $currentWeather!", Toast.LENGTH_SHORT).show() }, null) }
    }

    findViewById<Button>(R.id.btnTeachMove)?.setOnClickListener {
        if (party.size <= 1) { Toast.makeText(this, "Need another beast to learn from!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
        if (focusCoins < 50) { Toast.makeText(this, "Cost: 50c!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
        val activePet = party[activePetIndex]; val otherPets = party.filterIndexed { index, _ -> index != activePetIndex }
        DialogUtils.showCustomDialog(this, "Select Teacher", "Cost: 50c.", true, "", null) { content, dialog ->
            otherPets.forEach { teacher ->
                val btn = Button(this).apply { text = "Learn from ${teacher.name}"; setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
                    setOnClickListener {
                        dialog.dismiss()
                        DialogUtils.showCustomDialog(this@setupEquipPanel, "Learn which move?", null, true, "", null) { c2, d2 ->
                            val m1Btn = Button(this@setupEquipPanel).apply { text = teacher.move1; setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }; setOnClickListener { d2.dismiss(); chooseSlot(activePet, teacher.move1) } }
                            val m2Btn = Button(this@setupEquipPanel).apply { text = teacher.move2; setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }; setOnClickListener { d2.dismiss(); chooseSlot(activePet, teacher.move2) } }
                            c2.addView(m1Btn); c2.addView(m2Btn)
                        }
                    }
                }; content.addView(btn)
            }
        }
    }

    findViewById<Button>(R.id.btnSellBeast).setOnClickListener {
        if (party.size <= 1) { Toast.makeText(this, "Cannot sell your last Netbeast!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
        if (activeExpeditions.containsKey(activePetIndex)) { Toast.makeText(this, "Cannot sell while exploring!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
        val p = party[activePetIndex]; val suggestedPrice = (p.maxHp / 10) * Random.nextInt(4, 8)
        DialogUtils.showCustomDialog(this, "List on Market?", "List ${p.name} for ${suggestedPrice}c?", true, "LIST", { p.listedPrice = suggestedPrice; forSaleParty.add(p); party.removeAt(activePetIndex); activePetIndex = 0; prefs.edit().putInt("ACTIVE_PET_INDEX", 0).apply(); saveParty(); SaveManager.saveParty(prefs, "FORSALE_DATA", forSaleParty); updatePartyScreen(); updateBagScreen(); printLog("\n> 📦 You listed ${p.name} on the market for ${suggestedPrice}c.") }, null)
    }
}

internal fun GameActivity.chooseSlot(pet: Netbeast, newMove: String) {
    DialogUtils.showCustomDialog(this, "Forget which move?", null, true, "", null) { c3, d3 ->
        val slot1 = Button(this).apply { text = "Forget ${pet.move1}"; setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }; setOnClickListener { pet.move1 = newMove; finalizeTeach(d3) } }
        val slot2 = Button(this).apply { text = "Forget ${pet.move2}"; setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }; setOnClickListener { pet.move2 = newMove; finalizeTeach(d3) } }
        c3.addView(slot1); c3.addView(slot2)
    }
}

internal fun GameActivity.finalizeTeach(d: AlertDialog) {
    focusCoins -= 50; saveItems(); saveParty(); updatePartyScreen(); updateBattleUI()
    printLog("\n> 🧬 SUCCESS! Move learned."); d.dismiss()
}