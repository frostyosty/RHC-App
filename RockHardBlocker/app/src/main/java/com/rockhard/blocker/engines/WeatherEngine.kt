package com.rockhard.blocker

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object WeatherEngine {
    fun fetchSilent(
        context: Context,
        onSuccess: (city: String, weather: String, icon: String, terrain: String, debugStr: String) -> Unit, 
        onFail: (reason: String) -> Unit
    ) {
        Thread {
            try {
                var lat: Double
                var lon: Double
                var city: String
                var sourceStr = "IP Geolocation"

                // 1. Try Hardware GPS First!
                val hasGps = LocationEngine.hasGPSPermission(context)
                val gpsLoc = if (hasGps) LocationEngine.getCurrentLocation(context) else null
                
                if (gpsLoc != null) {
                    lat = gpsLoc.first
                    lon = gpsLoc.second
                    city = LocationEngine.getSuburbName(context, lat, lon)
                    sourceStr = "Hardware GPS"
                } else {
                    // Fallback to IP Geolocation
                    val ipUrl = URL("https://get.geojs.io/v1/ip/geo.json").openConnection() as HttpURLConnection
                    ipUrl.setRequestProperty("User-Agent", "Mozilla/5.0")
                    ipUrl.connectTimeout = 3000
                    val ipRes = ipUrl.inputStream.bufferedReader().readText()
                    val ipJson = JSONObject(ipRes)
                    city = ipJson.getString("city")
                    lat = ipJson.getDouble("latitude")
                    lon = ipJson.getDouble("longitude")
                }

                // 2. Open-Meteo Weather API
                val meteoUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=weather_code,relative_humidity_2m"
                val weatherUrl = URL(meteoUrl).openConnection() as HttpURLConnection
                weatherUrl.setRequestProperty("User-Agent", "Mozilla/5.0")
                weatherUrl.connectTimeout = 3000
                val weatherRes = weatherUrl.inputStream.bufferedReader().readText()
                val wJson = JSONObject(weatherRes)
                
                val currentObj = wJson.getJSONObject("current")
                val wCode = currentObj.getInt("weather_code")
                val humidity = currentObj.getInt("relative_humidity_2m")
                val elevation = wJson.getDouble("elevation")

                // 3. FULL WMO Weather Code Translation
                val (weatherText, icon) = when (wCode) { 
                    0, 1 -> Pair("Clear", "☀️")
                    2, 3 -> Pair("Cloudy", "☁️")
                    45, 48 -> Pair("Fog", "🌫️")
                    // Drizzle, Rain, Freezing Rain, and Showers!
                    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> Pair("Rain", "🌧️")
                    // Snow fall and Snow showers!
                    71, 73, 75, 77, 85, 86 -> Pair("Snow", "❄️")
                    // Thunderstorms!
                    95, 96, 99 -> Pair("Storm", "⚡")
                    else -> Pair("Clear", "☀️") // Fallback
                }
                
                // 4. Terrain Estimation Math
                var terrain = "Plains / Grasslands"
                if (elevation < 30 && humidity > 70) terrain = "Coastal, humid lowlands"
                else if (elevation < 30) terrain = "Coastal, flat terrain"
                else if (elevation > 500) terrain = "Mountainous, rocky terrain"
                else if (elevation > 150) terrain = "Hilly, elevated terrain"

                val debugStr = "Loc Source: $sourceStr\nLat/Lon: $lat, $lon\nElevation: ${elevation}m | Hum: $humidity%\nWMO Code: $wCode"
                
                onSuccess(city, weatherText, icon, terrain, debugStr)
            } catch (e: Exception) {
                onFail(e.message ?: "Network Timeout / Parse Error")
            }
        }.start()
    }
}
