package com.rockhard.blocker

import android.content.SharedPreferences
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.concurrent.thread

object LeaderboardEngine {

    private const val SUPABASE_URL = "https://oannlpewujcnmbzzvklu.supabase.co/rest/v1/momentum_leaders"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im9hbm5scGV3dWpjbm1ienp2a2x1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDYxMzQwMDQsImV4cCI6MjA2MTcxMDAwNH0.2hKaOLPYsRh6p1CQFfLYqpTo2Cz1WuQa4Y5n0AIoNPE" 

    fun getOrCreateUserId(prefs: SharedPreferences): String {
        var id = prefs.getString("LEADERBOARD_ID", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString("LEADERBOARD_ID", id).apply()
        }
        return id
    }

    fun getDisplayName(prefs: SharedPreferences): String {
        var name = prefs.getString("LEADERBOARD_NAME", null)
        if (name == null) {
            val isMale = BuildConfig.FLAVOR.lowercase().contains("male")
            val adjectives = listOf("Iron", "Stoic", "Silent", "Fierce", "Noble", "Steadfast")
            val nouns = if (isMale) listOf("Spartan", "Wolf", "Titan", "Bear") else listOf("Valkyrie", "Lioness", "Athena", "Owl")
            name = "${adjectives.random()} ${nouns.random()} ${kotlin.random.Random.nextInt(100, 999)}"
            prefs.edit().putString("LEADERBOARD_NAME", name).apply()
        }
        return name
    }

    fun submitScoreAsync(prefs: SharedPreferences, newMinsSpent: Int) {
        val currentTotal = prefs.getInt("TOTAL_LIFETIME_MOMENTUM_SPENT", 0) + newMinsSpent
        prefs.edit().putInt("TOTAL_LIFETIME_MOMENTUM_SPENT", currentTotal).apply()

        thread {
            try {
                val url = URL("$SUPABASE_URL?on_conflict=device_id")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
                connection.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Prefer", "resolution=merge-duplicates")
                connection.doOutput = true

                val jsonPayload = "{\"device_id\": \"${getOrCreateUserId(prefs)}\", \"display_name\": \"${getDisplayName(prefs)}\", \"total_spent_mins\": $currentTotal}"
                OutputStreamWriter(connection.outputStream).use { it.write(jsonPayload) }
                connection.responseCode
            } catch (e: Exception) {}
        }
    }

    fun fetchLeaderboardAsync(onResult: (List<Pair<String, Int>>) -> Unit) {
        thread {
            try {
                val url = URL("$SUPABASE_URL?select=display_name,total_spent_mins&order=total_spent_mins.desc&limit=10")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
                connection.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
                
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().readText()
                    val list = mutableListOf<Pair<String, Int>>()
                    val matches = "\"display_name\":\"([^\"]+)\",\"total_spent_mins\":(\\d+)".toRegex().findAll(response)
                    for (match in matches) { list.add(Pair(match.groupValues[1], match.groupValues[2].toInt())) }
                    onResult(list)
                } else { onResult(emptyList()) }
            } catch (e: Exception) { onResult(emptyList()) }
        }
    }
}
