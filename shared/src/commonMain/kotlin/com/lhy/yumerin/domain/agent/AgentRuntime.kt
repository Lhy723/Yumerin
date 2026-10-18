package com.lhy.yumerin.domain.agent

import com.lhy.yumerin.core.model.AgentBudgetWarning
import com.lhy.yumerin.core.model.AgentCapabilityCall
import com.lhy.yumerin.core.model.AgentCapabilityCompleted
import com.lhy.yumerin.core.model.AgentCapabilityEffect
import com.lhy.yumerin.core.model.AgentCapabilityRequested
import com.lhy.yumerin.core.model.AgentConversationMessage
import com.lhy.yumerin.core.model.AgentModelStep
import com.lhy.yumerin.core.model.AgentObservationRecorded
import com.lhy.yumerin.core.model.AgentReply
import com.lhy.yumerin.core.model.AgentRequest
import com.lhy.yumerin.core.model.AgentRunBudget
import com.lhy.yumerin.core.model.AgentRunCompleted
import com.lhy.yumerin.core.model.AgentRunEvent
import com.lhy.yumerin.core.model.AgentRunEventHandler
import com.lhy.yumerin.core.model.AgentRunFailed
import com.lhy.yumerin.core.model.AgentRunInterruptReason
import com.lhy.yumerin.core.model.AgentRunInterrupted
import com.lhy.yumerin.core.model.AgentRunStarted
import com.lhy.yumerin.core.model.AgentService
import com.lhy.yumerin.core.model.AgentTextDelta
import com.lhy.yumerin.core.model.AgentTrigger
import com.lhy.yumerin.core.model.EntityId
import com.lhy.yumerin.core.model.MessageSenderType
import com.lhy.yumerin.core.model.newEntityId
import com.lhy.yumerin.domain.persona.PersonaResultRewriter
import com.lhy.yumerin.domain.persona.PromptAssembler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock

/**
 * Coordinates a bounded model/capability loop without knowing about UI state,
 * Android services, provider SDKs, or storage implementations.
 *
 * The run emits typed [AgentRunEvent] values (streaming, cancellable contract)
 * while keeping the legacy accumulated-text callback working. When
 * capabilities are in play the model loop runs on the neutral executor prompt
 * layer for reliability; pure conversational turns use the persona layer. A
 * pluggable [PersonaResultRewriter] can re-voice executor-layer results.
 */
