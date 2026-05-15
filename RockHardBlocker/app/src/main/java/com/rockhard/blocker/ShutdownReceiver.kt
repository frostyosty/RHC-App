package com.rockhard.blocker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ShutdownReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_SHUTDOWN || intent.action == "android.intent.action.QUICKBOOT_POWEROFF") {
            CloakEngine.cloak(context)
        }
    }
}
