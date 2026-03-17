package com.rockhard.blocker

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import android.app.Activity

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. VPN BUTTON
        findViewById<Button>(R.id.btnEnableGuardian).setOnClickListener {
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent != null) {
                startActivityForResult(vpnIntent, 0)
            } else {
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

        // 4. THE DEBUG UNINSTALL BUTTON
        findViewById<Button>(R.id.btnDebugUninstall).setOnClickListener {
            // First, force the Guardian to sleep for 10 minutes so it doesn't fight us
            GuardianService.pauseUntil = System.currentTimeMillis() + (10 * 60 * 1000)
            
            // Trigger the Android native uninstall prompt
            val intent = Intent(Intent.ACTION_DELETE)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            startVpnEngine()
        } else {
            Toast.makeText(this, "You must grant VPN permission!", Toast.LENGTH_LONG).show()
        }
    }

    private fun startVpnEngine() {
        startService(Intent(this, BlockerVpnService::class.java))
        Toast.makeText(this, "The Blocker is active. Target apps are dead.", Toast.LENGTH_LONG).show()
    }
}
