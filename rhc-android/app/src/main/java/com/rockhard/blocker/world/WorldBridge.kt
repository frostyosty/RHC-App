package com.rockhard.blocker

import android.view.View
import android.widget.Toast
import com.rockhard.blocker.world.RegionProbe
import com.rockhard.blocker.world.WorldView
import com.rockhard.blocker.world.render.Palette
import com.rockhard.blocker.world.sim.HouseStyle
import com.rockhard.blocker.world.sim.LocalWorldSession
import com.rockhard.blocker.world.sim.Species
import com.rockhard.blocker.world.sim.World
import com.rockhard.blocker.world.sim.WorldEvent
import com.rockhard.blocker.world.sim.WorldMap
import kotlin.random.Random

// Glue between the 3D Wilds (world/) and the existing game: starting an
// exploration shaped like the player's real surroundings, handing encounters
// to the in-world fight (WorldFight.kt), and finishing when the walk timer
// runs out.

/** 3D exploring is the default; Settings > "Explore Netbeasts in 3D" turns it off (text log instead). */
internal val GameActivity.world3dEnabled get() = prefs.getBoolean("WORLD_3D", true)

private const val EXPLORE_SECONDS = 180 // same length as a text-mode expedition

/** Everyone gets the same Wilds on the same day (a shared seed is what multiplayer will need). */
private fun todaysSeed(): Long {
    val day = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
    return day.toLong() * 2654435761L
}

/** A netbeast's species (trait tags stripped), which is also its sprite row. */
internal fun speciesOf(p: Netbeast) = p.name.replace(Regex("\\[.*?\\]"), "").trim()

/** The species in your lead cage, or null if you have no netbeasts. */
private fun GameActivity.leadCompanion(): String? =
    party.getOrNull(activePetIndex.coerceIn(0, (party.size - 1).coerceAtLeast(0)))?.let { speciesOf(it) }

internal fun GameActivity.enterWorld() {
    if (aetherDepleted) { Toast.makeText(this, "Aether depleted! Return tomorrow.", Toast.LENGTH_SHORT).show(); return }
    if (inWorld) return
    val species = GameData.beasts.map { Species(it.name, it.evoStage) }
    // The land looks like where you are: the sea on its real side, local trees, local houses on the horizon
    val region = RegionProbe.load(prefs)
    worldSession = LocalWorldSession(World(WorldMap.generate(todaysSeed(), species, region)), playerId, leadCompanion(), EXPLORE_SECONDS)
    inWorld = true
    val cages = if (party.isEmpty()) "You have no netbeasts. You'll have to fight yourself." else "${party.size} netbeast${if (party.size == 1) "" else "s"} rattle in their cages."
    val where = if (region.houses != HouseStyle.NONE) " on the edge of $currentCity" else ""
    printLog("\n> 🌲 You set off into the Wilds$where. There's no turning back until the walk is done. $cages")
    showWorld()
    updateDispatchButton()
}

internal fun GameActivity.showWorld() {
    val view = findViewById<WorldView>(R.id.worldView) ?: return
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    view.session = worldSession
    view.palette = Palette.forWeather(currentWeather, hour)
    view.fightHud = worldFightHud()
    view.listener = object : WorldView.Listener {
        override fun onEncounter(e: WorldEvent.Encounter) = onWorldEncounter(e)
        override fun onCompanionOut(e: WorldEvent.CompanionOut) = onWorldCompanionOut(e)
        override fun onExplorationOver() = finishExploration()
    }
    setupWorldFightControls()
    findViewById<View>(R.id.worldOverlay)?.visibility = View.VISIBLE
    findViewById<View>(R.id.navTabs)?.visibility = View.GONE
    findViewById<View>(R.id.peaceControls)?.visibility = View.GONE
    view.start()
}

internal fun GameActivity.hideWorld() {
    findViewById<WorldView>(R.id.worldView)?.stop()
    findViewById<View>(R.id.worldOverlay)?.visibility = View.GONE
    findViewById<View>(R.id.worldFightPanel)?.visibility = View.GONE
    findViewById<View>(R.id.navTabs)?.visibility = View.VISIBLE
    findViewById<View>(R.id.peaceControls)?.visibility = View.VISIBLE
}

/**
 * Called from setUIState. World fights never leave the world; when one ends
 * (endBattle returns to HUB) the encounter is resolved and the walk goes on.
 * Any other battle (an invasion) takes the world off screen until it's over.
 */
internal fun GameActivity.syncWorldWithUIState(state: String) {
    if (!inWorld) return
    if (state == "BATTLE") {
        // setUIState already hid the tabs for the battle; just take the world off screen
        findViewById<WorldView>(R.id.worldView)?.stop()
        findViewById<View>(R.id.worldOverlay)?.visibility = View.GONE
        return
    }
    endWorldFight()
    worldSession?.setCompanion(leadCompanion()) // the lead may have fallen, been caught, or swapped
    if (aetherDepleted) { finishExploration(); return }
    showWorld()
}

internal fun GameActivity.finishExploration() {
    if (!inWorld) return
    endWorldFight()
    inWorld = false
    hideWorld()
    totalExpeds++
    val coins = Random.nextInt(5, 15)
    focusCoins += coins
    prefs.edit().putInt("TOTAL_EXPEDS", totalExpeds).apply()
    saveItems(); updateBagScreen(); updateDispatchButton()
    printLog("\n> 🏁 The walk is over. You head home with $coins Focus Coins.")
}
