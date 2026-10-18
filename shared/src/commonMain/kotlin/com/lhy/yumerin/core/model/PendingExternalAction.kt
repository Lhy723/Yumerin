package com.lhy.yumerin.core.model

/**
 * One external write an Agent run requested and the user has not approved
 * yet. ADR-0022 semantics in minimal form: the action executes only after the
 * user approves its displayed scope, expires 24 hours after creation
 * (expiry or rejection also cancels the linked goal), and retries are allowed
 * only inside the approved scope after the service confirms non-execution.
 *
 * V1 carries no executor: the model below, its repository contract, and the
 * approval use case land the boundary so capability modules and the approval
 * UI can attach without re-shaping the domain.
 */
data class PendingExternalAction(
    val id: EntityId,
    val goalId: EntityId?,
    val chatId: EntityId,
    val characterId: EntityId,
    /** Service account the write would act on, shown before approval. */
    val serviceAccount: String,
    /** Action type, e.g. "send_message", "create_document". */
    val actionType: String,
    /** Target or audience of the write. */
    val target: String,
    /** Full content that would be written; communication previews show it verbatim. */
    val content: String,
    /** Timing constraint the user sees; empty means immediately on approval. */
    val timing: String = "",
    /** Impact or quantity bound the user sees. */
    val impactLimit: String = "",
    /** Fields the user explicitly marked as flexible within displayed bounds. */
    val flexibleFields: List<String> = emptyList(),
    val status: PendingExternalActionStatus = PendingExternalActionStatus.PENDING,
    val createdAt: Long,
    val expiresAt: Long,
    val resolvedAt: Long? = null,
    val resolutionNote: String? = null
)

enum class PendingExternalActionStatus {
    PENDING,
    APPROVED,
    REJECTED,
    EXPIRED
}

/** Evaluates expiry for pending actions; pure so workers and UI share it. */
object PendingExternalActionRules {
    const val TTL_MILLIS = 24L * 60 * 60 * 1_000

    fun effectiveStatus(action: PendingExternalAction, nowMillis: Long): PendingExternalActionStatus = when {
        action.status != PendingExternalActionStatus.PENDING -> action.status
        nowMillis >= action.expiresAt -> PendingExternalActionStatus.EXPIRED
        else -> action.status
    }
}
