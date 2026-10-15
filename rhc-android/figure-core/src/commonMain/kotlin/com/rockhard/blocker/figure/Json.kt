package com.rockhard.blocker.figure

/**
 * Just enough JSON for a glTF header. Objects become Map<String, Any?>, arrays
 * List<Any?>, numbers Double, plus String, Boolean and null.
 */
internal object Json {
    fun parse(text: String): Any? {
        val p = Parser(text)
        val v = p.value()
        p.skipSpace()
        if (p.i != text.length) p.fail("trailing text")
        return v
    }

    private class Parser(val s: String) {
        var i = 0

        fun fail(why: String): Nothing = throw IllegalArgumentException("JSON: $why at $i")

        fun skipSpace() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++
        }

        fun value(): Any? {
            skipSpace()
            if (i >= s.length) fail("unexpected end")
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> if (c == '-' || c in '0'..'9') num() else fail("unexpected '$c'")
            }
        }

        private fun word(w: String, v: Any?): Any? {
            if (!s.startsWith(w, i)) fail("expected $w")
            i += w.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++
            skipSpace()
            if (s[i] == '}') { i++; return m }
            while (true) {
                skipSpace()
                if (s[i] != '"') fail("expected a key")
                val k = str()
                skipSpace()
                if (s[i] != ':') fail("expected ':'")
                i++
                m[k] = value()
                skipSpace()
                when (s[i]) {
                    ',' -> i++
                    '}' -> { i++; return m }
                    else -> fail("expected ',' or '}'")
                }
            }
        }

        private fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++
            skipSpace()
            if (s[i] == ']') { i++; return l }
            while (true) {
                l.add(value())
                skipSpace()
                when (s[i]) {
                    ',' -> i++
                    ']' -> { i++; return l }
                    else -> fail("expected ',' or ']'")
                }
            }
        }

        private fun str(): String {
            val sb = StringBuilder()
            i++
            while (true) {
                if (i >= s.length) fail("unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        when (val e = s[i++]) {
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'r' -> sb.append('\r')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun num(): Double {
            val start = i
            while (i < s.length && (s[i] in "+-.eE" || s[i] in '0'..'9')) i++
            return s.substring(start, i).toDoubleOrNull() ?: fail("bad number")
        }
    }
}

@Suppress("UNCHECKED_CAST")
internal fun Any?.obj(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()

internal fun Any?.list(): List<Any?> = this as? List<Any?> ?: emptyList()

internal fun Any?.int(default: Int = -1): Int = (this as? Double)?.toInt() ?: default

internal fun Any?.floats(): FloatArray? = (this as? List<*>)?.let { l -> FloatArray(l.size) { (l[it] as Double).toFloat() } }
