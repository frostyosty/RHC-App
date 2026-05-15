package com.rockhard.blocker

import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

internal fun MainActivity.setupMomentumUI() {
    val activity = this
    val btnTabEarned = findViewById<Button>(R.id.btnTabEarned)
    val btnTabSpent = findViewById<Button>(R.id.btnTabSpent)
    val llEarned = findViewById<LinearLayout>(R.id.llEarnedList)
    val llSpent = findViewById<LinearLayout>(R.id.llSpentList)
    val btnTabLeaders = findViewById<Button>(R.id.btnTabLeaders)
    val llLeaderList = findViewById<LinearLayout>(R.id.llLeaderList)

    btnTabEarned.setOnClickListener {
        llEarned.visibility = View.VISIBLE; llSpent.visibility = View.GONE; llLeaderList.visibility = View.GONE
        btnTabEarned.setBackgroundResource(R.drawable.bg_btn_accent); btnTabSpent.setBackgroundResource(R.drawable.bg_btn_standard); btnTabLeaders.setBackgroundResource(R.drawable.bg_btn_standard)
    }
    btnTabLeaders.setOnClickListener {
        llEarned.visibility = View.GONE; llSpent.visibility = View.GONE; llLeaderList.visibility = View.VISIBLE
        btnTabEarned.setBackgroundResource(R.drawable.bg_btn_standard); btnTabSpent.setBackgroundResource(R.drawable.bg_btn_standard); btnTabLeaders.setBackgroundResource(R.drawable.bg_btn_accent)
        loadLeaderboard()
    }
    btnTabSpent.setOnClickListener {
        llEarned.visibility = View.GONE; llSpent.visibility = View.VISIBLE; llLeaderList.visibility = View.GONE
        btnTabEarned.setBackgroundResource(R.drawable.bg_btn_standard); btnTabSpent.setBackgroundResource(R.drawable.bg_btn_accent); btnTabLeaders.setBackgroundResource(R.drawable.bg_btn_standard)
    }
    populateSpendingTasks(); updateMomentumUI()
}

internal fun MainActivity.updateMomentumUI() {
    val currentMins = MomentumEngine.calculateCurrentMomentum(prefs)
    findViewById<TextView>(R.id.tvMomentumText).text = "$currentMins MINS AVAILABLE"
    val bar = findViewById<ProgressBar>(R.id.barMomentum)
    bar.progress = Math.min(100, (currentMins.toFloat() / 120f * 100).toInt())
    bar.progressDrawable.setTint(if (currentMins < 15) Color.parseColor("#F44336") else Color.parseColor("#4CAF50"))
    renderEarnedList()
}

internal fun MainActivity.loadLeaderboard() {
    val activity = this
    val llLeader = findViewById<LinearLayout>(R.id.llLeaderList)
    llLeader.removeAllViews()
    llLeader.addView(TextView(activity).apply { text = "Loading Leaders..."; setTextColor(Color.GRAY) })
    LeaderboardEngine.fetchLeaderboardAsync { leaders ->
        mainHandler.post {
            llLeader.removeAllViews()
            if (leaders.isEmpty()) llLeader.addView(TextView(activity).apply { text = "Leaderboard offline or empty."; setTextColor(Color.GRAY) })
            else leaders.forEachIndexed { index, leader -> llLeader.addView(createRow("#${index+1} ${leader.first}", "${leader.second} Mins Transformed", if(index==0) "#FFD700" else "#4CAF50")) }
        }
    }
}

internal fun MainActivity.renderEarnedList() {
    val activity = this
    val llEarned = findViewById<LinearLayout>(R.id.llEarnedList)
    llEarned.removeAllViews()
    val earnedStr = prefs.getString("MOMENTUM_EARNED_TODAY", "") ?: ""
    if (earnedStr.isEmpty() && prefs.getInt("SLEEP_MOMENTUM_BONUS", 0) == 0) {
        llEarned.addView(TextView(activity).apply { text = "No momentum reclaimed today."; setTextColor(Color.GRAY) })
        return
    }
    val sleepBonus = prefs.getInt("SLEEP_MOMENTUM_BONUS", 0)
    if (sleepBonus > 0) llEarned.addView(createRow("🌙 Sleep Guard Reclaimed", "+$sleepBonus mins", "#00BCD4"))
    earnedStr.split(",").reversed().forEach { entry ->
        val parts = entry.split("|")
        if (parts.size == 3) llEarned.addView(createRow("🛡️ Overcame: ${parts[0]}", "+${parts[1]} mins\n@ ${java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(parts[2].toLong()))}", "#4CAF50"))
    }
}

internal fun MainActivity.populateSpendingTasks() {
    val activity = this
    val llSpent = findViewById<LinearLayout>(R.id.llSpentList)
    llSpent.removeAllViews()
    val isMale = BuildConfig.FLAVOR.lowercase().contains("male")
    val tasks = if (isMale) listOf(Pair("🪓 Chop Wood / Yard Work", 30), Pair("🏋️ Workout / Lift", 45), Pair("📚 Read a Book", 20), Pair("🔧 Fix / Build Something", 30), Pair("🏃‍♂️ Go for a Run", 30), Pair("🥩 Cook a Proper Meal", 40), Pair("🎸 Practice an Instrument", 30), Pair("🧹 Deep Clean a Room", 20), Pair("🚶‍♂️ Walk in Nature", 30), Pair("🧘‍♂️ Meditate / Pray", 15), Pair("🛠️ Organize Workspace", 15), Pair("✍️ Write / Plan Goals", 20))
    else listOf(Pair("🧘‍♀️ Yoga / Stretch", 20), Pair("🧴 Skincare Routine", 15), Pair("📚 Read a Book", 20), Pair("📝 Journal / Plan", 15), Pair("🏃‍♀️ Go for a Run / Walk", 30), Pair("🥗 Cook a Healthy Meal", 40), Pair("🎨 Create Art / Craft", 30), Pair("🧹 Declutter a Room", 20), Pair("🛁 Take a Relaxing Bath", 30), Pair("☕ Call a Friend / Family", 20), Pair("💅 Grooming / Nails", 20), Pair("🧘‍♀️ Meditate / Pray", 15))

    tasks.forEach { task ->
        llSpent.addView(Button(activity).apply {
            text = "${task.first}\nSpend ${task.second} mins"
            setBackgroundResource(R.drawable.bg_card); setTextColor(Color.WHITE)
            minHeight = 200; setPadding(40, 50, 40, 50)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 24, 0, 0) }
            setOnClickListener {
                if (MomentumEngine.spendMomentum(prefs, task.first, task.second)) {
                    Toast.makeText(activity, "Momentum Spent!", Toast.LENGTH_SHORT).show()
                    LeaderboardEngine.submitScoreAsync(prefs, task.second)
                    updateMomentumUI()
                } else Toast.makeText(activity, "Not enough Momentum. Block urges first!", Toast.LENGTH_SHORT).show()
            }
        })
    }
}

internal fun MainActivity.createRow(title: String, subtitle: String, colorHex: String): View {
    val activity = this
    return LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16)
        addView(TextView(activity).apply { text = title; setTextColor(Color.WHITE); textSize = 16f })
        addView(TextView(activity).apply { text = subtitle; setTextColor(Color.parseColor(colorHex)); textSize = 14f })
    }
}
