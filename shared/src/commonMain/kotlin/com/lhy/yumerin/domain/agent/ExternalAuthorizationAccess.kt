package com.lhy.yumerin.domain.agent

import com.lhy.yumerin.core.model.EntityId
import com.lhy.yumerin.core.model.ExternalServiceConnection
import com.lhy.yumerin.core.model.ExternalServiceKind

/**
 * The runtime's narrow view of external authorization state (ADR 0022).
 * Implemented over the authorization repository at composition time; the
 * runtime only asks whether a live connection covers a capability.
 */
interface ExternalAuthorizationAccess {
    suspend fun getConnection(service: ExternalServiceKind): ExternalServiceConnection?

    /** Latest account label for the service, for audit fields on pending actions. */
    suspend fun accountLabel(service: ExternalServiceKind): String?
}
