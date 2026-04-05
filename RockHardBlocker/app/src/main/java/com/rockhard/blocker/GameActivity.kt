// /workspaces/RHC-App/RockHardBlocker/app/src/main/java/com/rockhard/blocker/GameActivity.kt
package com.rockhard.blocker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class GameActivity : Activity() {
    internal lateinit var tvConsole: TextView
    internal lateinit var tvBag: TextView
    internal lateinit var partyContainer: LinearLayout
    internal lateinit var qteContainer: LinearLayout
    internal lateinit var prefs: SharedPreferences

    internal var playerId = ""
    internal var playerName = ""
    internal var isUnderAttack = false
    internal var isWildBattle = false
    internal var battleOver = false
    internal var playerLastStand = false
    internal var playerHasInstaKill = false
    internal var playerHasEvasion = false
    internal var currentEnemy: Netbeast? = null

    internal var sprays = 0
    internal var potions = 0
    internal var nets = 0
    internal var focusCoins = 0
    internal var transfusers = 0
    internal var party = mutableListOf<Netbeast>()
    internal var activePetIndex = 0
    internal var forSaleParty = mutableListOf<Netbeast>()
    internal var marketBeasts = mutableListOf<Netbeast>()
    internal val activeOffers = mutableMapOf<String, Int>() // Maps Beast Name -> Coin Offer

    internal var currentWeather = "Clear"
    internal var weatherIcon = "☀️"
    internal var currentCity = "Local Sanctuary"

    internal val activeExpeditions = mutableMapOf<Int, Long>()
    internal val activeQTEs = mutableMapOf<Int, View>()

    internal val mainHandler = Handler(Looper.getMainLooper())
    private val exploreRunnable =
        object : Runnable {
            override fun run() {
                performGlobalTick()
                mainHandler.postDelayed(this, 4000)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)
        prefs = getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE)

        tvConsole = findViewById(R.id.tvConsole)
        tvBag = findViewById(R.id.tvBag)
        partyContainer = findViewById(R.id.partyContainer)
        qteContainer = findViewById(R.id.qteContainer)

        try {
            loadSaveData()
        } catch (e: Exception) {
            wipeCorruptSave()
        }

        generateMarket()

        val btnRhcSettings = findViewById<Button>(R.id.btnRhcSettings)
        if (prefs.getBoolean("LAUNCH_GAME_DEFAULT", false)) {
            btnRhcSettings.visibility = View.VISIBLE
            btnRhcSettings.setOnClickListener {
                startActivity(Intent(this, MainActivity::class.java).apply { putExtra("FROM_GAME", true) })
            }
        }

        setupTabs()
        setupShop()
        setupEquipPanel()
        setupBattleControls()
        setupDispatchControl()
        updatePartyScreen()
        updateBagScreen()

        findViewById<Button>(R.id.btnExit).setOnClickListener { finish() }

        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent != null) handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(i: Intent) {
        isUnderAttack = i.getBooleanExtra("UNDER_ATTACK", false)
        if (isUnderAttack) {
            val triggerReason = i.getStringExtra("TRIGGER_REASON") ?: "Anomaly"
            val bossName = if (getString(R.string.flavor_id) == "rhc") BossNameEngine.generateBossName(triggerReason) else "The Dark One"
            
            // FIXED: 19 Variables!
            currentEnemy = Netbeast(bossName, "Corrupted", 9999, 9999, "Doom", "Crash", "Annihilate", 0L, 0, 0, 0, false, "None", 0, 0, 0, 0, "None", 0)
            
            setUIState("BATTLE")
            printLog("\n==================================")
            printLog("⚠️ EMERGENCY: ${currentEnemy?.name} HAS INVADED!")
            printLog("==================================")
            if (party.isNotEmpty()) {
                printLog("> You sent out ${party[activePetIndex].name} to defend!")
                showBattleArena(party[activePetIndex].name, bossName)
            } else {
                playerLastStand = true
                printLog("> You have no Netbeasts! Defend yourself!")
                showBattleArena("YOU", bossName)
            }
            updateBattleUI()
        } else {
            setUIState("HUB")
            printLog("> INITIALIZING NETBEAST SAFARI...")
            printLog("> Welcome back, $playerName!")

            WeatherEngine.fetchSilent(
                onSuccess = { city, weather, icon, terrain, debugStr ->
                    currentCity = city; currentWeather = weather; weatherIcon = icon
                    prefs.edit().putString("CURRENT_CITY", city).putString("DEBUG_API_DATA", debugStr).apply()
                    runOnUiThread {
                        printLog("> Base Locked: $city\n> Terrain: $terrain\n> Weather: $weather $icon\n> Awaiting orders.")
                        findViewById<Button>(R.id.btnInfuse)?.text = "INFUSE WITH CURRENT WEATHER ($icon)"
                    }
                },
                onFail = {
                    runOnUiThread { printLog("> Base Locked: Local Sanctuary\n> Weather: Offline\n> Awaiting orders.") }
                }
            )

            processFleePenalty()
            checkOfflineExpeditions()
            mainHandler.postDelayed(exploreRunnable, 3000)
        }
    }

    internal fun saveParty() {
        SaveManager.saveParty(prefs, "PARTY_DATA", party)
    }

    internal fun saveItems() {
        prefs
            .edit()
            .putInt(
                "SPRAYS",
                sprays,
            ).putInt("POTIONS", potions)
            .putInt("NETS", nets)
            .putInt("COINS", focusCoins)
            .putInt("TRANSFUSERS", transfusers)
            .apply()
    }

    internal fun printLog(msg: String) {
        tvConsole.text = "${tvConsole.text}\n$msg"
        findViewById<ScrollView>(R.id.viewActivity).post { findViewById<ScrollView>(R.id.viewActivity).fullScroll(View.FOCUS_DOWN) }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        SaveManager.saveExpeditions(prefs, activeExpeditions)
    }
}
