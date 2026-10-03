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

/** A coin lying in the Wilds: walk into it to pick it up (World.stepPlayer). */
data class Coin(val x: Double, val y: Double)

/** A patrol area. Beasts of [species] (evo [stage]) wander inside it. */
data class Zone(val id: Int, val x: Double, val y: Double, val radius: Double, val species: String, val stage: Int)

/** A beast species the generator may place, e.g. from GameData.beasts. */
data class Species(val name: String, val stage: Int)

/**
 * One big tile of the Wilds: [WorldMap.TILE] cells square, with its first
 * cell at ([x0], [y0]). Its props, zones and coins all lie inside it, in
 * world coordinates. Made by [WorldMap.make], and never changed afterwards.
 */
class MapTile(
    val tx: Int,
    val ty: Int,
    private val heights: FloatArray, // with an apron of WorldMap.PAD cells all round
    /** Row-major terrain of its own cells, no apron. */
    val terrain: IntArray,
    val props: List<Prop>,
    val zones: List<Zone>,
    val coins: List<Coin>,
) {
    val x0 = tx * WorldMap.TILE
    val y0 = ty * WorldMap.TILE

    /**
     * Trees bucketed by cell, for the walk's sidestep (World.sidestep): the
     * [props] indices of cell c's trees are treeIds[treeStart[c] until
     * treeStart[c + 1]], in props order. Derived from [props].
     */
    val treeStart = IntArray(WorldMap.TILE * WorldMap.TILE + 1)
    val treeIds: IntArray

    init {
        val cells = WorldMap.TILE * WorldMap.TILE
        val counts = IntArray(cells)
        props.forEach { if (it.kind.isTree) counts[cell(it)]++ }
        for (c in 0 until cells) treeStart[c + 1] = treeStart[c] + counts[c]
        treeIds = IntArray(treeStart[cells])
        val fill = treeStart.copyOf(cells)
        props.forEachIndexed { i, p -> if (p.kind.isTree) treeIds[fill[cell(p)]++] = i }
    }

    private fun cell(p: Prop) = (floor(p.y).toInt() - y0) * WorldMap.TILE + floor(p.x).toInt() - x0

    /** The id of this tile's coin [i]: what CoinPicked and CoinGone carry. */
    fun coinId(i: Int) = WorldMap.pack(tx, ty, i)

    /** For a spot inside this tile. */
    fun terrainAt(x: Double, y: Double): Int = terrain[(floor(y).toInt() - y0) * WorldMap.TILE + floor(x).toInt() - x0]

    /**
     * Smooth ground height (bilinear), under water included. Good for a spot
     * in this tile or up to a cell outside it (the apron), where it gives
     * exactly what the neighbouring tile would.
     */
    fun groundAt(x: Double, y: Double): Double {
        val fx = floor(x); val fy = floor(y)
        val u = x - fx; val v = y - fy
        val k = (fy.toInt() - y0 + WorldMap.PAD) * DIM + fx.toInt() - x0 + WorldMap.PAD
        val a = heights[k] + (heights[k + 1] - heights[k]) * u
        val b = heights[k + DIM] + (heights[k + DIM + 1] - heights[k + DIM]) * u
        return a + (b - a) * v
    }

    private companion object {
        const val DIM = WorldMap.TILE + 2 * WorldMap.PAD
    }
}

/**
 * Open, endless terrain: a smooth heightmap (hills, lakes, a sea across
 * your way on the coast) with a terrain type per cell, billboard props, patrol zones and
 * coins. There are no walls and no edges. The land comes in big tiles of
 * [TILE] x [TILE] cells: you start in the middle of tile (0, 0), and the
 * tiles around it are made as you walk toward them ([tile]).
 *
 * A tile is made from [seed], [region] and its own coordinates and nothing
 * else: not from the tiles next to it, and not from the order they were
 * made in. So every client that knows the seed and region builds the
 * identical land wherever anyone walks, and only entities need syncing.
 * Everything that crosses from one tile into the next (the ground, the
 * forests, the tracks) comes from noise and track nodes hashed from world
 * coordinates, so the joins don't show.
 *
 * The land is split by a slow "woodland" field into open meadows (flowers,
 * almost no trees), small dense forests (FOREST floor, trees packed as close
 * as [MIN_TRUNK_GAP] allows, ferns and tiny mushrooms) and groves in between.
 * Mud gathers on wet shores and in hollows, and walking tracks run out from
 * the start and on from one tile to the next, cutting through the forests.
 */
