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

    // Removed "PENDING OFFERS" from this text block since they are now real buttons below!
    var sText = "$eventText$amText=== INVENTORY ===\n\n💰 Focus Coins: $focusCoins\n🧬 Trait Transfusers: $transfusers\n\n🕸️ Nets: $nets\n🧪 Potions: $potions\n💨 Sprays: $sprays\n\n"
    if (forSaleParty.isNotEmpty()) {
        sText += "--- LISTED ON MARKET ---\n"
        forSaleParty.forEach { sText += "- ${it.name}: ${it.listedPrice}c\n" }
    }
    tvBag.text = sText

    // UPDATE INSTANT USE POTION BUTTONS
    val shopContainer = findViewById<LinearLayout>(R.id.llShopContainer)
    val btnUseSmall = shopContainer?.findViewWithTag<Button>("BTN_USE_SMALL")
    val btnUseLarge = shopContainer?.findViewWithTag<Button>("BTN_USE_LARGE")

    if (smallHpPots > 0 && party.isNotEmpty()) {
        btnUseSmall?.visibility = View.VISIBLE
        btnUseSmall?.text = "USE SMALL HEALTH POTION [$smallHpPots] on ${party[activePetIndex].name}"
    } else btnUseSmall?.visibility = View.GONE

    if (largeHpPots > 0 && party.isNotEmpty()) {
        btnUseLarge?.visibility = View.VISIBLE
        btnUseLarge?.text = "USE LARGE HEALTH POTION [$largeHpPots] on ${party[activePetIndex].name}"
    } else btnUseLarge?.visibility = View.GONE
    
    // Refresh the Inline Market
    updateMarketUI()
}

internal fun GameActivity.updateMarketUI() {
    val marketContainer = findViewById<LinearLayout>(R.id.marketContainer) ?: return
    marketContainer.removeAllViews()
    
    val title = TextView(this).apply {
        text = "--- BEAST MARKET ---"
        setTextColor(Color.WHITE)
        setTypeface(null, android.graphics.Typeface.BOLD)
        textAlignment = View.TEXT_ALIGNMENT_CENTER
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 32, 0, 16) }
    }
    marketContainer.addView(title)

    // 1. SHOW PENDING OFFERS (Golden Pill Buttons)
    activeOffers.forEach { (beastName, offerAmount) ->
        val btnOffer = Button(this).apply { 
            text = "ACCEPT OFFER: ${offerAmount}c for $beastName"
            setBackgroundResource(R.drawable.bg_btn_pill)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
            setOnClickListener {
                val beast = forSaleParty.find { it.name == beastName }
                if (beast != null) { 
                    forSaleParty.remove(beast)
                    focusCoins += offerAmount
                    activeOffers.remove(beastName)
                    SaveManager.saveParty(prefs, "FORSALE_DATA", forSaleParty)
                    saveItems()
                    updateBagScreen()
                    Toast.makeText(this@updateMarketUI, "Sold $beastName for ${offerAmount}c!", Toast.LENGTH_SHORT).show() 
                }
            }
        }
        marketContainer.addView(btnOffer)
    }

    // 2. SHOW BEASTS FOR SALE
    marketBeasts.forEach { beast ->
        val btnBuy = Button(this).apply { 
            text = "Make Offer: ${beast.name} Lvl ${beast.maxHp / 10}\n(Asking: ${beast.listedPrice}c)"
            setBackgroundResource(R.drawable.bg_btn_standard)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
            setOnClickListener { openBidDialog(beast) } 
        }
        marketContainer.addView(btnBuy)
    }

    if (marketBeasts.isEmpty() && activeOffers.isEmpty()) {
        marketContainer.addView(TextView(this).apply { 
            text = "The market is empty right now."
            setTextColor(Color.LTGRAY)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        })
    }
}

