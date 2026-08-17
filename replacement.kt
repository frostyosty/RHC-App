private fun triggerBossInvasion(debugReason: String, canDefend: Boolean) {
    val prefs = getSharedPreferences("RHC_PREFS", Context.MODE_PRIVATE)
    
    val currentFlavor = BuildConfig.FLAVOR.lowercase()
    val isGamers = currentFlavor.contains("gamers")
    val isGameDefault = prefs.getBoolean("LAUNCH_GAME_DEFAULT", false)
    
    val lockoutUntil = prefs.getLong("FLEE_LOCKOUT_UNTIL", 0L)
    val now = System.currentTimeMillis()
    val isLockedOut = isGamers && now < lockoutUntil

    addLog("RED WALL DROPPED. Reason: $debugReason")
    
    // --- INSTANT PRODUCTIVITY REDIRECT ---
    val destIntent = if (!isGamers) {
        Intent(this, MomentumActivity::class.java)
    } else if (isGameDefault) {
        Intent(this, GameActivity::class.java)
    } else {
        Intent(this, MainActivity::class.java)
    }
    destIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    try { startActivity(destIntent) } catch (e: Exception) {}

    Handler(Looper.getMainLooper()).post {
        if (isOverlayShowing) return@post
        urgesDefeatedCount++
        
        // --- THE NEW TOASTY SLIDE-IN OVERLAY ---
        val toastyParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { 
            gravity = Gravity.BOTTOM 
            windowAnimations = android.R.style.Animation_Toast
        }
        
        val toastyView = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E53935"))
            setPadding(48, 48, 48, 48)
            
            addView(TextView(this@GuardianService).apply {
                text = "🛑 BLOCKED: ${debugReason.take(40)}..."
                setTextColor(Color.WHITE)
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@GuardianService).apply {
                text = "Tap to view details or defend..."
                setTextColor(Color.parseColor("#FFCDD2"))
                textSize = 12f
                setPadding(0, 8, 0, 0)
            })
        }
        
        // --- THE FULL SCREEN DETAILS (ONLY SHOWN IF TAPPED) ---
        val fullParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES } }
        
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_guard, null)
        val tvReason = overlayView?.findViewById<TextView>(R.id.tvDebugReason)
        tvReason?.text = "Triggered by:\n\"$debugReason\""
        
        val tvMsg = overlayView?.findViewById<TextView>(R.id.tvOverlayMessage)
        val tvTitle = overlayView?.findViewById<TextView>(R.id.tvOverlayTitle)
        val btnDefend = overlayView?.findViewById<Button>(R.id.btnPauseBlocker)
        val btnYield = overlayView?.findViewById<Button>(R.id.btnYield)
        
        val triggerWord = debugReason.substringAfter(": ").substringBefore(" (").trim()
        val bossName = if (isGamers) BossNameEngine.generateBossName(triggerWord) else "The Dark One"

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

        if (isLockedOut) {
            val remain = (lockoutUntil - now) / 1000
            tvTitle?.text = "💀 SYSTEM LOCKED 💀"
            tvMsg?.text = "You recently abandoned your Netbeasts.\n\nLocked out for ${remain / 60}m ${remain % 60}s."
            btnDefend?.visibility = View.GONE
            btnYield?.text = "Retreat"
            btnYield?.setOnClickListener { removeOverlay(); performGlobalAction(GLOBAL_ACTION_HOME) }
        } else if (!canDefend) {
            tvTitle?.text = "🛑 SYSTEM LOCKED 🛑"
            tvMsg?.text = "$debugReason\n\nThis action cannot be bypassed without a System Override."
            btnDefend?.visibility = View.GONE
            btnYield?.text = "Momentum of a boss"
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
        
        toastyView.setOnClickListener {
            try { windowManager?.removeView(toastyView) } catch(e: Exception){}
            try { windowManager?.addView(overlayView, fullParams) } catch (e: Exception) {}
        }

        try { 
            windowManager?.addView(toastyView, toastyParams)
            toastyOverlayView = toastyView
            isOverlayShowing = true 
            
            // Auto yield after 6 seconds if they ignore the toasty notification
            Handler(Looper.getMainLooper()).postDelayed({
                if (isOverlayShowing && toastyOverlayView?.windowToken != null) {
                    fleeLogic()
                }
            }, 6000)
        } catch (e: Exception) {}
    }
}

private fun removeOverlay() {
    if (isOverlayShowing) {
        bossTimer?.cancel()
        try { if (overlayView != null) windowManager?.removeView(overlayView) } catch (e: Exception) {}
        try { if (toastyOverlayView != null) windowManager?.removeView(toastyOverlayView) } catch (e: Exception) {}
        overlayView = null
        toastyOverlayView = null
        isOverlayShowing = false
    }
}
