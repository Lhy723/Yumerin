package com.lhy.yumerin.core.model

/** A service identity an external capability can act through. */
enum class ExternalServiceKind {
    GENERIC_PROVIDER
}

enum class ExternalConnectionStatus {
    CONNECTED,
    DISCONNECTED
}

/**
 * One connected account at one external service (ADR 0022). Read and write
 * scopes are requested together at connection time; capability enablement is
 * tracked separately so a newly added capability stays unavailable until the
 * user reviews it, even when existing scopes already cover it.
 */
data class ExternalServiceConnection(
    val id: EntityId,
    val service: ExternalServiceKind,
    /** Service-side account label shown in previews and audit history. */
    val accountLabel: String,
    val readScopes: Set<String> = emptySet(),
    val writeScopes: Set<String> = emptySet(),
    /** Capability names the user has reviewed and enabled on this connection. */
    val enabledCapabilities: Set<String> = emptySet(),
    val status: ExternalConnectionStatus = ExternalConnectionStatus.CONNECTED,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * The app-wide, revocable consent that allows the minimum relevant
 * connected-service data to reach remote model endpoints. Local inference
 * that keeps data on device does not need it. Revocation blocks future
 * sharing and cancels in-progress runs that depend on the data; prepared
 * Pending external actions remain separately reviewable.
 */
data class ExternalDataSharingConsent(
    val granted: Boolean,
    val grantedAt: Long? = null,
    val revokedAt: Long? = null
)

/** Hard-blocked action types that no connection or approval can enable (ADR 0022). */
object ExternalActionBlocklist {
    val ACTION_TYPES = setOf(
        "permanent_deletion",
        "payment",
        "transfer",
        "account_security_change"
    )

    fun isBlocked(actionType: String): Boolean = actionType.lowercase() in ACTION_TYPES
}

enum class ExternalActionPhase {
    PREPARED,
    PENDING,
    APPROVED,
    EXECUTING,
    SUCCEEDED,
    FAILED,
    UNCERTAIN,
    REJECTED,
    EXPIRED,
    CANCELLED
}
