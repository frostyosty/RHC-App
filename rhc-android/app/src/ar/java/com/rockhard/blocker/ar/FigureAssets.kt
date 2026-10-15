package com.rockhard.blocker.ar

import android.content.Context
import android.util.Log
import com.rockhard.blocker.figure.Clip
import com.rockhard.blocker.figure.Figure
import com.rockhard.blocker.figure.Gltf
import com.rockhard.blocker.figure.Mannequin

/**
 * Figures and moves from the APK's assets (`assets/figures/`, written by
 * `tools/figures/import.sh`). Slow for a real figure (a few hundred ms), so
 * call it off the main and GL threads.
 */
object FigureAssets {
    class Loaded(val figure: Figure, val clips: List<Clip>, val isMannequin: Boolean) {
        fun clip(name: String) = clips.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    /**
     * The figure in `figures/<name>.glb` with the moves in `figures/moves.glb`
     * and any of its own. Falls back to the blocky [Mannequin] when the figure
     * hasn't been imported, so the AR test screen works without one.
     */
    fun load(context: Context, name: String): Loaded {
        return try {
            val gltf = Gltf.parse(read(context, "figures/$name.glb"))
            val own = Clip.from(gltf)
            val shared = try { Clip.from(Gltf.parse(read(context, "figures/moves.glb"))) } catch (e: java.io.IOException) { emptyList() }
            Loaded(Figure.from(gltf), own + shared, isMannequin = false)
        } catch (e: java.io.IOException) {
            Loaded(Mannequin.figure(), Mannequin.clips(), isMannequin = true)
        } catch (e: IllegalArgumentException) {
            Log.e("FigureAssets", "figures/$name.glb: ${e.message}")
            Loaded(Mannequin.figure(), Mannequin.clips(), isMannequin = true)
        }
    }

    private fun read(context: Context, path: String): ByteArray =
        context.assets.open(path).use { it.readBytes() }
}
