package com.rockhard.blocker.homevisits.save

/**
 * A small hand-written JSON reader and writer: objects, arrays, strings,
 * numbers, booleans and null. No library, so the core still compiles with the
 * bare Kotlin compiler. Objects keep their key order, so a save written twice
 * comes out byte for byte the same.
 */
sealed interface Json {
    data class Obj(val fields: Map<String, Json>) : Json {
        operator fun get(key: String): Json? = fields[key]
    }
    data class Arr(val items: List<Json>) : Json
    data class Str(val value: String) : Json
    data class Num(val value: Double) : Json
    data class Bool(val value: Boolean) : Json
    object Null : Json { override fun toString() = "Null" }

    companion object {
        fun parse(text: String): Json {
            val r = Reader(text)
            val v = r.value()
            r.skipSpace()
            if (!r.atEnd()) throw JsonError("trailing text at ${r.pos}")
            return v
        }

        fun write(v: Json): String = StringBuilder().also { writeTo(it, v) }.toString()

        private fun writeTo(sb: StringBuilder, v: Json) {
            when (v) {
                is Obj -> {
                    sb.append('{')
                    var first = true
                    for ((k, x) in v.fields) {
                        if (!first) sb.append(',')
                        first = false
                        writeString(sb, k); sb.append(':'); writeTo(sb, x)
                    }
                    sb.append('}')
                }
                is Arr -> {
                    sb.append('[')
                    v.items.forEachIndexed { i, x -> if (i > 0) sb.append(','); writeTo(sb, x) }
                    sb.append(']')
                }
                is Str -> writeString(sb, v.value)
                is Num -> sb.append(number(v.value))
                is Bool -> sb.append(v.value)
                Null -> sb.append("null")
            }
        }

        /** Whole numbers without ".0"; others as Kotlin prints them (shortest round-trip). */
        private fun number(d: Double): String {
            if (d.isNaN() || d.isInfinite()) return "null"
            if (d == kotlin.math.floor(d) && kotlin.math.abs(d) < 1e15) return d.toLong().toString()
            return d.toString()
        }

        private fun writeString(sb: StringBuilder, s: String) {
            sb.append('"')
            for (c in s) when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                else -> sb.append(c)
            }
            sb.append('"')
        }
    }
}

class JsonError(message: String) : Exception(message)

private class Reader(val s: String) {
    var pos = 0

    fun atEnd() = pos >= s.length

    fun skipSpace() { while (pos < s.length && s[pos].isWhitespace()) pos++ }

    fun value(): Json {
        skipSpace()
        if (atEnd()) throw JsonError("unexpected end")
        return when (val c = s[pos]) {
            '{' -> obj()
            '[' -> arr()
            '"' -> Json.Str(string())
            't' -> word("true", Json.Bool(true))
            'f' -> word("false", Json.Bool(false))
            'n' -> word("null", Json.Null)
            else -> if (c == '-' || c.isDigit()) num() else throw JsonError("unexpected '$c' at $pos")
        }
    }

    private fun word(w: String, v: Json): Json {
        if (!s.startsWith(w, pos)) throw JsonError("bad literal at $pos")
        pos += w.length
        return v
    }

    private fun expect(c: Char) {
        skipSpace()
        if (atEnd() || s[pos] != c) throw JsonError("expected '$c' at $pos")
        pos++
    }

    private fun obj(): Json.Obj {
        expect('{')
        val out = LinkedHashMap<String, Json>()
        skipSpace()
        if (!atEnd() && s[pos] == '}') { pos++; return Json.Obj(out) }
        while (true) {
            skipSpace()
            if (atEnd() || s[pos] != '"') throw JsonError("expected a key at $pos")
            val k = string()
            expect(':')
            out[k] = value()
            skipSpace()
            if (atEnd()) throw JsonError("unexpected end in object")
            when (s[pos++]) {
                ',' -> continue
                '}' -> return Json.Obj(out)
                else -> throw JsonError("expected ',' or '}' at ${pos - 1}")
            }
        }
    }

    private fun arr(): Json.Arr {
        expect('[')
        val out = ArrayList<Json>()
        skipSpace()
        if (!atEnd() && s[pos] == ']') { pos++; return Json.Arr(out) }
        while (true) {
            out.add(value())
            skipSpace()
            if (atEnd()) throw JsonError("unexpected end in array")
            when (s[pos++]) {
                ',' -> continue
                ']' -> return Json.Arr(out)
                else -> throw JsonError("expected ',' or ']' at ${pos - 1}")
            }
        }
    }

    private fun string(): String {
        pos++ // the opening quote
        val sb = StringBuilder()
        while (true) {
            if (atEnd()) throw JsonError("unterminated string")
            val c = s[pos++]
            when (c) {
                '"' -> return sb.toString()
                '\\' -> {
                    if (atEnd()) throw JsonError("unterminated escape")
                    when (val e = s[pos++]) {
                        '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                        'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                        'u' -> {
                            if (pos + 4 > s.length) throw JsonError("bad \\u escape")
                            sb.append(s.substring(pos, pos + 4).toInt(16).toChar())
                            pos += 4
                        }
                        else -> throw JsonError("bad escape '\\$e'")
                    }
                }
                else -> sb.append(c)
            }
        }
    }

    private fun num(): Json.Num {
        val start = pos
        if (s[pos] == '-') pos++
        while (pos < s.length && (s[pos].isDigit() || s[pos] in ".eE+-")) pos++
        return Json.Num(s.substring(start, pos).toDoubleOrNull() ?: throw JsonError("bad number at $start"))
    }
}

// ---- Small helpers for building and reading saves ----

fun obj(vararg fields: Pair<String, Json?>): Json.Obj =
    Json.Obj(LinkedHashMap<String, Json>().apply { for ((k, v) in fields) if (v != null) put(k, v) })

fun arr(items: List<Json>) = Json.Arr(items)
fun str(s: String?) = s?.let { Json.Str(it) }
/** Floats go through their own shortest form, so 0.1f saves as 0.1 and loads back as 0.1f. */
fun num(n: Number?) = n?.let { Json.Num(if (it is Float) it.toString().toDouble() else it.toDouble()) }
fun bool(b: Boolean) = Json.Bool(b)

fun Json?.str(): String = (this as? Json.Str)?.value ?: throw JsonError("expected a string, got $this")
fun Json?.strOrNull(): String? = (this as? Json.Str)?.value
fun Json?.int(): Int = (this as? Json.Num)?.value?.toInt() ?: throw JsonError("expected a number, got $this")
fun Json?.intOrNull(): Int? = (this as? Json.Num)?.value?.toInt()
fun Json?.float(): Float = (this as? Json.Num)?.value?.toFloat() ?: throw JsonError("expected a number, got $this")
fun Json?.bool(): Boolean = (this as? Json.Bool)?.value ?: throw JsonError("expected true or false, got $this")
fun Json?.list(): List<Json> = (this as? Json.Arr)?.items ?: emptyList()
fun Json?.obj(): Json.Obj = this as? Json.Obj ?: throw JsonError("expected an object, got $this")
