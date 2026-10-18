package com.lhy.yumerin.data.room

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(tableName = "characters")
data class CharacterEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatarUri: String?,
    val relationshipFraming: String,
    val setting: String,
    val description: String,
    val personality: String,
    val tone: String,
    val backstory: String,
    val interactionStyle: String,
    val modelProfileId: String?,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val proactiveEnabled: Boolean = false,
    val proactiveDailyLimit: Int = 3,
    val deletedAt: Long? = null
)

@Entity(tableName = "chat_threads")
data class ChatThreadEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val title: String,
    val characterId: String?,
    val groupId: String?,
    val avatarUri: String?,
    val lastMessagePreview: String,
    val lastActivityAt: Long,
    val unreadCount: Int,
    val pinned: Boolean,
    val muted: Boolean,
    val shortTermSummary: String,
    val summaryMaintenanceMode: String,
    val deletedAt: Long? = null
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String?,
    val senderType: String,
    val senderName: String,
    val senderAvatarUri: String?,
    val text: String,
    val timestamp: Long,
    val type: String,
    val isProactive: Boolean,
    val isRead: Boolean,
    val deliveryState: String = "SENT",
    val responseState: String? = null,
    val errorMessage: String? = null,
    val usedMemoryIds: String = "",
    val deletedAt: Long? = null,
    val partsJson: String = ""
)

@Entity(tableName = "groups")
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatarUri: String?,
    val memberCharacterIds: String,
    val scenario: String,
    val storySetting: String,
    val interactionStyle: String,
    val autonomousTurnLimit: Int,
    val summary: String,
    val summaryMaintenanceMode: String,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Entity(tableName = "memory_entries")
data class MemoryEntryEntity(
    @PrimaryKey val id: String,
    val characterId: String,
    val sourceChatId: String?,
    val sourceMessageId: String?,
    val category: String,
    val content: String,
    val confidence: Float,
    val reviewState: String,
    val pinned: Boolean,
    val supersedesId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val reviewAt: Long?,
    val reviewReason: String? = null,
    val expiresAt: Long? = null,
    val deletedAt: Long? = null,
    val importance: Float = 0.5f,
    val emotionTag: String? = null,
    val occurredAt: Long? = null,
    val factSubject: String? = null,
    val factPredicate: String? = null,
    val factValue: String? = null
)

@Entity(tableName = "model_profiles")
data class ModelProfileEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val providerKind: String,
    val providerName: String,
    val baseUrl: String?,
    val modelId: String,
    val credentialAlias: String?,
    val contextLength: Int?,
    val contextLengthMode: String,
    val temperature: Double?,
    val topP: Double?,
    val maxOutputTokens: Int?,
    val stopSequences: String,
    val localModelId: String?,
    val localModelVersion: String?,
    val enabled: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val localBackend: String = "auto",
    val deletedAt: Long? = null,
    val capabilities: String = ""
)

@Entity(tableName = "local_model_artifacts")
data class LocalModelArtifactEntity(
    @PrimaryKey val id: String,
    val logicalModelId: String = "",
    val displayName: String,
    val version: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val sha256: String,
    val license: String,
    val sourceUrl: String?,
    val backend: String,
    val contextLength: Int?,
    val state: String,
    val localPath: String?,
    val assignedProfileCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deviceRequirements: String = "",
    val licenseAcknowledged: Boolean = false,
    val diagnosticReason: String? = null,
    val deletedAt: Long? = null
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: String = GLOBAL_ID,
    val adultConfirmed: Boolean,
    val themeMode: String,
    val agentVisibility: String,
    val streamingEnabled: Boolean,
    val autoMemoryEnabled: Boolean,
    val proactiveMessagesEnabled: Boolean,
    val backgroundCompanionEnabled: Boolean,
    val dailyProactiveLimit: Int,
    val quietTimeEnabled: Boolean,
    val quietStartMinutes: Int,
    val quietEndMinutes: Int,
    val contextCompressionThreshold: Float,
    val defaultModelProfileId: String? = null,
    val reducedMotionEnabled: Boolean = false,
    val userName: String = "Yumerin User",
    val userAvatarUri: String? = null,
    @ColumnInfo(defaultValue = "'BLUE'")
    val themeColor: String = "BLUE",
    @ColumnInfo(defaultValue = "'STANDARD'")
    val componentMaterial: String = "STANDARD"
) {
    companion object {
        const val GLOBAL_ID = "global"
    }
}

@Entity(tableName = "group_membership_windows")
data class GroupMembershipWindowEntity(
    @PrimaryKey val id: String,
    val groupId: String,
    val characterId: String,
    val joinedAt: Long,
    val leftAt: Long?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "companion_schedules")
data class CompanionScheduleEntity(
    @PrimaryKey val id: String,
    val characterId: String,
    val chatId: String,
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

@Entity(tableName = "character_runtime_states")
data class CharacterRuntimeStateEntity(
    @PrimaryKey val characterId: String,
    val moodValence: Float,
    val moodArousal: Float,
    val trust: Float,
    val closeness: Float,
    val completedInteractions: Int,
    val lastInteractionAt: Long?,
    val sourceChatId: String?,
    val sourceMessageId: String?,
    val updatedAt: Long,
    val formsOfAddress: String = "",
    val sharedInJokes: String = "",
    val openCommitments: String = ""
)

@Entity(tableName = "scheduled_agent_goals")
data class ScheduledAgentGoalEntity(
    @PrimaryKey val id: String,
    val characterId: String,
    val chatId: String,
    val title: String,
    val prompt: String,
    val dueAt: Long,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    val resultMessageId: String?,
    val failureReason: String?
)

@Entity(tableName = "delegated_agent_goals")
data class DelegatedAgentGoalEntity(
    @PrimaryKey val id: String,
    val characterId: String,
    val chatId: String,
    val title: String,
    val outcome: String,
    val constraints: String,
    @ColumnInfo(defaultValue = "0") val replanCount: Int = 0,
    val status: String,
    val latestSummary: String?,
    val pendingQuestion: String?,
    val resumeInput: String?,
    val failureReason: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?
)

@Entity(tableName = "delegated_agent_goal_steps", primaryKeys = ["id", "goalId"])
data class DelegatedAgentGoalStepEntity(
    val id: String,
    val goalId: String,
    val stepOrder: Int,
    val title: String,
    val status: String,
    val summary: String?,
    val attempts: Int
)
