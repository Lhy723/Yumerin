package com.lhy.yumerin.core.model

/**
 * Typed progress emitted by one bounded Agent run.
 *
 * Events are plain framework-free data classes: the Chat UI consumes them for
 * status display, and a future voice or remote worker can consume the same
 * contract over a serialized transport. Every event carries a run id, a trace
 * id shared with the originating user turn, and a timestamp.
 */
sealed interface AgentRunEvent {
    val runId: EntityId
    val traceId: EntityId
    val timestampMillis: Long
}

/** Kinds of run budget that can reject work or end a run early. */
enum class AgentRunBudget {
    CAPABILITY_CALLS,
    CAPABILITY_COST_UNITS,
    OBSERVATION_CHARACTERS,
    MODEL_STEPS,
    OUTPUT_TOKENS,
    RUN_DURATION
}

enum class AgentRunInterruptReason {
    USER_STOPPED,
    SUPERSEDED_BY_NEWER_TURN,
    CHANNEL_CLOSED
}

data class AgentRunStarted(
    override val runId: EntityId,
    override val traceId: EntityId,
    override val timestampMillis: Long,
    val trigger: AgentTrigger,
    val chatId: EntityId,
    val characterId: EntityId?
) : AgentRunEvent

/**
 * Incremental model output. The payload is the accumulated answer so far, not
 * a raw token stream, so one-shot providers stay compatible with streaming ones.
 */
data class AgentTextDelta(
    override val runId: EntityId,
    override val traceId: EntityId,
    override val timestampMillis: Long,
    val accumulated: String
) : AgentRunEvent

data class AgentCapabilityRequested(
    override val runId: EntityId,
    override val traceId: EntityId,
    override val timestampMillis: Long,
    val call: AgentCapabilityCall
) : AgentRunEvent

data class AgentCapabilityCompleted(
    override val runId: EntityId,
    override val traceId: EntityId,
    override val timestampMillis: Long,
    val callId: EntityId,
    val capabilityName: String,
    val successful: Boolean,
    val userFacingSummary: String?,
    val observationCharacters: Int
) : AgentRunEvent

data class AgentObservationRecorded(
    override val runId: EntityId,
    override val traceId: EntityId,
    override val timestampMillis: Long,
    val callId: EntityId,
    val capabilityName: String,
    val content: String
) : AgentRunEvent

data class AgentBudgetWarning(
    override val runId: EntityId,
    override val traceId: EntityId,
    override val timestampMillis: Long,
    val budget: AgentRunBudget,
    val detail: String
) : AgentRunEvent

data class AgentRunInterrupted(
    override val runId: EntityId,
    override val traceId: EntityId,
    override val timestampMillis: Long,
    val reason: AgentRunInterruptReason
) : AgentRunEvent

data class AgentRunCompleted(
    override val runId: EntityId,
    override val traceId: EntityId,
    override val timestampMillis: Long,
    val reply: AgentReply
) : AgentRunEvent

data class AgentRunFailed(
    override val runId: EntityId,
    override val traceId: EntityId,
    override val timestampMillis: Long,
    val statusMessage: String,
    val message: String
) : AgentRunEvent

typealias AgentRunEventHandler = suspend (AgentRunEvent) -> Unit
