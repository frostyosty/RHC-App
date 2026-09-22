package com.rockhard.blocker

import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

internal fun GameActivity.updateBagScreen() {
    val activeEvent = prefs.getBoolean("EVENT_ACTIVE", false)
    var eventText = ""
    if (activeEvent) {
        val eBoss = prefs.getString("EVENT_BOSS_NAME", "")
        val eDay = prefs.getString("EVENT_TARGET_DAY", "")
        val eWeak = prefs.getString("EVENT_WEAKNESS", "")
        eventText = "⚠️ INVASION PENDING ⚠️\n$eBoss approaches on $eDay!\nWeakness: $eWeak\n\n"
    }

    val amulets = prefs.getInt("PLAYER_UNCLAIMED_AMULETS", 0)
    var amText = ""
    if (amulets > 0) amText = "🔮 UNCLAIMED TITAN AMULETS: $amulets\n(Use 'Distribute' button to equip)\n\n"
    
    val unlockShop = true
    findViewById<Button>(R.id.tabBag).text = if (unlockShop) "BAG/SHOP" else "BAG"
    findViewById<View>(R.id.llShopContainer).visibility = if (unlockShop) View.VISIBLE else View.GONE

    findViewById<Button>(R.id.btnEqNet)?.text = "+ NET [$nets]"
    findViewById<Button>(R.id.btnEqPot)?.text = "+ POT [$potions]"
    findViewById<Button>(R.id.btnEqSpray)?.text = "+ SPRAY [$sprays]"

    var sText = "$eventText$amText=== INVENTORY ===\n\n💰 Focus Coins: $focusCoins\n🧬 Trait Transfusers: $transfusers\n\n🕸️ Nets: $nets\n🧪 Potions: $potions\n💨 Sprays: $sprays\n\n"
    if (forSaleParty.isNotEmpty()) {
        sText += "--- LISTED ON MARKET ---\n"
        forSaleParty.forEach { sText += "- ${it.name}: ${it.listedPrice}c\n" }
    }
    tvBag.text = sText

    // INJECT USE POTION BUTTONS INTO THE BAG AREA (SQUISHED)
    val llBagActions = findViewById<LinearLayout>(R.id.llBagActions)
    llBagActions?.removeAllViews()
    
    if (smallHpPots > 0 && party.isNotEmpty()) {
        val btn = Button(this).apply {
            text = "USE SM HP ($smallHpPots)"
            setBackgroundResource(R.drawable.bg_btn_success); setTextColor(Color.WHITE); textSize = 10f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, 4, 0) }
            setOnClickListener {
                smallHpPots--; val p = party[activePetIndex]; val heal = (p.maxHp * 0.33).toInt()
                p.hp = (p.hp + heal).coerceAtMost(p.maxHp)
                saveItems(); saveParty(); updateBagScreen(); updatePartyScreen(); updateBattleUI()
                Toast.makeText(this@updateBagScreen, "${p.name} healed for $heal HP!", Toast.LENGTH_SHORT).show()
            }
        }
        llBagActions?.addView(btn)
    }
    
    if (largeHpPots > 0 && party.isNotEmpty()) {
        val btn = Button(this).apply {
            text = "USE LG HP ($largeHpPots)"
            setBackgroundResource(R.drawable.bg_btn_success); setTextColor(Color.WHITE); textSize = 10f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4, 0, 0, 0) }
            setOnClickListener {
                largeHpPots--; val p = party[activePetIndex]; p.hp = p.maxHp
                saveItems(); saveParty(); updateBagScreen(); updatePartyScreen(); updateBattleUI()
                Toast.makeText(this@updateBagScreen, "${p.name} fully healed!", Toast.LENGTH_SHORT).show()
            }
        }
        llBagActions?.addView(btn)
    }
    
    updateMarketUI()
}

internal fun GameActivity.updateMarketUI() {
    val marketContainer = findViewById<LinearLayout>(R.id.marketContainer) ?: return
    marketContainer.removeAllViews()
    
    val title = TextView(this).apply { text = "--- BEAST MARKET ---"; setTextColor(Color.WHITE); setTypeface(null, android.graphics.Typeface.BOLD); textAlignment = View.TEXT_ALIGNMENT_CENTER; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 16, 0, 16) } }
    marketContainer.addView(title)

    activeOffers.forEach { (beastName, offerAmount) ->
        val btnOffer = Button(this).apply { 
            text = "ACCEPT OFFER: ${offerAmount}c for $beastName"
            setBackgroundResource(R.drawable.bg_btn_pill); setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
            setOnClickListener {
                val beast = forSaleParty.find { it.name == beastName }
                if (beast != null) { 
                    forSaleParty.remove(beast); focusCoins += offerAmount; activeOffers.remove(beastName)
                    SaveManager.saveParty(prefs, "FORSALE_DATA", forSaleParty); saveItems(); updateBagScreen()
                    printShopLog("> 🤝 Sold $beastName for ${offerAmount}c!")
                }
            }
        }; marketContainer.addView(btnOffer)
    }

    marketBeasts.forEach { beast ->
        val btnBuy = Button(this).apply { 
            text = "Make Offer: ${beast.name} Lvl ${beast.maxHp / 10}\n(Asking: ${beast.listedPrice}c)"
            setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
            setOnClickListener { openBidDialog(beast) } 
        }; marketContainer.addView(btnBuy)
    }

    if (marketBeasts.isEmpty() && activeOffers.isEmpty()) {
        marketContainer.addView(TextView(this).apply { text = "The market is empty right now."; setTextColor(Color.LTGRAY); textAlignment = View.TEXT_ALIGNMENT_CENTER })
    }
}

