package com.lhy.yumerin.data.room

import com.lhy.yumerin.core.model.*

internal fun ScheduledAgentGoalEntity.toDomain() = ScheduledAgentGoal(
    id = id,
    characterId = characterId,
    chatId = chatId,
    title = title,
    prompt = prompt,
    dueAt = dueAt,
    status = runCatching { AgentGoalStatus.valueOf(status) }.getOrDefault(AgentGoalStatus.PENDING),
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    resultMessageId = resultMessageId,
    failureReason = failureReason
)

internal fun ScheduledAgentGoal.toEntity() = ScheduledAgentGoalEntity(
    id = id,
    characterId = characterId,
    chatId = chatId,
    title = title,
    prompt = prompt,
    dueAt = dueAt,
    status = status.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    resultMessageId = resultMessageId,
    failureReason = failureReason
)

internal fun DelegatedAgentGoalEntity.toDomain(steps: List<DelegatedAgentGoalStep>) = DelegatedAgentGoal(
    id = id,
    characterId = characterId,
    chatId = chatId,
    title = title,
    outcome = outcome,
    constraints = constraints,
    replanCount = replanCount.coerceIn(0, 2),
    status = runCatching { DelegatedAgentGoalStatus.valueOf(status) }.getOrDefault(DelegatedAgentGoalStatus.QUEUED),
    steps = steps,
    latestSummary = latestSummary,
    pendingQuestion = pendingQuestion,
    resumeInput = resumeInput,
    failureReason = failureReason,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt
)

internal fun DelegatedAgentGoal.toEntity() = DelegatedAgentGoalEntity(
    id = id,
    characterId = characterId,
    chatId = chatId,
    title = title,
    outcome = outcome,
    constraints = constraints,
    replanCount = replanCount.coerceIn(0, 2),
    status = status.name,
    latestSummary = latestSummary,
    pendingQuestion = pendingQuestion,
    resumeInput = resumeInput,
    failureReason = failureReason,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt
)

internal fun DelegatedAgentGoalStepEntity.toDomain() = DelegatedAgentGoalStep(
    id = id,
    goalId = goalId,
    order = stepOrder,
    title = title,
    status = runCatching { DelegatedAgentGoalStepStatus.valueOf(status) }.getOrDefault(DelegatedAgentGoalStepStatus.PENDING),
    summary = summary,
    attempts = attempts.coerceAtLeast(0)
)

internal fun DelegatedAgentGoalStep.toEntity() = DelegatedAgentGoalStepEntity(
    id = id,
    goalId = goalId,
    stepOrder = order,
    title = title,
    status = status.name,
    summary = summary,
    attempts = attempts.coerceAtLeast(0)
)
