package com.rockhard.blocker

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.rockhard.blocker.world.sim.EntityState
import kotlin.math.abs
import kotlin.math.roundToInt

// While you walk, your cages ride down the left edge in the order they'll be
// thrown: the top one first when a beast squares up, then the next each time
// one falls. Drag a cage up or down to change the order. The walk order is its
// own thing (the party's lead stays put) and carries over to the next walk.
//
// Tap a cage and you toss it down: the netbeast comes out and roams near you
// for the rest of the walk (GameActivity.worldRoaming). It doesn't fight, so
// its cage is gone from the column and from the fight panel until the walk
// ends. A [Looter] goes after coins while it's out, and they're yours.

private const val WALK_CAGE_DP = 40
private const val WALK_CAGE_GAP_DP = 4
private const val WALK_ORDER_KEY = "WALK_ORDER"
/** A touch on a cage shorter than this that doesn't move is a tap. */
private const val TAP_MS = 400L

/**
 * Your whole party in walk order: the saved order (by `boughtAt`, which stays the same as a
 * netbeast evolves or gains traits), then anyone it doesn't cover yet, the party lead first.
 */
private fun GameActivity.savedWalkOrder(): List<Netbeast> {
    val saved = prefs.getString(WALK_ORDER_KEY, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }
    val left = party.toMutableList()
    val out = mutableListOf<Netbeast>()
    for (id in saved) left.firstOrNull { it.boughtAt == id }?.let { out += it; left.remove(it) }
    party.getOrNull(activePetIndex)?.takeIf { it in left }?.let { out += it; left.remove(it) }
    return out + left
}

private fun GameActivity.isRoaming(p: Netbeast) = worldRoaming.any { it === p }

/** The netbeasts still in their cages, in walk order: the ones you can throw into a fight. */
internal fun GameActivity.walkOrder(): List<Netbeast> = savedWalkOrder().filterNot { isRoaming(it) }

/** No cage left to throw: you have no netbeasts, or they're all out roaming. */
internal fun GameActivity.noCages() = walkOrder().isEmpty()

/** What the log says when a fight finds you with [noCages]. */
internal fun GameActivity.noCagesLine() =
    if (party.isEmpty()) "> You have no netbeasts. Get your fists up!" else "> Your netbeasts are all off roaming. Get your fists up!"

/** The party index of whoever is next in walk order (the top cage, or the next one after it falls). */
internal fun GameActivity.walkLeadIndex(): Int =
    walkOrder().firstOrNull()?.let { l -> party.indexOfFirst { it === l } }?.takeIf { it >= 0 } ?: 0

/** Shows the column while walking (hidden in a fight, where the fight panel has the cages). */
internal fun GameActivity.refreshWalkCages() {
    val col = findViewById<LinearLayout>(R.id.worldWalkCages) ?: return
    if (!inWorld || worldFight != null || party.isEmpty()) { col.visibility = View.GONE; col.removeAllViews(); return }
    col.removeAllViews()
    walkOrder().forEachIndexed { i, p -> col.addView(walkCage(p, i + 1)) }
    col.visibility = View.VISIBLE
}

private fun GameActivity.walkCage(p: Netbeast, order: Int): View {
    val d = resources.displayMetrics.density
    fun px(dp: Int) = (dp * d).toInt()
    val cage = cageIcon(p).apply {
        background = cageBackground()
        tag = p
        layoutParams = LinearLayout.LayoutParams(px(WALK_CAGE_DP), px(WALK_CAGE_DP)).apply { bottomMargin = px(WALK_CAGE_GAP_DP) }
    }
    cage.addView(TextView(this).apply {
        text = "$order"; setTextColor(Color.WHITE); textSize = 9f; setShadowLayer(2f, 0f, 0f, Color.BLACK)
        setPadding(px(3), 0, 0, 0)
    }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START))
    // badly hurt: it'll go in weak
    if (p.hp < p.maxHp / 4) cage.alpha = 0.6f
    cage.setOnTouchListener(cageTouch())
    return cage
}

