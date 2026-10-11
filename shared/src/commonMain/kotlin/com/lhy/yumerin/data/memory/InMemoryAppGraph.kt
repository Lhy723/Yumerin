package com.lhy.yumerin.data.memory

import com.lhy.yumerin.core.model.AppSettings
import com.lhy.yumerin.core.model.AgentReply
import com.lhy.yumerin.core.model.AgentRequest
import com.lhy.yumerin.core.model.AgentService
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
import com.lhy.yumerin.core.model.newEntityId
import com.lhy.yumerin.domain.AppGraph
import com.lhy.yumerin.domain.background.BackgroundCompanionController
import com.lhy.yumerin.domain.repository.CharacterRepository
import com.lhy.yumerin.domain.repository.AvatarStore
import com.lhy.yumerin.domain.repository.ChatRepository
import com.lhy.yumerin.domain.repository.CredentialStore
import com.lhy.yumerin.domain.repository.GroupRepository
import com.lhy.yumerin.domain.repository.MemoryRepository
import com.lhy.yumerin.domain.repository.ModelProfileRepository
import com.lhy.yumerin.domain.repository.SettingsRepository
import com.lhy.yumerin.domain.repository.CompanionScheduleRepository
import com.lhy.yumerin.domain.repository.GroupMembershipRepository
import com.lhy.yumerin.domain.repository.ImportResult
import com.lhy.yumerin.domain.repository.ImportPreview
import com.lhy.yumerin.domain.repository.ImportConflictPolicy
import com.lhy.yumerin.domain.repository.LocalModelGateway
import com.lhy.yumerin.domain.repository.PortableDataManager
import com.lhy.yumerin.domain.repository.PortableDataCounts
import com.lhy.yumerin.domain.usecase.AppUseCases
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryAppGraph : AppGraph {
    override val characters: CharacterRepository = InMemoryCharacterRepository()
    override val chats: ChatRepository = InMemoryChatRepository()
    override val groups: GroupRepository = InMemoryGroupRepository()
    override val memories: MemoryRepository = InMemoryMemoryRepository()
    override val models: ModelProfileRepository = InMemoryModelProfileRepository()
    override val settings: SettingsRepository = InMemorySettingsRepository()
    override val groupMemberships: GroupMembershipRepository = InMemoryGroupMembershipRepository()
    override val schedules: CompanionScheduleRepository = InMemoryCompanionScheduleRepository()
    override val localModelGateway: LocalModelGateway = InMemoryLocalModelGateway(models)
    override val portableData: PortableDataManager = InMemoryPortableDataManager()
    override val credentials: CredentialStore = InMemoryCredentialStore()
    override val avatars: AvatarStore = object : AvatarStore { override suspend fun delete(uri: String) = Unit }
    override val agent: AgentService = UnavailableAgentService()
    override val backgroundCompanion: BackgroundCompanionController = InMemoryBackgroundCompanionController()
    override val useCases: AppUseCases by lazy {
        AppUseCases(characters, chats, groups, memories, models, agent, groupMemberships, schedules, avatars)
    }

    private val seedMutex = Mutex()
    private var seeded = false

    override suspend fun ensureSeedData() {
        seedMutex.withLock {
            if (seeded) return
            val companion = DefaultCompanionSeed.character
            characters.saveCharacter(companion)
            chats.saveChat(DefaultCompanionSeed.chat)
            chats.appendMessage(DefaultCompanionSeed.welcomeMessage)
            settings.saveSettings(AppSettings())
            seeded = true
        }
    }
}

internal object DefaultCompanionSeed {
    private const val characterId = "character-default-yuna"
    private const val chatId = "chat-default-yuna"

    val character = CharacterProfile(
        id = characterId,
        name = "由奈",
        avatarUri = defaultCompanionAvatarUri,
        relationshipFraming = "温柔、亲密、尊重边界的虚拟伴侣",
        setting = "住在雨夜霓虹城市里的二次元少女",
        description = "一个会认真听你说话，也会在你需要时陪你安静一会儿的默认伴侣。",
        personality = "温柔、敏锐、略带俏皮，重视你的感受和选择。",
        tone = "自然、亲近、简短，不用过度夸张的语气。",
        backstory = "她喜欢收集城市里细小的光，也相信每个普通日子都值得被记住。",
        interactionStyle = "优先回应你的情绪和意图，必要时提出一个轻量的问题。",
        modelProfileId = null,
        archived = false,
        createdAt = 0L,
        updatedAt = 0L
    )

    val chat = ChatThread(
        id = chatId,
        kind = com.lhy.yumerin.core.model.ChatKind.DIRECT,
        title = character.name,
        characterId = character.id,
        groupId = null,
        avatarUri = character.avatarUri,
        lastMessagePreview = "嗨，我是由奈。今天想从哪里开始？",
        lastActivityAt = 0L,
        unreadCount = 0,
        pinned = true,
        muted = false,
        shortTermSummary = "刚刚开始认识彼此。",
        summaryMaintenanceMode = com.lhy.yumerin.core.model.SummaryMaintenanceMode.AUTO
    )

