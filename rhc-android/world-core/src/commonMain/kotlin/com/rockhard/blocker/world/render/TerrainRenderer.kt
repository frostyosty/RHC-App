package com.rockhard.blocker.world.render

import com.rockhard.blocker.world.sim.Action
import com.rockhard.blocker.world.sim.Entity
import com.rockhard.blocker.world.sim.EntityKind
import com.rockhard.blocker.world.sim.EntityState
import com.rockhard.blocker.world.sim.Flora
import com.rockhard.blocker.world.sim.HouseStyle
import com.rockhard.blocker.world.sim.PropKind
import com.rockhard.blocker.world.sim.Terrain
import com.rockhard.blocker.world.sim.World
import com.rockhard.blocker.world.sim.WorldMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

// Pure Kotlin (no Android imports): renders into an IntArray so it can be
// tested on the JVM; WorldView copies the buffer into a Bitmap.

/** ARGB pixels, row-major. Alpha 0 = transparent. */
class Texture(val w: Int, val h: Int, val px: IntArray)

/** Supplies animated sprite frames by resource-style key, e.g. "spr_cacheon_walk_front". */
fun interface SpriteSource {
    fun frame(key: String, timeMs: Long): Texture?

    /** How long one play of [key] lasts, or 0 if unknown (one-shot effects then play for 0.7s). */
    fun durationMs(key: String): Long = 0
}

/** Precipitation drawn over the view, from the real weather. */
object Precip {
    const val NONE = 0
    const val RAIN = 1
    const val SNOW = 2
    const val STORM = 3
}

/** Sky/fog colours, light level and precipitation, picked from the real weather by [forWeather]. */
data class Palette(
    val skyTop: Int, val skyHorizon: Int, val hills: Int, val fog: Int, val fogDistance: Double, val light: Double,
    val precip: Int = Precip.NONE, val night: Boolean = false,
) {
    companion object {
        val DAY = Palette(0xFF4A8CD0.toInt(), 0xFFC4DDF0.toInt(), 0xFF6A8E96.toInt(), 0xFFC4DDF0.toInt(), 26.0, 1.0)
        val OVERCAST = Palette(0xFF6E7C8C.toInt(), 0xFFB8C2CC.toInt(), 0xFF6E8088.toInt(), 0xFFB8C2CC.toInt(), 18.0, 0.85)
        val FOG = Palette(0xFF9AA4AC.toInt(), 0xFFC8CED2.toInt(), 0xFFA8B0B6.toInt(), 0xFFC8CED2.toInt(), 8.0, 0.85)
        val RAIN = Palette(0xFF3A4452.toInt(), 0xFF7C8896.toInt(), 0xFF4E5E66.toInt(), 0xFF7C8896.toInt(), 13.0, 0.7, Precip.RAIN)
        val STORM = Palette(0xFF262C38.toInt(), 0xFF5A6474.toInt(), 0xFF3A4450.toInt(), 0xFF5A6474.toInt(), 11.0, 0.55, Precip.STORM)
        val SNOW = Palette(0xFF9AA8B8.toInt(), 0xFFE8EEF4.toInt(), 0xFFC8D4DE.toInt(), 0xFFE8EEF4.toInt(), 14.0, 0.95, Precip.SNOW)
        val NIGHT = Palette(0xFF0A0E24.toInt(), 0xFF26305A.toInt(), 0xFF1A2440.toInt(), 0xFF141A30.toInt(), 11.0, 0.45, night = true)

        fun forWeather(weather: String, hour: Int): Palette {
            val w = weather.lowercase()
            val precip = when {
                "snow" in w -> Precip.SNOW
                "storm" in w || "thunder" in w -> Precip.STORM
                "rain" in w || "drizzle" in w || "shower" in w -> Precip.RAIN
                else -> Precip.NONE
            }
            // it rains at night too: the night sky with the day's weather falling through it
            if (hour < 6 || hour >= 20) return NIGHT.copy(precip = precip, fogDistance = if (precip != Precip.NONE) 9.0 else NIGHT.fogDistance)
            return when {
                precip == Precip.SNOW -> SNOW
                precip == Precip.STORM -> STORM
                precip == Precip.RAIN -> RAIN
                "fog" in w || "mist" in w -> FOG
                "cloud" in w || "overcast" in w -> OVERCAST
                else -> DAY
            }
        }
    }
}

/** Where an entity was drawn last frame, in render pixels, for HUD overlays (health bars). */
class OnScreen(val x: Float, val top: Float, val height: Float, val depth: Double)

