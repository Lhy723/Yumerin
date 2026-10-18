package com.lhy.yumerin.domain.usecase

import com.lhy.yumerin.core.model.*
import com.lhy.yumerin.core.model.AgentRunEvent
import com.lhy.yumerin.core.model.AgentCapabilityRequested
import com.lhy.yumerin.core.model.AgentCapabilityCompleted
import com.lhy.yumerin.core.model.AgentBudgetWarning
import com.lhy.yumerin.core.model.AgentRunInterrupted
import com.lhy.yumerin.domain.agent.AgentRuntime
import com.lhy.yumerin.domain.agent.CharacterMemoryRetriever
import com.lhy.yumerin.domain.agent.CharacterStateCoordinator
import com.lhy.yumerin.domain.memory.MemoryPolicy
import com.lhy.yumerin.domain.repository.*
import com.lhy.yumerin.domain.safety.SafetyPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

data class SendChatMessageCommand(
    val chatId: EntityId,
    val text: String,
    val settings: AppSettings,
    val existingMessageId: EntityId? = null
)

/**
 * Renders one in-flight assistant turn into a single message bubble: the
 * accumulated answer text plus, per [AgentVisibility], a capability status
 * line. Event-driven so a future voice or remote channel reuses the same
 * rendering rules without touching message persistence.
 */
internal class AgentTurnBubbleRenderer(
    private val chats: ChatRepository,
    private val template: ChatMessage,
    private val visibility: AgentVisibility
) {
    private var accumulated: String = ""
    private val statusLines = mutableListOf<String>()
    private var stoppedPainted = false

    suspend fun onAccumulatedText(text: String) {
        accumulated = text
        repaint()
    }

    suspend fun onEvent(event: AgentRunEvent) {
        when (event) {
            is AgentCapabilityRequested -> if (visibility != AgentVisibility.FINAL_ONLY) {
                statusLines += "正在使用 ${event.call.name}…"
                repaint()
            }
            is AgentCapabilityCompleted -> if (visibility != AgentVisibility.FINAL_ONLY) {
                val summary = event.userFacingSummary?.takeIf { it.isNotBlank() }
                    ?: if (event.successful) "已完成 ${event.capabilityName}" else "${event.capabilityName} 执行失败"
                if (statusLines.isNotEmpty() && statusLines.last().endsWith("…")) {
                    statusLines[statusLines.lastIndex] = summary
                } else {
                    statusLines += summary
                }
                repaint()
            }
            is AgentBudgetWarning -> if (visibility == AgentVisibility.DETAILED_STEPS) {
                statusLines += "预算提示：${event.detail}"
                repaint()
            }
            is AgentRunInterrupted -> markStopped()
            else -> Unit
        }
    }

    /** Paints the stopped turn; safe to call from cancellation cleanup too. */
    suspend fun markStopped() {
        if (stoppedPainted) return
        stoppedPainted = true
        chats.appendMessage(
            template.copy(
                text = "已停止生成",
                timestamp = useCaseNowMillis(),
                deliveryState = MessageDeliveryState.RETRYABLE,
                responseState = AiResponseState.STOPPED
            )
        )
    }

    private suspend fun repaint() {
        val content = when (visibility) {
            AgentVisibility.FINAL_ONLY -> accumulated
            AgentVisibility.CONCISE_STATUS -> listOfNotNull(statusLines.lastOrNull(), accumulated)
                .filter(String::isNotBlank).joinToString("\n\n")
            AgentVisibility.DETAILED_STEPS -> (statusLines + accumulated)
                .filter(String::isNotBlank).joinToString("\n\n")
        }
        chats.appendMessage(
            template.copy(
                text = content,
                timestamp = useCaseNowMillis(),
                deliveryState = MessageDeliveryState.PENDING,
                responseState = AiResponseState.GENERATING
            )
        )
    }
}

