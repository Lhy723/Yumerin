package com.lhy.yumerin.domain.agent

import com.lhy.yumerin.core.model.EntityId
import com.lhy.yumerin.core.model.ExternalActionBlocklist
import com.lhy.yumerin.core.model.ExternalConnectionStatus
import com.lhy.yumerin.core.model.ExternalDataSharingConsent
import com.lhy.yumerin.core.model.ExternalServiceConnection
import com.lhy.yumerin.core.model.ExternalServiceKind

/**
 * Domain policy for the four independent authorization decisions (ADR 0022):
 * service connection, per-capability enablement, app-wide data-sharing
 * consent, and per-action approval. Pure decisions — storage and I/O stay in
 * repositories and adapters.
 */
class ExternalAuthorizationPolicy {

    /** A read is available when its capability is enabled on a live connection covering the scopes. */
    fun isReadAvailable(
        connection: ExternalServiceConnection?,
        capabilityName: String,
        requiredReadScopes: Set<String>
    ): Boolean {
        val live = connection?.takeIf { it.status == ExternalConnectionStatus.CONNECTED } ?: return false
        if (capabilityName !in live.enabledCapabilities) return false
        return live.readScopes.containsAll(requiredReadScopes)
    }

    /**
     * A write can be *requested* when the capability is enabled on a live
     * connection whose write scopes cover the requirement; execution still
     * requires per-action approval. Hard-blocked action types never even
     * produce a preview.
     */
    fun isWriteRequestable(
        connection: ExternalServiceConnection?,
        capabilityName: String,
        requiredWriteScopes: Set<String>
    ): Boolean {
        val live = connection?.takeIf { it.status == ExternalConnectionStatus.CONNECTED } ?: return false
        if (capabilityName !in live.enabledCapabilities) return false
        return live.writeScopes.containsAll(requiredWriteScopes)
    }

    /** Consent gate for sending connected-service data to any remote model. */
    fun isRemoteSharingAllowed(consent: ExternalDataSharingConsent): Boolean =
        consent.granted && consent.revokedAt == null

    /**
     * Compares a candidate scope against an approved one. Returns null when
     * execution may proceed; otherwise the first violated field name. Values
     * outside explicitly flexible fields must match exactly; flexible fields
     * only allow the field to be present, bounds stay with the preview text.
     */
    fun scopeViolation(approved: ExternalActionScope, candidate: ExternalActionScope): String? = when {
        approved.serviceKind != candidate.serviceKind -> "service"
        approved.capabilityName != candidate.capabilityName -> "capability"
        approved.actionType != candidate.actionType -> "actionType"
        approved.accountLabel != candidate.accountLabel -> "account"
        approved.target != candidate.target -> "target"
        approved.content != candidate.content -> "content"
        approved.timing != candidate.timing -> "timing"
        approved.impactLimit != candidate.impactLimit -> "impact"
        else -> null
    }

    /** Connection revocation cleanup per ADR 0022 story 8. */
    fun revoke(
        connection: ExternalServiceConnection,
        nowMillis: Long
    ): ExternalServiceConnection = connection.copy(
        status = ExternalConnectionStatus.DISCONNECTED,
        enabledCapabilities = emptySet(),
        updatedAt = nowMillis
    )

    /** Reconnection always requires fresh authorization; metadata never silently resumes. */
    fun freshConnection(
        service: ExternalServiceKind,
        accountLabel: String,
        readScopes: Set<String>,
        writeScopes: Set<String>,
        nowMillis: Long
    ): ExternalServiceConnection = ExternalServiceConnection(
        id = EntityIdGenerator.newId("connection"),
        service = service,
        accountLabel = accountLabel,
        readScopes = readScopes,
        writeScopes = writeScopes,
        enabledCapabilities = emptySet(),
        createdAt = nowMillis,
        updatedAt = nowMillis
    )

    /** Blocked action types cannot be previewed, approved, or enabled. */
    fun isHardBlocked(scope: ExternalActionScope): Boolean = ExternalActionBlocklist.isBlocked(scope.actionType)
}

internal object EntityIdGenerator {
    fun newId(prefix: String): EntityId = "$prefix-${kotlin.random.Random.nextLong().toString(16)}"
}