/**
 * "Voxel space" terrain renderer (the Comanche technique): for each screen
 * column, march out over the heightmap front to back and draw each ground
 * sample only where nothing nearer has been drawn yet. Hills occlude
 * sprites through a per-pixel depth buffer. Looking up/down shifts the
 * horizon ([pitch], in pixels).
 */
class TerrainRenderer(val w: Int, val h: Int, private val map: WorldMap, fovDeg: Double = 70.0) {
    val fb = IntArray(w * h)
    private val depth = FloatArray(w * h)
    private val yBuf = IntArray(w)
    private val half = tan(fovDeg * PI / 360)
    private val focal = (w / 2) / half
    private val maxZ = 30.0
    private var camH = Double.NaN
    private var skyline: Skyline? = null

    /** Entities drawn last frame, by id. */
    val onScreen = HashMap<Int, OnScreen>()

    // The lean: when the walk sidesteps a trunk the view rolls a little toward
    // it. Worked out from how the camera moved, so the sim needs no extra
    // state. tilt[x] shifts column x's horizon.
    private val tilt = IntArray(w)
    private var roll = 0.0
    private var lastCamX = Double.NaN
    private var lastCamY = 0.0
    private var lastMs = 0L

    // Leaves flicking past when you walk under a canopy (render-only)
    private val leafX = FloatArray(LEAVES); private val leafY = FloatArray(LEAVES)
    private val leafVX = FloatArray(LEAVES); private val leafVY = FloatArray(LEAVES)
    private val leafC = IntArray(LEAVES); private val leafMs = LongArray(LEAVES)
    private val fxRng = Random(7)

    // Ground colour texture: 4 texels per world unit, pre-shaded by slope,
    // with a slow light/dark drift so big fields don't look tiled.
    private val tpu = 4
    private val texSize = map.size * tpu
    private val ground = IntArray(texSize * texSize) { i ->
        val tx = i % texSize; val ty = i / texSize
        val wx = (tx + 0.5) / tpu; val wy = (ty + 0.5) / tpu
        val n = hash(tx, ty) and 0xFF
        val base = when (map.terrainAt(wx, wy)) {
            Terrain.WATER -> 0 // animated at draw time
            Terrain.SAND -> if (n < 40) 0xFFBFA97C.toInt() else if (n > 220) 0xFFD9C89C.toInt() else 0xFFCDBB8E.toInt()
            Terrain.TALL_GRASS -> if (n < 60) 0xFF3F6428.toInt() else if (n > 225) 0xFF66793A.toInt() else if (n > 170) 0xFF557A32.toInt() else 0xFF4A6F2E.toInt()
            Terrain.FOREST -> if (n < 70) 0xFF3B3826.toInt() else if (n > 230) 0xFF6A5A34.toInt() else if (n > 180) 0xFF46592C.toInt() else 0xFF4A4530.toInt()
            // wet brown mud with the odd puddle catching the sky
            Terrain.MUD -> if (n < 60) 0xFF46382A.toInt() else if (n > 238) 0xFF7A8A92.toInt() else if (n > 200) 0xFF5E4C38.toInt() else 0xFF524230.toInt()
            // a packed dirt track with pebbles
            Terrain.PATH -> if (n < 50) 0xFF7A6448.toInt() else if (n > 232) 0xFFB09A78.toInt() else if (n > 190) 0xFF947C5C.toInt() else 0xFF8A7254.toInt()
            else -> if (n < 22) 0xFF6E9448.toInt() else if (n < 80) 0xFF4E7732.toInt() else 0xFF587F38.toInt()
        }
        if (base == 0) 0 else {
            val slope = map.groundAt(wx - 0.5, wy - 0.5) - map.groundAt(wx + 0.5, wy + 0.5)
            val drift = 0.93 + 0.14 * smoothHash(wx / 5.0, wy / 5.0)
            shade(base, ((1.0 + slope * 0.9) * drift).coerceIn(0.62, 1.3))
        }
    }

