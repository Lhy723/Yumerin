package com.lhy.yumerin

import com.lhy.yumerin.core.model.AgentBudgetWarning
import com.lhy.yumerin.core.model.AgentCapabilityCall
import com.lhy.yumerin.core.model.AgentCapabilityCompleted
import com.lhy.yumerin.core.model.AgentCapabilityDescriptor
import com.lhy.yumerin.core.model.AgentCapabilityRequested
import com.lhy.yumerin.core.model.AgentConversationMessage
import com.lhy.yumerin.core.model.AgentModelStep
import com.lhy.yumerin.core.model.AgentObservationRecorded
import com.lhy.yumerin.core.model.AgentReply
import com.lhy.yumerin.core.model.AgentRequest
import com.lhy.yumerin.core.model.AgentRunBudget
import com.lhy.yumerin.core.model.AgentRunCompleted
import com.lhy.yumerin.core.model.AgentRunEvent
import com.lhy.yumerin.core.model.AgentRunFailed
import com.lhy.yumerin.core.model.AgentRunInterrupted
import com.lhy.yumerin.core.model.AgentRunStarted
import com.lhy.yumerin.core.model.AgentService
import com.lhy.yumerin.core.model.AgentTextDelta
import com.lhy.yumerin.core.model.AgentTrigger
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
import com.lhy.yumerin.domain.agent.AgentCapability
import com.lhy.yumerin.domain.agent.AgentCapabilityContext
import com.lhy.yumerin.domain.agent.AgentCapabilityResult
import com.lhy.yumerin.domain.agent.AgentRuntime
import com.lhy.yumerin.domain.agent.CalculatorCapability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AgentRuntimeEventsTest {
    @Test
    fun runEmitsTypedEventSequenceWithSharedTraceId() = runTest {
        val service = ScriptedAgentService(
            AgentModelStep.CapabilityRequests(
                calls = listOf(AgentCapabilityCall("call-1", "calculator", """{"expression":"6 * 7"}"""))
            ),
            AgentModelStep.FinalResponse(AgentReply("答案是 42。", model.id))
        )
        val runtime = AgentRuntime(service, listOf(CalculatorCapability()))
        val events = mutableListOf<AgentRunEvent>()

        val reply = runtime.run(request("帮我算一下 6 * 7"), AgentTrigger.USER_MESSAGE, onEvent = { events += it })

        assertEquals("答案是 42。", reply.text)
        val started = assertIs<AgentRunStarted>(events.first())
        assertIs<AgentCapabilityRequested>(events[1])
        assertIs<AgentCapabilityCompleted>(events[2])
        assertIs<AgentObservationRecorded>(events[3])
        val completed = assertIs<AgentRunCompleted>(events.last())
        assertEquals("答案是 42。", completed.reply.text)
        assertTrue(events.all { it.traceId == started.traceId })
        assertTrue(events.all { it.runId == started.runId })
    }

    @Test
    fun textDeltaEventsAccumulateAlongsideLegacyCallback() = runTest {
        val service = ScriptedAgentService(
            AgentModelStep.FinalResponse(AgentReply("慢慢说。", model.id))
        ) { accumulator -> accumulator("慢慢") ; accumulator("慢慢说。") }
        val runtime = AgentRuntime(service, emptyList())
        val events = mutableListOf<AgentRunEvent>()
        val legacyAccumulations = mutableListOf<String>()

        runtime.run(
            request("你好"),
            AgentTrigger.USER_MESSAGE,
            onEvent = { events += it },
            onAccumulatedText = { legacyAccumulations += it }
        )

        val deltas = events.filterIsInstance<AgentTextDelta>()
        assertEquals(listOf("慢慢", "慢慢说。"), deltas.map { it.accumulated })
        assertEquals(listOf("慢慢", "慢慢说。"), legacyAccumulations)
    }

    @Test
    fun budgetRejectionEmitsWarningAndBecomesObservation() = runTest {
        val service = ScriptedAgentService(
            AgentModelStep.CapabilityRequests(
                calls = listOf(
                    AgentCapabilityCall("call-1", "greedy", """{"expression":"1"}"""),
                    AgentCapabilityCall("call-2", "greedy", """{"expression":"2"}""")
                )
            ),
            AgentModelStep.FinalResponse(AgentReply("结果照旧。", model.id))
        )
        // Three units total: the first call consumes two, the second hits the cap.
        val runtime = AgentRuntime(service, listOf(GreedyCapability()), maxCapabilityBudgetUnits = 3)
        val events = mutableListOf<AgentRunEvent>()

        val reply = runtime.run(request("帮我算一下 1"), AgentTrigger.USER_MESSAGE, onEvent = { events += it })

        assertEquals("结果照旧。", reply.text)
        val warning = events.filterIsInstance<AgentBudgetWarning>().single()
        assertEquals(AgentRunBudget.CAPABILITY_COST_UNITS, warning.budget)
        val completions = events.filterIsInstance<AgentCapabilityCompleted>()
        assertEquals(2, completions.size)
        assertEquals(true, completions[0].successful)
        assertEquals(false, completions[1].successful)
    }

    @Test
    fun interruptionEmitsInterruptedEventAndRethrowsCancellation() = runTest {
        val service = CancellableAgentService()
        val runtime = AgentRuntime(service, emptyList())
        val events = mutableListOf<AgentRunEvent>()

        val job = launch {
            assertFailsWith<CancellationException> {
                runtime.run(request("长回复"), AgentTrigger.USER_MESSAGE, onEvent = { events += it })
            }
        }
        service.awaitStarted()
        job.cancel()
        job.join()

        val interrupted = events.filterIsInstance<AgentRunInterrupted>().single()
        assertEquals(com.lhy.yumerin.core.model.AgentRunInterruptReason.USER_STOPPED, interrupted.reason)
    }

    @Test
    fun capabilityFreeTurnUsesPersonaLayerWhileCapabilityTurnUsesExecutorLayer() = runTest {
        val plainService = ScriptedAgentService(AgentModelStep.FinalResponse(AgentReply("好的。", model.id)))
        AgentRuntime(plainService, emptyList()).run(request("你好"), AgentTrigger.USER_MESSAGE)
        val personaJoined = plainService.conversations.first().joinToString("\n") { it.content }
        assertTrue(personaJoined.contains("Interaction style:"))

        val toolService = ScriptedAgentService(
            AgentModelStep.CapabilityRequests(
                calls = listOf(AgentCapabilityCall("call-1", "calculator", """{"expression":"6 * 7"}"""))
            ),
            AgentModelStep.FinalResponse(AgentReply("答案是 42。", model.id))
        )
        AgentRuntime(toolService, listOf(CalculatorCapability())).run(
            request("帮我算一下 6 * 7"), AgentTrigger.USER_MESSAGE
        )
        val executorJoined = toolService.conversations.first().joinToString("\n") { it.content }
        assertTrue(executorJoined.contains("bounded companion task"))
        assertTrue(executorJoined.contains("Character name: 小雨"))
    }

    @Test
    fun failedRunEmitsRunFailedInsteadOfCompleted() = runTest {
        val service = ScriptedAgentService(
            AgentModelStep.FinalResponse(AgentReply("暂时不行。", model.id, statusMessage = "temporary-error"))
        )
        val runtime = AgentRuntime(service, emptyList())
        val events = mutableListOf<AgentRunEvent>()

        val reply = runtime.run(request("你好"), AgentTrigger.USER_MESSAGE, onEvent = { events += it })

        assertEquals("temporary-error", reply.statusMessage)
        assertTrue(events.filterIsInstance<AgentRunCompleted>().isEmpty())
        assertEquals("temporary-error", events.filterIsInstance<AgentRunFailed>().single().statusMessage)
    }

    private class GreedyCapability : AgentCapability {
        override val descriptor = AgentCapabilityDescriptor(
            name = "greedy",
            description = "Costs more than the run budget.",
            parametersJsonSchema = """{"type":"object","properties":{},"additionalProperties":false}""",
            budgetCostUnits = 2
        )

        override suspend fun execute(
            argumentsJson: String,
            context: AgentCapabilityContext
        ): AgentCapabilityResult = AgentCapabilityResult(true, "Result: 1")
    }

    private class ScriptedAgentService(
        vararg steps: AgentModelStep,
        private val onStep: (suspend (suspend (String) -> Unit) -> Unit)? = null
    ) : AgentService {
        private val remainingSteps = steps.toMutableList()
        val conversations = mutableListOf<List<AgentConversationMessage>>()

        override suspend fun reply(request: AgentRequest): AgentReply =
            error("The runtime should request model steps for this test.")

        override suspend fun nextStep(
            request: AgentRequest,
            conversation: List<AgentConversationMessage>,
            capabilities: List<AgentCapabilityDescriptor>,
            onAccumulatedText: suspend (String) -> Unit
        ): AgentModelStep {
            conversations += conversation
            val step = remainingSteps.removeAt(0)
            if (step is AgentModelStep.FinalResponse) onStep?.invoke(onAccumulatedText)
            return step
        }
    }

    private class CancellableAgentService : AgentService {
        private val started = kotlinx.coroutines.CompletableDeferred<Unit>()

        override suspend fun reply(request: AgentRequest): AgentReply = error("Use stream for this test.")

        override suspend fun nextStep(
            request: AgentRequest,
            conversation: List<AgentConversationMessage>,
            capabilities: List<AgentCapabilityDescriptor>,
            onAccumulatedText: suspend (String) -> Unit
        ): AgentModelStep = AgentModelStep.FinalResponse(stream(request, onAccumulatedText))

        override suspend fun stream(request: AgentRequest, onAccumulatedText: suspend (String) -> Unit): AgentReply {
            started.complete(Unit)
            kotlinx.coroutines.delay(60_000)
            return AgentReply("不该到这里。", request.model.id)
        }

        suspend fun awaitStarted() = started.await()
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
