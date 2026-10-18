package com.lhy.yumerin.domain.memory

/**
 * Runs the lexical policy first (always available, deterministic) and merges
 * the model-based extractor's facts on top (ADR 0028). Duplicates by content
 * are dropped with lexical precedence; the integrator still dedupes and
 * resolves conflicts against stored entries.
 */
class HybridMemoryExtractor(
    private val lexical: MemoryExtractor,
    private val modelBased: MemoryExtractor?
) : MemoryExtractor {
    override suspend fun extract(userText: String): List<ExtractedMemoryFact> {
        val lexicalFacts = runCatching { lexical.extract(userText) }.getOrDefault(emptyList())
        val modelFacts = modelBased?.let { extractor ->
            runCatching { extractor.extract(userText) }.getOrDefault(emptyList())
        }.orEmpty()
        if (modelFacts.isEmpty()) return lexicalFacts
        val seen = lexicalFacts.mapTo(mutableSetOf()) { it.content }
        return lexicalFacts + modelFacts.filter { it.content !in seen }
    }
}
