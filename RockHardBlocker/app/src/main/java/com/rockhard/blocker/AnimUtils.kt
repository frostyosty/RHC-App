// app/src/main/java/com/rockhard/blocker/AnimUtils.kt
package com.rockhard.blocker

/*
 * FILE: AnimUtils.kt
 * DESCRIPTION: Handles hardware-accelerated View animations (Shake, Evade, Death).
 */

import android.view.View

object AnimUtils {
    fun animShake(v: View) {
        v.animate().translationXBy(20f).setDuration(50).withEndAction { 
            v.animate().translationXBy(-40f).setDuration(50).withEndAction { 
                v.animate().translationXBy(20f).setDuration(50).start() 
            }.start() 
        }.start()
    }
    
    fun animEvade(v: View, isPlayer: Boolean) {
        val dir = if (isPlayer) -50f else 50f
        v.animate().translationXBy(dir).translationYBy(50f).setDuration(150).withEndAction { 
            v.animate().translationXBy(-dir).translationYBy(-50f).setDuration(150).start() 
        }.start()
    }
    
    fun animDeath(v: View) {
        v.animate().alpha(0.2f).setDuration(100).withEndAction { 
            v.animate().alpha(1f).setDuration(100).withEndAction { 
                v.animate().alpha(0f).setDuration(800).start() 
            }.start() 
        }.start()
    }
}