class WorldMap private constructor(val seed: Long, val region: Region, private val species: List<Species>) {
    companion object {
        const val WATER_LEVEL = 0.55

        /** Cells along one side of a big tile. */
        const val TILE = 96

        /** Cells of apron round a tile while it's made (and kept for its heights): the reach of every look at a neighbouring cell. */
        const val PAD = 3

        /** Unit steps toward each compass octant (0 = north = -y), all integers. */
        private val OCTANT = arrayOf(0 to -1, 1 to -1, 1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1)

        fun generate(seed: Long, species: List<Species>, region: Region = Region.DEFAULT) = WorldMap(seed, region, species)

        /** Key of tile ([tx], [ty]) in the tile cache and in World's list of woken tiles. */
        fun key(tx: Int, ty: Int) = (tx.toLong() shl 32) or (ty.toLong() and 0xFFFFFFFFL)

        /**
         * A zone's or coin's id: its tile and its index there (under 128), in one
         * positive Int. Good for tiles -2048..2047 each way, which is hundreds of
         * walks further than anyone can get.
         */
        fun pack(tx: Int, ty: Int, i: Int) = ((tx + 2048) shl 19) or ((ty + 2048) shl 7) or i

        private const val COIN_TRAILS = 8
        private const val COIN_SINGLES = 18
        private const val DIAG = 0.7071067811865476 // 1 / sqrt(2), written out so it's the same everywhere
        private val STEP = arrayOf(1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1)

        /** Trunks never closer than this, so there's always a way through a grove. */
        const val MIN_TRUNK_GAP = 0.9

        /** Where the sea band's middle sits, in tiles from the spawn toward the sea. */
        private const val SEA_OFFSET = 0.30
        private const val COAST_DEPTH = 2.2
        private val FOREST_SHARE = doubleArrayOf(0.06, 0.13, 0.20, 0.30)
        private val MEADOW_SHARE = doubleArrayOf(0.50, 0.36, 0.26, 0.16)

        /** Noise octaves of the ground: lattice cells per tile, and how tall. */
        private val OCTAVES = listOf(4 to 1.6, 8 to 0.8, 16 to 0.35, 32 to 0.12)

        // Tracks join nodes on a grid this many cells apart, each node shifted up to
        // NODE_JITTER either way. A node keeps well inside its grid square, so a leg
        // (which meanders up to 4 cells) never leaves the squares of its two ends
        private const val NODE = 48
        private const val NODE_JITTER = 14.0
        private const val LINK_SHARE = 0.65 // of neighbouring nodes have a track between them

        /** Territories' centres keep this far in from a tile's edge, and twice this from each other. */
        private const val ZONE_EDGE = 6

        /** Lattice spacing (cells) of the field that says how wild the country is ([stageAt]). */
        private const val WILD_SPAN = 48.0

        // One salt per hashed field, so they're independent of each other
        private const val HEIGHT_SALT = 0x4E16_4701L
        private const val WOOD_SALT = 0x3A0D_F00DL
        private const val LUSH_SALT = 0x1054_6A55L
        private const val WET_SALT = 0x3E7_3E7L
        private const val WILD_SALT = 0x311D_311DL
        private const val NODE_SALT = 0x40DE_40DEL
        private const val LINK_SALT = 0x7A1C_7A1CL
        private const val LEG_SALT = 0x1E65_1E65L
        private const val TILE_SALT = 0x711E_711EL
        private const val COIN_SALT = 0x0C01_4C01_4L

        private fun mix(v: Long): Long {
            var z = v
            z = (z xor (z ushr 30)) * 0xBF58476D1CE4E5B9uL.toLong()
            z = (z xor (z ushr 27)) * 0x94D049BB133111EBuL.toLong()
            return z xor (z ushr 31)
        }
    }

    /** Cells along one side of a big tile. */
    val size = TILE

    /** The middle of tile (0, 0). */
    val spawnX = TILE / 2 + 0.5
    val spawnY = TILE / 2 + 0.5

    // The ground and woodland noise is the same field everywhere. What counts as water, a
    // hilltop, a forest or a meadow is measured once, on the tile you start in, so that tile
    // has the water and woods its region asks for and the land beyond carries on from it
    private val span = 1.3 + 0.45 * region.relief
    private val low: Double
    private val high: Double
    private val forestAt: Double
    private val meadowBelow: Double

