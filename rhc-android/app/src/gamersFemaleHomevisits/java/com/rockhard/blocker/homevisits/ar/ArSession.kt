package com.rockhard.blocker.homevisits.ar

import android.app.Activity
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session

class ArSession(private val activity: Activity) {
    var session: Session? = null
        private set
    
    var tier: ArTier = ArTier.NONE
        private set

    fun setup(): Boolean {
        try {
            // Request installation of ARCore if not present
            val installStatus = ArCoreApk.getInstance().requestInstall(activity, true)
            if (installStatus == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                return false // Play Store launched, waiting for installation
            }
            
            session = Session(activity)
            val config = Config(session)
            
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            config.lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
            config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
            config.focusMode = Config.FocusMode.AUTO

            // Lock to 30 fps to save battery and reduce heat
            val filter = com.google.ar.core.CameraConfigFilter(session)
            filter.targetFps = java.util.EnumSet.of(com.google.ar.core.CameraConfig.TargetFps.TARGET_FPS_30)
            val cameraConfigs = session!!.getSupportedCameraConfigs(filter)
            if (cameraConfigs.isNotEmpty()) {
                session!!.cameraConfig = cameraConfigs[0]
            }
            
            if (session!!.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                config.depthMode = Config.DepthMode.AUTOMATIC
                tier = ArTier.FULL
            } else {
                tier = ArTier.BASIC
            }
            
            session!!.configure(config)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun resume() { session?.resume() }
    fun pause() { session?.pause() }
    fun destroy() { 
        session?.close()
        session = null
    }
}
