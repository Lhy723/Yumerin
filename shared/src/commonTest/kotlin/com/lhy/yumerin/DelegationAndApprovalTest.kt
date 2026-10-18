package com.lhy.yumerin

import com.lhy.yumerin.core.model.AgentCapabilityCall
import com.lhy.yumerin.core.model.AgentModelStep
import com.lhy.yumerin.core.model.AgentReply
import com.lhy.yumerin.core.model.AgentRequest
import com.lhy.yumerin.core.model.AgentService
import com.lhy.yumerin.core.model.AgentCapabilityDescriptor
import com.lhy.yumerin.core.model.AgentConversationMessage
import com.lhy.yumerin.core.model.ChatKind
import com.lhy.yumerin.core.model.ChatThread
import com.lhy.yumerin.core.model.CharacterProfile
import com.lhy.yumerin.core.model.DelegatedAgentGoalStatus
import com.lhy.yumerin.core.model.GenerationParameters
import com.lhy.yumerin.core.model.MessageSenderType
import com.lhy.yumerin.core.model.MessageType
import com.lhy.yumerin.core.model.ModelProfile
import com.lhy.yumerin.core.model.ModelProviderKind
import com.lhy.yumerin.core.model.PendingExternalActionRules
import com.lhy.yumerin.core.model.PendingExternalActionStatus
import com.lhy.yumerin.core.model.SummaryMaintenanceMode
import com.lhy.yumerin.data.memory.InMemoryAppGraph
import com.lhy.yumerin.domain.agent.AgentRuntime
import com.lhy.yumerin.domain.agent.CalculatorCapability
import com.lhy.yumerin.domain.agent.CharacterStateCoordinator
import com.lhy.yumerin.domain.agent.DelegateTaskCapability
import com.lhy.yumerin.domain.repository.ResolvePendingExternalActionUseCase
import com.lhy.yumerin.domain.usecase.SendChatMessageCommand
import com.lhy.yumerin.domain.usecase.SendChatMessageResult
import com.lhy.yumerin.domain.usecase.SendChatMessageUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DelegationAndApprovalTest {
    @Test
    fun delegateTaskCapabilityCreatesGoalDuringChatTurn() = runTest {
        val graph = InMemoryAppGraph()
        graph.characters.saveCharacter(character)
        graph.chats.saveChat(chat)
        graph.models.saveProfile(model)

        val createGoal = com.lhy.yumerin.domain.usecase.CreateDelegatedAgentGoalUseCase(
            graph.characters, graph.chats, graph.delegatedAgentGoals, graph.delegatedAgentGoalScheduler
        )
        val service = ScriptedAgentService(
            AgentModelStep.CapabilityRequests(
                calls = listOf(
                    AgentCapabilityCall(
                        id = "call-1",
                        name = "delegate_task",
                        argumentsJson = """{"title":"整理调研","outcome":"整理一份关于雨夜城市的调研清单"}"""
                    )
                )
            ),
            AgentModelStep.FinalResponse(AgentReply("好的，我会在后台慢慢整理，完成后告诉你。", model.id))
        )
        val runtime = AgentRuntime(service, listOf(DelegateTaskCapability(createGoal), CalculatorCapability()))
        val send = SendChatMessageUseCase(
            graph.characters, graph.chats, graph.groups, graph.memories, graph.models,
            runtime, graph.groupMemberships, CharacterStateCoordinator(graph.characterRuntimeStates)
        )

        val result = send(
            SendChatMessageCommand(
                chat.id,
                "帮我盯着这件事，慢慢整理一份调研清单。",
                com.lhy.yumerin.core.model.AppSettings(defaultModelProfileId = model.id, autoMemoryEnabled = false)
            )
        )

        assertEquals(SendChatMessageResult.Completed(responseCount = 1), result)
        val goals = graph.delegatedAgentGoals.observeGoals().first()
        assertEquals(1, goals.size)
        assertEquals(DelegatedAgentGoalStatus.QUEUED, goals.single().status)
        assertEquals("整理调研", goals.single().title)
        // The final persona reply stays a single ordinary chat message.
        val replies = graph.chats.getMessages(chat.id).filter { it.senderType == MessageSenderType.CHARACTER }
        assertEquals(1, replies.size)
        assertEquals("好的，我会在后台慢慢整理，完成后告诉你。", replies.single().text)
    }

    @Test
    fun pendingActionRejectionCancelsLinkedGoalAndExpiryIsEnforced() = runTest {
        val graph = InMemoryAppGraph()
        graph.characters.saveCharacter(character)
        graph.chats.saveChat(chat)
        graph.models.saveProfile(model)
        val created = com.lhy.yumerin.domain.usecase.CreateDelegatedAgentGoalUseCase(
            graph.characters, graph.chats, graph.delegatedAgentGoals, graph.delegatedAgentGoalScheduler
        )(character.id, chat.id, "外部任务", "把整理结果发送到用户的邮件草稿")
        val goalId = (created as com.lhy.yumerin.domain.usecase.CreateDelegatedAgentGoalResult.Created).goal.id

        val now = 1_000_000L
        val action = com.lhy.yumerin.core.model.PendingExternalAction(
            id = "action-1", goalId = goalId, chatId = chat.id, characterId = character.id,
            serviceAccount = "user@example.com", actionType = "send_message",
            target = "user@example.com", content = "整理结果全文", createdAt = now,
            expiresAt = now + PendingExternalActionRules.TTL_MILLIS
        )
        graph.pendingExternalActions.saveAction(action)
        val resolve = ResolvePendingExternalActionUseCase(
            graph.pendingExternalActions, graph.delegatedAgentGoals, graph.delegatedAgentGoalScheduler
        )

        // Rejection cancels the linked goal.
        val rejected = resolve("action-1", approve = false, nowMillis = now + 1)
        assertTrue(rejected is com.lhy.yumerin.domain.repository.ResolvePendingExternalActionUseCase.Result.Rejected)
        assertEquals(DelegatedAgentGoalStatus.CANCELLED, graph.delegatedAgentGoals.getGoal(goalId)?.status)

        // Expiry: a still-pending action past its TTL reads as expired.
        val expiredAction = action.copy(id = "action-2", goalId = null, status = PendingExternalActionStatus.PENDING)
        graph.pendingExternalActions.saveAction(expiredAction)
        assertEquals(
            PendingExternalActionStatus.EXPIRED,
            PendingExternalActionRules.effectiveStatus(expiredAction, expiredAction.expiresAt + 1)
        )
        val expired = resolve("action-2", approve = true, nowMillis = expiredAction.expiresAt + 1)
        assertTrue(expired is com.lhy.yumerin.domain.repository.ResolvePendingExternalActionUseCase.Result.Expired)
    }

    private class ScriptedAgentService(vararg steps: AgentModelStep) : AgentService {
        private val remainingSteps = steps.toMutableList()

        override suspend fun reply(request: AgentRequest): AgentReply =
            error("The runtime should request model steps for this test.")

        override suspend fun nextStep(
            request: AgentRequest,
            conversation: List<AgentConversationMessage>,
            capabilities: List<AgentCapabilityDescriptor>,
            onAccumulatedText: suspend (String) -> Unit
        ): AgentModelStep = remainingSteps.removeAt(0)
    }

    private companion object {
        val character = CharacterProfile(
            id = "delegation-character", name = "小雨", avatarUri = null,
            relationshipFraming = "亲密的朋友", setting = "测试", description = "",
            personality = "温柔", tone = "自然", backstory = "", interactionStyle = "简短",
            modelProfileId = "model-test", archived = false, createdAt = 0L, updatedAt = 0L
        )
        val chat = ChatThread(
            id = "delegation-chat", kind = ChatKind.DIRECT, title = character.name,
            characterId = character.id, groupId = null, avatarUri = null,
            lastMessagePreview = "", lastActivityAt = 0L, unreadCount = 0,
            pinned = false, muted = false, shortTermSummary = "",
            summaryMaintenanceMode = SummaryMaintenanceMode.AUTO
        )
        val model = ModelProfile(
            id = "model-test", displayName = "测试模型", providerKind = ModelProviderKind.OPENAI_COMPATIBLE,
            providerName = "test", baseUrl = null, modelId = "test", credentialAlias = null,
            contextLength = 8_192,
            contextLengthMode = com.lhy.yumerin.core.model.ContextLengthMode.KNOWN,
            generationParameters = GenerationParameters(), localModelId = null, localModelVersion = null,
            enabled = true, createdAt = 0L, updatedAt = 0L
        )
    }
}
