package com.lhy.yumerin.domain.usecase

import com.lhy.yumerin.core.model.*
import com.lhy.yumerin.domain.agent.AgentRuntime
import com.lhy.yumerin.domain.agent.CharacterStateCoordinator
import com.lhy.yumerin.domain.agent.CharacterMemoryRetriever
import com.lhy.yumerin.domain.background.DelegatedAgentGoalScheduler
import com.lhy.yumerin.domain.repository.*
import com.lhy.yumerin.domain.safety.SafetyPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

sealed interface CreateDelegatedAgentGoalResult {
    data class Created(val goal: DelegatedAgentGoal) : CreateDelegatedAgentGoalResult
    data object InvalidGoal : CreateDelegatedAgentGoalResult
    data object MissingCharacter : CreateDelegatedAgentGoalResult
    data object InvalidChat : CreateDelegatedAgentGoalResult
    data object UnsafeGoal : CreateDelegatedAgentGoalResult
    data object SchedulerUnavailable : CreateDelegatedAgentGoalResult
}

class CreateDelegatedAgentGoalUseCase(
    private val characters: CharacterRepository,
    private val chats: ChatRepository,
    private val goals: DelegatedAgentGoalRepository,
    private val scheduler: DelegatedAgentGoalScheduler
) {
    suspend operator fun invoke(
        characterId: EntityId,
        chatId: EntityId,
        title: String,
        outcome: String,
        constraints: String = ""
    ): CreateDelegatedAgentGoalResult {
        val cleanTitle = title.trim()
        val cleanOutcome = outcome.trim()
        val cleanConstraints = constraints.trim()
        if (cleanTitle.isBlank() || cleanTitle.length > 80 || cleanOutcome.isBlank() || cleanOutcome.length > 1_000 || cleanConstraints.length > 1_500) {
            return CreateDelegatedAgentGoalResult.InvalidGoal
        }
        if (!SafetyPolicy.evaluate(cleanOutcome).allowed || (cleanConstraints.isNotBlank() && !SafetyPolicy.evaluate(cleanConstraints).allowed)) {
            return CreateDelegatedAgentGoalResult.UnsafeGoal
        }
        val character = characters.getCharacter(characterId)?.takeUnless { it.archived || it.deletedAt != null }
            ?: return CreateDelegatedAgentGoalResult.MissingCharacter
        val chat = chats.getChat(chatId)?.takeIf { it.kind == ChatKind.DIRECT && it.characterId == character.id }
            ?: return CreateDelegatedAgentGoalResult.InvalidChat

        val now = useCaseNowMillis()
        val goal = DelegatedAgentGoal(
            id = newEntityId("delegated-goal"),
            characterId = character.id,
            chatId = chat.id,
            title = cleanTitle,
            outcome = cleanOutcome,
            constraints = cleanConstraints,
            createdAt = now,
            updatedAt = now
        )
        return try {
            goals.saveGoal(goal)
            scheduler.enqueue(goal.id)
            CreateDelegatedAgentGoalResult.Created(goal)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            goals.saveGoal(goal.copy(status = DelegatedAgentGoalStatus.FAILED, failureReason = "系统未能安排后台任务", updatedAt = useCaseNowMillis()))
            CreateDelegatedAgentGoalResult.SchedulerUnavailable
        }
    }
}

sealed interface ProcessDelegatedAgentGoalResult {
    data class Progressed(val messageId: EntityId?) : ProcessDelegatedAgentGoalResult
    data class WaitingForUser(val messageId: EntityId) : ProcessDelegatedAgentGoalResult
    data class Completed(val messageId: EntityId) : ProcessDelegatedAgentGoalResult
    data object Retry : ProcessDelegatedAgentGoalResult
    data object Paused : ProcessDelegatedAgentGoalResult
    data object Skipped : ProcessDelegatedAgentGoalResult
    data class Failed(val reason: String) : ProcessDelegatedAgentGoalResult
}

