package com.rockhard.blocker

import android.view.View
import android.widget.Toast
import com.rockhard.blocker.world.WorldView
import com.rockhard.blocker.world.render.Palette
import com.rockhard.blocker.world.sim.EncounterOutcome
import com.rockhard.blocker.world.sim.LocalWorldSession
import com.rockhard.blocker.world.sim.Species
import com.rockhard.blocker.world.sim.World
import com.rockhard.blocker.world.sim.WorldEvent
import com.rockhard.blocker.world.sim.WorldMap
import kotlin.random.Random

// Glue between the 3D Wilds (world/) and the existing game: starting an
// exploration, turning a world encounter into a normal wild battle, and
// finishing when the walk timer runs out.

/** 3D exploring is the default; Settings > "Explore Netbeasts in 3D" turns it off (text log instead). */
internal val GameActivity.world3dEnabled get() = prefs.getBoolean("WORLD_3D", true)

private const val EXPLORE_SECONDS = 180 // same length as a text-mode expedition

/** Everyone gets the same Wilds on the same day (a shared seed is what multiplayer will need). */
private fun todaysSeed(): Long {
    val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
    return day.toLong() * 2654435761L
}

/** The species in your lead cage (trait tags stripped), or null if you have no netbeasts. */
private fun GameActivity.leadCompanion(): String? =
    party.getOrNull(activePetIndex.coerceIn(0, (party.size - 1).coerceAtLeast(0)))?.name?.replace(Regex("\\[.*?\\]"), "")?.trim()

internal fun GameActivity.enterWorld() {
    if (aetherDepleted) { Toast.makeText(this, "Aether depleted! Return tomorrow.", Toast.LENGTH_SHORT).show(); return }
    if (inWorld) return
    val species = GameData.beasts.map { Species(it.name, it.evoStage) }
    worldSession = LocalWorldSession(World(WorldMap.generate(todaysSeed(), species)), playerId, leadCompanion(), EXPLORE_SECONDS)
    inWorld = true
    val cage = leadCompanion()?.let { "$it rattles in its cage." } ?: "You have no netbeasts. You'll have to fight yourself."
    printLog("\n> 🌲 You set off into the Wilds. There's no turning back until the walk is done. $cage")
    showWorld()
    updateDispatchButton()
}

internal fun GameActivity.showWorld() {
    val view = findViewById<WorldView>(R.id.worldView) ?: return
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    view.session = worldSession
    view.palette = Palette.forWeather(currentWeather, hour)
    view.listener = object : WorldView.Listener {
        override fun onEncounter(e: WorldEvent.Encounter) {
            val cage = leadCompanion()
            printLog(if (cage != null) "\n> ⚠️ A wild ${e.species} charges! You throw ${cage}'s cage!" else "\n> ⚠️ A wild ${e.species} charges straight at YOU!")
        }
        override fun onBattleReady(e: WorldEvent.BattleReady) = startWorldBattle(e)
        override fun onExplorationOver() = finishExploration()
    }
    findViewById<View>(R.id.worldOverlay)?.visibility = View.VISIBLE
    findViewById<View>(R.id.navTabs)?.visibility = View.GONE
    findViewById<View>(R.id.peaceControls)?.visibility = View.GONE
    view.start()
}

internal fun GameActivity.hideWorld() {
    findViewById<WorldView>(R.id.worldView)?.stop()
    findViewById<View>(R.id.worldOverlay)?.visibility = View.GONE
    findViewById<View>(R.id.navTabs)?.visibility = View.VISIBLE
    findViewById<View>(R.id.peaceControls)?.visibility = View.VISIBLE
}

/** Called from setUIState: battles hide the world, returning to HUB resumes the walk. */
internal fun GameActivity.syncWorldWithUIState(state: String) {
    if (!inWorld) return
    if (state == "BATTLE") {
        // setUIState already hid the tabs for the battle; just take the world off screen
        findViewById<WorldView>(R.id.worldView)?.stop()
        findViewById<View>(R.id.worldOverlay)?.visibility = View.GONE
        return
    }
    worldEncounter?.let { enc ->
        val won = (currentEnemy?.hp ?: 0) <= 0
        worldSession?.resolveEncounter(enc.beastId, if (won) EncounterOutcome.BEAST_DEFEATED else EncounterOutcome.PLAYER_FLED)
        worldEncounter = null
    }
    worldSession?.setCompanion(leadCompanion()) // the lead may have fallen, been caught, or swapped
    if (aetherDepleted) { finishExploration(); return }
    showWorld()
}

private fun GameActivity.startWorldBattle(e: WorldEvent.BattleReady) {
    worldEncounter = e
    val def = GameData.beasts.find { it.name == e.species } ?: GameData.beasts.random()
    val lead = if (party.isEmpty()) -1 else activePetIndex.coerceIn(0, party.size - 1)
    val refHp = if (lead == -1) 20 else party[lead].maxHp
    // Territory further from the start (higher evo stage) hits harder
    val pl = (refHp * (0.6 + 0.2 * e.stage) * Random.nextDouble(0.85, 1.15)).toInt().coerceAtLeast(10)
    val name = if (Random.nextInt(100) < 10) "[Obscure] ${def.name}" else def.name
    val wild = Netbeast(name, "Wild", pl, pl, def.m1, def.m2, "Tackle", 0L, 0, 0, 0, false, "None", 0, 0, 0, 0, "None", 0)
    printLog("> ${if (lead == -1) "You face" else "${party[lead].name} faces"} the wild ${wild.name} (HP: $pl)!")
    startWildBattle(lead, wild)
}

internal fun GameActivity.finishExploration() {
    if (!inWorld) return
    inWorld = false
    worldEncounter = null
    hideWorld()
    totalExpeds++
    val coins = Random.nextInt(5, 15)
    focusCoins += coins
    prefs.edit().putInt("TOTAL_EXPEDS", totalExpeds).apply()
    saveItems(); updateBagScreen(); updateDispatchButton()
    printLog("\n> 🏁 The walk is over. You head home with $coins Focus Coins.")
}
