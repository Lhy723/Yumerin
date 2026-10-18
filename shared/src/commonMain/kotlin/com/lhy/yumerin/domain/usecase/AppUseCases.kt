package com.lhy.yumerin.domain.usecase

import com.lhy.yumerin.core.model.*
import com.lhy.yumerin.domain.agent.AgentAdmissionArbiter
import com.lhy.yumerin.domain.agent.AgentRunDiagnostics
import com.lhy.yumerin.domain.agent.AgentCapability
import com.lhy.yumerin.domain.agent.AgentRuntime
import com.lhy.yumerin.domain.agent.CalculatorCapability
import com.lhy.yumerin.domain.agent.DelegateTaskCapability
import com.lhy.yumerin.domain.memory.ExtractedMemoryFact
import com.lhy.yumerin.domain.memory.HybridMemoryExtractor
import com.lhy.yumerin.domain.memory.MemoryExtractor
import com.lhy.yumerin.domain.memory.MemoryIntegrator
import com.lhy.yumerin.domain.memory.MemoryPolicy
import com.lhy.yumerin.domain.memory.ModelMemoryExtractor
import com.lhy.yumerin.domain.model.ModelRouter
import com.lhy.yumerin.domain.usecase.ImportCharacterCardUseCase
import com.lhy.yumerin.domain.agent.CharacterStateCoordinator
import com.lhy.yumerin.domain.agent.MemoryRecallCapability
import com.lhy.yumerin.domain.agent.ScheduleLookupCapability
import com.lhy.yumerin.domain.background.AgentGoalScheduler
import com.lhy.yumerin.domain.background.DelegatedAgentGoalScheduler
import com.lhy.yumerin.domain.repository.*

