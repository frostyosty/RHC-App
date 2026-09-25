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

/** Top-down overview (4px per tile): terrain colours, trees as dark dots, the spawn in white. */
fun overview(map: WorldMap, name: String) {
    val s = 4; val img = BufferedImage(map.size * s, map.size * s, BufferedImage.TYPE_INT_RGB)
    for (y in 0 until map.size) for (x in 0 until map.size) {
        val c = when (map.terrain[y * map.size + x]) {
            Terrain.WATER -> 0x3C6E9A; Terrain.SAND -> 0xCDBB8E; Terrain.TALL_GRASS -> 0x4A6F2E; Terrain.FOREST -> 0x3E4A2A
            Terrain.MUD -> 0x524230; Terrain.PATH -> 0xB09A78; else -> 0x5E8A3C
        }
        for (dy in 0 until s) for (dx in 0 until s) img.setRGB(x * s + dx, y * s + dy, c)
    }
    for (p in map.props) {
        val c = when {
            p.kind.isTree -> 0x14200F
            p.kind == PropKind.FLOWERS -> 0xE8C84A
            p.kind == PropKind.MUSHROOMS -> 0xB08A5E
            else -> continue
        }
        img.setRGB((p.x * s).toInt().coerceIn(0, map.size * s - 1), (p.y * s).toInt().coerceIn(0, map.size * s - 1), c)
    }
    for (dy in -3..3) for (dx in -3..3) img.setRGB((map.spawnX * s).toInt() + dx, (map.spawnY * s).toInt() + dy, 0xFFFFFF)
    ImageIO.write(img, "png", File(name))
}

