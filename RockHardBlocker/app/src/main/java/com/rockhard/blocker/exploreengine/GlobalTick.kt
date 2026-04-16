package com.rockhard.blocker
import android.graphics.Color
import android.widget.Toast
import kotlin.random.Random

internal fun GameActivity.performGlobalTick() {
    // 0. AETHER DECAY LOGIC
    if (!isUnderAttack) {
        aetherSeconds--
        tvAether.text = "Aether: ${String.format("%02d:%02d", aetherSeconds / 60, aetherSeconds % 60)}"
        tvAether.setTextColor(Color.parseColor("#00BCD4"))
        
        if (aetherSeconds <= 0) {
            aetherDepleted = true
            if (!battleOver && !isWildBattle && !isTrainerBattle) {
                Toast.makeText(this, "Aether Depleted! Returning to reality.", Toast.LENGTH_LONG).show()
                finish()
                return
            }
        }
    }

    val eventMsg = BossEventEngine.checkAndGenerateEvent(prefs, party, currentCity)
    if (eventMsg != null) { printLog(eventMsg); updateBagScreen() }

    val isEventActive = prefs.getBoolean("EVENT_ACTIVE", false)
    if (isEventActive && !isUnderAttack && !isWildBattle && activeQTEs.isEmpty()) {
        val todayStr = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date())
        val targetDate = prefs.getString("EVENT_TARGET_DATE", "") ?: "" 
        if (todayStr >= targetDate) { spawnEventBossQTE(); return }
    }

    val currentCity = prefs.getString("CURRENT_CITY", "") ?: ""
    val currentSuburb = if (LocationEngine.hasGPSPermission(this)) {
        val loc = LocationEngine.getCurrentLocation(this)
        if (loc != null) LocationEngine.getSuburbName(this, loc.first, loc.second) else ""
    } else ""

    val rescues = RescueEngine.getCapturedNearby(prefs, currentSuburb, currentCity)
    rescues.forEach { capturedBeast -> spawnRescueQTE(capturedBeast, if (currentSuburb.isNotEmpty()) currentSuburb else currentCity) }

    if (forSaleParty.isNotEmpty() && Random.nextInt(100) < 20) {
        val target = forSaleParty.random()
        if (!activeOffers.containsKey(target.name)) {
            val mood = Random.nextInt(100)
            val offer = if (mood < 30) (target.listedPrice * 0.5).toInt() else if (mood < 80) (target.listedPrice * 0.9).toInt() else (target.listedPrice * 1.5).toInt()
            activeOffers[target.name] = offer.coerceAtLeast(1)
            printShopLog("\n> 📩 MARKET ALERT! You received an offer of ${offer}c for ${target.name}!"); updateBagScreen()
        }
    }

    if (marketBeasts.isNotEmpty() && Random.nextInt(100) < 15) {
        marketBeasts.removeAt(Random.nextInt(marketBeasts.size))
        SaveManager.saveParty(prefs, "MARKET_DATA", marketBeasts)
    }

    val spawnChance = 100 - (marketBeasts.size * 25)
    if (marketBeasts.size < 4 && Random.nextInt(100) < spawnChance) {
        val b = GameData.beasts.random()
        val lvl = Random.nextInt(5, 20); val cost = (lvl * 5) + Random.nextInt(-10, 10)
        marketBeasts.add(Netbeast(b.name, b.type, lvl * 10, lvl * 10, b.m1, b.m2, "Tackle", System.currentTimeMillis(), 0, 0, 0, true, "None", 0, cost.coerceAtLeast(10), 0, 0, "None", 0))
        SaveManager.saveParty(prefs, "MARKET_DATA", marketBeasts)
    }

    if (activeExpeditions.isEmpty()) return
    val now = System.currentTimeMillis(); val toRemove = mutableListOf<Int>()

    for (petIndex in activeExpeditions.keys.toList()) {
        val isPlayer = petIndex == -1
        if (!isPlayer && petIndex >= party.size) { toRemove.add(petIndex); continue }
        val petName = if (isPlayer) "You" else party[petIndex].name

if (now >= activeExpeditions[petIndex]!!) {
            totalExpeds++
            prefs.edit().putInt("TOTAL_EXPEDS", totalExpeds).apply()
            
            if (!isPlayer) {
                val pet = party[petIndex]
                if (pet.name.contains("[Will to Live]")) {
                    val heal = (pet.maxHp * 0.15).toInt()
                    pet.hp = (pet.hp + heal).coerceAtMost(pet.maxHp)
                    printLog("> 💖[Will to Live] healed $petName for $heal HP!")
                }
            }
            printLog("\n> 🏁 EXPEDITION COMPLETE! $petName returned.")
            toRemove.add(petIndex); continue
        }
        if (activeQTEs.containsKey(petIndex)) continue

        if (Random.nextInt(100) < 25) {
            if (isUnderAttack || isWildBattle) {
                if (petIndex == activePetIndex || isPlayer) continue 
                if (Random.nextBoolean()) { nets++; printLog("> \uD83D\uDEE1\uFE0F While you battle, $petName scavenged a Net!") } 
                else { val c = Random.nextInt(5, 15); focusCoins += c; printLog("> \uD83D\uDEE1\uFE0F While you battle, $petName scavenged $c Coins!") }
                saveItems(); updateBagScreen()
            } else {
                val roll = Random.nextInt(100)
                if (party.size >= 8 && Random.nextInt(100) < 5 && !isPlayer) { transfusers++; printLog("> 🧬 INCREDIBLE! $petName uncovered a rare Trait Transfuser!"); saveItems(); updateBagScreen() } 
                else if (roll < 30) { printLog("> $petName found a Net!"); nets++; saveItems(); updateBagScreen() } 
                else if (roll < 50) { printLog("> $petName found a Potion!"); potions++; saveItems(); updateBagScreen() } 
                else if (roll < 70) { val c = Random.nextInt(5, 15); focusCoins += c; printLog("> $petName found $c Focus Coins!"); saveItems(); updateBagScreen() } 
                else {
                    val dummyPet = if (isPlayer) Netbeast("You", "Human", 20, 20, "Punch", "Block", "None", 0L, 0, 0, 0, false, "None", 0, 0, 0, 0, "None", 0) else party[petIndex]
                    spawnWildQTE(petIndex, dummyPet)
                }
            }
        }
    }
    toRemove.forEach { activeExpeditions.remove(it) }
    if (toRemove.isNotEmpty()) { SaveManager.saveExpeditions(prefs, activeExpeditions); updateDispatchButton() }
}



