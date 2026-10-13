package com.lhy.yumerin.data.background

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lhy.yumerin.data.room.createAndroidAppGraph
import com.lhy.yumerin.domain.usecase.ProcessDelegatedAgentGoalResult
import kotlinx.coroutines.flow.first
import java.util.Calendar

class DelegatedAgentGoalWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val goalId = inputData.getString(KEY_GOAL_ID) ?: return Result.failure()
        val graph = createAndroidAppGraph(applicationContext)
        return when (val result = graph.useCases.processDelegatedAgentGoal(goalId)) {
            is ProcessDelegatedAgentGoalResult.Progressed -> {
                result.messageId?.let { notifyGoalMessage(graph, goalId, it) }
                runCatching { graph.delegatedAgentGoalScheduler.enqueueNext(goalId) }
                Result.success()
            }
            is ProcessDelegatedAgentGoalResult.WaitingForUser -> {
                notifyGoalMessage(graph, goalId, result.messageId)
                Result.success()
            }
            is ProcessDelegatedAgentGoalResult.Completed -> {
                notifyGoalMessage(graph, goalId, result.messageId)
                Result.success()
            }
            ProcessDelegatedAgentGoalResult.Retry -> {
                if (runAttemptCount >= MAX_RETRIES) {
                    graph.useCases.processDelegatedAgentGoal.markFailed(goalId, "连续重试仍未能完成当前步骤")
                    Result.success()
                } else Result.retry()
            }
            ProcessDelegatedAgentGoalResult.Paused,
            ProcessDelegatedAgentGoalResult.Skipped,
            is ProcessDelegatedAgentGoalResult.Failed -> Result.success()
        }
    }

    private suspend fun notifyGoalMessage(graph: com.lhy.yumerin.domain.AppGraph, goalId: String, messageId: String) {
        val goal = graph.delegatedAgentGoals.getGoal(goalId) ?: return
        val message = graph.chats.getMessages(goal.chatId).firstOrNull { it.id == messageId } ?: return
        val chat = graph.chats.getChat(goal.chatId) ?: return
        val settings = graph.settings.observeSettings().first()
        val canNotify = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val quiet = settings.quietTimeEnabled && isQuiet(currentMinutes, settings.quietStartMinutes, settings.quietEndMinutes)
        if (canNotify && !quiet) CompanionNotifier.show(applicationContext, message, chat.muted)
    }

    private fun isQuiet(current: Int, start: Int, end: Int): Boolean =
        if (start == end) false else if (start < end) current in start until end else current >= start || current < end

    companion object {
        const val KEY_GOAL_ID = "delegated_agent_goal_id"
        private const val MAX_RETRIES = 3
    }
}
