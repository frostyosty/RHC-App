package com.rockhard.blocker

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.*
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    internal lateinit var dpm: DevicePolicyManager
    internal lateinit var compName: ComponentName
    internal lateinit var prefs: SharedPreferences
    
    internal val installedAppMap = mutableMapOf<String, String>()
    internal val mainHandler = Handler(Looper.getMainLooper())
    
    internal val tickRunnable = object : Runnable {
        override fun run() {
            if (!BuildConfig.FLAVOR.lowercase().contains("gamers")) updateMomentumUI()
            
            if (prefs.getBoolean("DEBUG_UI_TOASTS", false)) {
                findViewById<View>(R.id.llDebugTerminal)?.visibility = View.VISIBLE
                val terminalText = GuardianService.actionLogs.joinToString("\n")
                findViewById<TextView>(R.id.tvTerminalOutput)?.text = terminalText
            } else {
                findViewById<View>(R.id.llDebugTerminal)?.visibility = View.GONE
            }
            
            mainHandler.postDelayed(this, 1000)
        }
    }

    internal var setupPollHandler: Handler? = null
    internal var setupPollRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE)
        try { prefs.getString("PARTY_DATA", "") } catch (e: Exception) { prefs.edit().clear().apply() }
        if (!prefs.contains("INSTALL_TIME")) prefs.edit().putLong("INSTALL_TIME", System.currentTimeMillis()).apply()

        val isGamers = BuildConfig.FLAVOR.lowercase().contains("gamers")
        CloakEngine.uncloak(this, prefs.getBoolean("LAUNCH_GAME_DEFAULT", false))

        if (prefs.getBoolean("LAUNCH_GAME_DEFAULT", false) && !intent.getBooleanExtra("FROM_GAME", false)) {
            if (isGamers) { startActivity(Intent(this, GameActivity::class.java)); finish(); return }
        }
        setContentView(R.layout.activity_main)
        
        // CRITICAL: Force Watchdog restart on app open
        WatchdogReceiver.scheduleWatchdog(this)
        
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        compName = ComponentName(this, AdminReceiver::class.java)

        if (!isGamers) {
            MomentumEngine.resetDailyIfNeeded(prefs)
            setupMomentumUI()
        }
        mainHandler.postDelayed(tickRunnable, 1000)

        findViewById<View>(R.id.llMomentumContainer).visibility = if (isGamers) View.GONE else View.VISIBLE
        findViewById<View>(R.id.llSafariCard).visibility = if (isGamers) View.VISIBLE else View.GONE

        WeatherEngine.fetchSilent(this, onSuccess = { city, weather, icon, terrain, debugStr -> prefs.edit().putString("CURRENT_CITY", city).putString("DEBUG_API_DATA", debugStr).apply() }, {})

        if (!prefs.getBoolean("INITIALIZED", false)) {
            val starterParty = "Cacheon,Tech,120,120,Ping,Glitch,System Wipe,0,0,0,0,false,None,0,0,0,0,None,0;Cardiol,Fitness,150,150,Momentum,Heavy Lift,Flex,0,0,0,0,false,None,0,0,0,0,None,0"
            prefs.edit().putString("PARTY_DATA", starterParty).putInt("NETS", 5).putInt("SPRAYS", 2).putInt("POTIONS", 3).putBoolean("INITIALIZED", true).putBoolean("VIBRATION", true).apply()
        }

        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        
        val appMap = mutableMapOf<String, String>()
        val protectedPkgs = listOf(
            "com.android.systemui", "com.android.settings", "com.android.launcher", "com.google.android.apps.nexuslauncher",
            "com.android.contacts", "com.android.dialer", "com.google.android.dialer", "com.miui.securitycenter",
            "com.samsung.android.settings", "com.android.permissioncontroller", "com.android.providers"
        )

        for (info in resolveInfos) {
            val appName = info.loadLabel(pm).toString()
            val pkgName = info.activityInfo.packageName
            if (!protectedPkgs.any { pkgName.lowercase().contains(it) } && pkgName != packageName) {
                appMap[appName] = pkgName
            }
        }
        
        installedAppMap.clear()
        val displayList = mutableListOf<String>()
        val popularApps = listOf("TikTok", "Instagram", "Snapchat", "YouTube", "Facebook", "Twitter", "Reddit", "WeChat", "Telegram", "Tinder", "Discord", "Twitch", "Spotify")
        
        popularApps.forEach { 
            displayList.add("🔥 $it")
            installedAppMap["🔥 $it"] = it.lowercase() 
        }
        
        displayList.add("--- INSTALLED ON DEVICE ---")
        installedAppMap["--- INSTALLED ON DEVICE ---"] = ""

        val sortedAppNames = appMap.keys.sorted()
        sortedAppNames.forEach { 
            displayList.add(it)
            installedAppMap[it] = appMap[it]!!
        }

        val spinApps = findViewById<Spinner>(R.id.spinApps)
        spinApps.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, displayList)

        findViewById<Button>(R.id.btnSettings).setOnClickListener { openSettingsMenu() }

        val grantSettingsAccess = { prefs.edit().putLong("ALLOW_SETTINGS_UNTIL", System.currentTimeMillis() + 120000L).apply() }
        findViewById<Button>(R.id.btnStep1).setOnClickListener { if (!isAccessibilityServiceEnabled(this, GuardianService::class.java)) { grantSettingsAccess(); DialogUtils.showCustomDialog(this, "Step 1: Rock Hard Shield", "Android hides this setting for security.\n\n→ Tap 'Downloaded apps' or 'Installed services'.\n→ Find '${getString(R.string.app_name)}'.\n→ Turn the switch ON.", true, "GO TO SETTINGS", { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) } }
        findViewById<Button>(R.id.btnStep2).setOnClickListener { if (!dpm.isAdminActive(compName)) { grantSettingsAccess(); DialogUtils.showCustomDialog(this, "Step 2: Lock App", "This prevents the app from being uninstalled.", true, "LOCK APP", { startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply { putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName); putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Locks app down.") }) }) } }
        findViewById<Button>(R.id.btnStepBatteryOpt).setOnClickListener { grantSettingsAccess(); val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:$packageName") }; try { startActivity(intent) } catch (e: Exception) { Toast.makeText(this, "Setting unavailable.", Toast.LENGTH_SHORT).show() } }

        val isChinesePhone = listOf("xiaomi", "poco", "redmi", "huawei", "oppo", "vivo", "realme").any { android.os.Build.MANUFACTURER.lowercase().contains(it) }
        val btn4 = findViewById<Button>(R.id.btnStep4); val btn5 = findViewById<Button>(R.id.btnStep5) 
        if (isChinesePhone && btn4 != null && btn5 != null) {
            btn4.visibility = View.VISIBLE; btn5.visibility = View.VISIBLE
            btn4.text = "STEP 4: FIX AUTOSTART"; 
            btn4.setOnClickListener { 
                prefs.edit().putBoolean("AWAITING_STEP4", true).apply()
                grantSettingsAccess()
                DialogUtils.showCustomDialog(this, "Step 4: Autostart", "Set 'Autostart' to ON so the background process survives.", true, "FIX NOW", { 
                    // MULTI-OEM AUTOSTART LOOP
                    val intents = listOf(
                        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                        Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
                        Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
                        Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
                        Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
                        Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
                        Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
                        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"))
                    )
                    var success = false
                    for (intent in intents) {
                        try {
                            startActivity(intent)
                            success = true
                            break
                        } catch (e: Exception) {}
                    }
                    if (!success) {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:$packageName") })
                    }
                }) 
            }
            btn5.text = "STEP 5: SECURE APP MANAGER"; btn5.setOnClickListener { prefs.edit().putBoolean("STEP5_CLICKED", true).apply(); DialogUtils.showCustomDialog(this, "Step 5: App Manager", "We will now aggressively block the Android/MIUI 'Manage Apps' list screen so the shield cannot be bypassed.", true, "SECURE IT", { refreshUI() }) }
        }

        findViewById<Button>(R.id.btnAddWeb).setOnClickListener { 
            val webInput = findViewById<EditText>(R.id.etCustomWeb).text.toString().trim().lowercase()
            if (webInput.isNotEmpty()) {
                addBlockItem("BLOCKLIST_WEB", webInput, webInput)
                findViewById<EditText>(R.id.etCustomWeb).setText("") 
            }
        }
        
        findViewById<Button>(R.id.btnAddApp).setOnClickListener { 
            val selectedDisplay = spinApps.selectedItem?.toString() ?: ""
            val blockTarget = installedAppMap[selectedDisplay] ?: ""
            
            if (blockTarget.isNotEmpty()) {
                val cleanName = selectedDisplay.replace("🔥 ", "").replace("--- INSTALLED ON DEVICE ---", "")
                addBlockItem("BLOCKLIST_APP", cleanName, blockTarget)
            }
        }
        findViewById<Button>(R.id.btnGame).setOnClickListener { startActivity(Intent(this, GameActivity::class.java)) }

        // --- DRASTIC OVERCOMING LOGIC ---
        val btnDumbPhone = findViewById<Button>(R.id.btnDrasticDumbPhone)
        val btnNoInternet = findViewById<Button>(R.id.btnDrasticNoInternet)
        val btnCallsOnly = findViewById<Button>(R.id.btnDrasticCallsOnly)

        fun updateDrasticUI() {
            val isDumbPhone = prefs.getBoolean("DRASTIC_DUMB_PHONE", false)
            val isNoInternet = prefs.getBoolean("DRASTIC_NO_INTERNET", false)
            val isCallsOnly = prefs.getBoolean("DRASTIC_CALLS_ONLY", false)

            btnDumbPhone.text = if (isDumbPhone) "Dumb Phone Mode: ACTIVE" else "Make My Phone a Dumb Phone"
            btnDumbPhone.setBackgroundResource(if (isDumbPhone) R.drawable.bg_btn_success else R.drawable.bg_btn_warning)
            
            btnNoInternet.text = if (isNoInternet) "No Internet Mode: ACTIVE" else "Remove Internet From My Phone"
            btnNoInternet.setBackgroundResource(if (isNoInternet) R.drawable.bg_btn_success else R.drawable.bg_btn_warning)

            btnCallsOnly.text = if (isCallsOnly) "Calls/Texts Only: ACTIVE" else "Calls and Texts ONLY"
            btnCallsOnly.setBackgroundResource(if (isCallsOnly) R.drawable.bg_btn_success else R.drawable.bg_btn_danger)
        }

        updateDrasticUI()

        val confirmDrastic = { key: String, title: String, msg: String ->
            val isGodModeActive = System.currentTimeMillis() < prefs.getLong("ALLOW_SETTINGS_UNTIL", 0L)
            if (prefs.getBoolean(key, false)) {
                if (isGodModeActive) {
                    prefs.edit().putBoolean(key, false).apply()
                    updateDrasticUI()
                    Toast.makeText(this, "Drastic Mode Disabled.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Must schedule System Override (Pause) and execute it to disable.", Toast.LENGTH_LONG).show()
                }
            } else {
                DialogUtils.showCustomDialog(this, title, msg, true, "ENGAGE", { 
                    prefs.edit().putBoolean(key, true).apply()
                    updateDrasticUI()
                })
            }
        }

        btnDumbPhone.setOnClickListener { confirmDrastic("DRASTIC_DUMB_PHONE", "Engage Dumb Phone Mode?", "This will block EVERYTHING except Calculator, Calendar, Camera, Maps, Weather, Calls, and Texts.\n\nCannot be undone without scheduling a System Override.") }
        btnCallsOnly.setOnClickListener { confirmDrastic("DRASTIC_CALLS_ONLY", "Engage Calls/Texts ONLY?", "NUCLEAR OPTION.\n\nYour smartphone will become a brick that can only make phone calls and send SMS text messages.\n\nCannot be undone without scheduling a System Override.") }
        btnNoInternet.setOnClickListener { confirmDrastic("DRASTIC_NO_INTERNET", "Remove Internet?", "This will aggressively block ALL web browsers, app stores, and known social media.\n\nCannot be undone without scheduling a System Override.") }

        setupSystemOverrideUI()
    }

    internal fun refreshUI() {
        val btn1 = findViewById<Button>(R.id.btnStep1); val btn2 = findViewById<Button>(R.id.btnStep2); val btnBatteryOpt = findViewById<Button>(R.id.btnStepBatteryOpt)
        val btn4 = findViewById<Button>(R.id.btnStep4); val btn5 = findViewById<Button>(R.id.btnStep5)
        
        val step1Done = isAccessibilityServiceEnabled(this, GuardianService::class.java)
        val step2Done = dpm.isAdminActive(compName)
        val powMan = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val stepBatteryDone = powMan.isIgnoringBatteryOptimizations(packageName)
        val step4Done = prefs.getBoolean("STEP4_CLICKED", false)
        val step5Done = prefs.getBoolean("STEP5_CLICKED", false)
        
        val isChinesePhone = listOf("xiaomi", "poco", "redmi", "huawei", "oppo", "vivo", "realme").any { android.os.Build.MANUFACTURER.lowercase().contains(it) }
        val isPremium = prefs.getBoolean("REWARD_PREMIUM", false)

        btn2.isEnabled = step1Done; btnBatteryOpt.isEnabled = step1Done && step2Done
        btn4?.isEnabled = step1Done && step2Done && stepBatteryDone; btn5?.isEnabled = step1Done && step2Done && stepBatteryDone && step4Done

        if (step1Done) { btn1.text = "STEP 1: VERIFIED ✔️"; btn1.setBackgroundResource(R.drawable.bg_btn_success) }
        if (step2Done) { btn2.text = "STEP 2: VERIFIED ✔️"; btn2.setBackgroundResource(R.drawable.bg_btn_success) }
        if (stepBatteryDone) { btnBatteryOpt.text = "STEP 3: VERIFIED ✔️"; btnBatteryOpt.setBackgroundResource(R.drawable.bg_btn_success) }
        if (btn4?.visibility == View.VISIBLE && step4Done) { btn4.text = "STEP 4: VERIFIED ✔️"; btn4.setBackgroundResource(R.drawable.bg_btn_success) }
        if (btn5?.visibility == View.VISIBLE && step5Done) { btn5.text = "STEP 5: VERIFIED ✔️"; btn5.setBackgroundResource(R.drawable.bg_btn_success) }

        val isSetupDone = if (isChinesePhone) step1Done && step2Done && stepBatteryDone && step4Done && step5Done else step1Done && step2Done && stepBatteryDone || isPremium

        if (isSetupDone && !isPremium) {
            prefs.edit().putBoolean("REWARD_PREMIUM", true).apply()
            if (BuildConfig.FLAVOR.lowercase().contains("gamers")) {
                val legendary = if (kotlin.random.Random.nextBoolean()) "Aegis,Legendary,250,250,Light Pulse,Nova Shield,Orbital Cannon,0,0,0,0,true,None,0,0,0,0,None,0" else "Titan,Legendary,280,280,Feral Strike,Parry,Apex Predator,0,0,0,0,true,None,0,0,0,0,None,0"
                val partyStr = prefs.getString("PARTY_DATA", "") ?: ""
                prefs.edit().putString("PARTY_DATA", if(partyStr.isEmpty()) legendary else "$partyStr;$legendary").apply()
                Toast.makeText(this, "SYSTEM SECURED! Legendary Netbeast Unlocked!", Toast.LENGTH_LONG).show()
            } else { MomentumEngine.addEarnedMomentum(prefs, "System Secured Bonus", false); Toast.makeText(this, "SYSTEM SECURED! Gained 20 minutes of Momentum!", Toast.LENGTH_LONG).show() }
        }
        findViewById<View>(R.id.cardOvercomeWeb)?.visibility = if (isSetupDone) View.VISIBLE else View.GONE
        findViewById<View>(R.id.cardOvercomeApp)?.visibility = if (isSetupDone) View.VISIBLE else View.GONE
        if (isSetupDone) { renderBlockList(findViewById(R.id.llBannedWebs), "BLOCKLIST_WEB"); renderBlockList(findViewById(R.id.llBannedApps), "BLOCKLIST_APP") }
        if (isPremium) findViewById<View>(R.id.llOnboarding).visibility = View.GONE
    } 

    private fun openSettingsMenu() {
        DialogUtils.showCustomDialog(this, "Settings", null, true, "SAVE", null) { content, dialog ->
            val isGamers = BuildConfig.FLAVOR.lowercase().contains("gamers")
            val cbGame = CheckBox(this).apply { text = if(isGamers) "Enable Gamification" else "Enable Momentum Tracking"; isChecked = prefs.getBoolean("GAMIFICATION", true); setTextColor(android.graphics.Color.WHITE); textSize = 16f; setPadding(16, 16, 16, 16) }
            val cbDefault = CheckBox(this).apply { text = if(isGamers) "Set Netbeasts as Default Home App" else "Set Momentum as Default Home App"; isChecked = prefs.getBoolean("LAUNCH_GAME_DEFAULT", false); setTextColor(android.graphics.Color.WHITE); textSize = 16f; setPadding(16, 16, 16, 16) }
            val cbDebug = CheckBox(this).apply { text = "Enable UI Debugger (Hunt Chinese ROMs)"; isChecked = prefs.getBoolean("DEBUG_UI_TOASTS", false); setTextColor(android.graphics.Color.YELLOW); textSize = 16f; setPadding(16, 16, 16, 16) }
            
            content.addView(cbDebug)
            content.addView(cbGame)
            content.addView(cbDefault)
            
            content.addView(Button(this).apply {
                text = "Safe App Manager (Uninstall Apps)"
                setBackgroundResource(R.drawable.bg_btn_standard); setTextColor(android.graphics.Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 16, 0, 0) }
                setOnClickListener { showSafeUninstaller(); dialog.dismiss() }
            })
            
            val powMan = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val isIgnoringDoze = powMan.isIgnoringBatteryOptimizations(packageName)
            content.addView(Button(this).apply {
                text = if (isIgnoringDoze) "Doze Mode Disabled ✔️" else "Disable Doze Mode (Fixes Sleep Bug)"
                setBackgroundResource(if (isIgnoringDoze) R.drawable.bg_btn_success else R.drawable.bg_btn_danger); setTextColor(android.graphics.Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(0, 16, 0, 0) }
                isEnabled = !isIgnoringDoze
                setOnClickListener { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:$packageName") }); dialog.dismiss() }
            })
            dialog.findViewById<Button>(R.id.btnDialogPositive)?.setOnClickListener { 
                prefs.edit().putBoolean("GAMIFICATION", cbGame.isChecked)
                            .putBoolean("LAUNCH_GAME_DEFAULT", cbDefault.isChecked)
                            .putBoolean("DEBUG_UI_TOASTS", cbDebug.isChecked).apply()
                CloakEngine.uncloak(this@MainActivity, cbDefault.isChecked)
                dialog.dismiss() 
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); mainHandler.removeCallbacks(tickRunnable) }
    
    internal fun isAccessibilityServiceEnabled(context: Context, accessibilityService: Class<*>): Boolean {
        val setting = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val expected = ComponentName(context, accessibilityService)
        val splitter = TextUtils.SimpleStringSplitter(':'); splitter.setString(setting)
        while (splitter.hasNext()) if (ComponentName.unflattenFromString(splitter.next()) == expected) return true
        return false
    }

    override fun onPause() {
        super.onPause()
        val powMan = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val stepBatteryDone = powMan.isIgnoringBatteryOptimizations(packageName)
        val step4Done = prefs.getBoolean("STEP4_CLICKED", false)

        if (!stepBatteryDone || !step4Done) {
            setupPollHandler = Handler(Looper.getMainLooper())
            setupPollRunnable = object : Runnable {
                override fun run() {
                    val nowBattery = powMan.isIgnoringBatteryOptimizations(packageName)
                    val nowStep4 = prefs.getBoolean("STEP4_CLICKED", false)
                    
                    if ((!stepBatteryDone && nowBattery) || (!step4Done && nowStep4)) {
                        val intent = Intent(this@MainActivity, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP) }
                        try { startActivity(intent); setupPollHandler?.removeCallbacksAndMessages(null) } catch (e: Exception) {}
                    } else setupPollHandler?.postDelayed(this, 1000)
                }
            }
            setupPollHandler?.postDelayed(setupPollRunnable!!, 1000)
        }
    }

    override fun onResume() { 
        super.onResume()
        setupPollHandler?.removeCallbacksAndMessages(null)
        if (prefs.getBoolean("AWAITING_STEP4", false)) prefs.edit().putBoolean("STEP4_CLICKED", true).putBoolean("AWAITING_STEP4", false).apply()
        refreshUI()
        if (prefs.getBoolean("DEBUG_UI_TOASTS", false)) {
            findViewById<View>(R.id.llDebugTerminal)?.visibility = View.VISIBLE
        }
    }
}
