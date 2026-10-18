package com.lhy.yumerin

import com.lhy.yumerin.core.model.WorldInfoBook
import com.lhy.yumerin.core.model.WorldInfoEntry
import com.lhy.yumerin.core.model.WorldInfoPosition
import com.lhy.yumerin.core.model.WorldInfoSelectiveLogic
import com.lhy.yumerin.domain.persona.SemanticScorer
import com.lhy.yumerin.domain.persona.WorldInfoEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorldInfoEngineTest {
    @Test
    fun keywordEntryActivatesOnCaseInsensitiveMatch() {
        val engine = WorldInfoEngine()
        val book = WorldInfoBook(
            entries = listOf(
                WorldInfoEntry(uid = 1, key = listOf("咖啡"), content = "咖啡知识", order = 1),
                WorldInfoEntry(uid = 2, key = listOf("登山"), content = "登山知识", order = 2)
            )
        )

        val injections = engine.activate(book, "我想喝 CAFE 咖啡")

        assertEquals(1, injections.size)
        assertEquals("咖啡知识", injections.single().text)
    }

    @Test
    fun secondaryKeysRespectSelectiveLogic() {
        val engine = WorldInfoEngine()
        val andAll = WorldInfoEntry(
            uid = 1, key = listOf("旅行"), keysecondary = listOf("东京", "春天"),
            selectiveLogic = WorldInfoSelectiveLogic.AND_ALL, content = "东京春天"
        )
        val notAny = WorldInfoEntry(
            uid = 2, key = listOf("旅行"), keysecondary = listOf("东京"),
            selectiveLogic = WorldInfoSelectiveLogic.NOT_ANY, content = "非东京旅行"
        )

        val hits = engine.matchKeys(andAll, "我想去旅行，东京的春天")
        assertEquals(listOf("旅行", "东京", "春天"), hits)
        assertEquals(null, engine.matchKeys(andAll, "我想去旅行，东京"))
        assertEquals(null, engine.matchKeys(notAny, "我想去旅行，东京"))
        assertTrue(engine.matchKeys(notAny, "我想去旅行，京都") != null)
    }

    @Test
    fun constantEntriesAlwaysInject() {
        val engine = WorldInfoEngine()
        val book = WorldInfoBook(
            entries = listOf(WorldInfoEntry(uid = 1, constant = true, content = "世界规则"))
        )

        assertEquals("世界规则", engine.activate(book, "无关文本").single().text)
    }

    @Test
    fun recursionActivatesLinkedEntriesButPreventRecursionStopsIt() {
        val engine = WorldInfoEngine()
        val recursiveBook = WorldInfoBook(
            entries = listOf(
                WorldInfoEntry(uid = 1, key = listOf("触发"), content = "第一层包含 链接词"),
                WorldInfoEntry(uid = 2, key = listOf("链接词"), content = "第二层")
            )
        )
        val blockedBook = WorldInfoBook(
            entries = listOf(
                WorldInfoEntry(
                    uid = 1, key = listOf("触发"), content = "第一层包含 链接词", preventRecursion = true
                ),
                WorldInfoEntry(uid = 2, key = listOf("链接词"), content = "第二层")
            )
        )

        val recursive = engine.activate(recursiveBook, "触发")
        assertTrue(recursive.any { it.text == "第二层" })
        val blocked = engine.activate(blockedBook, "触发")
        assertTrue(blocked.none { it.text == "第二层" })
    }

    @Test
    fun inclusionGroupKeepsHighestWeightMember() {
        val engine = WorldInfoEngine()
        val book = WorldInfoBook(
            entries = listOf(
                WorldInfoEntry(uid = 1, key = listOf("战斗"), group = "scene", groupWeight = 50, content = "普通场景"),
                WorldInfoEntry(uid = 2, key = listOf("战斗"), group = "scene", groupWeight = 100, content = "高优先场景")
            )
        )

        val injections = engine.activate(book, "进入战斗")
        assertEquals(listOf("高优先场景"), injections.map { it.text })
    }

    @Test
    fun probabilisticEntriesRespectTheSource() {
        // Activation rule: roll < useProbability activates, so a zero roll always
        // activates and a 99 roll never activates for a 50-percent entry.
        val lucky = WorldInfoEngine(probabilitySource = { 0 })
        val unlucky = WorldInfoEngine(probabilitySource = { bound -> bound - 1 })
        val entry = WorldInfoEntry(uid = 1, key = listOf("彩蛋"), useProbability = 50, content = "彩蛋内容")

        assertEquals(1, lucky.activate(WorldInfoBook(entries = listOf(entry)), "彩蛋").size)
        assertEquals(0, unlucky.activate(WorldInfoBook(entries = listOf(entry)), "彩蛋").size)
    }

    @Test
    fun semanticScorerActivatesWithoutKeywords() {
        val scorer = SemanticScorer { _, _ -> 0.9f }
        val engine = WorldInfoEngine(scorer = scorer)
        val entry = WorldInfoEntry(uid = 1, content = "相关内容")
        assertEquals(1, engine.activate(WorldInfoBook(entries = listOf(entry)), "任意文本").size)

        val lowScorer = SemanticScorer { _, _ -> 0.1f }
        val strict = WorldInfoEngine(scorer = lowScorer)
        assertEquals(0, strict.activate(WorldInfoBook(entries = listOf(entry)), "任意文本").size)
    }

    @Test
    fun atDepthEntriesSortWithTheirDepth() {
        val engine = WorldInfoEngine()
        val book = WorldInfoBook(
            entries = listOf(
                WorldInfoEntry(
                    uid = 1, key = listOf("历史"), content = "深度注入",
                    position = WorldInfoPosition.AT_DEPTH, depth = 2
                ),
                WorldInfoEntry(uid = 2, key = listOf("历史"), content = "人格后注入", order = 1)
            )
        )

        val injections = engine.activate(book, "历史")
        assertEquals(
            listOf(WorldInfoPosition.AFTER_PERSONA, WorldInfoPosition.AT_DEPTH),
            injections.map { it.position }
        )
    }

    @Test
    fun disabledAndBlankEntriesNeverInject() {
        val engine = WorldInfoEngine()
        val book = WorldInfoBook(
            entries = listOf(
                WorldInfoEntry(uid = 1, key = listOf("词"), enabled = false, content = "禁用"),
                WorldInfoEntry(uid = 2, key = listOf("词"), content = "  "),
                WorldInfoEntry(uid = 3, key = listOf("词"), content = "启用")
            )
        )

        assertEquals(listOf("启用"), engine.activate(book, "词").map { it.text })
    }
}
