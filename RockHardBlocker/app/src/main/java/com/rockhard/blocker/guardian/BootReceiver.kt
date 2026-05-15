package com.rockhard.blocker.guardian

import android.content.*
import com.rockhard.blocker.MainActivity

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val i = Intent(context, MainActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            try { context.startActivity(i) } catch(e: Exception){}
        } else if (intent.action == Intent.ACTION_USER_PRESENT) {
            // HACK: Silently wake the process on every phone unlock so Chinese OEMs don't kill the Guardian!
            com.rockhard.blocker.GuardianService.addLog("Device Unlocked. Process kept warm.")
        }
    }
}