package com.rockhard.blocker

import android.graphics.Color
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

internal fun GameActivity.updatePartyScreen() {
    partyContainer.removeAllViews()
    if (party.isEmpty()) {
        partyContainer.addView(TextView(this).apply { text = "Party empty. You are defenseless."; setTextColor(Color.WHITE) })
        return
    }

    party.forEachIndexed { index, p ->
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
            if (index == activePetIndex) { setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_arrow_active, 0, 0, 0); compoundDrawablePadding = 16 }
            else setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)

            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }

            setOnClickListener {
                if (index == activePetIndex) {
                    val traitName = if (p.name.contains("]")) p.name.substringBefore("]").substringAfter("[") else "Ordinary"
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

                    val inf = if (p.infusionEl != "None") "${p.infusionEl} (Stacks: ${p.infusionStacks})" else "None"

                    DialogUtils.showCustomDialog(this@updatePartyScreen, "${p.name} Details", null, false, "CLOSE", null) { content, dialog ->
                        val headerLayout = LinearLayout(this@updatePartyScreen).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 5.5f; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); setPadding(0, 0, 0, 8) }
                        headerLayout.addView(TextView(this@updatePartyScreen).apply { text = "TRAIT"; setTextColor(Color.GRAY); textSize = 11f; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f) })
                        headerLayout.addView(TextView(this@updatePartyScreen).apply { text = "EFFECT"; setTextColor(Color.GRAY); textSize = 11f; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.3f) })
                        headerLayout.addView(TextView(this@updatePartyScreen).apply { text = "CAUSE"; setTextColor(Color.GRAY); textSize = 11f; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
                        headerLayout.addView(TextView(this@updatePartyScreen).apply { text = "RARITY"; setTextColor(Color.GRAY); textSize = 11f; gravity = android.view.Gravity.END; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })

                        val traitLayout = LinearLayout(this@updatePartyScreen).apply { orientation = LinearLayout.HORIZONTAL; weightSum = 5.5f; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT) }
                        traitLayout.addView(TextView(this@updatePartyScreen).apply { text = "[$traitName]"; setTextColor(Color.CYAN); textSize = 13f; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f) })
                        traitLayout.addView(TextView(this@updatePartyScreen).apply { text = tDesc; setTextColor(Color.WHITE); textSize = 13f; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2.3f) })
                        traitLayout.addView(TextView(this@updatePartyScreen).apply { text = tCause; setTextColor(Color.LTGRAY); textSize = 13f; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
                        traitLayout.addView(TextView(this@updatePartyScreen).apply { text = tRarityStr; setTextColor(rColor); textSize = 13f; gravity = android.view.Gravity.END; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })

                        val m1D = SkillEngine.getDetails(p.move1, p.maxHp)
                        val m2D = SkillEngine.getDetails(p.move2, p.maxHp)
                        val m3D = SkillEngine.getDetails(p.move3, p.maxHp)

                        val tvCombat = TextView(this@updatePartyScreen).apply { text = "\n--- COMBAT SKILLS ---\n⚔️ ${p.move1}:\n  ↳ $m1D\n⚔️ ${p.move2}:\n  ↳ $m2D\n⚔️ ${p.move3}:\n  ↳ $m3D\n\n--- STATS ---\nInfusion: $inf\nMax HP: ${p.maxHp}"; setTextColor(Color.WHITE); setPadding(0, 32, 0, 0) }

                        content.addView(headerLayout); content.addView(traitLayout); content.addView(tvCombat)
                    }
                } else {
                    activePetIndex = index; prefs.edit().putInt("ACTIVE_PET_INDEX", activePetIndex).apply(); updatePartyScreen(); updateBattleUI()
                }
            }
        }
        partyContainer.addView(btn)
    }
}