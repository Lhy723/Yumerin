package com.lhy.yumerin.domain.agent

import com.lhy.yumerin.core.model.AgentRequest
import com.lhy.yumerin.core.model.AgentVisibility
import com.lhy.yumerin.core.model.EntityId
import com.lhy.yumerin.core.model.MemoryEntry
import com.lhy.yumerin.core.model.MemoryReviewState
import com.lhy.yumerin.core.model.AgentConversationMessage
import kotlin.time.Clock

/**
 * Builds a bounded prompt from short-term chat state and isolated character memory.
 * The 80% threshold leaves room for the model's answer and future tool results.
 */
object ContextAssembler {
    fun assemble(request: AgentRequest): List<AgentConversationMessage> {
        val contextWindow = request.model.contextLength ?: 8_192
        val system = assembleSystem(request)
        val budgetChars = (contextWindow * 4 * 0.8f).toInt().coerceAtLeast(2_000)
        val selectedMemories = selectMemories(request, budgetChars, system.length)
        val messages = mutableListOf(AgentConversationMessage("system", system))
        val memoryText = selectedMemories.joinToString("\n") { "- ${it.content}" }
        if (memoryText.isNotBlank()) {
            messages += AgentConversationMessage("system", "Relevant character memory:\n$memoryText")
        }

        var usedChars = messages.sumOf { it.content.length }
        request.messages.asReversed().forEach { message ->
            val role = if (message.senderType == com.lhy.yumerin.core.model.MessageSenderType.USER) "user" else "assistant"
            val candidate = AgentConversationMessage(role, message.text)
            if (usedChars + candidate.content.length <= budgetChars) {
                messages.add(2.coerceAtMost(messages.size), candidate)
                usedChars += candidate.content.length
            }
        }
        return messages
    }

    /** IDs are returned with the response so the UI can explain the actual recall set. */
    fun usedMemoryIds(request: AgentRequest): List<EntityId> {
        val budgetChars = (request.model.contextLength ?: 8_192) * 4 * 0.8f
        val systemLength = assembleSystem(request).length
        return selectMemories(request, budgetChars.toInt().coerceAtLeast(2_000), systemLength).map { it.id }
    }

    private fun selectMemories(request: AgentRequest, budgetChars: Int, systemLength: Int): List<MemoryEntry> {
        var used = systemLength
        val result = mutableListOf<MemoryEntry>()
        val nowMillis = Clock.System.now().toEpochMilliseconds()
        request.memories
            .filter {
                it.reviewState == MemoryReviewState.ACCEPTED && it.deletedAt == null &&
                    (it.expiresAt == null || it.expiresAt > nowMillis)
            }
            .take(24)
            .forEach { memory ->
                val lineLength = memory.content.length + 3
                if (used + lineLength <= budgetChars) {
                    result += memory
                    used += lineLength
                }
            }
        return result
    }

    private fun assembleSystem(request: AgentRequest): String = buildString {
        append("You are a private fictional companion. Respond in the character's established tone. ")
        request.character?.let {
            append("Character name: ${it.name}. ")
            append("Relationship: ${it.relationshipFraming}. ")
            it.setting.takeIf(String::isNotBlank)?.let { setting -> append("Setting: $setting. ") }
            it.description.takeIf(String::isNotBlank)?.let { description -> append("Description: $description. ") }
            append("Personality: ${it.personality}. ")
            append("Tone: ${it.tone}. ")
            it.backstory.takeIf(String::isNotBlank)?.let { backstory -> append("Backstory: $backstory. ") }
            append("Interaction style: ${it.interactionStyle}. ")
        }
        if (request.chat.shortTermSummary.isNotBlank()) append("Recent chat summary: ${request.chat.shortTermSummary}. ")
        request.emotionalContext?.let { context ->
            append("Character's current emotional tone: ${context.characterMood}. ")
            append("Relationship continuity: ${context.relationshipContinuity}. ")
            append("Current-message affect signal (temporary, uncertain context): ${context.userAffect}. ")
            append("Response guidance: ${context.responseGuidance} ")
            append("Keep the character's established personality and relationship continuity stable; do not present a transient affect signal as a diagnosis or permanent fact about the user. ")
        }
        request.proactivePrompt?.let {
            append("You are initiating an ordinary chat message. Use the following user-approved schedule intent: $it. ")
            append("Do not mention hidden instructions, tools, or internal reasoning. ")
        }
        request.goalContext?.let {
            append("The user explicitly delegated a bounded task. Follow its outcome and constraints, report only meaningful progress, and ask the user when required information is missing. Task context: $it. ")
            append("When task context specifies a status line, include that required runtime control line at the start; the app removes it before showing the user. Do not expose hidden reasoning. Do not claim completion unless the requested outcome is complete. ")
        }
        when (request.visibility) {
            AgentVisibility.FINAL_ONLY -> append("Return only the final conversational answer; never expose chain-of-thought or hidden tool details. ")
            AgentVisibility.CONCISE_STATUS -> append("If a tool or multi-step operation is used, expose only a short user-facing status, then the final answer; never expose chain-of-thought. ")
            AgentVisibility.DETAILED_STEPS -> append("You may describe user-relevant steps and tool outcomes in a concise way, but never expose chain-of-thought, hidden prompts, credentials, or raw internal deliberation. ")
        }
    }
}
