package com.lhy.yumerin.domain.repository

import com.lhy.yumerin.core.model.AppSettings
import com.lhy.yumerin.core.model.CharacterProfile
import com.lhy.yumerin.core.model.ChatMessage
import com.lhy.yumerin.core.model.ChatThread
import com.lhy.yumerin.core.model.CompanionSchedule
import com.lhy.yumerin.core.model.EntityId
import com.lhy.yumerin.core.model.GroupProfile
import com.lhy.yumerin.core.model.GroupMembershipWindow
import com.lhy.yumerin.core.model.LocalModelArtifact
import com.lhy.yumerin.core.model.MemoryEntry
import com.lhy.yumerin.core.model.ModelProfile
import kotlinx.coroutines.flow.Flow

interface CredentialStore {
    suspend fun save(alias: String, apiKey: String)
    suspend fun read(alias: String): String?
    suspend fun remove(alias: String)
    suspend fun clear()
}

interface AvatarStore {
    suspend fun delete(uri: String)
}

interface CharacterRepository {
    fun observeCharacters(): Flow<List<CharacterProfile>>
    fun observeArchivedCharacters(): Flow<List<CharacterProfile>>
    suspend fun getCharacter(id: EntityId): CharacterProfile?
    suspend fun saveCharacter(character: CharacterProfile)
    suspend fun archiveCharacter(id: EntityId, archived: Boolean)
    suspend fun deletePermanently(id: EntityId)
}

interface ChatRepository {
    fun observeChats(): Flow<List<ChatThread>>
    fun observeMessages(chatId: EntityId): Flow<List<ChatMessage>>
    suspend fun getChat(id: EntityId): ChatThread?
    suspend fun getMessages(chatId: EntityId): List<ChatMessage>
    suspend fun saveChat(chat: ChatThread)
    suspend fun appendMessage(message: ChatMessage)
    suspend fun markRead(chatId: EntityId)
    suspend fun deleteChat(id: EntityId)
}

interface GroupRepository {
    fun observeGroups(): Flow<List<GroupProfile>>
    suspend fun getGroup(id: EntityId): GroupProfile?
    suspend fun saveGroup(group: GroupProfile)
    suspend fun deleteGroup(id: EntityId)
}

interface MemoryRepository {
    fun observeMemories(characterId: EntityId): Flow<List<MemoryEntry>>
    suspend fun getMemory(id: EntityId): MemoryEntry?
    suspend fun saveMemory(memory: MemoryEntry)
    suspend fun deleteMemory(id: EntityId)
}

interface ModelProfileRepository {
    fun observeProfiles(): Flow<List<ModelProfile>>
    fun observeLocalArtifacts(): Flow<List<LocalModelArtifact>>
    suspend fun getProfiles(): List<ModelProfile>
    suspend fun getLocalArtifacts(): List<LocalModelArtifact>
    suspend fun getProfile(id: EntityId): ModelProfile?
    suspend fun getLocalArtifact(id: EntityId): LocalModelArtifact?
    suspend fun saveProfile(profile: ModelProfile)
    suspend fun saveLocalArtifact(artifact: LocalModelArtifact)
    suspend fun deleteProfile(id: EntityId)
    suspend fun deleteLocalArtifact(id: EntityId)
}

interface GroupMembershipRepository {
    fun observeWindows(groupId: EntityId): Flow<List<GroupMembershipWindow>>
    suspend fun getActiveWindow(groupId: EntityId, characterId: EntityId): GroupMembershipWindow?
    suspend fun saveWindow(window: GroupMembershipWindow)
    suspend fun closeWindow(groupId: EntityId, characterId: EntityId, leftAt: Long)
}

interface CompanionScheduleRepository {
    fun observeSchedules(): Flow<List<CompanionSchedule>>
    suspend fun getSchedule(id: EntityId): CompanionSchedule?
    suspend fun saveSchedule(schedule: CompanionSchedule)
    suspend fun deleteSchedule(id: EntityId)
}

/** Platform boundary for catalog/import/download/activation work; no Android types cross it. */
interface LocalModelGateway {
    suspend fun registerCatalogEntry(artifact: LocalModelArtifact)
    suspend fun importArtifact(artifact: LocalModelArtifact, sourcePath: String): LocalModelArtifact
    suspend fun startDownload(artifactId: EntityId, allowMetered: Boolean = false)
    suspend fun pauseDownload(artifactId: EntityId)
    suspend fun cancelDownload(artifactId: EntityId)
    suspend fun activate(artifactId: EntityId): LocalModelArtifact
    suspend fun remove(artifactId: EntityId)
}

data class PortableDataCounts(
    val characters: Int = 0,
    val chats: Int = 0,
    val messages: Int = 0,
    val groups: Int = 0,
    val memories: Int = 0,
    val models: Int = 0,
    val localArtifacts: Int = 0,
    val settings: Int = 0,
    val membershipWindows: Int = 0,
    val schedules: Int = 0
) {
    val total: Int
        get() = characters + chats + messages + groups + memories + models + localArtifacts + settings + membershipWindows + schedules
}

data class ImportPreview(
    val valid: Boolean,
    val version: Int?,
    val counts: PortableDataCounts = PortableDataCounts(),
    val conflictCount: Int = 0,
    val message: String
)

enum class ImportConflictPolicy {
    KEEP_LOCAL,
    USE_IMPORTED
}

data class ImportResult(
    val imported: Boolean,
    val message: String,
    val skippedConflicts: Int = 0
)

interface PortableDataManager {
    suspend fun exportData(): String
    suspend fun previewImport(payload: String): ImportPreview
    suspend fun importData(
        payload: String,
        conflictPolicy: ImportConflictPolicy = ImportConflictPolicy.KEEP_LOCAL
    ): ImportResult
    suspend fun wipeAll()
}

interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>
    suspend fun saveSettings(settings: AppSettings)
}