/** Steering that heads for the nearest beast: what a player who wants a fight does. */
fun huntSteer(w: World, me: Entity): Double {
    val b = w.entities.values.filter { it.kind == EntityKind.BEAST && it.state != EntityState.GONE }
        .minByOrNull { w.map.distance(me.x, me.y, it.x, it.y) } ?: return 0.0
    var diff = atan2(w.map.delta(me.y, b.y), w.map.delta(me.x, b.x)) - me.angle
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
        val counts = map.terrain.groupBy { it }.mapValues { it.value.size * 100 / map.terrain.size }
        println("[$name] terrain % (0 grass,1 tall,2 sand,3 water,4 forest): $counts zones=${map.zones.size} props=${map.props.size}")
        check(WorldMap.generate(map.seed, species, map.region).terrain.contentEquals(map.terrain))
        check(WorldMap.generate(map.seed, species, map.region).props == map.props)
        check(map.props.none { it.kind.sprite.contains("mushroom") && it.kind.isTree }) { "no giant mushrooms" }
        // trees: kinds, spacing (there must always be a way through), levels
        val trees = map.props.filter { it.kind.isTree }
        println("[$name] trees ${trees.size}: " + trees.groupingBy { it.kind.name.lowercase() }.eachCount())
        println("[$name] small: " + map.props.filter { !it.kind.isTree }.groupingBy { it.kind.name.lowercase() }.eachCount())
        val grid = HashMap<Int, MutableList<Prop>>()
        for (t in trees) grid.getOrPut(t.y.toInt() * map.size + t.x.toInt()) { mutableListOf() } += t
        var closest = Double.MAX_VALUE
        for (t in trees) for (dy in -1..1) for (dx in -1..1) {
            val near = grid[(t.y.toInt() + dy).mod(map.size) * map.size + (t.x.toInt() + dx).mod(map.size)] ?: continue
            for (o in near) if (o !== t) closest = minOf(closest, map.distance(t.x, t.y, o.x, o.y))
        }
        check(closest >= WorldMap.MIN_TRUNK_GAP) { "trunks $closest apart" }
        println("[$name] closest trunks ${"%.2f".format(closest)} tiles (min ${WorldMap.MIN_TRUNK_GAP})")
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
        fun tileNear(pred: (Int) -> Boolean): Int? = (0 until map.size * map.size).filter { pred(it) }
            .minByOrNull { map.distance(map.spawnX, map.spawnY, it % map.size + 0.5, it / map.size + 0.5) }
        fun lookAt(i: Int, back: Double) {
            val tx = i % map.size + 0.5; val ty = i / map.size + 0.5
            val a = atan2(map.delta(map.spawnY, ty), map.delta(map.spawnX, tx))
            me.x = map.wrap(tx - kotlin.math.cos(a) * back); me.y = map.wrap(ty - kotlin.math.sin(a) * back); me.angle = a
        }
        // a forest tile with forest all around it, seen from outside and from inside
        tileNear { i -> (-2..2).all { d -> map.terrain[(i / map.size) * map.size + (i % map.size + d).mod(map.size)] == Terrain.FOREST } }?.let {
            lookAt(it, 7.0); shot("${name}_forest_edge")
            lookAt(it, 0.3); shot("${name}_forest_inside")
        }
        tileNear { i -> map.props.any { p -> p.kind == PropKind.FLOWERS && p.x.toInt() + p.y.toInt() * map.size == i } }?.let { lookAt(it, 3.0); shot("${name}_meadow") }
        // along a track 6+ tiles from the start, and at a patch of mud
        tileNear { i -> map.terrain[i] == Terrain.PATH && map.distance(map.spawnX, map.spawnY, i % map.size + 0.5, i / map.size + 0.5) > 6 }?.let { i ->
            val next = (1..4).map { d -> i to d }.let { _ -> (0 until map.size * map.size).filter { j -> map.terrain[j] == Terrain.PATH &&
                map.distance(i % map.size + 0.5, i / map.size + 0.5, j % map.size + 0.5, j / map.size + 0.5) in 3.0..4.0 }.firstOrNull() }
            me.x = i % map.size + 0.5; me.y = i / map.size + 0.5
            if (next != null) me.angle = atan2(map.delta(me.y, next / map.size + 0.5), map.delta(me.x, next % map.size + 0.5))
            shot("${name}_track")
        }
        tileNear { i -> map.terrain[i] == Terrain.MUD }?.let { lookAt(it, 2.5); shot("${name}_mud") }
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
    val z = map.zones.maxBy { zz -> -map.props.count { it.kind.isTree && map.distance(it.x, it.y, zz.x, zz.y) < 5 } }
    for (o in world.entities.values.filter { it.kind == EntityKind.BEAST && it.zoneId == z.id }) { o.x = map.wrap(z.x + 3); o.y = map.wrap(z.y + 3); o.state = EntityState.PAUSE; o.timer = 9999 }
    val b = world.entities.values.first { it.kind == EntityKind.BEAST && it.zoneId == z.id }
    b.x = z.x; b.y = z.y; b.state = EntityState.PATROL; me.x = map.wrap(z.x - 4.5); me.y = map.wrap(z.y + 0.3); me.angle = 0.0; me.grace = 0
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
    s.perform(Role.COMPANION, Action.ATTACK); run(6); s.effect(Role.BEAST, "bite"); s.perform(Role.BEAST, Action.HIT)
    run(4); rc.render(world, me, 0, Palette.DAY, src, 0); save(rc, "$out/fight_attack.png")
    run(60)
    val a0 = me.orbitA; run(60); check(me.orbitA != a0) { "you stopped circling" }
    s.perform(Role.BEAST, Action.FAINT); run(30); rc.render(world, me, 0, Palette.DAY, src, 0); save(rc, "$out/fight_faint.png")
    val onScreen = rc.onScreen.keys
    s.resolveEncounter(b.id, EncounterOutcome.BEAST_DEFEATED)
    run(60)
    check(world.roleEntity(me.id, Role.COMPANION) == null && me.state == EntityState.WALKING)
    println("fight events: $log; drawn: $onScreen")

    // the horizon all the way round from that open spot, by day and at night (8 views, north first)
    val pano = BufferedImage(8 * 120, 2 * 160, BufferedImage.TYPE_INT_ARGB)
    val pr = TerrainRenderer(120, 160, map)
    val pw = World(map); pw.entities.clear()
    val eye = Entity(1, EntityKind.PLAYER, map.wrap(z.x), map.wrap(z.y), 0.0, "", 1.0); pw.entities[1] = eye
    for (row in 0..1) for (i in 0 until 8) {
        eye.angle = -PI / 2 + i * PI / 4
        repeat(10) { pr.render(pw, eye, 0, if (row == 0) Palette.DAY else Palette.forWeather("Clear", 22), src, 0) }
        pano.setRGB(i * 120, row * 160, 120, 160, pr.fb, 0, 120)
    }
    ImageIO.write(pano, "png", File("$out/tauranga_pano.png"))

    // The sidestep: straight walks through the densest forests on both maps. Nothing blocks you,
    // you barely ever stand inside a trunk, and you never drop below 70% of the walking pace.
    for ((name, m) in maps) {
        val forest = (0 until m.size * m.size).filter { m.terrain[it] == Terrain.FOREST }
        val rnd = kotlin.random.Random(3)
        var walking = 0; var inside = 0; var dodging = 0; var minTick = 9.0; var minWindow = 9.0
        repeat(40) {
            val w = World(m); w.entities.values.removeAll { it.kind == EntityKind.BEAST } // no fights, just trees
            val p = w.addPlayer("t", null, 60)
            val start = forest[rnd.nextInt(forest.size)]
            p.x = start % m.size + 0.5; p.y = start / m.size + 0.5; p.angle = rnd.nextDouble(0.0, 2 * PI)
            // back up 4 tiles so you walk in from outside
            p.x = m.wrap(p.x - kotlin.math.cos(p.angle) * 4); p.y = m.wrap(p.y - kotlin.math.sin(p.angle) * 4)
            val window = ArrayDeque<Double>()
            repeat(World.TICK_HZ * 8) {
                val x0 = p.x; val y0 = p.y
                val pace = World.WALK_SPEED * World.speedFactor(m.terrainAt(x0, y0)) * World.DT
                w.step(emptyMap())
                val hx = kotlin.math.cos(p.angle); val hy = kotlin.math.sin(p.angle)
                val fwd = (m.delta(x0, p.x) * hx + m.delta(y0, p.y) * hy) / pace
                val side = kotlin.math.abs(-m.delta(x0, p.x) * hy + m.delta(y0, p.y) * hx)
                walking++; if (side > 1e-9) dodging++
                minTick = minOf(minTick, fwd)
                window.addLast(fwd); if (window.size > World.TICK_HZ) window.removeFirst()
                if (window.size == World.TICK_HZ) minWindow = minOf(minWindow, window.average())
                val cx = kotlin.math.floor(p.x).toInt(); val cy = kotlin.math.floor(p.y).toInt()
                var hit = false
                for (dy in -1..1) for (dx in -1..1) { val t = m.tile(cx + dx, cy + dy)
                    for (k in m.treeStart[t] until m.treeStart[t + 1]) { val tr = m.props[m.treeIds[k]]
                        if (m.distance(p.x, p.y, tr.x, tr.y) < tr.kind.radius) hit = true } }
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
        val forest = (0 until m.size * m.size).filter { i -> m.terrain[i] == Terrain.FOREST &&
            (-1..1).all { d -> m.terrain[m.tile(i % m.size + d, i / m.size)] == Terrain.FOREST } }
        val target = forest.maxBy { i -> m.treeStart[i + 1] - m.treeStart[i] + (m.treeStart[m.tile(i % m.size + 1, i / m.size) + 1] - m.treeStart[m.tile(i % m.size + 1, i / m.size)]) }
        val w = World(m); w.entities.values.removeAll { it.kind == EntityKind.BEAST }
        val p = w.addPlayer("g", null, 60)
        p.x = m.wrap(target % m.size + 0.5 - 5); p.y = target / m.size + 0.5; p.angle = 0.0
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
        for (tr in m.props.filter { it.kind.isTree }) {
            val px = m.delta(ox, tr.x) * s; val py = m.delta(oy, tr.y) * s
            if (px < -s || py < -s || px > span * s + s || py > span * s + s) continue
            val cr = (tr.kind.radius + World.CLEARANCE) * s; val rr = tr.kind.radius * s
            g.color = java.awt.Color(0x6A7A4A); g.drawOval((px - cr).toInt(), (py - cr).toInt(), (2 * cr).toInt(), (2 * cr).toInt())
            g.color = java.awt.Color(0x1A1410); g.fillOval((px - rr).toInt(), (py - rr).toInt(), (2 * rr).toInt().coerceAtLeast(2), (2 * rr).toInt().coerceAtLeast(2))
        }
        g.color = java.awt.Color.WHITE
        for ((a, b) in path.zipWithNext()) g.drawLine((m.delta(ox, a.first) * s).toInt(), (m.delta(oy, a.second) * s).toInt(), (m.delta(ox, b.first) * s).toInt(), (m.delta(oy, b.second) * s).toInt())
        ImageIO.write(img, "png", File("$out/sidestep_path.png"))
        println("grove walk: drifted ${"%.2f".format(m.delta(y0, p.y))} tiles sideways over ${"%.1f".format(m.delta(x0, p.x))} tiles")
    }

    // turnaround: a lone Cacheon 1.6 tiles ahead in an empty world, turned
    // through the 8 headings (walking on the top row, standing on the bottom)
    val tw = World(map); tw.entities.clear()
    val cam = Entity(1, EntityKind.PLAYER, me.x, me.y, 0.0, "", 1.0)
    val beast = Entity(2, EntityKind.BEAST, map.wrap(me.x + 1.6), me.y, 0.0, "Cacheon", 0.75)
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
    for ((name, m) in maps) for (hunter in listOf(false, true)) {
    val rng = kotlin.random.Random(1)
    val sw = World(m); val ss = LocalWorldSession(sw, "soak", "Cacheon", 180)
    val me2 = sw.entities[ss.localPlayerId]!!
    var enc = 0; var over = false; var ticks = 0; var fightTicks = -1; var beastId = -1; var outs = 0; var behind = 0
    while (!over && ticks < 30 * 1200) {
        ticks++
        if (hunter && me2.state == EntityState.WALKING) ss.steer(huntSteer(sw, me2))
        else if (ticks % 60 == 0) ss.steer(rng.nextDouble(-0.6, 0.6))
        if (fightTicks >= 0) {
            fightTicks++
            if (fightTicks == 60) ss.throwCage(listOf("Cacheon", "Bytelet").random(rng))
            if (fightTicks in 120..300 && fightTicks % 45 == 0) { ss.perform(Role.COMPANION, Action.ATTACK); ss.perform(Role.BEAST, Action.HIT) }
            if (fightTicks == 330) ss.perform(Role.BEAST, Action.FAINT)
            if (fightTicks == 390) { ss.resolveEncounter(beastId, EncounterOutcome.BEAST_DEFEATED); fightTicks = -1 }
        }
        val hx = kotlin.math.cos(me2.angle); val hy = kotlin.math.sin(me2.angle)
        for (e in ss.update(World.DT)) when (e) {
            is WorldEvent.Encounter -> {
                enc++; fightTicks = 0; beastId = e.beastId
                // it must have been in front of you: you walked into it, it didn't jump you
                val b = sw.entities[e.beastId]!!
                if (m.delta(me2.x, b.x) * hx + m.delta(me2.y, b.y) * hy <= 0) behind++
            }
            is WorldEvent.CompanionOut -> outs++
            is WorldEvent.ExplorationOver -> over = true
        }
    }
    println("[$name] ${if (hunter) "hunter" else "wanderer"}: over=$over after ${ticks / 30}s of sim, fights=$enc (from behind: $behind), netbeasts out=$outs")
    check(over && outs == enc && behind == 0)
    if (hunter) check(enc >= 3) { "a hunter should catch beasts" } else check(enc <= 1) { "a wanderer got into $enc fights" }
    }
    println("soaks: sim ${(System.nanoTime() - t0) / 1_000_000}ms")
    for ((name, m) in maps) {
        val w = World(m); val ls = LocalWorldSession(w, "r", null, 180); val p = w.entities[ls.localPlayerId]!!
        val r = TerrainRenderer(200, 300, m)
        repeat(5) { r.render(w, p, 0, Palette.DAY, src, it * 16L) }
        val t1 = System.nanoTime(); repeat(60) { p.angle += 0.1; r.render(w, p, 0, Palette.forWeather("Rain", 12), src, it * 16L) }
        println("[$name] render ${"%.2f".format((System.nanoTime() - t1) / 1e6 / 60)}ms/frame (rain)")
    }
    val snap = world.snapshot(); val w2 = World(map); w2.applySnapshot(snap); check(w2.snapshot() == snap); println("snapshot ok")
}