    fun render(world: World, cam: Entity, pitch: Int, palette: Palette, sprites: SpriteSource, timeMs: Long) {
        val sky = skyline ?: Skyline(map, sprites).also { skyline = it }
        onScreen.clear()
        // Your own moves: a punch pushes the view forward, a hit jolts it
        var shake = 0
        val (camX, camY) = when (cam.action) {
            Action.ATTACK -> {
                val k = sin(PI * (1 - cam.actionTicks.toDouble() / Action.ATTACK.ticks)) * 0.4
                map.wrap(cam.x + cos(cam.angle) * k) to map.wrap(cam.y + sin(cam.angle) * k)
            }
            Action.HIT -> { shake = if (cam.actionTicks % 4 < 2) 3 else -3; cam.x to cam.y }
            else -> cam.x to cam.y
        }
        val horizon = h / 2 + pitch + shake
        val dirX = cos(cam.angle); val dirY = sin(cam.angle)
        val rightX = -dirY; val rightY = dirX

        // Eye height follows the ground smoothly; lower when wading, with a walking bob
        val wading = map.terrainAt(camX, camY) == Terrain.WATER
        val target = map.surfaceAt(camX, camY) + if (wading) 0.42 else 0.6
        camH = if (camH.isNaN()) target else camH + (target - camH) * 0.15
        val eye = camH + if (cam.moving) sin(timeMs / 110.0) * 0.015 else 0.0

        lean(cam, rightX, rightY, timeMs)
        depth.fill(Float.MAX_VALUE)
        drawSky(cam.angle, horizon, palette, sky)
        drawTerrain(camX, camY, dirX, dirY, rightX, rightY, eye, horizon, palette, timeMs)
        drawSprites(world, cam, camX, camY, dirX, dirY, rightX, rightY, eye, horizon, palette, sprites, timeMs)
        drawLeaves(cam, palette, timeMs)
        drawWeather(palette, timeMs)
        if (cam.action == Action.HIT) for (i in fb.indices) fb[i] = lerp(fb[i], 0xFFB02020.toInt(), 0.22)
    }

    /** Rolls the view up to [LEAN] toward your sideways speed while walking (the sidestep), easing back. */
    private fun lean(cam: Entity, rightX: Double, rightY: Double, timeMs: Long) {
        val dt = timeMs - lastMs
        var target = 0.0
        if (cam.state == EntityState.WALKING && !lastCamX.isNaN() && dt in 1..250) {
            val side = (map.delta(lastCamX, cam.x) * rightX + map.delta(lastCamY, cam.y) * rightY) / (dt / 1000.0)
            target = (side / World.STRAFE_MAX).coerceIn(-1.0, 1.0) * LEAN
        }
        if (dt > 0) roll += (target - roll) * 0.12
        lastCamX = cam.x; lastCamY = cam.y; lastMs = timeMs
        // leaning right, the right side of the horizon rises
        val k = tan(roll)
        for (x in 0 until w) tilt[x] = (-(x - w / 2) * k).roundToInt()
    }

    private fun drawSky(angle: Double, horizon: Int, pal: Palette, sky: Skyline) {
        val span = (h * 0.6).coerceAtLeast(1.0)
        for (y in 0 until h) {
            val t = ((y - (horizon - span)) / span).coerceIn(0.0, 1.0)
            val c = lerp(pal.skyTop, pal.skyHorizon, t)
            val row = y * w
            for (x in 0 until w) fb[row + x] = c
        }
        // distant hills and the row of houses, one panorama that pans with the camera
        val sv = focal * 2 * PI / Skyline.PW // screen pixels per panorama pixel
        val hillC = lerp(pal.hills, pal.fog, 0.4)
        val haze = (0.35 + (1 - pal.fogDistance / 26.0) * 0.5).coerceIn(0.35, 0.8)
        for (x in 0 until w) {
            val hz = horizon + tilt[x] // leaning tilts the horizon
            val a = angle + atan((2.0 * x / w - 1) * half)
            val u = (((a / (2 * PI)) * Skyline.PW).roundToInt()).mod(Skyline.PW)
            val hill = sky.hills[u] * sv
            for (y in (hz - hill).toInt().coerceAtLeast(0) until hz.coerceAtMost(h)) fb[y * w + x] = hillC
            // the sea's flat horizon
            if (sky.sea[u] && hz in 1 until h) fb[(hz - 1) * w + x] = lerp(0xFF3C6C8C.toInt(), pal.fog, 0.55)
            // the strip's bottom row sits on the horizon, so near land always overlaps it
            val top = (hz - Skyline.PH * sv + 1).toInt().coerceAtLeast(0)
            for (y in top..hz.coerceAtMost(h - 1)) {
                val v = Skyline.PH - 1 - floor((hz - y) / sv).toInt()
                if (v !in 0 until Skyline.PH) continue
                val c = sky.px[v * Skyline.PW + u]
                if (c ushr 24 == 0) continue
                fb[y * w + x] = if (c == Skyline.WINDOW) {
                    // lit windows at night (not every house), dark glass by day
                    if (pal.night && hash(u / 6, 7) and 3 != 0) 0xFFFFD27A.toInt() else lerp(0xFF2A3440.toInt(), pal.fog, haze)
                } else lerp(shade(c, pal.light), pal.fog, haze)
            }
        }
    }

