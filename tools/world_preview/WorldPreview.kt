import com.rockhard.blocker.world.render.*
import com.rockhard.blocker.world.sim.*
import java.awt.image.BufferedImage
import kotlin.math.PI
import kotlin.math.atan2
import java.io.File
import javax.imageio.ImageIO

val DRAW = "/workspaces/RHC-App/rhc-android/app/src/main/res/drawable-nodpi"
val cache = HashMap<String, Texture?>()
val src = SpriteSource { key, _ ->
    cache.getOrPut(key) {
        val f = File("$DRAW/$key.gif"); if (!f.exists()) { println("missing sprite $key"); null } else {
            val img = ImageIO.read(f); val out = BufferedImage(img.width, img.height, BufferedImage.TYPE_INT_ARGB)
            out.graphics.drawImage(img, 0, 0, null)
            Texture(img.width, img.height, out.getRGB(0, 0, img.width, img.height, null, 0, img.width))
        }
    }
}
fun save(rc: TerrainRenderer, name: String) {
    val img = BufferedImage(rc.w, rc.h, BufferedImage.TYPE_INT_ARGB); img.setRGB(0, 0, rc.w, rc.h, rc.fb, 0, rc.w)
    val big = BufferedImage(rc.w * 2, rc.h * 2, BufferedImage.TYPE_INT_ARGB); big.createGraphics().drawImage(img, 0, 0, rc.w * 2, rc.h * 2, null)
    ImageIO.write(big, "png", File(name))
}

/** Top-down overview (3px per cell) of the tile you start in and the eight around it: terrain colours, trees as dark dots, coins, the spawn in white. */
fun overview(map: WorldMap, name: String) {
    val s = 3; val t = map.size; val img = BufferedImage(3 * t * s, 3 * t * s, BufferedImage.TYPE_INT_RGB)
    fun put(x: Double, y: Double, c: Int) = img.setRGB(((x + t) * s).toInt().coerceIn(0, img.width - 1), ((y + t) * s).toInt().coerceIn(0, img.height - 1), c)
    for (ty in -1..1) for (tx in -1..1) {
        val tile = map.tile(tx, ty)
        for (y in 0 until t) for (x in 0 until t) {
            val c = when (tile.terrain[y * t + x]) {
                Terrain.WATER -> 0x3C6E9A; Terrain.SAND -> 0xCDBB8E; Terrain.TALL_GRASS -> 0x4A6F2E; Terrain.FOREST -> 0x3E4A2A
                Terrain.MUD -> 0x524230; Terrain.PATH -> 0xB09A78; else -> 0x5E8A3C
            }
            for (dy in 0 until s) for (dx in 0 until s) img.setRGB((x + (tx + 1) * t) * s + dx, (y + (ty + 1) * t) * s + dy, c)
        }
        for (p in tile.props) {
            val c = when {
                p.kind.isTree -> 0x14200F
                p.kind == PropKind.FLOWERS -> 0xE8C84A
                p.kind == PropKind.MUSHROOMS -> 0xB08A5E
                else -> continue
            }
            put(p.x, p.y, c)
        }
        for (c in tile.coins) for (d in 0 until 4) put(c.x + (d % 2) / s.toDouble(), c.y + (d / 2) / s.toDouble(), 0xFFD700)
        // territories: a ring, redder the wilder
        for (z in tile.zones) for (a in 0 until 64) put(z.x + kotlin.math.cos(a * PI / 32) * z.radius, z.y + kotlin.math.sin(a * PI / 32) * z.radius, intArrayOf(0xFFFFFF, 0xFFB060, 0xFF4040)[z.stage - 1])
    }
    for (dy in -2..2) for (dx in -2..2) put(map.spawnX + dx / s.toDouble(), map.spawnY + dy / s.toDouble(), 0xFFFFFF)
    ImageIO.write(img, "png", File(name))
}

/** Steering that heads for the nearest coin still lying there: a player out for coins. */
fun coinSteer(w: World, me: Entity): Double {
    val m = w.map
    val near = mutableListOf<Coin>()
    m.tilesNear(me.x, me.y, 48.0) { t -> t.coins.forEachIndexed { i, c -> if (t.coinId(i) !in w.coinGone) near += c } }
    val c = near.minByOrNull { m.distance(me.x, me.y, it.x, it.y) } ?: return 0.0
    var diff = atan2(c.y - me.y, c.x - me.x) - me.angle
    while (diff > PI) diff -= 2 * PI
    while (diff < -PI) diff += 2 * PI
    return diff.coerceIn(-0.08, 0.08)
}

/** Steering that heads for the nearest beast: what a player who wants a fight does. */
fun huntSteer(w: World, me: Entity): Double {
    val b = w.entities.values.filter { it.kind == EntityKind.BEAST && it.state != EntityState.GONE }
        .minByOrNull { w.map.distance(me.x, me.y, it.x, it.y) } ?: return 0.0
    var diff = atan2(b.y - me.y, b.x - me.x) - me.angle
    while (diff > PI) diff -= 2 * PI
    while (diff < -PI) diff += 2 * PI
    return diff.coerceIn(-0.08, 0.08)
}