sealed interface SendChatMessageResult {
    data object Empty : SendChatMessageResult
    data object MissingModel : SendChatMessageResult
    data class Completed(val responseCount: Int) : SendChatMessageResult
    data class Failed(val message: String, val retryable: Boolean) : SendChatMessageResult
}

class SendChatMessageUseCase(
    private val characters: CharacterRepository,
    private val chats: ChatRepository,
    private val groups: GroupRepository,
    private val memories: MemoryRepository,
    private val models: ModelProfileRepository,
    private val agentRuntime: AgentRuntime,
    private val groupMemberships: GroupMembershipRepository,
    private val characterStateCoordinator: CharacterStateCoordinator
) {
    suspend operator fun invoke(command: SendChatMessageCommand): SendChatMessageResult {
        val text = command.text.trim()
        if (text.isEmpty()) return SendChatMessageResult.Empty
        val originalChat = chats.getChat(command.chatId)
            ?: return SendChatMessageResult.Failed("聊天不存在或已被删除。", retryable = false)
        val directCharacter = originalChat.characterId?.let { characters.getCharacter(it) }
        val group = originalChat.groupId?.let { groups.getGroup(it) }
        if (group == null && directCharacter?.archived == true) {
            return SendChatMessageResult.Failed("该角色已归档，请先恢复角色后再继续聊天。", retryable = false)
        }
        val candidates = if (group == null) {
            listOfNotNull(directCharacter?.takeUnless { it.archived || it.deletedAt != null })
        } else {
            val windows = groupMemberships.observeWindows(group.id).first()
            val activeIds = if (windows.isEmpty()) {
                group.memberCharacterIds.toSet()
            } else {
                windows.filter { it.leftAt == null }.map { it.characterId }.toSet()
            }
            group.memberCharacterIds.filter { it in activeIds }
                .mapNotNull { characters.getCharacter(it)?.takeUnless { character -> character.archived || character.deletedAt != null } }
        }
        val safety = SafetyPolicy.evaluate(text)
        if (!safety.allowed) {
            val timestamp = useCaseNowMillis()
            val userMessage = ChatMessage(
                id = newEntityId("message"), chatId = command.chatId, senderId = null,
                senderType = MessageSenderType.USER, senderName = "我", senderAvatarUri = null,
                text = text, timestamp = timestamp, type = MessageType.TEXT, isProactive = false,
                isRead = true, deliveryState = MessageDeliveryState.SENT
            )
            chats.appendMessage(userMessage)
            val responder = candidates.firstOrNull() ?: directCharacter
            chats.appendMessage(
                ChatMessage(
                    id = newEntityId("message"), chatId = command.chatId, senderId = responder?.id,
                    senderType = responder?.let { MessageSenderType.CHARACTER } ?: MessageSenderType.SYSTEM,
                    senderName = responder?.name ?: "Yumerin", senderAvatarUri = responder?.avatarUri,
                    text = SafetyPolicy.refusal(responder?.name ?: "Yumerin", safety.reason ?: "不安全内容"),
                    timestamp = useCaseNowMillis(), type = MessageType.TEXT, isProactive = false, isRead = true,
                    deliveryState = MessageDeliveryState.SENT, responseState = AiResponseState.COMPLETED
                )
            )
            return SendChatMessageResult.Completed(responseCount = 1)
        }
        val mentioned = candidates.filter { text.contains("@${it.name}") }
        val participants = if (candidates.isEmpty()) {
            emptyList()
        } else if (group == null) {
            candidates.take(1)
        } else if (mentioned.isNotEmpty()) {
            // An explicit mention is a direct turn request and takes precedence
            // over autonomous scene rotation.
            mentioned.take(1)
        } else {
            val turnLimit = group.autonomousTurnLimit.coerceIn(1, 3)
            val ordered = candidates.sortedByDescending { relevanceScore(text, it) }
            List(turnLimit) { index -> ordered[index % ordered.size] }
        }
        val selectedModels = participants.map { character ->
            val modelId = character.modelProfileId ?: command.settings.defaultModelProfileId
            character to modelId?.let { models.getProfile(it) }?.takeIf { it.enabled && it.deletedAt == null }
        }
        val existingUserMessage = command.existingMessageId?.let { messageId ->
            chats.getMessages(command.chatId).firstOrNull {
                it.id == messageId && it.senderType == MessageSenderType.USER
            }
        }
        val userMessage = existingUserMessage?.copy(
            text = text,
            timestamp = useCaseNowMillis(),
            deliveryState = MessageDeliveryState.PENDING,
            errorMessage = null,
            isRead = true
        ) ?: ChatMessage(
            id = newEntityId("message"), chatId = command.chatId, senderId = null,
            senderType = MessageSenderType.USER, senderName = "我", senderAvatarUri = null,
            text = text, timestamp = useCaseNowMillis(), type = MessageType.TEXT, isProactive = false,
            isRead = true, deliveryState = MessageDeliveryState.PENDING
        )
        if (participants.isEmpty()) {
            chats.appendMessage(
                userMessage.copy(
                    deliveryState = MessageDeliveryState.FAILED,
                    errorMessage = "当前没有可参与的角色。"
                )
            )
            return SendChatMessageResult.Failed("当前没有可参与的角色。", retryable = false)
        }
        if (selectedModels.any { it.second == null }) {
            // Keep the attempted outgoing message in the local timeline so a
            // missing model never makes the user's action disappear.
            chats.appendMessage(
                userMessage.copy(
                    deliveryState = MessageDeliveryState.FAILED,
                    errorMessage = "missing-model"
                )
            )
            return SendChatMessageResult.MissingModel
        }
        chats.appendMessage(userMessage)
        chats.appendMessage(userMessage.copy(deliveryState = MessageDeliveryState.SENT))

        if (command.settings.autoMemoryEnabled) {
            (if (group == null) listOfNotNull(directCharacter) else participants.distinctBy { it.id }).forEach { character ->
                persistStableMemories(character, command.chatId, userMessage.id, text)
            }
        }

        var responseCount = 0
        var previousResponseFingerprint: String? = null
        for ((character, selectedModel) in selectedModels) {
            val model = selectedModel ?: continue
            val responseId = newEntityId("message")
            val generating = ChatMessage(
                id = responseId, chatId = command.chatId, senderId = character.id,
                senderType = MessageSenderType.CHARACTER, senderName = character.name,
                senderAvatarUri = character.avatarUri, text = "", timestamp = useCaseNowMillis(),
                type = MessageType.TEXT, isProactive = false, isRead = true,
                deliveryState = MessageDeliveryState.PENDING, responseState = AiResponseState.GENERATING
            )
            chats.appendMessage(generating)
            val renderer = AgentTurnBubbleRenderer(
                chats = chats,
                template = generating,
                visibility = command.settings.agentVisibility
            )
            try {
                val messages = visibleMessagesFor(character, originalChat, group)
                val preparedChat = compressIfNeeded(originalChat, group, messages, model, command.settings)
                val turnTime = useCaseNowMillis()
                val preparedState = characterStateCoordinator.prepare(
                    characterId = character.id,
                    chatId = command.chatId,
                    sourceMessageId = userMessage.id,
                    userText = text,
                    nowMillis = turnTime
                )
                val reply = agentRuntime.run(
                    AgentRequest(
                        chat = preparedChat, character = character, messages = messages,
                        memories = CharacterMemoryRetriever.retrieve(
                            memories.observeMemories(character.id).first(), text, turnTime
                        ), model = model,
                        streaming = command.settings.streamingEnabled,
                        visibility = command.settings.agentVisibility,
                        emotionalContext = preparedState.emotionalContext
                    ),
                    trigger = AgentTrigger.USER_MESSAGE,
                    onEvent = renderer::onEvent
                ) { accumulatedText ->
                    renderer.onAccumulatedText(accumulatedText)
                }
                val failed = reply.statusMessage != null
                val fingerprint = reply.text.trim().lowercase().replace(Regex("\\s+"), " ")
                if (!failed && fingerprint.isNotBlank() && fingerprint == previousResponseFingerprint) {
                    chats.appendMessage(generating.copy(
                        text = "已停止：检测到重复内容",
                        timestamp = useCaseNowMillis(),
                        deliveryState = MessageDeliveryState.RETRYABLE,
                        responseState = AiResponseState.STOPPED,
                        errorMessage = "repetitive-dialogue"
                    ))
                    break
                }
                chats.appendMessage(
                    generating.copy(
                        text = reply.text, timestamp = useCaseNowMillis(),
                        deliveryState = if (failed) MessageDeliveryState.RETRYABLE else MessageDeliveryState.SENT,
                        responseState = if (failed) AiResponseState.RETRYABLE else AiResponseState.COMPLETED,
                        errorMessage = reply.statusMessage, usedMemoryIds = reply.usedMemoryIds
                    )
                )
                if (!failed && fingerprint.isNotBlank()) {
                    previousResponseFingerprint = fingerprint
                    characterStateCoordinator.commit(preparedState, useCaseNowMillis())
                }
                responseCount += 1
            } catch (cancelled: CancellationException) {
                renderer.markStopped()
                throw cancelled
            } catch (error: Exception) {
                chats.appendMessage(generating.copy(
                    text = "生成失败：${error.message ?: "未知错误"}", timestamp = useCaseNowMillis(),
                    deliveryState = MessageDeliveryState.RETRYABLE, responseState = AiResponseState.FAILED,
                    errorMessage = error.message
                ))
            }
        }
        return SendChatMessageResult.Completed(responseCount)
    }

    private suspend fun visibleMessagesFor(character: CharacterProfile, chat: ChatThread, group: GroupProfile?): List<ChatMessage> {
        val messages = chats.getMessages(chat.id)
        if (group == null) return messages
        val windows = groupMemberships.observeWindows(group.id).first()
        if (windows.isEmpty()) return messages
        return messages.filter { message ->
            windows.any { window ->
                window.characterId == character.id && message.timestamp >= window.joinedAt &&
                    (window.leftAt == null || message.timestamp <= window.leftAt)
            }
        }
    }

    private suspend fun compressIfNeeded(
        chat: ChatThread,
        group: GroupProfile?,
        messages: List<ChatMessage>,
        model: ModelProfile,
        settings: AppSettings
    ): ChatThread {
        val limit = ((model.contextLength ?: 8_192) * 4 * settings.contextCompressionThreshold)
            .toInt().coerceAtLeast(2_000)
        val approximate = chat.shortTermSummary.length + messages.sumOf { it.text.length } + 1_000
        if (approximate <= limit || chat.summaryMaintenanceMode.name == "FIXED") return chat
        val older = messages.dropLast(12).takeLast(24)
        if (older.isEmpty()) return chat
        val summary = buildString {
            if (chat.shortTermSummary.isNotBlank()) append(chat.shortTermSummary).append('\n')
            append(older.joinToString("\n") { "${it.senderName}: ${it.text}" })
        }.takeLast(4_000)
        val compressed = chat.copy(shortTermSummary = summary)
        chats.saveChat(compressed)
        if (group != null && group.summaryMaintenanceMode.name == "AUTO") {
            groups.saveGroup(group.copy(summary = summary, updatedAt = useCaseNowMillis()))
        }
        return compressed
    }

    private suspend fun persistStableMemories(character: CharacterProfile, chatId: EntityId, sourceMessageId: EntityId, text: String) {
        val current = memories.observeMemories(character.id).first()
        MemoryPolicy.extract(text).forEach { candidate ->
            if (current.any { it.content == candidate.content && it.reviewState != MemoryReviewState.REJECTED }) return@forEach
            val sameTopic = current.filter { it.category == candidate.category && memoryTopic(it.content) == memoryTopic(candidate.content) }
            val timestamp = useCaseNowMillis()
            val highConfidence = candidate.confidence >= 0.86f && !candidate.sensitive
            val conflict = sameTopic.firstOrNull { it.content != candidate.content && it.reviewState == MemoryReviewState.ACCEPTED }
            if (conflict != null && highConfidence) memories.saveMemory(conflict.copy(reviewState = MemoryReviewState.SUPERSEDED, updatedAt = timestamp))
            memories.saveMemory(
                MemoryEntry(
                    id = newEntityId("memory"), characterId = character.id, sourceChatId = chatId,
                    sourceMessageId = sourceMessageId, category = candidate.category, content = candidate.content,
                    confidence = candidate.confidence,
                    reviewState = if (highConfidence) MemoryReviewState.ACCEPTED else MemoryReviewState.PENDING_REVIEW,
                    pinned = false, supersedesId = conflict?.id, createdAt = timestamp, updatedAt = timestamp,
                    reviewAt = if (highConfidence) null else timestamp + 7 * 24 * 60 * 60 * 1_000L,
                    reviewReason = candidate.reviewReason ?: if (conflict != null) "与现有记忆冲突，需要用户确认" else null
                )
            )
        }
    }

    private fun memoryTopic(content: String): String = content.removePrefix("用户喜欢")
        .removePrefix("用户不喜欢").removePrefix("用户可能喜欢").removePrefix("用户的名字是").trim()

    private fun relevanceScore(text: String, character: CharacterProfile): Int {
        val keywords = text.split(Regex("[\\s，。！？、；：,.!?;:]+"))
            .map(String::trim)
            .filter { it.length >= 2 }
        return keywords.sumOf { keyword ->
            listOf(character.name, character.setting, character.description, character.personality, character.interactionStyle)
                .count { field -> field.contains(keyword, ignoreCase = true) }
        }
    }
}

