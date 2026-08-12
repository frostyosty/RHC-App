package com.rockhard.blocker

import android.widget.CheckBox
import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.*
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.widget.*

class MainActivity : Activity() {
    internal lateinit var dpm: DevicePolicyManager
    internal lateinit var compName: ComponentName
    internal lateinit var prefs: SharedPreferences
    
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
    internal var allAppsList = listOf<Pair<String, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE)

    // --- CLEANUP BAD NUMERIC ENTRIES (Fixes the 2026 Bug) ---
    val appList = prefs.getString("BLOCKLIST_APP", "")
        ?.split(",")
        ?.filter { it.isNotBlank() }
        ?: emptyList()

    val webList = prefs.getString("BLOCKLIST_WEB", "")
        ?.split(",")
        ?.filter { it.isNotBlank() }
        ?: emptyList()

    val cleanApp = appList
        .filter {
            !it.split("|")[0]
                .trim()
                .matches(Regex("^[0-9]+$"))
        }
        .joinToString(",")

    val cleanWeb = webList
        .filter {
            !it.split("|")[0]
                .trim()
                .matches(Regex("^[0-9]+$"))
        }
        .joinToString(",")

    prefs.edit()
        .putString("BLOCKLIST_APP", cleanApp)
        .putString("BLOCKLIST_WEB", cleanWeb)
        .apply()

    // --------------------------------------------------------
        try { prefs.getString("PARTY_DATA", "") } catch (e: Exception) { prefs.edit().clear().apply() }
        if (!prefs.contains("INSTALL_TIME")) prefs.edit().putLong("INSTALL_TIME", System.currentTimeMillis()).apply()

        val isGamers = BuildConfig.FLAVOR.lowercase().contains("gamers")
        CloakEngine.uncloak(this, prefs.getBoolean("LAUNCH_GAME_DEFAULT", false))

        if (prefs.getBoolean("LAUNCH_GAME_DEFAULT", false) && !intent.getBooleanExtra("FROM_GAME", false)) {
            if (isGamers) { startActivity(Intent(this, GameActivity::class.java)); finish(); return }
        }
        setContentView(R.layout.activity_main)
        
        WatchdogReceiver.scheduleWatchdog(this)
        
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        compName = ComponentName(this, AdminReceiver::class.java)

        if (!isGamers) { MomentumEngine.resetDailyIfNeeded(prefs); setupMomentumUI() }
        mainHandler.postDelayed(tickRunnable, 1000)

        findViewById<View>(R.id.llMomentumContainer).visibility = if (isGamers) View.GONE else View.VISIBLE
        findViewById<View>(R.id.llSafariCard).visibility = if (isGamers) View.VISIBLE else View.GONE

        WeatherEngine.fetchSilent(this, onSuccess = { city, weather, icon, terrain, debugStr -> prefs.edit().putString("CURRENT_CITY", city).putString("DEBUG_API_DATA", debugStr).apply() }, {})

        if (!prefs.getBoolean("INITIALIZED", false)) {
            val starterParty = "Cacheon,Tech,120,120,Ping,Glitch,System Wipe,0,0,0,0,false,None,0,0,0,0,None,0;Cardiol,Fitness,150,150,Momentum,Heavy Lift,Flex,0,0,0,0,false,None,0,0,0,0,None,0"
            prefs.edit().putString("PARTY_DATA", starterParty).putInt("NETS", 5).putInt("SPRAYS", 2).putInt("POTIONS", 3).putBoolean("INITIALIZED", true).putBoolean("VIBRATION", true).apply()
        }

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
                        try { startActivity(intent); success = true; break } catch (e: Exception) {}
                    }
                    if (!success) startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:$packageName") })
                }) 
            }
            btn5.text = "STEP 5: SECURE APP MANAGER"; btn5.setOnClickListener { prefs.edit().putBoolean("STEP5_CLICKED", true).apply(); DialogUtils.showCustomDialog(this, "Step 5: App Manager", "We will now aggressively block the Android/MIUI 'Manage Apps' list screen so the shield cannot be bypassed.", true, "SECURE IT", { refreshUI() }) }
        }

        findViewById<Button>(R.id.btnGame).setOnClickListener { startActivity(Intent(this, GameActivity::class.java)) }
        
        // --- NEW: Attach Uninstaller listener to the main layout button ---
        findViewById<Button>(R.id.btnSafeAppManager)?.setOnClickListener { showSafeUninstaller() }
        
        findViewById<Button>(R.id.btnTestFilter)?.setOnClickListener {
            refreshUI()
            val step1 = isAccessibilityServiceEnabled(this, GuardianService::class.java)
            val step2 = dpm.isAdminActive(compName)
            val pow = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val step3 = pow.isIgnoringBatteryOptimizations(packageName)
            val isChinese = listOf("xiaomi", "poco", "redmi", "huawei", "oppo", "vivo", "realme").any { android.os.Build.MANUFACTURER.lowercase().contains(it) }
            val step4 = prefs.getBoolean("STEP4_CLICKED", false)
            val step5 = prefs.getBoolean("STEP5_CLICKED", false)
            val done = (step1 && step2 && step3 && (!isChinese || (step4 && step5)))
            if (done) {
                Toast.makeText(this, "🛡️ ALL SYSTEMS VERIFIED! Reward Unlocked!", Toast.LENGTH_LONG).show()
            } else {
                val missing = mutableListOf<String>()
                if (!step1) missing.add("Step 1 (Accessibility)")
                if (!step2) missing.add("Step 2 (Device Admin)")
                if (!step3) missing.add("Step 3 (Battery Optimization)")
                if (isChinese && !step4) missing.add("Step 4 (Autostart)")
                if (isChinese && !step5) missing.add("Step 5 (Secure Manager)")
                Toast.makeText(this, "⚠️ Setup incomplete! Missing: " + missing.joinToString(", "), Toast.LENGTH_LONG).show()
            }
        }


        setupCategorizedSpinners()
        setupRedirectUI()
        setupSyncAdapter()

        findViewById<EditText>(R.id.etCustomWeb)?.apply {
            setTextColor(Color.parseColor("#121212"))
            setHintTextColor(Color.parseColor("#757575"))
        }
        findViewById<EditText>(R.id.etCustomRedirect)?.apply {
            setTextColor(Color.parseColor("#121212"))
            setHintTextColor(Color.parseColor("#757575"))
        }

        findViewById<Button>(R.id.btnAddWeb).setOnClickListener { 
            val webInput = findViewById<EditText>(R.id.etCustomWeb).text.toString().trim().lowercase()
            if (webInput.isNotEmpty()) {
                if (webInput.matches(Regex("^[0-9]+$"))) {
                    Toast.makeText(
                        this,
                        "Numeric-only blocks are too broad (e.g. blocking a year).",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }

                addBlockItem("BLOCKLIST_WEB", webInput, webInput)
            }
            findViewById<EditText>(R.id.etCustomWeb).setText("") 
        }

        val btnDumbPhoneCamera = findViewById<Button>(R.id.btnDrasticDumbPhoneCamera)
        val btnDumbPhoneNoCamera = findViewById<Button>(R.id.btnDrasticDumbPhoneNoCamera)
        val btnNoInternet = findViewById<Button>(R.id.btnDrasticNoInternet)
        val btnNoVideos = findViewById<Button>(R.id.btnDrasticNoVideos)
        val btnCallsOnly = findViewById<Button>(R.id.btnDrasticCallsOnly)

        fun updateDrasticUI() {
            val isDumbCamera = prefs.getBoolean("DRASTIC_DUMB_PHONE_CAMERA", false)
            val isDumbNoCamera = prefs.getBoolean("DRASTIC_DUMB_PHONE_NO_CAMERA", false)
            val isNoInternet = prefs.getBoolean("DRASTIC_NO_INTERNET", false)
            val isNoVideos = prefs.getBoolean("DRASTIC_NO_VIDEOS", false)
            val isCallsOnly = prefs.getBoolean("DRASTIC_CALLS_ONLY", false)

            btnDumbPhoneCamera.text = if (isDumbCamera) "Dumb Phone (With Camera): ACTIVE" else "Dumb Phone (WITH Camera)"
            btnDumbPhoneCamera.setBackgroundResource(if (isDumbCamera) R.drawable.bg_btn_success else R.drawable.bg_btn_warning)
            
            btnDumbPhoneNoCamera.text = if (isDumbNoCamera) "Dumb Phone (No Camera): ACTIVE" else "Dumb Phone (NO Camera)"
            btnDumbPhoneNoCamera.setBackgroundResource(if (isDumbNoCamera) R.drawable.bg_btn_success else R.drawable.bg_btn_warning)
            
            btnNoInternet.text = if (isNoInternet) "No Internet Mode: ACTIVE" else "Remove Internet From My Phone"
            btnNoInternet.setBackgroundResource(if (isNoInternet) R.drawable.bg_btn_success else R.drawable.bg_btn_warning)

            btnNoVideos.text = if (isNoVideos) "No Videos Mode: ACTIVE" else "Block All Videos System-Wide"
            btnNoVideos.setBackgroundResource(if (isNoVideos) R.drawable.bg_btn_success else R.drawable.bg_btn_warning)

            btnCallsOnly.text = if (isCallsOnly) "Calls/Texts Only: ACTIVE" else "Calls and Texts ONLY"
            btnCallsOnly.setBackgroundResource(if (isCallsOnly) R.drawable.bg_btn_success else R.drawable.bg_btn_danger)
        }

        updateDrasticUI()

        val confirmDrastic = { key: String, title: String, msg: String ->
            val isPauseActive = System.currentTimeMillis() < prefs.getLong("SYSTEM_PAUSE_UNTIL", 0L)
            if (prefs.getBoolean(key, false)) {
                if (isPauseActive) {
                    prefs.edit().putBoolean(key, false).apply(); updateDrasticUI(); Toast.makeText(this, "Drastic Mode Disabled.", Toast.LENGTH_SHORT).show()
                } else { Toast.makeText(this, "Must schedule System Override (Pause) and execute it to disable.", Toast.LENGTH_LONG).show() }
            } else {
                DialogUtils.showCustomDialog(this, title, msg, true, "ENGAGE", { prefs.edit().putBoolean(key, true).apply(); updateDrasticUI() })
            }
        }

        btnDumbPhoneCamera.setOnClickListener { confirmDrastic("DRASTIC_DUMB_PHONE_CAMERA", "Engage Dumb Phone (With Camera)?", "This will block EVERYTHING except Calculator, Calendar, Camera, Gallery, Maps, Weather, Calls, and Texts.\n\nCannot be undone without scheduling a System Override.") }
        btnDumbPhoneNoCamera.setOnClickListener { confirmDrastic("DRASTIC_DUMB_PHONE_NO_CAMERA", "Engage Dumb Phone (No Camera)?", "This will block EVERYTHING except Calculator, Calendar, Maps, Weather, Calls, and Texts.\n\nCamera and Photos will be blocked.\n\nCannot be undone without scheduling a System Override.") }
        btnNoInternet.setOnClickListener { confirmDrastic("DRASTIC_NO_INTERNET", "Remove Internet?", "This will aggressively block ALL web browsers, app stores, and known social media.\n\nCannot be undone without scheduling a System Override.") }
        btnNoVideos.setOnClickListener { confirmDrastic("DRASTIC_NO_VIDEOS", "Block All Videos?", "This will forcefully close video players and explicitly block major video apps system-wide.\n\nCannot be undone without scheduling a System Override.") }
        btnCallsOnly.setOnClickListener { confirmDrastic("DRASTIC_CALLS_ONLY", "Engage Calls/Texts ONLY?", "NUCLEAR OPTION.\n\nYour smartphone will become a brick that can only make phone calls and send SMS text messages.\n\nCannot be undone without scheduling a System Override.") }

        setupSystemOverrideUI(); setupMainNightfallUI(); setupTintUI()
    }

    private fun setupSyncAdapter() {
        val accountName = "RHC_Guard_Account"
        val accountType = "com.rockhard.blocker.account"
        val authority = "com.rockhard.blocker.provider"
        val account = Account(accountName, accountType)
        val am = getSystemService(Context.ACCOUNT_SERVICE) as AccountManager

        try {
            if (am.addAccountExplicitly(account, null, null)) {
                // Initialize background keep-alive system sync parameters (interval: 30 minutes / 1800s)
                ContentResolver.setIsSyncable(account, authority, 1)
                ContentResolver.setSyncAutomatically(account, authority, true)
                ContentResolver.addPeriodicSync(account, authority, Bundle.EMPTY, 1800L)
            }
        } catch (e: Exception) {
            // Fails silently if account exists
        }
    }

    private fun setupRedirectUI() {
        val spinTrigger = findViewById<Spinner>(R.id.spinRedirectTrigger)
        val spinTarget = findViewById<Spinner>(R.id.spinRedirectTarget)
        val etCustom = findViewById<EditText>(R.id.etCustomRedirect)
        val spinCustomApp = findViewById<Spinner>(R.id.spinCustomApp)
        val btnAdd = findViewById<Button>(R.id.btnAddRedirect)

        val triggers = mutableListOf<String>("- Select Blocked App/Website -")
        val blockedApps = prefs.getString("BLOCKLIST_APP", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        val blockedWebs = prefs.getString("BLOCKLIST_WEB", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        (blockedApps + blockedWebs).forEach {
            val name = it.split("|").getOrNull(0) ?: ""
            if (name.isNotEmpty()) triggers.add(name)
        }
        spinTrigger.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, triggers)

        val targets = listOf(
            Pair("- Select Productive Destination -", ""),
            Pair("📚 Wikipedia (Random)", "https://en.wikipedia.org/wiki/Special:Random"),
            Pair("🦉 Duolingo", "package:com.duolingo"),
            Pair("📖 Amazon Kindle", "package:com.amazon.kindle"),
            Pair("📝 Notion", "package:notion.id"),
            Pair("✅ Todoist", "package:com.todoist"),
            Pair("🎓 Khan Academy", "https://khanacademy.org"),
            Pair("🌐 Custom URL...", "CUSTOM_URL"),
            Pair("📱 Custom App...", "CUSTOM_APP")
        )
        spinTarget.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, targets.map { it.first })

        val appDisplayList = mutableListOf("- Select App from Phone -")
        appDisplayList.addAll(allAppsList.map { it.first })
        spinCustomApp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, appDisplayList)

        spinTarget.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                etCustom.visibility = if (targets[position].second == "CUSTOM_URL") View.VISIBLE else View.GONE
                val isCustomApp = targets[position].second == "CUSTOM_APP"
                spinCustomApp.visibility = if (isCustomApp) View.VISIBLE else View.GONE
                if (isCustomApp) {
                    spinCustomApp.post { spinCustomApp.performClick() }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnAdd.setOnClickListener {
            val tIdx = spinTrigger.selectedItemPosition
            val targIdx = spinTarget.selectedItemPosition
            if (tIdx == 0 || targIdx == 0) {
                Toast.makeText(this, "Select both a trigger and a destination.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val trigger = triggers[tIdx]
            var destination = targets[targIdx].second
            
            if (destination == "CUSTOM_URL") {
                destination = etCustom.text.toString().trim()
                if (destination.isEmpty()) { Toast.makeText(this, "Enter a custom URL.", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            } else if (destination == "CUSTOM_APP") {
                val appIdx = spinCustomApp.selectedItemPosition
                if (appIdx == 0) { Toast.makeText(this, "Select a Custom App.", Toast.LENGTH_SHORT).show(); return@setOnClickListener }
                destination = "package:${allAppsList[appIdx - 1].second}"
            }

            val currentStr = prefs.getString("REDIRECTS", "") ?: ""
            val newEntry = "$trigger|$destination"
            prefs.edit().putString("REDIRECTS", if (currentStr.isEmpty()) newEntry else "$currentStr,$newEntry").apply()
            
            Toast.makeText(this, "Habit Substitution Saved!", Toast.LENGTH_SHORT).show()
            etCustom.setText("")
            spinCustomApp.setSelection(0)
            spinTrigger.setSelection(0)
            spinTarget.setSelection(0)
            renderRedirects()
        }
        renderRedirects()
    }

    private fun renderRedirects() {
        val ll = findViewById<LinearLayout>(R.id.llRedirects)
        ll.removeAllViews()
        val data = prefs.getString("REDIRECTS", "") ?: ""
        if (data.isEmpty()) return
        
        data.split(",").filter { it.isNotEmpty() }.forEach { entry ->
            val parts = entry.split("|")
            if (parts.size == 2) {
                ll.addView(TextView(this).apply {
                    text = "⚡ When ${parts[0]} is blocked,\n   ↳ Open ${parts[1]}"
                    setTextColor(Color.parseColor("#4CAF50"))
                    textSize = 12f
                    setPadding(0, 8, 0, 8)
                })
            }
        }
    }

    private fun setupBlockSpinner(spinnerId: Int, prompt: String, items: List<Pair<String, String>>, isApp: Boolean) {
        val spinner = findViewById<Spinner>(spinnerId) ?: return
        val displayList = mutableListOf(prompt)
        displayList.addAll(items.map { it.first })
        
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, displayList)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0) {
                    val selectedDisplay = items[position - 1].first
                    val target = items[position - 1].second
                    if (isApp) addBlockItem("BLOCKLIST_APP", selectedDisplay, target) else addBlockItem("BLOCKLIST_WEB", selectedDisplay, target)
                    spinner.setSelection(0) 
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupCategorizedSpinners() {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        
        val protectedPkgs = listOf(
            "com.android.systemui", "com.android.settings", "com.android.launcher", "nexuslauncher", 
            "com.android.contacts", "com.android.dialer", "com.google.android.dialer", "com.miui.securitycenter", 
            "com.samsung.android.settings", "com.android.permissioncontroller", "com.android.providers",
            "filemanager", "documentsui", "gallery", "camera", "photos", "sec.android.app.myfiles"
        )
        
        val socialApps = mutableListOf<Pair<String, String>>()
        val videoApps = mutableListOf<Pair<String, String>>()
        val newsApps = mutableListOf<Pair<String, String>>()
        val allApps = mutableListOf<Pair<String, String>>()

        
    val blockedAppsStr = prefs.getString("BLOCKLIST_APP", "") ?: ""
    val blockedPkgNames = blockedAppsStr
        .split(",")
        .filter { it.isNotEmpty() }
        .map {
            it.split("|").getOrNull(1)?.trim()?.lowercase()
                ?: it.split("|")[0].trim().lowercase()
        }
for (info in resolveInfos) {
            val appName = info.loadLabel(pm).toString()
            val pkgName = info.activityInfo.packageName
            val lowerPkg = pkgName.lowercase()
            val lowerName = appName.lowercase()
            
            if (
            !protectedPkgs.any { lowerPkg.contains(it) } &&
            pkgName != packageName &&
            !blockedPkgNames.contains(lowerPkg)
        ) {
                val pair = Pair(appName, pkgName)
                allApps.add(pair)
                
                if (listOf("instagram", "facebook", "tiktok", "snapchat", "reddit", "twitter", "x", "threads", "bereal").any { lowerPkg.contains(it) || lowerName.contains(it) }) socialApps.add(pair)
                else if (listOf("youtube", "netflix", "hulu", "twitch", "prime", "disney", "max", "crunchyroll", "video", "player").any { lowerPkg.contains(it) || lowerName.contains(it) }) videoApps.add(pair)
                else if (listOf("cnn", "bbc", "fox", "news", "nytimes", "wsj", "flipboard").any { lowerPkg.contains(it) || lowerName.contains(it) }) newsApps.add(pair)
            }
        }

        allAppsList = allApps.sortedBy { it.first.lowercase() }

        setupBlockSpinner(R.id.spinSocialApps, "📱 Social Apps...", socialApps.sortedBy { it.first.lowercase() }, true)
        setupBlockSpinner(R.id.spinVideoApps, "📺 Video Apps...", videoApps.sortedBy { it.first.lowercase() }, true)
        setupBlockSpinner(R.id.spinNewsApps, "📰 News Apps...", newsApps.sortedBy { it.first.lowercase() }, true)
        setupBlockSpinner(R.id.spinAllApps, "📦 All Apps...", allAppsList, true)
    val blockedWebsStr = prefs.getString("BLOCKLIST_WEB", "") ?: ""
    val blockedWebDomains = blockedWebsStr
        .split(",")
        .filter { it.isNotEmpty() }
        .map {
            it.split("|").getOrNull(1)?.trim()?.lowercase()
                ?: it.split("|")[0].trim().lowercase()
        }



        val socialWeb = listOf(Pair("Instagram", "instagram.com"), Pair("Facebook", "facebook.com"), Pair("TikTok", "tiktok.com"), Pair("Twitter / X", "twitter.com"), Pair("Reddit", "reddit.com")).filter { !blockedWebDomains.contains(it.second.lowercase()) }
        val videoWeb = listOf(Pair("YouTube", "youtube.com"), Pair("Twitch", "twitch.tv"), Pair("Netflix", "netflix.com"), Pair("Hulu", "hulu.com")).filter { !blockedWebDomains.contains(it.second.lowercase()) }
        val newsWeb = listOf(Pair("CNN", "cnn.com"), Pair("Fox News", "foxnews.com"), Pair("BBC", "bbc.com"), Pair("NY Times", "nytimes.com")).filter { !blockedWebDomains.contains(it.second.lowercase()) }
        val searchWeb = listOf(Pair("Google Images", "google.com/search?tbm=isch"), Pair("Bing Video Search", "bing.com/videos"), Pair("Yahoo Search", "search.yahoo.com")).filter { !blockedWebDomains.contains(it.second.lowercase()) }

        setupBlockSpinner(R.id.spinSocialWeb, "🌐 Social Sites...", socialWeb, false)
        setupBlockSpinner(R.id.spinVideoWeb, "🎬 Video Sites...", videoWeb, false)
        setupBlockSpinner(R.id.spinNewsWeb, "🗞️ News Sites...", newsWeb, false)
        setupBlockSpinner(R.id.spinSearchWeb, "🔍 Search Engines...", searchWeb, false)
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

        
    // System Shield Diagnostics Check
    val issues = mutableListOf<String>()

    if (!step1Done) issues.add("Accessibility (Step 1) DISABLED")
    if (!step2Done) issues.add("Device Admin (Step 2) INACTIVE")
    if (!stepBatteryDone) issues.add("Battery Opt. (Step 3) OPTIMIZED")
    if (isChinesePhone && !step4Done) issues.add("Autostart (Step 4) PENDING")
    if (isChinesePhone && !step5Done) issues.add("Secure App Mgr (Step 5) PENDING")

    val shieldIcon = findViewById<TextView>(R.id.tvShieldStatusIcon)

    if (issues.isEmpty()) {
        shieldIcon.text = "🛡️"
    } else {
        shieldIcon.text = "⚠️"
    }

    shieldIcon.setOnClickListener {
        if (issues.isEmpty()) {
            Toast.makeText(
                this,
                "🛡️ SHIELDS FULLY ENGAGED",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                this,
                "⚠️ SHIELDS COMPROMISED:\n" + issues.joinToString("\n"),
                Toast.LENGTH_LONG
            ).show()
        }
    }

btn2.isEnabled = step1Done; btnBatteryOpt.isEnabled = step1Done && step2Done
        btn4?.isEnabled = step1Done && step2Done && stepBatteryDone
        btn5?.isEnabled = step1Done && step2Done && stepBatteryDone && step4Done

        if (step1Done) { btn1.text = "STEP 1: VERIFIED ✔️"; btn1.setBackgroundResource(R.drawable.bg_btn_success) }
        if (step2Done) { btn2.text = "STEP 2: VERIFIED ✔️"; btn2.setBackgroundResource(R.drawable.bg_btn_success) }
        if (stepBatteryDone) { btnBatteryOpt.text = "STEP 3: VERIFIED ✔️"; btnBatteryOpt.setBackgroundResource(R.drawable.bg_btn_success) }
        if (btn4?.visibility == View.VISIBLE && step4Done) { btn4.text = "STEP 4: VERIFIED ✔️"; btn4.setBackgroundResource(R.drawable.bg_btn_success) }
        if (btn5?.visibility == View.VISIBLE && step5Done) { btn5.text = "STEP 5: VERIFIED ✔️"; btn5.setBackgroundResource(R.drawable.bg_btn_success) }

        val isSetupDone = (step1Done && step2Done && stepBatteryDone && (!isChinesePhone || (step4Done && step5Done))) || isPremium

        if (isSetupDone && !isPremium) {
            prefs.edit().putBoolean("REWARD_PREMIUM", true).apply()
            if (BuildConfig.FLAVOR.lowercase().contains("gamers")) {
                val legendary = if (kotlin.random.Random.nextBoolean()) "Aegis,Legendary,250,250,Light Pulse,Nova Shield,Orbital Cannon,0,0,0,0,true,None,0,0,0,0,None,0" else "Titan,Legendary,280,280,Feral Strike,Parry,Apex Predator,0,0,0,0,true,None,0,0,0,0,None,0"
                val partyStr = prefs.getString("PARTY_DATA", "") ?: ""
                prefs.edit().putString("PARTY_DATA", if(partyStr.isEmpty()) legendary else "$partyStr;$legendary").apply()
                Toast.makeText(this, "SYSTEM SECURED! Legendary Netbeast Unlocked!", Toast.LENGTH_LONG).show()
            } else { MomentumEngine.addEarnedMomentum(prefs, "System Secured Bonus", false); Toast.makeText(this, "SYSTEM SECURED! Gained 20 minutes of Momentum!", Toast.LENGTH_LONG).show() }
        }
        
        findViewById<View>(R.id.cardOvercomeApp)?.visibility = if (isSetupDone) View.VISIBLE else View.GONE
        findViewById<View>(R.id.cardOvercomeWeb)?.visibility = if (isSetupDone) View.VISIBLE else View.GONE
        
        // --- NEW: Toggle the Uninstaller button based on setup state ---
        findViewById<View>(R.id.btnSafeAppManager)?.visibility = if (isSetupDone) View.VISIBLE else View.GONE

        if (isSetupDone) { 
            renderBlockList(findViewById(R.id.llBannedWebs), "BLOCKLIST_WEB")
            renderBlockList(findViewById(R.id.llBannedApps), "BLOCKLIST_APP")
                    setupCategorizedSpinners() // Dynamically re-filter dropdowns to hide banned/uninstalled apps
setupRedirectUI() // Repopulate redirect dropdowns with fresh blocklists
        }
        if (isPremium) findViewById<View>(R.id.llOnboarding).visibility = View.GONE
    } 

    private fun openSettingsMenu() {
        DialogUtils.showCustomDialog(this, "Settings", null, true, "SAVE", null) { content, dialog ->
            val isGamers = BuildConfig.FLAVOR.lowercase().contains("gamers")
            val cbGame = CheckBox(this).apply { text = if(isGamers) "Enable Gamification" else "Enable Momentum Tracking"; isChecked = prefs.getBoolean("GAMIFICATION", true); setTextColor(android.graphics.Color.WHITE); textSize = 16f; setPadding(16, 16, 16, 16) }
            val cbDefault = CheckBox(this).apply { text = if(isGamers) "Set Netbeasts as Default Home App" else "Set Momentum as Default Home App"; isChecked = prefs.getBoolean("LAUNCH_GAME_DEFAULT", false); setTextColor(android.graphics.Color.WHITE); textSize = 16f; setPadding(16, 16, 16, 16) }
            val cbDebug = CheckBox(this).apply { text = "Enable UI Debugger"; isChecked = prefs.getBoolean("DEBUG_UI_TOASTS", false); setTextColor(android.graphics.Color.YELLOW); textSize = 16f; setPadding(16, 16, 16, 16) }
            
            content.addView(cbDebug)
            content.addView(cbGame)
            content.addView(cbDefault)
            
            // --- FIX: The Safe App Manager button was removed from here ---
            
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
                prefs.edit().putBoolean("GAMIFICATION", cbGame.isChecked).putBoolean("LAUNCH_GAME_DEFAULT", cbDefault.isChecked).putBoolean("DEBUG_UI_TOASTS", cbDebug.isChecked).apply()
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
        
        // Auto-waking foreground accessibility lifecycle on app entry
        val step1Done = isAccessibilityServiceEnabled(this, GuardianService::class.java)
        if (step1Done) {
            GuardianService.addLog("Shield verified active from MainActivity.")
        }

        refreshUI()
        applyInAppGrayscale()
        if (prefs.getBoolean("DEBUG_UI_TOASTS", false)) {
            findViewById<View>(R.id.llDebugTerminal)?.visibility = View.VISIBLE
        }
    }

    
    private fun applyInAppGrayscale() {
        if (prefs.getBoolean("IN_APP_GRAYSCALE", false)) {
            val matrix = ColorMatrix().apply { setSaturation(0f) }
            val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
            window.decorView.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
        } else {
            window.decorView.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }

    private fun setupTintUI() {
        val btnTint = findViewById<Button>(R.id.btnCustomTint) ?: return
        val btnGray = findViewById<Button>(R.id.btnToggleGrayscale) ?: return
        val btnLock = findViewById<Button>(R.id.btnLockTint) ?: return

        fun updateUI() {
            val isGray = prefs.getBoolean("IN_APP_GRAYSCALE", false)
            val isLocked = prefs.getBoolean("TINT_LOCKED", false)
            btnGray.text = if (isGray) "In-App Grayscale: ACTIVE" else "In-App Grayscale: OFF"
            btnGray.setBackgroundResource(if (isGray) R.drawable.bg_btn_success else R.drawable.bg_btn_standard)
            btnLock.text = if (isLocked) "Tint Settings: LOCKED" else "Lock Tint Settings"
            btnLock.setBackgroundResource(if (isLocked) R.drawable.bg_btn_success else R.drawable.bg_btn_warning)
        }
        updateUI()

        val checkLock = {
            val isLocked = prefs.getBoolean("TINT_LOCKED", false)
            val isPauseActive = System.currentTimeMillis() < prefs.getLong("SYSTEM_PAUSE_UNTIL", 0L)
            if (isLocked && !isPauseActive) {
                Toast.makeText(this, "Must schedule System Override (Pause) to modify Tint Settings.", Toast.LENGTH_LONG).show()
                true
            } else false
        }

        btnGray.setOnClickListener {
            if (checkLock()) return@setOnClickListener
            prefs.edit().putBoolean("IN_APP_GRAYSCALE", !prefs.getBoolean("IN_APP_GRAYSCALE", false)).apply()
            updateUI()
            applyInAppGrayscale()
        }

        btnLock.setOnClickListener {
            if (checkLock()) return@setOnClickListener
            val isLocked = prefs.getBoolean("TINT_LOCKED", false)
            if (!isLocked) {
                DialogUtils.showCustomDialog(this, "Lock Tint Settings?", "You will not be able to change or turn off the screen tint or grayscale without a System Override.", true, "LOCK IT", {
                    prefs.edit().putBoolean("TINT_LOCKED", true).apply()
                    updateUI()
                })
            } else {
                prefs.edit().putBoolean("TINT_LOCKED", false).apply()
                updateUI()
                Toast.makeText(this, "Tint Settings Unlocked.", Toast.LENGTH_SHORT).show()
            }
        }

        btnTint.setOnClickListener {
            if (checkLock()) return@setOnClickListener
            DialogUtils.showCustomDialog(this, "Custom Screen Tint", null, true, "SAVE", null) { content, dialog ->
                val ll = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16) }
                
                val savedColor = prefs.getInt("CUSTOM_TINT_COLOR", 0)
                var a = Color.alpha(savedColor); var r = Color.red(savedColor); var g = Color.green(savedColor); var b = Color.blue(savedColor)

                fun addSlider(name: String, maxVal: Int, startVal: Int, onChange: (Int) -> Unit) {
                    ll.addView(TextView(this).apply { text = name; setTextColor(Color.WHITE); setPadding(0, 16, 0, 4) })
                    val seek = SeekBar(this).apply { max = maxVal; progress = startVal }
                    seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { onChange(p) }
                        override fun onStartTrackingTouch(s: SeekBar?) {}
                        override fun onStopTrackingTouch(s: SeekBar?) {}
                    })
                    ll.addView(seek)
                }

                addSlider("Intensity (Opacity)", 200, a) { a = it; prefs.edit().putInt("CUSTOM_TINT_COLOR", Color.argb(a, r, g, b)).apply() }
                addSlider("Red", 255, r) { r = it; prefs.edit().putInt("CUSTOM_TINT_COLOR", Color.argb(a, r, g, b)).apply() }
                addSlider("Green", 255, g) { g = it; prefs.edit().putInt("CUSTOM_TINT_COLOR", Color.argb(a, r, g, b)).apply() }
                addSlider("Blue", 255, b) { b = it; prefs.edit().putInt("CUSTOM_TINT_COLOR", Color.argb(a, r, g, b)).apply() }

                content.addView(ll)
                dialog.findViewById<Button>(R.id.btnDialogPositive)?.setOnClickListener { dialog.dismiss() }
            }
        }
    }


    private var isNightfallNewlyEnabled = false

    private fun setupMainNightfallUI() {
        val cbNightfall = findViewById<CheckBox>(R.id.cbMainNightfall) ?: return
        val llTimes = findViewById<LinearLayout>(R.id.llMainNightfallTimes) ?: return
        val btnStart = findViewById<Button>(R.id.btnMainNightfallStart) ?: return
        val btnEnd = findViewById<Button>(R.id.btnMainNightfallEnd) ?: return

    val cbNightfallNotes = findViewById<CheckBox>(R.id.cbNightfallNotes)

    if (cbNightfallNotes != null) {
        cbNightfallNotes.isChecked =
            prefs.getBoolean("NIGHTFALL_ALLOW_NOTES", true)

        cbNightfallNotes.setOnCheckedChangeListener { buttonView, isChecked ->
            val isPauseActive =
                System.currentTimeMillis() <
                prefs.getLong("SYSTEM_PAUSE_UNTIL", 0L)

            // If they try to re-tick it without an override,
            // immediately turn it back off.
            if (isChecked && !isPauseActive) {
                buttonView.setOnCheckedChangeListener(null)
                buttonView.isChecked = false

                setupMainNightfallUI()

                Toast.makeText(
                    this,
                    "Must schedule System Override (Pause) to allow Notes again.",
                    Toast.LENGTH_LONG
                ).show()

                return@setOnCheckedChangeListener
            }

            prefs.edit()
                .putBoolean("NIGHTFALL_ALLOW_NOTES", isChecked)
                .apply()
        }
    }



        cbNightfall.isChecked = prefs.getInt("NIGHTFALL_START", -1) != -1
        llTimes.visibility = if (cbNightfall.isChecked) View.VISIBLE else View.GONE

        fun formatTime(mins: Int): String {
            if (mins == -1) return "Not Set"
            val h = mins / 60
            val m = mins % 60
            val ampm = if (h >= 12) "PM" else "AM"
            val h12 = if (h % 12 == 0) 12 else h % 12
            return String.format("%02d:%02d %s", h12, m, ampm)
        }

        var startMins = prefs.getInt("NIGHTFALL_START", 1320)
        var endMins = prefs.getInt("NIGHTFALL_END", 360)
        if (startMins == -1) startMins = 1320
        if (endMins == -1) endMins = 360

        btnStart.text = "Start: " + formatTime(startMins)
        btnEnd.text = "End: " + formatTime(endMins)

        cbNightfall.setOnCheckedChangeListener { buttonView, isChecked ->
            val wasEnabled = prefs.getInt("NIGHTFALL_START", -1) != -1
            val isPauseActive = System.currentTimeMillis() < prefs.getLong("SYSTEM_PAUSE_UNTIL", 0L)

            if (wasEnabled && !isChecked && !isPauseActive) {
                buttonView.setOnCheckedChangeListener(null)
                buttonView.isChecked = true
                setupMainNightfallUI()
                Toast.makeText(this, "Must schedule System Override (Pause) and execute it to disable Nightfall.", Toast.LENGTH_LONG).show()
                return@setOnCheckedChangeListener
            }

            if (isChecked && !wasEnabled) {
                isNightfallNewlyEnabled = true
            }

            llTimes.visibility = if (isChecked) View.VISIBLE else View.GONE
            val editor = prefs.edit()
            if (isChecked) {
                editor.putInt("NIGHTFALL_START", startMins)
                editor.putInt("NIGHTFALL_END", endMins)
            } else {
                editor.putInt("NIGHTFALL_START", -1)
                editor.putInt("NIGHTFALL_END", -1)
                isNightfallNewlyEnabled = false
            }
            editor.apply()
        }

        btnStart.setOnClickListener {
            val isCurrentlyEnabled = prefs.getInt("NIGHTFALL_START", -1) != -1
            val isPauseActive = System.currentTimeMillis() < prefs.getLong("SYSTEM_PAUSE_UNTIL", 0L)
            if (isCurrentlyEnabled && !isPauseActive && !isNightfallNewlyEnabled) {
                Toast.makeText(this, "Must schedule System Override (Pause) and execute it to modify Nightfall.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val h = startMins / 60
            val m = startMins % 60
            android.app.TimePickerDialog(this, { _, selectedHour, selectedMinute ->
                startMins = selectedHour * 60 + selectedMinute
                btnStart.text = "Start: " + formatTime(startMins)
                if (cbNightfall.isChecked) prefs.edit().putInt("NIGHTFALL_START", startMins).apply()
            }, h, m, false).show()
        }

        btnEnd.setOnClickListener {
            val isCurrentlyEnabled = prefs.getInt("NIGHTFALL_START", -1) != -1
            val isPauseActive = System.currentTimeMillis() < prefs.getLong("SYSTEM_PAUSE_UNTIL", 0L)
            if (isCurrentlyEnabled && !isPauseActive && !isNightfallNewlyEnabled) {
                Toast.makeText(this, "Must schedule System Override (Pause) and execute it to modify Nightfall.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val h = endMins / 60
            val m = endMins % 60
            android.app.TimePickerDialog(this, { _, selectedHour, selectedMinute ->
                endMins = selectedHour * 60 + selectedMinute
                btnEnd.text = "End: " + formatTime(endMins)
                if (cbNightfall.isChecked) prefs.edit().putInt("NIGHTFALL_END", endMins).apply()
            }, h, m, false).show()
        }
    }
}
