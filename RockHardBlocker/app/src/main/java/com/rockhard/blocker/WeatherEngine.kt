package com.rockhard.blocker

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object WeatherEngine {
    fun fetchSilent(onSuccess: (city: String, weather: String, icon: String, terrain: String, debugStr: String) -> Unit, onFail: () -> Unit) {
        Thread {
            try {
                val ipUrl = URL("http://ip-api.com/json/").openConnection() as HttpURLConnection
                ipUrl.connectTimeout = 3000
                val ipJson = JSONObject(ipUrl.inputStream.bufferedReader().readText())
                val city = ipJson.getString("city")
                val lat = ipJson.getDouble("lat")
                val lon = ipJson.getDouble("lon")
                
                val weatherUrl = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=weather_code,relative_humidity_2m").openConnection() as HttpURLConnection
                weatherUrl.connectTimeout = 3000
                val wJson = JSONObject(weatherUrl.inputStream.bufferedReader().readText())
                val currentObj = wJson.getJSONObject("current")
                val wCode = currentObj.getInt("weather_code")
                val humidity = currentObj.getInt("relative_humidity_2m")
                val elevation = wJson.getDouble("elevation")

                val (weatherText, icon) = when (wCode) { 
                    0, 1 -> Pair("Clear", "☀️")
                    2, 3 -> Pair("Cloudy", "☁️")
                    45, 48 -> Pair("Fog", "🌫️")
                    51, 53, 55, 61, 63, 65 -> Pair("Rain", "🌧️")
                    71, 73, 75, 77 -> Pair("Snow", "❄️")
                    95, 96, 99 -> Pair("Storm", "⚡")
                    else -> Pair("Clear", "☀️") 
                }
                
                var terrain = "Plains / Grasslands"
                if (elevation < 30 && humidity > 70) terrain = "Coastal, humid lowlands"
                else if (elevation < 30) terrain = "Coastal, flat terrain"
                else if (elevation > 500) terrain = "Mountainous, rocky terrain"
                else if (elevation > 150) terrain = "Hilly, elevated terrain"

                val debugStr = "IP City: $city\nLat/Lon: $lat, $lon\nElevation: ${elevation}m\nHumidity: $humidity%\nWeather: $weatherText\nTerrain: $terrain"
                
                onSuccess(city, weatherText, icon, terrain, debugStr)
            } catch (e: Exception) {
                onFail()
            }
        }.start()
    }
}