    val welcomeMessage = ChatMessage(
        id = "message-default-welcome",
        chatId = chatId,
        senderId = character.id,
        senderType = com.lhy.yumerin.core.model.MessageSenderType.CHARACTER,
        senderName = character.name,
        senderAvatarUri = character.avatarUri,
        text = "嗨，我是由奈。今天想从哪里开始？",
        timestamp = 0L,
        type = com.lhy.yumerin.core.model.MessageType.TEXT,
        isProactive = false,
        isRead = true
    )
}

private class InMemoryCharacterRepository : CharacterRepository {
    private val state = MutableStateFlow<List<CharacterProfile>>(emptyList())

    override fun observeCharacters(): Flow<List<CharacterProfile>> = state
    override fun observeArchivedCharacters(): Flow<List<CharacterProfile>> =
        state.map { characters -> characters.filter { it.archived && it.deletedAt == null } }
    override suspend fun getCharacter(id: EntityId): CharacterProfile? = state.value.firstOrNull { it.id == id }
    override suspend fun saveCharacter(character: CharacterProfile) {
        state.update { current -> current.filterNot { it.id == character.id } + character }
    }
    override suspend fun archiveCharacter(id: EntityId, archived: Boolean) {
        state.update { current -> current.map { if (it.id == id) it.copy(archived = archived) else it } }
    }
    override suspend fun deletePermanently(id: EntityId) {
        state.update { current -> current.filterNot { it.id == id } }
    }
}

private class InMemoryChatRepository : ChatRepository {
    private val chats = MutableStateFlow<List<ChatThread>>(emptyList())
    private val messages = MutableStateFlow<Map<EntityId, List<ChatMessage>>>(emptyMap())

    override fun observeChats(): Flow<List<ChatThread>> = chats
    override fun observeMessages(chatId: EntityId): Flow<List<ChatMessage>> = messages.map { it[chatId].orEmpty() }
    override suspend fun getChat(id: EntityId): ChatThread? = chats.value.firstOrNull { it.id == id }
    override suspend fun getMessages(chatId: EntityId): List<ChatMessage> = messages.value[chatId].orEmpty()
    override suspend fun saveChat(chat: ChatThread) {
        chats.update { current -> current.filterNot { it.id == chat.id } + chat }
    }
    override suspend fun appendMessage(message: ChatMessage) {
        val alreadyPresent = messages.value[message.chatId].orEmpty().any { it.id == message.id }
        messages.update { current ->
            current + (message.chatId to (
                current[message.chatId].orEmpty().filterNot { it.id == message.id } + message
            ))
        }
        chats.update { current ->
            current.map { chat ->
                if (chat.id == message.chatId) {
                    chat.copy(
                        lastMessagePreview = message.text,
                        lastActivityAt = message.timestamp,
                        unreadCount = if (!alreadyPresent && message.senderType != com.lhy.yumerin.core.model.MessageSenderType.USER && !message.isRead) {
                            chat.unreadCount + 1
                        } else chat.unreadCount
                    )
                } else {
                    chat
                }
            }
        }
    }
    override suspend fun markRead(chatId: EntityId) {
        chats.update { current -> current.map { if (it.id == chatId) it.copy(unreadCount = 0) else it } }
    }
    override suspend fun deleteChat(id: EntityId) {
        chats.update { current -> current.filterNot { it.id == id } }
        messages.update { current -> current - id }
    }
}

private class InMemoryGroupRepository : GroupRepository {
    private val state = MutableStateFlow<List<GroupProfile>>(emptyList())
    override fun observeGroups(): Flow<List<GroupProfile>> = state
    override suspend fun getGroup(id: EntityId): GroupProfile? = state.value.firstOrNull { it.id == id }
    override suspend fun saveGroup(group: GroupProfile) {
        state.update { current -> current.filterNot { it.id == group.id } + group }
    }
    override suspend fun deleteGroup(id: EntityId) {
        state.update { current -> current.filterNot { it.id == id } }
    }
}

private class InMemoryMemoryRepository : MemoryRepository {
    private val state = MutableStateFlow<List<MemoryEntry>>(emptyList())
    override fun observeMemories(characterId: EntityId): Flow<List<MemoryEntry>> =
        state.map { entries -> entries.filter { it.characterId == characterId } }
    override suspend fun getMemory(id: EntityId): MemoryEntry? = state.value.firstOrNull { it.id == id }
    override suspend fun saveMemory(memory: MemoryEntry) {
        state.update { current -> current.filterNot { it.id == memory.id } + memory }
    }
    override suspend fun deleteMemory(id: EntityId) {
        state.update { current -> current.filterNot { it.id == id } }
    }
}