// Driven by tools/world_preview/run.sh: soak-tests the pure-Kotlin world sim and
// renders preview PNGs using the real sprite GIFs.
fun main(args: Array<String>) {
    val out = args[0]
    val species = listOf("Bytelet" to 1, "Cacheon" to 2, "Technophasia" to 3, "Chirplet" to 1, "Viralia" to 2, "Trendrake" to 3,
        "Noobit" to 1, "Skirmalot" to 2, "Grindlord" to 3, "Bufferoo" to 1, "Streamlet" to 2, "Bingewyrm" to 3,
        "Zephyrlet" to 1, "Airstream" to 2, "Stratolord" to 3, "Cartini" to 1).map { Species(it.first, it.second) }
    val seed = 20260923L * 2654435761L

    // DetMath (the sim's platform-identical trig) against kotlin.math
    var err = 0.0
    for (i in -4000..4000) {
        val x = i * 0.00731 * 7
        err = maxOf(err, kotlin.math.abs(DetMath.sin(x) - kotlin.math.sin(x)), kotlin.math.abs(DetMath.cos(x) - kotlin.math.cos(x)))
        val y = i * 0.0113 - 3.1; val xx = (i % 97) * 0.07 - 3.3
        err = maxOf(err, kotlin.math.abs(DetMath.atan2(y, xx) - kotlin.math.atan2(y, xx)), kotlin.math.abs(DetMath.atan(i * 0.37) - kotlin.math.atan(i * 0.37)))
    }
    check(err < 1e-9) { "DetMath error $err" }
    println("DetMath max error vs kotlin.math: $err")

    // Tauranga from real Open-Meteo elevations: 3 rings of 12 (2.5, 6, 12 km), north first, clockwise
    val tauranga = Region.fromSamples(listOf(
        0, 0, 2, 0, 18, 0, 4, 8, 13, 31, 42, 13,
        0, 0, 0, 6, 1, 55, 37, 39, 1, 1, 0, 0,
        0, 0, 0, 0, 161, 303, 117, 23, 107, 31, 15, 31).map { it.toDouble() }, 12, -37.687, "NZ")
    println("tauranga region: ${tauranga.encode()}")
    check(tauranga.coastal && tauranga.seaOctant == 0 && tauranga.flora == Flora.NZ && tauranga.houses == HouseStyle.NZ && tauranga.water >= 25)
    check(Region.decode(tauranga.encode()) == tauranga && Region.decode("junk") == null)
    val inland = Region.fromSamples(List(36) { 60.0 + it * 3 }, 12, 51.5, "GB")
    check(!inland.coastal && inland.houses == HouseStyle.EURO && inland.flora == Flora.TEMPERATE)

    val maps = listOf("temperate" to WorldMap.generate(seed, species), "tauranga" to WorldMap.generate(seed, species, tauranga))
    for ((name, map) in maps) {
        val home = map.home
        val counts = home.terrain.groupBy { it }.mapValues { it.value.size * 100 / home.terrain.size }
        println("[$name] terrain % (0 grass,1 tall,2 sand,3 water,4 forest): $counts zones=${home.zones.size} props=${home.props.size}")
        // A tile comes from the seed, the region and its own coordinates, whatever was made before it:
        // a second map asked for the far tiles first builds the same land
        val again = WorldMap.generate(map.seed, species, map.region)
        for ((tx, ty) in listOf(-3 to 5, 1 to 0, 0 to 0, -1 to -1, 0 to 1)) {
            val a = map.tile(tx, ty); val b = again.tile(tx, ty)
            check(a.terrain.contentEquals(b.terrain) && a.props == b.props && a.coins == b.coins && a.zones == b.zones) { "tile $tx,$ty differs" }
            check(map.make(tx, ty).props == a.props) { "make($tx, $ty) differs from the tile in use" }
            val inside = { x: Double, y: Double -> x >= a.x0 && x < a.x0 + map.size && y >= a.y0 && y < a.y0 + map.size }
            check(a.props.all { inside(it.x, it.y) } && a.coins.all { inside(it.x, it.y) } && a.zones.all { inside(it.x, it.y) }) { "tile $tx,$ty holds something outside itself" }
            check(a.zones.all { map.zone(it.id) === it } && a.coins.indices.all { map.coin(a.coinId(it)) === a.coins[it] }) { "ids of tile $tx,$ty" }
        }
        val onTrack = home.coins.count { map.terrainAt(it.x, it.y) == Terrain.PATH }
        println("[$name] coins ${home.coins.size} ($onTrack on tracks), none in water: ${home.coins.none { map.terrainAt(it.x, it.y) == Terrain.WATER }}")
        check(home.coins.size in 30..80 && home.coins.none { map.terrainAt(it.x, it.y) == Terrain.WATER })
        // the land around: the nine tiles of the overview
        val around = (-1..1).flatMap { ty -> (-1..1).map { tx -> map.tile(tx, ty) } }
        check(around.none { t -> t.props.any { it.kind.sprite.contains("mushroom") && it.kind.isTree } }) { "no giant mushrooms" }
        // trees: kinds, spacing (there must always be a way through, across the joins too), levels
        val trees = around.flatMap { t -> t.props.filter { it.kind.isTree } }
        println("[$name] trees ${home.props.count { it.kind.isTree }} (${trees.size} in the 9 tiles): " + trees.groupingBy { it.kind.name.lowercase() }.eachCount())
        println("[$name] small: " + home.props.filter { !it.kind.isTree }.groupingBy { it.kind.name.lowercase() }.eachCount())
        fun cell(x: Double, y: Double) = kotlin.math.floor(x).toInt() to kotlin.math.floor(y).toInt()
        val grid = HashMap<Pair<Int, Int>, MutableList<Prop>>()
        for (t in trees) grid.getOrPut(cell(t.x, t.y)) { mutableListOf() } += t
        var closest = Double.MAX_VALUE
        for (t in trees) for (dy in -1..1) for (dx in -1..1) {
            val (cx, cy) = cell(t.x, t.y)
            val near = grid[cx + dx to cy + dy] ?: continue
            for (o in near) if (o !== t) closest = minOf(closest, map.distance(t.x, t.y, o.x, o.y))
        }
        check(closest >= WorldMap.MIN_TRUNK_GAP - 1e-9) { "trunks $closest apart" }
        println("[$name] closest trunks ${"%.2f".format(closest)} tiles (min ${WorldMap.MIN_TRUNK_GAP})")
        // the joins don't show: the ground is the same from either side of an edge, and doesn't step across it;
        // a track that reaches an edge carries on in the next tile
        var step = 0.0; var ends = 0; var crossings = 0
        for (i in 0 until map.size * 3) {
            val v = i - map.size + 0.37
            for ((ax, ay, bx, by) in listOf(listOf(map.size - 1e-9, v, map.size + 1e-9, v), listOf(v, -1e-9, v, 1e-9))) {
                step = maxOf(step, kotlin.math.abs(map.groundAt(ax, ay) - map.groundAt(bx, by)))
                check(map.tileAt(ax, ay).groundAt(bx, by) == map.tileAt(bx, by).groundAt(bx, by)) { "the ground differs across the join at $bx,$by" }
                // (a track on a beach stays sand and a ford stays water, so only count plain ends)
                if (map.terrainAt(ax, ay) == Terrain.PATH) { crossings++; if ((-1..1).none { d -> map.terrainAt(if (ax == bx) bx + d else bx, if (ax == bx) by else by + d).let { it == Terrain.PATH || it == Terrain.SAND || it == Terrain.WATER } }) ends++ }
            }
        }
        println("[$name] joins: ground steps at most ${"%.6f".format(step)} across an edge; $crossings track cells at an edge, $ends of them dead ends")
        check(step < 1e-6 && ends == 0) { "a join shows" }
        // territories: tame round the start, and every stage somewhere in the land around
        val stages = around.flatMap { it.zones }.groupingBy { it.stage }.eachCount().toSortedMap()
        println("[$name] territories by stage in the 9 tiles: $stages (home: ${home.zones.groupingBy { it.stage }.eachCount().toSortedMap()})")
        check(home.zones.filter { map.distance(it.x, it.y, map.spawnX, map.spawnY) < map.size * 0.3 }.all { it.stage == 1 })
        overview(map, "$out/map_$name.png")
    }
    check(World.speedFactor(Terrain.SAND) == 0.9 && World.speedFactor(Terrain.MUD) == 0.8 && World.speedFactor(Terrain.PATH) == 1.1)
    check(TerrainRenderer.treeLevel(500.0) == 0 && TerrainRenderer.treeLevel(1.0) == 9 && TerrainRenderer.treeLevel(60.0) == 3)

    // Views on each map: around the spawn, into the nearest forest, over a meadow, toward the sea
    for ((name, map) in maps) {
        val world = World(map)
        val s = LocalWorldSession(world, "me", "Cacheon", 180)
        val me = world.entities[s.localPlayerId]!!
        val rc = TerrainRenderer(200, 300, map)
        fun shot(file: String, pal: Palette = Palette.DAY, t: Long = 0) { repeat(20) { rc.render(world, me, 0, pal, src, t) }; save(rc, "$out/$file.png") }
        for (i in 0 until 3) { me.angle = -PI / 2 + i * 2.1; shot("${name}_v$i") }
        val home = map.home
        fun tileNear(pred: (Int) -> Boolean): Int? = (0 until map.size * map.size).filter { pred(it) }
            .minByOrNull { map.distance(map.spawnX, map.spawnY, it % map.size + 0.5, it / map.size + 0.5) }
        fun lookAt(i: Int, back: Double) {
            val tx = i % map.size + 0.5; val ty = i / map.size + 0.5
            val a = atan2(ty - map.spawnY, tx - map.spawnX)
            me.x = tx - kotlin.math.cos(a) * back; me.y = ty - kotlin.math.sin(a) * back; me.angle = a
        }
        // a forest tile with forest all around it, seen from outside and from inside
        tileNear { i -> (-2..2).all { d -> home.terrain[(i / map.size) * map.size + (i % map.size + d).coerceIn(0, map.size - 1)] == Terrain.FOREST } }?.let {
            lookAt(it, 7.0); shot("${name}_forest_edge")
            lookAt(it, 0.3); shot("${name}_forest_inside")
        }
        tileNear { i -> home.props.any { p -> p.kind == PropKind.FLOWERS && p.x.toInt() + p.y.toInt() * map.size == i } }?.let { lookAt(it, 3.0); shot("${name}_meadow") }
        // along a track 6+ tiles from the start, and at a patch of mud
        tileNear { i -> home.terrain[i] == Terrain.PATH && map.distance(map.spawnX, map.spawnY, i % map.size + 0.5, i / map.size + 0.5) > 6 }?.let { i ->
            val next = (1..4).map { d -> i to d }.let { _ -> (0 until map.size * map.size).filter { j -> home.terrain[j] == Terrain.PATH &&
                map.distance(i % map.size + 0.5, i / map.size + 0.5, j % map.size + 0.5, j / map.size + 0.5) in 3.0..4.0 }.firstOrNull() }
            me.x = i % map.size + 0.5; me.y = i / map.size + 0.5
            if (next != null) me.angle = atan2(next / map.size + 0.5 - me.y, next % map.size + 0.5 - me.x)
            shot("${name}_track")
        }
        tileNear { i -> home.terrain[i] == Terrain.MUD }?.let { lookAt(it, 2.5); shot("${name}_mud") }
        if (map.region.coastal) {
            me.x = map.spawnX; me.y = map.spawnY; me.angle = -PI / 2 // north, to the sea
            shot("${name}_sea"); shot("${name}_sea_rain", Palette.forWeather("Rain", 12), 1234); shot("${name}_night", Palette.forWeather("Clear", 22))
            me.angle = PI / 2; shot("${name}_town"); shot("${name}_town_night", Palette.forWeather("Clear", 22))
            shot("${name}_storm", Palette.forWeather("Storm", 14), 2050)
        }
    }

    // The fight, on the Tauranga map: a beast squares up, you circle each other, a cage is thrown,
    // the netbeast comes out and the two circle while you circle them; then a move lands and it faints
    val map = maps[1].second
    val world = World(map)
    val s = LocalWorldSession(world, "me", "Cacheon", 180)
    val me = world.entities[s.localPlayerId]!!
    val rc = TerrainRenderer(200, 300, map)
    // the most open zone, so the previews show the fight rather than trunks
    val z = map.home.zones.maxBy { zz -> -map.home.props.count { it.kind.isTree && map.distance(it.x, it.y, zz.x, zz.y) < 5 } }
    for (o in world.entities.values.filter { it.kind == EntityKind.BEAST && it.zoneId == z.id }) { o.x = z.x + 3; o.y = z.y + 3; o.state = EntityState.PAUSE; o.timer = 9999 }
    val b = world.entities.values.first { it.kind == EntityKind.BEAST && it.zoneId == z.id }
    b.x = z.x; b.y = z.y; b.state = EntityState.PATROL; me.x = z.x - 4.5; me.y = z.y + 0.3; me.angle = 0.0; me.grace = 0
    val log = mutableListOf<String>()
    var t = 0
    fun run(ticks: Int, each: (Int) -> Unit = {}) = repeat(ticks) {
        if (me.state == EntityState.WALKING) s.steer(huntSteer(world, me)) // beasts shy away: go get it
        for (e in s.update(World.DT)) log += "t=$t ${e::class.simpleName}"
        each(t); t++
    }
    run(90) { if (it == 70) { rc.render(world, me, 0, Palette.DAY, src, 0); save(rc, "$out/fight_standoff.png") } }
    check(me.state == EntityState.ENGAGED && world.roleEntity(me.id, Role.BEAST) === b) { "no encounter with the placed beast: $log" }
    val gap = map.distance(me.x, me.y, b.x, b.y)
    check(gap > 2.0) { "the beast came right up to you ($gap tiles)" }
    s.throwCage("Cacheon")
    run(10); rc.render(world, me, 0, Palette.DAY, src, 0); save(rc, "$out/fight_throw.png")
    run(100)
    val comp = world.roleEntity(me.id, Role.COMPANION)
    check(comp != null) { "no companion: $log" }
    val watch = map.distance(me.x, me.y, (comp.x + b.x) / 2, (comp.y + b.y) / 2)
    println("fight: gap before cage ${"%.2f".format(gap)}, you circle the pair at ~${"%.2f".format(watch)} tiles, beasts ${"%.2f".format(map.distance(comp.x, comp.y, b.x, b.y))} apart")
    rc.render(world, me, 0, Palette.DAY, src, 0); save(rc, "$out/fight_duel.png")
    // nobody circles one way for long: with no swipes the fight turns round every 3-6s, easing to a stop first
    var flips = 0; var lastSign = 0; var slowest = Double.MAX_VALUE
    run(360) {
        val d = me.orbitSpin
        val sign = if (d > 0.5) 1 else if (d < -0.5) -1 else 0
        if (sign != 0 && lastSign != 0 && sign != lastSign) flips++
        if (sign != 0) lastSign = sign
        slowest = minOf(slowest, kotlin.math.abs(d))
    }
    println("fight: circled the other way $flips times in 12s (slowest ${"%.2f".format(slowest)})")
    check(flips in 2..4 && slowest < 0.1) { "the circling should reverse every 3-6s, easing through a stop: $flips flips" }
    // a hit lands when the attacker's lunge peaks, not when the move is called
    s.perform(Role.COMPANION, Action.ATTACK); run(3); s.effect(Role.BEAST, "bite"); s.perform(Role.BEAST, Action.HIT)
    check(b.action != Action.HIT && b.fx == null) { "the hit should wait for the lunge" }
    run(4)
    check(b.action == Action.HIT && b.fx == "bite") { "the hit should land at the lunge's peak" }
    rc.render(world, me, 0, Palette.DAY, src, 0); save(rc, "$out/fight_attack.png")
    run(60)
    // the beast's counter at the top of its lunge, with no effect in the way: the bodies should meet
    s.perform(Role.BEAST, Action.ATTACK); run(Action.ATTACK.ticks / 2)
    rc.render(world, me, 0, Palette.DAY, src, 0); save(rc, "$out/fight_lunge.png")
    run(30)
    val a0 = me.orbitA; run(60); check(me.orbitA != a0) { "you stopped circling" }
    s.perform(Role.BEAST, Action.FAINT); run(30); rc.render(world, me, 0, Palette.DAY, src, 0); save(rc, "$out/fight_faint.png")
    val onScreen = rc.onScreen.keys
    s.resolveEncounter(b.id, EncounterOutcome.BEAST_DEFEATED)
    // you walk on at 60% pace, easing back to full over 8s
    var x0 = me.x; var y0 = me.y
    run(30)
    val firstSecond = map.distance(x0, y0, me.x, me.y)
    check(world.roleEntity(me.id, Role.COMPANION) == null && me.state == EntityState.WALKING)
    println("fight events: $log; drawn: $onScreen")
    println("after the fight: ${"%.2f".format(firstSecond)} tiles in the first second (full pace ${World.WALK_SPEED})")
    check(firstSecond < World.WALK_SPEED * 0.75) { "you should walk on slowly after a fight" }

    // clobbered (a last stand lost): you're knocked flat, lie there, get up, then walk on slowly
    val b2 = world.entities.values.first { it.kind == EntityKind.BEAST && it.state != EntityState.GONE && it !== b }
    b2.x = me.x + kotlin.math.cos(me.angle) * 2; b2.y = me.y + kotlin.math.sin(me.angle) * 2
    b2.state = EntityState.PAUSE; b2.timer = 9999; me.grace = 0
    run(30)
    check(me.state == EntityState.ENGAGED) { "no second encounter: $log" }
    s.perform(Role.BEAST, Action.ATTACK); run(8); s.perform(Role.PLAYER, Action.HIT); run(20)
    s.resolveEncounter(b2.id, EncounterOutcome.PLAYER_BEATEN)
    x0 = me.x; y0 = me.y
    // one render a tick with the clock running, so the eased roll and eye height play out as in the app
    fun live(ticks: Int) = run(ticks) { rc.render(world, me, 0, Palette.DAY, src, t * 33L) }
    live(8); save(rc, "$out/getup_down.png")
    live(20); save(rc, "$out/getup_rising.png")
    check(map.distance(x0, y0, me.x, me.y) == 0.0) { "you walked off while knocked flat" }
    live(30); save(rc, "$out/getup_walking.png")

    // the Netbeasts screen's first frame (a 360x140dp window, 200 px wide like the walk), made the
    // way WorldBridge.prepareNextWalk makes it, and how long that takes
    run {
        val t0 = System.nanoTime()
        val fw = World(WorldMap.generate(seed, species, tauranga)); val fs = LocalWorldSession(fw, "p", "Cacheon", 180)
        val t1 = System.nanoTime()
        val fr = TerrainRenderer(200, 200 * 140 / 360, fw.map)
        fr.render(fw, fw.entities[fs.localPlayerId]!!, 0, Palette.DAY, src, 0L)
        val t2 = System.nanoTime()
        save(fr, "$out/first_frame.png")
        println("first frame: map and world ${(t1 - t0) / 1_000_000}ms, renderer and frame ${(t2 - t1) / 1_000_000}ms (JVM, warm)")
    }

    // a coin trail on a track, seen from a few steps back, and walking it picks every coin up
    run {
        val cw = World(map); val cs = LocalWorldSession(cw, "c", null, 180); val cme = cw.entities[cs.localPlayerId]!!
        cw.entities.values.removeAll { it.kind == EntityKind.BEAST }
        val a = map.home.coins[0]; val b = map.home.coins[1]
        val len = map.distance(a.x, a.y, b.x, b.y)
        val dx = (b.x - a.x) / len; val dy = (b.y - a.y) / len
        cme.x = a.x - dx * 2.5; cme.y = a.y - dy * 2.5; cme.angle = atan2(dy, dx); cme.grace = 999
        val crc = TerrainRenderer(200, 300, map)
        repeat(10) { crc.render(cw, cme, 0, Palette.DAY, src, it * 33L) }
        save(crc, "$out/coins.png")
        var got = 0
        repeat(30 * 4) { for (e in cs.update(World.DT)) if (e is WorldEvent.CoinPicked) got++ }
        println("coin trail: walked it and picked up $got")
        check(got >= 3) { "walking along a trail should pick its coins up" }
    }
    check(me.downTicks == 0 && me.moving) { "you should be up and walking" }

    // Visitors (WorldVisitors.kt in the app): a boss and a poacher put in your path. Walk straight
    // on and you walk into them; they stand and wait and never come at you. The poacher throws his
    // own cages, one netbeast at a time, and walks off beaten; a boss you lose to waits again.
    run {
        val vw = World(map); vw.entities.values.removeAll { it.kind == EntityKind.BEAST } // just the visitors
        val vs = LocalWorldSession(vw, "v", "Cacheon", 180); val vme = vw.entities[vs.localPlayerId]!!
        vme.x = z.x - 14; vme.y = z.y; vme.angle = 0.0; vme.grace = 0
        val vr = TerrainRenderer(200, 300, map)
        val vlog = mutableListOf<String>()
        var vt = 0
        fun step(ticks: Int, each: (WorldEvent) -> Unit = {}) = repeat(ticks) { for (e in vs.update(World.DT)) { vlog += "t=$vt ${e::class.simpleName}"; each(e) }; vt++ }
        fun shot(file: String) { repeat(3) { vr.render(vw, vme, 0, Palette.DAY, src, vt * 33L) }; save(vr, "$out/$file.png") }
        /** Walks on without steering until you square up to [id]; it mustn't have moved. Returns the seconds it took. */
        fun walkInto(id: Int): Double {
            val v = vw.entities[id]!!; val x0 = v.x; val y0 = v.y; val t0 = vt
            var met = false
            step(30 * 3); shot(if (v.kind == EntityKind.RIVAL) "visitor_poacher" else "visitor_boss") // on the way in
            while (!met && vt - t0 < 30 * 12) step(1) { e -> if (e is WorldEvent.Encounter) { check(e.beastId == id) { "squared up to ${e.species}" }; met = true } }
            check(met) { "walking straight on never reached the ${v.species}: $vlog" }
            check(v.x == x0 && v.y == y0) { "the ${v.species} moved toward you" }
            return (vt - t0) / 30.0
        }

        val boss = vs.summon(EntityKind.BEAST, "Titan", 1.5)
        val bossIn = walkInto(boss)
        vs.throwCage("Cacheon"); step(60); shot("visitor_boss_fight")
        // lost: you walk on, and it waits there again without squaring up to you straight away
        vs.resolveEncounter(boss, EncounterOutcome.PLAYER_BEATEN)
        step(30 * 6) { e -> check(e !is WorldEvent.Encounter) { "the boss squared up again straight after" } }
        check(vw.entities[boss]?.state == EntityState.WAIT && vme.state == EntityState.WALKING)

        val poacher = vs.summon(EntityKind.RIVAL, "Poacher", 0.7)
        val poacherIn = walkInto(poacher)
        var outs = 0
        vs.rivalSendOut("Bytelet"); step(60) { e -> if (e is WorldEvent.RivalOut) outs++ }
        val first = checkNotNull(vw.roleEntity(vme.id, Role.BEAST)) { "his netbeast didn't come out: $vlog" }
        check(outs == 1 && first.species == "Bytelet") { "the wrong netbeast came out: $vlog" }
        vs.throwCage("Cacheon"); step(60)
        check(vw.roleEntity(vme.id, Role.COMPANION) != null) { "yours didn't come out: $vlog" }
        step(60); shot("poacher_duel")
        val pr = vw.entities[poacher]!!
        check(map.distance(vme.x, vme.y, pr.x, pr.y) > map.distance(vme.x, vme.y, first.x, first.y) + 1) { "the poacher should hang back behind his netbeast" }
        // his first falls and lies there; he sends out the next where it fell
        vs.perform(Role.BEAST, Action.FAINT); step(30)
        vs.rivalSendOut("Chirplet"); step(60) { e -> if (e is WorldEvent.RivalOut) outs++ }
        val second = checkNotNull(vw.roleEntity(vme.id, Role.BEAST)) { "his second netbeast didn't come out: $vlog" }
        check(outs == 2 && second.species == "Chirplet" && first.state == EntityState.GONE) { "his first should lie there and the second be out: $vlog" }
        step(30); shot("poacher_second")
        vs.perform(Role.BEAST, Action.FAINT); step(30)
        vs.resolveEncounter(poacher, EncounterOutcome.BEAST_DEFEATED)
        step(30)
        check(pr.state == EntityState.LEAVE && vme.state == EntityState.WALKING)
        val vsnap = vw.snapshot(); val w3 = World(map); w3.applySnapshot(vsnap); check(w3.snapshot() == vsnap) { "visitors in a snapshot" }
        step(World.LEAVE_TICKS + World.FADE_TICKS)
        check(listOf(poacher, first.id, second.id).none { it in vw.entities }) { "the poacher and his netbeasts should be gone" }
        println("visitors: walked into the boss ${"%.1f".format(bossIn)}s and the poacher ${"%.1f".format(poacherIn)}s after they appeared; " +
            "the poacher sent out $outs and walked off; events $vlog")
    }

    // the horizon all the way round from that open spot, by day and at night (8 views, north first)
    val pano = BufferedImage(8 * 120, 2 * 160, BufferedImage.TYPE_INT_ARGB)
    val pr = TerrainRenderer(120, 160, map)
    val pw = World(map); pw.entities.clear()
    val eye = Entity(1, EntityKind.PLAYER, z.x, z.y, 0.0, "", 1.0); pw.entities[1] = eye
    for (row in 0..1) for (i in 0 until 8) {
        eye.angle = -PI / 2 + i * PI / 4
        repeat(10) { pr.render(pw, eye, 0, if (row == 0) Palette.DAY else Palette.forWeather("Clear", 22), src, 0) }
        pano.setRGB(i * 120, row * 160, 120, 160, pr.fb, 0, 120)
    }
    ImageIO.write(pano, "png", File("$out/tauranga_pano.png"))

    // The sidestep: straight walks through the densest forests on both maps. Nothing blocks you,
    // you barely ever stand inside a trunk, and you never drop below 70% of the walking pace.
    for ((name, m) in maps) {
        val forest = (0 until m.size * m.size).filter { m.home.terrain[it] == Terrain.FOREST }
        val rnd = kotlin.random.Random(3)
        var walking = 0; var inside = 0; var dodging = 0; var minTick = 9.0; var minWindow = 9.0
        repeat(40) {
            val w = World(m); w.entities.values.removeAll { it.kind == EntityKind.BEAST } // no fights, just trees
            val p = w.addPlayer("t", null, 60); p.grace = 9999 // the tiles next door wake as you near them: nothing squares up to you
            val start = forest[rnd.nextInt(forest.size)]
            p.x = start % m.size + 0.5; p.y = start / m.size + 0.5; p.angle = rnd.nextDouble(0.0, 2 * PI)
            // back up 4 tiles so you walk in from outside
            p.x = p.x - kotlin.math.cos(p.angle) * 4; p.y = p.y - kotlin.math.sin(p.angle) * 4
            val window = ArrayDeque<Double>()
            repeat(World.TICK_HZ * 8) {
                val x0 = p.x; val y0 = p.y
                val pace = World.WALK_SPEED * World.speedFactor(m.terrainAt(x0, y0)) * World.DT
                w.step(emptyMap())
                val hx = kotlin.math.cos(p.angle); val hy = kotlin.math.sin(p.angle)
                val fwd = ((p.x - x0) * hx + (p.y - y0) * hy) / pace
                val side = kotlin.math.abs(-(p.x - x0) * hy + (p.y - y0) * hx)
                walking++; if (side > 1e-9) dodging++
                minTick = minOf(minTick, fwd)
                window.addLast(fwd); if (window.size > World.TICK_HZ) window.removeFirst()
                if (window.size == World.TICK_HZ) minWindow = minOf(minWindow, window.average())
                val cx = kotlin.math.floor(p.x).toInt(); val cy = kotlin.math.floor(p.y).toInt()
                var hit = false
                for (dy in -1..1) for (dx in -1..1) m.treesAt(cx + dx, cy + dy) { tr, _ -> if (m.distance(p.x, p.y, tr.x, tr.y) < tr.kind.radius) hit = true }
                if (hit) inside++
            }
        }
        println("[$name] sidestep: ${"%.2f".format(inside * 100.0 / walking)}% of forest-walk ticks inside a trunk, dodging ${dodging * 100 / walking}% of the time, " +
            "forward pace min ${"%.2f".format(minTick)} per tick / ${"%.2f".format(minWindow)} per 1s window")
        check(minTick >= World.MIN_FORWARD - 1e-9 && minWindow >= World.MIN_FORWARD - 1e-9) { "the walk slowed below ${World.MIN_FORWARD}" }
        check(inside * 100.0 / walking < 1.0) { "too much time inside trunks" }
    }
    // walking straight at a grove: frames from the walk, and the path seen from above
    run {
        val m = maps[1].second
        val h = m.home
        val forest = (0 until m.size * m.size).filter { i -> i % m.size in 6 until m.size - 2 && (-1..1).all { d -> h.terrain[i + d] == Terrain.FOREST } }
        val target = forest.maxBy { i -> h.treeStart[i + 2] - h.treeStart[i] }
        val w = World(m); w.entities.values.removeAll { it.kind == EntityKind.BEAST }
        val p = w.addPlayer("g", null, 60); p.grace = 9999
        p.x = target % m.size + 0.5 - 5; p.y = target / m.size + 0.5; p.angle = 0.0
        val r = TerrainRenderer(200, 300, m)
        val strip = BufferedImage(6 * 204, 300, BufferedImage.TYPE_INT_ARGB)
        val path = mutableListOf<Pair<Double, Double>>()
        val x0 = p.x; val y0 = p.y
        for (t in 0 until 30 * 5) {
            w.step(emptyMap()); path += p.x to p.y
            r.render(w, p, 0, Palette.DAY, src, t * 33L)
            if (t % 25 == 24) strip.setRGB((t / 25) * 204, 0, 200, 300, r.fb, 0, 200)
        }
        ImageIO.write(strip, "png", File("$out/sidestep.png"))
        // top-down, 16px per tile: trunks with their clearance rings, and the path (white)
        val s = 16; val span = 14; val ox = x0 - 1; val oy = y0 - span / 2.0
        val img = BufferedImage(span * s, span * s, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        for (ty in 0 until span) for (tx in 0 until span) {
            g.color = java.awt.Color(if (m.terrainAt(ox + tx, oy + ty) == Terrain.FOREST) 0x3E4A2A else 0x5E8A3C)
            g.fillRect(tx * s, ty * s, s, s)
        }
        for (tr in h.props.filter { it.kind.isTree }) {
            val px = (tr.x - ox) * s; val py = (tr.y - oy) * s
            if (px < -s || py < -s || px > span * s + s || py > span * s + s) continue
            val cr = (tr.kind.radius + World.CLEARANCE) * s; val rr = tr.kind.radius * s
            g.color = java.awt.Color(0x6A7A4A); g.drawOval((px - cr).toInt(), (py - cr).toInt(), (2 * cr).toInt(), (2 * cr).toInt())
            g.color = java.awt.Color(0x1A1410); g.fillOval((px - rr).toInt(), (py - rr).toInt(), (2 * rr).toInt().coerceAtLeast(2), (2 * rr).toInt().coerceAtLeast(2))
        }
        g.color = java.awt.Color.WHITE
        for ((a, b) in path.zipWithNext()) g.drawLine(((a.first - ox) * s).toInt(), ((a.second - oy) * s).toInt(), ((b.first - ox) * s).toInt(), ((b.second - oy) * s).toInt())
        ImageIO.write(img, "png", File("$out/sidestep_path.png"))
        println("grove walk: drifted ${"%.2f".format(p.y - y0)} tiles sideways over ${"%.1f".format(p.x - x0)} tiles")
    }

    // turnaround: a lone Cacheon 1.6 tiles ahead in an empty world, turned
    // through the 8 headings (walking on the top row, standing on the bottom)
    val tw = World(map); tw.entities.clear()
    val cam = Entity(1, EntityKind.PLAYER, me.x, me.y, 0.0, "", 1.0)
    val beast = Entity(2, EntityKind.BEAST, me.x + 1.6, me.y, 0.0, "Cacheon", 0.75)
    tw.entities[1] = cam; tw.entities[2] = beast
    val turn = BufferedImage(8 * 90, 2 * 110, BufferedImage.TYPE_INT_ARGB)
    val tr = TerrainRenderer(90, 110, map)
    for (row in 0..1) for (i in 0 until 8) {
        beast.moving = row == 0; beast.angle = PI + i * PI / 4 // i=0 faces the camera
        tr.render(tw, cam, 0, Palette.DAY, src, 0)
        turn.setRGB(i * 90, row * 110, 90, 110, tr.fb, 0, 90)
    }
    ImageIO.write(turn, "png", File("$out/turn.png"))

    // soak: whole explorations on both maps. A wanderer steers at random and should never be
    // ambushed; a hunter heads for the nearest beast and catches them. Each fight throws a
    // cage after 2s, trades a few blows and ends.
    val t0 = System.nanoTime()
    // A coin seeker heads for the nearest coin (and fights whatever it walks into on the way).
    for ((name, m) in maps) for (mode in listOf("wanderer", "hunter", "coins")) {
    val hunter = mode == "hunter"
    val rng = kotlin.random.Random(1)
    val sw = World(m); val ss = LocalWorldSession(sw, "soak", "Cacheon", 180)
    val me2 = sw.entities[ss.localPlayerId]!!
    var enc = 0; var over = false; var ticks = 0; var fightTicks = -1; var beastId = -1; var outs = 0; var behind = 0; var coins = 0
    while (!over && ticks < 30 * 1200) {
        ticks++
        if (hunter && me2.state == EntityState.WALKING) ss.steer(huntSteer(sw, me2))
        else if (mode == "coins" && me2.state == EntityState.WALKING) ss.steer(coinSteer(sw, me2))
        else if (ticks % 60 == 0) ss.steer(rng.nextDouble(-0.6, 0.6))
        if (fightTicks >= 0) {
            fightTicks++
            if (fightTicks == 60) ss.throwCage(listOf("Cacheon", "Bytelet").random(rng))
            if (fightTicks in 120..300 && fightTicks % 45 == 0) { ss.perform(Role.COMPANION, Action.ATTACK); ss.perform(Role.BEAST, Action.HIT) }
            if (fightTicks == 330) ss.perform(Role.BEAST, Action.FAINT)
            // every third fight you lose and get knocked flat
            if (fightTicks == 390) { ss.resolveEncounter(beastId, if (enc % 3 == 0) EncounterOutcome.PLAYER_BEATEN else EncounterOutcome.BEAST_DEFEATED); fightTicks = -1 }
        }
        val hx = kotlin.math.cos(me2.angle); val hy = kotlin.math.sin(me2.angle)
        for (e in ss.update(World.DT)) when (e) {
            is WorldEvent.Encounter -> {
                enc++; fightTicks = 0; beastId = e.beastId
                // it must have been in front of you: you walked into it, it didn't jump you
                val b = sw.entities[e.beastId]!!
                if ((b.x - me2.x) * hx + (b.y - me2.y) * hy <= 0) behind++
            }
            is WorldEvent.CompanionOut -> outs++
            is WorldEvent.RivalOut -> {}
            is WorldEvent.CoinPicked -> {
                coins++
                val c = m.coin(e.coin)
                check(e.finderId == me2.id && m.distance(me2.x, me2.y, c.x, c.y) <= World.COIN_REACH + 1e-9) { "picked up a coin from afar" }
            }
            is WorldEvent.SlippedPast -> {}
            is WorldEvent.ExplorationOver -> over = true
        }
    }
    println("[$name] $mode: over=$over after ${ticks / 30}s of sim, fights=$enc (from behind: $behind), netbeasts out=$outs, coins=$coins")
    check(over && outs == enc && behind == 0)
    when (mode) {
        "hunter" -> check(enc >= 3) { "a hunter should catch beasts" }
        "coins" -> check(coins >= 15) { "heading for coins should pick plenty up: $coins" }
        else -> check(enc <= 1) { "a wanderer got into $enc fights" }
    }
    }
    // A netbeast let out on a walk (tap its cage): the cage lands ahead of you, it comes out and
    // roams near you until the walk ends. It's in nobody's fight. A forager (the Looter trait)
    // brings you the coins it finds; any other picks none up.
    for ((name, m) in maps) for (forage in listOf(false, true)) {
        val rng = kotlin.random.Random(2)
        val rw = World(m); val rs = LocalWorldSession(rw, "roam", "Cacheon", 180); val rme = rw.entities[rs.localPlayerId]!!
        var ticks = 0; var over = false; var fightTicks = -1; var beastId = -1; var fights = 0
        var found = 0; var mine = 0; var outAt = -1; var far = 0.0; var near = 0; var walked = 0; var ahead = 0
        fun roamer() = rw.entities.values.firstOrNull { it.kind == EntityKind.COMPANION && it.state == EntityState.ROAM }
        while (!over && ticks < 30 * 400) {
            ticks++
            if (ticks == 30) rs.letOut("Bytelet", forage)
            if (ticks % 60 == 0) rs.steer(rng.nextDouble(-0.6, 0.6))
            if (fightTicks >= 0 && ++fightTicks == 150) { rs.resolveEncounter(beastId, EncounterOutcome.PLAYER_FLED); fightTicks = -1 }
            for (e in rs.update(World.DT)) when (e) {
                is WorldEvent.Encounter -> { fights++; fightTicks = 0; beastId = e.beastId }
                is WorldEvent.CoinPicked -> if (e.finderId == rme.id) mine++ else {
                    found++
                    val c = m.coin(e.coin); val r = rw.entities[e.finderId]!!
                    check(forage && e.playerId == rme.id && m.distance(r.x, r.y, c.x, c.y) <= World.COIN_REACH + 1e-9) { "a roamer's coin from afar" }
                }
                is WorldEvent.ExplorationOver -> over = true
                else -> {}
            }
            val r = roamer()
            if (r == null) { check(outAt < 0) { "the roamer left at tick $ticks" }; continue }
            if (outAt < 0) {
                outAt = ticks
                // it comes out in front of you, close enough to see
                val d = m.distance(rme.x, rme.y, r.x, r.y)
                val front = (r.x - rme.x) * kotlin.math.cos(rme.angle) + (r.y - rme.y) * kotlin.math.sin(rme.angle)
                check(front > 1 && d < 4) { "the cage opened $d tiles off, $front ahead" }
            }
            check(r.link == -1 && r.foe == -1 && rw.roleEntity(rme.id, Role.COMPANION) == null) { "a roamer was drawn into a fight" }
            check(rw.entities.values.none { it.foe == r.id || it.link == r.id }) { "something took notice of the roamer" }
            if (rme.state == EntityState.WALKING) {
                walked++
                val d = m.distance(rme.x, rme.y, r.x, r.y); far = maxOf(far, d); if (d < 8) near++
                if ((r.x - rme.x) * kotlin.math.cos(rme.angle) + (r.y - rme.y) * kotlin.math.sin(rme.angle) > 0) ahead++
            }
        }
        println("[$name] roamer${if (forage) " (forager)" else ""}: out after ${"%.1f".format((outAt - 30) / 30.0)}s, within 8 tiles ${near * 100 / walked}% of the walk (furthest ${"%.1f".format(far)}), " +
            "in front of you ${ahead * 100 / walked}%, it found $found coins (you $mine), fights=$fights")
        check(over && roamer() != null) { "it should roam until the walk ends" }
        check(near * 100 / walked >= if (forage) 70 else 90) { "the roamer didn't keep up" }
        check(if (forage) found >= 3 else found == 0) { "coins found by the roamer: $found" }
    }
    // ...and what you see: the cage coming down, and the netbeast trotting ahead a few seconds on
    run {
        val rw = World(map); rw.entities.values.removeAll { it.kind == EntityKind.BEAST }
        val rs = LocalWorldSession(rw, "roam", "Cacheon", 180); val rme = rw.entities[rs.localPlayerId]!!
        val rr = TerrainRenderer(200, 300, map)
        var rt = 0
        fun go(ticks: Int, file: String) { repeat(ticks) { rs.update(World.DT); rr.render(rw, rme, 0, Palette.DAY, src, rt++ * 33L) }; save(rr, "$out/$file.png") }
        rs.letOut("Cacheon", false)
        go(12, "roamer_cage"); go(38, "roamer_out"); go(90, "roamer_1"); go(60, "roamer_2")
    }
    println("soaks: sim ${(System.nanoTime() - t0) / 1_000_000}ms")

    // The land is endless: a walk straight out from the start crosses into new tiles, and their beasts
    // come out as you near them. Which tiles happen to have been made already (by the renderer, the
    // minimap or the app's background tile-maker) must not change the sim, and nothing ever stops you.
    for ((name, m) in maps) for (heading in listOf(0.6, -PI / 2)) {
        val warm = WorldMap.generate(m.seed, species, m.region)
        for (ty in -6..6) for (tx in -6..6) warm.adopt(warm.make(tx, ty))
        val runs = listOf(WorldMap.generate(m.seed, species, m.region), warm).map { mm ->
            val w = World(mm); val p = w.addPlayer("far", null, 180); p.angle = heading
            var slowest = Double.MAX_VALUE
            while (p.state != EntityState.DONE) {
                val x0 = p.x; val y0 = p.y
                for (e in w.step(emptyMap())) if (e is WorldEvent.Encounter) w.resolveEncounter(p.id, e.beastId, EncounterOutcome.PLAYER_FLED)
                if (p.state == EntityState.WALKING) slowest = minOf(slowest, mm.distance(x0, y0, p.x, p.y) / World.DT)
            }
            Triple(w, p, slowest)
        }
        val (w, p, slowest) = runs[0]
        check(w.snapshot() == runs[1].first.snapshot()) { "the walk depends on which tiles were already made" }
        val far = m.distance(m.spawnX, m.spawnY, p.x, p.y)
        val snap = w.snapshot()
        println("[$name] straight walk (heading ${"%.1f".format(heading)}): ended ${"%.0f".format(far)} cells from the start in tile ${m.tileAt(p.x, p.y).let { "${it.tx},${it.ty}" }}, " +
            "${snap.awake.size} tiles awake, ${snap.entities.count { it.kind == EntityKind.BEAST }} beasts, slowest ${"%.2f".format(slowest)} cells/s")
        check(far > 150 && m.tileAt(p.x, p.y) !== m.home && slowest > 0.5) { "the walk should carry on across the tiles" }
        // the tiles left far behind go back to sleep, so the beasts don't pile up
        check(snap.awake.size <= 6 && WorldMap.key(0, 0) !in snap.awake) { "tiles left behind should sleep: ${snap.awake.size} awake" }
    }
    // how long a new tile takes to make, and a frame at a corner where four tiles meet
    run {
        val m = WorldMap.generate(seed + 1, species, tauranga)
        m.home
        val t1 = System.nanoTime(); var n = 0
        for (ty in -2..2) for (tx in 1..2) { m.make(tx, ty); n++ }
        println("a new tile: ${"%.1f".format((System.nanoTime() - t1) / 1e6 / n)}ms to make (JVM, warm)")
    }
    for ((name, m) in maps) {
        val w = World(m); val p = w.addPlayer("c", null, 180); p.x = m.size.toDouble(); p.y = m.size.toDouble()
        val r = TerrainRenderer(200, 300, m)
        repeat(40) { p.angle += 0.16; w.step(emptyMap()); r.render(w, p, 0, Palette.DAY, src, it * 16L) }
        val t1 = System.nanoTime(); repeat(60) { p.angle += 0.1; r.render(w, p, 0, Palette.forWeather("Rain", 12), src, it * 16L) }
        println("[$name] render at a four-tile corner ${"%.2f".format((System.nanoTime() - t1) / 1e6 / 60)}ms/frame (rain)")
        // looking across a join from a few cells back: east out of the first tile, and south
        p.x = m.size - 6.0; p.y = m.spawnY; p.angle = 0.0; repeat(3) { r.render(w, p, 0, Palette.DAY, src, 0) }; save(r, "$out/${name}_join_east.png")
        p.x = m.spawnX; p.y = m.size - 6.0; p.angle = PI / 2; repeat(3) { r.render(w, p, 0, Palette.DAY, src, 0) }; save(r, "$out/${name}_join_south.png")
    }
    for ((name, m) in maps) {
        val w = World(m); val ls = LocalWorldSession(w, "r", null, 180); val p = w.entities[ls.localPlayerId]!!
        val r = TerrainRenderer(200, 300, m)
        repeat(5) { r.render(w, p, 0, Palette.DAY, src, it * 16L) }
        val t1 = System.nanoTime(); repeat(60) { p.angle += 0.1; r.render(w, p, 0, Palette.forWeather("Rain", 12), src, it * 16L) }
        println("[$name] render ${"%.2f".format((System.nanoTime() - t1) / 1e6 / 60)}ms/frame (rain)")
    }
    val aCoin = map.home.coinId(0)
    world.coinGone[aCoin] = 99 // so the round trip carries a picked-up coin too
    val snap = world.snapshot(); val w2 = World(map); w2.applySnapshot(snap); check(w2.snapshot() == snap && w2.coinGone[aCoin] == 99); println("snapshot ok")
}
