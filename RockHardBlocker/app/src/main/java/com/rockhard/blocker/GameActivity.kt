package com.rockhard.blocker

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

data class Netbeast(val name: String, val type: String, var hp: Int, val maxHp: Int, val move1: String, val move2: String, val boughtAt: Long, var eqNets: Int, var eqPots: Int, var eqSprays: Int, var isNew: Boolean) {
    fun isGrouchy(): Boolean = (System.currentTimeMillis() - boughtAt) < (24 * 60 * 60 * 1000) && boughtAt > 0
}

class GameActivity : Activity() {
    private lateinit var tvConsole: TextView; private lateinit var tvParty: TextView; private lateinit var tvBag: TextView
    private lateinit var qteContainer: LinearLayout
    private lateinit var prefs: SharedPreferences
    
    private var isUnderAttack = false; private var isWildBattle = false; private var battleOver = false
    private var currentEnemy: Netbeast? = null
    
    private var sprays = 0; private var potions = 0; private var nets = 0; private var focusCoins = 0
    private var party = mutableListOf<Netbeast>()
    private var activePetIndex = 0

    private val activeExpeditions = mutableMapOf<Int, Long>()
    private val activeQTEs = mutableMapOf<Int, View>()
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private val exploreRunnable = object : Runnable { override fun run() { performGlobalTick(); mainHandler.postDelayed(this, 4000) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)
        prefs = getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE)

        tvConsole = findViewById(R.id.tvConsole); tvParty = findViewById(R.id.tvParty); tvBag = findViewById(R.id.tvBag)
        qteContainer = findViewById(R.id.qteContainer)
        loadSaveData()

        val btnRhcSettings = findViewById<Button>(R.id.btnRhcSettings)
        if (prefs.getBoolean("LAUNCH_GAME_DEFAULT", false)) {
            btnRhcSettings.visibility = View.VISIBLE
            btnRhcSettings.setOnClickListener { startActivity(Intent(this, MainActivity::class.java).apply { putExtra("FROM_GAME", true) }) }
        }

        setupTabs(); setupShop(); setupEquipPanel(); updatePartyScreen(); updateBagScreen()

        isUnderAttack = intent.getBooleanExtra("UNDER_ATTACK", false)
        if (isUnderAttack) {
            val bossName = if (getString(R.string.flavor_id) == "rhc") listOf("Pronosaur", "GoreIlla", "Fleshwire").random() else "The Dark One"
            currentEnemy = Netbeast(bossName, "Corrupted", 9999, 9999, "Doom", "Crash", 0L, 0, 0, 0, false)
            setUIState("BATTLE")
            printLog("\n==================================")
            printLog("⚠️ EMERGENCY: ${currentEnemy?.name} HAS INVADED!")
            printLog("==================================")
            if (party.isNotEmpty()) printLog("> You sent out ${party[activePetIndex].name} to defend!")
            updateBattleUI()
        } else {
            setUIState("HUB")
            printLog("> INITIALIZING NETBEAST SAFARI...\n> Current Base: Secured\n")
            checkOfflineExpeditions()
            mainHandler.postDelayed(exploreRunnable, 3000)
        }

        findViewById<Button>(R.id.btnExit).setOnClickListener { finish() }

        findViewById<Button>(R.id.btnMove1).setOnClickListener { executePlayerMove(party[activePetIndex].move1) }
        findViewById<Button>(R.id.btnMove2).setOnClickListener { executePlayerMove(party[activePetIndex].move2) }
        findViewById<Button>(R.id.btnSwap).setOnClickListener {
            if (battleOver || party.size <= 1) return@setOnClickListener
            activePetIndex = (activePetIndex + 1) % party.size
            printLog("\n> You swapped to ${party[activePetIndex].name}!"); updateBattleUI(); triggerEnemyCounterAttack()
        }
        
        // BATTLE ITEM USE (Now uses the ACTIVE PET's equipped inventory!)
        findViewById<Button>(R.id.btnItem).setOnClickListener {
            if (battleOver) return@setOnClickListener
            val pet = party[activePetIndex]
            if (pet.eqSprays <= 0) { printLog("> ${pet.name} has no Sprays equipped!"); return@setOnClickListener }
            pet.eqSprays--; saveParty(); updatePartyScreen()
            
            printLog("\n> ${pet.name} used a Repel Spray!")
            if (isUnderAttack && Random.nextInt(100) < 5) { printLog("> CRITICAL SUCCESS! Boss flees."); endBattle() } 
            else if (isWildBattle && Random.nextInt(100) < 60) { printLog("> The wild ${currentEnemy?.name} was blinded and ran away!"); endBattle() }
            else { printLog("> No effect..."); triggerEnemyCounterAttack() }
        }
        