internal fun GameActivity.checkOfflineExpeditions() {
    if (activeExpeditions.isEmpty()) return
    val now = System.currentTimeMillis(); val toRemove = mutableListOf<Int>()
    for ((idx, endTime) in activeExpeditions) {
        val isPlayer = idx == -1
        if (!isPlayer && idx >= party.size) { toRemove.add(idx); continue }
        val pName = if (isPlayer) "You" else party[idx].name
        if (now >= endTime) {
            toRemove.add(idx)
            if (kotlin.random.Random.nextInt(100) < 30) {
                val wildName = GameData.beasts.random().name; val pl = kotlin.random.Random.nextInt(40, 160)
                currentEnemy = Netbeast(wildName, "Wild", pl, pl, "Tackle", "Bite", "Swipe", 0L, 0, 0, 0, false, "None", 0, 0, 0, 0, "None", 0)
                isWildBattle = true
                if (isPlayer) { playerLastStand = true; setUIState("BATTLE"); showBattleArena("YOU", wildName) } 
                else { activePetIndex = idx; setUIState("BATTLE"); showBattleArena(party[idx].name, wildName) }
                printLog("⚠️ OFFLINE AMBUSH!\n> While you were away, $pName was attacked by a wild $wildName!"); updateBattleUI(); break
            } else { val c = kotlin.random.Random.nextInt(5, 15); focusCoins += c; printLog("> \uD83C\uDFC1 Offline: $pName returned with $c Focus Coins!") }
        } else printLog("> $pName is still exploring...")
    }
    toRemove.forEach { activeExpeditions.remove(it) }
    saveItems(); updateBagScreen(); SaveManager.saveExpeditions(prefs, activeExpeditions); updateDispatchButton()
}
internal fun GameActivity.showAetherDepletedDialog() {
    if (party.isEmpty()) {
        android.widget.Toast.makeText(this, "Aether Depleted! Returning to reality.", android.widget.Toast.LENGTH_LONG).show()
        finish()
        return
    }

    DialogUtils.showCustomDialog(this, "Aether Depleted", "Reality beckons. Check back tomorrow, or SACRIFICE a Netbeast to extend your time.\n\n(Warning: The others will remember this.)", false, "", null) { content, dialog ->
        val checkBoxes = mutableListOf<Pair<android.widget.CheckBox, Netbeast>>()
        
        party.forEach { p ->
            val lvl = p.maxHp / 10
            val addedSecs = Math.ceil(lvl / 60.0).toInt() * 60
            val cb = android.widget.CheckBox(this).apply {
                text = "${p.name} Lvl $lvl (+${addedSecs / 60} min)"
                setTextColor(android.graphics.Color.WHITE); textSize = 14f; setPadding(16, 16, 16, 16)
            }
            checkBoxes.add(Pair(cb, p))
            content.addView(cb)
        }
        
        val btnSacrifice = android.widget.Button(this).apply {
            text = "SACRIFICE SELECTED"
            setBackgroundResource(R.drawable.bg_btn_danger); setTextColor(android.graphics.Color.WHITE)
            layoutParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 16, 0, 8) }
            setOnClickListener {
                val toSacrifice = checkBoxes.filter { it.first.isChecked }.map { it.second }
                if (toSacrifice.isEmpty()) return@setOnClickListener
                
                var gainedSecs = 0
                toSacrifice.forEach { sBeast ->
                    val lvl = sBeast.maxHp / 10
                    gainedSecs += Math.ceil(lvl / 60.0).toInt() * 60
                    party.remove(sBeast)
                    
                    // Trauma Roll for survivors
                    party.forEach { survivor ->
                        if (kotlin.random.Random.nextInt(100) < 60) {
                            val cleanName = survivor.name.replace(Regex("\\[.*?\\]"), "").trim()
                            if (survivor.name.contains("[Worried]")) survivor.name = "[Petrified] $cleanName"
                            else if (survivor.name.contains("[Wary]")) survivor.name = "[Worried] $cleanName"
                            else if (!survivor.name.contains("[Petrified]")) survivor.name = "[Wary] $cleanName"
                        }
                    }
                }
                
                aetherSeconds += gainedSecs
                aetherDepleted = false
                saveParty(); updatePartyScreen(); updateBagScreen()
                printLog("\n> 🩸 SACRIFICE ACCEPTED. Gained ${gainedSecs / 60} minutes of Aether.")
                printLog("> The remaining beasts look at you in horror...")
                dialog.dismiss()
            }
        }
        
        val btnLeave = android.widget.Button(this).apply {
            text = "LEAVE REALM (EXIT)"
            setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(android.graphics.Color.WHITE)
            layoutParams = android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener { dialog.dismiss(); finish() }
        }
        
        content.addView(btnSacrifice); content.addView(btnLeave)
    }
}
