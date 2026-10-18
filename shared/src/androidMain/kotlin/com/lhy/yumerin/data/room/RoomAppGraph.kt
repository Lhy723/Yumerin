package com.lhy.yumerin.data.room

import android.content.Context
import com.lhy.yumerin.core.model.*
import com.lhy.yumerin.data.background.AndroidBackgroundCompanionController
import com.lhy.yumerin.data.background.AndroidAgentGoalScheduler
import com.lhy.yumerin.data.background.AndroidDelegatedAgentGoalScheduler
import com.lhy.yumerin.data.local.*
import com.lhy.yumerin.data.memory.DefaultCompanionSeed
import com.lhy.yumerin.data.portable.RoomPortableDataManager
import com.lhy.yumerin.data.remote.AndroidAgentService
import com.lhy.yumerin.data.remote.AndroidModelConfigurationTester
import com.lhy.yumerin.domain.agent.AgentAdmissionArbiter
import com.lhy.yumerin.domain.AppGraph
import com.lhy.yumerin.domain.background.AgentGoalScheduler
import com.lhy.yumerin.domain.background.DelegatedAgentGoalScheduler
import com.lhy.yumerin.domain.repository.*
import com.lhy.yumerin.domain.usecase.AppUseCases
import java.io.File

private object AndroidAgentAdmission {
    val arbiter = AgentAdmissionArbiter()
}

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
    override val modelConfigurationTester = AndroidModelConfigurationTester()
    override val settings: SettingsRepository = RoomSettingsRepository(database)
    override val groupMemberships: GroupMembershipRepository = RoomGroupMembershipRepository(database)
    override val schedules: CompanionScheduleRepository = RoomCompanionScheduleRepository(database)
    override val characterRuntimeStates: CharacterRuntimeStateRepository = RoomCharacterRuntimeStateRepository(database)
    override val agentGoals: AgentGoalRepository = RoomAgentGoalRepository(database)
    override val agentGoalScheduler: AgentGoalScheduler = AndroidAgentGoalScheduler(context, agentGoals)
    override val pendingExternalActions: com.lhy.yumerin.domain.repository.PendingExternalActionRepository =
        com.lhy.yumerin.data.memory.InMemoryPendingExternalActionRepository()
    override val delegatedAgentGoals: DelegatedAgentGoalRepository = RoomDelegatedAgentGoalRepository(database)
    override val delegatedAgentGoalScheduler: DelegatedAgentGoalScheduler = AndroidDelegatedAgentGoalScheduler(context, delegatedAgentGoals)
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
        modelsDirectory = modelsDirectory,
        agentGoalScheduler = agentGoalScheduler,
        delegatedAgentGoalScheduler = delegatedAgentGoalScheduler
    )
    override val agent: AgentService = AndroidAgentService(
        credentials = credentials,
        localModels = LocalModelManager(models, modelsDirectory)
    )
    override val useCases: AppUseCases by lazy {
        AppUseCases(
            characters = characters,
            chats = chats,
            groups = groups,
            memories = memories,
            models = models,
            agent = agent,
            settingsRepository = settings,
            memberships = groupMemberships,
            schedules = schedules,
            avatars = avatars,
            characterRuntimeStates = characterRuntimeStates,
            agentGoals = agentGoals,
            agentGoalScheduler = agentGoalScheduler,
            delegatedAgentGoals = delegatedAgentGoals,
            delegatedAgentGoalScheduler = delegatedAgentGoalScheduler,
            agentAdmissionArbiter = AndroidAgentAdmission.arbiter
        )
    }

    override suspend fun ensureSeedData() {
        val characterDao = database.characterDao()
        val chatDao = database.chatThreadDao()
        val messageDao = database.chatMessageDao()
        val seed = DefaultCompanionSeed.load()
        val seededCharacter = seed.character
        val existingCharacter = characterDao.findById(seededCharacter.id)
        if (existingCharacter == null) {
            characterDao.upsert(seededCharacter.toEntity())
        } else {
            val previousProfile = existingCharacter.toDomain()
            val refreshedProfile = seed.migrateExistingProfile(previousProfile)
            var updatedCharacter = if (refreshedProfile != previousProfile) refreshedProfile.toEntity() else existingCharacter
            if (updatedCharacter.avatarUri == null && seededCharacter.avatarUri != null) {
                updatedCharacter = updatedCharacter.copy(avatarUri = seededCharacter.avatarUri)
            }
            if (updatedCharacter != existingCharacter) characterDao.upsert(updatedCharacter)
        }
        val seededChat = seed.chat
        val existingChat = chatDao.findById(seededChat.id)
        if (existingChat == null) {
            chatDao.upsert(seededChat.toEntity())
            messageDao.upsert(seed.welcomeMessage.toEntity())
        } else if (existingChat.avatarUri == null && seededCharacter.avatarUri != null) {
            chatDao.upsert(existingChat.copy(avatarUri = seededCharacter.avatarUri))
            messageDao.findById(seed.welcomeMessage.id)?.let { welcomeMessage ->
                if (welcomeMessage.senderAvatarUri == null) {
                    messageDao.upsert(welcomeMessage.copy(senderAvatarUri = seededCharacter.avatarUri))
                }
            }
        }
        if (database.settingsDao().observeGlobalOnce() == null) {
            database.settingsDao().upsert(AppSettings().toEntity())
        }
        val proactiveEnabled = database.settingsDao().observeGlobalOnce()?.toDomain()?.proactiveMessagesEnabled == true
        agentGoalScheduler.setEnabled(proactiveEnabled)
        delegatedAgentGoalScheduler.reconcile()
    }
}