internal fun GameActivity.setupShop() {
    fun handleTx(cost: Int, isBuy: Boolean, itemType: String) {
        if (isBuy) {
            if (focusCoins >= cost) { focusCoins -= cost; when (itemType) { "NET" -> nets++; "POTION" -> potions++; "SPRAY" -> sprays++; "SMALL_HP" -> smallHpPots++; "LARGE_HP" -> largeHpPots++ }; printShopLog("> 🛒 Bought 1 $itemType (-${cost}c)") } 
            else printShopLog("> ❌ Not enough Focus Coins for $itemType!")
        } else {
            var hasItem = false
            when (itemType) { "NET" -> if (nets > 0) { nets--; hasItem = true }; "POTION" -> if (potions > 0) { potions--; hasItem = true }; "SPRAY" -> if (sprays > 0) { sprays--; hasItem = true } }
            if (hasItem) { focusCoins += cost; printShopLog("> 💰 Sold 1 $itemType (+${cost}c)") }
            else printShopLog("> ❌ You don't have any $itemType to sell!")
        }
        saveItems(); updateBagScreen()
    }

    findViewById<Button>(R.id.btnBuyNet).setOnClickListener { handleTx(5, true, "NET") }
    findViewById<Button>(R.id.btnSellNet).setOnClickListener { handleTx(2, false, "NET") }
    findViewById<Button>(R.id.btnBuyPotion).setOnClickListener { handleTx(10, true, "POTION") }
    findViewById<Button>(R.id.btnSellPotion).setOnClickListener { handleTx(5, false, "POTION") }
    findViewById<Button>(R.id.btnBuySpray).setOnClickListener { handleTx(15, true, "SPRAY") }
    findViewById<Button>(R.id.btnSellSpray).setOnClickListener { handleTx(7, false, "SPRAY") }
    
    // NEW SQUISHED EXPENSIVE POTIONS
    findViewById<Button>(R.id.btnBuySmallHp).setOnClickListener { handleTx(200, true, "SMALL_HP") }
    findViewById<Button>(R.id.btnBuyLargeHp).setOnClickListener { handleTx(600, true, "LARGE_HP") }

    val btnGrave = findViewById<Button>(R.id.btnGraveyard)
    if (totalExpeds >= 4) {
        btnGrave?.visibility = View.VISIBLE
        btnGrave?.setOnClickListener { openGraveyardDialog() }
    } else btnGrave?.visibility = View.GONE
}

internal fun GameActivity.openBidDialog(beast: Netbeast) {
    DialogUtils.showCustomDialog(this, "Negotiate with Seller", "Make an offer for ${beast.name}.", true, "", null) { content, dialog ->
        val bids = listOf(Pair("Lowball", (beast.listedPrice * 0.7).toInt()), Pair("Moderate", beast.listedPrice), Pair("Generous", (beast.listedPrice * 1.3).toInt()))
        bids.forEach { (type, amount) ->
            val btnBid = Button(this).apply { text = "$type Offer (${amount}c)"; setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
                setOnClickListener {
                    if (focusCoins < amount) { printShopLog("> ❌ Not enough coins!"); return@setOnClickListener }
                    val successChance = when (type) { "Lowball" -> 30; "Moderate" -> 75; else -> 100 }
                    if (kotlin.random.Random.nextInt(100) < successChance) {
                        focusCoins -= amount; marketBeasts.remove(beast); beast.isNew = true; party.add(beast); saveItems(); saveParty(); SaveManager.saveParty(prefs, "MARKET_DATA", marketBeasts); updatePartyScreen(); updateBagScreen(); printShopLog("\n> 🤝 DEAL! You bought ${beast.name} for ${amount}c!")
                    } else {
                        marketBeasts.remove(beast); SaveManager.saveParty(prefs, "MARKET_DATA", marketBeasts)
                        if (type == "Lowball") printShopLog("\n> 😡 The seller was insulted by your lowball and walked away!") else printShopLog("\n> 😭 Someone else bought it while you were negotiating!")
                    }
                    dialog.dismiss()
                }
            }; content.addView(btnBid)
        }
    }
}

internal fun GameActivity.openGraveyardDialog() {
    if (graveyard.isEmpty()) { Toast.makeText(this, "The Hall of Fame is empty.", Toast.LENGTH_SHORT).show(); return }
    DialogUtils.showCustomDialog(this, "Hall of Fame", "Resurrect a fallen adversary for 1000c.", true, "CLOSE", null) { content, dialog ->
        graveyard.forEach { beast ->
            val btn = Button(this).apply {
                text = "Resurrect ${beast.name} Lvl ${beast.maxHp/10} (1000c)"
                setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
                setOnClickListener {
                    if (focusCoins < 1000) { Toast.makeText(this@openGraveyardDialog, "Not enough coins!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                    focusCoins -= 1000; graveyard.remove(beast); beast.isNew = true; party.add(beast)
                    saveItems(); saveParty(); SaveManager.saveParty(prefs, "GRAVEYARD_DATA", graveyard)
                    updatePartyScreen(); updateBagScreen(); printShopLog("\n> ⚡ You resurrected ${beast.name}!")
                    dialog.dismiss()
                }
            }; content.addView(btn)
        }
    }
}
