package com.rockhard.blocker.homevisits.house

import kotlin.math.abs

data class Cell(val c: Int, val r: Int)

/**
 * A floor plan drawn in ASCII, each character half a metre square:
 * - `#` wall, `.` floor, `=` the front door (outside is past it);
 * - `+` a doorway (floor that belongs to no room; a 1-wide gap in a wall is one too);
 * - `^` stairs (floor whose height ramps between the storeys);
 * - a capital letter starts a room: K kitchen, B bedroom, L lounge, N dining, S study, H hall, O other;
 * - a lower-case letter is where a story spot goes: b bench, t table, w the bedroom wall;
 *   `D` is the doormat inside the front door.
 *
 * x is the column and z the row, so yaw 0 faces down the page.
 */
class FakeHouse(
    val rows: List<String>,
    /** Rooms the camera struggles in (tracking drops out more). */
    val dark: Set<Char> = emptySet(),
    /** Floor height per cell; null means all on one storey. */
    private val heightAt: ((Cell) -> Float)? = null,
) {
    val w = rows.maxOf { it.length }
    val h = rows.size

    fun ch(c: Cell): Char = rows.getOrNull(c.r)?.getOrNull(c.c) ?: '#'
    fun inside(c: Cell) = c.r in 0 until h && c.c in 0 until w
    fun passable(c: Cell) = inside(c) && ch(c) != '#' && ch(c) != ' ' && ch(c) != '='

    fun isDoorway(c: Cell): Boolean {
        if (ch(c) == '+') return true
        if (!passable(c)) return false
        val wallLR = ch(Cell(c.c - 1, c.r)) == '#' && ch(Cell(c.c + 1, c.r)) == '#'
        val wallUD = ch(Cell(c.c, c.r - 1)) == '#' && ch(Cell(c.c, c.r + 1)) == '#'
        return wallLR || wallUD
    }

    /** Each floor cell's room letter, flooding from the capitals without crossing doorways or stairs. */
    val roomOf: Map<Cell, Char> by lazy {
        val out = LinkedHashMap<Cell, Char>()
        for (r in 0 until h) for (c in 0 until w) {
            val ch = ch(Cell(c, r))
            if (ch !in ROOM_LETTERS) continue
            val q = ArrayDeque(listOf(Cell(c, r)))
            while (q.isNotEmpty()) {
                val x = q.removeFirst()
                if (x in out || !passable(x) || isDoorway(x) || ch(x) == '^') continue
                out[x] = ch
                for (n in neighbours4(x)) q.addLast(n)
            }
        }
        out
    }

    fun room(c: Cell): Char? = roomOf[c]

    fun find(marker: Char): Cell? {
        for (r in 0 until h) for (c in 0 until w) if (ch(Cell(c, r)) == marker) return Cell(c, r)
        return null
    }

    fun has(marker: Char) = find(marker) != null

    fun height(c: Cell): Float = heightAt?.invoke(c) ?: 0f

    /** The floor point in the middle of a cell. */
    fun world(c: Cell): Vec3 = Vec3(c.c * CELL + CELL / 2, height(c), c.r * CELL + CELL / 2)

    fun cellAt(p: Vec3) = Cell((p.x / CELL).toInt(), (p.z / CELL).toInt())

    fun neighbours4(c: Cell) = listOf(Cell(c.c + 1, c.r), Cell(c.c - 1, c.r), Cell(c.c, c.r + 1), Cell(c.c, c.r - 1))

    /** A wall or door cell next to `c` (what the player faces there), or null. */
    fun wallBeside(c: Cell, door: Boolean = false): Cell? =
        neighbours4(c).firstOrNull { if (door) ch(it) == '=' else ch(it) == '#' }

    /**
     * The shortest walk between two cells (8 ways, no cutting corners), as the
     * cells walked through. Null when there's no way.
     */
    fun path(from: Cell, to: Cell, avoid: Set<Cell> = emptySet()): List<Cell>? {
        if (!passable(from) || !passable(to)) return null
        val prev = HashMap<Cell, Cell>()
        val seen = HashSet<Cell>().apply { add(from) }
        val q = ArrayDeque(listOf(from))
        while (q.isNotEmpty()) {
            val x = q.removeFirst()
            if (x == to) break
            for (dr in -1..1) for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val n = Cell(x.c + dc, x.r + dr)
                if (!passable(n) || n in seen || n in avoid) continue
                if (dr != 0 && dc != 0 && (!passable(Cell(x.c + dc, x.r)) || !passable(Cell(x.c, x.r + dr)))) continue
                seen.add(n); prev[n] = x; q.addLast(n)
            }
        }
        if (to !in seen) return null
        val out = ArrayList<Cell>()
        var at = to
        while (at != from) { out.add(at); at = prev[at]!! }
        out.add(from)
        return out.reversed()
    }

    /** All floor cells in a room. */
    fun cellsIn(room: Char): List<Cell> = roomOf.filterValues { it == room }.keys.toList()

    /** Floor cells in a room that stand against a wall (somewhere to hang a painting). */
    fun wallCells(room: Char): List<Cell> = cellsIn(room).filter { wallBeside(it) != null }

    /** Whether the straight line between two floor points stays out of walls (sampled). */
    fun clearLine(a: Vec3, b: Vec3): Boolean {
        val n = (a.flat(b) / (CELL / 4)).toInt() + 1
        for (i in 0..n) {
            val t = i.toFloat() / n
            if (!passable(cellAt(Vec3(a.x + (b.x - a.x) * t, 0f, a.z + (b.z - a.z) * t)))) return false
        }
        return true
    }

    override fun toString() = rows.joinToString("\n")

    companion object {
        const val CELL = 0.5f
        const val ROOM_LETTERS = "KBLNSHO"

        fun roomKind(letter: Char?): RoomKind? = when (letter) {
            'K' -> RoomKind.KITCHEN; 'B' -> RoomKind.BEDROOM; 'L' -> RoomKind.LOUNGE; 'N' -> RoomKind.DINING
            'S' -> RoomKind.STUDY; 'H' -> RoomKind.HALL; 'O' -> RoomKind.OTHER; else -> null
        }

        fun letter(kind: RoomKind): Char? = when (kind) {
            RoomKind.KITCHEN -> 'K'; RoomKind.BEDROOM -> 'B'; RoomKind.LOUNGE -> 'L'; RoomKind.DINING -> 'N'
            RoomKind.STUDY -> 'S'; RoomKind.HALL -> 'H'; RoomKind.OTHER -> 'O'; else -> null
        }

        fun near(a: Float, b: Float, eps: Float) = abs(a - b) <= eps

        /**
         * 03's example: the kitchen and the bedroom back to back, with only the
         * hall between them. The bench and the bedroom wall are either side of
         * the same wall, a metre apart.
         */
        val BACK_TO_BACK = FakeHouse(
            listOf(
                "#############",
                "#K....#B....#",
                "#....b#w....#",
                "#.....#.....#",
                "###.#####.###",
                "#H..........#",
                "#.......D...#",
                "########=####",
            )
        )
    }
}
