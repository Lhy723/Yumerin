package com.lhy.yumerin.domain.agent

import com.lhy.yumerin.core.model.MemoryEntry
import com.lhy.yumerin.core.model.MemoryReviewState

/** Character-scoped lexical recall with explicit review, expiry, and provenance eligibility. */
object CharacterMemoryRetriever {
    fun retrieve(
        memories: List<MemoryEntry>,
        query: String,
        nowMillis: Long,
        limit: Int = DEFAULT_LIMIT
    ): List<MemoryEntry> {
        if (limit <= 0) return emptyList()
        val eligible = memories.filter {
            it.reviewState == MemoryReviewState.ACCEPTED &&
                it.deletedAt == null &&
                (it.expiresAt == null || it.expiresAt > nowMillis) &&
                it.content.isNotBlank()
        }
        if (eligible.isEmpty()) return emptyList()
        val queryTerms = terms(query)
        val scored = eligible.map { memory ->
            val overlap = terms(memory.content).intersect(queryTerms).size
            val relevance = if (queryTerms.isEmpty()) 0f else overlap.toFloat() / queryTerms.size
            val score = (if (memory.pinned) PINNED_SCORE else 0f) + relevance * 10f + memory.confidence.coerceIn(0f, 1f)
            ScoredMemory(memory, relevance, score)
        }
        val hasRelevant = scored.any { it.relevance > 0f }
        return scored.asSequence()
            .filter { !hasRelevant || it.relevance > 0f || it.memory.pinned }
            .sortedWith(
                compareByDescending<ScoredMemory> { it.score }
                    .thenByDescending { it.memory.updatedAt }
            )
            .take(limit)
            .map { it.memory }
            .toList()
    }

    private fun terms(text: String): Set<String> {
        val result = mutableSetOf<String>()
        val latin = StringBuilder()
        val cjk = StringBuilder()
        fun flushLatin() {
            if (latin.length >= 2) result += latin.toString().lowercase()
            latin.clear()
        }
        fun flushCjk() {
            if (cjk.isNotEmpty()) {
                val value = cjk.toString()
                result += value
                if (value.length > 1) {
                    for (index in 0 until value.lastIndex) result += value.substring(index, index + 2)
                    if (value.length > 3) {
                        for (index in 0..value.length - 3) result += value.substring(index, index + 3)
                    }
                }
            }
            cjk.clear()
        }
        text.lowercase().forEach { char ->
            when {
                char.isLetterOrDigit() && char.code in CJK_START..CJK_END -> {
                    flushLatin()
                    cjk.append(char)
                }
                char.isLetterOrDigit() -> {
                    flushCjk()
                    latin.append(char)
                }
                else -> {
                    flushLatin()
                    flushCjk()
                }
            }
        }
        flushLatin()
        flushCjk()
        return result
    }

    private const val DEFAULT_LIMIT = 24
    private data class ScoredMemory(val memory: MemoryEntry, val relevance: Float, val score: Float)
    private const val PINNED_SCORE = 100f
    private const val CJK_START = 0x4E00
    private const val CJK_END = 0x9FFF
}