    private fun drawTerrain(camX: Double, camY: Double, dirX: Double, dirY: Double, rightX: Double, rightY: Double, eye: Double, horizon: Int, pal: Palette, timeMs: Long) {
        yBuf.fill(h)
        var z = 0.2
        var dz = 0.02
        val shimmer = timeMs / 350
        val rain = if (pal.precip == Precip.RAIN || pal.precip == Precip.STORM) timeMs / 90 else -1L
        while (z < maxZ) {
            // the line of ground points at distance z, spanning the view
            var px = camX + dirX * z - rightX * z * half
            var py = camY + dirY * z - rightY * z * half
            val sx = rightX * z * half * 2 / w; val sy = rightY * z * half * 2 / w
            val fog = (z / pal.fogDistance).coerceIn(0.0, 1.0)
            val scale = focal / z
            val d = z.toFloat()
            for (i in 0 until w) {
                val top = (horizon + tilt[i] + (eye - map.surfaceAt(px, py)) * scale).toInt()
                if (top < yBuf[i]) {
                    val tx = floor(px * tpu).toInt().mod(texSize)
                    val ty = floor(py * tpu).toInt().mod(texSize)
                    var c = ground[ty * texSize + tx]
                    if (c == 0) c = water(tx, ty, WorldMap.WATER_LEVEL - map.groundAt(px, py), shimmer, rain)
                    c = lerp(shade(c, pal.light), pal.fog, fog)
                    val from = top.coerceAtLeast(0)
                    for (y in from until yBuf[i]) { val k = y * w + i; fb[k] = c; depth[k] = d }
                    yBuf[i] = from
                }
                px += sx; py += sy
            }
            z += dz
            dz *= 1.025
        }
    }

    private fun water(tx: Int, ty: Int, depth: Double, shimmer: Long, rain: Long): Int {
        val c = lerp(0xFF4E8FB0.toInt(), 0xFF22507A.toInt(), (depth * 2.0).coerceIn(0.0, 1.0))
        // raindrops ring the surface
        if (rain >= 0 && hash(tx + rain.toInt() * 7, ty * 3 + (rain / 3).toInt()) and 47 == 0) return 0xFFB8D4E6.toInt()
        return if (hash(tx / 2 + shimmer.toInt(), ty) and 31 == 0) 0xFFCFE4F2.toInt() else c
    }

