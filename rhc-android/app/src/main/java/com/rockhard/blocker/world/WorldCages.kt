package com.rockhard.blocker

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

// While you walk, your cages ride down the left edge in the order they'll be
// thrown: the top one first when a beast squares up, then the next each time
// one falls. Drag a cage up or down to change the order. The walk order is its
// own thing (the party's lead stays put) and carries over to the next walk.

private const val WALK_CAGE_DP = 40
private const val WALK_CAGE_GAP_DP = 4
private const val WALK_ORDER_KEY = "WALK_ORDER"

/**
 * Your party in walk order: the saved order (by `boughtAt`, which stays the same as a netbeast
 * evolves or gains traits), then anyone it doesn't cover yet, the party lead first.
 */
internal fun GameActivity.walkOrder(): List<Netbeast> {
    val saved = prefs.getString(WALK_ORDER_KEY, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }
    val left = party.toMutableList()
    val out = mutableListOf<Netbeast>()
    for (id in saved) left.firstOrNull { it.boughtAt == id }?.let { out += it; left.remove(it) }
    party.getOrNull(activePetIndex)?.takeIf { it in left }?.let { out += it; left.remove(it) }
    return out + left
}

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
    cage.setOnTouchListener(dragToReorder())
    return cage
}

/** Drag a cage along the column: it swaps places with each neighbour it passes, and the order saves on release. */
@SuppressLint("ClickableViewAccessibility")
private fun GameActivity.dragToReorder() = object : View.OnTouchListener {
    var lastY = 0f
    var offset = 0f
    override fun onTouch(v: View, e: MotionEvent): Boolean {
        val col = v.parent as? LinearLayout ?: return false
        val slot = v.height + (WALK_CAGE_GAP_DP * resources.displayMetrics.density)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastY = e.rawY; offset = 0f; v.elevation = 8f; v.scaleX = 1.15f; v.scaleY = 1.15f; vibratePhone(15) }
            MotionEvent.ACTION_MOVE -> {
                offset += e.rawY - lastY; lastY = e.rawY
                val i = col.indexOfChild(v)
                if (offset > slot / 2 && i < col.childCount - 1) { col.removeView(v); col.addView(v, i + 1); offset -= slot; vibratePhone(10) }
                else if (offset < -slot / 2 && i > 0) { col.removeView(v); col.addView(v, i - 1); offset += slot; vibratePhone(10) }
                v.translationY = offset
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.translationY = 0f; v.elevation = 0f; v.scaleX = 1f; v.scaleY = 1f
                commitCageOrder(col)
            }
        }
        return true
    }
}

/** The column's order becomes the walk order (the party's own order and lead don't change). */
private fun GameActivity.commitCageOrder(col: LinearLayout) {
    val order = (0 until col.childCount).mapNotNull { col.getChildAt(it).tag as? Netbeast }
    prefs.edit().putString(WALK_ORDER_KEY, order.joinToString(",") { it.boughtAt.toString() }).apply()
    worldSession?.setCompanion(leadCompanion())
    refreshWalkCages() // renumber
}
