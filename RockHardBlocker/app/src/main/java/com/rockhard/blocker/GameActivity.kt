package com.rockhard.blocker

/*
 * FILE: GameActivity.kt
 * 
 * INCLUDES: 
 * - UI Navigation (Explore, Party, Bag/Shop Tabs).
 * - Button listeners.
 * - Intent Handling (Waking up to Boss Fights from the Guardian).
 * 
 * EXCLUDES (Handled via internal Extension Functions elsewhere):
 * - Combat Engine (Damage calculations, Animations).
 * - Expedition Engine (Timers, Multi-Dispatch, QTE Spawners).
 * - Save Data Parsing -> SaveManager.kt
 * - Weather API Calls -> WeatherEngine.kt
 * - Beast/Trait Data  -> GameModels.kt
 */

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.*
import java.util.UUID

class GameActivity : Activity() {
    internal lateinit var tvConsole: TextView
    internal lateinit var tvParty: TextView
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
    internal var currentEnemy: Netbeast? = null
    
    internal var sprays = 0; internal var potions = 0; internal var nets = 0; internal var focusCoins = 0
    internal var party = mutableListOf<Netbeast>()
    internal var forSaleParty = mutableListOf<Netbeast>()
    internal var marketBeasts = mutableListOf<Pair<Netbeast, Int>>()
    internal var activePetIndex = 0

    internal var currentWeather = "Clear"
    internal var weatherIcon = "☀️"
    internal var currentCity = "Local Sanctuary"

    internal val activeExpeditions = mutableMapOf<Int, Long>()
    internal val activeQTEs = mutableMapOf<Int, View>()
    
