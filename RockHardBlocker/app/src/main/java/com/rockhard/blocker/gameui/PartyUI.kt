package com.rockhard.blocker

import android.graphics.Color
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.random.Random

internal fun GameActivity.updatePartyScreen() {
    partyContainer.removeAllViews()
    if (party.isEmpty()) {
        partyContainer.addView(TextView(this).apply { text = "Party empty. You are defenseless."; setTextColor(Color.WHITE) })
        return
    }

    party.forEachIndexed { index, p ->
        val rowContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
            setBackgroundColor(if (index == activePetIndex) Color.parseColor("#1976D2") else Color.parseColor("#333333"))
            setPadding(30, 30, 30, 30)
        }

        val mainBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val tvArrow = TextView(this).apply {
            text = "➤  "
            setTextColor(Color.WHITE)
            textSize = 18f
            visibility = if (index == activePetIndex) android.view.View.VISIBLE else android.view.View.INVISIBLE 
        }

        val textDataLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val newTag = if (p.isNew) "✨ " else ""
        val infTag = if (p.infusionEl != "None") "[🌟 ${p.infusionEl}: ${p.infusionStacks}] " else ""
        val hpPercent = (p.hp.toFloat() / p.maxHp.toFloat()) * 10
        val bar = "█".repeat(hpPercent.toInt().coerceAtLeast(0)) + "-".repeat((10 - hpPercent.toInt()).coerceAtLeast(0))

        val tvName = TextView(this).apply {
            val cleanBaseName = p.name.split(" ").last()
            val displayName = if (p.name.contains("[Obscure]")) "[Obscure] $cleanBaseName" else p.name
            text = "$newTag$infTag$displayName"
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val tvStats = TextView(this).apply {
            text = "HP:[$bar] ${p.hp.coerceAtLeast(0)}/${p.maxHp}\nEq: 🕸️${p.eqNets}  🧪${p.eqPots}  💨${p.eqSprays}"
            setTextColor(Color.LTGRAY)
            textSize = 14f
        }
        textDataLayout.addView(tvName); textDataLayout.addView(tvStats)

        mainBar.addView(tvArrow); mainBar.addView(textDataLayout)

        if (index == activePetIndex) {
            val btnSell = Button(this).apply {
                text = "SELL"
                setBackgroundResource(R.drawable.bg_btn_danger)
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(16, 0, 0, 0) }
                setOnClickListener {
                    if (party.size <= 1) { Toast.makeText(this@updatePartyScreen, "Cannot sell your last Netbeast!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                    if (activeExpeditions.containsKey(activePetIndex)) { Toast.makeText(this@updatePartyScreen, "Cannot sell while exploring!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                    val suggestedPrice = (p.maxHp / 10) * Random.nextInt(4, 8)
                    DialogUtils.showCustomDialog(this@updatePartyScreen, "List on Market?", "List ${p.name} for ${suggestedPrice}c?", true, "LIST", {
                        p.listedPrice = suggestedPrice; forSaleParty.add(p); party.removeAt(activePetIndex); activePetIndex = 0; prefs.edit().putInt("ACTIVE_PET_INDEX", 0).apply()
                        saveParty(); SaveManager.saveParty(prefs, "FORSALE_DATA", forSaleParty); updatePartyScreen(); updateBagScreen()
                        printLog("\n> 📦 You listed ${p.name} on the market for ${suggestedPrice}c.")
                    }, null)
                }
            }
            mainBar.addView(btnSell)
        }

        val detailsPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 32, 0, 0) }
            visibility = android.view.View.GONE
        }

        val traitName = if (p.name.contains("[Obscure]")) "Obscure" else if (p.name.contains("]")) p.name.substringBefore("]").substringAfter("[") else "Ordinary"
        val traitDef = GameData.traits.find { it.name == traitName }
        val tDesc = traitDef?.desc ?: "Standard stats."
        val tCause = traitDef?.trigger ?: "None"
        val tRarityRaw = traitDef?.rarityWeight ?: 100
        
        val (tRarityStr, rColor) = when {
            tRarityRaw >= 40 -> Pair("Common", Color.LTGRAY)
            tRarityRaw >= 20 -> Pair("Uncommon", Color.GREEN)
            tRarityRaw >= 10 -> Pair("Rare", Color.parseColor("#33B5E5"))
            tRarityRaw >= 5 -> Pair("Epic", Color.parseColor("#AA66CC"))
            else -> Pair("Legendary", Color.parseColor("#FFBB33"))
        }

        val st = p.infusionStacks
        val infDesc = when (p.infusionEl) {
            "Clear" -> "+${15 * st}% Net Catch Rate"
            "Cloudy" -> "+${15 * st}% Evasion"
            "Rain" -> "+${20 * st}% Critical Hit Chance"
            "Storm" -> "+${50 * st}% Damage"
            "Snow" -> "Damage Taken divided by ${st + 1}"
            else -> "None"
        }

        val headerLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 5.5f; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); setPadding(0, 0, 0, 8) }
        headerLayout.addView(TextView(this).apply { text = "TRAIT"; setTextColor(Color.GRAY); textSize = 11f; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f) })
        headerLayout.addView(TextView(this).apply { text = "EFFECT"; setTextColor(Color.GRAY); textSize = 11f; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.3f) })
        headerLayout.addView(TextView(this).apply { text = "CAUSE"; setTextColor(Color.GRAY); textSize = 11f; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        headerLayout.addView(TextView(this).apply { text = "RARITY"; setTextColor(Color.GRAY); textSize = 11f; gravity = android.view.Gravity.END; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })

        val traitLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 5.5f; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT) }
        traitLayout.addView(TextView(this).apply { text = "[$traitName]"; setTextColor(Color.CYAN); textSize = 13f; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f) })
        traitLayout.addView(TextView(this).apply { text = tDesc; setTextColor(Color.WHITE); textSize = 13f; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.3f) })
        traitLayout.addView(TextView(this).apply { text = tCause; setTextColor(Color.LTGRAY); textSize = 13f; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        traitLayout.addView(TextView(this).apply { text = tRarityStr; setTextColor(rColor); textSize = 13f; gravity = android.view.Gravity.END; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })

        val m1D = SkillEngine.getDetails(p.move1, p.maxHp)
        val m2D = SkillEngine.getDetails(p.move2, p.maxHp)
        val m3D = SkillEngine.getDetails(p.move3, p.maxHp)

        val tvCombat = TextView(this).apply { text = "\n--- COMBAT SKILLS ---\n⚔️ ${p.move1}:\n  ↳ $m1D\n⚔️ ${p.move2}:\n  ↳ $m2D\n⚔️ ${p.move3}:\n  ↳ $m3D\n\n--- STATS ---\nMax HP: ${p.maxHp}"; setTextColor(Color.WHITE); setPadding(0, 32, 0, 0) }

        // --- NEW LORE GENERATOR ---
        val cleanLoreName = p.name.replace(Regex("\\[.*?\\]"), "").trim()
        val originSite = when(p.type) {
            "Tech" -> listOf("sys-kernel.org", "packet-route.net", "data-cache.io", "server-farm.local").let { it[(p.name.hashCode() and 0x7FFFFFFF) % it.size] }
            "Social" -> listOf("scroll-feed.com", "chat-hub.app", "echo-chamber.net", "status-update.io").let { it[(p.name.hashCode() and 0x7FFFFFFF) % it.size] }
            "Gaming" -> listOf("loot-drop.gg", "match-maker.net", "boss-raid.com", "xp-grind.io").let { it[(p.name.hashCode() and 0x7FFFFFFF) % it.size] }
            "Streaming" -> listOf("vid-stream.tv", "binge-watch.com", "media-buffer.net", "auto-play.io").let { it[(p.name.hashCode() and 0x7FFFFFFF) % it.size] }
            "Flying" -> listOf("cloud-server.net", "aero-net.org", "strato-host.com").let { it[(p.name.hashCode() and 0x7FFFFFFF) % it.size] }
            "Reclaimed" -> {
                val baseApp = cleanLoreName.lowercase()
                    .replace("saurus","").replace("ling","").replace("let","")
                    .replace("puff","").replace("wing","").replace("sprite","")
                    .replace("bot","").replace("mon","").replace("fox","")
                "$baseApp.com"
            }
            else -> "the digital frontier"
        }
        val loreText = if (originSite.contains(" ")) originSite else "www.$originSite"
        val tvLore = TextView(this).apply { text = "\nFound roaming $loreText"; setTextColor(Color.parseColor("#888888")); setTypeface(null, android.graphics.Typeface.ITALIC); setPadding(0, 16, 0, 0) }

        detailsPanel.addView(headerLayout)
        detailsPanel.addView(traitLayout)
        
        // FIXED: Replaced the broken line-break!
        if (p.infusionStacks > 0) {
            detailsPanel.addView(TextView(this@updatePartyScreen).apply { 
                text = "🌟 ${p.infusionEl} Infused (Stacks: ${p.infusionStacks})\n  ↳ $infDesc"
                setTextColor(Color.parseColor("#E040FB"))
                textSize = 11f
                setPadding(0, 16, 0, 0) 
            })
        }
        
        detailsPanel.addView(tvCombat)
        detailsPanel.addView(tvLore)

        mainBar.setOnClickListener {
            if (index == activePetIndex) {
                detailsPanel.visibility = if (detailsPanel.visibility == android.view.View.GONE) android.view.View.VISIBLE else android.view.View.GONE
            } else {
                activePetIndex = index; prefs.edit().putInt("ACTIVE_PET_INDEX", activePetIndex).apply()
                updatePartyScreen(); updateBattleUI()
            }
        }

        rowContainer.addView(mainBar); rowContainer.addView(detailsPanel)
        partyContainer.addView(rowContainer)
    }
}