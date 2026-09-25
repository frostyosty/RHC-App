package com.rockhard.blocker.world.sim

// Pure Kotlin (no Android imports) so a future server can run the same sim.

/** Which trees grow here. Each set is a mix of the PropKind trees (see WorldMap.placeTrees). */
enum class Flora { TEMPERATE, NZ, TROPICAL, BOREAL }

/** The row of houses painted on the far horizon (prop_house_<style>_<n>). NONE shows only hills. */
enum class HouseStyle(val key: String) { NONE(""), NZ("nz"), EURO("euro"), US("us"), NORDIC("nordic"), TROPICAL("tropical") }

/**
 * What the real place around the player is like, from the app's location
 * and elevation lookup (RegionProbe). Everything is a small whole number so
 * two players in the same area build the identical map: the region is part
 * of the map's identity, alongside the day's seed.
 *
 * [water] is the percent of the map under water (lakes inland, a sea or
 * harbour on the coast). [seaOctant] is the compass direction of the sea
 * (0 = north, clockwise in 45 degree steps), or -1 inland. [relief] (0 flat
 * .. 3 mountainous) scales the hills; [woods] (0 open .. 3 wooded) how much
 * of the land is forest.
 */
data class Region(
    val water: Int = 12,
    val seaOctant: Int = -1,
    val relief: Int = 2,
    val woods: Int = 2,
    val flora: Flora = Flora.TEMPERATE,
    val houses: HouseStyle = HouseStyle.NONE,
) {
    init {
        require(water in 5..45 && seaOctant in -1..7 && relief in 0..3 && woods in 0..3)
    }

    val coastal get() = seaOctant >= 0

    /** For prefs and for sending to other players: "12,-1,2,2,TEMPERATE,NONE". */
    fun encode() = "$water,$seaOctant,$relief,$woods,${flora.name},${houses.name}"

    companion object {
        val DEFAULT = Region()

        fun decode(s: String?): Region? = try {
            val p = s!!.split(',')
            Region(p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toInt(), Flora.valueOf(p[4]), HouseStyle.valueOf(p[5]))
        } catch (e: Exception) { null }

        /**
         * Builds a region from elevation samples in rings around the player
         * ([elevations] in metres, ring by ring, [perRing] samples each,
         * starting north and going clockwise; the data reports the sea as
         * exactly 0, while land below sea level, like Dutch polders, is
         * negative), the latitude and the ISO country code. Pure arithmetic,
         * so it's easy to test.
         */
        fun fromSamples(elevations: List<Double>, perRing: Int, lat: Double, country: String?): Region {
            val sea = elevations.map { it == 0.0 }
            val seaShare = sea.count { it }.toDouble() / elevations.size.coerceAtLeast(1)
            // The sea's direction: the octant with the most sea samples across all rings
            var seaOctant = -1
            if (seaShare >= 0.08) {
                val perOctant = IntArray(8)
                elevations.indices.filter { sea[it] }.forEach { i -> perOctant[((i % perRing) * 8 / perRing) % 8]++ }
                seaOctant = perOctant.indices.maxBy { perOctant[it] }
            }
            // Coastal places get a big body of water (a harbour or the sea), inland ones lakes
            val water = if (seaOctant >= 0) (14 + seaShare * 45).toInt().coerceIn(18, 40) else 12
            val land = elevations.filterIndexed { i, _ -> !sea[i] }
            val spread = if (land.isEmpty()) 0.0 else land.max() - land.min()
            val relief = when { spread < 40 -> 0; spread < 160 -> 1; spread < 450 -> 2; else -> 3 }
            val absLat = kotlin.math.abs(lat)
            val cc = country?.uppercase()
            val flora = when {
                cc == "NZ" -> Flora.NZ
                absLat < 23.5 -> Flora.TROPICAL
                absLat > 56 || cc in setOf("NO", "SE", "FI", "IS") -> Flora.BOREAL
                else -> Flora.TEMPERATE
            }
            val houses = when {
                cc == "NZ" || cc == "AU" -> HouseStyle.NZ
                cc == "US" || cc == "CA" -> HouseStyle.US
                cc in setOf("NO", "SE", "FI", "DK", "IS") -> HouseStyle.NORDIC
                absLat < 23.5 -> HouseStyle.TROPICAL
                else -> HouseStyle.EURO
            }
            val woods = when (flora) { Flora.BOREAL -> 3; Flora.NZ -> 2; Flora.TROPICAL -> 2; else -> 1 + (relief + 1) / 2 }
            return Region(water, seaOctant, relief, woods.coerceIn(0, 3), flora, houses)
        }
    }
}
