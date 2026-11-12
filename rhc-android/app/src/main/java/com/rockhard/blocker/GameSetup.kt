package com.rockhard.blocker

import android.graphics.Color
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import com.rockhard.blocker.world.battle.FightInput
import com.rockhard.blocker.world.battle.FightItem

internal fun GameActivity.setupBattleControls() {
    // the buttons say what you want; the rules (FightHost.kt) decide whether it happens
    findViewById<Button>(R.id.btnMove1).setOnClickListener { fightInput(FightInput(move = 1)) }
    findViewById<Button>(R.id.btnMove2).setOnClickListener { fightInput(FightInput(move = 2)) }
    findViewById<Button>(R.id.btnMove3).setOnClickListener { fightInput(FightInput(move = 3)) }

    findViewById<Button>(R.id.btnSwap).setOnClickListener {
        val v = fightView() ?: return@setOnClickListener
        val me = v.me
        val enemy = currentEnemy
        if (battleOver || playerLastStand || enemy == null || me.living().size <= 1) return@setOnClickListener
        val wildLvl = enemy.maxHp / 10
        DialogUtils.showCustomDialog(this, "Swap to who?", null, true, "", null) { content, dialog ->
            me.living().forEach { i ->
                val p = me.party[i]
                val diff = (p.maxHp / 10) - wildLvl
                val btn = Button(this).apply { text = "${p.name} Lvl ${p.maxHp / 10} (${if (diff >= 0) "+$diff" else diff})"; setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
                    setOnClickListener { fightInput(FightInput(cage = i)); dialog.dismiss() }
                }; content.addView(btn)
            }
        }
    }

    findViewById<Button>(R.id.btnBattlePot).setOnClickListener { if ((myFighter()?.eqPots ?: 0) > 0) fightInput(FightInput(item = FightItem.POTION)) }
    findViewById<Button>(R.id.btnBattleSpray).setOnClickListener { if ((myFighter()?.eqSprays ?: 0) > 0) fightInput(FightInput(item = FightItem.SPRAY)) }
    // takes a turn
    findViewById<Button>(R.id.btnBattleNet).setOnClickListener { if ((myFighter()?.eqNets ?: 0) > 0) fightInput(FightInput(item = FightItem.NET)) }

    findViewById<Button>(R.id.btnAbandon).setOnClickListener {
        val p = myFighter() ?: return@setOnClickListener
        if (battleOver || playerLastStand) return@setOnClickListener
        DialogUtils.showCustomDialog(this, "Abandon Netbeast?", "If you flee now, ${p.name} will be lost forever. Are you sure?", true, "ABANDON", { fightInput(FightInput(abandon = true)) }, null)
    }
}

internal fun GameActivity.setupDispatchControl() {
    setupWorldPreview()
    findViewById<Button>(R.id.btnDispatch).setOnClickListener {
        if (aetherDepleted) { Toast.makeText(this, "Aether depleted! Return tomorrow.", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
        if (world3dEnabled) { enterWorld(); return@setOnClickListener }
        if (party.isEmpty()) {
            if (activeExpeditions.containsKey(-1)) printLog("> You are already exploring!") else { activeExpeditions[-1] = System.currentTimeMillis() + EXPEDITION_MS; prefs.edit().putInt("CLOAK_LEFT", if (Relics.cloakOn(prefs)) Relics.CLOAK_BEASTS else 0).apply(); printLog("\n> You bravely step out into the wild to explore..."); SaveManager.saveExpeditions(prefs, activeExpeditions); updateDispatchButton() }
            return@setOnClickListener
        }
        val availablePets = party.indices.filter { !activeExpeditions.containsKey(it) }
        if (availablePets.isEmpty()) { printLog("> All Netbeasts are exploring!"); return@setOnClickListener }

        var dispatched = 0
        for (idx in availablePets) { activeExpeditions[idx] = System.currentTimeMillis() + EXPEDITION_MS; printLog("\n> Dispatched ${party[idx].name} to explore..."); dispatched++ }
        if (dispatched > 0) { SaveManager.saveExpeditions(prefs, activeExpeditions); updateDispatchButton() }
    }
}
