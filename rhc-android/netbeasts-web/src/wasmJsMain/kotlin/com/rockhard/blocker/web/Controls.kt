package com.rockhard.blocker.web

import kotlinx.browser.window
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.pointerevents.PointerEvent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow

/**
 * Steering and looking, with the phone's feel (WorldView): drag sideways to steer (a full-width
 * swipe turns about 150 degrees), drag up/down to look, stiffer the further from level, and the
 * look drifts back to level when you let go. On a keyboard, A/D or the arrows steer and W/S or
 * up/down look. [steer] goes to the session; the look is the camera's, never the sim's.
 */
class Controls(private val canvas: HTMLCanvasElement, private val steer: (Double) -> Unit) {
    /** Look, in render pixels: positive moves the horizon down (looking up). */
    var pitch = 0.0
        private set
    /** The render height, which the look is measured in. */
    var renderH = 300

    private val maxPitch get() = renderH * 0.3
    private var pointer = -1
    private var lastX = 0.0
    private var lastY = 0.0
    private val keys = HashSet<String>()

    init {
        canvas.addEventListener("pointerdown", { e ->
            val p = e.unsafeCast<PointerEvent>()
            if (pointer == -1) {
                pointer = p.pointerId; lastX = p.clientX.toDouble(); lastY = p.clientY.toDouble()
                canvas.setPointerCapture(p.pointerId)
            }
        })
        canvas.addEventListener("pointermove", { e ->
            val p = e.unsafeCast<PointerEvent>()
            if (p.pointerId == pointer) {
                val dx = p.clientX - lastX; val dy = p.clientY - lastY
                lastX = p.clientX.toDouble(); lastY = p.clientY.toDouble()
                steer(dx / canvas.clientWidth * PI * 0.85)
                look(dy / canvas.clientHeight * renderH * 1.2) // dragging down looks up
            }
        })
        val up = { e: org.w3c.dom.events.Event -> if (e.unsafeCast<PointerEvent>().pointerId == pointer) pointer = -1 }
        canvas.addEventListener("pointerup", up)
        canvas.addEventListener("pointercancel", up)
        window.addEventListener("keydown", { e ->
            val k = e.unsafeCast<KeyboardEvent>()
            if (k.key in KEYS) { keys += k.key.lowercase(); e.preventDefault() } // arrows don't scroll the page
        })
        window.addEventListener("keyup", { e -> keys -= e.unsafeCast<KeyboardEvent>().key.lowercase() })
        window.addEventListener("blur", { keys.clear(); pointer = -1 })
    }

    /** Once a frame: the held keys, and the drift back to level. */
    fun tick(dt: Double) {
        val turn = (if (held("d", "arrowright")) 1 else 0) - (if (held("a", "arrowleft")) 1 else 0)
        if (turn != 0) steer(turn * KEY_TURN * dt)
        val tilt = (if (held("w", "arrowup")) 1 else 0) - (if (held("s", "arrowdown")) 1 else 0)
        if (tilt != 0) look(tilt * renderH * KEY_LOOK * dt)
        else if (pointer == -1) pitch *= 0.94.pow(dt * 60) // the phone's 0.94 a frame, at 60 fps
    }

    private fun held(a: String, b: String) = a in keys || b in keys

    private fun look(step: Double) {
        val outward = step * pitch > 0
        val stiff = if (outward) 1 - (abs(pitch) / maxPitch).coerceAtMost(1.0).let { it * it } else 1.0
        pitch = (pitch + step * stiff).coerceIn(-maxPitch, maxPitch)
    }

    private companion object {
        val KEYS = setOf("a", "d", "w", "s", "A", "D", "W", "S", "ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown")
        const val KEY_TURN = 1.6 // radians a second
        const val KEY_LOOK = 0.6 // render heights a second
    }
}
