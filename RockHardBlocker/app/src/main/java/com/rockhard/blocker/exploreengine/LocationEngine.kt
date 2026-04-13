package com.rockhard.blocker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import java.util.Locale
import kotlin.random.Random

object LocationEngine {
    fun hasGPSPermission(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun getCurrentLocation(context: Context): Pair<Double, Double>? {
        if (!hasGPSPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!isGpsEnabled && !isNetworkEnabled) return null
        try {
            val location = if (isGpsEnabled) lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null ?: if (isNetworkEnabled) lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) else null
            if (location != null) return Pair(location.latitude, location.longitude)
        } catch (e: SecurityException) { return null }
        return null
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

    fun generateNearbySuburb(context: Context, currentLat: Double, currentLon: Double): String {
        // Offsets by approx 2-4km
        val latOffset = Random.nextDouble(-0.04, 0.04)
        val lonOffset = Random.nextDouble(-0.04, 0.04)
        return getSuburbName(context, currentLat + latOffset, currentLon + lonOffset)
    }
}