    init {
        val raw = DoubleArray(TILE * TILE) { rawHeight(it % TILE, it / TILE) }
        raw.sort()
        low = raw[(raw.size * region.water / 100.0).toInt()]; high = raw.last()
        val ranked = DoubleArray(TILE * TILE) { woodAt(it % TILE, it / TILE) }
        ranked.sort()
        // woods 0..3 -> share of the land that is forest / open meadow
        forestAt = ranked[(ranked.size * (1 - FOREST_SHARE[region.woods])).toInt()]
        meadowBelow = ranked[(ranked.size * MEADOW_SHARE[region.woods]).toInt()]
    }

    private val tiles = HashMap<Long, MapTile>()
    private val recent = arrayOfNulls<MapTile>(16) // the tiles in use, by the low bits of their coordinates: no lookup, no boxing

    /** Tile ([tx], [ty]), made now if nobody has needed it yet. */
    fun tile(tx: Int, ty: Int): MapTile {
        val slot = (ty and 3) * 4 + (tx and 3)
        val r = recent[slot]
        if (r != null && r.tx == tx && r.ty == ty) return r
        val t = tiles.getOrPut(key(tx, ty)) { make(tx, ty) }
        recent[slot] = t
        return t
    }

    /** Tile ([tx], [ty]) if it has been made, without making it. */
    fun tileIfMade(tx: Int, ty: Int): MapTile? = tiles[key(tx, ty)]

    /**
     * Takes a tile that [make] built somewhere else (a background thread, so
     * the walk doesn't stall when you reach it). Call it on the thread that
     * uses this map.
     */
    fun adopt(t: MapTile) { tiles.getOrPut(key(t.tx, t.ty)) { t } }

    /** The tile you start in. */
    val home get() = tile(0, 0)

    fun tileAt(x: Double, y: Double) = tile(floor(x).toInt().floorDiv(TILE), floor(y).toInt().floorDiv(TILE))

    /** Every tile with land within [reach] of (x, y), row by row: always the same order. */
    inline fun tilesNear(x: Double, y: Double, reach: Double, each: (MapTile) -> Unit) {
        val tx0 = floor(x - reach).toInt().floorDiv(TILE); val tx1 = floor(x + reach).toInt().floorDiv(TILE)
        val ty0 = floor(y - reach).toInt().floorDiv(TILE); val ty1 = floor(y + reach).toInt().floorDiv(TILE)
        for (ty in ty0..ty1) for (tx in tx0..tx1) each(tile(tx, ty))
    }

    /** Every tree standing in cell ([cx], [cy]), with its index in its tile's props. */
    inline fun treesAt(cx: Int, cy: Int, each: (Prop, Int) -> Unit) {
        val t = tile(cx.floorDiv(TILE), cy.floorDiv(TILE))
        val c = cy.mod(TILE) * TILE + cx.mod(TILE)
        for (k in t.treeStart[c] until t.treeStart[c + 1]) { val id = t.treeIds[k]; each(t.props[id], id) }
    }

    private fun tileOf(id: Int) = tile((id ushr 19) - 2048, ((id ushr 7) and 4095) - 2048)

    /** The zone with this [Zone.id]. */
    fun zone(id: Int): Zone = tileOf(id).zones[id and 127]

    /** The coin with this id ([MapTile.coinId]). */
    fun coin(id: Int): Coin = tileOf(id).coins[id and 127]

    fun distance(ax: Double, ay: Double, bx: Double, by: Double) = hypot(bx - ax, by - ay)

    fun terrainAt(x: Double, y: Double): Int = tileAt(x, y).terrainAt(x, y)

    /** Smooth ground height (bilinear), under water included. */
    fun groundAt(x: Double, y: Double): Double = tileAt(x, y).groundAt(x, y)

    /** What you stand on: the water surface in lakes (you wade, never drown). */
    fun surfaceAt(x: Double, y: Double) = maxOf(groundAt(x, y), WATER_LEVEL)

    /**
     * How wild the country is at a spot, 1..3: the evo stage of the beasts
     * whose territory it is. Tame round the start, then a ring of stage 2;
     * further out it's tame in places and wild in others. The dead snags and
     * wire-trees only grow where it's 3.
     */
    fun stageAt(x: Double, y: Double): Int {
        val d = hypot(x - spawnX, y - spawnY)
        if (d < TILE * 0.3) return 1
        if (d < TILE * 0.42) return 2
        val w = noise(WILD_SALT, x / WILD_SPAN, y / WILD_SPAN)
        return if (w < 0.36) 1 else if (w < 0.53) 2 else 3 // about 28%, 27% and 45% of the land
    }

