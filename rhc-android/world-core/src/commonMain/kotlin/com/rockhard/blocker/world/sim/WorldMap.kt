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

/**
 * [size] is the billboard's world width and height. Trees ([radius] > 0, the
 * trunk radius in tiles) come in 10 distance levels, prop_tree_<kind>_d<0-9>,
 * drawn by sprite_studio/autogen/scenery.py, whose heights and radii match
 * these: change both or neither.
 */
enum class PropKind(val sprite: String, val size: Double, val radius: Double = 0.0) {
    OAK("prop_tree_oak", 2.8, 0.16),
    PINE("prop_tree_pine", 3.2, 0.12),
    BIRCH("prop_tree_birch", 2.6, 0.08),
    WILLOW("prop_tree_willow", 3.0, 0.18),
    PALM("prop_tree_palm", 3.0, 0.10),
    CYPRESS("prop_tree_cypress", 3.2, 0.09),
    MAPLE("prop_tree_maple", 2.8, 0.15),
    SNAG("prop_tree_snag", 2.6, 0.10),
    MUSHROOM("prop_tree_mushroom", 2.4, 0.12),
    WIRETREE("prop_tree_wiretree", 2.8, 0.12),
    BUSH("prop_bush", 0.6),
    STONE("prop_stone", 0.45),
    TUFT("prop_tuft", 0.35);

