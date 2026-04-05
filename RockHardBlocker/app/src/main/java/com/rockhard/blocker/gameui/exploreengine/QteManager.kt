package com.rockhard.blocker

import android.view.View
import android.widget.Button
import android.widget.TextView
import kotlin.random.Random

internal fun GameActivity.spawnWildQTE(petIndex: Int, pet: Netbeast) {
    val baseBeast = GameData.beasts.random()
    val partyPenalty = (party.size - 1) * 15
    val variance = (pet.maxHp * 0.15).toInt()
    val pl = Random.nextInt(pet.maxHp - variance, pet.maxHp + variance).coerceAtLeast(10) + partyPenalty
    // FIX: 19 Variables!
    val wildBeast = Netbeast(baseBeast.name, "Wild", pl, pl, baseBeast.m1, baseBeast.m2, "Tackle", 0L, 0, 0, 0, false, "None", 0, 0, 0, 0, "None", 0)

    if (partyPenalty > 0) printLog("\n> ⚠️ Your large party attracted a stronger threat (+$partyPenalty HP)!")
    printLog("> ⚠️ ${pet.name} spotted a wild ${wildBeast.name} (HP: $pl)!")

    if (pet.expedsDone >= 2 && Random.nextInt(100) < 20) evolveBeast(petIndex)

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
        printLog("\n> You signaled ${pet.name} to sneak away. Safe.")
    }

    val btnQteNet = qteView.findViewById<Button>(R.id.btnQteNet); val activeP = party[petIndex]
    if (activeP.eqNets > 0) {
        btnQteNet.text = "CAST NET (${activeP.eqNets})"
        btnQteNet.setOnClickListener {
            activeP.eqNets--; saveParty(); updatePartyScreen(); mainHandler.removeCallbacks(expireTask); qteContainer.removeView(qteView); activeQTEs.remove(petIndex)
            if (Random.nextBoolean()) {
                printLog("\n> \uD83C\uDF1F SUCCESS! You caught ${wildBeast.name}!"); wildBeast.isNew = true; party.add(wildBeast); saveParty(); updatePartyScreen(); updateDispatchButton()
            } else {
                if (activeP.eqNets <= 0) printLog("\n> The net broke! ${activeP.name} is out of nets. The wild ${wildBeast.name} wanders off.") 
                else { printLog("\n> The net broke! ${wildBeast.name} is angry! AMBUSH!"); startWildBattle(petIndex, wildBeast) }
            }
        }
    } else btnQteNet.visibility = View.GONE

    qteView.findViewById<Button>(R.id.btnQteAmbush).setOnClickListener {
        mainHandler.removeCallbacks(expireTask); qteContainer.removeView(qteView); activeQTEs.remove(petIndex)
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
    qteContainer.addView(qteView); activeQTEs[petIndex] = qteView; mainHandler.postDelayed(expireTask, 5000)
}

internal fun GameActivity.startWildBattle(petIndex: Int, wildBeast: Netbeast) {
    qteContainer.removeAllViews(); activeQTEs.clear(); currentEnemy = wildBeast; activePetIndex = petIndex; isWildBattle = true; battleOver = false
    setUIState("BATTLE"); showBattleArena(party[petIndex].name, wildBeast.name); updateBattleUI()
}