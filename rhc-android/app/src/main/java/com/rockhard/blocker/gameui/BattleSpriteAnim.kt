package com.rockhard.blocker

// Briefly swaps a combatant's GifView to a move-triggered frame
// (attack/hit/faint/victory), then reverts to idle once holdMs elapses -
// unless a newer call for the same side has already superseded it
// (checked via CombatState's generation counters), or revertToIdle is
// false for a terminal pose (faint/victory) that should stick until the
// arena resets.
internal fun GameActivity.playSpriteAnim(isPlayer: Boolean, name: String, anim: String, holdMs: Long = 450, revertToIdle: Boolean = true) {
    val gifView = findViewById<GifView>(if (isPlayer) R.id.spritePlayerGif else R.id.spriteEnemyGif) ?: return
    val res = SpriteUtils.resolveSpriteRes(this, name, anim)
    if (res == 0) return

    val myGen = if (isPlayer) ++CombatState.playerSpriteAnimGen else ++CombatState.enemySpriteAnimGen
    gifView.setGifResource(res)

    if (revertToIdle) {
        mainHandler.postDelayed({
            val stillCurrent = if (isPlayer) CombatState.playerSpriteAnimGen == myGen else CombatState.enemySpriteAnimGen == myGen
            if (stillCurrent) {
                val idleRes = SpriteUtils.resolveSpriteRes(this, name, "idle")
                if (idleRes != 0) gifView.setGifResource(idleRes)
            }
        }, holdMs)
    }
}

// Plays a move's attack effect (SkillEngine fx, e.g. fx_laser / fx_bite) over
// the defender. Moves without an fx do nothing.
internal fun GameActivity.playAttackFx(onPlayer: Boolean, moveName: String) {
    SkillEngine.fxFor(moveName)?.let { playFx(onPlayer, it) }
}

// Plays fx_<fx>.gif once over one combatant, then hides it. The GIFs end on
// an empty frame, so hiding a frame late never shows a stuck effect.
internal fun GameActivity.playFx(onPlayer: Boolean, fx: String) {
    val view = findViewById<GifView>(if (onPlayer) R.id.spritePlayerFx else R.id.spriteEnemyFx) ?: return
    val res = resources.getIdentifier("fx_$fx", "drawable", packageName)
    if (res == 0) return
    val gen = ((view.tag as? Int) ?: 0) + 1
    view.tag = gen
    view.setGifResource(res)
    val ms = view.durationMs().takeIf { it > 0 } ?: 500
    mainHandler.postDelayed({ if (view.tag == gen) view.setGifResource(null) }, ms.toLong())
}
