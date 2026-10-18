package com.lhy.yumerin.data.memory

import com.lhy.yumerin.core.model.EntityId
import com.lhy.yumerin.core.model.ExternalDataSharingConsent
import com.lhy.yumerin.core.model.ExternalServiceConnection
import com.lhy.yumerin.domain.repository.ExternalAuthorizationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class InMemoryExternalAuthorizationRepository : ExternalAuthorizationRepository {
    private val connections = MutableStateFlow<List<ExternalServiceConnection>>(emptyList())
    private val consent = MutableStateFlow(ExternalDataSharingConsent(granted = false))

    override fun observeConnections(): Flow<List<ExternalServiceConnection>> = connections
    override suspend fun getConnection(id: EntityId): ExternalServiceConnection? =
        connections.value.firstOrNull { it.id == id }
    override suspend fun getConnections(): List<ExternalServiceConnection> = connections.value
    override suspend fun saveConnection(connection: ExternalServiceConnection) {
        connections.update { current -> current.filterNot { it.id == connection.id } + connection }
    }

    override fun observeConsent(): Flow<ExternalDataSharingConsent> = consent
    override suspend fun getConsent(): ExternalDataSharingConsent = consent.value
    override suspend fun saveConsent(value: ExternalDataSharingConsent) {
        consent.value = value
    }
}
