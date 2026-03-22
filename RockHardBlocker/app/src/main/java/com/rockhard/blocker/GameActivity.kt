package com.rockhard.blocker

import android.app.Activity
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

class GameActivity : Activity() {

    private lateinit var tvConsole: TextView
    private lateinit var tvParty: TextView
    private lateinit var tvBag: TextView
    
    private var isUnderAttack = false
    private var bossName = "The Dark One"
    private var battleOver = false
    
    private var sprays = 3
    private var potions = 5
    private var nets = 10
    private var party = mutableListOf("TechLine: Cacheon", "FitnessLine: Cardiol", "GamingLine: Skirmalot")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        tvConsole = findViewById(R.id.tvConsole)
        tvParty = findViewById(R.id.tvParty)
        tvBag = findViewById(R.id.tvBag)

        isUnderAttack = intent.getBooleanExtra("UNDER_ATTACK", false)
        if (getString(R.string.flavor_id) == "rhc") bossName = listOf("Pornosaur", "GoreIlla", "Fleshwire").random()

        setupTabs()
        updatePartyScreen()
        updateBagScreen()

        // UI SWAP: Battle vs Hub
        val navTabs = findViewById<View>(R.id.navTabs)
        val battleControls = findViewById<View>(R.id.battleControls)
        val peaceControls = findViewById<View>(R.id.peaceControls)

        if (isUnderAttack) {
            navTabs.visibility = View.GONE
            peaceControls.visibility = View.GONE
            battleControls.visibility = View.VISIBLE
            
            printLog("⚠️ EMERGENCY: $bossName HAS INVADED THE SANCTUARY!")
            printLog("Your Bouncemons are terrified. You must DEFEND them!")
        } else {
            navTabs.visibility = View.VISIBLE
            peaceControls.visibility = View.VISIBLE
            battleControls.visibility = View.GONE
            
            printLog("> INITIALIZING BOUNCEMON SAFARI...")
            printLog("> Welcome back to the Hub.")
            fetchLocationSilent()
        }

        findViewById<Button>(R.id.btnExit).setOnClickListener { finish() }

        // PEACEFUL EXPLORATION LOGIC
        findViewById<Button>(R.id.btnDispatch).setOnClickListener {
            printLog("\n> You sent Cacheon out to explore the area.")
            printLog("> Cacheon will return later with items or a wild encounter!")
            it.isEnabled = false // Disable button after sending
        }

        // BATTLE LOGIC
        findViewById<Button>(R.id.btnAttack).setOnClickListener {
            if (battleOver) return@setOnClickListener
            printLog("\n> You order your Bouncemon to ATTACK!")
            Handler(Looper.getMainLooper()).postDelayed({
                printLog("> $bossName EVADED your attack! It is too fast!")
                triggerBossCounterAttack()
            }, 1000)
        }

        findViewById<Button>(R.id.btnSpray).setOnClickListener {
            if (battleOver) return@setOnClickListener
            if (sprays <= 0) { printLog("> You are out of Sprays!"); return@setOnClickListener }
            sprays--; updateBagScreen(); printLog("\n> You used a Repel Spray! ($sprays left)")
            if (Random.nextInt(100) < 5) {
                printLog("> CRITICAL SUCCESS! The spray burned $bossName! It flees.")
                battleOver = true
            } else {
                printLog("> It had no effect... $bossName is enraged!")
                triggerBossCounterAttack()
            }
        }
    }

    private fun fetchLocationSilent() {
        Thread {
            try {
                val conn = URL("http://ip-api.com/json/").openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                val res = conn.inputStream.bufferedReader().readText()
                val city = res.substringAfter("\"city\":\"").substringBefore("\"")
                runOnUiThread { printLog("> Current Base: $city\n> Awaiting your orders.") }
            } catch (e: Exception) { runOnUiThread { printLog("> Current Base: Local Sanctuary") } }
        }.start()
    }

    private fun triggerBossCounterAttack() {
        if (party.isEmpty()) { printLog("> All your Bouncemons are already dead..."); battleOver = true; return }
        Handler(Looper.getMainLooper()).postDelayed({
            val victim = party.random(); party.remove(victim); updatePartyScreen()
            printLog("🩸 $bossName strikes! $victim was KILLED!")
            Handler(Looper.getMainLooper()).postDelayed({ printLog("\n> $bossName fades into the shadows..."); battleOver = true }, 1500)
        }, 1500)
    }

    private fun printLog(msg: String) {
        tvConsole.text = "${tvConsole.text}\n$msg"
        findViewById<ScrollView>(R.id.viewActivity).post { findViewById<ScrollView>(R.id.viewActivity).fullScroll(View.FOCUS_DOWN) }
    }

    private fun updatePartyScreen() {
        var pText = "=== YOUR BOUNCEMONS ===\n\n"
        if (party.isEmpty()) pText += "Your party is empty. You are defenseless.\n"
        for (p in party) pText += "[Sprite Placeholder] $p\n- Health: 100/100\n\n"
        tvParty.text = pText
    }

    private fun updateBagScreen() {
        tvBag.text = "=== INVENTORY ===\n\n🕸️ Nets: $nets\n🧪 Potions: $potions\n💨 Sprays: $sprays\n"
    }

    private fun setupTabs() {
        val b1 = findViewById<Button>(R.id.tabActivity); val b2 = findViewById<Button>(R.id.tabParty); val b3 = findViewById<Button>(R.id.tabBag)
        val v1 = findViewById<ScrollView>(R.id.viewActivity); val v2 = findViewById<ScrollView>(R.id.viewParty); val v3 = findViewById<ScrollView>(R.id.viewBag)
        b1.setOnClickListener { v1.visibility = View.VISIBLE; v2.visibility = View.GONE; v3.visibility = View.GONE }
        b2.setOnClickListener { v1.visibility = View.GONE; v2.visibility = View.VISIBLE; v3.visibility = View.GONE }
        b3.setOnClickListener { v1.visibility = View.GONE; v2.visibility = View.GONE; v3.visibility = View.VISIBLE }
    }
}
