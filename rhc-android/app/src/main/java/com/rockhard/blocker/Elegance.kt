package com.rockhard.blocker

import android.app.Activity
import android.content.Context
import android.content.res.AssetManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

/**
 * The Homevisits APK's softer look: script headings, a book serif for the
 * rest, a dusk backdrop, and screens that fade in instead of snapping on.
 * Every call is a no-op in the other three APKs, so screens shared by all
 * flavors can call it.
 *
 * The fonts are Great Vibes (script) and Cormorant Garamond (serif), both
 * SIL OFL, subset to Latin plus macrons. They live in the Homevisits flavor's
 * `assets/fonts/` so the other APKs don't carry them; if one is missing the
 * nearest Android system family stands in.
 */
object Elegance {
    val on get() = Flavor.homevisits()

    class Faces(
        val script: Typeface,   // headings and titles
        val body: Typeface,     // ordinary text
        val light: Typeface,    // big numbers
        val label: Typeface,    // buttons and small capitals
        val italic: Typeface,   // asides
    ) {
        internal val all = setOf(script, body, light, label, italic)
    }

    @Volatile private var loaded: Faces? = null

    fun faces(ctx: Context): Faces = loaded ?: synchronized(this) {
        loaded ?: load(ctx.applicationContext.assets).also { loaded = it }
    }

    private fun load(assets: AssetManager): Faces {
        fun font(name: String, family: String, style: Int = Typeface.NORMAL) =
            try { Typeface.createFromAsset(assets, "fonts/$name.ttf") } catch (e: RuntimeException) { Typeface.create(family, style) }
        return Faces(
            script = font("great_vibes", "cursive"),
            body = font("cormorant_medium", "serif"),
            light = font("cormorant_light", "serif"),
            label = font("cormorant_semibold", "serif", Typeface.BOLD),
            italic = font("cormorant_italic", "serif", Typeface.ITALIC),
        )
    }

    const val IVORY = 0xFFF4EDE4.toInt()
    const val ROSE_GOLD = 0xFFD8A48F.toInt()
    const val MUTED = 0xFFA89BA5.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    /** Great Vibes reads small at the same size, and Cormorant's lower case is short, so both go up. */
    private const val SCRIPT_SCALE = 1.5f
    private const val SERIF_SCALE = 1.12f

    /** A dusk gradient, deep plum at the top to near-black. */
    fun backdrop() = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF241B2B.toInt(), 0xFF141018.toInt(), 0xFF0D0B10.toInt()))

    /**
     * Restyles the text under [root]. Bold labels with no id (the static
     * section titles) become script headings; buttons get tracked semibold
     * serif; the rest goes to the body serif. Monospace (the debug console) is
     * left alone, and running it twice changes nothing.
     */
    fun dress(root: View) {
        if (!on) return
        val f = faces(root.context)
        when (root) {
            is ViewGroup -> for (i in 0 until root.childCount) dress(root.getChildAt(i))
            is TextView -> when {
                root.typeface == Typeface.MONOSPACE || root.typeface in f.all -> {}
                root is EditText -> serif(root, f.body)
                root is Button -> { serif(root, f.label); root.letterSpacing = 0.1f }
                root.typeface?.isBold == true && root.id == View.NO_ID -> heading(root)
                else -> { serif(root, f.body); if (root.currentTextColor == WHITE) root.setTextColor(IVORY) }
            }
        }
    }

    private fun serif(tv: TextView, face: Typeface) {
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, tv.textSize * SERIF_SCALE)
        tv.typeface = face
    }

    /** A script heading. ALL-CAPS text is title-cased first, since capitals in script are hard to read. */
    fun heading(tv: TextView) {
        if (!on) return
        val script = faces(tv.context).script
        if (tv.typeface == script) return
        val text = tv.text?.toString().orEmpty()
        if (text.isNotEmpty() && text == text.uppercase()) {
            tv.text = text.lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
        }
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, tv.textSize * SCRIPT_SCALE)
        tv.typeface = script
        if (tv.currentTextColor == WHITE) tv.setTextColor(ROSE_GOLD)
    }

    /** Fades [group]'s visible children up into place one after another. */
    fun reveal(group: ViewGroup, stepMs: Long = 70, startMs: Long = 0) {
        if (!on) return
        val rise = 18 * group.resources.displayMetrics.density
        var n = 0
        for (i in 0 until group.childCount) {
            val v = group.getChildAt(i)
            if (v.visibility != View.VISIBLE) continue
            fadeUp(v, startMs + minOf(n++, 8) * stepMs, rise)
        }
    }

    /** One view fading and rising into place. */
    fun fadeUp(v: View, delayMs: Long, risePx: Float = 18 * v.resources.displayMetrics.density, durationMs: Long = 650) {
        v.alpha = 0f
        v.translationY = risePx
        v.animate().alpha(1f).translationY(0f).setStartDelay(delayMs).setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator(1.8f)).start()
    }

    /** A cross-fade instead of the default slide, called right after startActivity / finish or in onCreate. */
    @Suppress("DEPRECATION") // the replacement (overrideActivityTransition) is API 34+
    fun crossFade(activity: Activity) {
        if (on) activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
