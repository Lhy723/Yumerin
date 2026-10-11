package com.lhy.yumerin.data.room

import com.lhy.yumerin.core.model.*

fun MemoryEntryEntity.toDomain() = MemoryEntry(
    id = id,
    characterId = characterId,
    sourceChatId = sourceChatId,
    sourceMessageId = sourceMessageId,
    category = enumValueOrDefault(category, MemoryCategory.LONG_TERM),
    content = content,
    confidence = confidence,
    reviewState = enumValueOrDefault(reviewState, MemoryReviewState.ACCEPTED),
    pinned = pinned,
    supersedesId = supersedesId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    reviewAt = reviewAt,
    reviewReason = reviewReason,
    expiresAt = expiresAt,
    deletedAt = deletedAt
)

fun MemoryEntry.toEntity() = MemoryEntryEntity(
    id = id,
    characterId = characterId,
    sourceChatId = sourceChatId,
    sourceMessageId = sourceMessageId,
    category = category.name,
    content = content,
    confidence = confidence,
    reviewState = reviewState.name,
    pinned = pinned,
    supersedesId = supersedesId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    reviewAt = reviewAt,
    reviewReason = reviewReason,
    expiresAt = expiresAt,
    deletedAt = deletedAt
)
