package com.rockhard.blocker
import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE)

        // AUTO-LAUNCH NETBEASTS IF DEFAULT IS SET
        if (prefs.getBoolean("LAUNCH_GAME_DEFAULT", false) && !intent.getBooleanExtra("FROM_GAME", false)) {
            startActivity(Intent(this, GameActivity::class.java))
            finish(); return
        }

        setContentView(R.layout.activity_main)
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        compName = ComponentName(this, AdminReceiver::class.java)

        if (!prefs.getBoolean("INITIALIZED", false)) {
            val starterParty = "Cacheon,Tech,120,120,Digital Swipe,Overclock,0,0,0,0,false,None,0;Cardiol,Fitness,150,150,Momentum,Heavy Lift,0,0,0,0,false,None,0"
            prefs.edit().putString("PARTY_DATA", starterParty).putInt("NETS", 5).putInt("SPRAYS", 2).putInt("POTIONS", 3).putBoolean("INITIALIZED", true).apply()
        }

        findViewById<Button>(R.id.btnSettings).setOnClickListener { openSettingsMenu() }
        findViewById<Button>(R.id.btnStep1).setOnClickListener { if (!isAccessibilityServiceEnabled(this, GuardianService::class.java)) { AlertDialog.Builder(this).setTitle("Step 1: Turn on the Guardian").setMessage("Tap 'Downloaded apps' -> Find '${getString(R.string.app_name)}' -> Turn ON.").setPositiveButton("GOT IT") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }.show() } }
        findViewById<Button>(R.id.btnStep2).setOnClickListener { if (!dpm.isAdminActive(compName)) { AlertDialog.Builder(this).setTitle("Step 2: Lock the App").setMessage("This prevents the app from being uninstalled.").setPositiveButton("GOT IT") { _, _ -> startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply { putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName); putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Locks app down.") }) }.show() } }
        findViewById<Button>(R.id.btnStep3).setOnClickListener { val clip = ClipData.newPlainText("DNS", "adult-filter-dns.cleanbrowsing.org"); (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip); AlertDialog.Builder(this).setTitle("Step 3: The Web Filter").setMessage("We copied the secure address to your clipboard!\n\nFind 'Private DNS' -> Select 'Custom' -> Paste the text.").setPositiveButton("GOT IT") { _, _ -> startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }.show() }
        findViewById<Button>(R.id.btnTestFilter).setOnClickListener { Thread { try { val conn = URL("https://playboy.com").openConnection() as HttpURLConnection; conn.connectTimeout = 3000; conn.connect(); runOnUiThread { Toast.makeText(this, "DNS Not Working Yet.", Toast.LENGTH_SHORT).show() } } catch (e: Exception) { prefs.edit().putBoolean("DNS_VERIFIED", true).apply(); runOnUiThread { refreshUI() } } }.start() }
        findViewById<Button>(R.id.btnAddBlock).setOnClickListener { val word = findViewById<EditText>(R.id.etCustomBlock).text.toString().trim().lowercase(); if (word.isNotEmpty()) { val currentList = prefs.getString("BLOCKLIST", "") ?: ""; prefs.edit().putString("BLOCKLIST", "$currentList,$word").apply(); findViewById<EditText>(R.id.etCustomBlock).setText(""); Toast.makeText(this, "Permanently overcome: $word", Toast.LENGTH_SHORT).show() } }
        findViewById<Button>(R.id.btnGame).setOnClickListener { startActivity(Intent(this, GameActivity::class.java)) }
        findViewById<Button>(R.id.btnDebugReset).setOnClickListener { prefs.edit().clear().apply(); Toast.makeText(this, "SAVE WIPED.", Toast.LENGTH_LONG).show(); finish(); startActivity(intent) }
        findViewById<Button>(R.id.btnDebugUninstall).setOnClickListener { GuardianService.pauseUntil = System.currentTimeMillis() + (10 * 60 * 1000); if (dpm.isAdminActive(compName)) dpm.removeActiveAdmin(compName); startActivity(Intent(Intent.ACTION_DELETE).apply { data = Uri.parse("package:$packageName") }) }
    }

    private fun openSettingsMenu() {
        val view = layoutInflater.inflate(R.layout.activity_main, null) // Cheat for quick layout
        val cbGame = CheckBox(this).apply { text = "Enable Gamification"; isChecked = prefs.getBoolean("GAMIFICATION", true); setTextColor(Color.WHITE) }
        val cbDefault = CheckBox(this).apply { text = "Set Netbeasts as Default Home App"; isChecked = prefs.getBoolean("LAUNCH_GAME_DEFAULT", false); setTextColor(Color.WHITE) }
        
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40); addView(cbGame); addView(cbDefault) }
        
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle("Settings").setView(layout)
            .setPositiveButton("Save") { _, _ -> 
                prefs.edit().putBoolean("GAMIFICATION", cbGame.isChecked).putBoolean("LAUNCH_GAME_DEFAULT", cbDefault.isChecked).apply()
                swapAppIcon(cbDefault.isChecked)
            }.show()
    }

    private fun swapAppIcon(useGameIcon: Boolean) {
        val pm = packageManager
        val defaultAlias = ComponentName(this, "com.rockhard.blocker.DefaultLauncher")
        val gameAlias = ComponentName(this, "com.rockhard.blocker.GameLauncher")
        
        val currentDefault = pm.getComponentEnabledSetting(defaultAlias)
        
        if (useGameIcon && currentDefault != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            pm.setComponentEnabledSetting(defaultAlias, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(gameAlias, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            Toast.makeText(this, "Icon swapped to Netbeasts! (May take a moment to update on home screen)", Toast.LENGTH_LONG).show()
        } else if (!useGameIcon && currentDefault != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
            pm.setComponentEnabledSetting(gameAlias, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(defaultAlias, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            Toast.makeText(this, "Icon swapped to RHC Default!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() { super.onResume(); refreshUI() }

    private fun refreshUI() {
        val btn1 = findViewById<Button>(R.id.btnStep1); val btn2 = findViewById<Button>(R.id.btnStep2); val btn3 = findViewById<Button>(R.id.btnStep3)
        val step1Done = isAccessibilityServiceEnabled(this, GuardianService::class.java)
        val step2Done = dpm.isAdminActive(compName)
        val step3Done = prefs.getBoolean("DNS_VERIFIED", false)

        if (prefs.getBoolean("REWARD_PREMIUM", false)) { findViewById<View>(R.id.llOnboarding).visibility = View.GONE; return }

        if (step1Done) { btn1.text = "STEP 1: VERIFIED"; btn1.setBackgroundResource(R.drawable.bg_btn_success) }
        if (step2Done) { btn2.text = "STEP 2: VERIFIED"; btn2.setBackgroundResource(R.drawable.bg_btn_success) }
        if (step3Done) { btn3.text = "STEP 3: VERIFIED"; btn3.setBackgroundResource(R.drawable.bg_btn_success) }

        if (step1Done && step2Done && step3Done && !prefs.getBoolean("REWARD_PREMIUM", false)) {
            prefs.edit().putBoolean("REWARD_PREMIUM", true).apply()
            Toast.makeText(this, "SYSTEM SECURED! Premium Netbeast Unlocked!", Toast.LENGTH_LONG).show()
            findViewById<View>(R.id.llOnboarding).visibility = View.GONE
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, accessibilityService: Class<*>): Boolean {
        val expected = ComponentName(context, accessibilityService)
        val setting = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(setting)
        while (splitter.hasNext()) { if (ComponentName.unflattenFromString(splitter.next()) == expected) return true }
        return false
    }
}
