package com.rockhard.blocker

import android.accessibilityservice.AccessibilityService
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
    private var isOverlayShowing = false
    private var bossTimer: CountDownTimer? = null
    private lateinit var ruleEngine: ShieldRuleEngine
    
    private var lastScanTime = 0L
    private val CONTENT_SCAN_COOLDOWN_MS = 1500L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "HEARTBEAT_TICK") {
            val time = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date())
            val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.app.Notification.Builder(this, "shield_channel")
            } else {
                @Suppress("DEPRECATION") android.app.Notification.Builder(this)
            }
            val notification = builder.setContentTitle("Rock Hard Shield Active")
                .setContentText("Guarding your digital environment. Last check: $time")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .build()
            
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.notify(1011, notification)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
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

        addLog("Service Connected.")
        Toast.makeText(this, "Rock Hard Shield Activated!", Toast.LENGTH_LONG).show()

        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP) }
            try { startActivity(intent) } catch (e: Exception) { addLog("Auto-return failed.") }
        }, 500)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || System.currentTimeMillis() < pauseUntil) return

        val packageName = event.packageName?.toString() ?: ""
        val className = event.className?.toString() ?: ""
        val now = System.currentTimeMillis()
        val isWindowStateChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        // --- LIVE UI DEBUGGER FOR CHINESE ROMS ---
        if (isWindowStateChange) {
            val prefs = getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE)
            if (prefs.getBoolean("DEBUG_UI_TOASTS", false)) {
                Toast.makeText(this, "PKG: $packageName\nCLS: $className", Toast.LENGTH_SHORT).show()
                addLog("VISIT: $packageName | $className")
            }
        }

        if (!isWindowStateChange && now - lastScanTime < CONTENT_SCAN_COOLDOWN_MS) {
            return
        }
        lastScanTime = now

        if (isWindowStateChange) {
            val time = appTimeTrackers[packageName] ?: 0L
            appTimeTrackers[packageName] = time + 1000L
        }

        val rootNode = rootInActiveWindow
        
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val compName = ComponentName(this, AdminReceiver::class.java)
        val isAdminActive = dpm.isAdminActive(compName)

        val action = ruleEngine.evaluate(packageName, className, rootNode, isAdminActive)

        when (action) {
            is ShieldAction.Block -> {
                if (rootNode != null && !rootNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) performGlobalAction(GLOBAL_ACTION_BACK)
                performGlobalAction(GLOBAL_ACTION_HOME)
                triggerBossInvasion(action.reason, action.canDefend)
            }
            is ShieldAction.RewardApp -> handleReward(action.triggerWord, "BLOCKLIST_APP")
            is ShieldAction.RewardWeb -> handleReward(action.triggerWord, "BLOCKLIST_WEB")
            is ShieldAction.WeatherBuff -> handleWeatherBuff(action.element)
            ShieldAction.Allow -> { /* Do nothing */ }
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
            MomentumEngine.addEarnedMomentum(prefs, triggerWord, false)
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
                tvTitle?.text = "🌙 REST PROTECTED 🌙"
                tvMsg?.text = "Sleep block active.\n\nGo to sleep."
                btnDefend?.visibility = View.GONE
                btnYield?.text = "Praise God"
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

    override fun onUnbind(intent: Intent?): Boolean {
        WatchdogReceiver.fireAlert(this)
        return super.onUnbind(intent)
    }
    override fun onInterrupt() {}
}
