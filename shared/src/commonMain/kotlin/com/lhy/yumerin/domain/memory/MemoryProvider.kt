package com.lhy.yumerin.domain.memory

import com.lhy.yumerin.core.model.EntityId
import com.lhy.yumerin.core.model.MemoryEntry
import com.lhy.yumerin.core.model.MemoryReviewState
import com.lhy.yumerin.domain.agent.CharacterMemoryRetriever
import com.lhy.yumerin.domain.repository.MemoryRepository
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

/**
 * The memory surface a turn consumes. Implementations keep Character-scoped
 * isolation (ADR 0009): a provider is always bound to one character's memory
 * space. Retrieval returns ranked entries; the brief is a compact digest for
 * proactive messages and future voice calls that must read memory ahead of
 * the turn instead of mid-turn.
 */
interface MemoryProvider {
    /** Ranked, accepted, unexpired entries for the current turn's query. */
    suspend fun retrieve(query: String, limit: Int = DEFAULT_RECALL_LIMIT): List<MemoryEntry>

    /**
     * Compact pre-turn digest: pinned and important entries plus a recency
     * tail. At most [DEFAULT_BRIEF_LIMIT] lines so a proactive or voice turn
     * can inject it without a full retrieval pass.
     */
    suspend fun brief(limit: Int = DEFAULT_BRIEF_LIMIT): String

    companion object {
        const val DEFAULT_RECALL_LIMIT = 24
        const val DEFAULT_BRIEF_LIMIT = 8
    }
}

/** Default implementation over the lexical retriever and the memory repository. */
class RepositoryMemoryProvider(
    private val characterId: EntityId,
    private val memories: MemoryRepository
) : MemoryProvider {
    override suspend fun retrieve(query: String, limit: Int): List<MemoryEntry> =
        CharacterMemoryRetriever.retrieve(
            memories = memories.observeMemories(characterId).first(),
            query = query,
            nowMillis = Clock.System.now().toEpochMilliseconds(),
            limit = limit
        )

    override suspend fun brief(limit: Int): String {
        val nowMillis = Clock.System.now().toEpochMilliseconds()
        val accepted = memories.observeMemories(characterId).first().filter {
            it.reviewState == MemoryReviewState.ACCEPTED &&
                it.deletedAt == null && (it.expiresAt == null || it.expiresAt > nowMillis)
        }
        if (accepted.isEmpty()) return ""
        val ranked = accepted.sortedWith(
            compareByDescending<MemoryEntry> { it.pinned }
                .thenByDescending { it.importance }
                .thenByDescending { it.confidence }
                .thenByDescending { it.updatedAt }
        ).take(limit)
        return ranked.joinToString("\n") { "- ${it.content}" }
    }
}
