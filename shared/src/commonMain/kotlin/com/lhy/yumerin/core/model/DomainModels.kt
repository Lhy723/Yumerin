package com.lhy.yumerin.core.model

import kotlin.random.Random

typealias EntityId = String

fun newEntityId(prefix: String): EntityId = "$prefix-${Random.nextLong().toString(16)}"

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

enum class ThemeColor {
    BLUE,
    PURPLE,
    GREEN,
    ROSE,
    ORANGE
}

enum class ComponentMaterial {
    STANDARD,
    GLASS
}

enum class ChatKind {
    DIRECT,
    GROUP
}

enum class MessageSenderType {
    USER,
    CHARACTER,
    SYSTEM,
    DELETED_CHARACTER
}

enum class MessageType {
    TEXT,
    EMOJI,
    SYSTEM
}

enum class MessageDeliveryState {
    PENDING,
    SENT,
    FAILED,
    RETRYABLE
}

enum class AiResponseState {
    GENERATING,
    COMPLETED,
    STOPPED,
    FAILED,
    RETRYABLE
}

enum class MemoryCategory {
    LONG_TERM,
    USER_PREFERENCE,
    RELATIONSHIP,
    IMPORTANT_EVENT
}

enum class MemoryReviewState {
    ACCEPTED,
    PENDING_REVIEW,
    REJECTED,
    SUPERSEDED
}

enum class AgentGoalStatus {
    PENDING,
    COMPLETED,
    CANCELLED,
    FAILED
}

enum class DelegatedAgentGoalStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    WAITING_FOR_USER,
    COMPLETED,
    CANCELLED,
    FAILED
}

enum class DelegatedAgentGoalStepStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}

enum class ModelProviderKind {
    OPENAI_COMPATIBLE,
    LITERT_LM
}

enum class LocalModelState {
    CATALOGUED,
    DOWNLOADING,
    VERIFYING,
    INSTALLED,
    ACTIVE,
    FAILED,
    REMOVING
}

enum class AgentVisibility {
    FINAL_ONLY,
    CONCISE_STATUS,
    DETAILED_STEPS
}

enum class ContextLengthMode {
    AUTO,
    KNOWN,
    CUSTOM
}

enum class SummaryMaintenanceMode {
    AUTO,
    FIXED
}

data class CharacterProfile(
    val id: EntityId,
    val name: String,
    val avatarUri: String?,
    val relationshipFraming: String,
    val setting: String,
    val description: String,
    val personality: String,
    val tone: String,
    val backstory: String,
    val interactionStyle: String,
    val modelProfileId: EntityId?,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val proactiveEnabled: Boolean = false,
    val proactiveDailyLimit: Int = 3,
    val deletedAt: Long? = null
)

data class ChatThread(
    val id: EntityId,
    val kind: ChatKind,
    val title: String,
    val characterId: EntityId?,
    val groupId: EntityId?,
    val avatarUri: String?,
    val lastMessagePreview: String,
    val lastActivityAt: Long,
    val unreadCount: Int,
    val pinned: Boolean,
    val muted: Boolean,
    val shortTermSummary: String,
    val summaryMaintenanceMode: SummaryMaintenanceMode,
    val deletedAt: Long? = null
)

data class ChatMessage(
    val id: EntityId,
    val chatId: EntityId,
    val senderId: EntityId?,
    val senderType: MessageSenderType,
    val senderName: String,
    val senderAvatarUri: String?,
    val text: String,
    val timestamp: Long,
    val type: MessageType,
    val isProactive: Boolean,
    val isRead: Boolean,
    val deliveryState: MessageDeliveryState = MessageDeliveryState.SENT,
    val responseState: AiResponseState? = null,
    val errorMessage: String? = null,
    val usedMemoryIds: List<EntityId> = emptyList(),
    val deletedAt: Long? = null
)