private class InMemoryModelProfileRepository : ModelProfileRepository {
    private val profiles = MutableStateFlow<List<ModelProfile>>(emptyList())
    private val artifacts = MutableStateFlow<List<LocalModelArtifact>>(emptyList())
    override fun observeProfiles(): Flow<List<ModelProfile>> = profiles
    override fun observeLocalArtifacts(): Flow<List<LocalModelArtifact>> = artifacts
    override suspend fun getProfiles(): List<ModelProfile> = profiles.value
    override suspend fun getLocalArtifacts(): List<LocalModelArtifact> = artifacts.value
    override suspend fun getProfile(id: EntityId): ModelProfile? = profiles.value.firstOrNull { it.id == id }
    override suspend fun getLocalArtifact(id: EntityId): LocalModelArtifact? = artifacts.value.firstOrNull { it.id == id }
    override suspend fun saveProfile(profile: ModelProfile) {
        profiles.update { current -> current.filterNot { it.id == profile.id } + profile }
    }
    override suspend fun saveLocalArtifact(artifact: LocalModelArtifact) {
        artifacts.update { current -> current.filterNot { it.id == artifact.id } + artifact }
    }
    override suspend fun deleteProfile(id: EntityId) { profiles.update { it.filterNot { profile -> profile.id == id } } }
    override suspend fun deleteLocalArtifact(id: EntityId) { artifacts.update { it.filterNot { artifact -> artifact.id == id } } }
}

private class InMemoryCredentialStore : CredentialStore {
    private val values = mutableMapOf<String, String>()
    override suspend fun save(alias: String, apiKey: String) {
        values[alias] = apiKey
    }
    override suspend fun read(alias: String): String? = values[alias]
    override suspend fun remove(alias: String) {
        values.remove(alias)
    }
    override suspend fun clear() {
        values.clear()
    }
}

private class InMemoryGroupMembershipRepository : GroupMembershipRepository {
    private val state = MutableStateFlow<List<GroupMembershipWindow>>(emptyList())
    override fun observeWindows(groupId: EntityId): Flow<List<GroupMembershipWindow>> = state.map { values -> values.filter { it.groupId == groupId } }
    override suspend fun getActiveWindow(groupId: EntityId, characterId: EntityId): GroupMembershipWindow? =
        state.value.firstOrNull { it.groupId == groupId && it.characterId == characterId && it.leftAt == null }
    override suspend fun saveWindow(window: GroupMembershipWindow) { state.update { values -> values.filterNot { it.id == window.id } + window } }
    override suspend fun closeWindow(groupId: EntityId, characterId: EntityId, leftAt: Long) {
        state.update { values -> values.map { if (it.groupId == groupId && it.characterId == characterId && it.leftAt == null) it.copy(leftAt = leftAt, updatedAt = leftAt) else it } }
    }
}

private class InMemoryCompanionScheduleRepository : CompanionScheduleRepository {
    private val state = MutableStateFlow<List<CompanionSchedule>>(emptyList())
    override fun observeSchedules(): Flow<List<CompanionSchedule>> = state
    override suspend fun getSchedule(id: EntityId): CompanionSchedule? = state.value.firstOrNull { it.id == id }
    override suspend fun saveSchedule(schedule: CompanionSchedule) { state.update { values -> values.filterNot { it.id == schedule.id } + schedule } }
    override suspend fun deleteSchedule(id: EntityId) { state.update { values -> values.filterNot { it.id == id } } }
}

private class InMemoryLocalModelGateway(
    private val models: ModelProfileRepository
) : LocalModelGateway {
    override suspend fun registerCatalogEntry(artifact: LocalModelArtifact) = models.saveLocalArtifact(artifact)
    override suspend fun importArtifact(artifact: LocalModelArtifact, sourcePath: String): LocalModelArtifact = artifact
    override suspend fun startDownload(artifactId: EntityId, allowMetered: Boolean) = Unit
    override suspend fun pauseDownload(artifactId: EntityId) = Unit
    override suspend fun cancelDownload(artifactId: EntityId) = Unit
    override suspend fun activate(artifactId: EntityId): LocalModelArtifact = requireNotNull(models.getLocalArtifact(artifactId))
    override suspend fun remove(artifactId: EntityId) = Unit
}

private class InMemoryPortableDataManager : PortableDataManager {
    override suspend fun exportData(): String = "{\"format\":\"yumerin-portable\",\"version\":1}"
    override suspend fun previewImport(payload: String): ImportPreview =
        ImportPreview(false, null, message = "预览模式不持久化数据")
    override suspend fun importData(payload: String, conflictPolicy: ImportConflictPolicy): ImportResult =
        ImportResult(false, "预览模式不持久化数据")
    override suspend fun wipeAll() = Unit
}

private class InMemoryBackgroundCompanionController : BackgroundCompanionController {
    private var enabled = false
    override suspend fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
    override suspend fun stopAll() {
        enabled = false
    }
    override fun isEnabled(): Boolean = enabled
}

private class UnavailableAgentService : AgentService {
    override suspend fun reply(request: AgentRequest): AgentReply = AgentReply(
        text = "模型运行时还没有连接，请先完成模型配置。",
        modelProfileId = request.model.id,
        statusMessage = "agent-unavailable"
    )
}

private class InMemorySettingsRepository : SettingsRepository {
    private val state = MutableStateFlow(AppSettings())
    override fun observeSettings(): Flow<AppSettings> = state
    override suspend fun saveSettings(settings: AppSettings) {
        state.value = settings
    }
}
