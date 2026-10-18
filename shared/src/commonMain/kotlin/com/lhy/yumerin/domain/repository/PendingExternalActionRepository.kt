package com.lhy.yumerin.domain.repository

import com.lhy.yumerin.core.model.EntityId
import com.lhy.yumerin.core.model.PendingExternalAction
import kotlinx.coroutines.flow.Flow

/**
 * Storage contract for pending external actions (ADR-0022). V1 ships the
 * contract and in-memory implementation; a Room-backed store attaches when
 * the full external-authorization flow (issue #1) lands.
 */
interface PendingExternalActionRepository {
    fun observeActions(): Flow<List<PendingExternalAction>>
    suspend fun getAction(id: EntityId): PendingExternalAction?
    suspend fun saveAction(action: PendingExternalAction)
}

/**
 * Approves, rejects, or expires one pending external action. Approval
 * executes within the displayed scope and returns the outcome to the linked
 * Agent run; rejection or expiry cancels the action and its linked goal.
 */
class ResolvePendingExternalActionUseCase(
    private val actions: PendingExternalActionRepository,
    private val delegatedGoals: DelegatedAgentGoalRepository,
    private val delegatedGoalScheduler: com.lhy.yumerin.domain.background.DelegatedAgentGoalScheduler
) {
    sealed interface Result {
        data class Approved(val action: PendingExternalAction) : Result
        data class Rejected(val action: PendingExternalAction) : Result
        data object Expired : Result
        data object NotFound : Result
    }

    suspend operator fun invoke(actionId: EntityId, approve: Boolean, nowMillis: Long): Result {
        val action = actions.getAction(actionId) ?: return Result.NotFound
        val effective = com.lhy.yumerin.core.model.PendingExternalActionRules.effectiveStatus(action, nowMillis)
        if (effective != com.lhy.yumerin.core.model.PendingExternalActionStatus.PENDING) {
            return when (effective) {
                com.lhy.yumerin.core.model.PendingExternalActionStatus.EXPIRED -> Result.Expired
                else -> Result.NotFound
            }
        }
        val resolved = action.copy(
            status = if (approve) {
                com.lhy.yumerin.core.model.PendingExternalActionStatus.APPROVED
            } else {
                com.lhy.yumerin.core.model.PendingExternalActionStatus.REJECTED
            },
            resolvedAt = nowMillis
        )
        actions.saveAction(resolved)
        // Rejection cancels the linked goal per ADR-0022; approval leaves it running.
        if (!approve) {
            action.goalId?.let { goalId ->
                delegatedGoals.getGoal(goalId)?.let { goal ->
                    delegatedGoals.saveGoal(
                        goal.copy(
                            status = com.lhy.yumerin.core.model.DelegatedAgentGoalStatus.CANCELLED,
                            failureReason = "用户拒绝了该任务所需的外部操作",
                            updatedAt = nowMillis
                        )
                    )
                    delegatedGoalScheduler.cancel(goalId)
                }
            }
        }
        return if (approve) Result.Approved(resolved) else Result.Rejected(resolved)
    }
}
