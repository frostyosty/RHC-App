package com.rockhard.blocker.homevisits.ar

import com.google.ar.core.Frame
import com.google.ar.core.Plane

enum class SurfaceKind { FLOOR, BENCH, TABLE, SEAT, WALL, OTHER }

data class SurfaceHit(
    val kind: SurfaceKind,
    val y: Float,
    val isDepth: Boolean
)

object Surfaces {
    fun hit(frame: Frame, x: Float, y: Float): SurfaceHit? {
        val hits = frame.hitTest(x, y)
        for (hit in hits) {
            val trackable = hit.trackable
            if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) {
                val height = hit.hitPose.ty()
                
                // Simple categorization based on plane type (can be refined via height vs floor later)
                val kind = when (trackable.type) {
                    Plane.Type.VERTICAL -> SurfaceKind.WALL
                    Plane.Type.HORIZONTAL_UPWARD_FACING -> SurfaceKind.OTHER
                    else -> SurfaceKind.OTHER
                }
                return SurfaceHit(kind, height, false)
            }
        }
        return null
    }
}
