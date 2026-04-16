package com.rockhard.blocker

import android.app.AlertDialog
import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date

internal fun GameActivity.setupInfusionButton() {
    val btnInfuse = findViewById<Button>(R.id.btnInfuse) ?: return
    val todayStr = SimpleDateFormat("yyyyMMdd").format(Date())
    
    if (prefs.getString("LAST_INFUSION_DATE", "") == todayStr) {
        btnInfuse.visibility = View.GONE
    } else {
        btnInfuse.visibility = View.VISIBLE
    }

    btnInfuse.setOnClickListener {
        if (party.isEmpty()) return@setOnClickListener
        val p = party[activePetIndex]
        if (currentWeather == "Offline" || currentWeather == "Clear" && weatherIcon != "☀️") {
            Toast.makeText(this, "Weather API Offline", Toast.LENGTH_SHORT).show()
            return@setOnClickListener
        }
        val msg = "Infuse ${p.name} with $currentWeather energy? (Only 1 infusion allowed per day across your entire party!)"
        DialogUtils.showCustomDialog(this, "Infuse $currentWeather?", msg, true, "YES", {
            p.infusionEl = currentWeather
            p.infusionStacks += 1
            prefs.edit().putString("LAST_INFUSION_DATE", todayStr).apply()
            btnInfuse.visibility = View.GONE
            saveParty(); updatePartyScreen()
            Toast.makeText(this, "Infused!", Toast.LENGTH_SHORT).show()
        }, null)
    }
}

internal fun GameActivity.setupCleanseButton() {
    val btnTeachMove = findViewById<Button>(R.id.btnTeachMove)
    val parentLayout = btnTeachMove?.parent as? LinearLayout
    
    if (parentLayout?.findViewWithTag<Button>("BTN_REMOVE_TRAIT") == null) {
        val btnRemove = Button(this).apply {
            tag = "BTN_REMOVE_TRAIT"
            text = "CLEANSE MUTATIONS & LEARN ABILITY (300c)"
            setBackgroundResource(R.drawable.bg_btn_standard)
            setTextColor(Color.WHITE)
            textSize = 10f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
            setOnClickListener {
                if (party.isEmpty()) return@setOnClickListener
                val p = party[activePetIndex]
                if (focusCoins < 300) { Toast.makeText(this@setupCleanseButton, "Need 300c to cleanse!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                
                DialogUtils.showCustomDialog(this@setupCleanseButton, "Cleanse Beast?", "Pay 300c to strip mutations and teach the 'Cleanse' ability to ${p.name}?", true, "CLEANSE", {
                    focusCoins -= 300
                    p.name = p.name.replace(Regex("\\[.*?\\]"), "").trim()
                    p.move2 = "Cleanse" // THEY LEARN THE ABILITY HERE!
                    saveItems(); saveParty(); updatePartyScreen(); updateBattleUI()
                    Toast.makeText(this@setupCleanseButton, "Purged! Learned Cleanse!", Toast.LENGTH_SHORT).show()
                }, null)
            }
        }
        if (btnTeachMove != null) {
            parentLayout?.addView(btnRemove, parentLayout.indexOfChild(btnTeachMove) + 1)
        }
    }
}

internal fun GameActivity.setupTeachMoveButton() {
    val btnTeachMove = findViewById<Button>(R.id.btnTeachMove) ?: return
    btnTeachMove.setOnClickListener {
        if (party.size <= 1) { Toast.makeText(this, "Need another beast to learn from!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
        if (focusCoins < 50) { Toast.makeText(this, "Cost: 50c!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
        
        val activePet = party[activePetIndex]
        val otherPets = party.filterIndexed { index, _ -> index != activePetIndex }
        
        DialogUtils.showCustomDialog(this, "Select Teacher", "Cost: 50c.", true, "", null) { content, dialog ->
            otherPets.forEach { teacher ->
                val btn = Button(this).apply {
                    text = "Learn from ${teacher.name}"
                    setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
                    setOnClickListener {
                        dialog.dismiss()
                        DialogUtils.showCustomDialog(this@setupTeachMoveButton, "Learn which move?", null, true, "", null) { c2, d2 ->
                            val m1Btn = Button(this@setupTeachMoveButton).apply { text = teacher.move1; setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }; setOnClickListener { d2.dismiss(); chooseSlot(activePet, teacher.move1) } }
                            val m2Btn = Button(this@setupTeachMoveButton).apply { text = teacher.move2; setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }; setOnClickListener { d2.dismiss(); chooseSlot(activePet, teacher.move2) } }
                            c2.addView(m1Btn); c2.addView(m2Btn)
                        }
                    }
                }; content.addView(btn)
            }
        }
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
    focusCoins -= 50
    saveItems(); saveParty(); updatePartyScreen(); updateBattleUI()
    printLog("\n> 🧬 SUCCESS! Move learned.")
    d.dismiss()
}