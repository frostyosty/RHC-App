package com.rockhard.blocker

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.Manifest
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random

class MainActivity : Activity() {
    private lateinit var dpm: DevicePolicyManager; private lateinit var compName: ComponentName; private lateinit var prefs: SharedPreferences
    private val popularApps = arrayOf("YouTube", "TikTok", "Instagram", "Snapchat", "Facebook", "Reddit", "Twitter / X", "Discord", "Twitch", "Telegram", "Tinder", "WeChat")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE)
        try { prefs.getString("PARTY_DATA", "") } catch (e: Exception) { prefs.edit().clear().apply() }
        
        if (!prefs.contains("INSTALL_TIME")) prefs.edit().putLong("INSTALL_TIME", System.currentTimeMillis()).apply()

        if (prefs.getBoolean("LAUNCH_GAME_DEFAULT", false) && !intent.getBooleanExtra("FROM_GAME", false)) { startActivity(Intent(this, GameActivity::class.java)); finish(); return }
        setContentView(R.layout.activity_main)
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager; compName = ComponentName(this, AdminReceiver::class.java)

        WeatherEngine.fetchSilent(this, { city, _, _, _, debugStr -> prefs.edit().putString("CURRENT_CITY", city).putString("DEBUG_API_DATA", debugStr).apply() }, {})

        if (!prefs.getBoolean("INITIALIZED", false)) {
            val starterParty = "Cacheon,Tech,120,120,Ping,Glitch,System Wipe,0,0,0,0,false,None,0,0,0,0,None,0;Cardiol,Fitness,150,150,Momentum,Heavy Lift,Flex,0,0,0,0,false,None,0,0,0,0,None,0"
            prefs.edit().putString("PARTY_DATA", starterParty).putInt("NETS", 5).putInt("SPRAYS", 2).putInt("POTIONS", 3).putBoolean("INITIALIZED", true).putBoolean("VIBRATION", true).apply()
        }

        val spinApps = findViewById<Spinner>(R.id.spinApps)
        spinApps.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, popularApps)

        findViewById<Button>(R.id.btnSettings).setOnClickListener { openSettingsMenu() }
        findViewById<Button>(R.id.btnStep1).setOnClickListener { if (!isAccessibilityServiceEnabled(this, GuardianService::class.java)) { DialogUtils.showCustomDialog(this, "Step 1: Rock Hard Shield", "Android hides this setting for security.\n\n→ Tap 'Downloaded apps' or 'Installed services'.\n→ Find '${getString(R.string.app_name)}'.\n→ Turn the switch ON.", true, "GO TO SETTINGS", { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) } }
        findViewById<Button>(R.id.btnStep2).setOnClickListener { if (!dpm.isAdminActive(compName)) { DialogUtils.showCustomDialog(this, "Step 2: Lock App", "This prevents the app from being uninstalled.", true, "LOCK APP", { startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply { putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName); putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Locks app down.") }) }) } }
        findViewById<Button>(R.id.btnStep3).setOnClickListener { val clip = ClipData.newPlainText("DNS", "adult-filter-dns.cleanbrowsing.org"); (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip); DialogUtils.showCustomDialog(this, "Step 3: Web Filter", "Address copied to clipboard!\n\n→ Find 'Private DNS' in settings.\n→ Select 'Custom provider'.\n→ Paste the text.", true, "OPEN SETTINGS", { startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }) }

        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        val isChinesePhone = listOf("xiaomi", "poco", "redmi", "huawei", "oppo", "vivo", "realme").any { manufacturer.contains(it) }
        val btn4 = findViewById<Button>(R.id.btnStep4)
        if (btn4 != null && isChinesePhone) {
            btn4.visibility = View.VISIBLE; btn4.text = "STEP 4: AUTOSTART"
            btn4.setOnClickListener { prefs.edit().putBoolean("STEP4_CLICKED", true).apply(); DialogUtils.showCustomDialog(this, "Step 4: Autostart", "Find app in list. Set 'Autostart' to ON.", true, "FIX NOW", { try { startActivity(Intent().apply { component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity") }) } catch (e: Exception) { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:$packageName") }) }; refreshUI() }) }
            val parentLayout = btn4.parent as LinearLayout
            val btn5 = Button(this).apply { tag = "BTN_STEP_5"; text = "STEP 5: BATTERY SAVER"; setBackgroundResource(if (prefs.getBoolean("STEP5_CLICKED", false)) R.drawable.bg_btn_success else R.drawable.bg_btn_standard); setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, 0, 16) }
                setOnClickListener { prefs.edit().putBoolean("STEP5_CLICKED", true).apply(); GuardianService.pauseUntil = System.currentTimeMillis() + 60000L; DialogUtils.showCustomDialog(this@MainActivity, "Step 5: Battery Saver", "Tap 'Battery Saver'. Select 'No Restrictions'.\n\n(Rock Hard Shield has been paused for 60 seconds).", true, "FIX NOW", { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:$packageName") }); refreshUI() }) }
            }
            parentLayout.addView(btn5, parentLayout.indexOfChild(btn4) + 1)
        }

        findViewById<Button>(R.id.btnTestFilter).setOnClickListener {
            val s1 = isAccessibilityServiceEnabled(this, GuardianService::class.java)
            val s2 = dpm.isAdminActive(compName)
            Thread {
                var s3 = prefs.getBoolean("DNS_VERIFIED", false)
                if (!s3) {
                    try { val conn = URL("https://playboy.com").openConnection() as HttpURLConnection; conn.connectTimeout = 2000; conn.connect() } 
                    catch (e: Exception) { s3 = true; prefs.edit().putBoolean("DNS_VERIFIED", true).apply() }
                }
                runOnUiThread {
                    val s4 = prefs.getBoolean("STEP4_CLICKED", false); val s5 = prefs.getBoolean("STEP5_CLICKED", false)
                    val isDone = if (isChinesePhone) s1 && s2 && s3 && s4 && s5 else s1 && s2 && s3
                    if (!isDone) { Toast.makeText(this, "SYSTEM NOT SECURE! Complete all steps above.", Toast.LENGTH_LONG).show(); refreshUI() } 
                    else if (!prefs.getBoolean("REWARD_PREMIUM", false)) {
                        prefs.edit().putBoolean("REWARD_PREMIUM", true).apply()
                        val legendary = if (Random.nextBoolean()) "Aegis,Legendary,250,250,Light Pulse,Nova Shield,Orbital Cannon,0,0,0,0,true,None,0,0,0,0,None,0" else "Titan,Legendary,280,280,Feral Strike,Parry,Apex Predator,0,0,0,0,true,None,0,0,0,0,None,0"
                        val partyStr = prefs.getString("PARTY_DATA", "") ?: ""
                        prefs.edit().putString("PARTY_DATA", if(partyStr.isEmpty()) legendary else "$partyStr;$legendary").apply()
                        Toast.makeText(this, "SYSTEM SECURED! Legendary Netbeast '${legendary.split(",")[0]}' Unlocked!", Toast.LENGTH_LONG).show(); refreshUI()
                    } else refreshUI()
                }
            }.start()
        }

        findViewById<Button>(R.id.btnAddWeb).setOnClickListener { addBlockItem("BLOCKLIST_WEB", findViewById<EditText>(R.id.etCustomWeb).text.toString().trim().lowercase()); findViewById<EditText>(R.id.etCustomWeb).setText("") }
        findViewById<Button>(R.id.btnAddApp).setOnClickListener { 
            val pkg = when (spinApps.selectedItem.toString()) { "YouTube" -> "com.google.android.youtube"; "TikTok" -> "com.zhiliaoapp.musically"; "Instagram" -> "com.instagram.android"; "Snapchat" -> "com.snapchat.android"; "Facebook" -> "com.facebook.katana"; "Reddit" -> "com.reddit.frontpage"; "Twitter / X" -> "com.twitter.android"; "Discord" -> "com.discord"; "Twitch" -> "tv.twitch.android.app"; "Telegram" -> "org.telegram.messenger"; "Tinder" -> "com.tinder"; "WeChat" -> "com.tencent.mm"; else -> "" }
            addBlockItem("BLOCKLIST_APP", pkg) 
        }

        findViewById<Button>(R.id.btnGame).setOnClickListener { startActivity(Intent(this, GameActivity::class.java)) }
        findViewById<Button>(R.id.btnDebugConsole).setOnClickListener { DialogUtils.showCustomDialog(this, "Developer Console", "Stats saved.", false, "CLOSE", null) }
        findViewById<Button>(R.id.btnDebugReset).setOnClickListener { prefs.edit().clear().apply(); Toast.makeText(this, "SAVE WIPED.", Toast.LENGTH_LONG).show(); finish(); startActivity(intent) }
        
        val btnUninstall = findViewById<Button>(R.id.btnDebugUninstall)
        if (Config.UNINSTALL_PROTECTION_ENABLED) btnUninstall.visibility = View.GONE
        else {
            btnUninstall.visibility = View.VISIBLE
            btnUninstall.setOnClickListener { GuardianService.pauseUntil = System.currentTimeMillis() + (10 * 60 * 1000); if (dpm.isAdminActive(compName)) dpm.removeActiveAdmin(compName); startActivity(Intent(Intent.ACTION_DELETE).apply { data = Uri.parse("package:$packageName") }) }
        }
    }

    private fun addBlockItem(key: String, word: String) {
        val protectedWords = listOf("systemui", "launcher", "nexus", "pixel", "gallery", "camera", "dialer", "contacts", "settings", "note", "keyboard", "inputmethod", "swiftkey", "miui")
        if (word.isEmpty() || protectedWords.any { word.contains(it) }) { Toast.makeText(this, "Invalid or protected keyword!", Toast.LENGTH_LONG).show(); return }
        
        val currentList = prefs.getString(key, "") ?: ""
        val items = currentList.split(",").filter { it.isNotEmpty() }.toMutableList()
        if (!items.any { it.split("|")[0] == word }) {
            val dateStr = java.text.SimpleDateFormat("MMM dd, yyyy").format(java.util.Date())
            items.add("$word|$dateStr|0")
            prefs.edit().putString(key, items.joinToString(",")).apply()
            Toast.makeText(this, "Overcome Locked: $word", Toast.LENGTH_SHORT).show()
            refreshUI()
        } else Toast.makeText(this, "Already overcome!", Toast.LENGTH_SHORT).show()
    }

    private fun renderBlockList(ll: LinearLayout?, key: String) {
        ll?.removeAllViews()
        val data = prefs.getString(key, "") ?: ""
        if (data.isEmpty()) return
        data.split(",").filter { it.isNotEmpty() }.forEach { entry ->
            val parts = entry.split("|")
            val name = parts[0]
            val date = if (parts.size > 1) parts[1] else "Unknown Date"
            val count = if (parts.size > 2) parts[2] else "0"
            ll?.addView(TextView(this).apply { text = "🚫 $name\n   ↳ Banned: $date | Triggers: $count"; setTextColor(Color.parseColor("#CCCCCC")); textSize = 12f; setPadding(0, 8, 0, 8) })
        }
    }

    private fun openSettingsMenu() {
        DialogUtils.showCustomDialog(this, "Settings", null, true, "SAVE", null) { content, dialog ->
            val cbGame = CheckBox(this).apply { text = "Enable Gamification"; isChecked = prefs.getBoolean("GAMIFICATION", true); setTextColor(Color.WHITE); textSize = 16f; setPadding(16, 16, 16, 16) }
            val cbDefault = CheckBox(this).apply { text = "Set Netbeasts as Default Home App"; isChecked = prefs.getBoolean("LAUNCH_GAME_DEFAULT", false); setTextColor(Color.WHITE); textSize = 16f; setPadding(16, 16, 16, 16) }
            val cbVibe = CheckBox(this).apply { text = "Enable Combat Vibration"; isChecked = prefs.getBoolean("VIBRATION", true); setTextColor(Color.WHITE); textSize = 16f; setPadding(16, 16, 16, 16) }
            val hasGps = LocationEngine.hasGPSPermission(this@MainActivity)
            val btnGps = Button(this).apply {
                text = if (hasGps) "GPS Enabled (Rescues Active) ✔️" else "Grant GPS Permission (Enables Rescues)"
                setBackgroundResource(if (hasGps) R.drawable.bg_btn_success else R.drawable.bg_btn_accent)
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 24, 0, 0) }
                isEnabled = !hasGps 
                setOnClickListener { requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 101); dialog.dismiss() }
            }

            // --- DEEP DOZE PREVENTION BUTTON ---
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val isIgnoringDoze = pm.isIgnoringBatteryOptimizations(packageName)
            val btnDoze = Button(this).apply {
                text = if (isIgnoringDoze) "Doze Mode Disabled ✔️" else "Disable Doze Mode (Fixes Sleep Bug)"
                setBackgroundResource(if (isIgnoringDoze) R.drawable.bg_btn_success else R.drawable.bg_btn_danger)
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 16, 0, 0) }
                isEnabled = !isIgnoringDoze
                setOnClickListener {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                    dialog.dismiss()
                }
            }

            content.addView(cbGame); content.addView(cbDefault); content.addView(cbVibe); content.addView(btnGps); content.addView(btnDoze)
            dialog.findViewById<Button>(R.id.btnDialogPositive)?.setOnClickListener { prefs.edit().putBoolean("GAMIFICATION", cbGame.isChecked).putBoolean("LAUNCH_GAME_DEFAULT", cbDefault.isChecked).putBoolean("VIBRATION", cbVibe.isChecked).apply(); swapAppIcon(cbDefault.isChecked); dialog.dismiss() }
        }
    }

    private fun swapAppIcon(useGameIcon: Boolean) {
        val pm = packageManager; val defaultAlias = ComponentName(this, "com.rockhard.blocker.DefaultLauncher"); val gameAlias = ComponentName(this, "com.rockhard.blocker.GameLauncher"); val currentDefault = pm.getComponentEnabledSetting(defaultAlias)
        if (useGameIcon && currentDefault != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) { pm.setComponentEnabledSetting(defaultAlias, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP); pm.setComponentEnabledSetting(gameAlias, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP); Toast.makeText(this, "Icon swapped to Netbeasts!", Toast.LENGTH_LONG).show() } 
        else if (!useGameIcon && currentDefault != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) { pm.setComponentEnabledSetting(gameAlias, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP); pm.setComponentEnabledSetting(defaultAlias, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP); Toast.makeText(this, "Icon swapped to RHC Default!", Toast.LENGTH_LONG).show() }
    }

    override fun onResume() { super.onResume(); refreshUI() }

    private fun refreshUI() {
        val btn1 = findViewById<Button>(R.id.btnStep1); val btn2 = findViewById<Button>(R.id.btnStep2); val btn3 = findViewById<Button>(R.id.btnStep3); val btn4 = findViewById<Button>(R.id.btnStep4); val btn5 = findViewById<View>(android.R.id.content).findViewWithTag<Button>("BTN_STEP_5")
        val step1Done = isAccessibilityServiceEnabled(this, GuardianService::class.java); val step2Done = dpm.isAdminActive(compName); val step3Done = prefs.getBoolean("DNS_VERIFIED", false); val step4Done = prefs.getBoolean("STEP4_CLICKED", false); val step5Done = prefs.getBoolean("STEP5_CLICKED", false)
        val manufacturer = android.os.Build.MANUFACTURER.lowercase(); val isChinesePhone = listOf("xiaomi", "poco", "redmi", "huawei", "oppo", "vivo", "realme").any { manufacturer.contains(it) }
        val isPremium = prefs.getBoolean("REWARD_PREMIUM", false)

        btn2.isEnabled = step1Done
        btn3.isEnabled = step1Done && step2Done
        if (btn4 != null) btn4.isEnabled = step1Done && step2Done && step3Done
        if (btn5 != null) btn5.isEnabled = step1Done && step2Done && step3Done && step4Done
        findViewById<Button>(R.id.btnTestFilter).isEnabled = step1Done && step2Done

        if (step1Done) { btn1.text = "STEP 1: VERIFIED ✔️"; btn1.setBackgroundResource(R.drawable.bg_btn_success) }
        if (step2Done) { btn2.text = "STEP 2: VERIFIED ✔️"; btn2.setBackgroundResource(R.drawable.bg_btn_success) }
        if (step3Done) { btn3.text = "STEP 3: VERIFIED ✔️"; btn3.setBackgroundResource(R.drawable.bg_btn_success) }
        if (btn4?.visibility == View.VISIBLE && step4Done) { btn4.text = "STEP 4: VERIFIED ✔️"; btn4.setBackgroundResource(R.drawable.bg_btn_success) }
        if (btn5 != null && step5Done) { btn5.text = "STEP 5: VERIFIED ✔️"; btn5.setBackgroundResource(R.drawable.bg_btn_success) }

        val isSetupDone = if (isChinesePhone) step1Done && step2Done && step3Done && step4Done && step5Done else step1Done && step2Done && step3Done || isPremium

        findViewById<View>(R.id.cardOvercomeWeb)?.visibility = if (isSetupDone) View.VISIBLE else View.GONE
        findViewById<View>(R.id.cardOvercomeApp)?.visibility = if (isSetupDone) View.VISIBLE else View.GONE

        if (isSetupDone) {
            renderBlockList(findViewById(R.id.llBannedWebs), "BLOCKLIST_WEB")
            renderBlockList(findViewById(R.id.llBannedApps), "BLOCKLIST_APP")
        }

        if (isPremium) { findViewById<View>(R.id.llOnboarding).visibility = View.GONE }
    }

    private fun isAccessibilityServiceEnabled(context: Context, accessibilityService: Class<*>): Boolean {
        val expected = ComponentName(context, accessibilityService)
        val setting = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':'); splitter.setString(setting)
        while (splitter.hasNext()) { if (ComponentName.unflattenFromString(splitter.next()) == expected) return true }
        return false
    }
}
