package com.lhy.yumerin.domain.usecase

import com.lhy.yumerin.core.model.*
import com.lhy.yumerin.domain.memory.MemoryPolicy
import com.lhy.yumerin.domain.repository.*
import kotlinx.coroutines.flow.Flow
import com.lhy.yumerin.domain.safety.SafetyPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

class SaveCharacterUseCase(
    private val characters: CharacterRepository,
    private val chats: ChatRepository,
    private val avatars: AvatarStore
) {
    suspend operator fun invoke(character: CharacterProfile) {
        val previous = characters.getCharacter(character.id)
        val existed = previous != null
        val previousAvatar = previous?.avatarUri
        characters.saveCharacter(character)
        if (previousAvatar != null && previousAvatar != character.avatarUri) avatars.delete(previousAvatar)
        if (!existed) {
            val now = useCaseNowMillis()
            chats.saveChat(
                ChatThread(
                    id = newEntityId("chat"), kind = ChatKind.DIRECT, title = character.name,
                    characterId = character.id, groupId = null, avatarUri = character.avatarUri,
                    lastMessagePreview = "开始一段新的对话", lastActivityAt = now, unreadCount = 0,
                    pinned = false, muted = false, shortTermSummary = "刚刚创建的角色。",
                    summaryMaintenanceMode = com.lhy.yumerin.core.model.SummaryMaintenanceMode.AUTO
                )
            )
        } else {
            chats.observeChats().first().filter { it.characterId == character.id }.forEach { chat ->
                chats.saveChat(chat.copy(title = character.name, avatarUri = character.avatarUri, lastActivityAt = useCaseNowMillis()))
            }
        }
    }
}

class ArchiveCharacterUseCase(private val characters: CharacterRepository) {
    suspend operator fun invoke(characterId: EntityId, archived: Boolean) = characters.archiveCharacter(characterId, archived)
}

class DeleteCharacterUseCase(
    private val characters: CharacterRepository,
    private val chats: ChatRepository,
    private val groups: GroupRepository,
    private val memories: MemoryRepository,
    private val memberships: GroupMembershipRepository,
    private val avatars: AvatarStore,
    private val characterRuntimeStates: CharacterRuntimeStateRepository,
    private val agentGoals: AgentGoalRepository,
    private val agentGoalScheduler: com.lhy.yumerin.domain.background.AgentGoalScheduler,
    private val delegatedAgentGoals: DelegatedAgentGoalRepository,
    private val delegatedAgentGoalScheduler: com.lhy.yumerin.domain.background.DelegatedAgentGoalScheduler
) {
    suspend operator fun invoke(characterId: EntityId) {
        characters.getCharacter(characterId)?.avatarUri?.let { avatars.delete(it) }
        chats.observeChats().first().filter { it.kind == ChatKind.DIRECT && it.characterId == characterId }.forEach { chats.deleteChat(it.id) }
        memories.observeMemories(characterId).first().forEach { memories.deleteMemory(it.id) }
        characterRuntimeStates.deleteState(characterId)
        agentGoals.observeGoals().first().filter { it.characterId == characterId }.forEach { goal ->
            agentGoalScheduler.cancel(goal.id)
            agentGoals.deleteGoal(goal.id)
        }
        delegatedAgentGoals.observeGoals().first().filter { it.characterId == characterId }.forEach { goal ->
            delegatedAgentGoalScheduler.cancel(goal.id)
            delegatedAgentGoals.deleteGoal(goal.id)
        }
        groups.observeGroups().first().forEach { group ->
            val activeWindow = memberships.getActiveWindow(group.id, characterId)
            if (activeWindow != null) memberships.closeWindow(group.id, characterId, useCaseNowMillis())
            if (characterId in group.memberCharacterIds) {
                groups.saveGroup(group.copy(memberCharacterIds = group.memberCharacterIds.filterNot { it == characterId }, updatedAt = useCaseNowMillis()))
            }
            chats.observeChats().first().filter { it.groupId == group.id }.forEach { chat ->
                chats.getMessages(chat.id).filter { it.senderId == characterId }.forEach { message ->
                    chats.appendMessage(message.copy(senderId = null, senderType = MessageSenderType.DELETED_CHARACTER, senderName = "已删除角色", senderAvatarUri = null))
                }
            }
        }
        characters.deletePermanently(characterId)
    }
}

class ObserveCharacterRuntimeStateUseCase(
    private val states: CharacterRuntimeStateRepository
) {
    operator fun invoke(characterId: EntityId): Flow<CharacterRuntimeState?> = states.observeState(characterId)
}

class UpdateCharacterContinuityUseCase(
    private val states: CharacterRuntimeStateRepository
) {
    suspend operator fun invoke(
        characterId: EntityId,
        moodValence: Float,
        trust: Float,
        closeness: Float
    ) {
        val current = states.getState(characterId) ?: CharacterRuntimeState(characterId = characterId)
        states.saveState(
            current.copy(
                moodValence = moodValence.takeIf(Float::isFinite)?.coerceIn(-1f, 1f) ?: current.moodValence,
                trust = trust.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: current.trust,
                closeness = closeness.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: current.closeness,
                updatedAt = useCaseNowMillis()
            )
        )
    }
}