class AppUseCases(
    characters: CharacterRepository,
    chats: ChatRepository,
    groups: GroupRepository,
    memories: MemoryRepository,
    models: ModelProfileRepository,
    agent: AgentService,
    settingsRepository: SettingsRepository,
    memberships: GroupMembershipRepository,
    schedules: CompanionScheduleRepository,
    avatars: AvatarStore,
    characterRuntimeStates: CharacterRuntimeStateRepository,
    agentGoals: AgentGoalRepository,
    agentGoalScheduler: AgentGoalScheduler,
    delegatedAgentGoals: DelegatedAgentGoalRepository? = null,
    delegatedAgentGoalScheduler: DelegatedAgentGoalScheduler? = null,
    agentCapabilities: List<AgentCapability>? = null,
    agentAdmissionArbiter: AgentAdmissionArbiter = AgentAdmissionArbiter()
) {
    val agentRunDiagnostics = AgentRunDiagnostics()
    private val delegatedGoalRepository = delegatedAgentGoals ?: NoopDelegatedAgentGoalRepository
    private val delegatedGoalScheduler = delegatedAgentGoalScheduler ?: NoopDelegatedAgentGoalScheduler
    val createDelegatedAgentGoal = CreateDelegatedAgentGoalUseCase(
        characters, chats, delegatedGoalRepository, delegatedGoalScheduler
    )
    private val agentRuntime = AgentRuntime(
        agent,
        agentCapabilities ?: listOf(
            CalculatorCapability(),
            MemoryRecallCapability(memories),
            ScheduleLookupCapability(schedules),
            DelegateTaskCapability(createDelegatedAgentGoal)
        ),
        admission = agentAdmissionArbiter
    ).apply { diagnostics = agentRunDiagnostics }
    private val characterStateCoordinator = CharacterStateCoordinator(characterRuntimeStates)
    private val proactiveAdmissionPolicy = ProactiveAdmissionPolicy(chats)
    private val modelRouter = ModelRouter(models)
    private val memoryIntegrator = MemoryIntegrator(
        memories,
        HybridMemoryExtractor(
            lexical = MemoryExtractor { text -> MemoryPolicy.extract(text).map { candidate ->
                ExtractedMemoryFact(
                    content = candidate.content, category = candidate.category,
                    confidence = candidate.confidence, sensitive = candidate.sensitive,
                    reviewReason = candidate.reviewReason
                )
            }
            },
            modelBased = ModelMemoryExtractor(modelRouter) { request -> agent.reply(request).text }
        )
    )
    val sendChatMessage = SendChatMessageUseCase(characters, chats, groups, memories, models, agentRuntime, memberships, characterStateCoordinator, memoryIntegrator)
    val retryChatResponse = RetryChatResponseUseCase(characters, chats, groups, memories, models, agentRuntime, memberships, characterStateCoordinator)
    val saveCharacter = SaveCharacterUseCase(characters, chats, avatars)
    val importCharacterCard = ImportCharacterCardUseCase(characters, chats, memories, saveCharacter)
    val observeCharacterRuntimeState = ObserveCharacterRuntimeStateUseCase(characterRuntimeStates)
    val updateCharacterContinuity = UpdateCharacterContinuityUseCase(characterRuntimeStates)
    val observeScheduledAgentGoals = ObserveScheduledAgentGoalsUseCase(agentGoals)
    val observeDelegatedAgentGoals = ObserveDelegatedAgentGoalsUseCase(delegatedGoalRepository)
    val createGroupChat = CreateGroupChatUseCase(groups, chats, memberships)
    val createDirectChat = CreateDirectChatUseCase(chats)
    val updateGroupChat = UpdateGroupChatUseCase(groups, chats, memberships)
    val deleteChat = DeleteChatUseCase(chats, agentGoals, agentGoalScheduler, delegatedGoalRepository, delegatedGoalScheduler)
    val archiveCharacter = ArchiveCharacterUseCase(characters)
    val deleteCharacter = DeleteCharacterUseCase(
        characters, chats, groups, memories, memberships, avatars, characterRuntimeStates, agentGoals, agentGoalScheduler,
        delegatedGoalRepository, delegatedGoalScheduler
    )
    val sendProactiveMessage = SendProactiveMessageUseCase(characters, chats, memories, models, schedules, agentRuntime, characterStateCoordinator, proactiveAdmissionPolicy)
    val scheduleAgentGoal = ScheduleAgentGoalUseCase(characters, chats, agentGoals, settingsRepository, agentGoalScheduler)
    val cancelAgentGoal = CancelAgentGoalUseCase(agentGoals, agentGoalScheduler)
    val executeAgentGoal = ExecuteAgentGoalUseCase(
        characters, chats, memories, models, agentGoals, settingsRepository, agentRuntime,
        characterStateCoordinator, proactiveAdmissionPolicy
    )
    val processDelegatedAgentGoal = ProcessDelegatedAgentGoalUseCase(
        characters, chats, memories, models, delegatedGoalRepository, agentRuntime, settingsRepository, characterStateCoordinator
    )
    val pauseDelegatedAgentGoal = PauseDelegatedAgentGoalUseCase(delegatedGoalRepository, delegatedGoalScheduler)
    val resumeDelegatedAgentGoal = ResumeDelegatedAgentGoalUseCase(delegatedGoalRepository, delegatedGoalScheduler, chats)
    val cancelDelegatedAgentGoal = CancelDelegatedAgentGoalUseCase(delegatedGoalRepository, delegatedGoalScheduler)
}

private object NoopDelegatedAgentGoalRepository : DelegatedAgentGoalRepository {
    override fun observeGoals(): kotlinx.coroutines.flow.Flow<List<DelegatedAgentGoal>> = kotlinx.coroutines.flow.flowOf(emptyList())
    override suspend fun getGoal(id: EntityId): DelegatedAgentGoal? = null
    override suspend fun saveGoal(goal: DelegatedAgentGoal) = Unit
    override suspend fun deleteGoal(id: EntityId) = Unit
}

private object NoopDelegatedAgentGoalScheduler : DelegatedAgentGoalScheduler {
    override suspend fun enqueue(goalId: String) = Unit
    override suspend fun enqueueNext(goalId: String) = Unit
    override suspend fun cancel(goalId: String) = Unit
    override suspend fun reconcile() = Unit
    override suspend fun cancelAll() = Unit
}
