package com.rockhard.blocker.homevisits.house

import kotlin.random.Random

/**
 * Random floor plans for the fake-house tests: 4–9 rooms off a hallway, with
 * open-plan kitchens and dining rooms, dead-end rooms reached through another
 * room, rooms back to back with no door, sometimes a second storey up a flight
 * of stairs, and sometimes a single-room studio.
 */
object PlanGen {
    const val STOREY = 2.8f
    private const val HALL = 3

    fun make(seed: Long): FakeHouse {
        val rnd = Random(seed)
        return if (rnd.nextFloat() < 0.1f) studio(rnd) else house(rnd)
    }

    private class Grid(val w: Int, val h: Int) {
        val g = Array(h) { CharArray(w) { '#' } }
        operator fun set(c: Int, r: Int, ch: Char) { if (r in 0 until h && c in 0 until w) g[r][c] = ch }
        operator fun get(c: Int, r: Int) = if (r in 0 until h && c in 0 until w) g[r][c] else '#'
        fun fill(c0: Int, r0: Int, c1: Int, r1: Int, ch: Char) { for (r in r0..r1) for (c in c0..c1) this[c, r] = ch }
        fun rows() = g.map { String(it) }
    }

    private data class Box(val c0: Int, val r0: Int, val c1: Int, val r1: Int, var letter: Char = 'O') {
        val cells get() = (r0..r1).flatMap { r -> (c0..c1).map { c -> Cell(c, r) } }
    }

    private fun studio(rnd: Random): FakeHouse {
        val w = rnd.nextInt(12, 18); val h = rnd.nextInt(10, 14)
        val g = Grid(w + 2, h + 2)
        g.fill(1, 1, w, h, '.')
        g[1, 1] = 'L'
        val doorRow = rnd.nextInt(3, h - 1)
        g[0, doorRow] = '='; g[1, doorRow] = 'D'
        if (rnd.nextFloat() < 0.8f) g[rnd.nextInt(3, w - 1), 1] = 'b' // a bench along the top wall
        if (rnd.nextFloat() < 0.6f) g[w / 2, h / 2 + 1] = 't'
        return FakeHouse(g.rows(), dark = if (rnd.nextFloat() < 0.2f) setOf('L') else emptySet())
    }

