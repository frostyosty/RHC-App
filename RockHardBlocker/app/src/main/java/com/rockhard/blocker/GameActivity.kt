package com.rockhard.blocker

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.*
import java.util.UUID
import kotlin.random.Random

class GameActivity : Activity() {
    private lateinit var tvConsole: TextView
    private lateinit var tvParty: TextView
    private lateinit var tvBag: TextView
    private lateinit var partyContainer: LinearLayout
    private lateinit var qteContainer: LinearLayout
    private lateinit var prefs: SharedPreferences
    
    private var playerId = ""
    private var playerName = ""
    private var isUnderAttack = false
    private var isWildBattle = false
    private var battleOver = false
    private var playerLastStand = false
    private var currentEnemy: Netbeast? = null
    
    private var sprays = 0; private var potions = 0; private var nets = 0; private var focusCoins = 0
    private var party = mutableListOf<Netbeast>()
    private var activePetIndex = 0
    private var forSaleParty = mutableListOf<Netbeast>()
    private var marketBeasts = mutableListOf<Pair<Netbeast, Int>>()

    private var currentWeather = "Clear"; private var weatherIcon = "☀️"; private var currentCity = "Local Sanctuary"

    private val activeExpeditions = mutableMapOf<Int, Long>()
    private val activeQTEs = mutableMapOf<Int, View>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val exploreRunnable = object : Runnable { override fun run() { performGlobalTick(); mainHandler.postDelayed(this, 4000) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)
        prefs = getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE)

        tvConsole = findViewById(R.id.tvConsole)
        tvBag = findViewById(R.id.tvBag)
        partyContainer = findViewById(R.id.partyContainer)
        qteContainer = findViewById(R.id.qteContainer)

        try { loadSaveData() } catch (e: Exception) { wipeCorruptSave() }

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
        
