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
import com.rockhard.blocker.world.sim.EntityState
import com.rockhard.blocker.world.sim.Role
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
 * In a fight you circle automatically and dragging sideways picks which way.
 * Renders at ~200px wide and scales up unfiltered for the chunky pixel look.
 */
class WorldView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs), Choreographer.FrameCallback {

    interface Listener {
        fun onEncounter(e: WorldEvent.Encounter)
        fun onCompanionOut(e: WorldEvent.CompanionOut)
        fun onExplorationOver()
    }

    /** A fighter's name and health, for the bars over their heads. */
    class Fighter(val name: String, val level: Int, val hp: Int, val maxHp: Int)

    /** What the battle rules know about the current fight (WorldFight.kt). */
    interface FightHud {
        fun enemy(): Fighter?
        fun mine(): Fighter?
        /** 1 = full .. 0 = the beast acts; negative hides the bar. */
        fun patience(): Float
    }

    var session: WorldSession? = null
        set(value) { if (field !== value) { field = value; renderer = null; minimap = null } }
    var palette: Palette = Palette.DAY
    var listener: Listener? = null
    var fightHud: FightHud? = null

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
    val isRunning get() = running
    private var lastNanos = 0L
    private var shownAt = 0L

    // Look: pitch is camera-only (not part of the shared sim), in render pixels
    private var pitch = 0.0
    private val maxPitch get() = rh * 0.3
    private var touchId = -1
    private var lastX = 0f
    private var lastY = 0f

    // Battle log lines shown along the bottom for a few seconds each
    private class Caption(val text: String, val at: Long)
    private val captions = ArrayDeque<Caption>()

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

    /** Shows a line from the battle log over the world. */
    fun caption(text: String) {
        val t = text.trim().removePrefix(">").trim()
        if (t.isEmpty() || t.startsWith("---") || t.startsWith("===")) return
        captions.addLast(Caption(t, SystemClock.uptimeMillis()))
        while (captions.size > 3) captions.removeFirst()
        invalidate()
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
                is WorldEvent.CompanionOut -> listener?.onCompanionOut(e)
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
        val fighting = me.state == EntityState.ENGAGED
        drawMinimap(canvas, s)

        // time left, and what's in your cages
        val secs = (me.exploreTicks / World.TICK_HZ).coerceAtLeast(0)
        hud.style = Paint.Style.FILL; hud.textAlign = Paint.Align.LEFT
        hud.setShadowLayer(3f, 0f, 0f, Color.BLACK)
        hud.color = Color.WHITE; hud.textSize = 16 * density
        canvas.drawText("⏱ %d:%02d".format(secs / 60, secs % 60), 12 * density, 26 * density, hud)
        hud.textSize = 12 * density
        if (!fighting) {
            hud.color = Color.rgb(255, 214, 102)
            canvas.drawText("🔒 ${me.companion ?: "no netbeasts: you fight"}", 12 * density, 44 * density, hud)
        }
        // what's underfoot, when it changes your pace
        when (s.world.map.terrainAt(me.x, me.y)) {
            Terrain.WATER -> { hud.color = Color.rgb(140, 200, 255); canvas.drawText("wading…", 12 * density, 60 * density, hud) }
            Terrain.MUD -> { hud.color = Color.rgb(190, 160, 120); canvas.drawText("squelching through mud…", 12 * density, 60 * density, hud) }
            Terrain.PATH -> { hud.color = Color.rgb(230, 210, 170); canvas.drawText("on a track: quicker", 12 * density, 60 * density, hud) }
        }
        if (fighting) drawFightHud(canvas, s)
        drawCaptions(canvas, fighting)
        if (!fighting && SystemClock.uptimeMillis() - shownAt < 5000) {
            hud.color = Color.WHITE; hud.textSize = 13 * density; hud.textAlign = Paint.Align.CENTER
            canvas.drawText("You keep walking. Drag to steer and look.", width / 2f, height - 42 * density, hud)
            canvas.drawText("Beasts shy away: walk at one to fight it.", width / 2f, height - 24 * density, hud)
        }
        hud.clearShadowLayer()
    }

