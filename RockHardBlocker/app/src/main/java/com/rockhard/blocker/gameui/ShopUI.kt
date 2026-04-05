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

    var sText = "$eventText$amText=== INVENTORY ===\n\n💰 Focus Coins: $focusCoins\n🧬 Trait Transfusers: $transfusers\n\n🕸️ Nets: $nets\n🧪 Potions: $potions\n💨 Sprays: $sprays\n\n"
    if (forSaleParty.isNotEmpty()) {
        sText += "--- LISTED ON MARKET ---\n"
        forSaleParty.forEach { sText += "- ${it.name}: ${it.listedPrice}c\n" }
    }
    if (activeOffers.isNotEmpty()) {
        sText += "\n--- PENDING OFFERS ---\n"
        activeOffers.forEach { (name, offer) -> sText += "- $name: ${offer}c (Click 'ENTER MARKET' to review)\n" }
    }
    tvBag.text = sText
}

internal fun GameActivity.setupShop() {
    fun handleTx(cost: Int, isBuy: Boolean, itemType: String) {
        if (isBuy) {
            if (focusCoins >= cost) { focusCoins -= cost; when (itemType) { "NET" -> nets++; "POTION" -> potions++; "SPRAY" -> sprays++ }; Toast.makeText(this, "Bought 1 $itemType", Toast.LENGTH_SHORT).show() } 
            else Toast.makeText(this, "Not enough Focus Coins!", Toast.LENGTH_SHORT).show()
        } else {
            var hasItem = false
            when (itemType) { "NET" -> if (nets > 0) { nets--; hasItem = true }; "POTION" -> if (potions > 0) { potions--; hasItem = true }; "SPRAY" -> if (sprays > 0) { sprays--; hasItem = true } }
            if (hasItem) { focusCoins += cost; Toast.makeText(this, "Sold 1 $itemType", Toast.LENGTH_SHORT).show() }
        }
        saveItems(); updateBagScreen()
    }

    val shopContainer = findViewById<LinearLayout>(R.id.llShopContainer)
    val btnMarket = Button(this).apply { text = "ENTER BEAST MARKET"; setBackgroundResource(R.drawable.bg_btn_accent); setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 24) }; setOnClickListener { openMarketDialog() } }
    shopContainer.addView(btnMarket, 0)

    findViewById<Button>(R.id.btnBuyNet).setOnClickListener { handleTx(5, true, "NET") }
    findViewById<Button>(R.id.btnSellNet).setOnClickListener { handleTx(2, false, "NET") }
    findViewById<Button>(R.id.btnBuyPotion).setOnClickListener { handleTx(10, true, "POTION") }
    findViewById<Button>(R.id.btnSellPotion).setOnClickListener { handleTx(5, false, "POTION") }
    findViewById<Button>(R.id.btnBuySpray).setOnClickListener { handleTx(15, true, "SPRAY") }
    findViewById<Button>(R.id.btnSellSpray).setOnClickListener { handleTx(7, false, "SPRAY") }
}

internal fun GameActivity.openMarketDialog() {
    DialogUtils.showCustomDialog(this, "The Safari Lodge", "Buy wild beasts or review offers.", true, "CLOSE", null) { content, dialog ->
        activeOffers.forEach { (beastName, offerAmount) ->
            val btnOffer = Button(this).apply { text = "ACCEPT OFFER: ${offerAmount}c for $beastName"; setBackgroundColor(Color.parseColor("#FF9800")); setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
                setOnClickListener {
                    val beast = forSaleParty.find { it.name == beastName }
                    if (beast != null) { forSaleParty.remove(beast); focusCoins += offerAmount; activeOffers.remove(beastName); SaveManager.saveParty(prefs, "FORSALE_DATA", forSaleParty); saveItems(); updateBagScreen(); Toast.makeText(this@openMarketDialog, "Sold $beastName for ${offerAmount}c!", Toast.LENGTH_SHORT).show() }
                    dialog.dismiss(); openMarketDialog()
                }
            }; content.addView(btnOffer)
        }
        marketBeasts.forEach { beast ->
            val btnBuy = Button(this).apply { text = "Make Offer: ${beast.name} Lvl ${beast.maxHp / 10}\n(Asking: ${beast.listedPrice}c)"; setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }; setOnClickListener { dialog.dismiss(); openBidDialog(beast) } }
            content.addView(btnBuy)
        }
        if (marketBeasts.isEmpty() && activeOffers.isEmpty()) content.addView(TextView(this).apply { text = "The market is empty right now."; setTextColor(Color.WHITE) })
    }
}

internal fun GameActivity.openBidDialog(beast: Netbeast) {
    DialogUtils.showCustomDialog(this, "Negotiate with Seller", "Make an offer for ${beast.name}.", true, "CANCEL", null) { content, dialog ->
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