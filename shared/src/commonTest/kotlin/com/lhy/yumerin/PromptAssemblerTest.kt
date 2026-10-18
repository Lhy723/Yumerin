package com.lhy.yumerin

import com.lhy.yumerin.core.model.AgentEmotionalContext
import com.lhy.yumerin.core.model.AgentRequest
import com.lhy.yumerin.core.model.AgentVisibility
import com.lhy.yumerin.core.model.CharacterProfile
import com.lhy.yumerin.core.model.ChatKind
import com.lhy.yumerin.core.model.ChatMessage
import com.lhy.yumerin.core.model.ChatThread
import com.lhy.yumerin.core.model.GenerationParameters
import com.lhy.yumerin.core.model.MemoryCategory
import com.lhy.yumerin.core.model.MemoryEntry
import com.lhy.yumerin.core.model.MemoryReviewState
import com.lhy.yumerin.core.model.MessageSenderType
import com.lhy.yumerin.core.model.MessageType
import com.lhy.yumerin.core.model.ModelProfile
import com.lhy.yumerin.core.model.ModelProviderKind
import com.lhy.yumerin.core.model.PersonaPackage
import com.lhy.yumerin.core.model.SummaryMaintenanceMode
import com.lhy.yumerin.core.model.WorldInfoBook
import com.lhy.yumerin.core.model.WorldInfoEntry
import com.lhy.yumerin.core.model.WorldInfoPosition
import com.lhy.yumerin.domain.persona.PromptAssembler
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromptAssemblerTest {
    @Test
    fun personaLayerPlacesSystemPersonaMemoryAndHistoryInOrder() {
        val request = request(
            character = character,
            messages = listOf(userMessage("我们聊聊雨夜吧")),
            memories = listOf(acceptedMemory("memory-1", "用户喜欢雨夜"))
        )
        val assembled = PromptAssembler.assembleDetailed(request, PromptAssembler.Layer.PERSONA)

        val system = assembled.messages.first()
        assertEquals("system", system.role)
        assertTrue(system.content.contains("Character name: 小雨"))
        assertTrue(system.content.contains("Relationship:"))
        assertTrue(system.content.contains("Recent chat summary"))
        assertTrue(system.content.contains("Character's current emotional tone"))
        val memoryMessage = assembled.messages.first { it.role == "system" && it.content.startsWith("Relevant character memory") }
        assertTrue(memoryMessage.content.contains("用户喜欢雨夜"))
        assertEquals(listOf("memory-1"), assembled.usedMemoryIds)
        assertTrue(assembled.messages.last().role == "user" || assembled.messages.size == 2)
    }

    @Test
    fun executorLayerUsesNeutralFramingWithoutTheatricalProse() {
        val request = request(character = character, messages = listOf(userMessage("帮我算一下")))
        val assembled = PromptAssembler.assembleDetailed(request, PromptAssembler.Layer.EXECUTOR)
        val joined = assembled.messages.joinToString("\n") { it.content }

        assertTrue(joined.contains("bounded companion task"))
        assertTrue(joined.contains("Character name: 小雨"))
        assertFalse(joined.contains("Interaction style:"))
        assertFalse(joined.contains("Greeting example:"))
    }

    @Test
    fun creatorSystemPromptWithOriginalMacroWrapsYumerinRules() {
        val persona = PersonaPackage(
            name = "小雨",
            systemPrompt = "Creator rule.\n{{original}}"
        )
        val request = request(character = character, messages = listOf(userMessage("你好")), persona = persona)
        val assembled = PromptAssembler.assembleDetailed(request, PromptAssembler.Layer.PERSONA)
        val system = assembled.messages.first().content

        assertTrue(system.contains("Creator rule."))
        assertTrue(system.contains("Character name: 小雨"))
        assertTrue(system.indexOf("Creator rule.") < system.indexOf("Character name: 小雨"))
    }

    @Test
    fun macrosExpandInPersonaAndPostHistoryGoesAfterHistory() {
        val persona = PersonaPackage(
            name = "小雨",
            personality = "{{char}} 温柔",
            postHistoryInstructions = "{{user}}，请保持回答简短。"
        )
        val request = request(
            character = character,
            messages = listOf(userMessage("你好")),
            persona = persona
        )
        val assembled = PromptAssembler.assembleDetailed(request, PromptAssembler.Layer.PERSONA)

        assertTrue(assembled.messages.first().content.contains("小雨 温柔"))
        val last = assembled.messages.last()
        assertEquals("system", last.role)
        assertTrue(last.content.contains("请保持回答简短。"))
    }

    @Test
    fun worldInfoActivatesAndInjectsAtDepthInsideHistory() {
        val book = WorldInfoBook(
            entries = listOf(
                WorldInfoEntry(
                    uid = 1,
                    key = listOf("雨夜"),
                    content = "雨夜是小雨最喜欢的场景。",
                    position = WorldInfoPosition.AT_DEPTH,
                    depth = 1
                )
            )
        )
        val persona = PersonaPackage(name = "小雨", characterBook = book)
        val request = request(
            character = character,
            messages = listOf(userMessage("上一句"), userMessage("我们聊聊雨夜吧")),
            persona = persona
        )
        val assembled = PromptAssembler.assembleDetailed(request, PromptAssembler.Layer.PERSONA)

        assertTrue(assembled.worldInfoHits.any { it.contains("雨夜是小雨最喜欢的场景") })
        val indexOfInjection = assembled.messages.indexOfFirst { it.content.contains("雨夜是小雨最喜欢的场景") }
        val indexOfLastUser = assembled.messages.indexOfLast { it.role == "user" }
        assertTrue(indexOfInjection in 0..indexOfLastUser)
    }

    @Test
    fun budgetKeepsHistoryBoundedByContextWindow() {
        val longMessages = (0..200).map { userMessage("这是很长的第 $it 条消息，用来填满上下文预算。") }
        val assembled = PromptAssembler.assembleDetailed(
            request(character = character, messages = longMessages),
            PromptAssembler.Layer.PERSONA
        )
        val total = assembled.messages.sumOf { it.content.length }
        assertTrue(total <= 8_192 * 4 * 0.8f + 2_000, "assembled prompt exceeded budget: $total")
    }

    @Test
    fun slotBudgetsExposeCompositionForDebugging() {
        val assembled = PromptAssembler.assembleDetailed(
            request(character = character, messages = listOf(userMessage("你好"))),
            PromptAssembler.Layer.PERSONA
        )
        assertTrue(assembled.slotBudgets.any { it.slot == PromptAssembler.Slot.SYSTEM_RULES })
        assertTrue(assembled.slotBudgets.all { it.estimatedTokens >= 0 })
    }

    private fun request(
        character: CharacterProfile?,
        messages: List<ChatMessage>,
        memories: List<MemoryEntry> = emptyList(),
        persona: PersonaPackage? = null
    ) = AgentRequest(
        chat = chat,
        character = character,
        messages = messages,
        memories = memories,
        model = model,
        visibility = AgentVisibility.FINAL_ONLY,
        emotionalContext = AgentEmotionalContext("平静", "稳定", "distressed", "先回应感受"),
        persona = persona
    )

    private fun userMessage(text: String) = ChatMessage(
        id = "message-$text", chatId = chat.id, senderId = null,
        senderType = MessageSenderType.USER, senderName = "我", senderAvatarUri = null,
        text = text, timestamp = 1L, type = MessageType.TEXT, isProactive = false, isRead = true
    )

    private fun acceptedMemory(id: String, content: String) = MemoryEntry(
        id = id, characterId = character.id, sourceChatId = chat.id, sourceMessageId = null,
        category = MemoryCategory.LONG_TERM, content = content, confidence = 0.95f,
        reviewState = MemoryReviewState.ACCEPTED, pinned = false, supersedesId = null,
        createdAt = 0L, updatedAt = 0L, reviewAt = null
    )

    private companion object {
        val character = CharacterProfile(
            id = "character-test", name = "小雨", avatarUri = null,
            relationshipFraming = "亲密的朋友", setting = "雨夜的城市", description = "会认真听你说话",
            personality = "温柔", tone = "自然", backstory = "", interactionStyle = "简短",
            modelProfileId = "model-test", archived = false, createdAt = 0L, updatedAt = 0L
        )
        val chat = ChatThread(
            id = "chat-test", kind = ChatKind.DIRECT, title = character.name,
            characterId = character.id, groupId = null, avatarUri = null,
            lastMessagePreview = "", lastActivityAt = 0L, unreadCount = 0,
            pinned = false, muted = false, shortTermSummary = "刚开始熟悉彼此。",
            summaryMaintenanceMode = SummaryMaintenanceMode.AUTO
        )
        val model = ModelProfile(
            id = "model-test", displayName = "测试模型", providerKind = ModelProviderKind.OPENAI_COMPATIBLE,
            providerName = "test", baseUrl = null, modelId = "test", credentialAlias = null,
            contextLength = 8_192, contextLengthMode = com.lhy.yumerin.core.model.ContextLengthMode.KNOWN,
            generationParameters = GenerationParameters(), localModelId = null, localModelVersion = null,
            enabled = true, createdAt = 0L, updatedAt = 0L
        )
    }
}
