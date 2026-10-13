package com.lhy.yumerin.data.background

import android.content.Context
import com.lhy.yumerin.core.model.AgentGoalStatus
import com.lhy.yumerin.domain.background.AgentGoalScheduler
import com.lhy.yumerin.domain.repository.AgentGoalRepository
import kotlinx.coroutines.flow.first

class AndroidAgentGoalScheduler(
    context: Context,
    private val goals: AgentGoalRepository
) : AgentGoalScheduler {
    private val appContext = context.applicationContext

    override suspend fun schedule(goalId: String, dueAtMillis: Long) {
        BackgroundWorkScheduler.scheduleAgentGoal(appContext, goalId, dueAtMillis)
    }

    override suspend fun cancel(goalId: String) {
        BackgroundWorkScheduler.cancelAgentGoal(appContext, goalId)
    }

    override suspend fun setEnabled(enabled: Boolean) {
        if (!enabled) {
            BackgroundWorkScheduler.cancelAgentGoals(appContext)
            return
        }
        goals.observeGoals().first()
            .filter { it.status == AgentGoalStatus.PENDING }
            .forEach { BackgroundWorkScheduler.scheduleAgentGoal(appContext, it.id, it.dueAt) }
    }
}
