package com.lhy.yumerin.domain.usecase

import com.lhy.yumerin.core.model.*
import com.lhy.yumerin.domain.agent.AgentRuntime
import com.lhy.yumerin.domain.agent.CharacterMemoryRetriever
import com.lhy.yumerin.domain.agent.CharacterStateCoordinator
import com.lhy.yumerin.domain.background.AgentGoalScheduler
import com.lhy.yumerin.domain.repository.*
import com.lhy.yumerin.domain.safety.SafetyPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ObserveScheduledAgentGoalsUseCase(
    private val goals: AgentGoalRepository
) {
    operator fun invoke(): Flow<List<ScheduledAgentGoal>> = goals.observeGoals()
}

sealed interface ScheduleAgentGoalResult {
    data class Scheduled(val goal: ScheduledAgentGoal) : ScheduleAgentGoalResult
    data object ProactiveMessagesDisabled : ScheduleAgentGoalResult
    data object InvalidGoal : ScheduleAgentGoalResult
    data object InvalidDeadline : ScheduleAgentGoalResult
    data object MissingCharacter : ScheduleAgentGoalResult
    data object InvalidChat : ScheduleAgentGoalResult
    data object UnsafePrompt : ScheduleAgentGoalResult
    data object SchedulerUnavailable : ScheduleAgentGoalResult
}

class ScheduleAgentGoalUseCase(
    private val characters: CharacterRepository,
    private val chats: ChatRepository,
    private val goals: AgentGoalRepository,
    private val settings: SettingsRepository,
    private val scheduler: AgentGoalScheduler
) {
    suspend operator fun invoke(
        characterId: EntityId,
        chatId: EntityId,
        title: String,
        prompt: String,
        delayMinutes: Int
    ): ScheduleAgentGoalResult {
        val cleanTitle = title.trim()
        val cleanPrompt = prompt.trim()
        if (cleanTitle.isBlank() || cleanTitle.length > MAX_TITLE_LENGTH || cleanPrompt.isBlank() || cleanPrompt.length > MAX_PROMPT_LENGTH) {
            return ScheduleAgentGoalResult.InvalidGoal
        }
        if (delayMinutes !in 1..MAX_DELAY_MINUTES) return ScheduleAgentGoalResult.InvalidDeadline
        if (!settings.observeSettings().first().proactiveMessagesEnabled) return ScheduleAgentGoalResult.ProactiveMessagesDisabled
        val character = characters.getCharacter(characterId)?.takeUnless { it.archived || it.deletedAt != null }
            ?: return ScheduleAgentGoalResult.MissingCharacter
        val chat = chats.getChat(chatId) ?: return ScheduleAgentGoalResult.InvalidChat
        if (chat.kind != ChatKind.DIRECT || chat.characterId != character.id) return ScheduleAgentGoalResult.InvalidChat
        if (!SafetyPolicy.evaluate(cleanPrompt).allowed) return ScheduleAgentGoalResult.UnsafePrompt

        val now = useCaseNowMillis()
        val goal = ScheduledAgentGoal(
            id = newEntityId("agent-goal"),
            characterId = character.id,
            chatId = chat.id,
            title = cleanTitle,
            prompt = cleanPrompt,
            dueAt = now + delayMinutes * MILLIS_PER_MINUTE,
            createdAt = now,
            updatedAt = now
        )
        return try {
            goals.saveGoal(goal)
            scheduler.schedule(goal.id, goal.dueAt)
            ScheduleAgentGoalResult.Scheduled(goal)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            goals.saveGoal(goal.copy(status = AgentGoalStatus.FAILED, failureReason = "系统未能安排到期任务", updatedAt = useCaseNowMillis()))
            ScheduleAgentGoalResult.SchedulerUnavailable
        }
    }

    private companion object {
        const val MAX_TITLE_LENGTH = 80
        const val MAX_PROMPT_LENGTH = 1_000
        const val MAX_DELAY_MINUTES = 365 * 24 * 60
        const val MILLIS_PER_MINUTE = 60_000L
    }
}

class CancelAgentGoalUseCase(
    private val goals: AgentGoalRepository,
    private val scheduler: AgentGoalScheduler
) {
    suspend operator fun invoke(goalId: EntityId) {
        val goal = goals.getGoal(goalId) ?: return
        if (goal.status != AgentGoalStatus.PENDING) return
        goals.saveGoal(goal.copy(status = AgentGoalStatus.CANCELLED, updatedAt = useCaseNowMillis()))
        scheduler.cancel(goal.id)
    }
}

sealed interface ExecuteAgentGoalResult {
    data class Completed(val messageId: EntityId) : ExecuteAgentGoalResult
    data object Retry : ExecuteAgentGoalResult
    data object Paused : ExecuteAgentGoalResult
    data object Skipped : ExecuteAgentGoalResult
    data class Failed(val reason: String) : ExecuteAgentGoalResult
}

