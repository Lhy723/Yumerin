package com.lhy.yumerin.domain.usecase

import com.lhy.yumerin.core.model.EntityId
import com.lhy.yumerin.core.model.ExternalConnectionStatus
import com.lhy.yumerin.core.model.ExternalDataSharingConsent
import com.lhy.yumerin.core.model.ExternalServiceConnection
import com.lhy.yumerin.core.model.ExternalServiceKind
import com.lhy.yumerin.domain.agent.ExternalAuthorizationPolicy
import com.lhy.yumerin.domain.repository.ExternalAuthorizationRepository
import com.lhy.yumerin.domain.repository.PendingExternalActionRepository
import kotlinx.coroutines.flow.first

/**
 * Lifecycle use cases for the three independent authorization decisions
 * (ADR 0022): connect/revoke a service account, enable capabilities, and
 * grant/revoke the app-wide external-data sharing consent. Revocation stops
 * new access, clears enablement, and cancels that service's pending actions
 * while sanitized history is retained.
 */
class ManageExternalAuthorizationUseCase(
    private val authorizations: ExternalAuthorizationRepository,
    private val pendingActions: PendingExternalActionRepository,
    private val policy: ExternalAuthorizationPolicy = ExternalAuthorizationPolicy()
) {
    /** Connects one account after the user saw the scope explanation screen. */
    suspend fun connect(
        service: ExternalServiceKind,
        accountLabel: String,
        readScopes: Set<String>,
        writeScopes: Set<String>
    ): EntityId {
        val now = useCaseNowMillis()
        val connection = policy.freshConnection(service, accountLabel, readScopes, writeScopes, now)
        authorizations.saveConnection(connection)
        return connection.id
    }

    /** Enables one capability on a live connection (review requirement, story 5). */
    suspend fun enableCapability(connectionId: EntityId, capabilityName: String): Boolean {
        val connection = authorizations.getConnection(connectionId) ?: return false
        if (connection.status != ExternalConnectionStatus.CONNECTED) return false
        authorizations.saveConnection(
            connection.copy(enabledCapabilities = connection.enabledCapabilities + capabilityName, updatedAt = useCaseNowMillis())
        )
        return true
    }

    /**
     * Revokes one connection: stops new access, clears enablement, cancels its
     * pending actions, and keeps sanitized history (the connection row itself
     * with status DISCONNECTED and no secrets — credentials live elsewhere).
     */
    suspend fun revoke(connectionId: EntityId): Boolean {
        val connection = authorizations.getConnection(connectionId) ?: return false
        authorizations.saveConnection(policy.revoke(connection, useCaseNowMillis()))
        // Cancel this service's pending actions: they can no longer be approved
        // because the account they would act through is disconnected.
        val now = useCaseNowMillis()
        pendingActions.observeActions().first().filter {
            it.characterId.isNotBlank() && it.status == com.lhy.yumerin.core.model.PendingExternalActionStatus.PENDING
        }.forEach { action ->
            // Account-level match: pending actions carry the account label.
            if (action.serviceAccount == connection.accountLabel) {
                pendingActions.saveAction(
                    action.copy(
                        status = com.lhy.yumerin.core.model.PendingExternalActionStatus.EXPIRED,
                        resolvedAt = now,
                        resolutionNote = "服务连接已撤销"
                    )
                )
            }
        }
        return true
    }

    /** Grants the app-wide external-data sharing consent. */
    suspend fun grantDataSharingConsent() {
        authorizations.saveConsent(
            ExternalDataSharingConsent(granted = true, grantedAt = useCaseNowMillis())
        )
    }

    /** Revokes the app-wide consent; future remote requests stop receiving service data. */
    suspend fun revokeDataSharingConsent() {
        val now = useCaseNowMillis()
        val current = authorizations.getConsent()
        authorizations.saveConsent(
            current.copy(granted = false, revokedAt = now)
        )
    }

}
