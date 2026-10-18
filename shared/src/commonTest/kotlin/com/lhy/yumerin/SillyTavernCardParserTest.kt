package com.lhy.yumerin

import com.lhy.yumerin.core.model.MessagePart
import com.lhy.yumerin.core.model.WorldInfoPosition
import com.lhy.yumerin.core.model.WorldInfoSelectiveLogic
import com.lhy.yumerin.core.model.encodeMessageParts
import com.lhy.yumerin.core.model.decodeMessageParts
import com.lhy.yumerin.data.portable.st.Base64Common
import com.lhy.yumerin.data.portable.st.LorebookParser
import com.lhy.yumerin.data.portable.st.SillyTavernCardParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SillyTavernCardParserTest {
    @Test
    fun pngCardWithCcv3ChunkParsesIntoPersona() {
        val cardJson = """
            {"spec":"chara_card_v3","spec_version":"3.0","name":"雨夜小雨",
             "data":{"name":"雨夜小雨","description":"温柔的城市伴侣","personality":"温柔、敏锐",
             "scenario":"雨夜的城市","first_mes":"*微笑* 你回来了。","mes_example":"<START>\n{{char}}: 晚安。",
             "creator_notes":"测试卡","system_prompt":"Creator rule. {{original}}","post_history_instructions":"保持简短。",
             "character_book":{"name":"城市设定","entries":[{"uid":0,"key":["雨夜","城市"],"keysecondary":[],
             "content":"小雨熟悉每一条雨夜的街道。","constant":false,"enabled":true,"position":1,"order":5,
             "useProbability":100,"prevent_recursion":false}]},
             "extensions":{"depth_prompt":{"depth":2}},"custom_extension":{"secret":"value"}}}
        """.replace("\n", "").replace("  ", "")
        val png = pngWithTextChunks(mapOf("ccv3" to Base64Common.encode(cardJson.toByteArray())))

        val result = SillyTavernCardParser.parse(png)

        val parsed = result as SillyTavernCardParser.ParseResult.Parsed
        assertEquals("chara_card_v3", parsed.spec)
        assertEquals("雨夜小雨", parsed.persona.name)
        assertEquals("Creator rule. {{original}}", parsed.persona.systemPrompt)
        assertEquals("保持简短。", parsed.persona.postHistoryInstructions)
        assertTrue(parsed.hadEmbeddedBook)
        val book = parsed.persona.characterBook
        assertEquals(1, book?.entries?.size)
        val entry = book?.entries?.single()
        assertEquals(listOf("雨夜", "城市"), entry?.key)
        assertEquals(WorldInfoPosition.AFTER_PERSONA, entry?.position)
        // Unknown extension data is reported, never dropped silently.
        assertTrue(parsed.unmappedFields.any { it.contains("custom_extension") })
        assertTrue(parsed.persona.rawExtras.isNotEmpty())
    }

    @Test
    fun charaChunkIsUsedWhenCcv3IsAbsent() {
        val cardJson = "{\"name\":\"旧版卡\",\"description\":\"v1 flat card\"}"
        val png = pngWithTextChunks(mapOf("chara" to Base64Common.encode(cardJson.toByteArray())))

        val parsed = SillyTavernCardParser.parse(png) as SillyTavernCardParser.ParseResult.Parsed
        assertEquals("旧版卡", parsed.persona.name)
        assertEquals("v1", parsed.spec)
    }

    @Test
    fun nonPngOrMissingCardDataFailsWithReason() {
        val failed = SillyTavernCardParser.parse(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9))
        assertTrue(failed is SillyTavernCardParser.ParseResult.Failed)
        val empty = SillyTavernCardParser.parse(pngWithTextChunks(emptyMap()))
        assertTrue(empty is SillyTavernCardParser.ParseResult.Failed)
    }

    @Test
    fun lorebookSelectiveLogicAndProbabilityMap() {
        val book = LorebookParser.parse(
            """{"entries":[{"uid":0,"key":["旅行"],"keysecondary":["东京","春天"],"selectiveLogic":3,
            "content":"东京的春天","position":0,"depth":2,"order":3,"useProbability":50,
            "prevent_recursion":true,"excludeRecursion":[7,9]}]}"""
        )
        val entry = book?.entries?.single()
        assertEquals(WorldInfoSelectiveLogic.AND_ALL, entry?.selectiveLogic)
        assertEquals(WorldInfoPosition.BEFORE_PERSONA, entry?.position)
        assertEquals(50, entry?.useProbability)
        assertEquals(true, entry?.preventRecursion)
        assertEquals(listOf(7, 9), entry?.excludeRecursionIds)
    }

    @Test
    fun base64CodecHandlesPaddingAndWhitespace() {
        val original = "卡片数据 with ascii and 中文"
        val encoded = Base64Common.encode(original.toByteArray())
        assertEquals(original, String(Base64Common.decode(encoded)))
    }

    @Test
    fun messagePartCodecStaysLossless() {
        val parts = listOf(
            MessagePart.Text("正文"),
            MessagePart.Image("file:///tmp/a.png")
        )
        val decoded = decodeMessageParts(encodeMessageParts(parts))
        assertEquals(2, decoded.size)
    }

    /** Wraps tEXt chunks around a minimal valid PNG signature + IHDR + IEND. */
    private fun pngWithTextChunks(chunks: Map<String, String>): ByteArray {
        val out = mutableListOf<Byte>()
        out += byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A).toList()
        fun chunk(type: String, data: ByteArray) {
            val length = data.size
            out += byteArrayOf(
                (length ushr 24).toByte(), (length ushr 16).toByte(), (length ushr 8).toByte(), length.toByte()
            ).toList()
            out += type.encodeToByteArray().toList()
            out += data.toList()
            out += byteArrayOf(0, 0, 0, 0).toList() // CRC placeholder; parser ignores it
        }
        chunk("IHDR", byteArrayOf(0, 0, 0, 1, 0, 0, 0, 1, 8, 0, 0, 0, 0))
        chunks.forEach { (keyword, text) ->
            val payload = keyword.encodeToByteArray() + byteArrayOf(0) + text.encodeToByteArray()
            chunk("tEXt", payload)
        }
        chunk("IEND", byteArrayOf())
        return out.toByteArray()
    }
}