    internal val mainHandler = Handler(Looper.getMainLooper())
    private val exploreRunnable = object : Runnable { 
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
        tvParty = findViewById(R.id.tvParty)
        partyContainer = findViewById(R.id.partyContainer)
        qteContainer = findViewById(R.id.qteContainer)

        try { loadSaveData() } catch (e: Exception) { wipeCorruptSave() }

        if (marketBeasts.isEmpty()) {
            for (i in 0 until 3) {
                val b = GameData.beasts.random()
                val lvl = kotlin.random.Random.nextInt(5, 20)
                val cost = (lvl * 5) + kotlin.random.Random.nextInt(-10, 10)
                marketBeasts.add(Pair(Netbeast(b.name, b.type, lvl*10, lvl*10, b.m1, b.m2, System.currentTimeMillis(), 0, 0, 0, true, "None", 0, 0), cost.coerceAtLeast(10)))
            }
        }

        val btnRhcSettings = findViewById<Button>(R.id.btnRhcSettings)
        if (prefs.getBoolean("LAUNCH_GAME_DEFAULT", false)) { 
            btnRhcSettings.visibility = View.VISIBLE
            btnRhcSettings.setOnClickListener { startActivity(Intent(this, MainActivity::class.java).apply { putExtra("FROM_GAME", true) }) }
        }

        setupTabs()
        setupShop()
        setupEquipPanel()
        updatePartyScreen()
        updateBagScreen()

        findViewById<Button>(R.id.btnExit).setOnClickListener { finish() }

        findViewById<Button>(R.id.btnMove1).setOnClickListener { if (playerLastStand) executeHumanPunch() else executePlayerMove(party[activePetIndex].move1) }
        findViewById<Button>(R.id.btnMove2).setOnClickListener { executePlayerMove(party[activePetIndex].move2) }

        findViewById<Button>(R.id.btnSwap).setOnClickListener {
            if (battleOver || party.size <= 1 || playerLastStand || currentEnemy == null) return@setOnClickListener
            val wildLvl = currentEnemy!!.maxHp / 10
            val options = party.mapIndexed { index, p -> 
                val pLvl = p.maxHp / 10
                val diff = pLvl - wildLvl
                val diffStr = if (diff >= 0) "+$diff" else "$diff"
                "${p.name} Lvl $pLvl ($diffStr)" 
            }.toTypedArray()
            
            DialogUtils.showCustomDialog(this, "Swap to who?", null, true, "", null) { content, dialog ->
                party.forEachIndexed { index, p ->
                    val btn = Button(this).apply {
                        text = options[index]
                        setBackgroundResource(R.drawable.bg_btn_standard)
                        setTextColor(Color.WHITE)
                        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        lp.setMargins(0, 0, 0, 16)
                        layoutParams = lp
                        setOnClickListener {
                            if (index == activePetIndex) { 
                                printLog("> ${p.name} is already fighting!") 
                            } else { 
                                activePetIndex = index
                                prefs.edit().putInt("ACTIVE_PET_INDEX", activePetIndex).apply()
                                printLog("\n> Swapped to ${party[activePetIndex].name}!")
                                showBattleArena(party[activePetIndex].name, currentEnemy!!.name)
                                updateBattleUI()
                                triggerEnemyCounterAttack() 
                            }
                            dialog.dismiss()
                        }
                    }
                    content.addView(btn)
                }
            }
        }
        
        findViewById<Button>(R.id.btnItem).setOnClickListener {
            if (battleOver || playerLastStand) return@setOnClickListener
            val pet = party[activePetIndex]
            val opts = mutableListOf<String>()
            if (pet.eqPots > 0) opts.add("Use Potion (${pet.eqPots})")
            if (pet.eqSprays > 0) opts.add("Use Spray (${pet.eqSprays})")
            
            if (opts.isEmpty()) { Toast.makeText(this, "No items equipped!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            
            DialogUtils.showCustomDialog(this, "Use Equipped Item", null, true, "", null) { content, dialog ->
                if (pet.eqPots > 0) {
                    val btn = Button(this).apply { 
                        text = "Use Potion (${pet.eqPots})"
                        setBackgroundResource(R.drawable.bg_btn_standard)
                        setTextColor(Color.WHITE)
                        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        lp.setMargins(0, 0, 0, 16)
                        layoutParams = lp
                        setOnClickListener { 
                            pet.eqPots--
                            pet.hp = (pet.hp + 50).coerceAtMost(pet.maxHp)
                            saveParty()
                            updatePartyScreen()
                            updateBattleUI()
                            printLog("\n> ${pet.name} drank a Potion! HP restored.")
                            triggerEnemyCounterAttack()
                            dialog.dismiss() 
                        } 
                    }
                    content.addView(btn)
                }
                if (pet.eqSprays > 0) {
                    val btn = Button(this).apply { 
                        text = "Use Spray (${pet.eqSprays})"
                        setBackgroundResource(R.drawable.bg_btn_standard)
                        setTextColor(Color.WHITE)
                        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        lp.setMargins(0, 0, 0, 16)
                        layoutParams = lp
                        setOnClickListener { 
                            pet.eqSprays--
                            saveParty()
                            updatePartyScreen()
                            updateBattleUI()
                            printLog("\n> ${pet.name} used a Repel Spray!")
                            if (isUnderAttack && kotlin.random.Random.nextInt(100) < 5) { 
                                printLog("> CRITICAL SUCCESS! Boss flees.")
                                endBattle() 
                            } else if (isWildBattle && kotlin.random.Random.nextInt(100) < 60) { 
                                printLog("> The wild ${currentEnemy?.name} was blinded and ran away!")
                                endBattle() 
                            } else { 
                                printLog("> No effect...")
                                triggerEnemyCounterAttack() 
                            }
                            dialog.dismiss() 
                        } 
                    }
                    content.addView(btn)
                }
            }
        }
        
        findViewById<Button>(R.id.btnAbandon).setOnClickListener {
            if (battleOver || playerLastStand) return@setOnClickListener
            DialogUtils.showCustomDialog(this, "Abandon Netbeast?", "If you flee now, ${party[activePetIndex].name} will be lost forever.", true, "ABANDON", { 
                printLog("\n> You ABANDONED ${party[activePetIndex].name}!")
                AnimUtils.animDeath(findViewById(R.id.spritePlayer))
                party.removeAt(activePetIndex)
                activePetIndex = 0
                saveParty()
                endBattle() 
            }, null)
        }

        findViewById<Button>(R.id.btnDispatch).setOnClickListener {
            val availablePets = party.indices.filter { !activeExpeditions.containsKey(it) }
            if (availablePets.isEmpty()) { 
                printLog("> All Netbeasts are exploring!")
                return@setOnClickListener 
            }
            
            DialogUtils.showCustomDialog(this, "Dispatch Netbeasts", "Select beasts to deploy to $currentCity:", true, "DEPLOY", null) { content, dialog ->
                val checkBoxes = mutableListOf<CheckBox>()
                val btnSelectAll = Button(this).apply { 
                    text = "SELECT ALL"
                    setBackgroundResource(R.drawable.bg_btn_accent)
                    setTextColor(Color.WHITE)
                    val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    lp.setMargins(0, 0, 0, 16)
                    layoutParams = lp 
                }
                content.addView(btnSelectAll)
                
                for (i in availablePets) { 
                    val cb = CheckBox(this).apply { 
                        text = party[i].name
                        setTextColor(Color.WHITE)
                        tag = i
                        textSize = 16f
                        setPadding(16, 16, 16, 16) 
                    }
                    checkBoxes.add(cb)
                    content.addView(cb) 
                }
                
                btnSelectAll.setOnClickListener { 
                    val allChecked = checkBoxes.all { it.isChecked }
                    checkBoxes.forEach { it.isChecked = !allChecked } 
                }
                
                dialog.findViewById<Button>(R.id.btnDialogPositive).setOnClickListener {
                    var dispatched = 0
                    checkBoxes.filter { it.isChecked }.forEach { cb -> 
                        val chosenIndex = cb.tag as Int
                        activeExpeditions[chosenIndex] = System.currentTimeMillis() + (180 * 1000)
                        printLog("\n> Dispatched ${party[chosenIndex].name} to explore...")
                        dispatched++ 
                    }
                    if (dispatched > 0) { 
                        SaveManager.saveExpeditions(prefs, activeExpeditions)
                        updateDispatchButton() 
                    }
                    dialog.dismiss()
                }
            }
        }

        // Must run last so listeners are ready
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
            val bossName = if (getString(R.string.flavor_id) == "rhc") GameData.bossNames.random() else "The Dark One"
            currentEnemy = Netbeast(bossName, "Corrupted", 9999, 9999, "Doom", "Crash", 0L, 0, 0, 0, false, "None", 0, 0)
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
                    currentCity = city
                    currentWeather = weather
                    weatherIcon = icon
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
            
            checkOfflineExpeditions()
            mainHandler.postDelayed(exploreRunnable, 3000)
        }
    }

    internal fun showBattleArena(playerName: String, enemyName: String) {
        val arena = findViewById<View>(R.id.battleArena)
        val pSprite = findViewById<TextView>(R.id.spritePlayer)
        val eSprite = findViewById<TextView>(R.id.spriteEnemy)
        arena.visibility = View.VISIBLE
        pSprite.text = "$playerName\n(Player)"
        eSprite.text = "$enemyName\n(Enemy)"
        pSprite.alpha = 1f
        eSprite.alpha = 1f
        
        var pScale = 1.0f; var eScale = 1.0f
        if (currentEnemy != null && !playerLastStand) {
            val pLvl = party[activePetIndex].maxHp / 10
            val eLvl = currentEnemy!!.maxHp / 10
            val pEvo = GameData.beasts.find { it.name.contains(party[activePetIndex].name.split(" ").last()) }?.evoStage ?: 1
            val eEvo = GameData.beasts.find { it.name.contains(currentEnemy!!.name.split(" ").last()) }?.evoStage ?: 1
            
            if (pLvl > eLvl) { 
                eScale = (1.0f - (((pLvl - eLvl) * 0.03f) / eEvo)).coerceAtLeast(0.3f) 
            } else if (eLvl > pLvl) { 
                pScale = (1.0f - (((eLvl - pLvl) * 0.03f) / pEvo)).coerceAtLeast(0.3f) 
            }
        }
        
        pSprite.scaleX = pScale
        pSprite.scaleY = pScale
        eSprite.scaleX = eScale
        eSprite.scaleY = eScale

        pSprite.translationX = -400f
        eSprite.translationX = 400f
        pSprite.animate().translationX(20f).setDuration(500).start()
        eSprite.animate().translationX(-20f).setDuration(500).start()
    }
    
    internal fun hideBattleArena() {
        val pSprite = findViewById<TextView>(R.id.spritePlayer)
        val eSprite = findViewById<TextView>(R.id.spriteEnemy)
        pSprite.animate().translationX(-400f).setDuration(500).start()
        eSprite.animate().translationX(400f).setDuration(500).withEndAction { 
            findViewById<View>(R.id.battleArena).visibility = View.GONE 
        }.start()
    }

    private fun setupShop() {
        fun handleTx(cost: Int, isBuy: Boolean, itemType: String) {
            if (isBuy) {
                if (focusCoins >= cost) { 
                    focusCoins -= cost
                    when (itemType) { "NET" -> nets++; "POTION" -> potions++; "SPRAY" -> sprays++ }
                    Toast.makeText(this, "Bought 1 $itemType", Toast.LENGTH_SHORT).show() 
                } else Toast.makeText(this, "Not enough Focus Coins!", Toast.LENGTH_SHORT).show()
            } else {
                var hasItem = false
                when (itemType) { 
                    "NET" -> if (nets > 0) { nets--; hasItem = true }
                    "POTION" -> if (potions > 0) { potions--; hasItem = true }
                    "SPRAY" -> if (sprays > 0) { sprays--; hasItem = true } 
                }
                if (hasItem) { 
                    focusCoins += cost
                    Toast.makeText(this, "Sold 1 $itemType", Toast.LENGTH_SHORT).show() 
                } else Toast.makeText(this, "No ${itemType}s to sell!", Toast.LENGTH_SHORT).show()
            }
            saveItems()
            updateBagScreen()
        }
        
        findViewById<Button>(R.id.btnBuyNet).setOnClickListener { handleTx(5, true, "NET") }
        findViewById<Button>(R.id.btnSellNet).setOnClickListener { handleTx(2, false, "NET") }
        findViewById<Button>(R.id.btnBuyPotion).setOnClickListener { handleTx(10, true, "POTION") }
        findViewById<Button>(R.id.btnSellPotion).setOnClickListener { handleTx(5, false, "POTION") }
        findViewById<Button>(R.id.btnBuySpray).setOnClickListener { handleTx(15, true, "SPRAY") }
        findViewById<Button>(R.id.btnSellSpray).setOnClickListener { handleTx(7, false, "SPRAY") }
    }

    private fun setupEquipPanel() {
        fun setupAutoRepeat(btn: Button, itemType: String) {
            var isPressing = false
            val repeatRunnable = object : Runnable {
                override fun run() {
                    if (isPressing && party.isNotEmpty()) {
                        val p = party[activePetIndex]
                        var didEquip = false
                        if (itemType == "NET" && nets > 0) { nets--; p.eqNets++; didEquip = true }
                        if (itemType == "POT" && potions > 0) { potions--; p.eqPots++; didEquip = true }
                        if (itemType == "SPRAY" && sprays > 0) { sprays--; p.eqSprays++; didEquip = true }
                        if (didEquip) { saveItems(); saveParty(); updatePartyScreen(); updateBagScreen(); mainHandler.postDelayed(this, 150) }
                    }
                }
            }
            btn.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (party.isEmpty()) { Toast.makeText(this, "No Netbeast selected!", Toast.LENGTH_SHORT).show(); return@setOnTouchListener false }
                        isPressing = true
                        val p = party[activePetIndex]
                        var didEquip = false
                        if (itemType == "NET" && nets > 0) { nets--; p.eqNets++; didEquip = true }
                        else if (itemType == "POT" && potions > 0) { potions--; p.eqPots++; didEquip = true }
                        else if (itemType == "SPRAY" && sprays > 0) { sprays--; p.eqSprays++; didEquip = true }
                        else { Toast.makeText(this, "Out of $itemType!", Toast.LENGTH_SHORT).show() }
                        
                        if (didEquip) { saveItems(); saveParty(); updatePartyScreen(); updateBagScreen() }
                        mainHandler.postDelayed(repeatRunnable, 500)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { isPressing = false; mainHandler.removeCallbacks(repeatRunnable) }
                }
                false
            }
        }
        
