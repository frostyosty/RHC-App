package com.rockhard.blocker

import android.view.View
import android.widget.Button
import android.widget.TextView
import kotlin.random.Random

internal fun GameActivity.spawnWildQTE(petIndex: Int, pet: Netbeast) {
    val isPlayer = petIndex == -1
    val baseBeast = GameData.beasts.random()
    val partyPenalty = if (party.isEmpty()) 0 else (party.size - 1) * 15
    
    // SLIDER MATH (0 = Danger, 100 = Safe)
    val safeFactor = exploreDifficulty / 100f
    val levelMult = 1.5f - (safeFactor * 0.7f) // 1.5x at Danger, 0.8x at Safe
    
    val variance = (pet.maxHp * 0.15).toInt()
    val baseHp = Random.nextInt(pet.maxHp - variance, pet.maxHp + variance)
    val pl = ((baseHp * levelMult).toInt()).coerceAtLeast(10) + partyPenalty
    
    var spawnName = baseBeast.name
    if (Random.nextInt(100) < 10) spawnName = "[Obscure] $spawnName"
    val wildBeast = Netbeast(spawnName, "Wild", pl, pl, baseBeast.m1, baseBeast.m2, "Tackle", 0L, 0, 0, 0, false, "None", 0, 0, 0, 0, "None", 0)

    if (partyPenalty > 0) printLog("\n> ⚠️ Your large party attracted a stronger threat (+$partyPenalty HP)!")
    printLog("> ⚠️ ${pet.name} spotted a wild ${wildBeast.name} (HP: $pl)!")

    val qteView = layoutInflater.inflate(R.layout.item_qte_row, qteContainer, false)
    qteView.findViewById<TextView>(R.id.tvQteDesc).text = "Wild ${wildBeast.name} spotted by ${pet.name}!"

    val expireTask = Runnable {
        if (activeQTEs.containsKey(petIndex)) {
            qteContainer.removeView(qteView); activeQTEs.remove(petIndex)
            printLog("\n> You hesitated...")
            if (wildBeast.maxHp >= (pet.maxHp * 0.8)) { printLog("💢 The wild ${wildBeast.name} ambushed ${pet.name}!"); startWildBattle(petIndex, wildBeast) } 
            else printLog("> The wild ${wildBeast.name} got bored and left.")
        }
    }

    qteView.findViewById<Button>(R.id.btnQteAvoid).setOnClickListener {
        mainHandler.removeCallbacks(expireTask); qteContainer.removeView(qteView); activeQTEs.remove(petIndex)
        printLog("\n> You sneaked away. Safe.")
    }

    val btnQteNet = qteView.findViewById<Button>(R.id.btnQteNet)
    val hasNets = if (isPlayer) nets > 0 else party[petIndex].eqNets > 0
    var netCount = if (isPlayer) nets else party[petIndex].eqNets

    if (hasNets) {
        btnQteNet.text = "CAST NET ($netCount)"
        btnQteNet.setOnClickListener {
            if (isPlayer) nets-- else party[petIndex].eqNets--
            netCount--
            btnQteNet.text = "CAST NET ($netCount)"
            if (netCount <= 0) btnQteNet.visibility = View.GONE
            saveItems(); saveParty(); updatePartyScreen(); updateBagScreen()
            
            // LOGARITHMIC CATCH RATE WITH SLIDER PENALTY
            val totalNets = nets + party.sumOf { it.eqNets }
            val baseCatchChance = 15 + (25.0 * Math.exp(-0.05 * totalNets)).toInt()
            val isClear = !isPlayer && party[petIndex].infusionStacks > 0 && party[petIndex].infusionEl == "Clear"
            
            val catchPenalty = (safeFactor * 20).toInt() // -0% at Danger, -20% at Safe
            val catchChance = (if (isClear) baseCatchChance + (15 * party[petIndex].infusionStacks) else baseCatchChance) - catchPenalty
            
            if (Random.nextInt(100) < catchChance) {
                mainHandler.removeCallbacks(expireTask); qteContainer.removeView(qteView); activeQTEs.remove(petIndex)
                printLog("\n> 🌟 SUCCESS! You caught ${wildBeast.name}!")
                wildBeast.isNew = true; party.add(wildBeast)
                if (isPlayer) activeExpeditions.remove(-1) 
                saveParty(); updatePartyScreen(); updateDispatchButton()
            } else {
                if (Random.nextInt(100) < 30) {
                    mainHandler.removeCallbacks(expireTask); qteContainer.removeView(qteView); activeQTEs.remove(petIndex)
                    printLog("\n> 💢 The net broke! ${wildBeast.name} is angry and ATTACKS!")
                    startWildBattle(petIndex, wildBeast)
                } else {
                    if (netCount > 0) printLog("\n> The net broke! But ${wildBeast.name} hasn't fled yet.") 
                    else printLog("\n> The net broke! You are out of nets. The wild ${wildBeast.name} wanders off.")
                }
            }
        }
    } else btnQteNet.visibility = View.GONE

    qteView.findViewById<Button>(R.id.btnQteAmbush).setOnClickListener {
        mainHandler.removeCallbacks(expireTask)
        
        // NEW FIX: Wipe all other QTEs off the screen and lock the combat state instantly!
        qteContainer.removeAllViews()
        activeQTEs.clear()
        isWildBattle = true 
        
        if (isPlayer) {
            printLog("\n> You bravely charge at ${wildBeast.name}!"); startWildBattle(-1, wildBeast)
        } else {
            val wildLvl = wildBeast.maxHp / 10
            val options = party.map { p -> val diff = (p.maxHp / 10) - wildLvl; "${p.name} Lvl ${p.maxHp / 10} (${if (diff >= 0) "+$diff" else "$diff"})" }.toTypedArray()

            DialogUtils.showCustomDialog(this, "Who will Ambush?", null, false, "", null) { content, dialog ->
                party.forEachIndexed { chosenIdx, p ->
                    val btn = Button(this).apply { text = options[chosenIdx]; setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(android.graphics.Color.WHITE); layoutParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
                        setOnClickListener { printLog("\n> You ordered ${p.name} to AMBUSH ${wildBeast.name}!"); startWildBattle(chosenIdx, wildBeast); dialog.dismiss() }
                    }; content.addView(btn)
                }
            }
        }
    }
    qteContainer.addView(qteView); activeQTEs[petIndex] = qteView; mainHandler.postDelayed(expireTask, 5000)
}