    // ---- the fields: plain arithmetic on world coordinates, so every platform and every tile agrees ----

    private fun hash(salt: Long, x: Int, y: Int) = mix(mix((seed xor salt) + x) + y)

    /** 0..1 from a hash: its top 53 bits, which a Double holds exactly. */
    private fun unit(h: Long) = (h ushr 11).toDouble() / 9007199254740992.0

    /** Smooth 0..1 value noise on an endless lattice; ([fx], [fy]) is in lattice cells. */
    private fun noise(salt: Long, fx: Double, fy: Double): Double {
        val x0 = floor(fx).toInt(); val y0 = floor(fy).toInt()
        val tx = fx - x0; val ty = fy - y0
        val sx = tx * tx * (3 - 2 * tx); val sy = ty * ty * (3 - 2 * ty)
        val a = unit(hash(salt, x0, y0)); val b = unit(hash(salt, x0 + 1, y0))
        val c = unit(hash(salt, x0, y0 + 1)); val d = unit(hash(salt, x0 + 1, y0 + 1))
        val top = a + (b - a) * sx; val bottom = c + (d - c) * sx
        return top + (bottom - top) * sy
    }

    /** The ground at cell (x, y) before it's scaled: a few octaves of noise, less the sea. */
    private fun rawHeight(x: Int, y: Int): Double {
        var h = 0.0
        for ((cells, amp) in OCTAVES) h += noise(HEIGHT_SALT + cells, x.toDouble() * cells / TILE, y.toDouble() * cells / TILE) * amp
        // On the coast, one wide low band toward the real sea's direction becomes a sea or
        // harbour, with the noise making bays and islands; wade across it and the land
        // goes on beyond. A smoothed ramp either side of its middle, not cos: plain
        // arithmetic, so every platform builds bit-identical terrain.
        if (region.coastal) {
            val (ax, ay) = OCTANT[region.seaOctant]
            val s = ((x - TILE / 2.0) * ax + (y - TILE / 2.0) * ay) / TILE - SEA_OFFSET
            val d = (1 - 2 * kotlin.math.abs(s)).coerceIn(0.0, 1.0) // 1 at the sea's middle, 0 half a tile either side
            h -= d * d * (3 - 2 * d) * COAST_DEPTH
        }
        return h
    }

    /** The ground height at cell (x, y): region.water % of the first tile is under [WATER_LEVEL], hills scaled by relief. */
    private fun height(x: Int, y: Int) = ((rawHeight(x, y) - low) / (high - low) * span + WATER_LEVEL).toFloat()

    private fun woodAt(x: Int, y: Int) = noise(WOOD_SALT, x * 6.0 / TILE, y * 6.0 / TILE) + 0.45 * noise(WOOD_SALT + 1, x * 12.0 / TILE, y * 12.0 / TILE)

    /** A cell's terrain before the tracks are laid. */
    private fun baseTerrain(x: Int, y: Int, h: Float, wood: Double): Int {
        val lush = noise(LUSH_SALT, x * 12.0 / TILE, y * 12.0 / TILE)
        val wet = noise(WET_SALT, x * 10.0 / TILE, y * 10.0 / TILE)
        return when {
            h < WATER_LEVEL -> Terrain.WATER
            h < WATER_LEVEL + 0.12 -> if (wet > 0.6) Terrain.MUD else Terrain.SAND // muddy shores and flats, or beach
            h < WATER_LEVEL + 0.3 && wet > 0.72 -> Terrain.MUD // marshy hollows
            wood >= forestAt && hypot(x + 0.5 - spawnX, y + 0.5 - spawnY) > 5 -> Terrain.FOREST
            lush > 0.62 -> Terrain.TALL_GRASS
            else -> Terrain.GRASS
        }
    }

    // ---- making a tile ----

    /** A tile being made: its cells and [PAD] more all round, looked up by world cell. */
    private class Grid(val x0: Int, val y0: Int) {
        val dim = TILE + 2 * PAD
        val heights = FloatArray(dim * dim)
        val wood = DoubleArray(dim * dim)
        val terrain = IntArray(dim * dim)

