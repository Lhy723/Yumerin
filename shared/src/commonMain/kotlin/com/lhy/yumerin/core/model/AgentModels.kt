package com.lhy.yumerin.core.model

data class AgentRequest(
    val chat: ChatThread,
    val character: CharacterProfile?,
    val messages: List<ChatMessage>,
    val memories: List<MemoryEntry>,
    val model: ModelProfile,
    val proactivePrompt: String? = null,
    /** Private task context for a delegated goal; kept separate from proactive message instructions. */
    val goalContext: String? = null,
    val streaming: Boolean = false,
    val visibility: AgentVisibility = AgentVisibility.FINAL_ONLY,
    val emotionalContext: AgentEmotionalContext? = null
)

/** Prompt-ready, transient guidance assembled from character state and this turn's appraisal. */
data class AgentEmotionalContext(
    val characterMood: String,
    val relationshipContinuity: String,
    val userAffect: String,
    val responseGuidance: String
)

enum class AgentTrigger {
    USER_MESSAGE,
    RETRY,
    SCHEDULED_PROACTIVE,
    SCHEDULED_GOAL,
    DELEGATED_GOAL
}

data class AgentReply(
    val text: String,
    val modelProfileId: EntityId,
    val usedMemoryIds: List<EntityId> = emptyList(),
    val statusMessage: String? = null
)

/** A provider-neutral message used while a bounded Agent run invokes capabilities. */
data class AgentConversationMessage(
    val role: String,
    val content: String,
    val name: String? = null,
    val capabilityCallId: String? = null,
    val capabilityCalls: List<AgentCapabilityCall> = emptyList()
)

data class AgentCapabilityCall(
    val id: String,
    val name: String,
    val argumentsJson: String
)

enum class AgentCapabilityEffect {
    READ_ONLY,
    LOCAL_MUTATION,
    EXTERNAL_SIDE_EFFECT
}

/** Description sent to model adapters; execution remains in domain capability modules. */
data class AgentCapabilityDescriptor(
    val name: String,
    val description: String,
    val parametersJsonSchema: String,
    val effect: AgentCapabilityEffect = AgentCapabilityEffect.READ_ONLY,
    val requiredPermissions: Set<String> = emptySet(),
    val requiresUserConfirmation: Boolean = false,
    /** Relative units used by the runtime budget; one unit is the default low-cost operation. */
    val budgetCostUnits: Int = 1
)

sealed interface AgentModelStep {
    data class FinalResponse(val reply: AgentReply) : AgentModelStep
    data class CapabilityRequests(
        val calls: List<AgentCapabilityCall>,
        val assistantText: String? = null
    ) : AgentModelStep
}

interface AgentService {
    suspend fun reply(request: AgentRequest): AgentReply

    /**
     * Requests one bounded model step. Existing providers keep their one-shot
     * behavior through this default; tool-aware adapters may return capability
     * requests for the domain runtime to validate and execute.
     */
    suspend fun nextStep(
        request: AgentRequest,
        conversation: List<AgentConversationMessage>,
        capabilities: List<AgentCapabilityDescriptor>,
        onAccumulatedText: suspend (String) -> Unit
    ): AgentModelStep = AgentModelStep.FinalResponse(stream(request, onAccumulatedText))

    /**
     * Emits the accumulated answer so the presentation layer can render a single
     * stable message while the provider is producing output. Providers that do
     * not expose token streaming still get a correct one-shot fallback.
     */
    suspend fun stream(request: AgentRequest, onAccumulatedText: suspend (String) -> Unit): AgentReply {
        val reply = reply(request)
        if (reply.text.isNotBlank()) onAccumulatedText(reply.text)
        return reply
    }
}