        findViewById<Button>(R.id.btnAbandon).setOnClickListener {
            if (battleOver) return@setOnClickListener
            printLog("\n> You ABANDONED ${party[activePetIndex].name} to save yourself!")
            party.removeAt(activePetIndex); activePetIndex = 0; saveParty(); endBattle()
        }

        findViewById<Button>(R.id.btnDispatch).setOnClickListener {
            val availablePets = party.indices.filter { !activeExpeditions.containsKey(it) }
            if (availablePets.isEmpty()) { printLog("> All Netbeasts are exploring!"); return@setOnClickListener }
            AlertDialog.Builder(this).setTitle("Dispatch Netbeast:")
                .setItems(availablePets.map { party[it].name }.toTypedArray()) { _, i ->
                    val chosenIndex = availablePets[i]
                    activeExpeditions[chosenIndex] = System.currentTimeMillis() + (180 * 1000)
                    saveExpeditions(); printLog("\n> Dispatched ${party[chosenIndex].name} to explore..."); updateDispatchButton()
                }.show()
        }
    }

    private fun setupEquipPanel() {
        findViewById<Button>(R.id.btnEqNet).setOnClickListener { if (party.isEmpty()) return@setOnClickListener; if (nets > 0) { nets--; party[activePetIndex].eqNets++; saveItems(); saveParty(); updatePartyScreen(); updateBagScreen() } else Toast.makeText(this, "No Nets in Bag!", Toast.LENGTH_SHORT).show() }
        findViewById<Button>(R.id.btnEqPot).setOnClickListener { if (party.isEmpty()) return@setOnClickListener; if (potions > 0) { potions--; party[activePetIndex].eqPots++; saveItems(); saveParty(); updatePartyScreen(); updateBagScreen() } else Toast.makeText(this, "No Potions in Bag!", Toast.LENGTH_SHORT).show() }
        findViewById<Button>(R.id.btnEqSpray).setOnClickListener { if (party.isEmpty()) return@setOnClickListener; if (sprays > 0) { sprays--; party[activePetIndex].eqSprays++; saveItems(); saveParty(); updatePartyScreen(); updateBagScreen() } else Toast.makeText(this, "No Sprays in Bag!", Toast.LENGTH_SHORT).show() }
        findViewById<Button>(R.id.btnUnequip).setOnClickListener {
            if (party.isEmpty()) return@setOnClickListener
            val p = party[activePetIndex]; nets += p.eqNets; potions += p.eqPots; sprays += p.eqSprays
            p.eqNets = 0; p.eqPots = 0; p.eqSprays = 0
            saveItems(); saveParty(); updatePartyScreen(); updateBagScreen()
        }
    }

    private fun setupShop() {
        fun handleTx(cost: Int, isBuy: Boolean, itemType: String) {
            if (isBuy) {
                if (focusCoins >= cost) { focusCoins -= cost; when (itemType) { "NET" -> nets++; "POTION" -> potions++; "SPRAY" -> sprays++ }; printLog("> Bought 1 $itemType for ${cost}c.") } 
                else printLog("> Not enough Focus Coins!")
            } else {
                var hasItem = false
                when (itemType) { "NET" -> if (nets > 0) { nets--; hasItem = true }; "POTION" -> if (potions > 0) { potions--; hasItem = true }; "SPRAY" -> if (sprays > 0) { sprays--; hasItem = true } }
                if (hasItem) { focusCoins += cost; printLog("> Sold 1 $itemType for ${cost}c.") } else printLog("> You don't have any ${itemType}s to sell!")
            }
            saveItems(); updateBagScreen()
        }
        findViewById<Button>(R.id.btnBuyNet).setOnClickListener { handleTx(5, true, "NET") }; findViewById<Button>(R.id.btnSellNet).setOnClickListener { handleTx(2, false, "NET") }
        findViewById<Button>(R.id.btnBuyPotion).setOnClickListener { handleTx(10, true, "POTION") }; findViewById<Button>(R.id.btnSellPotion).setOnClickListener { handleTx(5, false, "POTION") }
        findViewById<Button>(R.id.btnBuySpray).setOnClickListener { handleTx(15, true, "SPRAY") }; findViewById<Button>(R.id.btnSellSpray).setOnClickListener { handleTx(7, false, "SPRAY") }
        
        findViewById<Button>(R.id.btnBuyBeast).setOnClickListener {
            if (focusCoins >= 100) {
                focusCoins -= 100; val wild = listOf("Chirplet", "Cartini", "Noobit", "Atomit", "Roamlet").random()
                party.add(Netbeast(wild, "Bought", 100, 100, "Tackle", "Bite", System.currentTimeMillis(), 0, 0, 0, true))
                saveItems(); saveParty(); updatePartyScreen(); updateBagScreen(); printLog("\n> 🛒 You bought a wild $wild for 100c!\n> It feels [Grouchy] about being traded.")
            } else printLog("> Not enough Focus Coins! Need 100c.")
        }
        findViewById<Button>(R.id.btnSellBeast).setOnClickListener {
            if (party.size <= 1) { printLog("> You cannot sell your last Netbeast!"); return@setOnClickListener }
            if (activeExpeditions.containsKey(activePetIndex)) { printLog("> Cannot sell a Netbeast while it is exploring!"); return@setOnClickListener }
            val soldName = party[activePetIndex].name; party.removeAt(activePetIndex); activePetIndex = 0; focusCoins += 50
            saveItems(); saveParty(); updatePartyScreen(); updateBagScreen(); printLog("\n> 💸 You sold $soldName to the Safari Lodge for 50c.")
        }
    }

    private fun checkOfflineExpeditions() {
        if (activeExpeditions.isEmpty()) return
        val now = System.currentTimeMillis()
        val toRemove = mutableListOf<Int>()

        for ((idx, endTime) in activeExpeditions) {
            if (now >= endTime) {
                toRemove.add(idx)
                if (Random.nextInt(100) < 30) {
                    val wildName = listOf("Chirplet", "Cartini", "Noobit", "Atomit", "Roamlet").random()
                    val pl = Random.nextInt(40, 160)
                    currentEnemy = Netbeast(wildName, "Wild", pl, pl, "Tackle", "Bite", 0L, 0, 0, 0, false)
                    isWildBattle = true; activePetIndex = idx; setUIState("BATTLE")
                    printLog("⚠️ OFFLINE AMBUSH!\n> While you were away, ${party[idx].name} was attacked by a wild $wildName!")
                    updateBattleUI(); break
                } else { val c = Random.nextInt(5, 15); focusCoins += c; printLog("> 🏁 Offline: ${party[idx].name} returned with $c Focus Coins!") }
            } else printLog("> ${party[idx].name} is still exploring...")
        }
        toRemove.forEach { activeExpeditions.remove(it) }
        saveItems(); updateBagScreen(); saveExpeditions(); updateDispatchButton()
    }

    private fun performGlobalTick() {
        if (activeExpeditions.isEmpty()) return
        val now = System.currentTimeMillis()
        val toRemove = mutableListOf<Int>()

        for (petIndex in activeExpeditions.keys.toList()) {
            val pet = party[petIndex]
            if (now >= activeExpeditions[petIndex]!!) {
                printLog("\n> 🏁 EXPEDITION COMPLETE! ${pet.name} returned to the Hub."); toRemove.add(petIndex); continue
            }
            if (activeQTEs.containsKey(petIndex)) continue

            if (Random.nextInt(100) < 25) {
                if (isUnderAttack || isWildBattle) {
                    if (Random.nextBoolean()) { nets++; printLog("> 🛡️ While you battle, ${pet.name} scavenged a Net!") } 
                    else { val c = Random.nextInt(5,15); focusCoins += c; printLog("> 🛡️ While you battle, ${pet.name} scavenged $c Coins!") }
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
        toRemove.forEach { activeExpeditions.remove(it) }; if (toRemove.isNotEmpty()) { saveExpeditions(); updateDispatchButton() }
    }

    private fun spawnWildQTE(petIndex: Int, pet: Netbeast) {
        val wildName = listOf("Chirplet", "Cartini", "Noobit", "Atomit", "Roamlet").random()
        val pl = Random.nextInt(40, 160)
        val wildBeast = Netbeast(wildName, "Wild", pl, pl, "Tackle", "Bite", 0L, 0, 0, 0, false)
        printLog("\n> ⚠️ ${pet.name} spotted a wild ${wildBeast.name} (HP: $pl)!")
        val qteView = layoutInflater.inflate(R.layout.item_qte_row, qteContainer, false)
        qteView.findViewById<TextView>(R.id.tvQteDesc).text = "Wild ${wildBeast.name} spotted by ${pet.name}!"
        
        val expireTask = Runnable {
            if (activeQTEs.containsKey(petIndex)) {
                qteContainer.removeView(qteView); activeQTEs.remove(petIndex); printLog("\n> You hesitated...")
                if (wildBeast.maxHp >= (pet.maxHp * 0.8)) { printLog("💢 The wild ${wildBeast.name} ambushed ${pet.name}!"); startWildBattle(petIndex, wildBeast) } 
                else printLog("> The wild ${wildBeast.name} got bored and left.")
            }
        }
        qteView.findViewById<Button>(R.id.btnQteAvoid).setOnClickListener { mainHandler.removeCallbacks(expireTask); qteContainer.removeView(qteView); activeQTEs.remove(petIndex); printLog("\n> You signaled ${pet.name} to sneak away. Safe.") }
        
        qteView.findViewById<Button>(R.id.btnQteNet).setOnClickListener {
            val activeP = party[petIndex]
            if (activeP.eqNets <= 0) { printLog("\n> ${activeP.name} has no nets equipped!"); return@setOnClickListener }
            activeP.eqNets--; saveParty(); updatePartyScreen()
            mainHandler.removeCallbacks(expireTask); qteContainer.removeView(qteView); activeQTEs.remove(petIndex)
            
            if (Random.nextBoolean()) {
                printLog("\n> 🌟 SUCCESS! You caught ${wildBeast.name}!")
                wildBeast.isNew = true // SHINY NEW TAG!
                party.add(wildBeast); saveParty(); updatePartyScreen(); updateDispatchButton()
            } else {
                if (activeP.eqNets <= 0) { printLog("\n> The net broke! ${activeP.name} is out of nets. The wild ${wildBeast.name} wanders off in pity.") }
                else { printLog("\n> The net broke! ${wildBeast.name} is angry! AMBUSH!"); startWildBattle(petIndex, wildBeast) }
            }
        }
        qteView.findViewById<Button>(R.id.btnQteAmbush).setOnClickListener { mainHandler.removeCallbacks(expireTask); qteContainer.removeView(qteView); activeQTEs.remove(petIndex); printLog("\n> You ordered ${pet.name} to AMBUSH ${wildBeast.name}!"); startWildBattle(petIndex, wildBeast) }
        qteContainer.addView(qteView); activeQTEs[petIndex] = qteView; mainHandler.postDelayed(expireTask, 5000)
    }

    private fun startWildBattle(petIndex: Int, wildBeast: Netbeast) {
        qteContainer.removeAllViews(); activeQTEs.clear(); currentEnemy = wildBeast; activePetIndex = petIndex; isWildBattle = true; battleOver = false; setUIState("BATTLE"); updateBattleUI()
    }

    private fun executePlayerMove(moveName: String) {
        if (battleOver) return
        val activePet = party[activePetIndex]
        printLog("\n--- PLAYER TURN ---")
        if (activePet.isGrouchy() && Random.nextInt(100) < 30) { printLog("> 😡 ${activePet.name} is feeling [Grouchy] about being traded!"); printLog("> ${activePet.name} ignored your command to use $moveName!"); Handler(Looper.getMainLooper()).postDelayed({ triggerEnemyCounterAttack() }, 1500); return }
        printLog("> ${activePet.name} attempts [$moveName]")
        if (isUnderAttack) { Handler(Looper.getMainLooper()).postDelayed({ printLog("> ${currentEnemy?.name} EVADED! It is invincible!"); triggerEnemyCounterAttack() }, 1000) } 
        else {
            val baseDmg = Random.nextInt(20, 50); var finalDmg = baseDmg
            if (Random.nextInt(100) < 15) { finalDmg = (baseDmg * 1.5).toInt(); printLog("> 💥 CRITICAL HIT!") }
            currentEnemy!!.hp -= finalDmg
            Handler(Looper.getMainLooper()).postDelayed({
                printLog("> Hit! ${currentEnemy?.name} takes $finalDmg damage. (HP: ${currentEnemy!!.hp.coerceAtLeast(0)}/${currentEnemy!!.maxHp})")
                if (currentEnemy!!.hp <= 0) { val coinsWon = Random.nextInt(10, 25); focusCoins += coinsWon; saveItems(); updateBagScreen(); printLog("> 🏆 VICTORY! The wild ${currentEnemy?.name} fainted!"); printLog("> You found $coinsWon Focus Coins!"); endBattle() } 
                else triggerEnemyCounterAttack()
            }, 1000)
        }
    }

    private fun triggerEnemyCounterAttack() {
        if (party.isEmpty() || battleOver) return
        val target = party[activePetIndex]; val damage = if (isUnderAttack) Random.nextInt(40, 80) else Random.nextInt(10, 30)
        printLog("\n--- ENEMY TURN ---"); printLog("> ${currentEnemy?.name} attempts [Brutal Strike] on ${target.name}...")
        Handler(Looper.getMainLooper()).postDelayed({
            target.hp -= damage; printLog("> 🩸 Hit! ${target.name} takes $damage damage."); printLog("> ${target.name} gains [Winded] status (-10 Speed).")
            if (target.hp <= 0) {
                printLog("> 💀 ${target.name} HAS BEEN KILLED!")
                party.removeAt(activePetIndex); activePetIndex = 0
                if (party.isEmpty()) { printLog("> All Netbeasts are dead..."); endBattle() } else { updateBattleUI(); printLog("> You send out ${party[activePetIndex].name} in desperation!") }
            }
            updatePartyScreen(); saveParty()
            if (isUnderAttack && !battleOver && Random.nextBoolean()) Handler(Looper.getMainLooper()).postDelayed({ printLog("\n> ${currentEnemy?.name} fades into the shadows..."); endBattle() }, 2000)
        }, 1500)
    }

    private fun endBattle() {
        battleOver = true; isUnderAttack = false; isWildBattle = false
        Handler(Looper.getMainLooper()).postDelayed({
            if (party.isEmpty()) { printLog("> You blacked out and returned to the Hub."); setUIState("HUB") }
            else { printLog("> Returning to exploration..."); setUIState("HUB") }
        }, 2000)
    }

    private fun setUIState(state: String) {
        val nav = findViewById<View>(R.id.navTabs); val peace = findViewById<View>(R.id.peaceControls); val battle = findViewById<View>(R.id.battleControls)
        nav.visibility = if (state == "BATTLE") View.GONE else View.VISIBLE
        peace.visibility = if (state == "BATTLE") View.GONE else View.VISIBLE
        battle.visibility = if (state == "BATTLE") View.VISIBLE else View.GONE
        qteContainer.visibility = if (state == "BATTLE") View.GONE else View.VISIBLE
        updateDispatchButton()
    }

    private fun updateDispatchButton() {
        val btn = findViewById<Button>(R.id.btnDispatch) ?: return
        if (activeExpeditions.size >= party.size && party.isNotEmpty()) { btn.text = "ALL NETBEASTS DEPLOYED"; btn.isEnabled = false }
        else { btn.text = "DISPATCH NETBEAST (${activeExpeditions.size}/${party.size} deployed)"; btn.isEnabled = true }
    }

    private fun loadSaveData() {
        sprays = prefs.getInt("SPRAYS", 0); potions = prefs.getInt("POTIONS", 0); nets = prefs.getInt("NETS", 0); focusCoins = prefs.getInt("COINS", 0)
        val data = prefs.getString("PARTY_DATA", "") ?: ""; party.clear()
        if (data.isNotEmpty()) {
            data.split(";").forEach { val p = it.split(",")
                if (p.size >= 6) {
                    val bought = if (p.size >= 7) p[6].toLong() else 0L
                    val eN = if (p.size >= 11) p[7].toInt() else 0
                    val eP = if (p.size >= 11) p[8].toInt() else 0
                    val eS = if (p.size >= 11) p[9].toInt() else 0
                    val isNew = if (p.size >= 11) p[10].toBoolean() else false
                    party.add(Netbeast(p[0], p[1], p[2].toInt(), p[3].toInt(), p[4], p[5], bought, eN, eP, eS, isNew))
                }
            }
        }
        val expData = prefs.getString("EXP_DATA", "") ?: ""
        if (expData.isNotEmpty()) expData.split(";").forEach { val p = it.split(":"); if (p.size == 2) activeExpeditions[p[0].toInt()] = p[1].toLong() }
    }
    private fun saveParty() { prefs.edit().putString("PARTY_DATA", party.joinToString(";") { "${it.name},${it.type},${it.hp},${it.maxHp},${it.move1},${it.move2},${it.boughtAt},${it.eqNets},${it.eqPots},${it.eqSprays},${it.isNew}" }).apply() }
    private fun saveItems() { prefs.edit().putInt("SPRAYS", sprays).putInt("POTIONS", potions).putInt("NETS", nets).putInt("COINS", focusCoins).apply() }
    private fun saveExpeditions() { prefs.edit().putString("EXP_DATA", activeExpeditions.entries.joinToString(";") { "${it.key}:${it.value}" }).apply() }
    private fun fetchLocationSilent() { Thread { try { val city = URL("http://ip-api.com/json/").openConnection().inputStream.bufferedReader().readText().substringAfter("\"city\":\"").substringBefore("\""); runOnUiThread { printLog("> Current Base: $city\n> Awaiting orders.") } } catch (e: Exception) { runOnUiThread { printLog("> Current Base: Local Sanctuary\n> Awaiting orders.") } } }.start() }
    private fun printLog(msg: String) { tvConsole.text = "${tvConsole.text}\n$msg"; findViewById<ScrollView>(R.id.viewActivity).post { findViewById<ScrollView>(R.id.viewActivity).fullScroll(View.FOCUS_DOWN) } }
    private fun updateBattleUI() { if (party.isNotEmpty()) { findViewById<Button>(R.id.btnMove1).text = party[activePetIndex].move1; findViewById<Button>(R.id.btnMove2).text = party[activePetIndex].move2 } }
    
    private fun updatePartyScreen() {
        var pText = "=== YOUR NETBEASTS ===\n\n"
        if (party.isEmpty()) pText += "Party empty.\n"
        for ((index, p) in party.withIndex()) {
            val activeTag = if (index == activePetIndex && (isUnderAttack || isWildBattle)) "[ACTIVE] " else ""
            val newTag = if (p.isNew) "[✨ NEW!] " else ""
            val grouchyTag = if (p.isGrouchy()) "[Grouchy] " else ""
            val hpPercent = (p.hp.toFloat() / p.maxHp.toFloat()) * 10
            val bar = "█".repeat(hpPercent.toInt().coerceAtLeast(0)) + "-".repeat((10 - hpPercent.toInt()).coerceAtLeast(0))
            
            pText += "$newTag$activeTag$grouchyTag${p.name} (${p.type})\n"
            pText += "HP:[$bar] ${p.hp.coerceAtLeast(0)}/${p.maxHp}\n"
            pText += "Carrying: 🕸️${p.eqNets}  🧪${p.eqPots}  💨${p.eqSprays}\n\n"
        }
        tvParty.text = pText
    }

    private fun updateBagScreen() {
        val unlockShop = (nets >= 20 || potions >= 20 || focusCoins >= 1)
        findViewById<Button>(R.id.tabBag).text = if (unlockShop) "BAG/SHOP" else "BAG"
        findViewById<View>(R.id.llShopContainer).visibility = if (unlockShop) View.VISIBLE else View.GONE
        tvBag.text = "=== INVENTORY ===\n\n💰 Focus Coins: $focusCoins\n\n🕸️ Nets: $nets\n🧪 Potions: $potions\n💨 Sprays: $sprays\n"
    }

    private fun setupTabs() {
        val b1 = findViewById<Button>(R.id.tabActivity); val b2 = findViewById<Button>(R.id.tabParty); val b3 = findViewById<Button>(R.id.tabBag)
        val v1 = findViewById<ScrollView>(R.id.viewActivity); val v2 = findViewById<ScrollView>(R.id.viewParty); val v3 = findViewById<ScrollView>(R.id.viewBag)
        
        b1.setOnClickListener { v1.visibility = View.VISIBLE; v2.visibility = View.GONE; v3.visibility = View.GONE; b1.setTextColor(Color.GREEN); b2.setTextColor(Color.GRAY); b3.setTextColor(Color.GRAY) }
        b2.setOnClickListener { 
            v1.visibility = View.GONE; v2.visibility = View.VISIBLE; v3.visibility = View.GONE; b1.setTextColor(Color.GRAY); b2.setTextColor(Color.CYAN); b3.setTextColor(Color.GRAY)
            var changed = false
            party.forEach { if (it.isNew) { it.isNew = false; changed = true } }
            if (changed) { saveParty(); updatePartyScreen() }
        }
        b3.setOnClickListener { v1.visibility = View.GONE; v2.visibility = View.GONE; v3.visibility = View.VISIBLE; b1.setTextColor(Color.GRAY); b2.setTextColor(Color.GRAY); b3.setTextColor(Color.YELLOW); updateBagScreen() }
    }
    
    override fun onDestroy() { super.onDestroy(); mainHandler.removeCallbacksAndMessages(null); saveExpeditions() }
}
