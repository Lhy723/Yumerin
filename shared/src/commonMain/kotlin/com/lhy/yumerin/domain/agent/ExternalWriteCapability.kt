package com.lhy.yumerin.domain.agent

import com.lhy.yumerin.domain.agent.AgentCapabilityContext
import com.lhy.yumerin.core.model.EntityId

/**
 * Typed contract for capability modules that write through external services
 * (ADR 0022, story 38). The runtime never calls [executeWrite] directly: it
 * first builds a preview via [prepareScope], waits for explicit user approval
 * of exactly that scope, and only then executes with the approved scope bound.
 */
interface ExternalWriteCapability : AgentCapability {
    /** Service this capability acts through. */
    val serviceKind: com.lhy.yumerin.core.model.ExternalServiceKind

    /** Write scopes this capability needs on the connection. */
    val requiredWriteScopes: Set<String>

    /**
     * Builds the user-facing scope from model arguments. Implementations must
     * fill every field the preview shows; communication actions state sender,
     * audience, and full content. Returns null when arguments are invalid.
     */
    suspend fun prepareScope(argumentsJson: String, context: AgentCapabilityContext): ExternalActionScope?

    /** Executes strictly within the approved scope and returns the outcome. */
    suspend fun executeWrite(approved: ApprovedExternalActionScope, context: AgentCapabilityContext): ExternalWriteOutcome

    /** Optional reliable status lookup for uncertain outcomes. */
    suspend fun checkStatus(approved: ApprovedExternalActionScope): ExternalWriteOutcome? = null
}

/** The complete scope a preview shows and an approval binds. */
data class ExternalActionScope(
    val serviceKind: com.lhy.yumerin.core.model.ExternalServiceKind,
    val capabilityName: String,
    val actionType: String,
    /** Service account label the write acts through. */
    val accountLabel: String,
    /** Target or audience of the write. */
    val target: String,
    /** Full content; communication previews show it verbatim. */
    val content: String,
    /** Allowed timing description; blank means on approval. */
    val timing: String = "",
    /** Impact or quantity bound the user sees. */
    val impactLimit: String = "",
    /** Field names the user may let the Agent vary within displayed bounds. */
    val flexibleFields: Set<String> = emptySet()
)

/** An [ExternalActionScope] the user explicitly approved; execution binds to it. */
data class ApprovedExternalActionScope(
    val scope: ExternalActionScope,
    val approvalId: EntityId,
    val approvedAtMillis: Long
)

sealed interface ExternalWriteOutcome {
    data class Succeeded(val observation: String) : ExternalWriteOutcome
    data class Failed(val observation: String) : ExternalWriteOutcome
    /** Service did not establish whether the write executed; never auto-retried. */
    data class Uncertain(val observation: String) : ExternalWriteOutcome
}
