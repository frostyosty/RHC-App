package com.rockhard.blocker.ar

import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose

/** Which way a plane faces. What it is (a floor, a bench, a park lawn) is the game's to decide, e.g. by height. */
enum class Facing { UP, DOWN, WALL }

/** A tap that landed on a found plane: which way it faces and its height in the session's world space. */
data class SurfaceHit(
    val facing: Facing,
    val y: Float,
    val isDepth: Boolean,
    val result: HitResult,
) {
    val pose: Pose get() = result.hitPose

    /** An anchor at the hit, kept upright (no tilt or turn), that moves with the plane as ARCore refines it. */
    fun uprightAnchor(): Anchor =
        result.trackable.createAnchor(Pose.makeTranslation(pose.tx(), pose.ty(), pose.tz()))
}

object Surfaces {
    fun hit(frame: Frame, x: Float, y: Float): SurfaceHit? {
        for (hit in frame.hitTest(x, y)) {
            val trackable = hit.trackable
            if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) {
                val facing = when (trackable.type) {
                    Plane.Type.VERTICAL -> Facing.WALL
                    Plane.Type.HORIZONTAL_DOWNWARD_FACING -> Facing.DOWN
                    else -> Facing.UP
                }
                return SurfaceHit(facing, hit.hitPose.ty(), false, hit)
            }
        }
        return null
    }
}
