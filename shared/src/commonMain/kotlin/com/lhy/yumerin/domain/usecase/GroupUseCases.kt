package com.lhy.yumerin.domain.usecase

import com.lhy.yumerin.core.model.*
import com.lhy.yumerin.domain.repository.*
import kotlinx.coroutines.flow.first

class CreateGroupChatUseCase(
    private val groups: GroupRepository,
    private val chats: ChatRepository,
    private val memberships: GroupMembershipRepository
) {
    suspend operator fun invoke(group: GroupProfile): ChatThread {
        require(group.memberCharacterIds.distinct().size >= 2) { "群聊至少需要两个角色。" }
        groups.saveGroup(group)
        val now = useCaseNowMillis()
        group.memberCharacterIds.forEach { characterId ->
            if (memberships.getActiveWindow(group.id, characterId) == null) {
                memberships.saveWindow(GroupMembershipWindow(newEntityId("membership"), group.id, characterId, now, null, now, now))
            }
        }
        val chat = ChatThread(
            id = newEntityId("chat"), kind = ChatKind.GROUP, title = group.name,
            characterId = null, groupId = group.id, avatarUri = group.avatarUri,
            lastMessagePreview = "群聊已创建", lastActivityAt = now, unreadCount = 0,
            pinned = false, muted = false, shortTermSummary = group.summary,
            summaryMaintenanceMode = group.summaryMaintenanceMode
        )
        chats.saveChat(chat)
        return chat
    }
}

class CreateDirectChatUseCase(
    private val chats: ChatRepository
) {
    suspend operator fun invoke(character: CharacterProfile): ChatThread {
        require(!character.archived && character.deletedAt == null) { "归档角色不能创建新聊天。" }
        val now = useCaseNowMillis()
        return ChatThread(
            id = newEntityId("chat"), kind = ChatKind.DIRECT, title = character.name,
            characterId = character.id, groupId = null, avatarUri = character.avatarUri,
            lastMessagePreview = "开始一段新的对话", lastActivityAt = now, unreadCount = 0,
            pinned = false, muted = false, shortTermSummary = "新聊天线程。",
            summaryMaintenanceMode = com.lhy.yumerin.core.model.SummaryMaintenanceMode.AUTO
        ).also { chats.saveChat(it) }
    }
}

class UpdateGroupChatUseCase(
    private val groups: GroupRepository,
    private val chats: ChatRepository,
    private val memberships: GroupMembershipRepository
) {
    suspend operator fun invoke(group: GroupProfile, selectedCharacterIds: Set<EntityId>) {
        require(selectedCharacterIds.size >= 2) { "群聊至少需要两个角色。" }
        val now = useCaseNowMillis()
        val currentWindows = memberships.observeWindows(group.id).first()
        currentWindows.filter { it.leftAt == null && it.characterId !in selectedCharacterIds }
            .forEach { memberships.closeWindow(group.id, it.characterId, now) }
        selectedCharacterIds.forEach { characterId ->
            if (memberships.getActiveWindow(group.id, characterId) == null) {
                memberships.saveWindow(
                    GroupMembershipWindow(
                        id = newEntityId("membership"), groupId = group.id, characterId = characterId,
                        joinedAt = now, leftAt = null, createdAt = now, updatedAt = now
                    )
                )
            }
        }
        val updatedGroup = group.copy(
            memberCharacterIds = selectedCharacterIds.toList(),
            updatedAt = now
        )
        groups.saveGroup(updatedGroup)
        chats.observeChats().first().filter { it.groupId == group.id }.forEach { chat ->
            chats.saveChat(
                chat.copy(
                    title = updatedGroup.name,
                    avatarUri = updatedGroup.avatarUri,
                    shortTermSummary = if (updatedGroup.summaryMaintenanceMode == com.lhy.yumerin.core.model.SummaryMaintenanceMode.AUTO) {
                        updatedGroup.summary
                    } else chat.shortTermSummary,
                    summaryMaintenanceMode = updatedGroup.summaryMaintenanceMode,
                    lastActivityAt = now
                )
            )
        }
    }
}
