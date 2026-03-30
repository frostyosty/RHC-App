package com.rockhard.blocker

import android.view.View
import android.widget.Button
import android.widget.TextView
import kotlin.random.Random

internal fun GameActivity.performGlobalTick() {
    if (forSaleParty.isNotEmpty() && Random.nextInt(100) < 15) { 
        val sold = forSaleParty.removeAt(0); focusCoins += sold.listedPrice
        printLog("\n> \uD83D\uDCB0 MARKET ALERT! A buyer purchased your ${sold.name} for ${sold.listedPrice}c!")
        SaveManager.saveParty(prefs, "FORSALE_DATA", forSaleParty); saveItems(); updateBagScreen() 
    }
    
    if (activeExpeditions.isEmpty()) return
    val now = System.currentTimeMillis(); val toRemove = mutableListOf<Int>()
    
    for (petIndex in activeExpeditions.keys.toList()) {
        if (petIndex >= party.size) { toRemove.add(petIndex); continue }
        val pet = party[petIndex]
        if (now >= activeExpeditions[petIndex]!!) { 
            printLog("\n> \uD83C\uDFC1 EXPEDITION COMPLETE! ${pet.name} returned to the Hub.")
            toRemove.add(petIndex); continue 
        }
        if (activeQTEs.containsKey(petIndex)) continue
        
        if (Random.nextInt(100) < 25) {
            if (isUnderAttack || isWildBattle) {
                if (petIndex == activePetIndex) continue // ACTIVE PET DOESNT SCAVENGE
                if (Random.nextBoolean()) { nets++; printLog("> \uD83D\uDEE1\uFE0F While you battle, ${pet.name} scavenged a Net!") } 
                else { val c = Random.nextInt(5,15); focusCoins += c; printLog("> \uD83D\uDEE1\uFE0F While you battle, ${pet.name} scavenged $c Coins!") }
                saveItems(); updateBagScreen()
            } else {
                val roll = Random.nextInt(100)
                if (roll < 30) { printLog("> ${pet.name} found a Net!"); nets++; saveItems(); updateBagScreen() }
                else if (roll < 50) { printLog("> ${pet.name} found a Potion!"); potions++; saveItems(); updateBagScreen() }
                else if (roll < 70) { val c = Random.nextInt(5,15); focusCoins += c; printLog("> ${pet.name} found $c Focus Coins!"); saveItems(); updateBagScreen() }
                else spawnWildQTE(petIndex, pet)
            }
        }
    }
    toRemove.forEach { activeExpeditions.remove(it) }
    if (toRemove.isNotEmpty()) { SaveManager.saveExpeditions(prefs, activeExpeditions); updateDispatchButton() }
}

