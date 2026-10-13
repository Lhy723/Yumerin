package com.lhy.yumerin.data.room

import com.lhy.yumerin.core.model.CharacterRuntimeState

internal fun CharacterRuntimeStateEntity.toDomain() = CharacterRuntimeState(
    characterId = characterId,
    moodValence = moodValence,
    moodArousal = moodArousal,
    trust = trust,
    closeness = closeness,
    completedInteractions = completedInteractions,
    lastInteractionAt = lastInteractionAt,
    sourceChatId = sourceChatId,
    sourceMessageId = sourceMessageId,
    updatedAt = updatedAt
)

internal fun CharacterRuntimeState.toEntity() = CharacterRuntimeStateEntity(
    characterId = characterId,
    moodValence = moodValence,
    moodArousal = moodArousal,
    trust = trust,
    closeness = closeness,
    completedInteractions = completedInteractions,
    lastInteractionAt = lastInteractionAt,
    sourceChatId = sourceChatId,
    sourceMessageId = sourceMessageId,
    updatedAt = updatedAt
)
