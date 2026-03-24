package com.rockhard.blocker

import android.app.Activity
import android.app.AlertDialog
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
import android.widget.*
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {
    private lateinit var dpm: DevicePolicyManager
    private lateinit var compName: ComponentName
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        compName = ComponentName(this, AdminReceiver::class.java)
        prefs = getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE)

        if (!prefs.getBoolean("INITIALIZED", false)) {
            val starterParty = "Cacheon,Tech,120,120,Digital Swipe,Overclock;Cardiol,Fitness,150,150,Momentum,Heavy Lift"
            prefs.edit().putString("PARTY_DATA", starterParty).putInt("NETS", 5).putInt("SPRAYS", 2).putInt("POTIONS", 3).putBoolean("INITIALIZED", true).apply()
        }

        val cbGamification = findViewById<CheckBox>(R.id.cbGamification)
        cbGamification.isChecked = prefs.getBoolean("GAMIFICATION", true)
        cbGamification.setOnCheckedChangeListener { _, isChecked -> prefs.edit().putBoolean("GAMIFICATION", isChecked).apply() }

        findViewById<Button>(R.id.btnStep1).setOnClickListener {
            if (!isAccessibilityServiceEnabled(this, GuardianService::class.java)) {
                AlertDialog.Builder(this).setTitle("Step 1: Turn on the Guardian").setMessage("Tap 'Downloaded apps' -> Find '${getString(R.string.app_name)}' -> Turn ON.").setPositiveButton("GOT IT") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }.show()
            }
        }
        findViewById<Button>(R.id.btnStep2).setOnClickListener {
            if (!dpm.isAdminActive(compName)) {
                AlertDialog.Builder(this).setTitle("Step 2: Lock the App").setMessage("This prevents the app from being uninstalled.").setPositiveButton("GOT IT") { _, _ -> startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply { putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, compName); putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Locks app down.") }) }.show()
            }
        }
        findViewById<Button>(R.id.btnStep3).setOnClickListener {
            val clip = ClipData.newPlainText("DNS", "adult-filter-dns.cleanbrowsing.org")
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
            AlertDialog.Builder(this).setTitle("Step 3: The Web Filter").setMessage("We copied the secure address to your clipboard!\n\nFind 'Private DNS' -> Select 'Custom' -> Paste the text.").setPositiveButton("GOT IT") { _, _ -> startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }.show()
        }
        findViewById<Button>(R.id.btnTestFilter).setOnClickListener {
            Toast.makeText(this, "Verifying DNS...", Toast.LENGTH_SHORT).show()
            Thread {
                try {
                    val conn = URL("https://playboy.com").openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000; conn.connect()
                    runOnUiThread { Toast.makeText(this, "DNS Not Working Yet. Try again.", Toast.LENGTH_LONG).show() }
                } catch (e: Exception) {
                    prefs.edit().putBoolean("DNS_VERIFIED", true).apply()
                    runOnUiThread { checkRewardsAndRefresh() }
                }
            }.start()
        }
        findViewById<Button>(R.id.btnAddBlock).setOnClickListener {
            val word = findViewById<EditText>(R.id.etCustomBlock).text.toString().trim().lowercase()
            if (word.isNotEmpty()) {
                val currentList = prefs.getString("BLOCKLIST", "") ?: ""
                prefs.edit().putString("BLOCKLIST", "$currentList,$word").apply()
                findViewById<EditText>(R.id.etCustomBlock).setText("")
                Toast.makeText(this, "Permanently overcome: $word", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.btnGame).setOnClickListener { startActivity(Intent(this, GameActivity::class.java)) }

        findViewById<Button>(R.id.btnDebugReset).setOnClickListener {
            prefs.edit().clear().apply()
            Toast.makeText(this, "SAVE FILE WIPED. Restarting app...", Toast.LENGTH_LONG).show()
            finish(); startActivity(intent)
        }

        findViewById<Button>(R.id.btnDebugUninstall).setOnClickListener {
            GuardianService.pauseUntil = System.currentTimeMillis() + (10 * 60 * 1000)
            if (dpm.isAdminActive(compName)) dpm.removeActiveAdmin(compName)
            startActivity(Intent(Intent.ACTION_DELETE).apply { data = Uri.parse("package:$packageName") })
        }
    }

    override fun onResume() { super.onResume(); checkRewardsAndRefresh() }

    private fun checkRewardsAndRefresh() {
        val btn1 = findViewById<Button>(R.id.btnStep1); val btn2 = findViewById<Button>(R.id.btnStep2); val btn3 = findViewById<Button>(R.id.btnStep3)
        val llOnboarding = findViewById<View>(R.id.llOnboarding)

        val step1Done = isAccessibilityServiceEnabled(this, GuardianService::class.java)
        val step2Done = dpm.isAdminActive(compName)
        val step3Done = prefs.getBoolean("DNS_VERIFIED", false)

        if (prefs.getBoolean("REWARD_PREMIUM", false)) { llOnboarding.visibility = View.GONE; return }

        if (step1Done) { btn1.text = "STEP 1: VERIFIED"; btn1.setBackgroundResource(R.drawable.bg_btn_success)
            if (!prefs.getBoolean("REWARD_1", false)) grantReward(btn1, "REWARD_1", "NETS", 1, "You got 1 Capture Net!") }
        if (step2Done) { btn2.text = "STEP 2: VERIFIED"; btn2.setBackgroundResource(R.drawable.bg_btn_success)
            if (!prefs.getBoolean("REWARD_2", false)) grantReward(btn2, "REWARD_2", "SPRAYS", 1, "You got 1 Repel Spray!") }
        if (step3Done) { btn3.text = "STEP 3: VERIFIED"; btn3.setBackgroundResource(R.drawable.bg_btn_success)
            if (!prefs.getBoolean("REWARD_3", false)) grantReward(btn3, "REWARD_3", "POTIONS", 1, "You got 1 Health Potion!") }

        if (step1Done && step2Done && step3Done && !prefs.getBoolean("REWARD_PREMIUM", false)) {
            Handler(Looper.getMainLooper()).postDelayed({
                btn1.setBackgroundColor(Color.WHITE); btn2.setBackgroundColor(Color.WHITE); btn3.setBackgroundColor(Color.WHITE)
                Toast.makeText(this, "SYSTEM SECURED! Premium Netbeast Unlocked!", Toast.LENGTH_LONG).show()
                val currentParty = prefs.getString("PARTY_DATA", "") ?: ""
                prefs.edit().putString("PARTY_DATA", "$currentParty;AuraBeast,Legendary,200,200,Hyper Beam,Protect").putBoolean("REWARD_PREMIUM", true).apply()
                Handler(Looper.getMainLooper()).postDelayed({ llOnboarding.visibility = View.GONE }, 2000)
            }, 1500)
        }
    }

    private fun grantReward(btn: Button, rewardKey: String, itemKey: String, amount: Int, msg: String) {
        prefs.edit().putBoolean(rewardKey, true).apply()
        prefs.edit().putInt(itemKey, prefs.getInt(itemKey, 0) + amount).apply()
        Handler(Looper.getMainLooper()).postDelayed({
            btn.setBackgroundColor(Color.WHITE); btn.setTextColor(Color.BLACK); Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            Handler(Looper.getMainLooper()).postDelayed({ btn.setBackgroundResource(R.drawable.bg_btn_success); btn.setTextColor(Color.WHITE) }, 800)
        }, 500)
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
