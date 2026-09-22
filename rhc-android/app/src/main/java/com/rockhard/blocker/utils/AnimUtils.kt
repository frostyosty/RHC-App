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

    // THE LUNGE ANIMATION
    fun fireProjectile(vfx: View, isPlayer: Boolean, attackerType: String, defenderType: String, moveName: String) {
        vfx.visibility = View.VISIBLE; (vfx as android.widget.TextView).text = moveName
        var angle = 0f
        if (attackerType == "Flying" && defenderType != "Flying") angle = 25f
        if (attackerType != "Flying" && defenderType == "Flying") angle = -25f
        if (attackerType == "EventBoss") angle = 35f
        if (defenderType == "EventBoss") angle = -35f
        if (!isPlayer) angle = -angle
        vfx.rotation = angle
        val startX = if (isPlayer) -200f else 200f; val endX = if (isPlayer) 200f else -200f
        vfx.translationX = startX; vfx.translationY = 0f
        vfx.animate().translationX(endX).setDuration(300).withEndAction { vfx.visibility = View.GONE }.start()
    }
    fun animAttack(v: View, isPlayer: Boolean) {
        val dir = if (isPlayer) 150f else -150f
        v.animate().translationXBy(dir).setDuration(100).setInterpolator(AccelerateDecelerateInterpolator()).withEndAction {
            v.animate().translationXBy(-dir).setDuration(200).setInterpolator(AccelerateDecelerateInterpolator()).start()
        }.start()
    }
}