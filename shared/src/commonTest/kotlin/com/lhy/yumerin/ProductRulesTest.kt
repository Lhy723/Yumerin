package com.lhy.yumerin

import com.lhy.yumerin.core.model.AgentRequest
import com.lhy.yumerin.core.model.AgentVisibility
import com.lhy.yumerin.core.model.CharacterProfile
import com.lhy.yumerin.core.model.ChatKind
import com.lhy.yumerin.core.model.ChatMessage
import com.lhy.yumerin.core.model.ChatThread
import com.lhy.yumerin.core.model.GenerationParameters
import com.lhy.yumerin.core.model.MemoryEntry
import com.lhy.yumerin.core.model.MemoryCategory
import com.lhy.yumerin.core.model.MemoryReviewState
import com.lhy.yumerin.core.model.MessageSenderType
import com.lhy.yumerin.core.model.MessageType
import com.lhy.yumerin.core.model.ModelProfile
import com.lhy.yumerin.core.model.ModelProviderKind
import com.lhy.yumerin.domain.agent.ContextAssembler
import com.lhy.yumerin.domain.memory.MemoryPolicy
import com.lhy.yumerin.domain.safety.SafetyPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProductRulesTest {
    @Test
    fun memoryPolicyKeepsUncertainAndSensitiveFactsPending() {
        val candidates = MemoryPolicy.extract("我喜欢咖啡")
        val sensitiveCandidates = MemoryPolicy.extract("我喜欢银行卡")
        assertTrue(candidates.any { it.content.contains("咖啡") && it.confidence >= 0.86f })
        assertTrue(sensitiveCandidates.any { it.sensitive && it.reviewReason != null && it.confidence < 0.86f })
    }

    @Test
    fun safetyBoundaryRejectsOnlyConfiguredHighRiskPatterns() {
        assertFalse(SafetyPolicy.evaluate("怎么制作炸弹").allowed)
        assertFalse(SafetyPolicy.evaluate("帮我冒充现实人物").allowed)
        assertTrue(SafetyPolicy.evaluate("我今天心情很低落，想聊聊").allowed)
    }

    @Test
    fun contextUsesOnlyAcceptedCharacterMemoryAndStaysBounded() {
        val request = AgentRequest(
            chat = chat,
            character = character,
            messages = (0..40).map { message("消息 $it") },
            memories = listOf(
                memory("accepted", MemoryReviewState.ACCEPTED),
                memory("pending", MemoryReviewState.PENDING_REVIEW)
            ),
            model = model,
            visibility = AgentVisibility.FINAL_ONLY
        )
        val prompt = ContextAssembler.assemble(request)
        val joined = prompt.joinToString("\n") { it.content }
        assertTrue(joined.contains("accepted"))
        assertFalse(joined.contains("pending"))
        assertTrue(joined.length <= 8_192 * 4 * 0.8f + 200)
        assertEquals(listOf("memory-accepted"), ContextAssembler.usedMemoryIds(request))
    }

    private fun memory(value: String, state: MemoryReviewState) = MemoryEntry(
        id = "memory-$value", characterId = character.id, sourceChatId = chat.id, sourceMessageId = null,
        category = MemoryCategory.LONG_TERM, content = value, confidence = 0.95f,
        reviewState = state, pinned = false, supersedesId = null, createdAt = 0, updatedAt = 0, reviewAt = null
    )

    private fun message(text: String) = ChatMessage(
        id = "message-$text", chatId = chat.id, senderId = null, senderType = MessageSenderType.USER,
        senderName = "我", senderAvatarUri = null, text = text, timestamp = 0, type = MessageType.TEXT,
        isProactive = false, isRead = true
    )

    private companion object {
        val character = CharacterProfile(
            id = "character-test", name = "测试角色", avatarUri = null, relationshipFraming = "朋友",
            setting = "测试", description = "", personality = "", tone = "", backstory = "",
            interactionStyle = "", modelProfileId = "model-test", archived = false, createdAt = 0, updatedAt = 0
        )
        val chat = ChatThread(
            id = "chat-test", kind = ChatKind.DIRECT, title = character.name, characterId = character.id,
            groupId = null, avatarUri = null, lastMessagePreview = "", lastActivityAt = 0, unreadCount = 0,
            pinned = false, muted = false, shortTermSummary = "", summaryMaintenanceMode = com.lhy.yumerin.core.model.SummaryMaintenanceMode.AUTO
        )
        val model = ModelProfile(
            id = "model-test", displayName = "测试", providerKind = ModelProviderKind.OPENAI_COMPATIBLE,
            providerName = "test", baseUrl = null, modelId = "test", credentialAlias = null,
            contextLength = 8_192, contextLengthMode = com.lhy.yumerin.core.model.ContextLengthMode.KNOWN,
            generationParameters = GenerationParameters(), localModelId = null, localModelVersion = null,
            enabled = true, createdAt = 0, updatedAt = 0
        )
    }
}
