package com.lhy.yumerin.domain.memory

import com.lhy.yumerin.core.model.AgentRequest
import com.lhy.yumerin.core.model.MessageSenderType
import com.lhy.yumerin.core.model.MessageType
import com.lhy.yumerin.core.model.ChatKind
import com.lhy.yumerin.core.model.ChatMessage
import com.lhy.yumerin.core.model.ChatThread
import com.lhy.yumerin.core.model.EntityId
import com.lhy.yumerin.core.model.MemoryCategory
import com.lhy.yumerin.core.model.StructuredFact
import com.lhy.yumerin.core.model.newEntityId
import com.lhy.yumerin.domain.model.ModelPurpose
import com.lhy.yumerin.domain.model.ModelRouter

/**
 * Model-based memory extractor (ADR 0028). Routes a cheap model per
 * [ModelPurpose.MEMORY_EXTRACTION], asks for a strict JSON fact list, and
 * merges results with the lexical policy's candidates at construction time
 * via [com.lhy.yumerin.domain.memory.MemoryIntegrator]'s extractor slot.
 *
 * Failure mode is conservative: any parse or availability problem returns an
 * empty list so the lexical extractor's output still stands.
 */
class ModelMemoryExtractor(
    private val modelRouter: ModelRouter,
    private val complete: suspend (AgentRequest) -> String
) : MemoryExtractor {
    override suspend fun extract(userText: String): List<ExtractedMemoryFact> {
        if (userText.length > MAX_TEXT_CHARS) return emptyList()
        val model = modelRouter.select(ModelPurpose.MEMORY_EXTRACTION).profile ?: return emptyList()
        val chat = ChatThread(
            id = newEntityId("extraction"), kind = ChatKind.DIRECT, title = "memory-extraction",
            characterId = null, groupId = null, avatarUri = null,
            lastMessagePreview = "", lastActivityAt = 0L, unreadCount = 0,
            pinned = false, muted = false, shortTermSummary = "",
            summaryMaintenanceMode = com.lhy.yumerin.core.model.SummaryMaintenanceMode.AUTO
        )
        val request = AgentRequest(
            chat = chat,
            character = null,
            messages = listOf(
                ChatMessage(
                    id = newEntityId("message"), chatId = chat.id, senderId = null,
                    senderType = MessageSenderType.USER, senderName = "我", senderAvatarUri = null,
                    text = PROMPT + userText, timestamp = 0L,
                    type = MessageType.TEXT, isProactive = false, isRead = true
                )
            ),
            memories = emptyList(),
            model = model
        )
        val response = runCatching { complete(request) }.getOrNull() ?: return emptyList()
        return parse(response)
    }

    private fun parse(response: String): List<ExtractedMemoryFact> {
        val jsonStart = response.indexOf('{')
        val arrayStart = response.indexOf('[')
        val arrayJson = when {
            arrayStart >= 0 && (jsonStart < 0 || arrayStart < jsonStart) ->
                response.substring(arrayStart, response.lastIndexOf(']') + 1)
            else -> return emptyList()
        }
        return com.lhy.yumerin.data.portable.st.JsonFlattener.arrayElements(arrayJson).mapNotNull { element ->
            val content = com.lhy.yumerin.data.portable.st.JsonFlattener.stringValue(element, "content")?.trim()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            if (content.length > MAX_FACT_CHARS) return@mapNotNull null
            ExtractedMemoryFact(
                content = content,
                category = parseCategory(com.lhy.yumerin.data.portable.st.JsonFlattener.stringValue(element, "category")),
                confidence = numericField(element, "confidence")?.coerceIn(0f, 1f) ?: 0.7f,
                importance = numericField(element, "importance")?.coerceIn(0f, 1f) ?: 0.5f,
                emotionTag = com.lhy.yumerin.data.portable.st.JsonFlattener.stringValue(element, "emotionTag")?.takeIf { it.isNotBlank() },
                structuredFact = structuredFact(element)
            )
        }
    }

    /** Reads a scalar number field that may be quoted or bare. */
    private fun numericField(element: String, key: String): Float? {
        val raw = com.lhy.yumerin.data.portable.st.JsonFlattener.stringValue(element, key)
            ?: com.lhy.yumerin.data.portable.st.JsonFlattener.rawField(element, key)
            ?: return null
        return raw.toFloatOrNull()
    }

    private fun structuredFact(element: String): StructuredFact? {
        val subject = com.lhy.yumerin.data.portable.st.JsonFlattener.stringValue(element, "subject")?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val predicate = com.lhy.yumerin.data.portable.st.JsonFlattener.stringValue(element, "predicate")?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val value = com.lhy.yumerin.data.portable.st.JsonFlattener.stringValue(element, "value")?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (subject.length > 80 || predicate.length > 40 || value.length > 200) return null
        return StructuredFact(subject, predicate, value)
    }

    private fun parseCategory(value: String?): MemoryCategory = when (value?.lowercase()) {
        "preference", "user_preference" -> MemoryCategory.USER_PREFERENCE
        "relationship" -> MemoryCategory.RELATIONSHIP
        "event", "important_event" -> MemoryCategory.IMPORTANT_EVENT
        else -> MemoryCategory.LONG_TERM
    }

    private companion object {
        const val MAX_TEXT_CHARS = 2_000
        const val MAX_FACT_CHARS = 400
        val PROMPT = """从下面的消息中抽取值得长期记住的稳定事实（用户偏好、关系、重要事件）。只输出一个 JSON 数组，每项形如 {"content":"…","category":"preference|relationship|event|long_term","confidence":0.0-1.0,"importance":0.0-1.0,"emotionTag":"…","subject":"…","predicate":"…","value":"…"}。subject/predicate/value 仅当事实可以表达为关于用户的结构化三元组时才填。没有值得记住的内容时输出 []。消息："""
    }
}

