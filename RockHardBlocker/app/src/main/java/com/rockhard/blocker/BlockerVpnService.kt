package com.rockhard.blocker

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.widget.Toast

class BlockerVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // If the VPN is already running, stop it before starting a new one
        vpnInterface?.close()

        val builder = Builder()
        
        // Give our fake VPN an IP address
        builder.addAddress("10.10.10.10", 24)
        
        // Route ALL internet traffic through this VPN
        builder.addRoute("0.0.0.0", 0)

        // ==========================================
        // THE HIT LIST (The Blackhole Targets)
        // Add the package names of apps you want to completely kill.
        // ==========================================
        val appsToKill = listOf(
                 // X / Twitter
                // Reddit
            "org.telegram.messenger"   // Telegram
        )

        var appsIntercepted = 0
        for (app in appsToKill) {
            try {
                // By adding them as an "Allowed Application", ONLY these apps
                // get sent into the VPN. Everything else bypasses it!
                builder.addAllowedApplication(app)
                appsIntercepted++
            } catch (e: Exception) {
                // App is not installed on the phone, skip it.
            }
        }

        // If none of the targeted apps are installed, we don't need to run.
        if (appsIntercepted > 0) {
            builder.setSession("Rock Hard Blocker")
            // Establish the VPN! This shows the 'Key' icon in the Android status bar.
            vpnInterface = builder.establish()
        }

        // We return START_STICKY so the OS knows to keep this running in the background
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        vpnInterface?.close()
        vpnInterface = null
    }
}
