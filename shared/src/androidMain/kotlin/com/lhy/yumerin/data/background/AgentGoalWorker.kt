package com.lhy.yumerin.data.background

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lhy.yumerin.data.room.createAndroidAppGraph
import com.lhy.yumerin.domain.usecase.ExecuteAgentGoalResult
import kotlinx.coroutines.flow.first
import java.util.Calendar

class AgentGoalWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val goalId = inputData.getString(KEY_GOAL_ID) ?: return Result.failure()
        val graph = createAndroidAppGraph(applicationContext)
        val settings = graph.settings.observeSettings().first()
        return when (val result = graph.useCases.executeAgentGoal(goalId)) {
            is ExecuteAgentGoalResult.Completed -> {
                val goal = graph.agentGoals.getGoal(goalId)
                val chat = goal?.let { graph.chats.getChat(it.chatId) }
                val message = goal?.let { graph.chats.getMessages(it.chatId).firstOrNull { msg -> msg.id == result.messageId } }
                val canNotify = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                val now = Calendar.getInstance()
                val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                val quiet = settings.quietTimeEnabled && isQuiet(currentMinutes, settings.quietStartMinutes, settings.quietEndMinutes)
                if (canNotify && !quiet && message != null && chat != null) {
                    CompanionNotifier.show(applicationContext, message, chat.muted)
                }
                Result.success()
            }
            ExecuteAgentGoalResult.Retry -> Result.retry()
            ExecuteAgentGoalResult.Paused,
            ExecuteAgentGoalResult.Skipped,
            is ExecuteAgentGoalResult.Failed -> Result.success()
        }
    }

    private fun isQuiet(current: Int, start: Int, end: Int): Boolean =
        if (start == end) false else if (start < end) current in start until end else current >= start || current < end

    companion object {
        const val KEY_GOAL_ID = "agent_goal_id"
    }
}
