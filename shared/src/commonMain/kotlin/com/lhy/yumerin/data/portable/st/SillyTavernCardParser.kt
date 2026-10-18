package com.lhy.yumerin.data.portable.st

import com.lhy.yumerin.core.model.PersonaPackage
import com.lhy.yumerin.core.model.WorldInfoBook
import com.lhy.yumerin.core.model.WorldInfoEntry
import com.lhy.yumerin.core.model.WorldInfoPosition
import com.lhy.yumerin.core.model.WorldInfoSelectiveLogic

/**
 * Clean-room reader for community character cards (SillyTavern-compatible
 * PNG exports) based on the public card-spec conventions: the card JSON is
 * embedded base64 in the PNG `tEXt` chunk keyed `chara` (v2) or `ccv3` (v3).
 * Compressed `zTXt` chunks are not decoded and surface as unmapped data in
 * the compatibility report.
 *
 * Card content is untrusted input: parsing never executes embedded data, and
 * unknown fields are preserved verbatim in [PersonaPackage.rawExtras].
 */
object SillyTavernCardParser {
    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private const val MAX_CARD_JSON_CHARS = 2_000_000

    sealed interface ParseResult {
        /** Parsed persona plus the fields this build did not map. */
        data class Parsed(
            val persona: PersonaPackage,
            val spec: String,
            val unmappedFields: List<String>,
            val hadEmbeddedBook: Boolean
        ) : ParseResult

        data class Failed(val reason: String) : ParseResult
    }

