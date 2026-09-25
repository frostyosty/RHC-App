package com.rockhard.blocker.world.sim

import com.rockhard.blocker.world.sim.DetMath.hypot
import kotlin.math.floor
import kotlin.random.Random

// Pure Kotlin (no Android imports) so a future server can run the same sim.

object Terrain {
    const val GRASS = 0
    const val TALL_GRASS = 1
    const val SAND = 2
    const val WATER = 3
    const val FOREST = 4 // woodland floor: leaf litter under dense trees
    const val MUD = 5    // lake shores, tidal flats and marshy hollows: slow going
    const val PATH = 6   // a walking track: the quickest way across the map
}

/**
 * [size] is the billboard's world width and height. Trees ([radius] > 0, the
 * trunk radius in tiles) come in 10 distance levels, prop_tree_<kind>_d<0-9>,
 * drawn by sprite_studio/autogen/scenery.py, whose heights and radii match
 * these: change both or neither. The rest are small ground props from
 * designs.py (prop_<name>.gif).
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
    WIRETREE("prop_tree_wiretree", 2.8, 0.12),
    POHUTUKAWA("prop_tree_pohutukawa", 2.8, 0.18),
    CABBAGE("prop_tree_cabbage", 2.6, 0.07),
    PONGA("prop_tree_ponga", 2.4, 0.08),
    NIKAU("prop_tree_nikau", 2.8, 0.08),
    BUSH("prop_bush", 0.6),
    STONE("prop_stone", 0.45),
    TUFT("prop_tuft", 0.35),
    FLOWERS("prop_flowers", 0.3),
    MUSHROOMS("prop_mushrooms", 0.22),
    FERN("prop_fern", 0.55);

    val isTree get() = radius > 0
}

/** Scenery billboard. Nothing collides: the player can never get stuck. */
data class Prop(val x: Double, val y: Double, val kind: PropKind)

/** A patrol area. Beasts of [species] (evo [stage]) wander inside it. */
data class Zone(val id: Int, val x: Double, val y: Double, val radius: Double, val species: String, val stage: Int)

/** A beast species the generator may place, e.g. from GameData.beasts. */
data class Species(val name: String, val stage: Int)

/**
 * Open, wrap-around terrain: a smooth heightmap (hills, lakes, a sea on the
 * coast) with a terrain type per cell, billboard props and patrol zones.
 * The edges wrap (a torus), so there are no walls anywhere. Generated
 * deterministically from [seed] and [region], so every client that knows
 * both builds the identical map; only entities need syncing.
 *
 * The land is split by a slow "woodland" field into open meadows (flowers,
 * almost no trees), small dense forests (FOREST floor, trees packed as close
 * as [MIN_TRUNK_GAP] allows, ferns and tiny mushrooms) and groves in between.
 * Mud gathers on wet shores and in hollows, and walking tracks run out from
 * the start and round the map, cutting through the forests.
 */