/**
 * Drag a cage along the column: it follows your finger and the cages it passes step out of its
 * way, and the order saves on release. The views only slide (translationY) while you drag:
 * taking the one under your finger out of the column would end its touch. A touch that never
 * moves is a tap, which lets the netbeast out.
 */
@SuppressLint("ClickableViewAccessibility")
private fun GameActivity.cageTouch() = object : View.OnTouchListener {
    val slop = ViewConfiguration.get(this@cageTouch).scaledTouchSlop
    var held = false
    var downY = 0f
    var downAt = 0L
    var from = 0        // where it sat when you picked it up
    var to = 0          // where it would sit if you let go now
    var dragged = false

    override fun onTouch(v: View, e: MotionEvent): Boolean {
        val col = v.parent as? LinearLayout ?: return false
        val slot = v.height + WALK_CAGE_GAP_DP * resources.displayMetrics.density
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                held = true; dragged = false; downY = e.rawY; downAt = SystemClock.uptimeMillis()
                from = col.indexOfChild(v); to = from
                v.elevation = 8f; v.scaleX = 1.15f; v.scaleY = 1.15f; vibratePhone(15)
            }
            MotionEvent.ACTION_MOVE -> if (held) {
                val dy = e.rawY - downY
                if (abs(dy) > slop) dragged = true
                if (!dragged) return true
                val offset = dy.coerceIn(-from * slot, (col.childCount - 1 - from) * slot)
                v.translationY = offset
                val now = from + (offset / slot).roundToInt()
                if (now != to) {
                    to = now; vibratePhone(10)
                    for (i in 0 until col.childCount) {
                        val c = col.getChildAt(i).takeIf { it !== v } ?: continue
                        val shift = when (i) { in from + 1..to -> -slot; in to until from -> slot; else -> 0f }
                        c.animate().translationY(shift).setDuration(90).start()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (held) {
                held = false
                v.elevation = 0f; v.scaleX = 1f; v.scaleY = 1f
                val tapped = e.actionMasked == MotionEvent.ACTION_UP && !dragged && SystemClock.uptimeMillis() - downAt < TAP_MS
                when {
                    tapped -> (v.tag as? Netbeast)?.let { col.post { letOutToRoam(it) } }
                    to != from -> {
                        // it drops into its new slot, then the column is rebuilt in that order
                        v.translationY = (to - from) * slot
                        val order = (0 until col.childCount).mapNotNull { col.getChildAt(it).tag as? Netbeast }.toMutableList()
                        order.add(to, order.removeAt(from))
                        col.post { commitCageOrder(order) }
                    }
                    else -> v.animate().translationY(0f).setDuration(90).start()
                }
            }
        }
        return true
    }
}

/**
 * [caged] becomes the walk order (the party's own order and lead don't change). Netbeasts out
 * roaming keep the places they had.
 */
private fun GameActivity.commitCageOrder(caged: List<Netbeast>) {
    val next = caged.iterator()
    val order = savedWalkOrder().mapNotNull { if (isRoaming(it)) it else if (next.hasNext()) next.next() else null }
    prefs.edit().putString(WALK_ORDER_KEY, order.joinToString(",") { it.boughtAt.toString() }).apply()
    worldSession?.setCompanion(leadCompanion())
    refreshWalkCages() // renumber
}

/**
 * You toss [p]'s cage down while walking: it comes out and roams near you until the walk ends.
 * It's out of every fight on this walk. A [Looter] goes after coins.
 */
private fun GameActivity.letOutToRoam(p: Netbeast) {
    val s = worldSession ?: return
    val me = s.world.entities[s.localPlayerId] ?: return
    if (!inWorld || worldFight != null || me.state != EntityState.WALKING || isRoaming(p) || party.none { it === p }) return
    worldRoaming += p
    val looter = p.name.contains("[Looter]")
    s.letOut(speciesOf(p), looter)
    s.setCompanion(leadCompanion())
    vibratePhone(30)
    printLog("> You toss ${p.name}'s cage down. It scampers out to roam${if (looter) ", nose to the ground for coins" else ""}. It won't fight on this walk.")
    refreshWalkCages()
}
