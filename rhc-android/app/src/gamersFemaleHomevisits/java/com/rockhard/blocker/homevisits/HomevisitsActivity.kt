package com.rockhard.blocker.homevisits

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.opengl.GLSurfaceView
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.rockhard.blocker.AetherEngine
import com.rockhard.blocker.homevisits.ar.ArRenderer
import com.rockhard.blocker.homevisits.ar.ArSession
import com.rockhard.blocker.homevisits.ar.ArTier
import com.rockhard.blocker.homevisits.ar.CameraPermission
import com.rockhard.blocker.homevisits.ar.Tracking

class HomevisitsActivity : Activity() {
    private var taps = 0
    private var isArMode = false
    
    private var arSession: ArSession? = null
    private var glSurfaceView: GLSurfaceView? = null
    private var arRenderer: ArRenderer? = null
    
    private lateinit var hudText: TextView
    private val hudHandler = Handler(Looper.getMainLooper())
    
    private var batteryLevel = -1
    private var batteryTemp = -1f
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            batteryLevel = (level * 100) / scale.toFloat().toInt()
            batteryTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10f
        }
    }

    private val hudRunnable = object : Runnable {
        override fun run() {
            if (isArMode && arRenderer != null) {
                val frame = arRenderer?.lastFrame
                val camera = frame?.camera
                val hint = Tracking.getHint(camera) ?: "Tracking Perfect"
                val tierStr = arSession?.tier?.name ?: "UNKNOWN"
                
                val camHeight = camera?.pose?.ty() ?: 0f
                
                hudText.text = """
                    Tier: $tierStr | FPS: ${arRenderer?.fps}
                    State: $hint
                    Planes Found: ${arRenderer?.planesCount}
                    Cam Height: ${String.format("%.2f", camHeight)}m
                    Depth: ${arSession?.session?.isDepthModeSupported(com.google.ar.core.Config.DepthMode.AUTOMATIC) ?: false}
                    Batt: $batteryLevel% | Temp: ${batteryTemp}°C
                """.trimIndent()
            }
            hudHandler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("RHC_PREFS", MODE_PRIVATE)
        val closed = AetherEngine.closedReason(prefs)
        if (closed != null) {
            Toast.makeText(this, closed, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        showPlaceholderScreen()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun showPlaceholderScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#121212"))
        }

        val tv = TextView(this).apply {
            text = "Homevisits\n\nYour first visitor is on the way."
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setOnClickListener {
                taps++
                if (taps >= 7) {
                    taps = 0
                    launchArDebugMode()
                }
            }
        }
        root.addView(tv)
        setContentView(root)
    }

    private fun launchArDebugMode() {
        if (!CameraPermission.hasPermission(this)) {
            Toast.makeText(this, "We'll look through your camera to map the floor...", Toast.LENGTH_LONG).show()
            CameraPermission.requestPermission(this)
            return
        }
        startArSession()
    }

    private fun startArSession() {
        isArMode = true
        arSession = ArSession(this)
        if (arSession?.setup() == false) {
            Toast.makeText(this, "ARCore installation required or failed.", Toast.LENGTH_LONG).show()
            return
        }

        val root = FrameLayout(this)
        glSurfaceView = GLSurfaceView(this).apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            
            arRenderer = ArRenderer(arSession!!)
            setRenderer(arRenderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        root.addView(glSurfaceView)

        hudText = TextView(this).apply {
            setTextColor(Color.GREEN)
            textSize = 14f
            setPadding(32, 64, 32, 32)
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
        }
        root.addView(hudText, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        val btnExit = Button(this).apply {
            text = "Exit Debug"
            setBackgroundColor(Color.parseColor("#D32F2F"))
            setTextColor(Color.WHITE)
            setOnClickListener {
                arSession?.destroy()
                isArMode = false
                hudHandler.removeCallbacks(hudRunnable)
                showPlaceholderScreen()
            }
        }
        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 64
        }
        root.addView(btnExit, lp)

        setContentView(root)
        hudHandler.post(hudRunnable)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == CameraPermission.REQUEST_CODE) {
            if (CameraPermission.onPermissionResult(this)) {
                startArSession()
            } else {
                Toast.makeText(this, "Camera permission required for AR test.", Toast.LENGTH_SHORT).show()
                taps = 0
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onResume() {
        super.onResume()
        if (isArMode) {
            arSession?.resume()
            glSurfaceView?.onResume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isArMode) {
            glSurfaceView?.onPause()
            arSession?.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(batteryReceiver)
        hudHandler.removeCallbacks(hudRunnable)
        arSession?.destroy()
    }
}
