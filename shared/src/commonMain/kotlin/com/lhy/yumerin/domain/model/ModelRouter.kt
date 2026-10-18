package com.lhy.yumerin.domain.model

import com.lhy.yumerin.core.model.EntityId
import com.lhy.yumerin.core.model.ModelCapability
import com.lhy.yumerin.core.model.ModelProfile
import com.lhy.yumerin.domain.repository.ModelProfileRepository

/**
 * What a model is being selected for. Each purpose has its own bias: voice
 * wants a fast first token, memory extraction wants a cheap model, task
 * execution wants tool-calling strength.
 */
enum class ModelPurpose {
    CONVERSATION,
    TASK_EXECUTION,
    MEMORY_EXTRACTION,
    VOICE
}

/**
 * Selects a Model profile for a purpose from the currently enabled profiles,
 * honoring character assignment > user-declared capabilities > defaults, and
 * returning the reason when a fallback was applied instead of silently
 * swapping models.
 */
class ModelRouter(
    private val models: ModelProfileRepository
) {
    data class Selection(
        val profile: ModelProfile?,
        /** Non-null when the requested purpose/capabilities could not be met exactly. */
        val fallbackReason: String? = null
    )

    suspend fun select(
        purpose: ModelPurpose,
        preferredProfileId: EntityId? = null,
        requiredCapabilities: Set<ModelCapability> = emptySet()
    ): Selection {
        val enabled = models.getProfiles().filter { it.enabled && it.deletedAt == null }
        if (enabled.isEmpty()) return Selection(null)

        val preferred = preferredProfileId?.let { id -> enabled.firstOrNull { it.id == id } }
        if (preferred != null && preferred.satisfies(requiredCapabilities)) {
            return Selection(preferred)
        }

        val exact = candidates(purpose, enabled).filter { it.satisfies(requiredCapabilities) }
        if (exact.isNotEmpty()) {
            return Selection(
                profile = exact.first(),
                // The preferred profile exists but missed a required capability:
                // the swap is a user-visible downgrade decision.
                fallbackReason = if (preferred != null) "preferred-model-missing" else null
            )
        }

        // Degrade only on capability pressure, never on purpose preference:
        // any enabled profile that satisfies the hard requirements wins; when
        // nothing does, the preferred profile is returned with a reason so the
        // caller can surface a user-visible normalization note.
        val capable = enabled.filter { it.satisfies(requiredCapabilities) }
        if (capable.isNotEmpty()) {
            return Selection(
                profile = candidates(purpose, capable).firstOrNull() ?: capable.first(),
                fallbackReason = "preferred-model-missing"
            )
        }
        return Selection(
            profile = preferred,
            fallbackReason = if (requiredCapabilities.isEmpty()) null else "capabilities-unavailable"
        )
    }

    private fun candidates(
        purpose: ModelPurpose,
        pool: List<ModelProfile>
    ): List<ModelProfile> = when (purpose) {
        // Conversation keeps the declared order (profiles are user-ordered).
        ModelPurpose.CONVERSATION -> pool
        // Task execution prefers explicit tool-calling strength.
        ModelPurpose.TASK_EXECUTION -> pool.sortedByDescending { it.capabilities.contains(ModelCapability.TOOL_CALLS) }
        // Memory extraction prefers cheap context handling; ordering unchanged.
        ModelPurpose.MEMORY_EXTRACTION -> pool
        // Voice prefers streaming for the fastest first token.
        ModelPurpose.VOICE -> pool.sortedByDescending { it.capabilities.contains(ModelCapability.STREAMING) }
    }

    private fun ModelProfile.satisfies(required: Set<ModelCapability>): Boolean =
        required.all { capability -> capabilities.contains(capability) }
}
