package com.lhy.yumerin.domain

import com.lhy.yumerin.domain.repository.CharacterRepository
import com.lhy.yumerin.domain.repository.AvatarStore
import com.lhy.yumerin.domain.repository.ChatRepository
import com.lhy.yumerin.domain.repository.CredentialStore
import com.lhy.yumerin.domain.repository.GroupRepository
import com.lhy.yumerin.domain.repository.MemoryRepository
import com.lhy.yumerin.domain.repository.ModelProfileRepository
import com.lhy.yumerin.domain.repository.ModelConfigurationTester
import com.lhy.yumerin.domain.repository.SettingsRepository
import com.lhy.yumerin.domain.repository.CompanionScheduleRepository
import com.lhy.yumerin.domain.repository.CharacterRuntimeStateRepository
import com.lhy.yumerin.domain.repository.AgentGoalRepository
import com.lhy.yumerin.domain.repository.DelegatedAgentGoalRepository
import com.lhy.yumerin.domain.background.AgentGoalScheduler
import com.lhy.yumerin.domain.background.DelegatedAgentGoalScheduler
import com.lhy.yumerin.domain.repository.GroupMembershipRepository
import com.lhy.yumerin.domain.repository.LocalModelGateway
import com.lhy.yumerin.domain.repository.PortableDataManager
import com.lhy.yumerin.domain.background.BackgroundCompanionController
import com.lhy.yumerin.domain.usecase.AppUseCases

/**
 * The only object the presentation layer needs from the application boundary.
 * Implementations are assembled by Android Koin or by a lightweight preview graph.
 */
interface AppGraph {
    val characters: CharacterRepository
    val chats: ChatRepository
    val groups: GroupRepository
    val memories: MemoryRepository
    val models: ModelProfileRepository
    val modelConfigurationTester: ModelConfigurationTester
    val settings: SettingsRepository
    val groupMemberships: GroupMembershipRepository
    val schedules: CompanionScheduleRepository
    val characterRuntimeStates: CharacterRuntimeStateRepository
    val agentGoals: AgentGoalRepository
    val agentGoalScheduler: AgentGoalScheduler
    val delegatedAgentGoals: DelegatedAgentGoalRepository
    val delegatedAgentGoalScheduler: DelegatedAgentGoalScheduler
    val localModelGateway: LocalModelGateway
    val portableData: PortableDataManager
    val credentials: CredentialStore
    val avatars: AvatarStore
    val agent: com.lhy.yumerin.core.model.AgentService
    val backgroundCompanion: BackgroundCompanionController

    val useCases: AppUseCases

    suspend fun ensureSeedData()
}