        fun k(x: Int, y: Int) = (y - y0 + PAD) * dim + x - x0 + PAD
        fun t(x: Int, y: Int) = terrain[k(x, y)]
        fun t(x: Double, y: Double) = terrain[k(floor(x).toInt(), floor(y).toInt())]
        fun covers(x: Int, y: Int) = x >= x0 - PAD && x < x0 + TILE + PAD && y >= y0 - PAD && y < y0 + TILE + PAD
        /** In the tile itself, not its apron. */
        fun owns(x: Double, y: Double) = x >= x0 && x < x0 + TILE && y >= y0 && y < y0 + TILE
    }

    /**
     * Builds tile ([tx], [ty]). It reads nothing but the seed and region and
     * touches no cache, so any thread can call it; hand the result to [adopt].
     */
    fun make(tx: Int, ty: Int): MapTile {
        val g = Grid(tx * TILE, ty * TILE)
        for (y in g.y0 - PAD until g.y0 + TILE + PAD) for (x in g.x0 - PAD until g.x0 + TILE + PAD) {
            val k = g.k(x, y)
            g.heights[k] = height(x, y); g.wood[k] = woodAt(x, y)
            g.terrain[k] = baseTerrain(x, y, g.heights[k], g.wood[k])
        }
        layPaths(tx, ty, g)

        val rng = Random(hash(TILE_SALT, tx, ty))
        fun meadow(x: Int, y: Int) = g.wood[g.k(x, y)] < meadowBelow
        val props = mutableListOf<Prop>()
        props += placeTrees(rng, g)
        repeat(TILE * TILE / 50) {
            val x = g.x0 + rng.nextInt(TILE); val y = g.y0 + rng.nextInt(TILE)
            if (hypot(x + 0.5 - spawnX, y + 0.5 - spawnY) < 4 || g.t(x, y) == Terrain.WATER || g.t(x, y) == Terrain.SAND || g.t(x, y) == Terrain.PATH) return@repeat
            val kind = if (rng.nextInt(100) < 55) PropKind.BUSH else PropKind.STONE
            props += Prop(x + rng.nextDouble(0.2, 0.8), y + rng.nextDouble(0.2, 0.8), kind)
        }
        repeat(TILE * TILE / 5) {
            val x = g.x0 + rng.nextInt(TILE); val y = g.y0 + rng.nextInt(TILE)
            if (g.t(x, y) == Terrain.TALL_GRASS || ((g.t(x, y) == Terrain.GRASS || g.t(x, y) == Terrain.MUD) && rng.nextInt(6) == 0)) {
                props += Prop(x + rng.nextDouble(), y + rng.nextDouble(), PropKind.TUFT)
            }
        }
        // meadow flowers, and ferns and tiny mushrooms on the forest floor
        repeat(TILE * TILE / 4) {
            val x = g.x0 + rng.nextInt(TILE); val y = g.y0 + rng.nextInt(TILE); val roll = rng.nextInt(100)
            val kind = when {
                g.t(x, y) == Terrain.FOREST -> if (roll < 45) PropKind.FERN else if (roll < 70) PropKind.MUSHROOMS else null
                meadow(x, y) && (g.t(x, y) == Terrain.GRASS || g.t(x, y) == Terrain.TALL_GRASS) -> if (roll < 60) PropKind.FLOWERS else null
                g.t(x, y) == Terrain.GRASS && roll < 3 -> PropKind.MUSHROOMS
                else -> null
            } ?: return@repeat
            props += Prop(x + rng.nextDouble(0.1, 0.9), y + rng.nextDouble(0.1, 0.9), kind)
        }

        // Territories keep ZONE_EDGE in from the tile's edge, so two in neighbouring tiles
        // are never closer than two in the same one
        val zones = mutableListOf<Zone>()
        var attempts = 0
        while (zones.size < 16 && attempts++ < 600 && species.isNotEmpty()) {
            val x = g.x0 + ZONE_EDGE + rng.nextInt(TILE - 2 * ZONE_EDGE); val y = g.y0 + ZONE_EDGE + rng.nextInt(TILE - 2 * ZONE_EDGE)
            if (g.t(x, y) == Terrain.WATER) continue
            if (hypot(x + 0.5 - spawnX, y + 0.5 - spawnY) < 10 || zones.any { hypot(it.x - x - 0.5, it.y - y - 0.5) < 2 * ZONE_EDGE }) continue
            val stage = stageAt(x + 0.5, y + 0.5)
            val pool = species.filter { it.stage == stage }.ifEmpty { species }
            val s = pool[rng.nextInt(pool.size)]
            zones += Zone(pack(tx, ty, zones.size), x + 0.5, y + 0.5, 4.0 + stage, s.name, s.stage)
        }

        val terrain = IntArray(TILE * TILE) { g.t(g.x0 + it % TILE, g.y0 + it / TILE) }
        return MapTile(tx, ty, g.heights, terrain, props, zones, placeCoins(tx, ty, g))
    }

