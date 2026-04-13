package com.rockhard.blocker

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object WeatherEngine {
    fun fetchSilent(
        onSuccess: (city: String, weather: String, icon: String, terrain: String, debugStr: String) -> Unit, 
        onFail: () -> Unit
    ) {
        Thread {
            try {
                // 1. IP Geolocation (Using HTTPS and User-Agent to bypass security blocks!)
                val ipUrl = URL("https://get.geojs.io/v1/ip/geo.json").openConnection() as HttpURLConnection
                ipUrl.setRequestProperty("User-Agent", "Mozilla/5.0")
                ipUrl.connectTimeout = 3000
                val ipRes = ipUrl.inputStream.bufferedReader().readText()
                val ipJson = JSONObject(ipRes)
                
                val city = ipJson.getString("city")
                val lat = ipJson.getDouble("latitude")
                val lon = ipJson.getDouble("longitude")

                // 2. Open-Meteo Weather API
                val weatherUrl = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=weather_code,relative_humidity_2m").openConnection() as HttpURLConnection
                weatherUrl.setRequestProperty("User-Agent", "Mozilla/5.0")
                weatherUrl.connectTimeout = 3000
                val weatherRes = weatherUrl.inputStream.bufferedReader().readText()
                val wJson = JSONObject(weatherRes)
                
                val currentObj = wJson.getJSONObject("current")
                val wCode = currentObj.getInt("weather_code")
                val humidity = currentObj.getInt("relative_humidity_2m")
                val elevation = wJson.getDouble("elevation")

                // 3. WMO Weather Code Translation
                val (weatherText, icon) = when (wCode) { 
                    0, 1 -> Pair("Clear", "☀️")
                    2, 3 -> Pair("Cloudy", "☁️")
                    45, 48 -> Pair("Fog", "🌫️")
                    51, 53, 55, 61, 63, 65 -> Pair("Rain", "🌧️")
                    71, 73, 75, 77 -> Pair("Snow", "❄️")
                    95, 96, 99 -> Pair("Storm", "⚡")
                    else -> Pair("Clear", "☀️") 
                }
                
                // 4. Terrain Estimation Math
                var terrain = "Plains / Grasslands"
                if (elevation < 30 && humidity > 70) terrain = "Coastal, humid lowlands"
                else if (elevation < 30) terrain = "Coastal, flat terrain"
                else if (elevation > 500) terrain = "Mountainous, rocky terrain"
                else if (elevation > 150) terrain = "Hilly, elevated terrain"

                // 5. Package for Debug Console
                val debugStr = "IP City: $city\nLat/Lon: $lat, $lon\nElevation: ${elevation}m\nHumidity: $humidity%\nWeather: $weatherText\nTerrain: $terrain"
                
                onSuccess(city, weatherText, icon, terrain, debugStr)
            } catch (e: Exception) {
                onFail()
            }
        }.start()
    }
}