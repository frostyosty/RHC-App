package com.rockhard.blocker.web

import com.rockhard.blocker.world.render.SpriteSource
import com.rockhard.blocker.world.render.Texture
import org.khronos.webgl.Int32Array
import org.khronos.webgl.get

/**
 * The web's SpriteBank: sprite frames from the PNG sheets that tools/web_sprites/make_sheets.py
 * makes from the autogen GIFs (sampled the way SpriteBank samples them, so they animate the
 * same). A sheet loads the first time its key is asked for; until it's in, [frame] returns null,
 * which the renderer copes with (nothing drawn there for a moment).
 */
class SheetSprites(private val base: String) : SpriteSource {
    private class Meta(val w: Int, val h: Int, val ms: IntArray) { val total = ms.sum() }
    private class Anim(val frames: Array<Texture>, val ends: IntArray, val total: Int)

    private var index: Map<String, Meta>? = null
    private val anims = HashMap<String, Anim>()
    private val asked = HashSet<String>()

    /** Sheets loaded and still loading, for the stats line. */
    val loaded get() = anims.size
    val pending get() = asked.size - anims.size

    init {
        fetchText("$base/index.txt") { text -> index = text?.let(::parseIndex) ?: emptyMap() }
    }

    override fun frame(key: String, timeMs: Long): Texture? {
        val a = anims[key]
        if (a == null) { load(key); return null }
        val t = (timeMs % a.total).toInt()
        var i = 0
        while (i < a.ends.size - 1 && t >= a.ends[i]) i++
        return a.frames[i]
    }

    // Known from the index before the sheet is in, so a one-shot effect plays its full length
    override fun durationMs(key: String): Long = index?.get(key)?.total?.toLong() ?: 0

    private fun load(key: String) {
        val meta = index?.get(key) ?: return // index not in yet, or no such sprite
        if (!asked.add(key)) return
        loadPixels("$base/$key.png") { px, sheetW, sheetH ->
            if (px != null && sheetH == meta.h) anims[key] = cut(meta, px, sheetW)
        }
    }

    /** The sheet's frames (side by side, RGBA) as ARGB textures. */
    private fun cut(meta: Meta, px: Int32Array, sheetW: Int): Anim {
        val n = meta.ms.size
        val frames = Array(n) { f ->
            val out = IntArray(meta.w * meta.h)
            for (y in 0 until meta.h) {
                val row = y * sheetW + f * meta.w
                for (x in 0 until meta.w) {
                    val v = px[row + x]
                    // RGBA bytes -> ARGB, alpha kept, and fully clear where it's clear (the renderer tests alpha 0)
                    out[y * meta.w + x] = if (v ushr 24 == 0) 0 else swapRedBlue(v)
                }
            }
            Texture(meta.w, meta.h, out)
        }
        val ends = IntArray(n); var acc = 0
        for (i in 0 until n) { acc += meta.ms[i]; ends[i] = acc }
        return Anim(frames, ends, acc.coerceAtLeast(1))
    }

    /** index.txt: a line a sprite, "key w h ms ms ...". */
    private fun parseIndex(text: String): Map<String, Meta> {
        val out = HashMap<String, Meta>()
        for (line in text.lineSequence()) {
            val p = line.trim().split(' ')
            if (p.size < 4) continue
            out[p[0]] = Meta(p[1].toInt(), p[2].toInt(), IntArray(p.size - 3) { p[it + 3].toInt() })
        }
        return out
    }
}

private fun fetchText(url: String, done: (String?) -> Unit): Unit = js(
    "fetch(url).then(r => r.ok ? r.text() : null).then(t => done(t), () => done(null))"
)

/** Decodes a PNG through an off-screen canvas; hands back its pixels as RGBA ints. */
private fun loadPixels(url: String, done: (Int32Array?, Int, Int) -> Unit): Unit = js("""{
    const img = new Image();
    img.onload = () => {
        const c = document.createElement('canvas');
        c.width = img.width; c.height = img.height;
        const g = c.getContext('2d', { willReadFrequently: true });
        g.drawImage(img, 0, 0);
        done(new Int32Array(g.getImageData(0, 0, img.width, img.height).data.buffer), img.width, img.height);
    };
    img.onerror = () => done(null, 0, 0);
    img.src = url;
}""")
