package com.rockhard.blocker

import android.Manifest
import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener2
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.rockhard.blocker.world.go.StepBank
import com.rockhard.blocker.world.go.StepPayout
import java.time.LocalDate

/**
 * The phone's step counter, feeding the step bank (Netbeasts Go 02). The sensor only keeps
 * counting while something listens, so the Guardian, which is always running, starts it; the
 * game starts it too in case the Guardian is off. Readings are batched by the phone for a few
 * minutes so it can sleep. Netbeasts APK only: the others don't declare the permission.
 */
object StepCounter {
    const val REQUEST_CODE = 4218
    private const val ASKED = "STEP_ASKED"   // the game has offered to count steps
    private const val LAST = "STEP_LAST"     // the bank's five numbers (StepBank)
    private const val DAY = "STEP_DAY"
    private const val TODAY = "STEP_TODAY"
    private const val UNPAID = "STEP_UNPAID"
    private const val WALKED = "STEP_WALKED"
    private const val BATCH_US = 5 * 60_000_000  // how long the phone may hold readings back
    private const val FLUSH_WAIT_MS = 2_000L     // how long a pay-out waits for held-back readings

    private val PERMISSIONS = arrayOf(Manifest.permission.ACTIVITY_RECOGNITION)

    private val handler = Handler(Looper.getMainLooper())
    private var sensors: SensorManager? = null
    private var prefs: android.content.SharedPreferences? = null
    private val flushed = mutableListOf<Runnable>()

    private val listener = object : SensorEventListener2 {
        override fun onSensorChanged(event: SensorEvent) {
            val p = prefs ?: return
            val bank = load(p)
            bank.onReading(event.values[0].toInt(), today())
            save(p, bank)
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        override fun onFlushCompleted(sensor: Sensor?) { handler.post { flushed.toList().forEach { it.run() } } }
    }

    private fun isNetbeastsApk() = Flavor.isGamers && !Flavor.homevisits()
    private fun today() = LocalDate.now().toEpochDay().toInt()

    /** Whether this phone has a step counter (and this is the Netbeasts APK). */
    fun available(context: Context) = isNetbeastsApk() &&
        (context.getSystemService(Context.SENSOR_SERVICE) as SensorManager).getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    /** From Android 10 the sensor needs ACTIVITY_RECOGNITION; before that it needs nothing. */
    fun allowed(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || PermissionGate.has(context, PERMISSIONS)

    /** Starts listening, if it can and isn't already. Safe to call often. */
    fun start(context: Context) {
        if (sensors != null || !available(context) || !allowed(context)) return
        val app = context.applicationContext
        val sm = app.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        prefs = app.getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE)
        if (sm.registerListener(listener, sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER), SensorManager.SENSOR_DELAY_NORMAL, BATCH_US)) sensors = sm
    }

    fun stop() {
        sensors?.unregisterListener(listener)
        sensors = null
    }

    /**
     * Pays out the steps banked since last time: first asks the phone for any readings it's
     * holding back, then calls [done] on the main thread. Null when steps aren't counted here.
     */
    fun payOut(context: Context, done: (StepPayout?) -> Unit) {
        start(context)
        val sm = sensors ?: return done(null)
        var finished = false
        lateinit var finish: Runnable
        finish = Runnable {
            if (finished) return@Runnable
            finished = true
            flushed.remove(finish); handler.removeCallbacks(finish)
            val p = prefs ?: return@Runnable done(null)
            val bank = load(p)
            val paid = bank.payOut()
            save(p, bank)
            done(paid)
        }
        flushed.add(finish)
        handler.postDelayed(finish, FLUSH_WAIT_MS)
        if (!sm.flush(listener)) handler.post(finish)
    }

    private fun load(p: android.content.SharedPreferences) =
        StepBank(p.getInt(LAST, -1), p.getInt(DAY, 0), p.getInt(TODAY, 0), p.getInt(UNPAID, 0), p.getInt(WALKED, 0))

    private fun save(p: android.content.SharedPreferences, bank: StepBank) {
        p.edit().putInt(LAST, bank.last).putInt(DAY, bank.day).putInt(TODAY, bank.stepsToday)
            .putInt(UNPAID, bank.unpaid).putInt(WALKED, bank.walked).apply()
    }

    // --- Asking -----------------------------------------------------------------

    /** Offered once, on a later opening than the location offer so the two don't come together. */
    fun shouldOffer(activity: Activity): Boolean {
        val p = activity.getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE)
        return available(activity) && !allowed(activity) && !p.getBoolean(ASKED, false)
    }

    /** Says why, then shows Android's prompt past the Guardian (PermissionGate). The answer goes to [onPermissionResult]. */
    fun offer(activity: Activity) {
        activity.getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE).edit().putBoolean(ASKED, true).apply()
        DialogUtils.showCustomDialog(
            activity, "👣 Count My Steps",
            "Walk with me in your pocket and I'll count your steps. Every 200 steps is a coin, and walking heals your netbeasts.",
            true, "COUNT STEPS", { PermissionGate.request(activity, PERMISSIONS, REQUEST_CODE) },
        )
    }

    /** Blocks the permission controller again, and starts counting if it's allowed now. */
    fun onPermissionResult(activity: Activity) {
        if (PermissionGate.onResult(activity, PERMISSIONS, "physical activity")) start(activity)
    }
}
