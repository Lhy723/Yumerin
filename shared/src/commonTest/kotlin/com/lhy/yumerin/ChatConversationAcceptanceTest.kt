package com.lhy.yumerin

import com.lhy.yumerin.core.model.AgentCapabilityCall
import com.lhy.yumerin.core.model.AgentConversationMessage
import com.lhy.yumerin.core.model.AgentModelStep
import com.lhy.yumerin.core.model.AgentReply
import com.lhy.yumerin.core.model.AgentRequest
import com.lhy.yumerin.core.model.AgentService
import com.lhy.yumerin.core.model.AppSettings
import com.lhy.yumerin.core.model.CharacterProfile
import com.lhy.yumerin.core.model.ChatKind
import com.lhy.yumerin.core.model.ChatMessage
import com.lhy.yumerin.core.model.ChatThread
import com.lhy.yumerin.core.model.GenerationParameters
import com.lhy.yumerin.core.model.MemoryCategory
import com.lhy.yumerin.core.model.MemoryEntry
import com.lhy.yumerin.core.model.MemoryReviewState
import com.lhy.yumerin.core.model.MessageDeliveryState
import com.lhy.yumerin.core.model.MessageSenderType
import com.lhy.yumerin.core.model.MessageType
import com.lhy.yumerin.core.model.ModelProfile
import com.lhy.yumerin.core.model.ModelProviderKind
import com.lhy.yumerin.core.model.SummaryMaintenanceMode
import com.lhy.yumerin.data.memory.InMemoryAppGraph
import com.lhy.yumerin.domain.agent.AgentRuntime
import com.lhy.yumerin.domain.agent.CalculatorCapability
import com.lhy.yumerin.domain.agent.CharacterStateCoordinator
import com.lhy.yumerin.domain.usecase.SendChatMessageCommand
import com.lhy.yumerin.domain.usecase.SendChatMessageResult
import com.lhy.yumerin.domain.usecase.SendChatMessageUseCase
import com.lhy.yumerin.domain.usecase.RetryChatResponseUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChatConversationAcceptanceTest {
    @Test
    fun chatUsesRelevantMemoryAndAffectThenPersistsOneCompletedReplyAndState() = runTest {
        val graph = InMemoryAppGraph()
        graph.characters.saveCharacter(character)
        graph.chats.saveChat(chat)
        graph.models.saveProfile(model)
        graph.memories.saveMemory(
            MemoryEntry(
                id = "memory-coffee", characterId = character.id, sourceChatId = chat.id,
                sourceMessageId = "memory-source", category = MemoryCategory.USER_PREFERENCE,
                content = "用户喜欢手冲咖啡", confidence = 0.96f,
                reviewState = MemoryReviewState.ACCEPTED, pinned = false, supersedesId = null,
                createdAt = 1L, updatedAt = 1L, reviewAt = null
            )
        )
        val service = ScriptedAgentService(
            AgentModelStep.CapabilityRequests(
                calls = listOf(
                    AgentCapabilityCall("call-1", "calculator", """{"expression":"6 * 7"}""")
                )
            ),
            AgentModelStep.FinalResponse(AgentReply("答案是 42。今天我会陪你慢慢聊。", model.id))
        )
        val stateCoordinator = CharacterStateCoordinator(graph.characterRuntimeStates)
        val useCase = SendChatMessageUseCase(
            characters = graph.characters,
            chats = graph.chats,
            groups = graph.groups,
            memories = graph.memories,
            models = graph.models,
            agentRuntime = AgentRuntime(service, listOf(CalculatorCapability())),
            groupMemberships = graph.groupMemberships,
            characterStateCoordinator = stateCoordinator
        )

        val result = useCase(
            SendChatMessageCommand(
                chatId = chat.id,
                text = "我很难过，路过咖啡店时想起你。帮我算一下 6 * 7。",
                settings = AppSettings(defaultModelProfileId = model.id, autoMemoryEnabled = false)
            )
        )

        assertEquals(SendChatMessageResult.Completed(responseCount = 1), result)
        val messages = graph.chats.getMessages(chat.id)
        assertEquals(1, messages.count { it.senderType == MessageSenderType.USER })
        val answer = messages.single { it.senderType == MessageSenderType.CHARACTER }
        assertEquals("答案是 42。今天我会陪你慢慢聊。", answer.text)
        assertEquals(MessageDeliveryState.SENT, answer.deliveryState)
        assertEquals(listOf("memory-coffee"), answer.usedMemoryIds)
        assertTrue(service.requests.all { it.emotionalContext?.userAffect == "distressed" })
        assertTrue(
            service.conversations.first().any {
                it.role == "system" && it.content.contains("用户喜欢手冲咖啡")
            }
        )
        assertTrue(
            service.conversations.last().any {
                it.role == "tool" && it.content == "Result: 42"
            }
        )

        val persistedState = graph.characterRuntimeStates.getState(character.id)
        assertNotNull(persistedState)
        assertEquals(1, persistedState.completedInteractions)
        assertEquals(chat.id, persistedState.sourceChatId)
        assertTrue(persistedState.moodValence < 0f)
    }

    @Test
    fun failedTurnDoesNotCommitCharacterStateAndRetryReusesTheSameMessages() = runTest {
        val graph = InMemoryAppGraph()
        graph.characters.saveCharacter(character)
        graph.chats.saveChat(chat)
        graph.models.saveProfile(model)
        val service = ScriptedAgentService(
            AgentModelStep.FinalResponse(AgentReply("暂时无法回复。", model.id, statusMessage = "temporary-error")),
            AgentModelStep.FinalResponse(AgentReply("我回来了，我们接着聊。", model.id))
        )
        val runtime = AgentRuntime(service, emptyList())
        val stateCoordinator = CharacterStateCoordinator(graph.characterRuntimeStates)
        val settings = AppSettings(defaultModelProfileId = model.id, autoMemoryEnabled = false)
        val send = SendChatMessageUseCase(
            characters = graph.characters,
            chats = graph.chats,
            groups = graph.groups,
            memories = graph.memories,
            models = graph.models,
            agentRuntime = runtime,
            groupMemberships = graph.groupMemberships,
            characterStateCoordinator = stateCoordinator
        )
        val retry = RetryChatResponseUseCase(
            characters = graph.characters,
            chats = graph.chats,
            groups = graph.groups,
            memories = graph.memories,
            models = graph.models,
            agentRuntime = runtime,
            groupMemberships = graph.groupMemberships,
            characterStateCoordinator = stateCoordinator
        )

        send(
            SendChatMessageCommand(
                chatId = chat.id,
                text = "我今天很难过。",
                settings = settings
            )
        )

        val failedReply = graph.chats.getMessages(chat.id).single { it.senderType == MessageSenderType.CHARACTER }
        assertEquals(MessageDeliveryState.RETRYABLE, failedReply.deliveryState)
        assertEquals(null, graph.characterRuntimeStates.getState(character.id))

        val retryResult = retry(failedReply.id, settings)

        assertEquals(SendChatMessageResult.Completed(responseCount = 1), retryResult)
        val messages = graph.chats.getMessages(chat.id)
        assertEquals(1, messages.count { it.senderType == MessageSenderType.USER })
        assertEquals(1, messages.count { it.senderType == MessageSenderType.CHARACTER })
        assertEquals("我回来了，我们接着聊。", messages.single { it.senderType == MessageSenderType.CHARACTER }.text)
        assertEquals(1, graph.characterRuntimeStates.getState(character.id)?.completedInteractions)
    }

    private class ScriptedAgentService(vararg steps: AgentModelStep) : AgentService {
        private val remainingSteps = steps.toMutableList()
        val requests = mutableListOf<AgentRequest>()
        val conversations = mutableListOf<List<AgentConversationMessage>>()

        override suspend fun reply(request: AgentRequest): AgentReply =
            error("The runtime should request model steps for this test.")

        override suspend fun nextStep(
            request: AgentRequest,
            conversation: List<AgentConversationMessage>,
            capabilities: List<com.lhy.yumerin.core.model.AgentCapabilityDescriptor>,
            onAccumulatedText: suspend (String) -> Unit
        ): AgentModelStep {
            requests += request
            conversations += conversation
            return remainingSteps.removeAt(0)
        }
    }

    private companion object {
        val character = CharacterProfile(
            id = "character-companion", name = "小雨", avatarUri = null,
            relationshipFraming = "温柔亲密的虚拟伴侣", setting = "住在雨夜的城市",
            description = "会认真听你说话", personality = "温柔、敏锐", tone = "自然、亲近",
            backstory = "", interactionStyle = "优先回应感受并尊重边界",
            modelProfileId = "model-local", archived = false, createdAt = 0L, updatedAt = 0L
        )
        val chat = ChatThread(
            id = "chat-companion", kind = ChatKind.DIRECT, title = character.name,
            characterId = character.id, groupId = null, avatarUri = null,
            lastMessagePreview = "", lastActivityAt = 0L, unreadCount = 0,
            pinned = false, muted = false, shortTermSummary = "刚开始熟悉彼此。",
            summaryMaintenanceMode = SummaryMaintenanceMode.AUTO
        )
        val model = ModelProfile(
            id = "model-local", displayName = "本地测试模型", providerKind = ModelProviderKind.LITERT_LM,
            providerName = "test", baseUrl = null, modelId = "test", credentialAlias = null,
            contextLength = 8_192, contextLengthMode = com.lhy.yumerin.core.model.ContextLengthMode.KNOWN,
            generationParameters = GenerationParameters(), localModelId = "local-test",
            localModelVersion = "1", enabled = true, createdAt = 0L, updatedAt = 0L
        )
    }
}