    private fun drawSprites(
        world: World, cam: Entity, camX: Double, camY: Double, dirX: Double, dirY: Double, rightX: Double, rightY: Double,
        eye: Double, horizon: Int, pal: Palette, sprites: SpriteSource, timeMs: Long,
    ) {
        /** Returns the on-screen box (x centre, top, height, depth) or null if nothing was drawn. */
        // In a fight, scenery inside your circle ghosts out so nothing hides the beasts
        val fight = cam.state == EntityState.ENGAGED && cam.orbitR > 0
        fun draw(x: Double, y: Double, lift: Double, size: Double, key: String, mirror: Boolean, t: Long, fallback: String? = null,
                 tree: Boolean = false, nearer: Double = 0.0, prop: Boolean = false): OnScreen? {
            val rx = map.delta(camX, x); val ry = map.delta(camY, y)
            val depthZ = rx * dirX + ry * dirY
            if (depthZ < 0.2 || depthZ > maxZ) return null
            val lateral = rx * rightX + ry * rightY
            val sh = focal * size / depthZ
            val sx = w / 2 + focal * lateral / depthZ
            if (sx + sh / 2 < 0 || sx - sh / 2 >= w) return null
            // trees: the hand-drawn level nearest the on-screen height, not a rescale
            val k = if (tree) "${key}_d${treeLevel(sh)}" else key
            val tex = sprites.frame(k, t) ?: fallback?.let { sprites.frame(it, t) } ?: return null
            val bottom = horizon + (eye - map.surfaceAt(x, y) - lift) * focal / depthZ
            val top = bottom - sh
            val left = sx - sh / 2
            val x0 = left.toInt().coerceAtLeast(0); val x1 = (left + sh).toInt().coerceAtMost(w - 1)
            val fog = (depthZ / pal.fogDistance).coerceIn(0.0, 1.0)
            val d = (depthZ - nearer).toFloat()
            // scenery you're brushing past dithers away instead of filling the view with giant pixels
            var keep = if (prop && depthZ < NEAR_FADE) ((depthZ - 0.25) / (NEAR_FADE - 0.25) * 16).toInt() else 16
            if (prop && fight && size > 0.5 && map.distance(x, y, cam.orbitX, cam.orbitY) < cam.orbitR + 0.5) keep = minOf(keep, 4)
            for (px in x0..x1) {
                var u = ((px - left) / sh * tex.w).toInt().coerceIn(0, tex.w - 1)
                if (mirror) u = tex.w - 1 - u
                val colTop = top + tilt[px]
                for (py in colTop.toInt().coerceAtLeast(0)..(bottom + tilt[px]).toInt().coerceAtMost(h - 1)) {
                    if (keep < 16 && BAYER4[(py and 3) * 4 + (px and 3)] >= keep) continue
                    val k2 = py * w + px
                    if (d >= depth[k2]) continue
                    val c = tex.px[((py - colTop) / sh * tex.h).toInt().coerceIn(0, tex.h - 1) * tex.w + u]
                    if (c ushr 24 == 0) continue
                    fb[k2] = lerp(shade(c, pal.light), pal.fog, fog)
                    depth[k2] = d
                }
            }
            return OnScreen(sx.toFloat(), top.toFloat(), sh.toFloat(), depthZ)
        }

        for (p in map.props) {
            if (p.kind.isTree) draw(p.x, p.y, 0.0, p.kind.size, p.kind.sprite, false, timeMs + (p.x * 7919 + p.y * 104729).toLong(), "prop_tree", tree = true, prop = true)
            else draw(p.x, p.y, 0.0, p.kind.size, p.kind.sprite, false, timeMs, prop = true)
        }
        for (e in world.entities.values) {
            if (e.id == cam.id) continue
            if (e.state == EntityState.GONE && !(e.action == Action.FAINT && e.actionTicks > 0)) continue
            val v = spriteFor(e, cam, rightX, rightY)
            val size = if (e.kind == EntityKind.CAGE) e.size else e.size * CREATURE_CANVAS
            // attack lunges toward the foe, a hit knocks back from it
            var ex = e.x; var ey = e.y
            val foe = world.entities[e.foe]
            if (foe != null && (e.action == Action.ATTACK || e.action == Action.HIT)) {
                val p = 1 - e.actionTicks.toDouble() / e.action.ticks
                val k = if (e.action == Action.ATTACK) sin(PI * p) * 0.45 else -sin(PI * p) * 0.18
                val a = atan2(map.delta(e.y, foe.y), map.delta(e.x, foe.x))
                ex = map.wrap(ex + cos(a) * k); ey = map.wrap(ey + sin(a) * k)
            }
            val t = if (e.action != Action.NONE) actionTime(e, v.key, sprites) else timeMs + e.id * 137L
            draw(ex, ey, e.z, size, v.key, v.mirror, t, v.fallback)?.let { onScreen[e.id] = it }
            // an effect over it, facing away from whoever it came from
            val fx = e.fx ?: continue
            val key = "fx_$fx"
            val ms = e.fxAge * 1000L / World.TICK_HZ
            val dur = sprites.durationMs(key).takeIf { it > 0 } ?: 700
            if (ms >= dur) continue
            val from = world.entities[e.foe]
            val fromLeft = from == null || map.delta(camX, from.x) * rightX + map.delta(camY, from.y) * rightY <
                map.delta(camX, ex) * rightX + map.delta(camY, ey) * rightY
            draw(ex, ey, e.z, e.size * CREATURE_CANVAS * 1.1, key, !fromLeft, ms, nearer = 0.05)
        }
    }

    /**
     * Walking under a canopy knocks a few leaves loose: they flick past the
     * view, drifting down and away. Render-only; the colours follow the tree.
     */
    private fun drawLeaves(cam: Entity, pal: Palette, timeMs: Long) {
        if (cam.state == EntityState.WALKING && fxRng.nextInt(3) == 0) {
            val cx = floor(cam.x).toInt(); val cy = floor(cam.y).toInt()
            var under: PropKind? = null
            for (dy in -1..1) for (dx in -1..1) {
                val t = map.tile(cx + dx, cy + dy)
                for (k in map.treeStart[t] until map.treeStart[t + 1]) {
                    val tree = map.props[map.treeIds[k]]
                    if (map.distance(cam.x, cam.y, tree.x, tree.y) < tree.kind.size * 0.28) under = tree.kind
                }
            }
            val c = under?.let { leafColour(it) } ?: 0
            val slot = leafMs.indices.firstOrNull { timeMs - leafMs[it] > LEAF_MS || leafMs[it] > timeMs }
            if (c != 0 && slot != null) {
                leafX[slot] = fxRng.nextFloat() * w; leafY[slot] = fxRng.nextFloat() * h * 0.4f
                leafVX[slot] = (fxRng.nextFloat() - 0.5f) * w * 0.0012f; leafVY[slot] = h * (0.00035f + fxRng.nextFloat() * 0.0004f)
                leafC[slot] = c; leafMs[slot] = timeMs
            }
        }
        for (i in 0 until LEAVES) {
            val age = timeMs - leafMs[i]
            if (leafC[i] == 0 || age !in 0 until LEAF_MS) continue
            val x = (leafX[i] + leafVX[i] * age).toInt(); val y = (leafY[i] + leafVY[i] * age).toInt()
            val c = shade(leafC[i], pal.light)
            // a leaf tumbles: wide, then thin
            val wide = (age / 120) % 2 == 0L
            for (dx in 0..(if (wide) 2 else 1)) for (dy in 0..(if (wide) 0 else 1)) {
                val xx = x + dx; val yy = y + dy
                if (xx in 0 until w && yy in 0 until h) fb[yy * w + xx] = c
            }
        }
    }

