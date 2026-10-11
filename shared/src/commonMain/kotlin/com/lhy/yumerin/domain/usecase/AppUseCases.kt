package com.lhy.yumerin.domain.usecase

import com.lhy.yumerin.core.model.*
import com.lhy.yumerin.domain.repository.*

class AppUseCases(
    characters: CharacterRepository,
    chats: ChatRepository,
    groups: GroupRepository,
    memories: MemoryRepository,
    models: ModelProfileRepository,
    agent: AgentService,
    memberships: GroupMembershipRepository,
    schedules: CompanionScheduleRepository,
    avatars: AvatarStore
) {
    val sendChatMessage = SendChatMessageUseCase(characters, chats, groups, memories, models, agent, memberships)
    val retryChatResponse = RetryChatResponseUseCase(characters, chats, groups, memories, models, agent, memberships)
    val saveCharacter = SaveCharacterUseCase(characters, chats, avatars)
    val createGroupChat = CreateGroupChatUseCase(groups, chats, memberships)
    val createDirectChat = CreateDirectChatUseCase(chats)
    val updateGroupChat = UpdateGroupChatUseCase(groups, chats, memberships)
    val deleteChat = DeleteChatUseCase(chats)
    val archiveCharacter = ArchiveCharacterUseCase(characters)
    val deleteCharacter = DeleteCharacterUseCase(characters, chats, groups, memories, memberships, avatars)
    val sendProactiveMessage = SendProactiveMessageUseCase(characters, chats, memories, models, schedules, agent)
}
