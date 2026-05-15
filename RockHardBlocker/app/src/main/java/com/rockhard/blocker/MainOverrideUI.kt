package com.rockhard.blocker

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

internal fun MainActivity.setupSystemOverrideUI() {
    val activity = this
    val btnToggleOverride = findViewById<Button>(R.id.btnToggleOverride)
    val llOverrideDetails = findViewById<LinearLayout>(R.id.llOverrideDetails)
    val seekOverride = findViewById<SeekBar>(R.id.seekOverride)
    val btnSchedPause = findViewById<Button>(R.id.btnSchedPause)
    val btnSchedUninstall = findViewById<Button>(R.id.btnSchedUninstall)
    val tvOverrideStatus = findViewById<TextView>(R.id.tvOverrideStatus)

    btnToggleOverride.setOnClickListener {
        if (llOverrideDetails.visibility == View.GONE) {
            llOverrideDetails.visibility = View.VISIBLE
            // FIX 1: Automatically scroll down so the user actually sees the menu open
            btnToggleOverride.postDelayed({
                val scrollView = btnToggleOverride.parent.parent as? ScrollView
                scrollView?.fullScroll(View.FOCUS_DOWN)
            }, 100)
        } else {
            llOverrideDetails.visibility = View.GONE
        }
    }

    seekOverride.progress = prefs.getInt("OVERRIDE_MIN_PROGRESS", 0)
    seekOverride.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser && progress < prefs.getInt("OVERRIDE_MIN_PROGRESS", 0)) {
                seekBar?.progress = prefs.getInt("OVERRIDE_MIN_PROGRESS", 0)
            }
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
        override fun onStopTrackingTouch(seekBar: SeekBar?) {
            prefs.edit().putInt("OVERRIDE_MIN_PROGRESS", seekBar?.progress ?: 0).apply()
        }
    })

    fun updateOverrideUI() {
        val unlockTime = prefs.getLong("OVERRIDE_UNLOCK_TIME", 0L)
        val unlockType = prefs.getString("OVERRIDE_TYPE", "")
        val now = System.currentTimeMillis()

        if (unlockTime > now) {
            val sdf = java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault())
            tvOverrideStatus.text = "$unlockType unlocks on: ${sdf.format(java.util.Date(unlockTime))}"
            tvOverrideStatus.setTextColor(Color.parseColor("#FF9800"))
            seekOverride.isEnabled = false
            btnSchedPause.isEnabled = false
            btnSchedUninstall.isEnabled = false
            btnSchedPause.text = "LOCKED"
            btnSchedUninstall.text = "LOCKED"
        } else if (unlockTime > 0 && now >= unlockTime) {
            // FIX 2: Timer finished! Enable the correct execution button.
            tvOverrideStatus.text = "SYSTEM UNLOCKED! You may proceed with $unlockType."
            tvOverrideStatus.setTextColor(Color.parseColor("#4CAF50"))
            seekOverride.isEnabled = false
            
            if (unlockType == "PAUSE") {
                btnSchedPause.isEnabled = true
                btnSchedPause.text = "EXECUTE PAUSE"
                btnSchedUninstall.isEnabled = false
                btnSchedUninstall.text = "SCHED UNINSTALL"
            } else if (unlockType == "UNINSTALL") {
                btnSchedUninstall.isEnabled = true
                btnSchedUninstall.text = "EXECUTE UNINSTALL"
                btnSchedPause.isEnabled = false
                btnSchedPause.text = "SCHED PAUSE"
            }
        } else {
            tvOverrideStatus.text = "Select delay before unlock:"
            tvOverrideStatus.setTextColor(Color.WHITE)
            seekOverride.isEnabled = true
            btnSchedPause.isEnabled = true
            btnSchedUninstall.isEnabled = true
            btnSchedPause.text = "SCHED PAUSE"
            btnSchedUninstall.text = "SCHED UNINSTALL"
        }
    }

    updateOverrideUI()

    fun scheduleOverride(type: String) {
        val days = when(seekOverride.progress) { 0 -> 3; 1 -> 5; 2 -> 7; 3 -> 14; else -> 3 }
        val unlockTime = System.currentTimeMillis() + (days * 24 * 60 * 60 * 1000L)
        prefs.edit().putLong("OVERRIDE_UNLOCK_TIME", unlockTime).putString("OVERRIDE_TYPE", type).apply()
        updateOverrideUI()
        Toast.makeText(activity, "$type scheduled in $days days.", Toast.LENGTH_LONG).show()
    }

    btnSchedPause.setOnClickListener { 
        val unlockTime = prefs.getLong("OVERRIDE_UNLOCK_TIME", 0L)
        val unlockType = prefs.getString("OVERRIDE_TYPE", "")
        if (unlockTime > 0 && System.currentTimeMillis() >= unlockTime && unlockType == "PAUSE") {
            // EXECUTE PAUSE: Give 2 hours of God Mode
            prefs.edit().putLong("ALLOW_SETTINGS_UNTIL", System.currentTimeMillis() + (2 * 60 * 60 * 1000L))
                 .putLong("OVERRIDE_UNLOCK_TIME", 0L) // Reset the override
                 .putString("OVERRIDE_TYPE", "")
                 .apply()
            Toast.makeText(activity, "Shield Paused for 2 Hours. Device Unlocked.", Toast.LENGTH_LONG).show()
            updateOverrideUI()
        } else {
            scheduleOverride("PAUSE") 
        }
    }
    
    btnSchedUninstall.setOnClickListener { 
        val unlockTime = prefs.getLong("OVERRIDE_UNLOCK_TIME", 0L)
        val unlockType = prefs.getString("OVERRIDE_TYPE", "")
        if (unlockTime > 0 && System.currentTimeMillis() >= unlockTime && unlockType == "UNINSTALL") {
            // FIX 3: EXECUTE UNINSTALL
            try {
                // Unbind Device Admin so it can actually be uninstalled
                val dpm = activity.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val compName = ComponentName(activity, AdminReceiver::class.java)
                if (dpm.isAdminActive(compName)) {
                    dpm.removeActiveAdmin(compName)
                }
                // Launch Android's standard package removal intent
                val intent = Intent(Intent.ACTION_DELETE)
                intent.data = Uri.parse("package:${activity.packageName}")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                activity.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(activity, "Uninstall failed to launch.", Toast.LENGTH_SHORT).show()
            }
        } else {
            scheduleOverride("UNINSTALL") 
        }
    }
}