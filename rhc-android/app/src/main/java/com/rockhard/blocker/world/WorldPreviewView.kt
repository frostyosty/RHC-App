package com.rockhard.blocker.world

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Where the Explore button was: the first frame of today's walk, a window
 * into the Wilds. Touch it and you set off (the host starts the walk from the
 * very world this frame came from), and the same drag goes on to steer. The
 * frame is rendered off the main thread by the host (WorldBridge), at the
 * walk's pixel size, so it looks like the walk's first frame cropped.
 */
class WorldPreviewView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    interface Listener {
        /** The view has a size, so a frame can be rendered for it. */
        fun onSized(w: Int, h: Int)
        fun onSetOff()
        /** A drag after setting off, as a share of this view's width (like WorldView's steering). */
        fun onDrag(dx: Float)
    }

    var listener: Listener? = null

    /** Shown over a dimmed frame when you can't set off (Aether depleted), or null. */
    var message: String? = null
        set(value) { if (field != value) { field = value; invalidate() } }

    private var frame: Bitmap? = null
    private val dst = Rect()
    private val pixelPaint = Paint().apply { isFilterBitmap = false }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shade = Paint()
    private val density = resources.displayMetrics.density
    private var lastX = 0f
    private var dragging = false

    init {
        background = GradientDrawable().apply { cornerRadius = 10 * density; setColor(Color.rgb(12, 16, 12)) }
        clipToOutline = true
        contentDescription = "Explore the Wilds"
    }

    /** The new first frame (or null while it's being made). */
    fun showFrame(bmp: Bitmap?) {
        frame = bmp
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        dst.set(0, 0, w, h)
        shade.shader = LinearGradient(0f, h * 0.45f, 0f, h.toFloat(), 0, Color.argb(190, 0, 0, 0), Shader.TileMode.CLAMP)
        if (w > 0 && h > 0) listener?.onSized(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val f = frame
        text.setShadowLayer(3f, 0f, 0f, Color.BLACK)
        text.textAlign = Paint.Align.LEFT
        if (f == null) {
            text.color = Color.rgb(160, 190, 150); text.textSize = 13 * density
            canvas.drawText("Finding the way into the Wilds…", 14 * density, height / 2f + 5 * density, text)
            return
        }
        canvas.drawBitmap(f, null, dst, pixelPaint)
        canvas.drawRect(dst, shade)
        val msg = message
        if (msg != null) {
            canvas.drawColor(Color.argb(150, 0, 0, 0))
            text.color = Color.rgb(255, 120, 120); text.textSize = 14 * density; text.textAlign = Paint.Align.CENTER
            canvas.drawText(msg, width / 2f, height / 2f + 5 * density, text)
            return
        }
        text.color = Color.WHITE; text.textSize = 16 * density; text.isFakeBoldText = true
        canvas.drawText("🌲 THE WILDS", 14 * density, height - 30 * density, text)
        text.isFakeBoldText = false
        text.color = Color.rgb(255, 214, 102); text.textSize = 12 * density
        canvas.drawText("Touch to set off · a 3-minute walk", 14 * density, height - 12 * density, text)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastX = e.x; dragging = true; performClick() }
            MotionEvent.ACTION_MOVE -> if (dragging && width > 0) { listener?.onDrag((e.x - lastX) / width); lastX = e.x }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
        }
        return true
    }

    /** Touching the frame is the click: you set off at once, and the drag carries on steering. */
    override fun performClick(): Boolean {
        super.performClick()
        listener?.onSetOff()
        return true
    }

}
