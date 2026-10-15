package com.rockhard.blocker

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.widget.Toast

/**
 * The one way to ask for an Android permission. Android's prompt comes from the permission
 * controller, which the Guardian blocks as anti-tamper, so ShieldRuleEngine lets that package (and
 * only that one) through while ALLOW_PERMISSION_PROMPT_UNTIL is in the future. [request] sets it and
 * [onResult] clears it; the activity's onRequestPermissionsResult must call [onResult].
 */
object PermissionGate {
    private const val PROMPT_UNTIL = "ALLOW_PERMISSION_PROMPT_UNTIL" // read by ShieldRuleEngine
    private const val PROMPT_MS = 60_000L                           // how long the Guardian lets the prompt through at most

    private var askedAt = 0L

    /** Whether any of [permissions] is granted (location asks for precise or approximate, and either will do). */
    fun has(context: Context, permissions: Array<String>) =
        permissions.any { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    fun request(activity: Activity, permissions: Array<String>, code: Int) {
        activity.getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE).edit()
            .putLong(PROMPT_UNTIL, System.currentTimeMillis() + PROMPT_MS).apply()
        askedAt = SystemClock.elapsedRealtime()
        activity.requestPermissions(permissions, code)
    }

    /** Blocks the permission controller again and says whether it's allowed now. [what] names it in the toast ("location", "camera"). */
    fun onResult(activity: Activity, permissions: Array<String>, what: String): Boolean {
        activity.getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE).edit().putLong(PROMPT_UNTIL, 0L).apply()
        val granted = has(activity, permissions)
        // After two "Don't allow"s Android answers at once without showing anything
        if (!granted && SystemClock.elapsedRealtime() - askedAt < 500) {
            Toast.makeText(activity, "Android won't show the $what prompt again. You can allow it for this app in Android Settings.", Toast.LENGTH_LONG).show()
        }
        return granted
    }
}
