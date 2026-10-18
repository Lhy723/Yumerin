package com.lhy.yumerin.data.portable.st

/**
 * Minimal JSON reader for card import: returns the top-level fields of an
 * object and raw array elements, preserving original value text verbatim so
 * unknown fields round-trip without re-serialization loss. Not a general
 * JSON library — import code only needs field lookup and array iteration.
 */
internal object JsonFlattener {

    /**
     * Raw value text of a JSON string field with quotes stripped and escapes
     * resolved; non-string or absent fields return null.
     */
    fun stringValue(json: String?, key: String): String? {
        if (json == null) return null
        val raw = topLevelFields(json)[key] ?: return null
        return decodeStringLiteral(raw)
    }

    /** Raw value text of one top-level field in [json], or null. */
    fun rawField(json: String?, key: String): String? {
        if (json == null) return null
        return topLevelFields(json)[key]
    }

    internal fun decodeStringLiteral(raw: String): String? {
        if (raw.length < 2 || !raw.startsWith('"') || !raw.endsWith('"')) return null
        return runCatching { Reader(raw).readString() }.getOrNull()
    }
    /** Top-level fields of the first JSON object found in [json]; key -> raw value text. */
    fun topLevelFields(json: String): Map<String, String> = runCatching {
        val parser = Reader(json)
        parser.skipWhitespace()
        if (!parser.tryConsume('{')) return emptyMap()
        val result = mutableMapOf<String, String>()
        parser.skipWhitespace()
        if (parser.tryConsume('}')) return result
        while (true) {
            parser.skipWhitespace()
            val key = parser.readString()
            parser.skipWhitespace()
            parser.expect(':')
            parser.skipWhitespace()
            val start = parser.cursor
            parser.skipValue()
            result[key] = json.substring(start, parser.cursor)
            parser.skipWhitespace()
            if (parser.tryConsume('}')) break
            parser.expect(',')
        }
        result
    }.getOrDefault(emptyMap())

    /** Raw text of each element in the first JSON array found in [json]. */
    fun arrayElements(json: String): List<String> = runCatching {
        val parser = Reader(json)
        parser.skipWhitespace()
        if (!parser.tryConsume('[')) return emptyList()
        val elements = mutableListOf<String>()
        parser.skipWhitespace()
        if (parser.tryConsume(']')) return elements
        while (true) {
            parser.skipWhitespace()
            val start = parser.cursor
            parser.skipValue()
            elements += json.substring(start, parser.cursor)
            parser.skipWhitespace()
            if (parser.tryConsume(']')) break
            parser.expect(',')
        }
        elements
    }.getOrDefault(emptyList())

    private class Reader(val source: String) {
        var cursor = 0

        fun skipWhitespace() {
            while (cursor < source.length && source[cursor].isWhitespace()) cursor += 1
        }

        fun tryConsume(expected: Char): Boolean {
            skipWhitespace()
            if (cursor < source.length && source[cursor] == expected) {
                cursor += 1
                return true
            }
            return false
        }

        fun expect(expected: Char) {
            require(tryConsume(expected)) { "expected $expected" }
        }

        fun readString(): String {
            expect('"')
            val value = StringBuilder()
            while (cursor < source.length) {
                val char = source[cursor++]
                when {
                    char == '\\' -> {
                        require(cursor < source.length) { "incomplete escape" }
                        when (val escaped = source[cursor++]) {
                            '"', '\\', '/' -> value.append(escaped)
                            'b' -> value.append('\b')
                            'f' -> value.append('')
                            'n' -> value.append('\n')
                            'r' -> value.append('\r')
                            't' -> value.append('\t')
                            'u' -> {
                                require(cursor + 4 <= source.length) { "incomplete unicode escape" }
                                val code = source.substring(cursor, cursor + 4).toIntOrNull(16)
                                    ?: error("invalid unicode escape")
                                value.append(code.toChar())
                                cursor += 4
                            }
                            else -> error("invalid escape")
                        }
                    }
                    char == '"' -> return value.toString()
                    char.code < 0x20 -> error("invalid control character")
                    else -> value.append(char)
                }
            }
            error("unterminated string")
        }

        fun skipValue() {
            skipWhitespace()
            require(cursor < source.length) { "missing value" }
            when (source[cursor]) {
                '"' -> readString()
                '{' -> skipContainer('{', '}')
                '[' -> skipContainer('[', ']')
                else -> {
                    while (cursor < source.length && source[cursor] !in ",]} \t\r\n") cursor += 1
                }
            }
        }

        private fun skipContainer(open: Char, close: Char) {
            cursor += 1
            var depth = 1
            while (cursor < source.length && depth > 0) {
                val char = source[cursor]
                when {
                    char == '"' -> readString()
                    char == open -> {
                        depth += 1
                        cursor += 1
                    }
                    char == close -> {
                        depth -= 1
                        cursor += 1
                    }
                    else -> cursor += 1
                }
            }
            require(depth == 0) { "unterminated container" }
        }
    }
}
