package com.rockhard.blocker

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException

class MainActivity : Activity() {

    private lateinit var dpm: DevicePolicyManager
    private lateinit var compName: ComponentName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        compName = ComponentName(this, AdminReceiver::class.java)

        findViewById<Button>(R.id.btnArmShield).setOnClickListener {
            if (!isAccessibilityServiceEnabled(this, GuardianService::class.java)) {
                Toast.makeText(this, "Step 1: Turn ON the Guardian", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return@setOnClickListener
            }
            if (!dpm.isAdminActive(compName)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Locks app.")
                startActivity(intent)
                return@setOnClickListener
            }
            Toast.makeText(this, "Final Step: Set Private DNS to -> adult-filter-dns.cleanbrowsing.org", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }

        findViewById<Button>(R.id.btnHumiliation).setOnClickListener { startActivity(Intent(this, GameActivity::class.java)) }
        findViewById<Button>(R.id.btnTrapBrick).setOnClickListener { startActivity(Intent(this, TrapActivity::class.java)) }

        findViewById<Button>(R.id.btnTestFilter).setOnClickListener {
            Toast.makeText(this, "Testing connections...", Toast.LENGTH_SHORT).show()
            Thread {
                val sites = listOf("https://playboy.com", "https://pornhub.com", "https://google.com")
                var report = "NETWORK FILTER REPORT:\n\n"
                for (site in sites) {
                    try {
                        val conn = URL(site).openConnection() as HttpURLConnection
                        conn.connectTimeout = 3000
                        conn.connect()
                        report += "❌ VULNERABLE: $site loaded.\n"
                    } catch (e: UnknownHostException) {
                        report += "✅ SECURE: $site blocked by DNS!\n"
                    } catch (e: Exception) {
                        report += "✅ SECURE: $site blocked.\n"
                    }
                }
                runOnUiThread {
                    AlertDialog.Builder(this).setTitle("Filter Diagnostics").setMessage(report).setPositiveButton("OK", null).show()
                }
            }.start()
        }

        // ==========================================
        // NEW: VIEW USAGE ANALYTICS
        // ==========================================
        findViewById<Button>(R.id.btnDebugUsage).setOnClickListener {
            val trackers = GuardianService.appTimeTrackers
            val defeats = GuardianService.urgesDefeatedCount
            
            var report = "Urges Defeated: $defeats\n\nActive Screen Time:\n"
            if (trackers.isEmpty()) {
                report += "(No data yet. Guardian must be running while you switch apps.)"
            } else {
                for ((appPackage, timeMs) in trackers) {
                    val seconds = timeMs / 1000
                    if (seconds > 0) {
                        report += "- $appPackage: ${seconds}s\n"
                    }
                }
            }
            
            AlertDialog.Builder(this)
                .setTitle("Guardian Tracking Data")
                .setMessage(report)
                .setPositiveButton("Cool", null)
                .show()
        }

        findViewById<Button>(R.id.btnDebugUninstall).setOnClickListener {
            GuardianService.pauseUntil = System.currentTimeMillis() + (10 * 60 * 1000)
            if (dpm.isAdminActive(compName)) dpm.removeActiveAdmin(compName)
            val intent = Intent(Intent.ACTION_DELETE)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, accessibilityService: Class<*>): Boolean {
        val expectedComponentName = ComponentName(context, accessibilityService)
        val enabledServicesSetting = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && enabledService == expectedComponentName) return true
        }
        return false
    }
}