internal fun GameActivity.setupShop() {
    fun handleTx(cost: Int, isBuy: Boolean, itemType: String) {
        if (isBuy) {
            if (focusCoins >= cost) { focusCoins -= cost; when (itemType) { "NET" -> nets++; "POTION" -> potions++; "SPRAY" -> sprays++; "SMALL_HP" -> smallHpPots++; "LARGE_HP" -> largeHpPots++ }; Toast.makeText(this, "Bought 1 $itemType", Toast.LENGTH_SHORT).show() } 
            else Toast.makeText(this, "Not enough Focus Coins!", Toast.LENGTH_SHORT).show()
        } else {
            var hasItem = false
            when (itemType) { "NET" -> if (nets > 0) { nets--; hasItem = true }; "POTION" -> if (potions > 0) { potions--; hasItem = true }; "SPRAY" -> if (sprays > 0) { sprays--; hasItem = true } }
            if (hasItem) { focusCoins += cost; Toast.makeText(this, "Sold 1 $itemType", Toast.LENGTH_SHORT).show() }
        }
        saveItems(); updateBagScreen()
    }

    val shopContainer = findViewById<LinearLayout>(R.id.llShopContainer)

    // 1. INSTANT USE BUTTONS (Added to very top of shop)
    val btnUseSmall = Button(this).apply { tag = "BTN_USE_SMALL"; setBackgroundResource(R.drawable.bg_btn_success); setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
        setOnClickListener {
            if (smallHpPots > 0 && party.isNotEmpty()) {
                smallHpPots--; val p = party[activePetIndex]; val heal = (p.maxHp * 0.33).toInt()
                p.hp = (p.hp + heal).coerceAtMost(p.maxHp)
                saveItems(); saveParty(); updateBagScreen(); updatePartyScreen(); updateBattleUI()
                Toast.makeText(this@setupShop, "${p.name} healed for $heal HP!", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val btnUseLarge = Button(this).apply { tag = "BTN_USE_LARGE"; setBackgroundResource(R.drawable.bg_btn_success); setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 24) }
        setOnClickListener {
            if (largeHpPots > 0 && party.isNotEmpty()) {
                largeHpPots--; val p = party[activePetIndex]
                p.hp = p.maxHp
                saveItems(); saveParty(); updateBagScreen(); updatePartyScreen(); updateBattleUI()
                Toast.makeText(this@setupShop, "${p.name} fully healed!", Toast.LENGTH_SHORT).show()
            }
        }
    }
    shopContainer.addView(btnUseLarge, 0)
    shopContainer.addView(btnUseSmall, 0)

    // 2. STANDARD SHOP BINDS
    findViewById<Button>(R.id.btnBuyNet).setOnClickListener { handleTx(5, true, "NET") }
    findViewById<Button>(R.id.btnSellNet).setOnClickListener { handleTx(2, false, "NET") }
    findViewById<Button>(R.id.btnBuyPotion).setOnClickListener { handleTx(10, true, "POTION") }
    findViewById<Button>(R.id.btnSellPotion).setOnClickListener { handleTx(5, false, "POTION") }
    findViewById<Button>(R.id.btnBuySpray).setOnClickListener { handleTx(15, true, "SPRAY") }
    findViewById<Button>(R.id.btnSellSpray).setOnClickListener { handleTx(7, false, "SPRAY") }

    // 3. EXPENSIVE HEALING POTIONS
    val btnBuySmall = Button(this).apply { text = "Buy Small Health Potion (200c)"; setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
        setOnClickListener { handleTx(200, true, "SMALL_HP") }
    }
    val btnBuyLarge = Button(this).apply { text = "Buy Large Health Potion (600c)"; setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
        setOnClickListener { handleTx(600, true, "LARGE_HP") }
    }
    
    // Look for the "--- BEASTS FOR SALE ---" title to insert these right above the market
    val marketTitle = findViewById<TextView>(R.id.tvMarketTitle)
    if (marketTitle != null) {
        val index = shopContainer.indexOfChild(marketTitle)
        shopContainer.addView(btnBuyLarge, index)
        shopContainer.addView(btnBuySmall, index)
        marketTitle.visibility = View.GONE // We injected a better dynamic one inside updateMarketUI!
    } else {
        shopContainer.addView(btnBuySmall)
        shopContainer.addView(btnBuyLarge)
    }
}

internal fun GameActivity.openBidDialog(beast: Netbeast) {
    DialogUtils.showCustomDialog(this, "Negotiate with Seller", "Make an offer for ${beast.name}.", true, null, null) { content, dialog ->
        val bids = listOf(Pair("Lowball", (beast.listedPrice * 0.7).toInt()), Pair("Moderate", beast.listedPrice), Pair("Generous", (beast.listedPrice * 1.3).toInt()))
        bids.forEach { (type, amount) ->
            val btnBid = Button(this).apply { text = "$type Offer (${amount}c)"; setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
                setOnClickListener {
                    if (focusCoins < amount) { Toast.makeText(this@openBidDialog, "Not enough coins!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                    val successChance = when (type) { "Lowball" -> 30; "Moderate" -> 75; else -> 100 }
                    if (kotlin.random.Random.nextInt(100) < successChance) {
                        focusCoins -= amount; marketBeasts.remove(beast); beast.isNew = true; party.add(beast); saveItems(); saveParty(); SaveManager.saveParty(prefs, "MARKET_DATA", marketBeasts); updatePartyScreen(); updateBagScreen(); printLog("\n> 🤝 DEAL! You bought ${beast.name} for ${amount}c!")
                    } else {
                        marketBeasts.remove(beast); SaveManager.saveParty(prefs, "MARKET_DATA", marketBeasts)
                        if (type == "Lowball") printLog("\n> 😡 The seller was insulted by your lowball and walked away!") else printLog("\n> 😭 Someone else bought it while you were negotiating!")
                    }
                    dialog.dismiss()
                }
            }; content.addView(btnBid)
        }
    }
}
