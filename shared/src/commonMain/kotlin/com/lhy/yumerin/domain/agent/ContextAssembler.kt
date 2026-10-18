package com.lhy.yumerin.domain.agent

import com.lhy.yumerin.core.model.AgentConversationMessage
import com.lhy.yumerin.core.model.AgentRequest
import com.lhy.yumerin.core.model.EntityId
import com.lhy.yumerin.domain.persona.PromptAssembler

/**
 * Compatibility facade over the slot-based prompt pipeline. New callers should
 * use [PromptAssembler] directly (persona vs executor layer, budgets, world
 * info); this object keeps the legacy one-call surface until adapters migrate.
 */
object ContextAssembler {
    fun assemble(request: AgentRequest): List<AgentConversationMessage> =
        PromptAssembler.assemble(request, PromptAssembler.Layer.PERSONA)

    /** IDs are returned with the response so the UI can explain the actual recall set. */
    fun usedMemoryIds(request: AgentRequest): List<EntityId> = PromptAssembler.usedMemoryIds(request)
}
