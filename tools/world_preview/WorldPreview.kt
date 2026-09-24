import com.rockhard.blocker.world.render.*
import com.rockhard.blocker.world.sim.*
import java.awt.image.BufferedImage
import kotlin.math.PI
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

// Driven by tools/world_preview/run.sh: soak-tests the pure-Kotlin world sim and
// renders preview PNGs using the real sprite GIFs.
fun main(args: Array<String>) {
    val out = args[0]
    val species = listOf("Bytelet" to 1, "Cacheon" to 2, "Technophasia" to 3, "Chirplet" to 1, "Viralia" to 2, "Trendrake" to 3,
        "Noobit" to 1, "Skirmalot" to 2, "Grindlord" to 3, "Bufferoo" to 1, "Streamlet" to 2, "Bingewyrm" to 3,
        "Zephyrlet" to 1, "Airstream" to 2, "Stratolord" to 3, "Cartini" to 1).map { Species(it.first, it.second) }
    val map = WorldMap.generate(20260923L * 2654435761L, species)
    val counts = map.terrain.groupBy { it }.mapValues { it.value.size * 100 / map.terrain.size }
    println("terrain % (0 grass,1 tall,2 sand,3 water): $counts zones=${map.zones.size} props=${map.props.size}")
    check(WorldMap.generate(map.seed, species).terrain.contentEquals(map.terrain))
    check(WorldMap.generate(map.seed, species).props == map.props)

    // trees: kinds, spacing (there must always be a way through), levels
    val trees = map.props.filter { it.kind.isTree }
    println("trees ${trees.size}: " + trees.groupingBy { it.kind.name.lowercase() }.eachCount())
    var closest = Double.MAX_VALUE
    for (i in trees.indices) for (j in i + 1 until trees.size) closest = minOf(closest, map.distance(trees[i].x, trees[i].y, trees[j].x, trees[j].y))
    check(closest >= WorldMap.MIN_TRUNK_GAP) { "trunks $closest apart" }
    println("closest trunks ${"%.2f".format(closest)} tiles (min ${WorldMap.MIN_TRUNK_GAP})")
    check(TerrainRenderer.treeLevel(500.0) == 0 && TerrainRenderer.treeLevel(1.0) == 9 && TerrainRenderer.treeLevel(60.0) == 3)

    val world = World(map)
    val s = LocalWorldSession(world, "me", "Cacheon", 180)
    val me = world.entities[s.localPlayerId]!!
    val rc = TerrainRenderer(200, 300, map)
    for (i in 0 until 3) { me.angle = i * 2.1; rc.render(world, me, 0, Palette.DAY, src, 0); save(rc, "$out/v$i.png") }
    rc.render(world, me, -90, Palette.DAY, src, 0); save(rc, "$out/down.png")
    // the densest grove, from 6 tiles out and from 2 tiles out
    val grove = trees.maxBy { t -> trees.count { map.distance(it.x, it.y, t.x, t.y) < 2.5 } }
    val gs = trees.filter { map.distance(it.x, it.y, grove.x, grove.y) < 2.5 }.groupingBy { it.kind.name.lowercase() }.eachCount()
    for ((name, dist) in listOf("grove" to 6.0, "grove_near" to 2.0)) {
        me.x = map.wrap(grove.x - dist); me.y = grove.y; me.angle = 0.0
        repeat(20) { rc.render(world, me, 0, Palette.DAY, src, 0) }; save(rc, "$out/$name.png")
    }
    println("grove previews: $gs")
    // stand in water if there is some
    val wi = map.terrain.indexOfFirst { it == Terrain.WATER }
    if (wi >= 0) { me.x = wi % map.size + 0.5; me.y = wi / map.size + 0.5; me.angle = 0.3
        repeat(20) { rc.render(world, me, 0, Palette.DAY, src, 0) }; save(rc, "$out/water.png") }

    // encounter: put a beast next to the player, step until companion out
    val b = world.entities.values.first { it.kind == EntityKind.BEAST }
    val z = map.zones[b.zoneId]
    b.x = z.x; b.y = z.y; me.x = map.wrap(z.x - 3.0); me.y = map.wrap(z.y + 0.3); me.angle = 0.0; me.grace = 0
    val log = mutableListOf<String>()
    for (t in 0 until 150) {
        for (e in s.update(World.DT)) log += "t=$t ${e::class.simpleName}"
        if (t == 30) { repeat(20) { rc.render(world, me, 0, Palette.DAY, src, 0) }; save(rc, "$out/throw.png") }
        if (t == 90) { rc.render(world, me, 0, Palette.DAY, src, 0); save(rc, "$out/faceoff.png") }
    }
    println("encounter events: $log")
    s.resolveEncounter(b.id, EncounterOutcome.BEAST_DEFEATED)

    // turnaround: a lone Cacheon 1.6 tiles ahead in an empty world, turned
    // through the 8 headings (walking on the top row, standing on the bottom)
    val tw = World(map); tw.entities.clear()
    val cam = Entity(1, EntityKind.PLAYER, me.x, me.y, 0.0, "", 1.0)
    val beast = Entity(2, EntityKind.BEAST, map.wrap(me.x + 1.6), me.y, 0.0, "Cacheon", b.size)
    tw.entities[1] = cam; tw.entities[2] = beast
    val turn = BufferedImage(8 * 90, 2 * 110, BufferedImage.TYPE_INT_ARGB)
    val tr = TerrainRenderer(90, 110, map)
    for (row in 0..1) for (i in 0 until 8) {
        beast.moving = row == 0; beast.angle = PI + i * PI / 4 // i=0 faces the camera
        tr.render(tw, cam, 0, Palette.DAY, src, 0)
        turn.setRGB(i * 90, row * 110, 90, 110, tr.fb, 0, 90)
    }
    ImageIO.write(turn, "png", File("$out/turn.png"))

    // soak: steer randomly for the whole exploration, count encounters
    val rng = kotlin.random.Random(1)
    var enc = 0; var over = false; var ticks = 0
    val t0 = System.nanoTime()
    while (!over && ticks < 30 * 400) {
        ticks++
        if (ticks % 60 == 0) s.steer(rng.nextDouble(-0.6, 0.6))
        for (e in s.update(World.DT)) when (e) {
            is WorldEvent.BattleReady -> { enc++; s.resolveEncounter(e.beastId, EncounterOutcome.BEAST_DEFEATED) }
            is WorldEvent.ExplorationOver -> over = true
            else -> {}
        }
    }
    println("exploration: over=$over after ${ticks / 30}s of sim, battles=$enc, sim ${(System.nanoTime() - t0) / 1_000_000}ms")
    val t1 = System.nanoTime(); repeat(60) { rc.render(world, me, 0, Palette.DAY, src, it * 16L) }
    println("render ${"%.2f".format((System.nanoTime() - t1) / 1e6 / 60)}ms/frame")
    val snap = world.snapshot(); val w2 = World(map); w2.applySnapshot(snap); check(w2.snapshot() == snap); println("snapshot ok")
}
