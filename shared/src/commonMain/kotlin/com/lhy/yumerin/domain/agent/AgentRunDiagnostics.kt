package com.lhy.yumerin.domain.agent

import com.lhy.yumerin.core.model.AgentRunEvent
import com.lhy.yumerin.core.model.EntityId
import com.lhy.yumerin.domain.persona.PromptAssembler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One recorded run for the debug panel. */
data class AgentRunDiagnostic(
    val runId: EntityId,
    val traceId: EntityId,
    val chatId: EntityId,
    val characterId: EntityId?,
    val startedAtMillis: Long,
    val completedAtMillis: Long? = null,
    val statusMessage: String? = null,
    val slotBudgets: List<PromptAssembler.SlotBudget> = emptyList(),
    val worldInfoHits: List<String> = emptyList(),
    val capabilityCalls: List<CapabilityCallTrace> = emptyList(),
    val usedMemoryIds: List<EntityId> = emptyList()
) {
    data class CapabilityCallTrace(
        val name: String,
        val successful: Boolean,
        val userFacingSummary: String?,
        val observationCharacters: Int
    )
}

/**
 * In-memory ring of recent run diagnostics for the debug panel. Privacy
 * follows the recall-trace rule: it lives only in this process, records no
 * message text or observations beyond sizes, and clears on app restart.
 */
class AgentRunDiagnostics(private val capacity: Int = DEFAULT_CAPACITY) {
    private val runs = MutableStateFlow<List<AgentRunDiagnostic>>(emptyList())

    val observeRuns: StateFlow<List<AgentRunDiagnostic>> = runs.asStateFlow()

    fun recordStart(
        runId: EntityId,
        traceId: EntityId,
        chatId: EntityId,
        characterId: EntityId?,
        startedAtMillis: Long,
        slotBudgets: List<PromptAssembler.SlotBudget>,
        worldInfoHits: List<String>
    ) {
        upsert(
            AgentRunDiagnostic(
                runId = runId,
                traceId = traceId,
                chatId = chatId,
                characterId = characterId,
                startedAtMillis = startedAtMillis,
                slotBudgets = slotBudgets,
                worldInfoHits = worldInfoHits
            )
        )
    }

    fun recordEvent(event: AgentRunEvent) {
        when (event) {
            is com.lhy.yumerin.core.model.AgentCapabilityCompleted -> update(event.runId) { run ->
                run.copy(
                    capabilityCalls = run.capabilityCalls + AgentRunDiagnostic.CapabilityCallTrace(
                        name = event.capabilityName,
                        successful = event.successful,
                        userFacingSummary = event.userFacingSummary,
                        observationCharacters = event.observationCharacters
                    )
                )
            }
            is com.lhy.yumerin.core.model.AgentRunCompleted -> update(event.runId) { run ->
                run.copy(
                    completedAtMillis = event.timestampMillis,
                    usedMemoryIds = event.reply.usedMemoryIds
                )
            }
            is com.lhy.yumerin.core.model.AgentRunFailed -> update(event.runId) { run ->
                run.copy(completedAtMillis = event.timestampMillis, statusMessage = event.statusMessage)
            }
            else -> Unit
        }
    }

    fun clear() {
        runs.value = emptyList()
    }

    private fun upsert(diagnostic: AgentRunDiagnostic) {
        runs.value = (runs.value.filterNot { it.runId == diagnostic.runId } + diagnostic)
            .sortedByDescending { it.startedAtMillis }
            .take(capacity)
    }

    private fun update(runId: EntityId, transform: (AgentRunDiagnostic) -> AgentRunDiagnostic) {
        runs.value = runs.value.map { if (it.runId == runId) transform(it) else it }
    }

    companion object {
        const val DEFAULT_CAPACITY = 20
    }
}
