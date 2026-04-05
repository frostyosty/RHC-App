package com.rockhard.blocker

import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {
    private lateinit var dpm: DevicePolicyManager
    private lateinit var compName: ComponentName
    private lateinit var prefs: SharedPreferences

    private val popularApps = arrayOf(
        "YouTube", "TikTok", "Instagram", "Snapchat", 
        "Facebook", "Reddit", "Twitter / X", "Discord", 
        "Twitch", "Telegram", "Tinder", "WeChat"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE)
        
        try {
            prefs.getString("PARTY_DATA", "")
        } catch (e: Exception) {
            prefs.edit().clear().apply()
        }

        if (prefs.getBoolean("LAUNCH_GAME_DEFAULT", false) && !intent.getBooleanExtra("FROM_GAME", false)) {
            startActivity(Intent(this, GameActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        compName = ComponentName(this, AdminReceiver::class.java)

        WeatherEngine.fetchSilent({ city, _, _, _, debugStr ->
            prefs.edit().putString("CURRENT_CITY", city).putString("DEBUG_API_DATA", debugStr).apply()
        }, {})

        if (!prefs.getBoolean("INITIALIZED", false)) {
            val starterParty = "Cacheon,Tech,120,120,Ping,Glitch,System Wipe,0,0,0,0,false,None,0,0,0,0,None,0;Cardiol,Fitness,150,150,Momentum,Heavy Lift,Flex,0,0,0,0,false,None,0,0,0,0,None,0"

            prefs.edit()
                .putString("PARTY_DATA", starterParty)
                .putInt("NETS", 5)
                .putInt("SPRAYS", 2)
                .putInt("POTIONS", 3)
                .putBoolean("INITIALIZED", true)
                .putBoolean("VIBRATION", true)
                .apply()
        }

        val spinApps = findViewById<Spinner>(R.id.spinApps)
        spinApps.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, popularApps)

        findViewById<Button>(R.id.btnSettings).setOnClickListener { openSettingsMenu() }

        findViewById<Button>(R.id.btnStep1).setOnClickListener {
            if (!isAccessibilityServiceEnabled(this, GuardianService::class.java)) {
                DialogUtils.showCustomDialog(
                    this,
                    "Step 1: The Guardian",
                    "Android hides this setting for security.\n\n→ Tap 'Downloaded apps' or 'Installed services'.\n→ Find '${getString(R.string.app_name)}'.\n→ Turn the switch ON.",
                    true,
                    "GO TO SETTINGS",
                    { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                )
            }
        }

        findViewById<Button>(R.id.btnStep2).setOnClickListener {
            if (!dpm.isAdminActive(compName)) {
                DialogUtils.showCustomDialog(this, "Step 2: Lock App", "This prevents the app from being uninstalled from your home screen.", true, "LOCK APP", {
                    startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName)
                        putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Locks app down.")
                    })
                })
            }
        }

        findViewById<Button>(R.id.btnStep3).setOnClickListener {
            val clip = ClipData.newPlainText("DNS", "adult-filter-dns.cleanbrowsing.org")
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
            DialogUtils.showCustomDialog(this, "Step 3: Web Filter", "We copied the secure address to your clipboard!\n\n→ Find 'Private DNS' in settings.\n→ Select 'Custom provider'.\n→ Paste the text.", true, "OPEN SETTINGS", {
                startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            })
        }

        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        val isChinesePhone = listOf("xiaomi", "poco", "redmi", "huawei", "oppo", "vivo", "realme").any { manufacturer.contains(it) }
        val btn4 = findViewById<Button>(R.id.btnStep4)
        
        if (btn4 != null) {
            if (isChinesePhone) {
                btn4.visibility = View.VISIBLE
                btn4.setOnClickListener {
                    DialogUtils.showCustomDialog(this, "Step 4: Battery Guard", "Your phone ($manufacturer) will kill this app to save battery.\n\n→ Set 'Autostart' to ON.\n→ Set 'Battery Saver' to NO RESTRICTIONS.", true, "FIX NOW", {
                        try {
                            startActivity(Intent().apply {
                                component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                            })
                        } catch (e: Exception) {
                            prefs.edit().putBoolean("STEP4_CLICKED", true).apply()
                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:$packageName") })
                        }
                    })
                }
            }
        }

        findViewById<Button>(R.id.btnTestFilter).setOnClickListener {
            Toast.makeText(this, "Verifying DNS...", Toast.LENGTH_SHORT).show()
            Thread {
                try {
                    val conn = URL("https://playboy.com").openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.connect()
                    runOnUiThread { Toast.makeText(this, "DNS Not Working Yet. Try again.", Toast.LENGTH_SHORT).show() }
                } catch (e: Exception) {
                    prefs.edit().putBoolean("DNS_VERIFIED", true).apply()
                    runOnUiThread { refreshUI() }
                }
            }.start()
        }

        findViewById<Button>(R.id.btnAddWeb).setOnClickListener {
            val word = findViewById<EditText>(R.id.etCustomWeb).text.toString().trim().lowercase()
            val protectedWords = listOf(".android", "systemui", "launcher", "nexus", "pixel", "gallery", "camera", "dialer", "contacts", "settings", "note")
            if (word.isNotEmpty()) {
                if (protectedWords.any { word.contains(it) }) {
                    Toast.makeText(this, "Cannot block core system functions!", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val currentList = prefs.getString("BLOCKLIST_WEB", "") ?: ""
                prefs.edit().putString("BLOCKLIST_WEB", "$currentList,$word").apply()
                findViewById<EditText>(R.id.etCustomWeb).setText("")
                Toast.makeText(this, "Permanently overcome Website: $word", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnAddApp).setOnClickListener {
            val selectedApp = spinApps.selectedItem.toString()
            val packageKeyword = when (selectedApp) {
                "YouTube" -> "com.google.android.youtube"
                "TikTok" -> "com.zhiliaoapp.musically"
                "Instagram" -> "com.instagram.android"
                "Snapchat" -> "com.snapchat.android"
                "Facebook" -> "com.facebook.katana"
                "Reddit" -> "com.reddit.frontpage"
                "Twitter / X" -> "com.twitter.android"
                "Discord" -> "com.discord"
                "Twitch" -> "tv.twitch.android.app"
                "Telegram" -> "org.telegram.messenger"
                "Tinder" -> "com.tinder"
                "WeChat" -> "com.tencent.mm"
                else -> ""
            }
            val protectedWords = listOf(".android", "systemui", "launcher", "nexus", "pixel", "gallery", "camera", "dialer", "contacts", "settings", "note")
            if (packageKeyword.isNotEmpty()) {
                if (protectedWords.any { packageKeyword.contains(it) }) {
                    Toast.makeText(this, "Cannot block core system functions!", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val currentList = prefs.getString("BLOCKLIST_APP", "") ?: ""
                prefs.edit().putString("BLOCKLIST_APP", "$currentList,$packageKeyword").apply()
                Toast.makeText(this, "Permanently overcome App: $selectedApp", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnGame).setOnClickListener { startActivity(Intent(this, GameActivity::class.java)) }

        findViewById<Button>(R.id.btnDebugConsole).setOnClickListener {
            val apiData = prefs.getString("DEBUG_API_DATA", "No API data fetched yet.")
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val batteryStatus: Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val temp = (batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
            val volt = (batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000.0
            val hardwareStats = "Battery: $batLevel%\nTemp: $temp°C\nVoltage: ${volt}V"
            
            val trackers = GuardianService.appTimeTrackers
            var social = 10L; var stream = 10L; var game = 10L; var tech = 10L
            
            for ((pkg, time) in trackers) {
                val sec = time / 1000
                if (pkg.contains("twitter") || pkg.contains("instagram") || pkg.contains("facebook") || pkg.contains("snapchat")) social += sec
                else if (pkg.contains("youtube") || pkg.contains("tiktok") || pkg.contains("twitch") || pkg.contains("netflix")) stream += sec
                else if (pkg.contains("game")) game += sec
                else tech += sec
            }
            val total = social + stream + game + tech
            val topSpawn = if (social > stream && social > game && social > tech) "Chirplet (Social)"
                else if (stream > social && stream > game && stream > tech) "Bufferoo (Streaming)"
                else if (game > social && game > stream && game > tech) "Noobit (Gaming)"
                else "Atomit (Tech/Web)"
                
            val habitData = "Social/Chat: ${(social * 100) / total}%\nMedia/Video: ${(stream * 100) / total}%\nGaming Apps: ${(game * 100) / total}%\nWeb/Tech: ${(tech * 100) / total}%\n--> Probable Spawn: $topSpawn"
            val logs = if (GuardianService.actionLogs.isEmpty()) "No logs recorded yet." else GuardianService.actionLogs.joinToString("\n")
            
            // Note: Make sure BuildInfo.TIMESTAMP exists in your code. 
            // If it throws an error, you can replace it with: val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(java.util.Date())
            val masterReport = "=== HARDWARE ===\n$hardwareStats\n\n=== API RAW DATA ===\n$apiData\n\n=== HABIT INTELLIGENCE ===\n$habitData\n\n=== GUARDIAN LOGS ===\n$logs\n\n=== PRODUCTION CHECKLIST ===\n1. Remove Debug UI box in XML.\n2. Remove Debug TextView in overlay_guard.xml.\n3. Remove REQUEST_DELETE_PACKAGES from Manifest."
            DialogUtils.showCustomDialog(this, "Developer Console", masterReport, false, "CLOSE", null)
        }

        findViewById<Button>(R.id.btnDebugReset).setOnClickListener { 
            prefs.edit().clear().apply()
            Toast.makeText(this, "SAVE WIPED.", Toast.LENGTH_LONG).show()
            finish()
            startActivity(intent) 
        }

        findViewById<Button>(R.id.btnDebugUninstall).setOnClickListener { 
            GuardianService.pauseUntil = System.currentTimeMillis() + (10 * 60 * 1000)
            if (dpm.isAdminActive(compName)) dpm.removeActiveAdmin(compName)
            startActivity(Intent(Intent.ACTION_DELETE).apply { data = Uri.parse("package:$packageName") }) 
        }
    }

    private fun openSettingsMenu() {
        DialogUtils.showCustomDialog(this, "Settings", null, true, "SAVE", null) { content, dialog ->
            val cbGame = CheckBox(this).apply { 
                text = "Enable Gamification"
                isChecked = prefs.getBoolean("GAMIFICATION", true)
                setTextColor(Color.WHITE) 
                textSize = 16f
                setPadding(16, 16, 16, 16)
            }
            val cbDefault = CheckBox(this).apply { 
                text = "Set Netbeasts as Default Home App"
                isChecked = prefs.getBoolean("LAUNCH_GAME_DEFAULT", false)
                setTextColor(Color.WHITE) 
                textSize = 16f
                setPadding(16, 16, 16, 16)
            }
            val cbVibe = CheckBox(this).apply { 
                text = "Enable Combat Vibration"
                isChecked = prefs.getBoolean("VIBRATION", true)
                setTextColor(Color.WHITE) 
                textSize = 16f
                setPadding(16, 16, 16, 16)
            }

            content.addView(cbGame)
            content.addView(cbDefault)
            content.addView(cbVibe)

            // Override the positive button to save settings
            dialog.findViewById<Button>(R.id.btnDialogPositive)?.setOnClickListener {
                prefs.edit()
                    .putBoolean("GAMIFICATION", cbGame.isChecked)
                    .putBoolean("LAUNCH_GAME_DEFAULT", cbDefault.isChecked)
                    .putBoolean("VIBRATION", cbVibe.isChecked)
                    .apply()
                swapAppIcon(cbDefault.isChecked)
                dialog.dismiss()
            }
        }
    }

    private fun swapAppIcon(useGameIcon: Boolean) {
        val pm = packageManager
        val defaultAlias = ComponentName(this, "com.rockhard.blocker.DefaultLauncher")
        val gameAlias = ComponentName(this, "com.rockhard.blocker.GameLauncher")
        val currentDefault = pm.getComponentEnabledSetting(defaultAlias)
        
        if (useGameIcon && currentDefault != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            pm.setComponentEnabledSetting(defaultAlias, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(gameAlias, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            Toast.makeText(this, "Icon swapped to Netbeasts!", Toast.LENGTH_LONG).show()
        } else if (!useGameIcon && currentDefault != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            pm.setComponentEnabledSetting(gameAlias, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(defaultAlias, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            Toast.makeText(this, "Icon swapped to RHC Default!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUI()
    }

    private fun refreshUI() {
        val btn1 = findViewById<Button>(R.id.btnStep1)
        val btn2 = findViewById<Button>(R.id.btnStep2)
        val btn3 = findViewById<Button>(R.id.btnStep3)
        val btn4 = findViewById<Button>(R.id.btnStep4)
        
        val step1Done = isAccessibilityServiceEnabled(this, GuardianService::class.java)
        val step2Done = dpm.isAdminActive(compName)
        val step3Done = prefs.getBoolean("DNS_VERIFIED", false)
        val isPremium = prefs.getBoolean("REWARD_PREMIUM", false)

        val isSetupDone = step1Done && step2Done && step3Done || isPremium
        findViewById<View>(R.id.cardOvercomeWeb)?.visibility = if (isSetupDone) View.VISIBLE else View.GONE
        findViewById<View>(R.id.cardOvercomeApp)?.visibility = if (isSetupDone) View.VISIBLE else View.GONE

        if (isPremium) {
            findViewById<View>(R.id.llOnboarding).visibility = View.GONE
            return
        }

        if (step1Done) {
            btn1.text = "STEP 1: VERIFIED ✔️"
            btn1.setBackgroundResource(R.drawable.bg_btn_success)
        }
        if (step2Done) {
            btn2.text = "STEP 2: VERIFIED ✔️"
            btn2.setBackgroundResource(R.drawable.bg_btn_success)
        }
        if (step3Done) {
            btn3.text = "STEP 3: VERIFIED ✔️"
            btn3.setBackgroundResource(R.drawable.bg_btn_success)
        }
        if (btn4.visibility == View.VISIBLE && prefs.getBoolean("STEP4_CLICKED", false)) {
            btn4.text = "STEP 4: VERIFIED ✔️"
            btn4.setBackgroundResource(R.drawable.bg_btn_success)
        }

        if (step1Done && step2Done && step3Done) {
            prefs.edit().putBoolean("REWARD_PREMIUM", true).apply()
            Toast.makeText(this, "SYSTEM SECURED! Premium Netbeast Unlocked!", Toast.LENGTH_LONG).show()
            findViewById<View>(R.id.llOnboarding).visibility = View.GONE
            findViewById<View>(R.id.cardOvercomeWeb)?.visibility = View.VISIBLE
            findViewById<View>(R.id.cardOvercomeApp)?.visibility = View.VISIBLE
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, accessibilityService: Class<*>): Boolean {
        val expected = ComponentName(context, accessibilityService)
        val setting = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(setting)
        while (splitter.hasNext()) {
            if (ComponentName.unflattenFromString(splitter.next()) == expected) return true
        }
        return false
    }
}