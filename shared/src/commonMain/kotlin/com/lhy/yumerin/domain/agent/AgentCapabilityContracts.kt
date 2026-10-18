package com.lhy.yumerin.domain.agent

import com.lhy.yumerin.core.model.AgentCapabilityDescriptor
import com.lhy.yumerin.core.model.AgentCapabilityEffect
import com.lhy.yumerin.core.model.AgentRequest
import com.lhy.yumerin.core.model.EntityId

data class AgentCapabilityContext(
    val chatId: EntityId,
    val characterId: EntityId?,
    val sourceMessageId: EntityId?
)

/** The observation is returned to the model; summary is reserved for user-facing status. */
data class AgentCapabilityResult(
    val successful: Boolean,
    val observation: String,
    val userFacingSummary: String? = null
)

/**
 * A capability module owns its input validation and execution. It has no
 * dependency on Compose, Android, ViewModels, or navigation.
 */
interface AgentCapability {
    val descriptor: AgentCapabilityDescriptor

    fun isAvailableFor(request: AgentRequest): Boolean = true

    suspend fun execute(
        argumentsJson: String,
        context: AgentCapabilityContext
    ): AgentCapabilityResult
}

class AgentCapabilityRegistry(capabilities: List<AgentCapability>) {
    private val byName: Map<String, AgentCapability>

    init {
        require(capabilities.map { it.descriptor.name }.distinct().size == capabilities.size) {
            "Agent capability names must be unique."
        }
        capabilities.forEach { capability ->
            require(capability.descriptor.name.matches(Regex("[a-zA-Z0-9_-]{1,64}"))) {
                "Invalid Agent capability name: ${capability.descriptor.name}"
            }
            require(capability.descriptor.budgetCostUnits >= 0) {
                "Agent capability budget cost cannot be negative."
            }
            require(
                capability.descriptor.effect == AgentCapabilityEffect.READ_ONLY ||
                    capability.descriptor.effect == AgentCapabilityEffect.EXTERNAL_SIDE_EFFECT ||
                    capability.descriptor.requiresUserConfirmation
            ) {
                "State-changing capabilities require explicit user confirmation."
            }
        }
        byName = capabilities.associateBy { it.descriptor.name }
    }

    fun descriptors(request: AgentRequest): List<AgentCapabilityDescriptor> = byName.values
        .filter { it.isAvailableFor(request) }
        .filter { !it.descriptor.requiresUserConfirmation && it.descriptor.requiredPermissions.isEmpty() }
        // External side-effect capabilities are listed so the model can request
        // them; the runtime routes every call through the approval gate and can
        // never execute one directly (ADR 0022).
        .filter { it.descriptor.effect == AgentCapabilityEffect.READ_ONLY || it.descriptor.effect == AgentCapabilityEffect.EXTERNAL_SIDE_EFFECT }
        .map { it.descriptor }

    fun find(name: String): AgentCapability? = byName[name]
}
