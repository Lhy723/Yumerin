package com.lhy.yumerin.data.room

import android.content.Context
import com.lhy.yumerin.core.model.*
import com.lhy.yumerin.data.background.AndroidBackgroundCompanionController
import com.lhy.yumerin.data.local.*
import com.lhy.yumerin.data.memory.DefaultCompanionSeed
import com.lhy.yumerin.data.portable.RoomPortableDataManager
import com.lhy.yumerin.data.remote.AndroidAgentService
import com.lhy.yumerin.domain.AppGraph
import com.lhy.yumerin.domain.repository.*
import com.lhy.yumerin.domain.usecase.AppUseCases
import java.io.File
class RoomAppGraph(
    private val database: YumerinDatabase,
    override val credentials: CredentialStore,
    private val modelsDirectory: File,
    private val context: Context
) : AppGraph {
    override val avatars = AndroidAvatarStore(context)
    override val characters: CharacterRepository = RoomCharacterRepository(database)
    override val chats: ChatRepository = RoomChatRepository(database)
    override val groups: GroupRepository = RoomGroupRepository(database)
    override val memories: MemoryRepository = RoomMemoryRepository(database)
    override val models: ModelProfileRepository = RoomModelProfileRepository(database)
    override val settings: SettingsRepository = RoomSettingsRepository(database)
    override val groupMemberships: GroupMembershipRepository = RoomGroupMembershipRepository(database)
    override val schedules: CompanionScheduleRepository = RoomCompanionScheduleRepository(database)
    override val backgroundCompanion = AndroidBackgroundCompanionController(context)
    override val localModelGateway = AndroidLocalModelGateway(
        repository = models,
        modelsDirectory = modelsDirectory,
        context = context
    )
    override val portableData = RoomPortableDataManager(
        database = database,
        credentials = credentials,
        backgroundCompanion = backgroundCompanion,
        modelsDirectory = modelsDirectory
    )
    override val agent: AgentService = AndroidAgentService(
        credentials = credentials,
        localModels = LocalModelManager(models, modelsDirectory)
    )
    override val useCases: AppUseCases by lazy {
        AppUseCases(characters, chats, groups, memories, models, agent, groupMemberships, schedules, avatars)
    }

    override suspend fun ensureSeedData() {
        val characterDao = database.characterDao()
        val chatDao = database.chatThreadDao()
        val messageDao = database.chatMessageDao()
        val seededCharacter = DefaultCompanionSeed.character
        val existingCharacter = characterDao.findById(seededCharacter.id)
        if (existingCharacter == null) {
            characterDao.upsert(seededCharacter.toEntity())
        } else if (existingCharacter.avatarUri == null && seededCharacter.avatarUri != null) {
            characterDao.upsert(existingCharacter.copy(avatarUri = seededCharacter.avatarUri))
        }
        val seededChat = DefaultCompanionSeed.chat
        val existingChat = chatDao.findById(seededChat.id)
        if (existingChat == null) {
            chatDao.upsert(seededChat.toEntity())
            messageDao.upsert(DefaultCompanionSeed.welcomeMessage.toEntity())
        } else if (existingChat.avatarUri == null && seededCharacter.avatarUri != null) {
            chatDao.upsert(existingChat.copy(avatarUri = seededCharacter.avatarUri))
            messageDao.findById(DefaultCompanionSeed.welcomeMessage.id)?.let { welcomeMessage ->
                if (welcomeMessage.senderAvatarUri == null) {
                    messageDao.upsert(welcomeMessage.copy(senderAvatarUri = seededCharacter.avatarUri))
                }
            }
        }
        if (database.settingsDao().observeGlobalOnce() == null) {
            database.settingsDao().upsert(AppSettings().toEntity())
        }
    }
}