    fun parse(pngBytes: ByteArray): ParseResult {
        if (pngBytes.size < 8 || !pngBytes.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)) {
            return ParseResult.Failed("不是 PNG 文件。")
        }
        val chunks = extractTextChunks(pngBytes)
        val ccv3 = chunks["ccv3"]
        val chara = chunks["chara"]
        val embedded = when {
            ccv3 != null -> ccv3
            chara != null -> chara
            else -> return ParseResult.Failed("PNG 中没有找到角色卡数据（chara/ccv3）。")
        }
        val json = runCatching { decodeBase64(embedded).decodeToString() }.getOrNull()
            ?: return ParseResult.Failed("角色卡数据不是有效的 base64 编码。")
        if (json.length > MAX_CARD_JSON_CHARS) return ParseResult.Failed("角色卡数据过大。")
        return parseCardJson(json, preferV3 = ccv3 != null)
    }

    /** Parses an already-decoded card JSON (drag-drop text or file import). */
    fun parseCardJson(json: String, preferV3: Boolean): ParseResult {
        val fields = JsonFlattener.topLevelFields(json)
        if (fields.isEmpty()) return ParseResult.Failed("角色卡 JSON 无法解析。")
        val data = fields["data"]
        // v2/v3 nest under `data`; legacy v1 cards are flat. String fields read
        // through stringValue; book/extensions stay raw for lossless handling.
        val cardJson = data ?: json
        fun text(vararg keys: String): String? =
            keys.firstNotNullOfOrNull { key -> JsonFlattener.stringValue(cardJson, key) }
        val spec = JsonFlattener.stringValue(json, "spec")?.takeIf { it.isNotBlank() }
            ?: if (preferV3) "ccv3" else "v1"

        fun pick(vararg keys: String): String? = text(*keys)

        val bookJson = fieldsOrRaw(cardJson, "character_book")
        var hadBook = false
        var book: WorldInfoBook? = null
        val unmapped = mutableListOf<String>()
        if (bookJson != null) {
            hadBook = true
            book = LorebookParser.parse(bookJson)
            unmapped += LorebookParser.lastUnmappedFields
        }

        val knownKeys = setOf(
            "name", "description", "personality", "scenario", "first_mes", "mes_example",
            "creator_notes", "system_prompt", "post_history_instructions", "character_book",
            "creator", "character_version", "tags", "extensions"
        )
        unmapped += JsonFlattener.topLevelFields(cardJson).keys.filter { it !in knownKeys }.map { "data.$it" }
        unmapped += fields.keys
            .filter { it != "data" && it !in setOf("spec", "spec_version", "name", "description", "personality", "scenario", "first_mes", "mes_example", "chat") }
            .map { it }

        val persona = PersonaPackage(
            name = pick("name").orEmpty(),
            description = pick("description").orEmpty(),
            personality = pick("personality").orEmpty(),
            scenario = pick("scenario").orEmpty(),
            firstMes = pick("first_mes").orEmpty(),
            mesExample = pick("mes_example").orEmpty(),
            creatorNotes = pick("creator_notes").orEmpty(),
            systemPrompt = pick("system_prompt"),
            postHistoryInstructions = pick("post_history_instructions"),
            characterBook = book,
            personaVersion = pick("character_version") ?: "1",
            rawExtras = listOfNotNull(fieldsOrRaw(cardJson, "extensions"), fieldsOrRaw(json, "extensions"))
                .filter { !it.isNullOrBlank() }
                .joinToString("\n")
        )
        return ParseResult.Parsed(
            persona = persona,
            spec = spec,
            unmappedFields = unmapped.distinct(),
            hadEmbeddedBook = hadBook
        )
    }

    /** Raw (undecoded) value text of a top-level field; absent returns null. */
    private fun fieldsOrRaw(json: String, key: String): String? =
        JsonFlattener.topLevelFields(json)[key]

    /** Extracts latin-1 tEXt chunks as keyword -> text. */
    private fun extractTextChunks(png: ByteArray): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var cursor = 8
        while (cursor + 8 <= png.size) {
            val length = readInt32(png, cursor)
            val type = png.copyOfRange(cursor + 4, cursor + 8).decodeToString()
            val dataStart = cursor + 8
            if (length < 0 || dataStart + length + 4 > png.size) break
            if (type == "tEXt") {
                val data = png.copyOfRange(dataStart, dataStart + length)
                val separator = data.indexOf(0.toByte())
                if (separator > 0) {
                    val keyword = data.copyOfRange(0, separator).decodeToString().lowercase()
                    val text = data.copyOfRange(separator + 1, data.size).decodeToString()
                    result[keyword] = text
                }
            }
            cursor = dataStart + length + 4 // skip CRC
        }
        return result
    }

    private fun readInt32(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)

    private fun decodeBase64(value: String): ByteArray {
        val normalized = value.filter { it !in "\r\n \t" }
        return Base64Common.decode(normalized)
    }
}

/** Minimal common-code base64 codec (stdlib-independent alphabet handling). */
internal object Base64Common {
    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun decode(value: String): ByteArray {
        val cleaned = value.trimEnd('=')
        val output = ByteArray(cleaned.length * 3 / 4)
        var outIndex = 0
        var buffer = 0
        var bits = 0
        cleaned.forEach { char ->
            val index = alphabet.indexOf(char)
            require(index >= 0) { "invalid base64 character" }
            buffer = (buffer shl 6) or index
            bits += 6
            if (bits >= 8) {
                bits -= 8
                output[outIndex++] = ((buffer shr bits) and 0xFF).toByte()
            }
        }
        return output.copyOf(outIndex)
    }

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size + 2) / 3 * 4)
        var index = 0
        while (index + 2 < bytes.size) {
            val triple = ((bytes[index].toInt() and 0xFF) shl 16) or
                ((bytes[index + 1].toInt() and 0xFF) shl 8) or
                (bytes[index + 2].toInt() and 0xFF)
            out.append(alphabet[(triple shr 18) and 0x3F])
                .append(alphabet[(triple shr 12) and 0x3F])
                .append(alphabet[(triple shr 6) and 0x3F])
                .append(alphabet[triple and 0x3F])
            index += 3
        }
        val remaining = bytes.size - index
        if (remaining == 1) {
            val pair = (bytes[index].toInt() and 0xFF) shl 8
            out.append(alphabet[(pair shr 10) and 0x3F])
                .append(alphabet[(pair shr 4) and 0x3F])
                .append("==")
        } else if (remaining == 2) {
            val triple = ((bytes[index].toInt() and 0xFF) shl 8) or (bytes[index + 1].toInt() and 0xFF)
            out.append(alphabet[(triple shr 10) and 0x3F])
                .append(alphabet[(triple shr 4) and 0x3F])
                .append(alphabet[(triple and 0xF) shl 2])
                .append("=")
        }
        return out.toString()
    }
}