class WorldMap(
    val size: Int,
    val seed: Long,
    val region: Region,
    private val heights: FloatArray,
    val terrain: IntArray,
    val props: List<Prop>,
    val zones: List<Zone>,
) {
    companion object {
        const val WATER_LEVEL = 0.55

        /** Unit steps toward each compass octant (0 = north = -y), all integers so the coast band tiles. */
        private val OCTANT = arrayOf(0 to -1, 1 to -1, 1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1)

        fun generate(seed: Long, species: List<Species>, region: Region = Region.DEFAULT, size: Int = 96): WorldMap {
            val rng = Random(seed)
            val cx = size / 2.0; val cy = size / 2.0
            // Tileable value noise: a few octaves on lattices that divide the map size
            val heights = FloatArray(size * size)
            for ((cells, amp) in listOf(4 to 1.6, 8 to 0.8, 16 to 0.35, 32 to 0.12)) {
                val lat = DoubleArray(cells * cells) { rng.nextDouble() }
                for (y in 0 until size) for (x in 0 until size) {
                    val fx = x.toDouble() * cells / size; val fy = y.toDouble() * cells / size
                    heights[y * size + x] += (smoothLattice(lat, cells, fx, fy) * amp).toFloat()
                }
            }
            // On the coast, a wide low band toward the real sea's direction becomes a sea or
            // harbour, with the noise making bays and islands. A smoothed triangle wave, not
            // cos: plain arithmetic, so every platform builds bit-identical terrain.
            if (region.coastal) {
                val (ax, ay) = OCTANT[region.seaOctant]
                for (y in 0 until size) for (x in 0 until size) {
                    val s = ((x - cx) * ax + (y - cy) * ay) / size - SEA_OFFSET
                    val d = kotlin.math.abs(2 * (s - floor(s)) - 1) // 1 at the sea's middle, 0 opposite
                    heights[y * size + x] -= (d * d * (3 - 2 * d) * COAST_DEPTH).toFloat()
                }
            }
            // Normalise so region.water % of the map sits under water, hills scaled by relief
            val span = 1.3 + 0.45 * region.relief
            val sorted = heights.sortedArray()
            val low = sorted[(sorted.size * region.water / 100.0).toInt()]; val high = sorted.last()
            for (i in heights.indices) heights[i] = ((heights[i] - low) / (high - low) * span + WATER_LEVEL).toFloat()

            val lushLat = DoubleArray(12 * 12) { rng.nextDouble() }
            val wetLat = DoubleArray(10 * 10) { rng.nextDouble() }
            val woodA = DoubleArray(6 * 6) { rng.nextDouble() }; val woodB = DoubleArray(12 * 12) { rng.nextDouble() }
            val wood = DoubleArray(size * size) { i ->
                val fx = (i % size).toDouble() / size; val fy = (i / size).toDouble() / size
                smoothLattice(woodA, 6, fx * 6, fy * 6) + 0.45 * smoothLattice(woodB, 12, fx * 12, fy * 12)
            }
            // woods 0..3 -> share of the map that is forest / open meadow
            val ranked = wood.sortedArray()
            val forestAt = ranked[(ranked.size * (1 - FOREST_SHARE[region.woods])).toInt()]
            val meadowBelow = ranked[(ranked.size * MEADOW_SHARE[region.woods]).toInt()]
            val terrain = IntArray(size * size) { i ->
                val h = heights[i]
                val lush = smoothLattice(lushLat, 12, (i % size) * 12.0 / size, (i / size) * 12.0 / size)
                val wet = smoothLattice(wetLat, 10, (i % size) * 10.0 / size, (i / size) * 10.0 / size)
                when {
                    h < WATER_LEVEL -> Terrain.WATER
                    h < WATER_LEVEL + 0.12 -> if (wet > 0.6) Terrain.MUD else Terrain.SAND // muddy shores and flats, or beach
                    h < WATER_LEVEL + 0.3 && wet > 0.72 -> Terrain.MUD // marshy hollows
                    wood[i] >= forestAt && hypot(i % size - cx, i / size - cy) > 5 -> Terrain.FOREST
                    lush > 0.62 -> Terrain.TALL_GRASS
                    else -> Terrain.GRASS
                }
            }
            val meadow = BooleanArray(size * size) { wood[it] < meadowBelow }
            layPaths(rng, size, terrain)

            fun t(x: Int, y: Int) = terrain[y * size + x]
            val props = mutableListOf<Prop>()
            props += placeTrees(rng, size, heights, terrain, meadow, region, span)
            repeat(size * size / 50) {
                val x = rng.nextInt(size); val y = rng.nextInt(size)
                if (hypot(x - cx, y - cy) < 4 || t(x, y) == Terrain.WATER || t(x, y) == Terrain.SAND || t(x, y) == Terrain.PATH) return@repeat
                val kind = if (rng.nextInt(100) < 55) PropKind.BUSH else PropKind.STONE
                props += Prop(x + rng.nextDouble(0.2, 0.8), y + rng.nextDouble(0.2, 0.8), kind)
            }
            repeat(size * size / 5) {
                val x = rng.nextInt(size); val y = rng.nextInt(size)
                if (t(x, y) == Terrain.TALL_GRASS || ((t(x, y) == Terrain.GRASS || t(x, y) == Terrain.MUD) && rng.nextInt(6) == 0)) {
                    props += Prop(x + rng.nextDouble(), y + rng.nextDouble(), PropKind.TUFT)
                }
            }
            // meadow flowers, and ferns and tiny mushrooms on the forest floor
            repeat(size * size / 4) {
                val x = rng.nextInt(size); val y = rng.nextInt(size); val roll = rng.nextInt(100)
                val kind = when {
                    t(x, y) == Terrain.FOREST -> if (roll < 45) PropKind.FERN else if (roll < 70) PropKind.MUSHROOMS else null
                    meadow[y * size + x] && (t(x, y) == Terrain.GRASS || t(x, y) == Terrain.TALL_GRASS) -> if (roll < 60) PropKind.FLOWERS else null
                    t(x, y) == Terrain.GRASS && roll < 3 -> PropKind.MUSHROOMS
                    else -> null
                } ?: return@repeat
                props += Prop(x + rng.nextDouble(0.1, 0.9), y + rng.nextDouble(0.1, 0.9), kind)
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
            return WorldMap(size, seed, region, heights, terrain, props, zones)
        }

        /**
         * Walking tracks: from the start out to two points, and a loop through
         * four points 18-40 tiles out. Each leg meanders (1D value noise, plain
         * arithmetic) and is about 1-2 tiles wide. A track crossing water is a
         * ford and stays water; on a beach it stays sand; everywhere else,
         * mud and forest included, it becomes PATH.
         */
        private fun layPaths(rng: Random, size: Int, terrain: IntArray) {
            val sx = size / 2.0; val sy = size / 2.0
            fun wrapI(v: Int) = v.mod(size)
            val points = mutableListOf<Pair<Double, Double>>()
            var tries = 0
            while (points.size < 4 && tries++ < 400) {
                val x = rng.nextDouble(size.toDouble()); val y = rng.nextDouble(size.toDouble())
                var dx = x - sx; var dy = y - sy
                if (dx > size / 2.0) dx -= size; if (dy > size / 2.0) dy -= size
                val d = hypot(dx, dy)
                if (d < 18 || d > 40 || terrain[wrapI(floor(y).toInt()) * size + wrapI(floor(x).toInt())] == Terrain.WATER) continue
                if (points.any { hypot(it.first - x, it.second - y) < 14 }) continue
                points += x to y
            }
            if (points.size < 2) return
            val legs = mutableListOf((sx to sy) to points[0], (sx to sy) to points[points.size / 2])
            for (i in points.indices) legs += points[i] to points[(i + 1) % points.size]
            for ((a, b) in legs) {
                var dx = (b.first - a.first) % size; var dy = (b.second - a.second) % size
                if (dx > size / 2.0) dx -= size; if (dx < -size / 2.0) dx += size
                if (dy > size / 2.0) dy -= size; if (dy < -size / 2.0) dy += size
                val len = hypot(dx, dy)
                if (len < 1) continue
                val nx = -dy / len; val ny = dx / len
                val knots = DoubleArray(maxOf(3, (len / 9).toInt() + 2)) { rng.nextDouble() * 2 - 1 }
                val amp = minOf(4.0, len * 0.12)
                val steps = (len / 0.35).toInt() + 1
                for (i in 0..steps) {
                    val t = i.toDouble() / steps
                    // meander: smooth noise along the leg, pinned to 0 at both ends
                    val k = t * (knots.size - 1); val k0 = floor(k).toInt().coerceAtMost(knots.size - 2); val f = k - k0
                    val m = knots[k0] + (knots[k0 + 1] - knots[k0]) * f * f * (3 - 2 * f)
                    val off = amp * m * t * (1 - t) * 4
                    val px = a.first + dx * t + nx * off; val py = a.second + dy * t + ny * off
                    for (w in doubleArrayOf(-0.3, 0.3)) {
                        val ti = wrapI(floor(py + ny * w).toInt()) * size + wrapI(floor(px + nx * w).toInt())
                        if (terrain[ti] != Terrain.WATER && terrain[ti] != Terrain.SAND) terrain[ti] = Terrain.PATH
                    }
                }
            }
        }

        /** Trunks never closer than this, so there's always a way through a grove. */
        const val MIN_TRUNK_GAP = 0.9

        /** Where the sea band's middle sits, in coast-band periods from the spawn toward the sea. */
        private const val SEA_OFFSET = 0.30
        private const val COAST_DEPTH = 2.2
        private val FOREST_SHARE = doubleArrayOf(0.06, 0.13, 0.20, 0.30)
        private val MEADOW_SHARE = doubleArrayOf(0.50, 0.36, 0.26, 0.16)

        /**
         * Forest tiles are packed with trees; elsewhere trees grow in groves
         * of one kind, picked by the terrain at the grove's centre, plus a
         * few loners, and open meadows stay nearly bare. The kinds come from
         * the region's flora. Each tree must also suit its own spot, and grid
         * rejection keeps trunks [MIN_TRUNK_GAP] apart.
         */
        private fun placeTrees(rng: Random, size: Int, heights: FloatArray, terrain: IntArray, meadow: BooleanArray, region: Region, span: Double): List<Prop> {
            val cx = size / 2.0; val cy = size / 2.0
            val flora = region.flora
            fun idx(x: Int, y: Int) = y.mod(size) * size + x.mod(size)
            fun t(x: Int, y: Int) = terrain[idx(x, y)]
            fun nearWater(x: Int, y: Int) = (-2..2).any { dy -> (-2..2).any { dx -> t(x + dx, y + dy) == Terrain.WATER } }
            fun hill(x: Int, y: Int) = heights[idx(x, y)] > WATER_LEVEL + span * 0.68
            fun pick(roll: Int, vararg mix: Pair<PropKind, Int>): PropKind {
                var acc = 0
                for ((k, w) in mix) { acc += w; if (roll < acc) return k }
                return mix.last().first
            }
            // one kind per grove or loner, by terrain and flora
            fun kindFor(x: Int, y: Int, roll: Int): PropKind? {
                val outer = hypot(x - cx, y - cy) > size * 0.42 // the stage-3 ring
                return when {
                    t(x, y) == Terrain.WATER -> null
                    t(x, y) == Terrain.SAND -> when (flora) {
                        Flora.NZ -> if (roll < 75) PropKind.POHUTUKAWA else PropKind.CABBAGE
                        Flora.TROPICAL -> PropKind.PALM
                        Flora.BOREAL -> PropKind.PINE
                        Flora.TEMPERATE -> if (nearWater(x, y) && roll < 50) PropKind.WILLOW else PropKind.PINE
                    }
                    outer && roll < 14 -> PropKind.SNAG
                    outer && roll < 20 -> PropKind.WIRETREE
                    nearWater(x, y) && roll < 45 -> when (flora) {
                        Flora.NZ -> if (roll < 25) PropKind.WILLOW else PropKind.POHUTUKAWA
                        Flora.TROPICAL -> PropKind.PALM
                        Flora.BOREAL -> PropKind.BIRCH
                        Flora.TEMPERATE -> PropKind.WILLOW
                    }
                    t(x, y) == Terrain.FOREST -> forestKind(flora, roll, x, y, hill(x, y))
                    hill(x, y) && roll < 60 -> when (flora) {
                        Flora.TROPICAL -> PropKind.OAK; Flora.NZ -> if (roll < 30) PropKind.PINE else PropKind.PONGA; else -> PropKind.PINE
                    }
                    t(x, y) == Terrain.TALL_GRASS -> when (flora) {
                        Flora.NZ -> pick(roll, PropKind.CABBAGE to 60, PropKind.PONGA to 40)
                        Flora.TROPICAL -> pick(roll, PropKind.PALM to 60, PropKind.OAK to 40)
                        Flora.BOREAL -> PropKind.BIRCH
                        Flora.TEMPERATE -> pick(roll, PropKind.MAPLE to 55, PropKind.BIRCH to 45)
                    }
                    else -> when (flora) {
                        // cabbage trees in the paddocks, macrocarpa (cypress) and pine shelter belts
                        Flora.NZ -> pick(roll, PropKind.CABBAGE to 40, PropKind.CYPRESS to 20, PropKind.PINE to 20, PropKind.POHUTUKAWA to 20)
                        Flora.TROPICAL -> pick(roll, PropKind.PALM to 55, PropKind.OAK to 45)
                        Flora.BOREAL -> pick(roll, PropKind.PINE to 50, PropKind.BIRCH to 40, PropKind.SNAG to 10)
                        Flora.TEMPERATE -> pick(roll, PropKind.OAK to 35, PropKind.BIRCH to 25, PropKind.PINE to 20, PropKind.CYPRESS to 20)
                    }
                }
            }
            fun suits(kind: PropKind, x: Int, y: Int) = when (t(x, y)) {
                Terrain.WATER, Terrain.PATH -> false
                Terrain.SAND -> kind == PropKind.PALM || kind == PropKind.POHUTUKAWA || kind == PropKind.PINE ||
                    kind == PropKind.CABBAGE || kind == PropKind.WILLOW
                else -> kind != PropKind.WILLOW || nearWater(x, y)
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
            // forests: every forest tile gets a few darts, so trunks pack about as close as the gap allows
            for (y in 0 until size) for (x in 0 until size) {
                if (t(x, y) != Terrain.FOREST) continue
                repeat(3) {
                    val px = x + rng.nextDouble(); val py = y + rng.nextDouble()
                    tryPlace(px, py, kindFor(x, y, rng.nextInt(100)) ?: return@repeat)
                }
            }
            repeat(size * size / 160) {
                val gx = rng.nextDouble(size.toDouble()); val gy = rng.nextDouble(size.toDouble())
                val roll = rng.nextInt(100)
                if (meadow[idx(floor(gx).toInt(), floor(gy).toInt())]) return@repeat // meadows stay open
                val kind = kindFor(floor(gx).toInt(), floor(gy).toInt(), roll) ?: return@repeat
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
            repeat(size * size / 120) { // loners, rarer out in the meadows
                val x = rng.nextDouble(size.toDouble()); val y = rng.nextDouble(size.toDouble())
                val roll = rng.nextInt(100)
                if (meadow[idx(floor(x).toInt(), floor(y).toInt())] && roll >= 25) return@repeat
                val kind = kindFor(floor(x).toInt(), floor(y).toInt(), roll) ?: return@repeat
                tryPlace(x, y, kind)
            }
            return trees
        }

        /** Forest kinds per flora. NZ bush is tree ferns and nīkau, with some pine plantations. */
        private fun forestKind(flora: Flora, roll: Int, x: Int, y: Int, hill: Boolean): PropKind = when (flora) {
            // the odd square block of plantation pine, native bush elsewhere
            Flora.NZ -> if ((x / 12 + y / 12 * 3) % 5 == 0) PropKind.PINE else when {
                roll < 50 -> PropKind.PONGA; roll < 78 -> PropKind.NIKAU; roll < 90 -> PropKind.POHUTUKAWA; else -> PropKind.CABBAGE
            }
            Flora.TROPICAL -> if (roll < 50) PropKind.PALM else PropKind.OAK
            Flora.BOREAL -> if (roll < 60) PropKind.PINE else if (roll < 95) PropKind.BIRCH else PropKind.SNAG
            Flora.TEMPERATE -> if (hill && roll < 50) PropKind.PINE else when { roll < 40 -> PropKind.OAK; roll < 65 -> PropKind.BIRCH; roll < 80 -> PropKind.PINE; else -> PropKind.MAPLE }
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

    /**
     * Trees bucketed by tile, for the walk's sidestep (World.sidestep): the
     * [props] indices of tile t's trees are treeIds[treeStart[t] until
     * treeStart[t + 1]], in props order. Derived from [props], so it isn't
     * part of the map's identity and snapshots don't carry it.
     */
    val treeStart = IntArray(size * size + 1)
    val treeIds: IntArray

    init {
        val counts = IntArray(size * size)
        props.forEach { if (it.kind.isTree) counts[tile(floor(it.x).toInt(), floor(it.y).toInt())]++ }
        for (t in 0 until size * size) treeStart[t + 1] = treeStart[t] + counts[t]
        treeIds = IntArray(treeStart[size * size])
        val fill = treeStart.copyOf(size * size)
        props.forEachIndexed { i, p -> if (p.kind.isTree) treeIds[fill[tile(floor(p.x).toInt(), floor(p.y).toInt())]++] = i }
    }

    /** Row-major index of tile ([tx], [ty]), wrapped. */
    fun tile(tx: Int, ty: Int) = ty.mod(size) * size + tx.mod(size)

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
