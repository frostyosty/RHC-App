package com.rockhard.blocker

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val isServiceRunning = isAccessibilityServiceEnabled(context, GuardianService::class.java)
        
        if (!isServiceRunning) {
            fireAlert(context)
        } else {
            // Service is active and system-managed. No manual starting needed.
        }
        
        scheduleWatchdog(context) 
    }

    companion object {
        fun isAccessibilityServiceEnabled(context: Context, accessibilityService: Class<*>): Boolean {
            val expected = ComponentName(context, accessibilityService)
            val setting = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(setting)
            while (splitter.hasNext()) {
                if (ComponentName.unflattenFromString(splitter.next()) == expected) return true
            }
            return false
        }

        fun fireAlert(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "watchdog_channel"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, "Shield Alerts", NotificationManager.IMPORTANCE_HIGH)
                nm.createNotificationChannel(channel)
            }
            
            val contentIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            val pendingIntent = PendingIntent.getActivity(context, 0, contentIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

            val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.app.Notification.Builder(context, channelId)
            } else {
                @Suppress("DEPRECATION") android.app.Notification.Builder(context)
            }

            val notification = builder.setContentTitle("⚠️ SHIELD DOWN ⚠️")
                .setContentText("Your protection has been disabled! Tap to reactivate.")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            nm.notify(9999, notification)
        }

        fun scheduleWatchdog(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, WatchdogReceiver::class.java)
            val pi = PendingIntent.getBroadcast(context, 1001, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            
            val triggerAt = System.currentTimeMillis() + (15 * 60 * 1000L)
            
            try {
                // FIX: Use AlarmClock API to completely bypass Android 14+ Doze Mode
                // and bypass the SCHEDULE_EXACT_ALARM permission restrictions.
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    val info = AlarmManager.AlarmClockInfo(triggerAt, pi)
                    am.setAlarmClock(info, pi)
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            } catch (e: Exception) {
                // Ultimate Fallback
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            }
        }
    }
}
