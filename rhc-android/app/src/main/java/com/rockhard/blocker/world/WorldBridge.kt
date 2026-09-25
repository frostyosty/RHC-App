package com.rockhard.blocker

import android.graphics.Bitmap
import android.view.View
import android.widget.Toast
import com.rockhard.blocker.world.RegionProbe
import com.rockhard.blocker.world.SpriteBank
import com.rockhard.blocker.world.WorldPreviewView
import com.rockhard.blocker.world.WorldView
import com.rockhard.blocker.world.render.Palette
import com.rockhard.blocker.world.render.TerrainRenderer
import com.rockhard.blocker.world.sim.HouseStyle
import com.rockhard.blocker.world.sim.LocalWorldSession
import com.rockhard.blocker.world.sim.Species
import com.rockhard.blocker.world.sim.World
import com.rockhard.blocker.world.sim.WorldEvent
import com.rockhard.blocker.world.sim.WorldMap
import java.util.concurrent.Executors

// Glue between the 3D Wilds (world/) and the existing game: making today's
// walk ahead so its first frame can stand in for the Explore button, starting
// an exploration shaped like the player's real surroundings, handing
// encounters to the in-world fight (WorldFight.kt), coins, and finishing when
// the walk timer runs out.

/** 3D exploring is the default; Settings > "Explore Netbeasts in 3D" turns it off (text log instead). */
internal val GameActivity.world3dEnabled get() = prefs.getBoolean("WORLD_3D", true)

private const val EXPLORE_SECONDS = 180 // same length as a text-mode expedition
/** Coins for finishing a walk, on top of every coin picked up on the way. */
private const val WALK_PURSE = 5

/**
 * Today's walk, made before you set off. [key] says which day and region its world is for;
 * [look] which weather and hour its first frame was drawn in.
 */
internal class PreparedWalk(val key: String, val look: String, val session: LocalWorldSession)

/** Makes the next walk (map, world, first frame) off the main thread. */
private val walkMaker = Executors.newSingleThreadExecutor()

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

private val beastSpecies get() = GameData.beasts.map { Species(it.name, it.evoStage) }

/** Which day and region a walk belongs to: a prepared walk for another is stale. */
private fun GameActivity.walkKey() = "${todaysSeed()}|${RegionProbe.load(prefs).encode()}"

private fun hourNow() = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)

/**
 * The first frame instead of the Explore button: tapping it sets off, the same drag steers,
 * and it asks for a frame once it has a size.
 */
internal fun GameActivity.setupWorldPreview() {
    findViewById<WorldPreviewView>(R.id.worldPreview)?.listener = object : WorldPreviewView.Listener {
        override fun onSized(w: Int, h: Int) = prepareNextWalk()
        override fun onSetOff() = enterWorld()
        override fun onDrag(dx: Float) { if (inWorld) worldSession?.steer(dx * Math.PI * 0.85) }
    }
}

/**
 * Makes today's walk ahead of time, off the main thread: the map, the world with you in it,
 * and its first frame for the preview. Setting off then uses that very world, so the walk
 * starts on the frame you tapped. Does nothing if one is ready for today, this region, and
 * this weather and hour.
 */
internal fun GameActivity.prepareNextWalk() {
    val view = findViewById<WorldPreviewView>(R.id.worldPreview) ?: return
    if (!world3dEnabled || inWorld || preparingWalk || view.width <= 0 || view.height <= 0) return
    val key = walkKey(); val hour = hourNow(); val look = "$currentWeather|$hour"
    if (nextWalk?.key == key && nextWalk?.look == look) return
    // a new world for a new day or region; for new weather or a new hour just the frame is
    // out of date, but a fresh world is cheap and never races the one you might set off in
    if (nextWalk?.key != key) view.showFrame(null)
    preparingWalk = true
    val species = beastSpecies; val region = RegionProbe.load(prefs); val seed = todaysSeed()
    val owner = playerId; val lead = leadCompanion()
    val palette = Palette.forWeather(currentWeather, hour)
    val rw = WorldView.PORTRAIT_W; val rh = (rw * view.height / view.width).coerceAtLeast(1)
    val app = applicationContext
    walkMaker.execute {
        val made = try {
            val session = LocalWorldSession(World(WorldMap.generate(seed, species, region)), owner, lead, EXPLORE_SECONDS)
            val r = TerrainRenderer(rw, rh, session.world.map)
            val me = session.world.entities.getValue(session.localPlayerId)
            r.render(session.world, me, 0, palette, SpriteBank(app), 0L)
            PreparedWalk(key, look, session) to Bitmap.createBitmap(r.fb, rw, rh, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) { null }
        runOnUiThread {
            preparingWalk = false
            if (isDestroyed || made == null) return@runOnUiThread
            nextWalk = made.first
            findViewById<WorldPreviewView>(R.id.worldPreview)?.showFrame(made.second)
            prepareNextWalk() // the day, region or weather changed while it was being made
        }
    }
}

internal fun GameActivity.enterWorld() {
    if (aetherDepleted) { Toast.makeText(this, "Aether depleted! Return tomorrow.", Toast.LENGTH_SHORT).show(); return }
    if (inWorld) return
    // The land looks like where you are: the sea on its real side, local trees, local houses on the horizon
    val region = RegionProbe.load(prefs)
    // the walk made for the preview, so you set off on the frame you tapped (made now if it isn't ready)
    val ready = nextWalk?.takeIf { it.key == walkKey() }?.session
    nextWalk = null
    worldSession = ready?.also { it.setCompanion(leadCompanion()) }
        ?: LocalWorldSession(World(WorldMap.generate(todaysSeed(), beastSpecies, region)), playerId, leadCompanion(), EXPLORE_SECONDS)
    walkCoins = 0
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
    view.purse = object : WorldView.Purse {
        override fun coins() = focusCoins
        override fun nets() = nets
    }
    view.listener = object : WorldView.Listener {
        override fun onEncounter(e: WorldEvent.Encounter) = onWorldEncounter(e)
        override fun onCompanionOut(e: WorldEvent.CompanionOut) = onWorldCompanionOut(e)
        override fun onCoinPicked(e: WorldEvent.CoinPicked) = onWorldCoin()
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

/** You walked into a coin: it's yours straight away (the counter over the world ticks up). */
private fun GameActivity.onWorldCoin() {
    focusCoins++; walkCoins++
    AudioEngine.playSfx(this, "sfx_coin") // plays once a sfx_coin sound is added to res/raw
    vibratePhone(12)
    saveItems()
}

internal fun GameActivity.finishExploration() {
    if (!inWorld) return
    endWorldFight()
    inWorld = false
    hideWorld()
    totalExpeds++
    focusCoins += WALK_PURSE
    prefs.edit().putInt("TOTAL_EXPEDS", totalExpeds).apply()
    saveItems(); updateBagScreen(); updateDispatchButton()
    val found = if (walkCoins == 0) "no coins" else "$walkCoins coin${if (walkCoins == 1) "" else "s"}"
    printLog("\n> 🏁 The walk is over. You picked up $found on the way, and get $WALK_PURSE Focus Coins for the walk.")
}
