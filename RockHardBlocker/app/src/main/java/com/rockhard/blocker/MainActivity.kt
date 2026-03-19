package com.rockhard.blocker

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import android.net.VpnService
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri

class MainActivity : Activity() {

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var componentName: ComponentName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        componentName = ComponentName(this, AdminReceiver::class.java)

        // 1. ENGAGE VPN
        findViewById<Button>(R.id.btnEnableGuardian).setOnClickListener {
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent != null) {
                startActivityForResult(vpnIntent, 0)
            } else {
                startVpnEngine()
            }
        }

        // 2. NEW: LOCK THE APP (Device Admin)
        val btnLockApp = findViewById<Button>(R.id.btnTrapAudio)
        btnLockApp.text = "2. LOCK THE APP (PREVENT UNINSTALL)"
        btnLockApp.backgroundTintList = getColorStateList(android.R.color.holo_blue_dark)
        
        btnLockApp.setOnClickListener {
            if (!devicePolicyManager.isAdminActive(componentName)) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Granting this prevents the app from being uninstalled during a moment of weakness.")
                startActivity(intent)
            } else {
                Toast.makeText(this, "App is already locked!", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. EMERGENCY UNBLOCK (BRICK)
        findViewById<Button>(R.id.btnTrapBrick).setOnClickListener {
            startActivity(Intent(this, TrapActivity::class.java))
        }

        // 4. DEBUG UNINSTALL (We'll keep it for your testing, but added the proper permissions to the Manifest next)
        findViewById<Button>(R.id.btnDebugUninstall).setOnClickListener {
            GuardianService.pauseUntil = System.currentTimeMillis() + (10 * 60 * 1000)
            
            // If Admin is active, we MUST remove it first, otherwise Android ignores the uninstall request
            if (devicePolicyManager.isAdminActive(componentName)) {
                devicePolicyManager.removeActiveAdmin(componentName)
            }
            
            val intent = Intent(Intent.ACTION_DELETE)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            startVpnEngine()
        }
    }

    private fun startVpnEngine() {
        startService(Intent(this, BlockerVpnService::class.java))
        Toast.makeText(this, "The Blocker is active.", Toast.LENGTH_SHORT).show()
    }
}
