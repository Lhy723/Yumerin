package com.lhy.yumerin.domain.usecase

import com.lhy.yumerin.core.model.*
import com.lhy.yumerin.domain.memory.MemoryPolicy
import com.lhy.yumerin.domain.repository.*
import com.lhy.yumerin.domain.safety.SafetyPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

sealed interface ProactiveMessageResult {
    data object Skipped : ProactiveMessageResult
    data object MissingModel : ProactiveMessageResult
    data class Sent(val messageId: EntityId) : ProactiveMessageResult
}

class SendProactiveMessageUseCase(
    private val characters: CharacterRepository,
    private val chats: ChatRepository,
    private val memories: MemoryRepository,
    private val models: ModelProfileRepository,
    private val schedules: CompanionScheduleRepository,
    private val agent: AgentService
) {
    suspend operator fun invoke(schedule: CompanionSchedule, settings: AppSettings): ProactiveMessageResult {
        if (!settings.proactiveMessagesEnabled || !schedule.enabled) return ProactiveMessageResult.Skipped
        val character = characters.getCharacter(schedule.characterId) ?: return ProactiveMessageResult.Skipped
        if (character.archived || !character.proactiveEnabled) return ProactiveMessageResult.Skipped
        // Use a platform-neutral UTC day bucket so the worker and this use case
        // enforce the same daily limit without depending on locale formatting.
        val now = useCaseNowMillis()
        val todayBucket = now / 86_400_000L
        val today = todayBucket.toString()
        val count = if (schedule.dailyCountStamp == today) schedule.dailyTriggerCount else 0
        val characterLimit = minOf(
            settings.dailyProactiveLimit.coerceAtLeast(1),
            character.proactiveDailyLimit.coerceAtLeast(1)
        )
        val characterDailyCount = chats.observeChats().first().sumOf { candidateChat ->
            chats.getMessages(candidateChat.id).count { message ->
                message.senderId == character.id &&
                    message.isProactive &&
                    message.timestamp >= todayBucket * 86_400_000L
            }
        }
        if (count >= characterLimit || characterDailyCount >= characterLimit) return ProactiveMessageResult.Skipped
        val chat = chats.getChat(schedule.chatId) ?: return ProactiveMessageResult.Skipped
        val modelId = character.modelProfileId ?: settings.defaultModelProfileId
        val model = modelId?.let { models.getProfile(it) }?.takeIf { it.enabled && it.deletedAt == null } ?: return ProactiveMessageResult.MissingModel
        val reply = agent.reply(AgentRequest(
            chat = chat, character = character, messages = chats.getMessages(chat.id),
            memories = memories.observeMemories(character.id).first(), model = model, proactivePrompt = schedule.prompt,
            visibility = settings.agentVisibility
        ))
        if (reply.statusMessage != null) return ProactiveMessageResult.Skipped
        val messageId = newEntityId("message")
        chats.appendMessage(ChatMessage(
            id = messageId, chatId = chat.id, senderId = character.id, senderType = MessageSenderType.CHARACTER,
            senderName = character.name, senderAvatarUri = character.avatarUri, text = reply.text, timestamp = useCaseNowMillis(),
            type = MessageType.TEXT, isProactive = true, isRead = false, deliveryState = MessageDeliveryState.SENT,
            responseState = AiResponseState.COMPLETED, usedMemoryIds = reply.usedMemoryIds
        ))
        schedules.saveSchedule(schedule.copy(lastTriggeredAt = now, dailyTriggerCount = count + 1, dailyCountStamp = today, updatedAt = useCaseNowMillis()))
        return ProactiveMessageResult.Sent(messageId)
    }
}