class ProcessDelegatedAgentGoalUseCase(
    private val characters: CharacterRepository,
    private val chats: ChatRepository,
    private val memories: MemoryRepository,
    private val models: ModelProfileRepository,
    private val goals: DelegatedAgentGoalRepository,
    private val agentRuntime: AgentRuntime,
    private val settingsRepository: SettingsRepository,
    private val characterStateCoordinator: CharacterStateCoordinator
) {
    suspend operator fun invoke(goalId: EntityId): ProcessDelegatedAgentGoalResult {
        var goal = goals.getGoal(goalId) ?: return ProcessDelegatedAgentGoalResult.Skipped
        if (goal.status in TERMINAL_STATUSES || goal.status == DelegatedAgentGoalStatus.PAUSED || goal.status == DelegatedAgentGoalStatus.WAITING_FOR_USER) {
            return ProcessDelegatedAgentGoalResult.Skipped
        }
        if (!SafetyPolicy.evaluate(goal.outcome).allowed || (goal.constraints.isNotBlank() && !SafetyPolicy.evaluate(goal.constraints).allowed)) {
            return fail(goal, "目标内容不再符合安全要求")
        }
        val character = characters.getCharacter(goal.characterId)?.takeUnless { it.archived || it.deletedAt != null }
            ?: return fail(goal, "角色已删除或归档")
        val chat = chats.getChat(goal.chatId)?.takeIf { it.kind == ChatKind.DIRECT && it.characterId == character.id }
            ?: return fail(goal, "目标聊天已不存在")
        val settings = settingsRepository.observeSettings().first()
        val modelId = character.modelProfileId ?: settings.defaultModelProfileId
        val model = modelId?.let { models.getProfile(it) }?.takeIf { it.enabled && it.deletedAt == null }
            ?: return fail(goal, "角色没有可用模型")

        if (goal.steps.isEmpty()) {
            val planningReply = runAgent(goal, character, chat, model, planningContext(goal), settings.agentVisibility)
            if (planningReply.statusMessage.isRetryableGoalFailure()) return ProcessDelegatedAgentGoalResult.Retry
            if (planningReply.statusMessage != null) return fail(goal, "计划生成失败：${planningReply.statusMessage}")
            val plannedSteps = parsePlan(goal, planningReply.text)
            if (plannedSteps.isEmpty()) return fail(goal, "Agent 未能生成有效计划")
            val planSummary = "我把这个目标拆成了 ${plannedSteps.size} 步：${plannedSteps.joinToString("；") { it.title }}".take(MAX_SUMMARY_LENGTH)
            goal = goal.copy(
                status = DelegatedAgentGoalStatus.RUNNING,
                steps = plannedSteps,
                latestSummary = planSummary,
                updatedAt = useCaseNowMillis()
            )
            goals.saveGoal(goal)
            val messageId = appendProgress(goal, character, planSummary, "plan")
            return ProcessDelegatedAgentGoalResult.Progressed(messageId)
        }

        val stepIndex = goal.steps.indexOfFirst { it.status != DelegatedAgentGoalStepStatus.COMPLETED }
        if (stepIndex < 0) return complete(goal, character, "已完成所有计划步骤。")
        val current = goals.getGoal(goalId) ?: return ProcessDelegatedAgentGoalResult.Skipped
        if (current.status == DelegatedAgentGoalStatus.PAUSED || current.status == DelegatedAgentGoalStatus.CANCELLED || current.status == DelegatedAgentGoalStatus.WAITING_FOR_USER) {
            return ProcessDelegatedAgentGoalResult.Skipped
        }
        goal = current
        val step = goal.steps[stepIndex]
        if (step.status == DelegatedAgentGoalStepStatus.FAILED) return fail(goal, "步骤未能完成：${step.title}")
        val runningStep = step.copy(status = DelegatedAgentGoalStepStatus.RUNNING, attempts = step.attempts + 1)
        val runningGoal = goal.copy(
            status = DelegatedAgentGoalStatus.RUNNING,
            steps = goal.steps.toMutableList().also { it[stepIndex] = runningStep },
            updatedAt = useCaseNowMillis()
        )
        goals.saveGoal(runningGoal)

        val context = stepContext(runningGoal, runningStep)
        val reply = runAgent(runningGoal, character, chat, model, context, settings.agentVisibility)
        if (reply.statusMessage.isRetryableGoalFailure()) return ProcessDelegatedAgentGoalResult.Retry
        if (reply.statusMessage != null) return fail(runningGoal, "步骤执行失败：${reply.statusMessage}")
        if (reply.text.isBlank()) return fail(runningGoal, "Agent 返回了空结果")

        val afterRun = goals.getGoal(goalId) ?: return ProcessDelegatedAgentGoalResult.Skipped
        if (afterRun.status == DelegatedAgentGoalStatus.PAUSED || afterRun.status == DelegatedAgentGoalStatus.CANCELLED) {
            return ProcessDelegatedAgentGoalResult.Skipped
        }
        val parsed = parseStepReply(reply.text)
        val now = useCaseNowMillis()
        val updatedSteps = afterRun.steps.toMutableList()
        val afterStep = updatedSteps.getOrNull(stepIndex) ?: return ProcessDelegatedAgentGoalResult.Skipped
        val summary = parsed.summary.ifBlank { reply.text.trim() }.take(MAX_SUMMARY_LENGTH)
        return when (parsed.status) {
            StepDirective.NEEDS_INPUT -> {
                val question = (parsed.question ?: summary.takeIf(String::isNotBlank) ?: "请补充完成下一步所需的信息。")
                    .take(MAX_QUESTION_LENGTH)
                updatedSteps[stepIndex] = afterStep.copy(status = DelegatedAgentGoalStepStatus.PENDING)
                val waiting = afterRun.copy(
                    status = DelegatedAgentGoalStatus.WAITING_FOR_USER,
                    steps = updatedSteps,
                    latestSummary = summary,
                    pendingQuestion = question,
                    resumeInput = null,
                    updatedAt = now
                )
                goals.saveGoal(waiting)
                val messageId = appendProgress(waiting, character, "我需要你补充一点信息：$question", "question-${afterStep.id}-${afterStep.attempts}")
                ProcessDelegatedAgentGoalResult.WaitingForUser(messageId)
            }
            StepDirective.REPLAN -> {
                val replacementTitles = parseNumberedTitles(reply.text)
                if (afterRun.replanCount >= MAX_REPLANS || replacementTitles.isEmpty()) {
                    val question = if (afterRun.replanCount >= MAX_REPLANS) {
                        "我已经在当前目标范围内调整过两次计划，接下来需要你决定如何继续。"
                    } else {
                        "当前步骤遇到了阻碍。你希望我怎样调整计划？"
                    }
                    updatedSteps[stepIndex] = afterStep.copy(status = DelegatedAgentGoalStepStatus.PENDING)
                    val waiting = afterRun.copy(
                        status = DelegatedAgentGoalStatus.WAITING_FOR_USER,
                        steps = updatedSteps,
                        latestSummary = "当前步骤遇到阻碍，暂停等待你的决定。",
                        pendingQuestion = question,
                        resumeInput = null,
                        updatedAt = now
                    )
                    goals.saveGoal(waiting)
                    val messageId = appendProgress(waiting, character, question, "replan-question-${afterStep.id}-${afterStep.attempts}")
                    ProcessDelegatedAgentGoalResult.WaitingForUser(messageId)
                } else {
                    val completedSteps = afterRun.steps.take(stepIndex)
                    val boundedReplacementTitles = replacementTitles.take((MAX_PLAN_STEPS - completedSteps.size).coerceAtLeast(1))
                    val replacementSteps = boundedReplacementTitles.mapIndexed { index, title ->
                        DelegatedAgentGoalStep(
                            id = newEntityId("delegated-step"),
                            goalId = goalId,
                            order = completedSteps.size + index,
                            title = title.take(MAX_STEP_TITLE_LENGTH)
                        )
                    }
                    val updated = afterRun.copy(
                        status = DelegatedAgentGoalStatus.QUEUED,
                        steps = completedSteps + replacementSteps,
                        replanCount = afterRun.replanCount + 1,
                        latestSummary = "根据执行情况调整了后续计划。",
                        pendingQuestion = null,
                        resumeInput = null,
                        updatedAt = now
                    )
                    val planSummary = "我根据进展调整了接下来的计划：${replacementSteps.joinToString("；") { it.title }}".take(MAX_SUMMARY_LENGTH)
                    val withSummary = updated.copy(latestSummary = planSummary)
                    goals.saveGoal(withSummary)
                    val messageId = appendProgress(withSummary, character, planSummary, "replan-${updated.replanCount}")
                    ProcessDelegatedAgentGoalResult.Progressed(messageId)
                }
            }
            StepDirective.GOAL_DONE -> {
                updatedSteps[stepIndex] = afterStep.copy(status = DelegatedAgentGoalStepStatus.COMPLETED, summary = summary)
                for (index in updatedSteps.indices) {
                    if (updatedSteps[index].status != DelegatedAgentGoalStepStatus.COMPLETED) {
                        updatedSteps[index] = updatedSteps[index].copy(status = DelegatedAgentGoalStepStatus.COMPLETED, summary = "Agent 确认目标结果已经达成。")
                    }
                }
                val completed = afterRun.copy(
                    status = DelegatedAgentGoalStatus.COMPLETED,
                    steps = updatedSteps,
                    latestSummary = summary,
                    pendingQuestion = null,
                    resumeInput = null,
                    updatedAt = now,
                    completedAt = now
                )
                goals.saveGoal(completed)
                val messageId = appendProgress(completed, character, summary, "completed")
                ProcessDelegatedAgentGoalResult.Completed(messageId)
            }
            StepDirective.STEP_DONE -> {
                updatedSteps[stepIndex] = afterStep.copy(status = DelegatedAgentGoalStepStatus.COMPLETED, summary = summary)
                val isComplete = updatedSteps.all { it.status == DelegatedAgentGoalStepStatus.COMPLETED }
                val updated = afterRun.copy(
                    status = if (isComplete) DelegatedAgentGoalStatus.COMPLETED else DelegatedAgentGoalStatus.QUEUED,
                    steps = updatedSteps,
                    latestSummary = summary,
                    pendingQuestion = null,
                    resumeInput = null,
                    updatedAt = now,
                    completedAt = now.takeIf { isComplete }
                )
                goals.saveGoal(updated)
                val messageId = appendProgress(updated, character, summary, "step-${afterStep.id}")
                if (isComplete) ProcessDelegatedAgentGoalResult.Completed(messageId)
                else ProcessDelegatedAgentGoalResult.Progressed(messageId)
            }
        }
    }

    suspend fun markFailed(goalId: EntityId, reason: String) {
        val goal = goals.getGoal(goalId) ?: return
        if (goal.status !in TERMINAL_STATUSES) fail(goal, reason)
    }

    private suspend fun runAgent(
        goal: DelegatedAgentGoal,
        character: CharacterProfile,
        chat: ChatThread,
        model: ModelProfile,
        taskContext: String,
        visibility: AgentVisibility
    ): AgentReply {
        val now = useCaseNowMillis()
        val emotionalContext = characterStateCoordinator.prepare(
            characterId = character.id,
            chatId = chat.id,
            sourceMessageId = null,
            userText = goal.resumeInput,
            nowMillis = now
        ).emotionalContext
        val taskMessage = ChatMessage(
            id = "goal-context-${goal.id}", chatId = chat.id, senderId = null,
            senderType = MessageSenderType.USER, senderName = "我", senderAvatarUri = null,
            text = "我委托你完成这个目标：${goal.outcome}", timestamp = goal.createdAt,
            type = MessageType.TEXT, isProactive = false, isRead = true
        )
        return agentRuntime.run(
            AgentRequest(
                chat = chat,
                character = character,
                messages = chats.getMessages(chat.id).takeLast(MAX_CONTEXT_MESSAGES) + taskMessage,
                memories = CharacterMemoryRetriever.retrieve(
                    memories.observeMemories(character.id).first(),
                    "${goal.outcome}\n$taskContext".take(MAX_MEMORY_QUERY_LENGTH),
                    now
                ),
                model = model,
                goalContext = taskContext,
                visibility = visibility,
                emotionalContext = emotionalContext
            ),
            trigger = AgentTrigger.DELEGATED_GOAL
        )
    }

    private suspend fun appendProgress(goal: DelegatedAgentGoal, character: CharacterProfile, text: String, suffix: String): EntityId {
        val id = "delegated-message-${goal.id}-$suffix"
        chats.appendMessage(
            ChatMessage(
                id = id,
                chatId = goal.chatId,
                senderId = character.id,
                senderType = MessageSenderType.CHARACTER,
                senderName = character.name,
                senderAvatarUri = character.avatarUri,
                text = "【${goal.title}】${text.take(MAX_SUMMARY_LENGTH)}",
                timestamp = useCaseNowMillis(),
                type = MessageType.TEXT,
                isProactive = false,
                isRead = false,
                deliveryState = MessageDeliveryState.SENT,
                responseState = AiResponseState.COMPLETED
            )
        )
        return id
    }

    private suspend fun complete(goal: DelegatedAgentGoal, character: CharacterProfile, summary: String): ProcessDelegatedAgentGoalResult {
        val now = useCaseNowMillis()
        val completed = goal.copy(status = DelegatedAgentGoalStatus.COMPLETED, latestSummary = summary, completedAt = now, updatedAt = now)
        goals.saveGoal(completed)
        val id = appendProgress(completed, character, summary, "completed")
        return ProcessDelegatedAgentGoalResult.Completed(id)
    }

    private suspend fun fail(goal: DelegatedAgentGoal, reason: String): ProcessDelegatedAgentGoalResult {
        val current = goals.getGoal(goal.id) ?: return ProcessDelegatedAgentGoalResult.Skipped
        if (current.status in TERMINAL_STATUSES || current.status == DelegatedAgentGoalStatus.PAUSED) return ProcessDelegatedAgentGoalResult.Skipped
        val now = useCaseNowMillis()
        goals.saveGoal(
            current.copy(
                status = DelegatedAgentGoalStatus.FAILED,
                steps = current.steps.map { if (it.status == DelegatedAgentGoalStepStatus.RUNNING) it.copy(status = DelegatedAgentGoalStepStatus.FAILED) else it },
                updatedAt = now,
                completedAt = now,
                failureReason = reason.take(240)
            )
        )
        return ProcessDelegatedAgentGoalResult.Failed(reason)
    }

    private fun planningContext(goal: DelegatedAgentGoal): String = buildString {
        append("Create a practical plan with at most $MAX_PLAN_STEPS short numbered steps. Do not execute the work yet. You may later replan within the same outcome and constraints if a step is blocked.\n")
        append("Goal title: ${goal.title}\nDesired outcome: ${goal.outcome}\n")
        if (goal.constraints.isNotBlank()) append("Constraints: ${goal.constraints}\n")
        append("Use only available read-only capabilities. If the goal needs unavailable external access or a consequential action, ask the user instead of claiming it was done. Return only the numbered plan.")
    }

    private fun stepContext(goal: DelegatedAgentGoal, step: DelegatedAgentGoalStep): String = buildString {
        append("Outcome: ${goal.outcome}\n")
        if (goal.constraints.isNotBlank()) append("Constraints: ${goal.constraints}\n")
        append("Plan:\n")
        goal.steps.forEach { append("${it.order + 1}. ${it.title}${it.summary?.let { summary -> " — $summary" }.orEmpty()}\n") }
        append("Current step (${step.order + 1}/${goal.steps.size}): ${step.title}\n")
        goal.steps.filter { it.status == DelegatedAgentGoalStepStatus.COMPLETED }.forEach { done ->
            done.summary?.let { append("Completed ${done.order + 1}: $it\n") }
        }
        goal.latestSummary?.let { append("Last progress: $it\n") }
        goal.resumeInput?.let { append("The user supplied this answer to the previous question: ${it.take(MAX_INPUT_LENGTH)}\n") }
        append("Perform only the current step using available capabilities. If information or user action is needed, ask one concise question. If the step is blocked but the same outcome can be reached another way within the constraints, you may emit a replacement numbered plan with [GOAL_STATUS: REPLAN]; this is allowed at most $MAX_REPLANS times.\n")
        append("At the beginning of your response, emit exactly one control line: [GOAL_STATUS: STEP_DONE], [GOAL_STATUS: GOAL_DONE], [GOAL_STATUS: NEEDS_INPUT], or [GOAL_STATUS: REPLAN]. If asking, add a line QUESTION: followed by the question. Then give a short user-facing summary. Never claim an external write or action happened.")
    }

    private fun parsePlan(goal: DelegatedAgentGoal, response: String): List<DelegatedAgentGoalStep> {
        val parsed = parseNumberedTitles(response)
        val titles = parsed.ifEmpty { listOf("完成目标：${goal.outcome}") }
        return titles.mapIndexed { index, value ->
            DelegatedAgentGoalStep(
                id = newEntityId("delegated-step"),
                goalId = goal.id,
                order = index,
                title = value.take(MAX_STEP_TITLE_LENGTH)
            )
        }
    }

    private fun parseNumberedTitles(response: String): List<String> {
        val numberedLine = Regex("^\\s*(?:\\d{1,2}[.)]|[-*])\\s+(.+?)\\s*$")
        return response.lineSequence().mapNotNull { line ->
            numberedLine.find(line)?.groupValues?.get(1)?.trim()?.takeIf(String::isNotBlank)
        }.take(MAX_PLAN_STEPS).toList()
    }

    private fun parseStepReply(response: String): ParsedStepReply {
        val marker = Regex("(?im)^\\s*\\[GOAL_STATUS:\\s*(STEP_DONE|GOAL_DONE|NEEDS_INPUT|REPLAN)]\\s*$").find(response)
        val question = Regex("(?im)^\\s*QUESTION:\\s*(.+?)\\s*$").find(response)?.groupValues?.get(1)?.trim()
        val summary = response
            .replace(Regex("(?im)^\\s*\\[GOAL_STATUS:\\s*(?:STEP_DONE|GOAL_DONE|NEEDS_INPUT|REPLAN)]\\s*$"), "")
            .replace(Regex("(?im)^\\s*QUESTION:\\s*.+$"), "")
            .trim()
        val markerValue = marker?.groupValues?.get(1)?.uppercase()
        val blockedOrQuestioned = question != null || Regex("(?i)(cannot|can't|unable|blocked|need(?:s)? more|please provide|please decide|无法|不能|无法访问|缺少|需要你|请补充|请提供|请决定)").containsMatchIn(response)
        val directive = when (markerValue) {
            "GOAL_DONE" -> StepDirective.GOAL_DONE
            "NEEDS_INPUT" -> StepDirective.NEEDS_INPUT
            "REPLAN" -> StepDirective.REPLAN
            "STEP_DONE" -> StepDirective.STEP_DONE
            null -> if (blockedOrQuestioned) StepDirective.NEEDS_INPUT else StepDirective.STEP_DONE
            else -> StepDirective.STEP_DONE
        }
        return ParsedStepReply(directive, question, summary)
    }

    private fun String?.isRetryableGoalFailure(): Boolean = when {
        this == "agent-chat-busy" || this == "agent-time-limit" || this == "remote-exception" || this == "remote-http-429" -> true
        this == null -> false
        else -> this.startsWith("remote-http-5")
    }

    private enum class StepDirective { STEP_DONE, GOAL_DONE, NEEDS_INPUT, REPLAN }
    private data class ParsedStepReply(val status: StepDirective, val question: String?, val summary: String)

    private companion object {
        const val MAX_PLAN_STEPS = 6
        const val MAX_REPLANS = 2
        const val MAX_STEP_TITLE_LENGTH = 240
        const val MAX_SUMMARY_LENGTH = 900
        const val MAX_QUESTION_LENGTH = 500
        const val MAX_INPUT_LENGTH = 1_000
        const val MAX_MEMORY_QUERY_LENGTH = 2_000
        const val MAX_CONTEXT_MESSAGES = 40
        val TERMINAL_STATUSES = setOf(DelegatedAgentGoalStatus.COMPLETED, DelegatedAgentGoalStatus.CANCELLED, DelegatedAgentGoalStatus.FAILED)
    }
}