class AgentRuntime(
    private val model: AgentService,
    capabilities: List<AgentCapability>,
    private val maxCapabilityCalls: Int = DEFAULT_MAX_CAPABILITY_CALLS,
    private val maxObservationCharacters: Int = DEFAULT_MAX_OBSERVATION_CHARACTERS,
    private val maxCapabilityBudgetUnits: Int = DEFAULT_MAX_CAPABILITY_BUDGET_UNITS,
    private val maxOutputTokensPerRun: Int = DEFAULT_MAX_OUTPUT_TOKENS_PER_RUN,
    private val maxRunDurationMillis: Long = DEFAULT_MAX_RUN_DURATION_MILLIS,
    private val admission: AgentAdmissionArbiter = AgentAdmissionArbiter(),
    private val resultRewriter: PersonaResultRewriter = PersonaResultRewriter.Identity
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
        onEvent: AgentRunEventHandler = {},
        traceId: EntityId = newEntityId("trace"),
        onAccumulatedText: suspend (String) -> Unit = {}
    ): AgentReply {
        val runId = newEntityId("agent-run")
        suspend fun emit(event: AgentRunEvent) = onEvent(event)

        emit(
            AgentRunStarted(
                runId = runId,
                traceId = traceId,
                timestampMillis = eventNow(),
                trigger = trigger,
                chatId = request.chat.id,
                characterId = request.character?.id
            )
        )
        val reply = try {
            withTimeoutOrNull(maxRunDurationMillis) {
                admission.withAdmission(request.chat.id, trigger) {
                    runAdmitted(request, trigger, runId, traceId, onAccumulatedText, onEvent)
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
        } catch (interrupted: CancellationException) {
            // Interruption is a first-class run outcome (voice barge-in, user stop).
            withContext(NonCancellable) {
                emit(
                    AgentRunInterrupted(
                        runId = runId,
                        traceId = traceId,
                        timestampMillis = eventNow(),
                        reason = AgentRunInterruptReason.USER_STOPPED
                    )
                )
            }
            throw interrupted
        }
        val status = reply.statusMessage
        if (status == null) {
            emit(
                AgentRunCompleted(
                    runId = runId,
                    traceId = traceId,
                    timestampMillis = eventNow(),
                    reply = reply
                )
            )
        } else {
            emit(
                AgentRunFailed(
                    runId = runId,
                    traceId = traceId,
                    timestampMillis = eventNow(),
                    statusMessage = status,
                    message = reply.text
                )
            )
        }
        return reply
    }

    private suspend fun runAdmitted(
        request: AgentRequest,
        trigger: AgentTrigger,
        runId: EntityId,
        traceId: EntityId,
        onAccumulatedText: suspend (String) -> Unit,
        onEvent: AgentRunEventHandler
    ): AgentReply {
        suspend fun emit(event: AgentRunEvent) = onEvent(event)
        val now = { eventNow() }
        val conversation = mutableListOf<AgentConversationMessage>()
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
        // Persona layer for pure conversation; neutral executor layer when the
        // model may call capabilities (keeps tool loops reliable).
        val layer = if (initialCapabilities.isEmpty()) {
            PromptAssembler.Layer.PERSONA
        } else {
            PromptAssembler.Layer.EXECUTOR
        }
        val assembled = PromptAssembler.assembleDetailed(request, layer)
        conversation += assembled.messages
        var usedMemoryIds = assembled.usedMemoryIds
        var hadCapabilityActivity = false

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
                    onAccumulatedText = { accumulated ->
                        onAccumulatedText(accumulated)
                        emit(
                            AgentTextDelta(
                                runId = runId,
                                traceId = traceId,
                                timestampMillis = now(),
                                accumulated = accumulated
                            )
                        )
                    }
                )
            ) {
                is AgentModelStep.FinalResponse -> {
                    var reply = step.reply
                    if (reply.usedMemoryIds.isEmpty() && usedMemoryIds.isNotEmpty()) {
                        reply = reply.copy(usedMemoryIds = usedMemoryIds)
                    }
                    if (reply.statusMessage == null && hadCapabilityActivity) {
                        reply = resultRewriter.rewrite(request, reply, hadCapabilityActivity = true)
                    }
                    return reply
                }
                is AgentModelStep.CapabilityRequests -> {
                    if (step.calls.isEmpty() || step.calls.size > MAX_CAPABILITY_CALLS_PER_RESPONSE) {
                        return withMemoryTrace(
                            AgentReply(
                                text = "模型返回的能力调用数量无效。请重试。",
                                modelProfileId = request.model.id,
                                statusMessage = "agent-invalid-capability-calls"
                            ),
                            usedMemoryIds
                        )
                    }
                    if (step.calls.any { it.id.isBlank() } || step.calls.map { it.id }.distinct().size != step.calls.size) {
                        return withMemoryTrace(
                            AgentReply(
                                text = "模型返回的能力调用标识无效。请重试。",
                                modelProfileId = request.model.id,
                                statusMessage = "agent-invalid-capability-call-ids"
                            ),
                            usedMemoryIds
                        )
                    }
                    conversation += AgentConversationMessage(
                        role = "assistant",
                        content = step.assistantText.orEmpty(),
                        capabilityCalls = step.calls
                    )
                    step.calls.forEach { call ->
                        emit(
                            AgentCapabilityRequested(
                                runId = runId,
                                traceId = traceId,
                                timestampMillis = now(),
                                call = call
                            )
                        )
                        val capability = registry.find(call.name)
                        val budgetRejection = when {
                            remainingCalls <= 0 -> AgentRunBudget.CAPABILITY_CALLS
                            remainingObservationCharacters <= 0 -> AgentRunBudget.OBSERVATION_CHARACTERS
                            capability != null && capability.descriptor.budgetCostUnits > remainingCapabilityBudget ->
                                AgentRunBudget.CAPABILITY_COST_UNITS
                            else -> null
                        }
                        val result = when {
                            capability == null || available.none { it.name == call.name } ->
                                AgentCapabilityResult(false, "This capability is not available in the current run.")
                            call.argumentsJson.length > MAX_ARGUMENT_CHARACTERS ->
                                AgentCapabilityResult(false, "Capability arguments exceeded the size limit.")
                            capability.descriptor.requiresUserConfirmation ->
                                AgentCapabilityResult(false, "This action requires user confirmation, which is not available in this run.")
                            capability.descriptor.requiredPermissions.isNotEmpty() ->
                                AgentCapabilityResult(false, "Required permissions have not been granted to this capability.")
                            budgetRejection != null -> {
                                emit(
                                    AgentBudgetWarning(
                                        runId = runId,
                                        traceId = traceId,
                                        timestampMillis = now(),
                                        budget = budgetRejection,
                                        detail = "Capability ${call.name} rejected: ${budgetLabel(budgetRejection)} exhausted."
                                    )
                                )
                                AgentCapabilityResult(false, "The run's ${budgetLabel(budgetRejection)} has been reached; this call was not executed.")
                            }
                            capability.descriptor.effect == AgentCapabilityEffect.EXTERNAL_SIDE_EFFECT ->
                                AgentCapabilityResult(false, "External side effects are not available in this run.")
                            else -> execute(capability, call, context).also {
                                hadCapabilityActivity = true
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
                        emit(
                            AgentCapabilityCompleted(
                                runId = runId,
                                traceId = traceId,
                                timestampMillis = now(),
                                callId = call.id,
                                capabilityName = call.name,
                                successful = result.successful,
                                userFacingSummary = result.userFacingSummary,
                                observationCharacters = boundedObservation.length
                            )
                        )
                        emit(
                            AgentObservationRecorded(
                                runId = runId,
                                traceId = traceId,
                                timestampMillis = now(),
                                callId = call.id,
                                capabilityName = call.name,
                                content = boundedObservation
                            )
                        )
                    }
                    if (remainingCalls == 0) {
                        conversation += AgentConversationMessage(
                            role = "system",
                            content = "The capability-call limit has been reached. Use the observations already provided to give the best final answer; do not request another capability."
                        )
                        emit(
                            AgentBudgetWarning(
                                runId = runId,
                                traceId = traceId,
                                timestampMillis = now(),
                                budget = AgentRunBudget.CAPABILITY_CALLS,
                                detail = "Capability-call limit reached; continuing for a final answer."
                            )
                        )
                    }
                }
            }
        }

        return withMemoryTrace(
            AgentReply(
                text = "本次任务已达到步骤上限。请缩小任务范围后重试。",
                modelProfileId = request.model.id,
                statusMessage = "agent-step-limit"
            ),
            usedMemoryIds
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

    private fun withMemoryTrace(reply: AgentReply, usedMemoryIds: List<EntityId>): AgentReply =
        if (reply.usedMemoryIds.isNotEmpty() || usedMemoryIds.isEmpty()) reply
        else reply.copy(usedMemoryIds = usedMemoryIds)

    private fun eventNow(): Long = Clock.System.now().toEpochMilliseconds()

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

        private fun budgetLabel(budget: AgentRunBudget): String = when (budget) {
            AgentRunBudget.CAPABILITY_CALLS -> "capability-call limit"
            AgentRunBudget.CAPABILITY_COST_UNITS -> "capability budget"
            AgentRunBudget.OBSERVATION_CHARACTERS -> "observation budget"
            AgentRunBudget.MODEL_STEPS -> "model-step limit"
            AgentRunBudget.OUTPUT_TOKENS -> "output-token budget"
            AgentRunBudget.RUN_DURATION -> "run duration limit"
        }

        private fun observationBudget(request: AgentRequest): Int =
            (((request.model.contextLength ?: 8_192) * 4) * 0.1f).toInt().coerceAtLeast(512)
    }
}
