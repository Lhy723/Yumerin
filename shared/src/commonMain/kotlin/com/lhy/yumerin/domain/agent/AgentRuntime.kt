package com.lhy.yumerin.domain.agent

import com.lhy.yumerin.core.model.AgentConversationMessage
import com.lhy.yumerin.core.model.AgentModelStep
import com.lhy.yumerin.core.model.AgentReply
import com.lhy.yumerin.core.model.AgentRequest
import com.lhy.yumerin.core.model.AgentService
import com.lhy.yumerin.core.model.AgentCapabilityCall
import com.lhy.yumerin.core.model.AgentCapabilityEffect
import com.lhy.yumerin.core.model.AgentTrigger
import com.lhy.yumerin.core.model.MessageSenderType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Coordinates a bounded model/capability loop without knowing about UI state,
 * Android services, provider SDKs, or storage implementations.
 */
class AgentRuntime(
    private val model: AgentService,
    capabilities: List<AgentCapability>,
    private val maxCapabilityCalls: Int = DEFAULT_MAX_CAPABILITY_CALLS,
    private val maxObservationCharacters: Int = DEFAULT_MAX_OBSERVATION_CHARACTERS,
    private val maxCapabilityBudgetUnits: Int = DEFAULT_MAX_CAPABILITY_BUDGET_UNITS,
    private val maxOutputTokensPerRun: Int = DEFAULT_MAX_OUTPUT_TOKENS_PER_RUN,
    private val maxRunDurationMillis: Long = DEFAULT_MAX_RUN_DURATION_MILLIS,
    private val admission: AgentAdmissionArbiter = AgentAdmissionArbiter()
) {
    private val registry = AgentCapabilityRegistry(capabilities)

    init {
        require(maxCapabilityCalls in 0..MAX_ALLOWED_CAPABILITY_CALLS)
        require(maxObservationCharacters > 0)
        require(maxCapabilityBudgetUnits >= 0)
        require(maxOutputTokensPerRun > 0)
        require(maxRunDurationMillis > 0)
    }

    suspend fun run(
        request: AgentRequest,
        trigger: AgentTrigger = AgentTrigger.USER_MESSAGE,
        onAccumulatedText: suspend (String) -> Unit = {}
    ): AgentReply {
        return withTimeoutOrNull(maxRunDurationMillis) {
            admission.withAdmission(request.chat.id, trigger) {
                runAdmitted(request, trigger, onAccumulatedText)
            } ?: AgentReply(
                text = "当前聊天正在处理其他内容，本次后台 Agent 任务已暂缓。",
                modelProfileId = request.model.id,
                statusMessage = "agent-chat-busy"
            )
        } ?: AgentReply(
            text = "本次任务超过了运行时限，请缩小范围后重试。",
            modelProfileId = request.model.id,
            statusMessage = "agent-time-limit"
        )
    }

    private suspend fun runAdmitted(
        request: AgentRequest,
        trigger: AgentTrigger,
        onAccumulatedText: suspend (String) -> Unit
    ): AgentReply {
        val conversation = ContextAssembler.assemble(request).toMutableList()
        val context = AgentCapabilityContext(
            chatId = request.chat.id,
            characterId = request.character?.id,
            sourceMessageId = request.messages.lastOrNull { it.senderType == MessageSenderType.USER }?.id
        )
        var remainingCalls = maxCapabilityCalls
        var remainingCapabilityBudget = maxCapabilityBudgetUnits
        var remainingObservationCharacters = observationBudget(request)
        val initialCapabilities = if (remainingCalls > 0 && (request.proactivePrompt == null || trigger == AgentTrigger.SCHEDULED_GOAL)) {
            registry.descriptors(request).filter { it.budgetCostUnits <= remainingCapabilityBudget }
        } else {
            emptyList()
        }
        val contextOutputTokenBudget = ((request.model.contextLength ?: DEFAULT_CONTEXT_LENGTH) / 4).coerceAtLeast(128)
        val totalOutputTokenBudget = minOf(maxOutputTokensPerRun, contextOutputTokenBudget)
        val maxModelSteps = if (initialCapabilities.isEmpty()) {
            1
        } else {
            minOf(maxCapabilityCalls + 2, totalOutputTokenBudget)
        }
        val perStepOutputTokenLimit = minOf(
            request.model.generationParameters.maxOutputTokens?.coerceAtLeast(1) ?: totalOutputTokenBudget,
            (totalOutputTokenBudget / maxModelSteps).coerceAtLeast(1)
        )
        val boundedRequest = request.copy(
            model = request.model.copy(
                generationParameters = request.model.generationParameters.copy(maxOutputTokens = perStepOutputTokenLimit)
            )
        )

        repeat(maxModelSteps) {
            val available = if (remainingCalls > 0 && (request.proactivePrompt == null || trigger == AgentTrigger.SCHEDULED_GOAL)) {
                registry.descriptors(request).filter { it.budgetCostUnits <= remainingCapabilityBudget }
            } else {
                emptyList()
            }
            when (
                val step = model.nextStep(
                    request = boundedRequest,
                    conversation = conversation.toList(),
                    capabilities = available,
                    onAccumulatedText = onAccumulatedText
                )
            ) {
                is AgentModelStep.FinalResponse -> return withMemoryTrace(step.reply, request)
                is AgentModelStep.CapabilityRequests -> {
                    if (step.calls.isEmpty() || step.calls.size > MAX_CAPABILITY_CALLS_PER_RESPONSE) {
                        return AgentReply(
                            text = "模型返回的能力调用数量无效。请重试。",
                            modelProfileId = request.model.id,
                            statusMessage = "agent-invalid-capability-calls"
                        )
                    }
                    if (step.calls.any { it.id.isBlank() } || step.calls.map { it.id }.distinct().size != step.calls.size) {
                        return AgentReply(
                            text = "模型返回的能力调用标识无效。请重试。",
                            modelProfileId = request.model.id,
                            statusMessage = "agent-invalid-capability-call-ids"
                        )
                    }
                    conversation += AgentConversationMessage(
                        role = "assistant",
                        content = step.assistantText.orEmpty(),
                        capabilityCalls = step.calls
                    )
                    step.calls.forEach { call ->
                        val capability = registry.find(call.name)
                        val result = when {
                            capability == null || available.none { it.name == call.name } ->
                                AgentCapabilityResult(false, "This capability is not available in the current run.")
                            call.argumentsJson.length > MAX_ARGUMENT_CHARACTERS ->
                                AgentCapabilityResult(false, "Capability arguments exceeded the size limit.")
                            capability.descriptor.requiresUserConfirmation ->
                                AgentCapabilityResult(false, "This action requires user confirmation, which is not available in this run.")
                            capability.descriptor.requiredPermissions.isNotEmpty() ->
                                AgentCapabilityResult(false, "Required permissions have not been granted to this capability.")
                            remainingCalls <= 0 ->
                                AgentCapabilityResult(false, "The capability step limit for this run has been reached.")
                            remainingObservationCharacters <= 0 ->
                                AgentCapabilityResult(false, "The capability observation budget for this run has been reached.")
                            capability.descriptor.budgetCostUnits > remainingCapabilityBudget ->
                                AgentCapabilityResult(false, "The capability budget for this run has been reached.")
                            capability.descriptor.effect == AgentCapabilityEffect.EXTERNAL_SIDE_EFFECT ->
                                AgentCapabilityResult(false, "External side effects are not available in this run.")
                            else -> execute(capability, call, context).also {
                                remainingCalls -= 1
                                remainingCapabilityBudget -= capability.descriptor.budgetCostUnits
                            }
                        }
                        val observation = if (result.successful) result.observation else "Execution failed: ${result.observation}"
                        val boundedObservation = observation.take(minOf(maxObservationCharacters, remainingObservationCharacters))
                        remainingObservationCharacters = (remainingObservationCharacters - boundedObservation.length).coerceAtLeast(0)
                        conversation += AgentConversationMessage(
                            role = "tool",
                            content = boundedObservation,
                            name = call.name,
                            capabilityCallId = call.id
                        )
                    }
                    if (remainingCalls == 0) {
                        conversation += AgentConversationMessage(
                            role = "system",
                            content = "The capability-call limit has been reached. Use the observations already provided to give the best final answer; do not request another capability."
                        )
                    }
                }
            }
        }

        return AgentReply(
            text = "本次任务已达到步骤上限。请缩小任务范围后重试。",
            modelProfileId = request.model.id,
            usedMemoryIds = ContextAssembler.usedMemoryIds(request),
            statusMessage = "agent-step-limit"
        )
    }

    private suspend fun execute(
        capability: AgentCapability,
        call: AgentCapabilityCall,
        context: AgentCapabilityContext
    ): AgentCapabilityResult = try {
        capability.execute(call.argumentsJson, context)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        AgentCapabilityResult(
            successful = false,
            observation = "Capability execution failed due to an internal error."
        )
    }

    private fun withMemoryTrace(reply: AgentReply, request: AgentRequest): AgentReply =
        if (reply.usedMemoryIds.isNotEmpty()) reply
        else reply.copy(usedMemoryIds = ContextAssembler.usedMemoryIds(request))

    companion object {
        const val DEFAULT_MAX_CAPABILITY_CALLS = 4
        const val DEFAULT_MAX_OBSERVATION_CHARACTERS = 4_000
        const val DEFAULT_MAX_CAPABILITY_BUDGET_UNITS = 8
        const val DEFAULT_MAX_OUTPUT_TOKENS_PER_RUN = 4_096
        const val DEFAULT_MAX_RUN_DURATION_MILLIS = 120_000L
        const val MAX_ARGUMENT_CHARACTERS = 12_000
        const val MAX_ALLOWED_CAPABILITY_CALLS = 8
        const val MAX_CAPABILITY_CALLS_PER_RESPONSE = 4
        private const val DEFAULT_CONTEXT_LENGTH = 8_192

        private fun observationBudget(request: AgentRequest): Int =
            (((request.model.contextLength ?: 8_192) * 4) * 0.1f).toInt().coerceAtLeast(512)
    }
}
