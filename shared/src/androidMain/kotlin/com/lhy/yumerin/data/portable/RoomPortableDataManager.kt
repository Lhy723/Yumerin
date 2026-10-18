package com.lhy.yumerin.data.portable

import androidx.room.withTransaction
import com.lhy.yumerin.data.room.AppSettingsEntity
import com.lhy.yumerin.data.room.CharacterEntity
import com.lhy.yumerin.data.room.ChatMessageEntity
import com.lhy.yumerin.data.room.ChatThreadEntity
import com.lhy.yumerin.data.room.CompanionScheduleEntity
import com.lhy.yumerin.data.room.CharacterRuntimeStateEntity
import com.lhy.yumerin.data.room.ScheduledAgentGoalEntity
import com.lhy.yumerin.data.room.DelegatedAgentGoalEntity
import com.lhy.yumerin.data.room.DelegatedAgentGoalStepEntity
import com.lhy.yumerin.data.room.GroupEntity
import com.lhy.yumerin.data.room.GroupMembershipWindowEntity
import com.lhy.yumerin.data.room.MemoryEntryEntity
import com.lhy.yumerin.data.room.ModelProfileEntity
import com.lhy.yumerin.data.room.LocalModelArtifactEntity
import com.lhy.yumerin.data.room.YumerinDatabase
import com.lhy.yumerin.data.room.toDomain
import com.lhy.yumerin.domain.background.BackgroundCompanionController
import com.lhy.yumerin.domain.background.AgentGoalScheduler
import com.lhy.yumerin.domain.background.DelegatedAgentGoalScheduler
import com.lhy.yumerin.domain.repository.CredentialStore
import com.lhy.yumerin.domain.repository.ImportConflictPolicy
import com.lhy.yumerin.domain.repository.ImportPreview
import com.lhy.yumerin.domain.repository.ImportResult
import com.lhy.yumerin.domain.repository.PortableDataManager
import com.lhy.yumerin.domain.repository.PortableDataCounts
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Portable local package. Credentials and model binaries are deliberately excluded. */
class RoomPortableDataManager(
    private val database: YumerinDatabase,
    private val credentials: CredentialStore,
    private val backgroundCompanion: BackgroundCompanionController,
    private val modelsDirectory: File,
    private val agentGoalScheduler: AgentGoalScheduler,
    private val delegatedAgentGoalScheduler: DelegatedAgentGoalScheduler
) : PortableDataManager {
    override suspend fun exportData(): String {
        val root = JSONObject()
            .put("format", "yumerin-portable")
            .put("version", 1)
            .put("exportedAt", System.currentTimeMillis())
        root.put("characters", JSONArray(database.characterDao().getAll().map(::characterJson)))
        root.put("chats", JSONArray(database.chatThreadDao().getAll().map(::chatJson)))
        root.put("messages", JSONArray(database.chatMessageDao().getAll().map(::messageJson)))
        root.put("groups", JSONArray(database.groupDao().getAll().map(::groupJson)))
        root.put("memories", JSONArray(database.memoryEntryDao().getAll().map(::memoryJson)))
        root.put("models", JSONArray(database.modelProfileDao().getProfiles().map(::modelJson)))
        root.put("localArtifacts", JSONArray(database.modelProfileDao().getArtifacts().map(::artifactJson)))
        root.put("settings", JSONArray(database.settingsDao().getAll().map(::settingsJson)))
        root.put("membershipWindows", JSONArray(database.groupMembershipWindowDao().getAll().map(::membershipJson)))
        root.put("schedules", JSONArray(database.companionScheduleDao().getAll().map(::scheduleJson)))
        root.put("characterRuntimeStates", JSONArray(database.characterRuntimeStateDao().getAll().map(::characterRuntimeStateJson)))
        root.put("agentGoals", JSONArray(database.scheduledAgentGoalDao().getAll().map(::agentGoalJson)))
        val delegatedSteps = database.delegatedAgentGoalStepDao().getAll().groupBy { it.goalId }
        root.put("delegatedAgentGoals", JSONArray(database.delegatedAgentGoalDao().getAll().map { goal ->
            delegatedAgentGoalJson(goal, delegatedSteps[goal.id].orEmpty())
        }))
        return root.toString(2)
    }

    override suspend fun previewImport(payload: String): ImportPreview {
        return runCatching {
            val root = JSONObject(payload)
            require(root.optString("format") == "yumerin-portable") { "不是 Yumerin 数据包" }
            val version = root.optInt("version", -1)
            require(version == 1) { "不支持的数据包版本：$version" }
            val counts = root.counts()
            val existing = existingIds()
            val conflicts = conflictCount(root, existing)
            ImportPreview(
                valid = true,
                version = version,
                counts = counts,
                conflictCount = conflicts,
                message = if (conflicts == 0) "数据包可以导入" else "发现 $conflicts 个同 ID 记录冲突，请选择处理方式"
            )
        }.getOrElse {
            ImportPreview(false, null, message = it.message ?: "数据包格式无效")
        }
    }

    override suspend fun importData(payload: String, conflictPolicy: ImportConflictPolicy): ImportResult {
        return runCatching {
            val root = JSONObject(payload)
            require(root.optString("format") == "yumerin-portable") { "不是 Yumerin 数据包" }
            require(root.optInt("version", -1) == 1) { "不支持的数据包版本" }
            val existing = existingIds()
            var skipped = 0
            database.withTransaction {
                root.array("characters").forEach {
                    val entity = parseCharacter(it).copy(avatarUri = null)
                    if (shouldImport(entity.id, existing.characters, conflictPolicy)) database.characterDao().upsert(entity) else skipped++
                }
                root.array("chats").forEach {
                    val entity = parseChat(it).copy(avatarUri = null)
                    if (shouldImport(entity.id, existing.chats, conflictPolicy)) database.chatThreadDao().upsert(entity) else skipped++
                }
                root.array("messages").forEach {
                    val entity = parseMessage(it).copy(senderAvatarUri = null)
                    if (shouldImport(entity.id, existing.messages, conflictPolicy)) database.chatMessageDao().upsert(entity) else skipped++
                }
                root.array("groups").forEach {
                    val entity = parseGroup(it).copy(avatarUri = null)
                    if (shouldImport(entity.id, existing.groups, conflictPolicy)) database.groupDao().upsert(entity) else skipped++
                }
                root.array("memories").forEach {
                    val entity = parseMemory(it)
                    if (shouldImport(entity.id, existing.memories, conflictPolicy)) database.memoryEntryDao().upsert(entity) else skipped++
                }
                root.array("models").forEach {
                    val entity = parseModel(it)
                    if (shouldImport(entity.id, existing.models, conflictPolicy)) database.modelProfileDao().upsertProfile(entity) else skipped++
                }
                root.array("localArtifacts").forEach {
                    val artifact = parseArtifact(it)
                    if (shouldImport(artifact.id, existing.localArtifacts, conflictPolicy)) {
                        database.modelProfileDao().upsertArtifact(
                            artifact.copy(state = "CATALOGUED", localPath = null, diagnosticReason = "需要重新导入或下载模型文件")
                        )
                    } else skipped++
                }
                root.array("settings").forEach {
                    val entity = parseSettings(it)
                    if (shouldImport(entity.id, existing.settings, conflictPolicy)) database.settingsDao().upsert(entity) else skipped++
                }
                root.array("membershipWindows").forEach {
                    val entity = parseMembership(it)
                    if (shouldImport(entity.id, existing.membershipWindows, conflictPolicy)) database.groupMembershipWindowDao().upsert(entity) else skipped++
                }
                root.array("schedules").forEach {
                    val entity = parseSchedule(it)
                    if (shouldImport(entity.id, existing.schedules, conflictPolicy)) database.companionScheduleDao().upsert(entity) else skipped++
                }
                root.array("characterRuntimeStates").forEach {
                    val entity = parseCharacterRuntimeState(it)
                    if (shouldImport(entity.characterId, existing.characterRuntimeStates, conflictPolicy)) {
                        database.characterRuntimeStateDao().upsert(entity)
                    } else skipped++
                }
                root.array("agentGoals").forEach {
                    val entity = parseAgentGoal(it)
                    if (shouldImport(entity.id, existing.agentGoals, conflictPolicy)) {
                        database.scheduledAgentGoalDao().upsert(entity)
                    } else skipped++
                }
                root.array("delegatedAgentGoals").forEach {
                    val parsed = parseDelegatedAgentGoal(it)
                    if (shouldImport(parsed.goal.id, existing.delegatedAgentGoals, conflictPolicy)) {
                        database.delegatedAgentGoalDao().upsert(parsed.goal)
                        database.delegatedAgentGoalStepDao().deleteForGoal(parsed.goal.id)
                        parsed.steps.forEach { step -> database.delegatedAgentGoalStepDao().upsert(step) }
                    } else skipped++
                }
            }
            val proactiveEnabled = database.settingsDao().observeGlobalOnce()?.toDomain()?.proactiveMessagesEnabled == true
            val goalsRescheduled = try {
                agentGoalScheduler.setEnabled(proactiveEnabled)
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            val delegatedGoalsRescheduled = try {
                delegatedAgentGoalScheduler.reconcile()
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            val baseMessage = if (skipped == 0) {
                "数据已合并；API Key 和模型文件未导入，请在本机重新配置。"
            } else {
                "数据已合并，$skipped 条冲突记录已保留本机版本；API Key 和模型文件未导入。"
            }
            ImportResult(
                imported = true,
                message = baseMessage + (if (goalsRescheduled) "" else " 一次性目标将在下次启动时重试调度。") +
                    (if (delegatedGoalsRescheduled) "" else " 委托目标将在下次启动时重试调度。"),
                skippedConflicts = skipped
            )
        }.getOrElse {
            if (it is CancellationException) throw it
            ImportResult(false, it.message ?: "数据包导入失败")
        }
    }

    private data class ExistingIds(
        val characters: Set<String>,
        val chats: Set<String>,
        val messages: Set<String>,
        val groups: Set<String>,
        val memories: Set<String>,
        val models: Set<String>,
        val localArtifacts: Set<String>,
        val settings: Set<String>,
        val membershipWindows: Set<String>,
        val schedules: Set<String>,
        val characterRuntimeStates: Set<String>,
        val agentGoals: Set<String>,
        val delegatedAgentGoals: Set<String>
    )

    private suspend fun existingIds() = ExistingIds(
        characters = database.characterDao().getAll().map { it.id }.toSet(),
        chats = database.chatThreadDao().getAll().map { it.id }.toSet(),
        messages = database.chatMessageDao().getAll().map { it.id }.toSet(),
        groups = database.groupDao().getAll().map { it.id }.toSet(),
        memories = database.memoryEntryDao().getAll().map { it.id }.toSet(),
        models = database.modelProfileDao().getProfiles().map { it.id }.toSet(),
        localArtifacts = database.modelProfileDao().getArtifacts().map { it.id }.toSet(),
        settings = database.settingsDao().getAll().map { it.id }.toSet(),
        membershipWindows = database.groupMembershipWindowDao().getAll().map { it.id }.toSet(),
        schedules = database.companionScheduleDao().getAll().map { it.id }.toSet(),
        characterRuntimeStates = database.characterRuntimeStateDao().getAll().map { it.characterId }.toSet(),
        agentGoals = database.scheduledAgentGoalDao().getAll().map { it.id }.toSet(),
        delegatedAgentGoals = database.delegatedAgentGoalDao().getAll().map { it.id }.toSet()
    )

    private fun shouldImport(id: String, existing: Set<String>, policy: ImportConflictPolicy): Boolean =
        policy == ImportConflictPolicy.USE_IMPORTED || id !in existing

    private fun conflictCount(root: JSONObject, existing: ExistingIds): Int =
        root.array("characters").count { it.str("id") in existing.characters } +
            root.array("chats").count { it.str("id") in existing.chats } +
            root.array("messages").count { it.str("id") in existing.messages } +
            root.array("groups").count { it.str("id") in existing.groups } +
            root.array("memories").count { it.str("id") in existing.memories } +
            root.array("models").count { it.str("id") in existing.models } +
            root.array("localArtifacts").count { it.str("id") in existing.localArtifacts } +
            root.array("settings").count { it.str("id") in existing.settings } +
            root.array("membershipWindows").count { it.str("id") in existing.membershipWindows } +
            root.array("schedules").count { it.str("id") in existing.schedules } +
            root.array("characterRuntimeStates").count { it.str("characterId") in existing.characterRuntimeStates } +
            root.array("agentGoals").count { it.str("id") in existing.agentGoals } +
            root.array("delegatedAgentGoals").count { it.str("id") in existing.delegatedAgentGoals }

    override suspend fun wipeAll() {
        backgroundCompanion.stopAll()
        agentGoalScheduler.setEnabled(false)
        delegatedAgentGoalScheduler.cancelAll()
        credentials.clear()
        database.withTransaction {
            database.characterDao().deleteAll()
            database.chatThreadDao().deleteAll()
            database.chatMessageDao().deleteAll()
            database.groupDao().deleteAll()
            database.memoryEntryDao().deleteAll()
            database.modelProfileDao().deleteProfiles()
            database.modelProfileDao().deleteArtifacts()
            database.settingsDao().deleteAll()
            database.groupMembershipWindowDao().deleteAll()
            database.companionScheduleDao().deleteAll()
            database.characterRuntimeStateDao().deleteAll()
            database.scheduledAgentGoalDao().deleteAll()
            database.delegatedAgentGoalStepDao().deleteAll()
            database.delegatedAgentGoalDao().deleteAll()
        }
        modelsDirectory.deleteRecursively()
        modelsDirectory.parentFile?.resolve("avatars")?.deleteRecursively()
        modelsDirectory.parentFile?.resolve("model-imports")?.deleteRecursively()
    }

    private fun characterJson(value: CharacterEntity) = JSONObject()
        .put("id", value.id).put("name", value.name).putNullable("avatarUri", value.avatarUri)
        .put("relationshipFraming", value.relationshipFraming).put("setting", value.setting)
        .put("description", value.description).put("personality", value.personality).put("tone", value.tone)
        .put("backstory", value.backstory).put("interactionStyle", value.interactionStyle)
        .putNullable("modelProfileId", value.modelProfileId).put("archived", value.archived)
        .put("createdAt", value.createdAt).put("updatedAt", value.updatedAt)
        .put("proactiveEnabled", value.proactiveEnabled).put("proactiveDailyLimit", value.proactiveDailyLimit)
        .putNullable("deletedAt", value.deletedAt)

    private fun chatJson(value: ChatThreadEntity) = JSONObject()
        .put("id", value.id).put("kind", value.kind).put("title", value.title)
        .putNullable("characterId", value.characterId).putNullable("groupId", value.groupId)
        .putNullable("avatarUri", value.avatarUri).put("lastMessagePreview", value.lastMessagePreview)
        .put("lastActivityAt", value.lastActivityAt).put("unreadCount", value.unreadCount)
        .put("pinned", value.pinned).put("muted", value.muted).put("shortTermSummary", value.shortTermSummary)
        .put("summaryMaintenanceMode", value.summaryMaintenanceMode).putNullable("deletedAt", value.deletedAt)

    private fun messageJson(value: ChatMessageEntity) = JSONObject()
        .put("id", value.id).put("chatId", value.chatId).putNullable("senderId", value.senderId)
        .put("senderType", value.senderType).put("senderName", value.senderName)
        .putNullable("senderAvatarUri", value.senderAvatarUri).put("text", value.text)
        .put("timestamp", value.timestamp).put("type", value.type).put("isProactive", value.isProactive)
        .put("isRead", value.isRead).put("deliveryState", value.deliveryState)
        .putNullable("responseState", value.responseState).putNullable("errorMessage", value.errorMessage)
        .put("usedMemoryIds", value.usedMemoryIds).putNullable("deletedAt", value.deletedAt)

    private fun groupJson(value: GroupEntity) = JSONObject()
        .put("id", value.id).put("name", value.name).putNullable("avatarUri", value.avatarUri)
        .put("memberCharacterIds", value.memberCharacterIds).put("scenario", value.scenario)
        .put("storySetting", value.storySetting).put("interactionStyle", value.interactionStyle)
        .put("autonomousTurnLimit", value.autonomousTurnLimit).put("summary", value.summary)
        .put("summaryMaintenanceMode", value.summaryMaintenanceMode).put("archived", value.archived)
        .put("createdAt", value.createdAt).put("updatedAt", value.updatedAt).putNullable("deletedAt", value.deletedAt)

    private fun memoryJson(value: MemoryEntryEntity) = JSONObject()
        .put("id", value.id).put("characterId", value.characterId).putNullable("sourceChatId", value.sourceChatId)
        .putNullable("sourceMessageId", value.sourceMessageId).put("category", value.category)
        .put("content", value.content).put("confidence", value.confidence).put("reviewState", value.reviewState)
        .put("pinned", value.pinned).putNullable("supersedesId", value.supersedesId)
        .put("createdAt", value.createdAt).put("updatedAt", value.updatedAt).putNullable("reviewAt", value.reviewAt)
        .putNullable("reviewReason", value.reviewReason).putNullable("expiresAt", value.expiresAt)
        .putNullable("deletedAt", value.deletedAt).put("importance", value.importance)
        .putNullable("emotionTag", value.emotionTag).putNullable("occurredAt", value.occurredAt)
        .putNullable("factSubject", value.factSubject).putNullable("factPredicate", value.factPredicate)
        .putNullable("factValue", value.factValue)

    private fun modelJson(value: ModelProfileEntity) = JSONObject()
        .put("id", value.id).put("displayName", value.displayName).put("providerKind", value.providerKind)
        .put("providerName", value.providerName).putNullable("baseUrl", value.baseUrl).put("modelId", value.modelId)
        .putNullable("credentialAlias", value.credentialAlias).putNullable("contextLength", value.contextLength)
        .put("contextLengthMode", value.contextLengthMode).putNullable("temperature", value.temperature)
        .putNullable("topP", value.topP).putNullable("maxOutputTokens", value.maxOutputTokens)
        .put("stopSequences", value.stopSequences).putNullable("localModelId", value.localModelId)
        .putNullable("localModelVersion", value.localModelVersion).put("enabled", value.enabled)
        .put("createdAt", value.createdAt).put("updatedAt", value.updatedAt).put("localBackend", value.localBackend)
        .putNullable("deletedAt", value.deletedAt)

    private fun artifactJson(value: LocalModelArtifactEntity) = JSONObject()
        .put("id", value.id).put("logicalModelId", value.logicalModelId).put("displayName", value.displayName).put("version", value.version)
        .put("fileName", value.fileName).put("fileSizeBytes", value.fileSizeBytes).put("sha256", value.sha256)
        .put("license", value.license).putNullable("sourceUrl", value.sourceUrl).put("backend", value.backend)
        .putNullable("contextLength", value.contextLength).put("state", value.state)
        .put("assignedProfileCount", value.assignedProfileCount)
        .put("createdAt", value.createdAt).put("updatedAt", value.updatedAt)
        .put("deviceRequirements", value.deviceRequirements).put("licenseAcknowledged", value.licenseAcknowledged)
        .putNullable("diagnosticReason", value.diagnosticReason).putNullable("deletedAt", value.deletedAt)

    private fun settingsJson(value: AppSettingsEntity) = JSONObject()
        .put("id", value.id).put("adultConfirmed", value.adultConfirmed).put("themeMode", value.themeMode)
        .put("agentVisibility", value.agentVisibility).put("streamingEnabled", value.streamingEnabled)
        .put("autoMemoryEnabled", value.autoMemoryEnabled).put("proactiveMessagesEnabled", value.proactiveMessagesEnabled)
        .put("backgroundCompanionEnabled", value.backgroundCompanionEnabled).put("dailyProactiveLimit", value.dailyProactiveLimit)
        .put("quietTimeEnabled", value.quietTimeEnabled).put("quietStartMinutes", value.quietStartMinutes)
        .put("quietEndMinutes", value.quietEndMinutes).put("contextCompressionThreshold", value.contextCompressionThreshold)
        .putNullable("defaultModelProfileId", value.defaultModelProfileId).put("reducedMotionEnabled", value.reducedMotionEnabled)
        .put("userName", value.userName).put("themeColor", value.themeColor)
        .put("componentMaterial", value.componentMaterial)

    private fun membershipJson(value: GroupMembershipWindowEntity) = JSONObject()
        .put("id", value.id).put("groupId", value.groupId).put("characterId", value.characterId)
        .put("joinedAt", value.joinedAt).putNullable("leftAt", value.leftAt)
        .put("createdAt", value.createdAt).put("updatedAt", value.updatedAt)

    private fun scheduleJson(value: CompanionScheduleEntity) = JSONObject()
        .put("id", value.id).put("characterId", value.characterId).put("chatId", value.chatId)
        .put("title", value.title).put("prompt", value.prompt).put("hour", value.hour).put("minute", value.minute)
        .put("enabled", value.enabled).putNullable("lastTriggeredAt", value.lastTriggeredAt)
        .put("dailyTriggerCount", value.dailyTriggerCount).putNullable("dailyCountStamp", value.dailyCountStamp)
        .put("createdAt", value.createdAt).put("updatedAt", value.updatedAt).putNullable("deletedAt", value.deletedAt)

    private fun characterRuntimeStateJson(value: CharacterRuntimeStateEntity) = JSONObject()
        .put("characterId", value.characterId).put("moodValence", value.moodValence).put("moodArousal", value.moodArousal)
        .put("trust", value.trust).put("closeness", value.closeness).put("completedInteractions", value.completedInteractions)
        .putNullable("lastInteractionAt", value.lastInteractionAt).putNullable("sourceChatId", value.sourceChatId)
        .putNullable("sourceMessageId", value.sourceMessageId).put("updatedAt", value.updatedAt)
        .put("formsOfAddress", value.formsOfAddress).put("sharedInJokes", value.sharedInJokes)
        .put("openCommitments", value.openCommitments)

    private fun agentGoalJson(value: ScheduledAgentGoalEntity) = JSONObject()
        .put("id", value.id).put("characterId", value.characterId).put("chatId", value.chatId)
        .put("title", value.title).put("prompt", value.prompt).put("dueAt", value.dueAt).put("status", value.status)
        .put("createdAt", value.createdAt).put("updatedAt", value.updatedAt).putNullable("completedAt", value.completedAt)
        .putNullable("resultMessageId", value.resultMessageId).putNullable("failureReason", value.failureReason)

    private fun delegatedAgentGoalJson(value: DelegatedAgentGoalEntity, steps: List<DelegatedAgentGoalStepEntity>) = JSONObject()
        .put("id", value.id).put("characterId", value.characterId).put("chatId", value.chatId)
        .put("title", value.title).put("outcome", value.outcome).put("constraints", value.constraints)
        .put("replanCount", value.replanCount)
        .put("status", value.status).putNullable("latestSummary", value.latestSummary)
        .putNullable("pendingQuestion", value.pendingQuestion).putNullable("resumeInput", value.resumeInput)
        .putNullable("failureReason", value.failureReason).put("createdAt", value.createdAt)
        .put("updatedAt", value.updatedAt).putNullable("completedAt", value.completedAt)
        .put("steps", JSONArray(steps.sortedBy { it.stepOrder }.map { step ->
            JSONObject().put("id", step.id).put("goalId", step.goalId).put("order", step.stepOrder)
                .put("title", step.title).put("status", step.status).putNullable("summary", step.summary)
                .put("attempts", step.attempts)
        }))

    private fun parseCharacter(v: JSONObject) = CharacterEntity(
        id = v.str("id"), name = v.str("name"), avatarUri = v.nullableString("avatarUri"),
        relationshipFraming = v.str("relationshipFraming"), setting = v.str("setting"), description = v.str("description"),
        personality = v.str("personality"), tone = v.str("tone"), backstory = v.str("backstory"),
        interactionStyle = v.str("interactionStyle"), modelProfileId = v.nullableString("modelProfileId"),
        archived = v.optBoolean("archived"), createdAt = v.optLong("createdAt"), updatedAt = v.optLong("updatedAt"),
        proactiveEnabled = v.optBoolean("proactiveEnabled"), proactiveDailyLimit = v.optInt("proactiveDailyLimit", 3),
        deletedAt = v.nullableLong("deletedAt")
    )

    private fun parseChat(v: JSONObject) = ChatThreadEntity(
        id = v.str("id"), kind = v.str("kind"), title = v.str("title"), characterId = v.nullableString("characterId"),
        groupId = v.nullableString("groupId"), avatarUri = v.nullableString("avatarUri"),
        lastMessagePreview = v.str("lastMessagePreview"), lastActivityAt = v.optLong("lastActivityAt"),
        unreadCount = v.optInt("unreadCount"), pinned = v.optBoolean("pinned"), muted = v.optBoolean("muted"),
        shortTermSummary = v.str("shortTermSummary"), summaryMaintenanceMode = v.str("summaryMaintenanceMode"),
        deletedAt = v.nullableLong("deletedAt")
    )

    private fun parseMessage(v: JSONObject) = ChatMessageEntity(
        id = v.str("id"), chatId = v.str("chatId"), senderId = v.nullableString("senderId"),
        senderType = v.str("senderType"), senderName = v.str("senderName"), senderAvatarUri = v.nullableString("senderAvatarUri"),
        text = v.str("text"), timestamp = v.optLong("timestamp"), type = v.str("type"), isProactive = v.optBoolean("isProactive"),
        isRead = v.optBoolean("isRead"), deliveryState = v.optString("deliveryState", "SENT"),
        responseState = v.nullableString("responseState"), errorMessage = v.nullableString("errorMessage"),
        usedMemoryIds = v.optString("usedMemoryIds"), deletedAt = v.nullableLong("deletedAt")
    )

    private fun parseGroup(v: JSONObject) = GroupEntity(
        id = v.str("id"), name = v.str("name"), avatarUri = v.nullableString("avatarUri"),
        memberCharacterIds = v.str("memberCharacterIds"), scenario = v.str("scenario"), storySetting = v.str("storySetting"),
        interactionStyle = v.str("interactionStyle"), autonomousTurnLimit = v.optInt("autonomousTurnLimit", 3),
        summary = v.str("summary"), summaryMaintenanceMode = v.str("summaryMaintenanceMode"), archived = v.optBoolean("archived"),
        createdAt = v.optLong("createdAt"), updatedAt = v.optLong("updatedAt"), deletedAt = v.nullableLong("deletedAt")
    )

    private fun parseMemory(v: JSONObject) = MemoryEntryEntity(
        id = v.str("id"), characterId = v.str("characterId"), sourceChatId = v.nullableString("sourceChatId"),
        sourceMessageId = v.nullableString("sourceMessageId"), category = v.str("category"), content = v.str("content"),
        confidence = v.optDouble("confidence", 0.0).toFloat(), reviewState = v.str("reviewState"), pinned = v.optBoolean("pinned"),
        supersedesId = v.nullableString("supersedesId"), createdAt = v.optLong("createdAt"), updatedAt = v.optLong("updatedAt"),
        reviewAt = v.nullableLong("reviewAt"), reviewReason = v.nullableString("reviewReason"), expiresAt = v.nullableLong("expiresAt"),
        deletedAt = v.nullableLong("deletedAt"), importance = v.optDouble("importance", 0.5).toFloat(),
        emotionTag = v.nullableString("emotionTag"), occurredAt = v.nullableLong("occurredAt"),
        factSubject = v.nullableString("factSubject"), factPredicate = v.nullableString("factPredicate"),
        factValue = v.nullableString("factValue")
    )

    private fun parseModel(v: JSONObject) = ModelProfileEntity(
        id = v.str("id"), displayName = v.str("displayName"), providerKind = v.str("providerKind"), providerName = v.str("providerName"),
        baseUrl = v.nullableString("baseUrl"), modelId = v.str("modelId"), credentialAlias = v.nullableString("credentialAlias"),
        contextLength = v.nullableInt("contextLength"), contextLengthMode = v.str("contextLengthMode"),
        temperature = v.nullableDouble("temperature"), topP = v.nullableDouble("topP"), maxOutputTokens = v.nullableInt("maxOutputTokens"),
        stopSequences = v.optString("stopSequences"), localModelId = v.nullableString("localModelId"), localModelVersion = v.nullableString("localModelVersion"),
        enabled = v.optBoolean("enabled"), createdAt = v.optLong("createdAt"), updatedAt = v.optLong("updatedAt"),
        localBackend = v.optString("localBackend", "auto"), deletedAt = v.nullableLong("deletedAt")
    )

    private fun parseArtifact(v: JSONObject) = LocalModelArtifactEntity(
        id = v.str("id"), logicalModelId = v.optString("logicalModelId", v.str("id")), displayName = v.str("displayName"), version = v.str("version"), fileName = v.str("fileName"),
        fileSizeBytes = v.optLong("fileSizeBytes"), sha256 = v.str("sha256"), license = v.str("license"), sourceUrl = v.nullableString("sourceUrl"),
        backend = v.str("backend"), contextLength = v.nullableInt("contextLength"), state = v.str("state"), localPath = null,
        assignedProfileCount = v.optInt("assignedProfileCount"), createdAt = v.optLong("createdAt"), updatedAt = v.optLong("updatedAt"),
        deviceRequirements = v.optString("deviceRequirements"), licenseAcknowledged = v.optBoolean("licenseAcknowledged"),
        diagnosticReason = v.nullableString("diagnosticReason"), deletedAt = v.nullableLong("deletedAt")
    )

    private fun parseSettings(v: JSONObject) = AppSettingsEntity(
        id = v.optString("id", AppSettingsEntity.GLOBAL_ID), adultConfirmed = v.optBoolean("adultConfirmed"),
        themeMode = v.str("themeMode"), agentVisibility = v.str("agentVisibility"), streamingEnabled = v.optBoolean("streamingEnabled"),
        autoMemoryEnabled = v.optBoolean("autoMemoryEnabled", true), proactiveMessagesEnabled = v.optBoolean("proactiveMessagesEnabled"),
        backgroundCompanionEnabled = false, dailyProactiveLimit = v.optInt("dailyProactiveLimit", 3), quietTimeEnabled = v.optBoolean("quietTimeEnabled", true),
        quietStartMinutes = v.optInt("quietStartMinutes", 1380), quietEndMinutes = v.optInt("quietEndMinutes", 480),
        contextCompressionThreshold = v.optDouble("contextCompressionThreshold", 0.8).toFloat(),
        defaultModelProfileId = v.nullableString("defaultModelProfileId"), reducedMotionEnabled = v.optBoolean("reducedMotionEnabled", false),
        userName = v.optString("userName", "Yumerin User"), themeColor = v.optString("themeColor", "BLUE"),
        componentMaterial = v.optString("componentMaterial", "STANDARD")
    )

    private fun parseMembership(v: JSONObject) = GroupMembershipWindowEntity(
        id = v.str("id"), groupId = v.str("groupId"), characterId = v.str("characterId"), joinedAt = v.optLong("joinedAt"),
        leftAt = v.nullableLong("leftAt"), createdAt = v.optLong("createdAt"), updatedAt = v.optLong("updatedAt")
    )

    private fun parseSchedule(v: JSONObject) = CompanionScheduleEntity(
        id = v.str("id"), characterId = v.str("characterId"), chatId = v.str("chatId"), title = v.str("title"), prompt = v.str("prompt"),
        hour = v.optInt("hour"), minute = v.optInt("minute"), enabled = v.optBoolean("enabled"), lastTriggeredAt = v.nullableLong("lastTriggeredAt"),
        dailyTriggerCount = v.optInt("dailyTriggerCount"), dailyCountStamp = v.nullableString("dailyCountStamp"), createdAt = v.optLong("createdAt"), updatedAt = v.optLong("updatedAt"), deletedAt = v.nullableLong("deletedAt")
    )

    private fun parseCharacterRuntimeState(v: JSONObject): CharacterRuntimeStateEntity {
        fun boundedFloat(key: String, default: Float, min: Float, max: Float): Float {
            val parsed = v.optDouble(key, default.toDouble()).toFloat()
            return if (parsed.isFinite()) parsed.coerceIn(min, max) else default
        }

        return CharacterRuntimeStateEntity(
            characterId = v.str("characterId"),
            moodValence = boundedFloat("moodValence", 0f, -1f, 1f),
            moodArousal = boundedFloat("moodArousal", 0.25f, 0f, 1f),
            trust = boundedFloat("trust", 0.5f, 0f, 1f),
            closeness = boundedFloat("closeness", 0.5f, 0f, 1f),
            completedInteractions = v.optInt("completedInteractions").coerceAtLeast(0),
            lastInteractionAt = v.nullableLong("lastInteractionAt"),
            sourceChatId = v.nullableString("sourceChatId"),
            sourceMessageId = v.nullableString("sourceMessageId"),
            updatedAt = v.optLong("updatedAt").coerceAtLeast(0L),
            formsOfAddress = v.optString("formsOfAddress"),
            sharedInJokes = v.optString("sharedInJokes"),
            openCommitments = v.optString("openCommitments")
        )
    }

    private fun parseAgentGoal(v: JSONObject): ScheduledAgentGoalEntity {
        val id = v.str("id")
        val characterId = v.str("characterId")
        val chatId = v.str("chatId")
        val title = v.str("title")
        val prompt = v.str("prompt")
        val dueAt = v.optLong("dueAt")
        val status = v.optString("status", "PENDING")
        val createdAt = v.optLong("createdAt")
        val updatedAt = v.optLong("updatedAt")
        require(id.isNotBlank() && characterId.isNotBlank() && chatId.isNotBlank()) { "一次性目标缺少标识" }
        require(title.isNotBlank() && title.length <= 80 && prompt.isNotBlank() && prompt.length <= 1_000) { "一次性目标内容长度无效" }
        require(dueAt > 0L && createdAt > 0L && updatedAt > 0L) { "一次性目标时间无效" }
        require(status in setOf("PENDING", "COMPLETED", "CANCELLED", "FAILED")) { "一次性目标状态无效" }
        return ScheduledAgentGoalEntity(
            id = id, characterId = characterId, chatId = chatId,
            title = title, prompt = prompt, dueAt = dueAt, status = status,
            createdAt = createdAt, updatedAt = updatedAt,
            completedAt = v.nullableLong("completedAt"), resultMessageId = v.nullableString("resultMessageId"),
            failureReason = v.nullableString("failureReason")
        )
    }

    private data class ParsedDelegatedGoal(
        val goal: DelegatedAgentGoalEntity,
        val steps: List<DelegatedAgentGoalStepEntity>
    )

    private fun parseDelegatedAgentGoal(v: JSONObject): ParsedDelegatedGoal {
        val id = v.str("id")
        val characterId = v.str("characterId")
        val chatId = v.str("chatId")
        val title = v.str("title")
        val outcome = v.str("outcome")
        val constraints = v.optString("constraints")
        val status = v.optString("status", "QUEUED")
        val createdAt = v.optLong("createdAt")
        val updatedAt = v.optLong("updatedAt")
        require(id.isNotBlank() && characterId.isNotBlank() && chatId.isNotBlank()) { "委托目标缺少标识" }
        require(title.isNotBlank() && title.length <= 80 && outcome.isNotBlank() && outcome.length <= 1_000 && constraints.length <= 1_500) {
            "委托目标内容长度无效"
        }
        require(createdAt > 0L && updatedAt > 0L) { "委托目标时间无效" }
        require(status in setOf("QUEUED", "RUNNING", "PAUSED", "WAITING_FOR_USER", "COMPLETED", "CANCELLED", "FAILED")) {
            "委托目标状态无效"
        }
        val steps = v.array("steps").map { step ->
            val stepId = step.str("id")
            val stepGoalId = step.optString("goalId", id)
            val order = step.optInt("order", -1)
            val stepTitle = step.str("title")
            val stepStatus = step.optString("status", "PENDING")
            require(stepId.isNotBlank() && stepGoalId == id && order >= 0) { "委托目标步骤标识无效" }
            require(stepTitle.isNotBlank() && stepTitle.length <= 240) { "委托目标步骤标题无效" }
            require(stepStatus in setOf("PENDING", "RUNNING", "COMPLETED", "FAILED")) { "委托目标步骤状态无效" }
            DelegatedAgentGoalStepEntity(
                id = stepId,
                goalId = id,
                stepOrder = order,
                title = stepTitle,
                status = stepStatus,
                summary = step.nullableString("summary")?.take(900),
                attempts = step.optInt("attempts").coerceIn(0, 100)
            )
        }
        require(steps.size <= 6 && steps.map { it.id }.distinct().size == steps.size && steps.map { it.stepOrder }.distinct().size == steps.size) {
            "委托目标步骤数量或顺序无效"
        }
        return ParsedDelegatedGoal(
            goal = DelegatedAgentGoalEntity(
                id = id,
                characterId = characterId,
                chatId = chatId,
                title = title,
                outcome = outcome,
                constraints = constraints,
                replanCount = v.optInt("replanCount").coerceIn(0, 2),
                status = status,
                latestSummary = v.nullableString("latestSummary")?.take(900),
                pendingQuestion = v.nullableString("pendingQuestion")?.take(500),
                resumeInput = v.nullableString("resumeInput")?.take(1_000),
                failureReason = v.nullableString("failureReason")?.take(240),
                createdAt = createdAt,
                updatedAt = updatedAt,
                completedAt = v.nullableLong("completedAt")
            ),
            steps = steps
        )
    }
}

private fun JSONObject.array(key: String): List<JSONObject> = buildList {
    val array = optJSONArray(key) ?: return@buildList
    for (index in 0 until array.length()) array.optJSONObject(index)?.let(::add)
}

private fun JSONObject.counts() = PortableDataCounts(
    characters = array("characters").size,
    chats = array("chats").size,
    messages = array("messages").size,
    groups = array("groups").size,
    memories = array("memories").size,
    models = array("models").size,
    localArtifacts = array("localArtifacts").size,
    settings = array("settings").size,
    membershipWindows = array("membershipWindows").size,
    schedules = array("schedules").size,
    characterRuntimeStates = array("characterRuntimeStates").size,
    agentGoals = array("agentGoals").size,
    delegatedAgentGoals = array("delegatedAgentGoals").size
)

private fun JSONObject.str(key: String): String = optString(key, "")
private fun JSONObject.nullableString(key: String): String? = if (isNull(key) || !has(key)) null else getString(key)
private fun JSONObject.nullableLong(key: String): Long? = if (isNull(key) || !has(key)) null else optLong(key)
private fun JSONObject.nullableInt(key: String): Int? = if (isNull(key) || !has(key)) null else optInt(key)
private fun JSONObject.nullableDouble(key: String): Double? = if (isNull(key) || !has(key)) null else optDouble(key)
private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)
