package com.rockhard.blocker

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*

class MomentumActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var tvMomentum: TextView
    private lateinit var barMomentum: ProgressBar
    private lateinit var llEarned: LinearLayout
    private lateinit var llSpent: LinearLayout
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            updateUI()
            mainHandler.postDelayed(this, 10000) // Update every 10 secs
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_momentum)
        prefs = getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE)
        MomentumEngine.resetDailyIfNeeded(prefs)

        tvMomentum = findViewById(R.id.tvMomentumText)
        barMomentum = findViewById(R.id.barMomentum)
        llEarned = findViewById(R.id.llEarnedList)
        llSpent = findViewById(R.id.llSpentList)

        val btnTabEarned = findViewById<Button>(R.id.btnTabEarned)
        val btnTabSpent = findViewById<Button>(R.id.btnTabSpent)
        val svEarned = findViewById<ScrollView>(R.id.svEarned)
        val svSpent = findViewById<ScrollView>(R.id.svSpent)

        btnTabEarned.setOnClickListener {
            svEarned.visibility = View.VISIBLE; svSpent.visibility = View.GONE
            btnTabEarned.setBackgroundResource(R.drawable.bg_btn_accent)
            btnTabSpent.setBackgroundResource(R.drawable.bg_btn_standard)
        }
        btnTabSpent.setOnClickListener {
            svEarned.visibility = View.GONE; svSpent.visibility = View.VISIBLE
            btnTabEarned.setBackgroundResource(R.drawable.bg_btn_standard)
            btnTabSpent.setBackgroundResource(R.drawable.bg_btn_accent)
        }

        findViewById<Button>(R.id.btnExitMomentum).setOnClickListener { finish() }

        populateSpendingTasks()
        updateUI()
        mainHandler.postDelayed(tickRunnable, 10000)
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(tickRunnable)
    }

    private fun updateUI() {
        val currentMins = MomentumEngine.calculateCurrentMomentum(prefs)
        tvMomentum.text = "$currentMins MINS AVAILABLE"
        barMomentum.progress = Math.min(100, (currentMins.toFloat() / 120f * 100).toInt()) // Assuming 120 mins is a "full bar"

        // Red bar if low, Green if high
        if (currentMins < 15) barMomentum.progressDrawable.setTint(Color.parseColor("#F44336"))
        else barMomentum.progressDrawable.setTint(Color.parseColor("#4CAF50"))

        renderEarnedList()
    }

    private fun renderEarnedList() {
        llEarned.removeAllViews()
        val earnedStr = prefs.getString("MOMENTUM_EARNED_TODAY", "") ?: ""
        if (earnedStr.isEmpty() && prefs.getInt("SLEEP_MOMENTUM_BONUS", 0) == 0) {
            llEarned.addView(TextView(this).apply { text = "No momentum reclaimed today."; setTextColor(Color.GRAY) })
            return
        }

        val sleepBonus = prefs.getInt("SLEEP_MOMENTUM_BONUS", 0)
        if (sleepBonus > 0) {
            llEarned.addView(createRow("🌙 Sleep Guard Reclaimed", "+$sleepBonus mins", "#00BCD4"))
        }

        earnedStr.split(",").reversed().forEach { entry ->
            val parts = entry.split("|")
            if (parts.size == 3) {
                val source = parts[0]
                val mins = parts[1]
                val timeStr = java.text.SimpleDateFormat("hh:mm a").format(java.util.Date(parts[2].toLong()))
                llEarned.addView(createRow("🛡️ Overcame: $source", "+$mins mins\n@ $timeStr", "#4CAF50"))
            }
        }
    }

    private fun populateSpendingTasks() {
        llSpent.removeAllViews()
        val isMale = BuildConfig.FLAVOR.lowercase().contains("male")
        
        val tasks = if (isMale) {
            listOf(Pair("🪓 Chop Wood / Yard Work", 30), Pair("🏋️ Workout / Lift", 45), Pair("📚 Read a Book", 20), Pair("🔧 Fix Something", 30))
        } else {
            listOf(Pair("🧘‍♀️ Yoga / Stretch", 20), Pair("🧴 Skincare Routine", 15), Pair("📚 Read a Book", 20), Pair("📝 Journal / Plan", 15))
        }

        tasks.forEach { task ->
            val btn = Button(this).apply {
                text = "${task.first}\nSpend ${task.second} mins"
                setBackgroundResource(R.drawable.bg_card)
                setTextColor(Color.WHITE)
                
                // --- FIX: Force minimum height and larger padding ---
                minHeight = 200 
                setPadding(40, 50, 40, 50) 
                
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 24, 0, 0) }
                
setOnClickListener {
                    if (MomentumEngine.spendMomentum(prefs, task.first, task.second)) {
                        Toast.makeText(this@MomentumActivity, "Momentum Spent! Go do it!", Toast.LENGTH_LONG).show()
                        
                        // --- NEW: Sync to Supabase ---
                        LeaderboardEngine.submitScoreAsync(prefs, task.second)
                        
                        updateUI()
                    } else {
                        Toast.makeText(this@MomentumActivity, "Not enough Momentum. Block urges first!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            llSpent.addView(btn)
        }
    }

    private fun createRow(title: String, subtitle: String, colorHex: String): View {
        val ll = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16) }
        ll.addView(TextView(this).apply { text = title; setTextColor(Color.WHITE); textSize = 16f })
        ll.addView(TextView(this).apply { text = subtitle; setTextColor(Color.parseColor(colorHex)); textSize = 14f })
        return ll
    }
}