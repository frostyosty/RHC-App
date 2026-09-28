package com.rockhard.blocker.homevisits.ar

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ArRenderer(private val arSession: ArSession) : GLSurfaceView.Renderer {
    private val backgroundRenderer = BackgroundRenderer()
    
    var lastFrame: Frame? = null
        private set
    var fps = 0
        private set
    var planesCount = 0
        private set

    private var frameCounter = 0
    private var lastFpsTime = System.currentTimeMillis()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
        backgroundRenderer.createOnGlThread()
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