internal fun GameActivity.startWildBattle(petIndex: Int, wildBeast: Netbeast) {
    qteContainer.removeAllViews(); activeQTEs.clear(); currentEnemy = wildBeast; isWildBattle = true; battleOver = false
    setUIState("BATTLE")
    if (petIndex == -1) { playerLastStand = true; showBattleArena("YOU", wildBeast.name) } 
    else { activePetIndex = petIndex; showBattleArena(party[petIndex].name, wildBeast.name) }
    updateBattleUI()
}

internal fun GameActivity.spawnRescueQTE(capturedBeast: Netbeast, suburb: String) {
    if (activeQTEs.values.any { it.findViewById<TextView>(R.id.tvQteDesc)?.text?.contains(capturedBeast.name) == true }) return

    printLog("\n🚨 DISTRESS BEACON DETECTED! 🚨\n> ${capturedBeast.name} is being held by a Poacher in $suburb!")

    val qteView = layoutInflater.inflate(R.layout.item_qte_row, qteContainer, false)
    qteView.findViewById<TextView>(R.id.tvQteDesc).text = "Rescue ${capturedBeast.name} from Poacher?"

    qteView.findViewById<Button>(R.id.btnQteAvoid).apply {
        text = "LEAVE"
        setOnClickListener { qteContainer.removeView(qteView); printLog("> You ignored the beacon.") }
    }

    qteView.findViewById<Button>(R.id.btnQteAmbush).apply {
        text = "FIGHT POACHER"
        setBackgroundResource(R.drawable.bg_btn_accent)
        setOnClickListener {
            qteContainer.removeAllViews(); activeQTEs.clear()

            val playerAvgLvl = if (party.isNotEmpty()) (party.sumOf { it.maxHp / 10 } / party.size).coerceAtLeast(1) else 1
            val poacherAvgLvl = (playerAvgLvl - 2).coerceAtLeast(1)
            val poacherPartySize = (party.size - 2).coerceAtLeast(2)

            enemyParty.clear()
            for (i in 0 until poacherPartySize) {
                val baseBeast = GameData.beasts.random()
                val pl = (poacherAvgLvl * 10) + kotlin.random.Random.nextInt(-10, 10)
                enemyParty.add(Netbeast(baseBeast.name, "Poacher", pl, pl, baseBeast.m1, baseBeast.m2, "Tackle", 0L, 0, 1, 0, false, "None", 0, 0, 0, 0, "None", 0))
            }

            capturedRescueTarget = capturedBeast
            isTrainerBattle = true; isWildBattle = false; battleOver = false
            currentEnemy = enemyParty[0]
            
            setUIState("BATTLE"); printLog("\n> 🦹 POACHER: 'Come and take it!'")
            
            if (party.isEmpty()) { playerLastStand = true; showBattleArena("YOU", currentEnemy!!.name) } 
            else { activePetIndex = 0; showBattleArena(party[0].name, currentEnemy!!.name) }
            updateBattleUI()
        }
    }
    qteView.findViewById<Button>(R.id.btnQteNet).visibility = View.GONE
    qteContainer.addView(qteView)
    
    val expireTask = Runnable { qteContainer.removeView(qteView) }
    activeQTEs[capturedBeast.name.hashCode()] = qteView
    mainHandler.postDelayed(expireTask, 20000)
}