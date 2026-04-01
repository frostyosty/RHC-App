package com.rockhard.blocker
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

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

    // NEW: THE LUNGE ANIMATION!
    fun animAttack(v: View, isPlayer: Boolean) {
        val dir = if (isPlayer) 150f else -150f
        v.animate().translationXBy(dir).setDuration(100).setInterpolator(AccelerateDecelerateInterpolator()).withEndAction {
            v.animate().translationXBy(-dir).setDuration(200).setInterpolator(AccelerateDecelerateInterpolator()).start()
        }.start()
    }
}