        setupAutoRepeat(findViewById(R.id.btnEqNet), "NET")
        setupAutoRepeat(findViewById(R.id.btnEqPot), "POT")
        setupAutoRepeat(findViewById(R.id.btnEqSpray), "SPRAY")
        
        findViewById<Button>(R.id.btnUnequip).setOnClickListener {
            if (party.isEmpty()) return@setOnClickListener
            val p = party[activePetIndex]
            nets += p.eqNets; potions += p.eqPots; sprays += p.eqSprays
            p.eqNets = 0; p.eqPots = 0; p.eqSprays = 0
            saveItems(); saveParty(); updatePartyScreen(); updateBagScreen()
        }
        
        findViewById<Button>(R.id.btnInfuse).setOnClickListener {
            if (party.isEmpty()) return@setOnClickListener
            val p = party[activePetIndex]
            if (currentWeather == "Offline" || currentWeather == "Clear" && weatherIcon != "☀️") { 
                Toast.makeText(this, "Weather API Offline", Toast.LENGTH_SHORT).show()
                return@setOnClickListener 
            }
            if (p.infusionEl == currentWeather) { 
                Toast.makeText(this, "${p.name} is already infused!", Toast.LENGTH_SHORT).show() 
            } else {
                val msg = if (p.infusionEl == "None") "Infuse ${p.name} with $currentWeather energy?" else "This will overwrite ${p.name}'s current infusion (${p.infusionEl}). Are you sure?"
                DialogUtils.showCustomDialog(this, "Infuse $currentWeather?", msg, true, "YES", {
                    p.infusionEl = currentWeather
                    p.infusionStacks = 0
                    saveParty()
                    updatePartyScreen()
                    Toast.makeText(this, "Infused with $currentWeather!", Toast.LENGTH_SHORT).show()
                }, null)
            }
        }
        
