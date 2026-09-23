package com.rockhard.blocker

import android.content.Context

// Resolves a beast's display name (e.g. "[Strong] Cacheon") to the
// spr_<beast>_<anim>.gif drawable saved by tools/sprite_studio, mirroring
// the "spr_<name>_<anim>" naming AudioEngine already uses for SFX lookups.
object SpriteUtils {
    fun resolveSpriteRes(context: Context, displayName: String, anim: String = "idle"): Int {
        var clean = displayName.replace(Regex("\\[.*?\\]"), "").trim().lowercase().replace(" ", "_")
        if (clean.isEmpty()) return 0
        // Last stand shows the human as "YOU"; the Studio row is "player".
        if (clean == "you") clean = "player"

        val res = context.resources
        val pkg = context.packageName
        var id = res.getIdentifier("spr_${clean}_$anim", "drawable", pkg)
        if (id == 0 && anim != "idle") id = res.getIdentifier("spr_${clean}_idle", "drawable", pkg)
        return id
    }
}
