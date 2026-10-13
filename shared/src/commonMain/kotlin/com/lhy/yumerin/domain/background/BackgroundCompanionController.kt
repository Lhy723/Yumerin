package com.lhy.yumerin.domain.background

interface BackgroundCompanionController {
    suspend fun setEnabled(enabled: Boolean)
    suspend fun stopAll()
    fun isEnabled(): Boolean
}

/** Work scheduling boundary for user-created, one-shot Agent goals. */
interface AgentGoalScheduler {
    suspend fun schedule(goalId: String, dueAtMillis: Long)
    suspend fun cancel(goalId: String)
    suspend fun setEnabled(enabled: Boolean)
}

/** Work boundary for explicitly delegated, resumable goals; independent of proactive messaging. */
interface DelegatedAgentGoalScheduler {
    suspend fun enqueue(goalId: String)
    suspend fun enqueueNext(goalId: String)
    suspend fun cancel(goalId: String)
    suspend fun reconcile()
    suspend fun cancelAll()
}
