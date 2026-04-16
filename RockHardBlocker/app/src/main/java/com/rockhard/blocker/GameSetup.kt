package com.rockhard.blocker

import android.graphics.Color
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import kotlin.random.Random

internal fun GameActivity.setupBattleControls() {
    val btn1 = findViewById<Button>(R.id.btnMove1)
    val btn2 = findViewById<Button>(R.id.btnMove2)
    val btn3 = findViewById<Button>(R.id.btnMove3)

    btn1.setOnClickListener { if (playerLastStand) executeHumanPunch() else executePlayerMove(party[activePetIndex].move1) }
    btn2.setOnClickListener { executePlayerMove(party[activePetIndex].move2) }
    btn3.setOnClickListener { executePlayerMove(party[activePetIndex].move3) }

    findViewById<Button>(R.id.btnSwap).setOnClickListener {
        cancelBattleTimer()
        if (battleOver || party.size <= 1 || playerLastStand || currentEnemy == null) return@setOnClickListener
        val wildLvl = currentEnemy!!.maxHp / 10
        val options = party.mapIndexed { index, p -> val diff = (p.maxHp / 10) - wildLvl; "${p.name} Lvl ${p.maxHp / 10} (${if (diff >= 0) "+$diff" else diff})" }.toTypedArray()
        
        DialogUtils.showCustomDialog(this, "Swap to who?", null, true, "", null) { content, dialog ->
            party.forEachIndexed { index, p ->
                val btn = Button(this).apply { text = options[index]; setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
                    setOnClickListener {
                        if (index == activePetIndex) printLog("\n> ${p.name} is already fighting!") 
                        else { activePetIndex = index; prefs.edit().putInt("ACTIVE_PET_INDEX", activePetIndex).apply(); printLog("\n> You swapped to ${party[activePetIndex].name}!"); showBattleArena(party[activePetIndex].name, currentEnemy!!.name); updateBattleUI(); triggerEnemyCounterAttack() }
                        dialog.dismiss()
                    }
                }; content.addView(btn)
            }
        }
    }

    // MID-BATTLE POTION
    findViewById<Button>(R.id.btnBattlePot).setOnClickListener {
    cancelBattleTimer()
        if (battleOver || playerLastStand) return@setOnClickListener
        val pet = party[activePetIndex]
        if (pet.eqPots > 0) {
            pet.eqPots--
            val effect = Random.nextInt(3)
            if (effect == 0) { playerHasTripleStrike = true; printLog("\n> 🧪 Potion glows RED! Next attack will be a TRIPLE STRIKE!") } 
            else if (effect == 1) { playerHasEvasion = true; printLog("\n> 🧪 Potion glows BLUE! Guaranteed EVASION!") } 
            else { pet.maxHp += 10; pet.hp = (pet.hp + 10).coerceAtMost(pet.maxHp); printLog("\n> 🧪 Potion glows GREEN! Max HP increased!") }
            saveParty(); updatePartyScreen(); updateBattleUI()
            triggerEnemyCounterAttack()
        }
    }

    // MID-BATTLE SPRAY
    findViewById<Button>(R.id.btnBattleSpray).setOnClickListener {
    cancelBattleTimer()
        if (battleOver || playerLastStand) return@setOnClickListener
        val pet = party[activePetIndex]
        if (pet.eqSprays > 0) {
            pet.eqSprays--
            printLog("\n> 💨 ${pet.name} used a Repel Spray!")
            saveParty(); updatePartyScreen(); updateBattleUI()
            
            if (isUnderAttack && Random.nextInt(100) < 5) { printLog("> CRITICAL SUCCESS! Boss flees."); endBattle() } 
            else if (isWildBattle && Random.nextInt(100) < 60) { printLog("> The wild ${currentEnemy?.name} was blinded and ran away!"); endBattle() } 
            else { printLog("> No effect..."); triggerEnemyCounterAttack() }
        }
    }

    // MID-BATTLE NET (Takes a turn!)
    findViewById<Button>(R.id.btnBattleNet).setOnClickListener {
    cancelBattleTimer()
        if (battleOver || playerLastStand || currentEnemy == null) return@setOnClickListener
        val pet = party[activePetIndex]
        if (pet.eqNets <= 0) return@setOnClickListener
        
        pet.eqNets--
        saveParty(); updatePartyScreen(); updateBattleUI()

        printLog("\n--- PLAYER TURN ---")
        printLog("> You threw a Net at ${currentEnemy?.name}!")

        if (currentEnemy?.type == "EventBoss" || isTrainerBattle) {
            printLog("> ❌ You cannot catch this beast! The net bounced off!")
            mainHandler.postDelayed({ triggerEnemyCounterAttack() }, 1000)
            return@setOnClickListener
        }

        // QTE Catch Math
        val totalNets = nets + party.sumOf { it.eqNets }
        val baseCatchChance = 15 + (25.0 * Math.exp(-0.05 * totalNets)).toInt()
        val isClear = pet.infusionStacks > 0 && pet.infusionEl == "Clear"
        val catchPenalty = (exploreDifficulty / 100f * 20).toInt()
        val catchChance = (if (isClear) baseCatchChance + 15 else baseCatchChance) - catchPenalty
        
        if (Random.nextInt(100) < catchChance) {
            printLog("> 🌟 SUCCESS! You caught ${currentEnemy?.name} mid-battle!")
            currentEnemy!!.isNew = true; party.add(currentEnemy!!)
            saveParty(); updatePartyScreen(); updateDispatchButton(); endBattle()
        } else {
            printLog("> 💢 The net broke! ${currentEnemy?.name} breaks free!")
            mainHandler.postDelayed({ triggerEnemyCounterAttack() }, 1000)
        }
    }

    findViewById<Button>(R.id.btnAbandon).setOnClickListener {
    cancelBattleTimer()
        if (battleOver || playerLastStand) return@setOnClickListener
        DialogUtils.showCustomDialog(this, "Abandon Netbeast?", "If you flee now, ${party[activePetIndex].name} will be lost forever. Are you sure?", true, "ABANDON", { printLog("\n> You ABANDONED ${party[activePetIndex].name} to save yourself!"); AnimUtils.animDeath(findViewById(R.id.spritePlayer)); party.removeAt(activePetIndex); activePetIndex = 0; saveParty(); endBattle() }, null)
    }
}

