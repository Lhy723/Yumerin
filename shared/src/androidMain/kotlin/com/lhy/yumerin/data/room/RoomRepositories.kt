package com.lhy.yumerin.data.room

import com.lhy.yumerin.core.model.*
import com.lhy.yumerin.domain.repository.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomCharacterRepository(
    private val database: YumerinDatabase
) : CharacterRepository {
    private val dao = database.characterDao()
    override fun observeCharacters(): Flow<List<CharacterProfile>> = dao.observeActive().map { it.map(CharacterEntity::toDomain) }
    override fun observeArchivedCharacters(): Flow<List<CharacterProfile>> = dao.observeArchived().map { it.map(CharacterEntity::toDomain) }
    override suspend fun getCharacter(id: EntityId): CharacterProfile? = dao.findById(id)?.toDomain()
    override suspend fun saveCharacter(character: CharacterProfile) = dao.upsert(character.toEntity())
    override suspend fun archiveCharacter(id: EntityId, archived: Boolean) =
        dao.setArchived(id, archived, System.currentTimeMillis())
    override suspend fun deletePermanently(id: EntityId) = dao.deleteById(id)
}

internal class RoomChatRepository(
    private val database: YumerinDatabase
) : ChatRepository {
    private val threadDao = database.chatThreadDao()
    private val messageDao = database.chatMessageDao()

    override fun observeChats(): Flow<List<ChatThread>> = threadDao.observeAll().map { it.map(ChatThreadEntity::toDomain) }
    override fun observeMessages(chatId: EntityId): Flow<List<ChatMessage>> =
        messageDao.observeForChat(chatId).map { it.map(ChatMessageEntity::toDomain) }
    override suspend fun getChat(id: EntityId): ChatThread? = threadDao.findById(id)?.toDomain()
    override suspend fun getMessages(chatId: EntityId): List<ChatMessage> = messageDao.getForChat(chatId).map(ChatMessageEntity::toDomain)
    override suspend fun saveChat(chat: ChatThread) = threadDao.upsert(chat.toEntity())
    override suspend fun appendMessage(message: ChatMessage) {
        val existed = messageDao.findById(message.id) != null
        messageDao.upsert(message.toEntity())
        threadDao.findById(message.chatId)?.let { thread ->
            threadDao.upsert(
                thread.copy(
                    lastMessagePreview = message.text,
                    lastActivityAt = message.timestamp,
                    unreadCount = if (!existed && message.senderType != com.lhy.yumerin.core.model.MessageSenderType.USER && !message.isRead) {
                        thread.unreadCount + 1
                    } else thread.unreadCount
                )
            )
        }
    }
    override suspend fun markRead(chatId: EntityId) = threadDao.markRead(chatId)
    override suspend fun deleteChat(id: EntityId) {
        messageDao.deleteForChat(id)
        threadDao.deleteById(id)
    }
}

internal class RoomGroupRepository(
    private val database: YumerinDatabase
) : GroupRepository {
    private val dao = database.groupDao()
    override fun observeGroups(): Flow<List<GroupProfile>> = dao.observeActive().map { it.map(GroupEntity::toDomain) }
    override suspend fun getGroup(id: EntityId): GroupProfile? = dao.findById(id)?.toDomain()
    override suspend fun saveGroup(group: GroupProfile) = dao.upsert(group.toEntity())
    override suspend fun deleteGroup(id: EntityId) = dao.deleteById(id)
}

internal class RoomMemoryRepository(
    private val database: YumerinDatabase
) : MemoryRepository {
    private val dao = database.memoryEntryDao()
    override fun observeMemories(characterId: EntityId): Flow<List<MemoryEntry>> =
        dao.observeForCharacter(characterId).map { it.map(MemoryEntryEntity::toDomain) }
    override suspend fun getMemory(id: EntityId): MemoryEntry? = dao.getAll().firstOrNull { it.id == id }?.toDomain()
    override suspend fun saveMemory(memory: MemoryEntry) = dao.upsert(memory.toEntity())
    override suspend fun deleteMemory(id: EntityId) = dao.deleteById(id)
}

internal class RoomModelProfileRepository(
    private val database: YumerinDatabase
) : ModelProfileRepository {
    private val dao = database.modelProfileDao()
    override fun observeProfiles(): Flow<List<ModelProfile>> = dao.observeProfiles().map { it.map(ModelProfileEntity::toDomain) }
    override fun observeLocalArtifacts(): Flow<List<LocalModelArtifact>> =
        dao.observeArtifacts().map { it.map(LocalModelArtifactEntity::toDomain) }
    override suspend fun getProfiles(): List<ModelProfile> = dao.getProfiles().map(ModelProfileEntity::toDomain)
    override suspend fun getLocalArtifacts(): List<LocalModelArtifact> = dao.getArtifacts().map(LocalModelArtifactEntity::toDomain)
    override suspend fun getProfile(id: EntityId): ModelProfile? = dao.findProfile(id)?.toDomain()
    override suspend fun getLocalArtifact(id: EntityId): LocalModelArtifact? = dao.findArtifact(id)?.toDomain()
    override suspend fun saveProfile(profile: ModelProfile) = dao.upsertProfile(profile.toEntity())
    override suspend fun saveLocalArtifact(artifact: LocalModelArtifact) = dao.upsertArtifact(artifact.toEntity())
    override suspend fun deleteProfile(id: EntityId) = dao.deleteProfile(id)
    override suspend fun deleteLocalArtifact(id: EntityId) = dao.deleteArtifact(id)
}


internal class RoomSettingsRepository(
    private val database: YumerinDatabase
) : SettingsRepository {
    private val dao = database.settingsDao()
    override fun observeSettings(): Flow<AppSettings> = dao.observeGlobal().map { it?.toDomain() ?: AppSettings() }
    override suspend fun saveSettings(settings: AppSettings) = dao.upsert(settings.toEntity())
}

internal class RoomGroupMembershipRepository(
    private val database: YumerinDatabase
) : GroupMembershipRepository {
    private val dao = database.groupMembershipWindowDao()
    override fun observeWindows(groupId: EntityId): Flow<List<GroupMembershipWindow>> =
        dao.observeForGroup(groupId).map { it.map(GroupMembershipWindowEntity::toDomain) }
    override suspend fun getActiveWindow(groupId: EntityId, characterId: EntityId): GroupMembershipWindow? =
        dao.findActive(groupId, characterId)?.toDomain()
    override suspend fun saveWindow(window: GroupMembershipWindow) = dao.upsert(window.toEntity())
    override suspend fun closeWindow(groupId: EntityId, characterId: EntityId, leftAt: Long) =
        dao.closeActive(groupId, characterId, leftAt)
}

internal class RoomCompanionScheduleRepository(
    private val database: YumerinDatabase
) : CompanionScheduleRepository {
    private val dao = database.companionScheduleDao()
    override fun observeSchedules(): Flow<List<CompanionSchedule>> =
        dao.observeActive().map { it.map(CompanionScheduleEntity::toDomain) }
    override suspend fun getSchedule(id: EntityId): CompanionSchedule? = dao.findById(id)?.toDomain()
    override suspend fun saveSchedule(schedule: CompanionSchedule) = dao.upsert(schedule.toEntity())
    override suspend fun deleteSchedule(id: EntityId) = dao.deleteById(id)
}
