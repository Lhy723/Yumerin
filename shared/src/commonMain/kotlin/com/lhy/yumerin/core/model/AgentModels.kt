package com.lhy.yumerin.core.model

data class AgentRequest(
    val chat: ChatThread,
    val character: CharacterProfile?,
    val messages: List<ChatMessage>,
    val memories: List<MemoryEntry>,
    val model: ModelProfile,
    val proactivePrompt: String? = null,
    val streaming: Boolean = false,
    val visibility: AgentVisibility = AgentVisibility.FINAL_ONLY
)

data class AgentReply(
    val text: String,
    val modelProfileId: EntityId,
    val usedMemoryIds: List<EntityId> = emptyList(),
    val statusMessage: String? = null
)

interface AgentService {
    suspend fun reply(request: AgentRequest): AgentReply

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
