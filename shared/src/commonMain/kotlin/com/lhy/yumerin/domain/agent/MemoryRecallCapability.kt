package com.lhy.yumerin.domain.agent

import com.lhy.yumerin.core.model.AgentCapabilityDescriptor
import com.lhy.yumerin.core.model.AgentRequest
import com.lhy.yumerin.core.model.MessageSenderType
import com.lhy.yumerin.domain.repository.MemoryRepository
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

/** Searches only the active Character's accepted, unexpired memory entries. */
class MemoryRecallCapability(
    private val memories: MemoryRepository
) : AgentCapability {
    override val descriptor = AgentCapabilityDescriptor(
        name = "memory_recall",
        description = "Search this character's accepted memory when the user explicitly asks what was remembered.",
        parametersJsonSchema = """{"type":"object","properties":{"query":{"type":"string","description":"The specific topic or detail to recall"}},"required":["query"],"additionalProperties":false}"""
    )

    override fun isAvailableFor(request: AgentRequest): Boolean {
        val text = request.proactivePrompt ?: request.messages.lastOrNull { it.senderType == MessageSenderType.USER }?.text.orEmpty()
        return RECALL_INTENT.containsMatchIn(text)
    }

    override suspend fun execute(
        argumentsJson: String,
        context: AgentCapabilityContext
    ): AgentCapabilityResult {
        val characterId = context.characterId
            ?: return AgentCapabilityResult(false, "There is no active character memory space in this run.")
        val query = AgentJsonStringArgument.read(argumentsJson, "query")?.trim().orEmpty()
        if (query.isBlank() || query.length > 240) {
            return AgentCapabilityResult(false, "Invalid arguments: provide a short, specific query.")
        }
        val recalled = CharacterMemoryRetriever.retrieve(
            memories = memories.observeMemories(characterId).first(),
            query = query,
            nowMillis = Clock.System.now().toEpochMilliseconds(),
            limit = MAX_RECALLED_MEMORIES
        )
        if (recalled.isEmpty()) return AgentCapabilityResult(true, "No matching accepted memory was found.")
        val observation = recalled.joinToString("\n") { memory ->
            "[memory_id=${memory.id}; confidence=${memory.confidence}] ${memory.content.take(MAX_MEMORY_CHARS)}"
        }
        return AgentCapabilityResult(true, observation, "已查找该角色的相关记忆")
    }

    private companion object {
        const val MAX_RECALLED_MEMORIES = 6
        const val MAX_MEMORY_CHARS = 480
        val RECALL_INTENT = Regex("还记得|记得我|我之前说|我告诉过|what did i tell|do you remember|remember when", RegexOption.IGNORE_CASE)
    }
}
