package com.rockhard.blocker

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import android.app.Activity

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. VPN BUTTON
        findViewById<Button>(R.id.btnEnableGuardian).text = "1. ENGAGE THE BLOCKER (VPN)"
        findViewById<Button>(R.id.btnEnableGuardian).setOnClickListener {
            // Android requires us to ask the OS for permission to start a VPN
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent != null) {
                // The OS will pop up a scary warning saying "This app wants to intercept network traffic"
                startActivityForResult(vpnIntent, 0)
            } else {
                // If it returns null, we already have permission! Start the engine.
                startVpnEngine()
            }
        }

        // 2. AUDIO TRAP
        findViewById<Button>(R.id.btnTrapAudio).setOnClickListener {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarm, 0)
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            RingtoneManager.getRingtone(applicationContext, alarmUri).play()
        }

        // 3. BRICK TRAP
        findViewById<Button>(R.id.btnTrapBrick).setOnClickListener {
            startActivity(Intent(this, TrapActivity::class.java))
        }
    }

    // Handles the user clicking "OK" on the Android VPN permission popup
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            startVpnEngine()
        } else {
            Toast.makeText(this, "You must grant VPN permission!", Toast.LENGTH_LONG).show()
        }
    }

    private fun startVpnEngine() {
        val intent = Intent(this, BlockerVpnService::class.java)
        startService(intent)
        Toast.makeText(this, "The Blocker is active. Target apps are dead.", Toast.LENGTH_LONG).show()
    }
}
