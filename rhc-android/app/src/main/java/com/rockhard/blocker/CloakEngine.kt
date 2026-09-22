package com.rockhard.blocker

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

object CloakEngine {
    fun cloak(context: Context) {
        val pm = context.packageManager
        try {
            pm.setComponentEnabledSetting(ComponentName(context, "com.rockhard.blocker.GameLauncher"), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(ComponentName(context, "com.rockhard.blocker.DefaultLauncher"), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(ComponentName(context, "com.rockhard.blocker.CloakLauncher"), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        } catch (e: Exception) {}
    }

    fun uncloak(context: Context, useGameIcon: Boolean) {
        val pm = context.packageManager
        try {
            pm.setComponentEnabledSetting(ComponentName(context, "com.rockhard.blocker.CloakLauncher"), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            if (useGameIcon) {
                pm.setComponentEnabledSetting(ComponentName(context, "com.rockhard.blocker.DefaultLauncher"), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                pm.setComponentEnabledSetting(ComponentName(context, "com.rockhard.blocker.GameLauncher"), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            } else {
                pm.setComponentEnabledSetting(ComponentName(context, "com.rockhard.blocker.GameLauncher"), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                pm.setComponentEnabledSetting(ComponentName(context, "com.rockhard.blocker.DefaultLauncher"), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            }
        } catch (e: Exception) {}
    }
}
