package com.lhy.yumerin.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CharacterDao {
    @Query("SELECT * FROM characters WHERE archived = 0 AND deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeActive(): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM characters WHERE archived = 1 AND deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeArchived(): Flow<List<CharacterEntity>>

    @Query("SELECT * FROM characters ORDER BY updatedAt DESC")
    suspend fun getAll(): List<CharacterEntity>

    @Query("SELECT * FROM characters WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): CharacterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CharacterEntity)

    @Query("UPDATE characters SET archived = :archived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean, updatedAt: Long)

    @Query("DELETE FROM characters WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM characters")
    suspend fun deleteAll()
}

@Dao
interface ChatThreadDao {
    @Query("SELECT * FROM chat_threads WHERE deletedAt IS NULL ORDER BY pinned DESC, lastActivityAt DESC")
    fun observeAll(): Flow<List<ChatThreadEntity>>

    @Query("SELECT * FROM chat_threads ORDER BY lastActivityAt ASC")
    suspend fun getAll(): List<ChatThreadEntity>

    @Query("SELECT * FROM chat_threads WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ChatThreadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChatThreadEntity)

    @Query("UPDATE chat_threads SET unreadCount = 0 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("DELETE FROM chat_threads WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM chat_threads")
    suspend fun deleteAll()
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun observeForChat(chatId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    suspend fun getForChat(chatId: String): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ChatMessageEntity?

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getAll(): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE chatId = :chatId")
    suspend fun deleteForChat(chatId: String)

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAll()
}

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups WHERE archived = 0 AND deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeActive(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups ORDER BY updatedAt DESC")
    suspend fun getAll(): List<GroupEntity>

    @Query("SELECT * FROM groups WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GroupEntity)

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM groups")
    suspend fun deleteAll()
}

@Dao
interface MemoryEntryDao {
    @Query("SELECT * FROM memory_entries WHERE characterId = :characterId ORDER BY pinned DESC, updatedAt DESC")
    fun observeForCharacter(characterId: String): Flow<List<MemoryEntryEntity>>

    @Query("SELECT * FROM memory_entries ORDER BY updatedAt ASC")
    suspend fun getAll(): List<MemoryEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MemoryEntryEntity)

    @Query("DELETE FROM memory_entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM memory_entries")
    suspend fun deleteAll()
}

@Dao
interface ModelProfileDao {
    @Query("SELECT * FROM model_profiles WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeProfiles(): Flow<List<ModelProfileEntity>>

    @Query("SELECT * FROM model_profiles WHERE deletedAt IS NULL ORDER BY updatedAt ASC")
    suspend fun getProfiles(): List<ModelProfileEntity>

    @Query("SELECT * FROM local_model_artifacts WHERE deletedAt IS NULL ORDER BY updatedAt DESC")
    fun observeArtifacts(): Flow<List<LocalModelArtifactEntity>>

    @Query("SELECT * FROM local_model_artifacts WHERE deletedAt IS NULL ORDER BY updatedAt ASC")
    suspend fun getArtifacts(): List<LocalModelArtifactEntity>

    @Query("SELECT * FROM model_profiles WHERE id = :id LIMIT 1")
    suspend fun findProfile(id: String): ModelProfileEntity?

    @Query("SELECT * FROM local_model_artifacts WHERE id = :id LIMIT 1")
    suspend fun findArtifact(id: String): LocalModelArtifactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(entity: ModelProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArtifact(entity: LocalModelArtifactEntity)

    @Query("DELETE FROM model_profiles")
    suspend fun deleteProfiles()

    @Query("DELETE FROM model_profiles WHERE id = :id")
    suspend fun deleteProfile(id: String)

    @Query("DELETE FROM local_model_artifacts")
    suspend fun deleteArtifacts()

    @Query("DELETE FROM local_model_artifacts WHERE id = :id")
    suspend fun deleteArtifact(id: String)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 'global' LIMIT 1")
    fun observeGlobal(): Flow<AppSettingsEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppSettingsEntity)

    @Query("SELECT * FROM app_settings")
    suspend fun getAll(): List<AppSettingsEntity>

    @Query("DELETE FROM app_settings")
    suspend fun deleteAll()

    @Query("SELECT * FROM app_settings WHERE id = 'global' LIMIT 1")
    suspend fun observeGlobalOnce(): AppSettingsEntity?
}

@Dao
interface GroupMembershipWindowDao {
    @Query("SELECT * FROM group_membership_windows WHERE groupId = :groupId ORDER BY joinedAt ASC")
    fun observeForGroup(groupId: String): Flow<List<GroupMembershipWindowEntity>>

    @Query("SELECT * FROM group_membership_windows WHERE groupId = :groupId AND characterId = :characterId AND leftAt IS NULL LIMIT 1")
    suspend fun findActive(groupId: String, characterId: String): GroupMembershipWindowEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: GroupMembershipWindowEntity)

    @Query("UPDATE group_membership_windows SET leftAt = :leftAt, updatedAt = :leftAt WHERE groupId = :groupId AND characterId = :characterId AND leftAt IS NULL")
    suspend fun closeActive(groupId: String, characterId: String, leftAt: Long)

    @Query("SELECT * FROM group_membership_windows ORDER BY joinedAt ASC")
    suspend fun getAll(): List<GroupMembershipWindowEntity>

    @Query("DELETE FROM group_membership_windows")
    suspend fun deleteAll()
}

@Dao
interface CompanionScheduleDao {
    @Query("SELECT * FROM companion_schedules WHERE deletedAt IS NULL ORDER BY hour ASC, minute ASC")
    fun observeActive(): Flow<List<CompanionScheduleEntity>>

    @Query("SELECT * FROM companion_schedules WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): CompanionScheduleEntity?

    @Query("SELECT * FROM companion_schedules ORDER BY updatedAt ASC")
    suspend fun getAll(): List<CompanionScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CompanionScheduleEntity)

    @Query("DELETE FROM companion_schedules WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM companion_schedules")
    suspend fun deleteAll()
}
