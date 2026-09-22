package com.rockhard.blocker

object CombatState {
    var playerShield = 0
    var enemyPoisonStacks = 0
    var enemyStunned = false
    var terrified = false

    // Bumped by every playSpriteAnim() call so a stale delayed
    // revert-to-idle can detect it's been superseded and skip itself.
    var playerSpriteAnimGen = 0
    var enemySpriteAnimGen = 0

    fun reset() {
        playerShield = 0
        enemyPoisonStacks = 0
        enemyStunned = false
        terrified = false
        playerSpriteAnimGen++
        enemySpriteAnimGen++
    }
}