    val isTree get() = radius > 0
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
            props += placeTrees(rng, size, heights, terrain)
            repeat(size * size / 50) {
                val x = rng.nextInt(size); val y = rng.nextInt(size)
                if (hypot(x - cx, y - cy) < 4 || t(x, y) == Terrain.WATER || t(x, y) == Terrain.SAND) return@repeat
                val kind = if (rng.nextInt(100) < 55) PropKind.BUSH else PropKind.STONE
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

        /** Trunks never closer than this, so there's always a way through a grove. */
        const val MIN_TRUNK_GAP = 0.9

        /**
         * Trees grow in groves of one kind, picked by the terrain at the
         * grove's centre, plus a few loners. Each tree must also suit its own
         * spot, and grid rejection keeps trunks [MIN_TRUNK_GAP] apart.
         */
        private fun placeTrees(rng: Random, size: Int, heights: FloatArray, terrain: IntArray): List<Prop> {
            val cx = size / 2.0; val cy = size / 2.0
            fun t(x: Int, y: Int) = terrain[y.mod(size) * size + x.mod(size)]
            fun nearWater(x: Int, y: Int) = (-2..2).any { dy -> (-2..2).any { dx -> t(x + dx, y + dy) == Terrain.WATER } }
            fun kindFor(x: Int, y: Int, roll: Int): PropKind? {
                val outer = hypot(x - cx, y - cy) > size * 0.42 // the stage-3 ring
                return when {
                    t(x, y) == Terrain.WATER -> null
                    t(x, y) == Terrain.SAND -> if (nearWater(x, y) && roll < 40) PropKind.WILLOW else PropKind.PALM
                    outer && roll < 30 -> PropKind.SNAG
                    outer && roll < 40 -> PropKind.WIRETREE
                    nearWater(x, y) && roll < 50 -> PropKind.WILLOW
                    t(x, y) == Terrain.TALL_GRASS -> if (roll < 70) PropKind.MAPLE else PropKind.MUSHROOM
                    heights[y.mod(size) * size + x.mod(size)] > 1.9 -> PropKind.PINE // hills
                    roll < 35 -> PropKind.OAK
                    roll < 60 -> PropKind.BIRCH
                    roll < 80 -> PropKind.PINE
                    else -> PropKind.CYPRESS
                }
            }
            fun suits(kind: PropKind, x: Int, y: Int) = when (kind) {
                PropKind.PALM -> t(x, y) == Terrain.SAND
                PropKind.WILLOW -> t(x, y) != Terrain.WATER && nearWater(x, y)
                PropKind.MAPLE, PropKind.MUSHROOM -> t(x, y) == Terrain.TALL_GRASS
                else -> t(x, y) == Terrain.GRASS || t(x, y) == Terrain.TALL_GRASS
            }

            val trees = mutableListOf<Prop>()
            val cells = HashMap<Int, MutableList<Prop>>() // tile -> trees, for the spacing check
            fun d(a: Double, b: Double): Double { var v = (b - a) % size; if (v > size / 2.0) v -= size; if (v < -size / 2.0) v += size; return v }
            fun tryPlace(x: Double, y: Double, kind: PropKind): Boolean {
                val ix = floor(x).toInt(); val iy = floor(y).toInt()
                if (hypot(x - cx, y - cy) < 4 || !suits(kind, ix, iy)) return false
                for (dy in -1..1) for (dx in -1..1) {
                    val near = cells[(iy + dy).mod(size) * size + (ix + dx).mod(size)] ?: continue
                    if (near.any { hypot(d(it.x, x), d(it.y, y)) < MIN_TRUNK_GAP }) return false
                }
                val p = Prop(x, y, kind)
                trees += p; cells.getOrPut(iy * size + ix) { mutableListOf() } += p
                return true
            }
            repeat(size * size / 160) {
                val gx = rng.nextDouble(size.toDouble()); val gy = rng.nextDouble(size.toDouble())
                val kind = kindFor(floor(gx).toInt(), floor(gy).toInt(), rng.nextInt(100)) ?: return@repeat
                val want = 3 + rng.nextInt(6); val r = 1.2 + rng.nextDouble() * 2.0
                var placed = 0
                repeat(want * 3) {
                    if (placed >= want) return@repeat
                    // a point in the grove's disc by rejection: plain arithmetic only, no
                    // trig, so every platform (JVM, wasm) places bit-identical trees
                    val ox = (rng.nextDouble() * 2 - 1) * r; val oy = (rng.nextDouble() * 2 - 1) * r
                    if (ox * ox + oy * oy > r * r) return@repeat
                    val x = (gx + ox).mod(size.toDouble()); val y = (gy + oy).mod(size.toDouble())
                    if (tryPlace(x, y, kind)) placed++
                }
            }
            repeat(size * size / 120) { // loners
                val x = rng.nextDouble(size.toDouble()); val y = rng.nextDouble(size.toDouble())
                val kind = kindFor(floor(x).toInt(), floor(y).toInt(), rng.nextInt(100)) ?: return@repeat
                tryPlace(x, y, kind)
            }
            return trees
        }

        private fun smoothLattice(lat: DoubleArray, cells: Int, fx: Double, fy: Double): Double {
            val x0 = floor(fx).toInt(); val y0 = floor(fy).toInt()
            val tx = fx - x0; val ty = fy - y0
            fun v(x: Int, y: Int) = lat[y.mod(cells) * cells + x.mod(cells)]
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

    fun terrainAt(x: Double, y: Double): Int = terrain[floor(y).toInt().mod(size) * size + floor(x).toInt().mod(size)]

    /** Smooth ground height (bilinear), under water included. */
    fun groundAt(x: Double, y: Double): Double {
        val x0 = floor(x).toInt(); val y0 = floor(y).toInt()
        val tx = x - x0; val ty = y - y0
        fun h(ix: Int, iy: Int) = heights[iy.mod(size) * size + ix.mod(size)].toDouble()
        val a = h(x0, y0) + (h(x0 + 1, y0) - h(x0, y0)) * tx
        val b = h(x0, y0 + 1) + (h(x0 + 1, y0 + 1) - h(x0, y0 + 1)) * tx
        return a + (b - a) * ty
    }

    /** What you stand on: the water surface in lakes (you wade, never drown). */
    fun surfaceAt(x: Double, y: Double) = maxOf(groundAt(x, y), WATER_LEVEL)
}
