package com.lhy.yumerin.domain.memory

import com.lhy.yumerin.core.model.EntityId
import com.lhy.yumerin.core.model.MemoryCategory
import com.lhy.yumerin.core.model.MemoryEntry
import com.lhy.yumerin.core.model.MemoryReviewState
import com.lhy.yumerin.core.model.StructuredFact
import com.lhy.yumerin.core.model.newEntityId
import com.lhy.yumerin.domain.repository.MemoryRepository
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

/** One fact candidate produced by an extractor. */
data class ExtractedMemoryFact(
    val content: String,
    val category: MemoryCategory,
    val confidence: Float,
    val sensitive: Boolean = false,
    val reviewReason: String? = null,
    val importance: Float = 0.5f,
    val emotionTag: String? = null,
    val occurredAt: Long? = null,
    val structuredFact: StructuredFact? = null
)

/**
 * Turns raw turn text into candidate facts. The lexical policy is the default;
 * a model-based extractor can replace or supplement it behind this interface.
 */
fun interface MemoryExtractor {
    suspend fun extract(userText: String): List<ExtractedMemoryFact>
}

/**
 * The background consolidator. Runs only after a turn fully succeeded —
 * failed, cancelled, and retried-in-vain turns never touch memory. Rules:
 * dedupe exact content, supersede conflicting accepted entries only with a
 * high-confidence non-sensitive candidate, and park everything else in the
 * review queue.
 */
class MemoryIntegrator(
    private val memories: MemoryRepository,
    private val extractor: MemoryExtractor = MemoryExtractor(::lexicalExtractor)
) {
    /**
     * Integrates one successful user turn into the character's memory space.
     * Returns the number of entries written (including supersede updates).
     */
    suspend fun integrateSuccessfulTurn(
        characterId: EntityId,
        chatId: EntityId,
        sourceMessageId: EntityId,
        userText: String,
        nowMillis: Long
    ): Int {
        if (userText.isBlank()) return 0
        val candidates = runCatching { extractor.extract(userText) }.getOrDefault(emptyList())
        if (candidates.isEmpty()) return 0
        val current = memories.observeMemories(characterId).first()
        var written = 0
        candidates.forEach { candidate ->
            if (candidate.content.isBlank()) return@forEach
            if (current.any { it.content == candidate.content && it.reviewState != MemoryReviewState.REJECTED }) return@forEach
            val sameTopic = current.filter {
                it.category == candidate.category && memoryTopic(it.content) == memoryTopic(candidate.content)
            }
            val highConfidence = candidate.confidence >= AUTO_ACCEPT_CONFIDENCE && !candidate.sensitive
            val conflict = sameTopic.firstOrNull {
                it.content != candidate.content && it.reviewState == MemoryReviewState.ACCEPTED
            }
            if (conflict != null && highConfidence) {
                memories.saveMemory(
                    conflict.copy(
                        reviewState = MemoryReviewState.SUPERSEDED,
                        updatedAt = nowMillis
                    )
                )
                written += 1
            }
            memories.saveMemory(
                MemoryEntry(
                    id = newEntityId("memory"),
                    characterId = characterId,
                    sourceChatId = chatId,
                    sourceMessageId = sourceMessageId,
                    category = candidate.category,
                    content = candidate.content,
                    confidence = candidate.confidence,
                    reviewState = if (highConfidence) MemoryReviewState.ACCEPTED else MemoryReviewState.PENDING_REVIEW,
                    pinned = false,
                    supersedesId = conflict?.id,
                    createdAt = nowMillis,
                    updatedAt = nowMillis,
                    reviewAt = if (highConfidence) null else nowMillis + REVIEW_WINDOW_MILLIS,
                    reviewReason = candidate.reviewReason
                        ?: conflict?.let { "与现有记忆冲突，需要用户确认" },
                    importance = candidate.importance.coerceIn(0f, 1f),
                    emotionTag = candidate.emotionTag,
                    occurredAt = candidate.occurredAt
                )
            )
            written += 1
        }
        return written
    }

    private fun memoryTopic(content: String): String = content
        .removePrefix("用户喜欢").removePrefix("用户不喜欢").removePrefix("用户可能喜欢")
        .removePrefix("用户的名字是").trim()

    companion object {
        const val AUTO_ACCEPT_CONFIDENCE = 0.86f
        const val REVIEW_WINDOW_MILLIS = 7L * 24 * 60 * 60 * 1_000

        /** Bridges the domain policy's candidates into the integrator's shape. */
        private fun lexicalExtractor(userText: String): List<ExtractedMemoryFact> =
            MemoryPolicy.extract(userText).map { candidate ->
                ExtractedMemoryFact(
                    content = candidate.content,
                    category = candidate.category,
                    confidence = candidate.confidence,
                    sensitive = candidate.sensitive,
                    reviewReason = candidate.reviewReason
                )
            }
    }
}