internal fun GameActivity.setupDispatchControl() {
    findViewById<Button>(R.id.btnDispatch).setOnClickListener {
        if (aetherDepleted) { Toast.makeText(this, "Aether depleted! Return tomorrow.", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
        if (party.isEmpty()) {
            if (activeExpeditions.containsKey(-1)) printLog("> You are already exploring!") else { activeExpeditions[-1] = System.currentTimeMillis() + (180 * 1000); printLog("\n> You bravely step out into the wild to explore..."); SaveManager.saveExpeditions(prefs, activeExpeditions); updateDispatchButton() }
            return@setOnClickListener
        }
        val availablePets = party.indices.filter { !activeExpeditions.containsKey(it) }
        if (availablePets.isEmpty()) { printLog("> All Netbeasts are exploring!"); return@setOnClickListener }

        var dispatched = 0
        for (idx in availablePets) { activeExpeditions[idx] = System.currentTimeMillis() + (180 * 1000); printLog("\n> Dispatched ${party[idx].name} to explore..."); dispatched++ }
        if (dispatched > 0) { SaveManager.saveExpeditions(prefs, activeExpeditions); updateDispatchButton() }
    }

    val btnFightAether = findViewById<Button>(R.id.btnFightAether)
    val aetherRunnable = object : Runnable {
        override fun run() {
            if (isFightAetherActive && activeExpeditions.isEmpty()) {
                aetherSeconds++
                tvAether.text = "Aether: ${String.format("%02d:%02d", aetherSeconds / 60, aetherSeconds % 60)}"
                tvAether.setTextColor(android.graphics.Color.parseColor("#E040FB"))
                mainHandler.postDelayed(this, 1250)
            }
        }
    }
    
    btnFightAether.setOnTouchListener { _, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> { if (activeExpeditions.isEmpty()) { isFightAetherActive = true; mainHandler.postDelayed(aetherRunnable, 1250) } }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { isFightAetherActive = false; tvAether.setTextColor(android.graphics.Color.parseColor("#00BCD4")); mainHandler.removeCallbacks(aetherRunnable) }
        }
        false
    }
}
