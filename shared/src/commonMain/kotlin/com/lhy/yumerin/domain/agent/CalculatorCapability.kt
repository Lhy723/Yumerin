package com.lhy.yumerin.domain.agent

import com.lhy.yumerin.core.model.AgentCapabilityDescriptor
import com.lhy.yumerin.core.model.AgentRequest
import com.lhy.yumerin.core.model.MessageSenderType

/** A deterministic, side-effect-free example of a separately registered capability module. */
class CalculatorCapability : AgentCapability {
    override val descriptor = AgentCapabilityDescriptor(
        name = "calculator",
        description = "Evaluate a basic arithmetic expression with parentheses.",
        parametersJsonSchema = """{"type":"object","properties":{"expression":{"type":"string","description":"Arithmetic expression using numbers, parentheses, +, -, *, /, and %"}},"required":["expression"],"additionalProperties":false}"""
    )

    override fun isAvailableFor(request: AgentRequest): Boolean {
        val text = request.proactivePrompt ?: request.messages.lastOrNull { it.senderType == MessageSenderType.USER }?.text.orEmpty()
        val asksForCalculation = Regex("计算|算一下|帮我算|求值|百分之|calculate|compute|what is", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)
        val hasArithmetic = Regex("\\d\\s*[+*/%()]|[+*/%()]\\s*\\d").containsMatchIn(text)
        return hasArithmetic || (asksForCalculation && text.any(Char::isDigit))
    }

    override suspend fun execute(
        argumentsJson: String,
        context: AgentCapabilityContext
    ): AgentCapabilityResult {
        val expression = AgentJsonStringArgument.read(argumentsJson, "expression")
            ?: return AgentCapabilityResult(false, "Invalid arguments: expression must be a string.")
        if (expression.length > MAX_EXPRESSION_LENGTH) {
            return AgentCapabilityResult(false, "Invalid expression: it is too long.")
        }
        return runCatching { ArithmeticExpression(expression).evaluate() }
            .fold(
                onSuccess = { value ->
                    val result = value.toString().removeSuffix(".0")
                    AgentCapabilityResult(true, "Result: $result", "计算结果：$result")
                },
                onFailure = { error ->
                    AgentCapabilityResult(false, "Invalid arithmetic expression: ${error.message ?: "parse error"}.")
                }
            )
    }

    private companion object {
        const val MAX_EXPRESSION_LENGTH = 256
    }
}

/** Small JSON object reader; keeps commonMain independent of platform JSON libraries. */
internal object AgentJsonStringArgument {
    fun read(json: String, field: String): String? =
        readFields(json, listOf(field))[field]

    /** Reads multiple string fields from one flat JSON object; extra fields are skipped. */
    fun readFields(json: String, fields: List<String>): Map<String, String> = runCatching {
        Parser(json).readStringFields(fields)
    }.getOrDefault(emptyMap())

    private class Parser(private val source: String) {
        private var cursor = 0

        fun readStringFields(fields: List<String>): Map<String, String> {
            val wanted = fields.toSet()
            val result = mutableMapOf<String, String>()
            skipWhitespace()
            expect('{')
            skipWhitespace()
            if (!consume('}')) {
                while (true) {
                    skipWhitespace()
                    val key = readString()
                    skipWhitespace()
                    expect(':')
                    skipWhitespace()
                    if (key in wanted && key !in result && cursor < source.length && source[cursor] == '"') {
                        result[key] = readString()
                    } else {
                        skipValue()
                    }
                    skipWhitespace()
                    if (consume('}')) break
                    expect(',')
                }
            }
            skipWhitespace()
            require(cursor == source.length) { "trailing input" }
            return result
        }