        handleIncomingIntent(intent)
        mainHandler.postDelayed(exploreRunnable, 3000)
        
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
                val activeStr = if (index == activePetIndex) " (ACTIVE)" else ""
                "${p.name} Lvl $pLvl ($diffStr)$activeStr" 
            }.toTypedArray()
            
            showCustomDialog("Swap to who?", null, true, "", null) { content, dialog ->
                party.forEachIndexed { index, p ->
                    val btn = Button(this).apply {
                        text = options[index]
                        setBackgroundResource(R.drawable.bg_btn_standard)
                        setTextColor(Color.WHITE)
                        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                        lp.setMargins(0, 0, 0, 16)
                        layoutParams = lp
                        setOnClickListener {
                            if (index == activePetIndex) { printLog("> ${p.name} is already fighting!") } 
                            else { 
                                activePetIndex = index
                                prefs.edit().putInt("ACTIVE_PET_INDEX", activePetIndex).apply()
                                printLog("\n> You swapped to ${party[activePetIndex].name}!")
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
            
            showCustomDialog("Use Equipped Item", null, true, "", null) { content, dialog ->
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
                            SaveManager.saveParty(prefs, "PARTY_DATA", party)
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
                            SaveManager.saveParty(prefs, "PARTY_DATA", party)
                            updatePartyScreen()
                            updateBattleUI()
                            printLog("\n> ${pet.name} used a Repel Spray!")
                            if (isUnderAttack && Random.nextInt(100) < 5) { printLog("> CRITICAL SUCCESS! Boss flees."); endBattle() } 
                            else if (isWildBattle && Random.nextInt(100) < 60) { printLog("> The wild ${currentEnemy?.name} was blinded and ran away!"); endBattle() } 
                            else { printLog("> No effect..."); triggerEnemyCounterAttack() }
                            dialog.dismiss() 
                        } 
                    }
                    content.addView(btn)
                }
            }
        }
        
        findViewById<Button>(R.id.btnAbandon).setOnClickListener {
            if (battleOver || playerLastStand) return@setOnClickListener
            showCustomDialog("Abandon Netbeast?", "If you flee now, ${party[activePetIndex].name} will be lost forever. Are you sure?", true, "ABANDON", { 
                printLog("\n> You ABANDONED ${party[activePetIndex].name} to save yourself!")
                animDeath(findViewById(R.id.spritePlayer))
                party.removeAt(activePetIndex)
                activePetIndex = 0
                SaveManager.saveParty(prefs, "PARTY_DATA", party)
                endBattle() 
            }, null)
        }

        findViewById<Button>(R.id.btnDispatch).setOnClickListener {
            val availablePets = party.indices.filter { !activeExpeditions.containsKey(it) }
            if (availablePets.isEmpty()) { printLog("> All Netbeasts are exploring!"); return@setOnClickListener }
            showCustomDialog("Dispatch Netbeasts", "Select beasts to deploy to $currentCity:", true, "DEPLOY", null) { content, dialog ->
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
                    val cb = CheckBox(this).apply { text = party[i].name; setTextColor(Color.WHITE); tag = i; textSize = 16f; setPadding(16,16,16,16) }
                    checkBoxes.add(cb)
                    content.addView(cb) 
                }
                btnSelectAll.setOnClickListener { val allChecked = checkBoxes.all { it.isChecked }; checkBoxes.forEach { it.isChecked = !allChecked } }
                dialog.findViewById<Button>(R.id.btnDialogPositive).setOnClickListener {
                    var dispatched = 0
                    checkBoxes.filter { it.isChecked }.forEach { cb -> 
                        val chosenIndex = cb.tag as Int
                        activeExpeditions[chosenIndex] = System.currentTimeMillis() + (180 * 1000)
                        printLog("\n> Dispatched ${party[chosenIndex].name} to explore...")
                        dispatched++ 
                    }
                    if (dispatched > 0) { SaveManager.saveExpeditions(prefs, activeExpeditions); updateDispatchButton() }
                    dialog.dismiss()
                }
            }
        }
    }

    // FIX: Catch the Intent if the app is already open!
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
            if (playerName.isEmpty()) playerName = "Player_${Random.nextInt(10, 99)}"
            printLog("> Welcome back, $playerName!")
            WeatherEngine.fetchSilent({ city, weather, icon, terrain, debugStr ->
                currentCity = city; currentWeather = weather; weatherIcon = icon
                prefs.edit().putString("CURRENT_CITY", city).putString("DEBUG_API_DATA", debugStr).apply()
                runOnUiThread { printLog("> Base Locked: $city\n> Terrain: $terrain\n> Weather: $weather $icon\n> Awaiting orders."); findViewById<Button>(R.id.btnInfuse)?.text = "INFUSE WITH CURRENT WEATHER ($icon)" }
            }, { runOnUiThread { printLog("> Base Locked: Local Sanctuary\n> Weather: Offline\n> Awaiting orders.") } })
            checkOfflineExpeditions()
        }
    }

    // =====================================
    // ALL BOILERPLATE FUNCTIONS (UNMODIFIED)
    // =====================================
    private fun showBattleArena(playerName: String, enemyName: String) { val arena = findViewById<View>(R.id.battleArena); val pSprite = findViewById<TextView>(R.id.spritePlayer); val eSprite = findViewById<TextView>(R.id.spriteEnemy); arena.visibility = View.VISIBLE; pSprite.text = "$playerName\n(Player)"; eSprite.text = "$enemyName\n(Enemy)"; pSprite.alpha = 1f; eSprite.alpha = 1f; var pScale = 1.0f; var eScale = 1.0f; if (currentEnemy != null && !playerLastStand) { val pLvl = party[activePetIndex].maxHp / 10; val eLvl = currentEnemy!!.maxHp / 10; val pEvo = GameData.beasts.find { it.name.contains(party[activePetIndex].name.split(" ").last()) }?.category ?: "1"; val eEvo = GameData.beasts.find { it.name.contains(currentEnemy!!.name.split(" ").last()) }?.category ?: "1"; val evoDiv = 2.0f; if (pLvl > eLvl) { val diff = pLvl - eLvl; eScale = (1.0f - ((diff * 0.03f) / evoDiv)).coerceAtLeast(0.3f) } else if (eLvl > pLvl) { val diff = eLvl - pLvl; pScale = (1.0f - ((diff * 0.03f) / evoDiv)).coerceAtLeast(0.3f) } }; pSprite.scaleX = pScale; pSprite.scaleY = pScale; eSprite.scaleX = eScale; eSprite.scaleY = eScale; pSprite.translationX = -400f; eSprite.translationX = 400f; pSprite.animate().translationX(20f).setDuration(500).start(); eSprite.animate().translationX(-20f).setDuration(500).start() }
    private fun hideBattleArena() { val pSprite = findViewById<TextView>(R.id.spritePlayer); val eSprite = findViewById<TextView>(R.id.spriteEnemy); pSprite.animate().translationX(-400f).setDuration(500).start(); eSprite.animate().translationX(400f).setDuration(500).withEndAction { findViewById<View>(R.id.battleArena).visibility = View.GONE }.start() }
    private fun animShake(v: View) { v.animate().translationXBy(20f).setDuration(50).withEndAction { v.animate().translationXBy(-40f).setDuration(50).withEndAction { v.animate().translationXBy(20f).setDuration(50).start() }.start() }.start() }
    private fun animEvade(v: View, isPlayer: Boolean) { val dir = if (isPlayer) -50f else 50f; v.animate().translationXBy(dir).translationYBy(50f).setDuration(150).withEndAction { v.animate().translationXBy(-dir).translationYBy(-50f).setDuration(150).start() }.start() }
    private fun animDeath(v: View) { v.animate().alpha(0.2f).setDuration(100).withEndAction { v.animate().alpha(1f).setDuration(100).withEndAction { v.animate().alpha(0f).setDuration(800).start() }.start() }.start() }
    private fun showCustomDialog(title: String, message: String?, showNegative: Boolean, positiveText: String, onPositive: (() -> Unit)?, viewSetup: ((LinearLayout, android.app.Dialog) -> Unit)? = null) { val dialogView = layoutInflater.inflate(R.layout.dialog_custom, null); val dialog = android.app.AlertDialog.Builder(this).setView(dialogView).create(); dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT)); dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = title; if (message != null) { val tvMsg = dialogView.findViewById<TextView>(R.id.tvDialogMessage); tvMsg.text = message; tvMsg.visibility = View.VISIBLE }; val contentLayout = dialogView.findViewById<LinearLayout>(R.id.llDialogContent); val btnPos = dialogView.findViewById<Button>(R.id.btnDialogPositive); if (positiveText.isNotEmpty()) { btnPos.text = positiveText; btnPos.visibility = View.VISIBLE; btnPos.setOnClickListener { onPositive?.invoke(); dialog.dismiss() } }; val btnNeg = dialogView.findViewById<Button>(R.id.btnDialogNegative); if (showNegative) { btnNeg.visibility = View.VISIBLE; btnNeg.setOnClickListener { dialog.dismiss() } }; dialog.show(); viewSetup?.invoke(contentLayout, dialog) }
    private fun setupShop() { generateMarket(); findViewById<Button>(R.id.btnBuyBeast).setOnClickListener { if (party.size >= 10) { Toast.makeText(this, "Party full! Sell a beast first.", Toast.LENGTH_SHORT).show(); return@setOnClickListener }; showCustomDialog("Local Market ($currentCity)", null, true, "", null) { content, dialog -> val options = marketBeasts.map { "${it.first.name} (Lvl ${it.first.maxHp/10}) - ${it.second}c" }.toTypedArray(); marketBeasts.forEachIndexed { idx, item -> val btn = Button(this).apply { text = options[idx]; setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(Color.WHITE); val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.setMargins(0, 0, 0, 16); layoutParams = lp; setOnClickListener { if (focusCoins >= item.second) { focusCoins -= item.second; party.add(item.first); marketBeasts.removeAt(idx); saveItems(); SaveManager.saveParty(prefs, "PARTY_DATA", party); updatePartyScreen(); updateBagScreen(); Toast.makeText(this@GameActivity, "Bought ${item.first.name}!", Toast.LENGTH_LONG).show() } else Toast.makeText(this@GameActivity, "Need ${item.second}c!", Toast.LENGTH_SHORT).show(); dialog.dismiss() } }; content.addView(btn) } } } }
    private fun generateMarket() { if (marketBeasts.isNotEmpty()) return; for (i in 0 until 3) { val b = GameData.beasts.random(); val lvl = Random.nextInt(5, 20); val cost = (lvl * 5) + Random.nextInt(-10, 10); marketBeasts.add(Pair(Netbeast(b.name, b.type, lvl*10, lvl*10, b.m1, b.m2, System.currentTimeMillis(), 0, 0, 0, true, "None", 0, 0), cost.coerceAtLeast(10))) } }
    private fun setupEquipPanel() { fun setupAutoRepeat(btn: Button, itemType: String) { var isPressing = false; val repeatRunnable = object : Runnable { override fun run() { if (isPressing && party.isNotEmpty()) { val p = party[activePetIndex]; var didEquip = false; if (itemType == "NET" && nets > 0) { nets--; p.eqNets++; didEquip = true }; if (itemType == "POT" && potions > 0) { potions--; p.eqPots++; didEquip = true }; if (itemType == "SPRAY" && sprays > 0) { sprays--; p.eqSprays++; didEquip = true }; if (didEquip) { saveItems(); SaveManager.saveParty(prefs, "PARTY_DATA", party); updatePartyScreen(); updateBagScreen(); mainHandler.postDelayed(this, 150) } } } }; btn.setOnTouchListener { _, event -> when (event.action) { MotionEvent.ACTION_DOWN -> { if (party.isEmpty()) { Toast.makeText(this, "No Netbeast selected!", Toast.LENGTH_SHORT).show(); return@setOnTouchListener false }; isPressing = true; val p = party[activePetIndex]; var didEquip = false; if (itemType == "NET" && nets > 0) { nets--; p.eqNets++; didEquip = true } else if (itemType == "POT" && potions > 0) { potions--; p.eqPots++; didEquip = true } else if (itemType == "SPRAY" && sprays > 0) { sprays--; p.eqSprays++; didEquip = true } else { Toast.makeText(this, "Out of $itemType!", Toast.LENGTH_SHORT).show() }; if (didEquip) { saveItems(); SaveManager.saveParty(prefs, "PARTY_DATA", party); updatePartyScreen(); updateBagScreen() }; mainHandler.postDelayed(repeatRunnable, 500) }; MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { isPressing = false; mainHandler.removeCallbacks(repeatRunnable) } }; false } }; setupAutoRepeat(findViewById(R.id.btnEqNet), "NET"); setupAutoRepeat(findViewById(R.id.btnEqPot), "POT"); setupAutoRepeat(findViewById(R.id.btnEqSpray), "SPRAY"); findViewById<Button>(R.id.btnUnequip).setOnClickListener { if (party.isEmpty()) return@setOnClickListener; val p = party[activePetIndex]; nets += p.eqNets; potions += p.eqPots; sprays += p.eqSprays; p.eqNets = 0; p.eqPots = 0; p.eqSprays = 0; saveItems(); SaveManager.saveParty(prefs, "PARTY_DATA", party); updatePartyScreen(); updateBagScreen() }; findViewById<Button>(R.id.btnInfuse).setOnClickListener { if (party.isEmpty()) return@setOnClickListener; val p = party[activePetIndex]; if (currentWeather == "Offline" || currentWeather == "Clear" && weatherIcon != "☀️") { Toast.makeText(this, "Weather API Offline", Toast.LENGTH_SHORT).show(); return@setOnClickListener }; if (p.infusionEl == currentWeather) { Toast.makeText(this, "${p.name} is already infused!", Toast.LENGTH_SHORT).show() } else { val msg = if (p.infusionEl == "None") "Infuse ${p.name} with $currentWeather energy?" else "This will overwrite ${p.name}'s current infusion (${p.infusionEl})."; showCustomDialog("Infuse $currentWeather?", msg, true, "YES", { p.infusionEl = currentWeather; p.infusionStacks = 0; SaveManager.saveParty(prefs, "PARTY_DATA", party); updatePartyScreen(); Toast.makeText(this, "Infused with $currentWeather!", Toast.LENGTH_SHORT).show() }, null) } }; findViewById<Button>(R.id.btnTeachMove)?.setOnClickListener { if (party.size <= 1) { Toast.makeText(this, "Need another beast to learn from!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }; if (focusCoins < 50) { Toast.makeText(this, "Cost: 50c!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }; val activePet = party[activePetIndex]; val otherPets = party.filterIndexed { index, _ -> index != activePetIndex }; showCustomDialog("Select Teacher", "Cost: 50c.", true, "", null) { content, dialog -> otherPets.forEach { teacher -> val btn = Button(this).apply { text = "Learn from ${teacher.name}"; setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(Color.WHITE); val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.setMargins(0, 0, 0, 16); layoutParams = lp; setOnClickListener { dialog.dismiss(); showCustomDialog("Learn which move?", null, true, "", null) { c2, d2 -> val m1Btn = Button(this@GameActivity).apply { text = teacher.move1; setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(Color.WHITE); val lp2 = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp2.setMargins(0, 0, 0, 16); layoutParams = lp2; setOnClickListener { d2.dismiss(); chooseSlot(activePet, teacher.move1) } }; val m2Btn = Button(this@GameActivity).apply { text = teacher.move2; setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(Color.WHITE); val lp2 = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp2.setMargins(0, 0, 0, 16); layoutParams = lp2; setOnClickListener { d2.dismiss(); chooseSlot(activePet, teacher.move2) } }; c2.addView(m1Btn); c2.addView(m2Btn) } } }; content.addView(btn) } } }; findViewById<Button>(R.id.btnSellBeast).setOnClickListener { if (party.size <= 1) { Toast.makeText(this, "Cannot sell your last Netbeast!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }; if (activeExpeditions.containsKey(activePetIndex)) { Toast.makeText(this, "Cannot sell while exploring!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }; val p = party[activePetIndex]; val suggestedPrice = (p.maxHp / 10) * Random.nextInt(4, 8); showCustomDialog("List on Market?", "List ${p.name} for ${suggestedPrice}c?", true, "LIST", { p.listedPrice = suggestedPrice; forSaleParty.add(p); party.removeAt(activePetIndex); activePetIndex = 0; prefs.edit().putInt("ACTIVE_PET_INDEX", 0).apply(); SaveManager.saveParty(prefs, "PARTY_DATA", party); SaveManager.saveParty(prefs, "FORSALE_DATA", forSaleParty); updatePartyScreen(); updateBagScreen(); printLog("\n> 📦 You listed ${p.name} on the market for ${suggestedPrice}c.") }, null) } }
    private fun chooseSlot(pet: Netbeast, newMove: String) { showCustomDialog("Forget which move?", null, true, "", null) { c3, d3 -> val slot1 = Button(this).apply { text = "Forget ${pet.move1}"; setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(Color.WHITE); val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.setMargins(0, 0, 0, 16); layoutParams = lp; setOnClickListener { pet.move1 = newMove; focusCoins -= 50; saveItems(); SaveManager.saveParty(prefs, "PARTY_DATA", party); updatePartyScreen(); updateBattleUI(); printLog("> \uD83E\uDDEC SUCCESS! Move learned."); d3.dismiss() } }; val slot2 = Button(this).apply { text = "Forget ${pet.move2}"; setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(Color.WHITE); val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.setMargins(0, 0, 0, 16); layoutParams = lp; setOnClickListener { pet.move2 = newMove; focusCoins -= 50; saveItems(); SaveManager.saveParty(prefs, "PARTY_DATA", party); updatePartyScreen(); updateBattleUI(); printLog("> \uD83E\uDDEC SUCCESS! Move learned."); d3.dismiss() } }; c3.addView(slot1); c3.addView(slot2) } }
    private fun executePlayerMove(moveName: String) { if (battleOver) return; val activePet = party[activePetIndex]; printLog("\n--- PLAYER TURN ---"); if (currentWeather == "Rain" && Random.nextInt(100) < 15) { printLog("> 🌧️ The ground is slick from the rain!"); printLog("> ${activePet.name} slipped and missed!"); animEvade(findViewById(R.id.spritePlayer), true); Handler(Looper.getMainLooper()).postDelayed({ triggerEnemyCounterAttack() }, 1500); return }; if (activePet.isGrouchy() && Random.nextInt(100) < 30) { printLog("> 😡 ${activePet.name} is feeling [Grouchy]!"); printLog("> ${activePet.name} ignored your command!"); Handler(Looper.getMainLooper()).postDelayed({ triggerEnemyCounterAttack() }, 1500); return }; printLog("> ${activePet.name} attempts [$moveName]"); if (isUnderAttack) { Handler(Looper.getMainLooper()).postDelayed({ printLog("> ${currentEnemy?.name} EVADED! It is invincible!"); animEvade(findViewById(R.id.spriteEnemy), false); triggerEnemyCounterAttack() }, 1000) } else { var baseDmg = (activePet.maxHp * 0.25).toInt() + Random.nextInt(-5, 5); if (activePet.infusionEl == currentWeather && activePet.infusionStacks > 0) { val mult = when(currentWeather) { "Clear" -> 1; "Cloudy" -> 2; "Rain" -> 3; "Storm" -> 5; "Snow" -> 10; else -> 0 }; val bonus = activePet.infusionStacks * mult; baseDmg += bonus; printLog("> 🌟 TACTICAL ADVANTAGE! ${currentWeather} boosts damage by $bonus!") }; var finalDmg = baseDmg; if (Random.nextInt(100) < 15) { finalDmg = (baseDmg * 1.5).toInt(); printLog("> 💥 CRITICAL HIT!") }; currentEnemy!!.hp -= finalDmg; Handler(Looper.getMainLooper()).postDelayed({ animShake(findViewById(R.id.spriteEnemy)); printLog("> Hit! ${currentEnemy?.name} takes $finalDmg damage. (HP: ${currentEnemy!!.hp.coerceAtLeast(0)}/${currentEnemy!!.maxHp})"); if (currentEnemy!!.hp <= 0) { animDeath(findViewById(R.id.spriteEnemy)); var coinsWon = Random.nextInt(10, 25); val diff = currentEnemy!!.maxHp - activePet.maxHp; if (diff > 0) { val goliathBonus = diff / 2; coinsWon += goliathBonus; printLog("> 🏆 GOLIATH BONUS! (+${goliathBonus}c)") }; focusCoins += coinsWon; saveItems(); updateBagScreen(); printLog("> 🏆 VICTORY! The wild ${currentEnemy?.name} fainted! You found $coinsWon Coins!"); endBattle() } else triggerEnemyCounterAttack() }, 1000) } }
    private fun triggerEnemyCounterAttack() { if (battleOver) return; if (party.isEmpty() || activePetIndex >= party.size) { playerLastStand = true; updateBattleUI(); printLog("\n> ⚠️ ALL NETBEASTS HAVE FALLEN!\n> There is no one left to protect you. It's your turn."); return }; val target = party[activePetIndex]; val damage = if (isUnderAttack) Random.nextInt(40, 80) else (currentEnemy!!.maxHp * 0.25).toInt() + Random.nextInt(-5, 5); printLog("\n--- ENEMY TURN ---"); printLog("> ${currentEnemy?.name} attempts[Brutal Strike] on ${target.name}..."); Handler(Looper.getMainLooper()).postDelayed({ target.hp -= damage; animShake(findViewById(R.id.spritePlayer)); printLog("> 🩸 Hit! ${target.name} takes $damage damage."); printLog("> ${target.name} gains [Winded] status (-10 Speed)."); if (target.hp <= 0) { animDeath(findViewById(R.id.spritePlayer)); printLog("> 💀 ${target.name} HAS BEEN KILLED!"); party.removeAt(activePetIndex); activePetIndex = 0; prefs.edit().putInt("ACTIVE_PET_INDEX", 0).apply(); if (party.isEmpty()) { playerLastStand = true; updateBattleUI(); printLog("\n> ⚠️ ALL NETBEASTS HAVE FALLEN!\n> ${currentEnemy?.name} turns its gaze slowly toward YOU.") } else { updateBattleUI(); printLog("> You send out ${party[activePetIndex].name}!"); showBattleArena(party[activePetIndex].name, currentEnemy!!.name) } }; updatePartyScreen(); SaveManager.saveParty(prefs, "PARTY_DATA", party); if (isUnderAttack && !battleOver && !playerLastStand && Random.nextBoolean()) Handler(Looper.getMainLooper()).postDelayed({ printLog("\n> ${currentEnemy?.name} fades into the shadows..."); endBattle() }, 2000) }, 1500) }
    private fun spawnWildQTE(petIndex: Int, pet: Netbeast) { val baseBeast = GameData.beasts.random(); val partyPenalty = (party.size - 1) * 15; val variance = (pet.maxHp * 0.15).toInt(); val pl = Random.nextInt(pet.maxHp - variance, pet.maxHp + variance).coerceAtLeast(10) + partyPenalty; val wildBeast = Netbeast(baseBeast.name, "Wild", pl, pl, baseBeast.m1, baseBeast.m2, 0L, 0, 0, 0, false, "None", 0, 0); if (partyPenalty > 0) printLog("\n> ⚠️ Your large party attracted a stronger threat (+${partyPenalty} HP)!"); printLog("> ⚠️ ${pet.name} spotted a wild ${wildBeast.name} (HP: $pl)!"); val qteView = layoutInflater.inflate(R.layout.item_qte_row, qteContainer, false); qteView.findViewById<TextView>(R.id.tvQteDesc).text = "Wild ${wildBeast.name} spotted by ${pet.name}!"; val expireTask = Runnable { if (activeQTEs.containsKey(petIndex)) { qteContainer.removeView(qteView); activeQTEs.remove(petIndex); printLog("\n> You hesitated..."); if (wildBeast.maxHp >= (pet.maxHp * 0.8)) { printLog("💢 The wild ${wildBeast.name} ambushed ${pet.name}!"); startWildBattle(petIndex, wildBeast) } else printLog("> The wild ${wildBeast.name} got bored and left.") } }; qteView.findViewById<Button>(R.id.btnQteAvoid).setOnClickListener { mainHandler.removeCallbacks(expireTask); qteContainer.removeView(qteView); activeQTEs.remove(petIndex); printLog("\n> You signaled ${pet.name} to sneak away. Safe.") }; val btnQteNet = qteView.findViewById<Button>(R.id.btnQteNet); val activeP = party[petIndex]; if (activeP.eqNets > 0) { btnQteNet.text = "CAST NET (${activeP.eqNets})"; btnQteNet.setOnClickListener { activeP.eqNets--; SaveManager.saveParty(prefs, "PARTY_DATA", party); updatePartyScreen(); mainHandler.removeCallbacks(expireTask); qteContainer.removeView(qteView); activeQTEs.remove(petIndex); if (Random.nextBoolean()) { printLog("\n> 🌟 SUCCESS! You caught ${wildBeast.name}!"); wildBeast.isNew = true; party.add(wildBeast); SaveManager.saveParty(prefs, "PARTY_DATA", party); updatePartyScreen(); updateDispatchButton() } else { if (activeP.eqNets <= 0) { printLog("\n> The net broke! ${activeP.name} is out of nets. The wild ${wildBeast.name} wanders off in pity.") } else { printLog("\n> The net broke! ${wildBeast.name} is angry! AMBUSH!"); startWildBattle(petIndex, wildBeast) } } } } else btnQteNet.visibility = View.GONE; qteView.findViewById<Button>(R.id.btnQteAmbush).setOnClickListener { mainHandler.removeCallbacks(expireTask); qteContainer.removeView(qteView); activeQTEs.remove(petIndex); val wildLvl = wildBeast.maxHp / 10; val options = party.map { p -> val pLvl = p.maxHp / 10; val diff = pLvl - wildLvl; val diffStr = if (diff >= 0) "+$diff" else "$diff"; "${p.name} Lvl $pLvl ($diffStr)" }.toTypedArray(); showCustomDialog("Who will Ambush?", null, false, "", null) { content, dialog -> party.forEachIndexed { chosenIdx, p -> val btn = Button(this@GameActivity).apply { text = options[chosenIdx]; setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(Color.WHITE); val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); lp.setMargins(0, 0, 0, 16); layoutParams = lp; setOnClickListener { printLog("\n> You ordered ${party[chosenIdx].name} to AMBUSH ${wildBeast.name}!"); startWildBattle(chosenIdx, wildBeast); dialog.dismiss() } }; content.addView(btn) } } }; qteContainer.addView(qteView); activeQTEs[petIndex] = qteView; mainHandler.postDelayed(expireTask, 5000) }
    private fun startWildBattle(petIndex: Int, wildBeast: Netbeast) { qteContainer.removeAllViews(); activeQTEs.clear(); currentEnemy = wildBeast; activePetIndex = petIndex; isWildBattle = true; battleOver = false; setUIState("BATTLE"); showBattleArena(party[petIndex].name, wildBeast.name); updateBattleUI() }
    private fun endBattle() { battleOver = true; isUnderAttack = false; isWildBattle = false; playerLastStand = false; hideBattleArena(); Handler(Looper.getMainLooper()).postDelayed({ if (party.isEmpty()) { printLog("> You blacked out and returned to the Hub."); setUIState("HUB") } else { printLog("> Returning to exploration..."); setUIState("HUB") } }, 2000) }
    private fun setUIState(state: String) { val nav = findViewById<View>(R.id.navTabs); val peace = findViewById<View>(R.id.peaceControls); val battle = findViewById<View>(R.id.battleControls); nav.visibility = if (state == "BATTLE") View.GONE else View.VISIBLE; peace.visibility = if (state == "BATTLE") View.GONE else View.VISIBLE; battle.visibility = if (state == "BATTLE") View.VISIBLE else View.GONE; qteContainer.visibility = if (state == "BATTLE") View.GONE else View.VISIBLE; updateDispatchButton(); if (state == "BATTLE") findViewById<Button>(R.id.tabActivity).performClick() else hideBattleArena() }
    private fun updateDispatchButton() { val btn = findViewById<Button>(R.id.btnDispatch) ?: return; if (activeExpeditions.size >= party.size && party.isNotEmpty()) { btn.text = "ALL NETBEASTS DEPLOYED"; btn.isEnabled = false } else { btn.text = "DISPATCH NETBEAST (${activeExpeditions.size}/${party.size} deployed)"; btn.isEnabled = true } }
    private fun checkOfflineExpeditions() { if (activeExpeditions.isEmpty()) return; val now = System.currentTimeMillis(); val toRemove = mutableListOf<Int>(); for ((idx, endTime) in activeExpeditions) { if (idx >= party.size) { toRemove.add(idx); continue }; if (now >= endTime) { toRemove.add(idx); if (Random.nextInt(100) < 30) { val wildName = GameData.beasts.random().name; val pl = Random.nextInt(40, 160); currentEnemy = Netbeast(wildName, "Wild", pl, pl, "Tackle", "Bite", 0L, 0, 0, 0, false, "None", 0, 0); isWildBattle = true; activePetIndex = idx; setUIState("BATTLE"); showBattleArena(party[idx].name, wildName); printLog("⚠️ OFFLINE AMBUSH!\n> While you were away, ${party[idx].name} was attacked by a wild $wildName!"); updateBattleUI(); break } else { val c = Random.nextInt(5, 15); focusCoins += c; printLog("> 🏁 Offline: ${party[idx].name} returned with $c Focus Coins!") } } else printLog("> ${party[idx].name} is still exploring...") }; toRemove.forEach { activeExpeditions.remove(it) }; saveItems(); updateBagScreen(); SaveManager.saveExpeditions(prefs, activeExpeditions); updateDispatchButton() }
    private fun performGlobalTick() { if (forSaleParty.isNotEmpty() && Random.nextInt(100) < 15) { val sold = forSaleParty.removeAt(0); focusCoins += sold.listedPrice; printLog("\n> 💰 MARKET ALERT! A buyer purchased your ${sold.name} for ${sold.listedPrice}c!"); SaveManager.saveParty(prefs, "FORSALE_DATA", forSaleParty); saveItems(); updateBagScreen() }; if (activeExpeditions.isEmpty()) return; val now = System.currentTimeMillis(); val toRemove = mutableListOf<Int>(); for (petIndex in activeExpeditions.keys.toList()) { if (petIndex >= party.size) { toRemove.add(petIndex); continue }; val pet = party[petIndex]; if (now >= activeExpeditions[petIndex]!!) { printLog("\n> 🏁 EXPEDITION COMPLETE! ${pet.name} returned to the Hub."); toRemove.add(petIndex); continue }; if (activeQTEs.containsKey(petIndex)) continue; if (Random.nextInt(100) < 25) { if (isUnderAttack || isWildBattle) { if (petIndex == activePetIndex) continue; if (Random.nextBoolean()) { nets++; printLog("> 🛡️ While you battle, ${pet.name} scavenged a Net!") } else { val c = Random.nextInt(5,15); focusCoins += c; printLog("> 🛡️ While you battle, ${pet.name} scavenged $c Coins!") }; saveItems(); updateBagScreen() } else { val roll = Random.nextInt(100); if (roll < 30) { printLog("> ${pet.name} found a Net!"); nets++; saveItems(); updateBagScreen() } else if (roll < 50) { printLog("> ${pet.name} found a Potion!"); potions++; saveItems(); updateBagScreen() } else if (roll < 70) { val c = Random.nextInt(5,15); focusCoins += c; printLog("> ${pet.name} found $c Focus Coins!"); saveItems(); updateBagScreen() } else spawnWildQTE(petIndex, pet) } } }; toRemove.forEach { activeExpeditions.remove(it) }; if (toRemove.isNotEmpty()) { SaveManager.saveExpeditions(prefs, activeExpeditions); updateDispatchButton() } }
    private fun wipeCorruptSave() { prefs.edit().clear().apply(); party.clear(); party.add(Netbeast("Cacheon", "Tech", 120, 120, "Digital Swipe", "Overclock", 0L, 0, 0, 0, false, "None", 0, 0)); activeExpeditions.clear(); sprays = 0; potions = 0; nets = 0; focusCoins = 0; SaveManager.saveParty(prefs, "PARTY_DATA", party); saveItems(); printLog("> ⚠️ WARNING: Save file wiped. Recovery Protocol Initialized.") }
    private fun loadSaveData() { sprays = prefs.getInt("SPRAYS", 0); potions = prefs.getInt("POTIONS", 0); nets = prefs.getInt("NETS", 0); focusCoins = prefs.getInt("COINS", 0); activePetIndex = prefs.getInt("ACTIVE_PET_INDEX", 0); playerId = prefs.getString("PLAYER_ID", "") ?: ""; playerName = prefs.getString("PLAYER_NAME", "") ?: ""; if (playerId.isEmpty()) { playerId = UUID.randomUUID().toString(); val adjs = listOf("Neon", "Cyber", "Void", "Quantum", "Shadow", "Rogue", "Iron", "Aura"); val nouns = listOf("Runner", "Ghost", "Paladin", "Hacker", "Samurai", "Vanguard", "Ninja"); playerName = "${adjs.random()}_${nouns.random()}_${Random.nextInt(10, 99)}"; prefs.edit().putString("PLAYER_ID", playerId).putString("PLAYER_NAME", playerName).apply() }; party = SaveManager.loadParty(prefs, "PARTY_DATA"); forSaleParty = SaveManager.loadParty(prefs, "FORSALE_DATA"); if (activePetIndex >= party.size) activePetIndex = 0; activeExpeditions.clear(); activeExpeditions.putAll(SaveManager.loadExpeditions(prefs)) }
    private fun saveParty() { SaveManager.saveParty(prefs, "PARTY_DATA", party) }
    private fun saveItems() { prefs.edit().putInt("SPRAYS", sprays).putInt("POTIONS", potions).putInt("NETS", nets).putInt("COINS", focusCoins).apply() }
    private fun saveExpeditions() { SaveManager.saveExpeditions(prefs, activeExpeditions) }
    private fun printLog(msg: String) { tvConsole.text = "${tvConsole.text}\n$msg"; findViewById<ScrollView>(R.id.viewActivity).post { findViewById<ScrollView>(R.id.viewActivity).fullScroll(View.FOCUS_DOWN) } }
    private fun updatePartyScreen() { val container = findViewById<LinearLayout>(R.id.partyContainer); container.removeAllViews(); if (party.isEmpty()) { val tv = TextView(this); tv.text = "Party empty. You are defenseless."; tv.setTextColor(Color.WHITE); container.addView(tv); return }; for ((index, p) in party.withIndex()) { val btn = Button(this); val activeTag = if (index == activePetIndex) "[ACTIVE] " else ""; val newTag = if (p.isNew) "✨ " else ""; val infTag = if (p.infusionEl != "None") "[🌟 ${p.infusionEl}: ${p.infusionStacks}] " else ""; val hpPercent = (p.hp.toFloat() / p.maxHp.toFloat()) * 10; val bar = "█".repeat(hpPercent.toInt().coerceAtLeast(0)) + "-".repeat((10 - hpPercent.toInt()).coerceAtLeast(0)); btn.text = "$newTag$activeTag$infTag${p.name} (${p.type})\nHP:[$bar] ${p.hp.coerceAtLeast(0)}/${p.maxHp}\nEq: 🕸️${p.eqNets}  🧪${p.eqPots}  💨${p.eqSprays}"; btn.setBackgroundColor(if (index == activePetIndex) Color.parseColor("#1976D2") else Color.parseColor("#333333")); btn.setTextColor(Color.WHITE); btn.setPadding(40, 20, 20, 20); if (index == activePetIndex) { btn.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_arrow_active, 0, 0, 0); btn.compoundDrawablePadding = 16 } else btn.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0); val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); params.setMargins(0, 0, 0, 16); btn.layoutParams = params; btn.setOnClickListener { activePetIndex = index; prefs.edit().putInt("ACTIVE_PET_INDEX", activePetIndex).apply(); updatePartyScreen(); updateBattleUI() }; container.addView(btn) } }
    private fun updateBagScreen() { val unlockShop = (nets >= 20 || potions >= 20 || focusCoins >= 1 || forSaleParty.isNotEmpty()); findViewById<Button>(R.id.tabBag).text = if (unlockShop) "BAG/SHOP" else "BAG"; findViewById<View>(R.id.llShopContainer).visibility = if (unlockShop) View.VISIBLE else View.GONE; var sText = "=== INVENTORY ===\n\n💰 Focus Coins: $focusCoins\n\n🕸️ Nets: $nets\n🧪 Potions: $potions\n💨 Sprays: $sprays\n\n"; if (forSaleParty.isNotEmpty()) { sText += "--- LISTED ON MARKET ---\n"; forSaleParty.forEach { sText += "- ${it.name}: ${it.listedPrice}c\n" } }; tvBag.text = sText }
    private fun updateBattleUI() { val btn1 = findViewById<Button>(R.id.btnMove1); val btn2 = findViewById<Button>(R.id.btnMove2); val btnSwap = findViewById<Button>(R.id.btnSwap); val btnItem = findViewById<Button>(R.id.btnItem); val btnAbandon = findViewById<Button>(R.id.btnAbandon); if (playerLastStand) { btn1.text = "THROW PUNCH"; btn2.visibility = View.GONE; btnSwap.visibility = View.GONE; btnItem.visibility = View.GONE; btnAbandon.visibility = View.GONE } else if (party.isNotEmpty()) { btn1.text = party[activePetIndex].move1; btn2.text = party[activePetIndex].move2; btn2.visibility = View.VISIBLE; btnSwap.visibility = View.VISIBLE; btnItem.visibility = View.VISIBLE; btnAbandon.visibility = View.VISIBLE; val p = party[activePetIndex]; if (p.eqPots <= 0 && p.eqSprays <= 0) { btnItem.visibility = View.GONE } else { btnItem.visibility = View.VISIBLE; btnItem.text = "BAG (${p.eqPots}P/${p.eqSprays}S)" } } }
    private fun setupTabs() { val b1 = findViewById<Button>(R.id.tabActivity); val b2 = findViewById<Button>(R.id.tabParty); val b3 = findViewById<Button>(R.id.tabBag); val v1 = findViewById<ScrollView>(R.id.viewActivity); val v2 = findViewById<ScrollView>(R.id.viewParty); val v3 = findViewById<ScrollView>(R.id.viewBag); b1.setOnClickListener { v1.visibility = View.VISIBLE; v2.visibility = View.GONE; v3.visibility = View.GONE; b1.setTextColor(Color.GREEN); b2.setTextColor(Color.GRAY); b3.setTextColor(Color.GRAY) }; b2.setOnClickListener { v1.visibility = View.GONE; v2.visibility = View.VISIBLE; v3.visibility = View.GONE; b1.setTextColor(Color.GRAY); b2.setTextColor(Color.CYAN); b3.setTextColor(Color.GRAY); var changed = false; party.forEach { if (it.isNew) { it.isNew = false; changed = true } }; if (changed) { saveParty(); updatePartyScreen() } }; b3.setOnClickListener { v1.visibility = View.GONE; v2.visibility = View.GONE; v3.visibility = View.VISIBLE; b1.setTextColor(Color.GRAY); b2.setTextColor(Color.GRAY); b3.setTextColor(Color.YELLOW); updateBagScreen() } }
    private fun executeHumanPunch() { if (battleOver) return; printLog("\n--- PLAYER TURN ---\n> YOU THROW A PUNCH!"); val dmg = Random.nextInt(1, 5); Handler(Looper.getMainLooper()).postDelayed({ animShake(findViewById(R.id.spriteEnemy)); printLog("> It barely connects... $dmg damage.\n> ${currentEnemy?.name} looks at you with pity."); Handler(Looper.getMainLooper()).postDelayed({ animDeath(findViewById(R.id.spritePlayer)); printLog("\n--- ENEMY TURN ---\n> ${currentEnemy?.name} obliterates you for 9,999 damage.\n💀 YOU DIED."); endBattle() }, 1500) }, 1000) }
    
    override fun onNewIntent(intent: Intent?) { super.onNewIntent(intent); setIntent(intent); if (intent != null) handleIncomingIntent(intent) }
    private fun handleIncomingIntent(i: Intent) { isUnderAttack = i.getBooleanExtra("UNDER_ATTACK", false); if (isUnderAttack) { val bossName = if (getString(R.string.flavor_id) == "rhc") GameData.bossNames.random() else "The Dark One"; currentEnemy = Netbeast(bossName, "Corrupted", 9999, 9999, "Doom", "Crash", 0L, 0, 0, 0, false, "None", 0, 0); setUIState("BATTLE"); printLog("\n=================================="); printLog("⚠️ EMERGENCY: ${currentEnemy?.name} HAS INVADED!"); printLog("=================================="); if (party.isNotEmpty()) { printLog("> You sent out ${party[activePetIndex].name} to defend!"); showBattleArena(party[activePetIndex].name, bossName) } else { playerLastStand = true; printLog("> You have no Netbeasts! Defend yourself!"); showBattleArena("YOU", bossName) }; updateBattleUI() } }
    override fun onDestroy() { super.onDestroy(); mainHandler.removeCallbacksAndMessages(null); saveExpeditions() }
}