    private fun leafColour(k: PropKind): Int = when (k) {
        PropKind.MAPLE -> 0xFFC0662A.toInt()
        PropKind.POHUTUKAWA -> if (fxRng.nextInt(4) == 0) 0xFFC0303A.toInt() else 0xFF3F5E36.toInt()
        PropKind.BIRCH -> 0xFF8A9A44.toInt()
        PropKind.PINE, PropKind.CYPRESS -> 0xFF2F5A3E.toInt()
        PropKind.PALM, PropKind.NIKAU, PropKind.PONGA, PropKind.CABBAGE -> 0xFF5A7A42.toInt()
        PropKind.WIRETREE -> 0xFF3FE0FF.toInt() // glitch sparks, not leaves
        PropKind.SNAG -> 0                      // dead: nothing to fall
        else -> 0xFF4E7A36.toInt()
    }

    /** One-shot pose GIFs play from their start; a faint holds its last frame. */
    private fun actionTime(e: Entity, key: String, sprites: SpriteSource): Long {
        val dur = sprites.durationMs(key)
        return if (e.action == Action.FAINT) (if (dur > 0) dur - 1 else 1950)
        else ((e.action.ticks - e.actionTicks) * 1000L / World.TICK_HZ).let { if (dur > 0) it.coerceAtMost(dur - 1) else it }
    }

    /** Rain streaks, snowflakes and the odd lightning flash, over everything. */
    private fun drawWeather(pal: Palette, timeMs: Long) {
        when (pal.precip) {
            Precip.RAIN, Precip.STORM -> {
                val n = w * h / (if (pal.precip == Precip.STORM) 130 else 220)
                val streak = lerp(pal.fog, 0xFFFFFFFF.toInt(), 0.3)
                for (i in 0 until n) {
                    val speed = 0.55 + (hash(i, 3) and 63) / 160.0 // px per ms
                    val y0 = ((hash(i, 2) and 0xFFFF) + (timeMs * speed).toLong()).mod(h + 24) - 12
                    val x0 = (hash(i, 1) and 0xFFFF) % w
                    for (k in 0 until 5) {
                        val y = y0 + k; val x = x0 - (y0 + k) / 5 // slanted
                        if (y in 0 until h) { val xx = x.mod(w); fb[y * w + xx] = lerp(fb[y * w + xx], streak, 0.55) }
                    }
                }
                if (pal.precip == Precip.STORM) {
                    // a flash roughly every 9s, two quick flickers
                    val slot = timeMs / 9000; val inSlot = timeMs % 9000
                    val at = (hash(slot.toInt(), 11) and 0xFFF) + 2000
                    if (inSlot in at until at + 90 || inSlot in at + 180 until at + 240) {
                        for (i in fb.indices) fb[i] = lerp(fb[i], 0xFFE8F0FF.toInt(), 0.45)
                    }
                }
            }
            Precip.SNOW -> {
                val n = w * h / 260
                for (i in 0 until n) {
                    val y = ((hash(i, 2) and 0xFFFF) + timeMs * (0.02 + (hash(i, 5) and 15) / 900.0)).toLong().mod(h)
                    val x = ((hash(i, 1) and 0xFFFF) + sin(timeMs / 700.0 + i) * 4).toInt().mod(w)
                    fb[y * w + x] = 0xFFF4F8FC.toInt()
                    if (i % 3 == 0 && x + 1 < w) fb[y * w + x + 1] = 0xFFF4F8FC.toInt()
                }
            }
        }
    }

    private class SpriteView(val key: String, val fallback: String?, val mirror: Boolean)