    /**
     * Coins: short trails along the walking tracks, plus singles scattered
     * over dry land, fewer of them near the start. Its own RNG, so placing
     * them leaves the rest of the tile as it was.
     */
    private fun placeCoins(tx: Int, ty: Int, g: Grid): List<Coin> {
        val rng = Random(hash(COIN_SALT, tx, ty))
        val coins = mutableListOf<Coin>()
        fun clear(x: Double, y: Double, gap: Double) = coins.none { hypot(it.x - x, it.y - y) < gap }
        // trails: 3-5 coins 0.9 tiles apart, heading along the track (no trig: one of 8 unit steps)
        var tries = 0; var trails = 0
        while (trails < COIN_TRAILS && tries++ < 4000) {
            val x = g.x0 + rng.nextInt(TILE); val y = g.y0 + rng.nextInt(TILE)
            if (g.t(x, y) != Terrain.PATH || !clear(x + 0.5, y + 0.5, 8.0)) continue
            val ways = (0 until 8).filter { i -> val (dx, dy) = STEP[i]; g.t(x + dx, y + dy) == Terrain.PATH }
            if (ways.isEmpty()) continue
            val (dx, dy) = STEP[ways[rng.nextInt(ways.size)]]
            val k = if (dx != 0 && dy != 0) DIAG else 1.0
            repeat(3 + rng.nextInt(3)) { i ->
                val px = x + 0.5 + dx * k * 0.9 * i; val py = y + 0.5 + dy * k * 0.9 * i
                if (g.owns(px, py) && g.t(px, py) != Terrain.WATER) coins += Coin(px, py) // a trail stops at the tile's edge
            }
            trails++
        }
        // singles: rarer near the start, commoner further out
        tries = 0; var singles = 0
        while (singles < COIN_SINGLES && tries++ < 4000) {
            val x = g.x0 + rng.nextDouble(TILE.toDouble()); val y = g.y0 + rng.nextDouble(TILE.toDouble())
            val far = (hypot(x - spawnX, y - spawnY) / (TILE / 2.0)).coerceAtMost(1.0)
            if (rng.nextDouble() > 0.15 + 0.85 * far || !g.owns(x, y) || g.t(x, y) == Terrain.WATER || !clear(x, y, 3.0)) continue
            coins += Coin(x, y); singles++
        }
        return coins
    }

    /** Track node (i, j), or null where its spot is under water: no track starts or ends in a lake. */
    private fun node(i: Int, j: Int): Pair<Double, Double>? {
        val x = (i + 0.5) * NODE + (unit(hash(NODE_SALT, i, j)) * 2 - 1) * NODE_JITTER
        val y = (j + 0.5) * NODE + (unit(hash(NODE_SALT + 1, i, j)) * 2 - 1) * NODE_JITTER
        return if (height(floor(x).toInt(), floor(y).toInt()) < WATER_LEVEL) null else x to y
    }

    /**
     * Walking tracks: a loose net over the whole land. A node sits in every
     * [NODE] x [NODE] square, and about [LINK_SHARE] of the nodes next to
     * each other have a track between them, so tracks run on from tile to
     * tile; two more run from the start to the nearest nodes. Each leg
     * meanders (1D value noise, plain arithmetic) and is about 1-2 cells wide.
     * A track crossing water is a ford and stays water; on a beach it stays
     * sand; everywhere else, mud and forest included, it becomes PATH.
     * A leg is the same whichever tile is drawing its share of it.
     */
    private fun layPaths(tx: Int, ty: Int, g: Grid) {
        val per = TILE / NODE
        for (j in ty * per - 1..ty * per + per) for (i in tx * per - 1..tx * per + per) {
            val a = node(i, j) ?: continue
            if (unit(hash(LINK_SALT, i, j)) < LINK_SHARE) node(i + 1, j)?.let { leg(a, it, Random(hash(LEG_SALT, i, j)), g) }
            if (unit(hash(LINK_SALT + 1, i, j)) < LINK_SHARE) node(i, j + 1)?.let { leg(a, it, Random(hash(LEG_SALT + 1, i, j)), g) }
        }
        if (tx != 0 || ty != 0) return
        // from the start, out to two of the nodes around it (opposite ones first)
        listOf(0 to 0, 1 to 1, 1 to 0, 0 to 1).mapNotNull { (i, j) -> node(i, j)?.let { Triple(i, j, it) } }.take(2)
            .forEach { (i, j, n) -> leg(spawnX to spawnY, n, Random(hash(LEG_SALT + 2, i, j)), g) }
    }

