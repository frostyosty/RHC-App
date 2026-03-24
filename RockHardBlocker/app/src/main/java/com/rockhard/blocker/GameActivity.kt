package com.rockhard.blocker

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

data class Netbeast(val name: String, val type: String, var hp: Int, val maxHp: Int, val move1: String, val move2: String)

class GameActivity : Activity() {
    private lateinit var tvConsole: TextView; private lateinit var tvParty: TextView; private lateinit var tvBag: TextView
    private lateinit var prefs: SharedPreferences
    
    private var isUnderAttack = false; private var bossName = "The Dark One"; private var battleOver = false
    private var sprays = 0; private var potions = 0; private var nets = 0
    private var party = mutableListOf<Netbeast>()
    private var activePetIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)
        prefs = getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE)

        tvConsole = findViewById(R.id.tvConsole); tvParty = findViewById(R.id.tvParty); tvBag = findViewById(R.id.tvBag)
        loadSaveData()

        isUnderAttack = intent.getBooleanExtra("UNDER_ATTACK", false)
        if (getString(R.string.flavor_id) == "rhc") bossName = listOf("Pornosaur", "GoreIlla", "Fleshwire").random()

        setupTabs(); updatePartyScreen(); updateBagScreen(); updateBattleUI()

        if (isUnderAttack) {
            findViewById<View>(R.id.navTabs).visibility = View.GONE; findViewById<View>(R.id.peaceControls).visibility = View.GONE; findViewById<View>(R.id.battleControls).visibility = View.VISIBLE
            printLog("⚠️ EMERGENCY: $bossName HAS INVADED THE SANCTUARY!")
            if (party.isNotEmpty()) printLog("> You sent out ${party[activePetIndex].name} to defend the squad!")
        } else {
            findViewById<View>(R.id.navTabs).visibility = View.VISIBLE; findViewById<View>(R.id.peaceControls).visibility = View.VISIBLE; findViewById<View>(R.id.battleControls).visibility = View.GONE
            printLog("> INITIALIZING NETBEAST SAFARI..."); fetchLocationSilent()
        }

        findViewById<Button>(R.id.btnExit).setOnClickListener { finish() }

        findViewById<Button>(R.id.btnDispatch).setOnClickListener {
            if (party.isEmpty()) { printLog("> You have no Netbeasts to send out!"); return@setOnClickListener }
            val explorer = party[activePetIndex].name
            printLog("\n> You dispatched $explorer to explore the local area...")
            it.isEnabled = false
            
            Handler(Looper.getMainLooper()).postDelayed({
                val event = Random.nextInt(100)
                if (event < 25) { printLog("> $explorer found a Capture Net in the bushes!"); nets++; saveItems(); updateBagScreen() } 
                else if (event < 50) { printLog("> $explorer dug up a Health Potion!"); potions++; saveItems(); updateBagScreen() } 
                else if (event < 60) { printLog("> $explorer found a Repel Spray!"); sprays++; saveItems(); updateBagScreen() } 
                else if (event < 85) {
                    val wild = listOf("Chirplet", "Cartini", "Noobit", "Atomit", "Roamlet").random()
                    printLog("> WILD ENCOUNTER! $explorer stumbled upon a wild $wild!")
                    if (nets > 0) {
                        printLog("> You automatically threw a Net!")
                        nets--
                        if (Random.nextBoolean()) {
                            printLog("> SUCCESS! You caught $wild!")
                            party.add(Netbeast(wild, "Wild", 100, 100, "Tackle", "Dodge"))
                            saveParty(); updatePartyScreen()
                        } else printLog("> Oh no! $wild broke free and ran away!")
                        saveItems(); updateBagScreen()
                    } else printLog("> You have no Nets! The wild $wild ran away.")
                } else printLog("> $explorer returned empty-handed. Try again!")
                
                it.isEnabled = true
            }, 3000)
        }

        findViewById<Button>(R.id.btnMove1).setOnClickListener { executePlayerMove(party[activePetIndex].move1) }
        findViewById<Button>(R.id.btnMove2).setOnClickListener { executePlayerMove(party[activePetIndex].move2) }
        
        findViewById<Button>(R.id.btnSwap).setOnClickListener {
            if (battleOver || party.size <= 1) return@setOnClickListener
            activePetIndex = (activePetIndex + 1) % party.size
            printLog("\n> You swapped to ${party[activePetIndex].name}!"); updateBattleUI(); triggerBossCounterAttack()
        }

        findViewById<Button>(R.id.btnItem).setOnClickListener {
            if (battleOver || sprays <= 0) return@setOnClickListener
            sprays--; saveItems(); updateBagScreen(); printLog("\n> You used a Repel Spray! ($sprays left)")
            if (Random.nextInt(100) < 5) { printLog("> CRITICAL SUCCESS! $bossName flees."); battleOver = true; saveParty() } 
            else { printLog("> No effect... $bossName is enraged!"); triggerBossCounterAttack() }
        }
    }

    private fun loadSaveData() {
        sprays = prefs.getInt("SPRAYS", 0); potions = prefs.getInt("POTIONS", 0); nets = prefs.getInt("NETS", 0)
        val data = prefs.getString("PARTY_DATA", "") ?: ""
        party.clear()
        if (data.isNotEmpty()) data.split(";").forEach { val p = it.split(","); if (p.size == 6) party.add(Netbeast(p[0], p[1], p[2].toInt(), p[3].toInt(), p[4], p[5])) }
    }
    private fun saveParty() { prefs.edit().putString("PARTY_DATA", party.joinToString(";") { "${it.name},${it.type},${it.hp},${it.maxHp},${it.move1},${it.move2}" }).apply() }
    private fun saveItems() { prefs.edit().putInt("SPRAYS", sprays).putInt("POTIONS", potions).putInt("NETS", nets).apply() }

    private fun executePlayerMove(moveName: String) {
        if (battleOver) return
        printLog("\n> ${party[activePetIndex].name} used $moveName!")
        Handler(Looper.getMainLooper()).postDelayed({ printLog("> $bossName EVADED your attack! It is invincible!"); triggerBossCounterAttack() }, 1000)
    }

    private fun triggerBossCounterAttack() {
        if (party.isEmpty() || battleOver) return
        Handler(Looper.getMainLooper()).postDelayed({
            val target = party[activePetIndex]
            val damage = Random.nextInt(40, 80)
            target.hp -= damage
            printLog("🩸 $bossName strikes ${target.name} for $damage damage!")
            if (target.hp <= 0) {
                printLog("💀 ${target.name} HAS BEEN KILLED!")
                party.removeAt(activePetIndex); activePetIndex = 0
                if (party.isEmpty()) { printLog("> All Netbeasts are dead..."); battleOver = true } else { updateBattleUI(); printLog("> You send out ${party[activePetIndex].name} in desperation!") }
            }
            updatePartyScreen(); saveParty()
            if (!battleOver && Random.nextBoolean()) bossFlees()
        }, 1500)
    }

    private fun bossFlees() { Handler(Looper.getMainLooper()).postDelayed({ printLog("\n> $bossName fades into the shadows..."); battleOver = true }, 2000) }

    private fun fetchLocationSilent() {
        Thread {
            try {
                val city = URL("http://ip-api.com/json/").openConnection().inputStream.bufferedReader().readText().substringAfter("\"city\":\"").substringBefore("\"")
                runOnUiThread { printLog("> Current Base: $city\n> Awaiting your orders.") }
            } catch (e: Exception) { runOnUiThread { printLog("> Current Base: Local Sanctuary\n> Awaiting orders.") } }
        }.start()
    }

    private fun printLog(msg: String) {
        tvConsole.text = "${tvConsole.text}\n$msg"
        findViewById<ScrollView>(R.id.viewActivity).post { findViewById<ScrollView>(R.id.viewActivity).fullScroll(View.FOCUS_DOWN) }
    }

    private fun updateBattleUI() {
        if (party.isNotEmpty()) { findViewById<Button>(R.id.btnMove1).text = party[activePetIndex].move1; findViewById<Button>(R.id.btnMove2).text = party[activePetIndex].move2 }
    }

    private fun updatePartyScreen() {
        var pText = "=== YOUR NETBEASTS ===\n\n"
        if (party.isEmpty()) pText += "Your party is empty. You are defenseless.\n"
        for ((index, p) in party.withIndex()) {
            val activeTag = if (index == activePetIndex) "[ACTIVE] " else ""
            val hpPercent = (p.hp.toFloat() / p.maxHp.toFloat()) * 10
            val bar = "█".repeat(hpPercent.toInt().coerceAtLeast(0)) + "-".repeat((10 - hpPercent.toInt()).coerceAtLeast(0))
            pText += "$activeTag${p.name} (${p.type})\nHP:[$bar] ${p.hp.coerceAtLeast(0)}/${p.maxHp}\n\n"
        }
        tvParty.text = pText
    }

    private fun updateBagScreen() { tvBag.text = "=== INVENTORY ===\n\n🕸️ Nets: $nets\n🧪 Potions: $potions\n💨 Sprays: $sprays\n" }

    private fun setupTabs() {
        val b1 = findViewById<Button>(R.id.tabActivity); val b2 = findViewById<Button>(R.id.tabParty); val b3 = findViewById<Button>(R.id.tabBag)
        val v1 = findViewById<ScrollView>(R.id.viewActivity); val v2 = findViewById<ScrollView>(R.id.viewParty); val v3 = findViewById<ScrollView>(R.id.viewBag)
        b1.setOnClickListener { v1.visibility = View.VISIBLE; v2.visibility = View.GONE; v3.visibility = View.GONE }
        b2.setOnClickListener { v1.visibility = View.GONE; v2.visibility = View.VISIBLE; v3.visibility = View.GONE }
        b3.setOnClickListener { v1.visibility = View.GONE; v2.visibility = View.GONE; v3.visibility = View.VISIBLE }
    }
}
