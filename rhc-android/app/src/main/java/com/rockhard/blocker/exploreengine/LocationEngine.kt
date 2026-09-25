package com.rockhard.blocker

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Where the phone really is, for the weather, the Wilds' region and the rescue
 * events. Without location permission the weather falls back to IP geolocation,
 * which is often a city away (NZ connections tend to read as Auckland).
 */
object LocationEngine {
    const val REQUEST_CODE = 4217
    private const val ASKED = "LOCATION_ASKED"      // the game has offered to use your location
    private const val SAVED = "LAST_LOCATION"       // "lat,lon,time" of the last fix, for when location is switched off
    private const val FRESH_MS = 30 * 60_000L       // a fix this recent is good enough for the weather
    private const val FIX_WAIT_MS = 8_000L          // how long the weather waits for a new fix
    private const val PLACES_MS = 2 * 60_000L       // how often the suburb lookups run again
    private const val PROMPT_UNTIL = "ALLOW_PERMISSION_PROMPT_UNTIL" // read by ShieldRuleEngine
    private const val PROMPT_MS = 60_000L           // how long the Guardian lets Android's prompt through at most

    private val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun prefs(context: Context) = context.getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE)

    /** Precise or approximate: an approximate fix is plenty for the weather and the suburb. */
    fun hasPermission(context: Context) = PERMISSIONS.any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    // --- Asking -----------------------------------------------------------------

    /** The game offers once, the first time it opens. Settings > "📍 Use My Location" asks again. */
    fun shouldOffer(context: Context) = !hasPermission(context) && !prefs(context).getBoolean(ASKED, false)

    /**
     * Says why, then shows Android's prompt, whose answer arrives in the activity's
     * onRequestPermissionsResult. [onDeclined] runs instead if the explanation is dismissed.
     */
    fun offer(activity: Activity, onDeclined: () -> Unit) {
        prefs(activity).edit().putBoolean(ASKED, true).apply()
        var sharing = false
        DialogUtils.showCustomDialog(
            activity, "📍 Real Weather",
            "The Wilds copy the weather and the land around you. Share your location so it's really your town. " +
                "Without it the app guesses from your internet connection, which is often a city away.",
            true, "SHARE LOCATION", { sharing = true; requestPermission(activity) },
        ) { _, dialog -> dialog.setOnDismissListener { if (!sharing) onDeclined() } }
    }

    private var askedAt = 0L

    /**
     * Shows Android's location prompt. It comes from the permission controller, which
     * the Guardian blocks as anti-tamper, so ShieldRuleEngine lets that package (and
     * only that one) through until the prompt is answered. The activity's
     * onRequestPermissionsResult must call [onPermissionResult].
     */
    fun requestPermission(activity: Activity) {
        prefs(activity).edit().putLong(PROMPT_UNTIL, System.currentTimeMillis() + PROMPT_MS).apply()
        askedAt = SystemClock.elapsedRealtime()
        activity.requestPermissions(PERMISSIONS, REQUEST_CODE)
    }

    /** Blocks the permission controller again and says whether location is allowed now. */
    fun onPermissionResult(activity: Activity): Boolean {
        prefs(activity).edit().putLong(PROMPT_UNTIL, 0L).apply()
        val granted = hasPermission(activity)
        // After two "Don't allow"s Android answers at once without showing anything
        if (!granted && SystemClock.elapsedRealtime() - askedAt < 500) {
            Toast.makeText(activity, "Android won't show the location prompt again. You can allow it for this app in Android Settings.", Toast.LENGTH_LONG).show()
        }
        return granted
    }

    // --- Finding --------------------------------------------------------------------

    /**
     * Where the phone is, for the weather: a recent fix from any provider, else a new
     * one (waits up to FIX_WAIT_MS), else the newest old one, else the last one saved.
     * Null without permission. Blocking: call it off the UI thread.
     */
    fun findLocation(context: Context): Location? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val known = newestKnown(lm)
        val fix = known?.takeIf { System.currentTimeMillis() - it.time < FRESH_MS } ?: freshFix(lm) ?: known
            ?: return saved(context)
        prefs(context).edit().putString(SAVED, "${fix.latitude},${fix.longitude},${fix.time}").apply()
        return fix
    }

    /** The newest fix the phone already has, or the last one saved. Doesn't wait. */
    fun getCurrentLocation(context: Context): Pair<Double, Double>? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = newestKnown(lm) ?: saved(context) ?: return null
        return Pair(loc.latitude, loc.longitude)
    }

    /** For the weather's debug line: "network fix, 3 min old". */
    fun describe(fix: Location): String {
        val min = (System.currentTimeMillis() - fix.time).coerceAtLeast(0) / 60_000
        val age = when {
            min < 1 -> "just now"
            min < 120 -> "$min min old"
            min < 48 * 60 -> "${min / 60} h old"
            else -> "${min / (24 * 60)} days old"
        }
        return "${fix.provider} fix, $age"
    }

    // Callers check hasPermission; one revoked since then throws SecurityException, caught here
    @SuppressLint("MissingPermission")
    private fun newestKnown(lm: LocationManager): Location? =
        lm.getProviders(true).mapNotNull { p -> try { lm.getLastKnownLocation(p) } catch (e: Exception) { null } }.maxByOrNull { it.time }

    /** Asks the best enabled provider for a new fix and waits for it. */
    @SuppressLint("MissingPermission")
    private fun freshFix(lm: LocationManager): Location? {
        val enabled = lm.getProviders(true)
        val fused = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) LocationManager.FUSED_PROVIDER else null
        val provider = listOfNotNull(fused, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull { it in enabled } ?: return null
        val done = CountDownLatch(1)
        var fix: Location? = null
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) { fix = location; done.countDown() }
            // Before API 30 these have no default bodies, so they must be here
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        // Not getCurrentLocation: on Android 11 its request outlives a cancel, so a provider
        // that never answers would be kept running. removeUpdates always ends this one
        return try {
            lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            done.await(FIX_WAIT_MS, TimeUnit.MILLISECONDS)
            fix
        } catch (e: Exception) {
            null
        } finally {
            lm.removeUpdates(listener)
        }
    }

    private fun saved(context: Context): Location? {
        val p = prefs(context).getString(SAVED, null)?.split(",") ?: return null
        return try {
            Location("saved").apply { latitude = p[0].toDouble(); longitude = p[1].toDouble(); time = p[2].toLong() }
        } catch (e: Exception) { null }
    }

    // --- Suburbs ------------------------------------------------------------------------

    // The suburb you're in, and one a few km off (where a poacher takes a netbeast). The
    // Geocoder is a network call, so they're looked up on their own thread and cached.
    @Volatile private var suburb = ""
    @Volatile private var nearby = ""
    private var placesAt = 0L

    /** The suburb you're in, or "" until known. Doesn't wait, so it's safe on the UI thread. */
    fun currentSuburb(context: Context): String { refreshPlaces(context); return suburb }

    /** A suburb a few km from you, or "" until known. Doesn't wait, so it's safe on the UI thread. */
    fun nearbySuburb(context: Context): String { refreshPlaces(context); return nearby }

    private fun refreshPlaces(context: Context) {
        if (!hasPermission(context)) { suburb = ""; nearby = ""; placesAt = 0L; return }
        val now = SystemClock.elapsedRealtime()
        if (placesAt != 0L && now - placesAt < PLACES_MS) return
        placesAt = now
        val app = context.applicationContext
        Thread {
            val (lat, lon) = getCurrentLocation(app) ?: return@Thread
            suburb = getSuburbName(app, lat, lon)
            nearby = generateNearbySuburb(app, lat, lon)
        }.start()
    }

    fun getSuburbName(context: Context, lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                address.subLocality ?: address.locality ?: "The Outskirts"
            } else "The Outskirts"
        } catch (e: Exception) { "The Outskirts" }
    }

    /** ISO country code ("NZ") for a point, or null if the geocoder can't tell. */
    fun getCountryCode(context: Context, lat: Double, lon: Double): String? {
        return try {
            Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()?.countryCode
        } catch (e: Exception) { null }
    }

    fun generateNearbySuburb(context: Context, currentLat: Double, currentLon: Double): String {
        // Offsets by approx 2-4km
        val latOffset = Random.nextDouble(-0.04, 0.04)
        val lonOffset = Random.nextDouble(-0.04, 0.04)
        return getSuburbName(context, currentLat + latOffset, currentLon + lonOffset)
    }
}
