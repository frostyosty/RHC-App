import com.rockhard.blocker.world.render.*
import com.rockhard.blocker.world.sim.*
import java.awt.image.BufferedImage
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

    val world = World(map)
    val s = LocalWorldSession(world, "me", "Cacheon", 180)
    val me = world.entities[s.localPlayerId]!!
    val rc = TerrainRenderer(200, 300, map)
    for (i in 0 until 3) { me.angle = i * 2.1; rc.render(world, me, 0, Palette.DAY, src, 0); save(rc, "$out/v$i.png") }
    rc.render(world, me, -90, Palette.DAY, src, 0); save(rc, "$out/down.png")
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
