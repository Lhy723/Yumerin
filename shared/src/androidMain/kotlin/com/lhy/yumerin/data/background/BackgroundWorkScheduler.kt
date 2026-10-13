package com.lhy.yumerin.data.background

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object BackgroundWorkScheduler {
    private const val WORK_NAME = "yumerin_companion_maintenance"
    private const val LOCAL_MODEL_PREFIX = "yumerin_local_model_"
    private const val AGENT_GOAL_PREFIX = "yumerin_agent_goal_"
    const val AGENT_GOAL_TAG = "yumerin_agent_goal"
    private const val DELEGATED_AGENT_GOAL_PREFIX = "yumerin_delegated_agent_goal_"
    const val DELEGATED_AGENT_GOAL_TAG = "yumerin_delegated_agent_goal"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<CompanionMaintenanceWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun scheduleAgentGoal(context: Context, goalId: String, dueAtMillis: Long) {
        val request = OneTimeWorkRequestBuilder<AgentGoalWorker>()
            .setInputData(workDataOf(AgentGoalWorker.KEY_GOAL_ID to goalId))
            .setInitialDelay((dueAtMillis - System.currentTimeMillis()).coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .addTag(AGENT_GOAL_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            AGENT_GOAL_PREFIX + goalId,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelAgentGoal(context: Context, goalId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(AGENT_GOAL_PREFIX + goalId)
    }

    fun cancelAgentGoals(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(AGENT_GOAL_TAG)
    }

    fun scheduleDelegatedAgentGoal(context: Context, goalId: String, append: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<DelegatedAgentGoalWorker>()
            .setInputData(workDataOf(DelegatedAgentGoalWorker.KEY_GOAL_ID to goalId))
            .addTag(DELEGATED_AGENT_GOAL_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            DELEGATED_AGENT_GOAL_PREFIX + goalId,
            if (append) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancelDelegatedAgentGoal(context: Context, goalId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(DELEGATED_AGENT_GOAL_PREFIX + goalId)
    }

    fun cancelDelegatedAgentGoals(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag(DELEGATED_AGENT_GOAL_TAG)
    }

    fun enqueueLocalModelDownload(context: Context, artifactId: String, allowMetered: Boolean = false) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (allowMetered) NetworkType.CONNECTED else NetworkType.UNMETERED)
            .build()
        val request = OneTimeWorkRequestBuilder<LocalModelDownloadWorker>()
            .setInputData(workDataOf(LocalModelDownloadWorker.KEY_ARTIFACT_ID to artifactId))
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            LOCAL_MODEL_PREFIX + artifactId,
            androidx.work.ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun pauseLocalModelDownload(context: Context, artifactId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(LOCAL_MODEL_PREFIX + artifactId)
    }

    fun cancelLocalModelDownload(context: Context, artifactId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(LOCAL_MODEL_PREFIX + artifactId)
    }
}
