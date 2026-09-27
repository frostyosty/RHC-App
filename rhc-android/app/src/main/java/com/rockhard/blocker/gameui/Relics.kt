package com.rockhard.blocker

import android.content.SharedPreferences
import android.graphics.Color
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Relics: expensive shop items you buy once and then equip or unequip.
 * - Cloak of Christ: the first [CLOAK_BEASTS] beasts on each explore never notice you.
 * - Sword of the Spirit: your punch becomes a sword swing that slays any netbeast it hits.
 */
object Relics {
    const val CLOAK_PRICE = 3000
    const val SWORD_PRICE = 5000
    const val CLOAK_BEASTS = 2

    data class Relic(val key: String, val name: String, val price: Int, val desc: String)

    val ALL = listOf(
        Relic("CLOAK", "🕊️ Cloak of Christ", CLOAK_PRICE, "The first $CLOAK_BEASTS netbeasts on each explore look straight through you, and you walk on past."),
        Relic("SWORD", "⚔️ Sword of the Spirit", SWORD_PRICE, "Your punch becomes a sword swing that slays any netbeast it hits in one blow."),
    )

    fun owned(prefs: SharedPreferences, key: String) = prefs.getBoolean("RELIC_OWN_$key", false)
    fun equipped(prefs: SharedPreferences, key: String) = owned(prefs, key) && prefs.getBoolean("RELIC_ON_$key", false)
    fun cloakOn(prefs: SharedPreferences) = equipped(prefs, "CLOAK")
    fun swordOn(prefs: SharedPreferences) = equipped(prefs, "SWORD")
}

/** The relic rows in the shop: BUY until you own one, then EQUIP / UNEQUIP. */
internal fun GameActivity.updateRelicsUI() {
    val box = findViewById<LinearLayout>(R.id.llRelics) ?: return
    box.removeAllViews()
    box.addView(TextView(this).apply { text = "--- RELICS ---"; setTextColor(Color.parseColor("#FFC940")); textAlignment = TextView.TEXT_ALIGNMENT_CENTER; setPadding(0, 16, 0, 8) })
    for (r in Relics.ALL) {
        val owned = Relics.owned(prefs, r.key)
        val on = Relics.equipped(prefs, r.key)
        box.addView(TextView(this).apply { text = "${r.name}\n  ↳ ${r.desc}"; setTextColor(Color.WHITE); textSize = 12f; setPadding(0, 8, 0, 4) })
        box.addView(Button(this).apply {
            text = when { !owned -> "BUY (${r.price}c)"; on -> "✅ EQUIPPED (tap to unequip)"; else -> "EQUIP" }
            setBackgroundResource(if (on) R.drawable.bg_btn_success else if (owned) R.drawable.bg_btn_standard else R.drawable.bg_btn_accent)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 8) }
            setOnClickListener {
                if (!owned) {
                    if (focusCoins < r.price) { printShopLog("> ❌ Not enough Focus Coins for the ${r.name}!"); return@setOnClickListener }
                    focusCoins -= r.price
                    prefs.edit().putBoolean("RELIC_OWN_${r.key}", true).putBoolean("RELIC_ON_${r.key}", true).apply()
                    printShopLog("> 🛒 Bought the ${r.name} (-${r.price}c). It's equipped.")
                    saveItems()
                } else {
                    prefs.edit().putBoolean("RELIC_ON_${r.key}", !on).apply()
                    printShopLog(if (on) "> Unequipped the ${r.name}." else "> Equipped the ${r.name}.")
                }
                updateBagScreen()
            }
        })
    }
}
