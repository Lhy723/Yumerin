package com.lhy.yumerin

import com.lhy.yumerin.core.model.*
import com.lhy.yumerin.data.memory.InMemoryAppGraph
import com.lhy.yumerin.domain.agent.AgentRuntime
import com.lhy.yumerin.domain.agent.CalculatorCapability
import com.lhy.yumerin.domain.agent.CharacterStateCoordinator
import com.lhy.yumerin.domain.usecase.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DelegatedAgentGoalAcceptanceTest {
    @Test
    fun goalPlanWaitsForUserThenResumesAndCompletesWithPersistedProgress() = runTest {
        val graph = InMemoryAppGraph()
        graph.characters.saveCharacter(character)
        graph.chats.saveChat(chat)
        graph.models.saveProfile(model)
        graph.settings.saveSettings(AppSettings(defaultModelProfileId = model.id, autoMemoryEnabled = false))

        val service = ScriptedGoalAgentService(
            AgentModelStep.FinalResponse(AgentReply("1. 核对计算结果\n2. 整理并汇报", model.id)),
            AgentModelStep.CapabilityRequests(
                listOf(AgentCapabilityCall("goal-calculation", "calculator", """{"expression":"6 * 7"}"""))
            ),
            AgentModelStep.FinalResponse(
                AgentReply(
                    "[GOAL_STATUS: NEEDS_INPUT]\nQUESTION: 你希望我把结果整理成表格还是清单？\n我已经确认了计算结果。",
                    model.id
                )
            ),
            AgentModelStep.FinalResponse(AgentReply("[GOAL_STATUS: STEP_DONE]\n我按表格整理好了。", model.id)),
            AgentModelStep.FinalResponse(AgentReply("[GOAL_STATUS: GOAL_DONE]\n两步都完成，结果已整理好。", model.id))
        )
        val runtime = AgentRuntime(service, listOf(CalculatorCapability()))
        val coordinator = CharacterStateCoordinator(graph.characterRuntimeStates)
        val create = CreateDelegatedAgentGoalUseCase(
            graph.characters, graph.chats, graph.delegatedAgentGoals, graph.delegatedAgentGoalScheduler
        )
        val process = ProcessDelegatedAgentGoalUseCase(
            graph.characters, graph.chats, graph.memories, graph.models, graph.delegatedAgentGoals,
            runtime, graph.settings, coordinator
        )
        val resume = ResumeDelegatedAgentGoalUseCase(
            graph.delegatedAgentGoals, graph.delegatedAgentGoalScheduler, graph.chats
        )
        val pause = PauseDelegatedAgentGoalUseCase(graph.delegatedAgentGoals, graph.delegatedAgentGoalScheduler)
        val cancel = CancelDelegatedAgentGoalUseCase(graph.delegatedAgentGoals, graph.delegatedAgentGoalScheduler)

        val created = assertIs<CreateDelegatedAgentGoalResult.Created>(
            create(character.id, chat.id, "整理计算结果", "计算 6 * 7 并整理成可读结果")
        )
        val goalId = created.goal.id

        assertIs<ProcessDelegatedAgentGoalResult.Progressed>(process(goalId))
        val planned = assertNotNull(graph.delegatedAgentGoals.getGoal(goalId))
        assertEquals(2, planned.steps.size)
        assertEquals(DelegatedAgentGoalStatus.RUNNING, planned.status)

        assertTrue(pause(goalId))
        assertEquals(DelegatedAgentGoalStatus.PAUSED, graph.delegatedAgentGoals.getGoal(goalId)?.status)
        assertIs<ProcessDelegatedAgentGoalResult.Skipped>(process(goalId))
        assertTrue(resume(goalId))

        assertIs<ProcessDelegatedAgentGoalResult.WaitingForUser>(process(goalId))
        val waiting = assertNotNull(graph.delegatedAgentGoals.getGoal(goalId))
        assertEquals(DelegatedAgentGoalStatus.WAITING_FOR_USER, waiting.status)
        assertEquals("你希望我把结果整理成表格还是清单？", waiting.pendingQuestion)
        assertTrue(graph.chats.getMessages(chat.id).any { it.text.contains("${waiting.pendingQuestion}") })

        assertTrue(resume(goalId, "请整理成表格。"))
        assertIs<ProcessDelegatedAgentGoalResult.Progressed>(process(goalId))
        assertIs<ProcessDelegatedAgentGoalResult.Completed>(process(goalId))

        val completed = assertNotNull(graph.delegatedAgentGoals.getGoal(goalId))
        assertEquals(DelegatedAgentGoalStatus.COMPLETED, completed.status)
        assertEquals(2, completed.steps.size)
        assertTrue(completed.steps.all { it.status == DelegatedAgentGoalStepStatus.COMPLETED })
        assertEquals(null, completed.pendingQuestion)
        val messages = graph.chats.getMessages(chat.id)
        assertEquals(1, messages.count { it.id.startsWith("delegated-answer-$goalId-") })
        assertEquals(4, messages.count { it.id.startsWith("delegated-message-$goalId-") })
        assertTrue(service.conversations.any { conversation -> conversation.any { it.role == "tool" && it.content == "Result: 42" } })
        assertTrue(service.requests.any { it.goalContext?.contains("Desired outcome") == true })
        assertTrue(service.requests.any { request ->
            request.goalContext.orEmpty().let { context ->
                context.contains("The user supplied this answer") && context.contains("请整理成表格")
            }
        })

        val cancelled = assertIs<CreateDelegatedAgentGoalResult.Created>(
            create(character.id, chat.id, "暂存目标", "整理一份待确认的清单")
        )
        assertTrue(cancel(cancelled.goal.id))
        assertEquals(DelegatedAgentGoalStatus.CANCELLED, graph.delegatedAgentGoals.getGoal(cancelled.goal.id)?.status)
        assertIs<ProcessDelegatedAgentGoalResult.Skipped>(process(cancelled.goal.id))
    }

    private class ScriptedGoalAgentService(vararg steps: AgentModelStep) : AgentService {
        private val remainingSteps = steps.toMutableList()
        val requests = mutableListOf<AgentRequest>()
        val conversations = mutableListOf<List<AgentConversationMessage>>()

        override suspend fun reply(request: AgentRequest): AgentReply = error("Use nextStep for goal runs.")

        override suspend fun nextStep(
            request: AgentRequest,
            conversation: List<AgentConversationMessage>,
            capabilities: List<AgentCapabilityDescriptor>,
            onAccumulatedText: suspend (String) -> Unit
        ): AgentModelStep {
            requests += request
            conversations += conversation
            return remainingSteps.removeAt(0)
        }
    }

    private companion object {
        val character = CharacterProfile(
            id = "goal-character", name = "小雨", avatarUri = null,
            relationshipFraming = "温柔的陪伴者", setting = "雨夜城市",
            description = "会认真倾听", personality = "温柔、敏锐", tone = "自然、简短",
            backstory = "", interactionStyle = "尊重边界", modelProfileId = "goal-model",
            archived = false, createdAt = 0L, updatedAt = 0L
        )
        val chat = ChatThread(
            id = "goal-chat", kind = ChatKind.DIRECT, title = character.name,
            characterId = character.id, groupId = null, avatarUri = null,
            lastMessagePreview = "", lastActivityAt = 0L, unreadCount = 0,
            pinned = false, muted = false, shortTermSummary = "开始对话。",
            summaryMaintenanceMode = SummaryMaintenanceMode.AUTO
        )
        val model = ModelProfile(
            id = "goal-model", displayName = "测试模型", providerKind = ModelProviderKind.LITERT_LM,
            providerName = "test", baseUrl = null, modelId = "test", credentialAlias = null,
            contextLength = 8_192, contextLengthMode = ContextLengthMode.KNOWN,
            generationParameters = GenerationParameters(), localModelId = "local-test",
            localModelVersion = "1", enabled = true, createdAt = 0L, updatedAt = 0L
        )
    }
}
