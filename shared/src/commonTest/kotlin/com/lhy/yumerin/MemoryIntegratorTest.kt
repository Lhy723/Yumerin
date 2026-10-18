package com.lhy.yumerin

import com.lhy.yumerin.core.model.AgentCapabilityCall
import com.lhy.yumerin.core.model.AgentCapabilityCompleted
import com.lhy.yumerin.core.model.AgentModelStep
import com.lhy.yumerin.core.model.AgentReply
import com.lhy.yumerin.core.model.AgentRequest
import com.lhy.yumerin.core.model.AgentRunCompleted
import com.lhy.yumerin.core.model.AgentService
import com.lhy.yumerin.core.model.AgentCapabilityDescriptor
import com.lhy.yumerin.core.model.AgentConversationMessage
import com.lhy.yumerin.core.model.CharacterProfile
import com.lhy.yumerin.core.model.ChatKind
import com.lhy.yumerin.core.model.ChatMessage
import com.lhy.yumerin.core.model.ChatThread
import com.lhy.yumerin.core.model.GenerationParameters
import com.lhy.yumerin.core.model.MessageSenderType
import com.lhy.yumerin.core.model.MessageType
import com.lhy.yumerin.core.model.ModelProfile
import com.lhy.yumerin.core.model.ModelProviderKind
import com.lhy.yumerin.core.model.SummaryMaintenanceMode
import com.lhy.yumerin.data.memory.InMemoryAppGraph
import com.lhy.yumerin.domain.agent.AgentRuntime
import com.lhy.yumerin.domain.memory.MemoryIntegrator
import com.lhy.yumerin.domain.memory.RepositoryMemoryProvider
import com.lhy.yumerin.domain.usecase.SendChatMessageCommand
import com.lhy.yumerin.domain.usecase.SendChatMessageResult
import com.lhy.yumerin.domain.usecase.SendChatMessageUseCase
import com.lhy.yumerin.domain.agent.CharacterStateCoordinator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemoryIntegratorTest {
    @Test
    fun successfulTurnWritesAcceptedMemoryAndFailedTurnWritesNothing() = runTest {
        val graph = InMemoryAppGraph()
        graph.characters.saveCharacter(character)
        graph.chats.saveChat(chat)
        graph.models.saveProfile(model)

        val integrator = MemoryIntegrator(graph.memories)
        val failingService = ScriptedAgentService(
            AgentModelStep.FinalResponse(AgentReply("暂时不行。", model.id, statusMessage = "temporary-error"))
        )
        val sendWithFailingModel = SendChatMessageUseCase(
            characters = graph.characters,
            chats = graph.chats,
            groups = graph.groups,
            memories = graph.memories,
            models = graph.models,
            agentRuntime = AgentRuntime(failingService, emptyList()),
            groupMemberships = graph.groupMemberships,
            characterStateCoordinator = CharacterStateCoordinator(graph.characterRuntimeStates),
            memoryIntegrator = integrator
        )
        val settings = com.lhy.yumerin.core.model.AppSettings(
            defaultModelProfileId = model.id,
            autoMemoryEnabled = true
        )
        sendWithFailingModel(SendChatMessageCommand(chat.id, "我喜欢手冲咖啡", settings))

        assertTrue(graph.memories.observeMemories(character.id).first().isEmpty())

        val succeedingService = ScriptedAgentService(
            AgentModelStep.FinalResponse(AgentReply("记住了。", model.id))
        )
        val sendOk = SendChatMessageUseCase(
            characters = graph.characters,
            chats = graph.chats,
            groups = graph.groups,
            memories = graph.memories,
            models = graph.models,
            agentRuntime = AgentRuntime(succeedingService, emptyList()),
            groupMemberships = graph.groupMemberships,
            characterStateCoordinator = CharacterStateCoordinator(graph.characterRuntimeStates),
            memoryIntegrator = integrator
        )
        val result = sendOk(SendChatMessageCommand(chat.id, "我喜欢手冲咖啡", settings))

        assertEquals(SendChatMessageResult.Completed(responseCount = 1), result)
        val memories = graph.memories.observeMemories(character.id).first()
        assertEquals(1, memories.size)
        assertEquals("用户喜欢手冲咖啡", memories.single().content)
        assertEquals(com.lhy.yumerin.core.model.MemoryReviewState.ACCEPTED, memories.single().reviewState)
    }

    @Test
    fun briefRanksPinnedAndImportantEntriesFirst() = runTest {
        val graph = InMemoryAppGraph()
        graph.memories.saveMemory(
            entry("memory-plain", "普通的近况记录", importance = 0.2f, pinned = false, updatedAt = 100L)
        )
        graph.memories.saveMemory(
            entry("memory-pinned", "固定的重要约定", importance = 0.4f, pinned = true, updatedAt = 50L)
        )
        graph.memories.saveMemory(
            entry("memory-important", "高重要度事件", importance = 0.95f, pinned = false, updatedAt = 60L)
        )
        val provider = RepositoryMemoryProvider(character.id, graph.memories)

        val brief = provider.brief(limit = 2)

        assertTrue(brief.contains("固定的重要约定"))
        assertTrue(brief.contains("高重要度事件"))
        assertTrue(brief.indexOf("固定的重要约定") < brief.indexOf("高重要度事件"))
        assertTrue(!brief.contains("普通的近况记录"))
    }

    private fun entry(
        id: String,
        content: String,
        importance: Float,
        pinned: Boolean,
        updatedAt: Long
    ) = com.lhy.yumerin.core.model.MemoryEntry(
        id = id, characterId = character.id, sourceChatId = chat.id, sourceMessageId = null,
        category = com.lhy.yumerin.core.model.MemoryCategory.LONG_TERM, content = content,
        confidence = 0.9f, reviewState = com.lhy.yumerin.core.model.MemoryReviewState.ACCEPTED,
        pinned = pinned, supersedesId = null, createdAt = 0L, updatedAt = updatedAt,
        reviewAt = null, importance = importance
    )

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
            id = "memory-character", name = "小雨", avatarUri = null,
            relationshipFraming = "亲密的朋友", setting = "测试", description = "",
            personality = "温柔", tone = "自然", backstory = "", interactionStyle = "简短",
            modelProfileId = "model-test", archived = false, createdAt = 0L, updatedAt = 0L
        )
        val chat = ChatThread(
            id = "memory-chat", kind = ChatKind.DIRECT, title = character.name,
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
