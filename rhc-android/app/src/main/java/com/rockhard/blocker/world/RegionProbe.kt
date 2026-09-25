package com.rockhard.blocker.world

import android.content.SharedPreferences
import com.rockhard.blocker.world.sim.Region
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Works out what the real place around the player is like for the 3D Wilds
 * (Region: sea, hills, trees, houses) from one Open-Meteo elevation request
 * (no key, the same service WeatherEngine uses): 36 points in rings around
 * the player, where the sea reads as exactly 0 m. The result is kept in
 * prefs, so the Wilds still look like home offline.
 */
object RegionProbe {
    private const val KEY = "WORLD_REGION"
    private const val PER_RING = 12
    private val RINGS_KM = doubleArrayOf(2.5, 6.0, 12.0)

    /** Looks up and stores the region around [lat], [lon]. Blocking: call it off the UI thread. */
    fun probe(prefs: SharedPreferences, lat: Double, lon: Double, country: String?) {
        try {
            val lats = mutableListOf<String>(); val lons = mutableListOf<String>()
            val kmPerLon = 111.0 * cos(lat * PI / 180).coerceAtLeast(0.05)
            for (r in RINGS_KM) for (i in 0 until PER_RING) {
                val a = i * 2 * PI / PER_RING // north first, clockwise
                lats += (Math.round((lat + r * cos(a) / 111.0) * 1e4) / 1e4).toString()
                lons += (Math.round((lon + r * sin(a) / kmPerLon) * 1e4) / 1e4).toString()
            }
            val url = URL("https://api.open-meteo.com/v1/elevation?latitude=${lats.joinToString(",")}&longitude=${lons.joinToString(",")}")
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.connectTimeout = 4000; conn.readTimeout = 4000
            val arr = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).getJSONArray("elevation")
            val elevations = List(arr.length()) { arr.optDouble(it, 1.0) }
            if (elevations.size != RINGS_KM.size * PER_RING) return
            prefs.edit().putString(KEY, Region.fromSamples(elevations, PER_RING, lat, country).encode()).apply()
        } catch (e: Exception) {
            // keep the last known region
        }
    }

    /** The last region looked up, or the default (temperate, inland, no houses). */
    fun load(prefs: SharedPreferences): Region = Region.decode(prefs.getString(KEY, null)) ?: Region.DEFAULT
}