    /** Health bars over the wild beast and your netbeast, and the beast's patience under its bar. */
    private fun drawFightHud(canvas: Canvas, s: WorldSession) {
        val r = renderer ?: return
        val h = fightHud ?: return
        val k = width.toFloat() / rw
        fun bar(id: Int?, f: Fighter?, color: Int, patience: Float) {
            val at = id?.let { r.onScreen[it] } ?: return
            f ?: return
            val bw = 64 * density; val bh = 5 * density
            val cx = (at.x * k).coerceIn(bw / 2 + 4 * density, width - bw / 2 - 4 * density)
            val y = (at.top * k + at.height * k * 0.18f - 10 * density).coerceAtLeast(70 * density)
            hud.style = Paint.Style.FILL; hud.clearShadowLayer()
            hud.color = Color.argb(170, 0, 0, 0); canvas.drawRect(cx - bw / 2 - 1, y - 1, cx + bw / 2 + 1, y + bh + 1, hud)
            hud.color = color; canvas.drawRect(cx - bw / 2, y, cx - bw / 2 + bw * (f.hp.coerceAtLeast(0).toFloat() / f.maxHp.coerceAtLeast(1)), y + bh, hud)
            if (patience >= 0) {
                val py = y + bh + 3 * density
                hud.color = Color.argb(170, 0, 0, 0); canvas.drawRect(cx - bw / 2 - 1, py - 1, cx + bw / 2 + 1, py + 3 * density + 1, hud)
                val urgent = patience < 0.35f && (SystemClock.uptimeMillis() / 150) % 2 == 0L
                hud.color = if (patience < 0.35f) (if (urgent) Color.WHITE else Color.rgb(255, 82, 82)) else Color.rgb(255, 196, 64)
                canvas.drawRect(cx - bw / 2, py, cx - bw / 2 + bw * patience, py + 3 * density, hud)
            }
            hud.setShadowLayer(3f, 0f, 0f, Color.BLACK); hud.color = Color.WHITE; hud.textSize = 10 * density; hud.textAlign = Paint.Align.CENTER
            canvas.drawText("${f.name} Lv ${f.level}", cx, y - 3 * density, hud)
        }
        bar(s.world.roleEntity(s.localPlayerId, Role.BEAST)?.id, h.enemy(), Color.rgb(229, 57, 53), h.patience())
        bar(s.world.roleEntity(s.localPlayerId, Role.COMPANION)?.id, h.mine(), Color.rgb(102, 187, 106), -1f)
    }

    private fun drawCaptions(canvas: Canvas, fighting: Boolean) {
        val now = SystemClock.uptimeMillis()
        while (captions.isNotEmpty() && now - captions.first().at > 5000) captions.removeFirst()
        if (captions.isEmpty()) return
        hud.setShadowLayer(3f, 0f, 0f, Color.BLACK); hud.textSize = 12 * density; hud.textAlign = Paint.Align.LEFT
        // clear of the fight panel on the left
        val left = if (fighting) 162 * density else 12 * density
        var y = height - 14 * density
        for (c in captions.reversed()) {
            val alpha = (255 * (1 - ((now - c.at - 3500).coerceAtLeast(0) / 1500f))).toInt().coerceIn(0, 255)
            hud.color = Color.argb(alpha, 255, 255, 255)
            val text = TextFit.ellipsize(c.text, hud, width - left - 8 * density)
            canvas.drawText(text, left, y, hud)
            y -= 16 * density
        }
    }

    /** Top-right overview: terrain, dots for beasts, arrow for you. */
    private fun drawMinimap(canvas: Canvas, s: WorldSession) {
        val map = s.world.map
        val mm = minimap ?: Bitmap.createBitmap(map.size, map.size, Bitmap.Config.ARGB_8888).also { b ->
            val px = IntArray(map.size * map.size) { i ->
                when (map.terrain[i]) {
                    Terrain.WATER -> Color.argb(190, 60, 110, 160)
                    Terrain.SAND -> Color.argb(170, 205, 187, 142)
                    Terrain.TALL_GRASS -> Color.argb(170, 74, 111, 46)
                    Terrain.FOREST -> Color.argb(185, 44, 58, 30)
                    Terrain.MUD -> Color.argb(185, 82, 66, 48)
                    Terrain.PATH -> Color.argb(210, 176, 150, 110)
                    else -> Color.argb(150, 94, 138, 60)
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
                e.kind == EntityKind.BEAST && e.state != EntityState.GONE -> { hud.color = Color.rgb(255, 80, 80); canvas.drawCircle(x, y, 1.8f * density, hud) }
                e.kind == EntityKind.PLAYER -> { hud.color = Color.CYAN; canvas.drawCircle(x, y, 2f * density, hud) }
            }
        }
    }
}

/** Shortens a line with "…" to fit [maxWidth] px. */
private object TextFit {
    fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 1 && paint.measureText(text, 0, end) + paint.measureText("…") > maxWidth) end--
        return text.substring(0, end) + "…"
    }
}