    /**
     * 8 directions from 5 drawn views: the angle between where the creature
     * faces and the direction to the camera picks front, fq (3/4 front),
     * side, bq (3/4 back) or back, in 45 degree steps. The non-symmetric
     * views face right and are mirrored when the creature heads left across
     * the screen. Rows without the extra views (see VIEWS in autogen's
     * designs.py) fall back to the old front/side pair. Battle poses
     * (attack, hit, faint, victory) are side views.
     */
    private fun spriteFor(e: Entity, cam: Entity, rightX: Double, rightY: Double): SpriteView {
        if (e.kind == EntityKind.CAGE) return SpriteView("prop_cage", null, false)
        val base = "spr_" + e.species.lowercase().replace(" ", "_")
        val mirror = cos(e.angle) * rightX + sin(e.angle) * rightY < 0
        var diff = e.angle - atan2(map.delta(e.y, cam.y), map.delta(e.x, cam.x))
        while (diff > PI) diff -= 2 * PI
        while (diff < -PI) diff += 2 * PI
        val sector = (abs(diff) / (PI / 4)).roundToInt() // 0 front .. 4 back
        if (e.action != Action.NONE) {
            if (e.action == Action.ATTACK && sector <= 1) return SpriteView("${base}_attack_front", "${base}_attack", mirror)
            return SpriteView("${base}_${e.action.anim}", "${base}_idle", mirror)
        }
        return if (e.moving) {
            val key = when (sector) { 0 -> "walk_front"; 1 -> "walk_fq"; 2 -> "explore"; 3 -> "walk_bq"; else -> "walk_back" }
            SpriteView("${base}_$key", if (sector <= 1) "${base}_walk_front" else "${base}_explore", mirror)
        } else {
            val key = when (sector) { 0 -> "idle_front"; 1 -> "idle_fq"; 2 -> "idle"; 3 -> "idle_bq"; else -> "idle_back" }
            SpriteView("${base}_$key", "${base}_idle", mirror)
        }
    }

    companion object {
        /**
         * Creature GIFs have empty room around the art for battle lunges
         * (CANVAS in sprite_studio/autogen/autogen.py): the frame is 42/32
         * of the creature, which stands on its bottom edge, so it's drawn
         * that much larger to keep the creature itself at e.size.
         */
        const val CREATURE_CANVAS = 42.0 / 32.0

        /**
         * Native heights of the tree distance levels, d0 nearest (D in
         * sprite_studio/autogen/scenery.py): change both or neither.
         */
        val TREE_LOD = intArrayOf(128, 96, 72, 56, 44, 32, 24, 16, 12, 8)

        /** How far the view rolls toward a full-speed sidestep (radians, about 2 degrees). */
        const val LEAN = 0.035

        private const val LEAVES = 16
        private const val LEAF_MS = 900L

        /** Scenery nearer than this (tiles) dithers out as you pass it. */
        const val NEAR_FADE = 0.9

        private val BAYER4 = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

        /** The smallest level at or above 0.9x the on-screen height [sh], clamped to d0/d9. */
        fun treeLevel(sh: Double): Int {
            val want = sh * 0.9
            for (i in TREE_LOD.indices.reversed()) if (TREE_LOD[i] >= want) return i
            return 0
        }

        fun lerp(a: Int, b: Int, t: Double): Int {
            if (t <= 0) return a or (0xFF shl 24)
            val ar = a shr 16 and 0xFF; val ag = a shr 8 and 0xFF; val ab = a and 0xFF
            val br = b shr 16 and 0xFF; val bg = b shr 8 and 0xFF; val bb = b and 0xFF
            return (0xFF shl 24) or ((ar + (br - ar) * t).toInt() shl 16) or ((ag + (bg - ag) * t).toInt() shl 8) or (ab + (bb - ab) * t).toInt()
        }

        fun shade(c: Int, k: Double): Int {
            if (k == 1.0) return c
            val r = ((c shr 16 and 0xFF) * k).toInt().coerceAtMost(255)
            val g = ((c shr 8 and 0xFF) * k).toInt().coerceAtMost(255)
            val b = ((c and 0xFF) * k).toInt().coerceAtMost(255)
            return (c and (0xFF shl 24)) or (r shl 16) or (g shl 8) or b
        }

        fun hash(x: Int, y: Int): Int {
            var n = x * 374761393 + y * 668265263
            n = (n xor (n ushr 13)) * 1274126177
            return n xor (n ushr 16)
        }

        /** Smooth 0..1 value noise from [hash], for slow colour drift. */
        private fun smoothHash(x: Double, y: Double): Double {
            val x0 = floor(x).toInt(); val y0 = floor(y).toInt()
            val tx = x - x0; val ty = y - y0
            fun v(ix: Int, iy: Int) = (hash(ix, iy) and 0xFF) / 255.0
            val sx = tx * tx * (3 - 2 * tx); val sy = ty * ty * (3 - 2 * ty)
            val a = v(x0, y0) + (v(x0 + 1, y0) - v(x0, y0)) * sx
            val b = v(x0, y0 + 1) + (v(x0 + 1, y0 + 1) - v(x0, y0 + 1)) * sx
            return a + (b - a) * sy
        }
    }
}