data class GroupProfile(
    val id: EntityId,
    val name: String,
    val avatarUri: String?,
    val memberCharacterIds: List<EntityId>,
    val scenario: String,
    val storySetting: String,
    val interactionStyle: String,
    val autonomousTurnLimit: Int,
    val summary: String,
    val summaryMaintenanceMode: SummaryMaintenanceMode,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

data class MemoryEntry(
    val id: EntityId,
    val characterId: EntityId,
    val sourceChatId: EntityId?,
    val sourceMessageId: EntityId?,
    val category: MemoryCategory,
    val content: String,
    val confidence: Float,
    val reviewState: MemoryReviewState,
    val pinned: Boolean,
    val supersedesId: EntityId?,
    val createdAt: Long,
    val updatedAt: Long,
    val reviewAt: Long?,
    val reviewReason: String? = null,
    val expiresAt: Long? = null,
    val deletedAt: Long? = null,
    /** Episodic weight (0..1); recall ranks pinned first, then importance. */
    val importance: Float = 0.5f,
    /** Short affect label captured when the entry was written (e.g. "开心"). */
    val emotionTag: String? = null,
    /** When the remembered event happened, distinct from createdAt. */
    val occurredAt: Long? = null,
    /** Structured user-profile fact (portrait layer); null for ordinary entries. */
    val structuredFact: StructuredFact? = null
)

/**
 * A structured portrait fact about the user: one subject-predicate-value triple
 * with provenance carried by the owning MemoryEntry. Users can view, edit, and
 * delete it through the same memory surfaces.
 */
data class StructuredFact(
    val subject: String,
    val predicate: String,
    val value: String
)

data class GenerationParameters(
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxOutputTokens: Int? = null,
    val stopSequences: List<String> = emptyList()
)

data class ModelProfile(
    val id: EntityId,
    val displayName: String,
    val providerKind: ModelProviderKind,
    val providerName: String,
    val baseUrl: String?,
    val modelId: String,
    val credentialAlias: String?,
    val contextLength: Int?,
    val contextLengthMode: ContextLengthMode,
    val generationParameters: GenerationParameters,
    val localModelId: String?,
    val localModelVersion: String?,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val localBackend: String = "auto",
    val deletedAt: Long? = null
)

data class LocalModelArtifact(
    val id: EntityId,
    /** Stable identity shared by all versions of one local model. */
    val logicalModelId: EntityId = id,
    val displayName: String,
    val version: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val sha256: String,
    val license: String,
    val sourceUrl: String?,
    val backend: String,
    val contextLength: Int?,
    val state: LocalModelState,
    val localPath: String?,
    val assignedProfileCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deviceRequirements: String = "",
    val licenseAcknowledged: Boolean = false,
    val diagnosticReason: String? = null,
    val deletedAt: Long? = null
)

data class AppSettings(
    val adultConfirmed: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val agentVisibility: AgentVisibility = AgentVisibility.FINAL_ONLY,
    val streamingEnabled: Boolean = false,
    val autoMemoryEnabled: Boolean = true,
    val proactiveMessagesEnabled: Boolean = false,
    val backgroundCompanionEnabled: Boolean = false,
    val dailyProactiveLimit: Int = 3,
    val quietTimeEnabled: Boolean = true,
    val quietStartMinutes: Int = 23 * 60,
    val quietEndMinutes: Int = 8 * 60,
    val contextCompressionThreshold: Float = 0.8f,
    val defaultModelProfileId: EntityId? = null,
    val reducedMotionEnabled: Boolean = false,
    val userName: String = "Yumerin User",
    val userAvatarUri: String? = null,
    val themeColor: ThemeColor = ThemeColor.BLUE,
    val componentMaterial: ComponentMaterial = ComponentMaterial.STANDARD
)

data class GroupMembershipWindow(
    val id: EntityId,
    val groupId: EntityId,
    val characterId: EntityId,
    val joinedAt: Long,
    val leftAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

data class CompanionSchedule(
    val id: EntityId,
    val characterId: EntityId,
    val chatId: EntityId,
    val title: String,
    val prompt: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val lastTriggeredAt: Long?,
    val dailyTriggerCount: Int,
    val dailyCountStamp: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

/** Durable, character-scoped interaction state; user affect itself is never stored here. */
data class CharacterRuntimeState(
    val characterId: EntityId,
    val moodValence: Float = 0f,
    val moodArousal: Float = 0.25f,
    val trust: Float = 0.5f,
    val closeness: Float = 0.5f,
    val completedInteractions: Int = 0,
    val lastInteractionAt: Long? = null,
    val sourceChatId: EntityId? = null,
    val sourceMessageId: EntityId? = null,
    val updatedAt: Long = 0L,
    /** How the character addresses the user ("小老板", pet names); user-editable. */
    val formsOfAddress: List<String> = emptyList(),
    /** Running jokes and shared references the character may echo. */
    val sharedInJokes: List<String> = emptyList(),
    /** Unfinished promises or follow-ups, each with an optional due time. */
    val openCommitments: List<OpenCommitment> = emptyList()
)

/** A promised follow-up in the relationship-state layer; surfaced in continuity settings. */
data class OpenCommitment(
    val text: String,
    val dueAtMillis: Long? = null
)

data class ScheduledAgentGoal(
    val id: EntityId,
    val characterId: EntityId,
    val chatId: EntityId,
    val title: String,
    val prompt: String,
    val dueAt: Long,
    val status: AgentGoalStatus = AgentGoalStatus.PENDING,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null,
    val resultMessageId: EntityId? = null,
    val failureReason: String? = null
)

/** A durable, multi-step task explicitly delegated by the user to the companion Agent. */
data class DelegatedAgentGoal(
    val id: EntityId,
    val characterId: EntityId,
    val chatId: EntityId,
    val title: String,
    val outcome: String,
    val constraints: String = "",
    val replanCount: Int = 0,
    val status: DelegatedAgentGoalStatus = DelegatedAgentGoalStatus.QUEUED,
    val steps: List<DelegatedAgentGoalStep> = emptyList(),
    val latestSummary: String? = null,
    val pendingQuestion: String? = null,
    val resumeInput: String? = null,
    val failureReason: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long? = null
)

data class DelegatedAgentGoalStep(
    val id: EntityId,
    val goalId: EntityId,
    val order: Int,
    val title: String,
    val status: DelegatedAgentGoalStepStatus = DelegatedAgentGoalStepStatus.PENDING,
    val summary: String? = null,
    val attempts: Int = 0
)
