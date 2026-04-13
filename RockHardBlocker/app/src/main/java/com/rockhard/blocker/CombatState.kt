package com.rockhard.blocker

object CombatState {
    var playerShield = 0
    var enemyPoisonStacks = 0
    var enemyStunned = false
    var terrified = false

    fun reset() {
        playerShield = 0
        enemyPoisonStacks = 0
        enemyStunned = false
        terrified = false
    }
}