/**
 * The far horizon as one 360 degree strip, built once per map: rolling
 * hills, a flat sea horizon toward the real sea, and (when the region has a
 * house style) towns of prop_house_<style>_<n> sprites with the region's
 * trees between them, drawn small (the d7/d8 tree levels). Column u faces
 * world angle u / PW * 2pi; world angle 0 is east and north is -pi/2, so the
 * sea lands on the real compass bearing.
 */
private class Skyline(map: WorldMap, sprites: SpriteSource) {
    companion object {
        const val PW = 1024
        const val PH = 40
        /** Window pixels in the house sprites (scenery.py WINDOW): lit at night, dark glass by day. */
        const val WINDOW = 0xFFFFE8A0.toInt()
        const val HOUSE_VARIANTS = 8
    }

    val px = IntArray(PW * PH)
    val hills = DoubleArray(PW)
    val sea = BooleanArray(PW)

    init {
        val rng = Random(map.seed xor 0x5C1L)
        val region = map.region
        val lift = 0.6 + 0.25 * region.relief
        // sea sector: 100 degrees centred on the sea's bearing
        val seaCol = if (region.coastal) ((region.seaOctant * 45.0 - 90).mod(360.0) / 360 * PW).toInt() else -1
        fun seaDist(u: Int) = if (seaCol < 0) PW else minOf((u - seaCol).mod(PW), (seaCol - u).mod(PW))
        for (u in 0 until PW) {
            val a = u * 2 * PI / PW
            val base = (sin(a * 2) * 0.5 + sin(a * 5 + 1.3) * 0.3 + sin(a * 11 + 0.4) * 0.12 + 1.0) * 5.5 * lift
            // hills fall away to the shore on either side of the sea
            val shore = ((seaDist(u) - PW * 0.12) / (PW * 0.06)).coerceIn(0.0, 1.0)
            hills[u] = base * shore
            sea[u] = seaDist(u) < PW * 0.13
        }
        if (region.houses != HouseStyle.NONE) {
            val trees = when (region.flora) {
                Flora.NZ -> listOf("cabbage", "pohutukawa", "pine", "nikau", "cypress")
                Flora.TROPICAL -> listOf("palm", "palm", "oak")
                Flora.BOREAL -> listOf("pine", "birch", "pine")
                Flora.TEMPERATE -> listOf("oak", "birch", "pine", "cypress")
            }
            // towns: on the coast they hug the shore either side of the sea, inland they're scattered
            val towns = if (seaCol >= 0) listOf(seaCol + (PW * 0.14).toInt() to (PW * 0.22).toInt(), seaCol - (PW * 0.36).toInt() to (PW * 0.22).toInt(), seaCol + PW / 2 to (PW * 0.08).toInt())
            else List(3) { rng.nextInt(PW) to (PW * (0.08 + rng.nextDouble() * 0.12)).toInt() }
            for ((start, len) in towns) {
                var u = start
                while (u < start + len) {
                    val tree = rng.nextInt(100) < 22
                    val key = if (tree) "prop_tree_${trees[rng.nextInt(trees.size)]}_d${7 + rng.nextInt(2)}"
                    else "prop_house_${region.houses.key}_${rng.nextInt(HOUSE_VARIANTS)}"
                    val tex = sprites.frame(key, 0)
                    if (tex == null) { u += 6; continue }
                    stamp(tex, u)
                    u += (if (tree) tex.w / 2 else tex.w) + rng.nextInt(-1, 4)
                }
            }
        }
    }

    /** Draws [tex] with its left edge at column [u] and its bottom on the strip's bottom row, trimmed to its opaque width. */
    private fun stamp(tex: Texture, u: Int) {
        val oy = PH - tex.h
        for (y in 0 until tex.h) for (x in 0 until tex.w) {
            val c = tex.px[y * tex.w + x]
            if (c ushr 24 == 0 || y + oy < 0) continue
            px[(y + oy) * PW + (u + x).mod(PW)] = c
        }
    }
}
