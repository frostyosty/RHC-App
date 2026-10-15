package com.rockhard.blocker.ar

import com.google.ar.core.Camera
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState

/**
 * What to say when ARCore loses track. Each game passes its own wording: "Can you switch a light
 * on?" is right in a lounge and wrong in a park.
 */
class TrackingPhrases(
    val starting: String,
    val tooDark: String,
    val tooFast: String,
    val tooPlain: String,
    val noCamera: String,
    val searching: String,
)

object Tracking {
    /** Null while tracking; otherwise the game's line for what's wrong. */
    fun getHint(camera: Camera?, phrases: TrackingPhrases): String? {
        if (camera == null) return phrases.starting
        if (camera.trackingState == TrackingState.TRACKING) return null

        return when (camera.trackingFailureReason) {
            TrackingFailureReason.INSUFFICIENT_LIGHT -> phrases.tooDark
            TrackingFailureReason.EXCESSIVE_MOTION -> phrases.tooFast
            TrackingFailureReason.INSUFFICIENT_FEATURES -> phrases.tooPlain
            TrackingFailureReason.CAMERA_UNAVAILABLE -> phrases.noCamera
            else -> phrases.searching
        }
    }
}