class ExecuteAgentGoalUseCase(
    private val characters: CharacterRepository,
    private val chats: ChatRepository,
    private val memories: MemoryRepository,
    private val models: ModelProfileRepository,
    private val goals: AgentGoalRepository,
    private val settingsRepository: SettingsRepository,
    private val agentRuntime: AgentRuntime,
    private val characterStateCoordinator: CharacterStateCoordinator,
    private val proactiveAdmissionPolicy: ProactiveAdmissionPolicy
) {
    suspend operator fun invoke(goalId: EntityId): ExecuteAgentGoalResult {
        val goal = goals.getGoal(goalId) ?: return ExecuteAgentGoalResult.Skipped
        if (goal.status != AgentGoalStatus.PENDING) return ExecuteAgentGoalResult.Skipped
        if (!SafetyPolicy.evaluate(goal.prompt).allowed) return fail(goal, "目标内容不再符合安全要求")
        val now = useCaseNowMillis()
        if (now < goal.dueAt) return ExecuteAgentGoalResult.Retry
        val settings = settingsRepository.observeSettings().first()
        if (!settings.proactiveMessagesEnabled) return ExecuteAgentGoalResult.Paused
        val character = characters.getCharacter(goal.characterId)?.takeUnless { it.archived || it.deletedAt != null }
            ?: return fail(goal, "角色已删除或归档")
        val chat = chats.getChat(goal.chatId)?.takeIf { it.kind == ChatKind.DIRECT && it.characterId == character.id }
            ?: return fail(goal, "目标聊天已不存在")
        val messageId = goal.resultMessageId ?: goalResultMessageId(goal.id)
        val existingResult = chats.getMessages(chat.id).firstOrNull { it.id == messageId }
        if (existingResult != null) {
            val currentGoal = goals.getGoal(goalId) ?: return ExecuteAgentGoalResult.Skipped
            if (currentGoal.status != AgentGoalStatus.PENDING) return ExecuteAgentGoalResult.Skipped
            val completedAt = useCaseNowMillis()
            val preparedState = characterStateCoordinator.prepare(
                characterId = character.id,
                chatId = chat.id,
                sourceMessageId = null,
                userText = null,
                nowMillis = completedAt
            )
            goals.saveGoal(
                currentGoal.copy(
                    status = AgentGoalStatus.COMPLETED,
                    updatedAt = completedAt,
                    completedAt = completedAt,
                    resultMessageId = messageId
                )
            )
            characterStateCoordinator.commit(preparedState, completedAt, completedMessageId = messageId)
            return ExecuteAgentGoalResult.Completed(messageId)
        }
        if (!proactiveAdmissionPolicy.isAllowed(character, settings, now, requireCharacterOptIn = false)) {
            return ExecuteAgentGoalResult.Retry
        }
        val modelId = character.modelProfileId ?: settings.defaultModelProfileId
        val model = modelId?.let { models.getProfile(it) }?.takeIf { it.enabled && it.deletedAt == null }
            ?: return fail(goal, "角色没有可用模型")
        val preparedState = characterStateCoordinator.prepare(
            characterId = character.id,
            chatId = chat.id,
            sourceMessageId = messageId,
            userText = null,
            nowMillis = now
        )
        val reply = agentRuntime.run(
            AgentRequest(
                chat = chat,
                character = character,
                messages = chats.getMessages(chat.id),
                memories = CharacterMemoryRetriever.retrieve(
                    memories.observeMemories(character.id).first(), goal.prompt, now
                ),
                model = model,
                proactivePrompt = "${goal.title}: ${goal.prompt}",
                visibility = settings.agentVisibility,
                emotionalContext = preparedState.emotionalContext
            ),
            trigger = AgentTrigger.SCHEDULED_GOAL
        )
        val currentGoal = goals.getGoal(goalId) ?: return ExecuteAgentGoalResult.Skipped
        if (currentGoal.status != AgentGoalStatus.PENDING) return ExecuteAgentGoalResult.Skipped
        if (!settingsRepository.observeSettings().first().proactiveMessagesEnabled) return ExecuteAgentGoalResult.Paused
        if (reply.statusMessage == "agent-chat-busy" || reply.statusMessage.isRetryableProviderFailure()) {
            return ExecuteAgentGoalResult.Retry
        }
        if (reply.statusMessage != null) return fail(currentGoal, reply.statusMessage)
        if (reply.text.isBlank()) return fail(currentGoal, "模型返回了空消息")

        chats.appendMessage(
            ChatMessage(
                id = messageId,
                chatId = chat.id,
                senderId = character.id,
                senderType = MessageSenderType.CHARACTER,
                senderName = character.name,
                senderAvatarUri = character.avatarUri,
                text = reply.text,
                timestamp = useCaseNowMillis(),
                type = MessageType.TEXT,
                isProactive = true,
                isRead = false,
                deliveryState = MessageDeliveryState.SENT,
                responseState = AiResponseState.COMPLETED,
                usedMemoryIds = reply.usedMemoryIds
            )
        )
        val completedAt = useCaseNowMillis()
        goals.saveGoal(
            currentGoal.copy(
                status = AgentGoalStatus.COMPLETED,
                updatedAt = completedAt,
                completedAt = completedAt,
                resultMessageId = messageId
            )
        )
        characterStateCoordinator.commit(preparedState, completedAt, completedMessageId = messageId)
        return ExecuteAgentGoalResult.Completed(messageId)
    }

    private suspend fun fail(goal: ScheduledAgentGoal, reason: String): ExecuteAgentGoalResult {
        val current = goals.getGoal(goal.id) ?: return ExecuteAgentGoalResult.Skipped
        if (current.status != AgentGoalStatus.PENDING) return ExecuteAgentGoalResult.Skipped
        goals.saveGoal(current.copy(status = AgentGoalStatus.FAILED, updatedAt = useCaseNowMillis(), failureReason = reason.take(240)))
        return ExecuteAgentGoalResult.Failed(reason)
    }

    private fun String?.isRetryableProviderFailure(): Boolean = when {
        this == "remote-exception" -> true
        this == "remote-http-429" -> true
        this == null -> false
        else -> this.startsWith("remote-http-5")
    }

    private fun goalResultMessageId(goalId: EntityId): EntityId = "agent-goal-result:$goalId"
}
