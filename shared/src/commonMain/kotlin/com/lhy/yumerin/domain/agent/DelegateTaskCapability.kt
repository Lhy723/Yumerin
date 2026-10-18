package com.lhy.yumerin.domain.agent

import com.lhy.yumerin.core.model.AgentCapabilityDescriptor
import com.lhy.yumerin.core.model.AgentRequest
import com.lhy.yumerin.core.model.ChatKind
import com.lhy.yumerin.core.model.MessageSenderType
import com.lhy.yumerin.domain.safety.SafetyPolicy
import com.lhy.yumerin.domain.usecase.CreateDelegatedAgentGoalResult
import com.lhy.yumerin.domain.usecase.CreateDelegatedAgentGoalUseCase

/**
 * The brain's delegation entry point. When a user asks for something that
 * needs bounded background work, the model calls this capability to create a
 * Delegated Agent goal; the existing goal pipeline (plan, run, report, wait
 * for user) takes over. The capability only creates the goal: it never
 * executes steps inline and never authorizes any external action.
 */
class DelegateTaskCapability(
    private val createGoal: CreateDelegatedAgentGoalUseCase
) : AgentCapability {
    override val descriptor = AgentCapabilityDescriptor(
        name = "delegate_task",
        description = "Create a bounded background task for this character to work on when the user explicitly asks for ongoing or multi-step work. Use only when the request needs more than one immediate reply.",
        parametersJsonSchema = """{"type":"object","properties":{"title":{"type":"string","description":"Short task name, max 80 characters"},"outcome":{"type":"string","description":"The requested outcome in the user's terms, max 1000 characters"},"constraints":{"type":"string","description":"Optional constraints the user stated, max 1500 characters"}},"required":["title","outcome"],"additionalProperties":false}"""
    )

    override fun isAvailableFor(request: AgentRequest): Boolean {
        // Direct chats only: delegated goals bind one character to one chat.
        if (request.chat.kind != ChatKind.DIRECT) return false
        val text = request.messages.lastOrNull { it.senderType == MessageSenderType.USER }?.text.orEmpty()
        return DELEGATION_INTENT.containsMatchIn(text)
    }

    override suspend fun execute(
        argumentsJson: String,
        context: AgentCapabilityContext
    ): AgentCapabilityResult {
        val fields = AgentJsonStringArgument.readFields(argumentsJson, listOf("title", "outcome", "constraints"))
        val title = fields["title"]?.trim().orEmpty()
        val outcome = fields["outcome"]?.trim().orEmpty()
        val constraints = fields["constraints"]?.trim().orEmpty()
        if (title.isBlank() || outcome.isBlank()) {
            return AgentCapabilityResult(false, "Invalid arguments: title and outcome are required.")
        }
        val characterId = context.characterId
            ?: return AgentCapabilityResult(false, "There is no active character to delegate to.")
        val chatId = context.chatId
        if (!SafetyPolicy.evaluate(outcome).allowed ||
            (constraints.isNotBlank() && !SafetyPolicy.evaluate(constraints).allowed)
        ) {
            return AgentCapabilityResult(false, "The requested task crosses a safety boundary and was not created.")
        }
        return when (val result = createGoal(characterId, chatId, title, outcome, constraints)) {
            is CreateDelegatedAgentGoalResult.Created -> AgentCapabilityResult(
                successful = true,
                observation = "Delegated goal created: id=${result.goal.id}. It will run in the background with at most 6 planned steps; report to the user that the task has started and they can pause or cancel it.",
                userFacingSummary = "已创建后台任务「${result.goal.title}」"
            )
            CreateDelegatedAgentGoalResult.InvalidGoal ->
                AgentCapabilityResult(false, "Invalid task: the title or outcome length is out of bounds.")
            CreateDelegatedAgentGoalResult.UnsafeGoal ->
                AgentCapabilityResult(false, "The requested task crosses a safety boundary and was not created.")
            CreateDelegatedAgentGoalResult.MissingCharacter ->
                AgentCapabilityResult(false, "There is no active character to delegate to.")
            CreateDelegatedAgentGoalResult.InvalidChat ->
                AgentCapabilityResult(false, "Delegation requires this character's direct chat.")
            CreateDelegatedAgentGoalResult.SchedulerUnavailable ->
                AgentCapabilityResult(false, "Background scheduling is unavailable right now; the task was not created.")
        }
    }

    private companion object {
        val DELEGATION_INTENT = Regex(
            "帮我(盯|跟踪|整理|调研|研究|跟进|准备)|持续关注|后台(帮|处理|跑)|交给你(办|处理|完成)|慢慢整理|盯着|帮我留意|delegate|keep (an eye|tracking)|work on this",
            RegexOption.IGNORE_CASE
        )
    }
}