internal fun GameActivity.spawnWildQTE(petIndex: Int, pet: Netbeast) {
    val baseBeast = GameData.beasts.random()
    val partyPenalty = (party.size - 1) * 15
    val variance = (pet.maxHp * 0.15).toInt()
    val pl = Random.nextInt(pet.maxHp - variance, pet.maxHp + variance).coerceAtLeast(10) + partyPenalty
    val wildBeast = Netbeast(baseBeast.name, "Wild", pl, pl, baseBeast.m1, baseBeast.m2, 0L, 0, 0, 0, false, "None", 0, 0)
    
    if (partyPenalty > 0) printLog("\n> ⚠️ Your large party attracted a stronger threat (+${partyPenalty} HP)!")
    printLog("> ⚠️ ${pet.name} spotted a wild ${wildBeast.name} (HP: $pl)!")
    
    val qteView = layoutInflater.inflate(R.layout.item_qte_row, qteContainer, false)
    qteView.findViewById<TextView>(R.id.tvQteDesc).text = "Wild ${wildBeast.name} spotted by ${pet.name}!"
    
    val expireTask = Runnable {
        if (activeQTEs.containsKey(petIndex)) {
            qteContainer.removeView(qteView); activeQTEs.remove(petIndex)
            printLog("\n> You hesitated...")
            if (wildBeast.maxHp >= (pet.maxHp * 0.8)) { 
                printLog("💢 The wild ${wildBeast.name} ambushed ${pet.name}!")
                startWildBattle(petIndex, wildBeast) 
            } else printLog("> The wild ${wildBeast.name} got bored and left.")
        }
    }
    
    qteView.findViewById<Button>(R.id.btnQteAvoid).setOnClickListener { 
        mainHandler.removeCallbacks(expireTask); qteContainer.removeView(qteView); activeQTEs.remove(petIndex)
        printLog("\n> You signaled ${pet.name} to sneak away. Safe.") 
    }
    
    val btnQteNet = qteView.findViewById<Button>(R.id.btnQteNet); val activeP = party[petIndex]
    if (activeP.eqNets > 0) {
        btnQteNet.text = "CAST NET (${activeP.eqNets})"
        btnQteNet.setOnClickListener {
            activeP.eqNets--; saveParty(); updatePartyScreen()
            mainHandler.removeCallbacks(expireTask); qteContainer.removeView(qteView); activeQTEs.remove(petIndex)
            if (Random.nextBoolean()) {
                printLog("\n> \uD83C\uDF1F SUCCESS! You caught ${wildBeast.name}!")
                wildBeast.isNew = true; party.add(wildBeast); saveParty(); updatePartyScreen(); updateDispatchButton()
            } else {
                if (activeP.eqNets <= 0) printLog("\n> The net broke! ${activeP.name} is out of nets. The wild ${wildBeast.name} wanders off.") 
                else { printLog("\n> The net broke! ${wildBeast.name} is angry! AMBUSH!"); startWildBattle(petIndex, wildBeast) }
            }
        }
    } else { btnQteNet.visibility = View.GONE }
    
    qteView.findViewById<Button>(R.id.btnQteAmbush).setOnClickListener { 
        mainHandler.removeCallbacks(expireTask); qteContainer.removeView(qteView); activeQTEs.remove(petIndex)
        DialogUtils.showCustomDialog(this, "Who will Ambush?", null, false, "", null) { content, dialog -> 
            party.forEachIndexed { chosenIdx, p -> 
                val btn = Button(this).apply { 
                    text = "${p.name}"; setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(android.graphics.Color.WHITE)
                    layoutParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
                    setOnClickListener { printLog("\n> You ordered ${p.name} to AMBUSH ${wildBeast.name}!"); startWildBattle(chosenIdx, wildBeast); dialog.dismiss() } 
                }; content.addView(btn) 
            } 
        }
    }
    qteContainer.addView(qteView); activeQTEs[petIndex] = qteView; mainHandler.postDelayed(expireTask, 5000)
}

internal fun GameActivity.startWildBattle(petIndex: Int, wildBeast: Netbeast) {
    qteContainer.removeAllViews(); activeQTEs.clear(); currentEnemy = wildBeast; activePetIndex = petIndex
    isWildBattle = true; battleOver = false
    setUIState("BATTLE"); showBattleArena(party[petIndex].name, wildBeast.name); updateBattleUI()
}

internal fun GameActivity.checkOfflineExpeditions() {
    if (activeExpeditions.isEmpty()) return
    val now = System.currentTimeMillis(); val toRemove = mutableListOf<Int>()
    for ((idx, endTime) in activeExpeditions) {
        if (idx >= party.size) { toRemove.add(idx); continue }
        if (now >= endTime) {
            toRemove.add(idx)
            if (Random.nextInt(100) < 30) {
                val wildName = GameData.beasts.random().name; val pl = Random.nextInt(40, 160)
                currentEnemy = Netbeast(wildName, "Wild", pl, pl, "Tackle", "Bite", 0L, 0, 0, 0, false, "None", 0, 0)
                isWildBattle = true; activePetIndex = idx; setUIState("BATTLE"); showBattleArena(party[idx].name, wildName)
                printLog("⚠️ OFFLINE AMBUSH!\n> While you were away, ${party[idx].name} was attacked by a wild $wildName!"); updateBattleUI(); break
            } else { val c = Random.nextInt(5, 15); focusCoins += c; printLog("> \uD83C\uDFC1 Offline: ${party[idx].name} returned with $c Focus Coins!") }
        } else printLog("> ${party[idx].name} is still exploring...")
    }
    toRemove.forEach { activeExpeditions.remove(it) }; saveItems(); updateBagScreen(); SaveManager.saveExpeditions(prefs, activeExpeditions); updateDispatchButton()
}
