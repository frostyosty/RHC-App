package com.rockhard.blocker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Movie
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View

// Dependency-free animated GIF view: decodes via the (deprecated but still
// functional) android.graphics.Movie API instead of pulling in an image
// library, matching this project's convention of no third-party deps.
class GifView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var movie: Movie? = null
    private var movieStart = 0L

    fun setGifResource(resId: Int?) {
        val newMovie = if (resId != null && resId != 0) {
            try {
                context.resources.openRawResource(resId).use { Movie.decodeStream(it) }
            } catch (e: Exception) {
                null
            }
        } else null

        movie = newMovie
        movieStart = 0L
        visibility = if (newMovie != null) VISIBLE else INVISIBLE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val m = movie ?: return
        val mw = m.width(); val mh = m.height()
        if (mw <= 0 || mh <= 0 || width <= 0 || height <= 0) return

        val now = SystemClock.uptimeMillis()
        if (movieStart == 0L) movieStart = now
        val duration = m.duration().takeIf { it > 0 } ?: 1000
        m.setTime(((now - movieStart) % duration).toInt())

        val scale = minOf(width.toFloat() / mw, height.toFloat() / mh)
        canvas.save()
        canvas.translate((width - mw * scale) / 2f, (height - mh * scale) / 2f)
        canvas.scale(scale, scale)
        m.draw(canvas, 0f, 0f)
        canvas.restore()

        postInvalidateOnAnimation()
    }
}
