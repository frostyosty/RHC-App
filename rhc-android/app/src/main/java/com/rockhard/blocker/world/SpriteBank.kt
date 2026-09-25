package com.rockhard.blocker.world

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Movie
import com.rockhard.blocker.world.render.SpriteSource
import com.rockhard.blocker.world.render.Texture

/**
 * Decodes the autogen GIFs (drawable-nodpi/spr_*.gif, prop_*.gif) into
 * texture frames for the raycaster, once per key, using the same
 * dependency-free android.graphics.Movie decoder as GifView.
 */
class SpriteBank(private val context: Context) : SpriteSource {
    private class Anim(val frames: List<Texture>, val frameMs: Long)

    private val cache = HashMap<String, Anim?>()

    override fun frame(key: String, timeMs: Long): Texture? {
        val anim = cache.getOrPut(key) { load(key) } ?: return null
        return anim.frames[((timeMs / anim.frameMs) % anim.frames.size).toInt()]
    }

    override fun durationMs(key: String): Long {
        val anim = cache.getOrPut(key) { load(key) } ?: return 0
        return anim.frames.size * anim.frameMs
    }

    private fun load(key: String): Anim? {
        val id = context.resources.getIdentifier(key, "drawable", context.packageName)
        if (id == 0) return null
        val movie = try {
            context.resources.openRawResource(id).use { Movie.decodeStream(it) }
        } catch (e: Exception) { null } ?: return null
        val w = movie.width(); val h = movie.height()
        if (w <= 0 || h <= 0) return null

        // Movie has no per-frame API, so sample it on a fixed clock. Long
        // terminal holds (faint) are capped; the world never shows those anyway.
        val step = 50L
        val duration = movie.duration().toLong().coerceIn(0, 2000)
        val count = (duration / step).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        // Samples that repeat the previous one share its Texture: a slow
        // 2-frame tree sway would otherwise keep ~36 copies of each frame.
        var last: Texture? = null
        val frames = List(count) { i ->
            bmp.eraseColor(0)
            movie.setTime((i * step).toInt())
            movie.draw(canvas, 0f, 0f)
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            last?.takeIf { it.px.contentEquals(px) } ?: Texture(w, h, px).also { last = it }
        }
        bmp.recycle()
        return Anim(frames, step)
    }
}
