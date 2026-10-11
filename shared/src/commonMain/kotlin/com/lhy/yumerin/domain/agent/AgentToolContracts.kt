package com.lhy.yumerin.domain.agent

/**
 * Provider-neutral tool boundary. V1 only uses the memory step implemented by
 * ContextAssembler; network search and outbound-platform actions deliberately
 * remain unavailable until a separately consented capability is added.
 */
enum class AgentToolKind {
    MEMORY_RECALL,
    WEB_SEARCH_PLACEHOLDER,
    EXTERNAL_SEND_PLACEHOLDER
}

data class AgentToolCall(
    val kind: AgentToolKind,
    val input: String
)

data class AgentToolResult(
    val completed: Boolean,
    val userFacingSummary: String? = null,
    val payload: String? = null
)

interface AgentToolGateway {
    suspend fun execute(call: AgentToolCall): AgentToolResult
}

object DisabledAgentToolGateway : AgentToolGateway {
    override suspend fun execute(call: AgentToolCall): AgentToolResult = AgentToolResult(
        completed = false,
        userFacingSummary = "该工具暂未启用。"
    )
}