    private fun leg(a: Pair<Double, Double>, b: Pair<Double, Double>, rng: Random, g: Grid) {
        val dx = b.first - a.first; val dy = b.second - a.second
        val len = hypot(dx, dy)
        if (len < 1) return
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
                val cx = floor(px + nx * w).toInt(); val cy = floor(py + ny * w).toInt()
                if (!g.covers(cx, cy)) continue
                val ti = g.k(cx, cy)
                if (g.terrain[ti] != Terrain.WATER && g.terrain[ti] != Terrain.SAND) g.terrain[ti] = Terrain.PATH
            }
        }
    }

    /**
     * Forest cells are packed with trees; elsewhere trees grow in groves
     * of one kind, picked by the terrain at the grove's centre, plus a
     * few loners, and open meadows stay nearly bare. The kinds come from
     * the region's flora. Each tree must also suit its own spot, and grid
     * rejection keeps trunks [MIN_TRUNK_GAP] apart; they keep half that
     * in from the tile's edge, so trunks either side of a join are as far
     * apart as any others.
     */
    private fun placeTrees(rng: Random, g: Grid): List<Prop> {
        val flora = region.flora
        val edge = MIN_TRUNK_GAP / 2
        fun nearWater(x: Int, y: Int) = (-2..2).any { dy -> (-2..2).any { dx -> g.t(x + dx, y + dy) == Terrain.WATER } }
        fun hill(x: Int, y: Int) = g.heights[g.k(x, y)] > WATER_LEVEL + span * 0.68
        fun pick(roll: Int, vararg mix: Pair<PropKind, Int>): PropKind {
            var acc = 0
            for ((k, w) in mix) { acc += w; if (roll < acc) return k }
            return mix.last().first
        }
        // one kind per grove or loner, by terrain and flora
        fun kindFor(x: Int, y: Int, roll: Int): PropKind? {
            val wild = stageAt(x + 0.5, y + 0.5) == 3 // stage-3 country
            return when {
                g.t(x, y) == Terrain.WATER -> null
                g.t(x, y) == Terrain.SAND -> when (flora) {
                    Flora.NZ -> if (roll < 75) PropKind.POHUTUKAWA else PropKind.CABBAGE
                    Flora.TROPICAL -> PropKind.PALM
                    Flora.BOREAL -> PropKind.PINE
                    Flora.TEMPERATE -> if (nearWater(x, y) && roll < 50) PropKind.WILLOW else PropKind.PINE
                }
                wild && roll < 14 -> PropKind.SNAG
                wild && roll < 20 -> PropKind.WIRETREE
                nearWater(x, y) && roll < 45 -> when (flora) {
                    Flora.NZ -> if (roll < 25) PropKind.WILLOW else PropKind.POHUTUKAWA
                    Flora.TROPICAL -> PropKind.PALM
                    Flora.BOREAL -> PropKind.BIRCH
                    Flora.TEMPERATE -> PropKind.WILLOW
                }
                g.t(x, y) == Terrain.FOREST -> forestKind(flora, roll, x, y, hill(x, y))
                hill(x, y) && roll < 60 -> when (flora) {
                    Flora.TROPICAL -> PropKind.OAK; Flora.NZ -> if (roll < 30) PropKind.PINE else PropKind.PONGA; else -> PropKind.PINE
                }
                g.t(x, y) == Terrain.TALL_GRASS -> when (flora) {
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
        fun suits(kind: PropKind, x: Int, y: Int) = when (g.t(x, y)) {
            Terrain.WATER, Terrain.PATH -> false
            Terrain.SAND -> kind == PropKind.PALM || kind == PropKind.POHUTUKAWA || kind == PropKind.PINE ||
                kind == PropKind.CABBAGE || kind == PropKind.WILLOW
            else -> kind != PropKind.WILLOW || nearWater(x, y)
        }

        val trees = mutableListOf<Prop>()
        val cells = HashMap<Int, MutableList<Prop>>() // cell -> trees, for the spacing check
        fun bucket(ix: Int, iy: Int) = (iy - g.y0 + 1) * (TILE + 2) + ix - g.x0 + 1
        fun tryPlace(x: Double, y: Double, kind: PropKind): Boolean {
            if (x < g.x0 + edge || x > g.x0 + TILE - edge || y < g.y0 + edge || y > g.y0 + TILE - edge) return false
            val ix = floor(x).toInt(); val iy = floor(y).toInt()
            if (hypot(x - spawnX, y - spawnY) < 4 || !suits(kind, ix, iy)) return false
            for (dy in -1..1) for (dx in -1..1) {
                val near = cells[bucket(ix + dx, iy + dy)] ?: continue
                if (near.any { hypot(x - it.x, y - it.y) < MIN_TRUNK_GAP }) return false
            }
            val p = Prop(x, y, kind)
            trees += p; cells.getOrPut(bucket(ix, iy)) { mutableListOf() } += p
            return true
        }
        // forests: every forest cell gets a few darts, so trunks pack about as close as the gap allows
        for (y in g.y0 until g.y0 + TILE) for (x in g.x0 until g.x0 + TILE) {
            if (g.t(x, y) != Terrain.FOREST) continue
            repeat(3) {
                val px = x + rng.nextDouble(); val py = y + rng.nextDouble()
                tryPlace(px, py, kindFor(x, y, rng.nextInt(100)) ?: return@repeat)
            }
        }
        repeat(TILE * TILE / 160) {
            val gx = g.x0 + rng.nextDouble(TILE.toDouble()); val gy = g.y0 + rng.nextDouble(TILE.toDouble())
            val roll = rng.nextInt(100)
            val cx = floor(gx).toInt(); val cy = floor(gy).toInt()
            if (!g.owns(gx, gy) || g.wood[g.k(cx, cy)] < meadowBelow) return@repeat // meadows stay open
            val kind = kindFor(cx, cy, roll) ?: return@repeat
            val want = 3 + rng.nextInt(6); val r = 1.2 + rng.nextDouble() * 2.0
            var placed = 0
            repeat(want * 3) {
                if (placed >= want) return@repeat
                // a point in the grove's disc by rejection: plain arithmetic only, no
                // trig, so every platform (JVM, wasm) places bit-identical trees
                val ox = (rng.nextDouble() * 2 - 1) * r; val oy = (rng.nextDouble() * 2 - 1) * r
                if (ox * ox + oy * oy > r * r) return@repeat
                if (tryPlace(gx + ox, gy + oy, kind)) placed++
            }
        }
        repeat(TILE * TILE / 120) { // loners, rarer out in the meadows
            val x = g.x0 + rng.nextDouble(TILE.toDouble()); val y = g.y0 + rng.nextDouble(TILE.toDouble())
            val roll = rng.nextInt(100)
            val cx = floor(x).toInt(); val cy = floor(y).toInt()
            if (!g.owns(x, y) || (g.wood[g.k(cx, cy)] < meadowBelow && roll >= 25)) return@repeat
            val kind = kindFor(cx, cy, roll) ?: return@repeat
            tryPlace(x, y, kind)
        }
        return trees
    }

    /** Forest kinds per flora. NZ bush is tree ferns and nīkau, with some pine plantations. */
    private fun forestKind(flora: Flora, roll: Int, x: Int, y: Int, hill: Boolean): PropKind = when (flora) {
        // the odd square block of plantation pine, native bush elsewhere
        Flora.NZ -> if ((x.floorDiv(12) + y.floorDiv(12) * 3).mod(5) == 0) PropKind.PINE else when {
            roll < 50 -> PropKind.PONGA; roll < 78 -> PropKind.NIKAU; roll < 90 -> PropKind.POHUTUKAWA; else -> PropKind.CABBAGE
        }
        Flora.TROPICAL -> if (roll < 50) PropKind.PALM else PropKind.OAK
        Flora.BOREAL -> if (roll < 60) PropKind.PINE else if (roll < 95) PropKind.BIRCH else PropKind.SNAG
        Flora.TEMPERATE -> if (hill && roll < 50) PropKind.PINE else when { roll < 40 -> PropKind.OAK; roll < 65 -> PropKind.BIRCH; roll < 80 -> PropKind.PINE; else -> PropKind.MAPLE }
    }
}