        /** Skips one JSON value of any type (null, bool, number, string, array, object). */
        private fun skipValue() {
            skipWhitespace()
            require(cursor < source.length) { "missing value" }
            when (source[cursor]) {
                '"' -> readString()
                '{' -> {
                    consume('{')
                    skipWhitespace()
                    if (!consume('}')) {
                        while (true) {
                            skipWhitespace()
                            readString()
                            skipWhitespace()
                            expect(':')
                            skipValue()
                            skipWhitespace()
                            if (consume('}')) break
                            expect(',')
                        }
                    }
                }
                '[' -> {
                    consume('[')
                    skipWhitespace()
                    if (!consume(']')) {
                        while (true) {
                            skipValue()
                            skipWhitespace()
                            if (consume(']')) break
                            expect(',')
                        }
                    }
                }
                else -> {
                    val start = cursor
                    while (cursor < source.length && source[cursor] !in ",]} \\	\r\n") cursor += 1
                    require(cursor > start) { "invalid scalar" }
                }
            }
        }

        private fun readString(): String {
            expect('\"')
            val value = StringBuilder()
            while (cursor < source.length) {
                val char = source[cursor++]
                when {
                    char == '\\' -> {
                        require(cursor < source.length) { "incomplete escape" }
                        when (val escaped = source[cursor++]) {
                            '\"', '\\', '/' -> value.append(escaped)
                            'b' -> value.append('\b')
                            'f' -> value.append('\u000C')
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
                    char == '\"' -> return value.toString()
                    char.code < 0x20 -> error("invalid control character")
                    else -> value.append(char)
                }
            }
            error("unterminated string")
        }

        private fun consume(expected: Char): Boolean {
            if (cursor >= source.length || source[cursor] != expected) return false
            cursor += 1
            return true
        }

        private fun expect(expected: Char) {
            require(consume(expected)) { "invalid JSON" }
        }

        private fun skipWhitespace() {
            while (cursor < source.length && source[cursor].isWhitespace()) cursor += 1
        }
    }
}

private class ArithmeticExpression(private val source: String) {
    private var cursor = 0

    fun evaluate(): Double {
        val result = parseSum()
        skipWhitespace()
        require(cursor == source.length) { "unexpected input" }
        require(result.isFinite()) { "result is not finite" }
        return result
    }

    private fun parseSum(): Double {
        var value = parseProduct()
        while (true) {
            skipWhitespace()
            value = when {
                consume('+') -> value + parseProduct()
                consume('-') -> value - parseProduct()
                else -> return value
            }
        }
    }

    private fun parseProduct(): Double {
        var value = parseUnary()
        while (true) {
            skipWhitespace()
            value = when {
                consume('*') -> value * parseUnary()
                consume('/') -> {
                    val divisor = parseUnary()
                    require(divisor != 0.0) { "division by zero" }
                    value / divisor
                }
                consume('%') -> {
                    val divisor = parseUnary()
                    require(divisor != 0.0) { "division by zero" }
                    value % divisor
                }
                else -> return value
            }
        }
    }

    private fun parseUnary(): Double {
        skipWhitespace()
        return when {
            consume('+') -> parseUnary()
            consume('-') -> -parseUnary()
            else -> parsePrimary()
        }
    }

    private fun parsePrimary(): Double {
        skipWhitespace()
        if (consume('(')) {
            val value = parseSum()
            skipWhitespace()
            require(consume(')')) { "missing closing parenthesis" }
            return value
        }
        val start = cursor
        var decimalSeen = false
        while (cursor < source.length) {
            val char = source[cursor]
            when {
                char.isDigit() -> cursor += 1
                char == '.' && !decimalSeen -> {
                    decimalSeen = true
                    cursor += 1
                }
                else -> break
            }
        }
        require(cursor > start && source.substring(start, cursor).any(Char::isDigit)) { "expected a number" }
        return source.substring(start, cursor).toDoubleOrNull() ?: error("invalid number")
    }

    private fun consume(expected: Char): Boolean {
        if (cursor >= source.length || source[cursor] != expected) return false
        cursor += 1
        return true
    }

    private fun skipWhitespace() {
        while (cursor < source.length && source[cursor].isWhitespace()) cursor += 1
    }
}
