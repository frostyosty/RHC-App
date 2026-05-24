package com.rockhard.blocker

import android.accessibilityservice.AccessibilityService
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

class GuardianService : AccessibilityService() {
    companion object {
        var pauseUntil: Long = 0L
        var urgesDefeatedCount = 0
        val appTimeTrackers = mutableMapOf<String, Long>()
        val actionLogs = mutableListOf<String>()

        fun addLog(msg: String) {
            val time = SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(Date())
            actionLogs.add(0, "[$time] $msg")
            if (actionLogs.size > 50) actionLogs.removeLast()
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var nightfallDimView: View? = null
    private var isOverlayShowing = false
    private var isNightfallDimShowing = false
    private var bossTimer: CountDownTimer? = null
    private lateinit var ruleEngine: ShieldRuleEngine
    private lateinit var dpm: DevicePolicyManager
    private lateinit var compName: ComponentName
    
    private var lastScanTime = 0L
    private val CONTENT_SCAN_COOLDOWN_MS = 1500L
    
    private var currentActivityClass: String = ""

    // Whitelisted wake lock to bypass aggressive Honor/Huawei/Meizu standby kills
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    private val handler = Handler(Looper.getMainLooper())
    private val nightfallRunnable = object : Runnable {
        override fun run() {
            checkNightfallTick()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        compName = ComponentName(this, AdminReceiver::class.java)
        
        val dynamicAppName = packageManager.getApplicationLabel(applicationInfo).toString()
        ruleEngine = ShieldRuleEngine(getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE), dynamicAppName)

        val channelId = "shield_channel"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(channelId, "Shield Status", android.app.NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.createNotificationChannel(channel)
        }
        val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION") android.app.Notification.Builder(this)
        }
        val notification = builder.setContentTitle("Rock Hard Shield Active")
            .setContentText("Guarding your digital environment.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
        
        startForeground(1011, notification)
        handler.post(nightfallRunnable)

        // WakeLock Tag Hack initiation
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "LocationManagerService")
        wakeLock?.acquire()

        addLog("Service Connected.")
        Toast.makeText(this, "Rock Hard Shield Activated!", Toast.LENGTH_LONG).show()

        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP) }
            try { startActivity(intent) } catch (e: Exception) { addLog("Auto-return failed.") }
        }, 500)
    }

    private fun checkNightfallTick() {
        val prefs = getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE)
        val nfStart = prefs.getInt("NIGHTFALL_START", -1)
        val nfEnd = prefs.getInt("NIGHTFALL_END", -1)

        if (nfStart != -1 && nfEnd != -1 && nfStart != nfEnd) {
            val cal = Calendar.getInstance()
            val currentMins = (cal.get(Calendar.HOUR_OF_DAY) * 60) + cal.get(Calendar.MINUTE)
            val isNightfall = if (nfStart < nfEnd) { currentMins in nfStart..nfEnd } else { currentMins >= nfStart || currentMins <= nfEnd }
            
            var warnStart = nfStart - 5
            if (warnStart < 0) warnStart += 1440
            val isWarn = if (warnStart < nfStart) { currentMins in warnStart until nfStart } else { currentMins >= warnStart || currentMins < nfStart }

            if (isNightfall) {
                updateNightfallDimmer(0f) 
                if (dpm.isAdminActive(compName)) {
                    Toast.makeText(this, "Overcoming sleeplessness. Time to rest.", Toast.LENGTH_SHORT).show()
                    dpm.lockNow()
                }
            } else if (isWarn) {
                val currSecs = currentMins * 60 + cal.get(Calendar.SECOND)
                val wStartSecs = warnStart * 60
                var diff = currSecs - wStartSecs
                if (diff < 0) diff += 86400
                var alpha = diff / 300f
                if (alpha > 0.95f) alpha = 0.95f
                updateNightfallDimmer(alpha)
            } else {
                updateNightfallDimmer(0f)
            }
        } else {
            updateNightfallDimmer(0f)
        }
    }

    private fun updateNightfallDimmer(alpha: Float) {
        if (alpha <= 0f) {
            if (isNightfallDimShowing && nightfallDimView != null) {
                windowManager?.removeView(nightfallDimView)
                nightfallDimView = null
                isNightfallDimShowing = false
            }
            return
        }

        if (!isNightfallDimShowing) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.CENTER }
            
            nightfallDimView = View(this).apply { setBackgroundColor(Color.BLACK) }
            try { 
                windowManager?.addView(nightfallDimView, params)
                isNightfallDimShowing = true 
            } catch (e: Exception) {}
        }
        nightfallDimView?.alpha = alpha
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || System.currentTimeMillis() < pauseUntil) return

        val packageName = event.packageName?.toString() ?: ""
        val className = event.className?.toString() ?: ""
        val now = System.currentTimeMillis()
        val isWindowStateChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        if (isWindowStateChange) {
            val time = appTimeTrackers[packageName] ?: 0L
            appTimeTrackers[packageName] = time + 1000L
            currentActivityClass = className
        }

        val rootNode = rootInActiveWindow
        val isAdminActive = dpm.isAdminActive(compName)
        val prefs = getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE)

        if (prefs.getBoolean("DEBUG_UI_TOASTS", false)) {
            if (packageName.contains("tencent.mm")) {
                if (isWindowStateChange) {
                    addLog("[WND] $className") 
                }
                if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                    val node = event.source
                    val resId = node?.viewIdResourceName?.substringAfterLast("/") ?: "null"
                    val txt = node?.text ?: node?.contentDescription ?: "null"
                    addLog("[CLICK] ID=$resId | TXT=$txt") 
                    node?.recycle()
                }
            }
        }

        if (!isWindowStateChange && now - lastScanTime < CONTENT_SCAN_COOLDOWN_MS) {
            return
        }
        lastScanTime = now

        val action = ruleEngine.evaluate(packageName, currentActivityClass, rootNode, isAdminActive)

        when (action) {
            is ShieldAction.Block -> {
                if (rootNode != null && !rootNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) performGlobalAction(GLOBAL_ACTION_BACK)
                performGlobalAction(GLOBAL_ACTION_HOME)
                
                if (action.redirectTarget != null) {
                    handleRedirect(action.redirectTarget)
                } else {
                    triggerBossInvasion(action.reason, action.canDefend)
                }
            }
            is ShieldAction.RewardApp -> handleReward(action.triggerWord, "BLOCKLIST_APP")
            is ShieldAction.RewardWeb -> handleReward(action.triggerWord, "BLOCKLIST_WEB")
            is ShieldAction.WeatherBuff -> handleWeatherBuff(action.element)
            ShieldAction.Allow -> { /* Do nothing */ }
        }
    }

    private fun handleRedirect(target: String) {
        pauseUntil = System.currentTimeMillis() + 4000L
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, "⚡ Urge Defeated! Energy Redirected!", Toast.LENGTH_LONG).show()
            try {
                if (target.startsWith("package:")) {
                    val pkg = target.removePrefix("package:")
                    val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                    } else {
                        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                } else {
                    var url = target
                    if (!url.startsWith("http")) url = "https://$url"
                    val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(browserIntent)
                }
            } catch (e: Exception) {
                addLog("Redirect failed: ${e.message}")
            }
        }
    }

    private fun handleReward(triggerWord: String, listKey: String) {
        val prefs = getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE)
        performGlobalAction(GLOBAL_ACTION_HOME)
        
        if (listKey == "BLOCKLIST_APP") prefs.edit().putBoolean("FIRST_OVERCOME_APP_$triggerWord", true).apply()
        else prefs.edit().putBoolean("FIRST_OVERCOME_WEB_$triggerWord", true).apply()

        val isGamers = BuildConfig.FLAVOR.lowercase().contains("gamers")
        pauseUntil = System.currentTimeMillis() + 3000L

        if (isGamers) {
            val bName = GoodBeastEngine.generateName(triggerWord)
            val (m1, m2, m3) = SkillEngine.generateMoves("Reclaimed", 1)
            val partyStr = prefs.getString("PARTY_DATA", "") ?: ""
            val newBeast = "$bName,Reclaimed,150,150,$m1,$m2,$m3,0,0,0,0,true,None,0,0,0,0,None,0"
            prefs.edit().putString("PARTY_DATA", if (partyStr.isEmpty()) newBeast else "$partyStr;$newBeast").apply()
            
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, "You found an orphaned $bName!", Toast.LENGTH_LONG).show()
            }
        } else {
            val now = System.currentTimeMillis()
            val mins = kotlin.random.Random.nextInt(10, 20)
            val current = prefs.getString("MOMENTUM_EARNED_TODAY", "") ?: ""
            val newEntry = "$triggerWord|$mins|$now"
            prefs.edit().putString("MOMENTUM_EARNED_TODAY", if (current.isEmpty()) newEntry else "$current,$newEntry").apply()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, "First time overcoming $triggerWord! Momentum Gained!", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun handleWeatherBuff(element: String) {
        val prefs = getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(Date())
        prefs.edit().putBoolean("LAST_BUFF_${element}_$today", true).apply()
        
        val activePetIndex = prefs.getInt("ACTIVE_PET_INDEX", 0)
        val partyData = prefs.getString("PARTY_DATA", "") ?: ""
        if (partyData.isEmpty()) return
        
        val party = partyData.split(";").toMutableList()
        if (activePetIndex >= party.size) return
        
        val activePet = party[activePetIndex].split(",")
        if (activePet.size < 13) return
        
        val newStacks = activePet[12].toInt() + 1
        val newPetData = "${activePet[0]},${activePet[1]},${activePet[2]},${activePet[3]},${activePet[4]},${activePet[5]},${activePet[6]},${activePet[7]},${activePet[8]},${activePet[9]},${activePet[10]},${activePet[11]},$newStacks,${activePet.getOrElse(13){"0"}}"
        party[activePetIndex] = newPetData
        prefs.edit().putString("PARTY_DATA", party.joinToString(";")).apply()

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, "🌟 ${activePet[0]} absorbed $element energy! (+1 Stack)", Toast.LENGTH_LONG).show()
        }
        addLog("Scavenged URL for $element.")
    }

    private fun triggerBossInvasion(debugReason: String, canDefend: Boolean) {
        val prefs = getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE)
        
        val currentFlavor = BuildConfig.FLAVOR.lowercase()
        val isGamers = currentFlavor.contains("gamers")
        
        val lockoutUntil = prefs.getLong("FLEE_LOCKOUT_UNTIL", 0L)
        val now = System.currentTimeMillis()
        val isLockedOut = isGamers && now < lockoutUntil

        addLog("RED WALL DROPPED. Reason: $debugReason")
        Handler(Looper.getMainLooper()).post {
            if (isOverlayShowing) return@post
            urgesDefeatedCount++
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.CENTER }
            
            overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_guard, null)
            val tvReason = overlayView?.findViewById<TextView>(R.id.tvDebugReason)
            tvReason?.text = "Triggered by:\n\"$debugReason\""
            
            val tvMsg = overlayView?.findViewById<TextView>(R.id.tvOverlayMessage)
            val tvTitle = overlayView?.findViewById<TextView>(R.id.tvOverlayTitle)
            val btnDefend = overlayView?.findViewById<Button>(R.id.btnPauseBlocker)
            val btnYield = overlayView?.findViewById<Button>(R.id.btnYield)
            
            val triggerWord = debugReason.substringAfter(": ").substringBefore(" (").trim()
            val bossName = if (isGamers) BossNameEngine.generateBossName(triggerWord) else "The Dark One"

            if (isLockedOut) {
                val remain = (lockoutUntil - now) / 1000
                tvTitle?.text = "💀 SYSTEM LOCKED 💀"
                tvMsg?.text = "You recently abandoned your Netbeasts.\n\nLocked out for ${remain / 60}m ${remain % 60}s."
                btnDefend?.visibility = View.GONE
                btnYield?.text = "Retreat"
                btnYield?.setOnClickListener { removeOverlay(); performGlobalAction(GLOBAL_ACTION_HOME) }
                try { windowManager?.addView(overlayView, params); isOverlayShowing = true } catch (e: Exception) {}
                return@post
            }

            val fleeLogic: () -> Unit = {
                removeOverlay()
                if (isGamers) {
                    val lockoutTime = System.currentTimeMillis() + (15 * 60 * 1000)
                    prefs.edit().putBoolean("FLED_BATTLE", true).putString("FLED_BOSS", bossName).putLong("FLEE_LOCKOUT_UNTIL", lockoutTime).apply()
                    performGlobalAction(GLOBAL_ACTION_HOME)
                } else {
                    if (!performGlobalAction(GLOBAL_ACTION_BACK)) {
                        performGlobalAction(GLOBAL_ACTION_HOME)
                    }
                }
            }

            if (!canDefend) {
                tvTitle?.text = "🛑 SYSTEM LOCKED 🛑"
                tvMsg?.text = "$debugReason\n\nThis action cannot be bypassed without a System Override."
                btnDefend?.visibility = View.GONE
                btnYield?.text = "Understood"
                btnYield?.setOnClickListener { fleeLogic() }
            } else {
                if (isGamers) {
                    btnDefend?.text = "Defend Netbeasts"
                    btnYield?.text = "Flee - leave the netbeasts to die"
                    tvTitle?.text = "⚠️ INVASION DETECTED ⚠️"
                    bossTimer = object : CountDownTimer(180000, 1000) {
                        override fun onTick(millisUntilFinished: Long) {
                            val secLeft = millisUntilFinished / 1000
                            tvMsg?.text = "$bossName is attacking!\n\nTime remaining:\n${String.format("%02d:%02d", secLeft / 60, secLeft % 60)}\n\n(If you ignore this, some Netbeasts might die.)"
                        }
                        override fun onFinish() { fleeLogic() }
                    }.start()
                } else {
                    val isAdult = debugReason.contains("Content Guard")
                    val isTamper = debugReason.contains("Guard") && !isAdult
                    val minsSaved = if (isAdult) kotlin.random.Random.nextInt(15, 28) else kotlin.random.Random.nextInt(10, 20)

                    btnDefend?.text = "Spend momentum"
                    btnYield?.text = "Well done me"
                    tvTitle?.text = "⚡ MOMENTUM PROTECTED ⚡"
                    
                    if (isTamper) {
                        tvMsg?.text = "System modification blocked.\nStay focused on your goals."
                    } else {
                        tvMsg?.text = "You almost wasted time.\nEstimated time saved: $minsSaved minutes.\n\n(This action has been tracked)."
                    }
                }

                btnYield?.setOnClickListener { fleeLogic() }
                
                btnDefend?.setOnClickListener {
                    removeOverlay()
                    if (isGamers) {
                        startActivity(Intent(this, GameActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            putExtra("UNDER_ATTACK", true)
                            putExtra("TRIGGER_REASON", triggerWord)
                            putExtra("BOSS_NAME", bossName)
                        })
                    } else {
                        Toast.makeText(this@GuardianService, "Urge Defeated! (Daily Yield Protected)", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@GuardianService, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        })
                    }
                }
            }
            try { windowManager?.addView(overlayView, params); isOverlayShowing = true } catch (e: Exception) {}
        }
    }

    private fun removeOverlay() {
        if (isOverlayShowing && overlayView != null) {
            bossTimer?.cancel()
            windowManager?.removeView(overlayView)
            overlayView = null
            isOverlayShowing = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(nightfallRunnable)
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        WatchdogReceiver.fireAlert(this)
        return super.onUnbind(intent)
    }
    override fun onInterrupt() {}
}
