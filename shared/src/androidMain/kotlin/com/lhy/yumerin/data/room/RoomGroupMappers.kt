package com.lhy.yumerin.data.room

import com.lhy.yumerin.core.model.*

fun GroupMembershipWindowEntity.toDomain() = GroupMembershipWindow(
    id = id,
    groupId = groupId,
    characterId = characterId,
    joinedAt = joinedAt,
    leftAt = leftAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun GroupMembershipWindow.toEntity() = GroupMembershipWindowEntity(
    id = id,
    groupId = groupId,
    characterId = characterId,
    joinedAt = joinedAt,
    leftAt = leftAt,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun CompanionScheduleEntity.toDomain() = CompanionSchedule(
    id = id,
    characterId = characterId,
    chatId = chatId,
    title = title,
    prompt = prompt,
    hour = hour,
    minute = minute,
    enabled = enabled,
    lastTriggeredAt = lastTriggeredAt,
    dailyTriggerCount = dailyTriggerCount,
    dailyCountStamp = dailyCountStamp,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)

fun CompanionSchedule.toEntity() = CompanionScheduleEntity(
    id = id,
    characterId = characterId,
    chatId = chatId,
    title = title,
    prompt = prompt,
    hour = hour,
    minute = minute,
    enabled = enabled,
    lastTriggeredAt = lastTriggeredAt,
    dailyTriggerCount = dailyTriggerCount,
    dailyCountStamp = dailyCountStamp,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt
)
