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
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.rockhard.blocker.AetherEngine
import com.rockhard.blocker.MomentumEngine
import com.rockhard.blocker.ar.ArRenderer
import com.rockhard.blocker.ar.ArSession
import com.rockhard.blocker.ar.Facing
import com.rockhard.blocker.ar.FigureAssets
import com.rockhard.blocker.ar.Surfaces
import com.rockhard.blocker.ar.CameraPermission
import com.rockhard.blocker.ar.Tracking
import com.rockhard.blocker.ar.TrackingPhrases
import kotlin.math.atan2

class HomevisitsActivity : Activity() {
    // What a visitor would say when the camera loses track, indoors
    private val phrases = TrackingPhrases(
        starting = "Initializing camera...",
        tooDark = "It's a bit dark in here. Can you switch a light on?",
        tooFast = "Whoa, slow down!",
        tooPlain = "Point it at something with a bit more going on.",
        noCamera = "Camera unavailable.",
        searching = "Looking for your surroundings...",
    )

    private var taps = 0
    private var isArMode = false
    
    private var arSession: ArSession? = null
    private var glSurfaceView: GLSurfaceView? = null
    private var arRenderer: ArRenderer? = null

    // The test figure: "figures/test.glb" once imported (tools/figures), the blocky mannequin until then
    @Volatile private var testFigure: FigureAssets.Loaded? = null
    private var moveIndex = 0
    
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
                val hint = Tracking.getHint(camera, phrases) ?: "Tracking Perfect"
                val tierStr = arSession?.tier?.name ?: "UNKNOWN"
                
                val camHeight = camera?.pose?.ty() ?: 0f
                
                hudText.text = """
                    Tier: $tierStr | FPS: ${arRenderer?.fps}
                    State: $hint
                    Planes Found: ${arRenderer?.planesCount}
                    Cam Height: ${String.format("%.2f", camHeight)}m
                    Depth: ${arSession?.session?.isDepthModeSupported(com.google.ar.core.Config.DepthMode.AUTOMATIC) ?: false}
                    Figures: ${arRenderer?.figureCount} (${testFigure?.let { if (it.isMannequin) "mannequin" else "test.glb" } ?: "loading"}) | Move: ${testFigure?.clips?.getOrNull(moveIndex)?.name ?: "-"}
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
            text = "Homevisits\n\nToday's Momentum: ${MomentumEngine.calculateCurrentMomentum(getSharedPreferences("RHC_PREFS", MODE_PRIVATE))} min\n\nYour first visitor is on the way."
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
            setEGLContextClientVersion(3) // the figures need GLES 3
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)

            arRenderer = ArRenderer(arSession!!)
            setRenderer(arRenderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_UP) dropFigure(e.x, e.y)
                true
            }
        }
        root.addView(glSurfaceView)
        if (testFigure == null) Thread { testFigure = FigureAssets.load(applicationContext, "test") }.start()

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
                glSurfaceView?.onPause()
                arSession?.destroy()
                isArMode = false
                hudHandler.removeCallbacks(hudRunnable)
                showPlaceholderScreen()
            }
        }
        // Every placed figure plays the next move
        val btnMove = Button(this).apply {
            text = "Next move"
            setOnClickListener {
                val loaded = testFigure ?: return@setOnClickListener
                if (loaded.clips.isEmpty()) return@setOnClickListener
                moveIndex = (moveIndex + 1) % loaded.clips.size
                val clip = loaded.clips[moveIndex]
                arRenderer?.queue { _, _ -> arRenderer?.placed?.forEach { it.play(clip) } }
            }
        }
        val btnClear = Button(this).apply {
            text = "Clear"
            setOnClickListener {
                arRenderer?.queue { _, _ -> arRenderer?.let { r -> r.placed.toList().forEach(r::remove) } }
            }
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(btnMove)
            addView(btnClear)
            addView(btnExit)
        }
        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 64
        }
        root.addView(buttons, lp)

        setContentView(root)
        hudHandler.post(hudRunnable)
    }

    /**
     * A tap stands the test figure on whatever floor-like surface it hits,
     * turned to face the phone, 1.65 m tall, doing the current move.
     */
    private fun dropFigure(x: Float, y: Float) {
        val loaded = testFigure ?: return
        val renderer = arRenderer ?: return
        renderer.queue { frame, _ ->
            val hit = Surfaces.hit(frame, x, y)
            if (hit == null || hit.facing != Facing.UP) {
                runOnUiThread { Toast.makeText(this, if (hit == null) "No surface there yet" else "Figures stand on floors, not walls", Toast.LENGTH_SHORT).show() }
                return@queue
            }
            val cam = frame.camera.pose
            val yaw = atan2(cam.tx() - hit.pose.tx(), cam.tz() - hit.pose.tz())
            val placed = renderer.place(loaded.figure, hit.uprightAnchor(), yaw, 1.65f)
            loaded.clips.getOrNull(moveIndex)?.let { placed.play(it, fadeS = 0f) }
        }
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
