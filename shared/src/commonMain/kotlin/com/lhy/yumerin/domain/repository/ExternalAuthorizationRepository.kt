package com.lhy.yumerin.domain.repository

import com.lhy.yumerin.core.model.EntityId
import com.lhy.yumerin.core.model.ExternalDataSharingConsent
import com.lhy.yumerin.core.model.ExternalServiceConnection
import kotlinx.coroutines.flow.Flow

/**
 * Storage for the three independent authorization decisions (ADR 0022):
 * service connections (with per-capability enablement) and the app-wide
 * external-data sharing consent. Secrets never live here — credentials stay
 * in platform-protected storage behind CredentialStore.
 */
interface ExternalAuthorizationRepository {
    fun observeConnections(): Flow<List<ExternalServiceConnection>>
    suspend fun getConnection(id: EntityId): ExternalServiceConnection?
    suspend fun getConnections(): List<ExternalServiceConnection>
    suspend fun saveConnection(connection: ExternalServiceConnection)

    fun observeConsent(): Flow<ExternalDataSharingConsent>
    suspend fun getConsent(): ExternalDataSharingConsent
    suspend fun saveConsent(consent: ExternalDataSharingConsent)
}
