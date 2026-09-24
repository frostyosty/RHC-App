package com.rockhard.blocker.world.render

import com.rockhard.blocker.world.sim.Entity
import com.rockhard.blocker.world.sim.EntityKind
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

// Pure Kotlin (no Android imports): renders into an IntArray so it can be
// tested on the JVM; WorldView copies the buffer into a Bitmap.

/** ARGB pixels, row-major. Alpha 0 = transparent. */
class Texture(val w: Int, val h: Int, val px: IntArray)

/** Supplies animated sprite frames by resource-style key, e.g. "spr_cacheon_walk_front". */
fun interface SpriteSource {
    fun frame(key: String, timeMs: Long): Texture?
}

/** Sky/fog colours and light level, picked from the real weather by [forWeather]. */
data class Palette(val skyTop: Int, val skyHorizon: Int, val hills: Int, val fog: Int, val fogDistance: Double, val light: Double) {
    companion object {
        val DAY = Palette(0xFF3F8FE0.toInt(), 0xFFBFE3FF.toInt(), 0xFF6C9FA0.toInt(), 0xFFBFE3FF.toInt(), 26.0, 1.0)
        val OVERCAST = Palette(0xFF6E7C8C.toInt(), 0xFFB8C2CC.toInt(), 0xFF6E8088.toInt(), 0xFFB8C2CC.toInt(), 18.0, 0.85)
        val RAIN = Palette(0xFF3A4452.toInt(), 0xFF7C8896.toInt(), 0xFF4E5E66.toInt(), 0xFF7C8896.toInt(), 13.0, 0.7)
        val SNOW = Palette(0xFF9AA8B8.toInt(), 0xFFE8EEF4.toInt(), 0xFFC8D4DE.toInt(), 0xFFE8EEF4.toInt(), 14.0, 0.95)
        val NIGHT = Palette(0xFF0A0E24.toInt(), 0xFF26305A.toInt(), 0xFF1A2440.toInt(), 0xFF141A30.toInt(), 11.0, 0.45)

        fun forWeather(weather: String, hour: Int): Palette {
            if (hour < 6 || hour >= 20) return NIGHT
            val w = weather.lowercase()
            return when {
                "snow" in w -> SNOW
                "rain" in w || "storm" in w || "drizzle" in w -> RAIN
                "cloud" in w || "fog" in w || "mist" in w || "overcast" in w -> OVERCAST
                else -> DAY
            }
        }
    }
}

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

    // Ground colour texture: 4 texels per world unit, pre-shaded by slope.
    private val tpu = 4
    private val texSize = map.size * tpu
    private val ground = IntArray(texSize * texSize) { i ->
        val tx = i % texSize; val ty = i / texSize
        val wx = (tx + 0.5) / tpu; val wy = (ty + 0.5) / tpu
        val n = hash(tx, ty) and 0xFF
        val base = when (map.terrainAt(wx, wy)) {
            Terrain.WATER -> 0 // animated at draw time
            Terrain.SAND -> if (n < 40) 0xFFD4B97A.toInt() else if (n > 220) 0xFFEAD6A2.toInt() else 0xFFE0C890.toInt()
            Terrain.TALL_GRASS -> if (n < 60) 0xFF3F7A2A.toInt() else if (n > 210) 0xFF7AA83A.toInt() else 0xFF4A8A30.toInt()
            else -> if (n < 20) 0xFF8FCB5A.toInt() else if (n < 70) 0xFF4E9A3A.toInt() else 0xFF5DAE44.toInt()
        }
        if (base == 0) 0 else {
            val slope = map.groundAt(wx - 0.5, wy - 0.5) - map.groundAt(wx + 0.5, wy + 0.5)
            shade(base, (1.0 + slope * 0.9).coerceIn(0.65, 1.3))
        }
    }

    fun render(world: World, cam: Entity, pitch: Int, palette: Palette, sprites: SpriteSource, timeMs: Long) {
        val horizon = h / 2 + pitch
        val dirX = cos(cam.angle); val dirY = sin(cam.angle)
        val rightX = -dirY; val rightY = dirX

        // Eye height follows the ground smoothly; lower when wading, with a walking bob
        val wading = map.terrainAt(cam.x, cam.y) == Terrain.WATER
        val target = map.surfaceAt(cam.x, cam.y) + if (wading) 0.42 else 0.6
        camH = if (camH.isNaN()) target else camH + (target - camH) * 0.15
        val eye = camH + if (cam.moving) sin(timeMs / 110.0) * 0.015 else 0.0

        depth.fill(Float.MAX_VALUE)
        drawSky(cam.angle, horizon, palette)
        drawTerrain(cam, dirX, dirY, rightX, rightY, eye, horizon, palette, timeMs)
        drawSprites(world, cam, dirX, dirY, rightX, rightY, eye, horizon, palette, sprites, timeMs)
    }

    private fun drawSky(angle: Double, horizon: Int, pal: Palette) {
        val span = (h * 0.6).coerceAtLeast(1.0)
        for (y in 0 until h) {
            val t = ((y - (horizon - span)) / span).coerceIn(0.0, 1.0)
            val c = lerp(pal.skyTop, pal.skyHorizon, t)
            val row = y * w
            for (x in 0 until w) fb[row + x] = c
        }
        // distant mountains that pan with the camera
        val c = lerp(pal.hills, pal.fog, 0.4)
        for (x in 0 until w) {
            val a = angle + atan((2.0 * x / w - 1) * half)
            val hill = (sin(a * 2) * 0.5 + sin(a * 5 + 1.3) * 0.3 + sin(a * 11 + 0.4) * 0.12 + 1.0) * h * 0.05
            for (y in (horizon - hill).toInt().coerceAtLeast(0) until horizon.coerceAtMost(h)) fb[y * w + x] = c
        }
    }

    private fun drawTerrain(cam: Entity, dirX: Double, dirY: Double, rightX: Double, rightY: Double, eye: Double, horizon: Int, pal: Palette, timeMs: Long) {
        yBuf.fill(h)
        var z = 0.2
        var dz = 0.02
        val shimmer = timeMs / 350
        while (z < maxZ) {
            // the line of ground points at distance z, spanning the view
            var px = cam.x + dirX * z - rightX * z * half
            var py = cam.y + dirY * z - rightY * z * half
            val sx = rightX * z * half * 2 / w; val sy = rightY * z * half * 2 / w
            val fog = (z / pal.fogDistance).coerceIn(0.0, 1.0)
            val scale = focal / z
            val d = z.toFloat()
            for (i in 0 until w) {
                val top = (horizon + (eye - map.surfaceAt(px, py)) * scale).toInt()
                if (top < yBuf[i]) {
                    val tx = floor(px * tpu).toInt().mod(texSize)
                    val ty = floor(py * tpu).toInt().mod(texSize)
                    var c = ground[ty * texSize + tx]
                    if (c == 0) c = water(tx, ty, WorldMap.WATER_LEVEL - map.groundAt(px, py), shimmer)
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

    private fun water(tx: Int, ty: Int, depth: Double, shimmer: Long): Int {
        val c = lerp(0xFF5AB4E0.toInt(), 0xFF2E6FA8.toInt(), (depth * 2.5).coerceIn(0.0, 1.0))
        return if (hash(tx / 2 + shimmer.toInt(), ty) and 31 == 0) 0xFFD8F0FF.toInt() else c
    }

    private fun drawSprites(world: World, cam: Entity, dirX: Double, dirY: Double, rightX: Double, rightY: Double, eye: Double, horizon: Int, pal: Palette, sprites: SpriteSource, timeMs: Long) {
        fun draw(x: Double, y: Double, lift: Double, size: Double, key: String, mirror: Boolean, phase: Long, fallback: String? = null, tree: Boolean = false) {
            val rx = map.delta(cam.x, x); val ry = map.delta(cam.y, y)
            val depthZ = rx * dirX + ry * dirY
            if (depthZ < 0.2 || depthZ > maxZ) return
            val lateral = rx * rightX + ry * rightY
            val sh = focal * size / depthZ
            val sx = w / 2 + focal * lateral / depthZ
            if (sx + sh / 2 < 0 || sx - sh / 2 >= w) return
            // trees: the hand-drawn level nearest the on-screen height, not a rescale
            val k = if (tree) "${key}_d${treeLevel(sh)}" else key
            val tex = sprites.frame(k, timeMs + phase)
                ?: fallback?.let { sprites.frame(it, timeMs + phase) } ?: return
            val bottom = horizon + (eye - map.surfaceAt(x, y) - lift) * focal / depthZ
            val top = bottom - sh
            val left = sx - sh / 2
            val x0 = left.toInt().coerceAtLeast(0); val x1 = (left + sh).toInt().coerceAtMost(w - 1)
            val y0 = top.toInt().coerceAtLeast(0); val y1 = bottom.toInt().coerceAtMost(h - 1)
            val fog = (depthZ / pal.fogDistance).coerceIn(0.0, 1.0)
            val d = depthZ.toFloat()
            for (px in x0..x1) {
                var u = ((px - left) / sh * tex.w).toInt().coerceIn(0, tex.w - 1)
                if (mirror) u = tex.w - 1 - u
                for (py in y0..y1) {
                    val k = py * w + px
                    if (d >= depth[k]) continue
                    val c = tex.px[((py - top) / sh * tex.h).toInt().coerceIn(0, tex.h - 1) * tex.w + u]
                    if (c ushr 24 == 0) continue
                    fb[k] = lerp(shade(c, pal.light), pal.fog, fog)
                    depth[k] = d
                }
            }
        }

        for (p in map.props) {
            if (p.kind.isTree) draw(p.x, p.y, 0.0, p.kind.size, p.kind.sprite, false, (p.x * 7919 + p.y * 104729).toLong(), "prop_tree", tree = true)
            else draw(p.x, p.y, 0.0, p.kind.size, p.kind.sprite, false, 0)
        }
        for (e in world.entities.values) {
            if (e.id == cam.id) continue
            val v = spriteFor(e, cam, rightX, rightY)
            val size = if (e.kind == EntityKind.CAGE) e.size else e.size * CREATURE_CANVAS
            draw(e.x, e.y, e.z, size, v.key, v.mirror, e.id * 137L, v.fallback)
        }
    }

    private class SpriteView(val key: String, val fallback: String?, val mirror: Boolean)

    /**
     * 8 directions from 5 drawn views: the angle between where the creature
     * faces and the direction to the camera picks front, fq (3/4 front),
     * side, bq (3/4 back) or back, in 45 degree steps. The non-symmetric
     * views face right and are mirrored when the creature heads left across
     * the screen. Rows without the extra views (see VIEWS in autogen's
     * designs.py) fall back to the old front/side pair.
     */
    private fun spriteFor(e: Entity, cam: Entity, rightX: Double, rightY: Double): SpriteView {
        if (e.kind == EntityKind.CAGE) return SpriteView("prop_cage", null, false)
        val base = "spr_" + e.species.lowercase().replace(" ", "_")
        val mirror = cos(e.angle) * rightX + sin(e.angle) * rightY < 0
        var diff = e.angle - atan2(map.delta(e.y, cam.y), map.delta(e.x, cam.x))
        while (diff > PI) diff -= 2 * PI
        while (diff < -PI) diff += 2 * PI
        val sector = (abs(diff) / (PI / 4)).roundToInt() // 0 front .. 4 back
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
    }
}