/** Retries only the failed assistant turn; it never duplicates the user's message. */
class RetryChatResponseUseCase(
    private val characters: CharacterRepository,
    private val chats: ChatRepository,
    private val groups: GroupRepository,
    private val memories: MemoryRepository,
    private val models: ModelProfileRepository,
    private val agentRuntime: AgentRuntime,
    private val groupMemberships: GroupMembershipRepository,
    private val characterStateCoordinator: CharacterStateCoordinator
) {
    suspend operator fun invoke(
        messageId: EntityId,
        settings: AppSettings
    ): SendChatMessageResult {
        val failed = findMessage(messageId)
            ?: return SendChatMessageResult.Failed("找不到需要重试的回复。", retryable = false)
        val chat = chats.getChat(failed.chatId)
            ?: return SendChatMessageResult.Failed("聊天不存在或已被删除。", retryable = false)
        val character = failed.senderId?.let { characters.getCharacter(it) }
            ?: chat.characterId?.let { characters.getCharacter(it) }
            ?: return SendChatMessageResult.Failed("找不到回复角色。", retryable = false)
        if (character.archived || character.deletedAt != null) {
            return SendChatMessageResult.Failed("该角色已归档，请先恢复角色后再重试。", retryable = false)
        }
        val group = chat.groupId?.let { groups.getGroup(it) }
        if (group != null && groupMemberships.getActiveWindow(group.id, character.id) == null) {
            return SendChatMessageResult.Failed("该角色当前不在群聊参与窗口内。", retryable = false)
        }
        val modelId = character.modelProfileId ?: settings.defaultModelProfileId
        val model = modelId?.let { models.getProfile(it) }?.takeIf { it.enabled && it.deletedAt == null }
            ?: return SendChatMessageResult.MissingModel
        val userMessage = chats.getMessages(chat.id)
            .filter { it.senderType == MessageSenderType.USER && it.timestamp <= failed.timestamp }
            .maxByOrNull { it.timestamp }
            ?: return SendChatMessageResult.Failed("找不到这条回复对应的用户消息。", retryable = false)
        val visibleMessages = chats.getMessages(chat.id)
            .filter { it.timestamp <= userMessage.timestamp || it.id == userMessage.id }
        val generating = failed.copy(
            text = "", timestamp = useCaseNowMillis(), deliveryState = MessageDeliveryState.PENDING,
            responseState = AiResponseState.GENERATING, errorMessage = null
        )
        chats.appendMessage(generating)
        val renderer = AgentTurnBubbleRenderer(
            chats = chats,
            template = generating,
            visibility = settings.agentVisibility
        )
        return try {
            val turnTime = useCaseNowMillis()
            val preparedState = characterStateCoordinator.prepare(
                characterId = character.id,
                chatId = chat.id,
                sourceMessageId = userMessage.id,
                userText = userMessage.text,
                nowMillis = turnTime
            )
            val reply = agentRuntime.run(
                AgentRequest(
                    chat = chat,
                    character = character,
                    messages = visibleMessages,
                    memories = CharacterMemoryRetriever.retrieve(
                        memories.observeMemories(character.id).first(), userMessage.text, turnTime
                    ),
                    model = model,
                    streaming = settings.streamingEnabled,
                    visibility = settings.agentVisibility,
                    emotionalContext = preparedState.emotionalContext
                ),
                trigger = AgentTrigger.RETRY,
                onEvent = renderer::onEvent
            ) { accumulatedText ->
                renderer.onAccumulatedText(accumulatedText)
            }
            chats.appendMessage(
                generating.copy(
                    text = reply.text,
                    timestamp = useCaseNowMillis(),
                    deliveryState = if (reply.statusMessage == null) MessageDeliveryState.SENT else MessageDeliveryState.RETRYABLE,
                    responseState = if (reply.statusMessage == null) AiResponseState.COMPLETED else AiResponseState.RETRYABLE,
                    errorMessage = reply.statusMessage,
                    usedMemoryIds = reply.usedMemoryIds
                )
            )
            if (reply.statusMessage == null) {
                characterStateCoordinator.commit(preparedState, useCaseNowMillis())
                SendChatMessageResult.Completed(1)
            } else {
                SendChatMessageResult.Failed(reply.statusMessage, retryable = true)
            }
        } catch (cancelled: CancellationException) {
            renderer.markStopped()
            throw cancelled
        } catch (error: Exception) {
            chats.appendMessage(generating.copy(
                text = "生成失败：${error.message ?: "未知错误"}", timestamp = useCaseNowMillis(),
                deliveryState = MessageDeliveryState.RETRYABLE, responseState = AiResponseState.FAILED,
                errorMessage = error.message
            ))
            SendChatMessageResult.Failed(error.message ?: "生成失败", retryable = true)
        }
    }

    private suspend fun findMessage(messageId: EntityId): ChatMessage? {
        for (chat in chats.observeChats().first()) {
            chats.getMessages(chat.id).firstOrNull { it.id == messageId }?.let { return it }
        }
        return null
    }
}

class DeleteChatUseCase(
    private val chats: ChatRepository,
    private val agentGoals: AgentGoalRepository,
    private val agentGoalScheduler: com.lhy.yumerin.domain.background.AgentGoalScheduler,
    private val delegatedAgentGoals: DelegatedAgentGoalRepository,
    private val delegatedAgentGoalScheduler: com.lhy.yumerin.domain.background.DelegatedAgentGoalScheduler
) {
    suspend operator fun invoke(chatId: EntityId) {
        agentGoals.observeGoals().first().filter { it.chatId == chatId }.forEach { goal ->
            agentGoalScheduler.cancel(goal.id)
            agentGoals.deleteGoal(goal.id)
        }
        delegatedAgentGoals.observeGoals().first().filter { it.chatId == chatId }.forEach { goal ->
            delegatedAgentGoalScheduler.cancel(goal.id)
            delegatedAgentGoals.deleteGoal(goal.id)
        }
        chats.deleteChat(chatId)
    }
}
