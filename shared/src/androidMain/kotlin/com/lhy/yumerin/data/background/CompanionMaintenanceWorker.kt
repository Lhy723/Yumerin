package com.lhy.yumerin.data.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lhy.yumerin.data.room.createAndroidAppGraph
import kotlinx.coroutines.flow.first
import java.util.Calendar
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class CompanionMaintenanceWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = createAndroidAppGraph(applicationContext)
        val settings = graph.settings.observeSettings().first()
        if (!settings.proactiveMessagesEnabled) return Result.success()
        val canNotify =
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (applicationContext.getSharedPreferences("background_companion", Context.MODE_PRIVATE)
                .getBoolean(AndroidBackgroundCompanionController.KEY_PAUSED, false)
        ) return Result.success()
        val calendar = Calendar.getInstance()
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val now = System.currentTimeMillis()
        // Expired memories are never silently deleted: they become reviewable again.
        graph.characters.observeCharacters().first().forEach { character ->
            graph.memories.observeMemories(character.id).first()
                .filter { it.expiresAt != null && it.expiresAt <= now && it.reviewState == com.lhy.yumerin.core.model.MemoryReviewState.ACCEPTED }
                .forEach { memory ->
                    graph.memories.saveMemory(
                        memory.copy(
                            reviewState = com.lhy.yumerin.core.model.MemoryReviewState.PENDING_REVIEW,
                            reviewAt = now,
                            reviewReason = "记忆已到期，请确认是否继续保留",
                            updatedAt = now
                        )
                    )
                }
        }
        val quiet = settings.quietTimeEnabled && isQuiet(
            currentMinutes,
            settings.quietStartMinutes,
            settings.quietEndMinutes
        )
        // Keep the same UTC day bucket as SendProactiveMessageUseCase.
        val dayStamp = (now / 86_400_000L).toString()
        graph.schedules.observeSchedules().first()
            .filter { it.enabled && it.hour * 60 + it.minute <= currentMinutes }
            .filter { schedule -> schedule.lastTriggeredAt?.let { !isSameLocalDay(it, now) } ?: true }
            .filter { it.dailyCountStamp != dayStamp || it.dailyTriggerCount < settings.dailyProactiveLimit }
            .forEach { schedule ->
                when (val result = graph.useCases.sendProactiveMessage(schedule, settings)) {
                    is com.lhy.yumerin.domain.usecase.ProactiveMessageResult.Sent -> {
                        val message = graph.chats.getMessages(schedule.chatId).lastOrNull { it.id == result.messageId }
                        val chat = graph.chats.getChat(schedule.chatId)
                        // Quiet time suppresses only the notification. The proactive
                        // message is still persisted and remains unread in the chat.
                        if (canNotify && !quiet && message != null && chat != null) {
                            CompanionNotifier.show(applicationContext, message, chat.muted)
                        }
                    }
                    else -> Unit
                }
            }
        return Result.success()
    }

    private fun isQuiet(current: Int, start: Int, end: Int): Boolean =
        if (start == end) false else if (start < end) current in start until end else current >= start || current < end

    private fun isSameLocalDay(firstMillis: Long, secondMillis: Long): Boolean {
        val first = Calendar.getInstance().apply { timeInMillis = firstMillis }
        val second = Calendar.getInstance().apply { timeInMillis = secondMillis }
        return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
            first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
    }
}
