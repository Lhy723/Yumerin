package com.lhy.yumerin.domain.usecase

import com.lhy.yumerin.core.model.*
import com.lhy.yumerin.domain.agent.AgentRuntime
import com.lhy.yumerin.domain.agent.CharacterMemoryRetriever
import com.lhy.yumerin.domain.agent.CharacterStateCoordinator
import com.lhy.yumerin.domain.repository.*
import kotlinx.coroutines.flow.first

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
    private val agentRuntime: AgentRuntime,
    private val characterStateCoordinator: CharacterStateCoordinator,
    private val proactiveAdmissionPolicy: ProactiveAdmissionPolicy
) {
    suspend operator fun invoke(schedule: CompanionSchedule, settings: AppSettings): ProactiveMessageResult {
        if (!schedule.enabled) return ProactiveMessageResult.Skipped
        val character = characters.getCharacter(schedule.characterId) ?: return ProactiveMessageResult.Skipped
        if (character.archived) return ProactiveMessageResult.Skipped
        // Use a platform-neutral UTC day bucket so the worker and this use case
        // enforce the same daily limit without depending on locale formatting.
        val now = useCaseNowMillis()
        val todayBucket = now / 86_400_000L
        val today = todayBucket.toString()
        val count = if (schedule.dailyCountStamp == today) schedule.dailyTriggerCount else 0
        val scheduleLimit = minOf(settings.dailyProactiveLimit.coerceAtLeast(1), character.proactiveDailyLimit.coerceAtLeast(1))
        if (count >= scheduleLimit) return ProactiveMessageResult.Skipped
        if (!proactiveAdmissionPolicy.isAllowed(character, settings, now, requireCharacterOptIn = true)) return ProactiveMessageResult.Skipped
        val chat = chats.getChat(schedule.chatId) ?: return ProactiveMessageResult.Skipped
        val modelId = character.modelProfileId ?: settings.defaultModelProfileId
        val model = modelId?.let { models.getProfile(it) }?.takeIf { it.enabled && it.deletedAt == null } ?: return ProactiveMessageResult.MissingModel
        val turnTime = useCaseNowMillis()
        val preparedState = characterStateCoordinator.prepare(
            characterId = character.id,
            chatId = chat.id,
            sourceMessageId = null,
            userText = null,
            nowMillis = turnTime
        )
        val reply = agentRuntime.run(
            AgentRequest(
                chat = chat, character = character, messages = chats.getMessages(chat.id),
                memories = CharacterMemoryRetriever.retrieve(
                    memories.observeMemories(character.id).first(), schedule.prompt, turnTime
                ),
                model = model, proactivePrompt = schedule.prompt,
                visibility = settings.agentVisibility,
                emotionalContext = preparedState.emotionalContext
            ),
            trigger = AgentTrigger.SCHEDULED_PROACTIVE
        )
        if (reply.statusMessage != null) return ProactiveMessageResult.Skipped
        val messageId = newEntityId("message")
        chats.appendMessage(ChatMessage(
            id = messageId, chatId = chat.id, senderId = character.id, senderType = MessageSenderType.CHARACTER,
            senderName = character.name, senderAvatarUri = character.avatarUri, text = reply.text, timestamp = useCaseNowMillis(),
            type = MessageType.TEXT, isProactive = true, isRead = false, deliveryState = MessageDeliveryState.SENT,
            responseState = AiResponseState.COMPLETED, usedMemoryIds = reply.usedMemoryIds
        ))
        characterStateCoordinator.commit(preparedState, useCaseNowMillis(), completedMessageId = messageId)
        schedules.saveSchedule(schedule.copy(lastTriggeredAt = now, dailyTriggerCount = count + 1, dailyCountStamp = today, updatedAt = useCaseNowMillis()))
        return ProactiveMessageResult.Sent(messageId)
    }
}

class ProactiveAdmissionPolicy(private val chats: ChatRepository) {
    suspend fun isAllowed(
        character: CharacterProfile,
        settings: AppSettings,
        nowMillis: Long,
        requireCharacterOptIn: Boolean
    ): Boolean {
        if (!settings.proactiveMessagesEnabled || character.archived || (requireCharacterOptIn && !character.proactiveEnabled)) return false
        val dailyLimit = minOf(
            settings.dailyProactiveLimit.coerceAtLeast(1),
            character.proactiveDailyLimit.coerceAtLeast(1)
        )
        val dayStartUtc = (nowMillis / 86_400_000L) * 86_400_000L
        val dailyCount = chats.observeChats().first().sumOf { candidateChat ->
            chats.getMessages(candidateChat.id).count { message ->
                message.senderId == character.id && message.isProactive && message.timestamp >= dayStartUtc
            }
        }
        return dailyCount < dailyLimit
    }
}
