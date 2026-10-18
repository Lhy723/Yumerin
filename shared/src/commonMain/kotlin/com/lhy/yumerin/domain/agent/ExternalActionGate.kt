package com.lhy.yumerin.domain.agent

import com.lhy.yumerin.domain.agent.AgentCapabilityContext
import com.lhy.yumerin.core.model.EntityId
import com.lhy.yumerin.core.model.ExternalActionPhase
import com.lhy.yumerin.core.model.ExternalServiceKind
import com.lhy.yumerin.core.model.PendingExternalAction

/** Why the runtime asked the gate to handle one external write. */
enum class ExternalActionRoute {
    /** A user-triggered run: suspend until the user approves or rejects. */
    FOREGROUND_AWAIT,
    /** A scheduled/background trigger: persist for in-app review only. */
    BACKGROUND_PERSIST
}

/** The decision a user reaches on one prepared scope. */
sealed interface ExternalActionDecision {
    data class Approved(val scope: com.lhy.yumerin.core.model.PendingExternalAction) : ExternalActionDecision
    data class Rejected(val reason: String?) : ExternalActionDecision
}

/**
 * The runtime's approval gateway for external writes (ADR 0022). The runtime
 * prepares a scope, hands it to the gate, and executes only inside a returned
 * approval; rejection becomes a denial observation that suppresses repeating
 * the same action in this run. Implementations own persistence, notifications,
 * and any foreground suspension — the runtime stays framework-free.
 */
interface ExternalActionGate {
    /** Decides the route for this run's trigger context. */
    fun routeFor(trigger: com.lhy.yumerin.core.model.AgentTrigger): ExternalActionRoute

    /**
     * Handles one prepared scope. Returns the observation text for the model
     * (approval+execution result, denial, or pending-saved notice) plus the
     * final phase for audit. Foreground implementations may suspend here.
     */
    suspend fun handle(
        scope: ExternalActionScope,
        capability: ExternalWriteCapability,
        context: AgentCapabilityContext,
        trigger: com.lhy.yumerin.core.model.AgentTrigger,
        runContext: ExternalRunContext
    ): ExternalActionHandling
}

data class ExternalRunContext(
    val runId: EntityId,
    val traceId: EntityId,
    val chatId: EntityId,
    val characterId: EntityId?
)

sealed interface ExternalActionHandling {
    data class Executed(val observation: String, val phase: ExternalActionPhase) : ExternalActionHandling
    data class Denied(val observation: String) : ExternalActionHandling
    data class SavedPending(val pendingId: EntityId, val observation: String) : ExternalActionHandling
    data class Blocked(val observation: String) : ExternalActionHandling
}

/**
 * Default gate: foreground runs are denied (no interactive approval surface is
 * wired into the runtime itself) and background runs persist a pending action
 * for in-app review. Compose the real gate at the composition root where the
 * approval UI can suspend the foreground path.
 */
class NoInteractiveExternalActionGate(
    private val pendingActions: com.lhy.yumerin.domain.repository.PendingExternalActionRepository,
    private val nowMillis: () -> Long
) : ExternalActionGate {
    override fun routeFor(trigger: com.lhy.yumerin.core.model.AgentTrigger): ExternalActionRoute =
        when (trigger) {
            com.lhy.yumerin.core.model.AgentTrigger.USER_MESSAGE,
            com.lhy.yumerin.core.model.AgentTrigger.RETRY -> ExternalActionRoute.FOREGROUND_AWAIT
            else -> ExternalActionRoute.BACKGROUND_PERSIST
        }

    override suspend fun handle(
        scope: ExternalActionScope,
        capability: ExternalWriteCapability,
        context: AgentCapabilityContext,
        trigger: com.lhy.yumerin.core.model.AgentTrigger,
        runContext: ExternalRunContext
    ): ExternalActionHandling = when (routeFor(trigger)) {
        ExternalActionRoute.FOREGROUND_AWAIT -> ExternalActionHandling.Denied(
            "This action needs your approval in the app; approval inside a running chat is not available yet, so it was not executed."
        )
        ExternalActionRoute.BACKGROUND_PERSIST -> {
            val now = nowMillis()
            val action = PendingExternalAction(
                id = EntityIdGenerator.newId("pending-action"),
                goalId = null,
                chatId = context.chatId,
                characterId = context.characterId ?: "",
                serviceAccount = scope.accountLabel,
                actionType = scope.actionType,
                target = scope.target,
                content = scope.content,
                timing = scope.timing,
                impactLimit = scope.impactLimit,
                flexibleFields = scope.flexibleFields.toList(),
                createdAt = now,
                expiresAt = now + com.lhy.yumerin.core.model.PendingExternalActionRules.TTL_MILLIS
            )
            pendingActions.saveAction(action)
            ExternalActionHandling.SavedPending(
                pendingId = action.id,
                observation = "The action was saved for the user's review in Yumerin and has not executed. It expires in 24 hours; rejecting or letting it expire cancels the linked task."
            )
        }
    }
}