    private fun house(rnd: Random): FakeHouse {
        val rooms = rnd.nextInt(3, 9) // plus the hall: 4–9 rooms
        val top = (rooms + 1) / 2
        val bottom = rooms - top
        val widths = List(top) { rnd.nextInt(6, 11) }
        val total = widths.sum() + top - 1
        val dTop = rnd.nextInt(6, 10)
        val dBot = rnd.nextInt(6, 10)
        val upstairs = rnd.nextFloat() < 0.15f
        val stairLen = 8
        val upW = 12
        val mainW = total + 2
        val w = mainW + if (upstairs) stairLen + upW + 1 else 0
        val hallR0 = dTop + 2
        val hallR1 = hallR0 + HALL - 1
        val h = hallR1 + 2 + if (bottom > 0) dBot + 1 else 0
        val g = Grid(w, h)

        // The hall runs the whole width.
        g.fill(1, hallR0, mainW - 2, hallR1, '.'); g[1, hallR0] = 'H'
        val boxes = ArrayList<Box>()
        var c = 1
        for (wd in widths) { boxes.add(Box(c, 1, c + wd - 1, dTop)); c += wd + 1 }
        if (bottom > 0) {
            val bw = splitWidth(rnd, total, bottom)
            c = 1
            for (wd in bw) { boxes.add(Box(c, hallR1 + 2, c + wd - 1, hallR1 + 1 + dBot)); c += wd + 1 }
        }
        val letters = ArrayDeque(roomLetters(rnd, boxes.size))
        for (b in boxes) {
            b.letter = letters.removeFirst()
            g.fill(b.c0, b.r0, b.c1, b.r1, '.')
            g[b.c0, b.r0] = b.letter
        }

        // Doors into the hall; some rooms are dead ends reached through a neighbour instead.
        val hasHallDoor = BooleanArray(boxes.size)
        for ((i, b) in boxes.withIndex()) {
            val deadEnd = i > 0 && rnd.nextFloat() < 0.25f && hasHallDoor[i - 1] && sameRow(boxes[i - 1], b)
            if (deadEnd) {
                val wallC = b.c0 - 1
                val r = rnd.nextInt(b.r0 + 1, b.r1)
                g[wallC, r] = '+'; g[wallC, r + 1] = '+'
                continue
            }
            hasHallDoor[i] = true
            val wallR = if (b.r1 < hallR0) b.r1 + 1 else b.r0 - 1
            val dc = rnd.nextInt(b.c0, b.c1)
            g[dc, wallR] = '+'; g[dc + 1, wallR] = '+'
        }
        // Open plan: a kitchen next to a dining room gets a wide opening between them.
        for (i in 1 until boxes.size) {
            val a = boxes[i - 1]; val b = boxes[i]
            if (!sameRow(a, b)) continue
            if (setOf(a.letter, b.letter) == setOf('K', 'N')) for (r in a.r0 + 1..minOf(a.r1, a.r0 + 4)) g[b.c0 - 1, r] = '+'
        }

        // The front door, at the hall's left end.
        val doorRow = hallR0 + 1
        g[0, doorRow] = '='; g[1, doorRow] = 'D'

        // Story spots: a bench in the kitchen (usually), a table in the dining room or lounge, a bedroom wall.
        boxes.firstOrNull { it.letter == 'K' }?.let { k -> if (rnd.nextFloat() < 0.8f) mark(g, rnd, k, 'b') }
        boxes.firstOrNull { it.letter == 'N' || it.letter == 'L' }?.let { t ->
            if (rnd.nextFloat() < 0.7f) g[(t.c0 + t.c1) / 2, (t.r0 + t.r1) / 2] = 't'
        }

        var stairC0 = Int.MAX_VALUE
        if (upstairs) {
            // A flight of stairs off the hall's right end, up to a landing with two rooms.
            stairC0 = mainW - 1
            g.fill(stairC0, hallR0, stairC0 + stairLen - 1, hallR1, '^')
            val landC0 = stairC0 + stairLen
            g.fill(landC0, hallR0, landC0 + upW - 1, hallR1, '.'); g[landC0, hallR0] = 'O'
            val upTop = Box(landC0, 1, landC0 + upW - 1, dTop, 'B')
            g.fill(upTop.c0, upTop.r0, upTop.c1, upTop.r1, '.'); g[upTop.c0, upTop.r0] = 'B'
            g[landC0 + 3, dTop + 1] = '+'; g[landC0 + 4, dTop + 1] = '+'
            if (bottom > 0) {
                val upBot = Box(landC0, hallR1 + 2, landC0 + upW - 1, hallR1 + 1 + dBot, 'S')
                g.fill(upBot.c0, upBot.r0, upBot.c1, upBot.r1, '.'); g[upBot.c0, upBot.r0] = 'S'
                g[landC0 + 5, hallR1 + 1] = '+'; g[landC0 + 6, hallR1 + 1] = '+'
            }
        }
        boxes.firstOrNull { it.letter == 'B' }?.let { mark(g, rnd, it, 'w') }

        val dark = boxes.filter { rnd.nextFloat() < 0.2f }.map { it.letter }.toSet()
        val s0 = stairC0
        val heights: ((Cell) -> Float)? = if (!upstairs) null else { cell ->
            when {
                cell.c < s0 -> 0f
                cell.c >= s0 + stairLen -> STOREY
                else -> STOREY * (cell.c - s0 + 1) / (stairLen + 1)
            }
        }
        return FakeHouse(g.rows(), dark, heights)
    }

    private fun sameRow(a: Box, b: Box) = a.r0 == b.r0

    /** A floor cell against the room's top or bottom wall, away from the corners. */
    private fun mark(g: Grid, rnd: Random, b: Box, ch: Char) {
        repeat(20) {
            val c = rnd.nextInt(b.c0 + 1, b.c1)
            val r = if (rnd.nextBoolean()) b.r0 else b.r1
            val outside = if (r == b.r0) r - 1 else r + 1
            if (g[c, r] == '.' && g[c, outside] == '#') { g[c, r] = ch; return }
        }
    }

    private fun splitWidth(rnd: Random, total: Int, n: Int): List<Int> {
        val inner = total - (n - 1)
        val cuts = (1 until n).map { inner * it / n + rnd.nextInt(-1, 2) }
        val edges = listOf(0) + cuts + inner
        return (1..n).map { (edges[it] - edges[it - 1]).coerceAtLeast(3) }.let { ws ->
            // Make them add back up exactly after the coercion.
            val diff = inner - ws.sum()
            ws.mapIndexed { i, x -> if (i == ws.lastIndex) x + diff else x }
        }
    }

    /** A kitchen most of the time, a bedroom most of the time, then whatever else. */
    private fun roomLetters(rnd: Random, n: Int): List<Char> {
        val out = ArrayList<Char>()
        if (rnd.nextFloat() < 0.9f) out.add('K')
        if (rnd.nextFloat() < 0.85f) out.add('B')
        val rest = listOf('L', 'N', 'S', 'B', 'O', 'L', 'N', 'O', 'S')
        var i = 0
        while (out.size < n) out.add(rest[i++ % rest.size])
        return out.take(n).shuffled(rnd)
    }
}
