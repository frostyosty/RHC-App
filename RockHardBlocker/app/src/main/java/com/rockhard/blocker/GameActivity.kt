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
import android.widget.TextView
import android.widget.Toast

class GameActivity : Activity() {
    internal lateinit var tvConsole: TextView
    internal lateinit var tvBag: TextView
    internal lateinit var tvBagConsole: TextView
    internal lateinit var tvAether: TextView
    internal lateinit var partyContainer: LinearLayout
    internal lateinit var qteContainer: LinearLayout
    internal lateinit var prefs: SharedPreferences

    internal var aetherSeconds = 600
    internal var aetherDepleted = false
    internal var exploreDifficulty = 50
    internal var totalExpeds = 0
    internal var graveyard = mutableListOf<Netbeast>()
    internal var isFightAetherActive = false

    internal var playerId = ""
    internal var playerName = ""
    internal var isUnderAttack = false
    internal var isWildBattle = false
    internal var battleOver = false
    internal var playerLastStand = false
    internal var playerHasTripleStrike = false
    internal var playerHasEvasion = false
    internal var currentEnemy: Netbeast? = null
    internal var enemyParty = mutableListOf<Netbeast>()
    internal var isTrainerBattle = false
    internal var capturedRescueTarget: Netbeast? = null

    internal var sprays = 0
    internal var smallHpPots = 0
    internal var largeHpPots = 0
    internal var potions = 0
    internal var nets = 0
    internal var focusCoins = 0
    internal var transfusers = 0
    internal var party = mutableListOf<Netbeast>()
    internal var activePetIndex = 0
    internal var forSaleParty = mutableListOf<Netbeast>()
    internal var marketBeasts = mutableListOf<Netbeast>()
    internal val activeOffers = mutableMapOf<String, Int>()

    internal var currentWeather = "Clear"
    internal var weatherIcon = "☀️"
    internal var currentCity = "Local Sanctuary"

    internal val activeExpeditions = mutableMapOf<Int, Long>()
    internal val activeQTEs = mutableMapOf<Int, View>()
    internal val participatingPets = mutableSetOf<Int>()

    internal var battleTimerRunnable: Runnable? = null
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
        tvBagConsole = findViewById(R.id.tvBagConsole)
        tvAether = findViewById(R.id.tvAether)
        partyContainer = findViewById(R.id.partyContainer)
        qteContainer = findViewById(R.id.qteContainer)

        try {
            loadSaveData()
        } catch (e: Exception) {
            wipeCorruptSave()
        }

        // Initialize Aether Engine!
        if (aetherSeconds == 600 && !isUnderAttack) {
            aetherSeconds = AetherEngine.calculateStartingAether(prefs)
            val rems = prefs.getInt("CURRENT_REMNANTS", 0)
            if (rems > 0) runOnUiThread { Toast.makeText(this, "$rems:00 unused aether remnants added!", Toast.LENGTH_LONG).show() }
        }

        generateMarket()
        setupTabs()
        setupShop()
        setupEquipPanel()
        setupBattleControls()
        setupDispatchControl()
        updatePartyScreen()
        updateBagScreen()

        findViewById<Button>(R.id.btnExit).setOnClickListener { finish() }

        val seekDifficulty = findViewById<android.widget.SeekBar>(R.id.seekDifficulty)
        seekDifficulty?.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: android.widget.SeekBar?,
                    progress: Int,
                    fromUser: Boolean,
                ) {
                    exploreDifficulty =
                        progress
                }

                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            },
        )

        intent?.let { handleIncomingIntent(it) }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent?.let { handleIncomingIntent(it) }
    }

    private fun handleIncomingIntent(i: Intent) {
        isUnderAttack = i.getBooleanExtra("UNDER_ATTACK", false)
        if (isUnderAttack) {
            val triggerReason = i.getStringExtra("TRIGGER_REASON") ?: "Anomaly"
            val bossName = i.getStringExtra("BOSS_NAME") ?: "The Dark One"

            currentEnemy =
                Netbeast(
                    "[Ephemeral] [Elusive] [Colossal] $bossName",
                    "Corrupted",
                    8000,
                    8000,
                    "Doom",
                    "Crash",
                    "Annihilate",
                    0L,
                    0,
                    0,
                    0,
                    false,
                    "None",
                    0,
                    0,
                    0,
                    0,
                    "None",
                    0,
                )

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
                this,
                onSuccess = { city, weather, icon, terrain, debugStr ->
                    currentCity = city
                    currentWeather = weather
                    weatherIcon = icon
                    prefs
                        .edit()
                        .putString("CURRENT_CITY", city)
                        .putString("DEBUG_API_DATA", debugStr)
                        .apply()
                    runOnUiThread {
                        printLog("\n=== WEATHER UPLINK ===")
                        printLog("> Base: $city")
                        printLog("> Terrain: $terrain")
                        printLog("> Weather: $weather $icon")
                        printLog("> " + debugStr.replace("\n", "\n> "))
                        printLog("======================")
                        findViewById<Button>(R.id.btnInfuse)?.text = "INFUSE WITH CURRENT WEATHER ($icon)"
                    }
                },
                onFail = { reason ->
                    runOnUiThread {
                        printLog(
                            "\n> ⚠️ UPLINK FAILED: $reason\n> Base Locked: Local Sanctuary\n> Weather: Offline\n> Awaiting orders.",
                        )
                    }
                },
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
            .putInt("SPRAYS", sprays)
            .putInt("POTIONS", potions)
            .putInt("NETS", nets)
            .putInt("COINS", focusCoins)
            .putInt("TRANSFUSERS", transfusers)
            .putInt("SMALL_HP_POTS", smallHpPots)
            .putInt("LARGE_HP_POTS", largeHpPots)
            .apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        SaveManager.saveExpeditions(prefs, activeExpeditions)
        AudioEngine.release()
    }

    internal fun printLog(msg: String) {
        runOnUiThread {
            tvConsole.text = "${tvConsole.text}\n$msg"
            (tvConsole.parent as? android.widget.ScrollView)?.post {
                (tvConsole.parent as? android.widget.ScrollView)?.fullScroll(android.view.View.FOCUS_DOWN)
            }
        }
    }

    internal fun printShopLog(msg: String) {
        runOnUiThread {
            tvBagConsole.text = "${tvBagConsole.text}\n$msg"
            (tvBagConsole.parent as? android.widget.ScrollView)?.post {
                (tvBagConsole.parent as? android.widget.ScrollView)?.fullScroll(android.view.View.FOCUS_DOWN)
            }
        }
    }
}