/**
 * Parses community lorebook JSON (standalone or card-embedded character_book)
 * into the Yumerin world-info model. Unmapped entry fields are collected in
 * [lastUnmappedFields] for the import compatibility report.
 */
object LorebookParser {
    internal var lastUnmappedFields: List<String> = emptyList()

    fun parse(json: String): WorldInfoBook? = runCatching {
        lastUnmappedFields = emptyList()
        val root = JsonFlattener.topLevelFields(json)
        val entriesJson = root["entries"]
            ?: return@runCatching null
        val entries = JsonFlattener.arrayElements(entriesJson).mapNotNull { element ->
            parseEntry(element)
        }
        lastUnmappedFields += root.keys.filter { it != "entries" }.map { "book.$it" }
        WorldInfoBook(name = root["name"].orEmpty(), entries = entries)
    }.getOrNull()

    private fun parseEntry(entryJson: String): WorldInfoEntry? {
        val f = JsonFlattener.topLevelFields(entryJson)
        val uid = f["uid"]?.toIntOrNull() ?: f["id"]?.toIntOrNull() ?: return null
        val content = f["content"] ?: return null
        val known = setOf(
            "uid", "id", "key", "keysecondary", "selectiveLogic", "content", "comment",
            "constant", "enabled", "position", "depth", "order", "useProbability",
            "probability", "group", "groupWeight", "prevent_recursion", "excludeRecursion",
            "extensions"
        )
        lastUnmappedFields += f.keys.filter { it !in known }.map { "entry.$uid.$it" }
        return WorldInfoEntry(
            uid = uid,
            key = splitKeys(f["key"]),
            keysecondary = splitKeys(f["keysecondary"]),
            selectiveLogic = parseLogic(f["selectiveLogic"]),
            content = content,
            comment = f["comment"].orEmpty(),
            constant = f["constant"] == "true",
            enabled = f["enabled"] != "false",
            position = parsePosition(f["position"]),
            depth = f["depth"]?.toIntOrNull() ?: 4,
            order = f["order"]?.toIntOrNull() ?: 0,
            useProbability = f["useProbability"]?.toIntOrNull() ?: f["probability"]?.toIntOrNull(),
            group = f["group"]?.takeIf(String::isNotBlank),
            groupWeight = f["groupWeight"]?.toIntOrNull() ?: 100,
            preventRecursion = f["prevent_recursion"] == "true",
            excludeRecursionIds = f["excludeRecursion"]?.removeSurrounding("[", "]")
                ?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
        )
    }

    private fun splitKeys(value: String?): List<String> = value
        ?.removeSurrounding("[", "]")
        ?.split(',')
        ?.map { it.trim().removeSurrounding("\"") }
        ?.filter(String::isNotBlank)
        .orEmpty()

    private fun parseLogic(value: String?): WorldInfoSelectiveLogic = when (value?.toIntOrNull()) {
        1 -> WorldInfoSelectiveLogic.NOT_ALL
        2 -> WorldInfoSelectiveLogic.NOT_ANY
        3 -> WorldInfoSelectiveLogic.AND_ALL
        else -> WorldInfoSelectiveLogic.AND_ANY
    }

    private fun parsePosition(value: String?): WorldInfoPosition = when (value?.toIntOrNull()) {
        0 -> WorldInfoPosition.BEFORE_PERSONA
        1 -> WorldInfoPosition.AFTER_PERSONA
        else -> WorldInfoPosition.AT_DEPTH
    }
}
