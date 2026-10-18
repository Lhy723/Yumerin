package com.lhy.yumerin.domain.persona

import com.lhy.yumerin.core.model.WorldInfoBook
import com.lhy.yumerin.core.model.WorldInfoEntry
import com.lhy.yumerin.core.model.WorldInfoPosition
import com.lhy.yumerin.core.model.WorldInfoSelectiveLogic

/**
 * Clean-room world-info (lorebook) activation engine.
 *
 * The semantics follow the public community card/world-book conventions:
 * keyword keys with and/or/not selective logic, constant entries, recursive
 * activation with a recursion guard, inclusion groups with weights, ordered
 * injection, probabilistic activation, and three injection positions (before
 * the persona block, after it, or at a chat depth inside the history).
 *
 * Activation is keyword-first with an optional semantic scorer on top, so a
 * vector backend can be plugged in later without changing entry format. No
 * card-authored content is executed: entries only contribute prompt text.
 */

data class WorldInfoActivation(
    val entry: WorldInfoEntry,
    val matchedKeys: List<String>
)

/** One assembled injection block with the position it belongs to. */
data class WorldInfoInjection(
    val position: WorldInfoPosition,
    val depth: Int,
    val order: Int,
    val text: String
)

/** Optional pluggable relevance signal (embedding/vector backend in the future). */
fun interface SemanticScorer {
    /** Returns 0..1 similarity between the entry text and the scan text, or null when unknown. */
    fun similarity(entry: WorldInfoEntry, scanText: String): Float?
}

class WorldInfoEngine(
    private val scorer: SemanticScorer? = null,
    private val semanticThreshold: Float = 0.6f,
    /** Deterministic source for probabilistic entries; tests pass a fixed value. */
    private val probabilitySource: (Int) -> Int = { bound -> kotlin.random.Random.nextInt(bound) }
) {
    /**
     * Activates entries against the scan text and returns injection blocks
     * sorted by position, then depth, then order. Each entry activates at most
     * once per run; recursion is bounded by [MAX_ACTIVATED_ENTRIES].
     */
    fun activate(book: WorldInfoBook, scanText: String): List<WorldInfoInjection> {
        val enabled = book.entries.filter { it.enabled && it.content.isNotBlank() }
        if (enabled.isEmpty()) return emptyList()
        val activated = LinkedHashMap<Int, WorldInfoActivation>()
        val pending = ArrayDeque<WorldInfoEntry>()
        val excluded = mutableSetOf<Int>()

        enabled.filter { it.constant }.forEach { entry ->
            if (tryActivate(entry, scanText, emptyList(), activated)) {
                if (!entry.preventRecursion) pending += entry
                excluded += entry.excludeRecursionIds
            }
        }
        enabled.forEach { entry ->
            if (entry.uid in activated || entry.uid in excluded) return@forEach
            // null means the keyword gate rejected this entry; emptyList() is
            // reserved for keyless entries that fall through to constant/semantic rules.
            val matches = matchKeys(entry, scanText) ?: return@forEach
            if (tryActivate(entry, scanText, matches, activated)) {
                if (!entry.preventRecursion) pending += entry
                excluded += entry.excludeRecursionIds
            }
        }
        while (pending.isNotEmpty() && activated.size < MAX_ACTIVATED_ENTRIES) {
            val source = pending.removeFirst()
            val triggerText = "$scanText\n${source.content}"
            enabled.forEach { entry ->
                if (entry.uid == source.uid || entry.uid in activated || entry.uid in excluded) return@forEach
                val matches = matchKeys(entry, triggerText) ?: return@forEach
                if (tryActivate(entry, triggerText, matches, activated)) {
                    if (!entry.preventRecursion) pending += entry
                    excluded += entry.excludeRecursionIds
                }
            }
        }

        val winners = selectGroupWinners(activated.values.toList())
        return winners.map { activation ->
            WorldInfoInjection(
                position = activation.entry.position,
                depth = activation.entry.depth,
                order = activation.entry.order,
                text = activation.entry.content
            )
        }.sortedWith(compareBy({ it.position }, { it.depth }, { it.order }))
    }

    /** Returns matched keys when the entry triggers on [text], or null when it does not. */
    fun matchKeys(entry: WorldInfoEntry, text: String): List<String>? {
        val primary = entry.key.map(String::trim).filter(String::isNotEmpty)
        val secondary = entry.keysecondary.map(String::trim).filter(String::isNotEmpty)
        // Keyless entries have no keyword gate; constant/semantic rules decide.
        if (primary.isEmpty() && secondary.isEmpty()) return emptyList()
        val lowered = text.lowercase()
        val primaryMatches = primary.filter { lowered.contains(it.lowercase()) }
        if (primary.isNotEmpty() && primaryMatches.isEmpty()) return null
        if (secondary.isEmpty()) return primaryMatches
        val secondaryMatches = secondary.count { lowered.contains(it.lowercase()) }
        val secondaryOk = when (entry.selectiveLogic) {
            WorldInfoSelectiveLogic.AND_ANY -> secondaryMatches >= 1
            WorldInfoSelectiveLogic.NOT_ALL -> secondaryMatches < secondary.size
            WorldInfoSelectiveLogic.NOT_ANY -> secondaryMatches == 0
            WorldInfoSelectiveLogic.AND_ALL -> secondaryMatches == secondary.size
        }
        return if (secondaryOk) {
            primaryMatches + secondary.filter { lowered.contains(it.lowercase()) }
        } else {
            null
        }
    }

    private fun tryActivate(
        entry: WorldInfoEntry,
        scanText: String,
        matchedKeys: List<String>,
        activated: MutableMap<Int, WorldInfoActivation>
    ): Boolean {
        if (entry.uid in activated) return false
        val probability = entry.useProbability
        when {
            probability == null || probability >= 100 -> Unit
            probability <= 0 -> return false
            probabilitySource(100) >= probability -> return false
        }
        val keyless = entry.key.none(String::isNotBlank) && entry.keysecondary.none(String::isNotBlank)
        if (keyless && !entry.constant) {
            // No keywords to trigger on: only a semantic hit can activate it.
            val semantic = scorer?.similarity(entry, scanText) ?: return false
            if (semantic < semanticThreshold) return false
        }
        activated[entry.uid] = WorldInfoActivation(entry, matchedKeys)
        return true
    }

    /** Within one inclusion group only the highest-weight members activate; ties activate all. */
    private fun selectGroupWinners(activations: List<WorldInfoActivation>): List<WorldInfoActivation> {
        val groups = activations.mapNotNull { activation ->
            activation.entry.group?.takeIf(String::isNotBlank)?.let { it to activation }
        }.groupBy({ it.first }, { it.second })
        val losers = mutableSetOf<Int>()
        groups.values.forEach { members ->
            if (members.size <= 1) return@forEach
            val maxWeight = members.maxOf { it.entry.groupWeight }
            members.filter { it.entry.groupWeight < maxWeight }.forEach { losers += it.entry.uid }
        }
        return activations.filterNot { it.entry.uid in losers }
    }

    companion object {
        const val MAX_ACTIVATED_ENTRIES = 64
    }
}
