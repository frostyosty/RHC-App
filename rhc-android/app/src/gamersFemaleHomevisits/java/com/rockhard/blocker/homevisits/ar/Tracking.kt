package com.rockhard.blocker.homevisits.ar

import com.google.ar.core.Camera
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState

object Tracking {
    fun getHint(camera: Camera?): String? {
        if (camera == null) return "Initializing camera..."
        if (camera.trackingState == TrackingState.TRACKING) return null

        return when (camera.trackingFailureReason) {
            TrackingFailureReason.INSUFFICIENT_LIGHT -> "It's a bit dark in here. Can you switch a light on?"
            TrackingFailureReason.EXCESSIVE_MOTION -> "Whoa, slow down!"
            TrackingFailureReason.INSUFFICIENT_FEATURES -> "Point it at something with a bit more going on."
            TrackingFailureReason.CAMERA_UNAVAILABLE -> "Camera unavailable."
            else -> "Looking for your surroundings..."
        }
    }
}
