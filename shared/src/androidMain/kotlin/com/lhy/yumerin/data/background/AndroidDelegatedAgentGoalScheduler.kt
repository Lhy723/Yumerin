package com.lhy.yumerin.data.background

import android.content.Context
import com.lhy.yumerin.core.model.DelegatedAgentGoalStatus
import com.lhy.yumerin.domain.background.DelegatedAgentGoalScheduler
import com.lhy.yumerin.domain.repository.DelegatedAgentGoalRepository
import kotlinx.coroutines.flow.first

class AndroidDelegatedAgentGoalScheduler(
    context: Context,
    private val goals: DelegatedAgentGoalRepository
) : DelegatedAgentGoalScheduler {
    private val appContext = context.applicationContext

    override suspend fun enqueue(goalId: String) {
        BackgroundWorkScheduler.scheduleDelegatedAgentGoal(appContext, goalId)
    }

    override suspend fun enqueueNext(goalId: String) {
        BackgroundWorkScheduler.scheduleDelegatedAgentGoal(appContext, goalId, append = true)
    }

    override suspend fun cancel(goalId: String) {
        BackgroundWorkScheduler.cancelDelegatedAgentGoal(appContext, goalId)
    }

    override suspend fun reconcile() {
        goals.observeGoals().first()
            .filter { it.status == DelegatedAgentGoalStatus.QUEUED || it.status == DelegatedAgentGoalStatus.RUNNING }
            .forEach { enqueue(it.id) }
    }

    override suspend fun cancelAll() {
        BackgroundWorkScheduler.cancelDelegatedAgentGoals(appContext)
    }
}
