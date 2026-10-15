package com.rockhard.blocker.ar

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.LightEstimate
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.rockhard.blocker.figure.Figure
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Draws the camera and, over it, the placed figures. The GLSurfaceView must
 * ask for GLES 3 (`setEGLContextClientVersion(3)`).
 */
class ArRenderer(private val arSession: ArSession) : GLSurfaceView.Renderer {
    private val backgroundRenderer = BackgroundRenderer()
    private val figureRenderer = FigureRenderer()
    private val tasks = ConcurrentLinkedQueue<(Frame, Session) -> Unit>()
    private val figures = ArrayList<PlacedFigure>()
    private val view = FloatArray(16)
    private val proj = FloatArray(16)
    private val light = floatArrayOf(1f, 1f, 1f, 0.466f)

    var lastFrame: Frame? = null
        private set
    var fps = 0
        private set
    var planesCount = 0
        private set
    /** How many figures are placed; read from any thread. */
    @Volatile var figureCount = 0
        private set

    private var frameCounter = 0
    private var lastFpsTime = System.currentTimeMillis()

    /**
     * Runs `task` on the GL thread with the next frame, after the session has
     * updated. Hit tests, anchors and placing or changing figures go through here.
     */
    fun queue(task: (Frame, Session) -> Unit) {
        tasks.add(task)
    }

    /** Stands a figure at an anchor (call from a queued task). */
    fun place(figure: Figure, anchor: Anchor, yaw: Float, height: Float): PlacedFigure =
        PlacedFigure(figure, anchor, yaw, height).also { figures.add(it); figureCount = figures.size }

    /** Takes a figure away and lets go of its anchor (call from a queued task). */
    fun remove(placed: PlacedFigure) {
        if (figures.remove(placed)) placed.anchor.detach()
        figureCount = figures.size
    }

    /** The figures standing now (read it from a queued task). */
    val placed: List<PlacedFigure> get() = figures

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
        backgroundRenderer.createOnGlThread()
        figureRenderer.createOnGlThread()
        arSession.session?.setCameraTextureName(backgroundRenderer.textureId)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        arSession.session?.setDisplayGeometry(0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val session = arSession.session ?: return

        try {
            session.setCameraTextureName(backgroundRenderer.textureId)
            val frame = session.update()
            lastFrame = frame

            val camera = frame.camera

            // Draw camera background
            backgroundRenderer.draw(frame)

            while (true) {
                val task = tasks.poll() ?: break
                task(frame, session)
            }

            if (camera.trackingState == TrackingState.TRACKING) {
                camera.getViewMatrix(view, 0)
                camera.getProjectionMatrix(proj, 0, 0.05f, 50f)
                val estimate = frame.lightEstimate
                if (estimate.state == LightEstimate.State.VALID) estimate.getColorCorrection(light, 0)
                figureRenderer.draw(figures, view, proj, light)
            }

            // Calculate metrics for HUD
            frameCounter++
            val now = System.currentTimeMillis()
            if (now - lastFpsTime >= 1000) {
                fps = frameCounter
                frameCounter = 0
                lastFpsTime = now

                // Count tracked planes
                planesCount = session.getAllTrackables(Plane::class.java)
                    .count { it.trackingState == TrackingState.TRACKING }
            }

        } catch (e: Exception) {
            // Ignore AR update exceptions on GL thread
        }
    }
}