        findViewById<Button>(R.id.btnSellBeast).setOnClickListener {
            if (party.size <= 1) { Toast.makeText(this, "Cannot sell your last Netbeast!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if (activeExpeditions.containsKey(activePetIndex)) { Toast.makeText(this, "Cannot sell while exploring!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            val p = party[activePetIndex]
            val suggestedPrice = (p.maxHp / 10) * kotlin.random.Random.nextInt(4, 8)
            
            DialogUtils.showCustomDialog(this, "List on Market?", "List ${p.name} for ${suggestedPrice}c?", true, "LIST", {
                p.listedPrice = suggestedPrice
                forSaleParty.add(p)
                party.removeAt(activePetIndex)
                activePetIndex = 0
                prefs.edit().putInt("ACTIVE_PET_INDEX", 0).apply()
                saveParty()
                saveForSale()
                updatePartyScreen()
                updateBagScreen()
                printLog("\n> 📦 You listed ${p.name} on the market for ${suggestedPrice}c.")
            }, null)
        }
    }

    internal fun setUIState(state: String) {
        val nav = findViewById<View>(R.id.navTabs)
        val peace = findViewById<View>(R.id.peaceControls)
        val battle = findViewById<View>(R.id.battleControls)
        
        nav.visibility = if (state == "BATTLE") View.GONE else View.VISIBLE
        peace.visibility = if (state == "BATTLE") View.GONE else View.VISIBLE
        battle.visibility = if (state == "BATTLE") View.VISIBLE else View.GONE
        qteContainer.visibility = if (state == "BATTLE") View.GONE else View.VISIBLE
        
        updateDispatchButton()
        if (state == "BATTLE") findViewById<Button>(R.id.tabActivity).performClick() 
    }

    internal fun updateDispatchButton() {
        val btn = findViewById<Button>(R.id.btnDispatch) ?: return
        if (activeExpeditions.size >= party.size && party.isNotEmpty()) { 
            btn.text = "ALL NETBEASTS DEPLOYED"
            btn.isEnabled = false 
        } else { 
            btn.text = "DISPATCH NETBEAST (${activeExpeditions.size}/${party.size} deployed)"
            btn.isEnabled = true 
        }
    }

    internal fun updateBattleUI() {
        val btn1 = findViewById<Button>(R.id.btnMove1)
        val btn2 = findViewById<Button>(R.id.btnMove2)
        val btnSwap = findViewById<Button>(R.id.btnSwap)
        val btnItem = findViewById<Button>(R.id.btnItem)
        val btnAbandon = findViewById<Button>(R.id.btnAbandon)

        if (playerLastStand) {
            btn1.text = "THROW PUNCH"
            btn2.visibility = View.GONE; btnSwap.visibility = View.GONE; btnItem.visibility = View.GONE; btnAbandon.visibility = View.GONE
        } else if (party.isNotEmpty()) {
            btn1.text = party[activePetIndex].move1
            btn2.text = party[activePetIndex].move2
            btn2.visibility = View.VISIBLE
            btnSwap.visibility = View.VISIBLE
            btnItem.visibility = View.VISIBLE
            btnAbandon.visibility = View.VISIBLE
            
            val p = party[activePetIndex]
            if (p.eqPots <= 0 && p.eqSprays <= 0) {
                btnItem.visibility = View.GONE 
            } else { 
                btnItem.visibility = View.VISIBLE
                btnItem.text = "BAG (${p.eqPots}P/${p.eqSprays}S)" 
            }
        }
    }

    internal fun updatePartyScreen() {
        partyContainer.removeAllViews()
        if (party.isEmpty()) { 
            val tv = TextView(this).apply { text = "Party empty. You are defenseless."; setTextColor(Color.WHITE) }
            partyContainer.addView(tv)
            return 
        }
        
        for ((index, p) in party.withIndex()) {
            val btn = Button(this).apply {
                val activeTag = if (index == activePetIndex) "[ACTIVE] " else ""
                val newTag = if (p.isNew) "✨ " else ""
                val infTag = if (p.infusionEl != "None") "[🌟 ${p.infusionEl}: ${p.infusionStacks}] " else ""
                val hpPercent = (p.hp.toFloat() / p.maxHp.toFloat()) * 10
                val bar = "█".repeat(hpPercent.toInt().coerceAtLeast(0)) + "-".repeat((10 - hpPercent.toInt()).coerceAtLeast(0))
                
                text = "$newTag$activeTag$infTag${p.name} (${p.type})\nHP:[$bar] ${p.hp.coerceAtLeast(0)}/${p.maxHp}\nEq: 🕸️${p.eqNets}  🧪${p.eqPots}  💨${p.eqSprays}"
                setBackgroundColor(if (index == activePetIndex) Color.parseColor("#1976D2") else Color.parseColor("#333333"))
                setTextColor(Color.WHITE)
                setPadding(40, 20, 20, 20)
                
                if (index == activePetIndex) { 
                    setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_arrow_active, 0, 0, 0)
                    compoundDrawablePadding = 16 
                } else {
                    setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                }
                
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 0, 0, 16)
                layoutParams = lp
                
                setOnClickListener {
                    activePetIndex = index
                    prefs.edit().putInt("ACTIVE_PET_INDEX", activePetIndex).apply()
                    updatePartyScreen()
                    updateBattleUI()
                }
            }
            partyContainer.addView(btn)
        }
    }

    internal fun updateBagScreen() {
        val unlockShop = (nets >= 20 || potions >= 20 || focusCoins >= 1 || forSaleParty.isNotEmpty())
        findViewById<Button>(R.id.tabBag).text = if (unlockShop) "BAG/SHOP" else "BAG"
        findViewById<View>(R.id.llShopContainer).visibility = if (unlockShop) View.VISIBLE else View.GONE
        
        var sText = "=== INVENTORY ===\n\n💰 Focus Coins: $focusCoins\n\n🕸️ Nets: $nets\n🧪 Potions: $potions\n💨 Sprays: $sprays\n\n"
        if (forSaleParty.isNotEmpty()) { 
            sText += "--- LISTED ON MARKET ---\n"
            forSaleParty.forEach { sText += "- ${it.name}: ${it.listedPrice}c\n" } 
        }
        tvBag.text = sText
    }

    private fun setupTabs() {
        val b1 = findViewById<Button>(R.id.tabActivity)
        val b2 = findViewById<Button>(R.id.tabParty)
        val b3 = findViewById<Button>(R.id.tabBag)
        val v1 = findViewById<ScrollView>(R.id.viewActivity)
        val v2 = findViewById<ScrollView>(R.id.viewParty)
        val v3 = findViewById<ScrollView>(R.id.viewBag)
        
        b1.setOnClickListener { 
            v1.visibility = View.VISIBLE; v2.visibility = View.GONE; v3.visibility = View.GONE
            b1.setTextColor(Color.GREEN); b2.setTextColor(Color.GRAY); b3.setTextColor(Color.GRAY) 
        }
        b2.setOnClickListener { 
            v1.visibility = View.GONE; v2.visibility = View.VISIBLE; v3.visibility = View.GONE
            b1.setTextColor(Color.GRAY); b2.setTextColor(Color.CYAN); b3.setTextColor(Color.GRAY)
            var changed = false
            party.forEach { if (it.isNew) { it.isNew = false; changed = true } }
            if (changed) { saveParty(); updatePartyScreen() }
        }
        b3.setOnClickListener { 
            v1.visibility = View.GONE; v2.visibility = View.GONE; v3.visibility = View.VISIBLE
            b1.setTextColor(Color.GRAY); b2.setTextColor(Color.GRAY); b3.setTextColor(Color.YELLOW)
            updateBagScreen() 
        }
    }

    private fun wipeCorruptSave() { 
        prefs.edit().clear().apply()
        party.clear()
        party.add(Netbeast("Cacheon", "Tech", 120, 120, "Digital Swipe", "Overclock", 0L, 0, 0, 0, false, "None", 0, 0))
        activeExpeditions.clear()
        sprays = 0; potions = 0; nets = 0; focusCoins = 0
        saveParty(); saveForSale(); saveItems()
        printLog("> ⚠️ WARNING: Save file wiped. Recovery Protocol Initialized.") 
    }

    private fun loadSaveData() {
        sprays = prefs.getInt("SPRAYS", 0); potions = prefs.getInt("POTIONS", 0); nets = prefs.getInt("NETS", 0); focusCoins = prefs.getInt("COINS", 0)
        activePetIndex = prefs.getInt("ACTIVE_PET_INDEX", 0)
        playerId = prefs.getString("PLAYER_ID", "") ?: ""; playerName = prefs.getString("PLAYER_NAME", "") ?: ""
        
        if (playerId.isEmpty()) {
            playerId = UUID.randomUUID().toString()
            val adjs = listOf("Neon", "Cyber", "Void", "Quantum", "Shadow", "Rogue", "Iron", "Aura")
            val nouns = listOf("Runner", "Ghost", "Paladin", "Hacker", "Samurai", "Vanguard", "Ninja")
            playerName = "${adjs.random()}_${nouns.random()}_${kotlin.random.Random.nextInt(10, 99)}"
            prefs.edit().putString("PLAYER_ID", playerId).putString("PLAYER_NAME", playerName).apply()
        }
        
        party = SaveManager.loadParty(prefs, "PARTY_DATA")
        forSaleParty = SaveManager.loadParty(prefs, "FORSALE_DATA")
        if (activePetIndex >= party.size) activePetIndex = 0
        
        activeExpeditions.clear()
        activeExpeditions.putAll(SaveManager.loadExpeditions(prefs))
    }

    internal fun saveParty() { SaveManager.saveParty(prefs, "PARTY_DATA", party) }
    internal fun saveForSale() { SaveManager.saveParty(prefs, "FORSALE_DATA", forSaleParty) }
    internal fun saveItems() { prefs.edit().putInt("SPRAYS", sprays).putInt("POTIONS", potions).putInt("NETS", nets).putInt("COINS", focusCoins).apply() }
    internal fun saveExpeditions() { SaveManager.saveExpeditions(prefs, activeExpeditions) }
    internal fun printLog(msg: String) { 
        tvConsole.text = "${tvConsole.text}\n$msg"
        findViewById<ScrollView>(R.id.viewActivity).post { findViewById<ScrollView>(R.id.viewActivity).fullScroll(View.FOCUS_DOWN) } 
    }

    override fun onDestroy() { 
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        saveExpeditions() 
    }
}