class PauseDelegatedAgentGoalUseCase(
    private val goals: DelegatedAgentGoalRepository,
    private val scheduler: DelegatedAgentGoalScheduler
) {
    suspend operator fun invoke(goalId: EntityId): Boolean {
        val goal = goals.getGoal(goalId) ?: return false
        if (goal.status in setOf(DelegatedAgentGoalStatus.COMPLETED, DelegatedAgentGoalStatus.CANCELLED, DelegatedAgentGoalStatus.FAILED)) return false
        goals.saveGoal(
            goal.copy(
                status = DelegatedAgentGoalStatus.PAUSED,
                steps = goal.steps.map { if (it.status == DelegatedAgentGoalStepStatus.RUNNING) it.copy(status = DelegatedAgentGoalStepStatus.PENDING) else it },
                updatedAt = useCaseNowMillis()
            )
        )
        scheduler.cancel(goalId)
        return true
    }
}

class ResumeDelegatedAgentGoalUseCase(
    private val goals: DelegatedAgentGoalRepository,
    private val scheduler: DelegatedAgentGoalScheduler,
    private val chats: ChatRepository
) {
    suspend operator fun invoke(goalId: EntityId, userInput: String? = null): Boolean {
        val goal = goals.getGoal(goalId) ?: return false
        if (goal.status != DelegatedAgentGoalStatus.PAUSED && goal.status != DelegatedAgentGoalStatus.WAITING_FOR_USER) return false
        val answer = userInput?.trim()?.takeIf(String::isNotBlank)
        if ((goal.status == DelegatedAgentGoalStatus.WAITING_FOR_USER || goal.pendingQuestion != null) && answer == null) return false
        if (answer != null && answer.length > 1_000) return false
        val now = useCaseNowMillis()
        if (answer != null) {
            val answerMessageId = "delegated-answer-${goal.id}-${goal.updatedAt}"
            if (chats.getMessages(goal.chatId).none { it.id == answerMessageId }) {
                chats.appendMessage(
                    ChatMessage(
                        id = answerMessageId,
                        chatId = goal.chatId,
                        senderId = null,
                        senderType = MessageSenderType.USER,
                        senderName = "我",
                        senderAvatarUri = null,
                        text = answer,
                        timestamp = now,
                        type = MessageType.TEXT,
                        isProactive = false,
                        isRead = true
                    )
                )
            }
        }
        val updated = goal.copy(
            status = DelegatedAgentGoalStatus.QUEUED,
            pendingQuestion = if (answer == null) goal.pendingQuestion else null,
            resumeInput = answer,
            updatedAt = now
        )
        goals.saveGoal(updated)
        scheduler.enqueue(goalId)
        return true
    }
}

class CancelDelegatedAgentGoalUseCase(
    private val goals: DelegatedAgentGoalRepository,
    private val scheduler: DelegatedAgentGoalScheduler
) {
    suspend operator fun invoke(goalId: EntityId): Boolean {
        val goal = goals.getGoal(goalId) ?: return false
        if (goal.status in setOf(DelegatedAgentGoalStatus.COMPLETED, DelegatedAgentGoalStatus.CANCELLED, DelegatedAgentGoalStatus.FAILED)) return false
        goals.saveGoal(goal.copy(status = DelegatedAgentGoalStatus.CANCELLED, updatedAt = useCaseNowMillis()))
        scheduler.cancel(goalId)
        return true
    }
}
