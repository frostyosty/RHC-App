package com.rockhard.blocker

import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/**
 * Tapping the Aether timer: a slider for your own daily Aether. It's one-way, like the
 * System Override slider: it drags left (less Aether) and never back to the right.
 */
internal fun GameActivity.showAetherLimit() {
    val min = AetherEngine.MIN_DAILY_MINUTES
    val max = AetherEngine.MAX_DAILY_MINUTES
    DialogUtils.showCustomDialog(this, "Daily Aether", "Drag left to give yourself less Aether each day, down to $min minutes. It can't be raised again.", false, "CLOSE", null) { content, _ ->
        var limit = AetherEngine.dailyMinutes(prefs)
        val tvLimit = TextView(this).apply {
            text = "$limit minutes a day"
            setTextColor(Color.parseColor("#00BCD4"))
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        val seek = SeekBar(this).apply { this.max = max - min; progress = limit - min }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && progress > limit - min) seekBar?.progress = limit - min
                tvLimit.text = "${(seekBar?.progress ?: progress) + min} minutes a day"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val chosen = (seekBar?.progress ?: return) + min
                if (chosen >= limit) return
                limit = chosen
                AetherEngine.lowerDailyMinutes(prefs, limit)
                // today's Aether comes down to the new limit too
                if (aetherSeconds > limit * 60) {
                    aetherSeconds = limit * 60
                    AetherEngine.save(prefs, aetherDay, aetherSeconds)
                }
            }
        })
        val labels = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("${min}m" to Gravity.START, "${max}m" to Gravity.END).forEach { (label, side) ->
            labels.addView(TextView(this).apply { text = label; setTextColor(Color.parseColor("#888888")); textSize = 11f; gravity = side }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        content.addView(tvLimit)
        content.addView(seek)
        content.addView(labels)
    }
}
