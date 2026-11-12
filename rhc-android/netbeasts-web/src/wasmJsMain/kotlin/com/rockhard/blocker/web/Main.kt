package com.rockhard.blocker.web

import com.rockhard.blocker.GameData
import com.rockhard.blocker.world.render.Palette
import com.rockhard.blocker.world.render.TerrainRenderer
import com.rockhard.blocker.world.sim.EncounterOutcome
import com.rockhard.blocker.world.sim.LocalWorldSession
import com.rockhard.blocker.world.sim.Region
import com.rockhard.blocker.world.sim.World
import com.rockhard.blocker.world.sim.WorldEvent
import com.rockhard.blocker.world.sim.WorldMap
import com.rockhard.blocker.world.sim.WorldSession
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import kotlin.math.floor

/** Same as the phone (WorldBridge): a walk is three minutes. */
private const val EXPLORE_SECONDS = 180

/** Render widths, as on the phone (WorldView): the frame is this many pixels across, scaled up. */
private const val PORTRAIT_W = 200
private const val LANDSCAPE_W = 320

fun main() {
    val canvas = document.getElementById("view") as HTMLCanvasElement
    Wilds(canvas, document.getElementById("stats") as HTMLElement, document.getElementById("hint") as HTMLElement).start()
}

/**
 * The step-01 spike: today's Wilds, walking and steering, no fights (a beast that squares up
 * lets you go at once) and no HUD beyond the frame-time readout.
 */
class Wilds(private val canvas: HTMLCanvasElement, private val stats: HTMLElement, private val hint: HTMLElement) {
    // Everyone gets the same Wilds on the same day, as on the phone. The region is the generic
    // one until step 05 brings the player's.
    private val map = WorldMap.generate(todaysSeed(), GameData.species, Region.DEFAULT)
    private var session: WorldSession = newWalk()
    private val sprites = SheetSprites("sprites")
    private val controls = Controls(canvas) { session.steer(it) }
    private val palette = Palette.forWeather("", hourNow())
    private val sinkKind = sinkFromUrl()

    private var renderer: TerrainRenderer? = null
    private var sink: FrameSink? = null
    private var last = -1.0
    private val started = now()

    // frame-time readout, averaged over half a second
    private var frames = 0; private var simMs = 0.0; private var renderMs = 0.0; private var copyMs = 0.0; private var shownAt = 0.0

    private fun newWalk(): WorldSession = LocalWorldSession(World(map), "web-guest", null, EXPLORE_SECONDS)

    fun start() {
        hint.textContent = "Drag to steer and look (or A/D and W/S). Beasts shy away: walk at one to meet it."
        window.requestAnimationFrame(::frame)
    }

    private fun frame(t: Double) {
        val dt = if (last < 0) 0.0 else (t - last) / 1000
        last = t
        val r = sized()
        val me0 = session.world.entities[session.localPlayerId]
        if (me0 != null) makeATileAhead(me0.x, me0.y)

        controls.tick(dt)
        val t0 = now()
        val events = session.update(dt)
        val t1 = now()
        val me = session.world.entities[session.localPlayerId]
        if (me != null) r.render(session.world, me, controls.pitch.toInt(), palette, sprites, t.toLong())
        val t2 = now()
        sink?.put(r.fb)
        val t3 = now()

        for (e in events) when (e) {
            // no fights in the spike: the beast lets you go and you walk on
            is WorldEvent.Encounter -> if (e.playerId == session.localPlayerId) {
                session.resolveEncounter(e.beastId, EncounterOutcome.PLAYER_FLED)
                hint.textContent = "A ${e.species} squared up. Fights come in the next step."
            }
            is WorldEvent.ExplorationOver -> if (e.playerId == session.localPlayerId) {
                session = newWalk() // a new walk on the same map
                hint.textContent = "That walk's over. Off again."
            }
            else -> {}
        }
        if (t - started > 8000 && hint.textContent?.startsWith("Drag") == true) hint.textContent = ""

        frames++; simMs += t1 - t0; renderMs += t2 - t1; copyMs += t3 - t2
        if (t - shownAt >= 500) {
            val fps = frames * 1000 / (t - shownAt).coerceAtLeast(1.0)
            stats.textContent = "${fmt(fps)} fps · sim ${fmt(simMs / frames)} · render ${fmt(renderMs / frames)} · " +
                "copy ${fmt(copyMs / frames)} ms ($sinkKind) · ${r.w}×${r.h} · sprites ${sprites.loaded}" +
                (if (sprites.pending > 0) " (+${sprites.pending})" else "")
            frames = 0; simMs = 0.0; renderMs = 0.0; copyMs = 0.0; shownAt = t
        }
        window.requestAnimationFrame(::frame)
    }

    /** The renderer and canvas at the phone's sizes for the window's shape, remade when it changes. */
    private fun sized(): TerrainRenderer {
        val vw = canvas.clientWidth.coerceAtLeast(1); val vh = canvas.clientHeight.coerceAtLeast(1)
        val rw = if (vw > vh) LANDSCAPE_W else PORTRAIT_W
        val rh = (rw * vh / vw).coerceAtLeast(1)
        renderer?.let { if (it.w == rw && it.h == rh) return it }
        canvas.width = rw; canvas.height = rh
        controls.renderH = rh
        sink = if (sinkKind == "mem") MemorySink(canvas, rw, rh) else TypedArraySink(canvas, rw, rh)
        return TerrainRenderer(rw, rh, map).also { renderer = it }
    }

    /**
     * Makes at most one not-yet-made tile within half a tile of you each frame, so the walk finds
     * them ready (the phone does this on a background thread; the browser has one). If the sim or
     * the renderer gets there first, they make it themselves, and it's the same tile either way.
     */
    private fun makeATileAhead(x: Double, y: Double) {
        val reach = map.size / 2.0
        fun tileOf(v: Double) = floor(v / map.size).toInt()
        for (ty in tileOf(y - reach)..tileOf(y + reach)) for (tx in tileOf(x - reach)..tileOf(x + reach)) {
            if (map.tileIfMade(tx, ty) == null) { map.adopt(map.make(tx, ty)); return }
        }
    }

    private fun sinkFromUrl(): String {
        return if (urlParam("sink") == "typed") "typed" else "mem"
    }
}

private fun fmt(v: Double) = (floor(v * 10 + 0.5) / 10).toString()

private fun now(): Double = window.performance.now()

/** As on the phone (WorldBridge.todaysSeed): the local date as yyyyMMdd, times a big odd number. */
private fun todaysSeed(): Long = todayYmd().toLong() * 2654435761L

private fun todayYmd(): Int = js("(() => { const d = new Date(); return d.getFullYear() * 10000 + (d.getMonth() + 1) * 100 + d.getDate(); })()")

private fun hourNow(): Int = js("new Date().getHours()")

private fun urlParam(name: String): String? = js("new URLSearchParams(window.location.search).get(name)")
