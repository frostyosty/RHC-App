package com.rockhard.blocker.homevisits.ar

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager

object CameraPermission {
    const val REQUEST_CODE = 9001

    fun hasPermission(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    fun requestPermission(activity: Activity) {
        // Bypass the Guardian temporarily to allow the system permission prompt
        val prefs = activity.getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE)
        prefs.edit().putLong("ALLOW_PERMISSION_PROMPT_UNTIL", System.currentTimeMillis() + 60000L).apply()

        activity.requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CODE)
    }

    fun onPermissionResult(activity: Activity): Boolean {
        // Clear the Guardian bypass once prompt finishes
        val prefs = activity.getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE)
        prefs.edit().remove("ALLOW_PERMISSION_PROMPT_UNTIL").apply()
        
        return hasPermission(activity)
    }
}
