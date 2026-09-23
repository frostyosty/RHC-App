package com.rockhard.blocker.world.sim

import kotlin.math.floor
import kotlin.math.hypot
import kotlin.random.Random

// Pure Kotlin (no Android imports) so a future server can run the same sim.

object Terrain {
    const val GRASS = 0
    const val TALL_GRASS = 1
    const val SAND = 2
    const val WATER = 3
}

enum class PropKind(val sprite: String, val size: Double) {
    TREE("prop_tree", 1.7),
    PINE("prop_pine", 1.9),
    BUSH("prop_bush", 0.6),
    STONE("prop_stone", 0.45),
    TUFT("prop_tuft", 0.35),
}

/** Scenery billboard. Nothing collides: the player can never get stuck. */
data class Prop(val x: Double, val y: Double, val kind: PropKind)

/** A patrol area. Beasts of [species] (evo [stage]) wander inside it. */
data class Zone(val id: Int, val x: Double, val y: Double, val radius: Double, val species: String, val stage: Int)

/** A beast species the generator may place, e.g. from GameData.beasts. */
data class Species(val name: String, val stage: Int)

/**
 * Open, wrap-around terrain: a smooth heightmap (hills, lakes) with a terrain
 * type per cell, billboard props and patrol zones. The edges wrap (a torus),
 * so there are no walls anywhere. Generated deterministically from [seed],
 * so every client that knows the seed builds the identical map; only
 * entities need syncing.
 */
class WorldMap(
    val size: Int,
    val seed: Long,
    private val heights: FloatArray,
    val terrain: IntArray,
    val props: List<Prop>,
    val zones: List<Zone>,
) {
    companion object {
        const val WATER_LEVEL = 0.55

        fun generate(seed: Long, species: List<Species>, size: Int = 96): WorldMap {
            val rng = Random(seed)
            // Tileable value noise: a few octaves on lattices that divide the map size
            val heights = FloatArray(size * size)
            for ((cells, amp) in listOf(4 to 1.6, 8 to 0.8, 16 to 0.35, 32 to 0.12)) {
                val lat = DoubleArray(cells * cells) { rng.nextDouble() }
                for (y in 0 until size) for (x in 0 until size) {
                    val fx = x.toDouble() * cells / size; val fy = y.toDouble() * cells / size
                    heights[y * size + x] += (smoothLattice(lat, cells, fx, fy) * amp).toFloat()
                }
            }
            // Normalise so ~12% of the map sits under water (lakes) and hills top out near 2.6
            val sorted = heights.sortedArray()
            val low = sorted[(sorted.size * 0.12).toInt()]; val high = sorted.last()
            for (i in heights.indices) heights[i] = ((heights[i] - low) / (high - low) * 2.0 + WATER_LEVEL).toFloat()

            val lushLat = DoubleArray(12 * 12) { rng.nextDouble() }
            val terrain = IntArray(size * size) { i ->
                val h = heights[i]
                val lush = smoothLattice(lushLat, 12, (i % size) * 12.0 / size, (i / size) * 12.0 / size)
                when {
                    h < WATER_LEVEL -> Terrain.WATER
                    h < WATER_LEVEL + 0.12 -> Terrain.SAND
                    lush > 0.62 -> Terrain.TALL_GRASS
                    else -> Terrain.GRASS
                }
            }

            val cx = size / 2.0; val cy = size / 2.0
            fun t(x: Int, y: Int) = terrain[y * size + x]
            val props = mutableListOf<Prop>()
            repeat(size * size / 22) {
                val x = rng.nextInt(size); val y = rng.nextInt(size)
                if (hypot(x - cx, y - cy) < 4 || t(x, y) == Terrain.WATER || t(x, y) == Terrain.SAND) return@repeat
                val roll = rng.nextInt(100)
                val kind = when { roll < 35 -> PropKind.TREE; roll < 55 -> PropKind.PINE; roll < 80 -> PropKind.BUSH; else -> PropKind.STONE }
                props += Prop(x + rng.nextDouble(0.2, 0.8), y + rng.nextDouble(0.2, 0.8), kind)
            }
            repeat(size * size / 5) {
                val x = rng.nextInt(size); val y = rng.nextInt(size)
                if (t(x, y) == Terrain.TALL_GRASS || (t(x, y) == Terrain.GRASS && rng.nextInt(6) == 0)) {
                    props += Prop(x + rng.nextDouble(), y + rng.nextDouble(), PropKind.TUFT)
                }
            }

            val zones = mutableListOf<Zone>()
            var attempts = 0
            while (zones.size < 16 && attempts++ < 600 && species.isNotEmpty()) {
                val x = rng.nextInt(size); val y = rng.nextInt(size)
                if (t(x, y) == Terrain.WATER) continue
                val d = hypot(x - cx, y - cy)
                if (d < 10 || zones.any { hypot(it.x - x, it.y - y) < 12 }) continue
                val stage = when { d < size * 0.3 -> 1; d < size * 0.42 -> 2; else -> 3 }
                val pool = species.filter { it.stage == stage }.ifEmpty { species }
                val s = pool[rng.nextInt(pool.size)]
                zones += Zone(zones.size, x + 0.5, y + 0.5, 4.0 + stage, s.name, s.stage)
            }
            return WorldMap(size, seed, heights, terrain, props, zones)
        }

        private fun smoothLattice(lat: DoubleArray, cells: Int, fx: Double, fy: Double): Double {
            val x0 = floor(fx).toInt(); val y0 = floor(fy).toInt()
            val tx = fx - x0; val ty = fy - y0
            fun v(x: Int, y: Int) = lat[Math.floorMod(y, cells) * cells + Math.floorMod(x, cells)]
            val sx = tx * tx * (3 - 2 * tx); val sy = ty * ty * (3 - 2 * ty)
            val a = v(x0, y0) + (v(x0 + 1, y0) - v(x0, y0)) * sx
            val b = v(x0, y0 + 1) + (v(x0 + 1, y0 + 1) - v(x0, y0 + 1)) * sx
            return a + (b - a) * sy
        }
    }

    val spawnX = size / 2 + 0.5
    val spawnY = size / 2 + 0.5

    fun wrap(v: Double): Double { val s = size.toDouble(); return ((v % s) + s) % s }

    /** Shortest signed distance from a to b on the wrapped axis. */
    fun delta(a: Double, b: Double): Double {
        var d = (b - a) % size
        if (d > size / 2.0) d -= size
        if (d < -size / 2.0) d += size
        return d
    }

    fun distance(ax: Double, ay: Double, bx: Double, by: Double) = hypot(delta(ax, bx), delta(ay, by))

    fun terrainAt(x: Double, y: Double): Int = terrain[Math.floorMod(floor(y).toInt(), size) * size + Math.floorMod(floor(x).toInt(), size)]

    /** Smooth ground height (bilinear), under water included. */
    fun groundAt(x: Double, y: Double): Double {
        val x0 = floor(x).toInt(); val y0 = floor(y).toInt()
        val tx = x - x0; val ty = y - y0
        fun h(ix: Int, iy: Int) = heights[Math.floorMod(iy, size) * size + Math.floorMod(ix, size)].toDouble()
        val a = h(x0, y0) + (h(x0 + 1, y0) - h(x0, y0)) * tx
        val b = h(x0, y0 + 1) + (h(x0 + 1, y0 + 1) - h(x0, y0 + 1)) * tx
        return a + (b - a) * ty
    }

    /** What you stand on: the water surface in lakes (you wade, never drown). */
    fun surfaceAt(x: Double, y: Double) = maxOf(groundAt(x, y), WATER_LEVEL)
}
