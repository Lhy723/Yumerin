package com.lhy.yumerin

import com.lhy.yumerin.core.model.AgentCapabilityCall
import com.lhy.yumerin.core.model.AgentConversationMessage
import com.lhy.yumerin.core.model.AgentModelStep
import com.lhy.yumerin.core.model.AgentReply
import com.lhy.yumerin.core.model.AgentRequest
import com.lhy.yumerin.core.model.AgentService
import com.lhy.yumerin.core.model.CharacterProfile
import com.lhy.yumerin.core.model.ChatKind
import com.lhy.yumerin.core.model.ChatMessage
import com.lhy.yumerin.core.model.ChatThread
import com.lhy.yumerin.core.model.GenerationParameters
import com.lhy.yumerin.core.model.MessageSenderType
import com.lhy.yumerin.core.model.MessageType
import com.lhy.yumerin.core.model.ModelProfile
import com.lhy.yumerin.core.model.ModelProviderKind
import com.lhy.yumerin.core.model.AgentTrigger
import com.lhy.yumerin.core.model.SummaryMaintenanceMode
import com.lhy.yumerin.domain.agent.AgentRuntime
import com.lhy.yumerin.domain.agent.CalculatorCapability
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentRuntimeTest {
    @Test
    fun builtInCapabilityObservationReturnsToTheSameRunBeforeTheFinalReply() = runTest {
        val service = ScriptedAgentService(
            AgentModelStep.CapabilityRequests(
                calls = listOf(
                    AgentCapabilityCall(
                        id = "call-1",
                        name = "calculator",
                        argumentsJson = """{"expression":"6 * 7"}"""
                    )
                )
            ),
            AgentModelStep.FinalResponse(AgentReply("答案是 42。", model.id))
        )
        val runtime = AgentRuntime(service, listOf(CalculatorCapability()))

        val reply = runtime.run(request("帮我算一下 6 * 7"), AgentTrigger.USER_MESSAGE)

        assertEquals("答案是 42。", reply.text)
        assertEquals(2, service.conversations.size)
        assertTrue(service.capabilityLists.first().any { it.name == "calculator" })
        assertTrue(
            service.conversations.last().any {
                it.role == "tool" && it.name == "calculator" &&
                    it.capabilityCallId == "call-1" && it.content == "Result: 42"
            }
        )
    }

    private class ScriptedAgentService(vararg steps: AgentModelStep) : AgentService {
        private val remainingSteps = steps.toMutableList()
        val conversations = mutableListOf<List<AgentConversationMessage>>()
        val capabilityLists = mutableListOf<List<com.lhy.yumerin.core.model.AgentCapabilityDescriptor>>()

        override suspend fun reply(request: AgentRequest): AgentReply =
            error("The runtime should request model steps for this test.")

        override suspend fun nextStep(
            request: AgentRequest,
            conversation: List<AgentConversationMessage>,
            capabilities: List<com.lhy.yumerin.core.model.AgentCapabilityDescriptor>,
            onAccumulatedText: suspend (String) -> Unit
        ): AgentModelStep {
            conversations += conversation
            capabilityLists += capabilities
            return remainingSteps.removeAt(0)
        }
    }

    private fun request(text: String): AgentRequest {
        val message = ChatMessage(
            id = "message-user", chatId = chat.id, senderId = null,
            senderType = MessageSenderType.USER, senderName = "我", senderAvatarUri = null,
            text = text, timestamp = 1L, type = MessageType.TEXT, isProactive = false, isRead = true
        )
        return AgentRequest(
            chat = chat,
            character = character,
            messages = listOf(message),
            memories = emptyList(),
            model = model
        )
    }

    private companion object {
        val character = CharacterProfile(
            id = "character-test", name = "小雨", avatarUri = null,
            relationshipFraming = "亲密的朋友", setting = "测试", description = "",
            personality = "温柔", tone = "自然", backstory = "", interactionStyle = "简短",
            modelProfileId = "model-test", archived = false, createdAt = 0L, updatedAt = 0L
        )
        val chat = ChatThread(
            id = "chat-test", kind = ChatKind.DIRECT, title = character.name,
            characterId = character.id, groupId = null, avatarUri = null,
            lastMessagePreview = "", lastActivityAt = 0L, unreadCount = 0,
            pinned = false, muted = false, shortTermSummary = "",
            summaryMaintenanceMode = SummaryMaintenanceMode.AUTO
        )
        val model = ModelProfile(
            id = "model-test", displayName = "测试模型", providerKind = ModelProviderKind.OPENAI_COMPATIBLE,
            providerName = "test", baseUrl = null, modelId = "test", credentialAlias = null,
            contextLength = 8_192, contextLengthMode = com.lhy.yumerin.core.model.ContextLengthMode.KNOWN,
            generationParameters = GenerationParameters(), localModelId = null, localModelVersion = null,
            enabled = true, createdAt = 0L, updatedAt = 0L
        )
    }
}
