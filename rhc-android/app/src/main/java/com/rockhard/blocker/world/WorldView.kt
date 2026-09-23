package com.rockhard.blocker.world

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import com.rockhard.blocker.world.render.Palette
import com.rockhard.blocker.world.render.TerrainRenderer
import com.rockhard.blocker.world.sim.EntityKind
import com.rockhard.blocker.world.sim.Terrain
import com.rockhard.blocker.world.sim.World
import com.rockhard.blocker.world.sim.WorldEvent
import com.rockhard.blocker.world.sim.WorldSession
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * First-person view of a WorldSession. The player auto-walks; dragging
 * sideways steers and dragging up/down looks, with the look slowing near its
 * limits and drifting back to level when released so steering stays easy.
 * Renders at ~200px wide and scales up unfiltered for the chunky pixel look.
 */
class WorldView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs), Choreographer.FrameCallback {

    interface Listener {
        fun onEncounter(e: WorldEvent.Encounter)
        fun onBattleReady(e: WorldEvent.BattleReady)
        fun onExplorationOver()
    }

    var session: WorldSession? = null
        set(value) { field = value; renderer = null; minimap = null }
    var palette: Palette = Palette.DAY
    var listener: Listener? = null

    private val sprites = SpriteBank(context)
    private var renderer: TerrainRenderer? = null
    private var frame: Bitmap? = null
    private var minimap: Bitmap? = null
    private var rw = 200
    private var rh = 300
    private val dst = Rect()
    private val pixelPaint = Paint().apply { isFilterBitmap = false }
    private val hud = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    private var running = false
    private var lastNanos = 0L
    private var shownAt = 0L

    // Look: pitch is camera-only (not part of the shared sim), in render pixels
    private var pitch = 0.0
    private val maxPitch get() = rh * 0.3
    private var touchId = -1
    private var lastX = 0f
    private var lastY = 0f

    fun start() {
        if (running) return
        running = true; lastNanos = 0L; shownAt = SystemClock.uptimeMillis()
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        running = false
        touchId = -1
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w <= 0 || h <= 0) return
        rw = if (w > h) 320 else 200
        rh = (rw * h.toFloat() / w).toInt().coerceAtLeast(1)
        frame = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
        renderer = null
        dst.set(0, 0, w, h)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        val s = session; val bmp = frame
        if (s != null && bmp != null) {
            val r = renderer ?: TerrainRenderer(rw, rh, s.world.map).also { renderer = it }
            val dt = if (lastNanos == 0L) 0.0 else (frameTimeNanos - lastNanos) / 1e9
            lastNanos = frameTimeNanos
            if (touchId == -1) pitch *= 0.94 // drift back to level
            val events = s.update(dt)
            s.world.entities[s.localPlayerId]?.let { me ->
                r.render(s.world, me, pitch.toInt(), palette, sprites, SystemClock.uptimeMillis())
                bmp.setPixels(r.fb, 0, rw, 0, 0, rw, rh)
            }
            invalidate()
            for (e in events) when (e) {
                is WorldEvent.Encounter -> listener?.onEncounter(e)
                is WorldEvent.BattleReady -> { stop(); listener?.onBattleReady(e); return }
                is WorldEvent.ExplorationOver -> { stop(); listener?.onExplorationOver(); return }
            }
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { touchId = e.getPointerId(0); lastX = e.x; lastY = e.y }
            MotionEvent.ACTION_MOVE -> {
                val i = e.findPointerIndex(touchId)
                if (i >= 0) {
                    val dx = e.getX(i) - lastX; val dy = e.getY(i) - lastY
                    lastX = e.getX(i); lastY = e.getY(i)
                    // a full-width swipe turns about 150 degrees
                    session?.steer(dx / width * Math.PI * 0.85)
                    // dragging down looks up (horizon moves down). Moving further
                    // from level gets stiffer so the view can't flip to sky/feet.
                    val step = dy / height * rh * 1.2
                    val outward = step * pitch > 0
                    val stiff = if (outward) 1 - (abs(pitch) / maxPitch).coerceAtMost(1.0).let { it * it } else 1.0
                    pitch = (pitch + step * stiff).coerceIn(-maxPitch, maxPitch)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touchId = -1
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        frame?.let { canvas.drawBitmap(it, null, dst, pixelPaint) }
        val s = session ?: return
        val me = s.world.entities[s.localPlayerId] ?: return
        drawMinimap(canvas, s)

        // time left, and what's in your cage
        val secs = (me.exploreTicks / World.TICK_HZ).coerceAtLeast(0)
        hud.style = Paint.Style.FILL; hud.textAlign = Paint.Align.LEFT
        hud.setShadowLayer(3f, 0f, 0f, Color.BLACK)
        hud.color = Color.WHITE; hud.textSize = 16 * density
        canvas.drawText("⏱ %d:%02d".format(secs / 60, secs % 60), 12 * density, 26 * density, hud)
        hud.textSize = 12 * density; hud.color = Color.rgb(255, 214, 102)
        canvas.drawText("🔒 ${me.companion ?: "no netbeasts: you fight"}", 12 * density, 44 * density, hud)
        if (s.world.map.terrainAt(me.x, me.y) == Terrain.WATER) {
            hud.color = Color.rgb(140, 200, 255); canvas.drawText("wading…", 12 * density, 60 * density, hud)
        }
        if (SystemClock.uptimeMillis() - shownAt < 5000) {
            hud.color = Color.WHITE; hud.textSize = 13 * density; hud.textAlign = Paint.Align.CENTER
            canvas.drawText("You keep walking. Drag to steer and look.", width / 2f, height - 24 * density, hud)
        }
        hud.clearShadowLayer()
    }

    /** Top-right overview: terrain, dots for beasts, arrow for you. */
    private fun drawMinimap(canvas: Canvas, s: WorldSession) {
        val map = s.world.map
        val mm = minimap ?: Bitmap.createBitmap(map.size, map.size, Bitmap.Config.ARGB_8888).also { b ->
            val px = IntArray(map.size * map.size) { i ->
                when (map.terrain[i]) {
                    Terrain.WATER -> Color.argb(190, 70, 140, 220)
                    Terrain.SAND -> Color.argb(170, 220, 200, 140)
                    Terrain.TALL_GRASS -> Color.argb(170, 50, 110, 40)
                    else -> Color.argb(150, 80, 150, 60)
                }
            }
            b.setPixels(px, 0, map.size, 0, 0, map.size, map.size)
            minimap = b
        }
        val size = 88 * density; val pad = 8 * density
        val left = width - size - pad; val top = pad
        val cell = size / map.size
        canvas.drawBitmap(mm, null, RectF(left, top, left + size, top + size), pixelPaint)
        for (e in s.world.entities.values) {
            val x = left + e.x.toFloat() * cell; val y = top + e.y.toFloat() * cell
            when {
                e.id == s.localPlayerId -> {
                    hud.color = Color.WHITE; hud.strokeWidth = 1.5f * density
                    canvas.drawCircle(x, y, 2.5f * density, hud)
                    canvas.drawLine(x, y, x + cos(e.angle).toFloat() * 6 * density, y + sin(e.angle).toFloat() * 6 * density, hud)
                }
                e.kind == EntityKind.BEAST -> { hud.color = Color.rgb(255, 80, 80); canvas.drawCircle(x, y, 1.8f * density, hud) }
                e.kind == EntityKind.PLAYER -> { hud.color = Color.CYAN; canvas.drawCircle(x, y, 2f * density, hud) }
            }
        }
    }
}
