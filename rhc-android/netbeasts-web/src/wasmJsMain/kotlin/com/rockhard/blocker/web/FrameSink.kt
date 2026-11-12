package com.rockhard.blocker.web

import org.khronos.webgl.Int32Array
import org.khronos.webgl.set
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.wasmMemory
import kotlin.wasm.unsafe.withScopedMemoryAllocator

/**
 * Puts the renderer's frame (ARGB ints in a Kotlin IntArray) on the canvas. The canvas is
 * the render size and CSS scales it up with `image-rendering: pixelated`, like the phone.
 * ARGB to ABGR is the swap: ABGR ints are RGBA bytes on a little-endian machine (all of them).
 */
interface FrameSink {
    fun put(fb: IntArray)
}

/** Swaps the red and blue bytes: ARGB <-> ABGR, both ways. */
internal fun swapRedBlue(p: Int) = (p and 0xFF00FF00.toInt()) or ((p ushr 16) and 0xFF) or ((p and 0xFF) shl 16)

/** A frame pixel for the canvas: always opaque, whatever the renderer left in alpha. */
internal fun argbToAbgr(p: Int) = swapRedBlue(p) or OPAQUE
private const val OPAQUE = 0xFF000000.toInt()

/** Option 1 of the plan: Kotlin writes each pixel into an Int32Array view of the ImageData. The fallback (`?sink=typed`). */
class TypedArraySink(canvas: HTMLCanvasElement, w: Int, h: Int) : FrameSink {
    private val ctx = canvas.getContext("2d") as CanvasRenderingContext2D
    private val img = ctx.createImageData(w.toDouble(), h.toDouble())
    private val px = Int32Array(img.data.buffer)

    override fun put(fb: IntArray) {
        for (i in fb.indices) px[i] = argbToAbgr(fb[i])
        ctx.putImageData(img, 0.0, 0.0)
    }
}

/**
 * The default. Kotlin writes the frame into wasm linear memory (plain i32 stores), and one JS
 * call hands that memory to the canvas: about 4x cheaper than [TypedArraySink], whose every
 * pixel is a call into JS.
 */
@OptIn(UnsafeWasmMemoryApi::class)
class MemorySink(canvas: HTMLCanvasElement, private val w: Int, private val h: Int) : FrameSink {
    private val ctx = canvas.getContext("2d") as CanvasRenderingContext2D

    override fun put(fb: IntArray) = withScopedMemoryAllocator { alloc ->
        val start = alloc.allocate(fb.size * 4)
        var at = start
        for (p in fb) { at.storeInt(argbToAbgr(p)); at += 4 }
        blitFromMemory(ctx, wasmMemory, start.address.toInt(), w, h)
    }
}

// The buffer is fetched on every call: growing the memory detaches the old one.
private fun blitFromMemory(ctx: CanvasRenderingContext2D, memory: JsAny, ptr: Int, w: Int, h: Int): Unit =
    js("ctx.putImageData(new ImageData(new Uint8ClampedArray(memory.buffer, ptr, w * h * 4), w, h), 0, 0)")
