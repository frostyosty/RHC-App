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
