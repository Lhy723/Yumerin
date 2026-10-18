package com.lhy.yumerin.domain.persona

import com.lhy.yumerin.core.model.AgentReply
import com.lhy.yumerin.core.model.AgentRequest

/**
 * Persona-voice rewrite channel for run results produced under the neutral
 * executor layer.
 *
 * When a run uses capabilities, the model step loop works against a neutral
 * task framing for reliability; the raw answer it finally produces passes
 * through this channel so the persona layer can re-voice it. The default is
 * identity: callers that need re-voicing (for example delegated-goal reports)
 * plug a rewriting implementation at composition time.
 */
fun interface PersonaResultRewriter {
    suspend fun rewrite(
        request: AgentRequest,
        reply: AgentReply,
        hadCapabilityActivity: Boolean
    ): AgentReply

    companion object {
        /** Pass-through rewriter; keeps raw executor output unchanged. */
        val Identity = PersonaResultRewriter { _, reply, _ -> reply }
    }
}
